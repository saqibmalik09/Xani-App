package app.xani.feature.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.isolate.runFeature
import app.xani.core.net.PkData
import app.xani.core.net.PkHostHitData
import app.xani.core.net.PkInviteData
import app.xani.core.net.PkSideData
import app.xani.core.net.Xani
import app.xani.feature.gift.formatCoins
import app.xani.feature.social.LevelMark
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val PkPurple = Color(0xFF8B5CF6)
private val PkPink = Color(0xFFEC4899)
private val PkGold = Color(0xFFF59E0B)
private val PkBlue = Color(0xFF3B82F6)
private val PkOrange = Color(0xFFF97316)
private val PkInk = Color(0xFF0B0818)

/** Results list in the invite sheet: resting height and the drag bounds, dp. */
private const val PkListDefaultDp = 340f
private const val PkListMinDp = 160f
private const val PkListMaxDp = 520f

/**
 * Host sheet: pick a duration, search a live host, tap to invite.
 * Sits over the broadcast; chat / gifts stay underneath when closed.
 */
@Composable
internal fun PkInviteSheet(
    roomId: Long,
    copy: Copy,
    onClose: () -> Unit,
) {
    var durationSec by remember { mutableIntStateOf(300) }
    var custom by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PkHostHitData>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var sentTo by remember { mutableStateOf(0L) }
    // Height of the results list, in dp. Starts tall enough for six hosts
    // and follows the grip between the field and the list: pull up for
    // more rows, down for fewer. Bounded so the sheet never leaves the screen.
    var listDp by remember { mutableFloatStateOf(PkListDefaultDp) }
    val density = LocalDensity.current

    LaunchedEffect(query, roomId) {
        delay(280)
        searching = true
        val r = withContext(Dispatchers.IO) {
            runFeature(null) { Xani.calls.pkHosts(roomId, query.trim(), 20) }
        }
        searching = false
        hits = r?.value.orEmpty()
    }

    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { close ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(18))
                .padding(bottom = rdp(28)),
        ) {
            XaniText(copy.t(Copy.PkTitle), Type.LabelBold, color = Color.White, fontSizeOverride = 17f)
            XaniText(
                copy.t(Copy.PkHint),
                Type.Caption,
                color = Color.White.copy(alpha = 0.55f),
                fontSizeOverride = 12f,
                modifier = Modifier.padding(top = rdp(4), bottom = rdp(14)),
            )

            XaniText(copy.t(Copy.PkDuration), Type.LabelBold, color = Color.White.copy(alpha = 0.7f), fontSizeOverride = 11f)
            Row(
                Modifier.padding(top = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                listOf(120 to "2m", 300 to "5m", 600 to "10m").forEach { (sec, label) ->
                    val on = durationSec == sec && custom.isBlank()
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .then(
                                if (on) Modifier.background(Brush.linearGradient(listOf(PkPurple, PkPink)))
                                else Modifier.background(Color.White.copy(alpha = 0.08f)),
                            )
                            .pressable(pressedScale = 0.92f, onClick = { durationSec = sec; custom = "" })
                            .padding(horizontal = rdp(14), vertical = rdp(8)),
                    ) { XaniText(label, Type.LabelBold, color = Color.White, fontSizeOverride = 13f) }
                }
                Box(
                    Modifier
                        .width(rdp(72))
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(if (custom.isNotBlank()) Color(0x338B5CF6) else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(14)))
                        .padding(horizontal = rdp(10), vertical = rdp(8)),
                ) {
                    BasicTextField(
                        value = custom,
                        onValueChange = {
                            custom = it.filter(Char::isDigit).take(2)
                            custom.toIntOrNull()?.coerceIn(1, 60)?.let { m -> durationSec = m * 60 }
                        },
                        textStyle = Type.LabelBold.copy(color = Color.White),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { inner ->
                            Box {
                                if (custom.isEmpty()) {
                                    XaniText(copy.t(Copy.PkCustomMin), Type.Caption, color = Color.White.copy(alpha = 0.4f), fontSizeOverride = 11f)
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(rdp(16)))
            XaniText(copy.t(Copy.PkSearch), Type.LabelBold, color = Color.White.copy(alpha = 0.7f), fontSizeOverride = 11f)
            Box(
                Modifier
                    .padding(top = rdp(8))
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(rdp(16)))
                    .padding(horizontal = rdp(14), vertical = rdp(11)),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    textStyle = Type.Caption.copy(color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                XaniText(copy.t(Copy.PkSearchHint), Type.Caption, color = Color.White.copy(alpha = 0.4f), fontSizeOverride = 13f)
                            }
                            inner()
                        }
                    },
                )
            }

            // The grip: a vertical pull here resizes the list. It consumes the
            // drag itself, so the sheet underneath does not slide with it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(22))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dy ->
                            change.consume()
                            listDp = (listDp - dy / density.density).coerceIn(PkListMinDp, PkListMaxDp)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = rdp(36), height = rdp(4))
                        .clip(RoundedCornerShape(rdp(2)))
                        .background(Color.White.copy(alpha = 0.22f)),
                )
            }
            val listHeight = rdp(listDp)
            when {
                searching && hits.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(listHeight),
                    contentAlignment = Alignment.Center,
                ) { XaniText("…", Type.LabelBold, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 18f) }
                hits.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(listHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(copy.t(Copy.PkNoHosts), Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 13f)
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().height(listHeight),
                    verticalArrangement = Arrangement.spacedBy(rdp(6)),
                ) {
                    items(hits, key = { it.host.id }) { hit ->
                        val name = hit.host.fullName.ifBlank { hit.host.username }
                        val sent = sentTo == hit.host.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(rdp(14)))
                                .background(Color.White.copy(alpha = 0.05f))
                                .pressable(enabled = !sent, pressedScale = 0.97f, onClick = {
                                    LiveSession.invitePk(hit.host.id, durationSec.coerceIn(60, 3600))
                                    sentTo = hit.host.id
                                    close()
                                })
                                .padding(horizontal = rdp(10), vertical = rdp(8)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                        ) {
                            PersonAvatar(hit.host.id, name, hit.host.avatarUrl, rdp(40), frameUrl = hit.host.frameUrl, frameMime = hit.host.frameMime)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(5))) {
                                    XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 14f, maxLines = 1)
                                    LevelMark(hit.host.receiverLevel, rdp(14))
                                }
                                XaniText(
                                    "@${hit.host.username} · ${hit.viewers}",
                                    Type.Caption,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSizeOverride = 11f,
                                    maxLines = 1,
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(rdp(12)))
                                    .then(
                                        if (sent) Modifier.background(Color.White.copy(alpha = 0.1f))
                                        else Modifier.background(Brush.linearGradient(listOf(PkPurple, PkPink))),
                                    )
                                    .padding(horizontal = rdp(12), vertical = rdp(6)),
                            ) {
                                XaniText(
                                    if (sent) copy.t(Copy.PkSent) else copy.t(Copy.PkInvite),
                                    Type.LabelBold,
                                    color = Color.White,
                                    fontSizeOverride = 11f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Invitee card: host invited you — Accept / Decline. */
@Composable
internal fun PkIncomingCard(
    invite: PkInviteData,
    copy: Copy,
    modifier: Modifier = Modifier,
) {
    val name = invite.fromHost.fullName.ifBlank { invite.fromHost.username }
    val mins = (invite.durationSec / 60).coerceAtLeast(1)
    Row(
        modifier
            .clip(RoundedCornerShape(rdp(18)))
            .background(Color(0xF0171230))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(18)))
            .padding(horizontal = rdp(10), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
    ) {
        PersonAvatar(invite.fromHost.id, name, invite.fromHost.avatarUrl, rdp(36), frameUrl = invite.fromHost.frameUrl, frameMime = invite.fromHost.frameMime)
        Column(Modifier.weight(1f)) {
            XaniText(
                copy.t(Copy.PkIncoming, "name" to name, "m" to "$mins"),
                Type.Label,
                color = Color.White,
                fontSizeOverride = 12f,
                maxLines = 2,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(rdp(14)))
                .background(Color.White.copy(alpha = 0.1f))
                .pressable(pressedScale = 0.92f, onClick = { LiveSession.declinePkInvite() })
                .padding(horizontal = rdp(10), vertical = rdp(7)),
        ) { XaniText(copy.t(Copy.PkDecline), Type.LabelBold, color = Color.White, fontSizeOverride = 11f) }
        Box(
            Modifier
                .clip(RoundedCornerShape(rdp(14)))
                .background(Brush.linearGradient(listOf(PkPurple, PkPink)))
                .pressable(pressedScale = 0.92f, onClick = { LiveSession.acceptPkInvite() })
                .padding(horizontal = rdp(10), vertical = rdp(7)),
        ) { XaniText(copy.t(Copy.PkAccept), Type.LabelBold, color = Color.White, fontSizeOverride = 11f) }
    }
}

/**
 * Split arena, the way every live app draws a PK: this room's host on the
 * left, the opponent on the right, both 9:16 halves, one score bar under
 * them that tilts toward whoever is ahead, and the clock over the seam.
 *
 * The opponent's picture is the SFU track they cross-published into this
 * room; until it lands (or if it never does) their HLS plays, and before
 * either, their avatar. Chat and gifts stay below this layer.
 *
 * Cost: the clock recomposes once per second (it sleeps to the next whole
 * second, not on a fixed tick); scores animate on the GPU via one float.
 */
@Composable
internal fun PkArena(
    pk: PkData,
    myHostId: Long,
    myRoomId: Long,
    isHost: Boolean,
    mediaRev: Int,
    copy: Copy,
    modifier: Modifier = Modifier,
    /** Local host preview when this device is hosting. */
    localHostView: (@Composable () -> Unit)? = null,
    /** Primary room host video for viewers (SFU / HLS already chosen by caller). */
    myHostVideo: (@Composable () -> Unit)? = null,
) {
    val mine = if (pk.left.roomId == myRoomId || pk.left.host.id == myHostId) pk.left else pk.right
    val opp = if (mine === pk.left) pk.right else pk.left
    val remain = pkCountdown(pk)
    val leader = when {
        !pk.fighting || mine.score == opp.score -> 0L
        mine.score > opp.score -> mine.host.id
        else -> opp.host.id
    }

    Column(modifier.clip(RoundedCornerShape(rdp(16))).background(PkInk)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxSize()) {
                PkHalf(
                    side = mine,
                    outcome = pk.outcomeOf(mine.host.id),
                    leading = leader == mine.host.id,
                    copy = copy,
                    video = if (isHost) localHostView else myHostVideo,
                    remoteOf = null,
                    mediaRev = mediaRev,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PkHalf(
                    side = opp,
                    outcome = pk.outcomeOf(opp.host.id),
                    leading = leader == opp.host.id,
                    copy = copy,
                    video = null,
                    remoteOf = opp.host.id,
                    mediaRev = mediaRev,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            // Seam: a hairline of the brand gradient between the two pictures.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(rdp(2))
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, PkPurple, PkPink, Color.Transparent))),
            )

            PkClock(
                pk = pk,
                remain = remain,
                copy = copy,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = rdp(8)),
            )

            if (isHost && pk.fighting) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(rdp(8))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(rdp(12)))
                        .pressable(pressedScale = 0.94f, onClick = { LiveSession.endPk() })
                        .padding(horizontal = rdp(10), vertical = rdp(5)),
                ) { XaniText(copy.t(Copy.PkEnd), Type.LabelBold, color = Color.White, fontSizeOverride = 10f) }
            }

            if (pk.showingResult) {
                PkResultOverlay(
                    pk = pk,
                    mine = mine,
                    opp = opp,
                    isHost = isHost,
                    remain = remain,
                    copy = copy,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        PkScoreBar(mine = mine.score, opp = opp.score, modifier = Modifier.fillMaxWidth())
    }
}

/** Seconds left in the current phase; recomposes once a second, not on a tick. */
@Composable
private fun pkCountdown(pk: PkData): Int {
    var remain by remember(pk.id) { mutableIntStateOf(0) }
    LaunchedEffect(pk.id, pk.round, pk.phase, pk.endsAtMs, pk.resultUntilMs) {
        val target = when (pk.phase) {
            "fighting" -> pk.endsAtMs
            "result" -> pk.resultUntilMs
            else -> 0L
        }
        while (true) {
            val left = target - System.currentTimeMillis()
            remain = ((left + 999L) / 1000L).toInt().coerceAtLeast(0)
            if (target == 0L || left <= 0L) break
            // Sleep to the next second boundary (never a hot loop on drift).
            delay((left % 1000L).coerceIn(80L, 1000L))
        }
    }
    return remain
}

/** 1 = won, -1 = lost, 0 = no result / draw. */
private fun PkData.outcomeOf(hostId: Long): Int = when {
    !showingResult || winnerId == 0L -> 0
    winnerId == hostId -> 1
    else -> -1
}

@Composable
private fun PkClock(pk: PkData, remain: Int, copy: Copy, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(RoundedCornerShape(rdp(14)))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(14)))
                .padding(start = rdp(6), end = rdp(12), top = rdp(4), bottom = rdp(4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(6)),
        ) {
            PkMark(Color.White, rdp(18))
            XaniText(
                if (pk.showingResult) copy.t(Copy.PkResult) else formatPkClock(remain),
                Type.LabelBold,
                color = if (pk.fighting && remain <= 10) PkGold else Color.White,
                fontSizeOverride = 13f,
            )
        }
        if (pk.round > 1) {
            XaniText(
                copy.t(Copy.PkRound, "n" to "${pk.round}"),
                Type.Caption,
                color = Color.White.copy(alpha = 0.75f),
                fontSizeOverride = 10f,
                modifier = Modifier.padding(top = rdp(3)),
            )
        }
    }
}

/**
 * The tug-of-war bar: left fill is mine, right fill is theirs, the lightning
 * seam slides to the split. One animated float; no per-gift recomposition
 * beyond the score text.
 */
@Composable
private fun PkScoreBar(mine: Long, opp: Long, modifier: Modifier) {
    val total = mine + opp
    val target = if (total <= 0L) 0.5f else (mine.toFloat() / total.toFloat()).coerceIn(0.12f, 0.88f)
    val frac by animateFloatAsState(target, tween(420, easing = FastOutSlowInEasing), label = "pk_split")
    Box(modifier.height(rdp(24))) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(frac)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(PkBlue, PkPurple))),
            )
            Box(
                Modifier
                    .weight(1f - frac)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(PkPink, PkOrange))),
            )
        }
        XaniText(
            formatCoins(mine),
            Type.LabelBold,
            color = Color.White,
            fontSizeOverride = 12f,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = rdp(10)),
        )
        XaniText(
            formatCoins(opp),
            Type.LabelBold,
            color = Color.White,
            fontSizeOverride = 12f,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = rdp(10)),
        )
        Box(
            Modifier
                .align(BiasAlignment(frac * 2f - 1f, 0f))
                .size(rdp(22))
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { XaniText("⚡", Type.LabelBold, fontSizeOverride = 12f, align = TextAlign.Center) }
    }
}

/**
 * One half. [video] is the caller's own picture (host preview / primary
 * host track); when absent and [remoteOf] is set, the SFU track that user
 * cross-published here, then HLS, then the avatar. The remote ask is retried
 * a bounded number of times: a late track lands within a few seconds or the
 * HLS carries the half — never a loop for the life of the fight.
 */
@Composable
private fun PkHalf(
    side: PkSideData,
    outcome: Int,
    leading: Boolean,
    copy: Copy,
    video: (@Composable () -> Unit)?,
    remoteOf: Long?,
    mediaRev: Int,
    modifier: Modifier,
) {
    val name = side.host.fullName.ifBlank { side.host.username }
    Box(modifier.background(PkInk)) {
        when {
            video != null -> video()
            remoteOf != null -> RemoteHalf(side = side, userId = remoteOf, mediaRev = mediaRev, copy = copy)
            else -> AvatarHalf(side = side, hint = "")
        }

        // Losing half dims under the result; winning half gets the gold edge.
        if (outcome < 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        if (outcome > 0) Box(Modifier.fillMaxSize().border(2.dp, PkGold))

        if (outcome != 0) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(rdp(8))
                    .clip(RoundedCornerShape(rdp(8)))
                    .then(
                        if (outcome > 0) Modifier.background(Brush.linearGradient(listOf(PkGold, Color(0xFFFBBF24))))
                        else Modifier.background(Color.Black.copy(alpha = 0.55f)),
                    )
                    .padding(horizontal = rdp(8), vertical = rdp(3)),
            ) {
                XaniText(
                    if (outcome > 0) copy.t(Copy.PkWinTag) else copy.t(Copy.PkLoseTag),
                    Type.LabelBold,
                    color = if (outcome > 0) Color(0xFF3B2A00) else Color.White.copy(alpha = 0.8f),
                    fontSizeOverride = 10f,
                )
            }
        }

        // Name plate, bottom: avatar · name · level, on a soft scrim.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(5)),
        ) {
            PersonAvatar(side.host.id, name, side.host.avatarUrl, rdp(22), frameUrl = side.host.frameUrl, frameMime = side.host.frameMime)
            if (leading) XaniText("👑", Type.LabelBold, fontSizeOverride = 11f)
            XaniText(
                name,
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 11f,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            LevelMark(side.host.receiverLevel, rdp(12))
        }
    }
}

private const val PkRemoteRetries = 12
private const val PkRemoteRetryMs = 700L

@Composable
private fun RemoteHalf(side: PkSideData, userId: Long, mediaRev: Int, copy: Copy) {
    val ctx = LocalContext.current
    var retry by remember(userId, mediaRev) { mutableIntStateOf(0) }
    val remote = remember(userId, mediaRev, retry) { LiveSession.remoteView(ctx, userId) }
    when {
        remote != null -> key(remote) { AndroidView(factory = { remote }, modifier = Modifier.fillMaxSize()) }
        side.hlsUrl.isNotBlank() -> {
            HlsPlayerView(side.hlsUrl, Modifier.fillMaxSize())
            // Keep asking briefly for the sharper SFU track behind the HLS.
            if (retry < PkRemoteRetries) {
                LaunchedEffect(userId, mediaRev, retry) {
                    delay(PkRemoteRetryMs)
                    retry++
                }
            }
        }
        else -> {
            if (retry < PkRemoteRetries) {
                LaunchedEffect(userId, mediaRev, retry) {
                    delay(PkRemoteRetryMs)
                    retry++
                }
            }
            AvatarHalf(side = side, hint = if (retry < PkRemoteRetries) copy.t(Copy.PkConnecting) else "")
        }
    }
}

/** No picture yet: the person's avatar over a soft brand wash. */
@Composable
private fun AvatarHalf(side: PkSideData, hint: String) {
    val name = side.host.fullName.ifBlank { side.host.username }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1533), PkInk))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PersonAvatar(side.host.id, name, side.host.avatarUrl, rdp(64), frameUrl = side.host.frameUrl, frameMime = side.host.frameMime)
            if (hint.isNotBlank()) {
                XaniText(
                    hint,
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSizeOverride = 11f,
                    modifier = Modifier.padding(top = rdp(8)),
                )
            }
        }
    }
}

@Composable
private fun PkResultOverlay(
    pk: PkData,
    mine: PkSideData,
    opp: PkSideData,
    isHost: Boolean,
    remain: Int,
    copy: Copy,
    modifier: Modifier,
) {
    val iWon = pk.winnerId == mine.host.id
    val draw = pk.winnerId == 0L
    val title = when {
        draw -> copy.t(Copy.PkDraw)
        iWon -> copy.t(Copy.PkWin)
        else -> copy.t(Copy.PkLose)
    }
    val iVoted = if (pk.left.host.id == mine.host.id) pk.rematchLeft else pk.rematchRight
    Column(
        modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(Color(0xF0171230))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(rdp(20)))
            .padding(horizontal = rdp(22), vertical = rdp(16)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(if (draw) "🤝" else if (iWon) "🏆" else "⚔", Type.Label, fontSizeOverride = 28f)
        XaniText(
            title,
            Type.LabelBold,
            color = if (iWon) PkGold else Color.White,
            fontSizeOverride = 20f,
            modifier = Modifier.padding(top = rdp(4)),
        )
        XaniText(
            "${formatCoins(mine.score)}  ·  ${formatCoins(opp.score)}",
            Type.LabelBold,
            color = Color.White.copy(alpha = 0.8f),
            fontSizeOverride = 12f,
            modifier = Modifier.padding(top = rdp(4)),
        )
        XaniText(
            copy.t(Copy.PkResultHold, "s" to "$remain"),
            Type.Caption,
            color = Color.White.copy(alpha = 0.55f),
            fontSizeOverride = 12f,
            modifier = Modifier.padding(top = rdp(4)),
        )
        if (isHost) {
            Spacer(Modifier.height(rdp(12)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(42))
                    .clip(RoundedCornerShape(rdp(21)))
                    .then(
                        if (iVoted) Modifier.background(Color.White.copy(alpha = 0.12f))
                        else Modifier.background(Brush.linearGradient(listOf(PkPurple, PkPink))),
                    )
                    .pressable(enabled = !iVoted, pressedScale = 0.95f, onClick = { LiveSession.rematchPk(pk.durationSec) }),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(
                    if (iVoted) copy.t(Copy.PkRematchWait) else copy.t(Copy.PkRematch),
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = 14f,
                )
            }
        }
    }
}

/** Compact "PK" mark for the host bottom bar. */
@Composable
internal fun PkMark(tint: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(rdp(6)))
            .background(Brush.linearGradient(listOf(PkPurple, PkPink))),
        contentAlignment = Alignment.Center,
    ) {
        XaniText("PK", Type.LabelBold, color = Color.White, fontSizeOverride = 9f, align = TextAlign.Center)
    }
}

private fun formatPkClock(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
