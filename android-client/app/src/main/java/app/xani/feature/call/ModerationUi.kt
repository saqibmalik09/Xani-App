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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xani.core.net.LiveBannedData
import app.xani.core.net.LiveChatData
import app.xani.core.net.LiveModeratorData
import app.xani.core.net.LivePersonData
import app.xani.feature.gift.CardTabs
import app.xani.feature.gift.GiftingPanel
import app.xani.feature.inbox.EmojiPickSheet
import app.xani.feature.inbox.QuickReactions
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.FollowStore
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

/*
 * Room moderation and chat reactions, drawn. Everything reads the live
 * [LiveSession.state] snapshot for who may do what; the server is the judge
 * and answers with a frame the whole room sees.
 */

private val ModAccent = Color(0xFF8B5CF6)
private val ModPink = Color(0xFFEC4899)
private val ModDanger = Color(0xFFEF4444)

/**
 * Someone in chat, as a small card: picture, name, profile / gift, and for
 * the host and their moderators the moderation rows — mute, block, kick,
 * and (host only) make / remove moderator.
 */
@Composable
internal fun LiveUserSheet(
    user: LivePersonData,
    copy: Copy,
    onGift: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val live by LiveSession.state.collectAsState()
    val ui = live
    val me = ProfileStore.currentUserId
    val isHost = ui?.role == LiveRole.Host
    val hostId = ui?.room?.hostId ?: 0L
    val targetMod = user.mod || (ui?.mods?.contains(user.id) == true)
    // The host rules everyone; a moderator rules plain viewers only.
    val canMod = ui != null && ui.canModerate && user.id != me && user.id != hostId && (isHost || !targetMod)
    val relations by FollowStore.relations.collectAsState()
    val following = relations[user.id]?.following ?: false
    var pick by remember { mutableStateOf<String?>(null) } // mute | block | mod
    var tab by remember { mutableIntStateOf(0) }
    val name = user.fullName.ifBlank { user.username }.ifBlank { "…" }

    LiveSheet(onClose = onClose) { close ->
        CardTabs(tab, copy, onSelect = { tab = it }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = rdp(10)))
        if (tab == 1) {
            GiftingPanel(userId = user.id, copy = copy, roomId = ui?.roomId ?: 0L, modifier = Modifier.padding(top = rdp(10), bottom = rdp(12)))
            return@LiveSheet
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(top = rdp(12), bottom = rdp(18)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .border(rdp(2), Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), CircleShape)
                    .padding(rdp(3))
                    .pressable(pressedScale = 0.94f, onClick = { close(); PersonRouter.open(user.id) }),
            ) {
                PersonAvatar(user.id, name, user.avatarUrl, rdp(64), frameUrl = user.frameUrl, frameMime = user.frameMime)
            }
            Row(
                Modifier.padding(top = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
                LevelMark(user.senderLevel, rdp(18))
                if (user.badgeUrl.isNotEmpty()) NameBadge(user.badgeUrl, user.badgeMime, rdp(18))
                if (targetMod) ModTag()
            }
            if (user.username.isNotBlank()) {
                XaniText("@${user.username}", Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 12f)
            }

            Row(Modifier.fillMaxWidth().padding(top = rdp(16)), horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                Pill(copy.t(Copy.LiveViewProfile), Modifier.weight(1f), fill = Color.White.copy(alpha = 0.08f), outline = Color.White.copy(alpha = 0.16f)) {
                    close(); PersonRouter.open(user.id)
                }
                if (user.id != me) {
                    Pill(
                        if (following) "✓ " + copy.t(Copy.LiveFollowing) else "+ " + copy.t(Copy.LiveFollow),
                        Modifier.weight(1f),
                        brush = if (following) null else Brush.linearGradient(listOf(ModAccent, ModPink)),
                        fill = Color.White.copy(alpha = 0.08f),
                        outline = if (following) Color.White.copy(alpha = 0.16f) else null,
                    ) { FollowStore.set(user.id, !following) }
                    Pill(copy.t(Copy.LiveSendGift), Modifier.weight(1f), brush = Brush.linearGradient(listOf(Color(0xFFF59E0B), ModPink))) {
                        onGift(user.id); close()
                    }
                }
            }

            if (canMod) {
                XaniText(
                    copy.t(Copy.LiveModeration).uppercase(),
                    Type.LabelBold,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSizeOverride = 10f,
                    modifier = Modifier.align(Alignment.Start).padding(top = rdp(18), bottom = rdp(6)),
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(16))).background(Color.White.copy(alpha = 0.05f)),
                ) {
                    if (isHost) {
                        ModRow(
                            if (targetMod) copy.t(Copy.LiveRemoveMod) else copy.t(Copy.LiveMakeMod),
                            if (targetMod) Color(0xFFFCA5A5) else Color(0xFF34D399),
                        ) {
                            if (targetMod) LiveSession.setModerator(user.id, 0) { if (it) close() } else pick = "mod"
                        }
                        ModDivider()
                    }
                    ModRow(copy.t(Copy.LiveMute), Color.White) { pick = "mute" }
                    ModDivider()
                    // One tap, no duration: a block holds for the rest of this
                    // broadcast and lifts by itself when it ends. The session's
                    // blocklist (moderation sheet) is where it can be undone.
                    ModRow(copy.t(Copy.LiveBlockLive), Color(0xFFFCD34D)) {
                        LiveSession.moderate(user.id, "block") { ok, _ -> if (ok) close() }
                    }
                    ModDivider()
                    ModRow(copy.t(Copy.LiveKick), Color(0xFFFCA5A5)) {
                        LiveSession.moderate(user.id, "kick") { ok, _ -> if (ok) close() }
                    }
                }
            }
        }
    }

    when (pick) {
        "mute" -> DurationSheet(
            title = copy.t(Copy.LiveMuteFor),
            presets = listOf(5, 10, 30),
            unit = { copy.t(Copy.LiveMinutes, "n" to "$it") },
            max = 24 * 60,
            copy = copy,
            onPick = { m -> pick = null; LiveSession.moderate(user.id, "mute", m) { ok, _ -> if (ok) onClose() } },
            onClose = { pick = null },
        )
        "mod" -> DurationSheet(
            title = copy.t(Copy.LiveModFor),
            presets = listOf(1, 7, 30),
            unit = { copy.t(Copy.LiveDays, "n" to "$it") },
            max = 365,
            copy = copy,
            onPick = { d -> pick = null; LiveSession.setModerator(user.id, d) { ok -> if (ok) onClose() } },
            onClose = { pick = null },
        )
    }
}

/** Three presets and a custom number — minutes or days, the caller says which. */
@Composable
private fun DurationSheet(
    title: String,
    presets: List<Int>,
    unit: (Int) -> String,
    max: Int,
    copy: Copy,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val n = custom.toIntOrNull()?.coerceIn(1, max)
    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { close ->
        Column(
            Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(bottom = rdp(24)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            XaniText(title, Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
            Row(Modifier.padding(top = rdp(16)), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                presets.forEach { p ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(14)))
                            .pressable(pressedScale = 0.94f, onClick = { close(); onPick(p) })
                            .padding(horizontal = rdp(16), vertical = rdp(10)),
                    ) { XaniText(unit(p), Type.LabelBold, color = Color.White, fontSizeOverride = 13f) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(rdp(14)))
                        .padding(horizontal = rdp(14)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (custom.isEmpty()) {
                        XaniText(copy.t(Copy.LiveCustom), Type.Caption, color = Color.White.copy(alpha = 0.4f), fontSizeOverride = 13f)
                    }
                    BasicTextField(
                        value = custom,
                        onValueChange = { v -> custom = v.filter { it.isDigit() }.take(6) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(ModAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Pill(
                    if (n != null) unit(n) + " · " + copy.t(Copy.LiveApply) else copy.t(Copy.LiveApply),
                    Modifier.width(rdp(130)),
                    brush = Brush.linearGradient(listOf(ModAccent, ModPink)),
                    enabled = n != null,
                ) { n?.let { close(); onPick(it) } }
            }
        }
    }
}

/**
 * The host's moderators, with how long each has left; the host may remove
 * them. Below it, everyone blocked or removed in THIS broadcast — a session
 * list that empties itself when the live ends — with an Unblock that lets
 * them back in right away.
 */
@Composable
internal fun LiveModeratorsSheet(copy: Copy, onClose: () -> Unit) {
    val live by LiveSession.state.collectAsState()
    val isHost = live?.role == LiveRole.Host
    val canMod = live?.canModerate == true
    var items by remember { mutableStateOf<List<LiveModeratorData>?>(null) }
    var bans by remember { mutableStateOf<List<LiveBannedData>?>(null) }
    LaunchedEffect(Unit) {
        items = LiveSession.moderators() ?: emptyList()
        // An older server has no session list; an empty section reads fine.
        bans = LiveSession.sessionBans() ?: emptyList()
    }
    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { _ ->
        Column(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(bottom = rdp(20))) {
            val list = items
            val banned = bans
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = rdp(440)),
                verticalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                item(key = "mods-title") {
                    XaniText(copy.t(Copy.LiveModerators), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
                }
                when {
                    list == null -> item(key = "mods-wait") { Spacer(Modifier.height(rdp(60))) }
                    list.isEmpty() -> item(key = "mods-none") {
                        XaniText(
                            copy.t(Copy.LiveModeratorsNone), Type.Caption, color = Color.White.copy(alpha = 0.55f),
                            fontSizeOverride = 12f, align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = rdp(20)),
                        )
                    }
                    else -> items(list, key = { "mod-" + it.user.id }) { m ->
                        val u = m.user
                        val name = u.fullName.ifBlank { u.username }.ifBlank { "…" }
                        val daysLeft = ((m.expiresMs - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                        PersonRow(
                            user = u,
                            line = if (m.expiresMs > 0) copy.t(Copy.LiveDaysLeft, "n" to "$daysLeft") else "@${u.username}",
                            action = if (isHost) copy.t(Copy.LiveStageRemove) else null,
                            onAction = {
                                LiveSession.setModerator(u.id, 0) { ok -> if (ok) items = list.filterNot { it.user.id == u.id } }
                            },
                        )
                    }
                }
                item(key = "bans-title") {
                    XaniText(
                        copy.t(Copy.LiveBlockedTitle), Type.LabelBold, color = Color.White, fontSizeOverride = 16f,
                        modifier = Modifier.padding(top = rdp(14)),
                    )
                }
                when {
                    banned == null -> item(key = "bans-wait") { Spacer(Modifier.height(rdp(40))) }
                    banned.isEmpty() -> item(key = "bans-none") {
                        XaniText(
                            copy.t(Copy.LiveBlockedNone), Type.Caption, color = Color.White.copy(alpha = 0.55f),
                            fontSizeOverride = 12f, align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = rdp(20)),
                        )
                    }
                    else -> items(banned, key = { "ban-" + it.user.id }) { b ->
                        val what = if (b.reason == "kicked") copy.t(Copy.LiveRemovedTag) else copy.t(Copy.LiveBlockedTag)
                        PersonRow(
                            user = b.user,
                            line = if (b.active) what else what + " · " + copy.t(Copy.LiveBanOver),
                            action = if (canMod && b.active) copy.t(Copy.LiveUnblock) else null,
                            onAction = {
                                LiveSession.moderate(b.user.id, "unblock") { ok, _ ->
                                    if (ok) bans = banned.filterNot { it.user.id == b.user.id }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One person line in the moderation sheet: picture, name, a caption, one action. */
@Composable
private fun PersonRow(
    user: LivePersonData,
    line: String,
    action: String?,
    onAction: () -> Unit,
) {
    val name = user.fullName.ifBlank { user.username }.ifBlank { "…" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14))).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = rdp(12), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
    ) {
        Box(Modifier.pressable(onClick = { PersonRouter.open(user.id) })) {
            PersonAvatar(user.id, name, user.avatarUrl, rdp(38), frameUrl = user.frameUrl, frameMime = user.frameMime)
        }
        Column(Modifier.weight(1f)) {
            XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 13f, maxLines = 1)
            XaniText(line, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 11f, maxLines = 1)
        }
        if (action != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(ModDanger.copy(alpha = 0.16f))
                    .border(1.dp, ModDanger.copy(alpha = 0.45f), RoundedCornerShape(rdp(12)))
                    .pressable(pressedScale = 0.94f, onClick = onAction)
                    .padding(horizontal = rdp(12), vertical = rdp(7)),
            ) { XaniText(action, Type.LabelBold, color = Color(0xFFFCA5A5), fontSizeOverride = 11f) }
        }
    }
}

/**
 * Long-press on a chat line: the quick emoji row with a "+" for the full
 * picker, and the person's name to open their card. Tapping outside closes.
 */
@Composable
internal fun LiveChatReactOverlay(
    line: LiveChatData,
    copy: Copy,
    onReact: (String) -> Unit,
    onUser: () -> Unit,
    onClose: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pressable(onClick = onClose))
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = rdp(24))
                .clip(RoundedCornerShape(rdp(22)))
                .background(Color(0xFF1B1430))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(rdp(22)))
                .padding(rdp(14)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(rdp(6)), verticalAlignment = Alignment.CenterVertically) {
                QuickReactions.forEach { e ->
                    val on = e == line.myReaction
                    Box(
                        Modifier
                            .size(rdp(40))
                            .clip(CircleShape)
                            .background(if (on) ModAccent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f))
                            .pressable(pressedScale = 0.85f, onClick = { onReact(e); onClose() }),
                        contentAlignment = Alignment.Center,
                    ) { XaniText(e, Type.Label, fontSizeOverride = 20f) }
                }
                Box(
                    Modifier
                        .size(rdp(40))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .pressable(pressedScale = 0.85f, onClick = { more = true }),
                    contentAlignment = Alignment.Center,
                ) { XaniText("⋯", Type.LabelBold, color = Color.White, fontSizeOverride = 18f) }
            }
            LiveChatLine(
                line,
                nameTint = Color(0xFFC4B5FD),
                background = Color.White.copy(alpha = 0.05f),
                corner = rdp(12),
                badgeSize = rdp(14),
                onUser = { onClose(); onUser() },
                likeLabel = copy.t(Copy.LiveSentLike),
                enterLabel = copy.t(Copy.LiveEnteredRoom),
                leaveLabel = copy.t(Copy.LiveLeftRoom),
            )
            Pill(copy.t(Copy.LiveViewProfile), Modifier.fillMaxWidth().padding(top = rdp(10)), fill = Color.White.copy(alpha = 0.08f), outline = Color.White.copy(alpha = 0.16f)) {
                onClose(); onUser()
            }
        }
    }
    if (more) {
        EmojiPickSheet(
            copy = copy,
            selected = line.myReaction.takeIf { it.isNotEmpty() },
            onPick = { e -> more = false; onReact(e); onClose() },
            onDismiss = { more = false },
        )
    }
}

/** Small green MOD tag beside a moderator's name. */
@Composable
internal fun ModTag() {
    XaniText(
        "MOD", Type.LabelBold, color = Color(0xFF34D399), fontSizeOverride = 9f,
        modifier = Modifier.clip(RoundedCornerShape(rdp(5))).background(Color(0x3334D399)).padding(horizontal = rdp(5), vertical = rdp(1)),
    )
}

@Composable
private fun ModRow(label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = rdp(16), vertical = rdp(13)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(label, Type.LabelBold, color = tint, fontSizeOverride = 13f, modifier = Modifier.weight(1f))
        XaniText("›", Type.LabelBold, color = Color.White.copy(alpha = 0.35f), fontSizeOverride = 16f)
    }
}

@Composable
private fun ModDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun Pill(
    label: String,
    modifier: Modifier,
    brush: Brush? = null,
    fill: Color = Color.Transparent,
    outline: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(rdp(44))
            .clip(RoundedCornerShape(rdp(22)))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(fill))
            .then(if (outline != null) Modifier.border(1.dp, outline, RoundedCornerShape(rdp(22))) else Modifier)
            .pressable(enabled = enabled, pressedScale = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), fontSizeOverride = 12f, maxLines = 1)
    }
}
