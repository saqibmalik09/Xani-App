package app.xani.feature.home

import androidx.compose.runtime.Immutable
import app.xani.core.isolate.runFeature
import app.xani.core.net.CallEvent
import app.xani.core.net.LiveFeed
import app.xani.core.net.LiveRoomData
import app.xani.core.net.Xani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Who is live right now, kept current by the server rather than by polling.
 *
 * Opening the Live tab registers this device as a directory watcher (the
 * `open` call does that server-side) and holds the realtime stream; from then
 * on every go-live, audience change and end arrives as a `live_directory`
 * frame and patches the list in place. The registration is renewed well
 * inside its TTL for as long as the tab is on screen, and one page is
 * re-read on each renewal so a frame shed during a reconnect cannot leave a
 * ghost card behind.
 */
object LiveDirectory {
    @Immutable
    data class State(
        val rooms: List<LiveRoomData> = emptyList(),
        /** At least one server read succeeded this session. */
        val loaded: Boolean = false,
        /** The last read failed on transport; the list shown is what we had. */
        val offline: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var collecting = false

    /**
     * Keeps this device registered as a watcher and the list fresh until the
     * calling coroutine is cancelled (the tab left the screen).
     */
    suspend fun watch(): Nothing = coroutineScope {
        ensureCollecting()
        launch { LiveFeed.hold() }
        while (true) {
            refresh()
            delay(RENEW_MS)
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("unreachable")
    }

    /** One page from the server; also (re)registers this device as a watcher. */
    suspend fun refresh() {
        val r = Xani.calls.liveOpen(PAGE)
        val list = r.value
        if (r.ok && list != null) {
            _state.value = State(rooms = list.filter { it.live }.sortedWith(order), loaded = true, offline = false)
        } else if (r.isTransport) {
            _state.value = _state.value.copy(offline = true)
        }
    }

    private fun ensureCollecting() {
        if (collecting) return
        collecting = true
        scope.launch {
            LiveFeed.call.collect { e -> runFeature(Unit) { onEvent(e) } }
        }
    }

    private fun onEvent(e: CallEvent) {
        when (e) {
            is CallEvent.LiveDirectory -> {
                val cur = _state.value.rooms
                val next = when {
                    e.op == "remove" || !e.room.live -> cur.filter { it.id != e.room.id }
                    else -> (cur.filter { it.id != e.room.id } + e.room).sortedWith(order)
                }
                if (next != cur) _state.value = _state.value.copy(rooms = next)
            }
            // A room we are watching told us it ended: drop the card before the directory frame lands.
            is CallEvent.LiveEnded -> {
                val cur = _state.value.rooms
                if (cur.any { it.id == e.roomId }) _state.value = _state.value.copy(rooms = cur.filter { it.id != e.roomId })
            }
            is CallEvent.LiveViewers -> {
                val cur = _state.value.rooms
                val i = cur.indexOfFirst { it.id == e.roomId }
                if (i >= 0 && cur[i].viewers != e.viewers) {
                    _state.value = _state.value.copy(rooms = cur.toMutableList().also { it[i] = cur[i].copy(viewers = e.viewers) })
                }
            }
            else -> Unit
        }
    }

    private val order = compareByDescending<LiveRoomData> { it.viewers }.thenByDescending { it.startedAtMs }

    private const val PAGE = 60
    private const val RENEW_MS = 45_000L
}

// ─── presentation ────────────────────────────────────────────────────────────

private val Palette = longArrayOf(
    0xFF7C3AED, 0xFFEC4899, 0xFF10B981, 0xFFF59E0B, 0xFF0EA5E9, 0xFFEF4444, 0xFF6366F1, 0xFFF472B6,
)

/** The listing card for one server room, in the same shape the Live pane has always drawn. */
internal fun LiveRoomData.toTile(): LiveTile {
    val display = host.fullName.ifBlank { host.username }.ifBlank { "Live" }
    val handle = if (host.username.isNotBlank()) "@${host.username}" else ""
    val iso = host.country.uppercase(Locale.ROOT).takeIf { it.length == 2 } ?: ""
    return LiveTile(
        id = id,
        host = handle,
        name = display,
        viewers = compactCount(viewers),
        category = title.ifBlank { if (kind == "audio") "Audio" else "Video" },
        initials = initialsOf(display),
        color = parseTint(tint) ?: Palette[Math.floorMod(hostId, Palette.size.toLong()).toInt()],
        flag = if (iso.isEmpty()) "🌍" else flagOf(iso),
        country = if (iso.isEmpty()) "ALL" else iso,
        kind = if (kind == "audio") LiveKind.Audio else LiveKind.Video,
        roomId = id,
        hostId = hostId,
        avatarUrl = host.avatarUrl,
        viewerCount = viewers,
        locked = locked,
        friendsOnly = audience == "friends",
        coverUrl = coverUrl,
        hostLevel = host.receiverLevel,
        pk = this.pk != null,
    )
}

internal fun compactCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000f)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1000f)
    else -> n.toString()
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(' ', '_', '.').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "•"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.ROOT)
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.ROOT)
    }
}

/** ISO-3166 alpha-2 → regional-indicator flag. */
private fun flagOf(iso: String): String {
    val sb = StringBuilder()
    for (c in iso) {
        if (c !in 'A'..'Z') return "🌍"
        sb.appendCodePoint(0x1F1E6 + (c - 'A'))
    }
    return sb.toString()
}

/** `#RRGGBB` or `#AARRGGBB` from the host's go-live banner pick. */
private fun parseTint(s: String): Long? {
    if (!s.startsWith("#")) return null
    val hex = s.substring(1)
    val v = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> 0xFF000000L or v
        8 -> v
        else -> null
    }
}
