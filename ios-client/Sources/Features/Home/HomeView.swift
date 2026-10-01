import SwiftUI

private enum HomeOverlay { case none, search, comments, sound }

struct HeartSpawn: Identifiable {
    let id: Int
    let x: CGFloat
    let y: CGFloat
    let a: Color
    let b: Color
}

let HeartGrads: [(Color, Color)] = [
    (Color(rgb: 0xA855F7), Color(rgb: 0xEC4899)),
    (Color(rgb: 0xFF3CAC), Color(rgb: 0x784BA0)),
    (Color(rgb: 0xFF6B6B), Color(rgb: 0xFF4FD8)),
    (Color(rgb: 0xFC466B), Color(rgb: 0x3F5EFB)),
    (Color(rgb: 0x12C2E9), Color(rgb: 0xC471ED)),
    (Color(rgb: 0xF953C6), Color(rgb: 0xB91D73)),
]

struct HomeView: View {
    let active: Bool
    let device: DeviceClass
    var onImmersive: (Bool) -> Void = { _ in }

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.feedBottomReserve) private var reserve
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var lane: HomeLane = .forYou
    @State private var muted = false
    @State private var index = 0
    @State private var drag: CGFloat = 0
    @ObservedObject private var feed = FeedRepo.shared
    // Follow state is one app-wide relation (feed card, search hit, follower
    // list all flip together); the card's own flag is the server's first word.
    @ObservedObject private var follows = FollowStore.shared
    @ObservedObject private var session = Xani.session
    @State private var paused = false
    @State private var holding = false
    @State private var overlay: HomeOverlay = .none
    @State private var overlayPost: FeedPost?
    @State private var viewing: LiveTile?
    @State private var minimized: LiveTile?
    @State private var heart: HeartSpawn?
    @State private var heartSeq = 0

    private func isFollowed(_ post: FeedPost) -> Bool {
        follows.isFollowing(post.authorId, fallback: post.following)
    }

    var body: some View {
        let posts: [FeedPost] = {
            switch lane {
            case .live: return []
            case .following: return feed.items.filter(isFollowed)
            case .forYou: return feed.items
            }
        }()
        let live = lane == .live ? (FeedStore.live().data ?? []) : []

        ZStack {
            Color.black.ignoresSafeArea()

            switch lane {
            case .live:
                LiveBrowsePane(tiles: live) { tile in
                    viewing = tile
                    minimized = nil
                }
                .padding(.top, insets.top + 56 * scale)
            case .following, .forYou:
                if posts.isEmpty {
                    if lane == .following {
                        emptyFollowing
                    } else if !(feed.refreshing && !feed.loaded) {
                        emptyFeed
                    }
                } else {
                    pager(posts)
                }
            }

            topBar
                .opacity(holding ? 0 : 1)
                .animation(.easeInOut(duration: 0.18), value: holding)
                .allowsHitTesting(!holding)

            if lane == .live, let pip = minimized, viewing == nil {
                LivePipCard(
                    stream: pip,
                    onRestore: { viewing = pip; minimized = nil },
                    onDismiss: { minimized = nil }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, 14 * scale)
                .padding(.bottom, reserve + 20 * scale)
            }

            if let stream = viewing {
                LiveViewerScreen(
                    stream: stream,
                    tiles: live.isEmpty ? (FeedStore.live().data ?? []) : live,
                    onSwitch: { dir in
                        let list = live.isEmpty ? (FeedStore.live().data ?? []) : live
                        guard !list.isEmpty else { return }
                        let idx = list.firstIndex(where: { $0.id == stream.id }) ?? 0
                        let next = (idx + dir + list.count) % list.count
                        viewing = list[next]
                    },
                    onMinimize: { minimized = stream; viewing = nil },
                    onClose: { viewing = nil }
                )
                .ignoresSafeArea()
                .zIndex(40)
            }

            switch overlay {
            case .search:
                FeedSearchPage(onClose: { overlay = .none }).zIndex(50)
            case .comments:
                if let post = overlayPost {
                    // The pager already holds this post's realtime channel, so the
                    // sheet only has to say which order it wants the thread in.
                    FeedCommentsSheet(
                        post: feed.items.first(where: { $0.id == post.id }) ?? post,
                        comments: feed.comments[post.id] ?? [],
                        onClose: { overlay = .none },
                        onSend: { body in feed.addComment(post.id, body) },
                        onLoadMore: { feed.loadMoreComments(post.id) },
                        onSort: { order in feed.loadComments(post.id, sort: order) }
                    )
                    .zIndex(50)
                }
            case .sound:
                if let post = overlayPost {
                    FeedSoundPage(post: post, onClose: { overlay = .none }).zIndex(50)
                }
            case .none:
                EmptyView()
            }
        }
        .onChange(of: lane) { _ in
            index = 0
            drag = 0
            paused = false
            if lane != .live { feed.ensureLoaded() } else { feed.unwatch() }
        }
        .onChange(of: active) { on in
            if !on {
                holding = false
                paused = false
                // Off Home: nothing here is showing a post, so stop paying for
                // its realtime channel. The pager re-takes it on the next settle.
                feed.unwatch()
            } else if lane != .live {
                feed.ensureLoaded()
            }
        }
        .onChange(of: index) { i in
            if lane == .forYou { feed.onPage(i) }
        }
        .onChange(of: session.userId) { id in
            if id == 0 { feed.signedOut() } else { feed.signedIn(id) }
        }
        // A full-screen live broadcast owns the screen: the shell drops its
        // bottom nav while one is open, so only the viewer's own back remains.
        .onChange(of: viewing?.id) { _ in onImmersive(viewing != nil) }
        .onAppear {
            if session.userId != 0 { feed.signedIn(session.userId) }
        }
    }

    /// Empty For You: nothing published yet, or offline with no cached head.
    private var emptyFeed: some View {
        VStack(spacing: 14 * scale) {
            AppText(
                Copy.t(feed.offline ? .homeFeedOffline : .homeFeedEmpty),
                style: Type.label,
                color: Color.white.opacity(0.5),
                alignment: .center
            )
            .padding(.horizontal, 32 * scale)
            if feed.offline {
                Button(action: { feed.refresh() }) {
                    AppText(Copy.t(.homeFeedRetry), style: Type.labelBold, color: .white)
                }
                .buttonStyle(PressScaleStyle())
            }
        }
    }

    private var topBar: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button(action: { overlay = .search }) {
                    if lane == .live {
                        VectorCanvas(
                            [
                                .data("M11 3C7.5 3 4.5 5.7 4.5 9v4.5L2.5 16h17l-2-2.5V9C17.5 5.7 14.5 3 11 3z", .stroke(.white, 1.8)),
                                .data("M9 17.5a2 2 0 004 0", .stroke(.white, 1.8)),
                            ],
                            viewport: 22,
                            side: 22 * scale
                        )
                        .frame(minWidth: 44, minHeight: 44)
                    } else {
                        Icon.search(22 * scale, tint: .white)
                            .frame(minWidth: 44, minHeight: 44)
                    }
                }
                .buttonStyle(PressScaleStyle())
            }
            .padding(.horizontal, 14 * scale)
            .overlay(
                HStack(spacing: 0) {
                    laneTab(Copy.t(.homeLive), .live, liveDot: true)
                    laneTab(Copy.t(.homeFollowing), .following)
                    laneTab(Copy.t(.homeForYou), .forYou, larger: true)
                }
            )
            .padding(.top, insets.top + 6 * scale)
            .padding(.bottom, 8 * scale)
            Spacer(minLength: 0)
        }
        // Solid black like the bottom bar — not a see-through strip that picks
        // up whatever colour the poster behind it happens to be.
        .background(Color.black.ignoresSafeArea(edges: .top))
        .allowsHitTesting(true)
    }

    private func laneTab(_ label: String, _ value: HomeLane, larger: Bool = false, liveDot: Bool = false) -> some View {
        let on = lane == value
        return Button(action: { lane = value }) {
            VStack(spacing: 4 * scale) {
                HStack(spacing: 5 * scale) {
                    if liveDot {
                        Circle().fill(Dark.accent).frame(width: 6 * scale, height: 6 * scale)
                    }
                    AppText(
                        label,
                        style: on ? Type.labelBold : Type.label,
                        color: on ? .white : Color.white.opacity(0.5),
                        sizeOverride: (on || larger) ? 16 : 14
                    )
                }
                Capsule()
                    .fill(on ? Color.white : Color.clear)
                    .frame(width: 22 * scale, height: 2.5 * scale)
            }
            .padding(.horizontal, 14 * scale)
            .frame(minHeight: 44)
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.96))
    }

    private func pager(_ posts: [FeedPost]) -> some View {
        GeometryReader { geo in
            let height = geo.size.height
            let extra = active && overlay == .none ? device.pagerNeighbours : 0
            let start = max(0, index - extra)
            let end = min(posts.count - 1, index + extra)
            ZStack {
                ForEach(start...end, id: \.self) { i in
                    let post = posts[i]
                    FeedPageView(
                        post: post,
                        liked: post.liked,
                        saved: post.saved,
                        followed: isFollowed(post),
                        spin: active && overlay == .none && !paused && !muted && !holding && !reduceMotion && i == index && drag == 0,
                        playing: active && overlay == .none && !paused && i == index && drag == 0,
                        paused: paused,
                        holding: holding,
                        muted: muted,
                        heart: i == index ? heart : nil,
                        // Pressing the heart in the rail reads as the same
                        // gesture as a double tap, so it gets the same
                        // animation — centred, since there is no tap point.
                        onLike: {
                            if !post.liked {
                                heartSeq += 1
                                let g = HeartGrads[heartSeq % HeartGrads.count]
                                heart = HeartSpawn(id: heartSeq, x: .nan, y: .nan, a: g.0, b: g.1)
                            }
                            feed.toggleLike(post.id)
                        },
                        onSave: { feed.toggleSave(post.id) },
                        onFollow: { follows.set(post.authorId, on: !isFollowed(post)) },
                        onMute: { muted.toggle() },
                        onComments: {
                            overlayPost = post
                            overlay = .comments
                        },
                        onSound: {
                            overlayPost = post
                            overlay = .sound
                        },
                        onOpenProfile: { PersonRouter.shared.open(post.authorId) },
                        onTap: { paused.toggle() },
                        // A double tap is affection, never its withdrawal: it
                        // plays the heart every time and only ever turns the
                        // like on. Taking a like back is the rail button's
                        // job, where it is deliberate.
                        onDoubleTap: { pos in
                            heartSeq += 1
                            let g = HeartGrads[heartSeq % HeartGrads.count]
                            heart = HeartSpawn(id: heartSeq, x: pos.x, y: pos.y, a: g.0, b: g.1)
                            feed.likeOn(post.id)
                        },
                        onHeartDone: { id in if heart?.id == id { heart = nil } },
                        onHold: { holding = $0 }
                    )
                    .offset(y: CGFloat(i - index) * height + drag)
                }
            }
            .clipped()
            .gesture(holding ? nil : pagerDrag(height: height, count: posts.count))
        }
        .ignoresSafeArea()
    }

    private func pagerDrag(height: CGFloat, count: Int) -> some Gesture {
        DragGesture()
            .onChanged { value in
                guard active, overlay == .none else { return }
                drag = value.translation.height
            }
            .onEnded { value in
                let delta = value.translation.height
                withAnimation(.interactiveSpring(response: 0.28, dampingFraction: 0.86)) {
                    if delta < -60, index < count - 1 {
                        index += 1
                        paused = false
                        heart = nil
                    } else if delta > 60, index > 0 {
                        index -= 1
                        paused = false
                        heart = nil
                    }
                    drag = 0
                }
            }
    }

    private var emptyFollowing: some View {
        VStack(spacing: 12 * scale) {
            VectorCanvas(
                [
                    .circle(28, 20, 12, .stroke(Color.white.opacity(0.3), 2)),
                    .data("M8 48C8 38 17 32 28 32s20 6 20 16", .stroke(Color.white.opacity(0.3), 2)),
                ],
                viewport: 56,
                side: 56 * scale
            )
            AppText(Copy.t(.homeFollowEmpty), style: Type.label, color: Color.white.opacity(0.5), alignment: .center)
                .padding(.horizontal, 32 * scale)
        }
    }
}

struct FeedPageView: View {
    let post: FeedPost
    let liked: Bool
    let saved: Bool
    let followed: Bool
    var spin: Bool = false
    let playing: Bool
    let paused: Bool
    let holding: Bool
    let muted: Bool
    let heart: HeartSpawn?
    let onLike: () -> Void
    let onSave: () -> Void
    let onFollow: () -> Void
    let onMute: () -> Void
    let onComments: () -> Void
    let onSound: () -> Void
    var onOpenProfile: () -> Void = {}
    let onTap: () -> Void
    let onDoubleTap: (CGPoint) -> Void
    let onHeartDone: (Int) -> Void
    let onHold: (Bool) -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.feedBottomReserve) private var reserve
    @State private var discAngle: Double = 0

    var body: some View {
        let color = Color(rgb: post.creator.color)
        GeometryReader { geo in
            ZStack {
            LinearGradient(
                colors: [color.opacity(0.85), .black, color.opacity(0.45)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            poster(in: geo.size)

            Color.black.opacity(paused ? 0.28 : 0)
                .animation(.easeInOut(duration: 0.3), value: paused)
                .allowsHitTesting(false)

            VStack {
                LinearGradient(colors: [Color.black.opacity(0.72), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 180 * scale)
                Spacer()
                LinearGradient(
                    colors: [.clear, Color.black.opacity(0.4), Color.black.opacity(0.88)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 340 * scale)
            }
            .allowsHitTesting(false)

            if let heart {
                DoubleTapHeartView(spawn: heart, onDone: onHeartDone)
            }

            pauseOverlay

            if holding {
                AppText("2×", style: Type.labelBold, color: .white, sizeOverride: 17)
                    .padding(.horizontal, 22 * scale)
                    .padding(.vertical, 10 * scale)
                    .background(Color.black.opacity(0.62))
                    .overlay(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous).stroke(Color.white.opacity(0.2), lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            }

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 5 * scale) {
                    HStack(spacing: 6 * scale) {
                        AppText(
                            post.creator.name.isEmpty
                                ? String(post.creator.handle.drop(while: { $0 == "@" }))
                                : post.creator.name,
                            style: Type.labelBold,
                            color: .white,
                            sizeOverride: 15
                        )
                            .contentShape(Rectangle())
                            .onTapGesture { onOpenProfile() }
                        if post.creator.verified {
                            VectorCanvas(
                                [
                                    .circle(7, 7, 7, .fill(Dark.primary)),
                                    .data("M4 7l2 2 4-4", .stroke(.white, 1.6)),
                                ],
                                viewport: 14,
                                side: 14 * scale
                            )
                        }
                    }
                    AppText(
                        post.description.count > 90 ? String(post.description.prefix(90)) + "…" : post.description,
                        style: Type.caption,
                        color: Color.white.opacity(0.88)
                    )
                    if !post.sound.isEmpty {
                        Button(action: onSound) {
                            HStack(spacing: 8 * scale) {
                                VectorCanvas(
                                    [
                                        .circle(7, 7, 5, .stroke(Color.white.opacity(0.7), 1.2)),
                                        .circle(7, 7, 2, .fill(Color.white.opacity(0.7))),
                                    ],
                                    viewport: 14,
                                    side: 14 * scale
                                )
                                AppText(post.sound, style: Type.label, color: Color.white.opacity(0.75), sizeOverride: 12, lineLimit: 1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.leading, 14 * scale)
                Spacer(minLength: 68 * scale)
            }
            .padding(.bottom, reserve + 10 * scale)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            .opacity(holding ? 0 : 1)
            .animation(.easeInOut(duration: 0.18), value: holding)

            VStack(spacing: 20 * scale) {
                ZStack(alignment: .bottom) {
                    ZStack {
                        Circle().fill(color)
                        if let u = post.creator.avatarURL {
                            RemoteImage(url: u, contentMode: .fill, fallback: .clear)
                        } else {
                            AppText(post.creator.initials, style: Type.labelBold, color: .white, sizeOverride: 15)
                        }
                    }
                    .frame(width: 48 * scale, height: 48 * scale)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color.white, lineWidth: 2 * scale))
                    .contentShape(Circle())
                    .onTapGesture { onOpenProfile() }
                    Button(action: onFollow) {
                        Text(followed ? "✓" : "+")
                            .font(.system(size: 11 * scale, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 20 * scale, height: 20 * scale)
                            .background(followed ? Dark.primary : Dark.accent)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.black, lineWidth: 2 * scale))
                    }
                    .buttonStyle(PressScaleStyle(pressedScale: 0.9))
                    .offset(y: 8 * scale)
                }
                .padding(.bottom, 4 * scale)

                action(post.likes, liked, Dark.accent, onLike) {
                    VectorCanvas(
                        [.data("M16 27S5 20 5 12.5C5 9.4 7.5 7 10.5 7c1.8 0 3.5.9 4.6 2.3a2 2 0 002.8 0C18.5 8 20.2 7 22 7c3 0 5.5 2.4 5.5 5.5C27.5 20 16 27 16 27z", liked ? .fill(Dark.accent) : .stroke(.white, 1.8))],
                        viewport: 32,
                        side: 32 * scale
                    )
                    .scaleEffect(liked ? 1.14 : 1)
                    .animation(.easeOut(duration: 0.16), value: liked)
                }
                action(post.comments, false, .white, onComments) {
                    VectorCanvas([.data("M4 4h22v18H17l-4 4-4-4H4V4z", .stroke(.white, 1.8))], viewport: 30, side: 30 * scale)
                }
                action(post.saves, saved, Color(rgb: 0xFBBF24), onSave) {
                    VectorCanvas(
                        [.data("M3 3h20v24l-10-7L3 27V3z", saved ? .fill(Color(rgb: 0xFBBF24)) : .stroke(.white, 1.8))],
                        viewport: 26,
                        side: 26 * scale
                    )
                }
                action(post.shares, false, .white, {}) {
                    VectorCanvas(
                        [
                            .circle(20, 5, 3, .stroke(.white, 1.8)),
                            .circle(6, 14, 3, .stroke(.white, 1.8)),
                            .circle(20, 23, 3, .stroke(.white, 1.8)),
                            .data("M9 12.5l8-6M9 15.5l8 6", .stroke(.white, 1.8)),
                        ],
                        viewport: 28,
                        side: 28 * scale
                    )
                }
                if !post.sound.isEmpty {
                    Button(action: onSound) {
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [Color(rgb: 0x1A1A2E), Color(rgb: 0x7C3AED)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                            Circle()
                                .stroke(Color.white.opacity(0.12), lineWidth: 3 * scale)
                            ZStack {
                                Circle().fill(color)
                                if let u = post.creator.avatarURL {
                                    RemoteImage(url: u, contentMode: .fill, fallback: .clear)
                                } else {
                                    AppText(post.creator.initials, style: Type.labelBold, color: .white, sizeOverride: 8)
                                }
                            }
                            .frame(width: 22 * scale, height: 22 * scale)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.white.opacity(0.25), lineWidth: 2 * scale))
                        }
                        .frame(width: 46 * scale, height: 46 * scale)
                        .rotationEffect(.degrees(spin ? discAngle : discAngle.truncatingRemainder(dividingBy: 360)))
                    }
                    .buttonStyle(PressScaleStyle(pressedScale: 0.92))
                    .padding(.top, 4 * scale)
                }
            }
            .padding(.trailing, 10 * scale)
            .padding(.bottom, reserve + 24 * scale)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .opacity(holding ? 0 : 1)
            .animation(.easeInOut(duration: 0.18), value: holding)
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .contentShape(Rectangle())
            .onTapGesture(count: 2) {
                onDoubleTap(CGPoint(x: geo.size.width * 0.5, y: geo.size.height * 0.42))
            }
            .onTapGesture {
                onTap()
            }
            .onLongPressGesture(minimumDuration: 0.32, maximumDistance: 48, pressing: { pressing in
                onHold(pressing)
            }, perform: {})
            .onChange(of: spin) { _, on in
                if on {
                    discAngle = discAngle.truncatingRemainder(dividingBy: 360)
                    withAnimation(.linear(duration: 4).repeatForever(autoreverses: false)) {
                        discAngle += 360
                    }
                }
            }
            .onAppear {
                guard spin else { return }
                withAnimation(.linear(duration: 4).repeatForever(autoreverses: false)) {
                    discAngle = 360
                }
            }
        }
    }

    /// The media layer under the chrome: poster still first, the clip fades in on
    /// its first frame; photos letterbox to their true aspect; text sets the body
    /// large on the author gradient. Anything within ~12% of the screen's own
    /// aspect fills and crops, like every short-video app.
    @ViewBuilder
    private func poster(in size: CGSize) -> some View {
        switch post.kind {
        case .text:
            AppText(
                post.description,
                style: Type.labelBold,
                color: .white,
                alignment: .center,
                lineLimit: 14,
                sizeOverride: post.description.count > 120 ? 20 : 26
            )
            .padding(.horizontal, 28 * scale)
            .padding(.vertical, 120 * scale)
        case .photo, .video:
            let screen = size.width / max(size.height, 1)
            let fill = post.aspect <= screen * 1.12
            let w = size.width
            let h = fill ? size.height : w / max(post.aspect, 0.1)
            ZStack {
                if let u = post.posterURL {
                    RemoteImage(url: u, contentMode: fill ? .fill : .fit, fallback: .clear)
                } else if post.videoURL == nil {
                    AppText(post.creator.initials, style: Type.wordmark, color: Color.white.opacity(0.18), sizeOverride: 96)
                }
                if post.kind == .video, let v = post.videoURL {
                    FeedVideoView(url: v, playing: playing, muted: muted, fill: fill) { at, total in
                        let id = post.id
                        Task { @MainActor in FeedRepo.shared.noteWatch(id, position: at, duration: total) }
                    }
                }
            }
            .frame(width: w, height: h)
            .clipped()
            .frame(width: size.width, height: size.height)
        }
    }

    private var pauseOverlay: some View {
        VStack(spacing: 14 * scale) {
            Button(action: onMute) {
                Image(systemName: muted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                    .foregroundColor(.white.opacity(0.9))
                    .font(.system(size: 18 * scale))
                    .frame(width: 46 * scale, height: 46 * scale)
                    .background(Color.black.opacity(0.42))
                    .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.9))
            .allowsHitTesting(paused && !holding)

            Image(systemName: paused ? "play.fill" : "pause.fill")
                .foregroundColor(.white)
                .font(.system(size: 22 * scale))
                .frame(width: 66 * scale, height: 66 * scale)
                .background(Color.black.opacity(0.42))
                .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                .clipShape(Circle())
                .allowsHitTesting(false)
        }
        .opacity((paused && !holding) ? 1 : 0)
        .animation(.easeInOut(duration: paused ? 0.2 : 0.4), value: paused)
        .allowsHitTesting(paused && !holding)
    }

    private func action<G: View>(
        _ label: String,
        _ on: Bool,
        _ color: Color,
        _ tap: @escaping () -> Void,
        @ViewBuilder glyph: () -> G
    ) -> some View {
        Button(action: tap) {
            VStack(spacing: 4 * scale) {
                glyph()
                AppText(label, style: Type.labelBold, color: on ? color : .white, sizeOverride: 12)
            }
            .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.92))
    }
}

private struct DoubleTapHeartView: View {
    let spawn: HeartSpawn
    var onDone: (Int) -> Void
    @Environment(\.uiScale) private var scale
    @State private var progress: CGFloat = 0

    var body: some View {
        let side = 110 * scale
        VectorCanvas(
            [
                .data(
                    "M55 90 C55 90 14 62 14 36 C14 22 24 13 35 13 C43 13 50 18 55 26 C60 18 67 13 75 13 C86 13 96 22 96 36 C96 62 55 90 55 90 Z",
                    .gradientFill([spawn.a, spawn.b])
                ),
            ],
            viewport: 110,
            side: side
        )
        .position(x: spawn.x, y: spawn.y)
        .offset(y: -130 * scale * progress)
        .scaleEffect(progress < 0.14 ? 0.4 + (progress / 0.14) * 0.75 : 1.15 - 0.15 * ((progress - 0.14) / 0.86))
        .rotationEffect(.degrees(sin(Double(progress) * 9) * 7 * Double(1 - progress)))
        .opacity(progress < 0.72 ? 1 : 1 - (progress - 0.72) / 0.28)
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.linear(duration: 1.15)) { progress = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.15) { onDone(spawn.id) }
        }
        .id(spawn.id)
    }
}

extension SessionManager {
    /// The signed-in account id, 0 when signed out; keyed so a silent token
    /// renew does not re-bind the feed.
    var userId: Int64 {
        if case .signedIn(let user, _) = state { return Int64(user.id) ?? 0 }
        return 0
    }
}
