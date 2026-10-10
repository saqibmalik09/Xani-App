package app.xani.feature.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xani.core.isolate.runFeature
import app.xani.core.net.PersonData
import app.xani.core.net.StageData
import app.xani.core.net.StageMemberData
import app.xani.core.net.StageRequestData
import app.xani.core.net.Xani
import app.xani.feature.create.CreateIco
import app.xani.feature.gift.CardTabs
import app.xani.feature.gift.GiftingPanel
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

/**
 * The stage, drawn: co-host tiles in a video room, the seat grid in an
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
 * one "…" chip for the rest. Both screens keep them on the far side from the
 * comments and directly above them: the host's screen stacks them down the
 * right edge ([horizontal] false), a viewer's lays them left → right on the
 * right ([horizontal] true). Nothing else sits there, so the tiles never land
 * on the chat or the action chrome. Tapping a tile opens that person; the chip
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
        // Four 72dp tiles plus the "…" chip is wider than a 360dp phone, and a
        // right-aligned Row that overruns its parent puts tile 1 off the left
        // edge. Scrolling keeps the row inside the screen with every tile
        // reachable; it still wraps its content, so a short rail stays right.
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) { tiles() }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(rdp(6)), horizontalAlignment = Alignment.End) { tiles() }
    }
}

/**
 * One co-host: their camera when they have turned it on, their profile
 * picture otherwise — so the frame shows the person, never a black box; the
 * seat number top-left, the mic state bottom-left, 💎 bottom-right.
 *
 * Whose camera is on comes from that person's own seat state, not from the
 * host's room rule. The rule only decides whether they have the button: read
 * off the rule instead, every co-host's tile would open a camera the moment
 * the host allowed them, which is someone else's room on screen.
 */
@Composable
private fun CoHostTile(m: StageMemberData, index: Int, mediaRev: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val me = m.user.id == ProfileStore.currentUserId
    // My own tile follows my local toggle, which is a tap ahead of the
    // snapshot; everyone else's follows what the room says they publish.
    val session by LiveSession.state.collectAsState()
    val camOff = if (me) session?.cameraOff != false else !m.cam
    // Re-ask for the renderer whenever the media session, the track set or
    // the camera state moved; the renderer itself is reused for the same person.
    val view = remember(m.user.id, mediaRev, me, camOff) {
        if (camOff) null else if (me) LiveSession.localView(ctx) else LiveSession.remoteView(ctx, m.user.id)
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
            VideoSurface(view, Modifier.fillMaxSize())
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
    var tab by remember { mutableIntStateOf(0) }
    val roomId = LiveSession.state.collectAsState().value?.roomId ?: 0L
    LiveSheet(onClose = onClose) { close ->
        val name = person?.fullName?.takeIf { it.isNotBlank() } ?: p.fullName.ifBlank { p.username }
        val handle = (person?.username ?: p.username).takeIf { it.isNotBlank() }?.let { "@$it" } ?: ""
        CardTabs(tab, copy, onSelect = { tab = it }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = rdp(10)))
        if (tab == 1) {
            GiftingPanel(userId = p.id, copy = copy, roomId = roomId, modifier = Modifier.padding(top = rdp(10), bottom = rdp(12)))
            return@LiveSheet
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(top = rdp(12), bottom = rdp(18)),
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
                StepButton("+", enabled = stage.seats < StageData.AudioSeatMax) { onSeats(stage.seats + 1) }
            }
            Row(Modifier.padding(top = rdp(16)), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                listOf(6, 12, 18, 24, 36, 45).forEach { n ->
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

// ─── audio: the seat grid ────────────────────────────────────────────────────

/**
 * Host is seat 0 at the top-left. Guests follow in order, left → right,
 * then the next row — 6 across when the cell stays ≥ 52 dp, otherwise 5.
 * Empty seats are dashed boxes a viewer taps to sit. The grid scrolls
 * inside the leftover height so 45 seats never shove chat off screen.
 */
@Composable
internal fun AudioStage(
    stage: StageData,
    copy: Copy,
    modifier: Modifier = Modifier,
    light: Boolean = false,
    onSeat: (seat: Int, member: StageMemberData?) -> Unit,
) {
    val total = stage.seats.coerceIn(1, StageData.AudioSeatMax)
    val bySeat = remember(stage.members) { stage.members.associateBy { it.seat } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = rdp(8)
        val cols = audioColumns(maxWidth, gap)
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        val cellH = cellW * 1.28f
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            val rows = (total + cols - 1) / cols
            for (row in 0 until rows) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (col in 0 until cols) {
                        val seat = row * cols + col
                        if (seat < total) {
                            key(seat) {
                                Box(Modifier.weight(1f).height(cellH)) {
                                    SeatBox(
                                        bySeat[seat],
                                        seat = seat,
                                        size = cellW,
                                        copy = copy,
                                        light = light,
                                        onTap = { onSeat(seat, bySeat[seat]) },
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(cellH))
                        }
                    }
                }
            }
        }
    }
}

/** 6 per row when each cell stays usable; 5 on a narrow phone. */
private fun audioColumns(maxWidth: Dp, gap: Dp): Int {
    if (maxWidth < 80.dp) return 5
    return if ((maxWidth - gap * 5) / 6 >= 52.dp) 6 else 5
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
                XaniText(
                    if (seat == 0) copy.t(Copy.LivePeopleHost) else "$seat",
                    Type.Micro,
                    color = sub,
                    fontSizeOverride = 9f,
                )
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
        // A video camera with a plus on its body: "ask to join on camera",
        // which is what the request is — not "add a friend".
        VShape.Box(2.5f, 6.5f, 13.5f, 11f, 2.6f, stroke(tint, 1.7f)),
        VShape.D("M16 10.5l5-3v9l-5-3z", stroke(tint, 1.7f)),
        VShape.Line(9.25f, 9.25f, 9.25f, 14.75f, stroke(tint, 1.8f)),
        VShape.Line(6.5f, 12f, 12f, 12f, stroke(tint, 1.8f)),
    ),
    viewport = 24f,
    modifier = Modifier.size(size),
)

/** Shield with a tick: the room's moderators. */
@Composable
internal fun ModeratorMark(tint: Color, size: Dp) = VectorCanvas(
    shapes = listOf(
        VShape.D("M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z", stroke(tint, 1.8f)),
        VShape.D("M9 12l2 2 4-4", stroke(tint, 1.8f)),
    ),
    viewport = 24f,
    modifier = Modifier.size(size),
)

/** Gift box: what a sender taps to give. The diamond is the receiver's counter. */
@Composable
internal fun GiftBoxMark(tint: Color = Color.White, size: Dp = rdp(20)) = VectorCanvas(
    shapes = listOf(
        VShape.D("M2.5 5.5h11V8h-11z", stroke(tint, 1.5f)),
        VShape.D("M3.5 8v5.5h9V8", stroke(tint, 1.5f)),
        VShape.Line(8f, 5.5f, 8f, 13.5f, stroke(tint, 1.5f)),
        VShape.D("M8 5.5C8 3.8 6.7 2.4 5.6 3 4.5 3.6 6 5.5 8 5.5z", stroke(tint, 1.5f)),
        VShape.D("M8 5.5c0-1.7 1.3-3.1 2.4-2.5 1.1.6-.4 2.5-2.4 2.5z", stroke(tint, 1.5f)),
    ),
    viewport = 16f,
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
