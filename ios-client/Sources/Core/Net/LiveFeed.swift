import Foundation

/// The one realtime connection per signed-in device, shared by every surface
/// that wants it.
///
/// The server multiplexes profile edits, balance movements and post state onto
/// a single Server-Sent Events stream. Holders (`hold()`) are reference counted:
/// the first opens the socket, the last closes it, so the profile screen and an
/// in-flight upload never hold two connections, and a backgrounded app with no
/// holder holds none.
///
/// Listeners are cheap closures registered once at startup; they only pay for
/// the socket while something on screen (or a pending post) needs freshness.
@MainActor
final class LiveFeed {
    static let shared = LiveFeed()

    typealias Token = Int

    private var onProfile: [Token: (ProfileData) -> Void] = [:]
    private var onBalance: [Token: (Balance) -> Void] = [:]
    private var onPost: [Token: (PostData) -> Void] = [:]
    private var onPostGone: [Token: (Int64) -> Void] = [:]
    private var onCounts: [Token: (Int64, LiveCounts) -> Void] = [:]
    private var onChat: [Token: (ChatEvent) -> Void] = [:]
    private var onCall: [Token: (CallEvent) -> Void] = [:]
    private var onProfileViews: [Token: (Int64) -> Void] = [:]
    private var onConnected: [Token: () -> Void] = [:]
    private var onResync: [Token: () -> Void] = [:]
    private var onNotice: [Token: (NoticeData) -> Void] = [:]
    private var onReward: [Token: (RewardToast) -> Void] = [:]
    private var onGifting: [Token: (GiftingUserEvent) -> Void] = [:]
    private var nextToken = 0

    private var holders = 0
    private var socket: Task<Void, Never>?

    private init() {}

    // MARK: - Listeners

    @discardableResult
    func listenProfile(_ f: @escaping (ProfileData) -> Void) -> Token { let t = token(); onProfile[t] = f; return t }

    @discardableResult
    func listenBalance(_ f: @escaping (Balance) -> Void) -> Token { let t = token(); onBalance[t] = f; return t }

    @discardableResult
    func listenPosts(_ f: @escaping (PostData) -> Void) -> Token { let t = token(); onPost[t] = f; return t }

    /// Post ids the author retired: every surface holding one drops it.
    @discardableResult
    func listenPostsGone(_ f: @escaping (Int64) -> Void) -> Token { let t = token(); onPostGone[t] = f; return t }

    /// Engagement on one of the signed-in account's own posts — a stranger's
    /// like, comment or counted watch. It arrives here rather than on the post's
    /// own topic because the author is not watching their own post while
    /// someone else is, and their grid still has to show the new number.
    @discardableResult
    func listenCounts(_ f: @escaping (Int64, LiveCounts) -> Void) -> Token { let t = token(); onCounts[t] = f; return t }

    /// Chat traffic for the signed-in account: messages, receipts, reactions,
    /// typing, presence, row updates.
    @discardableResult
    func listenChat(_ f: @escaping (ChatEvent) -> Void) -> Token { let t = token(); onChat[t] = f; return t }

    /// Call signalling and live-room frames: invite, accept, SDP/ICE relay, end.
    @discardableResult
    func listenCall(_ f: @escaping (CallEvent) -> Void) -> Token { let t = token(); onCall[t] = f; return t }

    /// Somebody opened the signed-in account's profile. The counter in the
    /// header and the "who viewed me" list both hang off this.
    @discardableResult
    func listenProfileViews(_ f: @escaping (Int64) -> Void) -> Token { let t = token(); onProfileViews[t] = f; return t }

    /// Fires each time the socket (re)connects, so a listener can fetch what it missed.
    @discardableResult
    func listenConnected(_ f: @escaping () -> Void) -> Token { let t = token(); onConnected[t] = f; return t }

    @discardableResult
    func listenResync(_ f: @escaping () -> Void) -> Token { let t = token(); onResync[t] = f; return t }

    @discardableResult
    func listenNotices(_ f: @escaping (NoticeData) -> Void) -> Token { let t = token(); onNotice[t] = f; return t }

    /// A live-time reward landed: the HUD toast and My Rewards refresh. Dedupe on ledgerId.
    @discardableResult
    func listenRewards(_ f: @escaping (RewardToast) -> Void) -> Token { let t = token(); onReward[t] = f; return t }

    /// A gift the signed-in account received or sent, with today's running totals.
    @discardableResult
    func listenGifting(_ f: @escaping (GiftingUserEvent) -> Void) -> Token { let t = token(); onGifting[t] = f; return t }

    /// A post state learned from a direct response (text post, complete call)
    /// rather than the stream, fanned out on the same bus so the feed, the grid
    /// and the posting strip update from one event whichever path it took.
    func emitPost(_ post: PostData) {
        onPost.values.forEach { $0(post) }
    }

    /// Same, for a delete the app performed itself: one event, every surface.
    func emitPostGone(_ postId: Int64) {
        onPostGone.values.forEach { $0(postId) }
    }

    func stopListening(_ t: Token) {
        onProfile[t] = nil
        onBalance[t] = nil
        onPost[t] = nil
        onPostGone[t] = nil
        onCounts[t] = nil
        onChat[t] = nil
        onCall[t] = nil
        onProfileViews[t] = nil
        onConnected[t] = nil
        onResync[t] = nil
        onNotice[t] = nil
        onReward[t] = nil
        onGifting[t] = nil
    }

    // MARK: - Holding

    /// Keeps the connection open for as long as the calling task lives.
    /// Suspends until cancelled.
    func hold() async {
        acquire()
        defer { release() }
        // Sleep until cancellation; the socket itself runs in its own task.
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 3_600_000_000_000)
        }
    }

    /// Drops the socket on sign-out regardless of holders; they re-acquire on sign-in.
    func reset() {
        socket?.cancel()
        socket = nil
        holders = 0
    }

    private func acquire() {
        holders += 1
        guard socket == nil else { return }
        socket = Task { [weak self] in
            await Xani.profile.stream(
                onProfile: { data in Task { @MainActor in self?.onProfile.values.forEach { $0(data) } } },
                onBalance: { b in Task { @MainActor in self?.onBalance.values.forEach { $0(b) } } },
                onPost: { p in Task { @MainActor in self?.onPost.values.forEach { $0(p) } } },
                onPostGone: { id in Task { @MainActor in self?.onPostGone.values.forEach { $0(id) } } },
                onCounts: { id, c in Task { @MainActor in self?.onCounts.values.forEach { $0(id, c) } } },
                onChat: { ev in Task { @MainActor in self?.onChat.values.forEach { $0(ev) } } },
                onCall: { ev in Task { @MainActor in self?.onCall.values.forEach { $0(ev) } } },
                onProfileViews: { v in Task { @MainActor in self?.onProfileViews.values.forEach { $0(v) } } },
                onNotice: { n in Task { @MainActor in self?.onNotice.values.forEach { $0(n) } } },
                onReward: { r in Task { @MainActor in self?.onReward.values.forEach { $0(r) } } },
                onGifting: { g in Task { @MainActor in self?.onGifting.values.forEach { $0(g) } } },
                onOpen: { Task { @MainActor in self?.onConnected.values.forEach { $0() } } },
                onResync: { Task { @MainActor in self?.onResync.values.forEach { $0() } } }
            )
        }
    }

    private func release() {
        holders = max(0, holders - 1)
        if holders == 0 {
            socket?.cancel()
            socket = nil
        }
    }

    private func token() -> Token {
        nextToken += 1
        return nextToken
    }
}
