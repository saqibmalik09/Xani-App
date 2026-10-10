package app.xani.feature.call

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import app.xani.core.device.DeviceClass
import app.xani.core.media.RemoteImage
import app.xani.core.net.MediaConfig
import app.xani.feature.inbox.ChatIco
import app.xani.feature.inbox.InboxController
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.system.systemContentInsets
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke
import kotlinx.coroutines.delay

private val AudioBg = Color(0xFF1A1F2E)
private val Red = Color(0xFFEF4444)
private val Green = Color(0xFF22C55E)
private val Amber = Color(0xFFF59E0B)

/** Line under the name: state first, then the running clock once connected. */
@Composable
internal fun callStatus(ui: CallUi, copy: Copy): Pair<String, Color> {
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.connectedAt, ui.phase) {
        if (ui.connectedAt == 0L || !ui.inMedia) return@LaunchedEffect
        while (true) {
            secs = ((SystemClock.elapsedRealtime() - ui.connectedAt) / 1000).toInt()
            delay(1_000)
        }
    }
    val dim = Color.White.copy(alpha = 0.55f)
    return when (ui.phase) {
        CallPhase.Outgoing -> (if (ui.ringing) copy.t(Copy.CallRinging) else copy.t(Copy.InboxCalling)) to dim
        CallPhase.Incoming -> copy.t(if (ui.video) Copy.CallIncomingVideo else Copy.CallIncomingAudio) to dim
        CallPhase.Connecting -> copy.t(Copy.InboxConnecting) to dim
        CallPhase.Connected -> "%02d:%02d".format(secs / 60, secs % 60) to dim
        CallPhase.Reconnecting -> copy.t(Copy.CallReconnecting) to Amber
        CallPhase.Ended -> copy.t(
            when (ui.outcome) {
                CallOutcome.Busy -> Copy.CallBusy
                CallOutcome.NoAnswer -> Copy.CallNoAnswer
                CallOutcome.Declined -> Copy.CallDeclined
                CallOutcome.Lost -> Copy.CallLost
                CallOutcome.Failed -> Copy.CallFailed
                CallOutcome.Unavailable -> Copy.CallUnavailable
                CallOutcome.Missed -> Copy.CallMissed
                else -> Copy.CallEnded
            },
        ) to if (ui.outcome == CallOutcome.Ended) dim else Color(0xFFFCA5A5)
    }
}

@Composable
fun AudioCallScreen(ui: CallUi, copy: Copy, device: DeviceClass) {
    val (status, statusColor) = callStatus(ui, copy)
    var addOpen by remember { mutableStateOf(false) }
    BackHandler { if (addOpen) addOpen = false else CallController.setMinimized(true) }

    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "p",
    )

    Box(Modifier.fillMaxSize().background(AudioBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().systemContentInsets()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallRound(onClick = { CallController.setMinimized(true) }) {
                    VectorCanvas(
                        shapes = listOf(VShape.D("M14 4L7 11l7 7", stroke(Color.White, 1.8f))),
                        viewport = 22f,
                        modifier = Modifier.size(rdp(18)),
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    XaniText(ui.peer.name, Type.LabelBold, color = Color.White, fontSizeOverride = 17f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VectorCanvas(
                            shapes = listOf(VShape.D("M5.5 1L6.8 4H10L7.4 5.9 8.4 9 5.5 7 2.6 9 3.6 5.9 1 4H4.2L5.5 1Z", fill(Color.White.copy(alpha = 0.45f)))),
                            viewport = 11f,
                            modifier = Modifier.size(rdp(11)),
                        )
                        XaniText(copy.t(Copy.InboxEncrypted), Type.Micro, color = Color.White.copy(alpha = 0.45f), fontSizeOverride = 11f)
                    }
                }
                CallRound(onClick = { if (ui.inMedia) addOpen = true }) { ChatIco.AddPerson(Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        if (device != DeviceClass.Low && ui.phase != CallPhase.Connected) {
                            Box(Modifier.size(rdp(220)).scale(pulse).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
                        }
                        PeerFace(ui.peer, rdp(140), 48f)
                    }
                    Spacer(Modifier.height(rdp(16)))
                    XaniText(status, Type.Label, color = statusColor)
                    if (ui.participants.size > 1) {
                        XaniText(
                            copy.t(Copy.CallInCall, "n" to (ui.participants.size + 1).toString()),
                            Type.Micro, color = Color.White.copy(alpha = 0.45f), fontSizeOverride = 11f,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .padding(horizontal = rdp(12), vertical = rdp(16))
                    .clip(RoundedCornerShape(rdp(28)))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(rdp(1), Color.White.copy(alpha = 0.1f), RoundedCornerShape(rdp(28)))
                    .padding(vertical = rdp(20)),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallCtl(copy.t(Copy.InboxSpeaker), ui.speaker, CallController::toggleSpeaker) {
                        ChatIco.Speaker(Color.White, rdp(26), dim = !ui.speaker)
                    }
                    CallCtl(copy.t(Copy.InboxVideo), false, CallController::upgradeToVideo) {
                        ChatIco.Video(Color.White, rdp(26))
                    }
                    CallCtl(copy.t(if (ui.muted) Copy.InboxUnmute else Copy.InboxMute), ui.muted, CallController::toggleMute) {
                        ChatIco.Mute(Color.White, rdp(26), muted = ui.muted)
                    }
                }
                Spacer(Modifier.height(rdp(16)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallCtl(copy.t(Copy.InboxMore), false, { }) { ChatIco.MoreH(Color.White, rdp(26)) }
                    CallCtl(copy.t(Copy.CallAddPerson), false, { if (ui.inMedia) addOpen = true }) { ChatIco.AddPerson(Color.White, rdp(26)) }
                    CallCtl(copy.t(Copy.InboxEnd), false, CallController::end, red = true) { ChatIco.Hangup(Color.White, rdp(28)) }
                }
            }
        }
        if (addOpen) AddParticipantSheet(ui, copy, onClose = { addOpen = false })
    }
}

@Composable
fun VideoCallScreen(ui: CallUi, copy: Copy) {
    val ctx = LocalContext.current
    val (status, statusColor) = callStatus(ui, copy)
    var addOpen by remember { mutableStateOf(false) }
    BackHandler { if (addOpen) addOpen = false else CallController.setMinimized(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Remote stream full-bleed once it flows; their face until then.
        // Re-ask the controller whenever the media session or the remote track changes,
        // so the renderer handed to Compose is the one the stream is painting into.
        val remote = remember(ui.callId, ui.mode, ui.mediaRev, ui.remoteVideo, ui.participants) { CallController.remoteView(ctx) }
        if (remote != null && ui.remoteVideo) {
            VideoSurface(remote, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(ui.peer.tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                PeerFace(ui.peer, rdp(120), 40f)
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().systemContentInsets()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(8)),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(rdp(20)))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = rdp(10), vertical = rdp(4)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(rdp(6)).clip(CircleShape).background(if (ui.phase == CallPhase.Reconnecting) Amber else Green))
                        Spacer(Modifier.width(rdp(6)))
                        XaniText(status, Type.LabelBold, color = if (ui.phase == CallPhase.Connected) Color.White else statusColor, fontSizeOverride = 11f)
                    }
                    Spacer(Modifier.height(rdp(10)))
                    XaniText(ui.peer.name, Type.LabelBold, color = Color.White, fontSizeOverride = 17f)
                    XaniText(copy.t(Copy.InboxEncrypted), Type.Micro, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 11f)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(rdp(12))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                        CallRound(dark = true, onClick = { CallController.setMinimized(true) }) {
                            VectorCanvas(
                                shapes = listOf(VShape.D("M4 14v4h4M18 8V4h-4M4 18l6-6M18 4l-6 6", stroke(Color.White, 1.8f))),
                                viewport = 22f,
                                modifier = Modifier.size(rdp(18)),
                            )
                        }
                        CallRound(dark = true, onClick = CallController::switchCamera) { ChatIco.FlipCam(Color.White) }
                    }
                    Box(
                        Modifier
                            .width(rdp(80))
                            .height(rdp(112))
                            .clip(RoundedCornerShape(rdp(14)))
                            .border(rdp(2), Color.White.copy(alpha = 0.18f), RoundedCornerShape(rdp(14)))
                            .background(ui.peer.tint),
                        contentAlignment = Alignment.Center,
                    ) {
                        val local = remember(ui.callId, ui.mode, ui.mediaRev, ui.cameraOff) { if (ui.cameraOff) null else CallController.localView(ctx) }
                        if (local != null) {
                            VideoSurface(local, Modifier.fillMaxSize())
                        } else {
                            XaniText(copy.t(Copy.CallYou), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .padding(horizontal = rdp(12), vertical = rdp(12))
                    .clip(RoundedCornerShape(rdp(50)))
                    .background(Color(0xF2232323))
                    .padding(horizontal = rdp(12), vertical = rdp(10))
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoCtl(onClick = { if (ui.inMedia) addOpen = true }) { ChatIco.AddPerson(Color.White, rdp(22)) }
                VideoCtl(onClick = CallController::toggleCamera) { ChatIco.Video(Color.White, rdp(22), off = ui.cameraOff) }
                VideoCtl(active = ui.speaker, onClick = CallController::toggleSpeaker) {
                    ChatIco.Speaker(if (ui.speaker) Color(0xFF111111) else Color.White, rdp(22), dim = !ui.speaker)
                }
                VideoCtl(onClick = CallController::toggleMute) { ChatIco.Mute(Color.White, rdp(22), muted = ui.muted) }
                Box(
                    Modifier.size(rdp(52)).clip(CircleShape).background(Red).pressable(role = Role.Button, onClick = CallController::end),
                    contentAlignment = Alignment.Center,
                ) { ChatIco.Hangup(Color.White, rdp(26)) }
            }
        }
        if (addOpen) AddParticipantSheet(ui, copy, onClose = { addOpen = false })
    }
}

/** Full-screen ring: who, what kind, decline / accept. */
@Composable
fun IncomingCallScreen(ui: CallUi, copy: Copy, onAccept: () -> Unit) {
    val (status, statusColor) = callStatus(ui, copy)
    BackHandler { CallController.reject() }
    val pulse by rememberInfiniteTransition(label = "in").animateFloat(
        initialValue = 0.94f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p",
    )
    Box(Modifier.fillMaxSize().background(AudioBg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().systemContentInsets().padding(horizontal = rdp(24)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(rdp(200)).scale(pulse).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)))
                PeerFace(ui.peer, rdp(132), 46f)
            }
            Spacer(Modifier.height(rdp(20)))
            XaniText(ui.peer.name, Type.H1, color = Color.White, fontSizeOverride = 26f)
            Spacer(Modifier.height(rdp(6)))
            XaniText(status, Type.Label, color = statusColor)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = rdp(40)), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallCtl(copy.t(Copy.CallDecline), false, CallController::reject, red = true) { ChatIco.Hangup(Color.White, rdp(28)) }
                CallCtl(copy.t(Copy.CallAccept), false, onAccept, green = true) {
                    if (ui.video) ChatIco.Video(Color.White, rdp(26)) else ChatIco.Phone(Color.White, rdp(24))
                }
            }
        }
    }
}

/** Minimized call: a strip at the top edge; tap returns to the call. */
@Composable
fun CallPill(ui: CallUi, copy: Copy, modifier: Modifier = Modifier) {
    val (status, _) = callStatus(ui, copy)
    Row(
        modifier
            .statusBarsPadding()
            .padding(horizontal = rdp(12), vertical = rdp(6))
            .clip(RoundedCornerShape(rdp(22)))
            .background(if (ui.phase == CallPhase.Reconnecting) Amber else Green)
            .pressable(pressedScale = 0.985f) { CallController.setMinimized(false) }
            .padding(horizontal = rdp(14), vertical = rdp(9)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatIco.Phone(Color.White, rdp(14))
        Spacer(Modifier.width(rdp(8)))
        XaniText(ui.peer.name, Type.LabelBold, color = Color.White, fontSizeOverride = 13f, modifier = Modifier.weight(1f))
        XaniText(status, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
        Spacer(Modifier.width(rdp(12)))
        Box(
            Modifier.size(rdp(28)).clip(CircleShape).background(Red).pressable(onClick = CallController::end),
            contentAlignment = Alignment.Center,
        ) { ChatIco.Hangup(Color.White, rdp(16)) }
    }
}

/** Pick someone from the inbox to pull into the call. */
@Composable
private fun BoxScope.AddParticipantSheet(ui: CallUi, copy: Copy, onClose: () -> Unit) {
    val inbox = InboxController.shared
    val candidates = inbox.chats.filter { !it.group && it.peerId != 0L && it.peerId != ui.peer.id && it.peerId !in ui.participants && !it.blocked }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pressable(pressedScale = 1f, onClick = onClose))
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = rdp(22), topEnd = rdp(22)))
            .background(Color(0xFF232838))
            .systemContentInsets()
            .padding(top = rdp(14), bottom = rdp(12)),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(rdp(36)).height(rdp(4)).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
        Spacer(Modifier.height(rdp(12)))
        XaniText(copy.t(Copy.CallAddPerson), Type.LabelBold, color = Color.White, fontSizeOverride = 16f, modifier = Modifier.padding(horizontal = rdp(18)))
        Spacer(Modifier.height(rdp(8)))
        if (candidates.isEmpty()) {
            XaniText(copy.t(Copy.CallNobodyToAdd), Type.Label, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(rdp(18)))
        }
        LazyColumn(Modifier.fillMaxWidth().height(rdp(320))) {
            items(candidates, key = { it.id }) { chat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(pressedScale = 1f) { CallController.addParticipant(chat.peerId); onClose() }
                        .padding(horizontal = rdp(18), vertical = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PeerFace(CallPeer(chat.peerId, chat.name, chat.initials, chat.tint, chat.avatarUrl), rdp(40), 14f)
                    Spacer(Modifier.width(rdp(12)))
                    Column(Modifier.weight(1f)) {
                        XaniText(inbox.displayName(chat), Type.LabelBold, color = Color.White, fontSizeOverride = 15f)
                        XaniText(chat.handle, Type.Micro, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 11f)
                    }
                    ChatIco.AddPerson(Color.White.copy(alpha = 0.7f), rdp(20))
                }
            }
        }
    }
}

@Composable
private fun PeerFace(peer: CallPeer, size: androidx.compose.ui.unit.Dp, fontSize: Float) {
    val url = remember(peer.avatarUrl) { MediaConfig.avatarUrl(peer.avatarUrl) }
    Box(Modifier.size(size).clip(CircleShape).background(peer.tint), contentAlignment = Alignment.Center) {
        if (url.isNotEmpty()) {
            RemoteImage(url, Modifier.fillMaxSize(), maxEdge = 512, fallback = Color.Transparent)
        } else {
            XaniText(peer.initials, Type.H1, color = Color.White, fontSizeOverride = fontSize)
        }
    }
}

@Composable
private fun CallRound(dark: Boolean = false, onClick: () -> Unit = {}, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(rdp(44))
            .clip(CircleShape)
            .background(if (dark) Color(0xD9282828) else Color.White.copy(alpha = 0.12f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun CallCtl(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    red: Boolean = false,
    green: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(rdp(8))) {
        Box(
            Modifier
                .size(rdp(64))
                .clip(CircleShape)
                .background(
                    when {
                        red -> Red
                        green -> Green
                        active -> Color.White.copy(alpha = 0.25f)
                        else -> Color.White.copy(alpha = 0.12f)
                    },
                )
                .pressable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
        XaniText(label, Type.Caption, color = Color.White.copy(alpha = 0.75f), fontSizeOverride = 12f)
    }
}

@Composable
private fun VideoCtl(active: Boolean = false, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(rdp(52))
            .clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = 0.1f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
