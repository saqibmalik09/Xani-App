package app.xani.feature.profile

import android.os.SystemClock
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.LiveTimeCalendar
import app.xani.core.net.LiveTimeDay
import app.xani.core.net.LiveTimeNow
import app.xani.core.net.LiveTimeSession
import app.xani.core.net.Xani
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

/**
 * The host's own record: today in hours / minutes / seconds, this week, this
 * month, all time, then every day with time on it and the sessions inside a
 * day. Every figure is the server's verified number — the same row support
 * reads in the dashboard — so what the host sees here is what they can
 * claim. While live, today advances on the monotonic clock between refreshes
 * and freezes if the server cannot be reached, exactly as the HUD does.
 */
@Composable
internal fun MyLiveScreen(copy: Copy, onBack: () -> Unit, onRewards: () -> Unit = {}) {
    var now by remember { mutableStateOf<LiveTimeNow?>(null) }
    var calendar by remember { mutableStateOf(LiveTimeCalendar.Empty) }
    // When the last `now` landed (monotonic), so today can tick between fetches.
    var nowAtMs by remember { mutableLongStateOf(0L) }
    var days by remember { mutableStateOf<List<LiveTimeDay>>(emptyList()) }
    var oldest by remember { mutableStateOf("") }
    var moreDays by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(0) }
    // One day open at a time; its sessions, or null while loading.
    var openDay by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf<Map<String, List<LiveTimeSession>>>(emptyMap()) }
    var sessionsBusy by remember { mutableStateOf("") }

    LaunchedEffect(key) {
        loading = true
        val r = Xani.liveTime.days()
        val got = r.value
        if (got != null) {
            now = got.now
            nowAtMs = SystemClock.elapsedRealtime()
            calendar = got.calendar
            days = got.days
            oldest = got.from
            moreDays = true
            error = ""
        } else {
            error = r.message
        }
        loading = false
    }

    // Live right now: today's number is moving on the server, so refresh it
    // every few seconds while this screen is open. The local tick covers the
    // seconds in between; a refresh that fails freezes the figure rather than
    // letting it drift past what the server will credit.
    val live = now?.live == true
    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val r = Xani.liveTime.me()
            val got = r.value ?: continue
            now = got.now
            nowAtMs = SystemClock.elapsedRealtime()
            calendar = got.calendar
            if (!got.now.live) break
        }
    }
    // The ticking today figure, derived once a second from the last server number.
    var todayMs by remember { mutableLongStateOf(0L) }
    var counting by remember { mutableStateOf(false) }
    LaunchedEffect(now, nowAtMs) {
        val n = now ?: return@LaunchedEffect
        while (true) {
            val since = (SystemClock.elapsedRealtime() - nowAtMs).coerceAtLeast(0L)
            // Past the freeze window with no fresh answer the server is no
            // longer crediting either (allowance ≤ 40 s); stop advancing.
            val moving = n.live && n.counting && since <= FREEZE_MS
            counting = moving
            todayMs = n.todayMs + if (n.live && n.counting) minOf(since, FREEZE_MS) else 0L
            if (!moving) break
            delay(1000L - (todayMs % 1000L))
        }
    }
    BackHandler(onBack = onBack)

    // "Earlier days": the 31-day window before the oldest one loaded. A
    // counter drives one coroutine so the fetch never lives in a click lambda.
    var moreKey by remember { mutableStateOf(0) }
    LaunchedEffect(moreKey) {
        if (moreKey == 0 || oldest.isEmpty()) return@LaunchedEffect
        loadingMore = true
        val to = LocalDate.parse(oldest).minusDays(1)
        val from = to.minusDays(30)
        val r = Xani.liveTime.days(from = from.toString(), to = to.toString())
        val got = r.value
        if (got != null) {
            days = days + got.days.filter { d -> days.none { it.day == d.day } }
            oldest = got.from
            // Past a year back with an empty window: nothing older is worth a tap.
            val todayDate = runCatching { LocalDate.parse(calendar.today) }.getOrDefault(to)
            if (got.days.isEmpty() && from.isBefore(todayDate.minusDays(365))) moreDays = false
        }
        loadingMore = false
    }

    val locale = remember { Locale.getDefault() }
    val dayFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    val timeFmt = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val zone = remember { ZoneId.systemDefault() }

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
                XaniText(copy.t(Copy.MyLiveTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, modifier = Modifier.weight(1f))
                if (loading) Spinner(rdp(18), ProfileTokens.Accent)
                Spacer(Modifier.width(rdp(10)))
                // The rewards this time earned live one tap away: same days, same calendar.
                Box(
                    Modifier.clip(RoundedCornerShape(rdp(14))).background(Color(0x14F59E0B)).pressable(onClick = onRewards)
                        .padding(horizontal = rdp(12), vertical = rdp(6)),
                ) { XaniText(copy.t(Copy.MyLiveRewards), Type.LabelBold, color = Color(0xFFD97706), fontSizeOverride = 12f) }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = rdp(16), end = rdp(16), bottom = rdp(32)),
            ) {
                item(key = "hero") {
                    TodayCard(
                        copy = copy,
                        todayMs = todayMs,
                        live = live,
                        counting = counting,
                        pausedMs = now?.pausedMs ?: 0L,
                    )
                }
                item(key = "totals") {
                    Spacer(Modifier.height(rdp(12)))
                    Row(horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                        TotalTile(copy.t(Copy.MyLiveWeek), fmtClock(now?.weekMs ?: 0L), Modifier.weight(1f))
                        TotalTile(copy.t(Copy.MyLiveMonth), fmtClock(now?.monthMs ?: 0L), Modifier.weight(1f))
                        TotalTile(copy.t(Copy.MyLiveLifetime), fmtHours(now?.lifetimeMs ?: 0L), Modifier.weight(1f))
                    }
                }
                if (error.isNotEmpty()) {
                    item(key = "error") {
                        Spacer(Modifier.height(rdp(12)))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.DangerSoft).padding(rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            XaniText(error.ifBlank { copy.t(Copy.MyLiveLoadFailed) }, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.Bg2).pressable(onClick = { key++ })
                                    .padding(horizontal = rdp(12), vertical = rdp(6)),
                            ) { XaniText(copy.t(Copy.TxnRetry), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 11f) }
                        }
                    }
                }
                item(key = "days-head") {
                    Spacer(Modifier.height(rdp(20)))
                    XaniText(copy.t(Copy.MyLiveDays), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f, modifier = Modifier.padding(start = rdp(4), bottom = rdp(8)))
                }
                if (!loading && days.isEmpty()) {
                    item(key = "days-empty") {
                        XaniText(copy.t(Copy.MyLiveDaysEmpty), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f, modifier = Modifier.padding(start = rdp(4), top = rdp(4)))
                    }
                }
                items(days, key = { it.day }) { d ->
                    val open = openDay == d.day
                    DayRow(
                        copy = copy,
                        day = d,
                        label = dayLabel(d.day, calendar.today, copy, dayFmt),
                        // Today while live shows the ticking figure, not the fetched one.
                        totalMs = if (d.live && d.day == calendar.today) todayMs else d.totalMs,
                        open = open,
                        busy = sessionsBusy == d.day,
                        sessions = if (open) sessions[d.day] else null,
                        timeFmt = timeFmt,
                        zone = zone,
                        onToggle = {
                            openDay = if (open) "" else d.day
                        },
                    )
                    LaunchedEffect(open) {
                        if (!open || sessions.containsKey(d.day)) return@LaunchedEffect
                        sessionsBusy = d.day
                        val r = Xani.liveTime.sessions(d.day)
                        val got = r.value
                        if (got != null) sessions = sessions + (d.day to got.sessions)
                        sessionsBusy = ""
                    }
                }
                if (days.isNotEmpty() && moreDays) {
                    item(key = "more") {
                        Box(
                            Modifier.fillMaxWidth().padding(top = rdp(10)).clip(RoundedCornerShape(rdp(12))).background(ProfileTokens.Bg2)
                                .pressable(enabled = !loadingMore, onClick = { moreKey++ })
                                .padding(vertical = rdp(12)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingMore) Spinner(rdp(16), ProfileTokens.Accent)
                            else XaniText(copy.t(Copy.MyLiveOlder), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f)
                        }
                    }
                }
                item(key = "note") {
                    Spacer(Modifier.height(rdp(16)))
                    XaniText(
                        copy.t(Copy.MyLiveDayNote, "start" to calendar.dayStart, "tz" to calendar.tz),
                        Type.Micro,
                        color = ProfileTokens.Muted,
                        fontSizeOverride = 11f,
                        modifier = Modifier.padding(horizontal = rdp(4)),
                    )
                }
            }
        }
    }
}

/** Freeze window for the local tick when the server stops answering (≥ any allowance). */
private const val FREEZE_MS = 40_000L

@Composable
private fun TodayCard(copy: Copy, todayMs: Long, live: Boolean, counting: Boolean, pausedMs: Long) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(rdp(20)))
            .background(Brush.linearGradient(listOf(ProfileTokens.AccentDeep, ProfileTokens.Accent)))
            .padding(rdp(18)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XaniText(copy.t(Copy.MyLiveToday), Type.Caption, color = Color.White.copy(alpha = 0.8f), fontSizeOverride = 12f, modifier = Modifier.weight(1f))
            if (live) {
                val tint = if (counting) Color(0xFF34D399) else Color(0xFFFBBF24)
                Row(
                    Modifier.clip(RoundedCornerShape(rdp(10))).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = rdp(10), vertical = rdp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(rdp(7)).clip(CircleShape).background(tint))
                    Spacer(Modifier.width(rdp(6)))
                    XaniText(
                        copy.t(if (counting) Copy.MyLiveCounting else Copy.MyLivePaused),
                        Type.Micro,
                        color = Color.White,
                        fontSizeOverride = 11f,
                    )
                }
            }
        }
        Spacer(Modifier.height(rdp(6)))
        XaniText(fmtClock(todayMs, seconds = true), Type.H1, color = Color.White, fontSizeOverride = 38f, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (live && pausedMs >= 1000L) {
            Spacer(Modifier.height(rdp(6)))
            XaniText(
                copy.t(Copy.MyLivePausedFor, "t" to fmtClock(pausedMs, seconds = true)),
                Type.Micro,
                color = Color.White.copy(alpha = 0.8f),
                fontSizeOverride = 11f,
            )
        }
    }
}

@Composable
private fun TotalTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(rdp(16))).background(ProfileTokens.Bg2).padding(horizontal = rdp(12), vertical = rdp(14)),
    ) {
        XaniText(label, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
        Spacer(Modifier.height(rdp(4)))
        XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DayRow(
    copy: Copy,
    day: LiveTimeDay,
    label: String,
    totalMs: Long,
    open: Boolean,
    busy: Boolean,
    sessions: List<LiveTimeSession>?,
    timeFmt: DateTimeFormatter,
    zone: ZoneId,
    onToggle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = rdp(8)).clip(RoundedCornerShape(rdp(14)))
            .background(ProfileTokens.Bg2),
    ) {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = onToggle).padding(horizontal = rdp(14), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
                    if (day.live) {
                        Spacer(Modifier.width(rdp(8)))
                        Box(Modifier.size(rdp(7)).clip(CircleShape).background(ProfileTokens.Online))
                    }
                }
                val parts = buildList {
                    if (day.cohostMs > 0L) add("${copy.t(Copy.MyLiveCohost)} ${fmtClock(day.cohostMs)}")
                    if (day.hostAudioMs > 0L && day.hostVideoMs > 0L) add("${copy.t(Copy.MyLiveAudio)} ${fmtClock(day.hostAudioMs)}")
                }
                if (parts.isNotEmpty()) {
                    XaniText(parts.joinToString(" · "), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
                }
            }
            XaniText(fmtClock(totalMs, seconds = true), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
            Spacer(Modifier.width(rdp(8)))
            Box(Modifier.rotate(if (open) 90f else 0f)) { ProfileIco.Chevron(Color(0xFFBBBBBB), rdp(15)) }
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
            when {
                busy || sessions == null -> Box(Modifier.fillMaxWidth().padding(rdp(14)), contentAlignment = Alignment.Center) {
                    Spinner(rdp(16), ProfileTokens.Accent)
                }
                sessions.isEmpty() -> XaniText(
                    copy.t(Copy.MyLiveSessionsEmpty), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f,
                    modifier = Modifier.padding(horizontal = rdp(14), vertical = rdp(12)),
                )
                else -> Column(Modifier.padding(horizontal = rdp(14), vertical = rdp(6))) {
                    XaniText(
                        copy.t(Copy.MyLiveSessionsCount, "n" to sessions.size.toString()),
                        Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 11f,
                        modifier = Modifier.padding(top = rdp(4), bottom = rdp(2)),
                    )
                    sessions.forEach { s -> SessionRow(copy, s, timeFmt, zone) }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(copy: Copy, s: LiveTimeSession, timeFmt: DateTimeFormatter, zone: ZoneId) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(rdp(34)).clip(RoundedCornerShape(rdp(10))).background(ProfileTokens.AccentWash),
            contentAlignment = Alignment.Center,
        ) {
            if (s.kind == "audio") ProfileIco.Path("M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3zm7 11a7 7 0 01-14 0M12 19v4", ProfileTokens.AccentDeep)
            else ProfileIco.Path("M15 10l4.553-2.069A1 1 0 0121 8.845v6.31a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z", ProfileTokens.AccentDeep)
        }
        Spacer(Modifier.width(rdp(10)))
        Column(Modifier.weight(1f)) {
            val start = if (s.startedAtMs > 0L) timeFmt.format(Instant.ofEpochMilli(s.startedAtMs).atZone(zone)) else "—"
            val end = when {
                s.live -> copy.t(Copy.MyLiveLiveNow)
                s.endedAtMs > 0L -> timeFmt.format(Instant.ofEpochMilli(s.endedAtMs).atZone(zone))
                else -> "—"
            }
            val head = buildString {
                append(start); append(" – "); append(end)
                if (s.role == "cohost") { append(" · "); append(copy.t(Copy.MyLiveCohost)) }
            }
            XaniText(head, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (s.segments > 1) add(if (s.segments == 2) copy.t(Copy.MyLiveDropsOnce) else copy.t(Copy.MyLiveDrops, "n" to (s.segments - 1).toString()))
                if (s.pausedMs >= 1000L) add(copy.t(Copy.MyLivePausedFor, "t" to fmtClock(s.pausedMs, seconds = true)))
                if (!s.live && s.endReason.isNotEmpty()) add(copy.t(Copy.MyLiveEndedBy, "r" to reasonLabel(copy, s.endReason)))
            }
            if (sub.isNotEmpty()) {
                XaniText(sub.joinToString(" · "), Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(rdp(8)))
        XaniText(
            fmtClock(s.verifiedMs, seconds = true),
            Type.LabelBold,
            color = if (s.live && s.counting) ProfileTokens.Ok else ProfileTokens.Text,
            fontSizeOverride = 13f,
        )
    }
}

private fun reasonLabel(copy: Copy, reason: String): String = when (reason) {
    "host_ended", "stop", "explicit" -> copy.t(Copy.MyLiveReasonHost)
    "lost", "host_lost", "dropped", "lost_grace" -> copy.t(Copy.MyLiveReasonLost)
    "app_closed", "background", "battery" -> copy.t(Copy.MyLiveReasonAppClosed)
    "max_length" -> copy.t(Copy.MyLiveReasonMax)
    "stand", "kicked", "left", "room_ended", "moved" -> copy.t(Copy.MyLiveReasonStand)
    else -> copy.t(Copy.MyLiveReasonOther, "r" to reason.replace('_', ' '))
}

/** "Today" / "Yesterday" / "Fri, 3 Oct" for an accounting day. */
private fun dayLabel(day: String, today: String, copy: Copy, fmt: DateTimeFormatter): String {
    if (day == today) return copy.t(Copy.MyLiveToday)
    return try {
        LocalDate.parse(day).format(fmt)
    } catch (_: Exception) {
        day
    }
}

/** `h:mm:ss` with seconds, else `h:mm` / `mm:ss` style without. */
internal fun fmtClock(ms: Long, seconds: Boolean = false): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    val h = s / 3600L
    val m = (s % 3600L) / 60L
    val sec = s % 60L
    return when {
        seconds && h > 0L -> "%d:%02d:%02d".format(h, m, sec)
        seconds -> "%02d:%02d".format(m, sec)
        h > 0L -> "%dh %02dm".format(h, m)
        else -> "%dm".format(m)
    }
}

/** Lifetime: hours with one decimal once past a day, otherwise `h:mm`. */
private fun fmtHours(ms: Long): String {
    val h = ms / 3_600_000.0
    return if (h >= 24.0) "%.1f h".format(h) else fmtClock(ms)
}
