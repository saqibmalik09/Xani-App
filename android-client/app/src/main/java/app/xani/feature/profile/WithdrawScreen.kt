package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.PaymentMethodData
import app.xani.core.net.WithdrawHomeData
import app.xani.core.net.WithdrawRequestData
import app.xani.core.net.WithdrawStatus
import app.xani.core.net.WithdrawWindowData
import app.xani.core.net.Xani
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Diamond withdrawals. One read brings the balance, what is already on hold,
 * the policy bounds, whether today is inside the month's window, and the
 * payment methods offered in the user's country. Picking a method opens the
 * form; submitting holds the diamonds at once and the request joins the
 * "My requests" tab with its status.
 */
@Composable
internal fun WithdrawScreen(copy: Copy, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var home by remember { mutableStateOf<WithdrawHomeData?>(null) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf("") }
    var homeKey by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<PaymentMethodData?>(null) }
    var banner by remember { mutableStateOf("") }
    var requestsKey by remember { mutableStateOf(0) }

    LaunchedEffect(homeKey) {
        homeLoading = true
        val r = Xani.wallet.withdrawHome()
        val got = r.value
        if (got != null) {
            home = got
            homeError = ""
        } else {
            homeError = r.message
        }
        homeLoading = false
    }

    fun pop() {
        if (picked != null) picked = null else onBack()
    }
    BackHandler(onBack = ::pop)

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(32)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = ::pop),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                XaniText(
                    picked?.name ?: copy.t(Copy.WithdrawTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }

            val method = picked
            if (method != null) {
                val h = home
                WithdrawForm(
                    copy = copy, method = method, available = h?.diamonds ?: 0L, window = h?.window ?: WithdrawWindowData.Open,
                    onDone = { message ->
                        banner = message
                        picked = null
                        tab = 1
                        homeKey++
                        requestsKey++
                    },
                )
                return@Column
            }

            WithdrawHeader(copy, home, homeLoading, homeError) { homeKey++ }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(10)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                WdTab(copy.t(Copy.WithdrawTabMethods), tab == 0, Modifier.weight(1f)) { tab = 0 }
                WdTab(copy.t(Copy.WithdrawTabRequests), tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
            if (banner.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(8))
                        .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Ok.copy(alpha = 0.10f))
                        .pressable(onClick = { banner = "" }).padding(rdp(12)),
                ) {
                    XaniText(banner, Type.Caption, color = ProfileTokens.Ok, fontSizeOverride = 12f)
                }
            }

            when (tab) {
                0 -> MethodsTab(copy, home, homeLoading) { picked = it }
                else -> RequestsTab(copy, requestsKey)
            }
        }
    }
}

// ─── header ──────────────────────────────────────────────────────────────────

@Composable
private fun WithdrawHeader(copy: Copy, home: WithdrawHomeData?, loading: Boolean, error: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = rdp(16))
            .clip(RoundedCornerShape(rdp(18))).background(ProfileTokens.Accent)
            .padding(rdp(16)),
    ) {
        XaniText(copy.t(Copy.WithdrawAvailable), Type.Caption, color = Color.White.copy(alpha = 0.8f), fontSizeOverride = 11f)
        Spacer(Modifier.height(rdp(4)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            XaniText("💎 ${compact(home?.diamonds ?: 0L)}", Type.H2, color = Color.White, fontSizeOverride = 26f, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (loading) Spinner(rdp(18), Color.White)
        }
        if (home != null) {
            if (home.pendingCount > 0L) {
                XaniText(
                    copy.t(Copy.WithdrawOnHold, "n" to compact(home.pendingDiamonds), "c" to home.pendingCount.toString()),
                    Type.Caption, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 12f,
                )
            }
            Spacer(Modifier.height(rdp(10)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                Pill(copy.t(Copy.WithdrawLimits, "min" to compact(home.minDiamonds), "max" to compact(home.maxDiamonds)))
                if (home.country.isNotEmpty()) Pill(copy.t(Copy.WithdrawCountry, "c" to home.country))
            }
            Spacer(Modifier.height(rdp(8)))
            WindowLine(copy, home.window, onDark = true)
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(rdp(8)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(error, Type.Caption, color = Color.White, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(rdp(10))).background(Color.White.copy(alpha = 0.2f))
                        .pressable(onClick = onRetry).padding(horizontal = rdp(12), vertical = rdp(6)),
                ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = Color.White, fontSizeOverride = 11f) }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(rdp(10))).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = rdp(10), vertical = rdp(5))) {
        XaniText(text, Type.Micro, color = Color.White, fontSizeOverride = 10f, maxLines = 1)
    }
}

/** "Open from the 10th to the 14th" or the closed message with the next date. */
@Composable
private fun WindowLine(copy: Copy, w: WithdrawWindowData, onDark: Boolean) {
    val text = windowText(copy, w)
    val tint = when {
        onDark -> Color.White
        w.open -> ProfileTokens.Ok
        else -> ProfileTokens.Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(rdp(8)).clip(CircleShape).background(if (w.open) ProfileTokens.Ok else Color(0xFFF59E0B)))
        Spacer(Modifier.width(rdp(8)))
        XaniText(text, Type.Caption, color = tint, fontSizeOverride = 12f)
    }
}

private fun windowText(copy: Copy, w: WithdrawWindowData): String {
    val from = ordinal(w.dayFrom)
    val to = ordinal(w.dayTo)
    return when {
        w.open && w.dayFrom == w.dayTo -> copy.t(Copy.WithdrawOpenDay, "from" to from)
        w.open -> copy.t(Copy.WithdrawOpenRange, "from" to from, "to" to to)
        w.message.isNotEmpty() -> w.message
        w.dayFrom == w.dayTo -> copy.t(Copy.WithdrawClosedDay, "from" to from, "next" to w.nextOpen)
        else -> copy.t(Copy.WithdrawClosedRange, "from" to from, "to" to to, "next" to w.nextOpen)
    }
}

private fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

@Composable
private fun WdTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(rdp(12)))
            .background(if (active) ProfileTokens.Text else ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(vertical = rdp(10)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (active) Color.White else ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1)
    }
}

// ─── methods ─────────────────────────────────────────────────────────────────

@Composable
private fun MethodsTab(copy: Copy, home: WithdrawHomeData?, loading: Boolean, onPick: (PaymentMethodData) -> Unit) {
    val methods = home?.methods.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "head") {
            XaniText(
                copy.t(Copy.WithdrawPickMethod), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                modifier = Modifier.padding(horizontal = rdp(20)).padding(bottom = rdp(8)),
            )
        }
        if (methods.isEmpty()) {
            item(key = "empty") {
                if (!loading) {
                    XaniText(
                        copy.t(Copy.WithdrawNoMethods), Type.Caption, color = ProfileTokens.Muted, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(24), horizontal = rdp(24)),
                    )
                }
            }
        } else {
            items(methods, key = { it.id }) { m -> MethodCard(copy, m) { onPick(m) } }
        }
    }
}

@Composable
private fun MethodCard(copy: Copy, m: PaymentMethodData, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(16), vertical = rdp(5))
            .clip(RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(rdp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(rdp(44)).clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            XaniText(m.name.take(1).uppercase(), Type.H2, color = ProfileTokens.Accent, fontSizeOverride = 18f)
        }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(m.name, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.title.isNotEmpty()) {
                XaniText(m.title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            XaniText(
                copy.t(Copy.WithdrawLimits, "min" to compact(m.minDiamonds), "max" to compact(m.maxDiamonds)),
                Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 10f, maxLines = 1,
            )
        }
        Spacer(Modifier.width(rdp(8)))
        ProfileIco.Chevron(ProfileTokens.Muted, rdp(16))
    }
}

// ─── form ────────────────────────────────────────────────────────────────────

@Composable
private fun WithdrawForm(
    copy: Copy,
    method: PaymentMethodData,
    available: Long,
    window: WithdrawWindowData,
    onDone: (String) -> Unit,
) {
    var accountTitle by rememberSaveable(method.id) { mutableStateOf("") }
    var accountNo by rememberSaveable(method.id) { mutableStateOf("") }
    var amountText by rememberSaveable(method.id) { mutableStateOf("") }
    // One id per form instance: a retry after a dropped reply lands on the
    // same request instead of holding the diamonds twice.
    var clientId by remember(method.id) { mutableStateOf(UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val amount = amountText.toLongOrNull() ?: 0L
    val inlineHint = when {
        amountText.isEmpty() -> ""
        amount < method.minDiamonds -> copy.t(Copy.WithdrawBelowMin, "n" to compact(method.minDiamonds))
        method.maxDiamonds > 0L && amount > method.maxDiamonds -> copy.t(Copy.WithdrawAboveMax, "n" to compact(method.maxDiamonds))
        amount > available -> copy.t(Copy.WithdrawInsufficient, "n" to compact(available))
        else -> ""
    }
    val canSubmit = window.open && !busy && inlineHint.isEmpty() && amount > 0L &&
        accountTitle.isNotBlank() && accountNo.isNotBlank()

    fun submit() {
        if (busy) return
        if (accountTitle.isBlank() || accountNo.isBlank() || amount <= 0L) {
            error = copy.t(Copy.WithdrawFill)
            return
        }
        if (inlineHint.isNotEmpty()) {
            error = inlineHint
            return
        }
        busy = true
        error = ""
        scope.launch {
            val r = Xani.wallet.withdraw(method.id, accountTitle.trim(), accountNo.trim(), amount, clientId)
            val got = r.value
            busy = false
            if (got != null) {
                clientId = UUID.randomUUID().toString()
                onDone(copy.t(Copy.WithdrawDone, "n" to compact(got.request.diamonds)))
            } else {
                error = r.message
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = rdp(16)).padding(bottom = rdp(32)),
    ) {
        // The method's own words, top of the page.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(rdp(16))) {
            XaniText(method.name, Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f)
            if (method.title.isNotEmpty()) {
                Spacer(Modifier.height(rdp(4)))
                XaniText(method.title, Type.LabelBold, color = ProfileTokens.Sub, fontSizeOverride = 13f)
            }
            if (method.description.isNotEmpty()) {
                Spacer(Modifier.height(rdp(8)))
                XaniText(method.description, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 13f)
            }
            if (method.message.isNotEmpty()) {
                Spacer(Modifier.height(rdp(10)))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Accent.copy(alpha = 0.10f)).padding(rdp(10))) {
                    XaniText(method.message, Type.Caption, color = ProfileTokens.Accent, fontSizeOverride = 12f)
                }
            }
            Spacer(Modifier.height(rdp(10)))
            Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                DarkPill(copy.t(Copy.WithdrawLimits, "min" to compact(method.minDiamonds), "max" to compact(method.maxDiamonds)))
                DarkPill("💎 ${compact(available)}")
            }
        }

        Spacer(Modifier.height(rdp(14)))
        WindowLine(copy, window, onDark = false)
        Spacer(Modifier.height(rdp(14)))

        FieldLabel(copy.t(Copy.WithdrawAccountTitle))
        LightField(
            value = accountTitle, onValueChange = { accountTitle = it.take(120) }, placeholder = copy.t(Copy.WithdrawAccountTitle),
            modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Words, maxChars = 120,
        )
        Spacer(Modifier.height(rdp(12)))
        FieldLabel(method.accountLabel)
        LightField(
            value = accountNo, onValueChange = { accountNo = it.take(120) }, placeholder = method.accountLabel,
            modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Next, maxChars = 120,
        )
        Spacer(Modifier.height(rdp(12)))
        FieldLabel(copy.t(Copy.WithdrawAmount))
        LightField(
            value = amountText, onValueChange = { amountText = it.filter(Char::isDigit).take(12) }, placeholder = "💎 ${compact(method.minDiamonds)}",
            modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, maxChars = 12,
        )
        if (inlineHint.isNotEmpty()) {
            XaniText(inlineHint, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(6), start = rdp(4)))
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(rdp(10)))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12))) {
                XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f)
            }
        }

        Spacer(Modifier.height(rdp(20)))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(rdp(14)))
                .background(if (canSubmit) ProfileTokens.Accent else ProfileTokens.Bg3)
                .pressable(enabled = canSubmit, onClick = ::submit)
                .padding(vertical = rdp(15)),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                Spinner(rdp(18), Color.White)
            } else {
                XaniText(
                    copy.t(Copy.WithdrawSubmit), Type.LabelBold,
                    color = if (canSubmit) Color.White else ProfileTokens.Muted, fontSizeOverride = 15f,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    XaniText(text, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
}

@Composable
private fun DarkPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg3).padding(horizontal = rdp(10), vertical = rdp(5))) {
        XaniText(text, Type.Micro, color = ProfileTokens.Text, fontSizeOverride = 10f, maxLines = 1)
    }
}

// ─── requests ────────────────────────────────────────────────────────────────

@Composable
private fun RequestsTab(copy: Copy, reloadKey: Int) {
    val rows = remember { mutableStateListOf<WithdrawRequestData>() }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retryKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(beforeId: Long) {
        loading = true
        val r = Xani.wallet.withdrawals(beforeId)
        val got = r.value
        if (got != null) {
            if (beforeId == 0L) rows.clear()
            rows.addAll(got.items)
            cursor = got.nextCursor
            error = ""
        } else {
            error = r.message
        }
        loading = false
    }
    LaunchedEffect(reloadKey, retryKey) { load(0L) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        if (loading && rows.isEmpty()) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = rdp(20)), contentAlignment = Alignment.Center) { Spinner(rdp(18), ProfileTokens.Accent) }
            }
        }
        if (error.isNotEmpty()) {
            item(key = "error") {
                Row(Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(8)), verticalAlignment = Alignment.CenterVertically) {
                    XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = { retryKey++ })
                            .padding(horizontal = rdp(12), vertical = rdp(6)),
                    ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f) }
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                if (!loading && error.isEmpty()) {
                    XaniText(
                        copy.t(Copy.WithdrawNoRequests), Type.Caption, color = ProfileTokens.Muted, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(24)),
                    )
                }
            }
        } else {
            items(rows, key = { it.id }) { r -> RequestRow(copy, r) }
        }
        if (cursor.isNotEmpty() || (loading && rows.isNotEmpty())) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(vertical = rdp(14)), contentAlignment = Alignment.Center) {
                    if (loading) {
                        Spinner(rdp(18), ProfileTokens.Accent)
                    } else {
                        Box(
                            Modifier.clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2)
                                .pressable(onClick = { scope.launch { load(cursor.toLongOrNull() ?: 0L) } })
                                .padding(horizontal = rdp(18), vertical = rdp(10)),
                        ) { XaniText(copy.t(Copy.TxnLoadMore), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12f) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(copy: Copy, r: WithdrawRequestData) {
    val (label, tint) = when (r.status) {
        WithdrawStatus.Approved -> copy.t(Copy.WithdrawStatusApproved) to ProfileTokens.Ok
        WithdrawStatus.Rejected -> copy.t(Copy.WithdrawStatusRejected) to ProfileTokens.Danger
        WithdrawStatus.Pending -> copy.t(Copy.WithdrawStatusPending) to Color(0xFFF59E0B)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(16), vertical = rdp(5))
            .clip(RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.Bg2)
            .padding(rdp(14)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                XaniText(r.methodName, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                XaniText("${r.accountTitle} · ${r.accountNo}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(rdp(10)))
            XaniText("💎 ${compact(r.diamonds)}", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
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
            XaniText(r.note, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
        }
    }
}
