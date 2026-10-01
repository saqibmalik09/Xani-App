import AVFoundation
import AudioToolbox
import UIKit

/// Owns the audio session for one call: category, routing, the incoming ring
/// and the outgoing ringback. Every step is best-effort — a device with no
/// vibration motor or a busy session cannot take the call down with it.
@MainActor
final class CallAudio {
    private let session = AVAudioSession.sharedInstance()
    private var ringTimer: Timer?
    private var ringbackTimer: Timer?
    private var inCall = false

    func beginCall(speaker: Bool) {
        if !inCall {
            inCall = true
            try? session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetooth, .allowBluetoothA2DP])
            try? session.setActive(true)
        }
        setSpeaker(speaker)
    }

    func setSpeaker(_ on: Bool) {
        try? session.overrideOutputAudioPort(on ? .speaker : .none)
    }

    func startRingtone() {
        guard ringTimer == nil else { return }
        try? session.setCategory(.playback, mode: .default, options: [])
        try? session.setActive(true)
        let tick: () -> Void = {
            AudioServicesPlaySystemSound(1007) // system ring cadence tone
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        }
        tick()
        ringTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { _ in tick() }
    }

    func stopRingtone() {
        ringTimer?.invalidate()
        ringTimer = nil
    }

    func startRingback() {
        guard ringbackTimer == nil else { return }
        let tick: () -> Void = { AudioServicesPlaySystemSound(1075) }
        tick()
        ringbackTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in tick() }
    }

    func stopRingback() {
        ringbackTimer?.invalidate()
        ringbackTimer = nil
    }

    func endCall() {
        stopRingtone()
        stopRingback()
        guard inCall else {
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
            return
        }
        inCall = false
        try? session.overrideOutputAudioPort(.none)
        try? session.setActive(false, options: .notifyOthersOnDeactivation)
    }
}
