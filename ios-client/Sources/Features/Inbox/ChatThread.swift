import SwiftUI
import UIKit

/// "No message is selected."
///
/// It cannot be -1: a message the device has made but the server has not
/// accepted yet is numbered downwards from -1, so -1 as a sentinel means the
/// first message you send in a session is treated as the selected one — the
/// action sheet opens by itself and nothing dismisses it. Zero is never a
/// message id, local or remote.
private let NoMsg: Int64 = 0

struct ChatThreadView: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    let onBack: () -> Void
    var onOpenChat: (InboxChat) -> Void = { _ in }

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var pane: ThreadPane = .chat
    @State private var vanish = false
    @State private var menu = false
    @State private var reply: ChatMessage?
    @State private var edit: ChatMessage?
    @State private var longPress: Int64 = NoMsg
    @State private var pins: [Int64] = []
    @State private var pinShow = 0
    @State private var share = false
    @State private var swipe: CGFloat = 0
    @State private var emojiOpen = false
    @State private var infoMsg: ChatMessage?
    @State private var forward: ChatMessage?
    @State private var blinkId: Int64 = NoMsg
    @State private var flashId: Int64 = NoMsg
    @State private var copied = false
    @State private var moreOpen = false
    @State private var injectPaste: String?
    @State private var scrolledAway = false
    @State private var viewMedia: ChatMessage?
    @State private var editEvent: ChatMessage?
    @State private var pulseId: Int64 = NoMsg
    @State private var reactorsOf: ChatMessage?
    @State private var jumpId: Int64 = NoMsg
    @FocusState private var composerFocus: Bool

    var body: some View {
        switch pane {
        case .profile, .search, .theme, .nicknames, .disappear, .media:
            ChatProfileView(chat: chat, inbox: inbox, pane: $pane, onBack: { pane = .chat })
        case .chat:
            chatBody
        }
    }

    /// Calls live above the shell (see CallHost); the thread only dials. The
    /// conversation id is what the server rings, so an unbound row cannot dial.
    private func dial(video: Bool) {
        guard !live.blocked, let conversationId = inbox.conversationOf(chat.id) else { return }
        let peer = CallPeer(id: live.peerId, name: inbox.displayName(live), initials: live.initials, color: live.color, avatarUrl: live.avatarUrl)
        let chatId = chat.id
        CallPermissions.request(video: video) {
            CallController.shared.start(conversationId: conversationId, chatId: chatId, peer: peer, video: video)
        }
    }

    private var theme: ChatTheme { chatThemes[inbox.themeIndex(for: chat.id)] }
    private var bg: Color {
        if vanish { return Color.black }
        return theme.label == "Default" ? Light.background : Color(rgb: theme.bg)
    }
    private var msgs: [ChatMessage] { inbox.threads[chat.id] ?? [] }
    /// The row as it is now: `chat` is the snapshot the screen opened with,
    /// presence and block state move underneath it.
    private var live: InboxChat { inbox.chat(chat.id) ?? chat }

    private var presenceLine: String {
        if chat.group { return Copy.t(.inboxOnlineShort, ["n": "\(max(chat.members.count, 1))"]) }
        if live.blocked { return Copy.t(.inboxBlocked) }
        if inbox.typing.contains(chat.id) { return Copy.t(.inboxTyping) }
        if vanish { return "Vanish mode on" }
        return inbox.presence(live)
    }

    private var chatBody: some View {
        ZStack(alignment: .bottom) {
            Color(rgb: 0x0D0D12).ignoresSafeArea()
            if swipe > 4 {
                VStack(spacing: 8 * scale) {
                    VanishProgressRing(progress: min(1, swipe / 248), vanish: vanish)
                        .frame(width: 48 * scale, height: 48 * scale)
                    AppText(
                        Copy.t(vanish ? .inboxVanishHintOff : .inboxVanishHintOn),
                        style: Type.caption,
                        color: .white.opacity(0.82),
                        sizeOverride: 12
                    )
                }
                .padding(.bottom, 24 * scale)
            }
        VStack(spacing: 0) {
            if vanish {
                AppText(Copy.t(.inboxVanish), style: Type.labelBold, color: .white, sizeOverride: 12)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6 * scale)
                    .background(Gradients.brand)
            }
            header
            ScrollViewReader { proxy in
                if let pinId = pins.indices.contains(pinShow) ? pins[pinShow] : pins.last,
                   let pinMsg = msgs.first(where: { $0.id == pinId }), !pins.isEmpty {
                    HStack(spacing: 10 * scale) {
                        HStack(spacing: 10 * scale) {
                            RoundedRectangle(cornerRadius: 2 * scale)
                                .fill(Gradients.brand)
                                .frame(width: 3 * scale, height: 28 * scale)
                            VStack(alignment: .leading, spacing: 2 * scale) {
                                AppText(
                                    pins.count > 1 ? "\(Copy.t(.inboxPinnedMessage))  \(pinShow + 1)/\(pins.count)" : Copy.t(.inboxPinnedMessage),
                                    style: Type.labelBold,
                                    color: Dark.primary,
                                    sizeOverride: 10
                                )
                                AppText(preview(of: pinMsg), style: Type.caption, color: vanish ? Color.white.opacity(0.7) : Color(rgb: 0x555555), sizeOverride: 12, lineLimit: 1)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            let id = pins.indices.contains(pinShow) ? pins[pinShow] : pinMsg.id
                            withAnimation { proxy.scrollTo(id, anchor: .center) }
                            blinkId = id
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) { blinkId = NoMsg }
                            if pins.count > 1 { pinShow = (pinShow + 1) % pins.count }
                        }
                        Button {
                            if pins.indices.contains(pinShow) { pins.remove(at: pinShow) }
                            pinShow = min(pinShow, max(pins.count - 1, 0))
                        } label: {
                            ChatIco.close(Color(rgb: 0x9CA3AF), side: 14 * scale)
                        }
                    }
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 7 * scale)
                    .background(vanish ? Dark.primary.opacity(0.15) : Color(rgb: 0xF8F5FF))
                }
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 6 * scale) {
                        ForEach(msgs) { msg in
                            bubble(msg)
                                .id(msg.id)
                                .background(
                                    blinkId == msg.id ? Color(rgb: 0xA78BFA).opacity(0.22)
                                    : flashId == msg.id ? Color(rgb: 0x22C55E).opacity(0.18)
                                    : Color.clear
                                )
                                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                                .contentShape(Rectangle())
                                .onTapGesture(count: 2) {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    inbox.react(chatId: chat.id, msgId: msg.id, emoji: "❤️")
                                    pulseId = msg.id
                                }
                                .onTapGesture(count: 1) {
                                    dismissKeyboard()
                                    if msg.kind == .poll { return }
                                    if msg.kind == .event { editEvent = msg; return }
                                    if msg.kind == .location { openMaps(msg.text); return }
                                    flashId = msg.id
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.52) { flashId = NoMsg }
                                }
                                .onLongPressGesture {
                                    dismissKeyboard()
                                    longPress = msg.id
                                    emojiOpen = false
                                    moreOpen = false
                                }
                                .gesture(
                                    DragGesture(minimumDistance: 18)
                                        .onEnded { v in
                                            if v.translation.width > 48 && abs(v.translation.width) > abs(v.translation.height) {
                                                reply = msg
                                                composerFocus = true
                                            }
                                        }
                                )
                                .background {
                                    if msg.id == msgs.last?.id {
                                        GeometryReader { g in
                                            Color.clear.preference(
                                                key: ChatTailKey.self,
                                                value: g.frame(in: .named("chatMsgs")).minY
                                            )
                                        }
                                    }
                                }
                        }
                        // The other side is writing: a bubble-shaped promise
                        // at the foot of the thread, where their message will
                        // land.
                        if inbox.typing.contains(chat.id) {
                            TypingDots(tint: vanish ? Color.white.opacity(0.75) : Light.sub, dot: 7 * scale)
                                .padding(.horizontal, 14 * scale)
                                .padding(.vertical, 10 * scale)
                                .background(vanish ? Color.white.opacity(0.1) : Light.surface)
                                .clipShape(Capsule())
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 4 * scale)
                        }
                    }
                    .padding(.horizontal, 12 * scale)
                    .padding(.vertical, 8 * scale)
                    .padding(.bottom, 12 * scale)
                }
                .scrollBounceBehavior(.basedOnSize)
                .coordinateSpace(name: "chatMsgs")
                .modifier(ChatKeyboardDismiss())
                .simultaneousGesture(TapGesture().onEnded { dismissKeyboard() })
                .onPreferenceChange(ChatTailKey.self) { minY in
                    scrolledAway = minY > UIScreen.main.bounds.height - 80
                }
                .onChange(of: msgs.count) { _ in
                    if let last = msgs.last, last.mine || !scrolledAway {
                        withAnimation(.easeOut(duration: 0.16)) { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
                .onChange(of: jumpId) { id in
                    guard id != NoMsg else { return }
                    withAnimation(.easeOut(duration: 0.16)) { proxy.scrollTo(id, anchor: .center) }
                    blinkId = id
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) {
                        blinkId = NoMsg
                        jumpId = NoMsg
                    }
                }
            }
            .overlay(alignment: .bottom) {
                Color.clear
                    .frame(maxWidth: .infinity, height: 28 * scale)
                    .contentShape(Rectangle())
                    .gesture(vanishLift)
                if msgs.isEmpty { vanishHint }
            }
            }
            ChatComposer(inbox: inbox, chat: chat, vanish: vanish, reply: $reply, edit: $edit, focused: $composerFocus, injectPaste: $injectPaste)
                .gesture(vanishHold)
        }
        .background {
            ZStack {
                bg
                if !vanish, let wall = inbox.wallpapers[chat.id], let url = URL(string: wall) {
                    LocalThumb(url: url, fallback: theme.bg)
                }
            }
            .ignoresSafeArea()
        }
        .overlay(reactorsOverlay)
        .overlay(menuOverlay)
        .overlay(reactOverlay)
        .overlay(moreOverlay)
        .overlay(emojiOverlay)
        .overlay(mediaOverlay)
        .overlay(forwardOverlay)
        .overlay(infoOverlay)
        .overlay(eventOverlay)
        .overlay(alignment: .bottom) {
            // Copied, or what the server refused (blocked, too long, offline): same pill.
            if copied || !inbox.notice.isEmpty {
                AppText(copied ? Copy.t(.inboxCopied) : inbox.notice, style: Type.labelBold, color: .white, sizeOverride: 13)
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(Color(rgb: 0x111111).opacity(0.9))
                    .clipShape(Capsule())
                    .padding(.bottom, 96 * scale)
            }
        }
        .onChange(of: inbox.notice) { text in
            guard !text.isEmpty else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) {
                if inbox.notice == text { inbox.notice = "" }
            }
        }
        .offset(y: -swipe)
        .onChange(of: pane) { next in
            if next != .chat { pulseId = NoMsg }
        }
        // Disappearing messages are the dark surface. Setting a timer from the
        // menu has to darken the thread exactly as the swipe does, otherwise
        // the one signal that messages are vanishing is a line of small text.
        .onChange(of: inbox.vanish) { rule in vanish = rule != .off }
        .onAppear { vanish = inbox.vanish != .off }
        .onChange(of: pulseId) { id in
            guard id != NoMsg else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.68) {
                if pulseId == id { pulseId = NoMsg }
            }
        }
        .navigationBarHidden(true)
        }
    }

    private var header: some View {
        HStack(spacing: 8 * scale) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16 * scale, weight: .semibold))
                    .foregroundColor(vanish ? .white : Light.text)
                    .frame(width: 36 * scale, height: 36 * scale)
            }
            Button { pane = .profile } label: {
                HStack(spacing: 10 * scale) {
                    if chat.group {
                        initials(chat.initials, color: chat.color, group: true, size: 40)
                    } else {
                        // Face → their public profile; name/row → chat settings.
                        ChatAvatar(chat: live, size: 40, showOnline: true)
                            .onTapGesture {
                                if live.peerId != 0 { PersonRouter.shared.open(live.peerId) }
                                else { pane = .profile }
                            }
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        AppText(inbox.displayName(chat), style: Type.labelBold, color: vanish ? .white : Light.text, sizeOverride: 17)
                        AppText(
                            presenceLine,
                            style: Type.micro,
                            color: live.online || inbox.typing.contains(chat.id) ? Color(rgb: 0x10B981) : (vanish ? Color.white.opacity(0.6) : Light.sub),
                            sizeOverride: 11
                        )
                        if !chat.group, !live.status.isEmpty, !inbox.typing.contains(chat.id) {
                            AppText(
                                live.status,
                                style: Type.micro,
                                color: vanish ? Color.white.opacity(0.55) : Light.sub,
                                sizeOverride: 10,
                                lineLimit: 1
                            )
                        }
                    }
                    Spacer(minLength: 0)
                }
            }
            roundBtn { dial(video: false) } icon: { ChatIco.phone(callIcon, side: 18 * scale) }
            roundBtn { dial(video: true) } icon: { ChatIco.video(callIcon, side: 18 * scale) }
            roundBtn { menu.toggle() } icon: { ChatIco.moreV(callIcon, side: 16 * scale) }
        }
        .padding(.horizontal, 8 * scale)
        .padding(.top, insets.top)
        .padding(.bottom, 8 * scale)
        .background(bg)
        .contentShape(Rectangle())
        .gesture(vanishLift)
        .onTapGesture { dismissKeyboard() }
    }

    private var vanishLift: some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { v in
                guard abs(v.translation.height) >= abs(v.translation.width) else { return }
                let next = max(0, min(248, -v.translation.height))
                if swipe < 128 && next >= 128 {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                }
                swipe = next
            }
            .onEnded { _ in settleVanish() }
    }

    private var vanishHold: some Gesture {
        LongPressGesture(minimumDuration: 0.16)
            .sequenced(before: DragGesture(minimumDistance: 4))
            .onChanged { v in
                guard case .second(true, let drag?) = v else { return }
                guard abs(drag.translation.height) >= abs(drag.translation.width) else { return }
                let next = max(0, min(248, -drag.translation.height))
                if swipe < 128 && next >= 128 {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                }
                swipe = next
            }
            .onEnded { _ in settleVanish() }
    }

    private func settleVanish() {
        if swipe >= 128 {
            vanish.toggle()
            inbox.vanish = vanish ? .seen : .off
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
        withAnimation(.spring(response: 0.32, dampingFraction: 0.86)) { swipe = 0 }
    }

    private func bubble(_ msg: ChatMessage) -> some View {
        HStack {
            if msg.mine { Spacer(minLength: 40 * scale) }
            if !msg.mine {
                initials(msg.initials, color: msg.color, group: false, size: 28)
            }
            ZStack(alignment: .bottomLeading) {
            VStack(alignment: msg.mine ? .trailing : .leading, spacing: 0) {
                if !msg.mine && chat.group {
                    AppText(msg.from, style: Type.micro, color: Color(rgb: msg.color), sizeOverride: 10)
                        .padding(.bottom, 4 * scale)
                }
                if let quoted = msgs.first(where: { $0.id == msg.replyTo }) {
                    AppText(
                        Copy.t(msg.mine ? .inboxYouReplied : (quoted.mine ? .inboxRepliedToYou : .inboxReply)),
                        style: Type.micro,
                        color: vanish ? Color.white.opacity(0.55) : Color(rgb: 0x7C6AA8),
                        sizeOverride: 11
                    )
                    replyCite(quoted, mine: msg.mine)
                        .padding(.top, 4 * scale)
                        .padding(.bottom, 6 * scale)
                        .onTapGesture { jumpId = quoted.id }
                }
                let isMedia = msg.kind == .image || msg.kind == .video || msg.kind == .ai
                if isMedia {
                    let hasCaption = !msg.text.isEmpty
                    HStack(spacing: 8 * scale) {
                        if msg.mine { mediaForward(msg) }
                        VStack(alignment: .leading, spacing: 0) {
                            ZStack(alignment: .bottomTrailing) {
                                LocalThumb(url: msg.mediaURL, fallback: chat.color)
                                    .frame(width: 220 * scale, height: 160 * scale)
                                    .onTapGesture { viewMedia = msg }
                                if msg.kind == .video {
                                    Circle().fill(Color.white.opacity(0.9)).frame(width: 48 * scale, height: 48 * scale)
                                        .overlay(ChatIco.play(Color(rgb: 0x111111), side: 18 * scale))
                                }
                                if !hasCaption {
                                    AppText(msg.time, style: Type.micro, color: .white.opacity(0.9), sizeOverride: 10)
                                        .padding(8 * scale)
                                }
                            }
                            if hasCaption {
                                VStack(alignment: .leading, spacing: 4 * scale) {
                                    Text(linkedChatText(msg.text, base: msg.mine ? .white : (vanish ? Color.white.opacity(0.9) : Light.text), link: msg.mine ? Color(rgb: 0xBFDBFE) : Color(rgb: 0x2563EB)))
                                        .font(.system(size: 15 * scale))
                                        .tint(msg.mine ? Color(rgb: 0xBFDBFE) : Color(rgb: 0x2563EB))
                                    HStack(spacing: 4 * scale) {
                                        Spacer(minLength: 0)
                                        AppText(msg.time, style: Type.micro, color: msg.mine ? Color.white.opacity(0.7) : Light.muted, sizeOverride: 10)
                                        if msg.mine { ChatIco.deliveryMark(msg.delivery, side: 13 * scale) }
                                    }
                                }
                                .padding(.horizontal, 10 * scale)
                                .padding(.vertical, 8 * scale)
                            }
                        }
                        .frame(width: 220 * scale)
                                .background {
                            if hasCaption {
                                if msg.mine {
                                    if let solid = inbox.bubbleColors[chat.id] {
                                        Color(rgb: solid)
                                    } else {
                                        LinearGradient(colors: [Color(rgb: theme.start), Color(rgb: theme.end)], startPoint: .leading, endPoint: .trailing)
                                    }
                                } else {
                                    (vanish ? Color.white.opacity(0.08) : Light.surface)
                                }
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 16 * scale))
                        if !msg.mine { mediaForward(msg) }
                    }
                } else {
                    VStack(alignment: msg.mine ? .trailing : .leading, spacing: 4 * scale) {
                        content(msg)
                        HStack(spacing: 4 * scale) {
                            if msg.edited {
                                AppText(Copy.t(.inboxEdited), style: Type.micro, color: msg.mine ? Color.white.opacity(0.55) : Light.muted, sizeOverride: 9)
                            }
                            AppText(msg.time, style: Type.micro, color: msg.mine ? Color.white.opacity(0.7) : Light.muted, sizeOverride: 10)
                            if msg.mine { ChatIco.deliveryMark(msg.delivery, side: 13 * scale) }
                        }
                    }
                    .padding(.horizontal, 12 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(
                        Group {
                            if msg.mine {
                                if let solid = inbox.bubbleColors[chat.id] {
                                    Color(rgb: solid)
                                } else {
                                    LinearGradient(colors: [Color(rgb: theme.start), Color(rgb: theme.end)], startPoint: .leading, endPoint: .trailing)
                                }
                            } else {
                                LinearGradient(colors: [vanish ? Color.white.opacity(0.08) : Light.surface], startPoint: .leading, endPoint: .trailing)
                            }
                        }
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
                }
            }
            if let reaction = msg.reaction {
                ReactionIcon(emoji: reaction, play: pulseId == msg.id, count: max(msg.reactors.count, 1))
                    .offset(x: msg.mine ? 10 * scale : 6 * scale, y: 8 * scale)
                    .onTapGesture { reactorsOf = msg }
            }
            }
            .padding(.bottom, msg.reaction != nil ? 10 * scale : 0)
            if !msg.mine { Spacer(minLength: 40 * scale) }
        }
    }

    private func replyCite(_ quoted: ChatMessage, mine: Bool) -> some View {
        let media = quoted.kind == .image || quoted.kind == .video || quoted.kind == .ai
        return HStack(spacing: 8 * scale) {
            RoundedRectangle(cornerRadius: 2 * scale)
                .fill(Gradients.brand)
                .frame(width: 3 * scale, height: 36 * scale)
            VStack(alignment: .leading, spacing: 2 * scale) {
                AppText(
                    quoted.text.isEmpty ? preview(of: quoted) : quoted.text,
                    style: Type.caption,
                    color: vanish ? Color.white.opacity(0.55) : Color(rgb: 0x6B6578),
                    sizeOverride: 12,
                    lineLimit: 2
                )
            }
            Spacer(minLength: 0)
            if media {
                LocalThumb(url: quoted.mediaURL, fallback: quoted.color)
                    .frame(width: 40 * scale, height: 40 * scale)
                    .clipShape(RoundedRectangle(cornerRadius: 8 * scale))
                    .opacity(0.82)
            }
        }
        .padding(.horizontal, 8 * scale)
        .padding(.vertical, 6 * scale)
        .background(vanish ? Color.white.opacity(0.08) : Color(rgb: 0xF3EEF8))
        .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
        .opacity(0.92)
        .frame(maxWidth: 260 * scale, alignment: mine ? .trailing : .leading)
    }

    @ViewBuilder
    private func content(_ msg: ChatMessage) -> some View {
        let color: Color = msg.mine ? .white : (vanish ? Color.white.opacity(0.9) : Light.text)
        switch msg.kind {
        case .image, .video, .ai:
            LocalThumb(url: msg.mediaURL, fallback: chat.color)
                .frame(width: 200 * scale, height: 140 * scale)
                .clipShape(RoundedRectangle(cornerRadius: 10 * scale))
        case .voice:
            HStack(spacing: 8 * scale) {
                Circle().fill(msg.mine ? Color.white.opacity(0.22) : Color.white).frame(width: 36 * scale, height: 36 * scale)
                    .overlay(ChatIco.play(msg.mine ? .white : Color(rgb: 0x111111), side: 14 * scale))
                AppText(msg.text.isEmpty ? "0:07" : msg.text, style: Type.micro, color: color, sizeOverride: 11)
            }
            .frame(width: 220 * scale, alignment: .leading)
        case .contact:
            VStack(alignment: .leading, spacing: 8 * scale) {
                HStack(spacing: 10 * scale) {
                    Group {
                        if let url = msg.mediaURL {
                            LocalThumb(url: url, fallback: msg.color)
                        } else {
                            Color(rgb: msg.color)
                        }
                    }
                    .frame(width: 52 * scale, height: 52 * scale)
                    .clipShape(Circle())
                    .overlay {
                        if msg.mediaURL == nil {
                            AppText(String((msg.text.isEmpty ? "C" : msg.text).prefix(1)).uppercased(), style: Type.labelBold, color: .white, sizeOverride: 18)
                        }
                    }
                    VStack(alignment: .leading, spacing: 2 * scale) {
                        AppText(msg.text.split(separator: "\n").first.map(String.init) ?? msg.text, style: Type.labelBold, color: color, sizeOverride: 14)
                        let note = msg.text.split(separator: "\n", omittingEmptySubsequences: false).dropFirst().joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
                        if !note.isEmpty {
                            AppText(note, style: Type.caption, color: color.opacity(0.75), sizeOverride: 12)
                        }
                    }
                }
                Button {
                    let name = msg.text.split(separator: "\n", omittingEmptySubsequences: false).first.map(String.init) ?? msg.text
                    let trimmed = name.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty { onOpenChat(inbox.openDirect(name: trimmed)) }
                } label: {
                    AppText(Copy.t(.inboxTypeMessage), style: Type.labelBold, color: color, sizeOverride: 13)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8 * scale)
                        .background(msg.mine ? Color.white.opacity(0.22) : Dark.primary.opacity(0.14))
                        .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            .frame(width: 210 * scale, alignment: .leading)
        case .location:
            Button { openMaps(msg.text) } label: {
                AppText("📍 \(msg.text)", style: Type.label, color: color)
            }
        case .document: AppText("📄 \(msg.text)", style: Type.label, color: color)
        case .poll:
            let opts = pollOptions(msg.text)
            let multi = pollMulti(msg.text)
            let mine = inbox.pollMine[msg.id] ?? []
            let counts = inbox.pollTally[msg.id] ?? Array(repeating: 0, count: opts.count)
            VStack(alignment: .leading, spacing: 6 * scale) {
                let q = pollQuestion(msg.text)
                if !q.isEmpty { AppText(q, style: Type.labelBold, color: color, sizeOverride: 14) }
                ForEach(opts.indices, id: \.self) { i in
                    let on = mine.contains(i)
                    Button { inbox.votePoll(msgId: msg.id, index: i, optionCount: opts.count, multi: multi) } label: {
                        HStack(spacing: 8 * scale) {
                            Group {
                                if multi {
                                    RoundedRectangle(cornerRadius: 4 * scale)
                                        .stroke(on ? Dark.primary : color.opacity(0.55), lineWidth: 1.5)
                                        .frame(width: 18 * scale, height: 18 * scale)
                                        .overlay { if on { ChatIco.tick(.white, side: 10 * scale) } }
                                        .background(on ? Dark.primary : Color.clear)
                                        .clipShape(RoundedRectangle(cornerRadius: 4 * scale))
                                } else {
                                    Circle()
                                        .stroke(on ? Dark.primary : color.opacity(0.55), lineWidth: 1.5)
                                        .frame(width: 18 * scale, height: 18 * scale)
                                        .overlay { if on { ChatIco.tick(.white, side: 10 * scale) } }
                                        .background(on ? Dark.primary : Color.clear)
                                        .clipShape(Circle())
                                }
                            }
                            AppText(opts[i], style: Type.label, color: color, sizeOverride: 13)
                            Spacer(minLength: 0)
                            AppText("\(counts.indices.contains(i) ? counts[i] : 0)", style: Type.labelBold, color: color.opacity(0.8), sizeOverride: 12)
                        }
                        .padding(.horizontal, 10 * scale)
                        .padding(.vertical, 8 * scale)
                        .background(on ? (msg.mine ? Color.white.opacity(0.28) : Dark.primary.opacity(0.12)) : (msg.mine ? Color.white.opacity(0.16) : Color.black.opacity(0.05)))
                        .clipShape(RoundedRectangle(cornerRadius: 10 * scale))
                    }
                    .buttonStyle(.plain)
                }
            }
        case .event:
            let ev = parseEvent(msg.text)
            let when = ev.at > 0 ? {
                let f = DateFormatter(); f.dateFormat = "EEE, d MMM · HH:mm"
                return f.string(from: Date(timeIntervalSince1970: ev.at))
            }() : Copy.t(.inboxEventWhen)
            VStack(alignment: .leading, spacing: 4 * scale) {
                AppText(ev.name.isEmpty ? Copy.t(.inboxEvent) : ev.name, style: Type.labelBold, color: color, sizeOverride: 14)
                AppText(when, style: Type.caption, color: color.opacity(0.8))
                if !ev.loc.isEmpty {
                    Button { openMaps(ev.loc) } label: { AppText(ev.loc, style: Type.caption, color: color.opacity(0.8)) }
                }
                if !ev.desc.isEmpty { AppText(ev.desc, style: Type.caption, color: color.opacity(0.75), sizeOverride: 12) }
                HStack(spacing: 8 * scale) {
                    Button { openCalendar(ev) } label: {
                        AppText(Copy.t(.inboxAddCalendar), style: Type.micro, color: msg.mine ? .white : Dark.primary, sizeOverride: 11)
                            .padding(.horizontal, 10 * scale).padding(.vertical, 6 * scale)
                            .background(msg.mine ? Color.white.opacity(0.2) : Dark.primary.opacity(0.12))
                            .clipShape(Capsule())
                    }
                    if msg.mine {
                        Button { editEvent = msg } label: {
                            AppText(Copy.t(.inboxEditEvent), style: Type.micro, color: color, sizeOverride: 11)
                                .padding(.horizontal, 10 * scale).padding(.vertical, 6 * scale)
                        }
                    }
                }
            }
            .frame(width: 220 * scale, alignment: .leading)
        case .text:
            Text(linkedChatText(msg.text, base: color, link: msg.mine ? Color(rgb: 0xBFDBFE) : Color(rgb: 0x2563EB)))
                .font(.system(size: 15 * scale))
                .tint(msg.mine ? Color(rgb: 0xBFDBFE) : Color(rgb: 0x2563EB))
        }
    }

    private func mediaForward(_ msg: ChatMessage) -> some View {
        Button {
            dismissKeyboard()
            forward = msg
        } label: {
            Circle()
                .fill(Color.black.opacity(0.38))
                .frame(width: 34 * scale, height: 34 * scale)
                .overlay(ChatIco.forwardFill(.white, side: 17 * scale))
        }
        .buttonStyle(.plain)
    }

    private var callIcon: Color { vanish ? Color.white.opacity(0.7) : Light.textSoft }

    private func roundBtn<V: View>(_ action: @escaping () -> Void, @ViewBuilder icon: () -> V) -> some View {
        Button(action: action) {
            Circle()
                .fill(vanish ? Color.white.opacity(0.08) : Light.surface)
                .frame(width: 36 * scale, height: 36 * scale)
                .overlay(icon())
        }
    }

    private var vanishHint: some View {
        ZStack(alignment: .bottom) {
            Color.clear
            VanishProgressRing(progress: min(1, swipe / 248), vanish: vanish)
                .frame(width: 44 * scale, height: 44 * scale)
                .padding(.bottom, 6 * scale)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 8)
                .onChanged { v in
                    let next = max(0, min(248, -v.translation.height))
                    if swipe < 128 && next >= 128 {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    }
                    swipe = next
                }
                .onEnded { _ in settleVanish() }
        )
    }

    private func dismissKeyboard() {
        composerFocus = false
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    @ViewBuilder
    private var reactorsOverlay: some View {
        if let open = reactorsOf, let live = msgs.first(where: { $0.id == open.id }), live.reaction != nil || !live.reactors.isEmpty {
            ReactorsSheet(
                msg: live,
                onDismiss: { reactorsOf = nil },
                onRemoveMine: {
                    inbox.clearMineReaction(chatId: chat.id, msgId: live.id)
                    pulseId = NoMsg
                }
            )
        }
    }

    @ViewBuilder
    private var menuOverlay: some View {
        if menu {
            ZStack(alignment: .topTrailing) {
                Color.black.opacity(0.01).ignoresSafeArea().onTapGesture { menu = false }
                VStack(alignment: .leading, spacing: 0) {
                    menuRow(Copy.t(.inboxReadReceipts)) { inbox.readReceipts.toggle() }
                    menuRow(Copy.t(.inboxMedia)) { menu = false; pane = .media }
                    menuRow(Copy.t(.inboxMarkRead)) { menu = false; inbox.markRead(chat.id) }
                    menuRow(Copy.t(.inboxMuteNotifs)) { menu = false; inbox.toggleMute(chat.id) }
                    menuRow(Copy.t(.inboxShareProfile)) { menu = false; share = true }
                    menuRow(Copy.t(.inboxClearChat), danger: true) { menu = false; inbox.clear(chatId: chat.id) }
                }
                .frame(width: 220 * scale)
                .background(vanish ? Color(rgb: 0x1A1A2E) : Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16 * scale))
                .padding(.trailing, 10 * scale)
                .padding(.top, insets.top + 50 * scale)
            }
        }
    }

    private func menuRow(_ label: String, danger: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(label, style: Type.labelStrong, color: danger ? Color(rgb: 0xEF4444) : (vanish ? .white : Light.text), sizeOverride: 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16 * scale)
                .padding(.vertical, 13 * scale)
        }
    }

    @ViewBuilder
    private var reactOverlay: some View {
        if longPress != NoMsg, let target = msgs.first(where: { $0.id == longPress }), !emojiOpen, !moreOpen {
            MessageActionOverlay(
                msg: target,
                vanish: vanish,
                selectedEmoji: target.reaction,
                onReact: {
                    inbox.react(chatId: chat.id, msgId: target.id, emoji: $0)
                    pulseId = target.id
                    longPress = NoMsg
                },
                onPlus: { emojiOpen = true },
                onReply: { reply = target; edit = nil; composerFocus = true; longPress = NoMsg },
                onEdit: { edit = target; reply = nil; composerFocus = true; longPress = NoMsg },
                onForward: { dismissKeyboard(); forward = target; longPress = NoMsg },
                onCopy: {
                    UIPasteboard.general.string = target.text.isEmpty ? preview(of: target) : target.text
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { copied = false }
                    longPress = NoMsg
                },
                onDownload: { longPress = NoMsg },
                onMore: { moreOpen = true },
                onDelete: { inbox.unsend(chatId: chat.id, msgId: target.id); longPress = NoMsg },
                onUnsend: { inbox.unsend(chatId: chat.id, msgId: target.id); longPress = NoMsg },
                onDismiss: { longPress = NoMsg }
            )
        }
    }

    @ViewBuilder
    private var moreOverlay: some View {
        if moreOpen, longPress != NoMsg, let target = msgs.first(where: { $0.id == longPress }) {
            MsgMoreSheet(
                msg: target,
                pinned: pins.contains(target.id),
                onPin: {
                    if let i = pins.firstIndex(of: target.id) { pins.remove(at: i) }
                    else {
                        if pins.count >= 3 { pins.removeFirst() }
                        pins.append(target.id)
                        pinShow = pins.count - 1
                    }
                    moreOpen = false
                    longPress = NoMsg
                },
                onInfo: { infoMsg = target; moreOpen = false; longPress = NoMsg },
                onReport: { moreOpen = false; longPress = NoMsg },
                onTranslate: { moreOpen = false; longPress = NoMsg },
                onDismiss: { moreOpen = false; longPress = NoMsg }
            )
        }
    }

    @ViewBuilder
    private var emojiOverlay: some View {
        if emojiOpen, longPress != NoMsg, let target = msgs.first(where: { $0.id == longPress }) {
            EmojiPickSheet(
                selected: target.reaction,
                onPick: {
                    inbox.react(chatId: chat.id, msgId: target.id, emoji: $0)
                    pulseId = target.id
                    emojiOpen = false
                    longPress = NoMsg
                },
                onDismiss: { emojiOpen = false }
            )
        }
    }

    @ViewBuilder
    private var forwardOverlay: some View {
        if let fwd = forward {
            ForwardPickSheet(
                inbox: inbox,
                preview: fwd.text.isEmpty ? preview(of: fwd) : fwd.text,
                mediaURL: fwd.mediaURL,
                kind: fwd.kind,
                onDismiss: { forward = nil }
            ) { targets, note in
                for dest in targets {
                    inbox.send(chatId: dest.id, text: fwd.text.isEmpty ? preview(of: fwd) : fwd.text, kind: fwd.kind, media: fwd.mediaURL)
                    if !note.isEmpty { inbox.send(chatId: dest.id, text: note) }
                }
                forward = nil
            }
            .onAppear { dismissKeyboard() }
        }
    }

    @ViewBuilder
    private var infoOverlay: some View {
        if let info = infoMsg {
            MessageInfoSheet(
                msg: info, theme: theme, onDismiss: { infoMsg = nil },
                seenAt: inbox.seenAt(chat.id, info), deliveredAt: inbox.deliveredAt(chat.id, info)
            )
        }
    }

    @ViewBuilder
    private var mediaOverlay: some View {
        if let m = viewMedia {
            MediaViewer(
                msg: m,
                onClose: { viewMedia = nil },
                onForward: { forward = m },
                onReply: { text in
                    inbox.send(chatId: chat.id, text: text, replyTo: m.id)
                }
            )
        }
    }

    @ViewBuilder
    private var eventOverlay: some View {
        if let ev = editEvent {
            CreateEventView(
                seed: parseEvent(ev.text),
                canEdit: ev.mine,
                onLeave: { editEvent = nil },
                onSend: { body in
                    if ev.mine { inbox.patch(chatId: chat.id, msgId: ev.id, text: body) }
                    editEvent = nil
                }
            )
        }
    }
}

private struct ChatTailKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

private struct ChatKeyboardDismiss: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.scrollDismissesKeyboard(.immediately)
        } else {
            content
        }
    }
}

private struct MediaViewer: View {
    let msg: ChatMessage
    let onClose: () -> Void
    let onForward: () -> Void
    let onReply: (String) -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var chrome = true
    @State private var draft = ""
    @State private var hint: String?
    @FocusState private var focused: Bool

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            LocalThumb(url: msg.mediaURL, fallback: 0x111111)
                .ignoresSafeArea()
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.22)) { chrome.toggle() }
                    if !chrome {
                        focused = false
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            if chrome {
                VStack {
                    HStack(spacing: 10 * scale) {
                        Button(action: onClose) {
                            Circle().fill(Color.white.opacity(0.16)).frame(width: 36 * scale, height: 36 * scale)
                                .overlay(ChatIco.close(.white, side: 16 * scale))
                        }
                        Circle()
                            .fill(Color(rgb: msg.color))
                            .frame(width: 34 * scale, height: 34 * scale)
                            .overlay(AppText(msg.initials, style: Type.labelBold, color: .white, sizeOverride: 12))
                        VStack(alignment: .leading, spacing: 1 * scale) {
                            AppText(msg.mine ? Copy.t(.inboxYou) : msg.from, style: Type.labelBold, color: .white, sizeOverride: 14)
                            AppText(msg.time, style: Type.micro, color: .white.opacity(0.72), sizeOverride: 11)
                        }
                        Spacer(minLength: 0)
                        Button(action: save) {
                            Circle().fill(Color.white.opacity(0.16)).frame(width: 36 * scale, height: 36 * scale)
                                .overlay(ChatIco.download(.white, side: 16 * scale))
                        }
                        Button(action: onForward) {
                            Circle().fill(Color.white.opacity(0.16)).frame(width: 36 * scale, height: 36 * scale)
                                .overlay(ChatIco.forward(.white, side: 16 * scale))
                        }
                    }
                    .padding(.horizontal, 10 * scale)
                    .padding(.top, insets.top + 8 * scale)
                    .padding(.bottom, 16 * scale)
                    .background(LinearGradient(colors: [Color.black.opacity(0.58), Color.clear], startPoint: .top, endPoint: .bottom))
                    Spacer()
                    if msg.kind == .video {
                        Circle().fill(Color.white.opacity(0.92)).frame(width: 64 * scale, height: 64 * scale)
                            .overlay(ChatIco.play(Color(rgb: 0x111111), side: 24 * scale))
                        Spacer()
                    }
                    HStack(spacing: 8 * scale) {
                        TextField(Copy.t(.inboxWriteMsg), text: $draft)
                            .focused($focused)
                            .foregroundColor(.white)
                            .padding(.leading, 14 * scale)
                            .padding(.vertical, 10 * scale)
                        Button {
                            let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !text.isEmpty else { return }
                            onReply(text)
                            draft = ""
                            focused = false
                        } label: {
                            ZStack {
                                Circle().fill(Color.white.opacity(0.2))
                                Circle().fill(Gradients.brand).opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0 : 1)
                                ChatIco.sendTri(.white, side: 18 * scale)
                            }
                            .frame(width: 40 * scale, height: 40 * scale)
                        }
                        .padding(6 * scale)
                    }
                    .background(Color.white.opacity(0.14))
                    .clipShape(Capsule())
                    .padding(.horizontal, 12 * scale)
                    .padding(.bottom, insets.bottom + 10 * scale)
                    .background(LinearGradient(colors: [Color.clear, Color.black.opacity(0.62)], startPoint: .top, endPoint: .bottom))
                }
                .transition(.opacity)
            }
            if let hint {
                AppText(hint, style: Type.micro, color: .white, sizeOverride: 12)
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 6 * scale)
                    .background(Color.black.opacity(0.9))
                    .clipShape(Capsule())
                    .padding(.bottom, 88 * scale)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .animation(.easeInOut(duration: 0.22), value: chrome)
    }

    private func save() {
        DispatchQueue.global(qos: .userInitiated).async {
            var ok = false
            if let url = msg.mediaURL, let data = try? Data(contentsOf: url), let img = UIImage(data: data) {
                UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                ok = true
            }
            DispatchQueue.main.async {
                hint = Copy.t(ok ? .inboxCopied : .inboxDownload)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { hint = nil }
            }
        }
    }
}

private struct ReactionIcon: View {
    let emoji: String
    var play: Bool = false
    var count: Int = 1
    @Environment(\.uiScale) private var scale
    @State private var pop: CGFloat = 1
    @State private var lift: CGFloat = 0
    @State private var played = false

    var body: some View {
        ZStack(alignment: .leading) {
            HStack(spacing: 3 * scale) {
                Text(emoji).font(.system(size: 14 * scale)).opacity(0)
                if count > 1 {
                    Text("\(count)")
                        .font(.system(size: 10 * scale, weight: .semibold))
                        .foregroundColor(Color(rgb: 0x5B4B73))
                }
            }
            .padding(.horizontal, 6 * scale)
            .padding(.vertical, 3 * scale)
            .background(Color(rgb: 0xEDE8F5))
            .overlay(Capsule().stroke(Color(rgb: 0xD4CCE4), lineWidth: 0.5))
            .clipShape(Capsule())
            Text(emoji)
                .font(.system(size: 14 * scale))
                .scaleEffect(pop)
                .offset(x: 6 * scale, y: lift)
        }
        .onChange(of: play) { v in
            if v {
                if !played {
                    played = true
                    bounce()
                }
            } else {
                played = false
                pop = 1
                lift = 0
            }
        }
        .onChange(of: emoji) { _ in
            played = false
        }
    }

    private func bounce() {
        pop = 0.88
        lift = 0
        withAnimation(.timingCurve(0.22, 1.0, 0.36, 1.0, duration: 0.42)) {
            pop = 1.32
            lift = -52
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.42) {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            withAnimation(.easeInOut(duration: 0.09)) {
                pop = 1.44
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.09) {
                withAnimation(.timingCurve(0.33, 0.0, 0.2, 1.0, duration: 0.5)) {
                    pop = 1
                    lift = 0
                }
            }
        }
    }
}

func initials(_ text: String, color: UInt32, group: Bool, size: CGFloat) -> some View {
    Text(text)
        .font(.system(size: size * 0.32, weight: .bold))
        .foregroundColor(.white)
        .frame(width: size, height: size)
        .background(Color(rgb: color))
        .clipShape(RoundedRectangle(cornerRadius: group ? size * 0.3 : size / 2, style: .continuous))
}
