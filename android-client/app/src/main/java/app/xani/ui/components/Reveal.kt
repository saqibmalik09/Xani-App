package app.xani.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val FigmaEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Staggered entrance used across the auth flow. Alpha and translation are applied
 * on the graphics layer, so the animation runs in the draw phase and never
 * re-lays-out or recomposes the subtree per frame.
 */
fun Modifier.revealUp(
    delayMillis: Int = 0,
    fromOffset: Dp = 14.dp,
    durationMillis: Int = 280,
): Modifier = composed {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val distance = remember(fromOffset, density) { with(density) { fromOffset.toPx() } }

    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(1f, tween(durationMillis, easing = FigmaEase))
    }

    graphicsLayer {
        alpha = progress.value
        translationY = distance * (1f - progress.value)
    }
}

fun Modifier.revealScale(
    delayMillis: Int = 0,
    fromScale: Float = 0.9f,
    durationMillis: Int = 260,
): Modifier = composed {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(1f, tween(durationMillis, easing = FigmaEase))
    }

    graphicsLayer {
        alpha = progress.value
        val s = fromScale + (1f - fromScale) * progress.value
        scaleX = s
        scaleY = s
    }
}

@Composable
fun rememberFigmaEasing() = FigmaEase
