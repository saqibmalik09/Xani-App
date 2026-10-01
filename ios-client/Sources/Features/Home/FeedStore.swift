import SwiftUI

struct Creator: Equatable {
    let handle: String
    let name: String
    let verified: Bool
    let initials: String
    let color: UInt32
    /// The author's picture, ready to fetch. Empty until they set one, which is
    /// when the initials on their gradient are what the rail shows.
    var avatarURL: URL?
}

enum FeedKind { case video, photo, text }

/// One feed card. The display strings are what the chrome renders; the media
/// fields are what the poster area plays. Built from the wire shape in `FeedRepo`.
struct FeedPost: Identifiable, Equatable {
    let id: Int64
    var creator: Creator
    let description: String
    let sound: String
    var likes: String
    var comments: String
    var shares: String
    var saves: String
    var kind: FeedKind = .video
    var authorId: Int64 = 0
    var posterURL: URL?
    var videoURL: URL?
    var aspect: CGFloat = 9.0 / 16.0
    var liked = false
    var saved = false
    var likeCount = 0
    var commentCount = 0
    var saveCount = 0
    var shareCount = 0
    var viewCount: Int64 = 0
    var allowComments = true
    /// Server truth on first paint: the viewer follows this author.
    var following = false
}

/// Server counters for one post, as the realtime channel last reported them.
/// `nil` is "the server has not said", which is how a surface knows to keep the
/// number it already had rather than paint a zero.
struct LiveCounts: Equatable {
    var likes: Int?
    var comments: Int?
    var saves: Int?
    var shares: Int?
    var views: Int64?
}

enum LiveKind { case video, audio }

struct LiveTile: Identifiable, Equatable {
    let id: Int
    let host: String
    let name: String
    let viewers: String
    let category: String
    let initials: String
    let color: UInt32
    var flag: String = "🌍"
    var country: String = "ALL"
    var diamonds: String = "0"
    var kind: LiveKind = .video
    var pk: Bool = false
    /// Server live room; 0 for a tile that has no broadcast behind it yet.
    var roomId: Int64 = 0
}

struct LiveCountry: Identifiable, Equatable {
    let code: String
    let flag: String
    let label: String
    var id: String { code }
}

struct FeedReply: Identifiable, Equatable {
    let id: Int64
    let user: String
    let text: String
    let time: String
    let likes: String
    let color: UInt32
}

struct FeedComment: Identifiable, Equatable {
    let id: Int64
    let user: String
    let text: String
    let time: String
    let likes: Int
    let color: UInt32
    var authorId: Int64 = 0
    var avatarURL: URL?
    var replies: [FeedReply] = []
}

enum HomeLane: String {
    case following, forYou, live
}

/// Live-lane reference data (typed seed until the live service ships). The For
/// You feed itself lives in `FeedRepo`.
enum FeedStore {
    static func live() -> Envelope<[LiveTile]> {
        .ok(liveCatalog, cursor: "live:\(liveCatalog.count)")
    }

    static let countries: [LiveCountry] = [
        LiveCountry(code: "ALL", flag: "🌍", label: "Worldwide"),
        LiveCountry(code: "US", flag: "🇺🇸", label: "USA"),
        LiveCountry(code: "UK", flag: "🇬🇧", label: "UK"),
        LiveCountry(code: "AE", flag: "🇦🇪", label: "UAE"),
        LiveCountry(code: "SA", flag: "🇸🇦", label: "Saudi"),
        LiveCountry(code: "FR", flag: "🇫🇷", label: "France"),
        LiveCountry(code: "DE", flag: "🇩🇪", label: "Germany"),
        LiveCountry(code: "BR", flag: "🇧🇷", label: "Brazil"),
        LiveCountry(code: "IN", flag: "🇮🇳", label: "India"),
        LiveCountry(code: "JP", flag: "🇯🇵", label: "Japan"),
        LiveCountry(code: "KR", flag: "🇰🇷", label: "Korea"),
        LiveCountry(code: "TR", flag: "🇹🇷", label: "Turkey"),
        LiveCountry(code: "PK", flag: "🇵🇰", label: "Pakistan"),
        LiveCountry(code: "EG", flag: "🇪🇬", label: "Egypt"),
        LiveCountry(code: "ID", flag: "🇮🇩", label: "Indonesia"),
        LiveCountry(code: "IT", flag: "🇮🇹", label: "Italy"),
        LiveCountry(code: "ES", flag: "🇪🇸", label: "Spain"),
    ]
}

/// The For You feed as the app sees it.
///
/// Open: the last head page paints from disk before any request, then the network
/// page replaces it. Scroll: the next page is requested four cards early, so the
/// pager never waits, and the next posters are warmed as each page lands.
/// Interact: like/save flip the card and count at once; the server's count snaps
/// in with the ack; a failure rolls back. The user's own post arrives on the post
/// bus and is inserted at the head.
@MainActor
final class FeedRepo: ObservableObject {
    static let shared = FeedRepo()

    @Published private(set) var items: [FeedPost] = []
    @Published private(set) var comments: [Int64: [FeedComment]] = [:]

    /// Server counters per post, independent of which list the post is in.
    ///
    /// The feed patches its own cards, but a post opened full-screen from a
    /// profile grid is not in the feed list at all — without this it would sit
    /// on the numbers it was opened with while everyone else's likes landed.
    /// Bounded, so a long session cannot keep an entry per card ever scrolled past.
    @Published private(set) var counts: [Int64: LiveCounts] = [:]
    private var countsOrder: [Int64] = []
    private let countsKept = 64

    @Published private(set) var refreshing = false
    @Published private(set) var loading = false
    @Published private(set) var loaded = false
    @Published private(set) var offline = false

    private var cursor = ""
    private var userId: Int64 = 0
    /// Last successful head read from the server; nil = never this process.
    private var revalidatedAt: Date?
    /// The pager's settled index; a refresh only swaps the list at the top.
    private var currentPage = 0
    /// A fresh head fetched while the user was scrolled deep.
    private var pendingHead: ([FeedPost], String)?
    private let staleAfter: TimeInterval = 90
    private let defaults = UserDefaults.standard
    private let keyHead = "xani.feed.head"
    private let keyUser = "xani.feed.user"
    private let loadAhead = 4
    private let warmAhead = 3
    private var maxVideoHeight: Int {
        switch DeviceClass.current() {
        case .low: return 360
        case .mid: return 480
        case .high: return 720
        }
    }

    private init() {
        userId = Int64(defaults.integer(forKey: keyUser))
        restore()
        LiveFeed.shared.listenPosts { [weak self] p in self?.onPostEvent(p) }
        LiveFeed.shared.listenPostsGone { [weak self] id in self?.onPostGone(id) }
        LiveFeed.shared.listenCounts { [weak self] id, c in self?.applyCounts(id, c) }
        // A new profile picture has to reach the cards the user already posted.
        // Those cards were built from a payload that carried the old key, and
        // the server does not republish a post because its author changed their
        // face — so the one surface that knows patches them here.
        LiveFeed.shared.listenProfile { [weak self] data in self?.applyMyAvatar(data.avatarUrl) }
    }

    private func applyMyAvatar(_ key: String) {
        guard userId != 0 else { return }
        let url = MediaConfig.shared.url(key)
        guard !items.isEmpty else { return }
        for i in items.indices where items[i].authorId == userId && items[i].creator.avatarURL != url {
            items[i].creator.avatarURL = url
        }
    }

    func signedIn(_ id: Int64) {
        if userId != 0, userId != id {
            items = []
            cursor = ""
            loaded = false
            revalidatedAt = nil
            pendingHead = nil
            defaults.removeObject(forKey: keyHead)
        }
        userId = id
        defaults.set(Int(id), forKey: keyUser)
        ensureLoaded()
    }

    func signedOut() {
        userId = 0
        items = []
        comments = [:]
        commentCursor = [:]
        commentSort = [:]
        counts = [:]
        countsOrder = []
        counted = []
        unwatch()
        FollowStore.shared.signedOut()
        cursor = ""
        loaded = false
        revalidatedAt = nil
        pendingHead = nil
        defaults.removeObject(forKey: keyHead)
        defaults.removeObject(forKey: keyUser)
    }

    /// Home came on screen. The cache (or the user's own post) may already be
    /// painted, but that is not the server's word: read the head if this process
    /// never has, or if the last read is stale.
    func ensureLoaded() {
        if refreshing { return }
        if let at = revalidatedAt, Date().timeIntervalSince(at) < staleAfter { return }
        refresh()
    }

    func refresh() {
        if refreshing { return }
        refreshing = true
        Task {
            let r = await Xani.media.feed()
            if let page = r.value {
                revalidatedAt = Date()
                persistHead(page)
                let fresh = page.items.map(card)
                // Swapping the list under a user ten cards deep would jump them;
                // hold the fresh head until they are back at the top.
                if currentPage <= 0 || items.isEmpty {
                    pendingHead = nil
                    applyHead(fresh, page.next)
                } else {
                    pendingHead = (fresh, page.next)
                }
                loaded = true
                offline = false
                warm(from: 0)
            } else {
                offline = r.isTransport
                loaded = !items.isEmpty
            }
            refreshing = false
        }
    }

    private func applyHead(_ fresh: [FeedPost], _ next: String) {
        // A post the user just published that is newer than the head stays in front.
        let ids = Set(fresh.map(\.id))
        let newest = fresh.first?.id ?? 0
        let keepAhead = items.filter { !ids.contains($0.id) && $0.id > newest }
        items = keepAhead + fresh
        cursor = next
    }

    func loadMore() {
        if loading || cursor.isEmpty { return }
        loading = true
        let c = cursor
        Task {
            let r = await Xani.media.feed(cursor: c)
            if let page = r.value {
                var seen = Set(items.map(\.id))
                let fresh = page.items.map(card).filter { seen.insert($0.id).inserted }
                let from = items.count
                items.append(contentsOf: fresh)
                cursor = page.next
                warm(from: from)
            } else {
                offline = r.isTransport
            }
            loading = false
        }
    }

    /// The pager's settled index; drives the next page, poster warming, and a held head.
    func onPage(_ index: Int) {
        currentPage = index
        if index == 0, let (fresh, next) = pendingHead {
            pendingHead = nil
            applyHead(fresh, next)
        }
        if index >= items.count - loadAhead { loadMore() }
        warm(from: index + 1)
        if index >= 0, index < items.count { watchVisible(items[index].id) }
    }

    // MARK: - Realtime for the card on screen

    /// The post the user is looking at right now. Holding its channel is what
    /// makes somebody else's like land on this screen: the card is repainted
    /// from the server's counters while it is visible, with no polling and no
    /// re-read of the feed.
    ///
    /// Exactly one subscription at a time, keyed by post, so the pager moving,
    /// the comment sheet opening on the same card, and a full-screen viewer all
    /// share one socket instead of stacking three.
    private var watchTask: Task<Void, Never>?
    private var watching: Int64 = 0

    func watchVisible(_ postId: Int64) {
        if postId == watching, watchTask != nil { return }
        watching = postId
        watchTask?.cancel()
        watchTask = postId > 0 ? watch(postId) : nil
    }

    /// The last surface showing that post went away: stop paying for the channel.
    func unwatch() {
        watching = 0
        watchTask?.cancel()
        watchTask = nil
    }

    private func warm(from: Int) {
        for i in from ..< min(items.count, from + warmAhead) {
            if let u = items[i].posterURL { ImageLoader.shared.prefetch(u) }
        }
    }

    // MARK: - Views

    /// Share of a clip that has to be watched before it counts as a view — 20
    /// seconds of a 100-second video. The server reports the same number in
    /// /v1/config; this is the floor if that read has not landed yet.
    private let viewThreshold = 0.20

    /// Posts this process has already counted a watch for, so a looping clip
    /// sends one request and not one per lap. It is a hint, not the rule: the
    /// server stores one row per viewer per post, so the count stays honest
    /// across reinstalls and across devices no matter what this set says.
    private var counted: Set<Int64> = []

    /// Playback progress for the clip on screen, reported twice a second by
    /// `FeedVideoView`.
    ///
    /// Position, not elapsed time, is what is measured, so scrubbing to the end
    /// does not earn a view and leaving the phone paused does not either.
    func noteWatch(_ postId: Int64, position: Double, duration: Double) {
        guard postId > 0, duration > 0, position / duration >= viewThreshold else { return }
        guard !counted.contains(postId) else { return }
        counted.insert(postId)
        Task {
            let r = await Xani.media.view(postId)
            // A failure that is not "your session ended" means the watch was
            // real but unrecorded; allow the next lap to try again.
            if r.error, !r.isSessionExpired { counted.remove(postId) }
        }
    }

    // MARK: - Reactions

    func toggleLike(_ id: Int64) { react(id, like: true) }
    func toggleSave(_ id: Int64) { react(id, like: false) }

    /// Sets a like without taking one back — what a double tap means.
    func likeOn(_ id: Int64) {
        guard items.first(where: { $0.id == id })?.liked != true else { return }
        react(id, like: true)
    }

    private func react(_ id: Int64, like: Bool) {
        guard let before = items.first(where: { $0.id == id }) else { return }
        let on = like ? !before.liked : !before.saved
        patch(id) { p in
            if like { p.liked = on; p.likeCount = max(0, p.likeCount + (on ? 1 : -1)) }
            else { p.saved = on; p.saveCount = max(0, p.saveCount + (on ? 1 : -1)) }
        }
        Task {
            let r = like ? await Xani.media.like(id, on: on) : await Xani.media.save(id, on: on)
            if let ack = r.value {
                patch(id) { p in
                    if like { p.liked = ack.on; p.likeCount = ack.count } else { p.saved = ack.on; p.saveCount = ack.count }
                }
            } else if !r.isSessionExpired {
                patch(id) { p in
                    if like { p.liked = before.liked; p.likeCount = before.likeCount }
                    else { p.saved = before.saved; p.saveCount = before.saveCount }
                }
            }
        }
    }

    private func patch(_ id: Int64, _ fn: (inout FeedPost) -> Void) {
        guard let i = items.firstIndex(where: { $0.id == id }) else { return }
        var p = items[i]
        fn(&p)
        items[i] = Self.withCounts(p)
    }

    // MARK: - Comments

    /// postId → cursor for the next (older) page; absent = not loaded, "" = all loaded.
    private var commentCursor: [Int64: String] = [:]
    private var commentLoading: Set<Int64> = []
    /// postId → the order its list is currently in, so paging stays consistent.
    private var commentSort: [Int64: String] = [:]

    /// Reads the first page of a thread. `sort` is "top" or "new"; the cursor
    /// belongs to one order, so changing it discards the list and starts over
    /// rather than interleaving two orders.
    func loadComments(_ postId: Int64, sort: String = "") {
        if commentLoading.contains(postId), commentSort[postId] == sort { return }
        if commentSort[postId] != sort {
            commentSort[postId] = sort
            comments.removeValue(forKey: postId)
        }
        commentLoading.insert(postId)
        Task {
            if let page = await Xani.media.comments(postId, sort: sort).value {
                commentCursor[postId] = page.nextCursor ?? ""
                // A comment that arrived live while this read was in flight stays.
                let ids = Set(page.items.map(\.id))
                let live = (comments[postId] ?? []).filter { !ids.contains($0.id) }
                comments[postId] = live + page.items.map(comment)
            }
            commentLoading.remove(postId)
        }
    }

    /// The sheet reached its last row: fetch the next older page, if any.
    func loadMoreComments(_ postId: Int64) {
        guard !commentLoading.contains(postId), let c = commentCursor[postId], !c.isEmpty else { return }
        let sort = commentSort[postId] ?? ""
        commentLoading.insert(postId)
        Task {
            if let page = await Xani.media.comments(postId, cursor: c, sort: sort).value {
                commentCursor[postId] = page.nextCursor ?? ""
                var seen = Set((comments[postId] ?? []).map(\.id))
                comments[postId] = (comments[postId] ?? []) + page.items.map(comment).filter { seen.insert($0.id).inserted }
            }
            commentLoading.remove(postId)
        }
    }

    func addComment(_ postId: Int64, _ body: String) {
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        Task {
            if let (c, count) = await Xani.media.comment(postId, body: text).value {
                receive(c, count: count)
            }
        }
    }

    /// Holds the post's realtime channel while its comment sheet is open: every
    /// comment from any viewer lands as it is written and the counters move with
    /// the room. Cancel the returned task when the sheet closes.
    private func watch(_ postId: Int64) -> Task<Void, Never> {
        Task {
            await Xani.media.postStream(
                postId,
                onComment: { c, count in Task { @MainActor in FeedRepo.shared.receive(c, count: count) } },
                onCounts: { c in Task { @MainActor in FeedRepo.shared.applyCounts(postId, c) } }
            )
        }
    }

    /// A counter frame for one post. The server sends the whole rail, so one
    /// frame repaints likes, comments and saves together.
    func applyCounts(_ postId: Int64, _ c: LiveCounts) {
        patch(postId) { p in
            if let v = c.likes { p.likeCount = v }
            if let v = c.saves { p.saveCount = v }
            if let v = c.comments { p.commentCount = v }
            if let v = c.shares { p.shareCount = v }
            if let v = c.views { p.viewCount = v }
        }
        note(postId) { cur in
            if let v = c.likes { cur.likes = v }
            if let v = c.saves { cur.saves = v }
            if let v = c.comments { cur.comments = v }
            if let v = c.shares { cur.shares = v }
            if let v = c.views { cur.views = v }
        }
    }

    private func note(_ postId: Int64, _ fn: (inout LiveCounts) -> Void) {
        var cur = counts[postId] ?? LiveCounts()
        fn(&cur)
        counts[postId] = cur
        // Re-insert so the freshest post is last and the oldest falls off.
        countsOrder.removeAll { $0 == postId }
        countsOrder.append(postId)
        while countsOrder.count > countsKept {
            counts.removeValue(forKey: countsOrder.removeFirst())
        }
    }

    /// One entry point for a comment from our own send and from the stream: dedupe by id.
    private func receive(_ c: CommentData, count: Int) {
        let cur = comments[c.postId] ?? []
        if !cur.contains(where: { $0.id == c.id }) { comments[c.postId] = [comment(c)] + cur }
        if count > 0 {
            patch(c.postId) { $0.commentCount = count }
            note(c.postId) { $0.comments = count }
        }
    }

    // MARK: - Author edits

    /// Saves an author's edit. The server answers with the post as everyone will
    /// now see it; that goes on the post bus, so the grid tile and the feed card
    /// swap in place with nothing reloaded.
    func edit(
        _ postId: Int64,
        caption: String? = nil,
        visibility: String? = nil,
        allowComments: Bool? = nil,
        onDone: @escaping (Bool) -> Void = { _ in }
    ) {
        Task {
            let r = await Xani.media.editPost(postId, caption: caption, visibility: visibility, allowComments: allowComments)
            if let post = r.value { LiveFeed.shared.emitPost(post) }
            onDone(r.value != nil)
        }
    }

    /// Retires the author's post, then drops it from every surface at once.
    func delete(_ postId: Int64, onDone: @escaping (Bool) -> Void = { _ in }) {
        Task {
            let ok = await Xani.media.deletePost(postId).value != nil
            if ok { LiveFeed.shared.emitPostGone(postId) }
            onDone(ok)
        }
    }

    // MARK: - Live

    /// The author's own post went live. A local insert, not a server read — it
    /// never counts as revalidation, so the next Home open still asks for
    /// everyone else's posts.
    private func onPostEvent(_ p: PostData) {
        // An edit can also take a post out of For You: made private, or hidden.
        if p.isLive, p.visibility != "public" { onPostGone(p.id); return }
        guard p.isLive else { onPostGone(p.id); return }
        let c = card(p)
        if let i = items.firstIndex(where: { $0.id == p.id }) {
            // Already on screen: this is an edit, so swap it in place. Replacing
            // the card rather than the list keeps the pager's position.
            items[i] = c
        } else {
            items.insert(c, at: 0)
        }
        loaded = true
        if let (fresh, next) = pendingHead {
            if let i = fresh.firstIndex(where: { $0.id == p.id }) {
                var copy = fresh
                copy[i] = c
                pendingHead = (copy, next)
            } else {
                pendingHead = ([c] + fresh, next)
            }
        }
    }

    /// The author retired a post: drop it wherever it is showing.
    private func onPostGone(_ postId: Int64) {
        items.removeAll { $0.id == postId }
        comments.removeValue(forKey: postId)
        if let (fresh, next) = pendingHead {
            pendingHead = (fresh.filter { $0.id != postId }, next)
        }
        if watching == postId { unwatch() }
    }

    // MARK: - Mapping

    private func persistHead(_ page: PostPage) {
        // Store the ids-free projection we can rebuild cards from: the raw JSON is
        // not available past the decoder, so keep the decoded page as property list.
        let enc = try? JSONEncoder().encode(page.items.map(HeadEntry.init))
        defaults.set(enc, forKey: keyHead)
        defaults.set(page.next, forKey: keyHead + ".next")
    }

    private func restore() {
        guard let data = defaults.data(forKey: keyHead),
              let entries = try? JSONDecoder().decode([HeadEntry].self, from: data) else { return }
        items = entries.map { card($0.post) }
        cursor = defaults.string(forKey: keyHead + ".next") ?? ""
        loaded = !items.isEmpty
    }

    /// A codable copy of the fields a card needs, so the head survives a relaunch.
    private struct HeadEntry: Codable {
        var id: Int64, authorId: Int64, kind: String, caption: String, visibility: String
        var username: String, fullName: String, verified: Bool
        var likes: Int, comments: Int, saves: Int, shares: Int
        var liked: Bool, saved: Bool, allowComments: Bool
        var w: Int, h: Int, poster: String?, video: [String: String]

        init(_ p: PostData) {
            id = p.id; authorId = p.authorId; kind = p.kind; caption = p.caption; visibility = p.visibility
            username = p.author.username; fullName = p.author.fullName; verified = p.author.verified
            likes = p.counts.likes; comments = p.counts.comments; saves = p.counts.saves; shares = p.counts.shares
            liked = p.liked; saved = p.saved; allowComments = p.allowComments
            w = p.width; h = p.height; poster = p.posterKey
            video = p.renditions.filter { $0.key.hasSuffix("p") }.mapValues(\.key)
        }

        var post: PostData {
            var p = PostData()
            p.id = id; p.authorId = authorId; p.kind = kind; p.caption = caption; p.visibility = visibility
            p.state = "live"
            var a = PostAuthor(); a.id = authorId; a.username = username; a.fullName = fullName; a.verified = verified
            p.author = a
            var c = PostCounts(); c.likes = likes; c.comments = comments; c.saves = saves; c.shares = shares
            p.counts = c
            p.liked = liked; p.saved = saved; p.allowComments = allowComments
            p.width = w; p.height = h
            var r: [String: Rendition] = [:]
            if let poster { r["poster"] = Rendition(key: poster, mime: "image/jpeg") }
            for (name, key) in video { r[name] = Rendition(key: key, mime: "video/mp4", height: Int(name.dropLast()) ?? 0) }
            p.renditions = r
            return p
        }
    }

    func card(_ p: PostData) -> FeedPost {
        var a = p.author
        if a.username.isEmpty, p.authorId != 0, p.authorId == userId {
            // The author's own post before the server hydrated it (an upload just
            // completed): the profile cache knows exactly who that is.
            let me = ProfileStore.shared.snapshot
            a.username = me.username; a.fullName = me.displayName; a.verified = me.verified
            a.avatarUrl = me.avatarUrl
        }
        if p.authorId != 0, p.authorId != userId { FollowStore.shared.learn(p.authorId, following: p.following) }
        let handle = a.username.isEmpty ? "@user" : "@\(a.username)"
        let name = a.fullName.isEmpty ? a.username : a.fullName
        let kind: FeedKind = p.kind == "photo" ? .photo : (p.kind == "text" ? .text : .video)
        let post = FeedPost(
            id: p.id,
            creator: Creator(handle: handle, name: name, verified: a.verified,
                             initials: Self.initials(name.isEmpty ? a.username : name), color: Self.palette(p.authorId),
                             avatarURL: MediaConfig.shared.url(a.avatarUrl)),
            description: p.caption,
            sound: kind == .video ? {
                let who = name.isEmpty ? (handle.hasPrefix("@") ? String(handle.dropFirst()) : handle) : name
                return "Original sound – \(who.isEmpty ? "user" : who)"
            }() : "",
            likes: "", comments: "", shares: "", saves: "",
            kind: kind,
            authorId: p.authorId,
            posterURL: MediaConfig.shared.url(p.posterKey),
            videoURL: kind == .video ? MediaConfig.shared.url(p.videoKey(maxHeight: maxVideoHeight)) : nil,
            aspect: p.aspect,
            liked: p.liked, saved: p.saved,
            likeCount: p.counts.likes, commentCount: p.counts.comments,
            saveCount: p.counts.saves, shareCount: p.counts.shares,
            viewCount: p.counts.views,
            allowComments: p.allowComments,
            following: p.following
        )
        return Self.withCounts(post)
    }

    static func withCounts(_ p: FeedPost) -> FeedPost {
        var q = p
        q.likes = compact(Int64(p.likeCount))
        q.comments = compact(Int64(p.commentCount))
        q.saves = compact(Int64(p.saveCount))
        q.shares = compact(Int64(p.shareCount))
        return q
    }

    private func comment(_ c: CommentData) -> FeedComment {
        var user = c.author.username
        var avatar = c.author.avatarUrl
        if c.author.id != 0, c.author.id == userId {
            // My own comment, posted optimistically: the server has not echoed
            // the row yet, so my name and picture come from the profile cache.
            let me = ProfileStore.shared.snapshot
            if user.isEmpty { user = me.username }
            if avatar.isEmpty { avatar = me.avatarUrl }
        }
        return FeedComment(
            id: c.id,
            user: user.isEmpty ? "user" : user,
            text: c.body,
            time: Self.relativeTime(c.createdAt),
            likes: c.likes,
            color: Self.palette(c.author.id),
            authorId: c.author.id,
            avatarURL: MediaConfig.shared.url(avatar)
        )
    }

    private static let paletteColors: [UInt32] = [0x7C3AED, 0xEC4899, 0x0EA5E9, 0xF59E0B, 0x10B981, 0x6366F1, 0xEF4444, 0xF472B6]

    static func palette(_ id: Int64) -> UInt32 {
        paletteColors[Int((UInt64(bitPattern: id) >> 7) % UInt64(paletteColors.count))]
    }

    static func initials(_ name: String) -> String {
        let parts = name.split(whereSeparator: { $0 == " " || $0 == "_" || $0 == "." }).map(String.init)
        switch parts.count {
        case 0: return "•"
        case 1: return String(parts[0].prefix(2)).uppercased()
        default: return (String(parts[0].prefix(1)) + String(parts[1].prefix(1))).uppercased()
        }
    }

    /// 847300 → "847.3K", 1200000 → "1.2M".
    static func compact(_ n: Int64) -> String {
        func trim(_ v: Double) -> String {
            let s = String(format: "%.1f", v)
            return s.hasSuffix(".0") ? String(s.dropLast(2)) : s
        }
        switch n {
        case ..<1_000: return String(n)
        case ..<1_000_000: return trim(Double(n) / 1_000) + "K"
        case ..<1_000_000_000: return trim(Double(n) / 1_000_000) + "M"
        default: return trim(Double(n) / 1_000_000_000) + "B"
        }
    }

    private static let iso = ISO8601DateFormatter()

    static func relativeTime(_ rfc3339: String) -> String {
        guard let t = iso.date(from: rfc3339) else { return "" }
        let d = max(0, Int(Date().timeIntervalSince(t)))
        switch d {
        case ..<60: return "now"
        case ..<3600: return "\(d / 60)m"
        case ..<86400: return "\(d / 3600)h"
        case ..<(86400 * 7): return "\(d / 86400)d"
        default: return "\(d / (86400 * 7))w"
        }
    }
}

private let liveCatalog: [LiveTile] = [
    LiveTile(id: 0, host: "@king_gamer99", name: "KingGamer", viewers: "12.4K", category: "Gaming", initials: "KG", color: 0x7C3AED, flag: "🇺🇸", country: "US", diamonds: "45.2K"),
    LiveTile(id: 1, host: "@soul_singer", name: "SoulSinger", viewers: "8.7K", category: "Music", initials: "SS", color: 0xEC4899, flag: "🇬🇧", country: "UK", diamonds: "32.8K"),
    LiveTile(id: 2, host: "@fit_ali", name: "FitWithAli", viewers: "5.2K", category: "Fitness", initials: "FA", color: 0x10B981, flag: "🇵🇰", country: "PK", diamonds: "18.4K", pk: true),
    LiveTile(id: 3, host: "@chef_marcus", name: "Chef Marcus", viewers: "3.1K", category: "Cooking", initials: "CM", color: 0xF59E0B, flag: "🇫🇷", country: "FR", diamonds: "9.6K"),
    LiveTile(id: 4, host: "@art_luna", name: "ArtByLuna", viewers: "2.8K", category: "Art", initials: "AL", color: 0x0EA5E9, flag: "🇧🇷", country: "BR", diamonds: "7.1K", kind: .audio),
    LiveTile(id: 5, host: "@comedy_rex", name: "ComedyKing", viewers: "1.9K", category: "Comedy", initials: "CK", color: 0xEF4444, flag: "🇮🇳", country: "IN", diamonds: "5.8K", pk: true),
    LiveTile(id: 6, host: "@dj_nova", name: "DJ Nova", viewers: "9.3K", category: "Music", initials: "DN", color: 0x6366F1, flag: "🇦🇪", country: "AE", diamonds: "27.5K"),
    LiveTile(id: 7, host: "@talk_zoe", name: "Zoe Talks", viewers: "4.4K", category: "Chat", initials: "ZT", color: 0xF472B6, flag: "🇹🇷", country: "TR", diamonds: "12.9K", kind: .audio),
]
