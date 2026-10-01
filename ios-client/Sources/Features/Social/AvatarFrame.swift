import SwiftUI

/// One place that knows what frame each user wears right now, learned from any
/// payload that carries `frame_url` (live people, gift events, profiles). Every
/// avatar on screen reads it, so a frame bought mid-session rings every surface
/// at once without each list refetching.
@MainActor
final class FrameStore: ObservableObject {
    static let shared = FrameStore()

    struct Frame: Equatable {
        var url = ""
        var mime = ""
    }

    @Published private(set) var frames: [Int64: Frame] = [:]

    private init() {}

    /// Records what a payload said this user wears. An empty url is knowledge
    /// too — "wearing none" — and clears a stale ring.
    func learn(_ id: Int64, url: String, mime: String) {
        guard id != 0 else { return }
        let next = Frame(url: url, mime: mime)
        if frames[id] != next { frames[id] = next }
    }

    func signedOut() { frames = [:] }
}

/// The decorative ring around an avatar. Drawn larger than the face on purpose
/// (frames are authored with art that overflows the circle) and never clipped
/// by the avatar's own shape. SVGA frames loop forever; anything else renders
/// as a still image. Sits inside the avatar's layout slot without growing it.
struct AvatarFrame: View {
    let url: String
    let mime: String
    let size: CGFloat

    /// How far past the face the ring reaches — matches Android so a frame
    /// looks identical on both platforms.
    private static let frameScale: CGFloat = 1.34

    var body: some View {
        let resolved = MediaBase.resolve(url)
        let side = size * Self.frameScale
        Group {
            if mime.contains("svga") || resolved.lowercased().hasSuffix(".svga") {
                SvgaLoopView(url: resolved)
            } else if let u = URL(string: resolved) {
                RemoteImage(url: u, contentMode: .fit, fallback: .clear)
            }
        }
        .frame(width: side, height: side)
        .allowsHitTesting(false)
    }
}

/// An SVGA movie looping forever at avatar size (loops <= 0 never completes).
private struct SvgaLoopView: UIViewRepresentable {
    let url: String

    func makeUIView(context: Context) -> SvgaPlayerView {
        let v = SvgaPlayerView(frame: .zero)
        context.coordinator.url = url
        load(into: v)
        return v
    }

    func updateUIView(_ v: SvgaPlayerView, context: Context) {
        guard context.coordinator.url != url else { return }
        context.coordinator.url = url
        load(into: v)
    }

    static func dismantleUIView(_ uiView: SvgaPlayerView, coordinator: Holder) { uiView.stop() }

    func makeCoordinator() -> Holder { Holder() }

    private func load(into v: SvgaPlayerView) {
        let holder = url
        Task { @MainActor [weak v] in
            guard let m = await SvgaLoader.shared.load(holder), let v else { return }
            v.play(m, loops: 0)
        }
    }

    final class Holder { var url = "" }
}
