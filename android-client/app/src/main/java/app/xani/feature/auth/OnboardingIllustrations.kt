package app.xani.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import app.xani.ui.components.XaniText
import app.xani.ui.theme.rdp
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.gradientFill
import app.xani.ui.vector.stroke
import app.xani.ui.vector.strokedFill

private const val Viewport = 200f

private val Frame = Color(0xFF111128)
private val Screen = Color(0xFF1A1A35)
private val CoinCore = Color(0xFF1A1510)
private val GiftBody = Color(0xFF1E1A35)
private val Gold = Dark.Gold

private val WatchShapes = listOf(
    VShape.Box(54f, 20f, 92f, 160f, 18f, strokedFill(Frame, Dark.Primary.copy(alpha = 0.30f), 1.5f)),
    VShape.Box(
        60f, 26f, 80f, 148f, 13f,
        gradientFill(listOf(Dark.Cyan, Dark.Primary), alpha = 0.12f),
    ),
    VShape.Box(60f, 26f, 80f, 148f, 13f, fill(Screen)),
    VShape.Circle(
        100f, 90f, 28f,
        strokedFill(Dark.Primary.copy(alpha = 0.15f), Dark.Primary.copy(alpha = 0.30f), 1f),
    ),
    VShape.Circle(100f, 80f, 10f, fill(Dark.PrimarySoft.copy(alpha = 0.50f))),
    VShape.Ellipse(100f, 100f, 16f, 8f, fill(Dark.PrimarySoft.copy(alpha = 0.30f))),
    VShape.D("M96 84 L96 98 L107 91 Z", fill(Color.White, alpha = 0.9f)),
    VShape.Circle(126f, 108f, 8f, fill(Color.White.copy(alpha = 0.07f))),
    VShape.Circle(126f, 108f, 3f, fill(Dark.PrimarySoft.copy(alpha = 0.60f))),
    VShape.Circle(126f, 126f, 8f, fill(Color.White.copy(alpha = 0.07f))),
    VShape.Circle(126f, 126f, 3f, fill(Dark.PrimarySoft.copy(alpha = 0.60f))),
    VShape.Circle(126f, 144f, 8f, fill(Color.White.copy(alpha = 0.07f))),
    VShape.Circle(126f, 144f, 3f, fill(Dark.PrimarySoft.copy(alpha = 0.60f))),
    VShape.Box(66f, 148f, 48f, 3f, 1.5f, fill(Color.White.copy(alpha = 0.25f))),
    VShape.Box(66f, 155f, 36f, 2f, 1f, fill(Color.White.copy(alpha = 0.12f))),
    VShape.Box(88f, 27f, 24f, 4f, 2f, fill(Dark.Background)),
    VShape.Box(64f, 30f, 26f, 12f, 4f, fill(Dark.Accent)),
)

private val CreateShapes = listOf(
    VShape.Box(
        30f, 40f, 140f, 110f, 16f,
        strokedFill(Frame, Dark.Primary.copy(alpha = 0.25f), 1.5f),
    ),
    VShape.D("M46 56 L36 56 L36 46", stroke(Dark.MarkStart, 2f)),
    VShape.D("M154 56 L164 56 L164 46", stroke(Dark.MarkStart, 2f)),
    VShape.D("M46 134 L36 134 L36 144", stroke(Dark.MarkEnd, 2f)),
    VShape.D("M154 134 L164 134 L164 144", stroke(Dark.MarkEnd, 2f)),
    VShape.Circle(100f, 82f, 18f, fill(Dark.PrimarySoft.copy(alpha = 0.20f))),
    VShape.Ellipse(100f, 108f, 28f, 14f, fill(Dark.PrimarySoft.copy(alpha = 0.12f))),
    VShape.Line(77f, 40f, 77f, 150f, stroke(Dark.Primary.copy(alpha = 0.08f), 1f)),
    VShape.Line(123f, 40f, 123f, 150f, stroke(Dark.Primary.copy(alpha = 0.08f), 1f)),
    VShape.Line(30f, 77f, 170f, 77f, stroke(Dark.Primary.copy(alpha = 0.08f), 1f)),
    VShape.Line(30f, 113f, 170f, 113f, stroke(Dark.Primary.copy(alpha = 0.08f), 1f)),
    VShape.Box(30f, 158f, 140f, 34f, 10f, fill(Frame)),
    VShape.Circle(
        54f, 175f, 7f,
        strokedFill(Color.White.copy(alpha = 0.08f), Dark.Primary.copy(alpha = 0.20f), 1f),
    ),
    VShape.Circle(
        78f, 175f, 7f,
        strokedFill(Color.White.copy(alpha = 0.08f), Dark.Primary.copy(alpha = 0.20f), 1f),
    ),
    VShape.Circle(
        122f, 175f, 7f,
        strokedFill(Color.White.copy(alpha = 0.08f), Dark.Primary.copy(alpha = 0.20f), 1f),
    ),
    VShape.Circle(
        146f, 175f, 7f,
        strokedFill(Color.White.copy(alpha = 0.08f), Dark.Primary.copy(alpha = 0.20f), 1f),
    ),
    VShape.Circle(100f, 175f, 9f, gradientFill(listOf(Dark.MarkStart, Dark.MarkEnd))),
    VShape.Circle(100f, 175f, 5f, fill(Color.White)),
    VShape.D("M158 166 L155 174 L159 174 L156 182", stroke(Dark.Warning, 1.5f)),
)

private val EarnShapes = listOf(
    VShape.Circle(
        100f, 105f, 42f,
        strokedFill(Gold.copy(alpha = 0.08f), Gold.copy(alpha = 0.30f), 1.5f),
    ),
    VShape.Circle(100f, 105f, 32f, strokedFill(CoinCore, Gold.copy(alpha = 0.50f), 1.5f)),
    VShape.Box(
        36f, 128f, 32f, 28f, 5f,
        strokedFill(GiftBody, Dark.Primary.copy(alpha = 0.40f), 1f),
    ),
    VShape.Box(
        36f, 120f, 32f, 10f, 3f,
        strokedFill(Dark.PrimarySoft.copy(alpha = 0.20f), Dark.Primary.copy(alpha = 0.40f), 1f),
    ),
    VShape.Line(52f, 120f, 52f, 156f, stroke(Dark.PrimarySoft.copy(alpha = 0.50f), 1.5f)),
    VShape.D(
        "M52 120 C52 115 44 112 44 116 C44 120 52 120 52 120",
        stroke(Dark.PrimarySoft.copy(alpha = 0.70f), 1.5f),
    ),
    VShape.D(
        "M52 120 C52 115 60 112 60 116 C60 120 52 120 52 120",
        stroke(Dark.PrimarySoft.copy(alpha = 0.70f), 1.5f),
    ),
    VShape.D(
        "M130 134 L134 120 L140 128 L146 116 L152 128 L158 120 L162 134 Z",
        strokedFill(Gold.copy(alpha = 0.15f), Gold, 1.5f),
    ),
    VShape.Circle(72f, 72f, 7f, strokedFill(Gold.copy(alpha = 0.15f), Gold.copy(alpha = 0.40f), 1f)),
    VShape.Circle(148f, 88f, 7f, strokedFill(Gold.copy(alpha = 0.15f), Gold.copy(alpha = 0.40f), 1f)),
    VShape.Circle(58f, 145f, 7f, strokedFill(Gold.copy(alpha = 0.15f), Gold.copy(alpha = 0.40f), 1f)),
    VShape.Circle(68f, 56f, 2.5f, fill(Gold, alpha = 0.9f)),
    VShape.Circle(148f, 62f, 2.5f, fill(Gold, alpha = 0.7f)),
    VShape.Circle(40f, 90f, 2.5f, fill(Gold, alpha = 0.5f)),
    VShape.Circle(164f, 98f, 2.5f, fill(Gold, alpha = 0.6f)),
    VShape.Circle(90f, 36f, 2.5f, fill(Gold, alpha = 0.8f)),
)

/**
 * [baselineSide] is a 390dp-frame number. Scaling is applied here so the embedded
 * labels are not scaled twice: `XaniText` already multiplies `fontSizeOverride` by
 * the responsive factor.
 */
@Composable
fun WatchIllustration(baselineSide: Float) {
    val side = rdp(baselineSide)
    Box(Modifier.size(side)) {
        VectorCanvas(WatchShapes, Viewport, Modifier.fillMaxSize())
        XaniText(
            text = "LIVE",
            style = Type.SlideTag.copy(letterSpacing = TextUnit.Unspecified),
            color = Color.White,
            fontSizeOverride = baselineSide * (7f / Viewport),
            modifier = Modifier.offset(
                x = side * (67f / Viewport),
                y = side * (32f / Viewport),
            ),
        )
    }
}

@Composable
fun CreateIllustration(baselineSide: Float) {
    VectorCanvas(CreateShapes, Viewport, Modifier.size(rdp(baselineSide)))
}

@Composable
fun EarnIllustration(baselineSide: Float) {
    val side = rdp(baselineSide)
    Box(Modifier.size(side), contentAlignment = Alignment.TopStart) {
        VectorCanvas(EarnShapes, Viewport, Modifier.fillMaxSize())
        XaniText(
            text = "¥",
            style = Type.H1,
            color = Gold,
            fontSizeOverride = baselineSide * (26f / Viewport),
            modifier = Modifier.offset(
                x = side * (92f / Viewport),
                y = side * (90f / Viewport),
            ),
        )
    }
}
