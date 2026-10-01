import SwiftUI
import UIKit

private let QuickReactions = ["❤️", "😂", "😮", "😢", "😡", "👍"]
private let EmojiSmileys = [
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😊", "😍", "🥰",
    "😘", "🤩", "🥳", "😏", "😒", "😞", "😔", "😢", "😭", "😤", "😠", "😡",
]
private let EmojiGestures = [
    "👋", "🤚", "🖐", "✋", "🤙", "👌", "✌️", "🤞", "🤟", "🤘", "👍", "👎",
    "👏", "🙌", "🫶", "🤝", "🙏", "💪",
]
private let EmojiRecent = ["🤔", "😏", "🙌", "🙏", "😎", "🤩", "💀", "😢", "🥹", "❤️", "😂", "👍"]

struct MessageActionOverlay: View {
    let msg: ChatMessage
    let vanish: Bool
    let selectedEmoji: String?
    let onReact: (String) -> Void
    let onPlus: () -> Void
    let onReply: () -> Void
    let onForward: () -> Void
    let onCopy: () -> Void
    let onDownload: () -> Void
    let onMore: () -> Void
    let onDelete: () -> Void
    let onUnsend: () -> Void
    var onEdit: () -> Void = {}
    let onDismiss: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var expandDel = false

    var body: some View {
        ZStack(alignment: msg.mine ? .trailing : .leading) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: onDismiss)
            VStack(alignment: msg.mine ? .trailing : .leading, spacing: 6 * scale) {
                HStack(spacing: 2 * scale) {
                    ForEach(QuickReactions, id: \.self) { em in
                        Button { onReact(em) } label: {
                            AppText(em, style: Type.label, sizeOverride: 26)
                                .frame(width: 40 * scale, height: 40 * scale)
                                .background(selectedEmoji == em ? Color(rgb: 0xF3E8FF) : Color.clear)
                                .clipShape(Circle())
                        }
                    }
                    Button(action: onPlus) {
                        Circle()
                            .fill(Color(rgb: 0xF0F0F0))
                            .frame(width: 40 * scale, height: 40 * scale)
                            .overlay(ChatIco.plus(Color(rgb: 0x555555), side: 18 * scale))
                    }
                }
                .padding(.horizontal, 6 * scale)
                .padding(.vertical, 4 * scale)
                .background(Color.white.opacity(0.97))
                .clipShape(Capsule())

                VStack(spacing: 0) {
                    action(Copy.t(.inboxReply), { ChatIco.reply(Color(rgb: 0x333333), side: 18 * scale) }, onReply)
                    if msg.mine && msg.kind == .text {
                        action(Copy.t(.inboxEdit), { ChatIco.pencil(Color(rgb: 0x333333), side: 18 * scale) }, onEdit)
                    }
                    action(Copy.t(.inboxForward), { ChatIco.forward(Color(rgb: 0x333333), side: 18 * scale) }, onForward)
                    action(Copy.t(.inboxDeleteMsg), { ChatIco.trash(Color(rgb: 0xEF4444), side: 18 * scale) }, { expandDel.toggle() }, red: true)
                    if expandDel {
                        action(Copy.t(.inboxFromYou), { ChatIco.trash(Color(rgb: 0x333333), side: 16 * scale) }, onDelete)
                        if msg.mine {
                            action(Copy.t(.inboxEveryone), { ChatIco.unsend(Color(rgb: 0xEF4444), side: 16 * scale) }, onUnsend, red: true)
                        }
                    }
                    action(Copy.t(.inboxCopyAction), { ChatIco.copyLines(Color(rgb: 0x333333), side: 18 * scale) }, onCopy)
                    if msg.kind == .image || msg.kind == .video {
                        action(Copy.t(.inboxDownload), { ChatIco.download(Color(rgb: 0x333333), side: 18 * scale) }, onDownload)
                    }
                    action(Copy.t(.inboxMore), { ChatIco.moreH(Color(rgb: 0x333333), side: 18 * scale) }, onMore, last: true)
                }
                .frame(width: 210 * scale, alignment: .leading)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
            }
            .padding(.horizontal, 10 * scale)
            .frame(maxWidth: 280 * scale, alignment: msg.mine ? .trailing : .leading)
            // Menu taps stay on the menu; outside taps hit the scrim above.
            .highPriorityGesture(TapGesture().onEnded { })
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.clear)
        .allowsHitTesting(true)
        .zIndex(50)
    }

    private func action<V: View>(_ label: String, _ icon: () -> V, _ tap: @escaping () -> Void, red: Bool = false, last: Bool = false) -> some View {
        Button(action: tap) {
            HStack(spacing: 12 * scale) {
                icon().frame(width: 18 * scale, height: 18 * scale)
                AppText(label, style: Type.labelStrong, color: red ? Color(rgb: 0xEF4444) : Color(rgb: 0x111111), sizeOverride: 14)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 12 * scale)
            .overlay(alignment: .bottom) {
                if !last { Color(rgb: 0xF5F5F5).frame(height: 1) }
            }
        }
    }
}

struct MsgMoreSheet: View {
    let msg: ChatMessage
    let pinned: Bool
    let onPin: () -> Void
    let onInfo: () -> Void
    let onReport: () -> Void
    let onTranslate: () -> Void
    let onDismiss: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var y: CGFloat = UIScreen.main.bounds.height
    @State private var origin: CGFloat?

    var body: some View {
        let screen = UIScreen.main.bounds.height
        return ZStack(alignment: .bottom) {
            Color.black.opacity(0.4 * Double(max(0, 1 - y / screen))).ignoresSafeArea()
                .onTapGesture {
                    guard origin == nil else { return }
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) { y = screen }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: onDismiss)
                }
            VStack(spacing: 0) {
                Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 40 * scale, height: 4 * scale)
                    .padding(.top, 12 * scale)
                    .padding(.bottom, 8 * scale)
                moreRow(Copy.t(pinned ? .inboxMsgUnpin : .inboxMsgPin), { ChatIco.pin(pinned ? Dark.primary : Color(rgb: 0x333333), side: 18 * scale, filled: pinned) }, onPin)
                if msg.mine {
                    moreRow(Copy.t(.inboxInfo), { ChatIco.info(Color(rgb: 0x333333), side: 18 * scale) }, onInfo)
                }
                moreRow(Copy.t(.inboxReport), { ChatIco.report(Color(rgb: 0xEF4444), side: 18 * scale) }, onReport, red: true)
                moreRow(Copy.t(.inboxTranslate), { ChatIco.translate(Color(rgb: 0x333333), side: 18 * scale) }, onTranslate, last: true)
            }
            .padding(.bottom, 12 * scale)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
            .offset(y: y)
            .gesture(
                DragGesture(minimumDistance: 4)
                    .onChanged { v in
                        if origin == nil { origin = y }
                        y = max(0, (origin ?? y) + v.translation.height)
                    }
                    .onEnded { _ in
                        let close = y > 140
                        origin = nil
                        withAnimation(.spring(response: 0.34, dampingFraction: 0.86)) {
                            y = close ? screen : 0
                        }
                        if close {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: onDismiss)
                        }
                    }
            )
        }
        .onAppear {
            y = screen
            withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) { y = 0 }
        }
    }

    private func moreRow<V: View>(_ label: String, _ icon: () -> V, _ tap: @escaping () -> Void, red: Bool = false, last: Bool = false) -> some View {
        Button(action: tap) {
            HStack(spacing: 12 * scale) {
                icon().frame(width: 18 * scale, height: 18 * scale)
                AppText(label, style: Type.labelStrong, color: red ? Color(rgb: 0xEF4444) : Color(rgb: 0x111111), sizeOverride: 14)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 14 * scale)
            .overlay(alignment: .bottom) {
                if !last { Color(rgb: 0xF5F5F5).frame(height: 1) }
            }
        }
    }
}

struct EmojiPickSheet: View {
    let selected: String?
    let onPick: (String) -> Void
    let onDismiss: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var query = ""
    @State private var cat = 0

    private var pool: [String] {
        if query.isEmpty {
            return [EmojiRecent, EmojiSmileys, EmojiGestures][min(max(cat, 0), 2)]
        }
        return Array(Set(EmojiRecent + EmojiSmileys + EmojiGestures))
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.5).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(spacing: 0) {
                Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 40 * scale, height: 4 * scale).padding(.top, 10 * scale)
                HStack(spacing: 8 * scale) {
                    ChatIco.search(Color(rgb: 0x9CA3AF), side: 16 * scale)
                    TextField(Copy.t(.inboxSearchShort), text: $query)
                        .font(.system(size: 14 * scale))
                }
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 9 * scale)
                .background(Color(rgb: 0xF3F4F6))
                .clipShape(Capsule())
                .padding(.horizontal, 16 * scale)
                .padding(.vertical, 6 * scale)

                ScrollView {
                    VStack(alignment: .leading, spacing: 10 * scale) {
                        if query.isEmpty {
                            HStack {
                                AppText(Copy.t(.inboxYourReactions), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 15)
                                Spacer()
                                AppText(Copy.t(.inboxCustomize), style: Type.labelBold, color: Color(rgb: 0x3B82F6), sizeOverride: 13)
                            }
                            HStack(spacing: 8 * scale) {
                                ForEach(QuickReactions, id: \.self) { em in
                                    Button { onPick(em) } label: {
                                        AppText(em, style: Type.label, sizeOverride: 28)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 8 * scale)
                                            .background(selected == em ? Color(rgb: 0xF3E8FF) : Color(rgb: 0xF9FAFB))
                                            .clipShape(RoundedRectangle(cornerRadius: 16 * scale))
                                    }
                                }
                            }
                        }
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 8 * scale) {
                            ForEach(pool, id: \.self) { em in
                                Button { onPick(em) } label: {
                                    AppText(em, style: Type.label, sizeOverride: 26)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 6 * scale)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16 * scale)
                }
                .frame(maxHeight: 320 * scale)

                HStack {
                    ForEach(Array(["🕒", "😊", "🤟"].enumerated()), id: \.offset) { i, mark in
                        Button { cat = i; query = "" } label: {
                            VStack(spacing: 4 * scale) {
                                AppText(mark, style: Type.label, sizeOverride: 20)
                                Capsule()
                                    .fill(cat == i && query.isEmpty ? Dark.primary : Color.clear)
                                    .frame(width: 20 * scale, height: 2 * scale)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.bottom, 10 * scale)
            }
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
        }
    }
}

struct ForwardPickSheet: View {
    let inbox: InboxController
    let preview: String
    var mediaURL: URL? = nil
    var kind: MsgKind = .text
    let onDismiss: () -> Void
    let onSend: ([InboxChat], String) -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.brand) private var brand
    @State private var query = ""
    @State private var note = ""
    @State private var picked: Set<Int> = []
    @State private var frac: CGFloat = 0.04
    @State private var origin: CGFloat?
    @State private var hint: String?

    private var people: [InboxChat] {
        inbox.chats.filter { query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) || $0.handle.localizedCaseInsensitiveContains(query) }
    }

    private var shareLink: String { "https://\(brand.wordmark).app/s" }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.45 * Double(min(1, max(0, frac)))).ignoresSafeArea()
                    .onTapGesture {
                        guard origin == nil else { return }
                        withAnimation(.spring(response: 0.34, dampingFraction: 0.88)) { frac = 0.02 }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: onDismiss)
                    }
                VStack(spacing: 0) {
                    Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 40 * scale, height: 4 * scale)
                        .padding(.top, 12 * scale)
                        .padding(.bottom, 8 * scale)
                    HStack {
                        AppText(Copy.t(.inboxForward), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                        Spacer()
                        Button {
                            withAnimation(.spring(response: 0.34, dampingFraction: 0.88)) { frac = 0.02 }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: onDismiss)
                        } label: { ChatIco.close(Color(rgb: 0x999999), side: 16 * scale) }
                    }
                    .padding(.horizontal, 16 * scale)
                    HStack(alignment: .center, spacing: 10 * scale) {
                        if let mediaURL, kind == .image || kind == .video || kind == .ai {
                            ZStack {
                                LocalThumb(url: mediaURL, fallback: 0x8B5CF6)
                                if kind == .video {
                                    Color.black.opacity(0.22)
                                    ChatIco.play(.white, side: 16 * scale)
                                }
                            }
                            .frame(width: 56 * scale, height: 56 * scale)
                            .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                        }
                        VStack(alignment: .leading, spacing: 2 * scale) {
                            AppText(Copy.t(.inboxForwarding), style: Type.labelBold, color: Dark.primary, sizeOverride: 12)
                            AppText(preview, style: Type.caption, color: Color(rgb: 0x555555), sizeOverride: 13, lineLimit: 2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 10 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(Color(rgb: 0xF8F8FF))
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    .padding(.horizontal, 16 * scale)
                    .padding(.top, 8 * scale)
                    HStack(spacing: 8 * scale) {
                        ChatIco.search(Color(rgb: 0x888888), side: 16 * scale)
                        TextField(Copy.t(.inboxSearchShort), text: $query)
                            .font(.system(size: 14 * scale))
                    }
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(Color(rgb: 0xF3F4F6))
                    .clipShape(Capsule())
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 8 * scale)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 16 * scale) {
                            ForEach(people) { c in
                                let on = picked.contains(c.id)
                                VStack(spacing: 6 * scale) {
                                    ZStack(alignment: .bottomTrailing) {
                                        RoundedRectangle(cornerRadius: c.group ? 18 * scale : 32 * scale, style: .continuous)
                                            .fill(Color(rgb: c.color))
                                            .frame(width: 64 * scale, height: 64 * scale)
                                            .overlay(AppText(c.initials, style: Type.labelBold, color: .white, sizeOverride: 18))
                                        if on {
                                            Circle()
                                                .fill(Color(rgb: 0x3B82F6))
                                                .frame(width: 20 * scale, height: 20 * scale)
                                                .overlay(Circle().stroke(Color.white, lineWidth: 2))
                                                .overlay(ChatIco.tick(.white, side: 10 * scale))
                                        }
                                    }
                                    AppText(c.name, style: Type.micro, color: Color(rgb: 0x333333), sizeOverride: 11, lineLimit: 1)
                                        .frame(width: 72 * scale)
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    if on { picked.remove(c.id) } else { picked.insert(c.id) }
                                }
                            }
                        }
                        .padding(.horizontal, 16 * scale)
                        .padding(.vertical, 8 * scale)
                    }
                    Spacer(minLength: 0)
                    if picked.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 16 * scale) {
                                shareChip(Copy.t(.inboxShareStoryShort), Color(rgb: 0x111111), { ChatIco.shareStory(.white, side: 22 * scale) }) {
                                    inbox.myStory = preview
                                    hint = Copy.t(.inboxAddToStory)
                                }
                                shareChip(Copy.t(.inboxWhatsApp), Color(rgb: 0x25D366), { ChatIco.shareWhatsApp(.white, side: 26 * scale) }) {
                                    presentShare()
                                }
                                shareChip(Copy.t(.inboxCopyAction), Color(rgb: 0x6B7280), { ChatIco.link(.white, side: 18 * scale) }) {
                                    UIPasteboard.general.string = shareLink
                                    hint = Copy.t(.inboxCopied)
                                }
                                shareChip(Copy.t(.inboxShareStatusShort), Color(rgb: 0x128C7E), { ChatIco.shareStatus(.white, side: 22 * scale) }) {
                                    presentShare()
                                }
                                shareChip(Copy.t(.inboxSnapchat), Color(rgb: 0xFFFC00), { ChatIco.shareSnap(Color(rgb: 0x111111), side: 22 * scale) }) {
                                    presentShare()
                                }
                                shareChip(Copy.t(.inboxDownload), Color(rgb: 0x8B5CF6), { ChatIco.download(.white, side: 20 * scale) }) {
                                    presentShare()
                                }
                                shareChip(Copy.t(.inboxMessenger), Color(rgb: 0x006AFF), { ChatIco.shareMessenger(.white, side: 22 * scale) }) {
                                    presentShare()
                                }
                                shareChip(Copy.t(.inboxSms), Color(rgb: 0x34C759), { ChatIco.shareSms(.white, side: 22 * scale) }) {
                                    let encoded = preview.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                                    if let url = URL(string: "sms:&body=\(encoded)") { UIApplication.shared.open(url) }
                                }
                                shareChip(Copy.t(.inboxLink), Color(rgb: 0x2563EB), { ChatIco.link(.white, side: 18 * scale) }) {
                                    presentShare(shareLink)
                                }
                                shareChip(Copy.t(.inboxFacebook), Color(rgb: 0x1877F2), { ChatIco.shareFacebook(.white, side: 22 * scale) }) {
                                    presentShare()
                                }
                            }
                            .padding(.horizontal, 16 * scale)
                            .padding(.top, 14 * scale)
                            .padding(.bottom, 22 * scale)
                        }
                    } else {
                        VStack(spacing: 10 * scale) {
                            TextField(Copy.t(.inboxWriteMsg), text: $note)
                                .padding(.horizontal, 14 * scale)
                                .padding(.vertical, 10 * scale)
                                .background(Color(rgb: 0xF3F4F6))
                                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                            Button {
                                onSend(inbox.chats.filter { picked.contains($0.id) }, note.trimmingCharacters(in: .whitespacesAndNewlines))
                            } label: {
                                AppText(
                                    "\(Copy.t(.inboxSendBtn)) (\(picked.count))",
                                    style: Type.labelBold,
                                    color: .white,
                                    sizeOverride: 16
                                )
                                .frame(maxWidth: .infinity)
                                .frame(height: 48 * scale)
                                .background(Gradients.brand)
                                .clipShape(Capsule())
                            }
                        }
                        .padding(.horizontal, 16 * scale)
                        .padding(.vertical, 10 * scale)
                    }
                    if let hint {
                        AppText(hint, style: Type.micro, color: .white, sizeOverride: 12)
                            .padding(.horizontal, 14 * scale)
                            .padding(.vertical, 6 * scale)
                            .background(Color.black.opacity(0.9))
                            .clipShape(Capsule())
                            .padding(.bottom, 8 * scale)
                            .onAppear {
                                DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { self.hint = nil }
                            }
                    }
                }
                .frame(height: geo.size.height * frac)
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
                .simultaneousGesture(
                    DragGesture(minimumDistance: 10)
                        .onChanged { v in
                            if origin == nil { origin = frac }
                            frac = min(0.92, max(0.04, (origin ?? frac) - v.translation.height / geo.size.height))
                        }
                        .onEnded { _ in
                            let close = frac < 0.38
                            origin = nil
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.9)) {
                                frac = close ? 0.02 : min(0.92, max(0.42, frac))
                            }
                            if close {
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.32, execute: onDismiss)
                            }
                        }
                )
            }
        }
        .ignoresSafeArea()
        .onAppear {
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
            frac = 0.04
            withAnimation(.spring(response: 0.42, dampingFraction: 0.9)) { frac = 0.62 }
        }
    }

    private func shareItems() -> [Any] {
        guard let mediaURL else { return [preview] }
        if kind == .image || kind == .ai, let data = try? Data(contentsOf: mediaURL), let img = UIImage(data: data) {
            return [img]
        }
        return [mediaURL]
    }

    private func presentShare(_ extra: Any? = nil) {
        DispatchQueue.global(qos: .userInitiated).async {
            let payload = extra.map { [$0] } ?? self.shareItems()
            DispatchQueue.main.async {
                let ac = UIActivityViewController(activityItems: payload, applicationActivities: nil)
                guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let root = scene.windows.first(where: \.isKeyWindow)?.rootViewController else { return }
                var top = root
                while let shown = top.presentedViewController { top = shown }
                top.present(ac, animated: true)
            }
        }
    }

    private func shareChip<V: View>(_ label: String, _ bg: Color, _ icon: () -> V, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            VStack(spacing: 8 * scale) {
                Circle().fill(bg).frame(width: 56 * scale, height: 56 * scale).overlay(icon())
                AppText(label, style: Type.micro, color: Color(rgb: 0x333333), sizeOverride: 11, lineLimit: 1)
                    .frame(width: 68 * scale)
            }
        }
        .buttonStyle(.plain)
    }
}

struct MessageInfoSheet: View {
    let msg: ChatMessage
    let theme: ChatTheme
    let onDismiss: () -> Void
    /// Clock times from the other side's receipts; empty until they happen.
    var seenAt: String = ""
    var deliveredAt: String = ""
    @Environment(\.uiScale) private var scale

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onDismiss) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16 * scale, weight: .semibold))
                        .foregroundColor(Light.text)
                        .frame(width: 36 * scale, height: 36 * scale)
                }
                AppText(Copy.t(.inboxInfo), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 17)
                Spacer()
            }
            .padding(.horizontal, 8 * scale)
            .padding(.vertical, 12 * scale)
            Group {
                if msg.kind == .image || msg.kind == .video || msg.kind == .ai {
                    LocalThumb(url: msg.mediaURL, fallback: theme.start)
                        .frame(maxWidth: .infinity)
                        .frame(height: 280 * scale)
                        .clipped()
                } else {
                    AppText(msg.text.isEmpty ? preview(of: msg) : msg.text, style: Type.label, color: .white)
                        .padding(.horizontal, 14 * scale)
                        .padding(.vertical, 10 * scale)
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .background(LinearGradient(colors: [Color(rgb: theme.start), Color(rgb: theme.end)], startPoint: .leading, endPoint: .trailing))
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale))
            .padding(14 * scale)
            .overlay(RoundedRectangle(cornerRadius: 16 * scale).stroke(Color(rgb: 0x22C55E), lineWidth: 2))
            .padding(.horizontal, 16 * scale)

            if msg.mine {
                VStack(spacing: 0) {
                    infoLine(Copy.t(.inboxSeen), seenAt.isEmpty ? "—" : seenAt, !seenAt.isEmpty)
                    infoLine(Copy.t(.inboxDelivered), deliveredAt.isEmpty ? "—" : deliveredAt, false)
                }
                .overlay(RoundedRectangle(cornerRadius: 16 * scale).stroke(Color(rgb: 0xF0F0F0), lineWidth: 1))
                .padding(.horizontal, 16 * scale)
            }
            Spacer()
        }
        .background(Color.white.ignoresSafeArea())
    }

    private func infoLine(_ title: String, _ sub: String, _ seen: Bool) -> some View {
        HStack(spacing: 12 * scale) {
            ChatIco.doubleTick(read: seen, side: 16 * scale)
            VStack(alignment: .leading, spacing: 2 * scale) {
                AppText(title, style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 15)
                AppText(sub, style: Type.caption, color: Color(rgb: 0x888888), sizeOverride: 12)
            }
            Spacer()
        }
        .padding(.horizontal, 18 * scale)
        .padding(.vertical, 16 * scale)
    }
}

struct ReactorsSheet: View {
    let msg: ChatMessage
    let onDismiss: () -> Void
    let onRemoveMine: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var frac: CGFloat = 0.04
    @State private var origin: CGFloat?

    private var rows: [MsgReactor] {
        if !msg.reactors.isEmpty { return msg.reactors }
        if let emoji = msg.reaction {
            return [MsgReactor(name: Copy.t(.inboxYou), initials: "ME", color: 0xFF8B5CF6, emoji: emoji, mine: true)]
        }
        return []
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.38 * Double(min(1, max(0, frac)))).ignoresSafeArea()
                    .onTapGesture {
                        guard origin == nil else { return }
                        withAnimation(.spring(response: 0.34, dampingFraction: 0.88)) { frac = 0.02 }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: onDismiss)
                    }
                VStack(spacing: 0) {
                    VStack(spacing: 0) {
                        Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 40 * scale, height: 4 * scale).padding(.top, 10 * scale)
                        AppText(Copy.t(.inboxReactions), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8 * scale)
                    }
                    .frame(maxWidth: .infinity)
                    }
                    .contentShape(Rectangle())
                    ScrollView {
                        VStack(spacing: 0) {
                            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                                Button(action: { if row.mine { onRemoveMine() } }) {
                                    HStack(spacing: 12 * scale) {
                                        Text(row.initials)
                                            .font(.system(size: 13 * scale, weight: .bold))
                                            .foregroundColor(.white)
                                            .frame(width: 42 * scale, height: 42 * scale)
                                            .background(Color(rgb: row.color))
                                            .clipShape(Circle())
                                        VStack(alignment: .leading, spacing: 2 * scale) {
                                            AppText(row.mine ? Copy.t(.inboxYou) : row.name, style: Type.labelStrong, color: Color(rgb: 0x111111), sizeOverride: 15)
                                            if row.mine {
                                                AppText(Copy.t(.inboxTapToRemove), style: Type.caption, color: Color(rgb: 0x8B8B8B), sizeOverride: 12)
                                            }
                                        }
                                        Spacer(minLength: 0)
                                        Text(row.emoji).font(.system(size: 22 * scale))
                                    }
                                    .padding(.horizontal, 18 * scale)
                                    .padding(.vertical, 10 * scale)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .disabled(!row.mine)
                            }
                        }
                    }
                }
                .frame(height: geo.size.height * frac)
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 22 * scale, style: .continuous))
                .padding(.bottom, insets.bottom)
                .simultaneousGesture(
                    DragGesture(minimumDistance: 10)
                        .onChanged { v in
                            if origin == nil { origin = frac }
                            frac = min(0.92, max(0.04, (origin ?? frac) - v.translation.height / geo.size.height))
                        }
                        .onEnded { _ in
                            let close = frac < 0.38
                            origin = nil
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.9)) {
                                frac = close ? 0.02 : min(0.92, max(0.42, frac))
                            }
                            if close {
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.32, execute: onDismiss)
                            }
                        }
                )
            }
        }
        .ignoresSafeArea()
        .onAppear {
            frac = 0.04
            withAnimation(.spring(response: 0.42, dampingFraction: 0.9)) { frac = 0.58 }
        }
    }
}

