import AVFoundation
import CoreImage
import CoreImage.CIFilterBuiltins
import CoreVideo
import Metal
import QuartzCore
import UIKit
import Vision

/**
 * Real-time CI/Metal processor for Xani Lens on iOS. Identity is a blit.
 * Person segmentation and face landmarks run on a downscaled frame at the
 * device-class cadence so a live never drops below ~30 fps.
 */
final class LensProcessor {
    private let ci: CIContext
    private let metal: MTLDevice?
    private let queue: MTLCommandQueue?
    private var lastFace: VNFaceObservation?
    private var lastMask: CIImage?
    private var lastBg: CIImage?
    private var lastBgURL: URL?
    private var frame: Int = 0
    private let analyzeEvery: Int

    init() {
        let mtl = MTLCreateSystemDefaultDevice()
        metal = mtl
        queue = mtl?.makeCommandQueue()
        if let mtl {
            ci = CIContext(mtlDevice: mtl, options: [.cacheIntermediates: false])
        } else {
            ci = CIContext(options: [.useSoftwareRenderer: false, .cacheIntermediates: false])
        }
        switch DeviceClass.current() {
        case .low: analyzeEvery = 4
        case .mid: analyzeEvery = 3
        case .high: analyzeEvery = 2
        }
    }

    func apply(_ image: CIImage, state: LensState, front _: Bool = false) -> CIImage {
        frame += 1
        if state.identity { return image }
        if frame % analyzeEvery == 0 {
            analyze(image, faces: state.needsFace, seg: state.needsSegmentation)
        }
        var out = image
        let beauty = state.beauty
        if beauty.smooth > 0.01 || beauty.whiten > 0.01 || beauty.glow > 0.01 || beauty.sharpen > 0.01 {
            out = beautyPass(out, beauty)
        }
        if !state.background.isNone {
            out = backgroundPass(out, image.extent, state.background)
        }
        if let look = LensCatalog.look(state.lookId), !look.neutral {
            out = lookPass(out, look)
        }
        if !state.masks.isEmpty, let face = lastFace {
            out = maskPass(out, image.extent, face, state.masks)
        }
        return out.cropped(to: image.extent)
    }

    func render(_ image: CIImage, to drawable: CAMetalDrawable, size: CGSize) {
        guard let queue, let cmd = queue.makeCommandBuffer() else { return }
        let bounds = CGRect(origin: .zero, size: size)
        ci.render(image, to: drawable.texture, commandBuffer: cmd, bounds: bounds, colorSpace: CGColorSpaceCreateDeviceRGB())
        cmd.present(drawable)
        cmd.commit()
    }

    private func analyze(_ image: CIImage, faces: Bool, seg: Bool) {
        let handler = VNImageRequestHandler(ciImage: image, orientation: .up, options: [:])
        var reqs: [VNRequest] = []
        if faces {
            let r = VNDetectFaceLandmarksRequest { [weak self] req, _ in
                self?.lastFace = (req.results as? [VNFaceObservation])?.first
            }
            r.revision = VNDetectFaceLandmarksRequestRevision3
            reqs.append(r)
        }
        if seg {
            let r = VNGeneratePersonSegmentationRequest { [weak self] req, _ in
                guard let obs = (req.results as? [VNPixelBufferObservation])?.first else { return }
                self?.lastMask = CIImage(cvPixelBuffer: obs.pixelBuffer)
            }
            r.qualityLevel = DeviceClass.current() == .low ? .fast : .balanced
            r.outputPixelFormat = kCVPixelFormatType_OneComponent8
            reqs.append(r)
        }
        if !reqs.isEmpty { try? handler.perform(reqs) }
    }

    private func beautyPass(_ img: CIImage, _ b: LensBeauty) -> CIImage {
        var out = img
        if b.smooth > 0.01 {
            let blur = img.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 2.2 + 6.0 * Double(b.smooth)])
            out = blur.applyingFilter("CIBlendWithMask", parameters: [
                kCIInputBackgroundImageKey: img,
                kCIInputMaskImageKey: skinMask(img.extent),
            ]).cropped(to: img.extent)
            // Mix back some of the original so pores don't turn to plastic.
            let mix = CIFilter.dissolveTransition()
            mix.inputImage = img
            mix.targetImage = out
            mix.time = b.smooth * 0.84
            out = mix.outputImage?.cropped(to: img.extent) ?? out
        }
        if b.whiten > 0.01 {
            let exp = CIFilter.exposureAdjust()
            exp.inputImage = out
            exp.ev = b.whiten * 0.35
            out = exp.outputImage ?? out
        }
        if b.sharpen > 0.01 {
            out = out.applyingFilter("CISharpenLuminance", parameters: [kCIInputSharpnessKey: 0.4 + b.sharpen])
        }
        if b.glow > 0.01 {
            let g = out.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 8.0])
            let add = CIFilter.additionCompositing()
            add.inputImage = g.applyingFilter("CIColorMatrix", parameters: [
                "inputRVector": CIVector(x: 0.18 * CGFloat(b.glow), y: 0, z: 0, w: 0),
                "inputGVector": CIVector(x: 0, y: 0.14 * CGFloat(b.glow), z: 0, w: 0),
                "inputBVector": CIVector(x: 0, y: 0, z: 0.1 * CGFloat(b.glow), w: 0),
                "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 0.35),
            ])
            add.backgroundImage = out
            out = add.outputImage?.cropped(to: img.extent) ?? out
        }
        return out
    }

    private func skinMask(_ extent: CGRect) -> CIImage {
        if let face = lastFace {
            let r = VNImageRectForNormalizedRect(face.boundingBox, Int(extent.width), Int(extent.height))
            return CIImage(color: .white).cropped(to: r.insetBy(dx: -r.width * 0.1, dy: -r.height * 0.08))
                .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 24])
                .cropped(to: extent)
        }
        return CIImage(color: CIColor(red: 0.7, green: 0.7, blue: 0.7, alpha: 1)).cropped(to: extent)
    }

    private func backgroundPass(_ person: CIImage, _ extent: CGRect, _ bg: LensBackground) -> CIImage {
        let bgImg: CIImage
        switch bg {
        case .none:
            return person
        case .blur:
            bgImg = person.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 22]).cropped(to: extent)
        case .gradient(_, let top, let bottom):
            bgImg = gradient(extent, top: top, bottom: bottom)
        case .image(let url):
            bgImg = imageBackground(url, extent: extent) ?? person.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 18]).cropped(to: extent)
        }
        guard var mask = lastMask else { return person }
        let sx = extent.width / mask.extent.width
        let sy = extent.height / mask.extent.height
        mask = mask.transformed(by: CGAffineTransform(scaleX: sx, y: sy))
        mask = mask.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 4])
        return person.applyingFilter("CIBlendWithMask", parameters: [
            kCIInputBackgroundImageKey: bgImg,
            kCIInputMaskImageKey: mask,
        ]).cropped(to: extent)
    }

    private func gradient(_ extent: CGRect, top: UInt32, bottom: UInt32) -> CIImage {
        let g = CIFilter.linearGradient()
        g.point0 = CGPoint(x: extent.midX, y: extent.maxY)
        g.point1 = CGPoint(x: extent.midX, y: extent.minY)
        g.color0 = ciColor(top)
        g.color1 = ciColor(bottom)
        return g.outputImage?.cropped(to: extent) ?? CIImage(color: .black).cropped(to: extent)
    }

    private func lookPass(_ img: CIImage, _ look: LensLook) -> CIImage {
        var out = img
        if look.exposure != 0 {
            let f = CIFilter.exposureAdjust()
            f.inputImage = out
            f.ev = look.exposure * 1.4
            out = f.outputImage ?? out
        }
        if look.contrast != 0 || look.saturation != 0 {
            let f = CIFilter.colorControls()
            f.inputImage = out
            f.contrast = 1 + look.contrast
            f.saturation = 1 + look.saturation
            out = f.outputImage ?? out
        }
        if look.temperature != 0 || look.tint != 0 {
            let f = CIFilter.temperatureAndTint()
            f.inputImage = out
            f.neutral = CIVector(x: 6500, y: 0)
            f.targetNeutral = CIVector(x: 6500 - CGFloat(look.temperature) * 1800, y: CGFloat(look.tint) * 80)
            out = f.outputImage ?? out
        }
        if look.fade > 0 {
            let f = CIFilter.colorMatrix()
            f.inputImage = out
            let m = 1 - CGFloat(look.fade) * 0.25
            f.rVector = CIVector(x: m, y: 0, z: 0, w: 0)
            f.gVector = CIVector(x: 0, y: m, z: 0, w: 0)
            f.bVector = CIVector(x: 0, y: 0, z: m, w: 0)
            f.aVector = CIVector(x: 0, y: 0, z: 0, w: 1)
            f.biasVector = CIVector(x: 0.12 * CGFloat(look.fade), y: 0.12 * CGFloat(look.fade), z: 0.12 * CGFloat(look.fade), w: 0)
            out = f.outputImage ?? out
        }
        if look.vignette > 0 {
            out = out.applyingFilter("CIVignette", parameters: [
                kCIInputIntensityKey: look.vignette * 1.4,
                kCIInputRadiusKey: 1.2,
            ])
        }
        if look.mono > 0.5 {
            out = out.applyingFilter("CIPhotoEffectMono")
            if look.id == "look.sepia" { out = out.applyingFilter("CISepiaTone", parameters: [kCIInputIntensityKey: 0.85]) }
        }
        if look.hue != 0 {
            out = out.applyingFilter("CIHueAdjust", parameters: [kCIInputAngleKey: look.hue * .pi])
        }
        return out
    }

    private func maskPass(_ img: CIImage, _ extent: CGRect, _ face: VNFaceObservation, _ ids: [String]) -> CIImage {
        var out = img
        let box = VNImageRectForNormalizedRect(face.boundingBox, Int(extent.width), Int(extent.height))
        for id in ids {
            guard let mask = LensCatalog.mask(id) else { continue }
            let overlay = glyphImage(mask.glyph, size: CGSize(width: box.width * 1.15, height: box.height * 0.42))
            let y: CGFloat
            if id == "m.crown" || id == "m.flowers" || id == "m.devil" || id == "m.angel" || id == "m.bunny" || id == "m.cat" || id == "m.puppy" || id == "m.wings" || id == "m.bow" {
                y = box.maxY - overlay.extent.height * 0.2
            } else if id == "m.stache" || id == "m.tears" {
                y = box.minY + box.height * 0.22
            } else {
                y = box.midY - overlay.extent.height * 0.35
            }
            let placed = overlay.transformed(by: CGAffineTransform(translationX: box.midX - overlay.extent.width / 2, y: y))
            out = placed.composited(over: out)
        }
        return out.cropped(to: extent)
    }

    private func glyphImage(_ glyph: String, size: CGSize) -> CIImage {
        let renderer = UIGraphicsImageRenderer(size: size)
        let ui = renderer.image { _ in
            let para = NSMutableParagraphStyle()
            para.alignment = .center
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: min(size.width, size.height) * 0.72),
                .paragraphStyle: para,
            ]
            (glyph as NSString).draw(in: CGRect(origin: .zero, size: size), withAttributes: attrs)
        }
        return CIImage(image: ui) ?? CIImage(color: .clear).cropped(to: CGRect(origin: .zero, size: size))
    }

    private func imageBackground(_ url: URL, extent: CGRect) -> CIImage? {
        if lastBgURL == url, let cached = lastBg {
            return cached.transformed(by: fit(cached.extent, into: extent))
        }
        guard let img = CIImage(contentsOf: url) else { return nil }
        lastBgURL = url
        lastBg = img
        return img.transformed(by: fit(img.extent, into: extent))
    }

    private func fit(_ src: CGRect, into dst: CGRect) -> CGAffineTransform {
        let sx = dst.width / max(src.width, 1)
        let sy = dst.height / max(src.height, 1)
        let s = max(sx, sy)
        let dx = dst.midX - src.midX * s
        let dy = dst.midY - src.midY * s
        return CGAffineTransform(a: s, b: 0, c: 0, d: s, tx: dx, ty: dy)
    }

    func jpeg(_ image: CIImage) -> Data? {
        ci.jpegRepresentation(of: image, colorSpace: CGColorSpaceCreateDeviceRGB())
    }

    private func ciColor(_ rgb: UInt32) -> CIColor {
        CIColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
