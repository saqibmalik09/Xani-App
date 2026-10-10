package app.xani.feature.gift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xani.core.isolate.runFeature
import app.xani.core.media.RemoteImage
import app.xani.core.net.GiftingStatementData
import app.xani.core.net.GiftingTotals
import app.xani.core.net.LiveFeed
import app.xani.core.net.LivePersonData
import app.xani.core.net.Xani
import app.xani.feature.call.LiveSession
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.LocalLiveSheetDrag
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A person's gifting, as a tab on the host / user card and on profiles: what
 * they received (and, for yourself, sent) this live, today, this week, this
 * month or between two dates — top counterparts or the latest gifts, each with
 * the sender, the gift and the amount.
 *
 * Reads are one projection call per filter; the server sums day rows. Realtime
 * comes from the frames already on this device: a room gift frame naming this
 * person moves the totals at once and a short debounce re-reads the lists;
 * your own per-user frame does the same off the live socket.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun GiftingPanel(
    userId: Long,
    copy: Copy,
    /** The room this card was opened in; > 0 adds and selects "This LIVE". */
    roomId: Long = 0L,
    modifier: Modifier = Modifier,
    /** Dark (over a broadcast) or light (profile) palette. */
    dark: Boolean = true,
) {
    val me = userId == ProfileStore.currentUserId
    val ink = if (dark) Color.White else Color(0xFF1E0A50)
    val sub = ink.copy(alpha = if (dark) 0.55f else 0.6f)
    val chipBg = if (dark) Color.White.copy(alpha = 0.07f) else Color(0x126D28D9)
    val line = if (dark) Color.White.copy(alpha = 0.08f) else Color(0x146D28D9)

    var period by remember(userId, roomId) { mutableStateOf(if (roomId > 0L) "live" else "today") }
    var sort by remember(userId) { mutableStateOf("top") }
    var received by remember(userId) { mutableStateOf(true) }
    val today = remember { LocalDate.now(ZoneOffset.UTC) }
    var fromText by remember { mutableStateOf(today.minusDays(6).format(IsoDay)) }
    var toText by remember { mutableStateOf(today.format(IsoDay)) }
    var range by remember { mutableStateOf(today.minusDays(6) to today) }
    var statement by remember(userId) { mutableStateOf<GiftingStatementData?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember(userId) { mutableStateOf("") }
    var isPrivate by remember(userId) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(userId, roomId, period, sort, received, range, reload) {
        loading = statement == null
        val r = withContext(Dispatchers.IO) {
            runFeature(null) {
                val from = if (period == "range") range.first.format(IsoDay) else ""
                val to = if (period == "range") range.second.format(IsoDay) else ""
                if (received) Xani.gifting.received(userId, period, from, to, roomId, sort)
                else Xani.gifting.sent(userId, period, from, to, roomId, sort)
            }
        }
        val got = r?.value
        when {
            got != null -> { statement = got; error = ""; isPrivate = false }
            r?.code == "gifting_private" -> { isPrivate = true; error = "" }
            else -> error = r?.message?.ifBlank { copy.t(Copy.GiftingLoadFailed) } ?: copy.t(Copy.GiftingLoadFailed)
        }
        loading = false
    }

    // Realtime: a frame that names this person bumps the totals now and
    // schedules one re-read for the lists; bursts collapse into one read.
    LaunchedEffect(userId, received) {
        val room = LiveSession.gifts
            .filter { it.id != 0L && (if (received) it.receiver.id == userId else it.sender.id == userId) }
            .map { ev ->
                statement?.let { s ->
                    val t = s.totals
                    statement = s.copy(
                        totals = GiftingTotals(
                            coins = t.coins + ev.totalCoins,
                            value = t.value,
                            diamonds = if (received && ev.receiverTodayDiamonds > 0L && period == "today") ev.receiverTodayDiamonds else t.diamonds,
                            cashbackCoins = t.cashbackCoins + ev.cashbackCoins,
                            count = t.count + 1,
                        ),
                    )
                }
                Unit
            }
        val mine = LiveFeed.gifting.filter { me && it.received == received }.map { }
        launch { merge(room, mine).debounce(600L).collect { reload++ } }
        if (me) launch { LiveFeed.hold() }
    }

    Column(modifier.fillMaxWidth()) {
        // Period chips
        LazyRow(
            Modifier.fillMaxWidth().then(LocalLiveSheetDrag.current),
            contentPadding = PaddingValues(horizontal = rdp(16)),
            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            val chips = buildList {
                if (roomId > 0L) add("live" to copy.t(Copy.GiftingThisLive))
                add("today" to copy.t(Copy.GiftingToday))
                add("week" to copy.t(Copy.GiftingWeek))
                add("month" to copy.t(Copy.GiftingMonth))
                add("range" to copy.t(Copy.GiftingDates))
            }
            items(chips, key = { it.first }) { (k, label) ->
                Chip(label, period == k, ink, chipBg) { period = k }
            }
        }
        if (period == "range") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                DateBox(fromText, { fromText = it }, copy.t(Copy.GiftRecordFrom), ink, chipBg, Modifier.weight(1f))
                DateBox(toText, { toText = it }, copy.t(Copy.GiftRecordTo), ink, chipBg, Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                        .pressable(
                            onClick = {
                                val a = parseDay(fromText)
                                val b = parseDay(toText)
                                if (a == null || b == null) error = copy.t(Copy.GiftRecordBadDate)
                                else { error = ""; range = if (b.isBefore(a)) b to a else a to b }
                            },
                        )
                        .padding(horizontal = rdp(14), vertical = rdp(10)),
                ) { XaniText(copy.t(Copy.GiftingShow), Type.LabelBold, color = Color.White, fontSizeOverride = 12f) }
            }
        }

        // Direction (yourself only) · sort
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            if (me) {
                Toggle(copy.t(Copy.GiftingReceived), received, ink, chipBg) { received = true }
                Toggle(copy.t(Copy.GiftingSent), !received, ink, chipBg) { received = false }
                Spacer(Modifier.weight(1f))
            }
            Toggle(if (received) copy.t(Copy.GiftingTopSenders) else copy.t(Copy.GiftingTopReceivers), sort == "top", ink, chipBg) { sort = "top" }
            Toggle(copy.t(Copy.GiftingLatest), sort == "latest", ink, chipBg) { sort = "latest" }
        }

        // Totals
        val s = statement
        if (s != null && !isPrivate) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(6)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                if (received) {
                    // Received shows only what was credited after the split —
                    // the senders' coin spend next to it reads like a second,
                    // bigger "earning" and confuses every host.
                    XaniText("💎 " + formatCoins(s.totals.diamonds), Type.LabelBold, color = ink, fontSizeOverride = 16f)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(4))) {
                        CoinGlyph(rdp(11))
                        XaniText(formatCoins(s.totals.coins), Type.LabelBold, color = ink, fontSizeOverride = 16f)
                    }
                }
                XaniText(copy.t(Copy.GiftingGiftsN, "n" to formatCoins(s.totals.count)), Type.Caption, color = sub, fontSizeOverride = 12f)
                if (!received && s.totals.cashbackCoins > 0L) {
                    XaniText(copy.t(Copy.GiftingCashback, "n" to formatCoins(s.totals.cashbackCoins)), Type.Caption, color = Color(0xFF34D399), fontSizeOverride = 12f)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = rdp(16)).height(1.dp).background(line))

        // List — tall enough that a record is readable without a fight with
        // the sheet's drag; the sheet grows with it.
        Box(Modifier.fillMaxWidth().height(rdp(380))) {
            when {
                isPrivate -> Centered(copy.t(Copy.GiftingPrivate), sub)
                error.isNotEmpty() && s == null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    XaniText(error, Type.Caption, color = sub, fontSizeOverride = 13f, align = TextAlign.Center)
                    Spacer(Modifier.height(rdp(8)))
                    Chip(copy.t(Copy.GiftingRetry), true, ink, chipBg) { reload++ }
                }
                loading && s == null -> Centered("…", sub)
                s == null || (if (sort == "top") s.peers.isEmpty() else s.gifts.isEmpty()) -> Centered(copy.t(Copy.GiftingEmpty), sub)
                sort == "top" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = rdp(6))) {
                    items(s.peers, key = { it.peer.id }) { p ->
                        PersonLine(
                            person = p.peer,
                            rank = p.rank,
                            headline = if (received) "💎 " + formatCoins(p.diamonds) else formatCoins(p.coins),
                            caption = copy.t(Copy.GiftingGiftsN, "n" to formatCoins(p.count)) + " · " + ago(p.lastAtMs, copy),
                            giftUrl = p.listingUrl,
                            ink = ink, sub = sub, line = line,
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = rdp(6))) {
                    items(s.gifts, key = { it.id }) { g ->
                        val who = if (received) g.sender else g.receiver
                        PersonLine(
                            person = who,
                            rank = 0,
                            headline = (if (received) "💎 " + formatCoins(g.diamonds) else formatCoins(g.coins)),
                            caption = g.giftTitle + (if (g.count > 1) " ×${g.count}" else "") +
                                (if (g.roomId != 0L) " · " + copy.t(Copy.GiftingLive) else "") + " · " + ago(g.atMs, copy),
                            giftUrl = g.listingUrl,
                            ink = ink, sub = sub, line = line,
                        )
                    }
                }
            }
        }
    }
}

private val IsoDay: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun parseDay(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), IsoDay)
} catch (_: DateTimeParseException) {
    null
}

/** "just now" · "5m" · "3h" · "2d" from an epoch-ms stamp. */
internal fun ago(atMs: Long, copy: Copy): String {
    if (atMs <= 0L) return ""
    val d = ((System.currentTimeMillis() - atMs).coerceAtLeast(0L)) / 1000L
    return when {
        d < 60 -> copy.t(Copy.InboxJustNow)
        d < 3600 -> "${d / 60}m"
        d < 86_400 -> "${d / 3600}h"
        else -> "${d / 86_400}d"
    }
}

@Composable
private fun Chip(label: String, on: Boolean, ink: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(14)))
            .then(
                if (on) Modifier.background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                else Modifier.background(bg),
            )
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = rdp(12), vertical = rdp(7)),
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else ink.copy(alpha = 0.8f), fontSizeOverride = 12f, maxLines = 1)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, ink: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(10)))
            .background(if (on) bg else Color.Transparent)
            .then(if (on) Modifier.border(1.dp, ink.copy(alpha = 0.18f), RoundedCornerShape(rdp(10))) else Modifier)
            .pressable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = rdp(10), vertical = rdp(5)),
    ) {
        XaniText(label, Type.Caption, color = if (on) ink else ink.copy(alpha = 0.5f), fontSizeOverride = 11f, maxLines = 1)
    }
}

@Composable
private fun DateBox(value: String, onChange: (String) -> Unit, hint: String, ink: Color, bg: Color, modifier: Modifier) {
    Box(
        modifier
            .height(rdp(38))
            .clip(RoundedCornerShape(rdp(12)))
            .background(bg)
            .padding(horizontal = rdp(12)),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) XaniText(hint, Type.Caption, color = ink.copy(alpha = 0.4f), fontSizeOverride = 12f)
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '-' }.take(10)) },
            singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(Color(0xFF8B5CF6)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PersonLine(
    person: LivePersonData,
    rank: Int,
    headline: String,
    caption: String,
    giftUrl: String,
    ink: Color,
    sub: Color,
    line: Color,
) {
    val name = person.fullName.ifBlank { person.username }.ifBlank { "…" }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.99f, onClick = { if (person.id != 0L) PersonRouter.open(person.id) })
            .padding(horizontal = rdp(16), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank > 0) {
            XaniText(
                "$rank", Type.LabelBold,
                color = when (rank) { 1 -> Color(0xFFFDE68A); 2 -> Color(0xFFCBD5E1); 3 -> Color(0xFFF59E0B); else -> sub },
                fontSizeOverride = 13f,
                modifier = Modifier.width(rdp(20)),
            )
        }
        PersonAvatar(person.id, name, person.avatarUrl, rdp(38), frameUrl = person.frameUrl, frameMime = person.frameMime)
        Spacer(Modifier.width(rdp(10)))
        Column(Modifier.weight(1f)) {
            XaniText(name, Type.LabelBold, color = ink, fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            XaniText(caption, Type.Caption, color = sub, fontSizeOverride = 11f, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (giftUrl.isNotEmpty()) {
            RemoteImage(
                url = giftImageUrl(giftUrl),
                modifier = Modifier.size(rdp(28)),
                contentScale = ContentScale.Fit,
                maxEdge = 120,
                fallback = Color.Transparent,
            )
            Spacer(Modifier.width(rdp(8)))
        }
        XaniText(headline, Type.LabelBold, color = ink, fontSizeOverride = 13f, maxLines = 1)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(16)).height(1.dp).background(line))
}

@Composable
private fun Centered(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        XaniText(text, Type.Caption, color = color, fontSizeOverride = 13f, align = TextAlign.Center, modifier = Modifier.padding(horizontal = rdp(24)))
    }
}

/**
 * Two-tab header for the host / user cards: Profile · Gifting. Drawn as a
 * segmented pill so the card keeps its shape and the tab is one tap.
 */
@Composable
internal fun CardTabs(selected: Int, copy: Copy, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(rdp(14)))
            .padding(rdp(3)),
    ) {
        listOf(copy.t(Copy.GiftingTabProfile), copy.t(Copy.GiftingTabGifting)).forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(11)))
                    .then(
                        if (on) Modifier.background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                        else Modifier,
                    )
                    .pressable(pressedScale = 0.96f, onClick = { onSelect(i) })
                    .padding(horizontal = rdp(18), vertical = rdp(7)),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(label, Type.LabelBold, color = if (on) Color.White else Color.White.copy(alpha = 0.6f), fontSizeOverride = 12f)
            }
        }
    }
}

/** Avatar ring shared by the cards' Profile tab. */
@Composable
internal fun CardAvatar(id: Long, name: String, url: String, frameUrl: String? = null, frameMime: String = "") {
    Box(
        Modifier
            .border(rdp(2), Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), CircleShape)
            .padding(rdp(3)),
    ) {
        PersonAvatar(id, name, url, rdp(72), frameUrl = frameUrl, frameMime = frameMime)
    }
}
