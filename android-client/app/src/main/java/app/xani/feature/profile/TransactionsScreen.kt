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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.media.RemoteImage
import app.xani.core.net.LiveHostData
import app.xani.core.net.MediaConfig
import app.xani.core.net.TxnCategory
import app.xani.core.net.TxnCategoryData
import app.xani.core.net.TxnDayData
import app.xani.core.net.TxnEntryData
import app.xani.core.net.TxnGiftData
import app.xani.core.net.TxnGiftGroupData
import app.xani.core.net.TxnGiftGroupsData
import app.xani.core.net.TxnPurchaseData
import app.xani.core.net.TxnSummaryData
import app.xani.core.net.Xani
import app.xani.feature.social.LevelMark
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The owner's statement. One date window (today by default) drives every
 * page of a small in-screen stack: the summary by category, a category's
 * gifts folded by the other person, one person's gifts, store purchases, and
 * raw ledger lines. Every read is one request; long lists page by cursor.
 */
@Composable
internal fun TransactionsScreen(copy: Copy, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    var range by rememberSaveable { mutableStateOf(TxnRange.Today) }
    var fromText by rememberSaveable { mutableStateOf(today.minusDays(6).format(TxnIsoDay)) }
    var toText by rememberSaveable { mutableStateOf(today.format(TxnIsoDay)) }
    var window by remember { mutableStateOf(txnWindow(TxnRange.Today, today)) }
    var dateError by remember { mutableStateOf("") }
    val stack = remember { mutableStateListOf<TxnPage>(TxnPage.Summary) }
    val page = stack.last()

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack()
    }
    BackHandler(onBack = ::pop)

    fun pick(next: TxnRange) {
        range = next
        dateError = ""
        if (next != TxnRange.Custom) window = txnWindow(next, today)
    }

    fun applyCustom() {
        val start = txnParseDay(fromText)
        val end = txnParseDay(toText)
        if (start == null || end == null) {
            dateError = copy.t(Copy.GiftRecordBadDate)
            return
        }
        dateError = ""
        window = if (end.isBefore(start)) Pair(end, start) else Pair(start, end)
    }

    val title = when (val p = page) {
        TxnPage.Summary -> copy.t(Copy.TxnTitle)
        is TxnPage.Gifts -> copy.t(if (p.sent) Copy.TxnCatGiftsSent else Copy.TxnCatGiftsReceived)
        is TxnPage.GiftDetail -> {
            val name = p.user.fullName.ifBlank { p.user.username }
            copy.t(if (p.sent) Copy.TxnGiftsWith else Copy.TxnGiftsFromName, "name" to name)
        }
        TxnPage.Store -> copy.t(Copy.TxnCatStore)
        is TxnPage.Entries -> copy.t(txnCategoryLabel(p.category))
    }

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
                    title, Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }

            // The window strip is shared by every page so a drill-down keeps
            // the period the user was looking at.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(10)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                TxnChip(copy.t(Copy.GiftRecordToday), range == TxnRange.Today) { pick(TxnRange.Today) }
                TxnChip(copy.t(Copy.GiftRecordWeek), range == TxnRange.Week) { pick(TxnRange.Week) }
                TxnChip(copy.t(Copy.GiftRecordMonth), range == TxnRange.Month) { pick(TxnRange.Month) }
                TxnChip(copy.t(Copy.GiftRecordCustom), range == TxnRange.Custom) { pick(TxnRange.Custom) }
            }
            if (range == TxnRange.Custom) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                ) {
                    LightField(
                        value = fromText, onValueChange = { fromText = it.take(10) },
                        placeholder = copy.t(Copy.GiftRecordFrom), modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Next, maxChars = 10,
                    )
                    LightField(
                        value = toText, onValueChange = { toText = it.take(10) },
                        placeholder = copy.t(Copy.GiftRecordTo), modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, maxChars = 10,
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(ProfileTokens.Accent)
                            .pressable(onClick = ::applyCustom)
                            .padding(horizontal = rdp(14), vertical = rdp(12)),
                    ) {
                        XaniText(copy.t(Copy.GiftRecordApply), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).padding(bottom = rdp(6))) {
                XaniText(
                    copy.t(Copy.GiftRecordRange, "from" to window.first.format(TxnIsoDay), "to" to window.second.format(TxnIsoDay)),
                    Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                )
                if (dateError.isNotEmpty()) {
                    Spacer(Modifier.width(rdp(10)))
                    XaniText(dateError, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f)
                }
            }

            val from = window.first.format(TxnIsoDay)
            val to = window.second.format(TxnIsoDay)
            when (val p = page) {
                TxnPage.Summary -> TxnSummaryPage(copy, from, to) { stack.add(it) }
                is TxnPage.Gifts -> TxnGiftGroupsPage(copy, from, to, p.sent) { stack.add(TxnPage.GiftDetail(p.sent, it)) }
                is TxnPage.GiftDetail -> TxnGiftDetailPage(copy, from, to, p.sent, p.user)
                TxnPage.Store -> TxnStorePage(copy, from, to)
                is TxnPage.Entries -> TxnEntriesPage(copy, from, to, p.category)
            }
        }
    }
}

// ─── pages ───────────────────────────────────────────────────────────────────

private sealed interface TxnPage {
    data object Summary : TxnPage
    data class Gifts(val sent: Boolean) : TxnPage
    data class GiftDetail(val sent: Boolean, val user: LiveHostData) : TxnPage
    data object Store : TxnPage
    data class Entries(val category: TxnCategory) : TxnPage
}

private enum class TxnRange { Today, Week, Month, Custom }

private val TxnIsoDay: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val TxnStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm")
private val TxnDayLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

private val SpendTint = Color(0xFF7C3AED)
private val EarnTint = Color(0xFF0EA5E9)

private fun txnWindow(range: TxnRange, today: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
    TxnRange.Today -> today to today
    TxnRange.Week -> today.with(DayOfWeek.MONDAY) to today
    TxnRange.Month -> today.withDayOfMonth(1) to today
    TxnRange.Custom -> today.minusDays(6) to today
}

private fun txnParseDay(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), TxnIsoDay)
} catch (_: DateTimeParseException) {
    null
}

internal fun txnStamp(ms: Long): String =
    if (ms <= 0L) "" else TxnStamp.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private fun txnDayLabel(day: String): String = try {
    TxnDayLabel.format(LocalDate.parse(day, TxnIsoDay))
} catch (_: DateTimeParseException) {
    day
}

private fun txnCategoryLabel(c: TxnCategory): String = when (c) {
    TxnCategory.GiftsSent -> Copy.TxnCatGiftsSent
    TxnCategory.Store -> Copy.TxnCatStore
    TxnCategory.GameBets -> Copy.TxnCatGameBets
    TxnCategory.GiftsReceived -> Copy.TxnCatGiftsReceived
    TxnCategory.Topups -> Copy.TxnCatTopups
    TxnCategory.GameWins -> Copy.TxnCatGameWins
    TxnCategory.Withdrawals -> Copy.TxnCatWithdrawals
    TxnCategory.Adjustments -> Copy.TxnCatAdjustments
}

private fun txnCategoryPage(c: TxnCategory): TxnPage = when (c) {
    TxnCategory.GiftsSent -> TxnPage.Gifts(sent = true)
    TxnCategory.GiftsReceived -> TxnPage.Gifts(sent = false)
    TxnCategory.Store -> TxnPage.Store
    else -> TxnPage.Entries(c)
}

/** A page's async state: loading, a message, or a value. */
private class Loaded<T>(val value: T?, val loading: Boolean, val error: String)

@Composable
private fun TxnSummaryPage(copy: Copy, from: String, to: String, open: (TxnPage) -> Unit) {
    var state by remember { mutableStateOf(Loaded<TxnSummaryData>(null, true, "")) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(from, to, reloadKey) {
        state = Loaded(state.value, true, "")
        val r = Xani.wallet.summary(from, to)
        state = Loaded(r.value ?: state.value, false, if (r.ok) "" else r.message)
    }
    val s = state.value

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "balance") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(top = rdp(6)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                BalanceCard(Modifier.weight(1f), copy.t(Copy.TxnCoins), "🪙 ${compact(s?.coins ?: 0L)}", Color(0xFFF59E0B))
                BalanceCard(Modifier.weight(1f), copy.t(Copy.TxnDiamonds), "💎 ${compact(s?.diamonds ?: 0L)}", EarnTint)
            }
        }
        item(key = "totals") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(top = rdp(10)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                TotalTile(Modifier.weight(1f), copy.t(Copy.TxnSpent), "🪙 ${compact(s?.spentCoins ?: 0L)}", SpendTint)
                TotalTile(
                    Modifier.weight(1f), copy.t(Copy.TxnEarned),
                    "🪙 ${compact(s?.earnedCoins ?: 0L)}  💎 ${compact(s?.earnedDiamonds ?: 0L)}", EarnTint,
                )
            }
        }
        item(key = "status") { StatusLine(copy, state.loading, state.error) { reloadKey++ } }

        item(key = "spend-head") { SectionHead(copy.t(Copy.TxnSpent), SpendTint) }
        item(key = "spend") {
            CategoryCard {
                val cats = TxnCategory.entries.filter { it.spend }
                cats.forEachIndexed { i, c ->
                    CategoryRow(copy, c, s?.category(c.key) ?: TxnCategoryData.Zero, i < cats.lastIndex) { open(txnCategoryPage(c)) }
                }
            }
        }
        item(key = "earn-head") { SectionHead(copy.t(Copy.TxnEarned), EarnTint) }
        item(key = "earn") {
            CategoryCard {
                val cats = TxnCategory.entries.filter { !it.spend }
                cats.forEachIndexed { i, c ->
                    CategoryRow(copy, c, s?.category(c.key) ?: TxnCategoryData.Zero, i < cats.lastIndex) { open(txnCategoryPage(c)) }
                }
            }
        }

        item(key = "days-head") { SectionHead(copy.t(Copy.TxnByDay), ProfileTokens.Text) }
        val days = s?.days.orEmpty()
        if (days.isEmpty()) {
            item(key = "days-empty") { if (!state.loading) EmptyLine(copy.t(Copy.TxnEmpty)) }
        } else {
            items(days, key = { it.day }) { d -> DayLine(copy, d) }
        }
    }
}

@Composable
private fun TxnGiftGroupsPage(copy: Copy, from: String, to: String, sent: Boolean, open: (LiveHostData) -> Unit) {
    var state by remember { mutableStateOf(Loaded<TxnGiftGroupsData>(null, true, "")) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(from, to, sent, reloadKey) {
        state = Loaded(state.value, true, "")
        val r = Xani.wallet.giftGroups(from, to, sent)
        state = Loaded(r.value ?: state.value, false, if (r.ok) "" else r.message)
    }
    val s = state.value
    val tint = if (sent) SpendTint else EarnTint

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "totals") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(top = rdp(6)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                TotalTile(Modifier.weight(1f), copy.t(Copy.GiftRecordGifts, "n" to compact(s?.count ?: 0L)), "🪙 ${compact(s?.coins ?: 0L)}", tint)
                TotalTile(
                    Modifier.weight(1f), copy.t(Copy.TxnPeople, "n" to (s?.items?.size ?: 0).toString()),
                    if (sent) "🪙 ${compact(s?.coins ?: 0L)}" else "💎 ${compact(s?.diamonds ?: 0L)}", tint,
                )
            }
        }
        item(key = "status") { StatusLine(copy, state.loading, state.error) { reloadKey++ } }
        item(key = "head") { SectionHead(copy.t(if (sent) Copy.TxnSentTo else Copy.TxnReceivedFrom), ProfileTokens.Text) }
        val rows = s?.items.orEmpty()
        if (rows.isEmpty()) {
            item(key = "empty") { if (!state.loading) EmptyLine(copy.t(Copy.TxnEmpty)) }
        } else {
            items(rows, key = { it.user.id }) { g -> GiftGroupRow(copy, g, sent) { open(g.user) } }
        }
    }
}

@Composable
private fun TxnGiftDetailPage(copy: Copy, from: String, to: String, sent: Boolean, user: LiveHostData) {
    val rows = remember { mutableStateListOf<TxnGiftData>() }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(beforeId: Long) {
        loading = true
        val r = Xani.wallet.giftsWith(user.id, from, to, sent, beforeId)
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
    LaunchedEffect(from, to, sent, user.id, reloadKey) { load(0L) }

    val name = user.fullName.ifBlank { user.username }
    val total = rows.sumOf { it.totalCoins }
    val diamonds = rows.sumOf { it.diamonds }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "person") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(16)).padding(top = rdp(6))
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(ProfileTokens.Bg2)
                    .padding(rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(user.id, name, user.avatarUrl, rdp(46), frameUrl = user.frameUrl, frameMime = user.frameMime)
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                        XaniText(name, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LevelMark(if (sent) user.receiverLevel else user.senderLevel, rdp(16))
                    }
                    if (user.username.isNotBlank()) {
                        XaniText("@${user.username}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    XaniText("🪙 ${compact(total)}", Type.LabelBold, color = if (sent) SpendTint else EarnTint, fontSizeOverride = 15f)
                    if (!sent) XaniText("💎 ${compact(diamonds)}", Type.Caption, color = EarnTint, fontSizeOverride = 12f)
                    XaniText(copy.t(Copy.GiftRecordGifts, "n" to rows.size.toString()), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
                }
            }
        }
        item(key = "status") { StatusLine(copy, loading && rows.isEmpty(), error) { reloadKey++ } }
        if (rows.isEmpty()) {
            item(key = "empty") { if (!loading) EmptyLine(copy.t(Copy.TxnEmpty)) }
        } else {
            items(rows, key = { it.id }) { g -> GiftRow(g, sent) }
        }
        loadMore(copy, cursor.isNotEmpty(), loading && rows.isNotEmpty()) {
            scope.launch { load(cursor.toLongOrNull() ?: 0L) }
        }
    }
}

@Composable
private fun TxnStorePage(copy: Copy, from: String, to: String) {
    val rows = remember { mutableStateListOf<TxnPurchaseData>() }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(beforeId: Long) {
        loading = true
        val r = Xani.wallet.purchases(from, to, beforeId)
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
    LaunchedEffect(from, to, reloadKey) { load(0L) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "totals") {
            Row(Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(top = rdp(6))) {
                TotalTile(Modifier.weight(1f), copy.t(Copy.TxnEntries, "n" to rows.size.toString()), "🪙 ${compact(rows.sumOf { it.coins })}", SpendTint)
            }
        }
        item(key = "status") { StatusLine(copy, loading && rows.isEmpty(), error) { reloadKey++ } }
        if (rows.isEmpty()) {
            item(key = "empty") { if (!loading) EmptyLine(copy.t(Copy.TxnEmpty)) }
        } else {
            items(rows, key = { it.id }) { p -> PurchaseRow(copy, p) }
        }
        loadMore(copy, cursor.isNotEmpty(), loading && rows.isNotEmpty()) {
            scope.launch { load(cursor.toLongOrNull() ?: 0L) }
        }
    }
}

@Composable
private fun TxnEntriesPage(copy: Copy, from: String, to: String, category: TxnCategory) {
    val rows = remember { mutableStateListOf<TxnEntryData>() }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(beforeId: Long) {
        loading = true
        val r = Xani.wallet.entries(category.key, from, to, beforeId)
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
    LaunchedEffect(from, to, category, reloadKey) { load(0L) }

    val tint = if (category.spend) SpendTint else EarnTint
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
        item(key = "totals") {
            val coins = rows.sumOf { it.coins }
            val diamonds = rows.sumOf { it.diamonds }
            Row(Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(top = rdp(6))) {
                TotalTile(
                    Modifier.weight(1f), copy.t(Copy.TxnEntries, "n" to rows.size.toString()),
                    buildString {
                        if (coins != 0L || diamonds == 0L) append("🪙 ").append(signed(coins))
                        if (diamonds != 0L) { if (isNotEmpty()) append("  "); append("💎 ").append(signed(diamonds)) }
                    },
                    tint,
                )
            }
        }
        item(key = "status") { StatusLine(copy, loading && rows.isEmpty(), error) { reloadKey++ } }
        if (rows.isEmpty()) {
            item(key = "empty") { if (!loading) EmptyLine(copy.t(Copy.TxnEmpty)) }
        } else {
            items(rows, key = { it.id }) { e -> EntryRow(copy, e) }
        }
        loadMore(copy, cursor.isNotEmpty(), loading && rows.isNotEmpty()) {
            scope.launch { load(cursor.toLongOrNull() ?: 0L) }
        }
    }
}

// ─── pieces ──────────────────────────────────────────────────────────────────

private fun signed(n: Long): String = if (n > 0) "+" + compact(n) else compact(n)

private fun LazyListScope.loadMore(copy: Copy, more: Boolean, busy: Boolean, onMore: () -> Unit) {
    if (!more && !busy) return
    item(key = "more") {
        Box(Modifier.fillMaxWidth().padding(vertical = rdp(14)), contentAlignment = Alignment.Center) {
            if (busy) {
                Spinner(rdp(18), ProfileTokens.Accent)
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(ProfileTokens.Bg2)
                        .pressable(onClick = onMore)
                        .padding(horizontal = rdp(18), vertical = rdp(10)),
                ) {
                    XaniText(copy.t(Copy.TxnLoadMore), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12f)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(copy: Copy, loading: Boolean, error: String, onRetry: () -> Unit) {
    if (!loading && error.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            Spinner(rdp(16), ProfileTokens.Accent)
        } else {
            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = onRetry)
                    .padding(horizontal = rdp(12), vertical = rdp(6)),
            ) {
                XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f)
            }
        }
    }
}

@Composable
private fun TxnChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(14)))
            .background(if (active) ProfileTokens.Accent else ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(12), vertical = rdp(8)),
    ) {
        XaniText(label, Type.LabelBold, color = if (active) Color.White else ProfileTokens.Text, fontSizeOverride = 12f, maxLines = 1)
    }
}

@Composable
private fun BalanceCard(modifier: Modifier, title: String, value: String, tint: Color) {
    Column(modifier.clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(rdp(14))) {
        XaniText(title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
        Spacer(Modifier.height(rdp(4)))
        XaniText(value, Type.H2, color = tint, fontSizeOverride = 20f, maxLines = 1)
    }
}

@Composable
private fun TotalTile(modifier: Modifier, title: String, value: String, tint: Color) {
    Column(modifier.clip(RoundedCornerShape(rdp(16))).background(tint.copy(alpha = 0.08f)).padding(rdp(14))) {
        XaniText(title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
        Spacer(Modifier.height(rdp(4)))
        XaniText(value, Type.LabelBold, color = tint, fontSizeOverride = 16f, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHead(title: String, tint: Color) {
    Row(
        Modifier.padding(horizontal = rdp(20)).padding(top = rdp(18), bottom = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(8)).clip(CircleShape).background(tint))
        Spacer(Modifier.width(rdp(8)))
        XaniText(title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
    }
}

@Composable
private fun CategoryCard(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = rdp(16)).fillMaxWidth().clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2),
    ) { content() }
}

@Composable
private fun CategoryRow(copy: Copy, c: TxnCategory, data: TxnCategoryData, divider: Boolean, onClick: () -> Unit) {
    val tint = if (c.spend) SpendTint else EarnTint
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = rdp(16), vertical = rdp(13)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XaniText(copy.t(txnCategoryLabel(c)), Type.Label, color = ProfileTokens.Text, fontSizeOverride = 14f)
            XaniText(copy.t(Copy.TxnEntries, "n" to compact(data.count)), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
        }
        Column(horizontalAlignment = Alignment.End) {
            val showCoins = data.coins != 0L || data.diamonds == 0L
            if (showCoins) XaniText("🪙 ${compact(data.coins)}", Type.LabelBold, color = tint, fontSizeOverride = 14f)
            if (data.diamonds != 0L) {
                XaniText("💎 ${compact(data.diamonds)}", if (showCoins) Type.Caption else Type.LabelBold, color = tint, fontSizeOverride = if (showCoins) 12f else 14f)
            }
        }
        Spacer(Modifier.width(rdp(8)))
        ProfileIco.Chevron(ProfileTokens.Muted, rdp(16))
    }
    if (divider) Box(Modifier.fillMaxWidth().padding(horizontal = rdp(16)).height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun DayLine(copy: Copy, d: TxnDayData) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XaniText(txnDayLabel(d.day), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
            XaniText(copy.t(Copy.TxnEntries, "n" to compact(d.count)), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (d.spentCoins != 0L) XaniText("-🪙 ${compact(d.spentCoins)}", Type.Caption, color = SpendTint, fontSizeOverride = 12f)
            if (d.earnedCoins != 0L) XaniText("+🪙 ${compact(d.earnedCoins)}", Type.Caption, color = EarnTint, fontSizeOverride = 12f)
            if (d.earnedDiamonds != 0L) XaniText("+💎 ${compact(d.earnedDiamonds)}", Type.Caption, color = EarnTint, fontSizeOverride = 12f)
            if (d.withdrawDiamonds != 0L) XaniText("-💎 ${compact(d.withdrawDiamonds)}", Type.Caption, color = SpendTint, fontSizeOverride = 12f)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun GiftGroupRow(copy: Copy, g: TxnGiftGroupData, sent: Boolean, onClick: () -> Unit) {
    val name = g.user.fullName.ifBlank { g.user.username }
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(g.user.id, name, g.user.avatarUrl, rdp(42), frameUrl = g.user.frameUrl, frameMime = g.user.frameMime)
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                XaniText(name, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LevelMark(if (sent) g.user.receiverLevel else g.user.senderLevel, rdp(14))
            }
            XaniText(
                "${copy.t(Copy.GiftRecordGifts, "n" to compact(g.count))} · ${txnStamp(g.lastAtMs)}",
                Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            XaniText("🪙 ${compact(g.coins)}", Type.LabelBold, color = if (sent) SpendTint else EarnTint, fontSizeOverride = 14f)
            if (!sent) XaniText("💎 ${compact(g.diamonds)}", Type.Caption, color = EarnTint, fontSizeOverride = 11f)
        }
        Spacer(Modifier.width(rdp(6)))
        ProfileIco.Chevron(ProfileTokens.Muted, rdp(16))
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun GiftRow(g: TxnGiftData, sent: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(44)).clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg3), contentAlignment = Alignment.Center) {
            val url = if (g.listingUrl.isEmpty() || g.listingUrl.startsWith("http")) g.listingUrl else MediaConfig.url(g.listingUrl)
            if (url.isNotEmpty()) RemoteImage(url, Modifier.fillMaxSize(), maxEdge = 160, fallback = ProfileTokens.Bg3)
            else XaniText("🎁", Type.H2, fontSizeOverride = 20f)
        }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(
                if (g.count > 1) "${g.title} ×${g.count}" else g.title,
                Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            XaniText(
                "🪙 ${compact(g.unitCoins)} × ${g.count} · ${txnStamp(g.atMs)}",
                Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            XaniText("🪙 ${compact(g.totalCoins)}", Type.LabelBold, color = if (sent) SpendTint else EarnTint, fontSizeOverride = 14f)
            if (!sent) XaniText("💎 ${compact(g.diamonds)}", Type.Caption, color = EarnTint, fontSizeOverride = 11f)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun PurchaseRow(copy: Copy, p: TxnPurchaseData) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(44)).clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg3), contentAlignment = Alignment.Center) {
            val url = MediaConfig.url(p.previewKey.ifEmpty { p.assetKey })
            if (url.isNotEmpty()) RemoteImage(url, Modifier.fillMaxSize(), maxEdge = 160, fallback = ProfileTokens.Bg3)
            else XaniText("🛍", Type.H2, fontSizeOverride = 20f)
        }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            XaniText(p.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                if (p.days > 0) append(copy.t(Copy.TxnDaysPack, "n" to p.days.toString())).append(" · ")
                append(txnStamp(p.atMs))
            }
            XaniText(meta, Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f, maxLines = 1)
            if (p.expiresAtMs > 0L) {
                XaniText(copy.t(Copy.TxnExpires, "date" to txnStamp(p.expiresAtMs)), Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 10f, maxLines = 1)
            }
        }
        XaniText("-🪙 ${compact(p.coins)}", Type.LabelBold, color = SpendTint, fontSizeOverride = 14f)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.RowLine))
}

@Composable
private fun EntryRow(copy: Copy, e: TxnEntryData) {
    val cat = TxnCategory.entries.firstOrNull { it.key == e.category }
    val label = if (cat != null) copy.t(txnCategoryLabel(cat)) else e.category
    // Who: the other side of a gift, or the staff member behind an adjustment.
    val who = buildString {
        e.peer?.let { append(copy.t(Copy.TxnWith, "name" to personName(it))) }
        e.actor?.let {
            if (isNotEmpty()) append(" · ")
            append(copy.t(Copy.TxnBy, "name" to personName(it)))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XaniText(e.note.ifEmpty { label }, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.note.isNotEmpty() || who.isNotEmpty()) {
                XaniText(
                    listOf(label, who).filter { it.isNotEmpty() }.joinToString(" · "),
                    Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            XaniText(txnStamp(e.atMs), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (e.coins != 0L) XaniText("🪙 ${signed(e.coins)}", Type.LabelBold, color = if (e.coins < 0) SpendTint else EarnTint, fontSizeOverride = 14f)
            if (e.diamonds != 0L) XaniText("💎 ${signed(e.diamonds)}", Type.LabelBold, color = if (e.diamonds < 0) SpendTint else EarnTint, fontSizeOverride = 14f)
            if (e.beans != 0L) XaniText("🫘 ${signed(e.beans)}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.RowLine))
}

/** Display name for a statement line: full name, else @handle, else the id. */
internal fun personName(p: app.xani.core.net.LiveHostData): String =
    p.fullName.ifBlank { if (p.username.isNotBlank()) "@${p.username}" else "#${p.id}" }

@Composable
private fun EmptyLine(text: String) {
    XaniText(
        text, Type.Caption, color = ProfileTokens.Muted, align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(16)),
    )
}
