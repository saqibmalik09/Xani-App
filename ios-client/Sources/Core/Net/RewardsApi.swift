import Foundation

/// Live-time rewards as the host sees them: what time on air earned, when the
/// required time was completed, when the balance moved and from what to what.
/// Every figure is the server's — the same ledger row support reads in the
/// dashboard — and every day is already the business day the admin's live-time
/// calendar defines.

/// How the server cuts reward days: same calendar as live time.
struct RewardCalendar: Decodable, Equatable {
    var tz = "UTC"
    var dayStart = "00:00"
    var weekStart = "monday"
    /// Today's business day, YYYY-MM-DD in `tz`.
    var today = ""
    /// Server clock when the reply was built, epoch seconds.
    var serverNow: TimeInterval = 0
    private enum CodingKeys: String, CodingKey { case tz, dayStart, weekStart, today, now }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tz = c.str(.tz, "UTC"); dayStart = c.str(.dayStart, "00:00"); weekStart = c.str(.weekStart, "monday")
        today = c.str(.today); serverNow = c.epoch(.now)
    }

    /// The business timezone, UTC when the name is unknown to this device.
    var zone: TimeZone { TimeZone(identifier: tz) ?? TimeZone(identifier: "UTC")! }
}

/// The rendered message and the local-time facts behind it.
struct RewardMessage: Decodable, Equatable {
    var title = "", body = "", detail = "", day = ""
    /// Local wall-clock text of the threshold crossing ("Fri 03 Oct 21:14:05").
    var reachedAt = ""
    /// Local wall-clock text of the wallet move; empty until added.
    var addedAt = ""
    var before: Int64?, after: Int64?
    var tz = "", status = "", statusLabel = ""
    private enum CodingKeys: String, CodingKey { case title, body, detail, day, reachedAt, addedAt, before, after, tz, status, statusLabel }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = c.str(.title); body = c.str(.body); detail = c.str(.detail); day = c.str(.day)
        reachedAt = c.str(.reachedAt); addedAt = c.str(.addedAt)
        before = c.int64OrNil(.before); after = c.int64OrNil(.after)
        tz = c.str(.tz); status = c.str(.status); statusLabel = c.str(.statusLabel)
    }
}

/// One reward. `status` is the user-facing word: pending | added | claimable | claimed | expired | cancelled.
struct RewardLedger: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    /// earn | manual | reversal
    var kind = "earn"
    var campaignId: Int64 = 0
    var campaign = ""
    var day = ""
    var periodKind = ""
    /// host | cohost
    var role = "host"
    var modeRule = ""
    var requiredMs: Int64 = 0
    var verifiedMs: Int64 = 0
    var asset = "coins"
    var amount: Int64 = 0
    var status = ""
    var statusLabel = ""
    var delivery = "direct"
    var reachedAt: TimeInterval = 0
    var earnedAt: TimeInterval = 0
    /// 0 until the balance moved.
    var issuedAt: TimeInterval = 0
    var claimableFrom: TimeInterval = 0
    var claimableUntil: TimeInterval = 0
    var claimedAt: TimeInterval = 0
    var balanceBefore: Int64?, balanceAfter: Int64?
    /// issued_at − reached_at: how late the credit was.
    var delayMs: Int64 = 0
    var message = RewardMessage()
    var note = ""

    var added: Bool { status == "added" || status == "claimed" }

    private enum CodingKeys: String, CodingKey {
        case id, kind, campaignId, campaign, day, periodKind, role, modeRule, requiredMs, verifiedMs, asset, amount, status, statusLabel
        case delivery, reachedAt, earnedAt, issuedAt, claimableFrom, claimableUntil, claimedAt, balanceBefore, balanceAfter, delayMs, message, note
    }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id); kind = c.str(.kind, "earn"); campaignId = c.wireId(.campaignId); campaign = c.str(.campaign)
        day = c.str(.day); periodKind = c.str(.periodKind); role = c.str(.role, "host"); modeRule = c.str(.modeRule)
        requiredMs = c.int64(.requiredMs); verifiedMs = c.int64(.verifiedMs); asset = c.str(.asset, "coins"); amount = c.int64(.amount)
        status = c.str(.status); statusLabel = c.str(.statusLabel); delivery = c.str(.delivery, "direct")
        reachedAt = c.epoch(.reachedAt); earnedAt = c.epoch(.earnedAt); issuedAt = c.epoch(.issuedAt)
        claimableFrom = c.epoch(.claimableFrom); claimableUntil = c.epoch(.claimableUntil); claimedAt = c.epoch(.claimedAt)
        balanceBefore = c.int64OrNil(.balanceBefore); balanceAfter = c.int64OrNil(.balanceAfter); delayMs = c.int64(.delayMs)
        message = (try? c.decodeIfPresent(RewardMessage.self, forKey: .message)) ?? RewardMessage()
        note = c.str(.note)
    }
}

/// asset → amount. Kept in the server's order (coins, diamonds, beans).
struct RewardAmounts: Decodable, Equatable {
    var entries: [(asset: String, amount: Int64)] = []
    var isEmpty: Bool { entries.isEmpty }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        var out: [(String, Int64)] = []
        for a in ["coins", "diamonds", "beans"] {
            if let v = try? c.decodeIfPresent(Int64.self, forKey: AnyKey(stringValue: a)!), v != 0 { out.append((a, v)) }
        }
        for k in c.allKeys where !["coins", "diamonds", "beans"].contains(k.stringValue) {
            if let v = try? c.decodeIfPresent(Int64.self, forKey: k), v != 0 { out.append((k.stringValue, v)) }
        }
        entries = out.map { (asset: $0.0, amount: $0.1) }
    }
    static func == (l: RewardAmounts, r: RewardAmounts) -> Bool {
        l.entries.count == r.entries.count && zip(l.entries, r.entries).allSatisfy { $0.asset == $1.asset && $0.amount == $1.amount }
    }
    /// "100 coins · 5 diamonds"
    var text: String { entries.map { "\($0.amount) \($0.asset)" }.joined(separator: " · ") }
}

private struct AnyKey: CodingKey {
    var stringValue: String
    var intValue: Int? { nil }
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { nil }
}

/// One business day inside the Today / Week / Month / All summary.
struct RewardDaySummary: Decodable, Equatable, Identifiable {
    var day = "", label = ""
    var liveMs: Int64 = 0
    var added = RewardAmounts()
    var count = 0
    var id: String { day }
    private enum CodingKeys: String, CodingKey { case day, label, liveMs, added, count }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        day = c.str(.day); label = c.str(.label); liveMs = c.int64(.liveMs)
        added = (try? c.decodeIfPresent(RewardAmounts.self, forKey: .added)) ?? RewardAmounts()
        count = Int(c.int64(.count))
    }
}

struct RewardSummary: Decodable, Equatable {
    /// today | week | month | all | custom
    var range = "today", from = "", to = ""
    var liveMs: Int64 = 0
    var earned = RewardAmounts(), added = RewardAmounts(), claimable = RewardAmounts(), pending = RewardAmounts()
    var count = 0
    /// Newest first.
    var days: [RewardDaySummary] = []
    private enum CodingKeys: String, CodingKey { case range, from, to, liveMs, earned, added, claimable, pending, count, days }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        range = c.str(.range, "today"); from = c.str(.from); to = c.str(.to); liveMs = c.int64(.liveMs)
        earned = (try? c.decodeIfPresent(RewardAmounts.self, forKey: .earned)) ?? RewardAmounts()
        added = (try? c.decodeIfPresent(RewardAmounts.self, forKey: .added)) ?? RewardAmounts()
        claimable = (try? c.decodeIfPresent(RewardAmounts.self, forKey: .claimable)) ?? RewardAmounts()
        pending = (try? c.decodeIfPresent(RewardAmounts.self, forKey: .pending)) ?? RewardAmounts()
        count = Int(c.int64(.count))
        days = (try? c.decodeIfPresent([RewardDaySummary].self, forKey: .days)) ?? []
    }
}

/// Per asset: what can be claimed right now and what is still waiting for its window.
struct RewardClaimable: Decodable, Equatable, Identifiable {
    var asset = "coins"
    var amount: Int64 = 0
    var count = 0
    var waitingAmount: Int64 = 0
    var nextOpensAt: TimeInterval = 0
    var id: String { asset }
    private enum CodingKeys: String, CodingKey { case asset, amount, count, waitingAmount, nextOpensAt }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        asset = c.str(.asset, "coins"); amount = c.int64(.amount); count = Int(c.int64(.count))
        waitingAmount = c.int64(.waitingAmount); nextOpensAt = c.epoch(.nextOpensAt)
    }
}

struct RewardHome: Decodable {
    var summary = RewardSummary()
    var claimable: [RewardClaimable] = []
    var calendar = RewardCalendar()
    var enabled = true
    private enum CodingKeys: String, CodingKey { case summary, claimable, calendar, enabled }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        summary = (try? c.decodeIfPresent(RewardSummary.self, forKey: .summary)) ?? RewardSummary()
        claimable = (try? c.decodeIfPresent([RewardClaimable].self, forKey: .claimable)) ?? []
        calendar = (try? c.decodeIfPresent(RewardCalendar.self, forKey: .calendar)) ?? RewardCalendar()
        enabled = (try? c.decodeIfPresent(Bool.self, forKey: .enabled)) ?? true
    }
}

/// History: rewards grouped by business day with that day's live time beside them.
struct RewardDayGroup: Decodable, Equatable, Identifiable {
    var day = "", label = ""
    var liveMs: Int64 = 0, audioMs: Int64 = 0, videoMs: Int64 = 0
    var totals = RewardAmounts()
    var rewards: [RewardLedger] = []
    var id: String { day }
    private enum CodingKeys: String, CodingKey { case day, label, liveMs, audioMs, videoMs, totals, rewards }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        day = c.str(.day); label = c.str(.label); liveMs = c.int64(.liveMs); audioMs = c.int64(.audioMs); videoMs = c.int64(.videoMs)
        totals = (try? c.decodeIfPresent(RewardAmounts.self, forKey: .totals)) ?? RewardAmounts()
        rewards = (try? c.decodeIfPresent([RewardLedger].self, forKey: .rewards)) ?? []
    }
}

struct RewardHistory: Decodable {
    var days: [RewardDayGroup] = []
    var cursor = ""
    var calendar = RewardCalendar()
    private enum CodingKeys: String, CodingKey { case days, cursor, calendar }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        days = (try? c.decodeIfPresent([RewardDayGroup].self, forKey: .days)) ?? []
        cursor = c.str(.cursor)
        calendar = (try? c.decodeIfPresent(RewardCalendar.self, forKey: .calendar)) ?? RewardCalendar()
    }
}

struct RewardTimelineStep: Decodable, Equatable, Identifiable {
    var at: TimeInterval = 0
    var local = "", what = ""
    var id: String { "\(at)|\(what)" }
    private enum CodingKeys: String, CodingKey { case at, local, what }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        at = c.epoch(.at); local = c.str(.local); what = c.str(.what)
    }
}

/// One reward in full: the row plus its timeline in local business time.
struct RewardDetail: Decodable {
    var reward = RewardLedger()
    var timeline: [RewardTimelineStep] = []
    var calendar = RewardCalendar()
    private enum CodingKeys: String, CodingKey { case timeline, calendar }
    init(from decoder: Decoder) throws {
        reward = try RewardLedger(from: decoder)
        let c = try decoder.container(keyedBy: CodingKeys.self)
        timeline = (try? c.decodeIfPresent([RewardTimelineStep].self, forKey: .timeline)) ?? []
        calendar = (try? c.decodeIfPresent(RewardCalendar.self, forKey: .calendar)) ?? RewardCalendar()
    }
}

struct RewardLevel: Decodable, Equatable, Identifiable {
    var thresholdMs: Int64 = 0
    var asset = "coins"
    var amount: Int64 = 0
    var amountCohost: Int64?
    var label = ""
    var reached = false, paid = false
    var id: Int64 { thresholdMs }
    private enum CodingKeys: String, CodingKey { case thresholdMs, asset, amount, amountCohost, label, reached, paid }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        thresholdMs = c.int64(.thresholdMs); asset = c.str(.asset, "coins"); amount = c.int64(.amount)
        amountCohost = c.int64OrNil(.amountCohost); label = c.str(.label); reached = c.bool(.reached); paid = c.bool(.paid)
    }
}

/// One campaign condition and whether this account passes it now.
struct RewardCondition: Decodable, Equatable {
    var type = ""
    var op = ""
    var required = ""
    var actual = ""
    var pass = false
    var reason = ""

    private enum CodingKeys: String, CodingKey { case type, op, required, actual, pass, reason }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = c.str(.type); op = c.str(.op); pass = c.bool(.pass); reason = c.str(.reason)
        required = Self.anyString(c, .required)
        actual = Self.anyString(c, .actual)
    }
    /// required/actual may be a string, number, or array — flatten for the chip.
    private static func anyString(_ c: KeyedDecodingContainer<CodingKeys>, _ k: CodingKeys) -> String {
        if let s = try? c.decodeIfPresent(String.self, forKey: k) { return s }
        if let n = try? c.decodeIfPresent(Double.self, forKey: k) {
            return n.truncatingRemainder(dividingBy: 1) == 0 ? String(Int64(n)) : String(n)
        }
        if let a = try? c.decodeIfPresent([String].self, forKey: k) { return a.joined(separator: ", ") }
        if let a = try? c.decodeIfPresent([Int64].self, forKey: k) { return a.map(String.init).joined(separator: ", ") }
        return ""
    }
}

/// Where I stand on one campaign right now: eligible, how far, what is next and when.
struct RewardCampaignStatus: Decodable, Equatable, Identifiable {
    var campaignId: Int64 = 0
    var campaign = ""
    var periodKind = "day"
    var eligible = false
    /// Machine reason when not eligible (ROLE_NOT_ALLOWED, CONDITION_FAILED, MAX_PER_DAY, …).
    var reason = ""
    var verifiedMs: Int64 = 0
    var nextThresholdMs: Int64 = 0
    var remainingMs: Int64 = 0
    var nextAmount: Int64 = 0
    var nextAsset = "coins"
    var nextDueAt: TimeInterval = 0
    var rewardsInPeriod = 0
    var maxTotal = 0
    var levels: [RewardLevel] = []
    var roles: [String] = []
    var modeRule = ""
    var delivery = "direct"
    var conditions: [RewardCondition] = []
    var id: Int64 { campaignId }

    /// All paid levels reached for this period: nothing more until it rolls.
    var complete: Bool { nextThresholdMs <= 0 && eligible }

    private enum CodingKeys: String, CodingKey {
        case campaignId, campaign, periodKind, eligible, reason, verifiedMs, nextThresholdMs, remainingMs, nextAmount, nextAsset, nextDueAt
        case rewardsInPeriod, maxTotal, levels, roles, modeRule, delivery, conditions
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        campaignId = c.wireId(.campaignId); campaign = c.str(.campaign); periodKind = c.str(.periodKind, "day")
        eligible = c.bool(.eligible); reason = c.str(.reason); verifiedMs = c.int64(.verifiedMs)
        nextThresholdMs = c.int64(.nextThresholdMs); remainingMs = c.int64(.remainingMs); nextAmount = c.int64(.nextAmount)
        nextAsset = c.str(.nextAsset, "coins"); nextDueAt = c.epoch(.nextDueAt); rewardsInPeriod = Int(c.int64(.rewardsInPeriod))
        maxTotal = Int(c.int64(.maxTotal)); levels = (try? c.decodeIfPresent([RewardLevel].self, forKey: .levels)) ?? []
        roles = (try? c.decodeIfPresent([String].self, forKey: .roles)) ?? []; modeRule = c.str(.modeRule); delivery = c.str(.delivery, "direct")
        conditions = (try? c.decodeIfPresent([RewardCondition].self, forKey: .conditions)) ?? []
    }
}

struct RewardEligibility: Decodable, Equatable {
    var live = false, counting = false
    var campaigns: [RewardCampaignStatus] = []
    var calendar = RewardCalendar()
    var claimable: [RewardClaimable] = []
    var enabled = true

    /// Anything I can earn by going live now?
    var anyEligible: Bool { enabled && campaigns.contains { $0.eligible && $0.nextThresholdMs > 0 } }
    /// The closest next reward across campaigns, for the HUD line.
    var next: RewardCampaignStatus? { campaigns.filter { $0.eligible && $0.nextThresholdMs > 0 }.min { $0.remainingMs < $1.remainingMs } }

    private enum CodingKeys: String, CodingKey { case eligibility, enabled }
    private enum Inner: String, CodingKey { case live, counting, campaigns, calendar, claimable }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = (try? c.decodeIfPresent(Bool.self, forKey: .enabled)) ?? true
        let e = try c.nestedContainer(keyedBy: Inner.self, forKey: .eligibility)
        live = e.bool(.live); counting = e.bool(.counting)
        campaigns = (try? e.decodeIfPresent([RewardCampaignStatus].self, forKey: .campaigns)) ?? []
        calendar = (try? e.decodeIfPresent(RewardCalendar.self, forKey: .calendar)) ?? RewardCalendar()
        claimable = (try? e.decodeIfPresent([RewardClaimable].self, forKey: .claimable)) ?? []
    }
}

struct RewardClaimResult: Decodable {
    var claimId: Int64 = 0
    var asset = "coins"
    var amount: Int64 = 0
    var count = 0
    var balance = Balance()
    var duplicate = false
    var rewards: [RewardLedger] = []
    private enum CodingKeys: String, CodingKey { case claim, rewards }
    private enum Inner: String, CodingKey { case claimId, asset, amount, count, balance, duplicate }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.nestedContainer(keyedBy: Inner.self, forKey: .claim) {
            claimId = i.wireId(.claimId); asset = i.str(.asset, "coins"); amount = i.int64(.amount); count = Int(i.int64(.count))
            balance = (try? i.decodeIfPresent(Balance.self, forKey: .balance)) ?? Balance(); duplicate = i.bool(.duplicate)
        }
        rewards = (try? c.decodeIfPresent([RewardLedger].self, forKey: .rewards)) ?? []
    }
}

/// The `reward_toast` hub event: one reward landed (or became claimable).
/// Surfaces dedupe on `ledgerId`; the server may replay it across reconnects.
struct RewardToast: Decodable, Equatable {
    var ledgerId: Int64 = 0
    var asset = "coins"
    var amount: Int64 = 0
    /// added | claimable | pending
    var status = "added"
    var title = "", body = ""
    var requiredMs: Int64 = 0
    var day = "", reachedAt = "", addedAt = ""
    var before: Int64?, after: Int64?
    private enum CodingKeys: String, CodingKey { case ledgerId, asset, amount, status, title, body, requiredMs, day, reachedAt, addedAt, before, after }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ledgerId = c.wireId(.ledgerId); asset = c.str(.asset, "coins"); amount = c.int64(.amount); status = c.str(.status, "added")
        title = c.str(.title); body = c.str(.body); requiredMs = c.int64(.requiredMs); day = c.str(.day)
        reachedAt = c.str(.reachedAt); addedAt = c.str(.addedAt); before = c.int64OrNil(.before); after = c.int64OrNil(.after)
    }
}

extension KeyedDecodingContainer {
    /// JSON `null` or absent → nil, else the number (string-wrapped accepted).
    func int64OrNil(_ key: Key) -> Int64? {
        if (try? decodeNil(forKey: key)) == true { return nil }
        if let n = try? decodeIfPresent(Int64.self, forKey: key) { return n }
        if let s = try? decodeIfPresent(String.self, forKey: key) { return Int64(s) }
        return nil
    }
}

struct RewardsApi {
    let api: ApiClient

    /// Today / Week / Month / All header plus per-day rows; what is claimable now; the business calendar.
    func me(range: String = "today", from: String = "", to: String = "") async -> ApiResponse<RewardHome> {
        var q = ["range": range]
        if !from.isEmpty { q["from"] = from }
        if !to.isEmpty { q["to"] = to }
        return await api.get("/v1/me/rewards", query: q, authenticated: true, as: RewardHome.self)
    }

    /// Rewards grouped by business day, newest first, keyset-paged.
    func history(from: String = "", to: String = "", status: String = "", asset: String = "", cursor: String = "", limit: Int = 50) async -> ApiResponse<RewardHistory> {
        var q = ["limit": String(limit)]
        if !from.isEmpty { q["from"] = from }
        if !to.isEmpty { q["to"] = to }
        if !status.isEmpty { q["status"] = status }
        if !asset.isEmpty { q["asset"] = asset }
        if !cursor.isEmpty { q["cursor"] = cursor }
        return await api.get("/v1/me/rewards/history", query: q, authenticated: true, as: RewardHistory.self)
    }

    func detail(_ id: Int64) async -> ApiResponse<RewardDetail> {
        await api.get("/v1/me/rewards/\(id)", authenticated: true, as: RewardDetail.self)
    }

    /// Pre-live sheet and HUD. `role` (host|cohost) and `mode` (audio|video) are the
    /// presumption for a user who is not live yet; a live user's open segment decides.
    func eligibility(role: String = "", mode: String = "") async -> ApiResponse<RewardEligibility> {
        var q: [String: String] = [:]
        if !role.isEmpty { q["role"] = role }
        if !mode.isEmpty { q["mode"] = mode }
        return await api.get("/v1/me/rewards/eligibility", query: q, authenticated: true, as: RewardEligibility.self)
    }

    /// Pays every claimable reward of `asset` whose window is open, once. `idemKey`
    /// makes a retry after a dropped reply return the first result, never pay twice.
    func claim(asset: String, idemKey: String) async -> ApiResponse<RewardClaimResult> {
        await api.post("/v1/me/rewards/claim", body: ["asset": asset, "idem_key": idemKey], authenticated: true, as: RewardClaimResult.self)
    }
}
