import SwiftUI
import UIKit

struct InboxView: View {
    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.feedBottomReserve) private var reserve
    @Environment(\.deviceClass) private var device

    @ObservedObject private var inbox = InboxController.shared
    @State private var lane: InboxLane = .chats
    @State private var query = ""
    @State private var showSearch = false
    @State private var openId: Int?
    @State private var shown: InboxChat?
    @State private var slide: CGFloat = 1
    @State private var newGroup = false
    @State private var newChat = false
    @State private var storyCreate = false
    @State private var context: InboxChat?
    @State private var storyId: Int?
    @State private var archivedOpen = false
    @State private var chatError = false
    var onImmersive: (Bool) -> Void
    var onUnread: (Int) -> Void

    init(onImmersive: @escaping (Bool) -> Void = { _ in }, onUnread: @escaping (Int) -> Void = { _ in }) {
        self.onImmersive = onImmersive
        self.onUnread = onUnread
    }

    var body: some View {
        let immersive = shown != nil || newGroup || newChat || storyCreate || storyId != nil
        return ZStack {
            Light.background.ignoresSafeArea()
            list
                .offset(x: (slide - 1) * UIScreen.main.bounds.width * 0.3)
                .allowsHitTesting(shown == nil && !newGroup && !newChat && !storyCreate)
            if newChat {
                NewChatView(inbox: inbox, onClose: { newChat = false }, onNewGroup: { newChat = false; newGroup = true }) { person in
                    newChat = false
                    openChat(inbox.openDirect(person: person).id)
                }
            } else if newGroup {
                NewGroupView(inbox: inbox, onClose: { newGroup = false }) { created in
                    newGroup = false
                    openChat(created.id)
                }
            } else if storyCreate {
                StoryCreateView(onClose: { storyCreate = false }, onShare: { _ in storyCreate = false })
            }
            if let chat = shown {
                ChatThreadView(chat: chat, inbox: inbox, onBack: { closeChat() }, onOpenChat: { next in
                    openChat(next.id)
                })
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .offset(x: slide * UIScreen.main.bounds.width)
                    .zIndex(2)
            }
        }
        .onAppear {
            inbox.attach()
            onUnread(inbox.unreadTotal)
            // The request can be set before this pane exists — the profile that
            // made it lives on another tab, and that tab may never have been
            // visited this session.
            consumeOpenRequest()
        }
        // Keep the realtime socket open while this pane lives: messages, ticks
        // and presence arrive on it, not by polling.
        .task { await LiveFeed.shared.hold() }
        .onChange(of: openId) { id in inbox.opened(id) }
        // "Message" from somebody's profile, which lives on another tab.
        .onChange(of: inbox.openRequest) { _ in consumeOpenRequest() }
        .onChange(of: inbox.unreadTotal) { v in onUnread(v) }
        .onChange(of: immersive) { v in
            if shown == nil { onImmersive(v) }
        }
        .overlay {
            if let chat = context {
                ContextMenu(chat: chat, inbox: inbox, onOpen: { openChat(chat.id); context = nil }, onDismiss: { context = nil })
            }
        }
        .fullScreenCover(item: storyBinding) { chat in
            StoryViewer(chat: chat, onClose: { storyId = nil })
        }
        .alert(Copy.t(.inboxChatOpenFail), isPresented: $chatError) {
            Button(Copy.t(.inboxOk), role: .cancel) { chatError = false }
        }
    }

    private func consumeOpenRequest() {
        guard let id = inbox.openRequest else { return }
        newChat = false
        newGroup = false
        storyCreate = false
        openChat(id)
        inbox.openRequestHandled()
    }

    private func openChat(_ id: Int) {
        guard let chat = inbox.chat(id) else {
            chatError = true
            return
        }
        onImmersive(true)
        shown = chat
        slide = 1
        openId = id
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.18)) { slide = 0 }
        }
    }

    private func closeChat() {
        openId = nil
        if !newGroup && !newChat && !storyCreate && storyId == nil { onImmersive(false) }
        withAnimation(.easeIn(duration: 0.14)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            if openId == nil { shown = nil }
        }
    }

    private var storyBinding: Binding<InboxChat?> {
        Binding(
            get: { storyId.flatMap { inbox.chat($0) } },
            set: { if $0 == nil { storyId = nil } }
        )
    }

    private var list: some View {
        VStack(spacing: 0) {
            HStack {
                AppText(Copy.t(.inboxTitle), style: Type.h2, color: Light.text, sizeOverride: 22)
                Spacer()
                Button {
                    showSearch.toggle()
                    if !showSearch { query = "" }
                } label: {
                    Circle()
                        .fill(showSearch ? Dark.primary.opacity(0.1) : Light.surface)
                        .frame(width: 36 * scale, height: 36 * scale)
                        .overlay(
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 14 * scale, weight: .semibold))
                                .foregroundColor(showSearch ? Dark.primary : Light.textSoft)
                        )
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            if showSearch {
                TextField(Copy.t(.inboxSearch), text: $query)
                    .padding(10 * scale)
                    .background(Light.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                    .padding(.horizontal, 20 * scale)
                    .padding(.top, 8 * scale)
                    // People who are not in the inbox yet are found in the
                    // directory, debounced inside the controller.
                    .onChange(of: query) { next in inbox.searchPeople(next) }
            }
            HStack(spacing: 6 * scale) {
                ForEach(InboxLane.allCases, id: \.self) { tab in
                    let on = lane == tab
                    let label: CopyKey = tab == .chats ? .inboxChats : tab == .notifications ? .inboxNotifications : .inboxActivity
                    Button { lane = tab } label: {
                        AppText(Copy.t(label), style: Type.labelStrong, color: on ? .white : Light.sub, sizeOverride: 13)
                            .frame(maxWidth: .infinity, minHeight: 32 * scale)
                            .background(
                                Capsule().fill(on ? Color.clear : Light.surface)
                            )
                            .background(
                                Capsule().fill(Gradients.brand).opacity(on ? 1 : 0)
                            )
                    }
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 8 * scale)
            switch lane {
            case .chats: chatsTab
            case .notifications: notifyTab
            case .activity: activityTab
            }
        }
    }

    private var chatsTab: some View {
        let pinned = inbox.pinned
        let archived = inbox.archived
        // Searching an inbox means finding a conversation: by who it is with,
        // or by something said in it. Anything not already in the inbox is a
        // person, listed separately below.
        let filtered = inbox.chats.filter {
            !archived.contains($0.id) && (query.isEmpty
                || $0.name.localizedCaseInsensitiveContains(query)
                || $0.handle.localizedCaseInsensitiveContains(query)
                || inbox.mentions($0.id, query))
        }
        .sorted { (pinned.contains($0.id) ? 1 : 0) > (pinned.contains($1.id) ? 1 : 0) }
        let known = Set(inbox.chats.map(\.peerId))
        let newPeople = query.isEmpty ? [] : inbox.peopleResults.filter { !known.contains($0.peerId) }
        let archivedChats = inbox.chats.filter { archived.contains($0.id) }
        return ZStack(alignment: .bottomTrailing) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    storiesRow
                    if filtered.contains(where: { pinned.contains($0.id) }) {
                        AppText(Copy.t(.inboxPinned), style: Type.micro, color: Light.muted, sizeOverride: 11)
                            .padding(.horizontal, 20 * scale)
                            .padding(.vertical, 6 * scale)
                    }
                    ForEach(Array(filtered.enumerated()), id: \.element.id) { idx, chat in
                        let isPinned = pinned.contains(chat.id)
                        let prevPinned = idx > 0 && pinned.contains(filtered[idx - 1].id)
                        if !isPinned && prevPinned {
                            AppText(Copy.t(.inboxAll), style: Type.micro, color: Light.muted, sizeOverride: 11)
                                .padding(.horizontal, 20 * scale)
                                .padding(.vertical, 6 * scale)
                        }
                        chatRow(chat)
                    }
                    if !newPeople.isEmpty {
                        AppText(Copy.t(.inboxPeople), style: Type.micro, color: Light.muted, sizeOverride: 11)
                            .padding(.horizontal, 20 * scale)
                            .padding(.vertical, 6 * scale)
                        ForEach(newPeople) { person in
                            personRow(person)
                        }
                    }
                    if filtered.isEmpty && newPeople.isEmpty && !query.isEmpty {
                        AppText(Copy.t(.inboxNoResults).replacingOccurrences(of: "{q}", with: query),
                                style: Type.caption, color: Light.sub)
                            .padding(.horizontal, 20 * scale)
                            .padding(.vertical, 24 * scale)
                    }
                    if !archivedChats.isEmpty {
                        Button { archivedOpen.toggle() } label: {
                            HStack {
                                AppText(Copy.t(.inboxArchived), style: Type.labelStrong, color: Light.text)
                                Spacer()
                                AppText("\(archivedChats.count)", style: Type.caption, color: Light.sub)
                            }
                            .padding(20 * scale)
                        }
                        if archivedOpen {
                            ForEach(archivedChats) { chat in chatRow(chat) }
                        }
                    }
                    Color.clear.frame(height: reserve + 72 * scale)
                }
            }
            .scrollBounceBehavior(.always)
            Button { newChat = true } label: {
                Circle().fill(Gradients.brand).frame(width: 52 * scale, height: 52 * scale)
                    .overlay(Image(systemName: "bubble.left.and.bubble.right.fill").foregroundColor(.white))
            }
            .padding(.trailing, 16 * scale)
            .padding(.bottom, reserve + 12 * scale)
        }
    }

    private var storiesRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12 * scale) {
                Button { storyCreate = true } label: {
                    VStack(spacing: 4 * scale) {
                        Circle()
                            .strokeBorder(Dark.primary.opacity(0.35), style: StrokeStyle(lineWidth: 2, dash: [5]))
                            .background(Circle().fill(Dark.primary.opacity(0.08)))
                            .frame(width: 52 * scale, height: 52 * scale)
                            .overlay(AppText("+", style: Type.h1, color: Dark.primary, sizeOverride: 22))
                        AppText(Copy.t(.inboxMyStory), style: Type.micro, color: Light.sub, sizeOverride: 10)
                    }
                }
                ForEach(inbox.stories) { c in
                    Button { storyId = c.id } label: {
                        VStack(spacing: 4 * scale) {
                            initials(c.initials, color: c.color, group: false, size: 52 * scale)
                                .overlay(Circle().stroke(Gradients.brand, lineWidth: 2.5))
                            AppText(c.name.split(separator: " ").first.map(String.init) ?? c.name, style: Type.micro, color: Light.sub, sizeOverride: 10)
                        }
                    }
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 10 * scale)
        }
    }

    private func chatRow(_ chat: InboxChat) -> some View {
        let muted = inbox.muted.contains(chat.id)
        return Button {
            inbox.markRead(chat.id)
            openChat(chat.id)
        } label: {
            HStack(spacing: 14 * scale) {
                ChatAvatar(chat: chat, size: 50 * scale, showOnline: chat.unread == 0 || muted)
                    .opacity(muted ? 0.45 : 1)
                VStack(alignment: .leading, spacing: 3 * scale) {
                    HStack {
                        AppText(chat.name, style: chat.unread > 0 ? Type.labelBold : Type.label, color: Light.text, sizeOverride: 17)
                        if inbox.pinned.contains(chat.id) { AppText("📌", style: Type.micro, sizeOverride: 10) }
                        if muted { AppText("🔕", style: Type.micro, sizeOverride: 10) }
                        Spacer()
                        AppText(chat.time, style: Type.micro, color: Light.muted, sizeOverride: 12)
                    }
                    // Somebody writing to you outranks whatever they last
                    // said: the row says so before the message lands.
                    if inbox.typing.contains(chat.id) {
                        HStack(spacing: 6 * scale) {
                            TypingDots(tint: Dark.primary, dot: 5 * scale)
                            AppText(Copy.t(.inboxTyping), style: Type.caption, color: Dark.primary, lineLimit: 1)
                        }
                    } else {
                        AppText(chat.preview, style: Type.caption, color: chat.unread > 0 && !muted ? Color(rgb: 0x7C3AED) : Light.sub, lineLimit: 1)
                    }
                }
                if chat.unread > 0 && !muted {
                    Circle().fill(Gradients.brand).frame(width: 20 * scale, height: 20 * scale)
                        .overlay(AppText("\(chat.unread)", style: Type.labelBold, color: .white, sizeOverride: 10))
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 11 * scale)
            .contentShape(Rectangle())
        }
        .simultaneousGesture(LongPressGesture().onEnded { _ in context = chat })
        .buttonStyle(PressScaleStyle(pressedScale: 0.99))
    }

    /// A search hit who has no conversation yet: tapping starts one.
    private func personRow(_ person: InboxChat) -> some View {
        Button {
            let chat = inbox.openDirect(person: person)
            query = ""
            showSearch = false
            openChat(chat.id)
        } label: {
            HStack(spacing: 14 * scale) {
                ChatAvatar(chat: person, size: 50 * scale, showOnline: true)
                VStack(alignment: .leading, spacing: 3 * scale) {
                    AppText(person.name, style: Type.label, color: Light.text, sizeOverride: 17)
                    AppText(person.handle, style: Type.caption, color: Light.sub, lineLimit: 1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 11 * scale)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.99))
    }

    private var notifyTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                AppText(Copy.t(.inboxNewFollowers), style: Type.micro, color: Light.muted, sizeOverride: 11)
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 8 * scale)
                ForEach(inbox.followers) { u in
                    HStack {
                        initials(u.initials, color: u.color, group: false, size: 44 * scale)
                        VStack(alignment: .leading) {
                            AppText(u.name, style: Type.labelBold, color: Light.text, sizeOverride: 13)
                            AppText("\(Copy.t(.inboxFollowedYou)) · \(u.time)", style: Type.caption, color: Light.sub)
                        }
                        Spacer()
                        Button { inbox.followingBack.insert(u.handle) } label: {
                            AppText(Copy.t(.inboxFollow), style: Type.labelBold, color: .white, sizeOverride: 12)
                                .padding(.horizontal, 14 * scale)
                                .padding(.vertical, 6 * scale)
                                .background(Gradients.brand)
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 10 * scale)
                }
                AppText(Copy.t(.inboxSystem), style: Type.micro, color: Light.muted, sizeOverride: 11)
                    .padding(.horizontal, 20 * scale)
                    .padding(.top, 12 * scale)
                ForEach(inbox.system) { n in
                    HStack(alignment: .top) {
                        Circle().fill(Dark.primary.opacity(0.08)).frame(width: 40 * scale, height: 40 * scale)
                            .overlay(Circle().fill(Dark.primary).frame(width: 10 * scale, height: 10 * scale))
                        VStack(alignment: .leading) {
                            AppText(
                                noticeTitle(n),
                                style: Type.labelStrong,
                                color: Light.text,
                                sizeOverride: 13
                            )
                            AppText(
                                n.kind == "post_restricted" ? Copy.t(.inboxVideoRestrictedBody) : n.msg,
                                style: Type.caption,
                                color: Light.sub
                            )
                        }
                        Spacer()
                        AppText(n.time, style: Type.micro, color: Light.muted, sizeOverride: 11)
                    }
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 10 * scale)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        switch n.kind {
                        case "support_reply":
                            if n.refId > 0 { SupportRouter.shared.openTicket(n.refId) }
                        case "role_request", "role_decision":
                            RolesRouter.shared.openRequest(n.refId)
                        case "role_change":
                            RolesRouter.shared.openHome()
                        default:
                            break
                        }
                    }
                }
                Color.clear.frame(height: reserve)
            }
        }
    }

    private func noticeTitle(_ n: SystemNotice) -> String {
        switch n.kind {
        case "post_restricted": return Copy.t(.inboxVideoRestrictedTitle)
        case "support_reply": return Copy.t(.inboxSupportRepliedTitle)
        case "role_request": return n.title.isEmpty ? Copy.t(.rolesNoticeRequest) : n.title
        case "role_decision": return n.title.isEmpty ? Copy.t(.rolesNoticeDecision) : n.title
        case "role_change": return n.title.isEmpty ? Copy.t(.rolesNoticeChange) : n.title
        default: return n.title
        }
    }

    private var activityTab: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(inbox.activity) { item in
                    HStack(alignment: .top, spacing: 12 * scale) {
                        initials(item.initials, color: item.color, group: false, size: 44 * scale)
                        VStack(alignment: .leading) {
                            AppText("\(item.user) \(item.msg)", style: Type.caption, color: Light.text)
                            AppText(item.time, style: Type.micro, color: Light.muted, sizeOverride: 11)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 11 * scale)
                }
                Color.clear.frame(height: reserve)
            }
        }
    }
}

struct NewChatView: View {
    @ObservedObject var inbox: InboxController
    let onClose: () -> Void
    let onNewGroup: () -> Void
    let onPick: (InboxChat) -> Void
    @State private var q = ""
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        // Empty query: the people already in the inbox. Typing: the account
        // directory (@username, name or email), answered per keystroke.
        let people = q.isEmpty ? inbox.chats.filter { !$0.group && $0.peerId != 0 } : inbox.peopleResults
        VStack(spacing: 0) {
            HStack {
                Button(Copy.t(.inboxCancel), action: onClose).foregroundColor(Dark.primary)
                Spacer()
                AppText(Copy.t(.inboxNewChat), style: Type.labelBold, color: Light.text, sizeOverride: 16)
                Spacer()
                Color.clear.frame(width: 52 * scale)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, insets.top + 8 * scale)
            Button(action: onNewGroup) {
                HStack(spacing: 14 * scale) {
                    Circle().fill(Gradients.brand).frame(width: 46 * scale, height: 46 * scale)
                        .overlay(Image(systemName: "person.badge.plus").foregroundColor(.white))
                    VStack(alignment: .leading, spacing: 2 * scale) {
                        AppText(Copy.t(.inboxNewGroup), style: Type.labelStrong, color: Light.text)
                        AppText(Copy.t(.inboxCreateGroup), style: Type.caption, color: Light.sub)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20 * scale)
                .padding(.vertical, 12 * scale)
            }
            TextField(Copy.t(.inboxSearchShort), text: $q)
                .padding(12 * scale)
                .background(Light.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                .padding(.horizontal, 20 * scale)
                .padding(.vertical, 8 * scale)
                .onChange(of: q) { next in inbox.searchPeople(next) }
            if q.isEmpty {
                AppText(Copy.t(.inboxRecents), style: Type.micro, color: Light.muted, sizeOverride: 11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 8 * scale)
            }
            ScrollView {
                // Search hits are unbound rows (id 0); the account id is the stable key.
                ForEach(people, id: \.peerId) { chat in
                    Button { onPick(chat) } label: {
                        HStack(spacing: 14 * scale) {
                            ChatAvatar(chat: chat, size: 46 * scale)
                            VStack(alignment: .leading, spacing: 2 * scale) {
                                AppText(chat.name, style: Type.labelStrong, color: Light.text)
                                AppText(chat.handle, style: Type.caption, color: Light.sub)
                            }
                            Spacer()
                        }
                        .padding(.horizontal, 20 * scale)
                        .padding(.vertical, 12 * scale)
                    }
                }
            }
        }
        .background(Light.background.ignoresSafeArea())
    }
}

struct NewGroupView: View {
    @ObservedObject var inbox: InboxController
    let onClose: () -> Void
    let onCreated: (InboxChat) -> Void
    @State private var q = ""
    @State private var groupName = ""
    @State private var selected: Set<Int> = []
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.brand) private var brand

    var body: some View {
        let people = inbox.chats.filter {
            !$0.group && (q.isEmpty || $0.name.localizedCaseInsensitiveContains(q) || $0.handle.localizedCaseInsensitiveContains(q))
        }
        let nextId = (inbox.chats.map(\.id).max() ?? 0) + 1
        let link = "https://\(brand.wordmark).app/g/\(nextId)"
        VStack(spacing: 0) {
            HStack {
                Button(Copy.t(.inboxCancel), action: onClose).foregroundColor(Dark.primary)
                Spacer()
                AppText(Copy.t(.inboxNewGroup), style: Type.labelBold, color: Light.text, sizeOverride: 16)
                Spacer()
                Button(Copy.t(.inboxCreate)) {
                    let members = inbox.chats.filter { selected.contains($0.id) }
                    let fallback = members.map { $0.name.split(separator: " ").first.map(String.init) ?? $0.name }.joined(separator: ", ")
                    let name = groupName.trimmingCharacters(in: .whitespacesAndNewlines)
                    onCreated(inbox.createGroup(name: name.isEmpty ? (fallback.isEmpty ? Copy.t(.inboxNewGroup) : fallback) : name, members: members))
                }
                .disabled(selected.isEmpty)
                .foregroundColor(selected.isEmpty ? Light.muted : .white)
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 6 * scale)
                .background(
                    Capsule().fill(selected.isEmpty ? Light.surface : Color.clear)
                )
                .background(
                    Capsule().fill(Gradients.brand).opacity(selected.isEmpty ? 0 : 1)
                )
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, insets.top + 8 * scale)
            VStack(alignment: .leading, spacing: 6 * scale) {
                AppText(Copy.t(.inboxGroupName), style: Type.micro, color: Light.muted, sizeOverride: 11)
                TextField(Copy.t(.inboxGroupName), text: $groupName)
                    .padding(12 * scale)
                    .background(Light.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                AppText(Copy.t(.inboxInviteLink), style: Type.micro, color: Light.muted, sizeOverride: 11)
                    .padding(.top, 6 * scale)
                HStack {
                    AppText(link, style: Type.caption, color: Dark.primary, sizeOverride: 13, lineLimit: 1)
                    Spacer()
                    Button(Copy.t(.inboxCopyAction)) { UIPasteboard.general.string = link }
                        .font(.system(size: 12 * scale, weight: .semibold))
                        .foregroundColor(Dark.primary)
                }
                .padding(12 * scale)
                .background(Light.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, 8 * scale)
            TextField(Copy.t(.inboxSearch), text: $q)
                .padding(12 * scale)
                .background(Light.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale))
                .padding(20 * scale)
            ScrollView {
                ForEach(people) { chat in
                    let on = selected.contains(chat.id)
                    Button {
                        if on { selected.remove(chat.id) } else { selected.insert(chat.id) }
                    } label: {
                        HStack {
                            initials(chat.initials, color: chat.color, group: false, size: 46 * scale)
                            VStack(alignment: .leading) {
                                AppText(chat.name, style: Type.labelStrong, color: Light.text)
                                AppText(chat.handle, style: Type.caption, color: Light.sub)
                            }
                            Spacer()
                            Circle()
                                .stroke(on ? Dark.primary : Light.borderStrong, lineWidth: 2)
                                .background(Circle().fill(on ? Dark.primary : Color.clear))
                                .frame(width: 22 * scale, height: 22 * scale)
                        }
                        .padding(.horizontal, 20 * scale)
                        .padding(.vertical, 12 * scale)
                    }
                }
            }
        }
        .background(Light.background.ignoresSafeArea())
    }
}

struct StoryCreateView: View {
    let onClose: () -> Void
    let onShare: (String) -> Void
    @State private var type: String?
    @State private var text = ""
    @State private var camera = false
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        VStack {
            if type == nil {
                HStack {
                    Button(action: onClose) { Image(systemName: "chevron.left").foregroundColor(Light.text) }
                    AppText(Copy.t(.inboxCreateStory), style: Type.labelBold, color: Light.text, sizeOverride: 17)
                    Spacer()
                }
                .padding(.horizontal, 20 * scale)
                .padding(.top, insets.top + 8 * scale)
                RoundedRectangle(cornerRadius: 20 * scale).fill(Gradients.brand).frame(height: 200 * scale)
                    .overlay(AppText(Copy.t(.inboxYourStory), style: Type.labelStrong, color: .white))
                    .padding(20 * scale)
                HStack {
                    ForEach([("photo", Copy.t(.inboxPhoto)), ("video", Copy.t(.inboxVideoType)), ("text", Copy.t(.inboxText))], id: \.0) { key, label in
                        Button { type = key } label: {
                            AppText(label, style: Type.labelBold, color: Light.text, sizeOverride: 13)
                                .frame(maxWidth: .infinity, minHeight: 64 * scale)
                                .background(Light.surface)
                                .clipShape(RoundedRectangle(cornerRadius: 16 * scale))
                        }
                    }
                }
                .padding(.horizontal, 20 * scale)
                Spacer()
            } else if type == "text" {
                ZStack {
                    Gradients.brand.ignoresSafeArea()
                    VStack {
                        HStack {
                            Button(Copy.t(.inboxCancel)) { type = nil }.foregroundColor(.white)
                            Spacer()
                            Button(Copy.t(.inboxShareStory)) { onShare(text.isEmpty ? "✨" : text) }
                                .foregroundColor(.white)
                        }
                        .padding()
                        TextField("Type something…", text: $text)
                            .multilineTextAlignment(.center)
                            .font(.system(size: 22 * scale, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                    }
                }
            } else {
                VStack {
                    HStack {
                        Button(Copy.t(.inboxCancel)) { type = nil }.foregroundColor(.white)
                        Spacer()
                        AppText(type == "photo" ? Copy.t(.inboxPhotoStory) : Copy.t(.inboxVideoStory), style: Type.labelBold, color: .white)
                        Spacer()
                        Button(Copy.t(.inboxShareStory)) { camera = true }.foregroundColor(.white)
                    }
                    .padding()
                    Spacer()
                    Button(Copy.t(.inboxOpenCamera)) { camera = true }
                        .foregroundColor(.white)
                        .padding(.horizontal, 28 * scale)
                        .padding(.vertical, 12 * scale)
                        .background(Gradients.brand)
                        .clipShape(Capsule())
                    Spacer()
                }
                .background(Color(rgb: 0x111111).ignoresSafeArea())
                .sheet(isPresented: $camera) {
                    MediaPicker(source: .camera) { _ in onShare("photo") }
                }
            }
        }
        .background(Light.background.ignoresSafeArea())
    }
}

struct StoryViewer: View {
    let chat: InboxChat
    let onClose: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        ZStack {
            Color(rgb: chat.color).ignoresSafeArea()
            VStack {
                Capsule().fill(Color.white.opacity(0.85)).frame(height: 3 * scale).padding(.horizontal, 20 * scale)
                HStack {
                    initials(chat.initials, color: 0xFFFFFF, group: false, size: 36 * scale)
                    AppText(chat.name, style: Type.labelBold, color: .white)
                    Spacer()
                }
                .padding(20 * scale)
                Spacer()
                AppText(chat.preview, style: Type.h2, color: .white, alignment: .center)
                    .padding(24 * scale)
                Spacer()
            }
        }
        .onTapGesture(perform: onClose)
    }
}

struct ContextMenu: View {
    let chat: InboxChat
    @ObservedObject var inbox: InboxController
    let onOpen: () -> Void
    let onDismiss: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        ZStack {
            Color.black.opacity(0.35).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    initials(chat.initials, color: chat.color, group: chat.group, size: 40 * scale)
                    AppText(chat.name, style: Type.labelBold, color: Light.text)
                }
                .padding(16 * scale)
                row(Copy.t(.inboxMessage), onOpen)
                row(inbox.pinned.contains(chat.id) ? Copy.t(.inboxUnpin) : Copy.t(.inboxPin)) { inbox.togglePin(chat.id); onDismiss() }
                row(inbox.muted.contains(chat.id) ? Copy.t(.inboxUnmute) : Copy.t(.inboxMute)) { inbox.toggleMute(chat.id); onDismiss() }
                row(inbox.archived.contains(chat.id) ? Copy.t(.inboxUnarchive) : Copy.t(.inboxArchive)) { inbox.toggleArchive(chat.id); onDismiss() }
                row(Copy.t(.inboxDelete), danger: true) { inbox.delete(chatId: chat.id); onDismiss() }
            }
            .frame(width: 260 * scale)
            .background(Light.background)
            .clipShape(RoundedRectangle(cornerRadius: 18 * scale))
        }
    }

    private func row(_ label: String, danger: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(label, style: Type.labelStrong, color: danger ? Color(rgb: 0xEF4444) : Light.text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20 * scale)
                .padding(.vertical, 12 * scale)
        }
    }
}
