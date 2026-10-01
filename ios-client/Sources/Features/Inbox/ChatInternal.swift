import SwiftUI
import Photos

struct ChatProfileView: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    @Binding var pane: ThreadPane
    let onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        switch pane {
        case .search: SearchChatView(chat: chat, inbox: inbox, onBack: onBack)
        case .theme: ThemePickerView(chat: chat, inbox: inbox, onBack: onBack)
        case .nicknames: NicknamesView(chat: chat, inbox: inbox, onBack: onBack)
        case .disappear: DisappearView(inbox: inbox, onBack: onBack)
        case .media: ChatMediaView(chat: chat, inbox: inbox, onBack: onBack)
        default: main
        }
    }

    @State private var showOptions = false

    /// The live row, so presence, avatar and status follow the stream rather
    /// than the snapshot this pane was opened with.
    private var live: InboxChat { inbox.chat(chat.id) ?? chat }

    private func openProfile() {
        guard live.peerId != 0 else { return }
        PersonRouter.shared.open(live.peerId)
    }

    private var main: some View {
        ZStack(alignment: .topTrailing) {
            ScrollView {
                VStack(spacing: 0) {
                    HStack {
                        back
                        Spacer()
                    }
                    .padding(.horizontal, 12 * scale)
                    .padding(.top, insets.top)
                    VStack(spacing: 12 * scale) {
                        // Their real picture, and a way through to the rest of
                        // them, rather than initials on a pane that claims to
                        // be a profile.
                        if chat.group {
                            initials(chat.initials, color: chat.color, group: true, size: 96 * scale)
                        } else {
                            ChatAvatar(chat: live, size: 96 * scale, showOnline: true)
                                .onTapGesture { openProfile() }
                        }
                        AppText(inbox.displayName(chat), style: Type.h2, color: Light.text, sizeOverride: 22)
                        AppText(chat.group ? Copy.t(.inboxOnlineShort, ["n": "\(max(chat.members.count, 1))"]) : chat.handle, style: Type.caption, color: Light.sub)
                        // What they put on their profile travels with them.
                        if !live.status.isEmpty {
                            AppText(live.status, style: Type.caption, color: Light.text, lineLimit: 3)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 14 * scale)
                                .padding(.vertical, 9 * scale)
                                .background(Light.surface)
                                .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                                .padding(.horizontal, 32 * scale)
                        }
                    }
                    .padding(.top, 16 * scale)
                    HStack {
                        profileAction(Copy.t(.inboxProfile), { openProfile() }) { ChatIco.person(Light.text, side: 22 * scale) }
                        profileAction("Search", { pane = .search }) { ChatIco.search(Light.text, side: 22 * scale) }
                        profileAction(inbox.muted.contains(chat.id) ? Copy.t(.inboxUnmute) : Copy.t(.inboxMute), { inbox.toggleMute(chat.id) }) {
                            ChatIco.bell(inbox.muted.contains(chat.id) ? Color(rgb: 0xEF4444) : Light.text, side: 22 * scale, muted: inbox.muted.contains(chat.id))
                        }
                        profileAction(Copy.t(.inboxOptions), { showOptions.toggle() }) { ChatIco.moreH(Light.text, side: 22 * scale) }
                    }
                    .padding(.vertical, 20 * scale)
                    row(Copy.t(.inboxMedia)) { pane = .media }
                    row(Copy.t(.inboxNicknames)) { pane = .nicknames }
                    row(Copy.t(.inboxDisappear)) { pane = .disappear }
                    HStack(spacing: 14 * scale) {
                        Circle().fill(Color(rgb: 0xF0FFF4)).frame(width: 38 * scale, height: 38 * scale)
                            .overlay(ChatIco.mediaVis(Color(rgb: 0x10B981), side: 18 * scale))
                        VStack(alignment: .leading, spacing: 2) {
                            AppText(Copy.t(.inboxMediaVis), style: Type.labelStrong, color: Light.text, sizeOverride: 14)
                            AppText(Copy.t(inbox.mediaVisibility ? .inboxSavingMedia : .inboxNotSavingMedia), style: Type.caption, color: Light.sub, sizeOverride: 12)
                        }
                        Spacer()
                        Button { inbox.mediaVisibility.toggle() } label: {
                            Capsule().fill(inbox.mediaVisibility ? Color(rgb: 0x10B981) : Color(rgb: 0xD1D5DB))
                                .frame(width: 44 * scale, height: 26 * scale)
                                .overlay(alignment: inbox.mediaVisibility ? .trailing : .leading) {
                                    Circle().fill(Color.white).frame(width: 20 * scale, height: 20 * scale).padding(3 * scale)
                                }
                        }
                    }
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 13 * scale)
                    if !chat.group {
                        row(Copy.t(.inboxCreateGroup)) { }
                    }
                    SharedMediaTabs(chat: chat, inbox: inbox)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Light.background.ignoresSafeArea())
            if showOptions {
                Color.black.opacity(0.01)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .ignoresSafeArea()
                    .onTapGesture { showOptions = false }
                VStack(alignment: .leading, spacing: 0) {
                    profileMenuRow(Copy.t(.inboxRestrict), Light.text) { ChatIco.restrict(Light.textSoft, side: 18 * scale) }
                    profileMenuRow(Copy.t((inbox.chat(chat.id)?.blocked ?? false) ? .inboxUnblock : .inboxBlock), Light.text) { ChatIco.blockUser(Light.textSoft, side: 18 * scale) }
                    profileMenuRow(Copy.t(.inboxReport), Color(rgb: 0xEF4444)) { ChatIco.report(Color(rgb: 0xEF4444), side: 18 * scale) }
                    profileMenuRow(Copy.t(.inboxShareProfile), Light.text) { ChatIco.share(Light.textSoft, side: 18 * scale) }
                    profileMenuRow(Copy.t(.inboxExportChat), Light.text) { ChatIco.exportChat(Light.textSoft, side: 18 * scale) }
                    profileMenuRow(Copy.t(.inboxClearChat), Color(rgb: 0xEF4444), last: true) { ChatIco.trash(Color(rgb: 0xEF4444), side: 18 * scale) }
                }
                .frame(width: 210 * scale, alignment: .leading)
                .background(Light.background)
                .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16 * scale).stroke(Color(rgb: 0xEBEBEB), lineWidth: 1))
                .padding(.top, insets.top + 300 * scale)
                .padding(.trailing, 12 * scale)
            }
        }
    }

    private var back: some View {
        Button(action: onBack) {
            Image(systemName: "chevron.left")
                .font(.system(size: 16 * scale, weight: .semibold))
                .foregroundColor(Light.text)
                .frame(width: 36 * scale, height: 36 * scale)
        }
    }

    private func profileAction<V: View>(_ label: String, _ action: @escaping () -> Void, @ViewBuilder icon: () -> V) -> some View {
        Button(action: action) {
            VStack(spacing: 6 * scale) {
                Circle().fill(Light.surface).frame(width: 52 * scale, height: 52 * scale)
                    .overlay(icon())
                AppText(label, style: Type.micro, color: Light.textSoft, sizeOverride: 11)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(PressScaleStyle())
    }

    private func row(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                AppText(label, style: Type.labelStrong, color: Light.text)
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12 * scale)).foregroundColor(Light.muted)
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 16 * scale)
        }
    }

    private func profileMenuRow<V: View>(_ label: String, _ color: Color, last: Bool = false, @ViewBuilder icon: () -> V) -> some View {
        Button {
            switch label {
            case Copy.t(.inboxClearChat): inbox.clear(chatId: chat.id)
            case Copy.t(.inboxBlock): inbox.block(chatId: chat.id, on: true)
            case Copy.t(.inboxUnblock): inbox.block(chatId: chat.id, on: false)
            case Copy.t(.inboxReport): inbox.report(chatId: chat.id)
            default: break
            }
            showOptions = false
        } label: {
            HStack(spacing: 12 * scale) {
                icon()
                AppText(label, style: Type.labelStrong, color: color, sizeOverride: 14)
                Spacer()
            }
            .padding(.horizontal, 16 * scale)
            .padding(.vertical, 13 * scale)
            .overlay(alignment: .bottom) {
                if !last { Color(rgb: 0xF5F5F5).frame(height: 1) }
            }
        }
        .buttonStyle(.plain)
    }
}

struct SearchChatView: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    let onBack: () -> Void
    @State private var query = ""
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        let msgs = inbox.threads[chat.id] ?? []
        let results = query.isEmpty ? [] : msgs.filter { $0.text.localizedCaseInsensitiveContains(query) }
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").foregroundColor(Light.text).frame(width: 36 * scale, height: 36 * scale)
                }
                TextField(Copy.t(.inboxSearchChat), text: $query)
                    .padding(10 * scale)
                    .background(Color(rgb: 0xF5F5F5))
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
            }
            .padding(.horizontal, 12 * scale)
            .padding(.top, insets.top)
            if query.isEmpty {
                Spacer()
                AppText(Copy.t(.inboxTypeSearch), style: Type.caption, color: Light.sub)
                Spacer()
            } else if results.isEmpty {
                Spacer()
                AppText(Copy.t(.inboxNoResults, ["q": query]), style: Type.caption, color: Light.sub)
                Spacer()
            } else {
                List(results) { msg in
                    VStack(alignment: .leading) {
                        AppText(msg.mine ? "You" : chat.name, style: Type.labelBold, color: Light.text, sizeOverride: 13)
                        AppText(msg.text, style: Type.caption, color: Light.sub, lineLimit: 1)
                    }
                }
                .listStyle(.plain)
            }
        }
        .background(Light.background.ignoresSafeArea())
    }
}

let bubbleSolids: [UInt32] = [0x8B5CF6, 0xEC4899, 0x10B981, 0x0EA5E9, 0xF59E0B, 0xEF4444, 0x3B82F6, 0x111111]

struct ThemePickerView: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    let onBack: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var previewWall: String?
    @State private var previewBubble: UInt32?
    @State private var gallery = false
    @State private var tab = 0
    @State private var recents: [RecentThumb] = []
    @State private var pickedRecent: String?

    var body: some View {
        let sel = inbox.themeIndex(for: chat.id)
        let theme = chatThemes[sel]
        let wall = previewWall ?? inbox.wallpapers[chat.id]
        let bubble = previewBubble ?? inbox.bubbleColors[chat.id]
            ScrollView {
                VStack(alignment: .leading, spacing: 12 * scale) {
            header(Copy.t(.inboxChatTheme))
            ZStack(alignment: .bottomLeading) {
                Color(rgb: theme.bg)
                if let wall, let url = URL(string: wall) {
                    LocalThumb(url: url, fallback: theme.bg).clipped()
                }
                VStack(alignment: .trailing, spacing: 8 * scale) {
                    AppText("Hey, looking great! 👋", style: Type.caption, color: .white)
                        .padding(8 * scale)
                        .background {
                            if let bubble {
                                Color(rgb: bubble)
                            } else {
                                LinearGradient(colors: [Color(rgb: theme.start), Color(rgb: theme.end)], startPoint: .leading, endPoint: .trailing)
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale))
                }
                .padding(12 * scale)
            }
            .frame(height: 160 * scale)
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale))
            .padding(.horizontal, 16 * scale)
            HStack(spacing: 0) {
                ForEach(["Wallpaper", "Bubble", "Theme"].indices, id: \.self) { i in
                    let labels = [Copy.t(.inboxWallpaper), Copy.t(.inboxBubbleColor), Copy.t(.inboxChatTheme)]
                    Button { tab = i } label: {
                        AppText(labels[i], style: Type.micro, color: tab == i ? .white : Light.sub, sizeOverride: 11)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8 * scale)
                            .background(tab == i ? Dark.primary : Color.clear)
                            .clipShape(Capsule())
                    }
                }
            }
            .padding(3 * scale)
            .background(Color(rgb: 0xF3F4F6))
            .clipShape(Capsule())
            .padding(.horizontal, 16 * scale)
            if tab == 0 {
            VStack(alignment: .leading, spacing: 10 * scale) {
                if !recents.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8 * scale) {
                            ForEach(recents) { item in
                                Button {
                                    pickedRecent = item.id
                                    exportWall(item.asset)
                                } label: {
                                    ZStack(alignment: .topTrailing) {
                                        Image(uiImage: item.image)
                                            .resizable().scaledToFill()
                                            .frame(width: 64 * scale, height: 64 * scale)
                                            .clipped()
                                            .clipShape(RoundedRectangle(cornerRadius: 10 * scale))
                                        if pickedRecent == item.id {
                                            Circle().fill(Dark.primary).frame(width: 18 * scale, height: 18 * scale)
                                                .overlay(ChatIco.tick(.white, side: 10 * scale))
                                                .padding(4 * scale)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HStack {
                    Button { gallery = true } label: {
                        AppText(Copy.t(.inboxSeeAll), style: Type.labelBold, color: Dark.primary, sizeOverride: 12)
                            .padding(.horizontal, 12 * scale).padding(.vertical, 8 * scale)
                            .background(Color(rgb: 0xF3E8FF)).clipShape(Capsule())
                    }
                    if wall != nil {
                        Button {
                            inbox.wallpapers[chat.id] = nil
                            previewWall = nil
                        } label: {
                            AppText(Copy.t(.inboxRemove), style: Type.labelBold, color: Color(rgb: 0xEF4444), sizeOverride: 12)
                                .padding(.horizontal, 12 * scale).padding(.vertical, 8 * scale)
                                .background(Color(rgb: 0xFEE2E2)).clipShape(Capsule())
                        }
                    }
                }
            }
            .padding(.horizontal, 16 * scale)
            }
            if tab == 1 {
            AppText(Copy.t(.inboxBubbleColor), style: Type.labelBold, color: Light.text, sizeOverride: 14)
                .padding(.horizontal, 16 * scale)
            HStack(spacing: 10 * scale) {
                ForEach(bubbleSolids, id: \.self) { color in
                    Button { previewBubble = color } label: {
                        Circle().fill(Color(rgb: color)).frame(width: 32 * scale, height: 32 * scale)
                            .overlay(Circle().stroke(previewBubble == color || bubble == color ? Color(rgb: 0x111111) : Color.clear, lineWidth: 3))
                    }
                }
            }
            .padding(.horizontal, 16 * scale)
            if let bubble {
                HStack(spacing: 8 * scale) {
                    Button { inbox.bubbleColors[chat.id] = bubble } label: {
                        AppText(Copy.t(.inboxApply), style: Type.labelBold, color: .white, sizeOverride: 13)
                            .padding(.horizontal, 16 * scale).padding(.vertical, 8 * scale)
                            .background(Dark.primary).clipShape(Capsule())
                    }
                    Button { inbox.bubbleColors[chat.id] = nil; previewBubble = nil } label: {
                        AppText(Copy.t(.inboxRemove), style: Type.labelBold, color: Light.sub, sizeOverride: 13)
                            .padding(.horizontal, 16 * scale).padding(.vertical, 8 * scale)
                            .background(Color(rgb: 0xF3F4F6)).clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 16 * scale)
            }
            }
            if tab == 2 {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 14 * scale) {
                ForEach(Array(chatThemes.enumerated()), id: \.offset) { i, t in
                    Button {
                        inbox.themes[chat.id] = i
                        if i == 0 { inbox.bubbleColors[chat.id] = nil; previewBubble = nil }
                    } label: {
                        VStack {
                            RoundedRectangle(cornerRadius: 14 * scale)
                                .fill(LinearGradient(colors: [Color(rgb: t.start), Color(rgb: t.end)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                .aspectRatio(1, contentMode: .fit)
                                .overlay(RoundedRectangle(cornerRadius: 14 * scale).stroke(i == sel ? Color(rgb: 0x111111) : Color.clear, lineWidth: 3))
                            AppText(t.label, style: Type.micro, color: i == sel ? Light.text : Light.sub, sizeOverride: 11)
                        }
                    }
                }
            }
            .padding(16 * scale)
            .padding(.bottom, 24 * scale)
            }
                }
            }
        .background(Light.background.ignoresSafeArea())
        .sheet(isPresented: $gallery) {
            DeviceGalleryView(
                onPick: { url in
                    previewWall = url.absoluteString
                    inbox.wallpapers[chat.id] = url.absoluteString
                    gallery = false
                },
                onClose: { gallery = false },
                confirmPick: true
            )
        }
        .onAppear {
            previewWall = inbox.wallpapers[chat.id]
            previewBubble = inbox.bubbleColors[chat.id]
            fetchRecentThumbs(limit: 8) { recents = $0 }
        }
    }

    private func header(_ title: String) -> some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left").foregroundColor(Light.text).frame(width: 36 * scale, height: 36 * scale)
            }
            AppText(title, style: Type.labelBold, color: Light.text, sizeOverride: 16)
            Spacer()
        }
        .padding(.horizontal, 12 * scale)
        .padding(.top, insets.top)
    }

    private func exportWall(_ asset: PHAsset) {
        exportPhotoAsset(asset) { url in
            previewWall = url.absoluteString
            inbox.wallpapers[chat.id] = url.absoluteString
        }
    }
}

struct NicknamesView: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    let onBack: () -> Void
    @State private var editing: InboxMember?
    @State private var draft = ""
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        let people = (chat.group ? chat.members : [InboxMember(name: chat.name, handle: chat.handle, initials: chat.initials, color: chat.color)])
            + [InboxMember(name: "You", handle: "me", initials: "ME", color: 0x8B5CF6)]
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").foregroundColor(Light.text).frame(width: 36 * scale, height: 36 * scale)
                }
                AppText(Copy.t(.inboxNicknames), style: Type.labelBold, color: Light.text, sizeOverride: 16)
                Spacer()
            }
            .padding(.horizontal, 12 * scale)
            .padding(.top, insets.top)
            AppText("Set a nickname for anyone in this chat. Only you can see it.", style: Type.caption, color: Light.sub)
                .padding(16 * scale)
            ForEach(people) { m in
                Button {
                    editing = m
                    draft = inbox.nicknames[m.handle] ?? ""
                } label: {
                    HStack {
                        initials(m.initials, color: m.color, group: false, size: 44 * scale)
                        VStack(alignment: .leading) {
                            AppText(m.name, style: Type.labelStrong, color: Light.text)
                            AppText(inbox.nicknames[m.handle] ?? Copy.t(.inboxAddNick), style: Type.caption, color: inbox.nicknames[m.handle] != nil ? Dark.primary : Light.muted)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 12 * scale)
                }
            }
            Spacer()
        }
        .background(Light.background.ignoresSafeArea())
        .overlay {
            if let m = editing {
                ZStack(alignment: .bottom) {
                    Color.black.opacity(0.35).ignoresSafeArea().onTapGesture { editing = nil }
                    VStack(spacing: 14 * scale) {
                        Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 36 * scale, height: 4 * scale)
                        HStack {
                            initials(m.initials, color: m.color, group: false, size: 48 * scale)
                            VStack(alignment: .leading) {
                                AppText(m.name, style: Type.labelBold, color: Light.text, sizeOverride: 15)
                                AppText(Copy.t(.inboxAddNick), style: Type.caption, color: Light.sub)
                            }
                            Spacer()
                        }
                        TextField(Copy.t(.inboxAddNick), text: $draft)
                            .padding(14 * scale)
                            .background(Color(rgb: 0xF5F5F5))
                            .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                            .textInputAutocapitalization(.words)
                        HStack {
                            Button(Copy.t(.inboxCancel)) { editing = nil }
                                .frame(maxWidth: .infinity, minHeight: 46 * scale)
                                .background(Color(rgb: 0xF0F0F0))
                                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                            Button(Copy.t(.profileSave)) {
                                let t = draft.trimmingCharacters(in: .whitespaces)
                                if t.isEmpty { inbox.nicknames[m.handle] = nil } else { inbox.nicknames[m.handle] = t }
                                editing = nil
                            }
                            .frame(maxWidth: .infinity, minHeight: 46 * scale)
                            .foregroundColor(.white)
                            .background(Gradients.brand)
                            .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                        }
                    }
                    .padding(20 * scale)
                    .background(Light.background)
                    .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
                }
                .ignoresSafeArea(.container, edges: .bottom)
            }
        }
    }
}

struct DisappearView: View {
    @ObservedObject var inbox: InboxController
    let onBack: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        let opts: [(VanishTimer, String, String)] = [
            (.off, Copy.t(.inboxOff), "Messages will stay in the chat"),
            (.seen, Copy.t(.inboxOnceSeen), "Disappear after being viewed"),
            (.hours, Copy.t(.inboxHours), "Disappear 24 hours after sent"),
            (.days, Copy.t(.inboxDays), "Disappear 7 days after sent"),
        ]
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").foregroundColor(Light.text).frame(width: 36 * scale, height: 36 * scale)
                }
                AppText(Copy.t(.inboxDisappear), style: Type.labelBold, color: Light.text, sizeOverride: 16)
                Spacer()
            }
            .padding(.horizontal, 12 * scale)
            .padding(.top, insets.top)
            VStack(spacing: 8 * scale) {
                Circle().fill(Color(rgb: 0xDCFCE7)).frame(width: 100 * scale, height: 100 * scale)
                AppText(Copy.t(.inboxMakeVanish), style: Type.labelBold, color: Light.text, sizeOverride: 17)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24 * scale)
            ForEach(opts, id: \.1) { opt in
                Button { inbox.vanish = opt.0 } label: {
                    HStack {
                        Circle().stroke(inbox.vanish == opt.0 ? Dark.primary : Light.muted, lineWidth: 2)
                            .frame(width: 22 * scale, height: 22 * scale)
                            .overlay(Circle().fill(inbox.vanish == opt.0 ? Dark.primary : Color.clear).frame(width: 11 * scale, height: 11 * scale))
                        VStack(alignment: .leading) {
                            AppText(opt.1, style: Type.labelStrong, color: Light.text)
                            AppText(opt.2, style: Type.caption, color: Light.sub)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 14 * scale)
                }
            }
            Spacer()
        }
        .background(Light.background.ignoresSafeArea())
    }
}

struct ChatMediaView: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    let onBack: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        VStack {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").foregroundColor(Light.text).frame(width: 36 * scale, height: 36 * scale)
                }
                AppText(Copy.t(.inboxMedia), style: Type.labelBold, color: Light.text, sizeOverride: 16)
                Spacer()
            }
            .padding(.horizontal, 12 * scale)
            .padding(.top, insets.top)
            SharedMediaTabs(chat: chat, inbox: inbox)
            Spacer()
        }
        .background(Light.background.ignoresSafeArea())
    }
}

struct SharedMediaTabs: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    @State private var tab = 0
    @Environment(\.uiScale) private var scale

    var body: some View {
        let thread = inbox.threads[chat.id] ?? []
        let images = thread.filter { $0.kind == .image }
        let docs = thread.filter { $0.kind == .document }
        let links = thread.filter { $0.kind == .location || $0.text.contains("http://") || $0.text.contains("https://") }
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                tabBtn(0, Copy.t(.inboxImages), grid: true)
                tabBtn(1, Copy.t(.inboxDocs), grid: false)
                tabBtn(2, Copy.t(.inboxLinks), grid: false)
            }
            Color(rgb: 0xF0F0F0).frame(height: 1)
            if tab == 0 {
                if images.isEmpty {
                    ForEach(0..<2, id: \.self) { _ in
                        HStack(spacing: 2) {
                            ForEach(0..<3, id: \.self) { _ in
                                Color(rgb: chat.color).opacity(0.18).aspectRatio(1, contentMode: .fit)
                            }
                        }
                    }
                } else {
                    let rows = stride(from: 0, to: images.count, by: 3).map { Array(images[$0..<min($0 + 3, images.count)]) }
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                        HStack(spacing: 2) {
                            ForEach(row) { msg in
                                LocalThumb(url: msg.mediaURL, fallback: chat.color).aspectRatio(1, contentMode: .fill).clipped()
                            }
                            if row.count < 3 {
                                ForEach(0..<(3 - row.count), id: \.self) { i in
                                    Color.clear.aspectRatio(1, contentMode: .fit).id(i)
                                }
                            }
                        }
                    }
                }
            } else if tab == 1 {
                if docs.isEmpty {
                    AppText(Copy.t(.inboxDocs), style: Type.caption, color: Light.sub).frame(maxWidth: .infinity).padding(24 * scale)
                } else {
                    ForEach(docs) { msg in
                        HStack(spacing: 12 * scale) {
                            RoundedRectangle(cornerRadius: 10 * scale).fill(Color(rgb: 0xEEF2FF)).frame(width: 40 * scale, height: 40 * scale)
                                .overlay(ChatIco.doc(Dark.primary, side: 18 * scale))
                            VStack(alignment: .leading) {
                                AppText(msg.text.isEmpty ? Copy.t(.inboxDocument) : msg.text, style: Type.labelStrong, color: Light.text, lineLimit: 1)
                                AppText(msg.time, style: Type.caption, color: Light.sub)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 16 * scale)
                        .padding(.vertical, 12 * scale)
                    }
                }
            } else {
                if links.isEmpty {
                    AppText(Copy.t(.inboxLinks), style: Type.caption, color: Light.sub).frame(maxWidth: .infinity).padding(24 * scale)
                } else {
                    ForEach(links) { msg in
                        HStack(spacing: 12 * scale) {
                            RoundedRectangle(cornerRadius: 10 * scale).fill(Color(rgb: 0xECFDF5)).frame(width: 40 * scale, height: 40 * scale)
                                .overlay(ChatIco.link(Color(rgb: 0x10B981), side: 18 * scale))
                            VStack(alignment: .leading) {
                                AppText(msg.text.isEmpty ? Copy.t(.inboxLocation) : msg.text, style: Type.labelStrong, color: Light.text, lineLimit: 1)
                                AppText(msg.time, style: Type.caption, color: Light.sub)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 16 * scale)
                        .padding(.vertical, 12 * scale)
                    }
                }
            }
        }
    }

    private func tabBtn(_ id: Int, _ label: String, grid: Bool) -> some View {
        let on = tab == id
        return Button { tab = id } label: {
            VStack(spacing: 6 * scale) {
                HStack(spacing: 6 * scale) {
                    if id == 0 { ChatIco.imagesGrid(on ? Dark.primary : Color(rgb: 0x888888), side: 16 * scale) }
                    else if id == 1 { ChatIco.doc(on ? Dark.primary : Color(rgb: 0x888888), side: 16 * scale) }
                    else { ChatIco.link(on ? Dark.primary : Color(rgb: 0x888888), side: 16 * scale) }
                    AppText(label, style: Type.labelStrong, color: on ? Dark.primary : Color(rgb: 0x888888), sizeOverride: 13)
                }
                .frame(maxWidth: .infinity, minHeight: 36 * scale)
                Rectangle().fill(on ? Dark.primary : Color.clear).frame(height: 2)
            }
        }
        .buttonStyle(.plain)
    }
}
