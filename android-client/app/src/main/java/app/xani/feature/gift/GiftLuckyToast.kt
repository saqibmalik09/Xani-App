package app.xani.feature.gift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.net.GiftingUserEvent
import app.xani.core.net.LiveFeed
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay

/**
 * The random-reward win toast: an admin-configured, weighted chance on
 * selected gift categories pays part of a gift's value back to the sender.
 * Sourced from the same per-user `gift_sent` stream the cashback figure
 * already rides (see GiftingPanel's "+{n} back"), so it reaches the sender
 * wherever they sent the gift from (live room, profile, stage). Dedupes on
 * gift id across reconnect replays; a second win within the display window
 * queues behind the first.
 */
@Composable
internal fun rememberGiftLuckyToasts(): GiftingUserEvent? {
    var current by remember { mutableStateOf<GiftingUserEvent?>(null) }
    val seen = remember { HashSet<Long>() }
    val queue = remember { ArrayDeque<GiftingUserEvent>() }
    LaunchedEffect(Unit) {
        LiveFeed.gifting.collect { ev ->
            if (ev.kind != "gift_sent" || ev.randomWinCoins <= 0L) return@collect
            if (!seen.add(ev.giftId)) return@collect
            if (seen.size > 512) {
                seen.clear()
                seen.add(ev.giftId)
            }
            if (current == null) current = ev else if (queue.size < 8) queue.addLast(ev)
        }
    }
    LaunchedEffect(current) {
        current ?: return@LaunchedEffect
        delay(2_500)
        current = queue.removeFirstOrNull()
    }
    return current
}

@Composable
internal fun GiftLuckyToastView(copy: Copy, ev: GiftingUserEvent, modifier: Modifier = Modifier) {
    val line = ev.randomWinMessage.ifBlank { copy.t(Copy.GiftingLuckyWon, "n" to ev.randomWinCoins.toString()) }
    Row(
        modifier.clip(RoundedCornerShape(rdp(16))).background(Color(0xE6111111)).padding(horizontal = rdp(14), vertical = rdp(10)),
    ) {
        Box(Modifier.size(rdp(28)).clip(CircleShape).background(Color(0xFFF59E0B)))
        Spacer(Modifier.width(rdp(10)))
        XaniText(line, Type.LabelBold, color = Color.White, fontSizeOverride = 13f, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
