import AVFoundation
import Foundation
import SwiftUI
import UIKit

enum LiveRole: Equatable { case host, guest, viewer }
enum LivePhase: Equatable { case connecting, live, reconnecting, ended }

struct LiveUi: Equatable {
    var roomId: Int64
    var role: LiveRole
    var phase: LivePhase
    var room: LiveRoomData
    /// sfu | hls — how this device receives (viewers) or sends (host/guest).
    var transport: String
    /// CDN playlist for HLS viewers; empty on the SFU path.
    var hlsUrl = ""
    var viewers: Int64 = 0
    var muted = false
    var cameraOff = false
    var endReason = ""
    /// Coins gifted into the room so far, as every device's pill shows it.
    var roomCoins: Int64 = 0
    /// The room's last lines, newest last. Bounded to `LiveSession.chatCap`:
    /// a stadium room costs the same memory as a quiet one.
    var chat: [LiveChatData] = []
    /// Bumped when a media track behind the renderers appears or goes away, so
    /// the view re-asks for the host renderer instead of staying on the wash.
    var mediaRev = 0
}

/// A refused gift send, for the sheet to say why (`insufficient_funds`, …).
struct GiftFailure: Equatable {
    let clientId: String
    let code: String
    let message: String
}

/// One live room at a time on this device: hosting, sitting as a guest, or
/// watching. The host and the first N viewers ride the SFU; the crowd rides
/// LL-HLS from the CDN, which is how a million people watch without a million
/// media sessions. Viewer counts and guest invites arrive on the user stream.
@MainActor
final class LiveSession: ObservableObject {
    static let shared = LiveSession()

    @Published private(set) var state: LiveUi?

    /// Gifts landing in the room — the sender's optimistic frame first, then
    /// everyone's settled frames off the room stream. Listeners are the overlay
    /// and the sheet; nothing is buffered, a gift nobody is listening to is gone.
    private var onGift: [Int: (LiveGiftEvent) -> Void] = [:]
    private var onGiftError: [Int: (GiftFailure) -> Void] = [:]
    private var nextToken = 0

    private var engine: RtcEngine?
    private var room: RtcRoom?
    private var hold: Task<Void, Never>?
    private var roomStream: Task<Void, Never>?
    private var seenGifts: [Int64] = []
    private var seenSet = Set<Int64>()
    private var giftSeq = 0
    private var attached = false
    private var session = 0

    private init() {}

    func attach() {
        guard !attached else { return }
        attached = true
        LiveFeed.shared.listenCall { [weak self] ev in self?.onEvent(ev) }
    }

    /// Start broadcasting. Fails closed with an explicit reason instead of a dead camera.
    func goLive(video: Bool, title: String) {
        if let s = state, s.phase != .ended { return }
        session += 1
        let mine = session
        var seed = LiveRoomData(); seed.kind = video ? "video" : "audio"; seed.title = title
        state = LiveUi(roomId: 0, role: .host, phase: .connecting, room: seed, transport: "sfu")
        hold = Task { await LiveFeed.shared.hold() }
        Task {
            let r = await Xani.calls.liveStart(kind: video ? "video" : "audio", title: title)
            guard mine == session else { return }
            guard r.ok, let data = r.value else { fail(r.code == "already_live" ? "already_live" : (r.code.isEmpty ? "failed" : r.code)); return }
            update { $0.roomId = data.room.id; $0.room = data.room; $0.hlsUrl = data.hlsUrl; $0.roomCoins = data.room.giftCoins }
            guard let sfu = data.sfu else { fail("live_unavailable"); return }
            join(sfu, publish: true, video: video)
            openRoomStream(mine, roomId: data.room.id)
        }
    }

    /// Watch. The server picks SFU or HLS; the UI renders whichever it got.
    func watch(roomId: Int64) {
        if let s = state, s.roomId == roomId, s.phase != .ended { return }
        leave()
        session += 1
        let mine = session
        var seed = LiveRoomData(); seed.id = roomId
        state = LiveUi(roomId: roomId, role: .viewer, phase: .connecting, room: seed, transport: "hls")
        hold = Task { await LiveFeed.shared.hold() }
        Task {
            let r = await Xani.calls.liveJoin(roomId)
            guard mine == session else { return }
            guard r.ok, let data = r.value else { fail(r.statusCode == 410 ? "live_ended" : (r.code.isEmpty ? "failed" : r.code)); return }
            update { $0.room = data.room; $0.viewers = data.room.viewers; $0.transport = data.mode; $0.hlsUrl = data.hlsUrl; $0.roomCoins = data.room.giftCoins }
            openRoomStream(mine, roomId: roomId)
            if data.mode == "sfu", let sfu = data.sfu {
                join(sfu, publish: false, video: true)
            } else if data.hlsUrl.isEmpty {
                fail("live_unavailable")
            } else {
                // HLS: the player view owns playback; the session is live once the URL is known.
                update { $0.phase = .live }
            }
        }
    }

    /// Host ends for everyone; guest/viewer just leaves.
    func leave() {
        guard let ui = state else { return }
        teardown()
        state = nil
        guard ui.roomId != 0 else { return }
        Task {
            if ui.role == .host { await Xani.calls.liveEnd(ui.roomId) } else { await Xani.calls.liveLeave(ui.roomId, mode: ui.transport) }
        }
    }

    func toggleMute() {
        guard let ui = state, ui.role != .viewer else { return }
        update { $0.muted = !ui.muted }
        room?.setMuted(!ui.muted)
    }

    func toggleCamera() {
        guard let ui = state, ui.role != .viewer else { return }
        update { $0.cameraOff = !ui.cameraOff }
        room?.setVideoEnabled(ui.cameraOff)
    }

    func switchCamera() { room?.switchCamera() }

    /// Host invites a viewer up as a guest (they get `live_guest` with an SFU seat).
    func inviteGuest(_ userId: Int64) {
        guard let ui = state, ui.role == .host, ui.roomId != 0 else { return }
        Task { await Xani.calls.liveGuest(ui.roomId, userId: userId) }
    }

    // MARK: - gifts

    @discardableResult
    func listenGifts(_ f: @escaping (LiveGiftEvent) -> Void) -> Int { nextToken += 1; onGift[nextToken] = f; return nextToken }

    @discardableResult
    func listenGiftErrors(_ f: @escaping (GiftFailure) -> Void) -> Int { nextToken += 1; onGiftError[nextToken] = f; return nextToken }

    func stopListening(_ token: Int) { onGift[token] = nil; onGiftError[token] = nil }

    /// Send `gift` to `receiverId` (0 = host). Returns the idempotency key.
    ///
    /// Optimistic: the frame is delivered to listeners at once, so the sender's
    /// screen animates on the tap. The server's reply carries the settled frame
    /// and the new balance; a refusal arrives on `listenGiftErrors` with the key.
    @discardableResult
    func sendGift(_ gift: GiftData, receiverId: Int64, combo: Int = 1, count: Int = 1) -> String? {
        guard let ui = state, ui.phase != .ended, ui.roomId != 0 else { return nil }
        let to = receiverId > 0 ? receiverId : ui.room.hostId
        giftSeq += 1
        let clientId = "g\(Xani.currentUserId)-\(Int64(Date().timeIntervalSince1970 * 1000))-\(giftSeq)"
        let me = ProfileStore.shared.snapshot
        var ev = LiveGiftEvent()
        ev.roomId = ui.roomId; ev.clientId = clientId; ev.gift = gift
        ev.sender = LivePersonData(id: Xani.currentUserId, username: me.username, fullName: me.displayName, avatarUrl: me.avatarUrl,
                                   role: ui.role == .host ? "host" : (ui.role == .guest ? "guest" : "viewer"))
        ev.receiver = LivePersonData(id: to, role: to == ui.room.hostId ? "host" : "viewer")
        ev.count = count; ev.combo = combo; ev.totalCoins = gift.coinPrice * Int64(count)
        ev.roomCoins = ui.roomCoins + ev.totalCoins
        ev.atMs = Int64(Date().timeIntervalSince1970 * 1000)
        onGift.values.forEach { $0(ev) }
        let mine = session
        let roomId = ui.roomId
        Task {
            var r = await Xani.gifts.send(roomId: roomId, giftId: gift.id, receiverId: to, clientId: clientId, count: count, combo: combo)
            if !r.ok, r.isTransport {
                // Same clientId: the server lands it once whichever attempt got through.
                try? await Task.sleep(nanoseconds: 400_000_000)
                r = await Xani.gifts.send(roomId: roomId, giftId: gift.id, receiverId: to, clientId: clientId, count: count, combo: combo)
            }
            guard mine == session else { return }
            if r.ok, let data = r.value {
                ProfileStore.shared.setBalance(data.balance)
                deliverGift(data.gift)
            } else if !r.ok {
                let f = GiftFailure(clientId: clientId, code: r.code, message: r.message)
                onGiftError.values.forEach { $0(f) }
            }
        }
        return clientId
    }

    // MARK: - chat

    static let chatCap = 40
    private var chatSeq = 0

    /// Posts one line. Optimistic: the bubble is on screen at once (id 0, own
    /// clientId); the server's echo — on the reply or the stream, whichever is
    /// first — replaces it. A refusal removes it.
    func sendChat(_ text: String) {
        guard let ui = state, ui.phase != .ended, ui.roomId != 0 else { return }
        let body = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(200))
        guard !body.isEmpty else { return }
        chatSeq += 1
        let clientId = "c\(Xani.currentUserId)-\(Int64(Date().timeIntervalSince1970 * 1000))-\(chatSeq)"
        let me = ProfileStore.shared.snapshot
        var line = LiveChatData()
        line.roomId = ui.roomId; line.clientId = clientId; line.text = body
        line.user = LivePersonData(id: Xani.currentUserId, username: me.username, fullName: me.displayName, avatarUrl: me.avatarUrl,
                                   role: ui.role == .host ? "host" : (ui.role == .guest ? "guest" : "viewer"))
        line.atMs = Int64(Date().timeIntervalSince1970 * 1000)
        update { $0.chat = Self.settle($0.chat, line) }
        let mine = session
        let roomId = ui.roomId
        Task {
            let r = await Xani.calls.liveChat(roomId, text: body, clientId: clientId)
            guard mine == session else { return }
            if r.ok, let settled = r.value {
                update { $0.chat = Self.settle($0.chat, settled) }
            } else if !r.ok {
                update { $0.chat.removeAll { $0.clientId == clientId && $0.id == 0 } }
            }
        }
    }

    /// Replaces the optimistic line with the same clientId, else appends;
    /// drops a duplicate settled id; trims to `chatCap`.
    private static func settle(_ chat: [LiveChatData], _ line: LiveChatData) -> [LiveChatData] {
        var next = chat
        if !line.clientId.isEmpty, let i = next.firstIndex(where: { !$0.clientId.isEmpty && $0.clientId == line.clientId }) {
            next[i] = line
        } else if line.id != 0, next.contains(where: { $0.id == line.id }) {
            return chat
        } else {
            next.append(line)
        }
        if next.count > chatCap { next.removeFirst(next.count - chatCap) }
        return next
    }

    /// The room's own stream: one publish per gift however large the room.
    private func openRoomStream(_ mine: Int, roomId: Int64) {
        roomStream?.cancel()
        seenGifts.removeAll(); seenSet.removeAll()
        coinsFlush?.cancel(); coinsFlush = nil
        coinsSeen = state?.roomCoins ?? 0
        roomStream = Task { [weak self] in
            await Xani.gifts.stream(roomId: roomId) { name, data in
                Task { @MainActor in
                    guard let self, mine == self.session else { return }
                    self.onRoomFrame(name, data)
                }
            }
        }
    }

    private func onRoomFrame(_ name: String, _ data: Data) {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        struct Frame<T: Decodable>: Decodable { let data: T }
        switch name {
        case "live_gift":
            if let f = try? decoder.decode(Frame<LiveGiftEvent>.self, from: data) { deliverGift(f.data) }
        case "live_room":
            if let f = try? decoder.decode(Frame<LiveRoomData>.self, from: data) { noteRoomCoins(f.data.giftCoins) }
        case "live_ended":
            if let ev = CallEvent.decode(name: name, data: data) { onEvent(ev) }
        default:
            break
        }
    }

    /// One settled frame from any path: dedupe by id, fan out, count.
    private func deliverGift(_ ev: LiveGiftEvent) {
        guard let ui = state, ev.roomId == ui.roomId else { return }
        if ev.id != 0 {
            if seenSet.contains(ev.id) { return }
            seenSet.insert(ev.id); seenGifts.append(ev.id)
            if seenGifts.count > 2048 {
                for id in seenGifts.prefix(512) { seenSet.remove(id) }
                seenGifts.removeFirst(512)
            }
        }
        onGift.values.forEach { $0(ev) }
        noteRoomCoins(ev.roomCoins)
    }

    /// The room total reaches `state` at most every 250 ms: a lucky combo lands
    /// dozens of frames a second and every write here re-renders the whole
    /// room view. The overlay reads the frames; the pill reads this.
    private var coinsSeen: Int64 = 0
    private var coinsFlush: Task<Void, Never>?

    private func noteRoomCoins(_ total: Int64) {
        guard total > 0 else { return }
        coinsSeen = max(coinsSeen, total)
        if coinsFlush != nil { return }
        coinsFlush = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard let self, !Task.isCancelled else { return }
            self.coinsFlush = nil
            let v = self.coinsSeen
            self.update { if v > $0.roomCoins { $0.roomCoins = v } }
        }
    }

    func localView() -> UIView? { room?.localView() }

    /// Host's stream for SFU viewers/guests; nil on the HLS path.
    func hostView() -> UIView? {
        guard let hostId = state?.room.hostId, hostId != 0 else { return nil }
        return room?.remoteView(userId: hostId)
    }

    // MARK: - internals

    private func onEvent(_ e: CallEvent) {
        guard let ui = state else { return }
        switch e {
        case .liveViewers(let roomId, let viewers):
            if roomId == ui.roomId { update { $0.viewers = viewers } }
        case .liveChat(let line):
            if line.roomId == ui.roomId { update { $0.chat = Self.settle($0.chat, line) } }
        case .liveEnded(let roomId, let reason):
            if roomId == ui.roomId {
                teardown()
                update { $0.phase = .ended; $0.endReason = reason.isEmpty ? "ended" : reason }
            }
        case .liveGuest(let roomId, let sfu):
            if roomId == ui.roomId, ui.role == .viewer {
                // Promoted: swap the viewer seat for a publishing one.
                room?.close(); room = nil
                update { $0.role = .guest; $0.transport = "sfu"; $0.phase = .connecting }
                join(sfu, publish: true, video: ui.room.kind == "video")
            }
        default:
            break
        }
    }

    private func join(_ sfu: RoomJoinData, publish: Bool, video: Bool) {
        guard let eng = engine ?? RtcEngineFactory.create() else { fail("live_unavailable"); return }
        engine = eng
        room = eng.joinRoom(url: sfu.url, token: sfu.token, video: video, publish: publish, listener: self)
        update { $0.mediaRev += 1 }
    }

    private func fail(_ reason: String) {
        teardown()
        update { $0.phase = .ended; $0.endReason = reason }
    }

    private func teardown() {
        session += 1
        hold?.cancel(); hold = nil
        roomStream?.cancel(); roomStream = nil
        coinsFlush?.cancel(); coinsFlush = nil
        coinsSeen = 0
        room?.close(); room = nil
    }

    private func update(_ f: (inout LiveUi) -> Void) {
        guard var s = state else { return }
        f(&s)
        state = s
    }
}

extension LiveSession: RtcRoomListener {
    nonisolated func room(_ room: RtcRoom, link: RtcLink) {
        Task { @MainActor in
            guard self.room === room else { return }
            switch link {
            case .connected: self.update { $0.phase = .live }
            case .disconnected, .failed: self.update { $0.phase = .reconnecting }
            case .closed: if self.state?.phase != .ended { self.update { $0.phase = .ended; $0.endReason = "lost" } }
            case .new, .connecting: break
            }
        }
    }

    nonisolated func room(_ room: RtcRoom, participants: [Int64]) {}

    nonisolated func room(_ room: RtcRoom, userId: Int64, remoteVideo present: Bool) {
        // Any remote track change republishes state, so the viewer re-asks for
        // the host renderer. Without this the host's track could land a beat
        // after the join and the screen stayed on the tinted wash until some
        // unrelated event (a viewer tick) happened to repaint it.
        Task { @MainActor in
            guard self.room === room else { return }
            self.update { $0.mediaRev += 1 }
        }
    }
}

/// Plays the CDN live playlist with AVPlayer: hardware-decoded, no third-party
/// player. A live playlist has no position to resume, so a stall re-arms at the
/// live edge instead of seeking.
struct HlsPlayerView: UIViewRepresentable {
    let url: String

    final class PlayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        var player: AVPlayer? {
            didSet {
                playerLayer.player = player
                playerLayer.videoGravity = .resizeAspectFill
            }
        }
        var stallToken: NSObjectProtocol?
        deinit {
            if let t = stallToken { NotificationCenter.default.removeObserver(t) }
            player?.pause()
        }
    }

    func makeUIView(context: Context) -> PlayerView {
        let v = PlayerView()
        guard let u = URL(string: url) else { return v }
        let item = AVPlayerItem(url: u)
        item.preferredForwardBufferDuration = 2
        let player = AVPlayer(playerItem: item)
        player.automaticallyWaitsToMinimizeStalling = true
        v.player = player
        v.stallToken = NotificationCenter.default.addObserver(forName: .AVPlayerItemPlaybackStalled, object: item, queue: .main) { _ in
            player.seek(to: .positiveInfinity) { _ in player.play() }
        }
        player.play()
        return v
    }

    func updateUIView(_ uiView: PlayerView, context: Context) {}

    static func dismantleUIView(_ uiView: PlayerView, coordinator: ()) {
        uiView.player?.pause()
        uiView.player = nil
    }
}
