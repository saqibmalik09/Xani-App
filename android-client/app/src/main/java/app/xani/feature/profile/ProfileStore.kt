package app.xani.feature.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.xani.core.net.ApiResponse
import app.xani.core.net.Balance
import app.xani.core.net.LevelData
import app.xani.core.net.LiveFeed
import app.xani.core.net.ProfileData
import app.xani.core.net.Xani
import app.xani.ui.theme.Dark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

/**
 * Profile visual tokens — UpdatedFigma light profile surface.
 * Keep magic colors here so API-driven screens stay rethemeable.
 */
internal object ProfileTokens {
    val Bg = Color(0xFFFFFFFF)
    val Bg2 = Color(0xFFF7F7F7)
    val Bg3 = Color(0xFFF0F0F0)
    val Text = Color(0xFF111111)
    val Sub = Color(0xFF888888)
    val Muted = Color(0xFFBBBBBB)
    val Border = Color(0xFFEBEBEB)
    val BorderStrong = Color(0xFFDBDBDB)
    val Hairline = Color(0xFFF0F0F0)
    val RowLine = Color(0xFFF5F5F5)
    val Accent = Dark.Primary // #8B5CF6
    val AccentDeep = Color(0xFF7C3AED)
    val AccentSoft = Accent.copy(alpha = 0.08f)
    val AccentWash = Accent.copy(alpha = 0.1f)
    val CopyFlash = Color(0xFF38BDF8)
    val Online = Color(0xFF10B981)
    val Ok = Color(0xFF16A34A)
    val Danger = Color(0xFFEF4444)
    val DangerSoft = Danger.copy(alpha = 0.06f)
    val AvatarRing = Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))
    val Cta = Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))
    val ReceivingWash = Brush.linearGradient(
        listOf(Color(0x146D28D9), Color(0x0FDB2777)),
    )
    val Scrim = Color.Black.copy(alpha = 0.4f)
    val SheetScrim = Color.Black.copy(alpha = 0.35f)
}

enum class ProfileFilter { Posts, Private, Saved, Liked }

enum class ProfileSort { Latest, Popular }

enum class ProfileStatsKind { Following, Followers, Sending, Receiving }

enum class ProfileGender { Male, Female, PreferNot }

enum class ReceivingPeriod { Daily, Weekly, Monthly }

/** Destination inside Profile — keeps overlays out of the scroll tree. */
enum class ProfileRoute {
    Home,
    Edit,
    Settings,
    Password,
    Stats,
    Visits,
    Frames,
    GiftRecord,
    Transactions,
    Withdraw,
    Balance,
    Recharge,
    SellerHub,
    CoinSellers,
    Share,
    Notifications,
    Blocked,
    Help,
    Report,
    Terms,
    Roles,
    Moderation,
    FindFriends,
    MyLive,
    MyRewards,
}

/** One grid cell. [views] doubles as the searchable label; [likes] is the count shown. */
@Immutable
data class ProfilePost(
    val id: Long,
    val color: Long,
    val views: String,
    val rank: Float,
    val thumbUrl: String = "",
    val likes: String = "0",
    val kind: String = "video", // video | photo | text
    val state: String = "live", // processing | live | failed
    val caption: String = "",
    val isPrivate: Boolean = false,
    val isRestricted: Boolean = false,
    val durationMs: Int = 0,
)

@Immutable
data class ProfileDraft(
    val displayName: String,
    val username: String,
    val bio: String,
    val gender: ProfileGender,
    val status: String,
)

sealed class ProfileSaveResult {
    data object Ok : ProfileSaveResult()
    data class UsernameLocked(val daysLeft: Int) : ProfileSaveResult()
}

/**
 * What the username field shows while the user types. [Checking] is only ever
 * seen on the first keystroke of a name that is not already in the local cache —
 * shape errors and repeats answer without a request.
 */
enum class UsernameState { Idle, Checking, Available, Taken, Invalid, Reserved, Current, Cooldown, Offline }

/** Something the user must be told about a write that had already been accepted on screen. */
sealed class ProfileNotice {
    data class UsernameLocked(val daysLeft: Int) : ProfileNotice()
    data class UsernameTaken(val username: String) : ProfileNotice()
    data object SaveFailed : ProfileNotice()
}

/**
 * Snapshot the screens render. Counters stay formatted strings because that is
 * what a cell shows; the numeric balances live alongside them so a wallet event
 * can be applied without re-parsing "12.4K".
 */
@Immutable
data class ProfileSnapshot(
    val displayName: String,
    val username: String,
    val email: String,
    val bio: String,
    val status: String,
    val gender: ProfileGender,
    val verified: Boolean,
    /** Storage key or absolute URL of the profile picture; empty = initials. */
    val avatarUrl: String,
    val following: String,
    val followers: String,
    /** People who opened this profile, as the header shows it ("1.2K"). */
    val profileViews: String,
    /** Lifetime gifting: what this account has sent, and what it has taken in. */
    val sending: String,
    val receiving: String,
    val coins: String,
    val diamonds: String,
    val beans: String,
    val balanceCoins: String,
    /**
     * Exact spendable coins for screens that compute with them (the gift
     * sheet). The compact strings above dedupe small movements ("60K" stays
     * "60K" after a 5-coin gift), so state watchers would never wake; this
     * number always changes when the balance does.
     */
    val coinsValue: Long,
    val usernameCooldownDays: Int,
    /** Gifting rungs the owner has reached; null = none yet (nothing drawn). */
    val senderLevel: LevelData? = null,
    val receiverLevel: LevelData? = null,
    val posts: List<ProfilePost>,
)

/**
 * The profile repository: one cache the whole app reads, the API behind it, and a
 * live stream keeping the two in step.
 *
 * How a save feels instant: the snapshot is updated the moment the user acts, the
 * write is queued, and the queue collapses a burst of keystrokes into one request.
 * Nothing on screen waits for a response. If the server rejects the write, the
 * snapshot is re-synced from it and the user is told through [notices] — the only
 * case where a message appears at all.
 *
 * How it stays fresh: [observeLive] holds a Server-Sent Events stream while a
 * profile surface is on screen, so an edit made on another device, and every coin
 * and diamond movement, arrive without polling. The stream is scoped to the
 * screen so a backgrounded app is not holding a socket open.
 */
object ProfileStore {
    private const val PREFS = "xani.profile"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_USER_ID = "user_id"

    /** Long enough to collapse typing into one request, short enough to feel saved. */
    private const val WRITE_DEBOUNCE_MS = 220L
    private const val MAX_WRITE_ATTEMPTS = 3
    private const val CHECK_CACHE_MAX = 64

    private val usernameRe = Regex("^[a-z0-9._]{3,24}$")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(Seed)

    /** The single source screens collect. */
    val state: StateFlow<ProfileSnapshot> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<ProfileNotice>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Write failures worth surfacing. Dropped rather than queued without bound. */
    val notices: SharedFlow<ProfileNotice> = _notices.asSharedFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var userId = 0L

    /** The signed-in account's id; 0 before sign-in. */
    val currentUserId: Long get() = userId

    /** Highest rev seen from the server; anything older is a stale event. */
    @Volatile
    private var serverRev = 0L

    @Volatile
    private var cooldownDays = 0

    @Volatile
    private var writerStarted = false

    @Volatile
    private var inFlight = false

    private val dirty = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var pending: Patch? = null

    private val checks = object : LinkedHashMap<String, UsernameState>(CHECK_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UsernameState>?) =
            size > CHECK_CACHE_MAX
    }

    /** Partial write. A null field was not touched and is not sent. */
    private data class Patch(
        val fullName: String? = null,
        val bio: String? = null,
        val status: String? = null,
        val gender: String? = null,
    ) {
        fun merge(next: Patch) = Patch(
            fullName = next.fullName ?: fullName,
            bio = next.bio ?: bio,
            status = next.status ?: status,
            gender = next.gender ?: gender,
        )

        val statusOnly: Boolean get() = status != null && fullName == null && bio == null && gender == null
    }

    fun attach(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            // Paint the last known profile before the first request finishes: the
            // screen opens with real data, not a placeholder that then jumps.
            restore(p)
            // Subscribe once. Collecting costs nothing until a holder opens the
            // shared stream; then edits from other devices and every balance
            // movement land here.
            scope.launch { LiveFeed.profile.collect { if (userId != 0L) apply(it, local = false) } }
            scope.launch { LiveFeed.balance.collect { if (userId != 0L) applyBalance(it) } }
            // Somebody just opened this profile. One number, so it is patched in
            // place rather than costing a re-read of the whole screen.
            scope.launch {
                LiveFeed.profileViews.collect { views ->
                    if (userId != 0L) _state.update { it.copy(profileViews = compact(views)) }
                }
            }
            scope.launch { LiveFeed.resync.collect { if (userId != 0L) refresh() } }
        }
    }

    fun load(): ProfileSnapshot = _state.value

    fun usernameCooldownDays(): Int = cooldownDays

    fun canChangeUsername(): Boolean = cooldownDays == 0

    /**
     * Binds the store to a signed-in account: paints what the session already
     * knows, then revalidates against the server.
     */
    fun signedIn(id: Long, fullName: String, username: String, email: String, bio: String) {
        val switched = userId != 0L && userId != id
        userId = id
        if (switched) reset()
        _state.update { current ->
            current.copy(
                displayName = fullName.ifBlank { current.displayName },
                username = username.ifBlank { current.username },
                email = email.ifBlank { current.email },
                bio = bio.ifBlank { current.bio },
            )
        }
        startWriter()
        refresh()
    }

    fun signedOut() {
        userId = 0L
        LiveFeed.reset()
        reset()
        prefs?.edit()?.remove(KEY_PAYLOAD)?.remove(KEY_USER_ID)?.apply()
    }

    /** Re-reads the profile. Cheap: the server answers this one from memory. */
    fun refresh() {
        scope.launch {
            val response = Xani.profile.me()
            response.value?.let { apply(it, local = false) }
        }
    }

    /**
     * Holds the realtime stream open for as long as the caller's coroutine lives,
     * so an edit on another device and every balance movement land here without
     * polling. Suspends until cancelled; failures are retried inside the
     * transport, never surfaced.
     */
    suspend fun observeLive() {
        if (userId == 0L) return
        LiveFeed.hold()
    }

    /**
     * Applies an edit immediately and queues the write.
     *
     * Returns [ProfileSaveResult.UsernameLocked] only for the one rule the client
     * can be sure of without asking — the change window it was told about. Every
     * other rejection arrives through [notices], because the screen has already
     * moved on by then.
     */
    fun save(draft: ProfileDraft): ProfileSaveResult {
        val current = _state.value
        val nextUser = draft.username.trim().trimStart('@').lowercase()
        val renaming = nextUser.isNotEmpty() && nextUser != current.username
        if (renaming && !canChangeUsername()) {
            return ProfileSaveResult.UsernameLocked(cooldownDays)
        }

        val name = draft.displayName.trim().ifBlank { current.displayName }
        _state.update {
            it.copy(
                displayName = name,
                username = if (renaming) nextUser else it.username,
                bio = draft.bio,
                gender = draft.gender,
                status = draft.status,
            )
        }
        enqueue(
            Patch(
                fullName = name.takeIf { it != current.displayName },
                bio = draft.bio.takeIf { it != current.bio },
                gender = draft.gender.wire().takeIf { draft.gender != current.gender },
                status = draft.status.takeIf { it != current.status },
            ),
        )
        if (renaming) rename(nextUser, previous = current.username)
        return ProfileSaveResult.Ok
    }

    /** "What's on your mind" — called while the user types, so it must be cheap. */
    fun updateStatus(status: String) {
        val trimmed = status.trim()
        if (trimmed == _state.value.status) return
        _state.update { it.copy(status = trimmed) }
        enqueue(Patch(status = trimmed))
    }

    /**
     * Availability for the handle being typed.
     *
     * Ordered cheapest first: the current name, then the shape rule, then names
     * already answered this session, and only then a request. The caller debounces
     * by restarting this call, so an abandoned keystroke's request is cancelled
     * with its coroutine.
     */
    suspend fun checkUsername(raw: String): UsernameState {
        val name = raw.trim().trimStart('@').lowercase()
        if (name.isEmpty()) return UsernameState.Idle
        if (name == _state.value.username) return UsernameState.Current
        if (!usernameRe.matches(name)) return UsernameState.Invalid
        if (!canChangeUsername()) return UsernameState.Cooldown
        synchronized(checks) { checks[name] }?.let { return it }

        val response = Xani.profile.checkUsername(name)
        val result = response.value?.let { check ->
            when {
                check.available -> UsernameState.Available
                check.reason == "reserved" -> UsernameState.Reserved
                check.reason == "current" -> UsernameState.Current
                check.reason == "invalid" -> UsernameState.Invalid
                check.reason == "cooldown" -> UsernameState.Cooldown
                else -> UsernameState.Taken
            }
        } ?: if (response.isTransport) UsernameState.Offline else UsernameState.Invalid

        // Never cache a network failure: the next keystroke should try again.
        if (result != UsernameState.Offline) {
            synchronized(checks) { checks[name] = result }
        }
        return result
    }

    // ─── writes ──────────────────────────────────────────────────────────────

    private fun enqueue(patch: Patch) {
        if (patch == Patch()) return
        synchronized(lock) { pending = pending?.merge(patch) ?: patch }
        startWriter()
        dirty.trySend(Unit)
    }

    private fun startWriter() {
        if (writerStarted) return
        synchronized(this) {
            if (writerStarted) return
            writerStarted = true
        }
        scope.launch {
            for (ignored in dirty) {
                // Collapse the burst: typing a status fires one request, not one per
                // character.
                delay(WRITE_DEBOUNCE_MS)
                val patch = synchronized(lock) { pending.also { pending = null } } ?: continue
                flush(patch)
            }
        }
    }

    private suspend fun flush(patch: Patch) {
        inFlight = true
        try {
            var attempt = 0
            var backoff = 400L
            while (true) {
                val response = if (patch.statusOnly) {
                    Xani.profile.setStatus(patch.status.orEmpty())
                } else {
                    Xani.profile.update(
                        fullName = patch.fullName,
                        bio = patch.bio,
                        status = patch.status,
                        gender = patch.gender,
                    )
                }
                val data = response.value
                if (data != null) {
                    apply(data, local = false)
                    return
                }
                attempt++
                // A refused write is refused for good; only transport failures are
                // worth another attempt, and only a bounded number of them.
                if (!response.isTransport || attempt >= MAX_WRITE_ATTEMPTS) {
                    if (!response.isSessionExpired) {
                        _notices.tryEmit(ProfileNotice.SaveFailed)
                        refresh() // snap the screen back to what the server holds
                    }
                    return
                }
                delay(backoff)
                backoff *= 2
            }
        } finally {
            inFlight = false
        }
    }

    private fun rename(next: String, previous: String) {
        scope.launch {
            val response = Xani.profile.setUsername(next)
            val data = response.value
            if (data != null) {
                apply(data, local = false)
                return@launch
            }
            // Put the old handle back: a username the server refused was never ours.
            _state.update { if (it.username == next) it.copy(username = previous) else it }
            synchronized(checks) { checks[next] = refusal(response, next) }
        }
    }

    /** Tells the user why a rename was refused, and remembers it for the field. */
    private fun refusal(response: ApiResponse<ProfileData>, username: String): UsernameState = when (response.code) {
        "duplicate_username", "reserved_username" -> {
            _notices.tryEmit(ProfileNotice.UsernameTaken(username))
            if (response.code == "reserved_username") UsernameState.Reserved else UsernameState.Taken
        }

        "username_cooldown" -> {
            _notices.tryEmit(ProfileNotice.UsernameLocked(cooldownDays))
            UsernameState.Cooldown
        }

        else -> {
            if (!response.isSessionExpired) _notices.tryEmit(ProfileNotice.SaveFailed)
            UsernameState.Offline
        }
    }

    // ─── applying server state ───────────────────────────────────────────────

    /**
     * Folds a server payload into the snapshot.
     *
     * Two guards keep realtime from fighting the user: an event older than what we
     * already show is dropped by [ProfileData.rev], and while a write of ours is
     * still queued or in flight, text fields keep what the user typed. Balances,
     * the handle and the change window always come from the server — those are
     * never locally authored.
     */
    private fun apply(data: ProfileData, local: Boolean) {
        if (!local && data.rev < serverRev) return
        serverRev = data.rev
        cooldownDays = data.usernameCooldownDays
        val keepTyped = pendingExists()

        _state.update { current ->
            current.copy(
                displayName = if (keepTyped) current.displayName else data.fullName.ifBlank { current.displayName },
                username = data.username.ifBlank { current.username },
                email = data.email.ifBlank { current.email },
                bio = if (keepTyped) current.bio else data.bio,
                status = if (keepTyped) current.status else data.status,
                gender = if (keepTyped) current.gender else data.gender.toGender(current.gender),
                verified = data.emailVerified,
                // The picture is never locally authored beyond the upload's own
                // preview, so the server's key always wins here.
                avatarUrl = data.avatarUrl,
                coins = compact(data.balance.coins).also { coinsNumeric = data.balance.coins; balanceSeen = true },
                diamonds = compact(data.balance.diamonds),
                beans = compact(data.balance.beans),
                balanceCoins = compact(data.balance.coins),
                coinsValue = data.balance.coins,
                usernameCooldownDays = data.usernameCooldownDays,
                followers = compact(data.followers.toLong()),
                following = compact(data.following.toLong()),
                profileViews = compact(data.profileViews),
                // What gifting has moved over the account's life — the same two
                // numbers the sender and receiver levels are cut from.
                sending = compact(data.sentCoins),
                receiving = compact(data.receivedCoins),
                senderLevel = data.senderLevel,
                receiverLevel = data.receiverLevel,
            )
        }
        persist(data)
    }

    /**
     * The follow ack carries the caller's own following count; apply it at once
     * so Profile is right before (or without) the stream's profile event.
     */
    fun setFollowing(count: Int) {
        _state.update { it.copy(following = compact(count.toLong())) }
    }

    /**
     * A balance the app learned from a direct reply (a gift send) rather than
     * the stream: apply it now so the sheet shows the ledger's number before
     * the wallet frame lands. Older revs are ignored — the stream may have
     * already delivered something newer.
     */
    fun setBalance(balance: Balance) {
        if (balance.rev != 0L && balance.rev < balanceRev) return
        balanceRev = balance.rev
        applyBalance(balance)
    }

    /** Numeric coins for screens that compute with them (the gift sheet). */
    val coins: Long get() = coinsNumeric

    /**
     * False until the server has told us a balance at least once this
     * process. A screen that gates on coins must not refuse on a 0 it has
     * only assumed — it asks the server instead.
     */
    val balanceKnown: Boolean get() = balanceSeen

    @Volatile
    private var balanceRev = 0L

    @Volatile
    private var coinsNumeric = 0L

    @Volatile
    private var balanceSeen = false

    /** Wallet events carry only money, so they can always be applied. */
    private fun applyBalance(balance: Balance) {
        if (balance.rev > balanceRev) balanceRev = balance.rev
        coinsNumeric = balance.coins
        balanceSeen = true
        _state.update {
            it.copy(
                coins = compact(balance.coins),
                diamonds = compact(balance.diamonds),
                beans = compact(balance.beans),
                balanceCoins = compact(balance.coins),
                coinsValue = balance.coins,
            )
        }
    }

    private fun pendingExists(): Boolean = inFlight || synchronized(lock) { pending != null }

    private fun persist(data: ProfileData) {
        val p = prefs ?: return
        p.edit()
            .putString(KEY_PAYLOAD, data.toJson().toString())
            .putLong(KEY_USER_ID, data.id)
            .apply()
    }

    private fun restore(p: SharedPreferences) {
        val raw = p.getString(KEY_PAYLOAD, null) ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        userId = p.getLong(KEY_USER_ID, 0L)
        apply(ProfileData.from(json), local = true)
    }

    private fun reset() {
        synchronized(lock) { pending = null }
        synchronized(checks) { checks.clear() }
        serverRev = 0L
        cooldownDays = 0
        balanceRev = 0L
        coinsNumeric = 0L
        balanceSeen = false
        _state.value = Seed
    }
}

private fun ProfileGender.wire(): String = when (this) {
    ProfileGender.Male -> "male"
    ProfileGender.Female -> "female"
    ProfileGender.PreferNot -> "prefer_not"
}

private fun String.toGender(fallback: ProfileGender): ProfileGender = when (this) {
    "male" -> ProfileGender.Male
    "female" -> ProfileGender.Female
    "prefer_not" -> ProfileGender.PreferNot
    else -> fallback
}

/**
 * Wallet numbers as a cell shows them: 940 · 12.4K · 3.1M. Formatting stays on
 * the client so the same number can render short in a chip and exact in a sheet.
 */
internal fun compact(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val n = abs(value)
    return when {
        n < 1_000 -> "$sign$n"
        n < 1_000_000 -> sign + trim(n / 1_000.0) + "K"
        n < 1_000_000_000 -> sign + trim(n / 1_000_000.0) + "M"
        else -> sign + trim(n / 1_000_000_000.0) + "B"
    }
}

private fun trim(v: Double): String {
    val rounded = (v * 10).toLong() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/**
 * First-paint values for a screen that has never had a payload: counters and
 * grid content still come from the feed and social services, which are not this
 * endpoint. Identity, status and balances are replaced by the first read.
 */
private val Seed = ProfileSnapshot(
    displayName = "",
    username = "",
    email = "",
    bio = "",
    status = "",
    gender = ProfileGender.PreferNot,
    verified = false,
    avatarUrl = "",
    following = "0",
    followers = "0",
    profileViews = "0",
    sending = "0",
    receiving = "0",
    coins = "0",
    diamonds = "0",
    beans = "0",
    balanceCoins = "0",
    coinsValue = 0L,
    usernameCooldownDays = 0,
    // The grid is served by [MyPosts] from /v1/posts/mine, not this snapshot.
    posts = emptyList(),
)
