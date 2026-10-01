package app.xani.feature.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.isolate.runFeature
import app.xani.core.net.PersonData
import app.xani.core.net.StageData
import app.xani.core.net.StageMemberData
import app.xani.core.net.StageRequestData
import app.xani.core.net.Xani
import app.xani.feature.create.CreateIco
import app.xani.feature.gift.formatCoins
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.FollowStore
import app.xani.feature.social.FrameStore
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The stage, drawn: co-host tiles in a video room, the seat circle in an
 * audio room, and the sheets around them. Everything here takes the
 * immutable [StageData] snapshot and nothing else that moves, so a frame
 * that changes one seat recomposes that seat — the chat, the picture and
 * the gift lane never re-enter.
 */

private val Accent = Color(0xFF8B5CF6)
private val Pink = Color(0xFFEC4899)
private val Diamond = Color(0xFFC4B5FD)
private val Danger = Color(0xFFEF4444)

/** How many co-host tiles show before the rest fold into "…". */
private const val RailTiles = 4

// ─── video: co-host rail ─────────────────────────────────────────────────────

/**
 * The co-hosts: up to [RailTiles] pictures numbered 1..4 in seat order, then
 * one "…" chip for the rest. The host's screen stacks them down the right
 * edge ([horizontal] false); a viewer's screen lays them left → right under
 * the host's name ([horizontal] true), where nothing else sits, so tiles
 * never land on the chrome. Tapping a tile opens that person; the chip
 * opens the full list.
 */
@Composable
internal fun CoHostRail(
    stage: StageData,
    mediaRev: Int,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    onTile: (StageMemberData) -> Unit,
    onMore: () -> Unit,
) {
    val guests = stage.guests
    if (guests.isEmpty()) return
    val tiles: @Composable () -> Unit = {
        guests.take(RailTiles).forEachIndexed { i, m ->
            key(m.user.id) {
                CoHostTile(m, index = i + 1, mediaRev = mediaRev, onClick = { onTile(m) })
            }
        }
        if (guests.size > RailTiles) {
            Box(
                Modifier
                    .width(rdp(72))
                    .height(rdp(26))
                    .clip(RoundedCornerShape(rdp(13)))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(rdp(13)))
                    .pressable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                XaniText("··· +${guests.size - RailTiles}", Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
            }
        }
    }
    if (horizontal) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(rdp(6)), verticalAlignment = Alignment.CenterVertically) { tiles() }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(rdp(6)), horizontalAlignment = Alignment.End) { tiles() }
    }
}

/**
 * One co-host: their camera when the track is here, their avatar until
 * then; the seat number top-left, the mic state bottom-left, 💎 bottom-right.
 */
@Composable
private fun CoHostTile(m: StageMemberData, index: Int, mediaRev: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val me = m.user.id == ProfileStore.currentUserId
    // Re-ask for the renderer whenever the media session or the track set
    // moved; the renderer itself is reused for the same person.
    val view = remember(m.user.id, mediaRev, me) {
        if (me) LiveSession.localView(ctx) else LiveSession.remoteView(ctx, m.user.id)
    }
    Box(
        Modifier
            .width(rdp(72))
            .height(rdp(96))
            .clip(RoundedCornerShape(rdp(12)))
            .background(Color(0xFF15122A))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(rdp(12)))
            .pressable(pressedScale = 0.96f, onClick = onClick),
    ) {
        if (view != null) {
            key(view) { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PersonAvatar(m.user.id, m.user.fullName, m.user.avatarUrl, rdp(40), frameUrl = m.user.frameUrl, frameMime = m.user.frameMime)
            }
        }
        // Number badge, top-left.
        Box(
            Modifier
                .padding(rdp(4))
                .size(rdp(16))
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center,
        ) { XaniText("$index", Type.LabelBold, color = Color.White, fontSizeOverride = 9f) }
        // Name strip with mic + coins along the bottom.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = rdp(4), vertical = rdp(3)),
        ) {
            XaniText(
                m.user.fullName.ifBlank { m.user.username },
                Type.Micro, color = Color.White, fontSizeOverride = 9f, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicMark(muted = m.muted, size = rdp(10))
                Spacer(Modifier.weight(1f))
                if (m.coins > 0) {
                    XaniText("💎" + formatCoins(m.coins), Type.Micro, color = Diamond, fontSizeOverride = 8f, maxLines = 1)
                }
            }
        }
    }
}

/** Mic on/off glyph, tinted by state. */
@Composable
internal fun MicMark(muted: Boolean, size: Dp) {
    val tint = if (muted) Danger else Color(0xFF34D399)
    Box(
        Modifier
            .size(size + rdp(6))
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) { CreateIco.Mic(tint, size, off = muted) }
}

// ─── host: request card ──────────────────────────────────────────────────────

/**
 * The host's notification: the oldest request, Accept / Decline, and how
 * many more are waiting. One card, never a stack — the queue drains one
 * yes at a time.
 */
@Composable
internal fun StageRequestCard(
    requests: List<StageRequestData>,
    copy: Copy,
    modifier: Modifier = Modifier,
    onAccept: (Long) -> Unit,
    onReject: (Long) -> Unit,
) {
    val first = requests.firstOrNull() ?: return
    val p = first.user
    val name = p.fullName.ifBlank { p.username }
    Row(
        modifier
            .clip(RoundedCornerShape(rdp(18)))
            .background(Color(0xF0171230))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(18)))
            .padding(horizontal = rdp(10), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
    ) {
        PersonAvatar(p.id, name, p.avatarUrl, rdp(34), frameUrl = p.frameUrl, frameMime = p.frameMime)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(4))) {
                XaniText(
                    copy.t(Copy.LiveCoHostWants, "name" to name),
                    Type.Label, color = Color.White, fontSizeOverride = 11f, maxLines = 2,
                )
                LevelMark(p.senderLevel, rdp(12))
            }
            if (requests.size > 1) {
                XaniText(
                    copy.t(Copy.LiveCoHostMore, "n" to "${requests.size - 1}"),
                    Type.Micro, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 10f,
                )
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(rdp(14)))
                .background(Color.White.copy(alpha = 0.1f))
                .pressable(pressedScale = 0.92f, onClick = { onReject(p.id) })
                .padding(horizontal = rdp(10), vertical = rdp(7)),
        ) { XaniText(copy.t(Copy.LiveCoHostDecline), Type.LabelBold, color = Color.White, fontSizeOverride = 11f) }
        Box(
            Modifier
                .clip(RoundedCornerShape(rdp(14)))
                .background(Brush.linearGradient(listOf(Accent, Pink)))
                .pressable(pressedScale = 0.92f, onClick = { onAccept(p.id) })
                .padding(horizontal = rdp(10), vertical = rdp(7)),
        ) { XaniText(copy.t(Copy.LiveCoHostAccept), Type.LabelBold, color = Color.White, fontSizeOverride = 11f) }
    }
}

// ─── sheets ──────────────────────────────────────────────────────────────────

/** Everyone on stage, by seat. The host can remove anyone from here. */
@Composable
internal fun CoHostSheet(
    stage: StageData,
    isHost: Boolean,
    copy: Copy,
    onPerson: (StageMemberData) -> Unit,
    onDrop: (Long) -> Unit,
    onClose: () -> Unit,
) {
    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { _ ->
        Column(Modifier.fillMaxWidth().padding(horizontal = rdp(18)).padding(bottom = rdp(28))) {
            Row(Modifier.fillMaxWidth().padding(top = rdp(4), bottom = rdp(10)), verticalAlignment = Alignment.CenterVertically) {
                XaniText(copy.t(Copy.LiveCoHosts), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
                Spacer(Modifier.weight(1f))
                XaniText("${stage.guests.size} / ${(stage.seats - 1).coerceAtLeast(0)}", Type.LabelBold, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 13f)
            }
            val guests = stage.guests
            if (guests.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(rdp(100)), contentAlignment = Alignment.Center) {
                    XaniText(copy.t(Copy.LiveCoHostNone), Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 13f)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(rdp(320)), verticalArrangement = Arrangement.spacedBy(rdp(4))) {
                    items(guests, key = { it.user.id }) { m ->
                        val name = m.user.fullName.ifBlank { m.user.username }.ifBlank { "…" }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(rdp(14)))
                                .background(Color.White.copy(alpha = 0.04f))
                                .pressable(onClick = { onPerson(m) })
                                .padding(horizontal = rdp(10), vertical = rdp(8)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                        ) {
                            Box(
                                Modifier.size(rdp(22)).clip(CircleShape).background(Accent.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) { XaniText("${m.seat}", Type.LabelBold, color = Color.White, fontSizeOverride = 11f) }
                            PersonAvatar(m.user.id, name, m.user.avatarUrl, rdp(40), frameUrl = m.user.frameUrl, frameMime = m.user.frameMime)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(5))) {
                                    XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 14f, maxLines = 1)
                                    LevelMark(m.user.senderLevel, rdp(16))
                                    if (m.user.badgeUrl.isNotEmpty()) NameBadge(m.user.badgeUrl, m.user.badgeMime, rdp(16))
                                }
                                XaniText("💎 " + formatCoins(m.coins), Type.Caption, color = Diamond, fontSizeOverride = 12f)
                            }
                            MicMark(muted = m.muted, size = rdp(12))
                            if (isHost) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(rdp(12)))
                                        .background(Danger.copy(alpha = 0.18f))
                                        .border(1.dp, Danger.copy(alpha = 0.5f), RoundedCornerShape(rdp(12)))
                                        .pressable(pressedScale = 0.92f, onClick = { onDrop(m.user.id) })
                                        .padding(horizontal = rdp(10), vertical = rdp(6)),
                                ) { XaniText(copy.t(Copy.LiveStageRemove), Type.LabelBold, color = Color(0xFFFCA5A5), fontSizeOverride = 11f) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One person on stage as a card — the same shape as the host card: picture,
 * name with level and badge, handle, what the room gave them up there,
 * follow / gift, and Remove for the host.
 */
@Composable
internal fun StagePersonSheet(
    member: StageMemberData,
    isHost: Boolean,
    copy: Copy,
    onGift: (Long) -> Unit,
    onDrop: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val p = member.user
    val me = p.id == ProfileStore.currentUserId
    var person by remember(p.id) { mutableStateOf<PersonData?>(null) }
    val relations by FollowStore.relations.collectAsState()
    val following = relations[p.id]?.following ?: false
    LaunchedEffect(p.id) {
        val r = withContext(Dispatchers.IO) { runFeature(null) { Xani.people.get(p.id) } }
        r?.value?.let {
            person = it
            FollowStore.learn(listOf(it))
            FrameStore.learn(it.id, it.frameUrl, it.frameMime)
            FrameStore.learnBadge(it.id, it.badgeUrl, it.badgeMime)
        }
    }
    LiveSheet(onClose = onClose) { close ->
        val name = person?.fullName?.takeIf { it.isNotBlank() } ?: p.fullName.ifBlank { p.username }
        val handle = (person?.username ?: p.username).takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""
        Column(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(bottom = rdp(18)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.border(rdp(2), Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), CircleShape).padding(rdp(3))) {
                PersonAvatar(p.id, name, person?.avatarUrl ?: p.avatarUrl, rdp(72), frameUrl = p.frameUrl, frameMime = p.frameMime)
            }
            Row(
                Modifier.padding(top = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 17f)
                LevelMark(if (member.seat == 0) p.receiverLevel else p.senderLevel, rdp(18))
                if (p.badgeUrl.isNotEmpty()) NameBadge(p.badgeUrl, p.badgeMime, rdp(18))
            }
            if (handle.isNotBlank()) XaniText(handle, Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 12f)
            person?.bio?.takeIf { it.isNotBlank() }?.let {
                XaniText(it, Type.Caption, color = Color.White.copy(alpha = 0.7f), fontSizeOverride = 12f, maxLines = 2, align = TextAlign.Center, modifier = Modifier.padding(top = rdp(6)))
            }
            Row(Modifier.padding(top = rdp(14)), horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                StageStat("💎 " + formatCoins(member.coins), copy.t(Copy.LiveStageCoins))
                StageStat(copy.t(Copy.LiveStageSince, "t" to stageClock(member.satAtMs)), "seat ${member.seat}")
                person?.let { StageStat(compact(it.followers.toLong()), copy.t(Copy.LiveHostFollowers)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = rdp(18)), horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                if (!me) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(44))
                            .clip(RoundedCornerShape(rdp(22)))
                            .then(
                                if (following) Modifier.background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(rdp(22)))
                                else Modifier.background(Brush.linearGradient(listOf(Accent, Pink))),
                            )
                            .pressable(pressedScale = 0.95f, onClick = { FollowStore.set(p.id, !following) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(if (following) "✓ " + copy.t(Copy.LiveFollowing) else "+ " + copy.t(Copy.LiveFollow), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Pink)))
                        .pressable(pressedScale = 0.95f, onClick = { onGift(p.id); close() }),
                    contentAlignment = Alignment.Center,
                ) { XaniText(copy.t(Copy.LiveSendGift), Type.LabelBold, color = Color.White, fontSizeOverride = 12f) }
                if (isHost && member.seat != 0) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(44))
                            .clip(RoundedCornerShape(rdp(22)))
                            .background(Danger.copy(alpha = 0.18f))
                            .border(1.dp, Danger.copy(alpha = 0.5f), RoundedCornerShape(rdp(22)))
                            .pressable(pressedScale = 0.95f, onClick = { onDrop(p.id); close() }),
                        contentAlignment = Alignment.Center,
                    ) { XaniText(copy.t(Copy.LiveStageRemove), Type.LabelBold, color = Color(0xFFFCA5A5), fontSizeOverride = 12f) }
                }
            }
        }
    }
}

@Composable
private fun StageStat(value: String, label: String) {
    Column(
        Modifier.clip(RoundedCornerShape(rdp(14))).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = rdp(14), vertical = rdp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(value, Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
        XaniText(label, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 10f, modifier = Modifier.padding(top = rdp(2)))
    }
}

/** Host (audio): grow the seat count while live. Nobody moves. */
@Composable
internal fun SeatsSheet(
    stage: StageData,
    copy: Copy,
    onSeats: (Int) -> Unit,
    onClose: () -> Unit,
) {
    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { _ ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(bottom = rdp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            XaniText(copy.t(Copy.LiveSeatsTitle), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
            XaniText(copy.t(Copy.LiveSeatsHint), Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 12f, align = TextAlign.Center, modifier = Modifier.padding(top = rdp(4)))
            val occupied = stage.members.maxOfOrNull { it.seat } ?: 0
            Row(Modifier.padding(top = rdp(18)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(18))) {
                StepButton("−", enabled = stage.seats > maxOf(2, occupied + 1)) { onSeats(stage.seats - 1) }
                XaniText(copy.t(Copy.LiveSeatsCount, "n" to "${stage.seats}"), Type.LabelBold, color = Color.White, fontSizeOverride = 22f)
                StepButton("+", enabled = stage.seats < 35) { onSeats(stage.seats + 1) }
            }
            Row(Modifier.padding(top = rdp(16)), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                listOf(4, 8, 12, 16, 24, 35).forEach { n ->
                    val on = n == stage.seats
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(if (on) Accent else Color.White.copy(alpha = 0.08f))
                            .pressable(enabled = n > occupied, pressedScale = 0.92f, onClick = { onSeats(n) })
                            .padding(horizontal = rdp(12), vertical = rdp(7)),
                    ) { XaniText("$n", Type.LabelBold, color = if (n > occupied) Color.White else Color.White.copy(alpha = 0.3f), fontSizeOverride = 12f) }
                }
            }
        }
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(rdp(44))
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.2f else 0.08f), CircleShape)
            .pressable(enabled = enabled, pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { XaniText(glyph, Type.LabelBold, color = Color.White.copy(alpha = if (enabled) 1f else 0.3f), fontSizeOverride = 20f) }
}

// ─── audio: the seat circle ──────────────────────────────────────────────────

/**
 * Seats around the host: seat 0 in the middle, the rest on one to three
 * rings, seat 1 at the left going clockwise. Empty seats are dashed boxes a
 * viewer taps to sit; a taken seat is that person's box (picture in the
 * middle, 💎 top-right, name under, mic bottom-left). Laid out with a custom
 * Layout: every box is measured once at a fixed size and placed by angle,
 * so 35 seats cost 35 placements and no nested rows.
 */
@Composable
internal fun AudioStage(
    stage: StageData,
    copy: Copy,
    modifier: Modifier = Modifier,
    light: Boolean = false,
    onSeat: (seat: Int, member: StageMemberData?) -> Unit,
) {
    val total = stage.seats.coerceIn(1, 35)
    val guestsN = total - 1
    val bySeat = remember(stage.members) { stage.members.associateBy { it.seat } }
    val rings = ringPlan(guestsN)
    // Box size follows the crowd: eight around the host draw large, thirty-five draw small.
    val boxDp = when {
        guestsN <= 8 -> 68
        guestsN <= 20 -> 56
        else -> 46
    }
    val box = rdp(boxDp)
    val boxH = rdp((boxDp * 1.32f).roundToInt())
    val hostBox = rdp((boxDp * 1.12f).roundToInt())

    Layout(
        modifier = modifier,
        content = {
            // Child 0: the host. Children 1..guestsN: seats in order.
            SeatBox(bySeat[0], seat = 0, size = hostBox, copy = copy, light = light, onTap = { onSeat(0, bySeat[0]) })
            for (i in 1..guestsN) {
                key(i) {
                    SeatBox(bySeat[i], seat = i, size = box, copy = copy, light = light, onTap = { onSeat(i, bySeat[i]) })
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val boxPx = box.roundToPx()
        val boxHPx = boxH.roundToPx()
        val hostPx = hostBox.roundToPx()
        val hostHPx = (hostPx * 1.32f).roundToInt()
        // Ring radii: the first clears the host; each next ring one box further
        // out; everything scaled down so the outer ring stays inside the width.
        val nominal = FloatArray(rings.size) { k -> hostPx * 0.75f + boxHPx * 0.85f + k * boxHPx * 1.02f }
        val maxR = (w / 2f - boxPx / 2f - 4.dp.roundToPx())
        val scale = if (rings.isEmpty()) 1f else minOf(1f, maxR / nominal.last())
        val radii = FloatArray(rings.size) { nominal[it] * scale }
        val outer = if (rings.isEmpty()) hostHPx / 2f else radii.last() + boxHPx / 2f
        val h = (outer * 2 + 8.dp.roundToPx()).roundToInt().coerceAtMost(constraints.maxHeight)
        val cx = w / 2f
        val cy = h / 2f

        val hostP = measurables[0].measure(Constraints.fixed(hostPx, hostHPx))
        val placed = ArrayList<Pair<androidx.compose.ui.layout.Placeable, Pair<Int, Int>>>(measurables.size)
        placed += hostP to Pair((cx - hostPx / 2f).roundToInt(), (cy - hostHPx / 2f).roundToInt())
        var idx = 1
        rings.forEachIndexed { k, count ->
            val r = radii[k]
            for (j in 0 until count) {
                if (idx >= measurables.size) break
                // Seat 1 sits at the left; the ring reads left → top → right → bottom.
                val a = PI + (2 * PI * j / count)
                val x = cx + r * cos(a).toFloat() - boxPx / 2f
                val y = cy + r * sin(a).toFloat() - boxHPx / 2f
                placed += measurables[idx].measure(Constraints.fixed(boxPx, boxHPx)) to Pair(x.roundToInt(), y.roundToInt())
                idx++
            }
        }
        layout(w, h) {
            for ((p, at) in placed) p.placeRelative(at.first, at.second)
        }
    }
}

/** Seats per ring: 8, then 12, then the rest. */
private fun ringPlan(n: Int): List<Int> {
    if (n <= 0) return emptyList()
    val out = ArrayList<Int>(3)
    var left = n
    for (cap in intArrayOf(8, 12, 15)) {
        if (left <= 0) break
        val take = minOf(cap, left)
        out += take
        left -= take
    }
    if (left > 0) out[out.lastIndex] += left
    return out
}

/**
 * One seat box. Taken: avatar centred, 💎 + coins top-right, name under,
 * mic bottom-left. Empty: dashed border, the seat number and a "+".
 */
@Composable
private fun SeatBox(
    member: StageMemberData?,
    seat: Int,
    size: Dp,
    copy: Copy,
    light: Boolean,
    onTap: () -> Unit,
) {
    val corner = rdp(14)
    val ink = if (light) Color(0xFF1E1B3A) else Color.White
    val sub = if (light) Color(0xFF1E1B3A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f)
    if (member == null) {
        val dash = if (light) Color(0xFF7C3AED).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f)
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner))
                .background(if (light) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.05f))
                .drawBehind {
                    drawRoundRect(
                        color = dash,
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                }
                .pressable(pressedScale = 0.94f, onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(size * 0.42f).clip(CircleShape).background(if (light) Color(0xFF7C3AED).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) { XaniText("+", Type.LabelBold, color = sub, fontSizeOverride = 18f) }
                Spacer(Modifier.height(rdp(4)))
                XaniText("$seat", Type.Micro, color = sub, fontSizeOverride = 9f)
            }
        }
        return
    }
    val p = member.user
    val name = p.fullName.ifBlank { p.username }.ifBlank { "…" }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(corner))
            .background(if (light) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (seat == 0) Accent.copy(alpha = 0.8f) else (if (light) Color(0xFFDDD6FE) else Color.White.copy(alpha = 0.14f)), RoundedCornerShape(corner))
            .pressable(pressedScale = 0.95f, onClick = onTap),
    ) {
        Column(Modifier.fillMaxSize().padding(top = size * 0.16f, start = rdp(3), end = rdp(3)), horizontalAlignment = Alignment.CenterHorizontally) {
            PersonAvatar(p.id, name, p.avatarUrl, size * 0.56f, frameUrl = p.frameUrl, frameMime = p.frameMime)
            Spacer(Modifier.height(rdp(3)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(2))) {
                XaniText(name, Type.Micro, color = ink, fontSizeOverride = if (size >= rdp(60)) 10f else 8.5f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LevelMark(if (seat == 0) p.receiverLevel else p.senderLevel, rdp(if (size >= rdp(60)) 11 else 9))
            }
        }
        // 💎 received, top-right inside the box.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = rdp(3), end = rdp(3))
                .clip(RoundedCornerShape(rdp(8)))
                .background(Color.Black.copy(alpha = if (light) 0.35f else 0.45f))
                .padding(horizontal = rdp(4), vertical = rdp(1)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText("💎", Type.Micro, fontSizeOverride = 7f)
            XaniText(formatCoins(member.coins), Type.Micro, color = Diamond, fontSizeOverride = 8f, maxLines = 1)
        }
        // Mic, bottom-left.
        Box(Modifier.align(Alignment.BottomStart).padding(rdp(3))) {
            MicMark(muted = member.muted, size = rdp(if (size >= rdp(60)) 10 else 8))
        }
        if (seat == 0) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(rdp(3))
                    .clip(RoundedCornerShape(rdp(6)))
                    .background(Accent)
                    .padding(horizontal = rdp(4), vertical = rdp(1)),
            ) { XaniText(copy.t(Copy.LivePeopleHost), Type.Micro, color = Color.White, fontSizeOverride = 7f) }
        }
    }
}

// ─── bottom-bar glyphs shared by host and viewer ─────────────────────────────

/** Ask to co-host: a person stepping up, with a plus — the "join stage" request. */
@Composable
internal fun RaiseHandMark(tint: Color, size: Dp) = VectorCanvas(
    shapes = listOf(
        // head + shoulders
        VShape.D("M9.5 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7z", stroke(tint, 1.7f)),
        VShape.D("M2.5 20a7 7 0 0114 0", stroke(tint, 1.7f)),
        // plus badge, top right
        VShape.Line(18.5f, 5.5f, 18.5f, 11.5f, stroke(tint, 1.8f)),
        VShape.Line(15.5f, 8.5f, 21.5f, 8.5f, stroke(tint, 1.8f)),
    ),
    viewport = 24f,
    modifier = Modifier.size(size),
)

/** Phone down: leave the stage / end. */
@Composable
internal fun HangUpMark(tint: Color, size: Dp) = VectorCanvas(
    shapes = listOf(
        VShape.D("M3 14.5c5-5 13-5 18 0l-2.5 2.5-3-1.5v-2.5c-2-.7-4-.7-6 0V15.5l-3 1.5z", stroke(tint, 1.7f)),
    ),
    viewport = 24f,
    modifier = Modifier.size(size),
)

/** Comment bubble: open the composer. */
@Composable
internal fun MessageMark(tint: Color, size: Dp) = VectorCanvas(
    shapes = listOf(
        VShape.D("M13 2.5H3c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h1.5V13l3.2-2.5H13c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1z", stroke(tint, 1.5f)),
    ),
    viewport = 16f,
    modifier = Modifier.size(size),
)

/** Three dots: the host's More sheet. */
@Composable
internal fun MoreMark(tint: Color, size: Dp) = VectorCanvas(
    shapes = listOf(
        VShape.Circle(4f, 8f, 1.6f, app.xani.ui.vector.fill(tint)),
        VShape.Circle(8f, 8f, 1.6f, app.xani.ui.vector.fill(tint)),
        VShape.Circle(12f, 8f, 1.6f, app.xani.ui.vector.fill(tint)),
    ),
    viewport = 16f,
    modifier = Modifier.size(size),
)

/**
 * Stage notices as a short pill: declined, dropped, seat taken. Collected
 * here so neither screen holds a queue; one at a time, three seconds each.
 */
@Composable
internal fun StageNotices(copy: Copy, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        LiveSession.notices.collect { n ->
            text = when (n.code) {
                "stage_denied" -> copy.t(Copy.LiveStageDenied)
                "host_dropped" -> copy.t(Copy.LiveStageDropped)
                "stage_full", "seat_taken" -> n.message.ifBlank { copy.t(Copy.LiveStageFull) }
                "pk_sent" -> copy.t(Copy.PkSentNotice)
                "pk_declined" -> copy.t(Copy.PkDeclined)
                "pk_expired" -> copy.t(Copy.PkExpired)
                "chat_muted", "live_muted" -> copy.t(Copy.LiveChatMuted)
                "chat_unmuted" -> copy.t(Copy.LiveUnmute)
                "made_mod" -> copy.t(Copy.LiveMadeMod)
                "mod_removed" -> copy.t(Copy.LiveModRemoved)
                "mod_mute", "mod_unmute", "mod_block", "mod_unblock", "mod_kick" -> copy.t(Copy.LiveModDone)
                else -> n.message
            }
            if (text.isNotBlank()) {
                delay(3_000)
                text = ""
            }
        }
    }
    if (text.isBlank()) return
    Box(
        modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(Color(0xE6171230))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(16)))
            .padding(horizontal = rdp(14), vertical = rdp(8)),
    ) { XaniText(text, Type.LabelBold, color = Color.White, fontSizeOverride = 12f) }
}

private fun stageClock(satAtMs: Long): String {
    if (satAtMs <= 0L) return "0:00"
    val s = ((System.currentTimeMillis() - satAtMs) / 1000L).coerceAtLeast(0L)
    val m = s / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "%d:%02d".format(m, s % 60)
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000f)
    else -> n.toString()
}
