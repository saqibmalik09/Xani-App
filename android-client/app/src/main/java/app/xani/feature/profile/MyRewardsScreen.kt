package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.LiveFeed
import app.xani.core.net.RewardAmounts
import app.xani.core.net.RewardCalendar
import app.xani.core.net.RewardCampaignStatus
import app.xani.core.net.RewardClaimable
import app.xani.core.net.RewardDayGroup
import app.xani.core.net.RewardDetail
import app.xani.core.net.RewardLedger
import app.xani.core.net.RewardSummary
import app.xani.core.net.Xani
import app.xani.feature.call.LiveSession
import app.xani.feature.create.conditionSummary
import app.xani.feature.create.fmtShort
import app.xani.feature.create.roleMatches
import app.xani.i18n.Copy
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * What the host's time on air earned. Today / Week / Month / All, each with
 * the live time counted in that range beside the rewards it produced, then
 * every business day with its rewards: when the required time was completed,
 * when the balance moved, from what to what. Every figure and every time is
 * the server's, in the business timezone the admin set for live time — the
 * same ledger row support reads, so what the host sees here is final.
 */
@Composable
internal fun MyRewardsScreen(
    copy: Copy,
    onBack: () -> Unit,
) {
    var range by remember { mutableStateOf("today") }
    var filter by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf(RewardSummary.Empty) }
    var claimable by remember { mutableStateOf<List<RewardClaimable>>(emptyList()) }
    var calendar by remember { mutableStateOf(RewardCalendar.Empty) }
    var enabled by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<RewardDayGroup>>(emptyList()) }
    var cursor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var key by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf(0L) }
    var toast by remember { mutableStateOf("") }
    // The running campaigns and where this host stands on each: criteria
    // reached vs remaining, straight from the same projection the live HUD
    // reads — never computed on the phone.
    var campaigns by remember { mutableStateOf<List<RewardCampaignStatus>>(emptyList()) }

    // One coroutine per (range, filter, refresh): header and first page together.
    LaunchedEffect(range, filter, key) {
        loading = true
        val home = Xani.rewards.me(range)
        val h = home.value
        if (h == null) {
            error = home.message
            loading = false
            return@LaunchedEffect
        }
        summary = h.summary
        claimable = h.claimable
        calendar = h.calendar
        enabled = h.enabled
        error = ""
        // "All" is keyset-paged with no date bound; the others are bounded by the range.
        val bounded = range != "all"
        val hist = Xani.rewards.history(
            from = if (bounded) h.summary.from else "",
            to = if (bounded) h.summary.to else "",
            status = filter,
        )
        val g = hist.value
        if (g != null) {
            groups = g.days
            cursor = g.cursor
        } else {
            error = hist.message
        }
        loading = false
    }

    // A reward landed on the hub while this screen is open: re-read, never patch.
    LaunchedEffect(Unit) {
        LiveFeed.rewards.collect { key++ }
    }
    // On stage right now (this device is the host / a co-host): the range's
    // live time is moving on the server, so re-read the header every 10 s
    // while this screen is open. Off stage nothing moves without a hub event.
    val onStage by LiveSession.state.collectAsState()
    LaunchedEffect(onStage?.onStage == true, range) {
        if (onStage?.onStage != true) return@LaunchedEffect
        while (true) {
            delay(10_000)
            val r = Xani.rewards.me(range).value ?: continue
            summary = r.summary
            claimable = r.claimable
            calendar = r.calendar
        }
    }
    LaunchedEffect(toast) {
        if (toast.isEmpty()) return@LaunchedEffect
        delay(3_500)
        toast = ""
    }
    // Campaign standing refreshes with the same key the ledger uses, so a
    // reward landing updates the criteria list too. Only campaigns whose
    // role rules match this account are kept — someone else's campaign
    // shown as "not eligible" reads as a problem, not information.
    LaunchedEffect(key) {
        val r = Xani.rewards.eligibility(role = "host", mode = "video")
        r.value?.let { campaigns = it.campaigns.filter { c -> c.roleMatches() } }
    }

    var moreKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(moreKey) {
        if (moreKey == 0 || cursor.isEmpty()) return@LaunchedEffect
        loadingMore = true
        val bounded = range != "all"
        val r = Xani.rewards.history(
            from = if (bounded) summary.from else "",
            to = if (bounded) summary.to else "",
            status = filter,
            cursor = cursor,
        )
        val g = r.value
        if (g != null) {
            // Merge by day: the page boundary can split one day across two pages.
            val merged = LinkedHashMap<String, RewardDayGroup>(groups.size + g.days.size)
            for (d in groups) merged[d.day] = d
            for (d in g.days) {
                val prev = merged[d.day]
                merged[d.day] = if (prev == null) d else prev.copy(rewards = prev.rewards + d.rewards.filter { n -> prev.rewards.none { it.id == n.id } })
            }
            groups = merged.values.toList()
            cursor = g.cursor
        }
        loadingMore = false
    }

    BackHandler(enabled = detailId == 0L, onBack = onBack)

    val locale = remember { Locale.getDefault() }
    val zone = remember(calendar.tz) { runCatching { ZoneId.of(calendar.tz) }.getOrDefault(ZoneId.of("UTC")) }
    val timeFmt = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val dayFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }

    // Days = every day with a reward row ∪ every day with live time but no
    // reward (so a host sees "you were live 20m, no level reached" rather than
    // a hole). Newest first; the filter hides live-only days.
    val days = remember(groups, summary, filter) {
        val byDay = LinkedHashMap<String, DayCard>()
        for (g in groups) byDay[g.day] = DayCard(g.day, g.label, g.liveMs, g.totals, g.rewards)
        if (filter.isEmpty()) {
            for (d in summary.days) {
                val cur = byDay[d.day]
                if (cur == null) byDay[d.day] = DayCard(d.day, d.label, d.liveMs, d.added, emptyList())
                else if (cur.liveMs == 0L && d.liveMs > 0L) byDay[d.day] = cur.copy(liveMs = d.liveMs)
            }
        }
        byDay.values.sortedByDescending { it.day }
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
                XaniText(copy.t(Copy.RewardsTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, modifier = Modifier.weight(1f))
                if (loading) Spinner(rdp(18), ProfileTokens.Accent)
            }

            // Today / Week / Month / All
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(rdp(3)),
            ) {
                listOf("today" to Copy.RewardsToday, "week" to Copy.RewardsWeek, "month" to Copy.RewardsMonth, "all" to Copy.RewardsAll).forEach { (k, label) ->
                    val on = range == k
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(rdp(11)))
                            .background(if (on) ProfileTokens.Bg else Color.Transparent)
                            .pressable(onClick = { if (!on) range = k })
                            .padding(vertical = rdp(8)),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(copy.t(label), if (on) Type.LabelBold else Type.Label, color = if (on) ProfileTokens.Text else ProfileTokens.Sub, fontSizeOverride = 13f)
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = rdp(16), end = rdp(16), top = rdp(12), bottom = rdp(32)),
            ) {
                item(key = "hero") {
                    HeroCard(copy = copy, summary = summary, enabled = enabled)
                }
                if (claimable.any { it.amount > 0L || it.waitingAmount > 0L }) {
                    item(key = "claim") {
                        Spacer(Modifier.height(rdp(10)))
                        claimable.forEach { c ->
                            ClaimRow(
                                copy = copy,
                                c = c,
                                zone = zone,
                                timeFmt = timeFmt,
                                onClaimed = { msg ->
                                    toast = msg
                                    key++
                                },
                                onError = { error = it },
                            )
                        }
                    }
                }
                if (campaigns.isNotEmpty()) {
                    item(key = "campaigns-h") {
                        Spacer(Modifier.height(rdp(18)))
                        XaniText(copy.t(Copy.RewardsCampaigns), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.padding(start = rdp(4)))
                        Spacer(Modifier.height(rdp(8)))
                    }
                    campaigns.forEach { c ->
                        item(key = "camp-${c.campaignId}") { CampaignCard(copy, c) }
                    }
                }
                if (error.isNotEmpty()) {
                    item(key = "error") {
                        Spacer(Modifier.height(rdp(12)))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            XaniText(error.ifBlank { copy.t(Copy.RewardsLoadFailed) }, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = { key++ })
                                    .padding(horizontal = rdp(12), vertical = rdp(6)),
                            ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f) }
                        }
                    }
                }
                item(key = "filters") {
                    Spacer(Modifier.height(rdp(18)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XaniText(copy.t(Copy.RewardsByDay), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.padding(start = rdp(4)).weight(1f))
                    }
                    Spacer(Modifier.height(rdp(8)))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                        listOf(
                            "" to Copy.RewardsFilterAll,
                            "earned" to Copy.RewardsStatusPending,
                            "issued" to Copy.RewardsStatusAdded,
                            "claimable" to Copy.RewardsStatusClaimable,
                            "claimed" to Copy.RewardsStatusClaimed,
                            "expired" to Copy.RewardsStatusExpired,
                        ).forEach { (k, label) ->
                            val on = filter == k
                            Box(
                                Modifier.clip(RoundedCornerShape(rdp(14)))
                                    .background(if (on) ProfileTokens.Text else ProfileTokens.Bg2)
                                    .pressable(onClick = { filter = k })
                                    .padding(horizontal = rdp(12), vertical = rdp(6)),
                            ) { XaniText(copy.t(label), Type.LabelBold, color = if (on) Color.White else ProfileTokens.Sub, fontSizeOverride = 11f) }
                        }
                    }
                    Spacer(Modifier.height(rdp(8)))
                }
                if (!loading && days.isEmpty()) {
                    item(key = "empty") {
                        XaniText(
                            copy.t(if (range == "all" && filter.isEmpty()) Copy.RewardsEmpty else Copy.RewardsEmptyRange),
                            Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f,
                            modifier = Modifier.padding(start = rdp(4), top = rdp(4)),
                        )
                    }
                }
                days.forEach { d ->
                    item(key = "day-${d.day}") {
                        DayHeader(copy = copy, d = d, label = dayLabel(d.day, d.label, calendar.today, copy, dayFmt))
                    }
                    if (d.rewards.isEmpty()) {
                        item(key = "none-${d.day}") {
                            XaniText(
                                copy.t(Copy.RewardsNoRewardsDay), Type.Caption, color = ProfileTokens.Muted, fontSizeOverride = 12f,
                                modifier = Modifier.padding(start = rdp(14), bottom = rdp(10)),
                            )
                        }
                    }
                    d.rewards.forEach { r ->
                        item(key = "r-${r.id}") {
                            RewardRow(copy = copy, r = r, zone = zone, timeFmt = timeFmt, onClick = { detailId = r.id })
                        }
                    }
                }
                if (cursor.isNotEmpty()) {
                    item(key = "more") {
                        Box(
                            Modifier.fillMaxWidth().padding(top = rdp(10)).clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2)
                                .pressable(enabled = !loadingMore, onClick = { moreKey++ })
                                .padding(vertical = rdp(12)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingMore) Spinner(rdp(16), ProfileTokens.Accent)
                            else XaniText(copy.t(Copy.RewardsOlder), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
                        }
                    }
                }
                item(key = "note") {
                    Spacer(Modifier.height(rdp(16)))
                    XaniText(
                        copy.t(Copy.RewardsDayNote, "start" to calendar.dayStart, "tz" to calendar.tz),
                        Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 11f,
                        modifier = Modifier.padding(horizontal = rdp(4)),
                    )
                }
            }
        }

        if (toast.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = rdp(24), start = rdp(24), end = rdp(24))
                    .clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Text).padding(horizontal = rdp(16), vertical = rdp(12)),
            ) { XaniText(toast, Type.LabelBold, color = Color.White, fontSizeOverride = 13f) }
        }

        if (detailId != 0L) {
            RewardDetailSheet(copy = copy, id = detailId, zone = zone, onClose = { detailId = 0L })
        }
    }
}

/** A business day on the list: live time, totals, the rewards (possibly none). */
private data class DayCard(
    val day: String,
    val label: String,
    val liveMs: Long,
    val totals: RewardAmounts,
    val rewards: List<RewardLedger>,
)

@Composable
private fun HeroCard(copy: Copy, summary: RewardSummary, enabled: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(20)))
            .background(Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B))))
            .padding(rdp(18)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XaniText(copy.t(Copy.RewardsAdded), Type.Caption, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 12f, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(rdp(10))).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = rdp(10), vertical = rdp(5)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.RewardsLiveTime), Type.Micro, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 10f)
                Spacer(Modifier.width(rdp(6)))
                XaniText(fmtClock(summary.liveMs, seconds = true), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
        }
        Spacer(Modifier.height(rdp(6)))
        XaniText(
            if (summary.added.isEmpty()) "0" else fmtAmounts(summary.added),
            Type.H1, color = Color.White, fontSizeOverride = 32f, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val sub = buildList {
            if (summary.pending.isNotEmpty()) add("${copy.t(Copy.RewardsPending)} ${fmtAmounts(summary.pending)}")
            if (summary.claimable.isNotEmpty()) add("${copy.t(Copy.RewardsClaimable)} ${fmtAmounts(summary.claimable)}")
            if (!enabled) add(copy.t(Copy.RewardsOff))
        }
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(rdp(6)))
            XaniText(sub.joinToString(" · "), Type.Micro, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 11f, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * One campaign and where the host stands: an Eligible / In progress pill,
 * each criterion marked reached (green) or still remaining (amber) with the
 * server's required-vs-actual, then what it pays next. All figures are the
 * server's own projection — the same one the live HUD explains.
 */
@Composable
private fun CampaignCard(copy: Copy, c: RewardCampaignStatus) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = rdp(6)).clip(RoundedCornerShape(rdp(14)))
            .background(ProfileTokens.Bg2).padding(horizontal = rdp(14), vertical = rdp(12)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XaniText(c.campaign, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13.5f, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(rdp(8)))
            val (bg, fg, label) =
                if (c.eligible) Triple(Color(0x1416A34A), Color(0xFF15803D), Copy.RewardsCampaignEligible)
                else Triple(Color(0x14F59E0B), Color(0xFFB45309), Copy.RewardsCampaignProgress)
            Box(Modifier.clip(RoundedCornerShape(rdp(8))).background(bg).padding(horizontal = rdp(7), vertical = rdp(2))) {
                XaniText(copy.t(label), Type.Micro, color = fg, fontSizeOverride = 10f)
            }
        }
        c.conditions.forEach { cond ->
            Spacer(Modifier.height(rdp(5)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(rdp(6)).clip(CircleShape).background(if (cond.pass) Color(0xFF16A34A) else Color(0xFFD97706)))
                Spacer(Modifier.width(rdp(8)))
                XaniText(
                    conditionSummary(cond), Type.Caption,
                    color = if (cond.pass) ProfileTokens.Sub else Color(0xFFB45309),
                    fontSizeOverride = 11.5f, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (c.nextAmount > 0L && c.nextThresholdMs > 0L) {
            Spacer(Modifier.height(rdp(7)))
            XaniText(
                copy.t(Copy.RewardsEligibleNext, "amt" to fmtAmount(c.nextAsset, c.nextAmount), "t" to fmtShort(c.nextThresholdMs)),
                Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 10.5f,
            )
        }
    }
}

/**
 * One asset's claim button. The idempotency key is minted when the button
 * is first tapped and kept until the server answers OK: a retry after a
 * dropped reply gets the first result back, never a second payment.
 */
@Composable
private fun ClaimRow(
    copy: Copy,
    c: RewardClaimable,
    zone: ZoneId,
    timeFmt: DateTimeFormatter,
    onClaimed: (String) -> Unit,
    onError: (String) -> Unit,
) {
    var idem by remember(c.asset) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        busy = true
        val r = Xani.rewards.claim(c.asset, idem)
        val got = r.value
        if (got != null) {
            idem = ""
            onClaimed(copy.t(Copy.RewardsClaimed, "amt" to fmtAmount(got.asset, got.amount)))
        } else {
            onError(r.message)
        }
        busy = false
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = rdp(6)).clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(horizontal = rdp(14), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (c.amount > 0L) {
                XaniText("${copy.t(Copy.RewardsClaimable)} · ${fmtAmount(c.asset, c.amount)}", Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
            }
            if (c.waitingAmount > 0L) {
                val opens = if (c.nextOpensAtMs > 0L) " · " + copy.t(Copy.RewardsClaimOpens, "t" to fmtDayTime(c.nextOpensAtMs, zone, timeFmt)) else ""
                XaniText(copy.t(Copy.RewardsClaimWaiting, "amt" to fmtAmount(c.asset, c.waitingAmount)) + opens, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
            }
        }
        if (c.amount > 0L) {
            Box(
                Modifier.clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Text)
                    .pressable(enabled = !busy) {
                        if (idem.isEmpty()) idem = UUID.randomUUID().toString().replace("-", "")
                        attempt++
                    }
                    .padding(horizontal = rdp(14), vertical = rdp(8)),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) Spinner(rdp(14), Color.White)
                else XaniText(copy.t(Copy.RewardsClaim, "amt" to fmtAmount(c.asset, c.amount)), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
        }
    }
}

@Composable
private fun DayHeader(copy: Copy, d: DayCard, label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = rdp(10), bottom = rdp(6), start = rdp(4), end = rdp(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.weight(1f))
        if (d.liveMs > 0L) {
            XaniText("${copy.t(Copy.RewardsLiveTime)} ${fmtClock(d.liveMs, seconds = true)}", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
        }
        if (d.totals.isNotEmpty()) {
            Spacer(Modifier.width(rdp(8)))
            XaniText("+${fmtAmounts(d.totals)}", Type.LabelBold, color = Color(0xFFD97706), fontSizeOverride = 12f)
        }
    }
}

@Composable
private fun RewardRow(copy: Copy, r: RewardLedger, zone: ZoneId, timeFmt: DateTimeFormatter, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = rdp(6)).clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2)
            .pressable(onClick = onClick).padding(horizontal = rdp(14), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(rdp(36)).clip(RoundedCornerShape(rdp(11))).background(Color(0x14F59E0B)),
            contentAlignment = Alignment.Center,
        ) { AssetGlyph(r.asset) }
        Spacer(Modifier.width(rdp(10)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(
                    (if (r.amount >= 0L) "+" else "") + fmtAmount(r.asset, r.amount),
                    Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f,
                )
                Spacer(Modifier.width(rdp(8)))
                RewardStatusPill(copy, r.status)
            }
            val line = buildString {
                if (r.kind == "earn") {
                    append(copy.t(Copy.RewardsCompletedAt)); append(' '); append(fmtTime(r.reachedAtMs, zone, timeFmt))
                    if (r.issuedAtMs > 0L) {
                        append(" · "); append(copy.t(Copy.RewardsAddedAt)); append(' '); append(fmtTime(r.issuedAtMs, zone, timeFmt))
                    }
                } else {
                    append(r.message.title.ifBlank { r.kind })
                    if (r.issuedAtMs > 0L) { append(" · "); append(fmtTime(r.issuedAtMs, zone, timeFmt)) }
                }
            }
            XaniText(line, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (r.campaign.isNotEmpty()) add(r.campaign)
                if (r.kind == "earn") add(fmtClock(r.requiredMs))
                if (r.role == "cohost") add(copy.t(Copy.MyLiveCohost))
                if (r.balanceBefore != null && r.balanceAfter != null) add("${r.balanceBefore} → ${r.balanceAfter}")
            }
            if (sub.isNotEmpty()) {
                XaniText(sub.joinToString(" · "), Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 10.5f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(rdp(6)))
        ProfileIco.Chevron(Color(0xFFBBBBBB), rdp(15))
    }
}

@Composable
private fun AssetGlyph(asset: String) {
    val tint = when (asset) {
        "diamonds" -> Color(0xFF3B82F6)
        "beans" -> Color(0xFF16A34A)
        else -> Color(0xFFD97706)
    }
    when (asset) {
        "diamonds" -> ProfileIco.Path("M6 3h12l4 6-10 12L2 9l4-6zm2.5 2L6.2 8.5h11.6L15.5 5h-7zM4.9 10.5 12 19l7.1-8.5H4.9z", tint)
        "beans" -> ProfileIco.Path("M7 4c4 0 8 3 8 8s-3 8-7 8-6-3-6-7 2-9 5-9zm9 1c2 0 4 2 4 5s-2 7-5 7c1-2 1-5 0-8s-1-3 1-4z", tint)
        else -> Box(Modifier.size(rdp(16)).clip(CircleShape).background(tint).padding(rdp(3))) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFFCD34D)))
        }
    }
}

@Composable
internal fun RewardStatusPill(copy: Copy, status: String) {
    val (bg, fg, label) = when (status) {
        "added" -> Triple(Color(0x1416A34A), Color(0xFF15803D), Copy.RewardsStatusAdded)
        "claimed" -> Triple(Color(0x1416A34A), Color(0xFF15803D), Copy.RewardsStatusClaimed)
        "claimable" -> Triple(Color(0x143B82F6), Color(0xFF1D4ED8), Copy.RewardsStatusClaimable)
        "pending" -> Triple(Color(0x14F59E0B), Color(0xFFB45309), Copy.RewardsStatusPending)
        "expired" -> Triple(Color(0x14777777), Color(0xFF666666), Copy.RewardsStatusExpired)
        else -> Triple(Color(0x14777777), Color(0xFF666666), Copy.RewardsStatusCancelled)
    }
    Box(Modifier.clip(RoundedCornerShape(rdp(8))).background(bg).padding(horizontal = rdp(7), vertical = rdp(2))) {
        XaniText(copy.t(label), Type.Micro, color = fg, fontSizeOverride = 10f)
    }
}

/**
 * One reward in full. Reward day, completed-at, added-at, the balance move
 * and the timeline, every time in the business timezone so the host and
 * support read the same clock.
 */
@Composable
private fun RewardDetailSheet(copy: Copy, id: Long, zone: ZoneId, onClose: () -> Unit) {
    var detail by remember(id) { mutableStateOf<RewardDetail?>(null) }
    var error by remember(id) { mutableStateOf("") }
    LaunchedEffect(id) {
        val r = Xani.rewards.detail(id)
        detail = r.value
        if (r.value == null) error = r.message
    }
    BackHandler(onBack = onClose)
    val locale = remember { Locale.getDefault() }
    val fullFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm:ss", locale) }
    Box(Modifier.fillMaxSize().background(ProfileTokens.SheetScrim).pressable(pressedScale = 1f, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(22), topEnd = rdp(22))).background(ProfileTokens.Bg)
                .pressable(pressedScale = 1f, onClick = {})
                .navigationBarsPadding().padding(horizontal = rdp(18), vertical = rdp(16))
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(copy.t(Copy.RewardsDetail), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 17f, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(rdp(30)).clip(CircleShape).background(ProfileTokens.Bg2).pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Close(ProfileTokens.Text, rdp(14)) }
            }
            val d = detail
            when {
                d == null && error.isEmpty() -> Box(Modifier.fillMaxWidth().padding(rdp(24)), contentAlignment = Alignment.Center) { Spinner(rdp(18), ProfileTokens.Accent) }
                d == null -> XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(vertical = rdp(12)))
                else -> {
                    val r = d.reward
                    val z = runCatching { ZoneId.of(d.calendar.tz) }.getOrDefault(zone)
                    Spacer(Modifier.height(rdp(10)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XaniText((if (r.amount >= 0L) "+" else "") + fmtAmount(r.asset, r.amount), Type.H1, color = ProfileTokens.Text, fontSizeOverride = 30f)
                        Spacer(Modifier.width(rdp(10)))
                        RewardStatusPill(copy, r.status)
                    }
                    if (r.message.body.isNotEmpty()) {
                        Spacer(Modifier.height(rdp(4)))
                        XaniText(r.message.body, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f)
                    }
                    Spacer(Modifier.height(rdp(14)))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(14))).background(ProfileTokens.Bg2).padding(rdp(12))) {
                        Fact(copy.t(Copy.RewardsDay), prettyDay(r.day, locale))
                        if (r.campaign.isNotEmpty()) Fact(copy.t(Copy.RewardsHistory), r.campaign)
                        if (r.kind == "earn") {
                            Fact(copy.t(Copy.RewardsRequired), fmtClock(r.requiredMs, seconds = true))
                            Fact(copy.t(Copy.RewardsVerified), fmtClock(r.verifiedMs, seconds = true))
                            Fact(copy.t(Copy.RewardsCompletedAt), fmtTime(r.reachedAtMs, z, fullFmt))
                        }
                        if (r.issuedAtMs > 0L) Fact(copy.t(Copy.RewardsAddedAt), fmtTime(r.issuedAtMs, z, fullFmt))
                        if (r.claimedAtMs > 0L) Fact(copy.t(Copy.RewardsStatusClaimed), fmtTime(r.claimedAtMs, z, fullFmt))
                        if (r.balanceBefore != null && r.balanceAfter != null) Fact(copy.t(Copy.RewardsBalance), "${r.balanceBefore} → ${r.balanceAfter}")
                        Fact(copy.t(Copy.RewardsRole), if (r.role == "cohost") copy.t(Copy.MyLiveCohost) else "Host")
                        if (r.note.isNotEmpty()) Fact("·", r.note)
                    }
                    if (d.timeline.isNotEmpty()) {
                        Spacer(Modifier.height(rdp(14)))
                        XaniText(copy.t(Copy.RewardsTimeline), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
                        Spacer(Modifier.height(rdp(6)))
                        d.timeline.forEach { s ->
                            Row(Modifier.padding(vertical = rdp(5)), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = rdp(5)).size(rdp(7)).clip(CircleShape).background(Color(0xFFD97706)))
                                Spacer(Modifier.width(rdp(10)))
                                Column {
                                    XaniText(s.what, Type.Label, color = ProfileTokens.Text, fontSizeOverride = 13f)
                                    XaniText(s.local.ifBlank { fmtTime(s.atMs, z, fullFmt) }, Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 11f)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(rdp(10)))
                    XaniText(
                        copy.t(Copy.RewardsDayNote, "start" to d.calendar.dayStart, "tz" to d.calendar.tz),
                        Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 10.5f,
                    )
                }
            }
            Spacer(Modifier.height(rdp(8)))
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = rdp(4)), verticalAlignment = Alignment.CenterVertically) {
        XaniText(label, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.width(rdp(110)))
        XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12.5f, modifier = Modifier.weight(1f))
    }
}

private fun dayLabel(day: String, serverLabel: String, today: String, copy: Copy, fmt: DateTimeFormatter): String {
    if (day == today) return copy.t(Copy.RewardsToday)
    return try {
        LocalDate.parse(day).format(fmt)
    } catch (_: Exception) {
        serverLabel.ifBlank { day }
    }
}

private fun prettyDay(day: String, locale: Locale): String = try {
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale))
} catch (_: Exception) {
    day
}

private fun fmtTime(ms: Long, zone: ZoneId, fmt: DateTimeFormatter): String =
    if (ms <= 0L) "—" else fmt.format(Instant.ofEpochMilli(ms).atZone(zone))

private fun fmtDayTime(ms: Long, zone: ZoneId, timeFmt: DateTimeFormatter): String {
    if (ms <= 0L) return "—"
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    return t.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM")) + " " + timeFmt.format(t)
}

/** "100 coins" / "5 diamonds". */
internal fun fmtAmount(asset: String, amount: Long): String = "$amount $asset"

/** "100 coins · 5 diamonds", in the server's asset order. */
internal fun fmtAmounts(m: RewardAmounts): String = m.entries.joinToString(" · ") { fmtAmount(it.key, it.value) }
