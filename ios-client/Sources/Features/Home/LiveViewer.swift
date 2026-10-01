import SwiftUI

struct LiveViewerScreen: View {
    let stream: LiveTile
    let tiles: [LiveTile]
    var onSwitch: (Int) -> Void
    var onMinimize: () -> Void
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var slide: CGFloat = 1
    @State private var swipeY: CGFloat = 0
    @State private var exitSheet = false
    @State private var giftSheet = false
    // roomId 0: the session only delivers the current room's frames anyway.
    @StateObject private var giftOverlay = GiftOverlayModel(roomId: 0)
    @State private var chatDraft = ""
    @State private var followedHost = false
    @State private var heartSeq = 0
    @StateObject private var games = LiveGamesModel()
    @ObservedObject private var live = LiveSession.shared
    @FocusState private var composing: Bool
    @State private var hearts: [(id: Int, x: CGFloat)] = []

    /// The room's real lines — the same list every device in the room holds.
    private var chat: [LiveChatData] {
        guard let s = live.state, s.roomId == stream.roomId else { return [] }
        return s.chat
    }

    private static let chatTints: [Color] = [
        Color(rgb: 0xF472B6), Color(rgb: 0x34D399), Color(rgb: 0xA78BFA),
        Color(rgb: 0x60A5FA), Color(rgb: 0xFBBF24), Color(rgb: 0xF87171),
    ]
    private func chatTint(_ userId: Int64) -> Color { Self.chatTints[Int(userId % Int64(Self.chatTints.count))] }

    private func sendChat() {
        let t = chatDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        LiveSession.shared.sendChat(t)
        chatDraft = ""
    }

    var body: some View {
        let color = Color(rgb: stream.color)
        ZStack {
            LinearGradient(colors: [color.opacity(0.75), Color(rgb: 0x07070F), color.opacity(0.35)], startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
            AppText(stream.initials, style: Type.wordmark, color: Color.white.opacity(0.14), sizeOverride: 88)

            // The broadcast when the tile has a room (SFU seat or the CDN
            // playlist, whichever the server handed us); the wash until then.
            if stream.roomId != 0, let session = live.state, session.roomId == stream.roomId {
                if session.transport == "sfu" {
                    // Keyed on the renderer instance: a rejoin hands SwiftUI a
                    // new UIView, and without the id it would keep painting the
                    // released one — a black host, at random. mediaRev bumps
                    // republish state, so a track landing late re-asks here.
                    if let host = LiveSession.shared.hostView() {
                        PlatformView(view: host).id(ObjectIdentifier(host)).ignoresSafeArea()
                    }
                } else if !session.hlsUrl.isEmpty {
                    HlsPlayerView(url: session.hlsUrl).ignoresSafeArea()
                }
                if session.phase == .connecting || session.phase == .reconnecting {
                    VStack {
                        Spacer()
                        AppText(Copy.t(session.phase == .reconnecting ? .callReconnecting : .inboxConnecting), style: Type.labelBold, color: Color.white.opacity(0.7))
                            .padding(.bottom, 160 * scale)
                    }
                }
            }

            LinearGradient(
                stops: [
                    .init(color: Color(rgb: 0x07070F).opacity(0.72), location: 0),
                    .init(color: .clear, location: 0.22),
                    .init(color: .clear, location: 0.55),
                    .init(color: Color(rgb: 0x07070F).opacity(0.95), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            .allowsHitTesting(false)

            // Swipe zone — right side hops streams
            HStack {
                Spacer()
                Color.clear
                    .frame(width: UIScreen.main.bounds.width * 0.42)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture()
                            .onChanged { swipeY = $0.translation.height * 0.38 }
                            .onEnded { v in
                                if v.translation.height < -55 { onSwitch(1) }
                                else if v.translation.height > 55 { onSwitch(-1) }
                                swipeY = 0
                            }
                    )
            }
            .padding(.top, 90 * scale)
            .padding(.bottom, 160 * scale)

            VStack(spacing: 0) {
                topBar
                statsRow
                Spacer(minLength: 0)
                heartsLayer
                chatLayer
                actionBar
                followRow
            }
            .padding(.top, insets.top)
            .padding(.bottom, max(insets.bottom, 8 * scale))

            // Gifts landing in the room — over the video and chrome, under the sheets.
            if stream.roomId != 0 { GiftOverlay(model: giftOverlay) }

            if exitSheet {
                LiveExitSheet(
                    stream: stream,
                    onMinimize: { exitSheet = false; dismiss(minimize: true) },
                    onCloseStream: { exitSheet = false; dismiss(minimize: false) },
                    onCancel: { exitSheet = false }
                )
            }

            if giftSheet, stream.roomId != 0 {
                LiveGiftSheet(
                    roomId: stream.roomId,
                    hostId: live.state?.room.hostId ?? 0,
                    onClose: { withAnimation(.easeIn(duration: 0.22)) { giftSheet = false } }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(2)
            }

            // Half-mode games layer over the broadcast — catalog sheet + game sheet.
            LiveGamesOverlay(model: games)
        }
        .offset(x: UIScreen.main.bounds.width * slide, y: swipeY)
        .opacity(abs(swipeY) > 1 ? max(0.3, 1 - abs(swipeY) / 200) : 1)
        // Watching a broadcast holds the screen awake: no dim, no auto-lock,
        // no "the stream froze because the phone slept".
        .keepScreenAwake()
        .onAppear {
            withAnimation(.easeInOut(duration: 0.36)) { slide = 0 }
            if stream.roomId != 0 { LiveSession.shared.watch(roomId: stream.roomId) }
        }
        .onDisappear { if stream.roomId != 0 { LiveSession.shared.leave() } }
    }

    private var topBar: some View {
        let color = Color(rgb: stream.color)
        return HStack(spacing: 10 * scale) {
            Button(action: { exitSheet = true }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 14 * scale, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 36 * scale, height: 36 * scale)
                    .background(Color.black.opacity(0.5))
                    .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)

            HStack(spacing: 8 * scale) {
                ZStack(alignment: .bottomTrailing) {
                    Circle().fill(color).frame(width: 36 * scale, height: 36 * scale)
                        .overlay(Circle().stroke(Color.white.opacity(0.25), lineWidth: 2 * scale))
                        .overlay(AppText(stream.initials, style: Type.labelBold, color: .white, sizeOverride: 12))
                    Circle().fill(Color(rgb: 0x10B981)).frame(width: 11 * scale, height: 11 * scale)
                        .overlay(Circle().stroke(Color(rgb: 0x07070F), lineWidth: 2 * scale))
                }
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 5 * scale) {
                        AppText(stream.name, style: Type.labelBold, color: .white, sizeOverride: 13)
                        AppText(stream.flag, style: Type.label, sizeOverride: 13)
                    }
                    AppText(stream.host, style: Type.caption, color: Color.white.opacity(0.5), sizeOverride: 10)
                }
                Spacer(minLength: 0)
                HStack(spacing: 4 * scale) {
                    Circle().fill(Color.white.opacity(0.8)).frame(width: 4 * scale, height: 4 * scale)
                    AppText(Copy.t(.liveBadge), style: Type.labelBold, color: .white, sizeOverride: 10)
                }
                .padding(.horizontal, 8 * scale)
                .padding(.vertical, 3 * scale)
                .background(Color(rgb: 0xEC4899))
                .clipShape(RoundedRectangle(cornerRadius: 5 * scale, style: .continuous))
            }
            .padding(.leading, 6 * scale)
            .padding(.trailing, 10 * scale)
            .padding(.vertical, 6 * scale)
            .background(Color.black.opacity(0.42))
            .overlay(Capsule().stroke(Color.white.opacity(0.1), lineWidth: 1))
            .clipShape(Capsule())

            Button(action: {}) {
                Image(systemName: "ellipsis")
                    .foregroundColor(.white)
                    .frame(width: 36 * scale, height: 36 * scale)
                    .background(Color.black.opacity(0.5))
                    .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14 * scale)
        .padding(.vertical, 8 * scale)
    }

    private var sheetSpring: Animation { .spring(response: 0.38, dampingFraction: 0.86) }

    private var statsRow: some View {
        HStack(spacing: 8 * scale) {
            pill { HStack(spacing: 5 * scale) {
                Image(systemName: "eye").font(.system(size: 10 * scale)).foregroundColor(.white)
                AppText(stream.viewers, style: Type.labelBold, color: .white, sizeOverride: 11)
            }}
            pill { HStack(spacing: 5 * scale) {
                AppText("💎", style: Type.caption, sizeOverride: 12)
                let session = live.state.flatMap { $0.roomId == stream.roomId && stream.roomId != 0 ? $0 : nil }
                AppText(session.map { formatCoins($0.roomCoins) } ?? stream.diamonds, style: Type.labelBold, color: Color(rgb: 0xC4B5FD), sizeOverride: 11)
            }}
            pill { HStack(spacing: 5 * scale) {
                AppText(stream.flag, style: Type.caption, sizeOverride: 11)
                AppText(stream.country, style: Type.label, color: Color.white.opacity(0.75), sizeOverride: 11)
            }}
            Spacer()
            pill { AppText(stream.category, style: Type.caption, color: Color.white.opacity(0.55), sizeOverride: 10) }
        }
        .padding(.horizontal, 14 * scale)
        .padding(.top, 4 * scale)
    }

    private func pill<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .padding(.horizontal, 12 * scale)
            .padding(.vertical, 5 * scale)
            .background(Color.black.opacity(0.42))
            .overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1))
            .clipShape(Capsule())
    }

    private var heartsLayer: some View {
        ZStack(alignment: .bottomLeading) {
            ForEach(hearts, id: \.id) { h in
                FloatingHeartView(xFrac: h.x) {
                    hearts.removeAll { $0.id == h.id }
                }
            }
        }
        .frame(height: 120 * scale)
        .frame(maxWidth: .infinity)
        .allowsHitTesting(false)
    }

    /// A keyed lazy list pinned to the newest line: a new message lays out one
    /// row, not the screen, and 40 lines stay 40 rows however busy the room is.
    private var chatLayer: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: 6 * scale) {
                    ForEach(chat, id: \.stableKey) { m in
                        let name = m.user.displayName.isEmpty ? "…" : m.user.displayName
                        HStack(alignment: .top, spacing: 5 * scale) {
                            AppText(name, style: Type.labelBold, color: chatTint(m.user.id), lineLimit: 1, sizeOverride: 11)
                            AppText(m.text, style: Type.caption, color: Color.white.opacity(m.id == 0 ? 0.6 : 0.92), sizeOverride: 12)
                        }
                        .padding(.horizontal, 9 * scale).padding(.vertical, 5 * scale)
                        .background(Color.black.opacity(0.28))
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                        .id(m.stableKey)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(height: 132 * scale)
            .onChange(of: chat.last?.stableKey) { key in
                if let key { withAnimation(.easeOut(duration: 0.18)) { proxy.scrollTo(key, anchor: .bottom) } }
            }
        }
        .padding(.horizontal, 14 * scale)
        .padding(.bottom, 10 * scale)
    }

    private var actionBar: some View {
        HStack(spacing: 10 * scale) {
            TextField(Copy.t(.liveSaySomething), text: $chatDraft)
                .font(.system(size: 13 * scale))
                .foregroundColor(.white)
                .focused($composing)
                .submitLabel(.send)
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 9 * scale)
                .background(Color.white.opacity(0.08))
                .overlay(Capsule().stroke(Color.white.opacity(0.1), lineWidth: 1))
                .clipShape(Capsule())
                .onSubmit(sendChat)

            if !chatDraft.trimmingCharacters(in: .whitespaces).isEmpty {
                // Typing: the row becomes composer + Send, the tools step aside.
                Button(action: sendChat) {
                    AppText(Copy.t(.liveGiftSend), style: Type.labelBold, color: .white, sizeOverride: 13)
                        .padding(.horizontal, 16 * scale)
                        .frame(height: 40 * scale)
                        .background(LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing))
                        .clipShape(Capsule())
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.92))
            } else {
            Button(action: { games.showSheet = true }) {
                GamesGlyph(on: true, side: 20 * scale)
                    .frame(width: 44 * scale, height: 44 * scale)
                    .background(Color.white.opacity(0.08))
                    .overlay(Circle().stroke(Color.white.opacity(0.14), lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.88))

            Button(action: {
                heartSeq += 1
                hearts.append((heartSeq, 0.15 + CGFloat(heartSeq % 7) * 0.045))
                if hearts.count > 12 { hearts.removeFirst() }
            }) {
                AppText("❤️", style: Type.label, sizeOverride: 18)
                    .frame(width: 44 * scale, height: 44 * scale)
                    .background(Color(rgb: 0xEC4899).opacity(0.2))
                    .overlay(Circle().stroke(Color(rgb: 0xEC4899).opacity(0.4), lineWidth: 1))
                    .clipShape(Circle())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.88))

            Button(action: { if stream.roomId != 0 { withAnimation(sheetSpring) { giftSheet = true } } }) {
                AppText("💎", style: Type.label, sizeOverride: 17)
                    .frame(width: 44 * scale, height: 44 * scale)
                    .background(LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .clipShape(Circle())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.88))
            }
        }
        .padding(.horizontal, 14 * scale)
        .padding(.vertical, 10 * scale)
        .background(Color(rgb: 0x07070F).opacity(0.72))
    }

    private var followRow: some View {
        HStack(spacing: 10 * scale) {
            Button(action: { followedHost.toggle() }) {
                AppText(
                    (followedHost ? "✓ " : "+ ") + Copy.t(.liveFollow),
                    style: Type.labelBold,
                    color: .white,
                    sizeOverride: 14
                )
                .frame(maxWidth: .infinity)
                .frame(height: 42 * scale)
                .background {
                    if followedHost {
                        Color.white.opacity(0.1)
                    } else {
                        LinearGradient(colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)], startPoint: .leading, endPoint: .trailing)
                    }
                }
                .clipShape(Capsule())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.97))

            Button(action: { if stream.roomId != 0 { withAnimation(sheetSpring) { giftSheet = true } } }) {
                AppText("💎 " + Copy.t(.liveSendGift), style: Type.labelBold, color: .white, sizeOverride: 13)
                    .frame(maxWidth: .infinity)
                    .frame(height: 42 * scale)
                    .background(Color.white.opacity(0.08))
                    .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                    .clipShape(Capsule())
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.97))
        }
        .padding(.horizontal, 14 * scale)
        .padding(.vertical, 10 * scale)
        .background(Color(rgb: 0x07070F).opacity(0.85))
    }

    private func dismiss(minimize: Bool) {
        withAnimation(.easeInOut(duration: 0.34)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.34) {
            if minimize { onMinimize() } else { onClose() }
        }
    }
}

private struct FloatingHeartView: View {
    let xFrac: CGFloat
    var onDone: () -> Void
    @State private var progress: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            AppText("❤️", style: Type.label, sizeOverride: 22)
                .offset(x: geo.size.width * xFrac, y: geo.size.height * (1 - progress) - geo.size.height * 0.1)
                .opacity(progress < 0.7 ? 1 : 1 - (progress - 0.7) / 0.3)
                .scaleEffect(0.8 + 0.4 * progress)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 2.0)) { progress = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { onDone() }
        }
    }
}

private struct LiveExitSheet: View {
    let stream: LiveTile
    var onMinimize: () -> Void
    var onCloseStream: () -> Void
    var onCancel: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        let color = Color(rgb: stream.color)
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture(perform: onCancel)
            VStack(spacing: 0) {
                Capsule()
                    .fill(Color(rgb: 0x6D28D9).opacity(0.18))
                    .frame(width: 36 * scale, height: 4 * scale)
                    .padding(.top, 20 * scale)

                HStack(spacing: 12 * scale) {
                    RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                        .fill(LinearGradient(colors: [color, Color(rgb: 0xEC4899)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 46 * scale, height: 46 * scale)
                        .overlay(AppText(stream.initials, style: Type.labelBold, color: .white, sizeOverride: 14))
                    VStack(alignment: .leading, spacing: 2 * scale) {
                        AppText(stream.name, style: Type.labelBold, color: Color(rgb: 0x1E0A50), sizeOverride: 14)
                        AppText("\(stream.flag) · \(stream.category)", style: Type.caption, color: Color(rgb: 0x1E0A50).opacity(0.5), sizeOverride: 12)
                    }
                    Spacer()
                    HStack(spacing: 4 * scale) {
                        Image(systemName: "eye").font(.system(size: 11 * scale)).foregroundColor(Color(rgb: 0x5B21B6))
                        AppText(stream.viewers, style: Type.labelBold, color: Color(rgb: 0x5B21B6), sizeOverride: 12)
                    }
                }
                .padding(12 * scale)
                .background(Color(rgb: 0x6D28D9).opacity(0.07))
                .overlay(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous).stroke(Color(rgb: 0x6D28D9).opacity(0.12), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
                .padding(.top, 16 * scale)

                AppText(Copy.t(.liveLeaveTitle), style: Type.labelBold, color: Color(rgb: 0x1E0A50), sizeOverride: 15, alignment: .center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 18 * scale)

                exitOption(
                    title: Copy.t(.liveMinimize),
                    sub: Copy.t(.liveMinimizeSub),
                    titleColor: Color(rgb: 0x1E0A50),
                    bg: Color(rgb: 0x6D28D9).opacity(0.07),
                    border: Color(rgb: 0x6D28D9).opacity(0.16),
                    badge: "PiP",
                    action: onMinimize
                ) {
                    Image(systemName: "arrow.down.right.and.arrow.up.left")
                        .foregroundColor(Color(rgb: 0x6D28D9))
                        .font(.system(size: 16 * scale, weight: .semibold))
                }
                .padding(.bottom, 10 * scale)

                exitOption(
                    title: Copy.t(.liveClose),
                    sub: Copy.t(.liveCloseSub),
                    titleColor: Color(rgb: 0xEF4444),
                    bg: Color(rgb: 0xEF4444).opacity(0.05),
                    border: Color(rgb: 0xEF4444).opacity(0.14),
                    badge: nil,
                    action: onCloseStream
                ) {
                    Image(systemName: "xmark")
                        .foregroundColor(Color(rgb: 0xEF4444))
                        .font(.system(size: 16 * scale, weight: .bold))
                }

                Button(action: onCancel) {
                    AppText(Copy.t(.liveCancelStay), style: Type.labelBold, color: Color(rgb: 0x1E0A50).opacity(0.45), sizeOverride: 14)
                        .padding(8 * scale)
                }
                .buttonStyle(.plain)
                .padding(.top, 14 * scale)
            }
            .padding(.horizontal, 20 * scale)
            .padding(.bottom, max(insets.bottom, 24 * scale))
            .background(
                LinearGradient(colors: [Color(rgb: 0xF0EEFF), Color(rgb: 0xF5F4FF)], startPoint: .top, endPoint: .bottom)
            )
            .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
        }
    }

    private func exitOption<Icon: View>(
        title: String,
        sub: String,
        titleColor: Color,
        bg: Color,
        border: Color,
        badge: String?,
        action: @escaping () -> Void,
        @ViewBuilder icon: () -> Icon
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 14 * scale) {
                icon()
                    .frame(width: 44 * scale, height: 44 * scale)
                    .background(bg)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                VStack(alignment: .leading, spacing: 2 * scale) {
                    AppText(title, style: Type.labelBold, color: titleColor, sizeOverride: 14)
                    AppText(sub, style: Type.caption, color: Color(rgb: 0x1E0A50).opacity(0.5), sizeOverride: 12)
                }
                Spacer()
                if let badge {
                    AppText(badge, style: Type.labelBold, color: Color(rgb: 0x6D28D9), sizeOverride: 10)
                        .padding(.horizontal, 10 * scale)
                        .padding(.vertical, 4 * scale)
                        .background(Color(rgb: 0x6D28D9).opacity(0.1))
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 18 * scale)
            .padding(.vertical, 16 * scale)
            .background(bg)
            .overlay(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous).stroke(border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

struct LivePipCard: View {
    let stream: LiveTile
    var onRestore: () -> Void
    var onDismiss: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var offset: CGSize = .zero

    var body: some View {
        let color = Color(rgb: stream.color)
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                LinearGradient(colors: [color.opacity(0.55), Color(rgb: 0xEC4899).opacity(0.4)], startPoint: .topLeading, endPoint: .bottomTrailing)
                RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                    .fill(LinearGradient(colors: [color, Color(rgb: 0xEC4899)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 38 * scale, height: 38 * scale)
                    .overlay(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous).stroke(Color.white.opacity(0.4), lineWidth: 2 * scale))
                    .overlay(AppText(stream.initials, style: Type.labelBold, color: .white, sizeOverride: 12))
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 9 * scale, weight: .bold))
                        .foregroundColor(Color.white.opacity(0.85))
                        .frame(width: 22 * scale, height: 22 * scale)
                        .background(Color.black.opacity(0.5))
                        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding(6 * scale)

                HStack(spacing: 3 * scale) {
                    Image(systemName: "eye").font(.system(size: 8 * scale)).foregroundColor(.white)
                    AppText(stream.viewers, style: Type.labelBold, color: .white, sizeOverride: 9)
                }
                .padding(.horizontal, 6 * scale)
                .padding(.vertical, 2 * scale)
                .background(Color.black.opacity(0.45))
                .clipShape(Capsule())
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                .padding(7 * scale)
            }
            .frame(height: 80 * scale)

            VStack(alignment: .leading, spacing: 1 * scale) {
                AppText(stream.name, style: Type.labelBold, color: .white, sizeOverride: 11, lineLimit: 1)
                AppText("\(stream.flag) \(stream.category)", style: Type.caption, color: Color.white.opacity(0.5), sizeOverride: 10, lineLimit: 1)
            }
            .padding(.horizontal, 8 * scale)
            .padding(.vertical, 6 * scale)
        }
        .frame(width: 140 * scale)
        .background(LinearGradient(colors: [Color(rgb: 0x1A0836), Color(rgb: 0x2D0F5C)], startPoint: .topLeading, endPoint: .bottomTrailing))
        .overlay(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous).stroke(Color(rgb: 0x8B5CF6).opacity(0.35), lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        .shadow(color: Color(rgb: 0x6D28D9).opacity(0.45), radius: 14, y: 6)
        .offset(offset)
        .gesture(
            DragGesture()
                .onChanged { offset = $0.translation }
                .onEnded { _ in }
        )
        .onTapGesture(perform: onRestore)
    }
}
