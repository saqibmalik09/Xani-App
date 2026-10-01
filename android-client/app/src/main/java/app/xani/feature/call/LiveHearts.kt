package app.xani.feature.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Type

/**
 * Hearts floating up the room — mine the moment I tap, everyone else's as
 * the room stream delivers them. Bounded: a busy room never draws more than
 * [MAX] at once, older ones are dropped, and each is one animated
 * [graphicsLayer], no layout per frame.
 */
class LiveHeartsState {
    internal val hearts = mutableStateListOf<FloatingHeart>()
    private var seq = 0

    fun add() {
        seq += 1
        hearts.add(FloatingHeart(seq, 0.15f + (seq % 7) * 0.045f))
        while (hearts.size > MAX) hearts.removeAt(0)
    }

    private companion object { const val MAX = 12 }
}

internal data class FloatingHeart(val id: Int, val xFrac: Float)

/** Hearts state that also floats what other people tap, for as long as it is on screen. */
@Composable
fun rememberLiveHearts(): LiveHeartsState {
    val state = remember { LiveHeartsState() }
    LaunchedEffect(Unit) {
        LiveSession.hearts.collect { n -> repeat(n.coerceIn(1, 4)) { state.add() } }
    }
    return state
}

@Composable
fun LiveHeartsLayer(state: LiveHeartsState, modifier: Modifier = Modifier) {
    Box(modifier) {
        state.hearts.forEach { h ->
            key(h.id) { FloatingHeartAnim(h, onDone = { state.hearts.remove(h) }) }
        }
    }
}

@Composable
private fun FloatingHeartAnim(heart: FloatingHeart, onDone: () -> Unit) {
    val progress = remember(heart.id) { Animatable(0f) }
    LaunchedEffect(heart.id) {
        progress.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                translationX = size.width * heart.xFrac
                translationY = size.height * (1f - p) - size.height * 0.1f
                alpha = if (p < 0.7f) 1f else 1f - (p - 0.7f) / 0.3f
                val s = 0.8f + 0.4f * p
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        XaniText("❤️", Type.Label, fontSizeOverride = 22f)
    }
}
