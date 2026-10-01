package app.xani.feature.call

import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Immutable
import app.xani.core.isolate.runFeature
import app.xani.core.net.CallEvent
import app.xani.core.net.GiftData
import app.xani.core.net.LiveChatData
import app.xani.core.net.LiveFeed
import app.xani.core.net.LiveEnterEvent
import app.xani.core.net.LiveGiftEvent
import app.xani.core.net.LiveModeratorData
import app.xani.core.net.bool
import app.xani.core.net.id
import app.xani.core.net.int
import app.xani.core.net.long
import app.xani.core.net.obj
import app.xani.core.net.str
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import app.xani.core.net.LivePersonData
import app.xani.core.net.LiveRoomData
import app.xani.core.net.LiveSummaryData
import app.xani.core.net.PkData
import app.xani.core.net.PkInviteData
import app.xani.core.net.PkStartData
import app.xani.core.net.RoomJoinData
import app.xani.core.net.StageData
import app.xani.core.net.StageRequestData
import app.xani.core.net.StickerData
import app.xani.core.net.Xani
import app.xani.feature.call.rtc.RtcEngineFactory
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.FrameStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class LiveRole { Host, Guest, Viewer }

enum class LivePhase { Connecting, Live, Reconnecting, Ending, Ended }

@Immutable
data class LiveUi(
    val roomId: Long,
    val role: LiveRole,
    val phase: LivePhase,
    val room: LiveRoomData,
    /** sfu | hls — how this device receives (viewers) or sends (host/guest). */
    val transport: String,
    /** CDN playlist for HLS viewers; empty on the SFU path. */
    val hlsUrl: String = "",
    val viewers: Long = 0,
    val peakViewers: Long = 0,
    val muted: Boolean = false,
    val cameraOff: Boolean = false,
    /**
     * host_ended · host_lost · lost · max_length · live_ended · live_unavailable ·
     * password_required · wrong_password · failed
     */
    val endReason: String = "",
    /** The server's sentence for a refused start/join (copy, not a code); empty otherwise. */
    val error: String = "",
    /** Server-side seconds live at the last sync, and the monotonic clock then. */
    val syncDurationSeconds: Long = 0L,
    val syncAtMs: Long = 0L,
    /** The host's end card, from the server; null until End is acknowledged. */
    val summary: LiveSummaryData? = null,
    /** Bumped when the media session behind the renderers is replaced; a remember key. */
    val mediaRev: Int = 0,
    /**
     * Host missed a heartbeat: their picture is frozen. Distinct from this
     * device's own Reconnecting — the viewer is fine, the host is not.
     */
    val hostAway: Boolean = false,
    /** Bounded room chat; newest last. Never grows past [ChatCap]. */
    val chat: List<LiveChatData> = emptyList(),
    /** Coins gifted into this room so far — the room's 💎 pill, the host's earnings. */
    val roomCoins: Long = 0L,
    /** The stage: seats, policy, who is up. Replaced wholesale on every `live_stage`. */
    val stage: StageData = StageData.Empty,
    /** My seat when on stage (guest); 0 otherwise. */
    val seat: Int = 0,
    /** Viewer: my co-host request is with the host. */
    val requested: Boolean = false,
    /** Host: who is asking to co-host, oldest first. Bounded to [RequestCap]. */
    val requests: List<StageRequestData> = emptyList(),
    /** A sit / stand is in flight: the seat taps ignore a second finger. */
    val stageBusy: Boolean = false,
    /** Active PK battle; null when this room is not fighting. */
    val pk: PkData? = null,
    /** Incoming PK invite for me as a live host (null when none). */
    val pkInvite: PkInviteData? = null,
    /** Hearts tapped in this room so far, by everyone (server total). */
    val likes: Long = 0L,
    /** My chat is muted by a moderator until this moment (unix ms); 0 = free. */
    val mutedUntilMs: Long = 0L,
    /** The host's moderators in this room, by user id. */
    val mods: Set<Long> = emptySet(),
) {
    /** Am I publishing (host or on stage)? */
    val onStage: Boolean get() = role != LiveRole.Viewer
    /** Can I mute / block / kick here — the host, or one of their moderators. */
    val canModerate: Boolean get() = role == LiveRole.Host || ProfileStore.currentUserId in mods
    val chatMuted: Boolean get() = mutedUntilMs > System.currentTimeMillis()
    val over: Boolean get() = phase == LivePhase.Ended || phase == LivePhase.Ending

    /** The door needs (a different) password; the viewer should be asked again, not told "ended". */
    val needsPassword: Boolean
        get() = phase == LivePhase.Ended && (endReason == "password_required" || endReason == "wrong_password")

    /**
     * Seconds live by the server's clock, advanced locally between syncs. A
     * phone whose clock drifts, sleeps or reboots mid-broadcast still shows
     * the same number the summary will.
     */
    fun elapsedSeconds(nowMs: Long = SystemClock.elapsedRealtime()): Long {
        if (syncAtMs == 0L) return 0L
        return syncDurationSeconds + ((nowMs - syncAtMs) / 1000L).coerceAtLeast(0L)
    }

    companion object {
        const val ChatCap = 40
        const val RequestCap = 50
    }
}

/** A one-line notice for the room surface (declined, dropped, seat taken…). */
@Immutable
data class LiveNotice(val code: String, val message: String = "")

/**
 * One live room at a time on this device: hosting, sitting as a guest, or
 * watching. The host and the first N viewers ride the SFU; the crowd rides
 * LL-HLS from the CDN, which is how a million people watch without a million
 * media sessions.
 *
 * Liveness is a heartbeat, not a socket: every [heartbeatSeconds] the device
 * tells the server it is still here and gets the room's clock and audience
 * back. A dropped link puts the session in Reconnecting and rejoins with the
 * same seat for up to the server's grace window; only the server ends a room,
 * and it tells everyone with `live_ended`.
 */
object LiveSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<LiveUi?>(null)
    val state: StateFlow<LiveUi?> = _state

    /**
     * Gifts landing in the room this device is in — the sender's own optimistic
     * preview, the server's settled frame, and everyone else's. The overlay
     * collects this; nothing is stored, so a stadium room costs no memory here.
     * Bounded: a burst past 256 undrained frames sheds the oldest, and the
     * overlay's own queue coalesces combos before anything is drawn.
     */
    private val _gifts = MutableSharedFlow<LiveGiftEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val gifts: SharedFlow<LiveGiftEvent> = _gifts

    /** A refused send, for the sheet to say why (insufficient_funds, …). */
    private val _giftErrors = MutableSharedFlow<GiftFailure>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val giftErrors: SharedFlow<GiftFailure> = _giftErrors

    /**
     * Someone with an entrance effect walking into this room. The overlay
     * plays each for a few seconds; a burst of arrivals sheds the oldest —
     * an entrance is a moment, not history.
     */
    private val _entrances = MutableSharedFlow<LiveEnterEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val entrances: SharedFlow<LiveEnterEvent> = _entrances

    /** Stage notices for the surface to toast: declined, dropped, seat taken. */
    private val _notices = MutableSharedFlow<LiveNotice>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<LiveNotice> = _notices

    /** Hearts other people tapped: how many to float this frame. */
    private val _hearts = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val hearts: SharedFlow<Int> = _hearts

    data class GiftFailure(val clientId: String, val code: String, val message: String)

    private lateinit var app: Context
    private var attached = false
    private var engine: RtcEngine? = null
    private var room: RtcRoom? = null
    /** Second SFU seat: publish into the opponent's room during PK. */
    private var pkRoom: RtcRoom? = null
    private var hold: Job? = null
    private var roomStream: Job? = null
    private var heartbeat: Job? = null
    private var rejoin: Job? = null
    private var session = 0
    /** Room-stream gift ids already delivered; bounded so a long live never grows it. */
    private val seenGifts = LinkedHashSet<Long>()
    private var giftSeq = 0L

    /**
     * The room total is written to [state] at most every [COINS_TICK_MS]: a
     * lucky combo lands dozens of frames a second, and each write here
     * recomposes the whole broadcast screen. The overlay reads the frames
     * themselves; the pill reads this.
     */
    private val coinsSeen = java.util.concurrent.atomic.AtomicLong(0L)
    private var coinsFlush: Job? = null

    private fun noteRoomCoins(total: Long) {
        if (total <= 0L) return
        var cur = coinsSeen.get()
        while (total > cur) {
            if (coinsSeen.compareAndSet(cur, total)) break
            cur = coinsSeen.get()
        }
        if (coinsFlush?.isActive == true) return
        coinsFlush = scope.launch {
            delay(COINS_TICK_MS)
            val v = coinsSeen.get()
            update { if (v > it.roomCoins) it.copy(roomCoins = v) else it }
        }
    }

    private const val COINS_TICK_MS = 250L

    // The seat we hold, so a rejoin never needs another round-trip to the API.
    private var seat: RoomJoinData? = null
    private var publish = false
    private var video = false
    private var heartbeatSeconds = 20
    private var graceSeconds = 150
    private var lostAtMs = 0L
    /** When I last sat down (monotonic), so a stale snapshot cannot unseat me. */
    private var satAtMs = 0L

    fun attach(context: Context) {
        if (attached) return
        attached = true
        app = context.applicationContext
        scope.launch { LiveFeed.call.collect { runFeature(Unit) { onEvent(it) } } }
    }

    /**
     * Start broadcasting. A room the host never ended (crash, battery) is
     * closed by the server on this call, so "already live" cannot block a
     * host from going live again.
     */
    fun goLive(
        video: Boolean,
        title: String,
        tint: String = "",
        coverUrl: String = "",
        audience: String = "public",
        password: String = "",
        /** Audio: seats including the host (2..35). */
        seats: Int = 0,
        /** Video: viewers may ask to co-host. */
        requests: Boolean = true,
    ) {
        if (_state.value != null && _state.value?.over != true) return
        val mine = ++session
        val kind = if (video) "video" else "audio"
        _state.value = LiveUi(0L, LiveRole.Host, LivePhase.Connecting, LiveRoomData(0, 0, kind, title, 0, true), "sfu")
        hold = scope.launch { LiveFeed.hold() }
        warmEngine()
        scope.launch {
            val r = Xani.calls.liveStart(
                kind, title, tint, coverUrl = coverUrl, audience = audience, password = password,
                seats = if (video) 0 else seats, requests = requests,
            )
            val data = r.value
            if (mine != session) {
                // The host left before the server answered: the room it just
                // opened has nobody behind it, so close it now, not at grace end.
                if (r.ok && data != null) Xani.calls.liveEnd(data.room.id, "host_ended")
                return@launch
            }
            if (!r.ok || data == null) {
                fail(r.code.ifBlank { "failed" }, r.message); return@launch
            }
            heartbeatSeconds = data.heartbeatSeconds.coerceIn(5, 60)
            graceSeconds = data.reconnectGraceSeconds.coerceIn(30, 600)
            update { it.copy(roomId = data.room.id, room = data.room, hlsUrl = data.hlsUrl, stage = data.stage, pk = data.room.pk).synced(data.room) }
            join(data.sfu, publish = true, video = video)
            startHeartbeat(mine, "sfu")
            openRoomStream(mine, data.room.id)
        }
    }

    /** Watch. The server picks SFU or HLS; the UI renders whichever it got. */
    fun watch(roomId: Long, password: String = "") {
        if (_state.value?.roomId == roomId && _state.value?.over != true) return
        leave()
        val mine = ++session
        _state.value = LiveUi(roomId, LiveRole.Viewer, LivePhase.Connecting, LiveRoomData(roomId, 0, "video", "", 0, true), "hls")
        hold = scope.launch { LiveFeed.hold() }
        warmEngine()
        scope.launch {
            val r = Xani.calls.liveJoin(roomId, password)
            if (mine != session) return@launch
            val data = r.value
            if (!r.ok || data == null) {
                fail(if (r.statusCode == 410 || r.code == "not_found") "live_ended" else r.code.ifBlank { "failed" }, r.message)
                return@launch
            }
            heartbeatSeconds = data.heartbeatSeconds.coerceIn(5, 60)
            graceSeconds = data.reconnectGraceSeconds.coerceIn(30, 600)
            update {
                it.copy(
                    room = data.room, transport = data.mode, hlsUrl = data.hlsUrl, stage = data.stage, pk = data.room.pk,
                    likes = maxOf(it.likes, data.likes),
                    mods = if (data.mod) it.mods + ProfileStore.currentUserId else it.mods,
                ).synced(data.room)
            }
            val sfu = data.sfu
            if (data.mode == "sfu" && sfu != null) {
                join(sfu, publish = false, video = true)
            } else {
                // HLS: the player view owns playback; the session is live once the URL is known.
                if (data.hlsUrl.isBlank()) fail("live_unavailable") else update { it.copy(phase = LivePhase.Live) }
            }
            startHeartbeat(mine, data.mode)
            openRoomStream(mine, roomId)
        }
    }

    // ─── gifts ───────────────────────────────────────────────────────────────

    /**
     * Send [gift] to [receiverId] (0 = host). Optimistic: the sender's overlay
     * plays now under [clientId]; the server's frame settles it (same clientId)
     * and the balance in the reply is applied at once. A refused send emits on
     * [giftErrors] and the overlay is told to drop the preview.
     *
     * [combo] is the position in a lucky streak; the server records it and
     * everyone's banner counts up with it.
     */
    fun sendGift(gift: GiftData, receiverId: Long, combo: Int = 1, count: Int = 1): String? {
        val ui = _state.value ?: return null
        if (ui.over || ui.roomId == 0L) return null
        val to = if (receiverId > 0L) receiverId else ui.room.hostId
        val clientId = "g" + ProfileStore.currentUserId + "-" + System.currentTimeMillis() + "-" + (++giftSeq)
        val me = ProfileStore.state.value
        val price = gift.coinPrice * count
        // Local truth first: the coins pill drops now; the server's number
        // replaces it on reply (or the wallet frame, whichever is first).
        val optimistic = LiveGiftEvent(
            id = 0L,
            roomId = ui.roomId,
            clientId = clientId,
            gift = gift,
            sender = LivePersonData(
                id = ProfileStore.currentUserId,
                username = me.username,
                fullName = me.displayName,
                avatarUrl = me.avatarUrl,
                role = when (ui.role) {
                    LiveRole.Host -> "host"
                    LiveRole.Guest -> "guest"
                    LiveRole.Viewer -> "viewer"
                },
            ),
            receiver = LivePersonData(id = to, username = "", fullName = "", avatarUrl = "", role = if (to == ui.room.hostId) "host" else "viewer"),
            count = count,
            combo = combo,
            totalCoins = price,
            roomCoins = ui.roomCoins + price,
            atMs = System.currentTimeMillis(),
        )
        _gifts.tryEmit(optimistic)
        val mine = session
        val roomId = ui.roomId
        scope.launch {
            var r = Xani.gifts.send(roomId, gift.id, to, clientId, count = count, combo = combo)
            if (!r.ok && r.isTransport) {
                // Same clientId: the server lands it once whichever attempt got through.
                delay(400)
                r = Xani.gifts.send(roomId, gift.id, to, clientId, count = count, combo = combo)
            }
            if (mine != session) return@launch
            val data = r.value
            if (r.ok && data != null) {
                ProfileStore.setBalance(data.balance)
                deliverGift(data.gift)
            } else if (!r.ok) {
                _giftErrors.tryEmit(GiftFailure(clientId, r.code, r.message))
            }
        }
        return clientId
    }

    /**
     * The room's own stream: gift frames for everyone inside, one publish per
     * gift however large the room. Held for as long as this device is in the
     * room; reconnects on its own and resyncs the room total on each connect.
     */
    private fun openRoomStream(mine: Int, roomId: Long) {
        roomStream?.cancel()
        seenGifts.clear()
        coinsFlush?.cancel(); coinsFlush = null
        coinsSeen.set(_state.value?.roomCoins ?: 0L)
        roomStream = scope.launch {
            Xani.gifts.stream(roomId) { name, frame ->
                if (mine != session) return@stream
                // Frames are `{kind, rev, data}` envelopes, the same shape as the
                // profile stream; the payload is under `data`.
                val data = frame.obj("data") ?: frame
                when (name) {
                    "live_gift" -> deliverGift(LiveGiftEvent.from(data))
                    "live_enter" -> {
                        val ev = LiveEnterEvent.from(data)
                        if (ev.roomId == (_state.value?.roomId ?: 0L) && ev.entranceUrl.isNotEmpty()) {
                            _entrances.tryEmit(ev)
                        }
                    }
                    "live_room" -> noteRoomCoins(data.long("gift_coins", -1L))
                    // Hearts from someone else: float them and move the total.
                    // Mine are already on screen (and counted on the reply).
                    "live_like" -> if (data.id("room_id") == roomId) {
                        val total = data.long("likes")
                        val n = data.int("n").coerceIn(0, 12)
                        val from = data.id("user_id")
                        update { if (total > it.likes) it.copy(likes = total) else it }
                        if (from != ProfileStore.currentUserId && n > 0) _hearts.tryEmit(n)
                    }
                    "live_chat_react" -> if (data.id("room_id") == roomId) {
                        val lineId = data.id("line_id")
                        val emoji = data.str("emoji")
                        val add = data.str("op", "add") == "add"
                        val from = data.id("user_id").let { if (it != 0L) it else data.obj("user")?.id("id") ?: 0L }
                        if (lineId != 0L && emoji.isNotEmpty() && from != ProfileStore.currentUserId) {
                            update { it.copy(chat = it.chat.react(lineId, emoji, add, mine = false)) }
                        }
                    }
                    "live_mods" -> if (data.id("room_id") == roomId) {
                        val who = data.obj("user")?.id("id") ?: 0L
                        val on = data.bool("mod")
                        if (who != 0L) update { it.copy(mods = if (on) it.mods + who else it.mods - who) }
                    }
                    "live_stage" -> {
                        val st = StageData.from(data)
                        if (st.roomId == roomId) scope.launch { runFeature(Unit) { onStage(st) } }
                    }
                    "live_pk" -> {
                        val phase = data.str("phase")
                        if (phase == "ended") {
                            closePkRoom()
                            update { it.copy(pk = null) }
                        } else {
                            val pk = runFeature(null) { PkData.from(data) }
                            // Only a battle this room is a side of; a frame for
                            // another room (stale stream) cannot repaint ours.
                            if (pk != null && pk.id != 0L && (pk.left.roomId == roomId || pk.right.roomId == roomId)) {
                                update { it.copy(pk = pk) }
                            }
                        }
                    }
                    "live_ended" -> scope.launch {
                        runFeature(Unit) {
                            onEvent(
                                CallEvent.LiveEnded(
                                    data.id("room_id"),
                                    data.str("reason"),
                                    data.obj("summary")?.let(LiveSummaryData::from),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /** One settled gift frame from any path: dedupe by id, then fan out and count. */
    private fun deliverGift(ev: LiveGiftEvent) {
        val ui = _state.value ?: return
        if (ev.roomId != ui.roomId) return
        if (ev.id != 0L) {
            synchronized(seenGifts) {
                if (!seenGifts.add(ev.id)) return
                if (seenGifts.size > SEEN_CAP) {
                    val iter = seenGifts.iterator()
                    var n = SEEN_CAP / 4
                    while (n > 0 && iter.hasNext()) { iter.next(); iter.remove(); n-- }
                }
            }
        }
        _gifts.tryEmit(ev)
        noteRoomCoins(ev.roomCoins)
    }

    private const val SEEN_CAP = 2048

    /**
     * Host ends for everyone. Media stops now; the state stays until the
     * server's summary lands so the end card shows the server's clock.
     */
    fun endLive(reason: String = "host_ended") {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.over) return
        stopMedia()
        update { it.copy(phase = LivePhase.Ending, endReason = reason) }
        if (ui.roomId == 0L) {
            // Start still in flight: bumping the session makes its reply close
            // the room instead of joining it (see goLive).
            teardown()
            update { it.copy(phase = LivePhase.Ended) }
            return
        }
        val mine = session
        scope.launch {
            var r = Xani.calls.liveEnd(ui.roomId, reason)
            if (!r.ok && r.isTransport) {
                delay(600)
                r = Xani.calls.liveEnd(ui.roomId, reason)
            }
            if (mine != session) return@launch
            val sum = r.value?.takeIf { r.ok }
            update {
                it.copy(
                    phase = LivePhase.Ended,
                    // Offline at the very end: the card still shows what we know; the
                    // server's sweeper closes the room and records the same duration.
                    summary = sum ?: LiveSummaryData(
                        roomId = it.roomId,
                        durationSeconds = it.elapsedSeconds().toInt(),
                        peakViewers = it.peakViewers.toInt(),
                        totalJoins = 0,
                        uniqueViewers = 0,
                        reason = reason,
                    ),
                )
            }
        }
    }

    /**
     * Leave the surface. A host who has not ended yet ends (a screen going
     * away must never leave a ghost room); a viewer releases the seat; a
     * session already over is simply dismissed.
     */
    fun leave() {
        val ui = _state.value ?: return
        if (ui.over) { dismiss(); return }
        if (ui.role == LiveRole.Host) { endLive(); return }
        val mine = session
        teardown()
        _state.value = null
        if (ui.roomId == 0L) return
        scope.launch {
            Xani.calls.liveLeave(ui.roomId, ui.transport)
            if (mine != session) return@launch
        }
    }

    /** Drop a finished session (after the end card / host-ended dialog was acknowledged). */
    fun dismiss() {
        teardown()
        _state.value = null
    }

    fun toggleMute() {
        val ui = _state.value ?: return
        if (ui.role == LiveRole.Viewer) return
        val muted = !ui.muted
        update { it.copy(muted = muted) }
        room?.setMuted(muted)
        // My seat box everywhere shows the same mic; a blip here costs only
        // the icon, the track itself is already muted.
        if (ui.roomId != 0L) {
            val mine = session
            scope.launch {
                Xani.calls.stageMute(ui.roomId, muted)
                if (mine != session) return@launch
            }
        }
    }

    // ─── stage: co-hosts (video) and seats (audio) ───────────────────────────

    /** One `live_stage` frame: the whole stage, replaced. */
    private fun onStage(st: StageData) {
        val ui = _state.value ?: return
        if (st.roomId != ui.roomId || st.rev < ui.stage.rev) return
        val me = ProfileStore.currentUserId
        val mine = st.member(me)
        update {
            it.copy(
                stage = st,
                seat = if (it.role == LiveRole.Host) 0 else mine?.seat ?: it.seat,
                // My request is answered the moment I am up, or moot when the door closes.
                requested = it.requested && mine == null && st.requests,
                room = it.room.copy(seats = if (st.kind == "audio") st.seats else it.room.seats, requests = st.requests),
            )
        }
        // The snapshot says I am not up, and I sat long enough ago for that to
        // be the truth rather than a frame from before I sat: step down. This
        // is the safety net under live_stage_off.
        if (ui.role == LiveRole.Guest && mine == null && SystemClock.elapsedRealtime() - satAtMs > STAGE_SETTLE_MS) {
            stepDown(null, "host_dropped")
        }
    }

    private const val STAGE_SETTLE_MS = 5_000L

    /** Viewer (video): ask the host to co-host. A second tap withdraws. */
    fun requestStage() {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Viewer || ui.roomId == 0L || ui.over) return
        val cancel = ui.requested
        update { it.copy(requested = !cancel) }
        val mine = session
        scope.launch {
            val r = Xani.calls.stageRequest(ui.roomId, cancel = cancel)
            if (mine != session) return@launch
            if (!r.ok && !cancel) {
                update { it.copy(requested = false) }
                _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
            }
        }
    }

    /** Host: accept | reject | drop one person. Optimistic on the queue. */
    fun decideStage(userId: Long, op: String) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L || ui.over) return
        if (op == "accept" || op == "reject") {
            update { it.copy(requests = it.requests.filterNot { q -> q.user.id == userId }) }
        }
        val mine = session
        scope.launch {
            val r = Xani.calls.stageDecide(ui.roomId, userId, op)
            if (mine != session) return@launch
            if (!r.ok) _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
        }
    }

    /** Host: open or close co-host requests (video). */
    fun setStageRequests(open: Boolean) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L || ui.over) return
        update { it.copy(room = it.room.copy(requests = open), stage = it.stage.copy(requests = open)) }
        val mine = session
        scope.launch {
            val r = Xani.calls.stageSettings(ui.roomId, requests = open)
            if (mine != session) return@launch
            r.value?.let { onStage(it) }
        }
    }

    /** Host (audio): grow (or shrink to the last occupied) the seat count. Nobody moves. */
    fun setSeats(n: Int) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L || ui.over) return
        val want = n.coerceIn(2, 35)
        if (want == ui.stage.seats) return
        val mine = session
        scope.launch {
            val r = Xani.calls.stageSettings(ui.roomId, seats = want)
            if (mine != session) return@launch
            val st = r.value
            if (r.ok && st != null) onStage(st) else if (!r.ok) _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
        }
    }

    /** Viewer (audio): sit on [seat] (0 = first free). The reply's door makes me a publisher. */
    fun takeSeat(seat: Int) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Viewer || ui.roomId == 0L || ui.over || ui.stageBusy) return
        update { it.copy(stageBusy = true) }
        val mine = session
        scope.launch {
            val r = Xani.calls.stageTake(ui.roomId, seat)
            if (mine != session) return@launch
            val data = r.value
            if (r.ok && data != null) {
                goUp(data.sfu, data.seat)
            } else {
                update { it.copy(stageBusy = false) }
                _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
            }
        }
    }

    /** Guest: step down and keep watching. The seat frees; I stay in the room. */
    fun leaveStage() {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Guest || ui.roomId == 0L || ui.over || ui.stageBusy) return
        update { it.copy(stageBusy = true) }
        val mine = session
        scope.launch {
            val r = Xani.calls.stageLeave(ui.roomId)
            if (mine != session) return@launch
            stepDown(r.value, "left")
        }
    }

    /** Swap my viewer seat for a publishing one at [seat]. */
    private fun goUp(sfu: RoomJoinData, seat: Int) {
        val ui = _state.value ?: return
        if (ui.over) return
        room?.let { runFeature(Unit) { it.close() } }
        room = null
        satAtMs = SystemClock.elapsedRealtime()
        update {
            it.copy(
                role = LiveRole.Guest, transport = "sfu", phase = LivePhase.Connecting,
                seat = seat, requested = false, stageBusy = false, muted = false,
            )
        }
        join(sfu, publish = true, video = ui.room.kind == "video")
    }

    /**
     * Back to the audience: close the publishing session and reopen a
     * subscribe-only one — the door in hand if the server gave one, else a
     * fresh Join. Never an ending: the room is still there to watch.
     */
    private fun stepDown(sfu: RoomJoinData?, reason: String) {
        val ui = _state.value ?: return
        if (ui.over || ui.role != LiveRole.Guest) {
            update { it.copy(stageBusy = false) }
            return
        }
        room?.let { runFeature(Unit) { it.close() } }
        room = null
        update {
            it.copy(
                role = LiveRole.Viewer, phase = LivePhase.Connecting, seat = 0, muted = false, cameraOff = false,
                stageBusy = false, requested = false,
                stage = it.stage.copy(members = it.stage.members.filterNot { m -> m.user.id == ProfileStore.currentUserId }),
            )
        }
        if (reason == "host_dropped") _notices.tryEmit(LiveNotice("host_dropped"))
        if (sfu != null) {
            join(sfu, publish = false, video = true)
            return
        }
        val mine = session
        val roomId = ui.roomId
        scope.launch {
            val r = Xani.calls.liveJoin(roomId)
            if (mine != session) return@launch
            val data = r.value
            if (!r.ok || data == null) {
                fail(if (r.statusCode == 410 || r.code == "not_found") "live_ended" else r.code.ifBlank { "failed" }, r.message)
                return@launch
            }
            update { it.copy(transport = data.mode, hlsUrl = data.hlsUrl, stage = data.stage).synced(data.room) }
            val door = data.sfu
            if (data.mode == "sfu" && door != null) join(door, publish = false, video = true)
            else if (data.hlsUrl.isBlank()) fail("live_unavailable") else update { it.copy(phase = LivePhase.Live) }
        }
    }

    /** A co-host's picture for their tile; null until their track lands. */
    fun remoteView(context: Context, userId: Long): View? = runFeature(null) {
        if (userId == 0L) return@runFeature null
        room?.remoteView(context, userId)
    }

    // ─── PK battles ──────────────────────────────────────────────────────────

    /** Host: invite [userId] for [durationSec] (60..3600). */
    fun invitePk(userId: Long, durationSec: Int) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L || ui.over) return
        val mine = session
        scope.launch {
            val r = Xani.calls.pkInvite(ui.roomId, userId, durationSec)
            if (mine != session) return@launch
            _notices.tryEmit(if (r.ok) LiveNotice("pk_sent") else LiveNotice(r.code.ifBlank { "failed" }, r.message))
        }
    }

    fun acceptPkInvite() {
        val inv = _state.value?.pkInvite ?: return
        val mine = session
        scope.launch {
            val r = Xani.calls.pkAccept(inv.id)
            if (mine != session) return@launch
            val data = r.value
            if (r.ok && data != null) {
                update { it.copy(pk = data.pk, pkInvite = null) }
                openPkPublish(data.sfu)
            } else if (!r.ok) {
                update { it.copy(pkInvite = null) }
                _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
            }
        }
    }

    fun declinePkInvite() {
        val inv = _state.value?.pkInvite ?: return
        update { it.copy(pkInvite = null) }
        scope.launch { Xani.calls.pkDecline(inv.id) }
    }

    fun endPk() {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L) return
        scope.launch { Xani.calls.pkEnd(ui.roomId) }
    }

    fun rematchPk(durationSec: Int = 0) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L) return
        scope.launch {
            val r = Xani.calls.pkRematch(ui.roomId, durationSec)
            if (!r.ok) _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
        }
    }

    private fun openPkPublish(sfu: RoomJoinData) {
        if (sfu.token.isBlank() || sfu.url.isBlank()) return
        val eng = engine ?: return
        val mine = session
        closePkRoom()
        pkRoom = runFeature(null) {
            eng.joinRoom(sfu.url, sfu.token, video = true, publish = true, object : RtcRoom.Listener {
                override fun onLink(link: RtcLink) = Unit
                override fun onParticipants(userIds: List<Long>) = Unit
                override fun onRemoteVideo(userId: Long, present: Boolean) = Unit
            })
        }
        if (mine != session) closePkRoom()
    }

    private fun closePkRoom() {
        pkRoom?.let { runFeature(Unit) { it.close() } }
        pkRoom = null
    }

    fun toggleCamera() {
        val ui = _state.value ?: return
        if (ui.role == LiveRole.Viewer) return
        update { it.copy(cameraOff = !ui.cameraOff) }
        room?.setVideoEnabled(ui.cameraOff)
    }

    fun switchCamera() = room?.switchCamera() ?: Unit

    /**
     * Publish mic (and camera in a video room) again to match the UI state.
     * A viewer promoted to guest joins the SFU before the runtime permission
     * prompt has been answered — LiveKit's enable then fails and the seat is
     * a silent, black tile. The screen calls this once the grant lands.
     */
    fun republish() {
        val ui = _state.value ?: return
        if (ui.role == LiveRole.Viewer || ui.over) return
        val r = room ?: return
        r.setMuted(ui.muted)
        if (ui.room.kind == "video") r.setVideoEnabled(!ui.cameraOff)
    }

    /** Host invites a viewer up as a guest (they get `live_guest` with an SFU seat). */
    fun inviteGuest(userId: Long) {
        val ui = _state.value ?: return
        if (ui.role != LiveRole.Host || ui.roomId == 0L) return
        scope.launch { Xani.calls.liveGuest(ui.roomId, userId) }
    }

    /**
     * Send a chat line. Optimistic: the bubble appears now and settles when
     * the server echoes the same [clientId]. Empty / over-cap text is ignored.
     */
    fun sendChat(text: String) = sendLine(text, sticker = null, to = null)

    /**
     * Send a [sticker] from the tray as a chat line, optionally aimed at [to]
     * (a person in the room; the line reads "@name" with the sticker under
     * it). Optimistic like text: the picture shows now, settles on the echo.
     */
    fun sendSticker(sticker: StickerData, to: LivePersonData? = null) = sendLine("", sticker, to)

    private fun sendLine(text: String, sticker: StickerData?, to: LivePersonData?) {
        val ui = _state.value ?: return
        if (ui.over || ui.roomId == 0L) return
        val body = text.trim().take(200)
        if (body.isEmpty() && sticker == null) return
        val target = to?.takeIf { it.id != 0L && it.id != ProfileStore.currentUserId }
        val clientId = UUID.randomUUID().toString()
        val me = ProfileStore.state.value
        // My worn frame and badge dress the optimistic bubble; the server echo
        // settles the line with the same values, so nothing flickers.
        val myFrame = FrameStore.frames.value[ProfileStore.currentUserId]
        val myBadge = FrameStore.badges.value[ProfileStore.currentUserId]
        val optimistic = LiveChatData(
            id = 0L,
            roomId = ui.roomId,
            clientId = clientId,
            user = LivePersonData(
                id = ProfileStore.currentUserId,
                username = me.username,
                fullName = me.displayName,
                avatarUrl = me.avatarUrl,
                role = when (ui.role) {
                    LiveRole.Host -> "host"
                    LiveRole.Guest -> "guest"
                    LiveRole.Viewer -> "viewer"
                },
                frameUrl = myFrame?.url.orEmpty(),
                frameMime = myFrame?.mime.orEmpty(),
                badgeUrl = myBadge?.url.orEmpty(),
                badgeMime = myBadge?.mime.orEmpty(),
            ),
            text = body,
            atMs = System.currentTimeMillis(),
            sticker = sticker,
            to = target,
        )
        update { it.copy(chat = (it.chat + optimistic).takeLast(LiveUi.ChatCap)) }
        val mine = session
        val roomId = ui.roomId
        scope.launch {
            val r = Xani.calls.liveChat(roomId, body, clientId, stickerId = sticker?.id ?: 0L, toUserId = target?.id ?: 0L)
            if (mine != session) return@launch
            val line = r.value
            if (r.ok && line != null) {
                update { it.copy(chat = it.chat.settle(line)) }
            } else if (!r.ok) {
                // Drop the optimistic bubble so a refused send does not linger.
                update { it.copy(chat = it.chat.filterNot { m -> m.clientId == clientId && m.id == 0L }) }
                if (r.code == "live_muted") _notices.tryEmit(LiveNotice("chat_muted"))
            }
        }
    }

    fun localView(context: Context): View? = runFeature(null) { room?.localView(context) }

    /** Host's stream for SFU viewers/guests; null on the HLS path. */
    fun hostView(context: Context): View? = runFeature(null) {
        val hostId = _state.value?.room?.hostId ?: return@runFeature null
        if (hostId == 0L) return@runFeature null
        room?.remoteView(context, hostId)
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private fun onEvent(e: CallEvent) {
        val ui = _state.value ?: return
        when (e) {
            is CallEvent.LiveViewers -> if (e.roomId == ui.roomId) update {
                it.copy(viewers = e.viewers, peakViewers = maxOf(it.peakViewers, e.viewers))
            }
            is CallEvent.LiveHost -> if (e.roomId == ui.roomId && ui.role != LiveRole.Host) {
                update { it.copy(hostAway = e.away, room = it.room.copy(hostAway = e.away)) }
            }
            is CallEvent.LiveChat -> if (e.message.roomId == ui.roomId) {
                update { it.copy(chat = it.chat.settle(e.message)) }
            }
            is CallEvent.LiveEnded -> if (e.roomId == ui.roomId && ui.phase != LivePhase.Ended) {
                stopMedia()
                update {
                    it.copy(
                        phase = LivePhase.Ended,
                        endReason = e.reason.ifBlank { "host_ended" },
                        summary = e.summary ?: it.summary,
                        hostAway = false,
                    )
                }
            }
            is CallEvent.LiveGuest -> if (e.roomId == ui.roomId && ui.role == LiveRole.Viewer) {
                // Promoted (accepted or invited): swap the viewer seat for a publishing one.
                goUp(e.sfu, e.seat)
            }
            is CallEvent.LiveStageRequest -> if (e.roomId == ui.roomId && ui.role == LiveRole.Host) update {
                val rest = it.requests.filterNot { q -> q.user.id == e.user.id }
                it.copy(
                    requests = if (e.op == "remove") rest else (rest + StageRequestData(e.user, e.atMs)).takeLast(LiveUi.RequestCap),
                )
            }
            is CallEvent.LiveStageDenied -> if (e.roomId == ui.roomId) {
                update { it.copy(requested = false) }
                _notices.tryEmit(LiveNotice("stage_denied"))
            }
            is CallEvent.LiveStageOff -> if (e.roomId == ui.roomId && ui.role == LiveRole.Guest) {
                stepDown(e.sfu, e.reason)
            }
            is CallEvent.LivePkInvite -> {
                when (e.invite.op) {
                    // Invitee side: the inviter withdrew or the clock ran out.
                    "remove" -> update {
                        if (it.pkInvite?.id == e.invite.id) it.copy(pkInvite = null) else it
                    }
                    // Inviter side: the answer (or the lack of one).
                    "declined" -> if (ui.role == LiveRole.Host) _notices.tryEmit(LiveNotice("pk_declined"))
                    "expired" -> if (ui.role == LiveRole.Host) _notices.tryEmit(LiveNotice("pk_expired"))
                    else -> if (!ui.over && ui.pk == null) update { it.copy(pkInvite = e.invite) }
                }
            }
            is CallEvent.LivePkStart -> if (ui.role == LiveRole.Host) {
                update { it.copy(pk = e.start.pk, pkInvite = null) }
                // Cross-publish into the opponent's SFU so their audience sees us
                // without anyone leaving their room. Camera may already be on the
                // primary seat; a failed second publish falls back to HLS on their side.
                openPkPublish(e.start.sfu)
            }
            // A moderator showed me the door: the room ends for this device
            // with its own reason, so the surface says so instead of "host ended".
            is CallEvent.LiveKicked -> if (e.roomId == ui.roomId && ui.role != LiveRole.Host && !ui.over) {
                stopMedia()
                update { it.copy(phase = LivePhase.Ended, endReason = e.reason.ifBlank { "kicked" }, hostAway = false) }
                _notices.tryEmit(LiveNotice(e.reason.ifBlank { "kicked" }))
            }
            is CallEvent.LiveMuted -> if (e.roomId == ui.roomId) {
                update { it.copy(mutedUntilMs = e.untilMs) }
                _notices.tryEmit(LiveNotice(if (e.untilMs > 0) "chat_muted" else "chat_unmuted"))
            }
            is CallEvent.LiveMods -> if (e.roomId == ui.roomId) {
                update { it.copy(mods = if (e.mod) it.mods + e.user.id else it.mods - e.user.id) }
                if (e.user.id == ProfileStore.currentUserId) _notices.tryEmit(LiveNotice(if (e.mod) "made_mod" else "mod_removed"))
            }
            // Entrances arrive on the per-user stream (the room fanout targets
            // each person's own channel), so this is where everyone — including
            // the person walking in — actually hears about them.
            is CallEvent.LiveEnter -> if (e.enter.roomId == ui.roomId && e.enter.entranceUrl.isNotEmpty()) {
                _entrances.tryEmit(e.enter)
            }
            else -> Unit
        }
    }

    // ─── hearts · reactions · moderation ─────────────────────────────────────

    private var likePending = 0
    private var likeFlush: Job? = null

    /**
     * One heart tapped. Taps are batched: the first starts a short timer,
     * every tap until it fires adds one, then a single request carries the
     * count. The room sees them as one frame; my own screen already floated
     * each heart the moment the finger landed.
     */
    fun like() {
        val ui = _state.value ?: return
        if (ui.over || ui.roomId == 0L) return
        likePending++
        update { it.copy(likes = it.likes + 1) }
        if (likeFlush?.isActive == true) return
        val mine = session
        val roomId = ui.roomId
        likeFlush = scope.launch {
            delay(700)
            val n = likePending
            likePending = 0
            if (n <= 0 || mine != session) return@launch
            val r = Xani.calls.like(roomId, n)
            if (mine != session) return@launch
            r.value?.let { total -> update { if (total > it.likes) it.copy(likes = total) else it } }
        }
    }

    /** Put [emoji] on chat line [line] for everyone (tap the same one again to take it off). */
    fun reactChat(line: LiveChatData, emoji: String) {
        val ui = _state.value ?: return
        if (ui.over || ui.roomId == 0L || line.id == 0L || emoji.isEmpty()) return
        if (ui.chatMuted) { _notices.tryEmit(LiveNotice("chat_muted")); return }
        val remove = line.myReaction == emoji
        update { it.copy(chat = it.chat.react(line.id, emoji, add = !remove, mine = true)) }
        val mine = session
        val roomId = ui.roomId
        scope.launch {
            val r = Xani.calls.reactChat(roomId, line.id, emoji, remove)
            if (mine != session) return@launch
            if (!r.ok) {
                update { it.copy(chat = it.chat.react(line.id, emoji, add = remove, mine = true)) }
                _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
            }
        }
    }

    /**
     * Host or moderator: [action] mute | unmute | block | unblock | kick on
     * [userId], [minutes] for the timed ones. The server answers with the
     * moment it lifts; the room learns through its own frames.
     */
    fun moderate(userId: Long, action: String, minutes: Int = 0, then: (ok: Boolean, untilMs: Long) -> Unit = { _, _ -> }) {
        val ui = _state.value ?: return
        if (ui.over || ui.roomId == 0L || !ui.canModerate || userId == 0L) return
        val mine = session
        val roomId = ui.roomId
        scope.launch {
            val r = Xani.calls.moderate(roomId, userId, action, minutes)
            if (mine != session) return@launch
            if (!r.ok) _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
            else _notices.tryEmit(LiveNotice("mod_$action"))
            then(r.ok, r.value ?: 0L)
        }
    }

    /** Host: make [userId] a moderator for [days] (≤ 0 removes). */
    fun setModerator(userId: Long, days: Int, then: (ok: Boolean) -> Unit = {}) {
        val ui = _state.value ?: return
        if (ui.over || ui.roomId == 0L || ui.role != LiveRole.Host || userId == 0L) return
        val mine = session
        val roomId = ui.roomId
        scope.launch {
            val r = Xani.calls.setModerator(roomId, userId, days)
            if (mine != session) return@launch
            if (!r.ok) _notices.tryEmit(LiveNotice(r.code.ifBlank { "failed" }, r.message))
            else update { it.copy(mods = if (days > 0) it.mods + userId else it.mods - userId) }
            then(r.ok)
        }
    }

    /** Host / moderator: the current moderator list, or null when refused. */
    suspend fun moderators(): List<LiveModeratorData>? {
        val ui = _state.value ?: return null
        if (ui.roomId == 0L) return null
        val r = Xani.calls.moderators(ui.roomId)
        val items = r.value ?: return null
        update { it.copy(mods = items.map { m -> m.user.id }.toSet()) }
        return items
    }

    /**
     * Build the media engine (factory + GPU context, ~150 ms) off Main while
     * the start/join request is in flight, so join() finds it ready and the
     * frame that shows the room never waits on it.
     */
    private fun warmEngine() {
        if (engine != null || !RtcEngineFactory.AVAILABLE) return
        scope.launch {
            val e = withContext(Dispatchers.Default) { RtcEngineFactory.create(app) } ?: return@launch
            if (engine == null) engine = e else runFeature(Unit) { e.release() }
        }
    }

    private fun join(sfu: RoomJoinData, publish: Boolean, video: Boolean) {
        val eng = engine ?: RtcEngineFactory.create(app)?.also { engine = it }
        if (eng == null) { fail("live_unavailable"); return }
        seat = sfu
        this.publish = publish
        this.video = video
        val mine = session
        room = runFeature(null) {
            eng.joinRoom(sfu.url, sfu.token, video, publish, object : RtcRoom.Listener {
                // Qualified on purpose: an unqualified `onLink` here is this listener's
                // own override, and post{} would recurse on Main until the ANR dialog.
                override fun onLink(link: RtcLink): Unit = post(mine) { this@LiveSession.onLink(link) }
                override fun onParticipants(userIds: List<Long>) = Unit
                override fun onRemoteVideo(userId: Long, present: Boolean): Unit = post(mine) {
                    // Any remote track change re-keys the renderers. The old
                    // host-only filter raced the join: the track event could
                    // land before the room told us who the host is, nothing
                    // bumped afterwards, and the viewer stared at black.
                    update { it.copy(mediaRev = it.mediaRev + 1) }
                }
            })
        }
        if (room == null) { fail("failed"); return }
        update { it.copy(mediaRev = it.mediaRev + 1) }
    }

    private fun onLink(link: RtcLink) {
        val ui = _state.value ?: return
        if (ui.over) return
        when (link) {
            RtcLink.Connected -> {
                lostAtMs = 0L
                rejoin?.cancel(); rejoin = null
                update { it.copy(phase = LivePhase.Live) }
            }
            RtcLink.Disconnected, RtcLink.Failed -> {
                // The room client resumes on its own; we only show it and start the clock.
                if (lostAtMs == 0L) lostAtMs = SystemClock.elapsedRealtime()
                update { it.copy(phase = LivePhase.Reconnecting) }
            }
            RtcLink.Closed -> {
                // The client gave up: rebuild the session on the same seat, backing off,
                // until the server's grace window closes. Weak Wi-Fi is not an ending.
                if (lostAtMs == 0L) lostAtMs = SystemClock.elapsedRealtime()
                update { it.copy(phase = LivePhase.Reconnecting) }
                scheduleRejoin()
            }
            RtcLink.New, RtcLink.Connecting -> Unit
        }
    }

    private fun scheduleRejoin() {
        if (rejoin?.isActive == true) return
        val mine = session
        rejoin = scope.launch {
            var wait = 1_000L
            while (mine == session) {
                val ui = _state.value ?: return@launch
                if (ui.over) return@launch
                val down = SystemClock.elapsedRealtime() - lostAtMs
                if (down > graceSeconds * 1000L) {
                    if (ui.role == LiveRole.Host) endLive("lost") else {
                        stopMedia()
                        update { it.copy(phase = LivePhase.Ended, endReason = "lost") }
                    }
                    return@launch
                }
                delay(wait)
                if (mine != session) return@launch
                val s = seat ?: return@launch
                room?.let { runFeature(Unit) { it.close() } }
                room = null
                join(s, publish, video)
                // join() fires Connected (which cancels us) or Closed (which lets the loop continue).
                wait = (wait * 2).coerceAtMost(10_000L)
                delay(8_000L) // give the attempt time to settle before another
            }
        }
    }

    /**
     * Presence + clock sync. A missed beat is the network, not the end; the
     * server's grace decides that and says so with `live=false`.
     */
    private fun startHeartbeat(mine: Int, mode: String) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (mine == session) {
                delay(heartbeatSeconds * 1000L)
                val ui = _state.value ?: return@launch
                if (mine != session || ui.over || ui.roomId == 0L) return@launch
                val r = Xani.calls.liveHeartbeat(ui.roomId, if (ui.role == LiveRole.Viewer) mode else "sfu")
                if (mine != session) return@launch
                val data = r.value
                when {
                    r.ok && data != null && !data.live -> {
                        stopMedia()
                        // Prefer the server's summary when the stream carries one;
                        // otherwise keep the clock we already synced so the end card
                        // still shows how long the live ran.
                        update {
                            it.copy(
                                phase = LivePhase.Ended,
                                endReason = if (it.role == LiveRole.Host) "lost" else "host_ended",
                                summary = it.summary ?: LiveSummaryData(
                                    roomId = it.roomId,
                                    durationSeconds = it.elapsedSeconds().toInt(),
                                    peakViewers = it.peakViewers.toInt(),
                                    totalJoins = 0,
                                    uniqueViewers = 0,
                                    reason = if (it.role == LiveRole.Host) "lost" else "host_ended",
                                ),
                                hostAway = false,
                            )
                        }
                        return@launch
                    }
                    r.ok && data != null -> update { it.synced(data.room) }
                    r.statusCode == 404 || r.statusCode == 410 -> {
                        stopMedia()
                        update {
                            it.copy(
                                phase = LivePhase.Ended,
                                endReason = "host_ended",
                                summary = it.summary ?: LiveSummaryData(
                                    roomId = it.roomId,
                                    durationSeconds = it.elapsedSeconds().toInt(),
                                    peakViewers = it.peakViewers.toInt(),
                                    totalJoins = 0,
                                    uniqueViewers = 0,
                                    reason = "host_ended",
                                ),
                                hostAway = false,
                            )
                        }
                        return@launch
                    }
                    else -> Unit // transport blip: keep the media session, try again next beat
                }
            }
        }
    }

    private fun LiveUi.synced(room: LiveRoomData): LiveUi = copy(
        room = if (room.id != 0L) room else this.room,
        viewers = room.viewers,
        roomCoins = maxOf(roomCoins, room.giftCoins),
        peakViewers = maxOf(peakViewers, room.viewers, room.peakViewers),
        syncDurationSeconds = room.durationSeconds,
        syncAtMs = SystemClock.elapsedRealtime(),
        // Heartbeat carries host_away so a mid-gap join learns the same truth.
        hostAway = if (role == LiveRole.Host) false else room.hostAway,
        pk = room.pk ?: pk,
    )

    private fun fail(reason: String, message: String = "") {
        teardown()
        update { it.copy(phase = LivePhase.Ended, endReason = reason, error = message, hostAway = false) }
    }

    /** Media and timers stop; the state stays so the surface can show why. */
    private fun stopMedia() {
        heartbeat?.cancel(); heartbeat = null
        rejoin?.cancel(); rejoin = null
        roomStream?.cancel(); roomStream = null
        coinsFlush?.cancel(); coinsFlush = null
        coinsSeen.set(0L)
        room?.let { runFeature(Unit) { it.close() } }
        room = null
        closePkRoom()
        seat = null
        lostAtMs = 0L
    }

    private fun teardown() {
        session++
        stopMedia()
        hold?.cancel(); hold = null
    }

    private inline fun update(f: (LiveUi) -> LiveUi) {
        _state.value = _state.value?.let(f)
    }

    private fun post(mine: Int, f: () -> Unit) {
        scope.launch { if (mine == session) runFeature(Unit, f) }
    }
}

/** Replace an optimistic bubble (same clientId, id=0) or append; never grow past the cap. */
private fun List<LiveChatData>.settle(line: LiveChatData): List<LiveChatData> {
    val i = indexOfFirst { it.clientId.isNotEmpty() && it.clientId == line.clientId }
    val next = if (i >= 0) toMutableList().also { it[i] = line } else {
        if (any { it.id != 0L && it.id == line.id }) this else this + line
    }
    return next.takeLast(LiveUi.ChatCap)
}

/**
 * Move one emoji on one line: count up or down, and remember mine. A line
 * this device never had (scrolled off, joined late) is left alone.
 */
private fun List<LiveChatData>.react(lineId: Long, emoji: String, add: Boolean, mine: Boolean): List<LiveChatData> {
    val i = indexOfFirst { it.id == lineId }
    if (i < 0) return this
    val line = this[i]
    val counts = line.reactions.toMutableMap()
    var my = line.myReaction
    if (mine) {
        // One reaction per person: swapping takes the old one off first.
        if (my.isNotEmpty() && my != emoji) counts[my] = ((counts[my] ?: 1) - 1).coerceAtLeast(0)
        my = if (add) emoji else ""
    }
    counts[emoji] = ((counts[emoji] ?: 0) + if (add) 1 else -1).coerceAtLeast(0)
    counts.entries.removeAll { it.value <= 0 }
    return toMutableList().also { it[i] = line.copy(reactions = counts, myReaction = my) }
}
