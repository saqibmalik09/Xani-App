package app.xani.feature.gift

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import app.xani.core.net.LiveEnterEvent
import app.xani.feature.call.LiveSession
import app.xani.feature.social.AssetAnimation
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The default hold when the store item sets no duration of its own. */
private const val ENTRANCE_DEFAULT_MS = 5_000L
private const val ENTRANCE_MIN_MS = 1_500L
private const val ENTRANCE_MAX_MS = 30_000L
private const val GAP_MS = 150L

/**
 * An entrance older than this on THIS device's clock is history, not a
 * moment: the session keeps a short replay buffer so a frame that lands
 * mid-join still plays once the screen is up, and this bound is what stops
 * that same buffer from resurrecting an old entrance on a later screen.
 */
private const val ENTRANCE_STALE_MS = 20_000L

/**
 * Someone wearing an entrance effect walks in (the host's plays on go-live
 * too): their effect holds the broadcast for the item's own duration — five
 * seconds when the dashboard sets none — with a "Name joined" chip and the
 * item's sound, one arrival at a time. A burst of arrivals queues a handful
 * and sheds the rest; replayed or stale frames are dropped, so each entrance
 * plays exactly once. Nothing here takes pointer input; taps fall through to
 * the room chrome like they do through gifts.
 */
@Composable
internal fun EntranceOverlay(roomId: Long, copy: Copy, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf<LiveEnterEvent?>(null) }
    LaunchedEffect(roomId) {
        val lane = Channel<LiveEnterEvent>(capacity = 6, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        // The session's replay buffer hands back recent frames on collect;
        // the seen-set makes a frame delivered twice (replay + live, or both
        // streams) one entrance on screen.
        val seen = LinkedHashSet<String>()
        launch {
            LiveSession.entrances.collect { ev ->
                if (roomId != 0L && ev.roomId != roomId) return@collect
                if (ev.receivedAtMs > 0L && System.currentTimeMillis() - ev.receivedAtMs > ENTRANCE_STALE_MS) return@collect
                val key = "${ev.user.id}-${ev.atMs}"
                if (!seen.add(key)) return@collect
                if (seen.size > 64) {
                    val it = seen.iterator(); it.next(); it.remove()
                }
                // Warm the asset (and the sound) so the entrance plays, not
                // loads. Each loader is cached and failure-marked; a second
                // arrival is free.
                val url = giftImageUrl(ev.entranceUrl)
                val mime = ev.entranceMime
                when {
                    mime.contains("svga") || url.endsWith(".svga", true) -> SvgaLoader.prefetch(url)
                    mime == "application/json" || mime.contains("lottie") || url.endsWith(".json", true) ->
                        LottieLoader.prefetch(url)
                    else -> app.xani.core.media.AssetCache.prefetch(url)
                }
                if (ev.soundUrl.isNotEmpty()) GiftSound.prefetch(ev.soundUrl)
                lane.trySend(ev)
            }
        }
        for (ev in lane) {
            playing = ev
            // The item's own duration rules; the dashboard default is 5 s.
            val hold = if (ev.durationMs > 0) {
                ev.durationMs.toLong().coerceIn(ENTRANCE_MIN_MS, ENTRANCE_MAX_MS)
            } else {
                ENTRANCE_DEFAULT_MS
            }
            // The item's sound plays to everyone for as long as the effect
            // holds; the shared player clips it if a gift sound takes over.
            if (ev.soundUrl.isNotEmpty()) GiftSound.play(ev.soundUrl, hold)
            delay(hold)
            playing = null
            delay(GAP_MS)
        }
    }

    val ev = playing ?: return
    val enter = remember(ev) { Animatable(0f) }
    LaunchedEffect(ev) { enter.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    Box(modifier.fillMaxSize()) {
        AssetAnimation(
            url = ev.entranceUrl,
            mime = ev.entranceMime,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .padding(rdp(24)),
            maxEdge = 1080,
        )
        val name = ev.user.fullName.ifBlank { ev.user.username }.ifBlank { "…" }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rdp(220))
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 40f
                }
                .clip(RoundedCornerShape(rdp(22)))
                .background(Brush.horizontalGradient(listOf(Color(0xCC7C3AED), Color(0x99EC4899))))
                .padding(start = rdp(4), end = rdp(14), top = rdp(4), bottom = rdp(4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            PersonAvatar(ev.user.id, name, ev.user.avatarUrl, rdp(30), frameUrl = ev.user.frameUrl, frameMime = ev.user.frameMime)
            XaniText(
                copy.t(Copy.LiveEntranceJoined, "name" to name),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 13f,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
