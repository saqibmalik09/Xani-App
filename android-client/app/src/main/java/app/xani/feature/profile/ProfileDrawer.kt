package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
) {
    if (!visible) return
    BackHandler(onBack = onClose)
    val slide by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "drawer",
    )
    val scrim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "scrim",
    )
    val brand = LocalBrand.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ProfileTokens.Scrim.copy(alpha = 0.4f * scrim))
                .pressable(onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
                .graphicsLayer { translationX = size.width * slide }
                .background(Color.White)
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
                DrawerRow(copy.t(Copy.ProfileBalance), copy.t(Copy.ProfileCoins).let { "${snapshot.balanceCoins} coins" }, onClick = { onAction("balance") }) {
                    ProfileIco.Path("M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z", Color(0xFF555555))
                }
                DrawerRow(copy.t(Copy.ProfileWallet), onClick = { onAction("wallet") }) {
                    ProfileIco.Path("M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z", Color(0xFF555555))
                }
                DrawerRow(copy.t(Copy.ProfileDiamonds), snapshot.diamonds, onClick = { onAction("diamonds") }) {
                    ProfileIco.Diamond(Color(0xFF555555), rdp(18))
                }
                DrawerRow(copy.t(Copy.StoreTitle), onClick = { onAction("frames") }) {
                    ProfileIco.Store(Color(0xFF555555), rdp(18))
                }
                DrawerRow(copy.t(Copy.GiftRecordTitle), onClick = { onAction("gifting") }) {
                    // A wrapped gift box: lid, ribbon, body.
                    ProfileIco.Path("M20 12v10H4V12M2 7h20v5H2zM12 22V7m0 0H7.5a2.5 2.5 0 010-5C11 2 12 7 12 7zm0 0h4.5a2.5 2.5 0 000-5C13 2 12 7 12 7z", Color(0xFF555555))
                }
                DrawerRow(copy.t(Copy.ProfileTxnHistory), onClick = { onAction("txn") }) {
                    ProfileIco.Path("M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2", Color(0xFF555555))
                }
                DrawerRow(copy.t(Copy.RolesTitle), onClick = { onAction("roles") }) {
                    // Org chart: one node over two.
                    ProfileIco.Path("M12 3v5m0 0H7a2 2 0 00-2 2v2m7-4h5a2 2 0 012 2v2M5 12v3m14-3v3M3 15h4v6H3zm14 0h4v6h-4zM10 15h4v6h-4zM12 12v3", Color(0xFF555555))
                }

                Box(Modifier.fillMaxWidth().padding(top = rdp(4)).height(rdp(1)).background(ProfileTokens.RowLine))
                SectionLabel(copy.t(Copy.ProfileDrawerCreator))
                DrawerRow(copy.t(Copy.ProfileCreatorTools), iconTint = ProfileTokens.Accent, iconBg = ProfileTokens.AccentSoft, onClick = { onAction("creator") }) {
                    ProfileIco.Path("M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7", ProfileTokens.Accent)
                }
                DrawerRow(copy.t(Copy.ProfileAnalytics), iconTint = ProfileTokens.Accent, iconBg = ProfileTokens.AccentSoft, onClick = { onAction("analytics") }) {
                    ProfileIco.Path("M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z", ProfileTokens.Accent)
                }
                DrawerRow(copy.t(Copy.ProfileLiveCenter), iconTint = ProfileTokens.Accent, iconBg = ProfileTokens.AccentSoft, onClick = { onAction("live") }) {
                    ProfileIco.Path("M15 10l4.553-2.069A1 1 0 0121 8.845v6.31a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z", ProfileTokens.Accent)
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
                copy.t(Copy.ProfileVersion, "app" to brand.wordmark.lowercase()),
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
