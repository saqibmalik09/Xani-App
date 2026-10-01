import SwiftUI

/// Capture / pick → watch it back → Next → details form. The file is shown at
/// its own aspect, fit inside the screen, never cropped, so what the user sees
/// is what will be posted. Video loops through the same player as the feed.
struct MediaPreviewView: View {
    let url: URL
    var onBack: () -> Void
    var onNext: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var info: MediaInfo?
    @State private var muted = false
    @State private var still: UIImage?

    private var isVideo: Bool {
        if let info { return info.isVideo }
        return ["mp4", "mov", "m4v", "webm", "3gp"].contains(url.pathExtension.lowercased())
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            GeometryReader { geo in
                let aspect = (info?.aspect ?? 0) > 0 ? CGFloat(info!.aspect) : 9 / 16
                let box = fit(aspect, in: geo.size)
                ZStack {
                    if isVideo {
                        FeedVideoView(url: url, playing: true, muted: muted, fill: false)
                    } else if let still {
                        Image(uiImage: still).resizable().aspectRatio(contentMode: .fit)
                    }
                }
                .frame(width: box.width, height: box.height)
                .clipped()
                .position(x: geo.size.width / 2, y: geo.size.height / 2)
            }
            .ignoresSafeArea()

            VStack {
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18 * scale, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .background(Color.black.opacity(0.35))
                            .clipShape(Circle())
                    }
                    .buttonStyle(PressScaleStyle())
                    Spacer()
                    if isVideo {
                        Button(action: { muted.toggle() }) {
                            Image(systemName: muted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                                .font(.system(size: 16 * scale, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(width: 44, height: 44)
                                .background(Color.black.opacity(0.35))
                                .clipShape(Circle())
                        }
                        .buttonStyle(PressScaleStyle())
                    }
                }
                .padding(.horizontal, 12 * scale)
                .padding(.top, insets.top + 6 * scale)

                Spacer()

                HStack {
                    if let info, info.isVideo, info.durationMs > 0 {
                        AppText(String(format: "%d:%02d", info.durationMs / 60000, (info.durationMs / 1000) % 60),
                                style: Type.labelBold, color: Color.white.opacity(0.8), sizeOverride: 13)
                    }
                    Spacer()
                    Button(action: onNext) {
                        HStack(spacing: 6 * scale) {
                            AppText(Copy.t(.createNext), style: Type.labelBold, color: .white, sizeOverride: 15)
                            Image(systemName: "chevron.right").font(.system(size: 13 * scale, weight: .bold)).foregroundColor(.white)
                        }
                        .padding(.horizontal, 22 * scale)
                        .padding(.vertical, 12 * scale)
                        .background(Capsule().fill(Color(rgb: 0xFE2C55)))
                    }
                    .buttonStyle(PressScaleStyle())
                }
                .padding(.horizontal, 18 * scale)
                .padding(.bottom, insets.bottom + 18 * scale)
            }
        }
        .task(id: url) {
            // Container facts only — no frame decode on this hop.
            info = await MediaProbe.probe(url)
            if !(info?.isVideo ?? isVideo) {
                // Decode off-main, once, sized for the screen.
                let edge = Int(UIScreen.main.bounds.height * UIScreen.main.scale)
                still = await Task.detached(priority: .userInitiated) { Self.decode(url, maxEdge: edge) }.value
            }
        }
    }

    /// Largest box of this aspect inside `size`: fit, never crop.
    private func fit(_ aspect: CGFloat, in size: CGSize) -> CGSize {
        let byHeight = CGSize(width: size.height * aspect, height: size.height)
        return byHeight.width <= size.width ? byHeight : CGSize(width: size.width, height: size.width / aspect)
    }

    private static func decode(_ url: URL, maxEdge: Int) -> UIImage? {
        guard let src = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxEdge,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        return UIImage(cgImage: cg)
    }
}
