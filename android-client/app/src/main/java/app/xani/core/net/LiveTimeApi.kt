package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * The host's own live-time record: today / week / month / lifetime, the
 * day-by-day ledger and the sessions inside a day. Every number is the
 * server's verified figure in milliseconds — the same one the dashboard
 * shows — never a device clock.
 */

/** How the server cuts days: the admin's timezone and day-start. */
@Immutable
data class LiveTimeCalendar(
    val tz: String,
    val dayStart: String,
    val weekStart: String,
    /** Today's accounting day, YYYY-MM-DD in [tz]. */
    val today: String,
    val weekFrom: String,
    val month: String,
    /** Unix ms of the server's clock when this reply was built. */
    val serverNowMs: Long,
) {
    companion object {
        val Empty = LiveTimeCalendar("UTC", "00:00", "monday", "", "", "", 0L)

        fun from(json: JSONObject?): LiveTimeCalendar {
            json ?: return Empty
            return LiveTimeCalendar(
                tz = json.str("tz", "UTC"),
                dayStart = json.str("day_start", "00:00"),
                weekStart = json.str("week_start", "monday"),
                today = json.str("today"),
                weekFrom = json.str("week_from"),
                month = json.str("month"),
                serverNowMs = parseRfc3339Ms(json.str("server_now")),
            )
        }
    }
}

/** The running figure: closed totals plus the open segment when live. */
@Immutable
data class LiveTimeNow(
    val todayMs: Long,
    val weekMs: Long,
    val monthMs: Long,
    val lifetimeMs: Long,
    val live: Boolean,
    val roomId: Long,
    /** The open segment's verified part — already inside the totals above. */
    val runningMs: Long,
    /** Live and the last heartbeat is inside the allowance: the totals advance. */
    val counting: Boolean,
    val pausedMs: Long,
) {
    companion object {
        fun from(json: JSONObject?): LiveTimeNow {
            json ?: return LiveTimeNow(0, 0, 0, 0, false, 0, 0, false, 0)
            return LiveTimeNow(
                todayMs = json.long("today_ms"),
                weekMs = json.long("week_ms"),
                monthMs = json.long("month_ms"),
                lifetimeMs = json.long("lifetime_ms"),
                live = json.bool("live"),
                roomId = json.id("room_id"),
                runningMs = json.long("running_ms"),
                counting = json.bool("counting"),
                pausedMs = json.long("paused_ms"),
            )
        }
    }
}

@Immutable
data class LiveTimeDay(
    /** YYYY-MM-DD in the accounting timezone. */
    val day: String,
    val totalMs: Long,
    val hostVideoMs: Long,
    val hostAudioMs: Long,
    val cohostVideoMs: Long,
    val cohostAudioMs: Long,
    /** Today while live: the running segment is included and still moving. */
    val live: Boolean,
) {
    val hostMs: Long get() = hostVideoMs + hostAudioMs
    val cohostMs: Long get() = cohostVideoMs + cohostAudioMs

    companion object {
        fun from(json: JSONObject) = LiveTimeDay(
            day = json.str("day"),
            totalMs = json.long("total_ms"),
            hostVideoMs = json.long("host_video_ms"),
            hostAudioMs = json.long("host_audio_ms"),
            cohostVideoMs = json.long("cohost_video_ms"),
            cohostAudioMs = json.long("cohost_audio_ms"),
            live = json.bool("live"),
        )
    }
}

@Immutable
data class LiveTimeSession(
    val roomId: Long,
    /** host | cohost */
    val role: String,
    /** video | audio */
    val kind: String,
    val title: String,
    val startedAtMs: Long,
    /** 0 while the session is still open. */
    val endedAtMs: Long,
    val verifiedMs: Long,
    val pausedMs: Long,
    /** How many times the connection dropped past the allowance and came back. */
    val segments: Int,
    val endReason: String,
    val live: Boolean,
    val counting: Boolean,
) {
    companion object {
        fun from(json: JSONObject): LiveTimeSession {
            val live = json.bool("live")
            return LiveTimeSession(
                roomId = json.id("room_id"),
                role = json.str("role", "host"),
                kind = json.str("kind", "video"),
                title = json.str("title"),
                startedAtMs = parseRfc3339Ms(json.str("started_at")),
                endedAtMs = if (live) 0L else parseRfc3339Ms(json.str("ended_at")),
                verifiedMs = json.long("verified_ms"),
                pausedMs = json.long("paused_ms"),
                segments = json.int("segments", 1),
                endReason = json.str("end_reason"),
                live = live,
                counting = json.bool("counting"),
            )
        }
    }
}

@Immutable
data class LiveTimeHome(val now: LiveTimeNow, val calendar: LiveTimeCalendar)

@Immutable
data class LiveTimeDays(
    val from: String,
    val to: String,
    val days: List<LiveTimeDay>,
    val now: LiveTimeNow,
    val calendar: LiveTimeCalendar,
)

@Immutable
data class LiveTimeSessions(val day: String, val sessions: List<LiveTimeSession>, val calendar: LiveTimeCalendar)

class LiveTimeApi internal constructor(private val api: ApiClient) {
    /** Today / week / month / lifetime right now, plus how the server cuts days. */
    suspend fun me(): ApiResponse<LiveTimeHome> =
        api.get("/v1/me/live-time", authenticated = true) { d ->
            LiveTimeHome(now = LiveTimeNow.from(d.obj("now")), calendar = LiveTimeCalendar.from(d.obj("calendar")))
        }

    /**
     * Day rows between [from] and [to] (YYYY-MM-DD, inclusive, newest first
     * from the server). Both empty = the server's default window (31 days).
     */
    suspend fun days(from: String = "", to: String = ""): ApiResponse<LiveTimeDays> {
        val q = HashMap<String, String>(2)
        if (from.isNotEmpty()) q["from"] = from
        if (to.isNotEmpty()) q["to"] = to
        return api.get("/v1/me/live-time/days", q, authenticated = true) { d ->
            LiveTimeDays(
                from = d.str("from"),
                to = d.str("to"),
                days = d.objects("days").map(LiveTimeDay::from),
                now = LiveTimeNow.from(d.obj("now")),
                calendar = LiveTimeCalendar.from(d.obj("calendar")),
            )
        }
    }

    /** Every session that touched [day] (YYYY-MM-DD); empty = today. */
    suspend fun sessions(day: String = ""): ApiResponse<LiveTimeSessions> =
        api.get(
            "/v1/me/live-time/sessions",
            if (day.isEmpty()) emptyMap() else mapOf("day" to day),
            authenticated = true,
        ) { d ->
            LiveTimeSessions(
                day = d.str("from"),
                sessions = d.objects("sessions").map(LiveTimeSession::from),
                calendar = LiveTimeCalendar.from(d.obj("calendar")),
            )
        }
}

/**
 * RFC 3339 → unix ms without java.time's parser allocating on every row.
 * Accepts `2026-10-03T09:15:30Z`, fractional seconds and `±HH:MM` offsets;
 * anything else is 0 (the row degrades to "—", it never crashes a screen).
 */
internal fun parseRfc3339Ms(s: String): Long {
    if (s.length < 20) return 0L
    fun num(from: Int, to: Int): Int {
        var v = 0
        for (i in from until to) {
            val c = s[i]
            if (c < '0' || c > '9') return -1
            v = v * 10 + (c - '0')
        }
        return v
    }
    val y = num(0, 4); val mo = num(5, 7); val d = num(8, 10)
    val h = num(11, 13); val mi = num(14, 16); val sec = num(17, 19)
    if (y < 0 || mo < 1 || d < 1 || h < 0 || mi < 0 || sec < 0) return 0L
    var i = 19
    var frac = 0L
    if (i < s.length && s[i] == '.') {
        i++
        var digits = 0
        var v = 0L
        while (i < s.length && s[i] in '0'..'9') {
            if (digits < 3) { v = v * 10 + (s[i] - '0'); digits++ }
            i++
        }
        while (digits < 3) { v *= 10; digits++ }
        frac = v
    }
    var offsetMin = 0
    if (i < s.length) {
        when (s[i]) {
            'Z', 'z' -> Unit
            '+', '-' -> {
                if (s.length < i + 6) return 0L
                val oh = num(i + 1, i + 3); val om = num(i + 4, i + 6)
                if (oh < 0 || om < 0) return 0L
                offsetMin = (oh * 60 + om) * (if (s[i] == '-') -1 else 1)
            }
            else -> return 0L
        }
    }
    // Days since epoch (proleptic Gregorian), the civil-from-days algorithm.
    val yy = if (mo <= 2) y - 1 else y
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (mo + (if (mo > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097L + doe - 719468L
    val secs = days * 86400L + h * 3600L + mi * 60L + sec - offsetMin * 60L
    return secs * 1000L + frac
}
