package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.xani.core.net.AppGate
import app.xani.core.net.Xani
import app.xani.i18n.Copy
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

@Composable
internal fun ProfileDrawer(
    copy: Copy,
    snapshot: ProfileSnapshot,
    visible: Boolean,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onAction: (String) -> Unit,
    onCopied: () -> Unit = {},
) {
    // slide: 1 = parked off-screen right, 0 = open. Driven by [visible] so the
    // panel animates both in and out; the composable stays mounted until the
    // close animation has finished rather than vanishing on the first frame.
    val slide = remember { Animatable(if (visible) 0f else 1f) }
    // drag: how far the finger has pulled the open panel back to the right.
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        if (visible) {
            drag.snapTo(0f)
            slide.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        } else {
            slide.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            drag.snapTo(0f)
        }
    }
    if (!visible && slide.value >= 0.999f) return
    if (visible) BackHandler(onBack = onClose)

    var panelWidth by remember { mutableFloatStateOf(0f) }
    // How much of the panel is showing, 0..1, counting both the slide and the
    // finger drag: the scrim fades with it so the two never disagree.
    val shown = ((1f - slide.value) * (1f - (if (panelWidth > 0f) drag.value / panelWidth else 0f))).coerceIn(0f, 1f)
    val brand = LocalBrand.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ProfileTokens.Scrim.copy(alpha = 0.4f * shown))
                .pressable(onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
                .onSizeChanged { panelWidth = it.width.toFloat() }
                .graphicsLayer { translationX = size.width * slide.value + drag.value }
                .background(Color.White)
                // Pull right to close: the panel follows the finger; past a
                // third of its width (or a quick flick) it goes, otherwise it
                // springs back. Rows underneath still take taps because a tap
                // never crosses the drag slop.
                .pointerInput(visible) {
                    if (!visible) return@pointerInput
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, delta ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next = (drag.value + delta).coerceAtLeast(0f)
                            if (next != drag.value) {
                                change.consume()
                                scope.launch { drag.snapTo(next) }
                            }
                        },
                        onDragEnd = {
                            val fling = tracker.calculateVelocity().x
                            val w = panelWidth.takeIf { it > 0f } ?: size.width.toFloat()
                            if (drag.value > w * 0.33f || fling > 1_200f) {
                                onClose()
                            } else {
                                scope.launch { drag.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { drag.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
                        },
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(20), vertical = rdp(18)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(rdp(46))
                        .clip(CircleShape)
                        .background(ProfileTokens.AccentWash),
                    contentAlignment = Alignment.Center,
                ) { XaniMark(rdp(24)) }
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    XaniText(snapshot.displayName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                    XaniText("@${snapshot.username}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                    CopyableUserId(
                        copy = copy,
                        userId = ProfileStore.currentUserId,
                        onCopied = onCopied,
                        modifier = Modifier.padding(top = rdp(6)),
                    )
                }
                Box(
                    Modifier
                        .size(rdp(36))
                        .pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Close(Color(0xFF999999), rdp(20)) }
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = rdp(32)),
            ) {
                SectionLabel(copy.t(Copy.ProfileDrawerAccount))
                DrawerRow(copy.t(Copy.ProfileBalance), "${snapshot.balanceCoins} " + copy.t(Copy.ProfileCoins), onClick = { onAction("balance") }) {
                    ProfileIco.Path("M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z", Color(0xFF555555))
                }
                DrawerRow(copy.t(Copy.BalanceRecharge), onClick = { onAction("recharge") }) {
                    ProfileIco.Path("M12 4v16m8-8H4", Color(0xFF555555))
                }
                // Coin sellers: one cheap read decides both rows — the public
                // list for everyone while the feature is on, the hub only for
                // accounts holding a seller role.
                var sellerMe by remember { androidx.compose.runtime.mutableStateOf<app.xani.core.net.SellerMeData?>(null) }
                LaunchedEffect(Unit) { sellerMe = Xani.sellers.me().value }
                if (sellerMe?.sellersEnabled == true) {
                    DrawerRow(copy.t(Copy.SellerDrawerList), onClick = { onAction("coin_sellers") }) {
                        ProfileIco.Path("M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z", Color(0xFF555555))
                    }
                }
                if (sellerMe?.isSeller == true) {
                    DrawerRow(copy.t(Copy.SellerDrawerHub), copy.t(Copy.SellerTabSend), iconTint = ProfileTokens.Accent, iconBg = ProfileTokens.AccentSoft, onClick = { onAction("seller_hub") }) {
                        ProfileIco.Path("M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z", ProfileTokens.Accent)
                    }
                }
                DrawerRow(copy.t(Copy.ProfileDiamonds), snapshot.diamonds, onClick = { onAction("diamonds") }) {
                    ProfileIco.Diamond(Color(0xFF555555), rdp(18))
                }
                DrawerRow(copy.t(Copy.ProfileTxnHistory), onClick = { onAction("txn") }) {
                    ProfileIco.Receipt(Color(0xFF555555), rdp(18))
                }
                // Wallet / Coins, Gifting record, My roles, My Live and My Rewards
                // sit on the profile itself now (the shortcut boxes under the
                // store row), so they are not repeated here.
                // The in-app moderation screen, only for accounts whose role (or
                // direct grant) carries it. The server enforces regardless.
                if (Xani.session.can("mod.app_users") || Xani.session.can("screen.capture.manage")) {
                    DrawerRow(copy.t(Copy.ModTitle), onClick = { onAction("moderation") }) {
                        // Shield with a check: moderation.
                        ProfileIco.Path("M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z", Color(0xFF555555))
                    }
                }

                Box(Modifier.fillMaxWidth().padding(top = rdp(4)).height(rdp(1)).background(ProfileTokens.RowLine))
                SectionLabel(copy.t(Copy.ProfileDrawerSettings))
                DrawerRow(copy.t(Copy.ProfileSettings), onClick = onSettings) {
                    ProfileIco.Path(
                        "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065zM15 12a3 3 0 11-6 0 3 3 0 016 0z",
                        Color(0xFF555555),
                    )
                }

                Box(Modifier.fillMaxWidth().padding(top = rdp(4)).height(rdp(1)).background(ProfileTokens.RowLine))
                SectionLabel(copy.t(Copy.ProfileDrawerSupport))
                DrawerRow(copy.t(Copy.ProfileHelpCentre), onClick = { onAction("help") }) {
                    ProfileIco.Path("M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z", Color(0xFF555555))
                }
                DrawerRow(copy.t(Copy.ProfileReportProblem), onClick = { onAction("report") }) {
                    ProfileIco.Path("M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z", Color(0xFF555555))
                }

                Box(Modifier.fillMaxWidth().padding(top = rdp(4)).height(rdp(1)).background(ProfileTokens.RowLine))
                SectionLabel(copy.t(Copy.ProfileDrawerLegal))
                DrawerRow(copy.t(Copy.ProfileTerms), onClick = { onAction("terms") }) {
                    ProfileIco.Path("M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z", Color(0xFF555555))
                }

                Box(
                    Modifier
                        .padding(horizontal = rdp(20))
                        .padding(top = rdp(20))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(ProfileTokens.DangerSoft)
                        .pressable(onClick = { onAction("logout") })
                        .padding(vertical = rdp(14)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileIco.Logout(ProfileTokens.Danger, rdp(18))
                        Spacer(Modifier.width(rdp(10)))
                        XaniText(copy.t(Copy.ProfileLogOut), Type.LabelBold, color = ProfileTokens.Danger, fontSizeOverride = 15f)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
            XaniText(
                copy.t(Copy.ProfileVersion, "app" to brand.name, "version" to AppGate.version),
                Type.Micro,
                color = Color(0xFFCCCCCC),
                fontSizeOverride = 11f,
                modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(12)),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    XaniText(
        text.uppercase(),
        Type.Micro,
        color = Color(0xFFAAAAAA),
        fontSizeOverride = 11f,
        modifier = Modifier.padding(start = rdp(20), end = rdp(20), top = rdp(18), bottom = rdp(6)),
    )
}

@Composable
private fun DrawerRow(
    label: String,
    sub: String? = null,
    iconTint: Color = Color(0xFF555555),
    iconBg: Color = Color(0xFFF5F5F5),
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(20), vertical = rdp(13)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(rdp(38))
                .clip(RoundedCornerShape(rdp(12)))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(rdp(14)))
        Column(Modifier.weight(1f)) {
            XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
            if (!sub.isNullOrBlank()) {
                XaniText(sub, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
            }
        }
        ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
    }
}
