package app.xani.feature.inbox

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.xani.core.isolate.runFeature
import app.xani.core.net.ChatEvent
import app.xani.core.net.ChatMessageData
import app.xani.core.net.ConversationData
import app.xani.core.net.LiveFeed
import app.xani.core.net.MediaConfig
import app.xani.core.net.NoticeData
import app.xani.core.net.Perm
import app.xani.core.net.PersonData
import app.xani.core.net.Xani
import app.xani.core.result.Envelope
import app.xani.feature.profile.ProfileStore
import app.xani.i18n.Copy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The inbox, backed by the chat service.
 *
 * Every public call the screens make is synchronous and repaints state at once
 * (optimistic UI); the network round trip runs behind it and the server's row
 * replaces the guess when the ack lands. Delivery to the other side arrives on
 * the shared realtime stream ([LiveFeed.chat]) and patches the same state, so
 * a message, a tick, a reaction or "online" shows up on every surface from one
 * event, with no reload.
 *
 * Ids: the screens key chats by `Int` and messages by `Long`. Conversations are
 * 64-bit snowflakes on the wire, so each gets a small local id for the session
 * ([localId]); message ids are the server's, with a negative local id while a
 * send is in flight.
 *
 * One instance per process ([shared]); the screen re-attaches to it.
 */
class InboxController private constructor(seed: InboxSnapshot) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val localClock = AtomicLong(-1L)
    private val nextChatId = AtomicInteger(1)
    private val stamp = SimpleDateFormat("HH:mm", Locale.US)

    val chats = mutableStateListOf<InboxChat>()
    val activity = seed.activity
    val followers = seed.followers
    val system = mutableStateListOf<SystemNotice>()

    /** The people the user talks to most, for the story rail. */
    val stories: List<InboxChat> get() = chats.filter { !it.group }.take(4)

    private val threads = mutableMapOf<Int, SnapshotStateList<ChatMessage>>()

    /** local chat id ⇄ server conversation id. */
    private val convOf = mutableMapOf<Int, Long>()
    private val localOf = mutableMapOf<Long, Int>()

    /** Last thing the server told us about each chat, by local id. */
    private val convs = mutableMapOf<Int, ConversationData>()

    /** Chats whose history has been read from the server at least once. */
    private val loaded = mutableSetOf<Int>()

    /** Sends in flight, by client id, so a stream echo or a retry can find them. */
    private val outbox = mutableMapOf<String, Pending>()

    private data class Pending(val chatId: Int, val localId: Long, val job: Job?)

    val pinned = mutableStateOf(emptySet<Int>())
    val muted = mutableStateOf(emptySet<Int>())
    val archived = mutableStateOf(emptySet<Int>())
    val nicknames = mutableStateMapOf<String, String>()
    val themes = mutableStateMapOf<Int, Int>()
    val wallpapers = mutableStateMapOf<Int, String>()
    val bubbleColors = mutableStateMapOf<Int, Long>()

    /**
     * The disappearing rule of the chat on screen. Reading it paints the
     * current chat's value; writing it (the UI assigns `.value` directly) saves
     * the rule for that chat on the server, for both sides.
     */
    private val vanishState = HookedState(VanishTimer.Off) { setVanish(it) }
    val vanish: MutableState<VanishTimer> get() = vanishState
    val mediaVisibility = mutableStateOf(true)
    val readReceipts = mutableStateOf(true)
    val followingBack = mutableStateOf(emptySet<String>())
    val myStory = mutableStateOf<String?>(null)

    /** Bumps on every thread mutation; screens that derive lists key on it. */
    val seq = mutableLongStateOf(0L)
    val pollMine = mutableStateMapOf<Long, Set<Int>>()
    val pollTally = mutableStateMapOf<Long, List<Int>>()

    /** Peers typing right now, by local chat id. */
    val typing = mutableStateOf(emptySet<Int>())

    /** Search hits for the "+" screen, as chat rows (unbound until picked). */
    val peopleResults = mutableStateOf<List<InboxChat>>(emptyList())

    /**
     * A chat another tab asked for — "Message" on somebody's profile. The shell
     * moves to Inbox, the inbox opens this thread and clears it. Kept here
     * rather than passed between screens because the two surfaces live on
     * different tabs and neither owns the other.
     */
    val openRequest = mutableStateOf<Int?>(null)

    /** A notification tap for a post (comment / reply / mention): the inbox shows it. */
    val postRequest = mutableStateOf<Long?>(null)

    /** A notification tap for an account notice: the inbox opens the notices lane. */
    val noticesRequest = mutableStateOf(false)

    /** Notices above the server's read boundary — the Notifications tab badge. */
    val noticesUnread = mutableStateOf(0)

    /** Set while the inbox has never been read from the server this session. */
    val loading = mutableStateOf(true)

    /** Non-empty after a send or an action the server refused; the UI shows it once. */
    val notice = mutableStateOf("")

    private var opened: Int? = null
    private var live: Job? = null
    private var searchJob: Job? = null
    private var typingJob: Job? = null
    private var typingSentAt = 0L
    private var userId = 0L
    private var appContext: Context? = null

    val unreadTotal: Int
        get() {
            val mute = muted.value
            return chats.sumOf { if (it.id in mute) 0 else it.unread } + noticesUnread.value
        }

    // ─── lifecycle ───────────────────────────────────────────────────────────

    /** Binds to the signed-in account and starts listening. Idempotent. */
    fun attach(context: Context) {
        appContext = context.applicationContext
        adoptAccount()
        if (live?.isActive != true) {
            live = scope.launch {
                launch { LiveFeed.chat.collect { onEvent(it) } }
                launch { LiveFeed.connected.collectLatest { up -> if (up) resync() } }
                // The shell attaches before the profile has necessarily landed,
                // so the account may still be unknown here. The stream's first
                // frame is the profile: take the id from it rather than leaving
                // every "is this mine?" test answering no.
                launch { LiveFeed.profile.collect { adoptAccount() } }
                launch { LiveFeed.notices.collect { prependNotice(it) } }
                launch { expiryLoop() }
            }
        }
        if (loading.value) refresh()
    }

    private fun adoptAccount() {
        val me = ProfileStore.currentUserId
        if (me == 0L || me == userId) return
        // Never through reset(): this runs inside the live job, which reset()
        // cancels — the listener would take itself down mid-switch.
        if (userId != 0L) clearState()
        userId = me
        refresh()
    }

    /** Signed-in id, refreshed from the profile store if we attached early. */
    private fun me(): Long {
        if (userId == 0L) {
            val id = ProfileStore.currentUserId
            if (id != 0L) userId = id
        }
        return userId
    }

    /** Holds the realtime socket for as long as the caller's coroutine lives. */
    suspend fun observeLive(): Nothing = LiveFeed.hold()

    fun signedOut() {
        live?.cancel()
        live = null
        clearState()
        userId = 0L
    }

    private fun clearState() {
        outbox.values.forEach { it.job?.cancel() }
        outbox.clear()
        chats.clear()
        threads.clear()
        convOf.clear()
        localOf.clear()
        convs.clear()
        loaded.clear()
        pinned.value = emptySet()
        muted.value = emptySet()
        archived.value = emptySet()
        typing.value = emptySet()
        peopleResults.value = emptyList()
        openRequest.value = null
        noticesUnread.value = 0
        loading.value = true
        opened = null
        openConvId = 0L
        pendingConv = 0L
    }

    /**
     * Server id of the thread on screen, read by the push service (another
     * thread) to skip a banner for the chat the user is already reading.
     */
    @Volatile var openConvId: Long = 0L
        private set

    private var pendingConv = 0L

    /**
     * A notification tap for a server conversation. Opens it now if the inbox
     * knows it; otherwise after the next inbox read lands.
     */
    fun requestConversation(convId: Long) {
        if (convId <= 0L) return
        val local = localOf[convId]
        if (local != null) {
            pendingConv = 0L
            openRequest.value = local
            return
        }
        pendingConv = convId
        refresh()
    }

    private fun flushPendingConversation() {
        val want = pendingConv
        if (want == 0L) return
        localOf[want]?.let {
            pendingConv = 0L
            openRequest.value = it
        }
    }

    /** Re-reads the inbox page. Cheap: one indexed read per 50 chats. */
    fun refresh() {
        scope.launch {
            // Chat can be switched off for this account (role or direct deny).
            // The notifications lane still has to load — warnings and system
            // notices live there — so only the conversation read is skipped.
            if (!Xani.session.can(Perm.Chat)) {
                loading.value = false
                loadNotices()
                return@launch
            }
            val r = Xani.chat.inbox()
            val page = r.value ?: return@launch
            page.items.forEach { absorb(it, bump = false) }
            // Chats the server no longer lists (deleted on another device) go.
            val present = page.items.map { it.id }.toSet()
            if (page.next.isEmpty()) {
                chats.filter { !it.group && it.peerId != 0L && convOf[it.id]?.let { c -> c !in present } == true }
                    .forEach { drop(it.id) }
            }
            sortChats()
            loading.value = false
            flushPendingConversation()
            loadNotices()
        }
    }

    private fun loadNotices() {
        scope.launch {
            val page = runFeature(null) { Xani.stories.notices() }?.value ?: return@launch
            system.clear()
            system.addAll(page.items.map { it.toRow() })
            noticesUnread.value = runFeature(0) { Xani.stories.noticesUnread().value ?: 0 }
        }
    }

    private fun prependNotice(n: NoticeData) {
        val id = n.id.toString()
        if (system.any { it.id == id }) return
        system.add(0, n.toRow())
        noticesUnread.value += 1
        // A role decision or change means this account's memberships moved:
        // re-read identity so the profile role title updates without a restart.
        if (n.kind == "role_decision" || n.kind == "role_change") {
            scope.launch { runFeature(Unit) { Xani.session.resync() } }
        }
    }

    /** The Notifications lane is on screen: clear the badge here and on the server. */
    fun markNoticesSeen() {
        if (noticesUnread.value == 0) return
        noticesUnread.value = 0
        scope.launch { runFeature(Unit) { Xani.stories.noticesSeen() } }
    }

    private fun NoticeData.toRow(): SystemNotice {
        val whenMs = if (createdMs > 0L) createdMs else System.currentTimeMillis()
        return SystemNotice(
            id = id.toString(),
            kind = kind,
            title = title,
            msg = body,
            time = stamp.format(Date(whenMs)),
            refId = refId,
        )
    }

    /** After a reconnect: refresh the list and pull what the open thread missed. */
    private fun resync() {
        refresh()
        // The socket is back, so the network is: every queued send gets another
        // go, not just the one in the thread on screen.
        outbox.values.map { it.chatId }.distinct().forEach { flushOutbox(it) }
        val id = opened ?: return
        val conv = convOf[id] ?: return
        val last = thread(id).lastOrNull { it.id > 0 }
        scope.launch {
            val r = Xani.chat.messages(conv, afterSeq = seqOf(id, last), limit = 100)
            r.value?.sortedBy { it.seq }?.forEach { absorbMessage(it) }
        }
    }

    // ─── reads the screens use ───────────────────────────────────────────────

    fun thread(chatId: Int): SnapshotStateList<ChatMessage> {
        val list = threads.getOrPut(chatId) { mutableStateListOf() }
        if (chatId !in loaded && convOf[chatId] != null) loadHistory(chatId)
        return list
    }

    fun chat(id: Int): InboxChat? = chats.firstOrNull { it.id == id }

    /** The server conversation behind a row; null while the row is still being bound. */
    fun conversationOf(chatId: Int): Long? = convOf[chatId]

    /** The row for a server conversation, if the inbox has it. */
    fun chatForConversation(conversationId: Long): InboxChat? =
        convOf.entries.firstOrNull { it.value == conversationId }?.let { chat(it.key) }

    /**
     * Whether anything said in this chat contains [q].
     *
     * Only the window already in memory is searched — the point is to find the
     * conversation, and the thread's own search takes it from there. It runs
     * per keystroke over a bounded list, so it never touches the network.
     */
    fun mentions(chatId: Int, q: String): Boolean {
        if (q.isBlank()) return false
        val list = threads[chatId] ?: return false
        return list.any { it.text.contains(q, ignoreCase = true) }
    }

    fun displayName(chat: InboxChat): String =
        nicknames[chat.handle]?.takeIf { it.isNotBlank() } ?: chat.name

    fun themeOf(chatId: Int): Int = (themes[chatId] ?: 0).coerceIn(ChatThemes.indices)

    /** "Online", "typing…", or "Last seen 5m ago" for the thread header. */
    fun presence(chat: InboxChat, copy: Copy): String = when {
        chat.id in typing.value -> copy.t(Copy.InboxTyping)
        chat.online -> copy.t(Copy.InboxOnline)
        chat.lastSeenAt > 0 -> copy.t(Copy.InboxLastSeen, "t" to ago(chat.lastSeenAt, copy))
        else -> copy.t(Copy.InboxOffline)
    }

    /** When the other side read [msg]; empty if not yet. */
    fun seenAt(chatId: Int, msg: ChatMessage): String {
        val c = convs[chatId] ?: return ""
        val s = seqs[msg.id] ?: return ""
        return if (s <= c.peerReadSeq && c.peerReadAt > 0) clock(c.peerReadAt) else ""
    }

    /** When the other side's phone received [msg]; empty if not yet. */
    fun deliveredAt(chatId: Int, msg: ChatMessage): String {
        val c = convs[chatId] ?: return ""
        val s = seqs[msg.id] ?: return ""
        return if (s <= c.peerDeliveredSeq && c.peerDeliveredAt > 0) clock(c.peerDeliveredAt) else ""
    }

    /** The screen tells us which thread is on screen so reads are acked as they land. */
    fun opened(chatId: Int?) {
        opened = chatId
        openConvId = chatId?.let { convOf[it] } ?: 0L
        if (chatId != null) {
            vanishState.paint(vanishOf(convs[chatId]?.vanishSeconds ?: 0))
            markRead(chatId)
            // A thread opened from search may not have a server id yet.
            ensureBound(chatId)
            // Anything that ran out of retries while the app was backgrounded is
            // still on screen with a clock on it. Opening the thread is the
            // moment the user expects it to go: try again now.
            flushOutbox(chatId)
        } else {
            stopTyping()
        }
    }

    // ─── chat-level actions ──────────────────────────────────────────────────

    fun pin(id: Int) {
        val on = id !in pinned.value
        pinned.value = if (on) pinned.value + id else pinned.value - id
        settings(id, pinned = on)
    }

    fun mute(id: Int) {
        val on = id !in muted.value
        muted.value = if (on) muted.value + id else muted.value - id
        settings(id, muted = on)
    }

    fun archive(id: Int) {
        val on = id !in archived.value
        archived.value = if (on) archived.value + id else archived.value - id
        settings(id, archived = on)
    }

    private fun settings(id: Int, pinned: Boolean? = null, muted: Boolean? = null, archived: Boolean? = null) {
        val conv = convOf[id] ?: return
        scope.launch {
            val r = Xani.chat.settings(conv, pinned = pinned, muted = muted, archived = archived)
            r.value?.let { absorb(it, bump = false) }
        }
    }

    private fun setVanish(timer: VanishTimer) {
        val id = opened ?: return
        val conv = convOf[id] ?: return
        val seconds = when (timer) {
            VanishTimer.Off -> 0
            VanishTimer.Seen -> -1
            VanishTimer.Hours -> 24 * 3600
            VanishTimer.Days -> 7 * 24 * 3600
        }
        convs[id]?.let { convs[id] = it.copy(vanishSeconds = seconds) }
        scope.launch {
            val r = Xani.chat.settings(conv, vanishSeconds = seconds)
            r.value?.let { absorb(it, bump = false) }
        }
    }

    fun markRead(id: Int) {
        val i = chats.indexOfFirst { it.id == id }
        if (i >= 0 && chats[i].unread != 0) chats[i] = chats[i].copy(unread = 0)
        val conv = convOf[id] ?: return
        val c = convs[id] ?: return
        if (c.readSeq >= c.lastSeq) return
        convs[id] = c.copy(readSeq = c.lastSeq, deliveredSeq = c.lastSeq)
        if (c.vanishSeconds == VANISH_SEEN) armExpiry(id, upToSeq = c.lastSeq, theirs = true)
        scope.launch {
            val r = Xani.chat.ack(conv, deliveredSeq = c.lastSeq, readSeq = c.lastSeq)
            r.value?.let { absorb(it, bump = false) }
        }
    }

    fun follow(handle: String) {
        followingBack.value = followingBack.value + handle
    }

    /** Clears the history for this user only. */
    fun clear(chatId: Int) {
        thread(chatId).clear()
        bumpPreview(chatId, "")
        val conv = convOf[chatId] ?: return
        scope.launch { Xani.chat.clear(conv).value?.let { absorb(it, bump = false) } }
    }

    /** Removes the chat from this user's list. It returns, empty, on the next message. */
    fun delete(chatId: Int) {
        val conv = convOf[chatId]
        drop(chatId)
        if (conv != null) scope.launch { Xani.chat.delete(conv) }
    }

    /** Blocks (or unblocks) the other person; their sends fail at the server from now on. */
    fun block(chatId: Int, on: Boolean = true) {
        val i = chats.indexOfFirst { it.id == chatId }
        if (i < 0) return
        val peer = chats[i].peerId
        if (peer == 0L) return
        chats[i] = chats[i].copy(blocked = on)
        scope.launch {
            val r = Xani.chat.block(peer, on)
            if (!r.ok) {
                val j = chats.indexOfFirst { it.id == chatId }
                if (j >= 0) chats[j] = chats[j].copy(blocked = !on)
                notice.value = r.message
            }
        }
    }

    /** Mirrors a block changed elsewhere (the Blocked accounts screen) onto the list. */
    fun peerBlocked(peerId: Long, on: Boolean) {
        val i = chats.indexOfFirst { !it.group && it.peerId == peerId }
        if (i >= 0 && chats[i].blocked != on) chats[i] = chats[i].copy(blocked = on)
    }

    /** Files a report on the other person, optionally about one message. */
    fun report(chatId: Int, msgId: Long = 0, reason: String = "") {
        val peer = chat(chatId)?.peerId ?: return
        val conv = convOf[chatId] ?: 0L
        scope.launch { Xani.chat.report(peer, reason, conv, if (msgId > 0) msgId else 0) }
    }

    /** Coalesced: the server hears "typing" at most every few seconds and "stopped" once. */
    fun typing(chatId: Int) {
        val conv = convOf[chatId] ?: return
        val now = System.currentTimeMillis()
        if (now - typingSentAt > TYPING_EVERY_MS) {
            typingSentAt = now
            scope.launch { Xani.chat.typing(conv, true) }
        }
        typingJob?.cancel()
        typingJob = scope.launch {
            delay(TYPING_IDLE_MS)
            typingSentAt = 0L
            Xani.chat.typing(conv, false)
        }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        typingJob = null
        if (typingSentAt == 0L) return
        typingSentAt = 0L
        val conv = opened?.let { convOf[it] } ?: return
        scope.launch { Xani.chat.typing(conv, false) }
    }

    // ─── people ──────────────────────────────────────────────────────────────

    /** Searches accounts by @username, email or name for the "+" screen. */
    fun searchPeople(q: String) {
        searchJob?.cancel()
        val query = q.trim()
        if (query.isEmpty()) {
            peopleResults.value = emptyList()
            return
        }
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val r = Xani.people.search(query)
            val hits = r.value ?: return@launch
            peopleResults.value = hits.filter { it.id != userId }.map { p ->
                chats.firstOrNull { c -> c.peerId == p.id } ?: rowFor(p, id = 0)
            }
        }
    }

    /** Opens (or creates) the direct chat with a person picked from search. */
    fun openDirect(person: InboxChat): InboxChat {
        chats.firstOrNull { !it.group && it.peerId != 0L && it.peerId == person.peerId }?.let { return it }
        val id = nextChatId.getAndIncrement()
        val chat = person.copy(id = id, preview = "", time = "", unread = 0)
        chats.add(0, chat)
        threads[id] = mutableStateListOf()
        if (person.peerId != 0L) bind(id, person.peerId)
        return chat
    }

    /**
     * "Message" on somebody's profile: finds or creates their chat and asks the
     * shell to show it. Returns nothing the caller has to act on — the request
     * is state, so the profile can close itself immediately.
     */
    fun requestChat(person: PersonData) {
        if (person.id == 0L || person.id == userId) return
        // The funnel for "Message" from every surface, so the permission is
        // checked once here as well as wherever the button is drawn.
        if (!Xani.session.can(Perm.Chat)) return
        val existing = chats.firstOrNull { !it.group && it.peerId == person.id }
        openRequest.value = (existing ?: openDirect(rowFor(person, id = 0))).id
    }

    /** The inbox has taken the request and opened the thread. */
    fun openRequestHandled() {
        openRequest.value = null
    }

    /**
     * Opens a chat by display name — the path a shared contact card takes.
     * The row appears at once; the account is looked up behind it.
     */
    fun openDirect(name: String): InboxChat {
        val n = name.trim()
        chats.firstOrNull { !it.group && it.name.equals(n, ignoreCase = true) }?.let { return it }
        val id = nextChatId.getAndIncrement()
        val chat = InboxChat(
            id = id,
            name = n.ifBlank { "Chat" },
            handle = "@" + n.lowercase(Locale.US).replace(Regex("[^a-z0-9_.]+"), "").take(24).ifBlank { "user" },
            preview = "",
            time = "",
            unread = 0,
            initials = initials(n),
            color = colorFor(n.hashCode().toLong()),
        )
        chats.add(0, chat)
        threads[id] = mutableStateListOf()
        scope.launch {
            val hit = Xani.people.search(n.removePrefix("@"), limit = 5).value
                ?.firstOrNull { it.id != userId && (it.username.equals(n.removePrefix("@"), true) || it.fullName.equals(n, true)) }
                ?: Xani.people.search(n.removePrefix("@"), limit = 1).value?.firstOrNull { it.id != userId }
            if (hit == null) {
                notice.value = "Couldn't find that account."
                return@launch
            }
            val i = chats.indexOfFirst { it.id == id }
            if (i >= 0) chats[i] = rowFor(hit, id).copy(preview = chats[i].preview, time = chats[i].time)
            bind(id, hit.id)
        }
        return chat
    }

    /** Groups stay on the device until the group service lands. */
    fun createGroup(name: String, members: List<InboxChat>): InboxChat {
        val id = nextChatId.getAndIncrement()
        val chat = InboxChat(
            id = id,
            name = name,
            handle = "group",
            preview = "You created this group",
            time = "now",
            unread = 0,
            initials = name.take(2).uppercase(Locale.US),
            color = 0xFF7C3AED,
            group = true,
            members = members.map { Member(it.name, it.handle, it.initials, it.color) },
        )
        chats.add(0, chat)
        threads[id] = mutableStateListOf()
        loaded.add(id)
        return chat
    }

    /** Resolves a local row to its server conversation, then flushes anything queued. */
    private fun bind(chatId: Int, peerId: Long) {
        scope.launch {
            val r = Xani.chat.open(peerId)
            val conv = r.value
            if (conv == null) {
                if (r.code == "blocked") notice.value = r.message
                else notice.value = r.message.ifBlank { "Couldn't open that chat." }
                failUnbound(chatId)
                return@launch
            }
            // The same conversation may already sit in the list under another
            // local id (opened from search while it existed offscreen).
            val existing = localOf[conv.id]
            if (existing != null && existing != chatId) {
                val moving = threads.remove(chatId).orEmpty()
                thread(existing).addAll(moving)
                chats.removeAll { it.id == chatId }
                outbox.replaceAll { _, p -> if (p.chatId == chatId) p.copy(chatId = existing) else p }
                absorb(conv, bump = false)
                flushOutbox(existing)
                return@launch
            }
            convOf[chatId] = conv.id
            localOf[conv.id] = chatId
            absorb(conv, bump = false)
            if (chatId !in loaded) loadHistory(chatId)
            flushOutbox(chatId)
        }
    }

    // ─── messages ────────────────────────────────────────────────────────────

    fun send(
        chatId: Int,
        text: String,
        kind: MsgKind = MsgKind.Text,
        mediaUri: String? = null,
        replyTo: Long? = null,
    ) {
        me() // must know who "mine" is before the ack lands
        val trimmed = text.trim()
        if (trimmed.isEmpty() && mediaUri == null && kind == MsgKind.Text) return
        val list = thread(chatId)
        val id = localClock.getAndDecrement()
        val clientId = UUID.randomUUID().toString().replace("-", "")
        val msg = ChatMessage(
            id = id,
            chatId = chatId,
            mine = true,
            from = "You",
            initials = "ME",
            color = 0xFF8B5CF6,
            text = trimmed,
            time = stamp.format(Date()),
            kind = kind,
            mediaUri = mediaUri,
            replyTo = replyTo,
            delivery = Delivery.Sending,
            clientId = clientId,
        )
        list.add(msg)
        trim(list)
        bumpPreview(chatId, previewOf(msg))
        touch()
        stopTyping()
        if (kind == MsgKind.Poll) {
            pollMine[id] = emptySet()
            pollTally[id] = List(pollOptions(trimmed).size) { 0 }
        }
        val chat = chat(chatId)
        if (chat?.group == true) {
            // Local-only until groups ship; show it as sent so the demo thread reads.
            replaceLocal(chatId, id) { it.copy(delivery = Delivery.Sent) }
            return
        }
        outbox[clientId] = Pending(chatId, id, null)
        // A brand-new chat may not have its server id yet: open it, then flush.
        // Without that the bubble sits on a clock forever.
        val conv = convOf[chatId]
        when {
            conv != null -> flushOutbox(chatId)
            (chat?.peerId ?: 0L) != 0L -> ensureBound(chatId)
            else -> {
                // No peer and no server id: nothing to deliver to. Surface it
                // instead of leaving a silent clock on the bubble.
                outbox.remove(clientId)
                replaceLocal(chatId, id) { it.copy(delivery = Delivery.Sent) }
                notice.value = "Couldn't reach that chat. Try again."
            }
        }
    }

    /** Opens the direct conversation when we know the peer but not the server id. */
    private fun ensureBound(chatId: Int) {
        val peer = chat(chatId)?.peerId ?: 0L
        if (peer == 0L) {
            failUnbound(chatId)
            return
        }
        if (convOf[chatId] != null) {
            flushOutbox(chatId)
            return
        }
        bind(chatId, peer)
    }

    /** Pending sends that can never leave the phone — drop the clock, tell the user. */
    private fun failUnbound(chatId: Int) {
        val stuck = outbox.filter { it.value.chatId == chatId }
        stuck.forEach { (clientId, p) ->
            outbox.remove(clientId)
            replaceLocal(chatId, p.localId) { it.copy(delivery = Delivery.Sent) }
        }
        if (stuck.isNotEmpty()) notice.value = "Couldn't open that chat. Try again."
    }

    private fun flushOutbox(chatId: Int) {
        val conv = convOf[chatId] ?: return
        outbox.filter { it.value.chatId == chatId && it.value.job == null }.forEach { (clientId, p) ->
            val msg = thread(chatId).firstOrNull { it.id == p.localId } ?: run { outbox.remove(clientId); return@forEach }
            // LAZY, then start: the scope dispatches on Main.immediate, so an
            // eager launch runs `deliver` inline and can finish — and clear the
            // outbox entry — before the assignment below, which would then put
            // a finished send back in the queue forever.
            val job = scope.launch(start = CoroutineStart.LAZY) {
                withContext(Dispatchers.IO) { deliver(conv, chatId, clientId, msg) }
            }
            outbox[clientId] = p.copy(job = job)
            job.start()
        }
    }

    /** Sends one message with idempotent retries; the server row replaces the guess. */
    private suspend fun deliver(conv: Long, chatId: Int, clientId: String, msg: ChatMessage) {
        me()
        val replyTo = msg.replyTo?.takeIf { it > 0 } ?: 0L
        var backoff = 1_000L
        var lastMessage = ""
        repeat(MAX_SEND_ATTEMPTS) { attempt ->
            val r = try {
                if (msg.mediaUri != null && msg.kind.isUpload) {
                    sendMedia(conv, clientId, msg, replyTo)
                } else {
                    Xani.chat.send(conv, clientId, msg.text.ifBlank { previewOf(msg) }, kind = wireKind(msg.kind), replyTo = replyTo)
                }
            } catch (t: Throwable) {
                lastMessage = t.message.orEmpty()
                if (attempt < MAX_SEND_ATTEMPTS - 1) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(15_000L)
                }
                return@repeat
            }
            val ack = r.value
            if (ack != null) {
                outbox.remove(clientId)
                // Keep mine=true and force a non-clock tick: fromWire alone can
                // paint "theirs" when the account id was still 0 at attach time.
                replaceLocal(chatId, msg.id) { local ->
                    val wire = fromWire(ack.message, chatId)
                    wire.copy(
                        mine = true,
                        from = "You",
                        initials = "ME",
                        color = 0xFF8B5CF6,
                        mediaUri = local.mediaUri ?: wire.mediaUri,
                        delivery = tickFor(ack.message.seq, convs[chatId]).takeIf { it != Delivery.Sending }
                            ?: Delivery.Sent,
                    )
                }
                absorb(ack.conversation, bump = false)
                return
            }
            lastMessage = r.message
            if (r.isSessionExpired) {
                outbox[clientId]?.let { outbox[clientId] = it.copy(job = null) }
                notice.value = "Session expired. Sign in again to send."
                return
            }
            if (r.statusCode in 400..499) {
                // The server refused (blocked, too long, not a member): no retry
                // will change that. Drop the bubble and tell the user once.
                outbox.remove(clientId)
                thread(chatId).removeAll { it.id == msg.id }
                touch()
                notice.value = r.message.ifBlank { "Couldn't send." }
                return
            }
            if (attempt < MAX_SEND_ATTEMPTS - 1) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000L)
            }
        }
        // Leave it as Sending; the next reconnect flushes the outbox again.
        outbox[clientId]?.let { outbox[clientId] = it.copy(job = null) }
        notice.value = lastMessage.ifBlank { "Still sending… check your connection." }
    }

    private suspend fun sendMedia(conv: Long, clientId: String, msg: ChatMessage, replyTo: Long) =
        withContext(Dispatchers.IO) {
            val ctx = appContext
            val uri = Uri.parse(msg.mediaUri)
            val (name, size) = ctx?.let { describe(it, uri) } ?: ("" to -1L)
            val mime = ctx?.contentResolver?.getType(uri) ?: defaultMime(msg.kind)
            // OpenableColumns.SIZE is often -1 for camera/capture Uris, and
            // InputStream.available() is a buffer hint — not the file length.
            // Without a real Content-Length the PUT is refused and the bubble
            // sits on a clock forever.
            val length = when {
                size > 0L -> size
                else -> ctx?.let { measure(it, uri) } ?: 0L
            }
            if (length <= 0L) {
                return@withContext app.xani.core.net.ApiResponse.failure<app.xani.core.net.SendAck>(422, "empty_file", "That file could not be read.")
            }
            Xani.chat.sendMedia(
                conversationId = conv,
                clientId = clientId,
                kind = wireKind(msg.kind),
                mime = mime,
                length = length,
                caption = if (msg.kind == MsgKind.Document) "" else msg.text,
                fileName = if (msg.kind == MsgKind.Document) msg.text.ifBlank { name } else name,
                durationMs = 0,
                replyTo = replyTo,
            ) { ctx?.contentResolver?.openInputStream(uri) }
        }

    fun patch(chatId: Int, msgId: Long, text: String) {
        val list = thread(chatId)
        val i = list.indexOfFirst { it.id == msgId }
        if (i < 0) return
        val next = text.trim()
        val cur = list[i]
        if (next == cur.text || next.isEmpty()) return
        list[i] = cur.copy(text = next, edited = true)
        bumpPreview(chatId, previewOf(list[i]), lift = false)
        touch()
        if (msgId > 0) scope.launch {
            val r = Xani.chat.edit(msgId, next)
            if (!r.ok && !r.isSessionExpired) {
                replaceLocal(chatId, msgId) { it.copy(text = cur.text, edited = cur.edited) }
                notice.value = r.message
            }
        }
    }

    fun votePoll(msgId: Long, index: Int, optionCount: Int, multi: Boolean) {
        if (index !in 0 until optionCount) return
        val mine = (pollMine[msgId] ?: emptySet()).toMutableSet()
        val counts = (pollTally[msgId] ?: List(optionCount) { 0 }).toMutableList()
        while (counts.size < optionCount) counts.add(0)
        if (multi) {
            if (index in mine) {
                mine.remove(index)
                counts[index] = (counts[index] - 1).coerceAtLeast(0)
            } else {
                mine.add(index)
                counts[index] += 1
            }
        } else {
            mine.forEach { counts[it] = (counts[it] - 1).coerceAtLeast(0) }
            mine.clear()
            mine.add(index)
            counts[index] += 1
        }
        pollMine[msgId] = mine
        pollTally[msgId] = counts
    }

    /** Toggles the signed-in user's reaction; one per person, as Instagram does it. */
    fun react(chatId: Int, msgId: Long, emoji: String) {
        val list = thread(chatId)
        val i = list.indexOfFirst { it.id == msgId }
        if (i < 0) return
        val cur = list[i]
        val mine = cur.reactors.firstOrNull { it.mine }
        val clearing = mine?.emoji == emoji
        val others = cur.reactors.filterNot { it.mine }
        val next = if (clearing) others else others + MsgReactor("You", "ME", 0xFF8B5CF6, emoji, mine = true)
        list[i] = cur.copy(reactors = next, reaction = next.lastOrNull()?.emoji)
        touch()
        if (msgId > 0) scope.launch { Xani.chat.react(msgId, if (clearing) "" else emoji) }
    }

    fun clearMineReaction(chatId: Int, msgId: Long) {
        val list = thread(chatId)
        val i = list.indexOfFirst { it.id == msgId }
        if (i < 0) return
        val next = list[i].reactors.filterNot { it.mine }
        list[i] = list[i].copy(reactors = next, reaction = next.lastOrNull()?.emoji)
        touch()
        if (msgId > 0) scope.launch { Xani.chat.react(msgId, "") }
    }

    /** Mine: unsent for everyone. Theirs: removed from this phone only. */
    fun unsend(chatId: Int, msgId: Long) {
        val list = thread(chatId)
        val msg = list.firstOrNull { it.id == msgId } ?: return
        list.removeAll { it.id == msgId }
        touch()
        if (msg.mine && msgId > 0) scope.launch { Xani.chat.unsend(msgId) }
        if (msg.mine && msgId < 0) {
            // Still in flight: cancel the send; if it already landed, the ack path
            // finds no bubble and the retry is skipped.
            outbox.entries.firstOrNull { it.value.localId == msgId }?.let { (k, p) -> p.job?.cancel(); outbox.remove(k) }
        }
        list.lastOrNull()?.let { bumpPreview(chatId, previewOf(it), lift = false) }
    }

    // ─── stream ──────────────────────────────────────────────────────────────

    private suspend fun onEvent(ev: ChatEvent) {
        when (ev) {
            is ChatEvent.Message -> absorbMessage(ev.message)
            is ChatEvent.Receipt -> {
                val id = localOf[ev.receipt.conversationId] ?: return
                val c = convs[id] ?: return
                if (ev.receipt.userId == me()) {
                    convs[id] = c.copy(readSeq = maxOf(c.readSeq, ev.receipt.readSeq), deliveredSeq = maxOf(c.deliveredSeq, ev.receipt.deliveredSeq))
                    return
                }
                convs[id] = c.copy(
                    peerReadSeq = maxOf(c.peerReadSeq, ev.receipt.readSeq),
                    peerReadAt = if (ev.receipt.readSeq > c.peerReadSeq) ev.receipt.at else c.peerReadAt,
                    peerDeliveredSeq = maxOf(c.peerDeliveredSeq, ev.receipt.deliveredSeq),
                    peerDeliveredAt = if (ev.receipt.deliveredSeq > c.peerDeliveredSeq) ev.receipt.at else c.peerDeliveredAt,
                )
                repaintTicks(id)
                if (c.vanishSeconds == VANISH_SEEN && ev.receipt.readSeq > 0) armExpiry(id, upToSeq = ev.receipt.readSeq, theirs = false)
            }
            is ChatEvent.Reaction -> {
                val id = localOf[ev.reaction.conversationId] ?: return
                val list = threads[id] ?: return
                val i = list.indexOfFirst { it.id == ev.reaction.messageId }
                if (i < 0) return
                val cur = list[i]
                val mine = ev.reaction.userId == me()
                val rest = cur.reactors.filterNot { if (mine) it.mine else !it.mine }
                val next = if (ev.reaction.emoji.isEmpty()) rest else rest + reactor(id, mine, ev.reaction.emoji)
                list[i] = cur.copy(reactors = next, reaction = next.lastOrNull()?.emoji)
                touch()
            }
            is ChatEvent.Typing -> {
                val id = localOf[ev.typing.conversationId] ?: return
                if (ev.typing.userId == me()) return
                typing.value = if (ev.typing.typing) typing.value + id else typing.value - id
                if (ev.typing.typing) scope.launch {
                    delay(TYPING_SHOW_MS)
                    typing.value = typing.value - id
                }
            }
            is ChatEvent.Presence -> {
                chats.indices.filter { chats[it].peerId == ev.presence.userId }.forEach { i ->
                    chats[i] = chats[i].copy(online = ev.presence.online, lastSeenAt = if (ev.presence.online) 0L else ev.presence.lastSeenAt)
                }
            }
            is ChatEvent.Conversation -> absorb(ev.conversation, bump = false)
            is ChatEvent.Gone -> localOf[ev.conversationId]?.let { drop(it) }
            ChatEvent.Resync -> resync()
        }
    }

    /** A message from the stream or a resume read: place it, ack it, repaint the row. */
    private suspend fun absorbMessage(m: ChatMessageData) {
        var id = localOf[m.conversationId]
        if (id == null) {
            // First word from someone new: fetch the row so the list has a name.
            val conv = Xani.chat.get(m.conversationId).value ?: return
            absorb(conv, bump = true)
            id = localOf[m.conversationId] ?: return
        }
        val list = thread(id)
        val mine = m.senderId == me()
        if (m.deleted) {
            if (list.removeAll { it.id == m.id }) touch()
            list.lastOrNull()?.let { bumpPreview(id, previewOf(it), lift = false) }
            return
        }
        val existing = list.indexOfFirst { it.id == m.id }
        val pendingIdx = if (mine) list.indexOfFirst { it.id < 0 && it.clientId == m.clientId } else -1
        val wire = fromWire(m, id)
        when {
            existing >= 0 -> list[existing] = wire.copy(mediaUri = list[existing].mediaUri ?: wire.mediaUri, reactors = list[existing].reactors, reaction = list[existing].reaction)
            pendingIdx >= 0 -> {
                list[pendingIdx] = wire.copy(
                    mine = true,
                    from = "You",
                    initials = "ME",
                    color = 0xFF8B5CF6,
                    mediaUri = list[pendingIdx].mediaUri ?: wire.mediaUri,
                    delivery = wire.delivery.takeIf { it != Delivery.Sending } ?: Delivery.Sent,
                )
                outbox.remove(m.clientId)?.job?.cancel()
            }
            id in loaded || list.isNotEmpty() -> insertBySeq(list, wire)
            else -> Unit // history not read yet; it arrives with the first page
        }
        trim(list)
        touch()
        val c = convs[id]
        if (c != null && m.seq > c.lastSeq) convs[id] = c.copy(lastSeq = m.seq)
        if (mine) {
            bumpPreview(id, previewOf(wire), lift = true)
            return
        }
        val preview = previewOf(wire)
        val onScreen = opened == id
        val i = chats.indexOfFirst { it.id == id }
        if (i >= 0) {
            val row = chats[i]
            chats[i] = row.copy(preview = preview, time = "now", unread = if (onScreen) 0 else row.unread + 1)
            if (i > 0) chats.add(0, chats.removeAt(i))
        }
        // Delivered the moment it is on the phone; read as well when the thread is open.
        val conv = m.conversationId
        val readSeq = if (onScreen) m.seq else 0L
        convs[id]?.let { convs[id] = it.copy(deliveredSeq = maxOf(it.deliveredSeq, m.seq), readSeq = if (onScreen) maxOf(it.readSeq, m.seq) else it.readSeq) }
        if (onScreen && c?.vanishSeconds == VANISH_SEEN) armExpiry(id, upToSeq = m.seq, theirs = true)
        scope.launch { Xani.chat.ack(conv, deliveredSeq = m.seq, readSeq = readSeq) }
    }

    private fun loadHistory(chatId: Int) {
        val conv = convOf[chatId] ?: return
        loaded.add(chatId)
        scope.launch {
            val r = Xani.chat.messages(conv, limit = MessageWindow)
            val page = r.value
            if (page == null) {
                loaded.remove(chatId)
                return@launch
            }
            val list = thread(chatId)
            val pending = list.filter { it.id < 0 }
            list.clear()
            page.sortedBy { it.seq }.forEach { list.add(fromWire(it, chatId)) }
            list.addAll(pending)
            touch()
            if (opened == chatId) markRead(chatId)
        }
    }

    // ─── shape mapping ───────────────────────────────────────────────────────

    /** Server row → list row. Assigns a local id on first sight. */
    private fun absorb(c: ConversationData, bump: Boolean) {
        val id = localOf[c.id] ?: nextChatId.getAndIncrement().also {
            localOf[c.id] = it
            convOf[it] = c.id
        }
        convs[id] = c
        val i = chats.indexOfFirst { it.id == id }
        val prev = if (i >= 0) chats[i] else null
        val row = InboxChat(
            id = id,
            name = c.peer.fullName.ifBlank { c.peer.username }.ifBlank { prev?.name ?: "User" },
            handle = "@" + c.peer.username.ifBlank { prev?.handle?.removePrefix("@") ?: "" },
            preview = c.last?.let { previewOf(fromWire(it, id)) } ?: prev?.preview.orEmpty(),
            time = if (c.last != null) ago(c.last.createdAt) else prev?.time.orEmpty(),
            unread = if (opened == id) 0 else c.unread.toInt(),
            initials = initials(c.peer.fullName.ifBlank { c.peer.username }),
            color = colorFor(c.peer.id),
            peerId = c.peer.id,
            avatarUrl = c.peer.avatarUrl,
            status = c.peer.status,
            online = c.peer.online,
            lastSeenAt = c.peer.lastSeenAt,
            blocked = c.peer.blocked,
            sortAt = c.sortAt,
        )
        if (i >= 0) chats[i] = row else chats.add(if (bump) 0 else chats.size, row)
        pinned.value = if (c.pinned) pinned.value + id else pinned.value - id
        muted.value = if (c.muted) muted.value + id else muted.value - id
        archived.value = if (c.archived) archived.value + id else archived.value - id
        if (opened == id) vanishState.paint(vanishOf(c.vanishSeconds))
        repaintTicks(id)
        if (bump) sortChats()
    }

    private fun rowFor(p: PersonData, id: Int) = InboxChat(
        id = id,
        name = p.fullName.ifBlank { p.username },
        handle = "@" + p.username,
        preview = "",
        time = "",
        unread = 0,
        initials = initials(p.fullName.ifBlank { p.username }),
        color = colorFor(p.id),
        peerId = p.id,
        avatarUrl = p.avatarUrl,
        status = p.status,
    )

    /** Message seq by message id, for tick and receipt lookups. */
    private val seqs = HashMap<Long, Long>()

    private fun fromWire(m: ChatMessageData, chatId: Int): ChatMessage {
        val mine = m.senderId == me()
        val chat = chat(chatId)
        seqs[m.id] = m.seq
        val c = convs[chatId]
        return ChatMessage(
            id = m.id,
            chatId = chatId,
            mine = mine,
            from = if (mine) "You" else chat?.name ?: "",
            initials = if (mine) "ME" else chat?.initials ?: "?",
            color = if (mine) 0xFF8B5CF6 else chat?.color ?: 0xFF7C3AED,
            text = if (m.kind == "document") m.mediaName.ifBlank { m.body } else m.body,
            time = clock(m.createdAt),
            kind = localKind(m.kind),
            mediaUri = m.mediaKey.takeIf { it.isNotEmpty() }?.let { MediaConfig.url(it) },
            replyTo = m.replyTo.takeIf { it > 0 },
            reaction = m.reactions.lastOrNull()?.emoji,
            reactors = m.reactions.map { reactor(chatId, it.userId == me(), it.emoji) },
            delivery = if (!mine) Delivery.Sent else tickFor(m.seq, c),
            edited = m.edited,
            clientId = m.clientId,
            expiresAt = m.expiresAt,
        )
    }

    private fun tickFor(seq: Long, c: ConversationData?): Delivery = when {
        c == null -> Delivery.Sent
        seq <= c.peerReadSeq -> Delivery.Seen
        seq <= c.peerDeliveredSeq -> Delivery.Delivered
        else -> Delivery.Sent
    }

    private fun repaintTicks(chatId: Int) {
        val c = convs[chatId] ?: return
        val list = threads[chatId] ?: return
        var changed = false
        for (i in list.indices) {
            val m = list[i]
            if (!m.mine || m.id < 0) continue
            val s = seqs[m.id] ?: continue
            val d = tickFor(s, c)
            if (d != m.delivery) {
                list[i] = m.copy(delivery = d)
                changed = true
            }
        }
        if (changed) touch()
    }

    private fun reactor(chatId: Int, mine: Boolean, emoji: String): MsgReactor {
        if (mine) return MsgReactor("You", "ME", 0xFF8B5CF6, emoji, mine = true)
        val c = chat(chatId)
        return MsgReactor(c?.name ?: "", c?.initials ?: "?", c?.color ?: 0xFF7C3AED, emoji)
    }

    private fun replaceLocal(chatId: Int, localId: Long, f: (ChatMessage) -> ChatMessage) {
        val list = thread(chatId)
        val i = list.indexOfFirst { it.id == localId }
        if (i < 0) return
        list[i] = f(list[i])
        touch()
    }

    private fun insertBySeq(list: SnapshotStateList<ChatMessage>, m: ChatMessage) {
        val s = seqs[m.id] ?: Long.MAX_VALUE
        // Newest almost always lands last; walk back only past in-flight bubbles.
        var at = list.size
        while (at > 0) {
            val prev = list[at - 1]
            val ps = if (prev.id < 0) Long.MAX_VALUE else seqs[prev.id] ?: 0L
            if (ps <= s) break
            at--
        }
        list.add(at, m)
    }

    private fun seqOf(chatId: Int, m: ChatMessage?): Long = m?.let { seqs[it.id] } ?: convs[chatId]?.lastSeq ?: 0L

    private fun bumpPreview(chatId: Int, preview: String, lift: Boolean = true) {
        val i = chats.indexOfFirst { it.id == chatId }
        if (i < 0) return
        chats[i] = chats[i].copy(preview = preview, time = "now", unread = 0, sortAt = System.currentTimeMillis())
        if (lift && i > 0) chats.add(0, chats.removeAt(i))
    }

    private fun sortChats() {
        val sorted = chats.sortedByDescending { it.sortAt }
        if (sorted != chats.toList()) {
            chats.clear()
            chats.addAll(sorted)
        }
    }

    private fun drop(chatId: Int) {
        chats.removeAll { it.id == chatId }
        threads.remove(chatId)
        loaded.remove(chatId)
        convs.remove(chatId)
        convOf.remove(chatId)?.let { localOf.remove(it) }
        pinned.value = pinned.value - chatId
        muted.value = muted.value - chatId
        archived.value = archived.value - chatId
        touch()
    }

    private fun trim(list: SnapshotStateList<ChatMessage>) {
        val extra = list.size - MessageWindow
        if (extra > 0) repeat(extra) { list.removeAt(0) }
    }

    private fun touch() {
        seq.longValue = seq.longValue + 1
    }

    // ─── disappearing messages ───────────────────────────────────────────────

    /**
     * In "after seen" mode: [theirs] = the other side's messages up to
     * [upToSeq] were just read here; otherwise mine were read over there.
     * Either way they get the same one-minute fuse the server set.
     */
    private fun armExpiry(chatId: Int, upToSeq: Long, theirs: Boolean) {
        val list = threads[chatId] ?: return
        val at = System.currentTimeMillis() + SEEN_FUSE_MS
        for (i in list.indices) {
            val m = list[i]
            if (m.id < 0 || m.expiresAt > 0 || m.mine == theirs) continue
            val s = seqs[m.id] ?: continue
            if (s <= upToSeq) list[i] = m.copy(expiresAt = at)
        }
    }

    private suspend fun expiryLoop() {
        while (scope.isActive) {
            delay(EXPIRY_TICK_MS)
            val now = System.currentTimeMillis()
            threads.forEach { (chatId, list) ->
                if (list.removeAll { it.expiresAt in 1 until now }) {
                    touch()
                    list.lastOrNull()?.let { bumpPreview(chatId, previewOf(it), lift = false) } ?: bumpPreview(chatId, "", lift = false)
                }
            }
        }
    }

    // ─── formatting ──────────────────────────────────────────────────────────

    private fun clock(epochMs: Long): String = if (epochMs <= 0) "" else stamp.format(Date(epochMs))

    private fun ago(epochMs: Long, copy: Copy? = null): String {
        if (epochMs <= 0) return ""
        val d = (System.currentTimeMillis() - epochMs).coerceAtLeast(0) / 1000
        return when {
            d < 60 -> copy?.t(Copy.InboxJustNow)?.lowercase(Locale.US) ?: "now"
            d < 3600 -> "${d / 60}m"
            d < 86_400 -> "${d / 3600}h"
            d < 7 * 86_400 -> "${d / 86_400}d"
            else -> "${d / (7 * 86_400)}w"
        }
    }

    /** A [MutableState] whose writes also run a hook — the UI assigns `.value` directly. */
    private class HookedState<T>(initial: T, private val onSet: (T) -> Unit) : MutableState<T> {
        private val inner = mutableStateOf(initial)
        override var value: T
            get() = inner.value
            set(v) {
                if (inner.value == v) return
                inner.value = v
                onSet(v)
            }

        /** Repaints from the server without firing the hook. */
        fun paint(v: T) {
            inner.value = v
        }

        override fun component1(): T = value
        override fun component2(): (T) -> Unit = { value = it }
    }

    companion object {
        private const val TYPING_EVERY_MS = 3_000L
        private const val TYPING_IDLE_MS = 4_000L
        private const val TYPING_SHOW_MS = 6_000L
        private const val SEARCH_DEBOUNCE_MS = 180L
        private const val MAX_SEND_ATTEMPTS = 4
        private const val VANISH_SEEN = -1
        private const val SEEN_FUSE_MS = 60_000L
        private const val EXPIRY_TICK_MS = 5_000L

        @Volatile
        private var instance: InboxController? = null

        /** The process-wide inbox, created on first use. */
        val shared: InboxController
            get() = instance ?: synchronized(this) {
                instance ?: InboxController(catalog("App")).also { instance = it }
            }

        /** Server id of the chat on screen, without creating the inbox. */
        fun openConversation(): Long = instance?.openConvId ?: 0L

        /** Main thread only: the chat list is snapshot state. */
        fun syncPeerBlocked(peerId: Long, on: Boolean) {
            instance?.peerBlocked(peerId, on)
        }

        fun seed(appName: String): Envelope<InboxSnapshot> = Envelope.ok(catalog(appName), cursor = "inbox:v1")

        /** Kept for the screen's existing call shape; the seed only fills the notification lanes. */
        fun from(envelope: Envelope<InboxSnapshot>): InboxController {
            val cur = instance
            if (cur != null) return cur
            return synchronized(this) {
                instance ?: InboxController(envelope.data ?: catalog("App")).also { instance = it }
            }
        }

        fun vanishOf(seconds: Int): VanishTimer = when {
            seconds == 0 -> VanishTimer.Off
            seconds < 0 -> VanishTimer.Seen
            seconds <= 24 * 3600 -> VanishTimer.Hours
            else -> VanishTimer.Days
        }

        private fun initials(name: String): String {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].filter { it.isLetterOrDigit() }.take(2).ifBlank { "?" }.uppercase(Locale.US)
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.US)
            }
        }

        private val palette = longArrayOf(0xFFEC4899, 0xFF0EA5E9, 0xFFF59E0B, 0xFF10B981, 0xFF8B5CF6, 0xFF7C3AED, 0xFFEF4444, 0xFF3B82F6)

        private fun colorFor(id: Long): Long = palette[((id xor (id ushr 32)) and 0x7fffffff).toInt() % palette.size]

        private fun wireKind(k: MsgKind): String = when (k) {
            MsgKind.Text -> "text"
            MsgKind.Image, MsgKind.Ai -> "image"
            MsgKind.Video -> "video"
            MsgKind.Voice -> "voice"
            MsgKind.Contact -> "contact"
            MsgKind.Location -> "location"
            MsgKind.Document -> "document"
            MsgKind.Poll -> "poll"
            MsgKind.Event -> "event"
            MsgKind.System -> "system"
        }

        private fun localKind(k: String): MsgKind = when (k) {
            "image" -> MsgKind.Image
            "video" -> MsgKind.Video
            "voice" -> MsgKind.Voice
            "contact" -> MsgKind.Contact
            "location" -> MsgKind.Location
            "document" -> MsgKind.Document
            "poll" -> MsgKind.Poll
            "event" -> MsgKind.Event
            "system" -> MsgKind.System
            else -> MsgKind.Text
        }

        private fun defaultMime(k: MsgKind) = when (k) {
            MsgKind.Image, MsgKind.Ai -> "image/jpeg"
            MsgKind.Video -> "video/mp4"
            MsgKind.Voice -> "audio/mp4"
            else -> "application/octet-stream"
        }

        private val MsgKind.isUpload: Boolean
            get() = this == MsgKind.Image || this == MsgKind.Video || this == MsgKind.Voice || this == MsgKind.Document || this == MsgKind.Ai

        /** Display name and byte size of a content Uri, without reading it. */
        private fun describe(context: Context, uri: Uri): Pair<String, Long> = runFeature("" to -1L) {
            if (uri.scheme == "file") {
                val f = java.io.File(uri.path.orEmpty())
                return@runFeature f.name to f.length()
            }
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@runFeature "" to -1L
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }.orEmpty()
                val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) } ?: -1L
                n to s
            } ?: ("" to -1L)
        }

        /** Byte length when the provider refuses to report SIZE (camera captures). */
        private fun measure(context: Context, uri: Uri): Long = runFeature(0L) {
            if (uri.scheme == "file") {
                return@runFeature java.io.File(uri.path.orEmpty()).length()
            }
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                val len = fd.length
                if (len > 0L) return@runFeature len
            }
            // Last resort: count the stream. Chat videos are short; this stays
            // on Dispatchers.IO and never touches the UI thread.
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
                total
            } ?: 0L
        }
    }
}

internal fun previewOf(msg: ChatMessage): String = when (msg.kind) {
    MsgKind.Image -> msg.text.ifBlank { "📷 Photo" }
    MsgKind.Video -> msg.text.ifBlank { "🎥 Video" }
    MsgKind.Voice -> "🎤 ${msg.text.ifBlank { "Voice message" }}"
    MsgKind.Contact -> if (msg.text.isNotBlank()) msg.text else "👤 Contact"
    MsgKind.Location -> "📍 ${msg.text.ifBlank { "Location" }}"
    MsgKind.Document -> if (msg.text.isNotBlank()) msg.text else "📄 Document"
    MsgKind.Poll -> "📊 ${pollQuestion(msg.text)}"
    MsgKind.Event -> "📅 ${msg.text.lineSequence().firstOrNull().orEmpty()}"
    MsgKind.Ai -> msg.text.ifBlank { "✨ AI image" }
    MsgKind.System -> callPreview(msg.text) ?: msg.text.ifBlank { "Update" }
    MsgKind.Text -> msg.text
}

private fun callPreview(text: String): String? {
    val call = parseCallMsg(text) ?: return null
    val label = if (call.video) "Video call" else "Voice call"
    return when (call.status) {
        "ongoing" -> "📞 $label · Ongoing"
        "declined" -> "📞 Declined ${label.lowercase()}"
        "missed" -> "📞 Missed ${label.lowercase()}"
        "cancelled" -> "📞 Cancelled ${label.lowercase()}"
        "busy" -> "📞 Busy"
        else -> if (call.durationSec > 0) "📞 $label · ${formatCallClock(call.durationSec)}" else "📞 $label"
    }
}

/**
 * The notification lanes are still seeded until the activity service lands;
 * chats come from the server.
 */
private fun catalog(app: String): InboxSnapshot {
    val official = "@${app.lowercase(Locale.US)}_official"
    return InboxSnapshot(
        chats = emptyList(),
        activity = listOf(
            ActivityItem(5, "system", app, official, "Welcome to $app! Your For You feed is ready.", "now", app.take(1).uppercase(Locale.US), 0xFF8B5CF6),
        ),
        followers = emptyList(),
        system = emptyList(),
        stories = emptyList(),
    )
}
