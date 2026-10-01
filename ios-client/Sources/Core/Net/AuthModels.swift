import Foundation

/// A country carries all three identifiers at once — numeric id, ISO alpha-2 and
/// calling code — so a picker can show "Pakistan +92" and a signup can post an
/// unambiguous value without a second lookup.
struct Country: Decodable, Identifiable, Equatable {
    let id: Int
    let iso2: String
    let iso3: String
    let name: String
    let phoneCode: Int

    var dialCode: String { "+\(phoneCode)" }
}

/// Ids are strings on the wire because a snowflake exceeds the precision of a
/// JavaScript number; they stay strings here because the client only ever compares
/// and displays them.
struct AccountUser: Decodable, Equatable {
    let id: String
    let username: String
    let fullName: String
    let email: String
    let phone: String
    let emailVerified: Bool
    let avatarURL: String
    let location: String
    let bio: String
    let birthday: String
    let country: Country?
    let passwordLoginActive: Bool

    enum CodingKeys: String, CodingKey {
        case id, username, fullName, email, phone, emailVerified
        case avatarURL = "avatarUrl"
        case location, bio, birthday, country, passwordLoginActive
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? container.decode(String.self, forKey: .id)) ?? ""
        username = (try? container.decode(String.self, forKey: .username)) ?? ""
        fullName = (try? container.decode(String.self, forKey: .fullName)) ?? ""
        email = (try? container.decode(String.self, forKey: .email)) ?? ""
        phone = (try? container.decode(String.self, forKey: .phone)) ?? ""
        emailVerified = (try? container.decode(Bool.self, forKey: .emailVerified)) ?? false
        avatarURL = (try? container.decode(String.self, forKey: .avatarURL)) ?? ""
        location = (try? container.decode(String.self, forKey: .location)) ?? ""
        bio = (try? container.decode(String.self, forKey: .bio)) ?? ""
        birthday = (try? container.decode(String.self, forKey: .birthday)) ?? ""
        country = try? container.decodeIfPresent(Country.self, forKey: .country)
        passwordLoginActive = (try? container.decode(Bool.self, forKey: .passwordLoginActive)) ?? false
    }

    private init() {
        id = ""; username = ""; fullName = ""; email = ""; phone = ""
        emailVerified = false; avatarURL = ""; location = ""; bio = ""; birthday = ""
        country = nil; passwordLoginActive = false
    }

    static let empty = AccountUser()
}

/// Scope ids agreed with the server (`store.Scope*`).
enum Scope {
    static let global = 1
    static let liveRoom = 2
    static let agency = 3
    static let country = 4
}

/// Permissions that apply only inside one target — one live room, one agency, one
/// country. This is how a single account can be an ordinary user everywhere and an
/// agency admin inside agency 42.
struct ScopedPermissions: Decodable, Equatable {
    let scopeType: Int
    let scopeId: String
    let permissions: [String]
}

/// One role the account holds inside the organisation tree, with the permissions
/// that role grants and how far down the tree each reaches (1 self, 2 direct
/// downline, 3 whole downline, 4 whole platform).
struct MembershipContext: Decodable, Equatable {
    let membershipId: String
    let parentId: String
    let rootId: String
    let roleId: Int
    let roleSlug: String
    let roleTitle: String
    let roleCode: String
    /// permission slug → reach
    let permissions: [String: Int]

    private struct Perm: Decodable {
        let slug: String
        let reach: Int
        private enum CodingKeys: String, CodingKey { case slug, reach }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            slug = (try? c.decode(String.self, forKey: .slug)) ?? ""
            reach = (try? c.decode(Int.self, forKey: .reach)) ?? 0
        }
    }

    private enum CodingKeys: String, CodingKey {
        case membershipId, parentId, rootId, roleId, roleSlug, roleTitle, roleCode, permissions
    }

    init(membershipId: String, parentId: String = "", rootId: String = "", roleId: Int = 0,
         roleSlug: String = "", roleTitle: String = "", roleCode: String = "", permissions: [String: Int] = [:]) {
        self.membershipId = membershipId
        self.parentId = parentId
        self.rootId = rootId
        self.roleId = roleId
        self.roleSlug = roleSlug
        self.roleTitle = roleTitle
        self.roleCode = roleCode
        self.permissions = permissions
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        membershipId = (try? c.decode(String.self, forKey: .membershipId)) ?? ""
        parentId = (try? c.decode(String.self, forKey: .parentId)) ?? ""
        rootId = (try? c.decode(String.self, forKey: .rootId)) ?? ""
        roleId = (try? c.decode(Int.self, forKey: .roleId)) ?? 0
        roleSlug = (try? c.decode(String.self, forKey: .roleSlug)) ?? ""
        roleTitle = (try? c.decode(String.self, forKey: .roleTitle)) ?? ""
        roleCode = (try? c.decode(String.self, forKey: .roleCode)) ?? ""
        var map: [String: Int] = [:]
        for p in (try? c.decode([Perm].self, forKey: .permissions)) ?? [] where !p.slug.isEmpty {
            map[p.slug] = p.reach
        }
        permissions = map
    }

    func holds(_ permission: String) -> Bool { permissions[permission] != nil }

    /// Plist-safe form for the account cache.
    func toDict() -> [String: Any] {
        [
            "membership_id": membershipId, "parent_id": parentId, "root_id": rootId,
            "role_id": roleId, "role_slug": roleSlug, "role_title": roleTitle, "role_code": roleCode,
            "permissions": permissions.map { ["slug": $0.key, "reach": $0.value] as [String: Any] },
        ]
    }

    static func fromDict(_ o: [String: Any]) -> MembershipContext? {
        guard let id = o["membership_id"] as? String, !id.isEmpty else { return nil }
        var perms: [String: Int] = [:]
        for p in o["permissions"] as? [[String: Any]] ?? [] {
            if let slug = p["slug"] as? String, !slug.isEmpty {
                perms[slug] = (p["reach"] as? Int) ?? (p["reach"] as? NSNumber)?.intValue ?? 0
            }
        }
        return MembershipContext(
            membershipId: id,
            parentId: o["parent_id"] as? String ?? "",
            rootId: o["root_id"] as? String ?? "",
            roleId: (o["role_id"] as? Int) ?? (o["role_id"] as? NSNumber)?.intValue ?? 0,
            roleSlug: o["role_slug"] as? String ?? "",
            roleTitle: o["role_title"] as? String ?? "",
            roleCode: o["role_code"] as? String ?? "",
            permissions: perms
        )
    }
}

/// What the signed-in account may do.
///
/// Roles are labels for display ("Host", "Agency Admin"); every gate in the UI checks
/// a permission instead, so adding a role on the server never needs an app release.
struct Access: Decodable, Equatable {
    let roles: [String]
    let permissions: [String]
    let scopedPermissions: [ScopedPermissions]
    let memberships: [MembershipContext]

    // Declared explicitly: providing `init(from:)` stops the compiler synthesising
    // `CodingKeys`, and these keys are read against the *parent* container.
    private enum CodingKeys: String, CodingKey {
        case roles, permissions, scopedPermissions, memberships
    }

    init(roles: [String] = [], permissions: [String] = [], scopedPermissions: [ScopedPermissions] = [],
         memberships: [MembershipContext] = []) {
        self.roles = roles
        self.permissions = permissions
        self.scopedPermissions = scopedPermissions
        self.memberships = memberships
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        roles = (try? container.decode([String].self, forKey: .roles)) ?? []
        permissions = (try? container.decode([String].self, forKey: .permissions)) ?? []
        scopedPermissions = (try? container.decode([ScopedPermissions].self, forKey: .scopedPermissions)) ?? []
        memberships = (try? container.decode([MembershipContext].self, forKey: .memberships)) ?? []
    }

    static let none = Access()

    /// Platform-wide check.
    func can(_ permission: String) -> Bool { permissions.contains(permission) }

    /// Held anywhere: platform-wide or through any membership at any reach. Use it to
    /// decide whether to *show* an organisation surface; the server decides per row.
    func holdsAnywhere(_ permission: String) -> Bool {
        permissions.contains(permission) || memberships.contains { $0.holds(permission) }
    }

    /// True when the account sits somewhere in the organisation tree.
    var hasMembership: Bool { !memberships.isEmpty }

    /// Scoped check. A permission held globally wins everywhere, which is what makes a
    /// platform moderator able to act inside a room they were never assigned to.
    func can(_ permission: String, scopeType: Int, scopeId: String) -> Bool {
        if permissions.contains(permission) { return true }
        return scopedPermissions.contains {
            $0.scopeType == scopeType && $0.scopeId == scopeId && $0.permissions.contains(permission)
        }
    }

    func hasRole(_ role: String) -> Bool { roles.contains(role) }
}

/// Tokens as the server issued them.
///
/// Expiry is computed from the server's *relative* `expiresIn` rather than its
/// absolute timestamp, because device clocks are wrong often enough that trusting
/// wall time signs people out at random.
struct Tokens: Equatable {
    let accessToken: String
    let refreshToken: String
    let accessExpiresAt: Date
    let refreshExpiresAt: Date

    func isAccessExpired(now: Date = Date(), skew: TimeInterval = 30) -> Bool {
        now >= accessExpiresAt.addingTimeInterval(-skew)
    }

    func isRefreshExpired(now: Date = Date()) -> Bool { now >= refreshExpiresAt }

    static let empty = Tokens(accessToken: "", refreshToken: "",
                              accessExpiresAt: .distantPast, refreshExpiresAt: .distantPast)
}

extension Tokens: Decodable {
    private enum CodingKeys: String, CodingKey {
        case accessToken, refreshToken, accessExpiresIn, refreshExpiresIn
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let now = Date()
        accessToken = (try? container.decode(String.self, forKey: .accessToken)) ?? ""
        refreshToken = (try? container.decode(String.self, forKey: .refreshToken)) ?? ""
        let accessIn = (try? container.decode(Double.self, forKey: .accessExpiresIn)) ?? 600
        let refreshIn = (try? container.decode(Double.self, forKey: .refreshExpiresIn)) ?? 72 * 3600
        accessExpiresAt = now.addingTimeInterval(accessIn)
        refreshExpiresAt = now.addingTimeInterval(refreshIn)
    }
}

/// One response that carries identity, authority and credentials.
struct Session: Decodable {
    let user: AccountUser
    let access: Access
    let tokens: Tokens
    let created: Bool

    private enum CodingKeys: String, CodingKey {
        case user, tokens, created
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        user = (try? container.decode(AccountUser.self, forKey: .user)) ?? .empty
        tokens = (try? container.decode(Tokens.self, forKey: .tokens)) ?? .empty
        created = (try? container.decode(Bool.self, forKey: .created)) ?? false
        // Roles and permissions sit beside `user` at the root of `data`, not inside it.
        access = (try? Access(from: decoder)) ?? .none
    }
}

/// Current identity and authority, without minting new tokens.
struct MeResponse: Decodable {
    let user: AccountUser
    let access: Access

    private enum CodingKeys: String, CodingKey { case user }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        user = (try? container.decode(AccountUser.self, forKey: .user)) ?? .empty
        access = (try? Access(from: decoder)) ?? .none
    }
}

/// Result of post-Google profile completion.
struct CompleteSocialPayload: Decodable {
    let user: AccountUser
}

/// Server bootstrap: the app name and which sign-in methods are live.
///
/// Every field is defaulted rather than required. This is the first call the app makes
/// and it must survive a server that has grown or dropped a key, otherwise a config
/// deploy bricks every installed build.
struct AppConfig: Decodable {
    let appName: String
    let signupEnabled: Bool
    let googleEnabled: Bool
    let sessionHours: Int
    let codeResendSeconds: Int
    let maxAccountsPerDevice: Int
    /// Where relative media keys resolve. Never stored with a key; combined at render.
    let mediaBaseUrl: String
    let maxVideoSeconds: Int
    let maxVideoMb: Int
    let maxImageMb: Int
    let maxCaption: Int

    private enum CodingKeys: String, CodingKey {
        case appName, signupEnabled, googleEnabled, sessionHours
        case codeResendSeconds, maxAccountsPerDevice
        case mediaBaseUrl, mediaMaxVideoSeconds, mediaMaxVideoMb, mediaMaxImageMb, mediaMaxCaption
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        appName = (try? container.decode(String.self, forKey: .appName)) ?? ""
        signupEnabled = (try? container.decode(Bool.self, forKey: .signupEnabled)) ?? true
        googleEnabled = (try? container.decode(Bool.self, forKey: .googleEnabled)) ?? false
        sessionHours = (try? container.decode(Int.self, forKey: .sessionHours)) ?? 72
        codeResendSeconds = (try? container.decode(Int.self, forKey: .codeResendSeconds)) ?? 60
        maxAccountsPerDevice = (try? container.decode(Int.self, forKey: .maxAccountsPerDevice)) ?? 3
        mediaBaseUrl = (try? container.decode(String.self, forKey: .mediaBaseUrl)) ?? ""
        maxVideoSeconds = (try? container.decode(Int.self, forKey: .mediaMaxVideoSeconds)) ?? 600
        maxVideoMb = (try? container.decode(Int.self, forKey: .mediaMaxVideoMb)) ?? 2048
        maxImageMb = (try? container.decode(Int.self, forKey: .mediaMaxImageMb)) ?? 25
        maxCaption = (try? container.decode(Int.self, forKey: .mediaMaxCaption)) ?? 2200
    }
}

/// Answer to "which account is this?" — a masked hint, never a yes/no.
struct AccountHint: Decodable {
    let found: Bool
    let maskedTo: String
    let channel: String
    let hasSocial: Bool
}

/// Result of asking for a verification or reset code.
struct CodeIssued: Decodable {
    let sent: Bool
    let maskedTo: String
    let retryAfterSeconds: Int
    let expiresInSeconds: Int

    private enum CodingKeys: String, CodingKey {
        case sent, maskedTo, retryAfterSeconds, expiresInSeconds
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        sent = (try? container.decode(Bool.self, forKey: .sent)) ?? false
        maskedTo = (try? container.decode(String.self, forKey: .maskedTo)) ?? ""
        retryAfterSeconds = (try? container.decode(Int.self, forKey: .retryAfterSeconds)) ?? 60
        expiresInSeconds = (try? container.decode(Int.self, forKey: .expiresInSeconds)) ?? 900
    }
}

/// Collections arrive as `data.items`, never as a bare array, so a list response can
/// gain fields without breaking the client.
struct ItemsResponse<Element: Decodable>: Decodable {
    let items: [Element]
    let nextCursor: String?

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        items = (try? container.decode([Element].self, forKey: .items)) ?? []
        nextCursor = try? container.decodeIfPresent(String.self, forKey: .nextCursor)
    }

    private enum CodingKeys: String, CodingKey { case items, nextCursor }
}
