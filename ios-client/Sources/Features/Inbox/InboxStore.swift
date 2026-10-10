import SwiftUI
import Combine
import UIKit

enum InboxLane: String, CaseIterable {
    case chats, notifications, activity
}

enum MsgKind { case text, image, video, voice, contact, location, document, poll, event, ai, system }

struct CallMsg {
    let video: Bool
    let status: String
    let durationSec: Int
    let callId: Int64
}

func parseCallMsg(_ text: String) -> CallMsg? {
    let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
    guard let first = lines.first, first.trimmingCharacters(in: .whitespaces) == "#call" else { return nil }
    return CallMsg(
        video: (lines.count > 1 ? lines[1].trimmingCharacters(in: .whitespaces) : "") == "video",
        status: lines.count > 2 ? lines[2].trimmingCharacters(in: .whitespaces) : "ended",
        durationSec: max(0, Int(lines.count > 3 ? lines[3].trimmingCharacters(in: .whitespaces) : "0") ?? 0),
        callId: Int64(lines.count > 4 ? lines[4].trimmingCharacters(in: .whitespaces) : "0") ?? 0
    )
}

func formatCallClock(_ sec: Int) -> String {
    let s = max(0, sec)
    return String(format: "%d:%02d", s / 60, s % 60)
}
enum VanishTimer { case off, seen, hours, days }

/// Where one of my messages is: on its way, on the server, on their phone, read.
enum Delivery { case sending, sent, delivered, seen }
enum ThreadPane { case chat, profile, search, theme, nicknames, disappear, media }

struct InboxMember: Identifiable, Equatable {
    var id: String { handle }
    let name: String
    let handle: String
    let initials: String
    let color: UInt32
}

struct InboxChat: Identifiable, Equatable {
    let id: Int
    var name: String
    let handle: String
    var preview: String
    var time: String
    var unread: Int
    let initials: String
    let color: UInt32
    var group: Bool = false
    var members: [InboxMember] = []
    /// The other account's id; 0 for groups and for a row still being resolved.
    var peerId: Int64 = 0
    /// Storage key or absolute URL; empty paints initials.
    var avatarUrl: String = ""
    /// Their "what's on your mind", shown on the chat's profile pane.
    var status: String = ""
    var online: Bool = false
    /// Epoch seconds of their last device leaving; 0 while online or unknown.
    var lastSeenAt: TimeInterval = 0
    /// The signed-in user has blocked this person.
    var blocked: Bool = false
    /// Epoch seconds of the last activity, for ordering.
    var sortAt: TimeInterval = 0
}

struct MsgReactor: Equatable {
    let name: String
    let initials: String
    let color: UInt32
    let emoji: String
    var mine: Bool = false
}

struct ChatMessage: Identifiable, Equatable {
    let id: Int64
    let chatId: Int
    let mine: Bool
    let from: String
    let initials: String
    let color: UInt32
    var text: String
    let time: String
    var kind: MsgKind = .text
    var mediaURL: URL? = nil
    var replyTo: Int64? = nil
    var reaction: String? = nil
    var reactors: [MsgReactor] = []
    var edited: Bool = false
    var delivery: Delivery = .sent
    /// The phone's own name for a send, so an ack or a stream echo finds the bubble.
    var clientId: String = ""
    /// Epoch seconds when a disappearing message leaves the screen; 0 for never.
    var expiresAt: TimeInterval = 0
}

struct ActivityItem: Identifiable {
    let id: Int
    let type: String
    let user: String
    let handle: String
    let msg: String
    let time: String
    let initials: String
    let color: UInt32
}

struct FollowNotice: Identifiable {
    var id: String { handle }
    let name: String
    let handle: String
    let initials: String
    let color: UInt32
    let time: String
}

struct SystemNotice: Identifiable {
    let id: String
    let kind: String
    let title: String
    let msg: String
    let time: String
    var refId: Int64 = 0
}

struct PendingAttach: Equatable {
    var kind: MsgKind
    var url: URL? = nil
    var label: String = ""
}

struct ChatTheme {
    let label: String
    let bg: UInt32
    let start: UInt32
    let end: UInt32
}

let chatThemes: [ChatTheme] = [
    ChatTheme(label: "Default", bg: 0xFFFFFF, start: 0x8B5CF6, end: 0xEC4899),
    ChatTheme(label: "Midnight", bg: 0x0F0F1A, start: 0x1A1A3E, end: 0x6D28D9),
    ChatTheme(label: "Sunset", bg: 0xFFF5F0, start: 0xF97316, end: 0xEC4899),
    ChatTheme(label: "Forest", bg: 0xF0FDF4, start: 0x059669, end: 0x10B981),
    ChatTheme(label: "Ocean", bg: 0xF0F9FF, start: 0x0284C7, end: 0x38BDF8),
    ChatTheme(label: "Rose", bg: 0xFFF1F2, start: 0xF43F5E, end: 0xFB7185),
    ChatTheme(label: "Gold", bg: 0xFFFBEB, start: 0xD97706, end: 0xF59E0B),
    ChatTheme(label: "Lavender", bg: 0xFAF5FF, start: 0x7C3AED, end: 0xA78BFA),
    ChatTheme(label: "Slate", bg: 0xF8FAFC, start: 0x475569, end: 0x94A3B8),
]

/// The inbox as the screens see it, backed by the chat service.
///
/// Rows and threads keep small local `Int` ids (the views key on them) mapped
/// to server conversation ids. Sends are optimistic: the bubble appears at
/// once with a clock tick, the server's row replaces it on ack, and receipts
/// off the shared stream move the tick to delivered and seen. One process-wide
/// instance: the tab bar badge, the list and the open thread all read it.
@MainActor
final class InboxController: ObservableObject {
    static let shared = InboxController(appName: Brand.current.name)

    @Published var chats: [InboxChat] = []
    /// A thread another screen wants opened; see `requestChat`.
    @Published var openRequest: Int?
    @Published var threads: [Int: [ChatMessage]] = [:]
    @Published var pinned: Set<Int> = []
    @Published var muted: Set<Int> = []
    @Published var archived: Set<Int> = []
    @Published var nicknames: [String: String] = [:]
    @Published var themes: [Int: Int] = [:]
    @Published var wallpapers: [Int: String] = [:]
    @Published var bubbleColors: [Int: UInt32] = [:]
    /// Writing this from the UI changes the open chat's timer on the server.
    @Published var vanish: VanishTimer = .off {
        didSet { if !paintingVanish, vanish != oldValue { setVanish(vanish) } }
    }
    @Published var mediaVisibility = true
    @Published var readReceipts = true
    @Published var followingBack: Set<String> = []
    @Published var system: [SystemNotice] = []
    @Published var myStory: String?
    @Published var pollMine: [Int64: Set<Int>] = [:]
    @Published var pollTally: [Int64: [Int]] = [:]
    /// Peers typing right now, by local chat id.
    @Published var typing: Set<Int> = []
    /// Search hits for the "+" screen, as chat rows (unbound until picked).
    @Published var peopleResults: [InboxChat] = []
    /// Set while the inbox has never been read from the server this session.
    @Published var loading = true
    /// Non-empty after a send or an action the server refused; the UI shows it once.
    @Published var notice = ""

    let activity: [ActivityItem]
    let followers: [FollowNotice]
    let stories: [InboxChat]

    private var convOf: [Int: Int64] = [:]
    private var localOf: [Int64: Int] = [:]
    private var convs: [Int: ConversationData] = [:]
    private var loaded: Set<Int> = []
    /// Message seq by message id, for tick and receipt lookups.
    private var seqs: [Int64: Int64] = [:]
    private var outbox: [String: Pending] = [:]
    private var nextChatId = 1
    private var localClock: Int64 = -1
    private var opened: Int?
    private var userId: Int64 = 0
    private var tokens: [LiveFeed.Token] = []
    private var expiryTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?
    private var typingTask: Task<Void, Never>?
    private var typingSentAt: TimeInterval = 0
    private var paintingVanish = false

    private struct Pending {
        var chatId: Int
        var localId: Int64
        var task: Task<Void, Never>?
    }

    private let stamp: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()

    var unreadTotal: Int {
        chats.reduce(0) { $0 + (muted.contains($1.id) ? 0 : $1.unread) }
    }

    init(appName: String) {
        let seed = InboxCatalog.load(appName)
        activity = seed.activity
        followers = seed.followers
        stories = []
    }

    // MARK: - lifecycle

    /// Binds to the signed-in account and starts listening. Idempotent.
    func attach() {
        adoptAccount()
        if tokens.isEmpty {
            let live = LiveFeed.shared
            tokens = [
                live.listenChat { [weak self] ev in self?.onEvent(ev) },
                live.listenConnected { [weak self] in self?.resync() },
                live.listenResync { [weak self] in self?.resync() },
                // The shell attaches before the profile has necessarily
                // landed, so the account may still be unknown here. The
                // stream's first frame is the profile: take the id from it
                // rather than leaving every "is this mine?" test answering no.
                live.listenProfile { [weak self] _ in self?.adoptAccount() },
                live.listenNotices { [weak self] n in self?.prependNotice(n) },
            ]
            expiryTask = Task { [weak self] in await self?.expiryLoop() }
        }
        if loading { refresh() }
    }

    private func adoptAccount() {
        let me = Xani.currentUserId
        guard me != 0, me != userId else { return }
        // Never through reset(): that drops the listeners, one of which is the
        // caller here.
        if userId != 0 {
            clearState()
            refresh()
        }
        userId = me
    }

    func signedOut() {
        reset()
        userId = 0
    }

    private func reset() {
        tokens.forEach { LiveFeed.shared.stopListening($0) }
        tokens = []
        expiryTask?.cancel()
        expiryTask = nil
        clearState()
    }

    private func clearState() {
        outbox.values.forEach { $0.task?.cancel() }
        outbox = [:]
        openRequest = nil
        chats = []
        threads = [:]
        convOf = [:]
        localOf = [:]
        convs = [:]
        loaded = []
        seqs = [:]
        pinned = []
        muted = []
        archived = []
        typing = []
        peopleResults = []
        loading = true
        opened = nil
    }

    /// Re-reads the inbox page. Cheap: one indexed read per 50 chats.
    func refresh() {
        Task {
            let r = await Xani.chat.inbox()
            guard let page = r.value else { return }
            page.items.forEach { absorb($0, bump: false) }
            // Chats the server no longer lists (deleted on another device) go.
            if page.next.isEmpty {
                let present = Set(page.items.map(\.id))
                chats.filter { !$0.group && $0.peerId != 0 && convOf[$0.id].map { !present.contains($0) } == true }
                    .forEach { drop($0.id) }
            }
            sortChats()
            loading = false
            await loadNotices()
        }
    }

    private func loadNotices() async {
        let r = await Xani.media.notices()
        guard let items = r.value else { return }
        let fmt = stamp
        system = items.map { n in
            let t = n.createdMs > 0 ? Date(timeIntervalSince1970: TimeInterval(n.createdMs) / 1000) : Date()
            return SystemNotice(id: String(n.id), kind: n.kind, title: n.title, msg: n.body, time: fmt.string(from: t), refId: n.refId)
        }
    }

    private func prependNotice(_ n: NoticeData) {
        let id = String(n.id)
        guard !system.contains(where: { $0.id == id }) else { return }
        let t = n.createdMs > 0 ? Date(timeIntervalSince1970: TimeInterval(n.createdMs) / 1000) : Date()
        system.insert(SystemNotice(id: id, kind: n.kind, title: n.title, msg: n.body, time: stamp.string(from: t), refId: n.refId), at: 0)
    }

    /// After a reconnect: refresh the list and pull what the open thread missed.
    private func resync() {
        refresh()
        // The socket is back, so the network is: every queued send gets another
        // go, not just the one in the thread on screen.
        Set(outbox.values.map(\.chatId)).forEach { flushOutbox($0) }
        guard let id = opened, let conv = convOf[id] else { return }
        let last = (threads[id] ?? []).last { $0.id > 0 }
        Task {
            let r = await Xani.chat.messages(conv, afterSeq: seqOf(id, last), limit: 100)
            r.value?.sorted { $0.seq < $1.seq }.forEach { absorbMessage($0) }
        }
    }

    // MARK: - reads the screens use

    func chat(_ id: Int) -> InboxChat? { chats.first { $0.id == id } }

    /// The server conversation behind a row; nil while the row is still being bound.
    func conversationOf(_ chatId: Int) -> Int64? { convOf[chatId] }

    /// Whether anything said in this chat contains `q`.
    ///
    /// Only the window already in memory is searched — the point is to find
    /// the conversation, and the thread's own search takes it from there. It
    /// runs per keystroke over a bounded list, so it never touches the network.
    func mentions(_ chatId: Int, _ q: String) -> Bool {
        guard !q.isEmpty, let list = threads[chatId] else { return false }
        return list.contains { $0.text.localizedCaseInsensitiveContains(q) }
    }

    func displayName(_ chat: InboxChat) -> String {
        nicknames[chat.handle] ?? chat.name
    }

    func themeIndex(for id: Int) -> Int {
        min(max(themes[id] ?? 0, 0), chatThemes.count - 1)
    }

    /// Ensures a thread's history is on its way; the views read `threads[id]`.
    func ensureLoaded(_ chatId: Int) {
        if !loaded.contains(chatId), convOf[chatId] != nil { loadHistory(chatId) }
    }

    /// "Online", "typing…", or "Last seen 5m ago" for the thread header.
    func presence(_ chat: InboxChat) -> String {
        if typing.contains(chat.id) { return Copy.t(.inboxTyping) }
        if chat.online { return Copy.t(.inboxOnline) }
        if chat.lastSeenAt > 0 { return Copy.t(.inboxLastSeen, ["t": ago(chat.lastSeenAt)]) }
        return Copy.t(.inboxOffline)
    }

    /// When the other side read `msg`; empty if not yet.
    func seenAt(_ chatId: Int, _ msg: ChatMessage) -> String {
        guard let c = convs[chatId], let s = seqs[msg.id] else { return "" }
        return s <= c.peerReadSeq && c.peerReadAt > 0 ? clock(c.peerReadAt) : ""
    }

    /// When the other side's phone received `msg`; empty if not yet.
    func deliveredAt(_ chatId: Int, _ msg: ChatMessage) -> String {
        guard let c = convs[chatId], let s = seqs[msg.id] else { return "" }
        return s <= c.peerDeliveredSeq && c.peerDeliveredAt > 0 ? clock(c.peerDeliveredAt) : ""
    }

    /// The screen tells us which thread is on screen so reads are acked as they land.
    func opened(_ chatId: Int?) {
        opened = chatId
        if let chatId {
            paintVanish(Self.vanishOf(convs[chatId]?.vanishSeconds ?? 0))
            ensureLoaded(chatId)
            markRead(chatId)
            // A thread opened from search may not have a server id yet.
            ensureBound(chatId)
            // Anything that ran out of retries while the app was backgrounded
            // is still on screen with a clock on it. Opening the thread is the
            // moment the user expects it to go: try again now.
            flushOutbox(chatId)
        } else {
            stopTyping()
        }
    }

    // MARK: - chat-level actions

    func togglePin(_ id: Int) {
        let on = !pinned.contains(id)
        if on { pinned.insert(id) } else { pinned.remove(id) }
        settings(id, pinned: on)
    }

    func toggleMute(_ id: Int) {
        let on = !muted.contains(id)
        if on { muted.insert(id) } else { muted.remove(id) }
        settings(id, muted: on)
    }

    func toggleArchive(_ id: Int) {
        let on = !archived.contains(id)
        if on { archived.insert(id) } else { archived.remove(id) }
        settings(id, archived: on)
    }

    private func settings(_ id: Int, pinned: Bool? = nil, muted: Bool? = nil, archived: Bool? = nil) {
        guard let conv = convOf[id] else { return }
        Task {
            let r = await Xani.chat.settings(conv, pinned: pinned, muted: muted, archived: archived)
            if let c = r.value { absorb(c, bump: false) }
        }
    }

    private func setVanish(_ timer: VanishTimer) {
        guard let id = opened, let conv = convOf[id] else { return }
        let seconds: Int
        switch timer {
        case .off: seconds = 0
        case .seen: seconds = -1
        case .hours: seconds = 24 * 3600
        case .days: seconds = 7 * 24 * 3600
        }
        convs[id]?.vanishSeconds = seconds
        Task {
            let r = await Xani.chat.settings(conv, vanishSeconds: seconds)
            if let c = r.value { absorb(c, bump: false) }
        }
    }

    private func paintVanish(_ v: VanishTimer) {
        guard vanish != v else { return }
        paintingVanish = true
        vanish = v
        paintingVanish = false
    }

    func markRead(_ id: Int) {
        if let i = chats.firstIndex(where: { $0.id == id }), chats[i].unread != 0 { chats[i].unread = 0 }
        guard let conv = convOf[id], let c = convs[id], c.readSeq < c.lastSeq else { return }
        convs[id]?.readSeq = c.lastSeq
        convs[id]?.deliveredSeq = c.lastSeq
        if c.vanishSeconds == Self.vanishSeen { armExpiry(id, upToSeq: c.lastSeq, theirs: true) }
        Task {
            let r = await Xani.chat.ack(conv, deliveredSeq: c.lastSeq, readSeq: c.lastSeq)
            if let next = r.value { absorb(next, bump: false) }
        }
    }

    /// Clears the history for this user only.
    func clear(chatId: Int) {
        threads[chatId] = []
        bumpPreview(chatId, "", lift: false)
        guard let conv = convOf[chatId] else { return }
        Task { if let c = await Xani.chat.clear(conv).value { absorb(c, bump: false) } }
    }

    /// Removes the chat from this user's list. It returns, empty, on the next message.
    func delete(chatId: Int) {
        let conv = convOf[chatId]
        drop(chatId)
        if let conv { Task { await Xani.chat.delete(conv) } }
    }

    /// Blocks (or unblocks) the other person; their sends fail at the server from now on.
    func block(chatId: Int, on: Bool = true) {
        guard let i = chats.firstIndex(where: { $0.id == chatId }), chats[i].peerId != 0 else { return }
        let peer = chats[i].peerId
        chats[i].blocked = on
        Task {
            let r = await Xani.chat.block(peer, on: on)
            if !r.ok, let j = chats.firstIndex(where: { $0.id == chatId }) {
                chats[j].blocked = !on
                notice = r.message
            }
        }
    }

    /// Files a report on the other person, optionally about one message.
    func report(chatId: Int, msgId: Int64 = 0, reason: String = "") {
        guard let peer = chat(chatId)?.peerId, peer != 0 else { return }
        let conv = convOf[chatId] ?? 0
        Task {
            let r = await Xani.chat.report(peer, reason: reason, conversationId: conv, messageId: msgId > 0 ? msgId : 0)
            if r.ok { notice = Copy.t(.inboxReported) }
        }
    }

    /// Coalesced: the server hears "typing" at most every few seconds and "stopped" once.
    func typing(chatId: Int) {
        guard let conv = convOf[chatId] else { return }
        let now = Date().timeIntervalSince1970
        if now - typingSentAt > Self.typingEvery {
            typingSentAt = now
            Task { await Xani.chat.typing(conv, on: true) }
        }
        typingTask?.cancel()
        typingTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Self.typingIdle * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.typingSentAt = 0
            await Xani.chat.typing(conv, on: false)
        }
    }

    private func stopTyping() {
        typingTask?.cancel()
        typingTask = nil
        guard typingSentAt != 0 else { return }
        typingSentAt = 0
        guard let id = opened, let conv = convOf[id] else { return }
        Task { await Xani.chat.typing(conv, on: false) }
    }

    // MARK: - people

    /// Searches accounts by @username, email or name for the "+" screen.
    func searchPeople(_ q: String) {
        searchTask?.cancel()
        let query = q.trimmingCharacters(in: .whitespacesAndNewlines)
        if query.isEmpty {
            peopleResults = []
            return
        }
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Self.searchDebounce * 1_000_000_000))
            guard !Task.isCancelled, let self else { return }
            let r = await Xani.people.search(query)
            guard !Task.isCancelled, let hits = r.value else { return }
            self.peopleResults = hits.filter { $0.id != self.userId }.map { p in
                self.chats.first { $0.peerId == p.id } ?? Self.row(for: p, id: 0)
            }
        }
    }

    /// A chat another tab asked for — "Message" on somebody's profile. The
    /// shell switches to Inbox when this is set, and the inbox opens the thread
    /// and clears it. Going through state rather than a call keeps the profile
    /// screen out of the business of knowing where the inbox lives.
    func requestChat(_ person: PersonData) {
        guard person.id != 0, person.id != userId else { return }
        let existing = chats.first { !$0.group && $0.peerId == person.id }
        openRequest = openDirect(person: existing ?? Self.row(for: person, id: 0)).id
    }

    func openRequestHandled() { openRequest = nil }

    /// Opens (or creates) the direct chat with a person picked from search.
    func openDirect(person: InboxChat) -> InboxChat {
        if let existing = chats.first(where: { !$0.group && $0.peerId != 0 && $0.peerId == person.peerId }) { return existing }
        let id = nextId()
        let chat = InboxChat(
            id: id, name: person.name, handle: person.handle, preview: "", time: "", unread: 0,
            initials: person.initials, color: person.color, peerId: person.peerId, avatarUrl: person.avatarUrl,
            online: person.online, lastSeenAt: person.lastSeenAt, blocked: person.blocked, sortAt: Date().timeIntervalSince1970
        )
        chats.insert(chat, at: 0)
        threads[id] = []
        if person.peerId != 0 { bind(id, peerId: person.peerId) }
        return chat
    }

    /// Opens a chat by display name — the path a shared contact card takes.
    /// The row appears at once; the account is looked up behind it.
    func openDirect(name: String) -> InboxChat {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if let existing = chats.first(where: { !$0.group && $0.name.compare(n, options: .caseInsensitive) == .orderedSame }) {
            return existing
        }
        let id = nextId()
        let handle = "@" + String(n.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "." }.prefix(24))
        let chat = InboxChat(
            id: id, name: n.isEmpty ? "Chat" : n, handle: handle == "@" ? "@user" : handle,
            preview: "", time: "", unread: 0, initials: Self.initials(n), color: Self.color(for: Int64(n.hashValue)),
            sortAt: Date().timeIntervalSince1970
        )
        chats.insert(chat, at: 0)
        threads[id] = []
        let bare = n.hasPrefix("@") ? String(n.dropFirst()) : n
        Task {
            let hits = await Xani.people.search(bare, limit: 5).value ?? []
            let hit = hits.first { $0.id != userId && ($0.username.compare(bare, options: .caseInsensitive) == .orderedSame || $0.fullName.compare(n, options: .caseInsensitive) == .orderedSame) }
                ?? hits.first { $0.id != userId }
            guard let hit else {
                notice = "Couldn't find that account."
                return
            }
            if let i = chats.firstIndex(where: { $0.id == id }) {
                var row = Self.row(for: hit, id: id)
                row.preview = chats[i].preview
                row.time = chats[i].time
                chats[i] = row
            }
            bind(id, peerId: hit.id)
        }
        return chat
    }

    /// Groups stay on the device until the group service lands.
    func createGroup(name: String, members: [InboxChat]) -> InboxChat {
        let id = nextId()
        let chat = InboxChat(
            id: id, name: name, handle: "group", preview: "You created this group", time: "now",
            unread: 0, initials: String(name.prefix(2)).uppercased(), color: 0x7C3AED, group: true,
            members: members.map { InboxMember(name: $0.name, handle: $0.handle, initials: $0.initials, color: $0.color) },
            sortAt: Date().timeIntervalSince1970
        )
        chats.insert(chat, at: 0)
        threads[id] = []
        loaded.insert(id)
        return chat
    }

    /// Resolves a local row to its server conversation, then flushes anything queued.
    private func bind(_ chatId: Int, peerId: Int64) {
        Task {
            let r = await Xani.chat.open(peerId)
            guard let conv = r.value else {
                notice = r.message.isEmpty ? "Couldn't open that chat." : r.message
                failUnbound(chatId)
                return
            }
            // The same conversation may already sit in the list under another
            // local id (opened from search while it existed offscreen).
            if let existing = localOf[conv.id], existing != chatId {
                let moving = threads.removeValue(forKey: chatId) ?? []
                threads[existing, default: []].append(contentsOf: moving)
                chats.removeAll { $0.id == chatId }
                for (k, p) in outbox where p.chatId == chatId { outbox[k]?.chatId = existing }
                absorb(conv, bump: false)
                flushOutbox(existing)
                return
            }
            convOf[chatId] = conv.id
            localOf[conv.id] = chatId
            absorb(conv, bump: false)
            if !loaded.contains(chatId) { loadHistory(chatId) }
            flushOutbox(chatId)
        }
    }

    // MARK: - messages

    func send(chatId: Int, text: String, kind: MsgKind = .text, media: URL? = nil, replyTo: Int64? = nil) {
        adoptAccount()
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty || media != nil || kind != .text else { return }
        localClock -= 1
        let id = localClock
        let clientId = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let msg = ChatMessage(
            id: id, chatId: chatId, mine: true, from: "You", initials: "ME", color: 0x8B5CF6,
            text: trimmed, time: stamp.string(from: Date()), kind: kind, mediaURL: media, replyTo: replyTo,
            delivery: .sending, clientId: clientId
        )
        var list = threads[chatId] ?? []
        list.append(msg)
        threads[chatId] = Self.trim(list)
        bumpPreview(chatId, preview(of: msg))
        stopTyping()
        if kind == .poll {
            pollMine[id] = []
            pollTally[id] = Array(repeating: 0, count: pollOptions(trimmed).count)
        }
        if chat(chatId)?.group == true {
            // Local-only until groups ship; show it as sent so the demo thread reads.
            replaceLocal(chatId, id) { $0.delivery = .sent }
            return
        }
        outbox[clientId] = Pending(chatId: chatId, localId: id, task: nil)
        // A brand-new chat may not have its server id yet: open it, then flush.
        // Without that the bubble sits on a clock forever.
        if convOf[chatId] != nil {
            flushOutbox(chatId)
        } else if let peer = chat(chatId)?.peerId, peer != 0 {
            ensureBound(chatId)
        } else {
            outbox[clientId] = nil
            replaceLocal(chatId, id) { $0.delivery = .sent }
            notice = "Couldn't reach that chat. Try again."
        }
    }

    /// Opens the direct conversation when we know the peer but not the server id.
    private func ensureBound(_ chatId: Int) {
        guard let peer = chat(chatId)?.peerId, peer != 0 else {
            failUnbound(chatId)
            return
        }
        if convOf[chatId] != nil {
            flushOutbox(chatId)
            return
        }
        bind(chatId, peerId: peer)
    }

    private func failUnbound(_ chatId: Int) {
        let stuck = outbox.filter { $0.value.chatId == chatId }
        for (clientId, p) in stuck {
            outbox[clientId] = nil
            replaceLocal(chatId, p.localId) { $0.delivery = .sent }
        }
        if !stuck.isEmpty { notice = "Couldn't open that chat. Try again." }
    }

    private func flushOutbox(_ chatId: Int) {
        guard let conv = convOf[chatId] else { return }
        for (clientId, p) in outbox where p.chatId == chatId && p.task == nil {
            guard let msg = (threads[chatId] ?? []).first(where: { $0.id == p.localId }) else {
                outbox[clientId] = nil
                continue
            }
            outbox[clientId]?.task = Task { [weak self] in await self?.deliver(conv, chatId, clientId, msg) }
        }
    }

    /// Sends one message with idempotent retries; the server row replaces the guess.
    private func deliver(_ conv: Int64, _ chatId: Int, _ clientId: String, _ msg: ChatMessage) async {
        let replyTo = (msg.replyTo ?? 0) > 0 ? msg.replyTo! : 0
        var backoff: UInt64 = 1_000_000_000
        for attempt in 0..<Self.maxSendAttempts {
            let r: ApiResponse<SendAck>
            if let media = msg.mediaURL, Self.isUpload(msg.kind) {
                r = await sendMedia(conv, clientId, msg, media, replyTo)
            } else {
                r = await Xani.chat.send(conv, clientId: clientId, body: msg.text.isEmpty ? preview(of: msg) : msg.text,
                                         kind: Self.wireKind(msg.kind), replyTo: replyTo)
            }
            if let ack = r.value {
                outbox[clientId] = nil
                replaceLocal(chatId, msg.id) { local in
                    let wire = self.fromWire(ack.message, chatId)
                    let keep = local.mediaURL
                    local = wire
                    // Keep the phone's own copy on screen: it is already decoded
                    // and it avoids downloading what we just sent.
                    if let keep { local.mediaURL = keep }
                    // Force mine + a non-clock tick even if the account id was
                    // still unknown when the wire row was shaped.
                    local.mine = true
                    local.from = "You"
                    local.initials = "ME"
                    if local.delivery == .sending { local.delivery = .sent }
                }
                absorb(ack.conversation, bump: false)
                return
            }
            if r.isSessionExpired {
                outbox[clientId]?.task = nil
                notice = "Session expired. Sign in again to send."
                return
            }
            if (400..<500).contains(r.statusCode) {
                // The server refused (blocked, too long, not a member): no retry
                // will change that. Drop the bubble and tell the user once.
                outbox[clientId] = nil
                threads[chatId]?.removeAll { $0.id == msg.id }
                notice = r.message
                return
            }
            if attempt < Self.maxSendAttempts - 1 {
                try? await Task.sleep(nanoseconds: backoff)
                backoff = min(backoff * 2, 15_000_000_000)
            }
        }
        // Leave it as Sending; the next reconnect flushes the outbox again.
        outbox[clientId]?.task = nil
    }

    private func sendMedia(_ conv: Int64, _ clientId: String, _ msg: ChatMessage, _ file: URL, _ replyTo: Int64) async -> ApiResponse<SendAck> {
        let name = file.lastPathComponent
        let mime = Self.mime(for: file, kind: msg.kind)
        return await Xani.chat.sendMedia(
            conv, clientId: clientId, kind: Self.wireKind(msg.kind), mime: mime, file: file,
            caption: msg.kind == .document ? "" : msg.text,
            fileName: msg.kind == .document ? (msg.text.isEmpty ? name : msg.text) : name,
            durationMs: 0, replyTo: replyTo
        )
    }

    func patch(chatId: Int, msgId: Int64, text: String) {
        guard var list = threads[chatId], let i = list.firstIndex(where: { $0.id == msgId }) else { return }
        let next = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let cur = list[i]
        if next == cur.text || next.isEmpty { return }
        list[i].text = next
        list[i].edited = true
        threads[chatId] = list
        bumpPreview(chatId, preview(of: list[i]), lift: false)
        guard msgId > 0 else { return }
        Task {
            let r = await Xani.chat.edit(msgId, body: next)
            if !r.ok, !r.isSessionExpired {
                replaceLocal(chatId, msgId) { $0.text = cur.text; $0.edited = cur.edited }
                notice = r.message
            }
        }
    }

    func votePoll(msgId: Int64, index: Int, optionCount: Int, multi: Bool) {
        guard index >= 0, index < optionCount else { return }
        var mine = pollMine[msgId] ?? []
        var counts = pollTally[msgId] ?? Array(repeating: 0, count: optionCount)
        while counts.count < optionCount { counts.append(0) }
        if multi {
            if mine.contains(index) {
                mine.remove(index)
                counts[index] = max(0, counts[index] - 1)
            } else {
                mine.insert(index)
                counts[index] += 1
            }
        } else {
            mine.forEach { counts[$0] = max(0, counts[$0] - 1) }
            mine = [index]
            counts[index] += 1
        }
        pollMine[msgId] = mine
        pollTally[msgId] = counts
    }

    /// Toggles the signed-in user's reaction; one per person, as Instagram does it.
    func react(chatId: Int, msgId: Int64, emoji: String) {
        guard var list = threads[chatId], let i = list.firstIndex(where: { $0.id == msgId }) else { return }
        let mine = list[i].reactors.first { $0.mine }
        let clearing = mine?.emoji == emoji
        let others = list[i].reactors.filter { !$0.mine }
        let next = clearing ? others : others + [MsgReactor(name: "You", initials: "ME", color: 0x8B5CF6, emoji: emoji, mine: true)]
        list[i].reactors = next
        list[i].reaction = next.last?.emoji
        threads[chatId] = list
        if msgId > 0 { Task { await Xani.chat.react(msgId, emoji: clearing ? "" : emoji) } }
    }

    func clearMineReaction(chatId: Int, msgId: Int64) {
        guard var list = threads[chatId], let i = list.firstIndex(where: { $0.id == msgId }) else { return }
        let next = list[i].reactors.filter { !$0.mine }
        list[i].reactors = next
        list[i].reaction = next.last?.emoji
        threads[chatId] = list
        if msgId > 0 { Task { await Xani.chat.react(msgId, emoji: "") } }
    }

    /// Mine: unsent for everyone. Theirs: removed from this phone only.
    func unsend(chatId: Int, msgId: Int64) {
        guard var list = threads[chatId], let msg = list.first(where: { $0.id == msgId }) else { return }
        list.removeAll { $0.id == msgId }
        threads[chatId] = list
        if msg.mine, msgId > 0 { Task { await Xani.chat.unsend(msgId) } }
        if msg.mine, msgId < 0 {
            // Still in flight: cancel the send; if it already landed, the ack path
            // finds no bubble and the retry is skipped.
            if let hit = outbox.first(where: { $0.value.localId == msgId }) {
                hit.value.task?.cancel()
                outbox[hit.key] = nil
            }
        }
        if let last = list.last { bumpPreview(chatId, preview(of: last), lift: false) }
    }

    // MARK: - stream

    private func onEvent(_ ev: ChatEvent) {
        switch ev {
        case .message(let m):
            absorbMessage(m)
        case .receipt(let r):
            guard let id = localOf[r.conversationId], let c = convs[id] else { return }
            if r.userId == userId {
                convs[id]?.readSeq = max(c.readSeq, r.readSeq)
                convs[id]?.deliveredSeq = max(c.deliveredSeq, r.deliveredSeq)
                return
            }
            convs[id]?.peerReadSeq = max(c.peerReadSeq, r.readSeq)
            if r.readSeq > c.peerReadSeq { convs[id]?.peerReadAt = r.at }
            convs[id]?.peerDeliveredSeq = max(c.peerDeliveredSeq, r.deliveredSeq)
            if r.deliveredSeq > c.peerDeliveredSeq { convs[id]?.peerDeliveredAt = r.at }
            repaintTicks(id)
            if c.vanishSeconds == Self.vanishSeen, r.readSeq > 0 { armExpiry(id, upToSeq: r.readSeq, theirs: false) }
        case .reaction(let r):
            guard let id = localOf[r.conversationId], var list = threads[id],
                  let i = list.firstIndex(where: { $0.id == r.messageId }) else { return }
            let mine = r.userId == userId
            let rest = list[i].reactors.filter { mine ? !$0.mine : $0.mine }
            let next = r.emoji.isEmpty ? rest : rest + [reactor(id, mine: mine, emoji: r.emoji)]
            list[i].reactors = next
            list[i].reaction = next.last?.emoji
            threads[id] = list
        case .typing(let t):
            guard let id = localOf[t.conversationId], t.userId != userId else { return }
            if t.typing {
                typing.insert(id)
                Task { [weak self] in
                    try? await Task.sleep(nanoseconds: UInt64(Self.typingShow * 1_000_000_000))
                    self?.typing.remove(id)
                }
            } else {
                typing.remove(id)
            }
        case .presence(let p):
            for i in chats.indices where chats[i].peerId == p.userId {
                chats[i].online = p.online
                chats[i].lastSeenAt = p.online ? 0 : p.lastSeenAt
            }
        case .conversation(let c):
            absorb(c, bump: false)
        case .gone(let conv):
            if let id = localOf[conv] { drop(id) }
        }
    }

    /// A message from the stream or a resume read: place it, ack it, repaint the row.
    private func absorbMessage(_ m: ChatMessageData) {
        guard let id = localOf[m.conversationId] else {
            // First word from someone new: fetch the row so the list has a name.
            Task {
                guard let conv = await Xani.chat.get(m.conversationId).value else { return }
                absorb(conv, bump: true)
                if localOf[m.conversationId] != nil { absorbMessage(m) }
            }
            return
        }
        var list = threads[id] ?? []
        adoptAccount()
        let mine = m.senderId == userId
        if m.deleted {
            list.removeAll { $0.id == m.id }
            threads[id] = list
            if let last = list.last { bumpPreview(id, preview(of: last), lift: false) }
            return
        }
        let wire = fromWire(m, id)
        if let existing = list.firstIndex(where: { $0.id == m.id }) {
            var next = wire
            next.mediaURL = list[existing].mediaURL ?? wire.mediaURL
            next.reactors = list[existing].reactors
            next.reaction = list[existing].reaction
            list[existing] = next
        } else if mine, let pendingIdx = list.firstIndex(where: { $0.id < 0 && $0.clientId == m.clientId }) {
            var next = wire
            next.mine = true
            next.from = "You"
            next.initials = "ME"
            next.mediaURL = list[pendingIdx].mediaURL ?? wire.mediaURL
            if next.delivery == .sending { next.delivery = .sent }
            list[pendingIdx] = next
            outbox.removeValue(forKey: m.clientId)?.task?.cancel()
        } else if loaded.contains(id) || !list.isEmpty {
            insertBySeq(&list, wire)
        }
        // else: history not read yet; it arrives with the first page
        threads[id] = Self.trim(list)
        if let c = convs[id], m.seq > c.lastSeq { convs[id]?.lastSeq = m.seq }
        if mine {
            bumpPreview(id, preview(of: wire), lift: true)
            return
        }
        let onScreen = opened == id
        if let i = chats.firstIndex(where: { $0.id == id }) {
            chats[i].preview = preview(of: wire)
            chats[i].time = "now"
            chats[i].unread = onScreen ? 0 : chats[i].unread + 1
            chats[i].sortAt = Date().timeIntervalSince1970
            if i > 0 { let row = chats.remove(at: i); chats.insert(row, at: 0) }
        }
        // Delivered the moment it is on the phone; read as well when the thread is open.
        let readSeq: Int64 = onScreen ? m.seq : 0
        if let c = convs[id] {
            convs[id]?.deliveredSeq = max(c.deliveredSeq, m.seq)
            if onScreen { convs[id]?.readSeq = max(c.readSeq, m.seq) }
            if onScreen, c.vanishSeconds == Self.vanishSeen { armExpiry(id, upToSeq: m.seq, theirs: true) }
        }
        let conv = m.conversationId
        Task { await Xani.chat.ack(conv, deliveredSeq: m.seq, readSeq: readSeq) }
    }

    private func loadHistory(_ chatId: Int) {
        guard let conv = convOf[chatId] else { return }
        loaded.insert(chatId)
        Task {
            let r = await Xani.chat.messages(conv, limit: Self.messageWindow)
            guard let page = r.value else {
                loaded.remove(chatId)
                return
            }
            let pending = (threads[chatId] ?? []).filter { $0.id < 0 }
            var list = page.sorted { $0.seq < $1.seq }.map { fromWire($0, chatId) }
            list.append(contentsOf: pending)
            threads[chatId] = list
            if opened == chatId { markRead(chatId) }
        }
    }

    // MARK: - shape mapping

    /// Server row → list row. Assigns a local id on first sight.
    private func absorb(_ c: ConversationData, bump: Bool) {
        let id: Int
        if let known = localOf[c.id] {
            id = known
        } else {
            id = nextId()
            localOf[c.id] = id
            convOf[id] = c.id
        }
        convs[id] = c
        let i = chats.firstIndex { $0.id == id }
        let prev = i.map { chats[$0] }
        let display = c.peer.fullName.isEmpty ? c.peer.username : c.peer.fullName
        var row = InboxChat(
            id: id,
            name: display.isEmpty ? (prev?.name ?? "User") : display,
            handle: "@" + (c.peer.username.isEmpty ? String((prev?.handle ?? "@").dropFirst()) : c.peer.username),
            preview: c.last.map { preview(of: fromWire($0, id)) } ?? (prev?.preview ?? ""),
            time: c.last.map { ago($0.createdAt) } ?? (prev?.time ?? ""),
            unread: opened == id ? 0 : c.unread,
            initials: Self.initials(display),
            color: Self.color(for: c.peer.id),
            peerId: c.peer.id,
            avatarUrl: c.peer.avatarUrl,
            status: c.peer.status,
            online: c.peer.online,
            lastSeenAt: c.peer.lastSeenAt,
            blocked: c.peer.blocked,
            sortAt: c.sortAt
        )
        if row.sortAt == 0 { row.sortAt = prev?.sortAt ?? 0 }
        if let i { chats[i] = row } else if bump { chats.insert(row, at: 0) } else { chats.append(row) }
        if c.pinned { pinned.insert(id) } else { pinned.remove(id) }
        if c.muted { muted.insert(id) } else { muted.remove(id) }
        if c.archived { archived.insert(id) } else { archived.remove(id) }
        if opened == id { paintVanish(Self.vanishOf(c.vanishSeconds)) }
        repaintTicks(id)
        if bump { sortChats() }
    }

    private static func row(for p: PersonData, id: Int) -> InboxChat {
        let display = p.fullName.isEmpty ? p.username : p.fullName
        return InboxChat(
            id: id, name: display, handle: "@" + p.username, preview: "", time: "", unread: 0,
            initials: initials(display), color: color(for: p.id), peerId: p.id, avatarUrl: p.avatarUrl,
            status: p.status
        )
    }

    private func fromWire(_ m: ChatMessageData, _ chatId: Int) -> ChatMessage {
        adoptAccount()
        let mine = m.senderId == userId
        let chat = chat(chatId)
        seqs[m.id] = m.seq
        let c = convs[chatId]
        return ChatMessage(
            id: m.id,
            chatId: chatId,
            mine: mine,
            from: mine ? "You" : (chat?.name ?? ""),
            initials: mine ? "ME" : (chat?.initials ?? "?"),
            color: mine ? 0x8B5CF6 : (chat?.color ?? 0x7C3AED),
            text: m.kind == "document" ? (m.mediaName.isEmpty ? m.body : m.mediaName) : m.body,
            time: clock(m.createdAt),
            kind: Self.localKind(m.kind),
            mediaURL: m.mediaKey.isEmpty ? nil : MediaConfig.shared.url(m.mediaKey),
            replyTo: m.replyTo > 0 ? m.replyTo : nil,
            reaction: m.reactions.last?.emoji,
            reactors: m.reactions.map { reactor(chatId, mine: $0.userId == userId, emoji: $0.emoji) },
            edited: m.edited,
            delivery: mine ? tick(for: m.seq, c) : .sent,
            clientId: m.clientId,
            expiresAt: m.expiresAt
        )
    }

    private func tick(for seq: Int64, _ c: ConversationData?) -> Delivery {
        guard let c else { return .sent }
        if seq <= c.peerReadSeq { return .seen }
        if seq <= c.peerDeliveredSeq { return .delivered }
        return .sent
    }

    private func repaintTicks(_ chatId: Int) {
        guard let c = convs[chatId], var list = threads[chatId] else { return }
        var changed = false
        for i in list.indices where list[i].mine && list[i].id > 0 {
            guard let s = seqs[list[i].id] else { continue }
            let d = tick(for: s, c)
            if d != list[i].delivery {
                list[i].delivery = d
                changed = true
            }
        }
        if changed { threads[chatId] = list }
    }

    private func reactor(_ chatId: Int, mine: Bool, emoji: String) -> MsgReactor {
        if mine { return MsgReactor(name: "You", initials: "ME", color: 0x8B5CF6, emoji: emoji, mine: true) }
        let c = chat(chatId)
        return MsgReactor(name: c?.name ?? "", initials: c?.initials ?? "?", color: c?.color ?? 0x7C3AED, emoji: emoji)
    }

    private func replaceLocal(_ chatId: Int, _ localId: Int64, _ f: (inout ChatMessage) -> Void) {
        guard var list = threads[chatId], let i = list.firstIndex(where: { $0.id == localId }) else { return }
        f(&list[i])
        threads[chatId] = list
    }

    private func insertBySeq(_ list: inout [ChatMessage], _ m: ChatMessage) {
        let s = seqs[m.id] ?? Int64.max
        // Newest almost always lands last; walk back only past in-flight bubbles.
        var at = list.count
        while at > 0 {
            let prev = list[at - 1]
            let ps = prev.id < 0 ? Int64.max : (seqs[prev.id] ?? 0)
            if ps <= s { break }
            at -= 1
        }
        list.insert(m, at: at)
    }

    private func seqOf(_ chatId: Int, _ m: ChatMessage?) -> Int64 {
        if let m, let s = seqs[m.id] { return s }
        return convs[chatId]?.lastSeq ?? 0
    }

    private func bumpPreview(_ chatId: Int, _ text: String, lift: Bool = true) {
        guard let i = chats.firstIndex(where: { $0.id == chatId }) else { return }
        chats[i].preview = text
        chats[i].time = "now"
        chats[i].unread = 0
        chats[i].sortAt = Date().timeIntervalSince1970
        if lift, i > 0 { let row = chats.remove(at: i); chats.insert(row, at: 0) }
    }

    private func sortChats() {
        let sorted = chats.sorted { $0.sortAt > $1.sortAt }
        if sorted != chats { chats = sorted }
    }

    private func drop(_ chatId: Int) {
        chats.removeAll { $0.id == chatId }
        threads[chatId] = nil
        loaded.remove(chatId)
        convs[chatId] = nil
        if let conv = convOf.removeValue(forKey: chatId) { localOf[conv] = nil }
        pinned.remove(chatId)
        muted.remove(chatId)
        archived.remove(chatId)
    }

    private func nextId() -> Int {
        defer { nextChatId += 1 }
        return nextChatId
    }

    // MARK: - disappearing messages

    /// In "after seen" mode: `theirs` = the other side's messages up to
    /// `upToSeq` were just read here; otherwise mine were read over there.
    /// Either way they get the same one-minute fuse the server set.
    private func armExpiry(_ chatId: Int, upToSeq: Int64, theirs: Bool) {
        guard var list = threads[chatId] else { return }
        let at = Date().timeIntervalSince1970 + Self.seenFuse
        var changed = false
        for i in list.indices {
            let m = list[i]
            if m.id < 0 || m.expiresAt > 0 || m.mine == theirs { continue }
            guard let s = seqs[m.id], s <= upToSeq else { continue }
            list[i].expiresAt = at
            changed = true
        }
        if changed { threads[chatId] = list }
    }

    private func expiryLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: UInt64(Self.expiryTick * 1_000_000_000))
            let now = Date().timeIntervalSince1970
            for (chatId, list) in threads {
                let kept = list.filter { !($0.expiresAt > 0 && $0.expiresAt < now) }
                if kept.count != list.count {
                    threads[chatId] = kept
                    bumpPreview(chatId, kept.last.map { preview(of: $0) } ?? "", lift: false)
                }
            }
        }
    }

    // MARK: - formatting

    private func clock(_ epoch: TimeInterval) -> String {
        epoch <= 0 ? "" : stamp.string(from: Date(timeIntervalSince1970: epoch))
    }

    private func ago(_ epoch: TimeInterval) -> String {
        guard epoch > 0 else { return "" }
        let d = Int(max(0, Date().timeIntervalSince1970 - epoch))
        switch d {
        case ..<60: return "now"
        case ..<3600: return "\(d / 60)m"
        case ..<86_400: return "\(d / 3600)h"
        case ..<(7 * 86_400): return "\(d / 86_400)d"
        default: return "\(d / (7 * 86_400))w"
        }
    }

    // MARK: - constants and pure helpers

    private static let typingEvery: TimeInterval = 3
    private static let typingIdle: TimeInterval = 4
    private static let typingShow: TimeInterval = 6
    private static let searchDebounce: TimeInterval = 0.18
    private static let maxSendAttempts = 4
    private static let vanishSeen = -1
    private static let seenFuse: TimeInterval = 60
    private static let expiryTick: TimeInterval = 5
    private static let messageWindow = 200

    static func vanishOf(_ seconds: Int) -> VanishTimer {
        if seconds == 0 { return .off }
        if seconds < 0 { return .seen }
        return seconds <= 24 * 3600 ? .hours : .days
    }

    private static func trim(_ list: [ChatMessage]) -> [ChatMessage] {
        list.count > messageWindow ? Array(list.suffix(messageWindow)) : list
    }

    private static func initials(_ name: String) -> String {
        let parts = name.split(whereSeparator: \.isWhitespace).map(String.init)
        switch parts.count {
        case 0: return "?"
        case 1:
            let s = String(parts[0].filter { $0.isLetter || $0.isNumber }.prefix(2)).uppercased()
            return s.isEmpty ? "?" : s
        default: return (String(parts[0].prefix(1)) + String(parts[1].prefix(1))).uppercased()
        }
    }

    private static let palette: [UInt32] = [0xEC4899, 0x0EA5E9, 0xF59E0B, 0x10B981, 0x8B5CF6, 0x7C3AED, 0xEF4444, 0x3B82F6]

    private static func color(for id: Int64) -> UInt32 {
        let mixed = UInt64(bitPattern: id) ^ (UInt64(bitPattern: id) >> 32)
        return palette[Int(mixed % UInt64(palette.count))]
    }

    private static func wireKind(_ k: MsgKind) -> String {
        switch k {
        case .text: return "text"
        case .image, .ai: return "image"
        case .video: return "video"
        case .voice: return "voice"
        case .contact: return "contact"
        case .location: return "location"
        case .document: return "document"
        case .poll: return "poll"
        case .event: return "event"
        case .system: return "system"
        }
    }

    private static func localKind(_ k: String) -> MsgKind {
        switch k {
        case "image": return .image
        case "video": return .video
        case "voice": return .voice
        case "contact": return .contact
        case "location": return .location
        case "document": return .document
        case "poll": return .poll
        case "event": return .event
        case "system": return .system
        default: return .text
        }
    }

    private static func isUpload(_ k: MsgKind) -> Bool {
        k == .image || k == .video || k == .voice || k == .document || k == .ai
    }

    private static func mime(for file: URL, kind: MsgKind) -> String {
        switch file.pathExtension.lowercased() {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "heic": return "image/heic"
        case "webp": return "image/webp"
        case "gif": return "image/gif"
        case "mp4": return "video/mp4"
        case "mov": return "video/quicktime"
        case "m4a", "aac": return "audio/mp4"
        case "mp3": return "audio/mpeg"
        case "wav": return "audio/wav"
        case "pdf": return "application/pdf"
        default:
            switch kind {
            case .image, .ai: return "image/jpeg"
            case .video: return "video/mp4"
            case .voice: return "audio/mp4"
            default: return "application/octet-stream"
            }
        }
    }
}

func preview(of msg: ChatMessage) -> String {
    switch msg.kind {
    case .image: return msg.text.isEmpty ? "📷 Photo" : msg.text
    case .video: return msg.text.isEmpty ? "🎥 Video" : msg.text
    case .voice: return "🎤 \(msg.text.isEmpty ? "Voice message" : msg.text)"
    case .contact: return msg.text.isEmpty ? "👤 Contact" : msg.text
    case .location: return "📍 \(msg.text.isEmpty ? "Location" : msg.text)"
    case .document: return msg.text.isEmpty ? "📄 Document" : msg.text
    case .poll: return "📊 \(msg.text)"
    case .event: return "📅 \(msg.text)"
    case .ai: return msg.text.isEmpty ? "✨ AI image" : msg.text
    case .system:
        if let call = parseCallMsg(msg.text) {
            let label = call.video ? "Video call" : "Voice call"
            switch call.status {
            case "ongoing": return "📞 \(label) · Ongoing"
            case "declined": return "📞 Declined \(label.lowercased())"
            case "missed": return "📞 Missed \(label.lowercased())"
            case "cancelled": return "📞 Cancelled \(label.lowercased())"
            case "busy": return "📞 Busy"
            default:
                return call.durationSec > 0 ? "📞 \(label) · \(formatCallClock(call.durationSec))" : "📞 \(label)"
            }
        }
        return msg.text.isEmpty ? "Update" : msg.text
    case .text: return msg.text
    }
}

struct EventPayload {
    var name: String
    var desc: String
    var at: TimeInterval
    var loc: String
    var reminder: Int
}

func encodeEvent(_ e: EventPayload) -> String {
    [e.name, e.desc.replacingOccurrences(of: "\n", with: " "), String(Int64(e.at * 1000)), e.loc.replacingOccurrences(of: "\n", with: " "), String(e.reminder)].joined(separator: "\n")
}

func parseEvent(_ text: String) -> EventPayload {
    let p = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
    let ms = Double(p.count > 2 ? p[2] : "0") ?? 0
    return EventPayload(name: p.first ?? "", desc: p.count > 1 ? p[1] : "", at: ms / 1000, loc: p.count > 3 ? p[3] : "", reminder: Int(p.count > 4 ? p[4] : "60") ?? 60)
}

func pollQuestion(_ text: String) -> String { text.split(whereSeparator: \.isNewline).first.map(String.init) ?? "" }

func pollOptions(_ text: String) -> [String] {
    text.split(whereSeparator: \.isNewline).dropFirst().map(String.init).filter { !$0.isEmpty && $0 != "#multi" }
}

func pollMulti(_ text: String) -> Bool { text.split(whereSeparator: \.isNewline).contains { $0 == "#multi" } }

private let chatLinkRegex = try! NSRegularExpression(pattern: #"(?i)((?:https?://|www\.)[^\s<>"]+)"#)

func linkedChatText(_ text: String, base: Color, link: Color) -> AttributedString {
    var out = AttributedString(text)
    out.foregroundColor = base
    let ns = text as NSString
    chatLinkRegex.matches(in: text, range: NSRange(location: 0, length: ns.length)).forEach { m in
        guard let range = Range(m.range, in: text) else { return }
        let raw = String(text[range]).trimmingCharacters(in: CharacterSet(charactersIn: ".,;)]!?"))
        let href = raw.lowercased().hasPrefix("http") ? raw : "https://\(raw)"
        guard let attrRange = Range(m.range, in: out) else { return }
        out[attrRange].foregroundColor = link
        out[attrRange].underlineStyle = .single
        out[attrRange].link = URL(string: href)
    }
    return out
}

func openMaps(_ text: String) {
    let raw = text.replacingOccurrences(of: "📍 ", with: "")
    let q = raw.hasPrefix("http") ? raw : "https://maps.google.com/?q=\(raw.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? raw)"
    if let url = URL(string: q) { UIApplication.shared.open(url) }
}

func openCalendar(_ e: EventPayload) {
    let name = e.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? e.name
    let loc = e.loc.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? e.loc
    let desc = e.desc.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? e.desc
    let df = DateFormatter(); df.dateFormat = "yyyyMMdd'T'HHmmss'Z'"; df.timeZone = TimeZone(secondsFromGMT: 0)
    let start = df.string(from: Date(timeIntervalSince1970: e.at))
    if let url = URL(string: "https://calendar.google.com/calendar/render?action=TEMPLATE&text=\(name)&dates=\(start)/\(start)&details=\(desc)&location=\(loc)") {
        UIApplication.shared.open(url)
    }
}

/// The notification lanes are still seeded until the activity service lands;
/// chats come from the server.
enum InboxCatalog {
    struct Seed {
        let activity: [ActivityItem]
        let followers: [FollowNotice]
        let system: [SystemNotice]
    }

    static func load(_ app: String) -> Seed {
        let official = "@\(app.lowercased())_official"
        return Seed(
            activity: [
                ActivityItem(id: 5, type: "system", user: app, handle: official, msg: "Welcome to \(app)! Your For You feed is ready.", time: "now", initials: String(app.prefix(1)).uppercased(), color: 0x8B5CF6),
            ],
            followers: [],
            system: [],
        )
    }
}
