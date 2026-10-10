import Foundation

/// Call and live wire types. Signalling only: media never crosses this layer.

struct CallerBrief: Decodable, Equatable {
    var id: Int64 = 0
    var username = ""
    var fullName = ""
    var avatarUrl = ""

    private enum CodingKeys: String, CodingKey { case id, username, fullName, avatarUrl }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); username = c.str(.username); fullName = c.str(.fullName); avatarUrl = c.str(.avatarUrl)
    }
}

struct CallParticipantData: Decodable, Equatable {
    var userId: Int64 = 0
    var state = ""
    private enum CodingKeys: String, CodingKey { case userId, state }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.wireId(.userId); state = c.str(.state)
    }
}

struct CallData: Decodable, Equatable {
    var id: Int64 = 0
    var conversationId: Int64 = 0
    /// audio | video
    var kind = "audio"
    /// p2p | sfu
    var mode = "p2p"
    /// ringing | active | ended
    var state = "ringing"
    var callerId: Int64 = 0
    var caller = CallerBrief()
    var participants: [CallParticipantData] = []
    var endReason = ""
    var seq: Int64 = 0

    private enum CodingKeys: String, CodingKey {
        case id, conversationId, kind, mode, state, callerId, caller, participants, endReason, seq
    }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        conversationId = c.wireId(.conversationId)
        kind = c.str(.kind, "audio")
        mode = c.str(.mode, "p2p")
        state = c.str(.state, "ringing")
        callerId = c.wireId(.callerId)
        caller = (try? c.decodeIfPresent(CallerBrief.self, forKey: .caller)) ?? CallerBrief()
        participants = (try? c.decodeIfPresent([CallParticipantData].self, forKey: .participants)) ?? []
        endReason = c.str(.endReason)
        seq = c.int64(.seq)
    }

    var joined: [Int64] { participants.filter { $0.state == "joined" }.map(\.userId) }
}

struct IceServerData: Decodable, Equatable {
    var urls: [String] = []
    var username = ""
    var credential = ""
    private enum CodingKeys: String, CodingKey { case urls, username, credential }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        urls = (try? c.decodeIfPresent([String].self, forKey: .urls)) ?? []
        username = c.str(.username); credential = c.str(.credential)
    }
}

struct IceConfigData: Decodable, Equatable {
    var servers: [IceServerData] = []
    var ttlSeconds = 0
    var reconnectGraceSeconds = 150
    var relayOnly = false

    static let empty = IceConfigData()

    private enum CodingKeys: String, CodingKey { case servers, ttlSeconds, reconnectGraceSeconds, relayOnly }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        servers = (try? c.decodeIfPresent([IceServerData].self, forKey: .servers)) ?? []
        ttlSeconds = Int(c.int64(.ttlSeconds))
        let grace = Int(c.int64(.reconnectGraceSeconds))
        reconnectGraceSeconds = grace > 0 ? grace : 150
        relayOnly = c.bool(.relayOnly)
    }
}

struct RoomJoinData: Decodable, Equatable {
    var url = ""
    var token = ""
    var room = ""
    private enum CodingKeys: String, CodingKey { case url, token, room }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = c.str(.url); token = c.str(.token); room = c.str(.room)
    }
}

struct CallStartData: Decodable {
    var call = CallData()
    var ice = IceConfigData.empty
    var calleeOnline = false
    var room: RoomJoinData?
    private enum CodingKeys: String, CodingKey { case call, ice, calleeOnline, room }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        call = (try? c.decodeIfPresent(CallData.self, forKey: .call)) ?? CallData()
        ice = (try? c.decodeIfPresent(IceConfigData.self, forKey: .ice)) ?? .empty
        calleeOnline = c.bool(.calleeOnline)
        room = try? c.decodeIfPresent(RoomJoinData.self, forKey: .room)
    }
}

struct CallAcceptData: Decodable {
    var call = CallData()
    var ice = IceConfigData.empty
    var room: RoomJoinData?
    private enum CodingKeys: String, CodingKey { case call, ice, room }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        call = (try? c.decodeIfPresent(CallData.self, forKey: .call)) ?? CallData()
        ice = (try? c.decodeIfPresent(IceConfigData.self, forKey: .ice)) ?? .empty
        room = try? c.decodeIfPresent(RoomJoinData.self, forKey: .room)
    }
}

struct LiveRoomData: Decodable, Equatable {
    var id: Int64 = 0
    var hostId: Int64 = 0
    var kind = "video"
    var title = ""
    var viewers: Int64 = 0
    var live = true
    /// Coins gifted into the room so far.
    var giftCoins: Int64 = 0
    private enum CodingKeys: String, CodingKey { case id, hostId, kind, title, viewers, live, giftCoins }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); hostId = c.wireId(.hostId); kind = c.str(.kind, "video")
        title = c.str(.title); viewers = c.int64(.viewers)
        live = (try? c.decodeIfPresent(Bool.self, forKey: .live)) ?? true
        giftCoins = c.int64(.giftCoins)
    }
}

/// The verified-time HUD contract, carried on start and on every host /
/// co-host heartbeat. The device shows this and only this: `verifiedMs` is
/// the ledger's figure as of `serverNowMs`; the screen advances it locally
/// while `counting` and stops once the last ack is older than
/// `allowanceSeconds` — exactly when the server stops too.
struct LiveTimeView: Decodable, Equatable {
    var verifiedMs: Int64 = 0
    var counting = false
    var pausedMs: Int64 = 0
    var segmentNo = 0
    var serverNowMs: Int64 = 0
    var allowanceSeconds = 30
    private enum CodingKeys: String, CodingKey { case verifiedMs, counting, pausedMs, segmentNo, serverNowMs, allowanceSeconds }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        verifiedMs = c.int64(.verifiedMs); counting = c.bool(.counting); pausedMs = c.int64(.pausedMs)
        segmentNo = Int(c.int64(.segmentNo)); serverNowMs = c.int64(.serverNowMs)
        let a = Int(c.int64(.allowanceSeconds)); allowanceSeconds = a > 0 ? a : 30
    }
}

/// The host's end card: the server's clock and counters, never the phone's.
struct LiveSummaryData: Decodable, Equatable {
    var roomId: Int64 = 0
    var durationSeconds = 0
    var peakViewers = 0
    var totalJoins = 0
    var uniqueViewers = 0
    var reason = ""
    var giftCoins: Int64 = 0
    var giftCount = 0
    /// The ledger's number for this room — what the host is credited. Wall
    /// time (`durationSeconds`) may be larger: silence inside the room
    /// (`pausedMs`) is never counted. -1 when the server did not answer.
    var verifiedMs: Int64 = -1
    var pausedMs: Int64 = 0
    /// The host's accounting-day total after this room; -1 when unknown.
    var todayMs: Int64 = -1
    private enum CodingKeys: String, CodingKey {
        case roomId, durationSeconds, peakViewers, totalJoins, uniqueViewers, reason, giftCoins, giftCount, verifiedMs, pausedMs, todayMs
    }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        roomId = c.wireId(.roomId); durationSeconds = Int(c.int64(.durationSeconds))
        peakViewers = Int(c.int64(.peakViewers)); totalJoins = Int(c.int64(.totalJoins)); uniqueViewers = Int(c.int64(.uniqueViewers))
        reason = c.str(.reason); giftCoins = c.int64(.giftCoins); giftCount = Int(c.int64(.giftCount))
        verifiedMs = c.contains(.verifiedMs) ? c.int64(.verifiedMs) : -1
        pausedMs = c.int64(.pausedMs)
        todayMs = c.contains(.todayMs) ? c.int64(.todayMs) : -1
    }
}

struct LiveStartData: Decodable {
    var room = LiveRoomData()
    var sfu: RoomJoinData?
    var hlsUrl = ""
    var reconnectGraceSeconds = 180
    var heartbeatSeconds = 15
    /// The HUD's starting point: the ledger's zero, or its figure on a resume.
    var time: LiveTimeView?
    /// The same go-live (client_id) was already open; this is that room, not a new one.
    var resumed = false
    private enum CodingKeys: String, CodingKey { case room, sfu, hlsUrl, reconnectGraceSeconds, heartbeatSeconds, time, resumed }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        room = (try? c.decodeIfPresent(LiveRoomData.self, forKey: .room)) ?? LiveRoomData()
        sfu = try? c.decodeIfPresent(RoomJoinData.self, forKey: .sfu)
        hlsUrl = c.str(.hlsUrl)
        let g = Int(c.int64(.reconnectGraceSeconds)); reconnectGraceSeconds = g > 0 ? g : 180
        let h = Int(c.int64(.heartbeatSeconds)); heartbeatSeconds = h > 0 ? h : 15
        time = try? c.decodeIfPresent(LiveTimeView.self, forKey: .time)
        resumed = c.bool(.resumed)
    }
}

struct LiveHeartbeatData: Decodable {
    var room = LiveRoomData()
    var live = false
    /// Host / co-host: the ledger's running figure. Nil for viewers.
    var time: LiveTimeView?
    private enum CodingKeys: String, CodingKey { case room, live, time }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        room = (try? c.decodeIfPresent(LiveRoomData.self, forKey: .room)) ?? LiveRoomData()
        live = c.bool(.live)
        time = try? c.decodeIfPresent(LiveTimeView.self, forKey: .time)
    }
}

struct LiveJoinData: Decodable {
    var room = LiveRoomData()
    /// sfu | hls
    var mode = "hls"
    var sfu: RoomJoinData?
    var hlsUrl = ""
    var reconnectGraceSeconds = 150
    private enum CodingKeys: String, CodingKey { case room, mode, sfu, hlsUrl, reconnectGraceSeconds }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        room = (try? c.decodeIfPresent(LiveRoomData.self, forKey: .room)) ?? LiveRoomData()
        mode = c.str(.mode, "hls")
        sfu = try? c.decodeIfPresent(RoomJoinData.self, forKey: .sfu)
        hlsUrl = c.str(.hlsUrl)
        let g = Int(c.int64(.reconnectGraceSeconds)); reconnectGraceSeconds = g > 0 ? g : 150
    }
}

/// One call or live frame off the stream, already typed.
enum CallEvent {
    case invite(CallData)
    case ringing(callId: Int64, userId: Int64)
    case accept(callId: Int64, userId: Int64, deviceId: Int64)
    case reject(callId: Int64, userId: Int64, reason: String)
    case taken(callId: Int64, deviceId: Int64)
    case end(callId: Int64, reason: String)
    case signal(callId: Int64, fromUser: Int64, fromDevice: Int64, toDevice: Int64, type: String, payload: [String: Any])
    case participant(callId: Int64, userId: Int64, state: String)
    case room(RoomJoinData)
    case liveViewers(roomId: Int64, viewers: Int64)
    case liveGuest(roomId: Int64, sfu: RoomJoinData)
    case liveEnded(roomId: Int64, reason: String)
    case liveChat(LiveChatData)

    static func decode(name: String, data: Data) -> CallEvent? {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        struct Frame<T: Decodable>: Decodable { let data: T }
        struct Fields: Decodable {
            var callId: Int64 = 0, userId: Int64 = 0, deviceId: Int64 = 0, roomId: Int64 = 0
            var fromUser: Int64 = 0, fromDevice: Int64 = 0, toDevice: Int64 = 0, viewers: Int64 = 0
            var reason = "", type = "", state = ""
            var sfu: RoomJoinData?
            private enum CodingKeys: String, CodingKey {
                case callId, userId, deviceId, roomId, fromUser, fromDevice, toDevice, viewers, reason, type, state, sfu
            }
            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                callId = c.wireId(.callId); userId = c.wireId(.userId); deviceId = c.wireId(.deviceId); roomId = c.wireId(.roomId)
                fromUser = c.wireId(.fromUser); fromDevice = c.wireId(.fromDevice); toDevice = c.wireId(.toDevice)
                viewers = c.int64(.viewers); reason = c.str(.reason); type = c.str(.type); state = c.str(.state)
                sfu = try? c.decodeIfPresent(RoomJoinData.self, forKey: .sfu)
            }
        }
        func fields() -> Fields? { (try? decoder.decode(Frame<Fields>.self, from: data))?.data }
        switch name {
        case "call_invite":
            return (try? decoder.decode(Frame<CallData>.self, from: data)).map { .invite($0.data) }
        case "call_ringing":
            return fields().map { .ringing(callId: $0.callId, userId: $0.userId) }
        case "call_accept":
            return fields().map { .accept(callId: $0.callId, userId: $0.userId, deviceId: $0.deviceId) }
        case "call_reject":
            return fields().map { .reject(callId: $0.callId, userId: $0.userId, reason: $0.reason) }
        case "call_taken":
            return fields().map { .taken(callId: $0.callId, deviceId: $0.deviceId) }
        case "call_end":
            return fields().map { .end(callId: $0.callId, reason: $0.reason) }
        case "call_signal":
            guard let f = fields() else { return nil }
            // The payload is opaque SDP/ICE JSON: read it once as a dictionary and pass through.
            let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let payload = ((root?["data"] as? [String: Any])?["payload"] as? [String: Any]) ?? [:]
            return .signal(callId: f.callId, fromUser: f.fromUser, fromDevice: f.fromDevice, toDevice: f.toDevice, type: f.type, payload: payload)
        case "call_participant":
            return fields().map { .participant(callId: $0.callId, userId: $0.userId, state: $0.state) }
        case "call_room":
            return (try? decoder.decode(Frame<RoomJoinData>.self, from: data)).map { .room($0.data) }
        case "live_viewers":
            return fields().map { .liveViewers(roomId: $0.roomId, viewers: $0.viewers) }
        case "live_guest":
            guard let f = fields(), let sfu = f.sfu else { return nil }
            return .liveGuest(roomId: f.roomId, sfu: sfu)
        case "live_ended":
            return fields().map { .liveEnded(roomId: $0.roomId, reason: $0.reason) }
        case "live_chat":
            return (try? decoder.decode(Frame<LiveChatData>.self, from: data)).map { .liveChat($0.data) }
        default:
            return nil
        }
    }
}

private struct CallEnvelope: Decodable { let call: CallData }
private struct IceEnvelope: Decodable { let ice: IceConfigData }
private struct RoomEnvelope: Decodable {
    var room: RoomJoinData?
    private enum CodingKeys: String, CodingKey { case room }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        room = try? c.decodeIfPresent(RoomJoinData.self, forKey: .room)
    }
}
private struct LiveRoomEnvelope: Decodable { let room: LiveRoomData }
private struct LiveItems: Decodable { let items: [LiveRoomData] }

/// Typed call + live signalling endpoints.
struct CallApi {
    let api: ApiClient

    func start(conversationId: Int64, kind: String, clientId: String, relayOnly: Bool) async -> ApiResponse<CallStartData> {
        await api.post("/v1/calls", body: ["conversation_id": String(conversationId), "kind": kind, "client_id": clientId, "relay_only": relayOnly],
                       authenticated: true, as: CallStartData.self)
    }

    func get(_ callId: Int64) async -> ApiResponse<CallData> {
        await api.get("/v1/calls/\(callId)", authenticated: true, as: CallEnvelope.self).map(\.call)
    }

    @discardableResult
    func ring(_ callId: Int64) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/calls/\(callId)/ring", body: [:], authenticated: true)
    }

    func accept(_ callId: Int64, relayOnly: Bool) async -> ApiResponse<CallAcceptData> {
        await api.post("/v1/calls/\(callId)/accept", body: ["relay_only": relayOnly], authenticated: true, as: CallAcceptData.self)
    }

    @discardableResult
    func reject(_ callId: Int64, reason: String) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/calls/\(callId)/reject", body: ["reason": reason], authenticated: true)
    }

    @discardableResult
    func end(_ callId: Int64, reason: String) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/calls/\(callId)/end", body: ["reason": reason], authenticated: true)
    }

    func signal(_ callId: Int64, toUser: Int64, toDevice: Int64, type: String, payload: [String: Any]) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/calls/\(callId)/signal",
                       body: ["to_user": String(toUser), "to_device": String(toDevice), "type": type, "payload": payload],
                       authenticated: true)
    }

    func add(_ callId: Int64, userId: Int64) async -> ApiResponse<RoomJoinData?> {
        await api.post("/v1/calls/\(callId)/add", body: ["user_id": String(userId)], authenticated: true, as: RoomEnvelope.self).map(\.room)
    }

    func ice(relayOnly: Bool) async -> ApiResponse<IceConfigData> {
        await api.get("/v1/calls/ice", query: ["relay_only": relayOnly ? "1" : "0"], authenticated: true, as: IceEnvelope.self).map(\.ice)
    }

    // MARK: live

    /// `clientId` is this device's key for one go-live: a retry after a
    /// transport failure sends the same key and gets the same room back, so
    /// the time already verified is kept rather than restarted.
    func liveStart(kind: String, title: String, seats: Int = 0, clientId: String = "") async -> ApiResponse<LiveStartData> {
        var body: [String: Any] = ["kind": kind, "title": title]
        if kind == "audio" {
            body["seats"] = max(2, min(45, seats))
        }
        if !clientId.isEmpty { body["client_id"] = clientId }
        return await api.post("/v1/live/rooms", body: body, authenticated: true, as: LiveStartData.self)
    }

    /// Presence + clock sync. The reply carries `live=false` once the server
    /// ended the room, and the verified-time figure for hosts and co-hosts.
    func liveHeartbeat(_ roomId: Int64, mode: String) async -> ApiResponse<LiveHeartbeatData> {
        await api.post("/v1/live/rooms/\(roomId)/heartbeat", body: ["mode": mode], authenticated: true, as: LiveHeartbeatData.self)
    }

    func liveOpen(limit: Int = 50) async -> ApiResponse<[LiveRoomData]> {
        await api.get("/v1/live/rooms", query: ["limit": String(limit)], authenticated: true, as: LiveItems.self).map(\.items)
    }

    func liveGet(_ roomId: Int64) async -> ApiResponse<LiveRoomData> {
        await api.get("/v1/live/rooms/\(roomId)", authenticated: true, as: LiveRoomEnvelope.self).map(\.room)
    }

    func liveJoin(_ roomId: Int64) async -> ApiResponse<LiveJoinData> {
        await api.post("/v1/live/rooms/\(roomId)/join", body: [:], authenticated: true, as: LiveJoinData.self)
    }

    @discardableResult
    func liveLeave(_ roomId: Int64, mode: String) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/live/rooms/\(roomId)/leave", body: ["mode": mode], authenticated: true)
    }

    /// Host ends the room. `reason`: host_ended · lost · background · battery ·
    /// app_closed — it labels the record; the server bounds the time itself.
    @discardableResult
    func liveEnd(_ roomId: Int64, reason: String = "host_ended") async -> ApiResponse<LiveSummaryData> {
        struct Envelope: Decodable {
            var summary = LiveSummaryData()
            private enum CodingKeys: String, CodingKey { case summary }
            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                summary = (try? c.decodeIfPresent(LiveSummaryData.self, forKey: .summary)) ?? LiveSummaryData()
            }
        }
        let r: ApiResponse<Envelope> = await api.post("/v1/live/rooms/\(roomId)/end", body: ["reason": reason], authenticated: true, as: Envelope.self)
        return r.map(\.summary)
    }

    @discardableResult
    func liveGuest(_ roomId: Int64, userId: Int64) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/live/rooms/\(roomId)/guest", body: ["user_id": String(userId)], authenticated: true)
    }

    /// Posts one chat line into the room. `clientId` is echoed on the reply
    /// so the sender's optimistic bubble settles; everyone else gets the line
    /// on their stream.
    func liveChat(_ roomId: Int64, text: String, clientId: String) async -> ApiResponse<LiveChatData> {
        struct Envelope: Decodable { let message: LiveChatData }
        let r: ApiResponse<Envelope> = await api.post(
            "/v1/live/rooms/\(roomId)/chat",
            body: ["text": text, "client_id": clientId],
            authenticated: true, as: Envelope.self
        )
        return r.map { $0.message }
    }
}
