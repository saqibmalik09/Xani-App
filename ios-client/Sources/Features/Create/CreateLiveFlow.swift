import SwiftUI
import AVFoundation

private enum LiveBroadcast { case video, audio }
private enum LiveAudience { case publicRoom, friends, privateRoom }

private struct LiveRoomConfig: Equatable {
    var isVideo: Bool
    var title: String
    var audience: LiveAudience
    var password: String
    var seats: Int
    var hostSeat: Int
    var bannerTint: UInt32
    var frontCamera: Bool
}

private let TitleSuggestions = [
    "Vibing with good music",
    "Late night talk sesh",
    "Let's chat & chill",
    "Gaming with the squad",
    "Q&A — ask me anything",
    "Study with me",
    "Just hanging out",
    "Fan appreciation stream",
]

private let BannerTints: [UInt32] = [0x7C3AED, 0xEC4899, 0x06B6D4, 0xF59E0B, 0x10B981, 0x6366F1]

/// UpdatedFigma Live: lavender setup → seat arrange (audio) → live room.
///
/// Navigation contract (Figma + requested UX): closing the setup returns to
/// the Post camera (onPost), never straight home; ending a live returns to the
/// setup so back/forth toggles cleanly. Only the Post lane closes Create.
struct CreateLiveFlow: View {
    var active: Bool
    var onPost: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var step = 0
    @State private var broadcast = LiveBroadcast.video
    @State private var title = ""
    @State private var audience = LiveAudience.publicRoom
    @State private var password = ""
    @State private var seats: Int? = nil
    @State private var customSeats = ""
    @State private var banner = -1
    @State private var hostSeat = 0
    @State private var frontCam = true
    @State private var suggestions: [String] = Array(TitleSuggestions.shuffled().prefix(5))

    private var resolvedSeats: Int {
        if seats == -1 {
            return min(35, max(1, Int(customSeats) ?? 0))
        }
        return seats ?? 0
    }

    private var canGoVideo: Bool { broadcast == .video }
    private var canNextAudio: Bool { broadcast == .audio && resolvedSeats > 0 }

    private func config(isVideo: Bool) -> LiveRoomConfig {
        LiveRoomConfig(
            isVideo: isVideo,
            title: title.isEmpty ? Copy.t(.createLiveHint) : title,
            audience: audience,
            password: password,
            seats: isVideo ? 0 : resolvedSeats,
            hostSeat: hostSeat,
            bannerTint: banner >= 0 && banner < BannerTints.count ? BannerTints[banner] : BannerTints[0],
            frontCamera: frontCam
        )
    }

    var body: some View {
        ZStack {
            switch step {
            case 1:
                SeatArrangeView(
                    seatCount: max(1, min(35, resolvedSeats)),
                    hostSeat: $hostSeat,
                    onBack: { withAnimation(.easeInOut(duration: 0.32)) { step = 0 } },
                    // Mic granted before the room opens: publishing without it is silent, not an error.
                    onStart: { CallPermissions.request(video: false) { withAnimation(.easeInOut(duration: 0.32)) { step = 2 } } }
                )
                .transition(.asymmetric(
                    insertion: .move(edge: .trailing).combined(with: .opacity),
                    removal: .move(edge: .leading).combined(with: .opacity)
                ))
            case 2:
                LiveRoomView(
                    active: active,
                    config: config(isVideo: broadcast == .video),
                    onEnd: { withAnimation(.easeInOut(duration: 0.32)) { step = 0 } }
                )
                .transition(.opacity)
            default:
                LiveSetupForm(
                    broadcast: $broadcast,
                    title: $title,
                    suggestions: suggestions,
                    audience: $audience,
                    password: $password,
                    seats: $seats,
                    customSeats: $customSeats,
                    banner: $banner,
                    canGoVideo: canGoVideo,
                    canNextAudio: canNextAudio,
                    onClose: onPost,
                    onFlipCam: { frontCam.toggle() },
                    onGoLive: { CallPermissions.request(video: true) { withAnimation(.easeInOut(duration: 0.32)) { step = 2 } } },
                    onNextSeats: { withAnimation(.easeInOut(duration: 0.32)) { step = 1 } }
                )
                .transition(.asymmetric(
                    insertion: .move(edge: .leading).combined(with: .opacity),
                    removal: .move(edge: .trailing).combined(with: .opacity)
                ))
            }
        }
        .animation(.easeInOut(duration: 0.34), value: step)
    }
}

private struct LiveSetupForm: View {
    @Binding var broadcast: LiveBroadcast
    @Binding var title: String
    var suggestions: [String]
    @Binding var audience: LiveAudience
    @Binding var password: String
    @Binding var seats: Int?
    @Binding var customSeats: String
    @Binding var banner: Int
    var canGoVideo: Bool
    var canNextAudio: Bool
    var onClose: () -> Void
    var onFlipCam: () -> Void
    var onGoLive: () -> Void
    var onNextSeats: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    private var isVideo: Bool { broadcast == .video }

    var body: some View {
        ZStack {
            CreateTokens.liveSetupWash.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    softHit(onClose) { CreateIco.close(CreateTokens.liveAccent, side: 16 * scale) }
                    Spacer()
                    HStack(spacing: 2 * scale) {
                        chip("Video", on: isVideo) { broadcast = .video }
                        chip(Copy.t(.createAudioOnly), on: !isVideo) { broadcast = .audio }
                    }
                    .padding(3 * scale)
                    .background(CreateTokens.liveSoft)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(CreateTokens.liveAccent.opacity(0.15), lineWidth: 1))
                    Spacer()
                    if isVideo {
                        softHit(onFlipCam) { CreateIco.flipCam(CreateTokens.liveAccent, side: 18 * scale) }
                    } else {
                        Color.clear.frame(width: 40 * scale, height: 40 * scale)
                    }
                }
                .padding(.horizontal, 18 * scale)
                .padding(.top, insets.top + 12 * scale)

                VStack(alignment: .leading, spacing: 3 * scale) {
                    AppText(
                        Copy.t(isVideo ? .createLiveVideoTitle : .createLiveAudioTitle),
                        style: Type.h2,
                        color: CreateTokens.liveInk,
                        sizeOverride: 22
                    )
                    AppText(
                        Copy.t(isVideo ? .createLiveVideoSub : .createLiveAudioSub),
                        style: Type.caption,
                        color: CreateTokens.liveSub,
                        sizeOverride: 13
                    )
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 18 * scale)
                .padding(.top, 8 * scale)

                if !isVideo {
                    HStack(spacing: 14 * scale) {
                        Circle()
                            .fill(CreateTokens.liveCta)
                            .frame(width: 52 * scale, height: 52 * scale)
                            .overlay(CreateIco.softMic(.white, side: 24 * scale))
                        VStack(alignment: .leading, spacing: 2 * scale) {
                            AppText(Copy.t(.createAudioRoomLabel), style: Type.labelBold, color: CreateTokens.liveInk, sizeOverride: 15)
                            AppText(Copy.t(.createAudioRoomSub), style: Type.micro, color: CreateTokens.liveSub, sizeOverride: 12)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 18 * scale)
                    .padding(.top, 10 * scale)
                }

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18 * scale) {
                        section(Copy.t(.createBanner))
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8 * scale) {
                                ForEach(Array(BannerTints.enumerated()), id: \.offset) { i, tint in
                                    RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                                        .fill(Color(rgb: tint))
                                        .frame(width: 88 * scale, height: 54 * scale)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                                                .stroke(banner == i ? CreateTokens.liveAccent : CreateTokens.liveAccent.opacity(0.12), lineWidth: banner == i ? 2 : 1)
                                        )
                                        .onTapGesture { banner = i }
                                }
                            }
                        }

                        section(Copy.t(.createTitleField))
                        ZStack(alignment: .leading) {
                            if title.isEmpty {
                                AppText(Copy.t(.createLiveTitleHint), style: Type.label, color: CreateTokens.liveSub.opacity(0.45), sizeOverride: 15)
                            }
                            TextField("", text: $title)
                                .font(.system(size: 15 * scale, weight: .medium))
                                .foregroundColor(CreateTokens.liveInk)
                        }
                        .padding(.horizontal, 14 * scale)
                        .padding(.vertical, 12 * scale)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                                .stroke(CreateTokens.liveBorder, lineWidth: 1.5)
                        )

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 7 * scale) {
                                ForEach(suggestions, id: \.self) { s in
                                    let on = title == s
                                    AppText(s, style: Type.label, color: on ? .white : CreateTokens.liveChip, sizeOverride: 12.5)
                                        .padding(.horizontal, 14 * scale)
                                        .padding(.vertical, 7 * scale)
                                        .background(on ? CreateTokens.liveAccent : Color.white)
                                        .clipShape(Capsule())
                                        .overlay(Capsule().stroke(on ? CreateTokens.liveAccent : CreateTokens.liveAccent.opacity(0.18), lineWidth: 1.5))
                                        .onTapGesture { title = s }
                                }
                            }
                        }

                        if !isVideo {
                            section(Copy.t(.createSpeakerSeats))
                            HStack(spacing: 8 * scale) {
                                ForEach([4, 6, 8, 10, -1], id: \.self) { n in
                                    let on = seats == n
                                    AppText(
                                        n == -1 ? Copy.t(.createCustom) : "\(n)",
                                        style: on ? Type.labelBold : Type.label,
                                        color: on ? .white : CreateTokens.liveChip,
                                        sizeOverride: n == -1 ? 11 : 14
                                    )
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 13 * scale)
                                    .background(on ? CreateTokens.liveAccent : Color.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                                            .stroke(on ? CreateTokens.liveAccent : CreateTokens.liveAccent.opacity(0.15), lineWidth: 1.5)
                                    )
                                    .onTapGesture { seats = n }
                                }
                            }
                            if seats == -1 {
                                HStack {
                                    ZStack(alignment: .leading) {
                                        if customSeats.isEmpty {
                                            AppText(Copy.t(.createSeatsHint), style: Type.caption, color: CreateTokens.liveSub.opacity(0.45), sizeOverride: 14)
                                        }
                                        TextField("", text: $customSeats)
                                            .keyboardType(.numberPad)
                                            .font(.system(size: 15 * scale, weight: .semibold))
                                            .foregroundColor(CreateTokens.liveInk)
                                            .onChange(of: customSeats) { v in
                                                let digits = String(v.filter(\.isNumber).prefix(2))
                                                if let n = Int(digits) { customSeats = String(min(35, n)) }
                                                else { customSeats = digits }
                                            }
                                    }
                                    AppText(Copy.t(.createSeatsMax), style: Type.micro, color: CreateTokens.liveSub.opacity(0.4), sizeOverride: 12)
                                }
                                .padding(.horizontal, 16 * scale)
                                .padding(.vertical, 12 * scale)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                                        .stroke(CreateTokens.liveBorder, lineWidth: 1.5)
                                )
                            }
                        }

                        section(Copy.t(.createAudience))
                        HStack(spacing: 8 * scale) {
                            audienceBtn(.publicRoom, .createAudiencePublic, .createAudiencePublicSub)
                            audienceBtn(.friends, .createAudienceFriends, .createAudienceFriendsSub)
                            audienceBtn(.privateRoom, .createAudiencePrivate, .createAudiencePrivateSub)
                        }
                        if audience == .privateRoom {
                            ZStack(alignment: .leading) {
                                if password.isEmpty {
                                    AppText(Copy.t(.createPasswordHint), style: Type.label, color: CreateTokens.liveSub.opacity(0.45), sizeOverride: 14)
                                }
                                SecureField("", text: $password)
                                    .font(.system(size: 14 * scale, weight: .medium))
                                    .foregroundColor(CreateTokens.liveInk)
                            }
                            .padding(.horizontal, 14 * scale)
                            .padding(.vertical, 12 * scale)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                                    .stroke(CreateTokens.liveAccent.opacity(0.25), lineWidth: 1.5)
                            )
                        }
                        Color.clear.frame(height: 88 * scale)
                    }
                    .padding(.horizontal, 18 * scale)
                    .padding(.top, 14 * scale)
                }

                let enabled = isVideo ? canGoVideo : canNextAudio
                Button(action: { if isVideo { onGoLive() } else { onNextSeats() } }) {
                    AppText(
                        isVideo ? Copy.t(.createGoLiveBtn) : (canNextAudio ? Copy.t(.createNextSeats) : Copy.t(.createSelectSeats)),
                        style: Type.labelBold,
                        color: enabled ? .white : CreateTokens.liveSub.opacity(0.4),
                        sizeOverride: 16
                    )
                    .frame(maxWidth: .infinity)
                    .frame(height: 56 * scale)
                    .background {
                        if enabled {
                            CreateTokens.liveCta
                        } else {
                            CreateTokens.liveSoft
                        }
                    }
                    .clipShape(Capsule())
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.97))
                .disabled(!enabled)
                .padding(.horizontal, 18 * scale)
                .padding(.bottom, insets.bottom + 14 * scale)
            }
        }
    }

    private func section(_ text: String) -> some View {
        AppText(text.uppercased(), style: Type.micro, color: CreateTokens.liveAccent.opacity(0.55), sizeOverride: 11)
    }

    private func chip(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(label, style: Type.labelBold, color: on ? .white : CreateTokens.liveSub, sizeOverride: 13)
                .padding(.horizontal, 18 * scale)
                .padding(.vertical, 8 * scale)
                .background(on ? CreateTokens.liveAccent : Color.clear)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private func softHit(_ action: @escaping () -> Void, @ViewBuilder icon: () -> some View) -> some View {
        Button(action: action) {
            icon().frame(width: 40 * scale, height: 40 * scale)
                .background(CreateTokens.liveSoft)
                .clipShape(Circle())
                .overlay(Circle().stroke(CreateTokens.liveAccent.opacity(0.15), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func audienceBtn(_ key: LiveAudience, _ label: CopyKey, _ sub: CopyKey) -> some View {
        let on = audience == key
        return Button(action: { audience = key }) {
            VStack(spacing: 4 * scale) {
                AppText(Copy.t(label), style: Type.labelBold, color: on ? .white : CreateTokens.liveChip, sizeOverride: 13)
                AppText(Copy.t(sub), style: Type.micro, color: on ? Color.white.opacity(0.65) : CreateTokens.liveSub.opacity(0.45), sizeOverride: 10)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12 * scale)
            .background(on ? CreateTokens.liveAccent : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                    .stroke(on ? CreateTokens.liveAccent : CreateTokens.liveAccent.opacity(0.15), lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}

private struct SeatArrangeView: View {
    var seatCount: Int
    @Binding var hostSeat: Int
    var onBack: () -> Void
    var onStart: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var spots: [(id: Int, x: CGFloat, y: CGFloat)] = []

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                HStack {
                    Button(action: onBack) {
                        CreateIco.chevron(.white, side: 18 * scale).frame(width: 40 * scale, height: 40 * scale)
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    VStack(spacing: 2 * scale) {
                        AppText(Copy.t(.createArrangeRoom), style: Type.labelBold, color: .white, sizeOverride: 17)
                        AppText(Copy.t(.createArrangeHint, ["n": "\(seatCount)"]), style: Type.micro, color: Color.white.opacity(0.35), sizeOverride: 12)
                    }
                    Spacer()
                    Color.clear.frame(width: 40 * scale, height: 40 * scale)
                }
                .padding(.horizontal, 18 * scale)
                .padding(.top, insets.top + 10 * scale)

                ScrollView {
                    ZStack(alignment: .topLeading) {
                        Color.clear.frame(height: max(200, (spots.map(\.y).max() ?? 200) + 80))
                        ForEach(spots, id: \.id) { seat in
                            let isHost = seat.id == hostSeat
                            Circle()
                                .fill(isHost ? CreateTokens.liveAccent.opacity(0.22) : Color.white.opacity(0.05))
                                .overlay(Circle().stroke(isHost ? CreateTokens.liveAccent : Color.white.opacity(0.2), lineWidth: 2))
                                .overlay(CreateIco.users(isHost ? Color(rgb: 0xA78BFA) : Color.white.opacity(0.28), side: 20 * scale))
                                .frame(width: 54 * scale, height: 54 * scale)
                                .position(x: seat.x, y: seat.y)
                                .onTapGesture { hostSeat = seat.id }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 20 * scale)
                }

                Button(action: onStart) {
                    AppText(Copy.t(.createStartAudioRoom), style: Type.labelBold, color: .white, sizeOverride: 16)
                        .frame(maxWidth: .infinity)
                        .frame(height: 54 * scale)
                        .background(CreateTokens.liveCta)
                        .clipShape(Capsule())
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.97))
                .padding(.horizontal, 18 * scale)
                .padding(.bottom, insets.bottom + 14 * scale)
            }
            .onAppear {
                spots = layoutRows(count: seatCount, canvasW: geo.size.width)
            }
            .onChange(of: seatCount) { _ in
                spots = layoutRows(count: seatCount, canvasW: geo.size.width)
            }
        }
        .background(Color(rgb: 0x0B0818).ignoresSafeArea())
    }

    private func layoutRows(count: Int, canvasW: CGFloat) -> [(id: Int, x: CGFloat, y: CGFloat)] {
        let cols: Int = count <= 3 ? max(1, count) : (count <= 24 ? 4 : 5)
        let pad: CGFloat = 16
        let usable = canvasW - pad * 2
        let cell = usable / CGFloat(cols)
        let rowH: CGFloat = 70
        let startY: CGFloat = 48
        return (0..<count).map { i in
            (id: i, x: pad + CGFloat(i % cols) * cell + cell / 2, y: startY + CGFloat(i / cols) * rowH)
        }
    }
}

private struct LiveRoomView: View {
    var active: Bool
    var config: LiveRoomConfig
    var onEnd: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var elapsed = 0
    // The broadcast itself: the session owns the room, the SFU seat and the
    // camera once it publishes; this screen renders it.
    @ObservedObject private var live = LiveSession.shared
    private var viewers: Int { Int(live.state?.viewers ?? 0) }
    private var micOn: Bool { live.state?.muted != true }
    private var camOn: Bool { live.state?.cameraOff != true }
    private var publishing: Bool { live.state?.phase == .live || live.state?.phase == .reconnecting }
    @State private var draft = ""
    /// The room's real lines — what every viewer sees too.
    private var messages: [LiveChatData] { live.state?.chat ?? [] }
    private static let chatTints: [Color] = [
        Color(rgb: 0xA78BFA), Color(rgb: 0xF472B6), Color(rgb: 0x34D399),
        Color(rgb: 0x60A5FA), Color(rgb: 0xFBBF24), Color(rgb: 0xF87171),
    ]
    private func chatTint(_ userId: Int64) -> Color { Self.chatTints[Int(userId % Int64(Self.chatTints.count))] }
    private func sendChat() {
        let t = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        LiveSession.shared.sendChat(t)
        draft = ""
    }
    @StateObject private var games = LiveGamesModel()
    @StateObject private var giftOverlay = GiftOverlayModel(roomId: 0)
    @State private var giftSheet = false

    var body: some View {
        ZStack {
            if config.isVideo && camOn {
                if let host = LiveSession.shared.localView() {
                    // The engine owns the camera while publishing; paint its preview.
                    PlatformView(view: host).ignoresSafeArea()
                } else {
                    CameraPreview(front: config.frontCamera, enabled: active && !publishing)
                        .ignoresSafeArea()
                }
            } else if !config.isVideo {
                CreateTokens.liveSetupWash.ignoresSafeArea()
                VStack(spacing: 12 * scale) {
                    Circle()
                        .fill(CreateTokens.liveCta)
                        .frame(width: 88 * scale, height: 88 * scale)
                        .overlay(CreateIco.softMic(.white, side: 36 * scale))
                    AppText(config.title, style: Type.labelBold, color: CreateTokens.liveInk, sizeOverride: 18)
                    AppText(Copy.t(.createSeatCount, ["n": "\(config.seats)"]), style: Type.caption, color: CreateTokens.liveSub, sizeOverride: 13)
                }
            } else {
                Color(rgb: 0x0D0D12).ignoresSafeArea()
                CreateIco.cam(Color.white.opacity(0.4), side: 40 * scale, off: true)
            }

            // Figma HUD theming: audio rooms sit on the light lavender wash, video on camera.
            let light = !config.isVideo
            let pill = light ? Color.white.opacity(0.72) : CreateTokens.livePill
            let pillBorder = light ? CreateTokens.liveBorder : Color.white.opacity(0.14)
            let ink = light ? CreateTokens.liveInk : Color.white
            let sub = light ? CreateTokens.liveSub : Color.white.opacity(0.6)

            VStack(spacing: 0) {
                // Row 1 — host pill (title + clock) · coins · end (X)
                HStack(spacing: 8 * scale) {
                    HStack(spacing: 8 * scale) {
                        Circle()
                            .fill(CreateTokens.liveCta)
                            .frame(width: 34 * scale, height: 34 * scale)
                            .overlay(CreateIco.users(.white, side: 16 * scale))
                        VStack(alignment: .leading, spacing: 1) {
                            AppText(config.title, style: Type.labelBold, color: ink, lineLimit: 1, sizeOverride: 12)
                            AppText(clock(elapsed), style: Type.micro, color: sub, sizeOverride: 10)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        CreateIco.chevron(sub, side: 12 * scale)
                    }
                    .padding(.leading, 5 * scale)
                    .padding(.trailing, 12 * scale)
                    .padding(.vertical, 5 * scale)
                    .background(pill)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(pillBorder, lineWidth: 1))

                    HStack(spacing: 4 * scale) {
                        Circle()
                            .fill(Color(rgb: 0xF59E0B))
                            .overlay(Circle().stroke(Color(rgb: 0xD97706), lineWidth: 2 * scale))
                            .frame(width: 12 * scale, height: 12 * scale)
                        AppText(formatCoins(live.state?.roomCoins ?? 0), style: Type.labelBold, color: Color(rgb: 0xD97706), sizeOverride: 12)
                    }
                    .padding(.horizontal, 10 * scale)
                    .padding(.vertical, 6 * scale)
                    .background(pill)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(pillBorder, lineWidth: 1))

                    Button(action: onEnd) {
                        CreateIco.close(ink, side: 14 * scale)
                            .frame(width: 36 * scale, height: 36 * scale)
                            .background(pill)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(pillBorder, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 14 * scale)
                .padding(.top, insets.top + 10 * scale)

                // Row 2 — viewers pill · co-host stack
                HStack {
                    HStack(spacing: 5 * scale) {
                        CreateIco.users(ink, side: 13 * scale)
                        AppText(viewerLabel, style: Type.labelBold, color: ink, sizeOverride: 11)
                    }
                    .padding(.horizontal, 11 * scale)
                    .padding(.vertical, 5 * scale)
                    .background(pill)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(pillBorder, lineWidth: 1))
                    Spacer()
                    AppText(Copy.t(.createCoHosts), style: Type.label, color: sub, sizeOverride: 10)
                    HStack(spacing: -8 * scale) {
                        ForEach(0..<2, id: \.self) { i in
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: i == 0
                                            ? [Color(rgb: 0xF59E0B), Color(rgb: 0xEF4444)]
                                            : [Color(rgb: 0x10B981), Color(rgb: 0x3B82F6)],
                                        startPoint: .topLeading, endPoint: .bottomTrailing
                                    )
                                )
                                .overlay(Circle().stroke(light ? Color.white.opacity(0.9) : Color.black.opacity(0.6), lineWidth: 2 * scale))
                                .frame(width: 30 * scale, height: 30 * scale)
                        }
                    }
                }
                .padding(.horizontal, 14 * scale)
                .padding(.top, 8 * scale)

                Spacer(minLength: 0)

                ScrollViewReader { proxy in
                    ScrollView(showsIndicators: false) {
                        LazyVStack(alignment: .leading, spacing: 6 * scale) {
                            ForEach(messages, id: \.stableKey) { m in
                                let name = m.user.displayName.isEmpty ? "…" : m.user.displayName
                                HStack(spacing: 6 * scale) {
                                    AppText(name, style: Type.labelBold, color: chatTint(m.user.id), lineLimit: 1, sizeOverride: 12)
                                    AppText(m.text, style: Type.caption, color: Color.white.opacity(m.id == 0 ? 0.6 : 0.92), sizeOverride: 12)
                                }
                                .padding(.horizontal, 10 * scale)
                                .padding(.vertical, 6 * scale)
                                .background(CreateTokens.livePill)
                                .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                                .id(m.stableKey)
                            }
                        }
                        .padding(.horizontal, 12 * scale)
                    }
                    .onChange(of: messages.last?.stableKey) { key in
                        if let key { withAnimation(.easeOut(duration: 0.18)) { proxy.scrollTo(key, anchor: .bottom) } }
                    }
                }
                .frame(height: 160 * scale)

                HStack(spacing: 8 * scale) {
                    ZStack(alignment: .leading) {
                        if draft.isEmpty {
                            AppText(Copy.t(.createLiveChatHint), style: Type.caption, color: Color.white.opacity(0.45), sizeOverride: 13)
                        }
                        TextField("", text: $draft)
                            .font(.system(size: 13 * scale))
                            .foregroundColor(.white)
                            .submitLabel(.send)
                            .onSubmit(sendChat)
                    }
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 10 * scale)
                    .background(CreateTokens.livePill)
                    .clipShape(Capsule())

                    toolBtn(active: true) { games.showSheet = true } icon: {
                        GamesGlyph(on: true, side: 20 * scale)
                    }
                    toolBtn(active: true) {
                        if (live.state?.roomId ?? 0) != 0 { withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) { giftSheet = true } }
                    } icon: {
                        AppText("💎", style: Type.label, sizeOverride: 16)
                    }
                    toolBtn(active: micOn) { LiveSession.shared.toggleMute() } icon: {
                        CreateIco.mic(micOn ? .white : Color.white.opacity(0.35), side: 18 * scale, off: !micOn)
                    }
                    if config.isVideo {
                        toolBtn(active: camOn) { LiveSession.shared.toggleCamera() } icon: {
                            CreateIco.cam(camOn ? .white : Color.white.opacity(0.35), side: 18 * scale, off: !camOn)
                        }
                    }
                    toolBtn(active: true) { sendChat() } icon: {
                        CreateIco.music(.white, side: 16 * scale)
                    }
                }
                .padding(.horizontal, 12 * scale)
                .padding(.bottom, insets.bottom + 10 * scale)
            }

            // Gifts landing in the room, over the picture and chrome.
            GiftOverlay(model: giftOverlay)

            // Half-mode games layer over the broadcast — catalog sheet + game sheet.
            LiveGamesOverlay(model: games)

            if giftSheet, let id = live.state?.roomId, id != 0 {
                // The host gifts guests: the receiver row excludes themself.
                LiveGiftSheet(roomId: id, hostId: live.state?.room.hostId ?? 0,
                              onClose: { withAnimation(.easeIn(duration: 0.22)) { giftSheet = false } })
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(2)
            }
        }
        .task {
            let start = Date()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                elapsed = Int(Date().timeIntervalSince(start))
            }
        }
        // Hosting holds the screen awake: a sleeping phone stops the camera
        // and freezes the broadcast for everyone watching.
        .keepScreenAwake()
        .onAppear { LiveSession.shared.goLive(video: config.isVideo, title: config.title) }
        .onDisappear { LiveSession.shared.leave() }
        // The server ended the room (or the media stack is missing): leave the surface.
        .onChange(of: live.state?.phase) { phase in if phase == .ended { onEnd() } }
    }

    private func toolBtn(active: Bool, action: @escaping () -> Void, @ViewBuilder icon: () -> some View) -> some View {
        Button(action: action) {
            icon()
                .frame(width: 42 * scale, height: 42 * scale)
                .background(active ? CreateTokens.livePill : Color.black.opacity(0.35))
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.18), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var viewerLabel: String {
        viewers >= 1000 ? String(format: "%.1fK", Double(viewers) / 1000) : "\(viewers)"
    }

    private func clock(_ sec: Int) -> String {
        let h = sec / 3600
        let m = (sec % 3600) / 60
        let s = sec % 60
        if h > 0 { return String(format: "%d:%02d:%02d", h, m, s) }
        return String(format: "%02d:%02d", m, s)
    }
}
