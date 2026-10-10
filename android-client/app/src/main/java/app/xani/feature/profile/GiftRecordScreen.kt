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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.net.GiftDayData
import app.xani.core.net.GiftStatsData
import app.xani.core.net.LevelData
import app.xani.core.net.Xani
import app.xani.feature.gift.GiftingPanel
import app.xani.feature.social.LevelMark
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Preset windows the record screen offers; Custom takes two typed dates. */
private enum class RecordRange { Today, Week, Month, Custom }

private val IsoDay: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * The owner's gifting record: lifetime standing on both ladders (picture,
 * level, progress to the next rung) and coins sent / received for today,
 * this week, this month, or any date range — with a per-day breakdown. One
 * read per range; the server sums day rows, so nothing here loops a ledger.
 */
@Composable
internal fun GiftRecordScreen(copy: Copy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var range by rememberSaveable { mutableStateOf(RecordRange.Month) }
    val today = remember { LocalDate.now(ZoneOffset.UTC) }
    var fromText by rememberSaveable { mutableStateOf(today.minusDays(6).format(IsoDay)) }
    var toText by rememberSaveable { mutableStateOf(today.format(IsoDay)) }
    // The window actually queried; Custom only moves it on "Show".
    var window by remember { mutableStateOf(presetWindow(RecordRange.Month, today)) }
    var stats by remember { mutableStateOf<GiftStatsData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(window) {
        loading = true
        val r = Xani.levels.myStats(window.first.format(IsoDay), window.second.format(IsoDay))
        val got = r.value
        if (got != null) {
            stats = got
            error = ""
        } else {
            error = r.message
        }
        loading = false
    }

    fun pick(next: RecordRange) {
        range = next
        if (next != RecordRange.Custom) window = presetWindow(next, today)
    }

    fun applyCustom() {
        val start = parseDay(fromText)
        val end = parseDay(toText)
        if (start == null || end == null) {
            error = copy.t(Copy.GiftRecordBadDate)
            return
        }
        window = if (end.isBefore(start)) Pair(end, start) else Pair(start, end)
    }

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(rdp(32))
                        .clip(CircleShape)
                        .background(ProfileTokens.Bg2)
                        .pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                XaniText(copy.t(Copy.GiftRecordTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f)
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
                item(key = "levels") {
                    val me = stats?.me
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = rdp(16)),
                        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                    ) {
                        LevelCard(
                            copy, Modifier.weight(1f),
                            title = copy.t(Copy.GiftRecordSenderLevel),
                            level = me?.senderLevel, coins = me?.sentCoins ?: 0L, nextAt = me?.senderNext ?: 0L,
                        )
                        LevelCard(
                            copy, Modifier.weight(1f),
                            title = copy.t(Copy.GiftRecordReceiverLevel),
                            level = me?.receiverLevel, coins = me?.receivedCoins ?: 0L, nextAt = me?.receiverNext ?: 0L,
                        )
                    }
                }

                item(key = "ranges") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(14)),
                        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                    ) {
                        RangeChip(copy.t(Copy.GiftRecordToday), range == RecordRange.Today) { pick(RecordRange.Today) }
                        RangeChip(copy.t(Copy.GiftRecordWeek), range == RecordRange.Week) { pick(RecordRange.Week) }
                        RangeChip(copy.t(Copy.GiftRecordMonth), range == RecordRange.Month) { pick(RecordRange.Month) }
                        RangeChip(copy.t(Copy.GiftRecordCustom), range == RecordRange.Custom) { pick(RecordRange.Custom) }
                    }
                }

                if (range == RecordRange.Custom) {
                    item(key = "custom") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = rdp(16)).padding(bottom = rdp(12)),
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
                }

                item(key = "totals") {
                    val s = stats
                    Column(Modifier.fillMaxWidth().padding(horizontal = rdp(16))) {
                        XaniText(
                            copy.t(Copy.GiftRecordRange, "from" to window.first.format(IsoDay), "to" to window.second.format(IsoDay)),
                            Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                        )
                        Spacer(Modifier.height(rdp(8)))
                        Row(horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                            TotalCard(
                                Modifier.weight(1f), copy.t(Copy.GiftRecordSent),
                                s?.rangeSentCoins ?: 0L, s?.rangeSentCount ?: 0L, copy, Color(0xFF7C3AED),
                            )
                            TotalCard(
                                Modifier.weight(1f), copy.t(Copy.GiftRecordReceived),
                                s?.rangeReceivedCoins ?: 0L, s?.rangeReceivedCount ?: 0L, copy, Color(0xFFEC4899),
                            )
                        }
                        if (error.isNotEmpty()) {
                            XaniText(error, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(8)))
                        }
                    }
                }

                // Who sent what (and who you sent to): the engine's statement,
                // top counterparts or latest gifts, live as frames land.
                item(key = "people") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rdp(16))
                            .padding(top = rdp(14))
                            .clip(RoundedCornerShape(rdp(16)))
                            .background(ProfileTokens.Bg2)
                            .padding(vertical = rdp(10)),
                    ) {
                        GiftingPanel(userId = Xani.session.currentUserId, copy = copy, dark = false)
                    }
                }

                item(key = "by-day-head") {
                    XaniText(
                        copy.t(Copy.GiftRecordByDay), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f,
                        modifier = Modifier.padding(horizontal = rdp(20)).padding(top = rdp(18), bottom = rdp(6)),
                    )
                }
                val days = stats?.days.orEmpty().asReversed()
                if (days.isEmpty()) {
                    item(key = "by-day-empty") {
                        if (!loading) {
                            XaniText(
                                copy.t(Copy.GiftRecordEmpty), Type.Caption, color = ProfileTokens.Muted,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = rdp(16)),
                            )
                        }
                    }
                } else {
                    items(days, key = { it.day }) { d -> DayRow(d, copy) }
                }
            }
        }
    }
}

/** A preset's [from, to] in UTC days; the week starts on Monday. */
private fun presetWindow(range: RecordRange, today: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
    RecordRange.Today -> today to today
    RecordRange.Week -> today.with(DayOfWeek.MONDAY) to today
    RecordRange.Month -> today.withDayOfMonth(1) to today
    RecordRange.Custom -> today.minusDays(6) to today
}

private fun parseDay(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), IsoDay)
} catch (_: DateTimeParseException) {
    null
}

@Composable
private fun LevelCard(copy: Copy, modifier: Modifier, title: String, level: LevelData?, coins: Long, nextAt: Long) {
    val base = level?.thresholdCoins ?: 0L
    val span = if (nextAt > base) nextAt - base else 0L
    val progress = when {
        span > 0L -> ((coins - base).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        level != null -> 1f
        else -> 0f
    }
    Column(
        modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.Bg2)
            .padding(rdp(14)),
    ) {
        XaniText(title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
        Spacer(Modifier.height(rdp(8)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
            LevelMark(level, rdp(28))
            XaniText(
                if (level == null) copy.t(Copy.GiftRecordNoLevel) else copy.t(Copy.GiftRecordLevelN, "n" to level.slug),
                Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1,
            )
        }
        Spacer(Modifier.height(rdp(10)))
        Box(Modifier.fillMaxWidth().height(rdp(5)).clip(CircleShape).background(ProfileTokens.Bg3)) {
            Box(Modifier.fillMaxWidth(progress).height(rdp(5)).clip(CircleShape).background(ProfileTokens.Accent))
        }
        Spacer(Modifier.height(rdp(6)))
        XaniText(
            when {
                nextAt > 0L -> copy.t(Copy.GiftRecordToNext, "n" to compact((nextAt - coins).coerceAtLeast(0L)))
                level != null -> copy.t(Copy.GiftRecordTop)
                else -> compact(coins)
            },
            Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f, maxLines = 1,
        )
    }
}

@Composable
private fun RangeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(14)))
            .background(if (active) ProfileTokens.Accent else ProfileTokens.Bg2)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(12), vertical = rdp(8)),
    ) {
        XaniText(
            label, Type.LabelBold,
            color = if (active) Color.White else ProfileTokens.Text,
            fontSizeOverride = 12f, maxLines = 1,
        )
    }
}

@Composable
private fun TotalCard(modifier: Modifier, title: String, coins: Long, count: Long, copy: Copy, tint: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(tint.copy(alpha = 0.08f))
            .padding(rdp(14)),
    ) {
        XaniText(title, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
        Spacer(Modifier.height(rdp(4)))
        XaniText("🪙 ${compact(coins)}", Type.H2, color = tint, fontSizeOverride = 20f, maxLines = 1)
        XaniText(copy.t(Copy.GiftRecordGifts, "n" to compact(count)), Type.Micro, color = ProfileTokens.Sub, fontSizeOverride = 10f)
    }
}

@Composable
private fun DayRow(d: GiftDayData, copy: Copy) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(20), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(d.day, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            XaniText(
                "${copy.t(Copy.GiftRecordSent)} 🪙 ${compact(d.sentCoins)}",
                Type.Caption, color = Color(0xFF7C3AED), fontSizeOverride = 12f,
            )
            XaniText(
                "${copy.t(Copy.GiftRecordReceived)} 🪙 ${compact(d.receivedCoins)}",
                Type.Caption, color = Color(0xFFEC4899), fontSizeOverride = 12f,
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = rdp(20)).height(rdp(1)).background(ProfileTokens.RowLine))
}
