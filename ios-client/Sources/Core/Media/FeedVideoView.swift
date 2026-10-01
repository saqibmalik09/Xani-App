import AVFoundation
import SwiftUI
import UIKit

/// One looping feed clip on an `AVPlayerLayer`.
///
/// The item is created as soon as the page is composed — the pager keeps one
/// neighbour alive, so the next clip has buffered before the swipe lands — and
/// plays only while `playing` is true. The poster underneath stays visible until
/// the layer reports it is ready for display, so there is never a black flash or
/// a spinner. Playback failures are swallowed: the poster stays, nothing crashes.
struct FeedVideoView: UIViewRepresentable {
    let url: URL
    let playing: Bool
    let muted: Bool
    var fill: Bool = true
    /// Playback position and clip length, reported while this clip plays.
    var onProgress: ((Double, Double) -> Void)?

    func makeUIView(context: Context) -> PlayerView {
        let v = PlayerView()
        v.onProgress = onProgress
        v.load(url)
        return v
    }

    func updateUIView(_ v: PlayerView, context: Context) {
        v.onProgress = onProgress
        if v.url != url { v.load(url) }
        v.layer.contentsGravity = fill ? .resizeAspectFill : .resizeAspect
        v.playerLayer.videoGravity = fill ? .resizeAspectFill : .resizeAspect
        v.set(playing: playing, muted: muted)
    }

    static func dismantleUIView(_ v: PlayerView, coordinator: ()) {
        v.tear()
    }

    final class PlayerView: UIView {
        private(set) var url: URL?
        private var player: AVQueuePlayer?
        private var looper: AVPlayerLooper?
        private var readyObs: NSKeyValueObservation?
        private var timeObs: Any?
        var onProgress: ((Double, Double) -> Void)?

        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            playerLayer.videoGravity = .resizeAspectFill
            alpha = 0
        }

        required init?(coder: NSCoder) { nil }

        func load(_ url: URL) {
            tear()
            self.url = url
            let asset = AVURLAsset(url: url)
            let item = AVPlayerItem(asset: asset)
            item.preferredForwardBufferDuration = 3
            let p = AVQueuePlayer()
            p.automaticallyWaitsToMinimizeStalling = true
            p.actionAtItemEnd = .none
            looper = AVPlayerLooper(player: p, templateItem: item)
            player = p
            playerLayer.player = p
            readyObs = playerLayer.observe(\.isReadyForDisplay, options: [.initial, .new]) { [weak self] layer, _ in
                guard layer.isReadyForDisplay, let self else { return }
                DispatchQueue.main.async {
                    UIView.animate(withDuration: 0.12) { self.alpha = 1 }
                }
            }
            // Twice a second is fine enough to spot a 20% threshold on the
            // shortest clip the product allows, and AVFoundation only fires it
            // while the player is actually running.
            timeObs = p.addPeriodicTimeObserver(
                forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
                queue: .main
            ) { [weak self] time in
                guard let self, let item = self.player?.currentItem else { return }
                let duration = item.duration.seconds
                guard duration.isFinite, duration > 0 else { return }
                self.onProgress?(time.seconds, duration)
            }
        }

        func set(playing: Bool, muted: Bool) {
            guard let player else { return }
            player.isMuted = muted
            if playing {
                if player.timeControlStatus != .playing { player.play() }
            } else if player.timeControlStatus != .paused {
                player.pause()
            }
        }

        func tear() {
            readyObs = nil
            if let timeObs { player?.removeTimeObserver(timeObs) }
            timeObs = nil
            player?.pause()
            looper?.disableLooping()
            looper = nil
            playerLayer.player = nil
            player = nil
            url = nil
            alpha = 0
        }
    }
}
