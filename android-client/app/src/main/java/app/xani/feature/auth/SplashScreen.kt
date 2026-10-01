package app.xani.feature.auth

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.xani.ui.brand.LaunchTiming
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.DarkGlow
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Dark
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.WordmarkGradient
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay

private val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private const val MarkAt = 0L
private const val WordmarkAt = 180L
private const val TaglineAt = 380L

@Composable
fun SplashScreen(onDone: () -> Unit) {
    SystemBarIcons(lightBackground = false)

    val brand = LocalBrand.current
    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        phase = 1
        delay(WordmarkAt - MarkAt)
        phase = 2
        delay(TaglineAt - WordmarkAt)
        phase = 3
        val remaining = LaunchTiming.splashMinMillis - TaglineAt
        if (remaining > 0) delay(remaining)
        onDone()
    }

    val mark by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(380, easing = Ease),
        label = "mark",
    )
    val wordmark by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(300, easing = Ease),
        label = "wordmark",
    )
    val tagline by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(260),
        label = "tagline",
    )

    AuthScaffold(background = Dark.Background, glow = DarkGlow, scrollable = false) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rdp(20)),
            ) {
                XaniMark(
                    size = rdp(88),
                    modifier = Modifier.graphicsLayer {
                        alpha = mark
                        val s = 0.72f + 0.28f * mark
                        scaleX = s
                        scaleY = s
                    },
                )

                XaniText(
                    text = brand.wordmark,
                    style = Type.Wordmark,
                    brush = WordmarkGradient,
                    align = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = wordmark
                        translationY = 10.dp.toPx() * (1f - wordmark)
                    },
                )

                if (brand.tagline.isNotBlank()) {
                    XaniText(
                        text = brand.tagline,
                        style = Type.Tagline,
                        color = Dark.Faint,
                        align = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = tagline
                            translationY = 8.dp.toPx() * (1f - tagline)
                        },
                    )
                }
            }
        }
    }
}
