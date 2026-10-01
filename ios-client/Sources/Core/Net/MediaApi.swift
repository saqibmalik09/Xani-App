import Foundation

/// A resumable upload session as the server sees it.
struct UploadSession: Decodable, Equatable {
    var id: Int64 = 0
    var chunkSize: Int = 0
    var parts: Int = 0
    var received: Set<Int> = []
    /// open · done · aborted · expired
    var state: String = "open"
    var postId: Int64 = 0

    private enum CodingKeys: String, CodingKey { case id, chunkSize, parts, received, state, postId }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = Self.int64(c, .id)
        chunkSize = (try? c.decode(Int.self, forKey: .chunkSize)) ?? 0
        parts = (try? c.decode(Int.self, forKey: .parts)) ?? 0
        received = Set((try? c.decode([Int].self, forKey: .received)) ?? [])
        state = (try? c.decode(String.self, forKey: .state)) ?? "open"
        postId = Self.int64(c, .postId)
    }

    /// Ids are strings on the wire (snowflakes exceed JS precision); accept both.
    fileprivate static func int64(_ c: KeyedDecodingContainer<CodingKeys>, _ k: CodingKeys) -> Int64 {
        if let s = try? c.decode(String.self, forKey: k) { return Int64(s) ?? 0 }
        return (try? c.decode(Int64.self, forKey: k)) ?? 0
    }
}

/// One produced file. `key` is relative; prefix with `MediaConfig.baseURL`.
struct Rendition: Decodable, Equatable {
    var key: String = ""
    var mime: String = ""
    var width: Int = 0
    var height: Int = 0
    var bytes: Int64 = 0
    var kbps: Int = 0

    private enum CodingKeys: String, CodingKey { case key, mime, w, h, bytes, kbps }

    init(key: String, mime: String, width: Int = 0, height: Int = 0) {
        self.key = key
        self.mime = mime
        self.width = width
        self.height = height
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = (try? c.decode(String.self, forKey: .key)) ?? ""
        mime = (try? c.decode(String.self, forKey: .mime)) ?? ""
        width = (try? c.decode(Int.self, forKey: .w)) ?? 0
        height = (try? c.decode(Int.self, forKey: .h)) ?? 0
        bytes = (try? c.decode(Int64.self, forKey: .bytes)) ?? 0
        kbps = (try? c.decode(Int.self, forKey: .kbps)) ?? 0
    }
}

/// The author slice a feed card shows.
struct PostAuthor: Decodable, Equatable {
    var id: Int64 = 0
    var username: String = ""
    var fullName: String = ""
    var avatarUrl: String = ""
    var verified: Bool = false

    static let unknown = PostAuthor()

    private enum CodingKeys: String, CodingKey { case id, username, fullName, avatarUrl, verified }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .id) { id = Int64(s) ?? 0 } else { id = (try? c.decode(Int64.self, forKey: .id)) ?? 0 }
        username = (try? c.decode(String.self, forKey: .username)) ?? ""
        fullName = (try? c.decode(String.self, forKey: .fullName)) ?? ""
        avatarUrl = (try? c.decode(String.self, forKey: .avatarUrl)) ?? ""
        verified = (try? c.decode(Bool.self, forKey: .verified)) ?? false
    }
}

/// Display counters — straight off the post row.
struct PostCounts: Decodable, Equatable {
    var likes: Int = 0
    var comments: Int = 0
    var saves: Int = 0
    var shares: Int = 0
    var views: Int64 = 0

    init() {}

    private enum CodingKeys: String, CodingKey { case likes, comments, saves, shares, views }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        likes = (try? c.decode(Int.self, forKey: .likes)) ?? 0
        comments = (try? c.decode(Int.self, forKey: .comments)) ?? 0
        saves = (try? c.decode(Int.self, forKey: .saves)) ?? 0
        shares = (try? c.decode(Int.self, forKey: .shares)) ?? 0
        views = (try? c.decode(Int64.self, forKey: .views)) ?? 0
    }
}

/// A post: what the author sees while it processes and what the feed plays.
struct PostData: Decodable, Equatable {
    var id: Int64 = 0
    var authorId: Int64 = 0
    /// video · photo · text
    var kind: String = ""
    /// processing · live · failed · hidden · removed
    var state: String = "processing"
    var caption: String = ""
    var visibility: String = "public"
    var createdAt: String = ""
    var author: PostAuthor = .unknown
    var counts = PostCounts()
    var liked = false
    var saved = false
    /// The viewer follows this post's author.
    var following = false
    var allowComments = true
    var mediaState: String = ""
    var width: Int = 0
    var height: Int = 0
    var durationMs: Int = 0
    var renditions: [String: Rendition] = [:]
    var error: String = ""

    var isLive: Bool { state == "live" }
    var isRestricted: Bool { state == "restricted" || state == "hidden" }
    var isFailed: Bool { state == "failed" }
    var isVideo: Bool { kind == "video" }
    var isText: Bool { kind == "text" }

    /// Best still for a grid cell.
    var thumbKey: String? {
        renditions["thumb"]?.key ?? renditions["poster"]?.key ?? renditions["md"]?.key
    }

    /// Full-screen still: the poster for video, the largest image otherwise.
    var posterKey: String? {
        renditions["poster"]?.key ?? renditions["full"]?.key ?? renditions["md"]?.key ?? renditions["thumb"]?.key
    }

    /// width / height; 9:16 when unknown.
    var aspect: CGFloat { width > 0 && height > 0 ? CGFloat(width) / CGFloat(height) : 9.0 / 16.0 }

    /// The tallest ladder rung that fits `maxHeight`; the smallest rung when nothing fits.
    func videoKey(maxHeight: Int) -> String? {
        var best: Rendition?
        var smallest: Rendition?
        for (name, r) in renditions where name.hasSuffix("p") && r.mime.hasPrefix("video/") {
            if smallest == nil || r.height < smallest!.height { smallest = r }
            if r.height <= maxHeight, best == nil || r.height > best!.height { best = r }
        }
        return (best ?? smallest)?.key
    }

    private enum CodingKeys: String, CodingKey {
        case id, authorId, kind, state, caption, visibility, createdAt, media, author, counts, viewer, allowComments
    }
    private enum MediaKeys: String, CodingKey { case state, w, h, durationMs, renditions, error }
    private enum ViewerKeys: String, CodingKey { case liked, saved, following }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .id) { id = Int64(s) ?? 0 } else { id = (try? c.decode(Int64.self, forKey: .id)) ?? 0 }
        if let s = try? c.decode(String.self, forKey: .authorId) { authorId = Int64(s) ?? 0 } else { authorId = (try? c.decode(Int64.self, forKey: .authorId)) ?? 0 }
        kind = (try? c.decode(String.self, forKey: .kind)) ?? ""
        state = (try? c.decode(String.self, forKey: .state)) ?? "processing"
        caption = (try? c.decode(String.self, forKey: .caption)) ?? ""
        visibility = (try? c.decode(String.self, forKey: .visibility)) ?? "public"
        createdAt = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
        author = (try? c.decode(PostAuthor.self, forKey: .author)) ?? .unknown
        counts = (try? c.decode(PostCounts.self, forKey: .counts)) ?? PostCounts()
        allowComments = (try? c.decode(Bool.self, forKey: .allowComments)) ?? true
        if let v = try? c.nestedContainer(keyedBy: ViewerKeys.self, forKey: .viewer) {
            liked = (try? v.decode(Bool.self, forKey: .liked)) ?? false
            saved = (try? v.decode(Bool.self, forKey: .saved)) ?? false
            following = (try? v.decode(Bool.self, forKey: .following)) ?? false
        }
        if let m = try? c.nestedContainer(keyedBy: MediaKeys.self, forKey: .media) {
            mediaState = (try? m.decode(String.self, forKey: .state)) ?? ""
            width = (try? m.decode(Int.self, forKey: .w)) ?? 0
            height = (try? m.decode(Int.self, forKey: .h)) ?? 0
            durationMs = (try? m.decode(Int.self, forKey: .durationMs)) ?? 0
            renditions = (try? m.decode([String: Rendition].self, forKey: .renditions)) ?? [:]
            error = (try? m.decode(String.self, forKey: .error)) ?? ""
        }
    }
}

/// One page of posts.
struct PostPage: Equatable {
    var items: [PostData]
    var next: String
}

/// One comment with its author.
struct CommentData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var postId: Int64 = 0
    var parentId: Int64 = 0
    var author: PostAuthor = .unknown
    var body: String = ""
    var likes: Int = 0
    var createdAt: String = ""

    private enum CodingKeys: String, CodingKey { case id, postId, parentId, author, body, likes, createdAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func id64(_ k: CodingKeys) -> Int64 {
            if let s = try? c.decode(String.self, forKey: k) { return Int64(s) ?? 0 }
            return (try? c.decode(Int64.self, forKey: k)) ?? 0
        }
        id = id64(.id)
        postId = id64(.postId)
        parentId = id64(.parentId)
        author = (try? c.decode(PostAuthor.self, forKey: .author)) ?? .unknown
        body = (try? c.decode(String.self, forKey: .body)) ?? ""
        likes = (try? c.decode(Int.self, forKey: .likes)) ?? 0
        createdAt = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
    }
}

/// Server ack for a like/save: the true count the optimistic number snaps to.
struct ReactionAck: Decodable, Equatable {
    var on: Bool = true
    var count: Int = 0

    private enum CodingKeys: String, CodingKey { case on, count }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        on = (try? c.decode(Bool.self, forKey: .on)) ?? true
        count = (try? c.decode(Int.self, forKey: .count)) ?? 0
    }
}

/// Everything the client declares before the first byte.
struct UploadDeclaration {
    let idem: String
    let kind: String // video | image
    let mime: String
    let size: Int64
    let width: Int
    let height: Int
    let durationMs: Int
    let capturedAt: String? // RFC3339 or nil
}

/// The post form.
struct PostForm: Equatable, Codable {
    var caption: String
    var visibility: String = "public"
    var allowComments: Bool = true
    var allowDuet: Bool = true
}

private struct UploadEnvelope: Decodable { let upload: UploadSession }
private struct PostEnvelope: Decodable { let post: PostData }
private struct PostGoneEnvelope: Decodable { let postId: String }
private struct LikeEnvelope: Decodable { let like: ReactionAck }
private struct SaveEnvelope: Decodable { let save: ReactionAck }
private struct CommentEnvelope: Decodable {
    let comment: CommentData
    let count: Int
}

struct NoticeData: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var kind: String = ""
    var title: String = ""
    var body: String = ""
    var refId: Int64 = 0
    var createdMs: Int64 = 0

    private enum CodingKeys: String, CodingKey { case id, kind, title, body, refId, createdMs }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .id) { id = Int64(s) ?? 0 }
        else { id = (try? c.decode(Int64.self, forKey: .id)) ?? 0 }
        kind = (try? c.decode(String.self, forKey: .kind)) ?? ""
        title = (try? c.decode(String.self, forKey: .title)) ?? ""
        body = (try? c.decode(String.self, forKey: .body)) ?? ""
        if let s = try? c.decode(String.self, forKey: .refId) { refId = Int64(s) ?? 0 }
        else { refId = (try? c.decode(Int64.self, forKey: .refId)) ?? 0 }
        createdMs = (try? c.decode(Int64.self, forKey: .createdMs)) ?? 0
    }
}

/// Typed media endpoints. The upload protocol is: `open` → `putPart` for every
/// part (parallel, any order, retried individually) → `complete`. `status` is the
/// resume read after a relaunch.
struct MediaApi {
    let api: ApiClient

    func open(_ d: UploadDeclaration) async -> ApiResponse<UploadSession> {
        var body: [String: Any] = [
            "idem": d.idem, "kind": d.kind, "mime": d.mime, "size": d.size,
            "width": d.width, "height": d.height, "duration_ms": d.durationMs,
        ]
        body["captured_at"] = d.capturedAt ?? NSNull()
        return await api.post("/v1/media/uploads", body: body, authenticated: true, as: UploadEnvelope.self)
            .map(\.upload)
    }

    func status(_ id: Int64) async -> ApiResponse<UploadSession> {
        await api.get("/v1/media/uploads/\(id)", authenticated: true, as: UploadEnvelope.self).map(\.upload)
    }

    func putPart(_ id: Int64, n: Int, bytes: Data, sha256Hex: String) async -> ApiResponse<UploadSession> {
        await api.putBytes("/v1/media/uploads/\(id)/parts/\(n)", body: bytes, sha256Hex: sha256Hex,
                           as: UploadEnvelope.self).map(\.upload)
    }

    func complete(_ id: Int64, form: PostForm) async -> ApiResponse<PostData> {
        await api.post(
            "/v1/media/uploads/\(id)/complete",
            body: [
                "caption": form.caption, "visibility": form.visibility,
                "allow_comments": form.allowComments, "allow_duet": form.allowDuet,
            ],
            authenticated: true, as: PostEnvelope.self
        ).map(\.post)
    }

    @discardableResult
    func abort(_ id: Int64) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/media/uploads/\(id)/abort", body: [:], authenticated: true)
    }

    func post(_ id: Int64) async -> ApiResponse<PostData> {
        await api.get("/v1/posts/\(id)", authenticated: true, as: PostEnvelope.self).map(\.post)
    }

    func mine(before: Int64 = 0, limit: Int = 24) async -> ApiResponse<PostPage> {
        var query = ["limit": String(limit)]
        if before > 0 { query["before"] = String(before) }
        return await api.get("/v1/posts/mine", query: query, authenticated: true,
                             as: ItemsResponse<PostData>.self).map { PostPage(items: $0.items, next: $0.nextCursor ?? "") }
    }

    func notices(before: String = "", limit: Int = 30) async -> ApiResponse<[NoticeData]> {
        var query = ["limit": String(limit)]
        if !before.isEmpty { query["before"] = before }
        return await api.get("/v1/notices", query: query, authenticated: true,
                             as: ItemsResponse<NoticeData>.self).map(\.items)
    }

    func byUser(_ userId: Int64, before: Int64 = 0, limit: Int = 24) async -> ApiResponse<PostPage> {
        var query = ["limit": String(limit)]
        if before > 0 { query["before"] = String(before) }
        return await api.get("/v1/users/\(userId)/posts", query: query, authenticated: true,
                             as: ItemsResponse<PostData>.self).map { PostPage(items: $0.items, next: $0.nextCursor ?? "") }
    }

    /// The For You page; empty cursor is the head.
    func feed(cursor: String = "", limit: Int = 12) async -> ApiResponse<PostPage> {
        var query = ["limit": String(limit)]
        if !cursor.isEmpty { query["cursor"] = cursor }
        return await api.get("/v1/feed", query: query, authenticated: true,
                             as: ItemsResponse<PostData>.self).map { PostPage(items: $0.items, next: $0.nextCursor ?? "") }
    }

    func postText(_ text: String, visibility: String = "public") async -> ApiResponse<PostData> {
        await api.post("/v1/posts/text", body: ["text": text, "visibility": visibility],
                       authenticated: true, as: PostEnvelope.self).map(\.post)
    }

    func like(_ postId: Int64, on: Bool) async -> ApiResponse<ReactionAck> {
        await api.post("/v1/posts/\(postId)/like", body: ["on": on], authenticated: true, as: LikeEnvelope.self).map(\.like)
    }

    func save(_ postId: Int64, on: Bool) async -> ApiResponse<ReactionAck> {
        await api.post("/v1/posts/\(postId)/save", body: ["on": on], authenticated: true, as: SaveEnvelope.self).map(\.save)
    }

    /// Counts a watch. Fire-and-forget: the server stores one row per viewer per
    /// post, so a duplicate is a cheap success and the caller never has to
    /// remember across installs whether it already sent this.
    @discardableResult
    func view(_ postId: Int64) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/posts/\(postId)/view", body: [:], authenticated: true)
    }

    /// `sort` is "top" (most liked) or "" / "new" for newest first.
    func comments(_ postId: Int64, cursor: String = "", limit: Int = 30, sort: String = "") async -> ApiResponse<ItemsResponse<CommentData>> {
        var query = ["limit": String(limit)]
        if !cursor.isEmpty { query["cursor"] = cursor }
        if !sort.isEmpty { query["sort"] = sort }
        return await api.get("/v1/posts/\(postId)/comments", query: query, authenticated: true,
                             as: ItemsResponse<CommentData>.self)
    }

    /// An author's edit. Only the fields passed are sent, so saving the caption
    /// cannot silently overwrite the audience the user set on another screen.
    func editPost(
        _ postId: Int64,
        caption: String? = nil,
        visibility: String? = nil,
        allowComments: Bool? = nil
    ) async -> ApiResponse<PostData> {
        var payload: [String: Any] = [:]
        if let caption { payload["caption"] = caption }
        if let visibility { payload["visibility"] = visibility }
        if let allowComments { payload["allow_comments"] = allowComments }
        return await api.post("/v1/posts/\(postId)/edit", body: payload, authenticated: true,
                              as: PostEnvelope.self).map(\.post)
    }

    /// Retires the author's own post.
    func deletePost(_ postId: Int64) async -> ApiResponse<Int64> {
        await api.delete("/v1/posts/\(postId)", authenticated: true, as: PostGoneEnvelope.self)
            .map { Int64($0.postId) ?? postId }
    }

    /// Returns the stored comment and the post's new comment count.
    func comment(_ postId: Int64, body: String, parentId: Int64 = 0) async -> ApiResponse<(CommentData, Int)> {
        var payload: [String: Any] = ["body": body]
        if parentId > 0 { payload["parent_id"] = String(parentId) }
        return await api.post("/v1/posts/\(postId)/comments", body: payload, authenticated: true,
                              as: CommentEnvelope.self).map { ($0.comment, $0.count) }
    }

    /// The per-post realtime channel while a comment sheet is open: new comments
    /// and counter moves from every other viewer. Runs until the task is cancelled.
    func postStream(
        _ postId: Int64,
        onComment: @escaping @Sendable (CommentData, Int) -> Void,
        onCounts: @escaping @Sendable (LiveCounts) -> Void
    ) async {
        await api.stream("/v1/posts/\(postId)/stream") { name, data in
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            switch name {
            case "comment":
                if let frame = try? decoder.decode(Frame<CommentEnvelope>.self, from: data) {
                    onComment(frame.data.comment, frame.data.count)
                }
            case "counts":
                if let frame = try? decoder.decode(Frame<CountsFrame>.self, from: data) {
                    onCounts(frame.data.resolved)
                }
            default:
                break
            }
        }
    }

    private struct Frame<T: Decodable>: Decodable { let data: T }

    /// The server sends the whole rail under `counts`; `like` and `save` are the
    /// per-kind keys kept for a server that predates it.
    private struct CountsFrame: Decodable {
        var like: Int?
        var save: Int?
        var counts: PostCounts?

        var resolved: LiveCounts {
            guard let c = counts else {
                return LiveCounts(likes: like, comments: nil, saves: save, shares: nil, views: nil)
            }
            return LiveCounts(likes: c.likes, comments: c.comments, saves: c.saves, shares: c.shares, views: c.views)
        }
    }
}
