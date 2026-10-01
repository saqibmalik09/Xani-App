import AVFoundation
import Foundation

/// Gift sound effects: one `AVAudioPlayer` at a time from a disk-cached file,
/// prepared off the main thread. A sound not on disk yet plays silently rather
/// than late — the animation never waits on audio, and audio never arrives
/// after the gift has left the screen.
@MainActor
final class GiftSound {
    static let shared = GiftSound()
    private var player: AVAudioPlayer?
    private var generation = 0
    var muted = false

    private init() {}

    // Sound URLs arrive as bare storage keys like the animations: resolve to
    // an absolute URL before the cache sees them, or the download 404s.
    func prefetch(_ url: String) { AssetCache.shared.prefetch(MediaBase.resolve(url)) }

    func play(_ rawUrl: String, maxMs: Int) {
        guard !rawUrl.isEmpty, !muted else { return }
        let url = MediaBase.resolve(rawUrl)
        guard let file = AssetCache.shared.cached(url) else { AssetCache.shared.prefetch(url); return }
        generation += 1
        let gen = generation
        player?.stop()
        Task.detached(priority: .userInitiated) { [file] in
            guard let p = try? AVAudioPlayer(contentsOf: file) else { return }
            p.prepareToPlay()
            await MainActor.run {
                guard gen == GiftSound.shared.generation else { return }
                GiftSound.shared.player = p
                p.play()
            }
        }
        if maxMs > 0 {
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: UInt64(maxMs + 400) * 1_000_000)
                if gen == self.generation { self.stop() }
            }
        }
    }

    func stop() {
        generation += 1
        player?.stop()
        player = nil
    }
}
