package app.xani.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.DarkCta
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.BrandGradient
import app.xani.ui.theme.Dark
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

private val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private data class Slide(
    val tag: String,
    val title: String,
    val body: String,
    val accent: Color,
)

private val Slides = listOf(
    Slide(
        tag = "WATCH",
        title = "Stories that\nmove you",
        body = "Discover short videos, live streams and creator content tailored to your taste.",
        accent = Dark.Cyan,
    ),
    Slide(
        tag = "CREATE",
        title = "Your stage,\nyour rules",
        body = "Go live, post clips, share stories. Build an audience on your own terms.",
        accent = Dark.PrimarySoft,
    ),
    Slide(
        tag = "EARN",
        title = "Passion\nthat pays",
        body = "Receive gifts, unlock rewards, and turn creativity into real earnings.",
        accent = Dark.Gold,
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    SystemBarIcons(lightBackground = false)

    var index by rememberSaveable { mutableIntStateOf(0) }
    val slide = Slides[index]

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(index) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(350, easing = Ease))
    }
    val enter = entrance.value

    AuthScaffold(background = Dark.Background, scrollable = false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(24), vertical = rdp(12)),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(
                        rdp(1),
                        Dark.Primary.copy(alpha = 0.20f),
                        RoundedCornerShape(rdp(20)),
                    )
                    .pressable(onClick = onDone)
                    .padding(horizontal = rdp(16), vertical = rdp(6)),
            ) {
                XaniText(text = "Skip", style = Type.Caption, color = Dark.MutedFg)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.graphicsLayer {
                    alpha = enter
                    val s = 0.94f + 0.06f * enter
                    scaleX = s
                    scaleY = s
                },
            ) {
                when (index) {
                    0 -> WatchIllustration(baselineSide = 200f)
                    1 -> CreateIllustration(baselineSide = 200f)
                    else -> EarnIllustration(baselineSide = 200f)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(32))
                .graphicsLayer { alpha = enter },
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(slide.accent.copy(alpha = 0.10f))
                    .border(
                        rdp(1),
                        slide.accent.copy(alpha = 0.25f),
                        RoundedCornerShape(rdp(20)),
                    )
                    .padding(horizontal = rdp(10), vertical = rdp(4)),
            ) {
                XaniText(text = slide.tag, style = Type.SlideTag, color = slide.accent)
            }

            Spacer(Modifier.height(rdp(12)))

            XaniText(
                text = slide.title,
                style = Type.SlideTitle,
                color = Dark.Foreground,
            )

            Spacer(Modifier.height(rdp(12)))

            XaniText(
                text = slide.body,
                style = Type.BodyLight,
                color = Dark.MutedFg,
                modifier = Modifier.widthIn(max = rdp(300)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = rdp(32), end = rdp(32), top = rdp(28), bottom = rdp(36)),
            verticalArrangement = Arrangement.spacedBy(rdp(20)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slides.forEachIndexed { position, _ ->
                    val active = position == index
                    Box(
                        modifier = Modifier
                            .height(rdp(4))
                            .width(if (active) rdp(28) else rdp(6))
                            .clip(RoundedCornerShape(rdp(2)))
                            .then(
                                if (active) Modifier.background(BrandGradient)
                                else Modifier.background(Dark.Primary.copy(alpha = 0.20f)),
                            )
                            .pressable(onClick = { index = position }),
                    )
                }
            }

            DarkCta(
                text = if (index < Slides.lastIndex) "Continue" else "Get started",
                enabled = true,
                onClick = {
                    if (index < Slides.lastIndex) index += 1 else onDone()
                },
            )
        }
    }
}
