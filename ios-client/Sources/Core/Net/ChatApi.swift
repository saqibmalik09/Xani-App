import Foundation

/// Chat wire types. Ids are strings on the wire (they exceed 2^53) and Int64
/// here. Times are RFC3339 and parsed once, to epoch seconds.

struct ChatPeer: Decodable, Equatable {
    var id: Int64 = 0
    var username = ""
    var fullName = ""
    var avatarUrl = ""
    var verified = false
    /// Their "what's on your mind"; empty when they have not set one.
    var status = ""
    var online = false
    /// Epoch seconds of the last device leaving; 0 while online or unknown.
    var lastSeenAt: TimeInterval = 0
    /// The signed-in user has blocked this person.
    var blocked = false

    private enum CodingKeys: String, CodingKey {
        case id, username, fullName, avatarUrl, verified, status, online, lastSeenAt, blocked
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        username = c.str(.username)
        fullName = c.str(.fullName)
        avatarUrl = c.str(.avatarUrl)
        verified = c.bool(.verified)
        status = c.str(.status)
        online = c.bool(.online)
        lastSeenAt = c.epoch(.lastSeenAt)
        blocked = c.bool(.blocked)
    }
}

struct ChatReactionData: Decodable, Equatable {
    var userId: Int64 = 0
    var emoji = ""

    private enum CodingKeys: String, CodingKey { case userId, emoji }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.wireId(.userId)
        emoji = c.str(.emoji)
    }
}

struct ChatMessageData: Decodable, Equatable {
    var id: Int64 = 0
    var conversationId: Int64 = 0
    var seq: Int64 = 0
    var senderId: Int64 = 0
    /// text · image · video · voice · contact · location · document · poll · event · system
    var kind = "text"
    var body = ""
    var mediaKey = ""
    var mediaMime = ""
    var mediaBytes: Int64 = 0
    var mediaName = ""
    var mediaMs = 0
    var replyTo: Int64 = 0
    var clientId = ""
    var createdAt: TimeInterval = 0
    var edited = false
    var deleted = false
    /// Epoch seconds when the message disappears; 0 for never.
    var expiresAt: TimeInterval = 0
    var reactions: [ChatReactionData] = []

    private enum CodingKeys: String, CodingKey {
        case id, conversationId, seq, senderId, kind, body, mediaKey, mediaMime, mediaBytes, mediaName, mediaMs
        case replyTo, clientId, createdAt, edited, deleted, expiresAt, reactions
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        conversationId = c.wireId(.conversationId)
        seq = c.int64(.seq)
        senderId = c.wireId(.senderId)
        kind = c.str(.kind, "text")
        body = c.str(.body)
        mediaKey = c.str(.mediaKey)
        mediaMime = c.str(.mediaMime)
        mediaBytes = c.int64(.mediaBytes)
        mediaName = c.str(.mediaName)
        mediaMs = Int(c.int64(.mediaMs))
        replyTo = c.wireId(.replyTo)
        clientId = c.str(.clientId)
        createdAt = c.epoch(.createdAt)
        edited = c.bool(.edited)
        deleted = c.bool(.deleted)
        expiresAt = c.epoch(.expiresAt)
        reactions = (try? c.decode([ChatReactionData].self, forKey: .reactions)) ?? []
    }
}

struct ConversationData: Decodable, Equatable {
    var id: Int64 = 0
    var peer = ChatPeer()
    var lastSeq: Int64 = 0
    var unread = 0
    var readSeq: Int64 = 0
    var deliveredSeq: Int64 = 0
    var peerReadSeq: Int64 = 0
    var peerReadAt: TimeInterval = 0
    var peerDeliveredSeq: Int64 = 0
    var peerDeliveredAt: TimeInterval = 0
    var pinned = false
    var muted = false
    var archived = false
    /// 0 off · -1 after seen · n seconds after send.
    var vanishSeconds = 0
    var last: ChatMessageData?
    var sortAt: TimeInterval = 0
    /// The raw cursor value for paging the inbox.
    var sortAtRaw = ""

    private enum CodingKeys: String, CodingKey {
        case id, peer, lastSeq, unread, readSeq, deliveredSeq, peerReadSeq, peerReadAt, peerDeliveredSeq, peerDeliveredAt
        case pinned, muted, archived, vanishSeconds, last, sortAt
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        peer = (try? c.decode(ChatPeer.self, forKey: .peer)) ?? ChatPeer()
        lastSeq = c.int64(.lastSeq)
        unread = Int(c.int64(.unread))
        readSeq = c.int64(.readSeq)
        deliveredSeq = c.int64(.deliveredSeq)
        peerReadSeq = c.int64(.peerReadSeq)
        peerReadAt = c.epoch(.peerReadAt)
        peerDeliveredSeq = c.int64(.peerDeliveredSeq)
        peerDeliveredAt = c.epoch(.peerDeliveredAt)
        pinned = c.bool(.pinned)
        muted = c.bool(.muted)
        archived = c.bool(.archived)
        vanishSeconds = Int(c.int64(.vanishSeconds))
        last = try? c.decode(ChatMessageData.self, forKey: .last)
        sortAt = c.epoch(.sortAt)
        sortAtRaw = c.str(.sortAt)
    }
}

/// The ack for a send: the canonical message and the sender's inbox row.
struct SendAck: Decodable, Equatable {
    let message: ChatMessageData
    let conversation: ConversationData
}

/// One member's markers moving.
struct ReceiptEvent: Decodable, Equatable {
    var conversationId: Int64 = 0
    var userId: Int64 = 0
    var deliveredSeq: Int64 = 0
    var readSeq: Int64 = 0
    var at: TimeInterval = 0

    private enum CodingKeys: String, CodingKey { case conversationId, userId, deliveredSeq, readSeq, at }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        conversationId = c.wireId(.conversationId)
        userId = c.wireId(.userId)
        deliveredSeq = c.int64(.deliveredSeq)
        readSeq = c.int64(.readSeq)
        at = c.epoch(.at)
    }
}

struct ReactionEvent: Decodable, Equatable {
    var conversationId: Int64 = 0
    var messageId: Int64 = 0
    var userId: Int64 = 0
    var emoji = ""

    private enum CodingKeys: String, CodingKey { case conversationId, messageId, userId, emoji }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        conversationId = c.wireId(.conversationId)
        messageId = c.wireId(.messageId)
        userId = c.wireId(.userId)
        emoji = c.str(.emoji)
    }
}

struct TypingEvent: Decodable, Equatable {
    var conversationId: Int64 = 0
    var userId: Int64 = 0
    var typing = false

    private enum CodingKeys: String, CodingKey { case conversationId, userId, typing }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        conversationId = c.wireId(.conversationId)
        userId = c.wireId(.userId)
        typing = c.bool(.typing)
    }
}

struct PresenceEvent: Decodable, Equatable {
    var userId: Int64 = 0
    var online = false
    var lastSeenAt: TimeInterval = 0

    private enum CodingKeys: String, CodingKey { case userId, online, lastSeenAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        userId = c.wireId(.userId)
        online = c.bool(.online)
        lastSeenAt = c.epoch(.lastSeenAt)
    }
}

/// A chat event off the shared user stream, already typed.
enum ChatEvent {
    case message(ChatMessageData)
    case receipt(ReceiptEvent)
    case reaction(ReactionEvent)
    case typing(TypingEvent)
    case presence(PresenceEvent)
    case conversation(ConversationData)
    case gone(Int64)

    /// Decodes one `chat_*` frame; nil for names this build does not know.
    static func decode(name: String, data: Data) -> ChatEvent? {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        struct Frame<T: Decodable>: Decodable { let data: T }
        struct Gone: Decodable {
            var conversationId: Int64 = 0
            private enum CodingKeys: String, CodingKey { case conversationId }
            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                conversationId = c.wireId(.conversationId)
            }
        }
        switch name {
        case "chat_message":
            return (try? decoder.decode(Frame<ChatMessageData>.self, from: data)).map { .message($0.data) }
        case "chat_receipt":
            return (try? decoder.decode(Frame<ReceiptEvent>.self, from: data)).map { .receipt($0.data) }
        case "chat_reaction":
            return (try? decoder.decode(Frame<ReactionEvent>.self, from: data)).map { .reaction($0.data) }
        case "chat_typing":
            return (try? decoder.decode(Frame<TypingEvent>.self, from: data)).map { .typing($0.data) }
        case "chat_presence":
            return (try? decoder.decode(Frame<PresenceEvent>.self, from: data)).map { .presence($0.data) }
        case "chat_conversation":
            return (try? decoder.decode(Frame<ConversationData>.self, from: data)).map { .conversation($0.data) }
        case "chat_gone":
            return (try? decoder.decode(Frame<Gone>.self, from: data)).map { .gone($0.data.conversationId) }
        default:
            return nil
        }
    }
}

/// One page of chats.
struct InboxPage: Equatable {
    var items: [ConversationData]
    var next: String
}

private struct ConversationEnvelope: Decodable { let conversation: ConversationData }
private struct MessageEnvelope: Decodable { let message: ChatMessageData }

/// Chat endpoints. Writes answer with the canonical row; delivery to the other
/// side rides the shared user stream as `chat_*` events (see `LiveFeed`).
struct ChatApi {
    let api: ApiClient

    /// Finds or creates the direct chat with `userId`.
    func open(_ userId: Int64) async -> ApiResponse<ConversationData> {
        await api.post("/v1/chats/direct", body: ["user_id": String(userId)], authenticated: true,
                       as: ConversationEnvelope.self).map(\.conversation)
    }

    func inbox(cursor: String = "", limit: Int = 50) async -> ApiResponse<InboxPage> {
        var query = ["limit": String(limit)]
        if !cursor.isEmpty { query["cursor"] = cursor }
        return await api.get("/v1/chats", query: query, authenticated: true, as: ItemsResponse<ConversationData>.self)
            .map { InboxPage(items: $0.items, next: $0.nextCursor ?? "") }
    }

    func get(_ conversationId: Int64) async -> ApiResponse<ConversationData> {
        await api.get("/v1/chats/\(conversationId)", authenticated: true, as: ConversationEnvelope.self).map(\.conversation)
    }

    /// Newest first. `beforeSeq` walks back; `afterSeq` alone resumes after a reconnect.
    func messages(_ conversationId: Int64, beforeSeq: Int64 = 0, afterSeq: Int64 = 0, limit: Int = 50) async -> ApiResponse<[ChatMessageData]> {
        var query = ["limit": String(limit)]
        if beforeSeq > 0 { query["before_seq"] = String(beforeSeq) }
        if afterSeq > 0 { query["after_seq"] = String(afterSeq) }
        return await api.get("/v1/chats/\(conversationId)/messages", query: query, authenticated: true,
                             as: ItemsResponse<ChatMessageData>.self).map(\.items)
    }

    func send(_ conversationId: Int64, clientId: String, body: String, kind: String = "text", replyTo: Int64 = 0) async -> ApiResponse<SendAck> {
        var payload: [String: Any] = ["kind": kind, "body": body, "client_id": clientId]
        if replyTo > 0 { payload["reply_to"] = String(replyTo) }
        return await api.post("/v1/chats/\(conversationId)/messages", body: payload, authenticated: true, as: SendAck.self)
    }

    /// Uploads one attachment from disk and creates its message in a single round trip.
    func sendMedia(
        _ conversationId: Int64,
        clientId: String,
        kind: String,
        mime: String,
        file: URL,
        caption: String = "",
        fileName: String = "",
        durationMs: Int = 0,
        replyTo: Int64 = 0
    ) async -> ApiResponse<SendAck> {
        var query = ["kind": kind, "client_id": clientId]
        if !caption.isEmpty { query["caption"] = caption }
        if replyTo > 0 { query["reply_to"] = String(replyTo) }
        var headers: [String: String] = [:]
        if !fileName.isEmpty {
            headers["X-File-Name"] = String(fileName.unicodeScalars.filter { (32...126).contains($0.value) })
        }
        if durationMs > 0 { headers["X-Media-Ms"] = String(durationMs) }
        return await api.putFile("/v1/chats/\(conversationId)/media", query: query, headers: headers, file: file,
                                 contentType: mime.isEmpty ? "application/octet-stream" : mime, as: SendAck.self)
    }

    func ack(_ conversationId: Int64, deliveredSeq: Int64, readSeq: Int64) async -> ApiResponse<ConversationData> {
        await api.post("/v1/chats/\(conversationId)/ack", body: ["delivered_seq": deliveredSeq, "read_seq": readSeq],
                       authenticated: true, as: ConversationEnvelope.self).map(\.conversation)
    }

    @discardableResult
    func typing(_ conversationId: Int64, on: Bool) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/chats/\(conversationId)/typing", body: ["typing": on], authenticated: true)
    }

    func settings(_ conversationId: Int64, pinned: Bool? = nil, muted: Bool? = nil, archived: Bool? = nil, vanishSeconds: Int? = nil) async -> ApiResponse<ConversationData> {
        var body: [String: Any] = [:]
        if let pinned { body["pinned"] = pinned }
        if let muted { body["muted"] = muted }
        if let archived { body["archived"] = archived }
        if let vanishSeconds { body["vanish_seconds"] = vanishSeconds }
        return await api.post("/v1/chats/\(conversationId)/settings", body: body, authenticated: true,
                              as: ConversationEnvelope.self).map(\.conversation)
    }

    func clear(_ conversationId: Int64) async -> ApiResponse<ConversationData> {
        await api.post("/v1/chats/\(conversationId)/clear", body: [:], authenticated: true,
                       as: ConversationEnvelope.self).map(\.conversation)
    }

    @discardableResult
    func delete(_ conversationId: Int64) async -> ApiResponse<EmptyPayload> {
        await api.delete("/v1/chats/\(conversationId)", authenticated: true, as: EmptyPayload.self)
    }

    /// Empty `emoji` clears the signed-in user's reaction.
    @discardableResult
    func react(_ messageId: Int64, emoji: String) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/messages/\(messageId)/react", body: ["emoji": emoji], authenticated: true)
    }

    func edit(_ messageId: Int64, body: String) async -> ApiResponse<ChatMessageData> {
        await api.post("/v1/messages/\(messageId)/edit", body: ["body": body], authenticated: true,
                       as: MessageEnvelope.self).map(\.message)
    }

    @discardableResult
    func unsend(_ messageId: Int64) async -> ApiResponse<ChatMessageData> {
        await api.delete("/v1/messages/\(messageId)", authenticated: true, as: MessageEnvelope.self).map(\.message)
    }

    @discardableResult
    func block(_ userId: Int64, on: Bool) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/users/\(userId)/block", body: ["on": on], authenticated: true)
    }

    @discardableResult
    func report(_ userId: Int64, reason: String, conversationId: Int64 = 0, messageId: Int64 = 0) async -> ApiResponse<EmptyPayload> {
        var body: [String: Any] = ["reason": reason]
        if conversationId > 0 { body["conversation_id"] = String(conversationId) }
        if messageId > 0 { body["message_id"] = String(messageId) }
        return await api.post("/v1/users/\(userId)/report", body: body, authenticated: true)
    }
}

// MARK: - lenient field reads

private let rfc3339Fraction: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()

private let rfc3339Plain: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime]
    return f
}()

extension KeyedDecodingContainer {
    /// An id the server writes as a string (or a number in older payloads).
    func wireId(_ key: Key) -> Int64 {
        if let s = try? decodeIfPresent(String.self, forKey: key) { return Int64(s) ?? 0 }
        return (try? decodeIfPresent(Int64.self, forKey: key)) ?? 0
    }

    func int64(_ key: Key) -> Int64 {
        if let n = try? decodeIfPresent(Int64.self, forKey: key) { return n }
        if let s = try? decodeIfPresent(String.self, forKey: key) { return Int64(s) ?? 0 }
        return 0
    }

    func str(_ key: Key, _ fallback: String = "") -> String {
        (try? decodeIfPresent(String.self, forKey: key)) ?? fallback
    }

    func bool(_ key: Key) -> Bool {
        (try? decodeIfPresent(Bool.self, forKey: key)) ?? false
    }

    /// RFC3339 → epoch seconds; 0 when absent or unparseable.
    func epoch(_ key: Key) -> TimeInterval {
        guard let raw = try? decodeIfPresent(String.self, forKey: key), !raw.isEmpty else { return 0 }
        return (rfc3339Fraction.date(from: raw) ?? rfc3339Plain.date(from: raw))?.timeIntervalSince1970 ?? 0
    }
}
