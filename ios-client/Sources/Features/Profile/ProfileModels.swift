import SwiftUI
import UIKit

enum ProfileFilter { case posts, locked, saved, liked }
enum ProfileSort { case latest, popular }
enum ProfileStatsKind { case following, followers, receiving }
enum ProfileGender { case male, female, preferNot }
enum ReceivingPeriod { case daily, weekly, monthly }
enum ProfileRoute { case home, edit, settings, password, stats, visits, help, report, terms, roles }

/// One grid cell. `views` doubles as the searchable label; `likes` is the count shown.
struct ProfilePost: Identifiable, Equatable {
    let id: Int64
    let color: UInt32
    var views: String
    var rank: Float
    var thumbURL: URL?
    var likes: String = "0"
    var kind: String = "video" // video | photo | text
    var state: String = "live" // processing | live | failed
    var caption: String = ""
    var isPrivate = false
    var isRestricted = false
    var durationMs = 0
}

/// The signed-in user's own grid: every post in every state, newest first, keyset
/// paged from `/v1/posts/mine`. A post that goes live or fails arrives on the post
/// bus and replaces its cell in place — no re-read.
@MainActor
final class MyPosts: ObservableObject {
    static let shared = MyPosts()

    @Published private(set) var items: [ProfilePost] = []
    @Published private(set) var loaded = false
    /// The wire post behind each cell, so a tap opens it full-screen with no re-read.
    @Published private(set) var raw: [Int64: PostData] = [:]
    private var cursor = ""
    private var userId: Int64 = 0
    private var loading = false

    private init() {
        LiveFeed.shared.listenPosts { [weak self] p in self?.onEvent(p) }
        LiveFeed.shared.listenPostsGone { [weak self] id in self?.onGone(id) }
        LiveFeed.shared.listenCounts { [weak self] id, c in self?.onCounts(id, c) }
    }

    func bind(_ id: Int64) {
        if userId != id {
            userId = id
            items = []
            cursor = ""
            loaded = false
        }
        if !loaded { refresh() }
    }

    func signedOut() {
        userId = 0
        items = []
        raw = [:]
        cursor = ""
        loaded = false
    }

    func refresh() {
        if loading { return }
        loading = true
        Task {
            if let page = await Xani.media.mine().value {
                items = page.items.map(Self.cell)
                raw = Dictionary(page.items.map { ($0.id, $0) }, uniquingKeysWith: { _, b in b })
                cursor = page.next
                loaded = true
                for c in items.prefix(12) { if let u = c.thumbURL { ImageLoader.shared.prefetch(u) } }
            }
            loading = false
        }
    }

    func loadMore() {
        if loading || cursor.isEmpty { return }
        loading = true
        let before = Int64(cursor) ?? 0
        Task {
            if let page = await Xani.media.mine(before: before).value {
                var seen = Set(items.map(\.id))
                items.append(contentsOf: page.items.map(Self.cell).filter { seen.insert($0.id).inserted })
                for p in page.items { raw[p.id] = p }
                cursor = page.next
            }
            loading = false
        }
    }

    private func onEvent(_ p: PostData) {
        guard userId != 0, p.authorId == 0 || p.authorId == userId else { return }
        if p.state == "removed" { onGone(p.id); return }
        let c = Self.cell(p)
        // In place for an edit or a state change, at the head for a new post, so
        // an edited caption lands without the grid jumping.
        if let i = items.firstIndex(where: { $0.id == p.id }) { items[i] = c } else { items.insert(c, at: 0) }
        raw[p.id] = p
        loaded = true
    }

    /// Somebody else watched, liked or commented on one of these posts. The cell
    /// repaints from the server's numbers while the author is looking at the
    /// grid — no pull to refresh, no re-read of the page.
    private func onCounts(_ postId: Int64, _ c: LiveCounts) {
        guard let i = items.firstIndex(where: { $0.id == postId }) else { return }
        var cell = items[i]
        if let v = c.views { cell.views = FeedRepo.compact(v) }
        if let v = c.likes {
            cell.likes = FeedRepo.compact(Int64(v))
            cell.rank = Float(v)
        }
        items[i] = cell
        if var post = raw[postId] {
            if let v = c.views { post.counts.views = v }
            if let v = c.likes { post.counts.likes = v }
            if let v = c.comments { post.counts.comments = v }
            if let v = c.saves { post.counts.saves = v }
            if let v = c.shares { post.counts.shares = v }
            raw[postId] = post
        }
    }

    /// The author deleted this post: the cell goes, here and everywhere else.
    private func onGone(_ postId: Int64) {
        items.removeAll { $0.id == postId }
        raw.removeValue(forKey: postId)
    }

    private static func cell(_ p: PostData) -> ProfilePost {
        ProfilePost(
            id: p.id,
            color: FeedRepo.palette(p.id),
            views: FeedRepo.compact(p.counts.views),
            rank: Float(p.counts.likes),
            thumbURL: MediaConfig.shared.url(p.thumbKey),
            likes: FeedRepo.compact(Int64(p.counts.likes)),
            kind: p.kind,
            state: p.state,
            caption: p.caption,
            isPrivate: p.visibility == "private",
            isRestricted: p.isRestricted,
            durationMs: p.durationMs
        )
    }
}

struct ProfilePerson: Identifiable {
    let id: String
    let name: String
    let handle: String
    let initials: String
    let tintA: UInt32
    let tintB: UInt32
    var mutual: Bool = false
    var diamonds: String = ""
}

struct ProfileReceivingSummary {
    let diamonds: String
    let coins: String
    let gifts: String
}

struct ProfileDraft {
    var displayName: String
    var username: String
    var bio: String
    var gender: ProfileGender
    var status: String
}

/// Snapshot the screens render. Counters stay formatted strings because that is
/// what a cell shows; balances arrive as integers and are formatted here.
struct ProfileSnapshot {
    var displayName: String
    var username: String
    var email: String
    var bio: String
    var status: String
    /// Storage key of the stored profile picture, empty until one is set.
    var avatarUrl: String
    var gender: ProfileGender
    var verified: Bool
    var following: String
    var followers: String
    /// How many people have opened this profile, as the header chip shows it.
    var profileViews: String
    var receiving: String
    var coins: String
    var diamonds: String
    var beans: String
    var balanceCoins: String
    var usernameCooldownDays: Int
    var posts: [ProfilePost]
    var people: [ProfilePerson]
    var receivingDaily: ProfileReceivingSummary
    var receivingWeekly: ProfileReceivingSummary
    var receivingMonthly: ProfileReceivingSummary
}

/// What the username field shows while the user types. `checking` is only ever
/// seen on the first keystroke of a name not already answered this session.
enum UsernameState {
    case idle, checking, available, taken, invalid, reserved, current, cooldown, offline
}

/// Something the user must be told about a write that was already accepted on screen.
enum ProfileNotice: Equatable {
    case usernameLocked(daysLeft: Int)
    case usernameTaken(username: String)
    case saveFailed
}

enum ProfileTokens {
    static let bg = Color.white
    static let bg2 = Color(rgb: 0xF7F7F7)
    static let text = Color(rgb: 0x111111)
    static let sub = Color(rgb: 0x888888)
    static let muted = Color(rgb: 0xBBBBBB)
    static let border = Color(rgb: 0xEBEBEB)
    static let borderStrong = Color(rgb: 0xDBDBDB)
    static let hairline = Color(rgb: 0xF0F0F0)
    static let rowLine = Color(rgb: 0xF5F5F5)
    static let accent = Dark.primary
    static let accentDeep = Color(rgb: 0x7C3AED)
    static let accentSoft = Dark.primary.opacity(0.08)
    static let accentWash = Dark.primary.opacity(0.1)
    static let copyFlash = Color(rgb: 0x38BDF8)
    static let avatarRing = Dark.primary
    static let online = Color(rgb: 0x10B981)
    static let ok = Color(rgb: 0x16A34A)
    static let danger = Color(rgb: 0xEF4444)
    static let dangerSoft = Color(rgb: 0xEF4444).opacity(0.06)
    static var cta: LinearGradient {
        LinearGradient(colors: [Dark.primary, Dark.accent], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    static let scrim = Color.black.opacity(0.4)
    static let sheetScrim = Color.black.opacity(0.35)
}

enum ProfileSaveResult {
    case ok
    case usernameLocked(daysLeft: Int)
}

extension ProfileGender {
    var wire: String {
        switch self {
        case .male: return "male"
        case .female: return "female"
        case .preferNot: return "prefer_not"
        }
    }

    init?(wire: String) {
        switch wire {
        case "male": self = .male
        case "female": self = .female
        case "prefer_not": self = .preferNot
        default: return nil
        }
    }
}

/// Wallet numbers as a cell shows them: 940 · 12.4K · 3.1M. Formatting stays on the
/// client so the same number can render short in a chip and exact in a sheet.
func compact(_ value: Int64) -> String {
    let sign = value < 0 ? "-" : ""
    let n = abs(value)
    func trim(_ v: Double) -> String {
        let rounded = (v * 10).rounded(.towardZero) / 10
        return rounded == rounded.rounded() ? String(Int64(rounded)) : String(rounded)
    }
    switch n {
    case ..<1_000: return "\(sign)\(n)"
    case ..<1_000_000: return sign + trim(Double(n) / 1_000) + "K"
    case ..<1_000_000_000: return sign + trim(Double(n) / 1_000_000) + "M"
    default: return sign + trim(Double(n) / 1_000_000_000) + "B"
    }
}

/// The profile repository: one cache the whole app reads, the API behind it, and a
/// live stream keeping the two in step.
///
/// How a save feels instant: the snapshot changes the moment the user acts, the
/// write is queued, and the queue collapses a burst of keystrokes into one request.
/// Nothing on screen waits for a response. If the server refuses a write, the
/// snapshot is re-synced from it and the user is told through `notice` — the only
/// case where a message appears at all.
///
/// How it stays fresh: `observeLive()` holds a Server-Sent Events stream while a
/// profile surface is on screen, so an edit made on another device, and every coin
/// and diamond movement, arrive without polling.
@MainActor
final class ProfileStore: ObservableObject {
    static let shared = ProfileStore()

    @Published private(set) var snapshot: ProfileSnapshot
    /// Set when a write already shown on screen was refused. The view clears it.
    @Published var notice: ProfileNotice?

    private let defaults = UserDefaults.standard
    private let keyPayload = "xani.profile.payload"

    /// Long enough to collapse typing into one request, short enough to feel saved.
    private let debounce: UInt64 = 220_000_000
    private let maxWriteAttempts = 3
    private let checkCacheMax = 64

    /// Highest rev seen from the server; anything older is a stale event.
    private var serverRev: Int64 = 0
    private var cooldownDays = 0
    private var pending: Patch?
    private var writeTask: Task<Void, Never>?
    private var inFlight = false
    private var checks: [String: UsernameState] = [:]
    private var checkOrder: [String] = []

    /// Partial write. A nil field was not touched and is not sent.
    private struct Patch: Equatable {
        var fullName: String?
        var bio: String?
        var status: String?
        var gender: String?

        func merged(with next: Patch) -> Patch {
            Patch(
                fullName: next.fullName ?? fullName,
                bio: next.bio ?? bio,
                status: next.status ?? status,
                gender: next.gender ?? gender
            )
        }

        var statusOnly: Bool { status != nil && fullName == nil && bio == nil && gender == nil }
        var isEmpty: Bool { self == Patch() }
    }

    private init() {
        snapshot = ProfileStore.seed
        // Paint the last known profile before the first request finishes: the screen
        // opens with real data, not a placeholder that then jumps.
        restore()
    }

    func usernameCooldownDays() -> Int { cooldownDays }

    func canChangeUsername() -> Bool { cooldownDays == 0 }

    /// Binds the store to a signed-in account: paints what the session already
    /// knows, then revalidates against the server.
    func signedIn(user: AccountUser) {
        var next = snapshot
        if !user.fullName.isEmpty { next.displayName = user.fullName }
        if !user.username.isEmpty { next.username = user.username }
        if !user.email.isEmpty { next.email = user.email }
        if next.bio.isEmpty { next.bio = user.bio }
        snapshot = next
        refresh()
    }

    func signedOut() {
        LiveFeed.shared.reset()
        writeTask?.cancel()
        writeTask = nil
        pending = nil
        checks.removeAll()
        checkOrder.removeAll()
        serverRev = 0
        cooldownDays = 0
        balanceRev = 0
        coins = 0
        balanceKnown = false
        snapshot = ProfileStore.seed
        defaults.removeObject(forKey: keyPayload)
    }

    /// Re-reads the profile. Cheap: the server answers this one from memory.
    func refresh() {
        Task { [weak self] in
            let response = await Xani.profile.me()
            guard let data = response.value else { return }
            self?.apply(data, local: false)
        }
    }

    /// Holds the realtime stream open for as long as the caller's task lives, so an
    /// edit on another device and every balance movement land here without polling.
    /// Failures are retried inside the transport, never surfaced.
    func observeLive() async {
        // One socket for the whole app: the feed fans frames out to every store.
        listen()
        await LiveFeed.shared.hold()
    }

    private var listening = false

    private func listen() {
        guard !listening else { return }
        listening = true
        let feed = LiveFeed.shared
        feed.listenProfile { [weak self] data in self?.apply(data, local: false) }
        feed.listenBalance { [weak self] balance in self?.applyBalance(balance) }
        // A visit carries no rev, so it cannot ride the profile event: it is its
        // own frame, and the counter moves the instant somebody opens the profile.
        feed.listenProfileViews { [weak self] views in self?.applyViews(views) }
        feed.listenResync { [weak self] in self?.refresh() }
    }

    /// Applies an edit immediately and queues the write.
    ///
    /// Returns `.usernameLocked` only for the one rule the client can be sure of
    /// without asking — the change window it was told about. Every other rejection
    /// arrives through `notice`, because the screen has already moved on by then.
    @discardableResult
    func save(_ draft: ProfileDraft) -> ProfileSaveResult {
        let current = snapshot
        let nextUser = draft.username
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "@", with: "")
            .lowercased()
        let renaming = !nextUser.isEmpty && nextUser != current.username
        if renaming && !canChangeUsername() {
            return .usernameLocked(daysLeft: cooldownDays)
        }

        let name = draft.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        var next = current
        next.displayName = name.isEmpty ? current.displayName : name
        next.username = renaming ? nextUser : current.username
        next.bio = draft.bio
        next.gender = draft.gender
        next.status = draft.status
        snapshot = next

        enqueue(
            Patch(
                fullName: next.displayName == current.displayName ? nil : next.displayName,
                bio: draft.bio == current.bio ? nil : draft.bio,
                status: draft.status == current.status ? nil : draft.status,
                gender: draft.gender == current.gender ? nil : draft.gender.wire
            )
        )
        if renaming { rename(to: nextUser, previous: current.username) }
        return .ok
    }

    /// "What's on your mind" — called while the user types, so it must be cheap.
    func updateStatus(_ status: String) {
        let trimmed = status.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed != snapshot.status else { return }
        var next = snapshot
        next.status = trimmed
        snapshot = next
        enqueue(Patch(status: trimmed))
    }

    /// Availability for the handle being typed.
    ///
    /// Ordered cheapest first: the current name, then the shape rule, then names
    /// already answered this session, and only then a request. The caller debounces
    /// by cancelling this task, so an abandoned keystroke never reaches the network.
    func checkUsername(_ raw: String) async -> UsernameState {
        let name = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "@", with: "")
            .lowercased()
        if name.isEmpty { return .idle }
        if name == snapshot.username { return .current }
        if !ProfileStore.usernameShape(name) { return .invalid }
        if !canChangeUsername() { return .cooldown }
        if let cached = checks[name] { return cached }

        let response = await Xani.profile.checkUsername(name)
        let result: UsernameState
        if let check = response.value {
            if check.available {
                result = .available
            } else {
                switch check.reason {
                case "reserved": result = .reserved
                case "current": result = .current
                case "invalid": result = .invalid
                case "cooldown": result = .cooldown
                default: result = .taken
                }
            }
        } else {
            result = response.isTransport ? .offline : .invalid
        }

        // Never cache a network failure: the next keystroke should try again.
        if result != .offline { remember(name, result) }
        return result
    }

    // MARK: - Writes

    private func enqueue(_ patch: Patch) {
        guard !patch.isEmpty else { return }
        pending = pending?.merged(with: patch) ?? patch
        writeTask?.cancel()
        writeTask = Task { [weak self] in
            // Collapse the burst: typing a status fires one request, not one per
            // character.
            try? await Task.sleep(nanoseconds: self?.debounce ?? 220_000_000)
            guard !Task.isCancelled else { return }
            await self?.flush()
        }
    }

    private func flush() async {
        guard let patch = pending else { return }
        pending = nil
        inFlight = true
        defer { inFlight = false }

        var attempt = 0
        var backoff: UInt64 = 400_000_000
        while true {
            let response: ApiResponse<ProfileData>
            if patch.statusOnly {
                response = await Xani.profile.setStatus(patch.status ?? "")
            } else {
                response = await Xani.profile.update(
                    fullName: patch.fullName,
                    bio: patch.bio,
                    status: patch.status,
                    gender: patch.gender
                )
            }
            if let data = response.value {
                apply(data, local: false)
                return
            }
            attempt += 1
            // A refused write is refused for good; only transport failures are worth
            // another attempt, and only a bounded number of them.
            if !response.isTransport || attempt >= maxWriteAttempts {
                if !response.isSessionExpired {
                    notice = .saveFailed
                    refresh() // snap the screen back to what the server holds
                }
                return
            }
            try? await Task.sleep(nanoseconds: backoff)
            backoff *= 2
        }
    }

    private func rename(to next: String, previous: String) {
        Task { [weak self] in
            let response = await Xani.profile.setUsername(next)
            guard let self else { return }
            if let data = response.value {
                self.apply(data, local: false)
                return
            }
            // Put the old handle back: a username the server refused was never ours.
            if self.snapshot.username == next {
                var reverted = self.snapshot
                reverted.username = previous
                self.snapshot = reverted
            }
            self.remember(next, self.refusal(response, username: next))
        }
    }

    /// Tells the user why a rename was refused, and remembers it for the field.
    private func refusal(_ response: ApiResponse<ProfileData>, username: String) -> UsernameState {
        switch response.code {
        case "duplicate_username":
            notice = .usernameTaken(username: username)
            return .taken
        case "reserved_username":
            notice = .usernameTaken(username: username)
            return .reserved
        case "username_cooldown":
            notice = .usernameLocked(daysLeft: cooldownDays)
            return .cooldown
        default:
            if !response.isSessionExpired { notice = .saveFailed }
            return .offline
        }
    }

    // MARK: - Applying server state

    /// Folds a server payload into the snapshot.
    ///
    /// Two guards keep realtime from fighting the user: an event older than what we
    /// already show is dropped by `rev`, and while a write of ours is still queued or
    /// in flight, text fields keep what the user typed. Balances, the handle and the
    /// change window always come from the server — those are never locally authored.
    private func apply(_ data: ProfileData, local: Bool) {
        if !local && data.rev < serverRev { return }
        serverRev = data.rev
        cooldownDays = data.usernameCooldownDays
        let keepTyped = inFlight || pending != nil

        var next = snapshot
        if !keepTyped {
            if !data.fullName.isEmpty { next.displayName = data.fullName }
            next.bio = data.bio
            next.status = data.status
            next.gender = ProfileGender(wire: data.gender) ?? next.gender
        }
        if !data.username.isEmpty { next.username = data.username }
        if !data.email.isEmpty { next.email = data.email }
        next.avatarUrl = data.avatarUrl
        next.verified = data.emailVerified
        next.coins = compact(data.balance.coins)
        next.diamonds = compact(data.balance.diamonds)
        next.beans = compact(data.balance.beans)
        next.balanceCoins = compact(data.balance.coins)
        coins = data.balance.coins
        balanceKnown = true
        next.usernameCooldownDays = data.usernameCooldownDays
        next.followers = compact(Int64(data.followers))
        next.following = compact(Int64(data.following))
        next.profileViews = compact(data.profileViews)
        snapshot = next
        persist(data)
    }

    /// The follow ack carries the caller's own following count; apply it at once
    /// so Profile is right before (or without) the stream's profile event.
    func setFollowing(count: Int) {
        var next = snapshot
        next.following = compact(Int64(count))
        snapshot = next
    }

    private func applyViews(_ views: Int64) {
        var next = snapshot
        next.profileViews = compact(views)
        snapshot = next
    }

    /// The sender's spendable coins as a number, for the gift sheet's pill.
    @Published private(set) var coins: Int64 = 0
    private var balanceRev: Int64 = 0
    /// False until the server has told us a balance at least once this process.
    /// A screen that gates on coins must not refuse on a 0 it only assumed.
    private(set) var balanceKnown = false

    /// A balance learned from a direct reply (a gift send) rather than the stream.
    func setBalance(_ balance: Balance) {
        if balance.rev != 0, balance.rev < balanceRev { return }
        balanceRev = balance.rev
        applyBalance(balance)
    }

    /// Wallet events carry only money, so they can always be applied.
    private func applyBalance(_ balance: Balance) {
        coins = balance.coins
        balanceKnown = true
        var next = snapshot
        next.coins = compact(balance.coins)
        next.diamonds = compact(balance.diamonds)
        next.beans = compact(balance.beans)
        next.balanceCoins = compact(balance.coins)
        snapshot = next
    }

    private func remember(_ name: String, _ state: UsernameState) {
        if checks[name] == nil {
            checkOrder.append(name)
            if checkOrder.count > checkCacheMax, let oldest = checkOrder.first {
                checkOrder.removeFirst()
                checks.removeValue(forKey: oldest)
            }
        }
        checks[name] = state
    }

    private func persist(_ data: ProfileData) {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        guard let blob = try? encoder.encode(data) else { return }
        defaults.set(blob, forKey: keyPayload)
    }

    private func restore() {
        guard let blob = defaults.data(forKey: keyPayload) else { return }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        guard let data = try? decoder.decode(ProfileData.self, from: blob) else { return }
        apply(data, local: true)
    }

    static func usernameShape(_ name: String) -> Bool {
        guard name.count >= 3, name.count <= 24 else { return false }
        return name.allSatisfy { $0.isLowercase && $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" }
    }

    private static let seedPeople: [ProfilePerson] = [
        ProfilePerson(id: "1", name: "alex_k", handle: "@alex_k", initials: "AK", tintA: 0x8B5CF6, tintB: 0xEC4899, mutual: true, diamonds: "124"),
        ProfilePerson(id: "2", name: "zara_w", handle: "@zara_w", initials: "ZW", tintA: 0x10B981, tintB: 0x3B82F6, mutual: true, diamonds: "62"),
        ProfilePerson(id: "3", name: "leo99", handle: "@leo99", initials: "LO", tintA: 0xF59E0B, tintB: 0xEF4444, mutual: false, diamonds: "98"),
        ProfilePerson(id: "4", name: "mika_v", handle: "@mika_v", initials: "MV", tintA: 0x06B6D4, tintB: 0x8B5CF6, mutual: false, diamonds: "12"),
        ProfilePerson(id: "5", name: "sky_9", handle: "@sky_9", initials: "S9", tintA: 0xEC4899, tintB: 0xF59E0B, mutual: true, diamonds: "44"),
        ProfilePerson(id: "6", name: "pure_", handle: "@pure_", initials: "PU", tintA: 0x34D399, tintB: 0x60A5FA, mutual: false, diamonds: "8"),
    ]

    /// First-paint values for a screen that has never had a payload: counters and
    /// grid content still come from the feed and social services, which are not this
    /// endpoint. Identity, status and balances are replaced by the first read.
    private static let seed = ProfileSnapshot(
        displayName: "",
        username: "",
        email: "",
        bio: "",
        status: "",
        avatarUrl: "",
        gender: .preferNot,
        verified: false,
        following: "0",
        followers: "0",
        profileViews: "0",
        receiving: "3.2K",
        coins: "0",
        diamonds: "0",
        beans: "0",
        balanceCoins: "0",
        usernameCooldownDays: 0,
        // The grid is served by `MyPosts` from /v1/posts/mine, not this snapshot.
        posts: [],
        people: seedPeople,
        receivingDaily: ProfileReceivingSummary(diamonds: "340", coins: "1,240", gifts: "18"),
        receivingWeekly: ProfileReceivingSummary(diamonds: "2.1K", coins: "7.8K", gifts: "112"),
        receivingMonthly: ProfileReceivingSummary(diamonds: "8.4K", coins: "31K", gifts: "448")
    )
}
