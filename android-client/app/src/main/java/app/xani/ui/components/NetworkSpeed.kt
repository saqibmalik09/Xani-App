package app.xani.ui.components

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

private const val SampleMs = 5_000L

@Immutable
data class LinkSpeed(
    val online: Boolean,
    val wifi: Boolean,
    val label: String,
) {
    companion object {
        val Offline = LinkSpeed(false, false, "Offline")
        val Waiting = LinkSpeed(true, true, "…")
    }
}

fun formatLinkSpeed(kbps: Int): String {
    if (kbps < 1) return "0 kb/s"
    if (kbps < 1000) return "$kbps kb/s"
    val mbps = kbps / 1000f
    return if (mbps >= 10f) "${mbps.toInt()} Mb/s" else "${"%.1f".format(mbps)} Mb/s"
}

private fun rxBytes(): Long {
    val n = TrafficStats.getTotalRxBytes()
    return if (n < 0L) 0L else n
}

private fun snapshot(cm: ConnectivityManager, kbps: Int): LinkSpeed {
    val network = cm.activeNetwork ?: return LinkSpeed.Offline
    val caps = cm.getNetworkCapabilities(network) ?: return LinkSpeed.Offline
    val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    return LinkSpeed(true, wifi, formatLinkSpeed(kbps))
}

/**
 * One 5s sampler for the whole app. Reads TrafficStats deltas — no probe,
 * no extra sockets, no per-screen work. First live figure appears after 5s.
 */
@Composable
fun rememberLinkSpeed(): LinkSpeed {
    val context = LocalContext.current
    val state = remember { mutableStateOf(LinkSpeed.Waiting) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        var lastRx = rxBytes()
        var lastAt = SystemClock.elapsedRealtime()
        val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                val rx = rxBytes()
                val dt = (now - lastAt).coerceAtLeast(1L)
                val delta = (rx - lastRx).coerceAtLeast(0L)
                lastRx = rx
                lastAt = now
                val kbps = ((delta * 8L) / dt).toInt()
                val next = snapshot(cm, kbps)
                if (state.value != next) state.value = next
                main.postDelayed(this, SampleMs)
            }
        }
        main.postDelayed(tick, SampleMs)
        onDispose { main.removeCallbacksAndMessages(null) }
    }
    return state.value
}

/** Global HUD. One instance at the window root; not mounted per screen. */
@Composable
fun NetworkSpeedBadge(modifier: Modifier = Modifier) {
    val link = rememberLinkSpeed()
    val ink = Color.White
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top))
            .padding(start = rdp(8), top = rdp(56))
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0x66000000))
            .padding(horizontal = rdp(7), vertical = rdp(3)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(4)),
    ) {
        WifiIcon(size = rdp(10), tint = ink)
        XaniText(
            text = link.label,
            style = Type.Micro,
            color = ink,
            align = TextAlign.Start,
            fontSizeOverride = 9f,
        )
    }
}
