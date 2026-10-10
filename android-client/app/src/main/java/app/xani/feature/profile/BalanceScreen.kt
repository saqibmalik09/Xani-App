package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.LiveFeed
import app.xani.core.net.RechargeHomeData
import app.xani.core.net.RechargeRequestData
import app.xani.core.net.WithdrawStatus
import app.xani.core.net.Xani
import app.xani.i18n.Copy
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import java.text.NumberFormat

/**
 * The balance hub: coins, diamonds and beans as exact numbers, the three
 * things to do with them (recharge, withdraw, history), and the latest
 * recharge requests with their status. Numbers follow the wallet stream, so
 * an approval lands on this screen the moment the reviewer taps it.
 */
@Composable
internal fun BalanceScreen(
    copy: Copy,
    onBack: () -> Unit,
    onRecharge: () -> Unit,
    onWithdraw: () -> Unit,
    onHistory: () -> Unit,
) {
    var home by remember { mutableStateOf<RechargeHomeData?>(null) }
    var recent by remember { mutableStateOf<List<RechargeRequestData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(0) }
    var coins by remember { mutableStateOf(-1L) }
    var diamonds by remember { mutableStateOf(-1L) }
    var beans by remember { mutableStateOf(-1L) }

    LaunchedEffect(key) {
        loading = true
        val r = Xani.wallet.rechargeHome()
        val got = r.value
        if (got != null) {
            home = got
            coins = got.coins
            diamonds = got.diamonds
            beans = got.beans
            error = ""
        } else {
            error = r.message
        }
        loading = false
        val list = Xani.wallet.recharges(limit = 5).value
        if (list != null) recent = list.items
    }
    // Live: a credited recharge or a gift changes the numbers in place.
    LaunchedEffect(Unit) {
        LiveFeed.balance.collect { b ->
            coins = b.coins
            diamonds = b.diamonds
            beans = b.beans
        }
    }
    BackHandler(onBack = onBack)

    val fmt = remember { NumberFormat.getIntegerInstance() }
    fun num(v: Long) = if (v < 0L) "—" else fmt.format(v)

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(32)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                XaniText(copy.t(Copy.BalanceTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, modifier = Modifier.weight(1f))
                if (loading) Spinner(rdp(18), ProfileTokens.Accent)
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = rdp(16)).padding(bottom = rdp(32)),
            ) {
                // Coins: the headline, on the brand gradient.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(20)))
                        .background(Brush.linearGradient(listOf(ProfileTokens.AccentDeep, ProfileTokens.Accent)))
                        .padding(rdp(18)),
                ) {
                    XaniText(copy.t(Copy.BalanceCoins), Type.Caption, color = Color.White.copy(alpha = 0.8f), fontSizeOverride = 12f)
                    Spacer(Modifier.height(rdp(6)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileIco.Coin(Color.White, rdp(28))
                        Spacer(Modifier.width(rdp(10)))
                        XaniText(num(coins), Type.H1, color = Color.White, fontSizeOverride = 34f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val pending = home?.pendingCount ?: 0
                    if (pending > 0) {
                        Spacer(Modifier.height(rdp(8)))
                        Box(Modifier.clip(RoundedCornerShape(rdp(10))).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = rdp(10), vertical = rdp(5))) {
                            XaniText(copy.t(Copy.BalancePending, "n" to pending.toString()), Type.Micro, color = Color.White, fontSizeOverride = 11f)
                        }
                    }
                    Spacer(Modifier.height(rdp(14)))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14))).background(Color.White)
                            .pressable(onClick = onRecharge).padding(vertical = rdp(13)),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(copy.t(Copy.BalanceRecharge), Type.LabelBold, color = ProfileTokens.AccentDeep, fontSizeOverride = 15f)
                    }
                }

                Spacer(Modifier.height(rdp(12)))
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                    MiniBalance(copy.t(Copy.BalanceDiamonds), num(diamonds), Modifier.weight(1f)) { ProfileIco.Diamond(ProfileTokens.AccentDeep, rdp(18)) }
                    MiniBalance(copy.t(Copy.BalanceBeans), num(beans), Modifier.weight(1f)) {
                        XaniText("🫘", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
                    }
                }

                Spacer(Modifier.height(rdp(12)))
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                    ActionTile(copy.t(Copy.BalanceWithdraw), Modifier.weight(1f), onWithdraw) {
                        ProfileIco.Path("M12 4v12m0 0l-4-4m4 4l4-4M4 20h16", ProfileTokens.Text)
                    }
                    ActionTile(copy.t(Copy.BalanceHistory), Modifier.weight(1f), onHistory) {
                        ProfileIco.History(ProfileTokens.Text, rdp(19))
                    }
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(12)))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = { key++ })
                                .padding(horizontal = rdp(12), vertical = rdp(6)),
                        ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f) }
                    }
                }

                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(20)))
                    XaniText(copy.t(Copy.BalanceRecent), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(8)))
                    recent.forEach { r -> RechargeRequestRow(copy, r) }
                }
            }
        }
    }
}

@Composable
private fun MiniBalance(label: String, value: String, modifier: Modifier, icon: @Composable () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(rdp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(36)).clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.AccentWash), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(rdp(10)))
        Column {
            XaniText(label, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
            XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionTile(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).pressable(onClick = onClick).padding(horizontal = rdp(14), vertical = rdp(13)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.width(rdp(8)))
        XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1)
    }
}

/** One recharge request card; shared by the balance hub and the recharge list. */
@Composable
internal fun RechargeRequestRow(copy: Copy, r: RechargeRequestData) {
    val (label, tint) = when (r.status) {
        WithdrawStatus.Approved -> copy.t(Copy.WithdrawStatusApproved) to ProfileTokens.Ok
        WithdrawStatus.Rejected -> copy.t(Copy.WithdrawStatusRejected) to ProfileTokens.Danger
        WithdrawStatus.Pending -> copy.t(Copy.WithdrawStatusPending) to Color(0xFFF59E0B)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = rdp(5))
            .clip(RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.Bg2)
            .padding(rdp(14)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                XaniText(
                    r.packageTitle.ifEmpty { "${r.currency} ${r.price}" }, Type.LabelBold, color = ProfileTokens.Text,
                    fontSizeOverride = 15f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                XaniText("${r.currency} ${r.price} · ${r.methodName}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(rdp(10)))
            val shown = if (r.status == WithdrawStatus.Approved) r.creditedCoins else r.coins
            XaniText("🪙 ${compact(shown)}", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
        }
        Spacer(Modifier.height(rdp(10)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(rdp(8))).background(tint.copy(alpha = 0.12f)).padding(horizontal = rdp(8), vertical = rdp(4))) {
                XaniText(label, Type.Micro, color = tint, fontSizeOverride = 10f)
            }
            Spacer(Modifier.width(rdp(10)))
            XaniText(txnStamp(r.createdAtMs), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f, modifier = Modifier.weight(1f))
            if (r.decidedAtMs > 0L) {
                XaniText(txnStamp(r.decidedAtMs), Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 10f)
            }
        }
        if (r.note.isNotEmpty()) {
            Spacer(Modifier.height(rdp(6)))
            XaniText(r.note, Type.Caption, color = if (r.status == WithdrawStatus.Rejected) ProfileTokens.Danger else ProfileTokens.Sub, fontSizeOverride = 12f)
        }
    }
}
