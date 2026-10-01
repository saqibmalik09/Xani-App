package app.xani.feature.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.view.View
import app.xani.core.isolate.runFeature
import app.xani.core.net.ApiResponse
import app.xani.core.net.AppForeground
import app.xani.core.net.AuthState
import app.xani.core.net.CallData
import app.xani.core.net.CallEvent
import app.xani.core.net.IceConfigData
import app.xani.core.net.LiveFeed
import app.xani.core.net.RoomJoinData
import app.xani.core.net.Xani
import app.xani.feature.call.rtc.RtcEngineFactory
import app.xani.feature.profile.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

/**
 * One call at a time, app-wide. Owns the signalling state machine, the media
 * session, audio routing and the reconnect policy; screens only render [state]
 * and call the verbs below. Every engine callback hops to Main, so state changes
 * are sequential and the UI never sees a half-applied transition.
 *
 * Reconnect contract (matches the server): media may drop for up to the ICE
 * grace window (default 150 s) while we restart ICE with backoff and show
 * "Reconnecting…". Only End, the remote End, or grace expiry finishes the call.
 */
object CallController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<CallUi?>(null)
    val state: StateFlow<CallUi?> = _state

    /** False when this build shipped without the media stack. */
    val available: Boolean get() = RtcEngineFactory.AVAILABLE

    private lateinit var app: Context
    private var attached = false
    private var audio: CallAudio? = null
    private var engine: RtcEngine? = null

    // Per-call session; all reset by [teardown].
    private var peer: RtcPeer? = null
    private var room: RtcRoom? = null
    private var ice: IceConfigData = IceConfigData.Empty
    private var peerUser = 0L
    private var peerDevice = 0L
    private var remoteSet = false
    private var pendingOffer: Pair<String, String>? = null
    private val pendingLocalCandidates = ArrayList<JSONObject>()
    private val pendingRemoteCandidates = ArrayList<JSONObject>()
    private val pendingSignals = ArrayList<CallEvent.Signal>()
    private var signals: Channel<Outbound>? = null
    private var sender: Job? = null
    private var hold: Job? = null
    private var ringTimer: Job? = null
    private var graceTimer: Job? = null
    private var restartTimer: Job? = null
    private var clearTimer: Job? = null
    private var restarts = 0
    private var session = 0

    // The path under the call (Wi-Fi ↔ cellular); a change restarts ICE at once.
    private var network: Network? = null
    private var lastIncomingCheckMs = 0L

    private class Outbound(val callId: Long, val toUser: Long, val toDevice: Long, val type: String, val payload: JSONObject)

    fun attach(context: Context) {
        if (attached) return
        attached = true
        app = context.applicationContext
        audio = CallAudio(app)
        scope.launch { LiveFeed.call.collect { runFeature(Unit) { onEvent(it) } } }
        // The stream came back after a gap: ask the server whether the call
        // survived — or, with no call up, whether one started ringing while we
        // were away (the invite frame went to a stream that did not exist yet).
        scope.launch {
            LiveFeed.connected.drop(1).filter { it }.collect {
                runFeature(Unit) { if (_state.value?.active == true) resync() else checkIncoming() }
            }
        }
        // Coming to the foreground is the same question, answered over HTTP
        // even when the stream is still connecting.
        scope.launch {
            AppForeground.visible.filter { it }.collect { runFeature(Unit) { checkIncoming() } }
        }
        watchNetwork()
    }

    // ─── verbs ───────────────────────────────────────────────────────────────

    /** Dial. No-op when a call is already up: the UI shows that call instead. */
    fun start(conversationId: Long, chatId: Int?, peer: CallPeer, video: Boolean) {
        if (_state.value?.active == true) return
        val mine = ++session
        val ui = CallUi(
            callId = 0L, conversationId = conversationId, chatId = chatId, outgoing = true,
            video = video, phase = CallPhase.Outgoing, peer = peer, speaker = video,
        )
        _state.value = ui
        beginSession()
        if (!available) {
            finish(CallOutcome.Unavailable); return
        }
        if (!hasMedia(video)) {
            finish(CallOutcome.Failed); return
        }
        val clientId = UUID.randomUUID().toString()
        scope.launch {
            val r = Xani.calls.start(conversationId, if (video) "video" else "audio", clientId, relayOnly = false)
            if (mine != session) return@launch
            val data = r.value
            if (!r.ok || data == null) {
                finish(outcomeFor(r)); return@launch
            }
            ice = data.ice
            update { it.copy(callId = data.call.id, mode = data.call.mode, participants = joined(data.call)) }
            startRingTimer(RING_TIMEOUT_MS)
            CallService.ensureRunning(app)
            if (data.room != null) {
                joinRoom(data.room, publish = true)
            } else {
                // Gather ICE while it rings so the offer is on the wire the instant they accept.
                val p = createPeer(video) ?: run { finish(CallOutcome.Failed); return@launch }
                peerUser = peer.id
                p.offer(iceRestart = false)
            }
        }
    }

    fun accept() {
        val ui = _state.value ?: return
        if (ui.phase != CallPhase.Incoming) return
        val mine = session
        audio?.stopRingtone()
        IncomingCallNotifier.cancel(app)
        ringTimer?.cancel()
        if (!available) {
            finish(CallOutcome.Unavailable); return
        }
        if (!hasMedia(ui.video)) {
            finish(CallOutcome.Failed); return
        }
        update { it.copy(phase = CallPhase.Connecting) }
        audio?.beginCall(ui.speaker)
        // ICE was fetched when the ring arrived: open the camera and start
        // gathering now, in parallel with the accept round-trip, so the answer
        // is ready the moment the caller's offer lands.
        if (ui.mode == "p2p" && ice.servers.isNotEmpty() && peer == null) createPeer(ui.video)
        scope.launch {
            val r = Xani.calls.accept(ui.callId, relayOnly = false)
            if (mine != session) return@launch
            val data = r.value
            if (!r.ok || data == null) {
                finish(if (r.statusCode == 410) CallOutcome.Missed else outcomeFor(r)); return@launch
            }
            ice = data.ice
            update { it.copy(mode = data.call.mode, participants = joined(data.call)) }
            CallService.ensureRunning(app)
            if (data.room != null) {
                // Converted to a room while ringing: the early peer is not needed.
                peer?.let { runFeature(Unit) { it.close() } }; peer = null
                joinRoom(data.room, publish = true)
            } else {
                if (peer == null && createPeer(ui.video) == null) {
                    finish(CallOutcome.Failed); return@launch
                }
                // The caller's offer may have raced the accept response; replay it.
                val queued = pendingSignals.toList()
                pendingSignals.clear()
                queued.forEach { onSignal(it) }
            }
        }
    }

    fun reject() {
        val ui = _state.value ?: return
        if (ui.phase != CallPhase.Incoming) return
        val id = ui.callId
        clear()
        scope.launch { Xani.calls.reject(id, "declined") }
    }

    /** End from our side, whatever the phase. Never waits on the network. */
    fun end() {
        val ui = _state.value ?: return
        if (!ui.active) return
        val id = ui.callId
        val reason = if (ui.phase == CallPhase.Outgoing) "cancelled" else "hangup"
        finish(CallOutcome.Ended)
        if (id != 0L) scope.launch { Xani.calls.end(id, reason) }
    }

    fun toggleMute() {
        val ui = _state.value ?: return
        val muted = !ui.muted
        update { it.copy(muted = muted) }
        peer?.setMuted(muted)
        room?.setMuted(muted)
        sendSignal("mute", JSONObject().put("muted", muted))
    }

    fun toggleSpeaker() {
        val ui = _state.value ?: return
        val on = !ui.speaker
        update { it.copy(speaker = on) }
        audio?.setSpeaker(on)
    }

    fun toggleCamera() {
        val ui = _state.value ?: return
        val off = !ui.cameraOff
        if (!off && !hasPermission(Manifest.permission.CAMERA)) return
        update { it.copy(cameraOff = off, video = true, speaker = if (it.video) it.speaker else true) }
        if (!ui.video) audio?.setSpeaker(true)
        peer?.setVideoEnabled(!off)
        room?.setVideoEnabled(!off)
        sendSignal("camera", JSONObject().put("off", off))
    }

    /** Audio call → video: enables the camera track, which renegotiates on the wire. */
    fun upgradeToVideo() {
        val ui = _state.value ?: return
        if (ui.video && !ui.cameraOff) return
        if (!hasPermission(Manifest.permission.CAMERA)) return
        update { it.copy(video = true, cameraOff = false, speaker = true) }
        audio?.setSpeaker(true)
        peer?.setVideoEnabled(true)
        room?.setVideoEnabled(true)
        sendSignal("camera", JSONObject().put("off", false))
    }

    fun switchCamera() {
        update { it.copy(frontCamera = !it.frontCamera) }
        peer?.switchCamera()
        room?.switchCamera()
    }

    fun setMinimized(minimized: Boolean) = update { it.copy(minimized = minimized) }

    /** Pull another member in; the server converts a 1:1 into a room when needed. */
    fun addParticipant(userId: Long) {
        val ui = _state.value ?: return
        if (!ui.inMedia || ui.callId == 0L) return
        val mine = session
        scope.launch {
            val r = Xani.calls.add(ui.callId, userId)
            if (mine != session || !r.ok) return@launch
            r.data?.let { migrateToRoom(it) }
        }
    }

    fun localView(context: Context): View? = runFeature(null) { peer?.localView(context) ?: room?.localView(context) }

    fun remoteView(context: Context): View? = runFeature(null) {
        peer?.remoteView(context) ?: remoteUser()?.let { room?.remoteView(context, it) }
    }

    /** In a room the participant list includes us; the face on screen is the first other one. */
    private fun remoteUser(): Long? {
        val me = ProfileStore.currentUserId
        return _state.value?.participants?.firstOrNull { it != me && it != 0L }
    }

    // ─── inbound events ──────────────────────────────────────────────────────

    private fun onEvent(e: CallEvent) {
        val ui = _state.value
        when (e) {
            is CallEvent.Invite -> onInvite(e.call)
            is CallEvent.Ringing -> if (ui?.callId == e.callId && ui.phase == CallPhase.Outgoing && !ui.ringing) {
                update { it.copy(ringing = true) }
                audio?.startRingback()
            }
            is CallEvent.Accept -> if (ui?.callId == e.callId && ui.outgoing && ui.phase == CallPhase.Outgoing) {
                ringTimer?.cancel()
                audio?.stopRingback()
                audio?.beginCall(ui.speaker)
                peerUser = e.userId
                peerDevice = e.deviceId
                update { it.copy(phase = CallPhase.Connecting, participants = (it.participants + e.userId).distinct()) }
                flushLocal()
            }
            is CallEvent.Reject -> if (ui?.callId == e.callId && ui.phase == CallPhase.Outgoing) {
                finish(if (e.reason == "busy") CallOutcome.Busy else CallOutcome.Declined)
            }
            is CallEvent.Taken -> if (ui?.callId == e.callId && ui.phase == CallPhase.Incoming) clear()
            is CallEvent.End -> if (ui?.callId == e.callId) finish(outcomeFor(e.reason, ui))
            is CallEvent.Signal -> if (ui?.callId == e.callId) onSignal(e)
            is CallEvent.Participant -> if (ui?.callId == e.callId) update {
                val list = if (e.state == "joined") (it.participants + e.userId).distinct() else it.participants - e.userId
                it.copy(participants = list)
            }
            is CallEvent.Room -> if (ui != null && ui.inMedia) migrateToRoom(e.room)
            is CallEvent.LiveViewers, is CallEvent.LiveGuest, is CallEvent.LiveEnded,
            is CallEvent.LiveDirectory, is CallEvent.LiveHost, is CallEvent.LiveChat,
            is CallEvent.LiveStageRequest, is CallEvent.LiveStageDenied, is CallEvent.LiveStageOff,
            is CallEvent.LivePkInvite, is CallEvent.LivePkStart,
            is CallEvent.LiveKicked, is CallEvent.LiveMuted, is CallEvent.LiveMods,
            is CallEvent.LiveEnter -> Unit // owned by the live feature
        }
    }

    private fun onInvite(call: CallData, notify: Boolean = true) {
        if (call.callerId == ProfileStore.currentUserId) return
        if (_state.value?.active == true) {
            if (_state.value?.callId != call.id) scope.launch { Xani.calls.reject(call.id, "busy") }
            return
        }
        ++session
        val peer = CallPeer(
            id = call.callerId,
            name = call.caller.fullName.ifBlank { call.caller.username }.ifBlank { "…" },
            initials = initialsOf(call.caller.fullName.ifBlank { call.caller.username }),
            tint = androidx.compose.ui.graphics.Color(0xFF6D5DF6),
            avatarUrl = call.caller.avatarUrl,
        )
        val video = call.kind == "video"
        _state.value = CallUi(
            callId = call.id, conversationId = call.conversationId, chatId = null, outgoing = false,
            video = video, phase = CallPhase.Incoming, peer = peer, mode = call.mode, speaker = video,
            participants = joined(call),
        )
        beginSession()
        peerUser = call.callerId
        audio?.startRingtone()
        startRingTimer(RING_TIMEOUT_MS)
        // Nobody is looking at the app: the ring has to be a notification too,
        // or the phone sits in a pocket playing a tone with nothing to tap.
        if (notify && !AppForeground.visible.value) IncomingCallNotifier.show(app, call.id, peer.name, video)
        scope.launch { Xani.calls.ring(call.id) }
        warmForAccept(call.mode == "p2p")
    }

    /**
     * Spend the ring on the slow parts of connecting: TURN credentials and the
     * media engine (factory + GPU context, ~150 ms). Accept then only has to
     * open the camera, so audio is flowing about a second sooner.
     */
    private fun warmForAccept(p2p: Boolean) {
        if (!available) return
        val mine = session
        if (p2p && ice.servers.isEmpty()) {
            scope.launch {
                val r = Xani.calls.ice(relayOnly = false)
                val cfg = r.value ?: return@launch
                if (mine == session && ice.servers.isEmpty()) ice = cfg
            }
        }
        if (engine == null) {
            scope.launch {
                val e = withContext(Dispatchers.Default) { RtcEngineFactory.create(app) } ?: return@launch
                if (engine == null) engine = e else runFeature(Unit) { e.release() }
            }
        }
    }

    /**
     * Nothing is ringing on this device, but the server may hold a call for
     * us that started while the app was closed or the stream was down. Ring it
     * now through the same path an invite frame takes.
     */
    private fun checkIncoming() {
        if (_state.value?.active == true) return
        if (Xani.session.state.value !is AuthState.SignedIn) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastIncomingCheckMs < 1_500L) return
        lastIncomingCheckMs = now
        scope.launch {
            val r = Xani.calls.incoming()
            val call = r.value ?: return@launch
            if (call.state != "ringing" || call.id == 0L) return@launch
            if (_state.value?.active == true) return@launch
            onInvite(call)
        }
    }

    // ─── network path ────────────────────────────────────────────────────────

    private fun watchNetwork() {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        runFeature(Unit) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(net: Network) {
                    scope.launch { runFeature(Unit) { onNetworkAvailable(net) } }
                }

                override fun onLost(net: Network) {
                    scope.launch { if (network == net) network = null }
                }
            })
        }
    }

    /**
     * The default route moved (Wi-Fi → cellular, or back). ICE would notice in
     * 10–15 s of silence; we restart it now so the gap is under a second. The
     * offerer owns the restart; the answerer asks for one.
     */
    private fun onNetworkAvailable(net: Network) {
        val prev = network
        network = net
        if (prev == null || prev == net) return
        val ui = _state.value ?: return
        if (!ui.inMedia || ui.mode != "p2p" || peer == null) return
        restartTimer?.cancel(); restartTimer = null
        restarts = 0
        if (ui.outgoing) peer?.offer(iceRestart = true) else sendSignal("ice_restart", JSONObject())
    }

    /**
     * FCM woke us for a call. The notification goes up first, from the push
     * payload alone, so the phone rings before any network; then the call is
     * fetched and rung in-app through the same path the stream uses.
     */
    fun ringFromPush(callId: Long, callerName: String, video: Boolean) {
        if (callId == 0L) return
        val cur = _state.value
        if (cur?.callId == callId) return
        if (cur?.active == true) return // busy: the caller's ring times out or the stream path says busy
        IncomingCallNotifier.show(app, callId, callerName, video)
        scope.launch {
            val call = fetchRinging(callId) ?: return@launch
            if (_state.value?.active == true) return@launch
            onInvite(call, notify = false)
        }
    }

    /**
     * The user tapped the ring notification (open / answer / decline). Cold
     * start included: waits for the session, then hydrates the call if the
     * in-app ring is not already up.
     */
    fun openFromNotification(callId: Long, action: String) {
        if (callId == 0L) return
        IncomingCallNotifier.cancel(app)
        scope.launch {
            val ui = _state.value
            if (ui?.callId != callId || ui.phase != CallPhase.Incoming) {
                if (ui?.active == true) return@launch
                val call = fetchRinging(callId) ?: return@launch
                if (_state.value?.active == true) return@launch
                onInvite(call, notify = false)
            }
            val now = _state.value ?: return@launch
            if (now.callId != callId || now.phase != CallPhase.Incoming) return@launch
            when (action) {
                IncomingCallNotifier.ACTION_DECLINE -> reject()
                // Without permissions the incoming screen stays up and asks; accepting blind would fail.
                IncomingCallNotifier.ACTION_ACCEPT -> if (hasMedia(now.video)) accept()
            }
        }
    }

    /** The call as the server sees it, or null (and the notification gone) when it is no longer ringing. */
    private suspend fun fetchRinging(callId: Long): CallData? {
        withTimeoutOrNull(8_000L) { Xani.session.state.first { it !is AuthState.Unknown } }
        if (Xani.session.state.value !is AuthState.SignedIn) return null
        var r = Xani.calls.get(callId)
        if (!r.ok && r.isTransport) {
            delay(500)
            r = Xani.calls.get(callId)
        }
        val call = r.value
        if (!r.ok || call == null || call.state != "ringing") {
            IncomingCallNotifier.cancel(app)
            return null
        }
        return call
    }

    private fun onSignal(s: CallEvent.Signal) {
        val ui = _state.value ?: return
        when (s.type) {
            "mute" -> { update { it.copy(remoteMuted = s.payload.optBoolean("muted")) }; return }
            "camera" -> { update { it.copy(remoteVideo = !s.payload.optBoolean("off")) }; return }
        }
        val p = peer
        if (p == null) {
            // Callee: accept response not back yet, or room mode where no peer exists.
            if (ui.phase == CallPhase.Connecting && ui.mode == "p2p" && pendingSignals.size < 512) pendingSignals += s
            return
        }
        if (s.fromDevice != 0L) peerDevice = s.fromDevice
        when (s.type) {
            "offer" -> {
                val sdp = s.payload.optString("sdp")
                if (sdp.isEmpty()) return
                remoteSet = true
                p.setRemote("offer", sdp)
                drainRemoteCandidates(p)
            }
            "answer" -> {
                val sdp = s.payload.optString("sdp")
                if (sdp.isEmpty()) return
                remoteSet = true
                p.setRemote("answer", sdp)
                drainRemoteCandidates(p)
            }
            "ice" -> if (remoteSet) p.addCandidate(s.payload) else if (pendingRemoteCandidates.size < 256) pendingRemoteCandidates += s.payload
            // Callee could not reach us; we own the offer, so we restart.
            "ice_restart" -> if (ui.outgoing && ui.inMedia) p.offer(iceRestart = true)
        }
    }

    // ─── media session ───────────────────────────────────────────────────────

    private fun createPeer(video: Boolean): RtcPeer? {
        val eng = engine ?: RtcEngineFactory.create(app)?.also { engine = it } ?: return null
        val mine = session
        return runFeature(null) {
            eng.createPeer(ice.servers, ice.relayOnly, video, object : RtcPeer.Listener {
                override fun onLocalDescription(type: String, sdp: String): Unit = post(mine) {
                    val payload = JSONObject().put("sdp", sdp)
                    if (peerDevice == 0L && type == "offer") pendingOffer = type to sdp else sendSignal(type, payload)
                }

                override fun onIceCandidate(candidate: JSONObject): Unit = post(mine) {
                    if (peerDevice == 0L) { if (pendingLocalCandidates.size < 256) pendingLocalCandidates += candidate } else sendSignal("ice", candidate)
                }

                override fun onLink(link: RtcLink): Unit = post(mine) { this@CallController.onLink(link) }

                override fun onRemoteVideo(present: Boolean): Unit = post(mine) { update { it.copy(remoteVideo = present) } }
            })
        }?.also {
            peer = it
            it.setMuted(_state.value?.muted == true)
            update { it.copy(mediaRev = it.mediaRev + 1) }
        }
    }

    private fun joinRoom(join: RoomJoinData, publish: Boolean) {
        val eng = engine ?: RtcEngineFactory.create(app)?.also { engine = it }
        if (eng == null) { finish(CallOutcome.Unavailable); return }
        val ui = _state.value ?: return
        val mine = session
        room = runFeature(null) {
            eng.joinRoom(join.url, join.token, ui.video && !ui.cameraOff, publish, object : RtcRoom.Listener {
                override fun onLink(link: RtcLink): Unit = post(mine) { this@CallController.onLink(link) }
                override fun onParticipants(userIds: List<Long>): Unit = post(mine) { update { it.copy(participants = userIds) } }
                override fun onRemoteVideo(userId: Long, present: Boolean): Unit = post(mine) {
                    if (remoteUser() == userId) update { it.copy(remoteVideo = present, mediaRev = it.mediaRev + 1) }
                }
            })
        }
        if (room == null) { finish(CallOutcome.Failed); return }
        room?.setMuted(ui.muted)
        update {
            it.copy(
                mode = "sfu",
                phase = if (it.phase == CallPhase.Outgoing) it.phase else CallPhase.Connecting,
                mediaRev = it.mediaRev + 1,
            )
        }
    }

    private fun migrateToRoom(join: RoomJoinData) {
        val ui = _state.value ?: return
        if (ui.mode == "sfu" && room != null) return
        peer?.let { runFeature(Unit) { it.close() } }
        peer = null
        remoteSet = false
        joinRoom(join, publish = true)
    }

    private fun onLink(link: RtcLink) {
        val ui = _state.value ?: return
        if (!ui.active) return
        when (link) {
            RtcLink.Connected -> {
                graceTimer?.cancel(); graceTimer = null
                restartTimer?.cancel(); restartTimer = null
                restarts = 0
                update { it.copy(phase = CallPhase.Connected, connectedAt = if (it.connectedAt == 0L) SystemClock.elapsedRealtime() else it.connectedAt) }
            }
            RtcLink.Disconnected, RtcLink.Failed -> {
                if (ui.phase == CallPhase.Outgoing) return
                update { it.copy(phase = CallPhase.Reconnecting) }
                startGrace()
                scheduleRestart(immediate = link == RtcLink.Failed)
            }
            RtcLink.Closed -> if (ui.inMedia) finish(CallOutcome.Lost)
            RtcLink.New, RtcLink.Connecting -> Unit
        }
    }

    private fun startGrace() {
        if (graceTimer != null) return
        val mine = session
        graceTimer = scope.launch {
            delay(ice.reconnectGraceSeconds.coerceIn(30, 600) * 1000L)
            if (mine == session && _state.value?.phase == CallPhase.Reconnecting) {
                val id = _state.value?.callId ?: 0L
                finish(CallOutcome.Lost)
                if (id != 0L) Xani.calls.end(id, "lost")
            }
        }
    }

    /** Offerer restarts ICE; answerer nudges the offerer. Backoff 1.5 s → 8 s. */
    private fun scheduleRestart(immediate: Boolean) {
        if (restartTimer?.isActive == true) return
        val mine = session
        restartTimer = scope.launch {
            val wait = if (immediate && restarts == 0) 0L else (1_500L shl restarts.coerceAtMost(2)).coerceAtMost(8_000L)
            delay(wait)
            val ui = _state.value ?: return@launch
            if (mine != session || ui.phase != CallPhase.Reconnecting) return@launch
            restarts++
            when {
                ui.mode == "sfu" -> Unit // the room client owns its own resume
                ui.outgoing -> peer?.offer(iceRestart = true)
                else -> sendSignal("ice_restart", JSONObject())
            }
            restartTimer = null
            // Loop until Connected clears the phase or grace ends the call.
            scheduleRestart(immediate = false)
        }
    }

    private fun resync() {
        val ui = _state.value ?: return
        if (!ui.active || ui.callId == 0L) return
        val mine = session
        scope.launch {
            val r = Xani.calls.get(ui.callId)
            if (mine != session) return@launch
            if (r.statusCode == 410 || r.value?.state == "ended") finish(outcomeFor(r.value?.endReason ?: "lost", ui))
        }
    }

    // ─── signalling out ──────────────────────────────────────────────────────

    private fun flushLocal() {
        pendingOffer?.let { (type, sdp) -> sendSignal(type, JSONObject().put("sdp", sdp)) }
        pendingOffer = null
        pendingLocalCandidates.forEach { sendSignal("ice", it) }
        pendingLocalCandidates.clear()
    }

    private fun sendSignal(type: String, payload: JSONObject) {
        val ui = _state.value ?: return
        if (ui.callId == 0L || peerUser == 0L) return
        val ch = signals ?: return
        // Ordered, bounded; a full queue means the network is gone and grace will decide.
        ch.trySend(Outbound(ui.callId, peerUser, peerDevice, type, payload))
    }

    private fun startSender(ch: Channel<Outbound>) = scope.launch(Dispatchers.IO) {
        for (o in ch) {
            var r = Xani.calls.signal(o.callId, o.toUser, o.toDevice, o.type, o.payload)
            if (!r.ok && r.isTransport) {
                delay(400)
                r = Xani.calls.signal(o.callId, o.toUser, o.toDevice, o.type, o.payload)
            }
            if (!r.ok && (r.statusCode == 410 || r.code == "not_active")) {
                scope.launch { if (_state.value?.callId == o.callId) finish(CallOutcome.Ended) }
            }
        }
    }

    private fun drainRemoteCandidates(p: RtcPeer) {
        pendingRemoteCandidates.forEach { p.addCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    // ─── lifecycle ───────────────────────────────────────────────────────────

    private fun beginSession() {
        val ch = Channel<Outbound>(capacity = 512)
        signals = ch
        sender = startSender(ch)
        hold = scope.launch { LiveFeed.hold() }
    }

    private fun startRingTimer(ms: Long) {
        ringTimer?.cancel()
        val mine = session
        ringTimer = scope.launch {
            delay(ms)
            val ui = _state.value ?: return@launch
            if (mine != session) return@launch
            when (ui.phase) {
                CallPhase.Outgoing -> {
                    finish(CallOutcome.NoAnswer)
                    if (ui.callId != 0L) Xani.calls.end(ui.callId, "no_answer")
                }
                CallPhase.Incoming -> clear()
                else -> Unit
            }
        }
    }

    /** Terminal transition: tear media down now, show the outcome briefly, then clear. */
    private fun finish(outcome: CallOutcome) {
        val ui = _state.value ?: return
        if (!ui.active) return
        // A ring that ends before we answered is a missed call, not a screen to linger on.
        if (ui.phase == CallPhase.Incoming && outcome != CallOutcome.Unavailable && outcome != CallOutcome.Failed) {
            clear(); return
        }
        teardown()
        _state.value = ui.copy(phase = CallPhase.Ended, outcome = outcome, minimized = false)
        clearTimer = scope.launch {
            delay(if (outcome == CallOutcome.Ended) 1_200 else 1_800)
            if (_state.value?.phase == CallPhase.Ended) _state.value = null
        }
    }

    /** Silent clear (missed, taken by another device, declined by us). */
    private fun clear() {
        teardown()
        _state.value = null
    }

    private fun teardown() {
        session++
        IncomingCallNotifier.cancel(app)
        ringTimer?.cancel(); ringTimer = null
        graceTimer?.cancel(); graceTimer = null
        restartTimer?.cancel(); restartTimer = null
        clearTimer?.cancel(); clearTimer = null
        signals?.close(); signals = null
        sender = null
        hold?.cancel(); hold = null
        peer?.let { runFeature(Unit) { it.close() } }; peer = null
        room?.let { runFeature(Unit) { it.close() } }; room = null
        remoteSet = false
        pendingOffer = null
        pendingLocalCandidates.clear()
        pendingRemoteCandidates.clear()
        pendingSignals.clear()
        peerUser = 0L
        peerDevice = 0L
        restarts = 0
        ice = IceConfigData.Empty
        audio?.endCall()
        CallService.stop(app)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private inline fun update(f: (CallUi) -> CallUi) {
        _state.value = _state.value?.let(f)
    }

    /** Runs on Main only if the session that registered the callback is still the live one. */
    private fun post(mine: Int, f: () -> Unit) {
        scope.launch { if (mine == session) runFeature(Unit, f) }
    }

    private fun hasMedia(video: Boolean): Boolean =
        hasPermission(Manifest.permission.RECORD_AUDIO) && (!video || hasPermission(Manifest.permission.CAMERA))

    private fun hasPermission(p: String) =
        app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun joined(call: CallData) = call.participants.filter { it.state == "joined" }.map { it.userId }

    private fun outcomeFor(r: ApiResponse<*>): CallOutcome = when (r.code) {
        "busy", "peer_busy", "calls_busy" -> CallOutcome.Busy
        "sfu_unavailable" -> CallOutcome.Unavailable
        else -> CallOutcome.Failed
    }

    private fun outcomeFor(reason: String, ui: CallUi): CallOutcome = when (reason) {
        "hangup", "max_length" -> if (ui.phase == CallPhase.Incoming) CallOutcome.Missed else CallOutcome.Ended
        "cancelled" -> CallOutcome.Missed
        "declined" -> CallOutcome.Declined
        "busy" -> CallOutcome.Busy
        "no_answer" -> if (ui.outgoing) CallOutcome.NoAnswer else CallOutcome.Missed
        "lost" -> CallOutcome.Lost
        else -> CallOutcome.Failed
    }

    private fun initialsOf(name: String): String =
        name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "?" }

    private const val RING_TIMEOUT_MS = 60_000L
}
