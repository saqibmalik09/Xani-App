import SwiftUI

/// One post full-screen, opened from a profile grid cell. Same page as the feed
/// (same media, same rail, same gestures), so a tap in Profile lands on the
/// exact card the feed would have shown. Like/save are optimistic against a
/// local copy — the post may not be in the feed list — and the ack snaps the
/// count to truth. Comments ride the shared repo and the post's live channel.
struct PostViewerView: View {
    let initial: PostData
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ObservedObject private var feed = FeedRepo.shared
    @ObservedObject private var follows = FollowStore.shared
    @State private var post: FeedPost?
    @State private var muted = false
    @State private var paused = false
    @State private var holding = false
    @State private var comments = false
    @State private var heart: HeartSpawn?
    @State private var heartSeq = 0
    @State private var ownerMenu = false
    @State private var ownerEdit = false

    private var mine: Bool {
        let id = post?.authorId ?? initial.authorId
        return id != 0 && id == Xani.currentUserId
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let p = post {
                FeedPageView(
                    post: p,
                    liked: p.liked,
                    saved: p.saved,
                    followed: follows.isFollowing(p.authorId, fallback: p.following),
                    spin: !muted && !reduceMotion && !paused && !holding,
                    playing: !paused && !comments,
                    paused: paused,
                    holding: holding,
                    muted: muted,
                    heart: heart,
                    onLike: { react(like: true) },
                    onSave: { react(like: false) },
                    onFollow: { follows.set(p.authorId, on: !follows.isFollowing(p.authorId, fallback: p.following)) },
                    onMute: { muted.toggle() },
                    onComments: { comments = true },
                    onSound: {},
                    onOpenProfile: {
                        let author = p.authorId
                        onClose()
                        PersonRouter.shared.open(author)
                    },
                    onTap: { paused.toggle() },
                    onDoubleTap: { pos in
                        heartSeq += 1
                        let g = HeartGrads[heartSeq % HeartGrads.count]
                        heart = HeartSpawn(id: heartSeq, x: pos.x, y: pos.y, a: g.0, b: g.1)
                        if !p.liked { react(like: true) }
                    },
                    onHeartDone: { id in if heart?.id == id { heart = nil } },
                    onHold: { holding = $0 }
                )
                .ignoresSafeArea()

                if comments {
                    FeedCommentsSheet(
                        post: p,
                        comments: feed.comments[p.id] ?? [],
                        onClose: { comments = false },
                        onSend: { body in feed.addComment(p.id, body) },
                        onLoadMore: { feed.loadMoreComments(p.id) },
                        onSort: { order in feed.loadComments(p.id, sort: order) }
                    )
                    .zIndex(50)
                }
            }

            // Close top-left; owner overflow top-right — small, over the card.
            VStack {
                HStack {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: 17 * scale, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 36 * scale, height: 36 * scale)
                            .background(Color.black.opacity(0.35))
                            .clipShape(Circle())
                    }
                    .buttonStyle(PressScaleStyle())
                    Spacer()
                    if mine {
                        Button(action: { ownerMenu = true }) {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 14 * scale, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(width: 32 * scale, height: 32 * scale)
                                .background(Color.black.opacity(0.35))
                                .clipShape(Circle())
                        }
                        .buttonStyle(PressScaleStyle())
                    }
                }
                .padding(.horizontal, 12 * scale)
                .padding(.top, insets.top + 6 * scale)
                Spacer()
            }
            .opacity(holding || comments || ownerMenu || ownerEdit ? 0 : 1)
            .allowsHitTesting(!holding && !comments && !ownerMenu && !ownerEdit)

            if ownerMenu {
                ProfilePostActionsSheet(
                    post: initial,
                    onEdit: { ownerEdit = true },
                    onDeleted: onClose,
                    onClose: { ownerMenu = false }
                )
                .zIndex(60)
            }
            if ownerEdit {
                ProfilePostEditSheet(post: initial, onClose: { ownerEdit = false })
                    .zIndex(61)
            }
        }
        .onAppear { if post == nil { post = feed.card(initial) } }
        // Opened from the profile grid, this post is usually not in the feed
        // list at all, so the card takes its numbers straight off the post's
        // realtime channel: another viewer's like moves the rail while this
        // screen is open.
        .task(id: initial.id) {
            feed.watchVisible(initial.id)
            await withTaskCancellationHandler(
                operation: { while !Task.isCancelled { try? await Task.sleep(nanoseconds: 3_600_000_000_000) } },
                onCancel: { Task { @MainActor in FeedRepo.shared.unwatch() } }
            )
        }
        .onReceive(feed.$counts) { all in
            guard var p = post, let c = all[p.id] else { return }
            if let v = c.likes { p.likeCount = v }
            if let v = c.comments { p.commentCount = v }
            if let v = c.saves { p.saveCount = v }
            if let v = c.shares { p.shareCount = v }
            if let v = c.views { p.viewCount = v }
            post = FeedRepo.withCounts(p)
        }
    }

    private func react(like: Bool) {
        guard let before = post else { return }
        let on = like ? !before.liked : !before.saved
        var p = before
        if like { p.liked = on; p.likeCount = max(0, p.likeCount + (on ? 1 : -1)) }
        else { p.saved = on; p.saveCount = max(0, p.saveCount + (on ? 1 : -1)) }
        post = FeedRepo.withCounts(p)
        Task {
            let r = like ? await Xani.media.like(before.id, on: on) : await Xani.media.save(before.id, on: on)
            guard var cur = post else { return }
            if let ack = r.value {
                if like { cur.liked = ack.on; cur.likeCount = ack.count } else { cur.saved = ack.on; cur.saveCount = ack.count }
                post = FeedRepo.withCounts(cur)
            } else if !r.isSessionExpired {
                if like { cur.liked = before.liked; cur.likeCount = before.likeCount }
                else { cur.saved = before.saved; cur.saveCount = before.saveCount }
                post = FeedRepo.withCounts(cur)
            }
        }
    }
}
