import AVFoundation
import Foundation
import UIKit

/// One call at a time, app-wide. Owns the signalling state machine, the media
/// session, audio routing and the reconnect policy; views only render `state`
/// and call the verbs below. Engine callbacks hop to the main actor, so state
/// changes are sequential and SwiftUI never sees a half-applied transition.
///
/// Reconnect contract (matches the server): media may drop for up to the ICE
/// grace window (default 150 s) while we restart ICE with backoff and show
/// "Reconnecting…". Only End, the remote End, or grace expiry finishes the call.
@MainActor
final class CallController: ObservableObject {
    static let shared = CallController()

    @Published private(set) var state: CallUi?

    /// False when this build shipped without the media framework.
    var available: Bool { RtcEngineFactory.available }

    private let audio = CallAudio()
    private var engine: RtcEngine?
    private var attached = false

    // Per-call session; all reset by teardown().
    private var peer: RtcPeer?
    private var room: RtcRoom?
    private var ice = IceConfigData.empty
    private var peerUser: Int64 = 0
    private var peerDevice: Int64 = 0
    private var remoteSet = false
    private var pendingOffer: (String, String)?
    private var pendingLocalCandidates: [[String: Any]] = []
    private var pendingRemoteCandidates: [[String: Any]] = []
    private var pendingSignals: [CallEvent] = []
    private var outbound: [Outbound] = []
    private var sender: Task<Void, Never>?
    private var hold: Task<Void, Never>?
    private var ringTimer: Task<Void, Never>?
    private var graceTimer: Task<Void, Never>?
    private var restartTimer: Task<Void, Never>?
    private var clearTimer: Task<Void, Never>?
    private var restarts = 0
    private var session = 0

    private struct Outbound { let callId: Int64; let toUser: Int64; let toDevice: Int64; let type: String; let payload: [String: Any] }

    private init() {}

    func attach() {
        guard !attached else { return }
        attached = true
        LiveFeed.shared.listenCall { [weak self] ev in self?.onEvent(ev) }
        // The stream came back after a gap: ask the server whether the call survived.
        LiveFeed.shared.listenConnected { [weak self] in self?.resync() }
    }

    // MARK: - verbs

    /// Dial. No-op when a call is already up: the UI shows that call instead.
    func start(conversationId: Int64, chatId: Int?, peer: CallPeer, video: Bool) {
        if state?.active == true { return }
        session += 1
        let mine = session
        state = CallUi(callId: 0, conversationId: conversationId, chatId: chatId, outgoing: true, video: video,
                       phase: .outgoing, peer: peer, speaker: video)
        beginSession()
        guard available else { finish(.unavailable); return }
        guard hasMedia(video: video) else { finish(.failed); return }
        let clientId = UUID().uuidString
        Task {
            let r = await Xani.calls.start(conversationId: conversationId, kind: video ? "video" : "audio", clientId: clientId, relayOnly: false)
            guard mine == session else { return }
            guard r.ok, let data = r.value else { finish(outcome(for: r)); return }
            ice = data.ice
            update { $0.callId = data.call.id; $0.mode = data.call.mode; $0.participants = data.call.joined }
            startRingTimer()
            if let room = data.room {
                joinRoom(room, publish: true)
            } else {
                // Gather ICE while it rings so the offer is on the wire the instant they accept.
                guard let p = createPeer(video: video) else { finish(.failed); return }
                peerUser = peer.id
                p.offer(iceRestart: false)
            }
        }
    }

    func accept() {
        guard let ui = state, ui.phase == .incoming else { return }
        let mine = session
        audio.stopRingtone()
        ringTimer?.cancel()
        guard available else { finish(.unavailable); return }
        guard hasMedia(video: ui.video) else { finish(.failed); return }
        update { $0.phase = .connecting }
        audio.beginCall(speaker: ui.speaker)
        Task {
            let r = await Xani.calls.accept(ui.callId, relayOnly: false)
            guard mine == session else { return }
            guard r.ok, let data = r.value else { finish(r.statusCode == 410 ? .missed : outcome(for: r)); return }
            ice = data.ice
            update { $0.mode = data.call.mode; $0.participants = data.call.joined }
            if let room = data.room {
                joinRoom(room, publish: true)
            } else {
                guard createPeer(video: ui.video) != nil else { finish(.failed); return }
                // The caller's offer may have raced the accept response; replay it.
                let queued = pendingSignals
                pendingSignals.removeAll()
                queued.forEach(onSignal)
            }
        }
    }

    func reject() {
        guard let ui = state, ui.phase == .incoming else { return }
        let id = ui.callId
        clear()
        Task { await Xani.calls.reject(id, reason: "declined") }
    }

    /// End from our side, whatever the phase. Never waits on the network.
    func end() {
        guard let ui = state, ui.active else { return }
        let id = ui.callId
        let reason = ui.phase == .outgoing ? "cancelled" : "hangup"
        finish(.ended)
        if id != 0 { Task { await Xani.calls.end(id, reason: reason) } }
    }

    func toggleMute() {
        guard let ui = state else { return }
        let muted = !ui.muted
        update { $0.muted = muted }
        peer?.setMuted(muted)
        room?.setMuted(muted)
        sendSignal("mute", ["muted": muted])
    }

    func toggleSpeaker() {
        guard let ui = state else { return }
        let on = !ui.speaker
        update { $0.speaker = on }
        audio.setSpeaker(on)
    }

    func toggleCamera() {
        guard let ui = state else { return }
        let off = !ui.cameraOff
        if !off, AVCaptureDevice.authorizationStatus(for: .video) != .authorized { return }
        update { $0.cameraOff = off; if !$0.video { $0.video = true; $0.speaker = true } }
        if !ui.video { audio.setSpeaker(true) }
        peer?.setVideoEnabled(!off)
        room?.setVideoEnabled(!off)
        sendSignal("camera", ["off": off])
    }

    /// Audio call → video: enables the camera track, which renegotiates on the wire.
    func upgradeToVideo() {
        guard let ui = state, !(ui.video && !ui.cameraOff) else { return }
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else { return }
        update { $0.video = true; $0.cameraOff = false; $0.speaker = true }
        audio.setSpeaker(true)
        peer?.setVideoEnabled(true)
        room?.setVideoEnabled(true)
        sendSignal("camera", ["off": false])
    }

    func switchCamera() {
        update { $0.frontCamera.toggle() }
        peer?.switchCamera()
        room?.switchCamera()
    }

    func setMinimized(_ minimized: Bool) { update { $0.minimized = minimized } }

    /// Pull another member in; the server converts a 1:1 into a room when needed.
    func addParticipant(_ userId: Int64) {
        guard let ui = state, ui.inMedia, ui.callId != 0 else { return }
        let mine = session
        Task {
            let r = await Xani.calls.add(ui.callId, userId: userId)
            guard mine == session, r.ok else { return }
            if let room = r.value ?? nil { migrateToRoom(room) }
        }
    }

    func localView() -> UIView? { peer?.localView() ?? room?.localView() }

    func remoteView() -> UIView? {
        if let v = peer?.remoteView() { return v }
        guard let first = state?.participants.first else { return nil }
        return room?.remoteView(userId: first)
    }

    // MARK: - inbound events

    private func onEvent(_ e: CallEvent) {
        let ui = state
        switch e {
        case .invite(let call):
            onInvite(call)
        case .ringing(let callId, _):
            if let ui, ui.callId == callId, ui.phase == .outgoing, !ui.ringing {
                update { $0.ringing = true }
                audio.startRingback()
            }
        case .accept(let callId, let userId, let deviceId):
            if let ui, ui.callId == callId, ui.outgoing, ui.phase == .outgoing {
                ringTimer?.cancel()
                audio.stopRingback()
                audio.beginCall(speaker: ui.speaker)
                peerUser = userId
                peerDevice = deviceId
                update { $0.phase = .connecting; if !$0.participants.contains(userId) { $0.participants.append(userId) } }
                flushLocal()
            }
        case .reject(let callId, _, let reason):
            if let ui, ui.callId == callId, ui.phase == .outgoing { finish(reason == "busy" ? .busy : .declined) }
        case .taken(let callId, _):
            if let ui, ui.callId == callId, ui.phase == .incoming { clear() }
        case .end(let callId, let reason):
            if let ui, ui.callId == callId { finish(outcome(for: reason, ui: ui)) }
        case .signal(let callId, _, _, _, _, _):
            if let ui, ui.callId == callId { onSignal(e) }
        case .participant(let callId, let userId, let st):
            if let ui, ui.callId == callId {
                update {
                    if st == "joined" { if !$0.participants.contains(userId) { $0.participants.append(userId) } }
                    else { $0.participants.removeAll { $0 == userId } }
                }
            }
        case .room(let join):
            if let ui, ui.inMedia { migrateToRoom(join) }
        case .liveViewers, .liveGuest, .liveEnded:
            break // owned by the live feature
        }
    }

    private func onInvite(_ call: CallData) {
        if call.callerId == Xani.currentUserId { return }
        if state?.active == true {
            Task { await Xani.calls.reject(call.id, reason: "busy") }
            return
        }
        session += 1
        let display = call.caller.fullName.isEmpty ? call.caller.username : call.caller.fullName
        let peer = CallPeer(id: call.callerId, name: display.isEmpty ? "…" : display, initials: Self.initials(display),
                            color: 0x6D5DF6, avatarUrl: call.caller.avatarUrl)
        let video = call.kind == "video"
        state = CallUi(callId: call.id, conversationId: call.conversationId, chatId: nil, outgoing: false, video: video,
                       phase: .incoming, peer: peer, mode: call.mode, speaker: video, participants: call.joined)
        beginSession()
        peerUser = call.callerId
        audio.startRingtone()
        startRingTimer()
        Task { await Xani.calls.ring(call.id) }
    }

    private func onSignal(_ e: CallEvent) {
        guard case .signal(_, _, let fromDevice, _, let type, let payload) = e, let ui = state else { return }
        switch type {
        case "mute": update { $0.remoteMuted = payload["muted"] as? Bool ?? false }; return
        case "camera": update { $0.remoteVideo = !(payload["off"] as? Bool ?? false) }; return
        default: break
        }
        guard let p = peer else {
            // Callee: accept response not back yet, or room mode where no peer exists.
            if ui.phase == .connecting, ui.mode == "p2p", pendingSignals.count < 512 { pendingSignals.append(e) }
            return
        }
        if fromDevice != 0 { peerDevice = fromDevice }
        switch type {
        case "offer", "answer":
            guard let sdp = payload["sdp"] as? String, !sdp.isEmpty else { return }
            remoteSet = true
            p.setRemote(type: type, sdp: sdp)
            pendingRemoteCandidates.forEach { p.addCandidate($0) }
            pendingRemoteCandidates.removeAll()
        case "ice":
            if remoteSet { p.addCandidate(payload) } else if pendingRemoteCandidates.count < 256 { pendingRemoteCandidates.append(payload) }
        case "ice_restart":
            // Callee could not reach us; we own the offer, so we restart.
            if ui.outgoing, ui.inMedia { p.offer(iceRestart: true) }
        default:
            break
        }
    }

    // MARK: - media session

    private func createPeer(video: Bool) -> RtcPeer? {
        guard let eng = engine ?? RtcEngineFactory.create() else { return nil }
        engine = eng
        let p = eng.createPeer(ice: ice.servers, relayOnly: ice.relayOnly, video: video, listener: self)
        peer = p
        p.setMuted(state?.muted == true)
        return p
    }

    private func joinRoom(_ join: RoomJoinData, publish: Bool) {
        guard let eng = engine ?? RtcEngineFactory.create() else { finish(.unavailable); return }
        engine = eng
        guard let ui = state else { return }
        let r = eng.joinRoom(url: join.url, token: join.token, video: ui.video && !ui.cameraOff, publish: publish, listener: self)
        room = r
        r.setMuted(ui.muted)
        update { $0.mode = "sfu"; if $0.phase != .outgoing { $0.phase = .connecting } }
    }

    private func migrateToRoom(_ join: RoomJoinData) {
        guard let ui = state else { return }
        if ui.mode == "sfu", room != nil { return }
        peer?.close()
        peer = nil
        remoteSet = false
        joinRoom(join, publish: true)
    }

    private func onLink(_ link: RtcLink) {
        guard let ui = state, ui.active else { return }
        switch link {
        case .connected:
            graceTimer?.cancel(); graceTimer = nil
            restartTimer?.cancel(); restartTimer = nil
            restarts = 0
            update { $0.phase = .connected; if $0.connectedAt == 0 { $0.connectedAt = ProcessInfo.processInfo.systemUptime } }
        case .disconnected, .failed:
            if ui.phase == .outgoing { return }
            update { $0.phase = .reconnecting }
            startGrace()
            scheduleRestart(immediate: link == .failed)
        case .closed:
            if ui.inMedia { finish(.lost) }
        case .new, .connecting:
            break
        }
    }

    private func startGrace() {
        guard graceTimer == nil else { return }
        let mine = session
        let grace = UInt64(min(max(ice.reconnectGraceSeconds, 30), 600))
        graceTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: grace * 1_000_000_000)
            guard let self, !Task.isCancelled, mine == self.session, self.state?.phase == .reconnecting else { return }
            let id = self.state?.callId ?? 0
            self.finish(.lost)
            if id != 0 { await Xani.calls.end(id, reason: "lost") }
        }
    }

    /// Offerer restarts ICE; answerer nudges the offerer. Backoff 1.5 s → 8 s.
    private func scheduleRestart(immediate: Bool) {
        if let t = restartTimer, !t.isCancelled { return }
        let mine = session
        let wait: UInt64 = (immediate && restarts == 0) ? 0 : UInt64(min(1_500 << min(restarts, 2), 8_000))
        restartTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: wait * 1_000_000)
            guard let self, !Task.isCancelled, mine == self.session, let ui = self.state, ui.phase == .reconnecting else { return }
            self.restarts += 1
            if ui.mode != "sfu" {
                if ui.outgoing { self.peer?.offer(iceRestart: true) } else { self.sendSignal("ice_restart", [:]) }
            }
            self.restartTimer = nil
            // Loop until Connected clears the phase or grace ends the call.
            self.scheduleRestart(immediate: false)
        }
    }

    private func resync() {
        guard let ui = state, ui.active, ui.callId != 0 else { return }
        let mine = session
        Task {
            let r = await Xani.calls.get(ui.callId)
            guard mine == session else { return }
            if r.statusCode == 410 || r.value?.state == "ended" { finish(outcome(for: r.value?.endReason ?? "lost", ui: ui)) }
        }
    }

    // MARK: - signalling out

    private func flushLocal() {
        if let (type, sdp) = pendingOffer { sendSignal(type, ["sdp": sdp]) }
        pendingOffer = nil
        pendingLocalCandidates.forEach { sendSignal("ice", $0) }
        pendingLocalCandidates.removeAll()
    }

    private func sendSignal(_ type: String, _ payload: [String: Any]) {
        guard let ui = state, ui.callId != 0, peerUser != 0, sender != nil else { return }
        // Ordered, bounded; a full queue means the network is gone and grace will decide.
        guard outbound.count < 512 else { return }
        outbound.append(Outbound(callId: ui.callId, toUser: peerUser, toDevice: peerDevice, type: type, payload: payload))
    }

    private func startSender() -> Task<Void, Never> {
        Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                guard !self.outbound.isEmpty else {
                    try? await Task.sleep(nanoseconds: 15_000_000)
                    continue
                }
                let o = self.outbound.removeFirst()
                var r = await Xani.calls.signal(o.callId, toUser: o.toUser, toDevice: o.toDevice, type: o.type, payload: o.payload)
                if !r.ok, r.isTransport {
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    r = await Xani.calls.signal(o.callId, toUser: o.toUser, toDevice: o.toDevice, type: o.type, payload: o.payload)
                }
                if !r.ok, r.statusCode == 410 || r.code == "not_active", self.state?.callId == o.callId {
                    self.finish(.ended)
                }
            }
        }
    }

    // MARK: - lifecycle

    private func beginSession() {
        outbound.removeAll()
        sender = startSender()
        hold = Task { await LiveFeed.shared.hold() }
    }

    private func startRingTimer() {
        ringTimer?.cancel()
        let mine = session
        ringTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 60_000_000_000)
            guard let self, !Task.isCancelled, mine == self.session, let ui = self.state else { return }
            switch ui.phase {
            case .outgoing:
                self.finish(.noAnswer)
                if ui.callId != 0 { await Xani.calls.end(ui.callId, reason: "no_answer") }
            case .incoming:
                self.clear()
            default:
                break
            }
        }
    }

    /// Terminal transition: tear media down now, show the outcome briefly, then clear.
    private func finish(_ outcome: CallOutcome) {
        guard let ui = state, ui.active else { return }
        // A ring that ends before we answered is a missed call, not a screen to linger on.
        if ui.phase == .incoming, outcome != .unavailable, outcome != .failed { clear(); return }
        teardown()
        var ended = ui
        ended.phase = .ended; ended.outcome = outcome; ended.minimized = false
        state = ended
        clearTimer = Task { [weak self] in
            try? await Task.sleep(nanoseconds: outcome == .ended ? 1_200_000_000 : 1_800_000_000)
            guard let self, !Task.isCancelled, self.state?.phase == .ended else { return }
            self.state = nil
        }
    }

    /// Silent clear (missed, taken by another device, declined by us).
    private func clear() {
        teardown()
        state = nil
    }

    private func teardown() {
        session += 1
        ringTimer?.cancel(); ringTimer = nil
        graceTimer?.cancel(); graceTimer = nil
        restartTimer?.cancel(); restartTimer = nil
        clearTimer?.cancel(); clearTimer = nil
        sender?.cancel(); sender = nil
        outbound.removeAll()
        hold?.cancel(); hold = nil
        peer?.close(); peer = nil
        room?.close(); room = nil
        remoteSet = false
        pendingOffer = nil
        pendingLocalCandidates.removeAll()
        pendingRemoteCandidates.removeAll()
        pendingSignals.removeAll()
        peerUser = 0
        peerDevice = 0
        restarts = 0
        ice = .empty
        audio.endCall()
    }

    // MARK: - helpers

    private func update(_ f: (inout CallUi) -> Void) {
        guard var s = state else { return }
        f(&s)
        state = s
    }

    private func hasMedia(video: Bool) -> Bool {
        AVCaptureDevice.authorizationStatus(for: .audio) == .authorized &&
            (!video || AVCaptureDevice.authorizationStatus(for: .video) == .authorized)
    }

    private func outcome<T>(for r: ApiResponse<T>) -> CallOutcome {
        switch r.code {
        case "busy", "peer_busy", "calls_busy": return .busy
        case "sfu_unavailable": return .unavailable
        default: return .failed
        }
    }

    private func outcome(for reason: String, ui: CallUi) -> CallOutcome {
        switch reason {
        case "hangup", "max_length": return ui.phase == .incoming ? .missed : .ended
        case "cancelled": return .missed
        case "declined": return .declined
        case "busy": return .busy
        case "no_answer": return ui.outgoing ? .noAnswer : .missed
        case "lost": return .lost
        default: return .failed
        }
    }

    private static func initials(_ name: String) -> String {
        let parts = name.split(whereSeparator: \.isWhitespace).prefix(2)
        let s = parts.compactMap { $0.first.map { String($0).uppercased() } }.joined()
        return s.isEmpty ? "?" : s
    }
}

// Engine callbacks may arrive on media threads; hop to the main actor and drop
// anything from a session that has since been torn down.
extension CallController: RtcPeerListener, RtcRoomListener {
    nonisolated func peer(_ peer: RtcPeer, localDescription type: String, sdp: String) {
        Task { @MainActor in
            guard self.peer === peer else { return }
            if self.peerDevice == 0, type == "offer" { self.pendingOffer = (type, sdp) } else { self.sendSignal(type, ["sdp": sdp]) }
        }
    }

    nonisolated func peer(_ peer: RtcPeer, iceCandidate: [String: Any]) {
        Task { @MainActor in
            guard self.peer === peer else { return }
            if self.peerDevice == 0 {
                if self.pendingLocalCandidates.count < 256 { self.pendingLocalCandidates.append(iceCandidate) }
            } else {
                self.sendSignal("ice", iceCandidate)
            }
        }
    }

    nonisolated func peer(_ peer: RtcPeer, link: RtcLink) {
        Task { @MainActor in
            guard self.peer === peer else { return }
            self.onLink(link)
        }
    }

    nonisolated func peer(_ peer: RtcPeer, remoteVideo present: Bool) {
        Task { @MainActor in
            guard self.peer === peer else { return }
            self.update { $0.remoteVideo = present }
        }
    }

    nonisolated func room(_ room: RtcRoom, link: RtcLink) {
        Task { @MainActor in
            guard self.room === room else { return }
            self.onLink(link)
        }
    }

    nonisolated func room(_ room: RtcRoom, participants: [Int64]) {
        Task { @MainActor in
            guard self.room === room else { return }
            self.update { $0.participants = participants }
        }
    }

    nonisolated func room(_ room: RtcRoom, userId: Int64, remoteVideo present: Bool) {
        Task { @MainActor in
            guard self.room === room, self.state?.participants.first == userId else { return }
            self.update { $0.remoteVideo = present }
        }
    }
}
