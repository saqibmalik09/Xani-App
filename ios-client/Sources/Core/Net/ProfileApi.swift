import Foundation

/// Wallet balances. Integers on the wire; formatting is a UI decision.
struct Balance: Codable, Equatable {
    var coins: Int64 = 0
    var diamonds: Int64 = 0
    var beans: Int64 = 0
    var rev: Int64 = 0
}

/// The whole profile screen in one payload — identity, display fields and
/// balances — so opening Profile is one call, not five.
///
/// `rev` increases on every server-side profile write. The client applies a
/// stream event only when its rev is newer than what it already shows, which is
/// what makes optimistic UI and out-of-order delivery safe.
struct ProfileData: Codable, Equatable {
    var id: String = ""
    var username: String = ""
    var fullName: String = ""
    var email: String = ""
    var emailVerified: Bool = false
    var avatarUrl: String = ""
    var bio: String = ""
    var status: String = ""
    var location: String = ""
    var birthday: String = ""
    var gender: String = ""
    var rev: Int64 = 0
    var usernameCooldownDays: Int = 0
    var balance: Balance = Balance()
    var followers: Int = 0
    var following: Int = 0
    /// How many people have opened this profile.
    var profileViews: Int64 = 0

    // Every field is optional on the wire: a server that renames or drops one must
    // degrade a row on the screen, not fail the decode for the whole profile.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id)) ?? ""
        username = (try? c.decode(String.self, forKey: .username)) ?? ""
        fullName = (try? c.decode(String.self, forKey: .fullName)) ?? ""
        email = (try? c.decode(String.self, forKey: .email)) ?? ""
        emailVerified = (try? c.decode(Bool.self, forKey: .emailVerified)) ?? false
        avatarUrl = (try? c.decode(String.self, forKey: .avatarUrl)) ?? ""
        bio = (try? c.decode(String.self, forKey: .bio)) ?? ""
        status = (try? c.decode(String.self, forKey: .status)) ?? ""
        location = (try? c.decode(String.self, forKey: .location)) ?? ""
        birthday = (try? c.decode(String.self, forKey: .birthday)) ?? ""
        gender = (try? c.decode(String.self, forKey: .gender)) ?? ""
        rev = (try? c.decode(Int64.self, forKey: .rev)) ?? 0
        usernameCooldownDays = (try? c.decode(Int.self, forKey: .usernameCooldownDays)) ?? 0
        balance = (try? c.decode(Balance.self, forKey: .balance)) ?? Balance()
        followers = (try? c.decode(Int.self, forKey: .followers)) ?? 0
        following = (try? c.decode(Int.self, forKey: .following)) ?? 0
        profileViews = (try? c.decode(Int64.self, forKey: .profileViews)) ?? 0
    }

    init() {}
}

/// The answer to "is this handle free?", asked while the user is still typing.
struct UsernameCheck: Codable {
    var username: String = ""
    var available: Bool = false
    /// ok · invalid · reserved · taken · current · cooldown
    var reason: String = ""
    var cooldownDays: Int = 0
}

private struct ProfileEnvelope: Decodable {
    let profile: ProfileData
}

/// Profile endpoints.
///
/// Writes are partial: a field left nil is not sent, so the status editor and the
/// edit screen cannot overwrite each other's work.
struct ProfileApi {
    let api: ApiClient

    func me() async -> ApiResponse<ProfileData> {
        await api.get("/v1/profile/me", authenticated: true, as: ProfileEnvelope.self)
            .map(\.profile)
    }

    func update(
        fullName: String? = nil,
        bio: String? = nil,
        status: String? = nil,
        location: String? = nil,
        gender: String? = nil
    ) async -> ApiResponse<ProfileData> {
        var body: [String: Any] = [:]
        if let fullName { body["full_name"] = fullName }
        if let bio { body["bio"] = bio }
        if let status { body["status"] = status }
        if let location { body["location"] = location }
        if let gender { body["gender"] = gender }
        return await api.post("/v1/profile/update", body: body, authenticated: true,
                              as: ProfileEnvelope.self).map(\.profile)
    }

    /// The one-column write behind "what's on your mind".
    func setStatus(_ status: String) async -> ApiResponse<ProfileData> {
        await api.post("/v1/profile/status", body: ["status": status], authenticated: true,
                       as: ProfileEnvelope.self).map(\.profile)
    }

    /// Changes the handle; the server enforces the 30-day window.
    func setUsername(_ username: String) async -> ApiResponse<ProfileData> {
        await api.post("/v1/profile/username", body: ["username": username], authenticated: true,
                       as: ProfileEnvelope.self).map(\.profile)
    }

    func checkUsername(_ username: String) async -> ApiResponse<UsernameCheck> {
        await api.get("/v1/profile/username/check", query: ["u": username],
                      authenticated: true, as: UsernameCheck.self)
    }

    /// Replaces the profile picture with `jpeg` and answers with the profile as
    /// everyone will now see it.
    ///
    /// The bytes go up raw rather than as a multipart form: there is exactly one
    /// field, and a form would cost a base64-ish envelope and a parse on both
    /// ends for nothing. The server stores each upload under its own revision,
    /// so the returned URL is a new key and no cache anywhere has to be purged.
    func setAvatar(_ jpeg: Data) async -> ApiResponse<ProfileData> {
        await api.putBytes("/v1/profile/avatar", body: jpeg, contentType: "image/jpeg",
                           as: ProfileEnvelope.self).map(\.profile)
    }

    /// Subscribes to profile and balance changes for the signed-in account. Runs
    /// until the calling task is cancelled.
    func stream(
        onProfile: @escaping @Sendable (ProfileData) -> Void,
        onBalance: @escaping @Sendable (Balance) -> Void,
        onPost: @escaping @Sendable (PostData) -> Void = { _ in },
        onPostGone: @escaping @Sendable (Int64) -> Void = { _ in },
        onCounts: @escaping @Sendable (Int64, LiveCounts) -> Void = { _, _ in },
        onChat: @escaping @Sendable (ChatEvent) -> Void = { _ in },
        onCall: @escaping @Sendable (CallEvent) -> Void = { _ in },
        onProfileViews: @escaping @Sendable (Int64) -> Void = { _ in },
        onNotice: @escaping @Sendable (NoticeData) -> Void = { _ in },
        onOpen: @escaping @Sendable () -> Void = {},
        onResync: @escaping @Sendable () -> Void
    ) async {
        await api.stream("/v1/profile/stream", onOpen: onOpen) { name, data in
            // Chat rides the same socket under its own prefix.
            if name.hasPrefix("chat_") {
                if let ev = ChatEvent.decode(name: name, data: data) { onChat(ev) }
                return
            }
            // Call signalling and live-room frames share the socket too.
            if name.hasPrefix("call_") || name.hasPrefix("live_") {
                if let ev = CallEvent.decode(name: name, data: data) { onCall(ev) }
                return
            }
            // The decoder is built per frame rather than captured: a stream handler
            // crosses concurrency domains, and frames arrive seconds apart.
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            switch name {
            case "profile":
                if let frame = try? decoder.decode(Frame<ProfileData>.self, from: data) {
                    onProfile(frame.data)
                }
            case "wallet":
                if let frame = try? decoder.decode(Frame<Balance>.self, from: data) {
                    onBalance(frame.data)
                }
            case "post":
                if let frame = try? decoder.decode(Frame<PostData>.self, from: data) {
                    onPost(frame.data)
                }
            case "post_gone":
                if let frame = try? decoder.decode(Frame<PostGone>.self, from: data),
                   let id = Int64(frame.data.postId) {
                    onPostGone(id)
                }
            case "counts":
                if let frame = try? decoder.decode(Frame<OwnCounts>.self, from: data),
                   let id = Int64(frame.data.postId) {
                    onCounts(id, frame.data.resolved)
                }
            case "profile_views":
                if let frame = try? decoder.decode(Frame<ViewsFrame>.self, from: data) {
                    onProfileViews(frame.data.profileViews)
                }
            case "resync":
                onResync()
            case "notice":
                if let frame = try? decoder.decode(Frame<NoticeData>.self, from: data) {
                    onNotice(frame.data)
                }
            default:
                break
            }
        }
    }

    private struct Frame<T: Decodable>: Decodable {
        let data: T
    }

    private struct PostGone: Decodable {
        let postId: String
    }

    /// Somebody new opened this profile.
    private struct ViewsFrame: Decodable {
        let profileViews: Int64
    }

    /// Engagement on one of the account's own posts, on the profile topic.
    private struct OwnCounts: Decodable {
        let postId: String
        var counts: PostCounts?

        var resolved: LiveCounts {
            guard let c = counts else { return LiveCounts() }
            return LiveCounts(likes: c.likes, comments: c.comments, saves: c.saves, shares: c.shares, views: c.views)
        }
    }
}
