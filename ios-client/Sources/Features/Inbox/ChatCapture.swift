import SwiftUI
import AVFoundation
import UniformTypeIdentifiers
import UIKit
import Photos
import Contacts
import MetalKit
import CoreImage
import QuartzCore

/// What kind of message a picked file becomes.
///
/// The visual picker returns pictures and clips through one callback, and the
/// server validates the bytes against the kind it is told, so the kind has to
/// come from the file rather than from which button was pressed.
func attachmentKind(_ url: URL) -> MsgKind {
    if let type = UTType(filenameExtension: url.pathExtension.lowercased()) {
        if type.conforms(to: .movie) || type.conforms(to: .video) { return .video }
        if type.conforms(to: .image) { return .image }
        if type.conforms(to: .audio) { return .voice }
    }
    // Unknown type: guessing "picture" on a clip is the one guess the server
    // always rejects, so check the extensions that matter by hand.
    return ["mp4", "mov", "m4v", "3gp", "webm", "mkv", "avi"].contains(url.pathExtension.lowercased()) ? .video : .image
}

struct MediaPicker: UIViewControllerRepresentable {
    var source: UIImagePickerController.SourceType
    var onPick: (URL) -> Void
    @Environment(\.presentationMode) private var presentation

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.delegate = context.coordinator
        p.sourceType = UIImagePickerController.isSourceTypeAvailable(source) ? source : .photoLibrary
        p.mediaTypes = ["public.image", "public.movie"]
        p.allowsEditing = false
        return p
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: MediaPicker
        init(_ parent: MediaPicker) { self.parent = parent }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.presentation.wrappedValue.dismiss()
        }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let url = info[.imageURL] as? URL ?? info[.mediaURL] as? URL {
                parent.onPick(url)
            } else if let image = info[.originalImage] as? UIImage, let data = image.jpegData(compressionQuality: 0.82) {
                let url = FileManager.default.temporaryDirectory.appendingPathComponent("cap_\(UUID().uuidString).jpg")
                try? data.write(to: url, options: .atomic)
                parent.onPick(url)
            }
            parent.presentation.wrappedValue.dismiss()
        }
    }
}

struct DocumentPick: UIViewControllerRepresentable {
    var onPick: (URL) -> Void
    @Environment(\.presentationMode) private var presentation

    func makeCoordinator() -> Coord { Coord(self) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let p = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
    final class Coord: NSObject, UIDocumentPickerDelegate {
        let parent: DocumentPick
        init(_ parent: DocumentPick) { self.parent = parent }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            if let u = urls.first { parent.onPick(u) }
            parent.presentation.wrappedValue.dismiss()
        }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            parent.presentation.wrappedValue.dismiss()
        }
    }
}

final class VoiceRecorder {
    private var rec: AVAudioRecorder?
    private var file: URL?

    func start() -> Bool {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("voice_\(UUID().uuidString).m4a")
            let rec = try AVAudioRecorder(url: url, settings: [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 44100,
                AVNumberOfChannelsKey: 1,
                AVEncoderBitRateKey: 96000,
            ])
            rec.record()
            self.rec = rec
            self.file = url
            return true
        } catch {
            return false
        }
    }

    func stop() -> URL? {
        rec?.stop()
        rec = nil
        let out = file
        file = nil
        return out
    }

    func cancel() {
        rec?.stop()
        rec = nil
        if let file { try? FileManager.default.removeItem(at: file) }
        file = nil
    }
}

struct CameraPreview: UIViewRepresentable {
    var front: Bool
    var enabled: Bool
    var snap: CameraSnap? = nil
    var lens: LensController? = nil

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.lens = lens
        snap?.view = v
        if enabled { v.start(front: front) }
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        snap?.view = uiView
        uiView.lens = lens
        if enabled { uiView.start(front: front) } else { uiView.stop() }
    }

    static func dismantleUIView(_ uiView: PreviewView, coordinator: ()) {
        uiView.stop()
    }

    final class PreviewView: UIView, AVCapturePhotoCaptureDelegate, AVCaptureVideoDataOutputSampleBufferDelegate {
        private let session = AVCaptureSession()
        private let photoOut = AVCapturePhotoOutput()
        private let videoOut = AVCaptureVideoDataOutput()
        private let lensQueue = DispatchQueue(label: "xani.lens", qos: .userInteractive)
        private let processor = LensProcessor()
        private var metal: MTKView?
        var lens: LensController?
        private var started = false
        var onShot: ((URL) -> Void)?
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }

        private var currentFront: Bool?
        override func layoutSubviews() {
            super.layoutSubviews()
            clipsToBounds = true
            preview.frame = bounds
            preview.videoGravity = .resizeAspectFill
            metal?.frame = bounds
        }

        func start(front: Bool) {
            if started, currentFront == front { return }
            currentFront = front
            started = true
            clipsToBounds = true
            preview.videoGravity = .resizeAspectFill
            preview.session = session
            preview.frame = bounds
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                guard let self else { return }
                if self.session.isRunning { self.session.stopRunning() }
                self.session.beginConfiguration()
                self.session.inputs.forEach { self.session.removeInput($0) }
                self.session.outputs.forEach { self.session.removeOutput($0) }
                // HD preset: default session quality is soft when stretched full-screen.
                if self.session.canSetSessionPreset(.hd1920x1080) {
                    self.session.sessionPreset = .hd1920x1080
                } else if self.session.canSetSessionPreset(.hd1280x720) {
                    self.session.sessionPreset = .hd1280x720
                }
                let pos: AVCaptureDevice.Position = front ? .front : .back
                if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: pos),
                   let input = try? AVCaptureDeviceInput(device: device),
                   self.session.canAddInput(input) {
                    self.session.addInput(input)
                    // Continuous 3A + low-light boost: tracks focus/exposure as
                    // the scene changes and brightens dim rooms automatically.
                    if (try? device.lockForConfiguration()) != nil {
                        if device.isFocusModeSupported(.continuousAutoFocus) {
                            device.focusMode = .continuousAutoFocus
                        }
                        if device.isExposureModeSupported(.continuousAutoExposure) {
                            device.exposureMode = .continuousAutoExposure
                        }
                        if device.isWhiteBalanceModeSupported(.continuousAutoWhiteBalance) {
                            device.whiteBalanceMode = .continuousAutoWhiteBalance
                        }
                        if device.isLowLightBoostSupported {
                            device.automaticallyEnablesLowLightBoostWhenAvailable = true
                        }
                        device.isSubjectAreaChangeMonitoringEnabled = true
                        device.videoZoomFactor = 1.0
                        device.unlockForConfiguration()
                    }
                }
                if self.session.canAddOutput(self.photoOut) {
                    self.session.addOutput(self.photoOut)
                }
                if self.lens != nil {
                    self.videoOut.alwaysDiscardsLateVideoFrames = true
                    self.videoOut.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                    self.videoOut.setSampleBufferDelegate(self, queue: self.lensQueue)
                    if self.session.canAddOutput(self.videoOut) {
                        self.session.addOutput(self.videoOut)
                    }
                    if let conn = self.videoOut.connection(with: .video) {
                        if conn.isVideoOrientationSupported { conn.videoOrientation = .portrait }
                        if conn.isVideoMirroringSupported { conn.isVideoMirrored = front }
                    }
                }
                self.session.commitConfiguration()
                DispatchQueue.main.async {
                    if let conn = self.preview.connection, conn.isVideoOrientationSupported {
                        conn.videoOrientation = .portrait
                    }
                    self.preview.videoGravity = .resizeAspectFill
                    self.bindMetal(on: self.lens != nil)
                }
                self.session.startRunning()
            }
        }

        func stop() {
            started = false
            currentFront = nil
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.session.stopRunning()
            }
        }

        func snap() {
            photoOut.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }

        private func bindMetal(on: Bool) {
            preview.isHidden = on
            if on {
                if metal == nil, let device = MTLCreateSystemDefaultDevice() {
                    let v = MTKView(frame: bounds, device: device)
                    v.framebufferOnly = false
                    v.isPaused = true
                    v.enableSetNeedsDisplay = false
                    v.contentMode = .scaleAspectFill
                    v.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                    insertSubview(v, at: 0)
                    metal = v
                }
                metal?.isHidden = false
                metal?.frame = bounds
            } else {
                metal?.isHidden = true
            }
        }

        func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
            guard let lens, let pixel = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
            var image = CIImage(cvPixelBuffer: pixel)
            let state = lens.state
            image = processor.apply(image, state: state, front: currentFront == true)
            guard let metal, let layer = metal.layer as? CAMetalLayer else { return }
            let scale = max(layer.contentsScale, 1)
            let size = CGSize(width: metal.bounds.width * scale, height: metal.bounds.height * scale)
            if size.width < 2 || size.height < 2 { return }
            layer.framebufferOnly = false
            layer.drawableSize = size
            guard let drawable = layer.nextDrawable() else { return }
            processor.render(image, to: drawable, size: size)
        }

        func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
            guard let data = photo.fileDataRepresentation() else { return }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("cap_\(UUID().uuidString).jpg")
            if let lens, !lens.state.identity, let ci = CIImage(data: data) {
                let out = processor.apply(ci, state: lens.state, front: currentFront == true)
                if let jpeg = processor.jpeg(out) {
                    try? jpeg.write(to: url, options: .atomic)
                    DispatchQueue.main.async { self.onShot?(url) }
                    return
                }
            }
            try? data.write(to: url, options: .atomic)
            DispatchQueue.main.async { self.onShot?(url) }
        }
    }
}

final class CameraSnap: ObservableObject {
    weak var view: CameraPreview.PreviewView?
    func capture(_ done: @escaping (URL) -> Void) {
        view?.onShot = done
        view?.snap()
    }
}

struct ChatCameraSheet: View {
    let onShot: (URL) -> Void
    let onGallery: () -> Void
    let onClose: () -> Void
    var onPicked: ([URL]) -> Void = { _ in }
    @Environment(\.uiScale) private var scale
    @StateObject private var snap = CameraSnap()
    @State private var recents: [RecentThumb] = []
    @State private var assets: [AssetRef] = []
    @State private var allowed = false
    @State private var lift = false
    @State private var picked: Set<String> = []

    var body: some View {
        ZStack(alignment: .bottom) {
            CameraPreview(front: false, enabled: true, snap: snap).ignoresSafeArea()
            VStack {
                HStack {
                    Button(action: onClose) {
                        Circle().fill(Color.black.opacity(0.45)).frame(width: 40 * scale, height: 40 * scale)
                            .overlay(ChatIco.close(.white, side: 16 * scale))
                    }
                    Spacer()
                }
                .padding(.horizontal, 12 * scale)
                .padding(.top, 12 * scale)
                Spacer()
                if !recents.isEmpty {
                    VStack(spacing: 8 * scale) {
                        Button { lift.toggle() } label: {
                            Image(systemName: "chevron.up")
                                .foregroundColor(.white)
                                .font(.system(size: 13 * scale, weight: .semibold))
                                .rotationEffect(.degrees(lift ? 180 : 0))
                                .frame(width: 28 * scale, height: 28 * scale)
                                .background(Color.white.opacity(0.22))
                                .clipShape(Circle())
                        }
                        if lift {
                            ScrollView {
                                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4 * scale), count: 4), spacing: 4 * scale) {
                                    ForEach(assets) { item in
                                        Button {
                                            if picked.contains(item.id) { picked.remove(item.id) } else { picked.insert(item.id) }
                                        } label: {
                                            ZStack(alignment: .topTrailing) {
                                                GalleryThumbView(asset: item.asset, edge: 160)
                                                    .aspectRatio(1, contentMode: .fill)
                                                    .clipped()
                                                if picked.contains(item.id) {
                                                    Circle().fill(Dark.primary).frame(width: 18 * scale, height: 18 * scale)
                                                        .overlay(ChatIco.tick(.white, side: 10 * scale))
                                                        .padding(4 * scale)
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(4 * scale)
                            }
                            .frame(height: 280 * scale)
                            if !picked.isEmpty {
                                Button {
                                    exportPicked()
                                } label: {
                                    AppText(Copy.t(.inboxSendBtn), style: Type.labelBold, color: .white, sizeOverride: 13)
                                        .padding(.horizontal, 16 * scale).padding(.vertical, 8 * scale)
                                        .background(Dark.primary).clipShape(Capsule())
                                }
                                .frame(maxWidth: .infinity, alignment: .trailing)
                                .padding(.horizontal, 12 * scale)
                            }
                        } else {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8 * scale) {
                                    ForEach(recents.prefix(12)) { item in
                                        Button {
                                            if picked.contains(item.id) { picked.remove(item.id) } else { picked.insert(item.id) }
                                        } label: {
                                            ZStack(alignment: .topTrailing) {
                                                Image(uiImage: item.image)
                                                    .resizable()
                                                    .scaledToFill()
                                                    .frame(width: 56 * scale, height: 56 * scale)
                                                    .clipped()
                                                    .clipShape(RoundedRectangle(cornerRadius: 8 * scale))
                                                if picked.contains(item.id) {
                                                    Circle().fill(Dark.primary).frame(width: 16 * scale, height: 16 * scale)
                                                        .overlay(ChatIco.tick(.white, side: 9 * scale))
                                                        .padding(3 * scale)
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 12 * scale)
                            }
                            if !picked.isEmpty {
                                Button(action: exportPicked) {
                                    AppText(Copy.t(.inboxSendBtn), style: Type.labelBold, color: .white, sizeOverride: 13)
                                        .padding(.horizontal, 16 * scale).padding(.vertical, 8 * scale)
                                        .background(Dark.primary).clipShape(Capsule())
                                }
                                .frame(maxWidth: .infinity, alignment: .trailing)
                                .padding(.horizontal, 12 * scale)
                            }
                        }
                    }
                    .gesture(
                        DragGesture(minimumDistance: 12)
                            .onEnded { v in
                                if v.translation.height < -40 { lift = true }
                                if v.translation.height > 40 { lift = false }
                            }
                    )
                } else if !allowed {
                    Button(action: requestPhotos) {
                        HStack(spacing: 8 * scale) {
                            ChatIco.attach(0, side: 22 * scale)
                            AppText(Copy.t(.inboxAllowPhotos), style: Type.labelBold, color: .white, sizeOverride: 13)
                        }
                        .padding(.horizontal, 14 * scale)
                        .padding(.vertical, 8 * scale)
                        .background(Color.white.opacity(0.16))
                        .clipShape(Capsule())
                    }
                    .padding(.horizontal, 16 * scale)
                }
                HStack {
                    Button(action: onGallery) {
                        Circle().fill(Color.white.opacity(0.16)).frame(width: 52 * scale, height: 52 * scale)
                            .overlay(ChatIco.attach(0, side: 26 * scale))
                    }
                    Spacer()
                    Button {
                        snap.capture(onShot)
                    } label: {
                        Circle().fill(Color.white).frame(width: 76 * scale, height: 76 * scale)
                            .overlay(Circle().fill(Color(rgb: 0xF3F4F6)).frame(width: 64 * scale, height: 64 * scale))
                    }
                    Spacer()
                    Color.clear.frame(width: 52 * scale, height: 52 * scale)
                }
                .padding(.horizontal, 28 * scale)
                .padding(.vertical, 14 * scale)
                .padding(.bottom, 16 * scale)
            }
            .background(
                LinearGradient(colors: [.clear, Color.black.opacity(0.72)], startPoint: .top, endPoint: .bottom)
                    .allowsHitTesting(false),
                alignment: .bottom
            )
        }
        .background(Color.black.ignoresSafeArea())
        .onAppear(perform: requestPhotos)
    }

    private func exportPicked() {
        let chosen = assets.filter { picked.contains($0.id) }.map(\.asset)
        var urls: [URL] = []
        let group = DispatchGroup()
        for a in chosen {
            group.enter()
            exportPhotoAsset(a) { url in
                urls.append(url)
                group.leave()
            }
        }
        group.notify(queue: .main) { onPicked(urls) }
    }

    private func requestPhotos() {
        let level: PHAccessLevel = .readWrite
        PHPhotoLibrary.requestAuthorization(for: level) { status in
            let ok = status == .authorized || status == .limited
            DispatchQueue.main.async {
                allowed = ok
                if ok { loadRecents() }
            }
        }
    }

    private func loadRecents() {
        let fetch = PHFetchOptions()
        fetch.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        fetch.fetchLimit = 120
        let result = PHAsset.fetchAssets(with: .image, options: fetch)
        var refs: [AssetRef] = []
        refs.reserveCapacity(result.count)
        result.enumerateObjects { asset, _, _ in
            refs.append(AssetRef(asset: asset))
        }
        DispatchQueue.main.async { assets = refs }
        let opts = PHImageRequestOptions()
        opts.deliveryMode = .fastFormat
        opts.resizeMode = .fast
        opts.isNetworkAccessAllowed = false
        let mgr = PHCachingImageManager()
        let size = CGSize(width: 120, height: 120)
        for asset in refs.prefix(12).map(\.asset) {
            mgr.requestImage(for: asset, targetSize: size, contentMode: .aspectFill, options: opts) { image, info in
                guard let image else { return }
                let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
                if degraded { return }
                DispatchQueue.main.async {
                    if recents.contains(where: { $0.id == asset.localIdentifier }) { return }
                    recents.append(RecentThumb(id: asset.localIdentifier, image: image, asset: asset))
                }
            }
        }
    }

    private func exportAsset(_ asset: PHAsset) {
        let opts = PHImageRequestOptions()
        opts.deliveryMode = .highQualityFormat
        opts.isNetworkAccessAllowed = false
        PHImageManager.default().requestImageDataAndOrientation(for: asset, options: opts) { data, _, _, _ in
            guard let data else { return }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("cap_\(UUID().uuidString).jpg")
            try? data.write(to: url, options: .atomic)
            DispatchQueue.main.async { onShot(url) }
        }
    }
}

struct RecentThumb: Identifiable {
    let id: String
    let image: UIImage
    let asset: PHAsset
}

struct AssetRef: Identifiable {
    var id: String { asset.localIdentifier }
    let asset: PHAsset
}

enum PhotoThumbCache {
    static let images = NSCache<NSString, UIImage>()
    static let mgr = PHCachingImageManager()
    static func key(_ id: String, _ size: Int) -> NSString { "\(id)_\(size)" as NSString }
}

struct GalleryThumbView: View {
    let asset: PHAsset
    var edge: CGFloat = 160
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Color(white: 0.18)
            }
        }
        .onAppear(perform: load)
    }

    private func load() {
        let k = PhotoThumbCache.key(asset.localIdentifier, Int(edge))
        if let hit = PhotoThumbCache.images.object(forKey: k) {
            image = hit
            return
        }
        let opts = PHImageRequestOptions()
        opts.deliveryMode = .fastFormat
        opts.resizeMode = .fast
        opts.isNetworkAccessAllowed = false
        PhotoThumbCache.mgr.requestImage(
            for: asset,
            targetSize: CGSize(width: edge, height: edge),
            contentMode: .aspectFill,
            options: opts
        ) { img, info in
            guard let img else { return }
            let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
            if !degraded { PhotoThumbCache.images.setObject(img, forKey: k) }
            DispatchQueue.main.async { image = img }
        }
    }
}

func fetchRecentThumbs(limit: Int, done: @escaping ([RecentThumb]) -> Void) {
    PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
        guard status == .authorized || status == .limited else {
            DispatchQueue.main.async { done([]) }
            return
        }
        let opts = PHFetchOptions()
        opts.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        opts.fetchLimit = limit
        let result = PHAsset.fetchAssets(with: .image, options: opts)
        let mgr = PHImageManager.default()
        var out: [RecentThumb] = []
        let lock = NSLock()
        let group = DispatchGroup()
        result.enumerateObjects { asset, _, _ in
            group.enter()
            mgr.requestImage(for: asset, targetSize: CGSize(width: 240, height: 240), contentMode: .aspectFill, options: nil) { image, _ in
                if let image {
                    lock.lock()
                    out.append(RecentThumb(id: asset.localIdentifier, image: image, asset: asset))
                    lock.unlock()
                }
                group.leave()
            }
        }
        group.notify(queue: .main) { done(out) }
    }
}

func exportPhotoAsset(_ asset: PHAsset, done: @escaping (URL) -> Void) {
    let opts = PHImageRequestOptions()
    opts.deliveryMode = .highQualityFormat
    opts.isNetworkAccessAllowed = false
    PHImageManager.default().requestImageDataAndOrientation(for: asset, options: opts) { data, _, _, _ in
        guard let data else { return }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("cap_\(UUID().uuidString).jpg")
        try? data.write(to: url, options: .atomic)
        DispatchQueue.main.async { done(url) }
    }
}

struct LocalThumb: View {
    let url: URL?
    let fallback: UInt32
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color(rgb: fallback).opacity(0.35)
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .task(id: url) {
            guard let url, image == nil else { return }
            // Attachments that came over the wire are https URLs; the shared
            // image loader has the disk cache and the off-thread decode.
            if url.scheme?.hasPrefix("http") == true {
                if let hit = ImageLoader.shared.cached(url) { image = hit; return }
                let loaded = await ImageLoader.shared.load(url)
                if !Task.isCancelled { image = loaded }
                return
            }
            let path = url.path
            let decoded = await Task.detached(priority: .userInitiated) { UIImage(contentsOfFile: path) }.value
            if !Task.isCancelled { image = decoded }
        }
    }
}

struct DeviceGalleryView: View {
    var onPick: (URL) -> Void
    var onClose: () -> Void
    var confirmPick: Bool = false
    @Environment(\.uiScale) private var scale
    @State private var recents: [RecentThumb] = []
    @State private var allowed = false
    @State private var selected: String?
    @State private var assets: [AssetRef] = []

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onClose) { ChatIco.close(Color(rgb: 0x111111), side: 16 * scale) }
                    .frame(width: 36 * scale, height: 36 * scale)
                AppText(Copy.t(.inboxGallery), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                Spacer()
                if confirmPick, selected != nil {
                    Button {
                        if let id = selected, let item = assets.first(where: { $0.id == id }) {
                            exportAsset(item.asset)
                        }
                    } label: {
                        Circle().fill(Dark.primary).frame(width: 36 * scale, height: 36 * scale)
                            .overlay(ChatIco.tick(.white, side: 14 * scale))
                    }
                }
            }
            .padding(12 * scale)
            if assets.isEmpty && !allowed {
                Button(action: requestPhotos) {
                    AppText(Copy.t(.inboxAllowPhotos), style: Type.labelBold, color: Dark.primary)
                        .padding(12 * scale)
                        .background(Color(rgb: 0xF3E8FF))
                        .clipShape(Capsule())
                }
                .padding(.top, 40 * scale)
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4 * scale), count: 3), spacing: 4 * scale) {
                        ForEach(assets) { item in
                            Button {
                                if confirmPick { selected = item.id } else { exportAsset(item.asset) }
                            } label: {
                                ZStack(alignment: .topTrailing) {
                                    GalleryThumbView(asset: item.asset, edge: 180)
                                        .aspectRatio(1, contentMode: .fill)
                                        .clipped()
                                    if confirmPick && selected == item.id {
                                        Circle().fill(Dark.primary).frame(width: 22 * scale, height: 22 * scale)
                                            .overlay(ChatIco.tick(.white, side: 12 * scale))
                                            .padding(6 * scale)
                                    }
                                }
                            }
                        }
                    }
                    .padding(4 * scale)
                }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear(perform: requestPhotos)
    }

    private func requestPhotos() {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            let ok = status == .authorized || status == .limited
            DispatchQueue.main.async {
                allowed = ok
                if ok { loadRecents() }
            }
        }
    }

    private func loadRecents() {
        let opts = PHFetchOptions()
        opts.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        opts.fetchLimit = 200
        let result = PHAsset.fetchAssets(with: .image, options: opts)
        var out: [AssetRef] = []
        out.reserveCapacity(result.count)
        result.enumerateObjects { asset, _, _ in
            out.append(AssetRef(asset: asset))
        }
        DispatchQueue.main.async { assets = out }
    }

    private func exportAsset(_ asset: PHAsset) {
        let opts = PHImageRequestOptions()
        opts.deliveryMode = .highQualityFormat
        opts.isNetworkAccessAllowed = false
        PHImageManager.default().requestImageDataAndOrientation(for: asset, options: opts) { data, _, _, _ in
            guard let data else { return }
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("cap_\(UUID().uuidString).jpg")
            try? data.write(to: url, options: .atomic)
            DispatchQueue.main.async { onPick(url) }
        }
    }
}

struct ContactPickView: View {
    var onPick: ([String]) -> Void
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var people: [(id: String, name: String, number: String)] = []
    @State private var allowed = false
    @State private var searching = false
    @State private var query = ""
    @State private var picked: Set<String> = []

    var body: some View {
        let shown = query.isEmpty ? people : people.filter { $0.name.localizedCaseInsensitiveContains(query) || $0.number.contains(query) }
        VStack(spacing: 0) {
            HStack {
                Button(action: onClose) { ChatIco.close(Color(rgb: 0x111111), side: 16 * scale) }
                    .frame(width: 36 * scale, height: 36 * scale)
                if searching {
                    TextField(Copy.t(.inboxSearchShort), text: $query)
                } else {
                    AppText(Copy.t(.inboxContacts), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                    Spacer()
                }
                Button { searching.toggle() } label: {
                    ChatIco.search(Color(rgb: 0x555555), side: 18 * scale)
                }
                .frame(width: 36 * scale, height: 36 * scale)
                if !picked.isEmpty {
                    Button { onPick(people.filter { picked.contains($0.id) }.map(\.name)) } label: {
                        Circle().fill(Dark.primary).frame(width: 36 * scale, height: 36 * scale)
                            .overlay(ChatIco.tick(.white, side: 14 * scale))
                    }
                }
            }
            .padding(12 * scale)
            if !allowed {
                Button(action: requestContacts) {
                    AppText(Copy.t(.inboxAllowContacts), style: Type.labelBold, color: Dark.primary)
                        .padding(12 * scale)
                        .background(Color(rgb: 0xF3E8FF))
                        .clipShape(Capsule())
                }
                .padding(.top, 40 * scale)
                Spacer()
            } else {
                List(shown, id: \.id) { c in
                    Button {
                        if picked.contains(c.id) { picked.remove(c.id) } else { picked.insert(c.id) }
                    } label: {
                        HStack(spacing: 12 * scale) {
                            Circle().fill(Dark.primary.opacity(0.18)).frame(width: 44 * scale, height: 44 * scale)
                                .overlay(AppText(String(c.name.prefix(1)).uppercased(), style: Type.labelBold, color: Dark.primary, sizeOverride: 16))
                            VStack(alignment: .leading, spacing: 2) {
                                AppText(c.name, style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 14)
                                if !c.number.isEmpty {
                                    AppText(c.number, style: Type.caption, color: Color(rgb: 0x888888), sizeOverride: 12)
                                }
                            }
                            Spacer()
                            if picked.contains(c.id) {
                                Circle().fill(Dark.primary).frame(width: 22 * scale, height: 22 * scale)
                                    .overlay(ChatIco.tick(.white, side: 12 * scale))
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear(perform: requestContacts)
    }

    private func requestContacts() {
        let store = CNContactStore()
        store.requestAccess(for: .contacts) { ok, _ in
            DispatchQueue.main.async {
                allowed = ok
                if ok { load(store) }
            }
        }
    }

    private func load(_ store: CNContactStore) {
        DispatchQueue.global(qos: .userInitiated).async {
            let keys = [CNContactGivenNameKey, CNContactFamilyNameKey, CNContactPhoneNumbersKey] as [CNKeyDescriptor]
            let req = CNContactFetchRequest(keysToFetch: keys)
            req.sortOrder = .givenName
            var out: [(id: String, name: String, number: String)] = []
            try? store.enumerateContacts(with: req) { contact, _ in
                let name = "\(contact.givenName) \(contact.familyName)".trimmingCharacters(in: .whitespaces)
                let number = contact.phoneNumbers.first?.value.stringValue ?? ""
                if !name.isEmpty || !number.isEmpty {
                    out.append((contact.identifier, name.isEmpty ? number : name, number))
                }
            }
            DispatchQueue.main.async { people = out }
        }
    }
}
