package app.xani.feature.inbox

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import app.xani.ui.theme.rdp

/**
 * Three dots riding a wave — "the other side is writing".
 *
 * Driven entirely on the render thread (alpha and translation on a
 * graphicsLayer, no recomposition per frame), so a typing peer in a visible
 * conversation costs nothing measurable even with the list scrolling.
 */
@Composable
fun TypingDots(
    tint: Color,
    dot: Dp = rdp(6),
    modifier: Modifier = Modifier,
) {
    val wave = rememberInfiniteTransition(label = "typing")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(dot * 0.55f))
            // Each dot is a third of a cycle behind the one before it, which is
            // what reads as a wave rather than a blink.
            val phase by wave.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(TypingCycleMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * TypingCycleMs / 3),
                ),
                label = "dot$i",
            )
            // A single smooth hump per cycle: up, down, then rest.
            val lift = kotlin.math.sin(phase * Math.PI).toFloat().coerceAtLeast(0f)
            Spacer(
                Modifier
                    .size(dot)
                    .graphicsLayer {
                        translationY = -lift * dot.toPx() * 0.6f
                    }
                    .alpha(0.45f + lift * 0.55f)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

private const val TypingCycleMs = 900
