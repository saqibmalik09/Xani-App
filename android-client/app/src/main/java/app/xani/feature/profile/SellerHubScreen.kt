package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.SellerDisputeData
import app.xani.core.net.SellerDisputeDetailData
import app.xani.core.net.SellerLookupData
import app.xani.core.net.SellerMeData
import app.xani.core.net.SellerRequestData
import app.xani.core.net.SellerTeamData
import app.xani.core.net.SellerTransferData
import app.xani.core.net.Xani
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * The coin seller's own hub: balance + totals, the send form (lookup → preview
 * → amount → send, idempotent per attempt), requests from users (approve
 * sends the money), the read-only history and team, and disputes against the
 * seller with their chat threads.
 */
@Composable
internal fun SellerHubScreen(copy: Copy, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var me by remember { mutableStateOf<SellerMeData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var meKey by remember { mutableStateOf(0) }
    var disputeId by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(meKey) {
        val r = Xani.sellers.me()
        r.value?.let { me = it; error = "" } ?: run { error = r.message }
        loading = false
    }

    if (disputeId > 0L) {
        SellerDisputeThread(copy, disputeId, onBack = { disputeId = 0L })
        return
    }
    BackHandler(onBack = onBack)

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
                XaniText(
                    copy.t(Copy.SellerHubTitle), Type.H2, color = ProfileTokens.Text,
                    fontSizeOverride = 18f, modifier = Modifier.weight(1f),
                )
                if (loading) Spinner(rdp(18), ProfileTokens.Accent)
            }

            val m = me
            if (error.isNotEmpty() && m == null) {
                SellerBanner(error, danger = true)
            }
            if (m != null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = rdp(16)).padding(bottom = rdp(10)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                ) {
                    listOf(
                        copy.t(Copy.SellerTabOverview), copy.t(Copy.SellerTabSend), copy.t(Copy.SellerTabRequests),
                        copy.t(Copy.SellerTabHistory), copy.t(Copy.SellerTabTeam),
                    ).forEachIndexed { i, label ->
                        SellerTab(label, tab == i) { tab = i }
                    }
                }
                when (tab) {
                    0 -> SellerOverviewTab(copy, m, onDispute = { disputeId = it })
                    1 -> SellerSendTab(copy, m, onSent = { meKey++ })
                    2 -> SellerRequestsTab(copy, m, onDecided = { meKey++ })
                    3 -> SellerHistoryTab(copy)
                    else -> SellerTeamTab(copy)
                }
            }
        }
    }
}

@Composable
private fun SellerTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(rdp(12))).background(if (active) ProfileTokens.Text else ProfileTokens.Bg2)
            .pressable(onClick = onClick).padding(horizontal = rdp(14), vertical = rdp(9)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = if (active) Color.White else ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1)
    }
}

@Composable
internal fun SellerBanner(text: String, danger: Boolean = false, onClick: (() -> Unit)? = null) {
    var mod = Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(8))
        .clip(RoundedCornerShape(rdp(12)))
        .background(if (danger) ProfileTokens.DangerSoft else ProfileTokens.Ok.copy(alpha = 0.10f))
    if (onClick != null) mod = mod.pressable(onClick = onClick)
    Box(mod.padding(rdp(12))) {
        XaniText(text, Type.Caption, color = if (danger) ProfileTokens.Danger else ProfileTokens.Ok, fontSizeOverride = 12f)
    }
}

internal fun sellerWhen(ms: Long): String =
    if (ms <= 0L) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

/** Status → label + colour, shared by transfers, requests and disputes. */
@Composable
internal fun SellerStatusChip(copy: Copy, status: String) {
    val (key, color) = when (status) {
        "completed" -> Copy.SellerStatusCompleted to ProfileTokens.Ok
        "approved" -> Copy.SellerStatusApproved to ProfileTokens.Ok
        "resolved" -> Copy.SellerStatusResolved to ProfileTokens.Ok
        "failed" -> Copy.SellerStatusFailed to ProfileTokens.Danger
        "rejected" -> Copy.SellerStatusRejected to ProfileTokens.Danger
        "dismissed" -> Copy.SellerStatusDismissed to ProfileTokens.Sub
        "disputed" -> Copy.SellerStatusDisputed to ProfileTokens.Danger
        "open" -> Copy.SellerStatusOpen to ProfileTokens.Accent
        else -> Copy.SellerStatusPending to ProfileTokens.Sub
    }
    Box(
        Modifier.clip(RoundedCornerShape(rdp(8))).background(color.copy(alpha = 0.12f))
            .padding(horizontal = rdp(8), vertical = rdp(3)),
    ) { XaniText(copy.t(key), Type.Micro, color = color, fontSizeOverride = 10f) }
}

// ─── overview ────────────────────────────────────────────────────────────────

@Composable
private fun SellerOverviewTab(copy: Copy, m: SellerMeData, onDispute: (Long) -> Unit) {
    var disputes by remember { mutableStateOf<List<SellerDisputeData>>(emptyList()) }
    LaunchedEffect(Unit) {
        Xani.sellers.myDisputes().value?.let { disputes = it.items }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = rdp(32))) {
        if (!m.active) SellerBanner(copy.t(Copy.SellerInactive), danger = true)
        else if (!m.canSend) SellerBanner(copy.t(Copy.SellerNoSendPerm), danger = true)

        Column(
            Modifier.padding(horizontal = rdp(16)).fillMaxWidth()
                .clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(rdp(16)),
        ) {
            XaniText(copy.t(Copy.SellerBalance), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f)
            Spacer(Modifier.height(rdp(4)))
            XaniText("${m.sendable} ${m.debitCurrency}", Type.H2, color = ProfileTokens.Text, fontSizeOverride = 26f)
            Spacer(Modifier.height(rdp(4)))
            XaniText(
                "🪙 ${m.coins} · 💎 ${m.diamonds} · 🫘 ${m.beans}",
                Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
            )
        }
        Spacer(Modifier.height(rdp(12)))
        Column(
            Modifier.padding(horizontal = rdp(16)).fillMaxWidth()
                .clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(rdp(14)),
        ) {
            StatRow(copy.t(Copy.SellerSentToday), m.totals.sentToday.toString())
            StatRow(copy.t(Copy.SellerSentWeek), m.totals.sentWeek.toString())
            StatRow(copy.t(Copy.SellerSentMonth), m.totals.sentMonth.toString())
            StatRow(copy.t(Copy.SellerSentTotal), "${m.totals.sentTotal} (${copy.t(Copy.SellerSends, "n" to m.totals.sendCount.toString())})")
            StatRow(copy.t(Copy.SellerToppedUp), m.totals.toppedUp.toString())
        }
        if (m.policy.isNotEmpty()) {
            Spacer(Modifier.height(rdp(12)))
            Box(
                Modifier.padding(horizontal = rdp(16)).fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Accent.copy(alpha = 0.08f)).padding(rdp(12)),
            ) { XaniText(m.policy, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f) }
        }
        if (disputes.isNotEmpty()) {
            Spacer(Modifier.height(rdp(16)))
            XaniText(
                copy.t(Copy.SellerTabDisputes), Type.LabelBold, color = ProfileTokens.Text,
                fontSizeOverride = 14f, modifier = Modifier.padding(horizontal = rdp(20)),
            )
            Spacer(Modifier.height(rdp(8)))
            disputes.forEach { d ->
                Row(
                    Modifier.padding(horizontal = rdp(16), vertical = rdp(4)).fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2)
                        .pressable(onClick = { onDispute(d.id) }).padding(rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        XaniText("@${d.username} · ${sellerWhen(d.createdAtMs)}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                        XaniText(d.reason, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(rdp(8)))
                    SellerStatusChip(copy, d.status)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = rdp(4)), verticalAlignment = Alignment.CenterVertically) {
        XaniText(label, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
        XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
    }
}

// ─── send ────────────────────────────────────────────────────────────────────

@Composable
private fun SellerSendTab(copy: Copy, m: SellerMeData, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<SellerLookupData?>(null) }
    var searching by remember { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var okMsg by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    fun find() {
        if (query.isBlank() || searching) return
        searching = true
        error = ""
        scope.launch {
            val r = Xani.sellers.lookup(query.trim())
            searching = false
            r.value?.let { target = it } ?: run {
                target = null
                error = r.message
            }
        }
    }

    fun send() {
        val t = target ?: return
        val n = amount.toLongOrNull() ?: 0L
        if (n <= 0L || busy) return
        busy = true
        error = ""
        okMsg = ""
        scope.launch {
            val r = Xani.sellers.send(t.userId.toString(), n, note.trim(), clientId)
            busy = false
            val got = r.value
            if (got != null) {
                okMsg = copy.t(Copy.SellerSent, "n" to "${got.coins + got.diamonds + got.beans}")
                amount = ""
                note = ""
                target = null
                query = ""
                clientId = UUID.randomUUID().toString()
                onSent()
            } else {
                error = r.message
            }
        }
    }

    val amountN = amount.toLongOrNull() ?: 0L
    val fee = amountN * m.feeBps / 10_000L
    val canSend = m.active && m.canSend && target != null && amountN > 0L && !busy

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = rdp(16)).padding(bottom = rdp(32))) {
        if (!m.canSend) SellerBanner(copy.t(if (m.active) Copy.SellerNoSendPerm else Copy.SellerInactive), danger = true)
        if (okMsg.isNotEmpty()) SellerBanner(okMsg, onClick = { okMsg = "" })

        XaniText(copy.t(Copy.SellerFindUser), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LightField(
                value = query, onValueChange = { query = it.take(64) }, placeholder = copy.t(Copy.SellerFindHint),
                modifier = Modifier.weight(1f), imeAction = ImeAction.Search, maxChars = 64,
            )
            Spacer(Modifier.width(rdp(8)))
            Box(
                Modifier.clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2)
                    .pressable(enabled = !searching, onClick = ::find).padding(horizontal = rdp(14), vertical = rdp(12)),
            ) {
                if (searching) Spinner(rdp(16), ProfileTokens.Accent)
                else ProfileIco.Path("M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z", ProfileTokens.Text)
            }
        }

        val t = target
        if (t != null) {
            Spacer(Modifier.height(rdp(12)))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(t.userId, t.fullName.ifEmpty { t.username }, t.avatarUrl, rdp(44))
                Spacer(Modifier.width(rdp(10)))
                Column(Modifier.weight(1f)) {
                    XaniText(t.fullName.ifEmpty { "@${t.username}" }, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    XaniText("@${t.username} · #${t.userId}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                }
            }
            Spacer(Modifier.height(rdp(14)))
            XaniText(copy.t(Copy.SellerAmount), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
            LightField(
                value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
                placeholder = copy.t(Copy.SellerAmountHint), modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number, maxChars = 9,
            )
            XaniText(
                copy.t(Copy.SellerLimits, "min" to m.minSend.toString(), "max" to if (m.maxSend > 0) m.maxSend.toString() else "∞"),
                Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f,
                modifier = Modifier.padding(start = rdp(4), top = rdp(4)),
            )
            if (amountN > 0L) {
                XaniText(
                    copy.t(Copy.SellerTheyGet, "n" to (amountN - fee).toString(), "cur" to m.creditCurrency),
                    Type.Caption, color = ProfileTokens.Accent, fontSizeOverride = 12f,
                    modifier = Modifier.padding(start = rdp(4), top = rdp(4)),
                )
            }
            Spacer(Modifier.height(rdp(12)))
            XaniText(copy.t(Copy.SellerNote), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(6)))
            LightField(
                value = note, onValueChange = { note = it.take(300) }, placeholder = copy.t(Copy.SellerNote),
                modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Sentences, maxChars = 300,
            )
        }

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(rdp(10)))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12))) {
                XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f)
            }
        }

        Spacer(Modifier.height(rdp(20)))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14)))
                .background(if (canSend) ProfileTokens.Accent else ProfileTokens.Bg3)
                .pressable(enabled = canSend, onClick = ::send).padding(vertical = rdp(15)),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) Spinner(rdp(18), Color.White)
            else XaniText(
                copy.t(Copy.SellerSendBtn, "n" to if (amountN > 0) "$amountN ${m.debitCurrency}" else ""),
                Type.LabelBold, color = if (canSend) Color.White else ProfileTokens.Muted, fontSizeOverride = 15f,
            )
        }
    }
}

// ─── requests ────────────────────────────────────────────────────────────────

@Composable
private fun SellerRequestsTab(copy: Copy, m: SellerMeData, onDecided: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var items by remember { mutableStateOf<List<SellerRequestData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(0) }
    var busyId by remember { mutableStateOf(0L) }

    LaunchedEffect(key) {
        loading = true
        val r = Xani.sellers.requests()
        r.value?.let { items = it.items; error = "" } ?: run { error = r.message }
        loading = false
    }

    fun decide(req: SellerRequestData, approve: Boolean) {
        if (busyId != 0L) return
        busyId = req.id
        scope.launch {
            val r = Xani.sellers.decideRequest(req.id, approve)
            busyId = 0L
            if (r.ok) {
                key++
                onDecided()
            } else {
                error = r.message
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(32))) {
        if (error.isNotEmpty()) item { SellerBanner(error, danger = true, onClick = { key++ }) }
        if (loading && items.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(rdp(32)), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) } }
        } else if (items.isEmpty()) {
            item {
                XaniText(
                    copy.t(Copy.SellerNoRequests), Type.Caption, color = ProfileTokens.Sub,
                    fontSizeOverride = 13f, modifier = Modifier.padding(rdp(24)),
                )
            }
        }
        items(items, key = { it.id }) { req ->
            Column(
                Modifier.padding(horizontal = rdp(16), vertical = rdp(5)).fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(rdp(12)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(req.userId, req.fullName.ifEmpty { req.username }, req.avatarUrl, rdp(40))
                    Spacer(Modifier.width(rdp(10)))
                    Column(Modifier.weight(1f)) {
                        XaniText(req.fullName.ifEmpty { "@${req.username}" }, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        XaniText("@${req.username} · ${sellerWhen(req.createdAtMs)}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                    }
                    Spacer(Modifier.width(rdp(8)))
                    Column(horizontalAlignment = Alignment.End) {
                        XaniText(req.amount.toString(), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                        SellerStatusChip(copy, req.status)
                    }
                }
                if (req.note.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(6)))
                    XaniText(req.note, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                }
                if (req.screenshotUrl.isNotEmpty()) {
                    Spacer(Modifier.height(rdp(6)))
                    XaniText(
                        copy.t(Copy.SellerViewProof), Type.Caption, color = ProfileTokens.Accent, fontSizeOverride = 12f,
                        modifier = Modifier.pressable(onClick = { uriHandler.openUri(req.screenshotUrl) }),
                    )
                }
                if (req.status == "pending") {
                    Spacer(Modifier.height(rdp(10)))
                    Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                        val busy = busyId == req.id
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(rdp(10)))
                                .background(if (m.canSend && m.active) ProfileTokens.Accent else ProfileTokens.Bg3)
                                .pressable(enabled = m.canSend && m.active && !busy, onClick = { decide(req, true) })
                                .padding(vertical = rdp(10)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (busy) Spinner(rdp(14), Color.White)
                            else XaniText(copy.t(Copy.SellerApprove), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                        }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.DangerSoft)
                                .pressable(enabled = !busy, onClick = { decide(req, false) }).padding(vertical = rdp(10)),
                            contentAlignment = Alignment.Center,
                        ) { XaniText(copy.t(Copy.SellerReject), Type.LabelBold, color = ProfileTokens.Danger, fontSizeOverride = 12f) }
                    }
                }
            }
        }
    }
}

// ─── history ─────────────────────────────────────────────────────────────────

@Composable
private fun SellerHistoryTab(copy: Copy) {
    var items by remember { mutableStateOf<List<SellerTransferData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Xani.sellers.transfers().value?.let { items = it.items }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(32))) {
        if (loading && items.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(rdp(32)), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) } }
        } else if (items.isEmpty()) {
            item { XaniText(copy.t(Copy.SellerNoTransfers), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(24))) }
        }
        items(items, key = { it.id }) { t ->
            Row(
                Modifier.padding(horizontal = rdp(16), vertical = rdp(4)).fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2).padding(rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(t.receiverId, t.receiverName.ifEmpty { t.receiverUsername }, t.receiverAvatar, rdp(38))
                Spacer(Modifier.width(rdp(10)))
                Column(Modifier.weight(1f)) {
                    XaniText(t.receiverName.ifEmpty { "@${t.receiverUsername}" }, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    XaniText("@${t.receiverUsername} · ${sellerWhen(t.createdAtMs)}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                }
                Spacer(Modifier.width(rdp(8)))
                Column(horizontalAlignment = Alignment.End) {
                    XaniText("-${t.amount} ${t.debitCurrency}", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
                    SellerStatusChip(copy, t.status)
                }
            }
        }
    }
}

// ─── team ────────────────────────────────────────────────────────────────────

@Composable
private fun SellerTeamTab(copy: Copy) {
    var items by remember { mutableStateOf<List<SellerTeamData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Xani.sellers.team().value?.let { items = it }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(32))) {
        if (loading && items.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(rdp(32)), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) } }
        } else if (items.isEmpty()) {
            item { XaniText(copy.t(Copy.SellerNoTeam), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(rdp(24))) }
        }
        items(items, key = { it.userId }) { t ->
            Row(
                Modifier.padding(horizontal = rdp(16), vertical = rdp(4)).fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2).padding(rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(t.userId, t.fullName.ifEmpty { t.username }, t.avatarUrl, rdp(38))
                Spacer(Modifier.width(rdp(10)))
                Column(Modifier.weight(1f)) {
                    XaniText(t.fullName.ifEmpty { "@${t.username}" }, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    XaniText("@${t.username} · ${t.roleTitle}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                }
                Spacer(Modifier.width(rdp(8)))
                Column(horizontalAlignment = Alignment.End) {
                    XaniText("↑ ${t.sent}", Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f)
                    XaniText("↓ ${t.received}", Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                }
            }
        }
    }
}

// ─── dispute thread (shared with the user side) ──────────────────────────────

/** One dispute's chat: header, the disputed transfer, the thread, a reply box. */
@Composable
internal fun SellerDisputeThread(copy: Copy, disputeId: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<SellerDisputeDetailData?>(null) }
    var error by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(0) }
    var reply by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        val r = Xani.sellers.dispute(disputeId)
        r.value?.let { detail = it; error = "" } ?: run { error = r.message }
    }
    BackHandler(onBack = onBack)

    fun send() {
        if (reply.isBlank() || sending) return
        sending = true
        scope.launch {
            val r = Xani.sellers.reply(disputeId, reply.trim())
            sending = false
            if (r.ok) {
                reply = ""
                key++
            } else {
                error = r.message
            }
        }
    }

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
                Column(Modifier.weight(1f)) {
                    XaniText(copy.t(Copy.SellerDispute), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 17f)
                    detail?.dispute?.let { d ->
                        XaniText(
                            copy.t(Copy.SellerDisputeAgainst, "name" to d.sellerUsername),
                            Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f,
                        )
                    }
                }
                detail?.dispute?.let { SellerStatusChip(copy, it.status) }
            }

            if (error.isNotEmpty()) SellerBanner(error, danger = true, onClick = { key++ })

            val d = detail
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(rdp(22), ProfileTokens.Accent) }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(12))) {
                    d.transfer?.let { t ->
                        item {
                            Box(
                                Modifier.padding(horizontal = rdp(16)).padding(bottom = rdp(8)).fillMaxWidth()
                                    .clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2).padding(rdp(12)),
                            ) {
                                Column {
                                    XaniText(
                                        "${t.amount} ${t.debitCurrency} → ${t.received} ${t.creditCurrency} · @${t.receiverUsername}",
                                        Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 12f,
                                    )
                                    XaniText(sellerWhen(t.createdAtMs), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                                }
                            }
                        }
                    }
                    items(d.messages, key = { it.id }) { msg ->
                        val mine = msg.senderId != 0L && msg.senderId == Xani.session.currentUserId
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(4)),
                            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                        ) {
                            XaniText(
                                if (msg.system) copy.t(Copy.SellerDisputeSystem) else "@${msg.sender}",
                                Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f,
                            )
                            Box(
                                Modifier.clip(RoundedCornerShape(rdp(12)))
                                    .background(
                                        when {
                                            msg.system -> ProfileTokens.Accent.copy(alpha = 0.08f)
                                            mine -> ProfileTokens.Accent.copy(alpha = 0.14f)
                                            else -> ProfileTokens.Bg2
                                        },
                                    )
                                    .padding(horizontal = rdp(12), vertical = rdp(8)),
                            ) { XaniText(msg.body, Type.Caption, color = ProfileTokens.Text, fontSizeOverride = 13f) }
                        }
                    }
                }
                if (d.dispute.status == "open") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(8)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightField(
                            value = reply, onValueChange = { reply = it.take(1000) },
                            placeholder = copy.t(Copy.SellerDisputeReply),
                            modifier = Modifier.weight(1f), imeAction = ImeAction.Send,
                            capitalization = KeyboardCapitalization.Sentences, maxChars = 1000,
                        )
                        Spacer(Modifier.width(rdp(8)))
                        Box(
                            Modifier.clip(RoundedCornerShape(rdp(12)))
                                .background(if (reply.isNotBlank()) ProfileTokens.Accent else ProfileTokens.Bg3)
                                .pressable(enabled = reply.isNotBlank() && !sending, onClick = ::send)
                                .padding(horizontal = rdp(14), vertical = rdp(12)),
                        ) {
                            if (sending) Spinner(rdp(16), Color.White)
                            else ProfileIco.Path("M12 19l9 2-9-18-9 18 9-2zm0 0v-8", if (reply.isNotBlank()) Color.White else ProfileTokens.Muted)
                        }
                    }
                } else {
                    XaniText(
                        copy.t(Copy.SellerDisputeClosed), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                        modifier = Modifier.padding(rdp(16)),
                    )
                }
            }
        }
    }
}
