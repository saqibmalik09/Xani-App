package app.xani.feature.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.xani.core.media.RemoteImage
import app.xani.core.net.AuthState
import app.xani.core.net.MediaConfig
import app.xani.core.net.SavedAccount
import app.xani.core.net.Xani
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

private const val SheetMax = 0.80f
private const val SheetRest = 0.62f
private const val SheetMin = 0.08f

/**
 * Account picker: switch between saved accounts, or add another via a one-tap
 * "Log in to another account" that then offers Create vs Log in.
 *
 * Whole-surface drag (not just the handle): pull up to 80% of the screen,
 * pull down to dismiss. The account list keeps its own scroll and hands the
 * sheet the leftover pull when it is already at the top.
 */
@Composable
internal fun AccountSwitcherSheet(
    copy: Copy,
    onClose: () -> Unit,
    onCreateAccount: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val accounts by Xani.session.accounts.collectAsState()
    val auth by Xani.session.state.collectAsState()
    val activeId = (auth as? AuthState.SignedIn)?.user?.id ?: 0L
    val switchError by Xani.session.switchError.collectAsState()
    val switching by Xani.session.switching.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    // The row the user tapped. The sheet stays up until the server has agreed,
    // so a refused switch shows its reason here instead of closing onto the
    // account the user was trying to leave.
    var requested by remember { mutableLongStateOf(0L) }

    val screenPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    var target by remember { mutableFloatStateOf(SheetMin) }
    val animated = remember { Animatable(SheetMin) }
    var dragging by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val frac = if (dragging) target else animated.value

    LaunchedEffect(target, dragging, closing) {
        if (dragging) {
            animated.snapTo(target)
            return@LaunchedEffect
        }
        animated.animateTo(
            target,
            spring(dampingRatio = 0.88f, stiffness = 380f),
        )
        if (closing && target <= SheetMin + 0.01f) onClose()
    }

    fun settle() {
        dragging = false
        if (closing) return
        if (target < 0.34f) {
            closing = true
            target = SheetMin
        } else {
            target = if (target > 0.72f) SheetMax else SheetRest
        }
    }

    fun dragBy(dyPx: Float) {
        if (closing) return
        dragging = true
        target = (target - dyPx / screenPx).coerceIn(SheetMin, SheetMax)
    }

    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && target < SheetMax && source == NestedScrollSource.UserInput) {
                    val room = (SheetMax - target) * screenPx
                    val take = maxOf(available.y, -room)
                    dragBy(take)
                    return Offset(0f, take)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragging) settle()
                return Velocity.Zero
            }
        }
    }

    val sheetDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                dragging = true
                closing = false
            },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
            onVerticalDrag = { change, amount ->
                change.consume()
                dragBy(amount)
            },
        )
    }

    LaunchedEffect(Unit) {
        choosing = false
        Xani.session.clearSwitchError()
        target = SheetMin
        animated.snapTo(SheetMin)
        dragging = false
        closing = false
        target = SheetRest
    }

    // A switch has come back from the server. Only a switch that took effect
    // closes the sheet, and it leaves the same way a swipe does; one the server
    // refused stays up, with the account the user is still on marked active and
    // the reason above the list.
    LaunchedEffect(requested, switching, activeId) {
        if (requested == 0L || switching != 0L) return@LaunchedEffect
        if (activeId != requested) {
            requested = 0L
            return@LaunchedEffect
        }
        closing = true
        dragging = false
        target = SheetMin
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * frac.coerceIn(0f, 1f)))
                .pressable(pressedScale = 1f, onClick = {
                    if (dragging || closing) return@pressable
                    closing = true
                    dragging = false
                    target = SheetMin
                }),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(frac.coerceAtLeast(SheetMin))
                .clip(RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24)))
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF7F7F8))))
                .border(1.dp, Color(0x14000000), RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24)))
                .navigationBarsPadding()
                .then(sheetDrag)
                .nestedScroll(nested)
                .padding(horizontal = rdp(16))
                .padding(bottom = rdp(16)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(28)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(rdp(40))
                        .height(rdp(4))
                        .clip(RoundedCornerShape(rdp(2)))
                        .background(Color(0xFFE0E0E0)),
                )
            }
            if (choosing) {
                ChoosePane(
                    copy = copy,
                    onBack = { choosing = false },
                    onCreate = {
                        onClose()
                        onCreateAccount()
                    },
                    onLogin = {
                        onClose()
                        onAddAccount()
                    },
                )
            } else {
                AccountsPane(
                    copy = copy,
                    accounts = accounts,
                    activeId = activeId,
                    switchError = switchError,
                    pendingId = requested,
                    onSwitch = { id ->
                        if (id == activeId) {
                            onClose()
                        } else if (requested == 0L) {
                            requested = id
                            Xani.session.switchTo(id)
                        }
                    },
                    onRemove = { id ->
                        Xani.session.removeAccount(id)
                        if (accounts.size <= 1) onClose()
                    },
                    onAdd = { choosing = true },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AccountsPane(
    copy: Copy,
    accounts: List<SavedAccount>,
    activeId: Long,
    switchError: String,
    pendingId: Long,
    onSwitch: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    XaniText(
        copy.t(Copy.ProfileSwitchAccounts),
        Type.LabelBold,
        color = ProfileTokens.Text,
        fontSizeOverride = 17f,
        modifier = Modifier.padding(bottom = rdp(10)),
    )
    if (switchError.isNotEmpty()) {
        XaniText(
            switchError,
            Type.Caption,
            color = ProfileTokens.Danger,
            fontSizeOverride = 12f,
            modifier = Modifier.padding(bottom = rdp(8)),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rdp(6)),
    ) {
        accounts.forEach { account ->
            AccountRow(
                account = account,
                active = account.userId == activeId,
                pending = account.userId == pendingId,
                enabled = pendingId == 0L,
                onClick = { onSwitch(account.userId) },
                onRemove = { onRemove(account.userId) },
            )
        }
        // Keep Add inside the list so it stays reachable when the sheet is
        // short or the account list scrolls; sticky footer alone can clip.
        SwitcherAction(
            label = copy.t(Copy.HelpAddAccount),
            filled = true,
            onClick = onAdd,
        )
        Spacer(Modifier.height(rdp(8)))
    }
}

@Composable
private fun ChoosePane(
    copy: Copy,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onLogin: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(rdp(36))
                .clip(RoundedCornerShape(rdp(10)))
                .pressable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            XaniText("‹", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 22f)
        }
        Spacer(Modifier.width(rdp(8)))
        XaniText(
            copy.t(Copy.HelpAddAccount),
            Type.LabelBold,
            color = ProfileTokens.Text,
            fontSizeOverride = 17f,
        )
    }
    SwitcherAction(
        label = copy.t(Copy.HelpCreateAccount),
        filled = false,
        onClick = onCreate,
    )
    Spacer(Modifier.height(rdp(8)))
    SwitcherAction(
        label = copy.t(Copy.HelpUseExisting),
        filled = true,
        onClick = onLogin,
    )
}

@Composable
private fun SwitcherAction(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(12)))
            .background(if (filled) ProfileTokens.AccentSoft else Color.White)
            .border(
                1.dp,
                if (filled) Color.Transparent else ProfileTokens.Hairline,
                RoundedCornerShape(rdp(12)),
            )
            .pressable(onClick = onClick)
            .padding(vertical = rdp(14)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(
            label,
            Type.LabelBold,
            color = ProfileTokens.Accent,
            fontSizeOverride = 14f,
        )
    }
}

@Composable
private fun AccountRow(
    account: SavedAccount,
    active: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(12)))
            .background(if (active) ProfileTokens.AccentSoft else Color.White)
            .border(
                1.dp,
                if (active) ProfileTokens.Accent.copy(alpha = 0.35f) else ProfileTokens.Hairline,
                RoundedCornerShape(rdp(12)),
            )
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = rdp(12), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val url = MediaConfig.avatarUrl(account.avatarUrl)
        Box(
            Modifier
                .size(rdp(44))
                .clip(CircleShape)
                .background(ProfileTokens.Bg3),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotEmpty()) {
                RemoteImage(
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                    maxEdge = 128,
                    fallback = Color.Transparent,
                )
            } else {
                val initial = account.fullName.ifBlank { account.username }.take(1).uppercase()
                XaniText(initial, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
            }
        }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(
                account.fullName.ifBlank { account.username },
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 14f,
            )
            XaniText(
                "@${account.username}",
                Type.Caption,
                color = ProfileTokens.Sub,
                fontSizeOverride = 12f,
            )
        }
        when {
            // While the server is being asked, the row says so: the switch is a
            // round trip and a silent row reads as an ignored tap.
            pending -> XaniText(
                "Switching…",
                Type.Caption,
                color = ProfileTokens.Accent,
                fontSizeOverride = 11f,
            )

            active -> XaniText("✓", Type.LabelBold, color = ProfileTokens.Accent, fontSizeOverride = 16f)

            else -> Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(8)))
                    .then(if (enabled) Modifier.pressable(onClick = onRemove) else Modifier)
                    .padding(horizontal = rdp(8), vertical = rdp(6)),
            ) {
                XaniText("Remove", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
            }
        }
    }
}
