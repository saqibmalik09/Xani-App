import Foundation

// MARK: - Wire constants (agreed with the server: `store.Membership*`, `store.Request*`)

enum MembershipStatus {
    static let active = 2
    static let suspended = 3
    static let revoked = 4
    static let blocked = 5
}

enum RoleRequestKind {
    static let assign = 1
    static let apply = 2
    static let transfer = 3
    static let revoke = 4
    static let reactivate = 5
}

enum RoleRequestStatus {
    static let pending = 2
    static let approved = 3
    static let rejected = 4
    static let cancelled = 5
    static let expired = 6
}

// MARK: - Models

/// Role identity as the server prints it: permanent code, slug, title.
struct RoleBrief: Decodable, Equatable {
    let id: Int
    let code: String
    let slug: String
    let title: String
    let status: Int

    static let empty = RoleBrief(id: 0, code: "", slug: "", title: "", status: 0)

    init(id: Int, code: String, slug: String, title: String, status: Int) {
        self.id = id
        self.code = code
        self.slug = slug
        self.title = title
        self.status = status
    }

    private enum CodingKeys: String, CodingKey { case id, code, slug, title, name, status }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = Int(c.int64(.id))
        code = c.str(.code)
        slug = c.str(.slug)
        let t = c.str(.title)
        title = t.isEmpty ? c.str(.name) : t
        status = Int(c.int64(.status))
    }
}

/// One person's seat in the tree. Used for own roles, upline and downline rows alike.
struct MembershipData: Decodable, Identifiable, Equatable {
    let id: Int64
    let code: String
    let userId: Int64
    let parentId: Int64
    let rootId: Int64
    let status: Int
    let depth: Int
    let treeDepth: Int
    let directChildren: Int
    let username: String
    let fullName: String
    let avatarUrl: String
    let referralCode: String
    let approvedAt: String
    let createdAt: String
    let endedReason: String
    let role: RoleBrief

    var displayName: String { fullName.isEmpty ? "@\(username)" : fullName }
    var isActive: Bool { status == MembershipStatus.active }

    private enum CodingKeys: String, CodingKey {
        case id, code, userId, parentId, rootId, status, depth, treeDepth, directChildren
        case username, fullName, avatarUrl, referralCode, approvedAt, createdAt, endedReason, role
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        code = c.str(.code)
        userId = c.wireId(.userId)
        parentId = c.wireId(.parentId)
        rootId = c.wireId(.rootId)
        status = Int(c.int64(.status))
        depth = Int(c.int64(.depth))
        treeDepth = Int(c.int64(.treeDepth))
        directChildren = Int(c.int64(.directChildren))
        username = c.str(.username)
        fullName = c.str(.fullName)
        avatarUrl = c.str(.avatarUrl)
        referralCode = c.str(.referralCode)
        approvedAt = c.str(.approvedAt)
        createdAt = c.str(.createdAt)
        endedReason = c.str(.endedReason)
        role = (try? c.decodeIfPresent(RoleBrief.self, forKey: .role)) ?? .empty
    }
}

/// Compact person, as embedded in requests and code lookups.
struct UserBriefData: Decodable, Equatable {
    let id: Int64
    let username: String
    let fullName: String
    let avatarUrl: String

    static let empty = UserBriefData(id: 0, username: "", fullName: "", avatarUrl: "")

    var displayName: String { fullName.isEmpty ? "@\(username)" : fullName }

    init(id: Int64, username: String, fullName: String, avatarUrl: String) {
        self.id = id
        self.username = username
        self.fullName = fullName
        self.avatarUrl = avatarUrl
    }

    private enum CodingKeys: String, CodingKey { case id, username, fullName, avatarUrl }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        username = c.str(.username)
        fullName = c.str(.fullName)
        avatarUrl = c.str(.avatarUrl)
    }
}

/// Compact membership (id, code, person, role) as embedded in requests.
struct MembershipBriefData: Decodable, Equatable {
    let id: Int64
    let code: String
    let userId: Int64
    let username: String
    let fullName: String
    let status: Int
    let role: RoleBrief

    var displayName: String { fullName.isEmpty ? "@\(username)" : fullName }

    private enum CodingKeys: String, CodingKey { case id, code, userId, username, fullName, status, role }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        code = c.str(.code)
        userId = c.wireId(.userId)
        username = c.str(.username)
        fullName = c.str(.fullName)
        status = Int(c.int64(.status))
        role = (try? c.decodeIfPresent(RoleBrief.self, forKey: .role)) ?? .empty
    }
}

/// A role request: assign / application / transfer / removal / reactivation.
/// `canDecide` and `canCancel` are the server's verdict for the *caller*; the UI
/// shows buttons from them and the server re-checks on tap.
struct RoleRequestData: Decodable, Identifiable, Equatable {
    let id: Int64
    let code: String
    let kind: Int
    let status: Int
    let subject: UserBriefData
    let subjectUserId: Int64
    let role: RoleBrief
    let parent: MembershipBriefData?
    let membership: MembershipBriefData?
    let requestedBy: UserBriefData
    let requestedById: Int64
    let decidedBy: UserBriefData?
    let note: String
    let reason: String
    let childrenOnly: Bool
    let downlineCount: Int
    let createdAt: String
    let decidedAt: String
    let expiresAt: String
    let canDecide: Bool
    let canCancel: Bool
    let kindLabel: String
    let statusLabel: String

    var isPending: Bool { status == RoleRequestStatus.pending }

    private enum CodingKeys: String, CodingKey {
        case id, code, kind, status, subject, subjectUserId, role, parent, membership
        case requestedByUser, requestedBy, decidedByUser, note, reason, childrenOnly, downlineCount
        case createdAt, decidedAt, expiresAt, canDecide, canCancel, kindLabel, statusLabel
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.wireId(.id)
        code = c.str(.code)
        kind = Int(c.int64(.kind))
        status = Int(c.int64(.status))
        subject = (try? c.decodeIfPresent(UserBriefData.self, forKey: .subject)) ?? .empty
        subjectUserId = c.wireId(.subjectUserId)
        role = (try? c.decodeIfPresent(RoleBrief.self, forKey: .role)) ?? .empty
        let p = try? c.decodeIfPresent(MembershipBriefData.self, forKey: .parent)
        parent = (p?.id ?? 0) > 0 ? p : nil
        let m = try? c.decodeIfPresent(MembershipBriefData.self, forKey: .membership)
        membership = (m?.id ?? 0) > 0 ? m : nil
        requestedBy = (try? c.decodeIfPresent(UserBriefData.self, forKey: .requestedByUser)) ?? .empty
        requestedById = c.wireId(.requestedBy)
        decidedBy = try? c.decodeIfPresent(UserBriefData.self, forKey: .decidedByUser)
        note = c.str(.note)
        reason = c.str(.reason)
        childrenOnly = c.bool(.childrenOnly)
        downlineCount = Int(c.int64(.downlineCount))
        createdAt = c.str(.createdAt)
        decidedAt = c.str(.decidedAt)
        expiresAt = c.str(.expiresAt)
        canDecide = c.bool(.canDecide)
        canCancel = c.bool(.canCancel)
        kindLabel = c.str(.kindLabel)
        statusLabel = c.str(.statusLabel)
    }
}

/// A code the caller can share, and which roles it onboards.
struct ReferralCodeData: Decodable, Identifiable, Equatable {
    let code: String
    let membershipId: Int64
    let role: RoleBrief
    let targetRoles: [RoleBrief]

    var id: String { code }

    private enum CodingKeys: String, CodingKey { case code, membershipId, role, targetRoles }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        code = c.str(.code)
        membershipId = c.wireId(.membershipId)
        role = (try? c.decodeIfPresent(RoleBrief.self, forKey: .role)) ?? .empty
        targetRoles = (try? c.decodeIfPresent([RoleBrief].self, forKey: .targetRoles)) ?? []
    }
}

/// What a code someone typed resolves to, before they commit to applying.
struct CodeInfoData: Decodable, Equatable {
    let code: String
    let owner: UserBriefData
    let ownerRole: RoleBrief
    let targetRoles: [RoleBrief]

    private struct MembershipRole: Decodable {
        let role: RoleBrief
        private enum CodingKeys: String, CodingKey { case role }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            role = (try? c.decodeIfPresent(RoleBrief.self, forKey: .role)) ?? .empty
        }
    }

    private enum CodingKeys: String, CodingKey { case code, owner, membership, targetRoles }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        code = c.str(.code)
        owner = (try? c.decodeIfPresent(UserBriefData.self, forKey: .owner)) ?? .empty
        ownerRole = (try? c.decodeIfPresent(MembershipRole.self, forKey: .membership))?.role ?? .empty
        targetRoles = (try? c.decodeIfPresent([RoleBrief].self, forKey: .targetRoles)) ?? []
    }
}

struct DownlineSummaryRow: Decodable, Identifiable, Equatable {
    let role: RoleBrief
    let status: Int
    let count: Int

    var id: String { "\(role.id):\(status)" }

    private enum CodingKeys: String, CodingKey { case role, status, count }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        role = (try? c.decodeIfPresent(RoleBrief.self, forKey: .role)) ?? .empty
        status = Int(c.int64(.status))
        count = Int(c.int64(.count))
    }
}

private struct PendingCount: Decodable {
    let pending: Int
    private enum CodingKeys: String, CodingKey { case pending }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        pending = Int(c.int64(.pending))
    }
}

// MARK: - API

/// Organisation roles for the signed-in account: what they hold, who is above and
/// below them, codes they can share, requests in flight and — for uplines — the
/// approvals inbox. Every write is pending-by-default on the server; a response with
/// status APPROVED only happens when the caller could approve their own action.
struct RolesApi {
    let api: ApiClient

    private static let pageSize = "30"

    func myMemberships() async -> ApiResponse<[MembershipData]> {
        await api.get("/v1/me/memberships", authenticated: true, as: ItemsResponse<MembershipData>.self).map(\.items)
    }

    func myCodes() async -> ApiResponse<[ReferralCodeData]> {
        await api.get("/v1/me/referral-codes", authenticated: true, as: ItemsResponse<ReferralCodeData>.self).map(\.items)
    }

    func myRequests(cursor: String) async -> ApiResponse<ItemsResponse<RoleRequestData>> {
        await api.get("/v1/me/requests", query: page(cursor), authenticated: true, as: ItemsResponse<RoleRequestData>.self)
    }

    func lookupCode(_ code: String) async -> ApiResponse<CodeInfoData> {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        let escaped = trimmed.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? trimmed
        return await api.get("/v1/referral/codes/\(escaped)", authenticated: true, as: CodeInfoData.self)
    }

    /// "I have a code": the code fixes both the role and the upline.
    func redeem(code: String, note: String) async -> ApiResponse<RoleRequestData> {
        await api.post(
            "/v1/referral/redeem",
            body: ["code": code.trimmingCharacters(in: .whitespacesAndNewlines), "note": note],
            authenticated: true,
            as: RoleRequestData.self
        )
    }

    /// Apply for one specific role a code opens (codes that onboard several roles).
    func apply(code: String, roleSlug: String, note: String) async -> ApiResponse<RoleRequestData> {
        await api.post(
            "/v1/requests/apply",
            body: ["referral_code": code.trimmingCharacters(in: .whitespacesAndNewlines), "role": roleSlug, "note": note],
            authenticated: true,
            as: RoleRequestData.self
        )
    }

    func membership(_ id: Int64) async -> ApiResponse<MembershipData> {
        await api.get("/v1/memberships/\(id)", authenticated: true, as: MembershipData.self)
    }

    func upline(_ id: Int64) async -> ApiResponse<[MembershipData]> {
        await api.get("/v1/memberships/\(id)/upline", authenticated: true, as: ItemsResponse<MembershipData>.self).map(\.items)
    }

    func summary(_ id: Int64) async -> ApiResponse<[DownlineSummaryRow]> {
        await api.get("/v1/memberships/\(id)/summary", authenticated: true, as: ItemsResponse<DownlineSummaryRow>.self).map(\.items)
    }

    /// `depth` 1 = direct only; 0 = everyone below (server caps the walk).
    func downline(_ id: Int64, depth: Int, cursor: String, query: String = "") async -> ApiResponse<ItemsResponse<MembershipData>> {
        var q = page(cursor)
        if depth > 0 { q["depth"] = String(depth) }
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { q["q"] = trimmed }
        return await api.get("/v1/memberships/\(id)/downline", query: q, authenticated: true, as: ItemsResponse<MembershipData>.self)
    }

    func request(_ id: Int64) async -> ApiResponse<RoleRequestData> {
        await api.get("/v1/requests/\(id)", authenticated: true, as: RoleRequestData.self)
    }

    /// Requests waiting on the caller (or visible to them), newest first.
    func inbox(status: Int, cursor: String) async -> ApiResponse<ItemsResponse<RoleRequestData>> {
        var q = page(cursor)
        if status > 0 { q["status"] = String(status) }
        return await api.get("/v1/requests/inbox", query: q, authenticated: true, as: ItemsResponse<RoleRequestData>.self)
    }

    func inboxCount() async -> ApiResponse<Int> {
        await api.get("/v1/requests/inbox/count", authenticated: true, as: PendingCount.self).map(\.pending)
    }

    func approve(_ id: Int64, note: String) async -> ApiResponse<RoleRequestData> {
        await api.post("/v1/requests/\(id)/approve", body: ["note": note], authenticated: true, as: RoleRequestData.self)
    }

    func reject(_ id: Int64, reason: String) async -> ApiResponse<RoleRequestData> {
        await api.post("/v1/requests/\(id)/reject", body: ["reason": reason], authenticated: true, as: RoleRequestData.self)
    }

    func cancel(_ id: Int64, reason: String) async -> ApiResponse<RoleRequestData> {
        await api.post("/v1/requests/\(id)/cancel", body: ["reason": reason], authenticated: true, as: RoleRequestData.self)
    }

    private func page(_ cursor: String) -> [String: String] {
        cursor.isEmpty ? ["limit": Self.pageSize] : ["limit": Self.pageSize, "cursor": cursor]
    }
}
