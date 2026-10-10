import Foundation

/// Gifting engine wire types: the rule-aware catalog tree the sheet lists
/// (every leaf carries whether this sender may send it to this receiver and,
/// when not, the reason in plain words), and the per-user statements the
/// Gifting tab shows — who sent what, who received what, for a window.

/// Whether a gift may go from the signed-in user to the chosen receiver.
struct GiftLockState: Decodable, Equatable {
    var allowed = true
    var hidden = false
    /// Machine code (`gift_<CODE>`), for logging and tests; `text` is for people.
    var reason = ""
    var text = ""
    static let open = GiftLockState()
    private enum CodingKeys: String, CodingKey { case allowed, hidden, reason, text }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        allowed = (try? c.decodeIfPresent(Bool.self, forKey: .allowed)) ?? true
        hidden = c.bool(.hidden); reason = c.str(.reason); text = c.str(.text)
    }
}

/// A gift as the engine lists it: the catalog row plus its lock state.
struct GiftLeafData: Decodable, Equatable, Identifiable {
    var gift = GiftData()
    var state = GiftLockState.open
    var id: Int64 { gift.id }
    init(gift: GiftData, state: GiftLockState = .open) { self.gift = gift; self.state = state }
    init(from decoder: Decoder) throws {
        // A leaf is the gift's own fields with `state` alongside.
        gift = try GiftData(from: decoder)
        let c = try decoder.container(keyedBy: Keys.self)
        state = (try? c.decodeIfPresent(GiftLockState.self, forKey: .state)) ?? .open
    }
    private enum Keys: String, CodingKey { case state }
}

/// One node of the category tree; a gift may sit under several nodes.
struct GiftCategoryNode: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var parentId: Int64 = 0
    var title = ""
    var lucky = false
    var gifts: [GiftLeafData] = []
    var children: [GiftCategoryNode] = []

    /// Own gifts followed by every descendant's, deduplicated by gift id.
    var allGifts: [GiftLeafData] {
        var seen = Set<Int64>()
        var out: [GiftLeafData] = []
        func walk(_ n: GiftCategoryNode) {
            for g in n.gifts where seen.insert(g.id).inserted { out.append(g) }
            n.children.forEach(walk)
        }
        walk(self)
        return out
    }

    private enum CodingKeys: String, CodingKey { case id, parentId, title, lucky, gifts, children }
    init(id: Int64, parentId: Int64 = 0, title: String, lucky: Bool, gifts: [GiftLeafData], children: [GiftCategoryNode] = []) {
        self.id = id; self.parentId = parentId; self.title = title; self.lucky = lucky; self.gifts = gifts; self.children = children
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); parentId = c.wireId(.parentId); title = c.str(.title); lucky = c.bool(.lucky)
        gifts = (try? c.decodeIfPresent([GiftLeafData].self, forKey: .gifts)) ?? []
        children = (try? c.decodeIfPresent([GiftCategoryNode].self, forKey: .children)) ?? []
    }
}

struct GiftTreeData: Decodable, Equatable {
    var categories: [GiftCategoryNode] = []
    var rev: Int64 = 0
    /// Rules version the states were computed against.
    var version: Int64 = 0
    static let empty = GiftTreeData()
    var all: [GiftLeafData] {
        var seen = Set<Int64>()
        return categories.flatMap(\.allGifts).filter { seen.insert($0.id).inserted }
    }
    private enum CodingKeys: String, CodingKey { case categories, rev, version }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        categories = (try? c.decodeIfPresent([GiftCategoryNode].self, forKey: .categories)) ?? []
        rev = c.int64(.rev); version = c.int64(.version)
    }
}

// MARK: - statements

struct GiftingTotals: Decodable, Equatable {
    var coins: Int64 = 0
    var value: Int64 = 0
    var diamonds: Int64 = 0
    var cashbackCoins: Int64 = 0
    var count: Int64 = 0
    private enum CodingKeys: String, CodingKey { case coins, value, diamonds, cashbackCoins, count }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        coins = c.int64(.coins); value = c.int64(.value); diamonds = c.int64(.diamonds)
        cashbackCoins = c.int64(.cashbackCoins); count = c.int64(.count)
    }
}

/// One counterpart in a statement: a sender (received view) or a receiver (sent view).
struct GiftingPeerData: Decodable, Equatable, Identifiable {
    var peer = LivePersonData()
    var coins: Int64 = 0
    var diamonds: Int64 = 0
    var count: Int64 = 0
    var lastAtMs: Int64 = 0
    var lastGiftId: Int64 = 0
    var listingUrl = ""
    var rank = 0
    var id: Int64 { peer.id }
    private enum CodingKeys: String, CodingKey { case peer, coins, diamonds, count, lastAtMs, lastGiftId, listingUrl, rank }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        peer = (try? c.decodeIfPresent(LivePersonData.self, forKey: .peer)) ?? LivePersonData()
        coins = c.int64(.coins); diamonds = c.int64(.diamonds); count = c.int64(.count)
        lastAtMs = c.int64(.lastAtMs); lastGiftId = c.wireId(.lastGiftId); listingUrl = c.str(.listingUrl)
        rank = Int(c.int64(.rank))
    }
}

/// One gift fact as the statement lists it (latest view).
struct GiftingRowData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var roomId: Int64 = 0
    var giftId: Int64 = 0
    var giftTitle = ""
    var listingUrl = ""
    var sender = LivePersonData()
    var receiver = LivePersonData()
    var count: Int64 = 0
    var coins: Int64 = 0
    var diamonds: Int64 = 0
    var cashbackCoins: Int64 = 0
    var atMs: Int64 = 0
    private enum CodingKeys: String, CodingKey {
        case id, roomId, giftId, giftTitle, listingUrl, sender, receiver, count, coins, diamonds, cashbackCoins, atMs
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); roomId = c.wireId(.roomId); giftId = c.wireId(.giftId)
        giftTitle = c.str(.giftTitle); listingUrl = c.str(.listingUrl)
        sender = (try? c.decodeIfPresent(LivePersonData.self, forKey: .sender)) ?? LivePersonData()
        receiver = (try? c.decodeIfPresent(LivePersonData.self, forKey: .receiver)) ?? LivePersonData()
        count = c.int64(.count); coins = c.int64(.coins); diamonds = c.int64(.diamonds)
        cashbackCoins = c.int64(.cashbackCoins); atMs = c.int64(.atMs)
    }
}

struct GiftingStatementData: Decodable, Equatable {
    var period = ""
    var from = ""
    var to = ""
    var totals = GiftingTotals()
    var peers: [GiftingPeerData] = []
    var gifts: [GiftingRowData] = []
    var nextCursor = ""
    var sort = "top"
    static let empty = GiftingStatementData()
    private enum CodingKeys: String, CodingKey { case period, from, to, totals, peers, gifts, nextCursor, sort }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        period = c.str(.period); from = c.str(.from); to = c.str(.to)
        totals = (try? c.decodeIfPresent(GiftingTotals.self, forKey: .totals)) ?? GiftingTotals()
        peers = (try? c.decodeIfPresent([GiftingPeerData].self, forKey: .peers)) ?? []
        gifts = (try? c.decodeIfPresent([GiftingRowData].self, forKey: .gifts)) ?? []
        nextCursor = c.str(.nextCursor); sort = c.str(.sort, "top")
    }
}

/// A gift landing for the signed-in user, off the user stream: `gift_received`
/// when they are the receiver, `gift_sent` when they are the sender. Carries
/// the day's running totals so a tab can move without a re-read.
struct GiftingUserEvent: Equatable {
    var received = false
    var id: Int64 = 0
    var peerId: Int64 = 0
    var giftId: Int64 = 0
    var title = ""
    var count: Int64 = 0
    var coins: Int64 = 0
    var diamonds: Int64 = 0
    var cashbackCoins: Int64 = 0
    var todayDiamonds: Int64 = 0
    var todayCount: Int64 = 0
    var todayCoins: Int64 = 0
    var roomId: Int64 = 0
    var atMs: Int64 = 0

    private struct Wire: Decodable {
        var id: Int64 = 0, senderId: Int64 = 0, receiverId: Int64 = 0, giftId: Int64 = 0
        var title = ""
        var count: Int64 = 0, coins: Int64 = 0, diamonds: Int64 = 0, cashbackCoins: Int64 = 0
        var todayDiamonds: Int64 = 0, todayCount: Int64 = 0, todayCoins: Int64 = 0
        var roomId: Int64 = 0, atMs: Int64 = 0
        private enum CodingKeys: String, CodingKey {
            case id, senderId, receiverId, giftId, title, count, coins, diamonds, cashbackCoins
            case todayDiamonds, todayCount, todayCoins, roomId, atMs
        }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = c.wireId(.id); senderId = c.wireId(.senderId); receiverId = c.wireId(.receiverId); giftId = c.wireId(.giftId)
            title = c.str(.title)
            count = c.int64(.count); coins = c.int64(.coins); diamonds = c.int64(.diamonds); cashbackCoins = c.int64(.cashbackCoins)
            todayDiamonds = c.int64(.todayDiamonds); todayCount = c.int64(.todayCount); todayCoins = c.int64(.todayCoins)
            roomId = c.wireId(.roomId); atMs = c.int64(.atMs)
        }
    }

    /// Decodes a `{data:{…}}` frame named `gift_received` / `gift_sent`; nil otherwise.
    static func decode(name: String, data: Data) -> GiftingUserEvent? {
        guard name == "gift_received" || name == "gift_sent" else { return nil }
        struct Frame: Decodable { let data: Wire }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        guard let f = try? decoder.decode(Frame.self, from: data) else { return nil }
        let w = f.data
        var e = GiftingUserEvent()
        e.received = name == "gift_received"
        e.id = w.id; e.peerId = e.received ? w.senderId : w.receiverId; e.giftId = w.giftId; e.title = w.title
        e.count = w.count; e.coins = w.coins; e.diamonds = w.diamonds; e.cashbackCoins = w.cashbackCoins
        e.todayDiamonds = w.todayDiamonds; e.todayCount = w.todayCount; e.todayCoins = w.todayCoins
        e.roomId = w.roomId; e.atMs = w.atMs
        return e
    }
}

// MARK: - API

/// Typed gifting-engine endpoints.
struct GiftingApi {
    let api: ApiClient

    /// The tree for this room and receiver, lock states included. Hidden gifts
    /// are already left out by the server.
    func catalog(roomId: Int64, receiverId: Int64) async -> ApiResponse<GiftTreeData> {
        var q: [String: String] = [:]
        if roomId > 0 { q["room_id"] = String(roomId) }
        if receiverId > 0 { q["receiver_id"] = String(receiverId) }
        return await api.get("/v1/gifting/catalog", query: q, authenticated: true, as: GiftTreeData.self)
    }

    /// What `userId` received. `period`: live | today | week | month | range.
    func received(
        userId: Int64, period: String, from: String = "", to: String = "",
        roomId: Int64 = 0, sort: String = "top", cursor: String = "", limit: Int = 50
    ) async -> ApiResponse<GiftingStatementData> {
        await api.get("/v1/users/\(userId)/gifting/received",
                      query: query(period, from, to, roomId, sort, cursor, limit),
                      authenticated: true, as: GiftingStatementData.self)
    }

    /// What `userId` sent.
    func sent(
        userId: Int64, period: String, from: String = "", to: String = "",
        roomId: Int64 = 0, sort: String = "top", cursor: String = "", limit: Int = 50
    ) async -> ApiResponse<GiftingStatementData> {
        await api.get("/v1/users/\(userId)/gifting/sent",
                      query: query(period, from, to, roomId, sort, cursor, limit),
                      authenticated: true, as: GiftingStatementData.self)
    }

    private func query(_ period: String, _ from: String, _ to: String, _ roomId: Int64, _ sort: String, _ cursor: String, _ limit: Int) -> [String: String] {
        var q: [String: String] = ["period": period, "sort": sort, "limit": String(limit)]
        if !from.isEmpty { q["from"] = from }
        if !to.isEmpty { q["to"] = to }
        if roomId > 0 { q["room_id"] = String(roomId) }
        if !cursor.isEmpty { q["cursor"] = cursor }
        return q
    }
}
