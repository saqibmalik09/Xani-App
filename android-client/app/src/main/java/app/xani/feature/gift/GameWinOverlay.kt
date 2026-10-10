package app.xani.feature.gift

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xani.core.net.LiveGameWinEvent
import app.xani.feature.call.LiveSession
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long one win strip holds the screen, enter and exit included. */
private const val WIN_HOLD_MS = 3_800L
private const val WIN_EXIT_MS = 240L
private const val WIN_GAP_MS = 150L

/** A win frame older than this on THIS device's clock is history, not news. */
private const val WIN_STALE_MS = 20_000L

/**
 * Somebody on the platform won in a game: a short golden strip near the
 * center of every live screen — the winner's picture, name and badges, and
 * what they took home. One at a time; a burst queues a handful and sheds the
 * rest; stale or repeated frames never play. Takes no pointer input, so taps
 * fall through to the room like they do through gifts.
 */
@Composable
internal fun GameWinOverlay(roomId: Long, copy: Copy, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf<LiveGameWinEvent?>(null) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(roomId) {
        val lane = Channel<LiveGameWinEvent>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val seen = LinkedHashSet<String>()
        launch {
            LiveSession.gameWins.collect { ev ->
                if (roomId != 0L && ev.roomId != roomId) return@collect
                if (ev.receivedAtMs > 0L && System.currentTimeMillis() - ev.receivedAtMs > WIN_STALE_MS) return@collect
                val key = "${ev.user.id}-${ev.atMs}"
                if (!seen.add(key)) return@collect
                if (seen.size > 64) {
                    val it = seen.iterator(); it.next(); it.remove()
                }
                lane.trySend(ev)
            }
        }
        for (ev in lane) {
            playing = ev
            leaving = false
            delay(WIN_HOLD_MS - WIN_EXIT_MS)
            leaving = true
            delay(WIN_EXIT_MS)
            playing = null
            delay(WIN_GAP_MS)
        }
    }

    val ev = playing ?: return
    // Enter: slide down from above with a slight grow; exit: fade and lift.
    val x = remember(ev) { Animatable(0f) }
    LaunchedEffect(ev, leaving) {
        if (leaving) {
            x.animateTo(0f, tween(WIN_EXIT_MS.toInt(), easing = FastOutLinearInEasing))
        } else {
            x.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        }
    }
    val name = ev.user.fullName.ifBlank { ev.user.username }.ifBlank { "…" }
    val game = ev.game.ifBlank { copy.t(Copy.LiveGameWinGame) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                // Sits just above true center so it never fights the gift lane.
                .offset(y = -rdp(70))
                .graphicsLayer {
                    alpha = x.value
                    translationY = (1f - x.value) * -48f
                    scaleX = 0.92f + 0.08f * x.value
                    scaleY = 0.92f + 0.08f * x.value
                }
                .clip(RoundedCornerShape(rdp(24)))
                .background(Brush.horizontalGradient(listOf(Color(0xE6B45309), Color(0xD9F59E0B), Color(0xCCEC4899))))
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(rdp(24)))
                .padding(start = rdp(5), end = rdp(16), top = rdp(5), bottom = rdp(5)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(9)),
        ) {
            PersonAvatar(ev.user.id, name, ev.user.avatarUrl, rdp(36), frameUrl = ev.user.frameUrl, frameMime = ev.user.frameMime)
            Column {
                XaniText(
                    name,
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = 13f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                XaniText(
                    copy.t(Copy.LiveGameWin, "amount" to formatCoins(ev.amount), "game" to game),
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSizeOverride = 12f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
