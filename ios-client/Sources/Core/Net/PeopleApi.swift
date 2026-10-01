import Foundation

/// Another account as a list row shows it: a search hit, a follower, someone the
/// user follows, or a post's author opened from the feed.
struct PersonData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var username: String = ""
    var fullName: String = ""
    var avatarUrl: String = ""
    var verified: Bool = false
    var followers: Int = 0
    /// The signed-in user follows this person.
    var following: Bool = false
    /// This person follows the signed-in user.
    var followsYou: Bool = false
    var bio: String = ""
    var status: String = ""
    var followingCount: Int = 0

    private enum CodingKeys: String, CodingKey {
        case id, username, fullName, avatarUrl, verified, followers, following, followsYou, bio, status, followingCount
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .id) { id = Int64(s) ?? 0 } else { id = (try? c.decode(Int64.self, forKey: .id)) ?? 0 }
        username = (try? c.decode(String.self, forKey: .username)) ?? ""
        fullName = (try? c.decode(String.self, forKey: .fullName)) ?? ""
        avatarUrl = (try? c.decode(String.self, forKey: .avatarUrl)) ?? ""
        verified = (try? c.decode(Bool.self, forKey: .verified)) ?? false
        followers = (try? c.decode(Int.self, forKey: .followers)) ?? 0
        following = (try? c.decode(Bool.self, forKey: .following)) ?? false
        followsYou = (try? c.decode(Bool.self, forKey: .followsYou)) ?? false
        bio = (try? c.decode(String.self, forKey: .bio)) ?? ""
        status = (try? c.decode(String.self, forKey: .status)) ?? ""
        followingCount = (try? c.decode(Int.self, forKey: .followingCount)) ?? 0
    }
}

/// Server ack for a follow toggle: both counters after the change.
struct FollowAck: Decodable, Equatable {
    var userId: Int64 = 0
    var following: Bool = true
    var followers: Int = 0
    var myFollowing: Int = 0

    private enum CodingKeys: String, CodingKey { case userId, following, followers, myFollowing }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .userId) { userId = Int64(s) ?? 0 }
        following = (try? c.decode(Bool.self, forKey: .following)) ?? true
        followers = (try? c.decode(Int.self, forKey: .followers)) ?? 0
        myFollowing = (try? c.decode(Int.self, forKey: .myFollowing)) ?? 0
    }
}

/// One page of people.
struct PeoplePage: Equatable {
    var items: [PersonData]
    var next: String
}

/// One visitor on the "who viewed me" list: the person, when they were last
/// here, and how many times they have been.
struct VisitData: Decodable, Equatable, Identifiable {
    var person: PersonData = PersonData()
    /// RFC 3339, formatted for display by the same helper the feed uses.
    var at: String = ""
    var visits: Int = 1

    var id: Int64 { person.id }

    private enum CodingKeys: String, CodingKey { case person, at, visits }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        person = (try? c.decode(PersonData.self, forKey: .person)) ?? PersonData()
        at = (try? c.decode(String.self, forKey: .at)) ?? ""
        visits = (try? c.decode(Int.self, forKey: .visits)) ?? 1
    }
}

struct VisitPage: Equatable {
    var items: [VisitData]
    var next: String
}

private struct UserEnvelope: Decodable { let user: PersonData }
private struct FollowEnvelope: Decodable { let follow: FollowAck }

/// Typed people + follow-graph endpoints.
struct PeopleApi {
    let api: ApiClient

    /// Search by @username, email or name. Answers per keystroke.
    func search(_ q: String, limit: Int = 20) async -> ApiResponse<[PersonData]> {
        await api.get("/v1/users/search", query: ["q": q, "limit": String(limit)], authenticated: true,
                      as: ItemsResponse<PersonData>.self).map(\.items)
    }

    /// Reads one person. `visit: true` also records that the caller opened their
    /// profile, which is what feeds the owner's view counter and visitor list —
    /// so a row rendered in a list costs nothing, and only a real open counts.
    func get(_ id: Int64, visit: Bool = false) async -> ApiResponse<PersonData> {
        await api.get("/v1/users/\(id)", query: visit ? ["visit": "1"] : [:],
                      authenticated: true, as: UserEnvelope.self).map(\.user)
    }

    /// Who opened the signed-in account's profile, latest first.
    func visits(cursor: String = "", limit: Int = 30) async -> ApiResponse<VisitPage> {
        var query = ["limit": String(limit)]
        if !cursor.isEmpty { query["cursor"] = cursor }
        return await api.get("/v1/profile/visits", query: query, authenticated: true,
                             as: ItemsResponse<VisitData>.self)
            .map { VisitPage(items: $0.items, next: $0.nextCursor ?? "") }
    }

    func follow(_ id: Int64, on: Bool) async -> ApiResponse<FollowAck> {
        await api.post("/v1/users/\(id)/follow", body: ["on": on], authenticated: true, as: FollowEnvelope.self).map(\.follow)
    }

    func followers(_ id: Int64, cursor: String = "", limit: Int = 30) async -> ApiResponse<PeoplePage> {
        await page("/v1/users/\(id)/followers", cursor: cursor, limit: limit)
    }

    func following(_ id: Int64, cursor: String = "", limit: Int = 30) async -> ApiResponse<PeoplePage> {
        await page("/v1/users/\(id)/following", cursor: cursor, limit: limit)
    }

    private func page(_ path: String, cursor: String, limit: Int) async -> ApiResponse<PeoplePage> {
        var query = ["limit": String(limit)]
        if !cursor.isEmpty { query["cursor"] = cursor }
        return await api.get(path, query: query, authenticated: true, as: ItemsResponse<PersonData>.self)
            .map { PeoplePage(items: $0.items, next: $0.nextCursor ?? "") }
    }
}
