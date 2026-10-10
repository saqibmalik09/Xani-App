package app.xani.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * The one realtime connection per signed-in device, shared by every surface
 * that wants it.
 *
 * The server multiplexes profile edits, balance movements and post state onto a
 * single Server-Sent Events stream. Holders ([hold]) are reference counted: the
 * first opens the socket, the last closes it, so the profile screen and an
 * in-flight upload never hold two connections, and a backgrounded app with no
 * holder holds none.
 *
 * Frames fan out as [SharedFlow]s. Collecting a flow is free while no holder is
 * present; that lets a store subscribe once at startup and only pay for the
 * socket while something on screen (or a pending post) needs freshness.
 */
/**
 * The owner turned a like or a save on or off. Their "Liked" and "Saved" tabs
 * are lists of exactly these rows, so they move with the tap rather than
 * waiting for the next time the tab is opened.
 */
data class PostReaction(val postId: Long, val like: Boolean, val on: Boolean)

object LiveFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun <T> bus() = MutableSharedFlow<T>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _profile = bus<ProfileData>()
    private val _balance = bus<Balance>()
    private val _posts = bus<PostData>()
    private val _postsGone = bus<Long>()
    private val _counts = bus<PostCountsEvent>()
    private val _profileViews = bus<Long>()
    private val _reactions = bus<PostReaction>()
    private val _resync = bus<Unit>()
    private val _sessionEnd = bus<String>()
    private val _chat = MutableSharedFlow<ChatEvent>(
        replay = 0,
        // Chat bursts (a reconnect replays receipts and typing) must not shed
        // a message; the consumer drains on its own dispatcher.
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Everything chat: messages, receipts, reactions, typing, presence, settings. */
    val chat: SharedFlow<ChatEvent> = _chat

    private val _call = MutableSharedFlow<CallEvent>(
        replay = 0,
        // ICE trickles dozens of candidates in the first second of a call; none
        // may be shed or the connection takes the slow path.
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Call signalling and live-room frames: invite, accept, SDP/ICE relay, end. */
    val call: SharedFlow<CallEvent> = _call

    private val _activity = MutableSharedFlow<ActivityData>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** My activity lane, live: "X viewed your story" the moment it happens. */
    val activity: SharedFlow<ActivityData> = _activity

    private val _notices = MutableSharedFlow<NoticeData>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Inbox notifications tab: moderation and product notices. */
    val notices: SharedFlow<NoticeData> = _notices

    private val _rewards = MutableSharedFlow<RewardToast>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A live-time reward landed: the HUD toast and My Rewards refresh. Dedupe on ledgerId. */
    val rewards: SharedFlow<RewardToast> = _rewards

    private val _gifting = MutableSharedFlow<GiftingUserEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A gift reached or left this account: statements and totals move at once. */
    val gifting: SharedFlow<GiftingUserEvent> = _gifting

    /** Connection state, so the thread header can say "Connecting…" honestly. */
    private val _connected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val connected: kotlinx.coroutines.flow.StateFlow<Boolean> = _connected

    val profile: SharedFlow<ProfileData> = _profile
    val balance: SharedFlow<Balance> = _balance
    val posts: SharedFlow<PostData> = _posts

    /** Post ids the author retired: every surface holding one drops it. */
    val postsGone: SharedFlow<Long> = _postsGone

    /**
     * Engagement on one of the signed-in account's own posts — a stranger's
     * like, comment or counted watch. It arrives here rather than on the
     * post's own topic because the author is not watching their own post while
     * someone else is, and their grid still has to show the new number.
     */
    val counts: SharedFlow<PostCountsEvent> = _counts

    /** Somebody opened this account's profile: the new visit count. */
    val profileViews: SharedFlow<Long> = _profileViews

    /** A like or save this device just made, wherever it was made. */
    val reactions: SharedFlow<PostReaction> = _reactions

    fun emitReaction(postId: Long, like: Boolean, on: Boolean) {
        _reactions.tryEmit(PostReaction(postId, like, on))
    }

    /** Fired when the server says the client fell behind: re-read, do not patch. */
    val resync: SharedFlow<Unit> = _resync

    /** Admin ban kicked this device; SessionManager already expired the session. */
    val sessionEnd: SharedFlow<String> = _sessionEnd

    /**
     * A post state the app learned from a direct response (text post, complete
     * call) rather than the stream. Fanned out on the same bus so the feed, the
     * grid and the posting strip update from one event, whichever path it took.
     */
    fun emitPost(post: PostData) {
        _posts.tryEmit(post)
    }

    /** Same, for a delete the app performed itself: one event, every surface. */
    fun emitPostGone(postId: Long) {
        _postsGone.tryEmit(postId)
    }

    private val lock = Any()
    private var holders = 0
    private var job: Job? = null

    /**
     * Which connection the flows belong to. A cancelled stream finishes after its
     * replacement has started, so it only clears [connected] while it is still the
     * current one — otherwise a re-dial would report itself down right after
     * coming up.
     */
    private var generation = 0

    /**
     * Keeps the connection open for as long as the calling coroutine lives.
     * Suspends until cancelled.
     */
    suspend fun hold(): Nothing {
        acquire()
        try {
            awaitCancellation()
        } finally {
            release()
        }
    }

    private fun acquire() {
        synchronized(lock) {
            holders++
            if (job?.isActive == true) return
            launchStream()
        }
    }

    private fun launchStream() {
        synchronized(lock) {
            val gen = ++generation
            job = scope.launch {
                try {
                    Xani.profile.stream(
                        // The first frame on every (re)connection is the profile
                        // snapshot: that is the moment the socket is known good.
                        onProfile = { _profile.tryEmit(it); _connected.value = true },
                        onBalance = { _balance.tryEmit(it) },
                        onPost = { _posts.tryEmit(it) },
                        onPostGone = { _postsGone.tryEmit(it) },
                        onCounts = { json -> PostCountsEvent.from(json)?.let { _counts.tryEmit(it) } },
                        onProfileViews = { _profileViews.tryEmit(it) },
                        onResync = { _resync.tryEmit(Unit); _chat.tryEmit(ChatEvent.Resync) },
                        onSessionEnd = { msg ->
                            _sessionEnd.tryEmit(msg)
                            Xani.session.expire()
                        },
                        onChat = { name, data -> ChatEvent.from(name, data)?.let { _chat.tryEmit(it) } },
                        onCall = { name, data -> CallEvent.from(name, data)?.let { _call.tryEmit(it) } },
                        onActivity = { _activity.tryEmit(it) },
                        onNotice = { _notices.tryEmit(it) },
                        // A moderator changed this account's permissions: pull
                        // /v1/auth/me now so FLAG_SECURE and mic gates flip live.
                        onAccess = { scope.launch { Xani.session.resync() } },
                        // Admin toggled a released version: re-check this build's gate.
                        onVersion = { scope.launch { AppGate.refresh() } },
                        onReward = { _rewards.tryEmit(it) },
                        onGifting = { _gifting.tryEmit(it) },
                    )
                } finally {
                    synchronized(lock) { if (gen == generation) _connected.value = false }
                }
            }
        }
    }

    /**
     * Drops the socket and dials again for whoever is signed in now.
     *
     * The stream carries the account in its bearer, so an account switch has to
     * re-dial: otherwise every push — profile, balance, chat, calls — still
     * belongs to the account the user just left. Holders keep their
     * subscriptions; the first frame of the new connection is the new account's
     * profile.
     */
    fun restart() {
        synchronized(lock) {
            job?.cancel()
            job = null
            _connected.value = false
            if (holders > 0) launchStream()
        }
    }

    private fun release() {
        synchronized(lock) {
            holders = (holders - 1).coerceAtLeast(0)
            if (holders == 0) {
                job?.cancel()
                job = null
                _connected.value = false
            }
        }
    }

    /** Drops the socket on sign-out regardless of holders; they re-acquire on sign-in. */
    fun reset() {
        synchronized(lock) {
            job?.cancel()
            job = null
            holders = 0
            _connected.value = false
        }
    }
}

/** One chat frame off the stream, already typed. */
sealed class ChatEvent {
    data class Message(val message: ChatMessageData) : ChatEvent()
    data class Receipt(val receipt: ReceiptEvent) : ChatEvent()
    data class Reaction(val reaction: ReactionEvent) : ChatEvent()
    data class Typing(val typing: TypingEvent) : ChatEvent()
    data class Presence(val presence: PresenceEvent) : ChatEvent()
    data class Conversation(val conversation: ConversationData) : ChatEvent()
    data class Gone(val conversationId: Long) : ChatEvent()
    /** The server said frames were shed, or the socket reconnected: re-read, do not patch. */
    data object Resync : ChatEvent()

    companion object {
        fun from(name: String, data: org.json.JSONObject): ChatEvent? = when (name) {
            "chat_message" -> Message(ChatMessageData.from(data))
            "chat_receipt" -> Receipt(ReceiptEvent.from(data))
            "chat_reaction" -> Reaction(ReactionEvent.from(data))
            "chat_typing" -> Typing(TypingEvent.from(data))
            "chat_presence" -> Presence(PresenceEvent.from(data))
            "chat_conversation" -> Conversation(ConversationData.from(data))
            "chat_gone" -> data.id("conversation_id").takeIf { it > 0 }?.let(::Gone)
            else -> null
        }
    }
}
