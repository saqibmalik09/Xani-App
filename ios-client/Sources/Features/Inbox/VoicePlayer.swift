import AVFoundation
import Foundation

/// One voice-note player for the whole app. Tap a bubble to play; tap again
/// (or another bubble) to stop. Downloads go through AssetCache.
@MainActor
final class VoicePlayer: ObservableObject {
    static let shared = VoicePlayer()

    @Published private(set) var playingId: Int64 = 0

    private var player: AVAudioPlayer?
    private var generation = 0

    private init() {}

    func toggle(msgId: Int64, url: URL?) {
        guard let url else { return }
        if playingId == msgId {
            stop()
            return
        }
        play(msgId: msgId, url: url)
    }

    func play(msgId: Int64, url: URL) {
        stop()
        generation += 1
        let gen = generation
        playingId = msgId

        if url.isFileURL {
            start(file: url, gen: gen, msgId: msgId)
            return
        }

        let key = url.absoluteString
        Task {
            let file = await AssetCache.shared.load(key)
            guard gen == self.generation else { return }
            guard let file else {
                self.playingId = 0
                return
            }
            self.start(file: file, gen: gen, msgId: msgId)
        }
    }

    func stop() {
        generation += 1
        playingId = 0
        player?.stop()
        player = nil
    }

    private func start(file: URL, gen: Int, msgId: Int64) {
        Task.detached(priority: .userInitiated) {
            guard let p = try? AVAudioPlayer(contentsOf: file) else {
                await MainActor.run {
                    if gen == VoicePlayer.shared.generation { VoicePlayer.shared.playingId = 0 }
                }
                return
            }
            p.prepareToPlay()
            await MainActor.run {
                guard gen == VoicePlayer.shared.generation else { return }
                p.delegate = VoicePlayerDelegate.shared
                VoicePlayer.shared.player = p
                VoicePlayer.shared.playingId = msgId
                p.play()
            }
        }
    }
}

private final class VoicePlayerDelegate: NSObject, AVAudioPlayerDelegate {
    static let shared = VoicePlayerDelegate()
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            if VoicePlayer.shared.playingId != 0 { VoicePlayer.shared.stop() }
        }
    }
}
