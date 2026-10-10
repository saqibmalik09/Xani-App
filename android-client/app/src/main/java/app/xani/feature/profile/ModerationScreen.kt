package app.xani.feature.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.isolate.runFeature
import app.xani.core.net.ModUser
import app.xani.core.net.Xani
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The separate in-app moderation screen for delegated accounts: search any
 * user by id / username / name, then allow or revoke screen capture, disable
 * or re-enable their microphone, send a warning with a reason, and block or
 * unblock the account. Which sections show follows the caller's permissions —
 * screen.capture.manage for capture, mod.app_users for the rest — and every
 * action is enforced server-side regardless of what this screen shows.
 */
@Composable
internal fun ModerationScreen(copy: Copy, onBack: () -> Unit) {
    var open by remember { mutableStateOf<ModUser?>(null) }
    BackHandler { if (open != null) open = null else onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg2)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = rdp(20), vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(40)).pressable(onClick = { if (open != null) open = null else onBack() }),
                contentAlignment = Alignment.Center,
            ) { ProfileIco.Back(ProfileTokens.Text) }
            XaniText(
                open?.displayName ?: copy.t(Copy.ModTitle),
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 17f,
                maxLines = 1,
            )
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

        val picked = open
        if (picked == null) {
            ModSearch(copy) { open = it }
        } else {
            ModUserCard(copy, picked, onChanged = { open = it })
        }
    }
}

// ─── search ──────────────────────────────────────────────────────────────────

@Composable
private fun ModSearch(copy: Copy, onPick: (ModUser) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    // Debounced server search: one request 350ms after the last keystroke.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        results = runFeature(emptyList()) { Xani.mod.search(q).data ?: emptyList() }
        searching = false
        searched = true
    }

    val shape = RoundedCornerShape(rdp(12))
    BasicTextField(
        value = query,
        onValueChange = { query = it.take(64) },
        singleLine = true,
        textStyle = Type.Label.copy(color = ProfileTokens.Text),
        cursorBrush = SolidColor(ProfileTokens.Accent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(16), vertical = rdp(12)),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White)
                    .border(rdp(1), ProfileTokens.Hairline, shape)
                    .padding(horizontal = rdp(14), vertical = rdp(12)),
            ) {
                if (query.isEmpty()) {
                    XaniText(copy.t(Copy.ModSearchHint), Type.Label, color = ProfileTokens.Muted)
                }
                field()
            }
        },
    )

    LazyColumn(Modifier.fillMaxSize()) {
        if (searching) {
            item(key = "busy") {
                Box(Modifier.fillMaxWidth().padding(rdp(24)), contentAlignment = Alignment.Center) {
                    Spinner(rdp(20), ProfileTokens.Accent)
                }
            }
        } else if (searched && results.isEmpty()) {
            item(key = "empty") {
                XaniText(
                    copy.t(Copy.ModSearchEmpty),
                    Type.Caption,
                    color = ProfileTokens.Muted,
                    fontSizeOverride = 14f,
                    modifier = Modifier.fillMaxWidth().padding(rdp(32)),
                    align = TextAlign.Center,
                )
            }
        }
        items(results.size, key = { results[it].id }) { i ->
            val person = results[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .pressable(onClick = { onPick(person) })
                    .padding(horizontal = rdp(18), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(person.id, person.displayName, person.avatarUrl, rdp(44))
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    XaniText(person.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
                    XaniText(
                        "@${person.username} · ${person.id}",
                        Type.Micro,
                        color = ProfileTokens.Muted,
                        fontSizeOverride = 11f,
                        maxLines = 1,
                    )
                }
                if (person.banned) {
                    XaniText(copy.t(Copy.ModBlocked), Type.Micro, color = ProfileTokens.Danger, fontSizeOverride = 11f)
                }
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
        }
    }
}

// ─── one account's card ──────────────────────────────────────────────────────

@Composable
private fun ModUserCard(copy: Copy, picked: ModUser, onChanged: (ModUser) -> Unit) {
    val scope = rememberCoroutineScope()
    var user by remember(picked.id) { mutableStateOf(picked) }
    var busy by remember(picked.id) { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    // The search row has no flags; the card read does. Refresh once on open.
    LaunchedEffect(picked.id) {
        runFeature(null as ModUser?) { Xani.mod.user(picked.id).data }?.let { user = it; onChanged(it) }
    }
    LaunchedEffect(toast) {
        if (toast.isEmpty()) return@LaunchedEffect
        delay(1800)
        toast = ""
    }

    fun refresh(message: String) {
        scope.launch {
            runFeature(null as ModUser?) { Xani.mod.user(picked.id).data }?.let { user = it; onChanged(it) }
            busy = false
            toast = message
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(rdp(16)),
            verticalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            // Who this is.
            ModCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(user.id, user.displayName, user.avatarUrl, rdp(52))
                    Spacer(Modifier.width(rdp(12)))
                    Column(Modifier.weight(1f)) {
                        XaniText(user.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f, maxLines = 1)
                        XaniText("@${user.username} · ${user.id}", Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 12f, maxLines = 1)
                    }
                    if (user.banned) {
                        StatePill(copy.t(Copy.ModBlocked), ProfileTokens.Danger)
                    }
                }
            }

            // Screen capture — needs screen.capture.manage.
            if (Xani.session.can("screen.capture.manage")) {
                ModCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XaniText(copy.t(Copy.ModCapture), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.weight(1f))
                        StatePill(
                            copy.t(if (user.canCapture) Copy.ModCaptureOn else Copy.ModCaptureOff),
                            if (user.canCapture) ProfileTokens.Accent else ProfileTokens.Muted,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = rdp(10)),
                        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                    ) {
                        ModChip(copy.t(Copy.ModCaptureAllow), user.captureOverride == "allow", Modifier.weight(1f)) {
                            if (busy) return@ModChip
                            busy = true
                            scope.launch {
                                val r = Xani.mod.setCapture(user.id, "allow")
                                if (r.ok) refresh(copy.t(Copy.ModSaved)) else { busy = false; toast = r.message }
                            }
                        }
                        ModChip(copy.t(Copy.ModCaptureRevoke), user.captureOverride == "deny", Modifier.weight(1f)) {
                            if (busy) return@ModChip
                            busy = true
                            scope.launch {
                                val r = Xani.mod.setCapture(user.id, "revoke")
                                if (r.ok) refresh(copy.t(Copy.ModSaved)) else { busy = false; toast = r.message }
                            }
                        }
                        ModChip(copy.t(Copy.ModCaptureInherit), user.captureOverride == "inherit", Modifier.weight(1f)) {
                            if (busy) return@ModChip
                            busy = true
                            scope.launch {
                                val r = Xani.mod.setCapture(user.id, "inherit")
                                if (r.ok) refresh(copy.t(Copy.ModSaved)) else { busy = false; toast = r.message }
                            }
                        }
                    }
                }
            }

            if (Xani.session.can("mod.app_users")) {
                // Microphone.
                var micReason by remember(user.id) { mutableStateOf("") }
                ModCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XaniText(copy.t(Copy.ModMic), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.weight(1f))
                        StatePill(
                            copy.t(if (user.micDisabled) Copy.ModMicOff else Copy.ModMicOn),
                            if (user.micDisabled) ProfileTokens.Danger else ProfileTokens.Accent,
                        )
                    }
                    if (!user.micDisabled) {
                        ModReasonField(copy, micReason, { micReason = it })
                    }
                    ModButton(
                        copy.t(if (user.micDisabled) Copy.ModMicEnable else Copy.ModMicDisable),
                        danger = !user.micDisabled,
                        busy = busy,
                    ) {
                        if (busy) return@ModButton
                        busy = true
                        scope.launch {
                            val r = Xani.mod.setMic(user.id, disabled = !user.micDisabled, reason = micReason)
                            if (r.ok) refresh(copy.t(Copy.ModSaved)) else { busy = false; toast = r.message }
                        }
                    }
                }

                // Warning with a reason.
                var warnReason by remember(user.id) { mutableStateOf("") }
                ModCard {
                    XaniText(copy.t(Copy.ModWarn), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
                    ModReasonField(copy, warnReason, { warnReason = it })
                    ModButton(copy.t(Copy.ModWarnSend), danger = false, busy = busy) {
                        if (busy) return@ModButton
                        if (warnReason.isBlank()) {
                            toast = copy.t(Copy.ModReasonRequired)
                            return@ModButton
                        }
                        busy = true
                        scope.launch {
                            val r = Xani.mod.warn(user.id, warnReason)
                            busy = false
                            if (r.ok) {
                                warnReason = ""
                                toast = copy.t(Copy.ModWarnSent)
                            } else {
                                toast = r.message
                            }
                        }
                    }
                }

                // Block / unblock the account.
                var blockReason by remember(user.id) { mutableStateOf("") }
                ModCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XaniText(copy.t(Copy.ModBlock), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.weight(1f))
                        if (user.banned) StatePill(copy.t(Copy.ModBlocked), ProfileTokens.Danger)
                    }
                    if (user.banned && user.banReason.isNotBlank()) {
                        XaniText(user.banReason, Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(4)))
                    }
                    if (!user.banned) {
                        ModReasonField(copy, blockReason, { blockReason = it })
                    }
                    ModButton(
                        copy.t(if (user.banned) Copy.ModUnblock else Copy.ModBlock),
                        danger = !user.banned,
                        busy = busy,
                    ) {
                        if (busy) return@ModButton
                        if (!user.banned && blockReason.isBlank()) {
                            toast = copy.t(Copy.ModReasonRequired)
                            return@ModButton
                        }
                        busy = true
                        scope.launch {
                            val r = if (user.banned) Xani.mod.unblock(user.id) else Xani.mod.block(user.id, blockReason)
                            if (r.ok) {
                                blockReason = ""
                                refresh(copy.t(Copy.ModSaved))
                            } else {
                                busy = false
                                toast = r.message
                            }
                        }
                    }
                }
            }
        }

        if (toast.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = rdp(28))
                    .clip(RoundedCornerShape(rdp(18)))
                    .background(Color(0xCC111111))
                    .padding(horizontal = rdp(16), vertical = rdp(9)),
            ) {
                XaniText(toast, Type.Label, color = Color.White, fontSizeOverride = 13f)
            }
        }
    }
}

// ─── small pieces ────────────────────────────────────────────────────────────

@Composable
private fun ModCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color.White)
            .border(rdp(1), ProfileTokens.Hairline, RoundedCornerShape(rdp(14)))
            .padding(rdp(14)),
    ) { content() }
}

@Composable
private fun StatePill(label: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(10)))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = rdp(8), vertical = rdp(3)),
    ) { XaniText(label, Type.LabelBold, color = color, fontSizeOverride = 11f) }
}

@Composable
private fun ModChip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(rdp(16))
    Box(
        modifier
            .height(rdp(32))
            .clip(shape)
            .background(if (on) ProfileTokens.Accent else ProfileTokens.Bg2)
            .border(rdp(1), if (on) ProfileTokens.Accent else ProfileTokens.Hairline, shape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else ProfileTokens.Text, fontSizeOverride = 12f, maxLines = 1)
    }
}

@Composable
private fun ModReasonField(copy: Copy, value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(rdp(10))
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(180)) },
        textStyle = Type.Label.copy(color = ProfileTokens.Text),
        cursorBrush = SolidColor(ProfileTokens.Accent),
        modifier = Modifier.fillMaxWidth().padding(top = rdp(10)),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ProfileTokens.Bg2)
                    .border(rdp(1), ProfileTokens.Hairline, shape)
                    .padding(horizontal = rdp(12), vertical = rdp(10)),
            ) {
                if (value.isEmpty()) {
                    XaniText(copy.t(Copy.ModReasonHint), Type.Label, color = ProfileTokens.Muted)
                }
                field()
            }
        },
    )
}

@Composable
private fun ModButton(label: String, danger: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(rdp(12))
    val bg = if (danger) ProfileTokens.DangerSoft else ProfileTokens.AccentSoft
    val fg = if (danger) ProfileTokens.Danger else ProfileTokens.AccentDeep
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = rdp(10))
            .clip(shape)
            .background(bg)
            .pressable(onClick = onClick)
            .padding(vertical = rdp(11)),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) Spinner(rdp(16), fg) else XaniText(label, Type.LabelBold, color = fg, fontSizeOverride = 14f)
    }
}
