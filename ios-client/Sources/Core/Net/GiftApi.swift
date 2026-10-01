import Foundation

/// Live gift wire types. The catalog is what the sheet lists; a `LiveGiftEvent`
/// is one gift landing in a room — the same frame the sender gets as a reply
/// and everyone else gets on the room stream.

struct GiftData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var categoryId: Int64 = 0
    var title = ""
    var coinPrice: Int64 = 0
    var listingUrl = ""
    var animationUrl = ""
    var animationMime = ""
    var soundUrl = ""
    var soundMime = ""
    /// How long every viewer holds it on screen; 0 = animation's own length.
    var durationMs = 0
    /// Lucky: the sheet keeps it selected and every tap sends again with a combo.
    var lucky = false

    var isSvga: Bool { animationMime.contains("svga") || animationUrl.lowercased().hasSuffix(".svga") }

    private enum CodingKeys: String, CodingKey {
        case id, categoryId, title, coinPrice, listingUrl, animationUrl, animationMime, soundUrl, soundMime, durationMs, lucky
    }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); categoryId = c.wireId(.categoryId); title = c.str(.title)
        coinPrice = c.int64(.coinPrice); listingUrl = c.str(.listingUrl)
        animationUrl = c.str(.animationUrl); animationMime = c.str(.animationMime)
        soundUrl = c.str(.soundUrl); soundMime = c.str(.soundMime)
        durationMs = Int(c.int64(.durationMs)); lucky = c.bool(.lucky)
    }
}

struct GiftCategoryData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var title = ""
    var lucky = false
    var gifts: [GiftData] = []
    private enum CodingKeys: String, CodingKey { case id, title, lucky, gifts }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); title = c.str(.title); lucky = c.bool(.lucky)
        gifts = (try? c.decodeIfPresent([GiftData].self, forKey: .gifts)) ?? []
    }
}

struct GiftCatalogData: Decodable, Equatable {
    var categories: [GiftCategoryData] = []
    var rev: Int64 = 0
    var all: [GiftData] { categories.flatMap(\.gifts) }
    static let empty = GiftCatalogData()
    private enum CodingKeys: String, CodingKey { case categories, rev }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        categories = (try? c.decodeIfPresent([GiftCategoryData].self, forKey: .categories)) ?? []
        rev = c.int64(.rev)
    }
}

struct LivePersonData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var username = ""
    var fullName = ""
    var avatarUrl = ""
    /// host | guest | viewer
    var role = "viewer"
    /// The avatar frame this person wears right now; empty = none.
    var frameUrl = ""
    var frameMime = ""
    private enum CodingKeys: String, CodingKey { case id, username, fullName, avatarUrl, role, frameUrl, frameMime }
    init(id: Int64 = 0, username: String = "", fullName: String = "", avatarUrl: String = "", role: String = "viewer") {
        self.id = id; self.username = username; self.fullName = fullName; self.avatarUrl = avatarUrl; self.role = role
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); username = c.str(.username); fullName = c.str(.fullName)
        avatarUrl = c.str(.avatarUrl); role = c.str(.role, "viewer")
        frameUrl = c.str(.frameUrl); frameMime = c.str(.frameMime)
    }
    var displayName: String { fullName.isEmpty ? username : fullName }
}

/// One chat line in a room: who said what. `clientId` is the sender's own
/// optimistic id, echoed so their bubble settles; empty on everyone else's.
struct LiveChatData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var roomId: Int64 = 0
    var clientId = ""
    var user = LivePersonData()
    var text = ""
    var atMs: Int64 = 0
    private enum CodingKeys: String, CodingKey { case id, roomId, clientId, user, text, atMs }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); roomId = c.wireId(.roomId); clientId = c.str(.clientId)
        user = (try? c.decodeIfPresent(LivePersonData.self, forKey: .user)) ?? LivePersonData()
        text = c.str(.text); atMs = c.int64(.atMs)
    }
    /// List identity that survives the optimistic → settled swap: the server
    /// id once known, else the sender's own clientId.
    var stableKey: String { id != 0 ? "i\(id)" : "c\(clientId)" }
}

struct LivePeopleData: Decodable {
    var items: [LivePersonData] = []
    var viewers: Int64 = 0
    private enum CodingKeys: String, CodingKey { case items, viewers }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = (try? c.decodeIfPresent([LivePersonData].self, forKey: .items)) ?? []
        viewers = c.int64(.viewers)
    }
}

/// One gift landing in a room.
struct LiveGiftEvent: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var roomId: Int64 = 0
    /// The sender's own idempotency key; empty on frames from other people.
    var clientId = ""
    var gift = GiftData()
    var sender = LivePersonData()
    var receiver = LivePersonData()
    var count = 1
    var combo = 1
    var totalCoins: Int64 = 0
    /// What landed in the receiver's wallet (their share of totalCoins).
    var receiverDiamonds: Int64 = 0
    /// The room's running gift total after this one.
    var roomCoins: Int64 = 0
    var atMs: Int64 = 0

    private enum CodingKeys: String, CodingKey {
        case id, roomId, clientId, gift, sender, receiver, count, combo, totalCoins, receiverDiamonds, roomCoins, atMs
    }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); roomId = c.wireId(.roomId); clientId = c.str(.clientId)
        gift = (try? c.decodeIfPresent(GiftData.self, forKey: .gift)) ?? GiftData()
        sender = (try? c.decodeIfPresent(LivePersonData.self, forKey: .sender)) ?? LivePersonData()
        receiver = (try? c.decodeIfPresent(LivePersonData.self, forKey: .receiver)) ?? LivePersonData()
        count = max(1, Int(c.int64(.count))); combo = max(1, Int(c.int64(.combo)))
        totalCoins = c.int64(.totalCoins); receiverDiamonds = c.int64(.receiverDiamonds)
        roomCoins = c.int64(.roomCoins); atMs = c.int64(.atMs)
    }
}

struct SendGiftData: Decodable {
    var gift = LiveGiftEvent()
    var balance = Balance()
    var replayed = false
    private enum CodingKeys: String, CodingKey { case gift, balance, replayed }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        gift = (try? c.decodeIfPresent(LiveGiftEvent.self, forKey: .gift)) ?? LiveGiftEvent()
        balance = (try? c.decodeIfPresent(Balance.self, forKey: .balance)) ?? Balance()
        replayed = c.bool(.replayed)
    }
}

/// Typed gift endpoints.
struct GiftApi {
    let api: ApiClient

    func catalog() async -> ApiResponse<GiftCatalogData> {
        await api.get("/v1/gifts/catalog", authenticated: true, as: GiftCatalogData.self)
    }

    func people(_ roomId: Int64, limit: Int = 50) async -> ApiResponse<LivePeopleData> {
        await api.get("/v1/live/rooms/\(roomId)/people", query: ["limit": String(limit)], authenticated: true, as: LivePeopleData.self)
    }

    /// Sends gift `giftId` to `receiverId` (0 = host). `clientId` is the
    /// idempotency key: a retry lands the same gift once and debits once.
    func send(roomId: Int64, giftId: Int64, receiverId: Int64, clientId: String, count: Int = 1, combo: Int = 1) async -> ApiResponse<SendGiftData> {
        await api.post(
            "/v1/live/rooms/\(roomId)/gifts",
            body: [
                "gift_id": String(giftId), "receiver_id": String(receiverId),
                "count": count, "combo": combo, "client_id": clientId,
            ],
            authenticated: true, as: SendGiftData.self
        )
    }

    /// The room's own stream: gift frames and the end notice for everyone inside.
    func stream(roomId: Int64, onEvent: @escaping @Sendable (String, Data) -> Void) async {
        await api.stream("/v1/live/rooms/\(roomId)/stream", onEvent: onEvent)
    }
}
