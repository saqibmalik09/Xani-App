package app.xani.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import app.xani.ui.theme.Dark
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke
import app.xani.ui.vector.strokedFill

/** Official 48×48 G. Same-colour hairline stroke closes anti-alias gaps between slices. */
private fun googleSlice(data: String, color: Color) = VShape.D(
    data,
    strokedFill(color, color, 0.7f),
)

private val Google = listOf(
    googleSlice(
        "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 4.96 2.56 12.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
        Color(0xFFEA4335),
    ),
    googleSlice(
        "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
        Color(0xFF4285F4),
    ),
    googleSlice(
        "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
        Color(0xFFFBBC05),
    ),
    googleSlice(
        "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 43.04 14.62 48 24 48z",
        Color(0xFF34A853),
    ),
)

private val Apple = listOf(
    VShape.D(
        "M14.95 10.63c-.02-2.3 1.88-3.4 1.97-3.46-1.08-1.58-2.76-1.8-3.36-1.82-1.43-.15-2.8.84-3.53.84-.73 0-1.85-.82-3.04-.8-1.56.02-3 .91-3.8 2.3-1.63 2.82-.42 6.99 1.17 9.28.78 1.12 1.7 2.38 2.9 2.34 1.17-.05 1.61-.75 3.03-.75 1.41 0 1.81.75 3.04.73 1.26-.02 2.06-1.14 2.83-2.27a10.3 10.3 0 001.27-2.62c-.03-.01-2.44-.93-2.48-3.77zM12.57 3.52C13.24 2.71 13.7 1.6 13.57.46c-.99.04-2.2.66-2.91 1.47-.64.72-1.2 1.88-1.05 2.99 1.1.08 2.23-.56 2.96-1.4z",
        fill(Color(0xFF111111)),
    ),
)

private val Facebook = listOf(
    VShape.D(
        "M20 10C20 4.477 15.523 0 10 0S0 4.477 0 10c0 4.991 3.657 9.128 8.438 9.878V12.89H5.898V10h2.54V7.797c0-2.506 1.492-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562V10h2.773l-.443 2.89h-2.33v6.988C16.343 19.128 20 14.991 20 10z",
        fill(Color(0xFF1877F2)),
    ),
)

@Composable fun GoogleIcon(size: Dp) = VectorCanvas(Google, 48f, Modifier.size(size))

@Composable fun AppleIcon(size: Dp) = VectorCanvas(Apple, 20f, Modifier.size(size))

@Composable fun FacebookIcon(size: Dp) = VectorCanvas(Facebook, 20f, Modifier.size(size))

@Composable
fun MailIcon(size: Dp, tint: Color = Color.White) = VectorCanvas(
    shapes = listOf(
        VShape.Box(2f, 4f, 16f, 12f, 3f, stroke(tint, 1.5f)),
        VShape.D("M2 7.5l7.2 5a1.4 1.4 0 001.6 0L18 7.5", stroke(tint, 1.5f)),
    ),
    viewport = 20f,
    modifier = Modifier.size(size),
)

@Composable
fun BackIcon(size: Dp, tint: Color = Color(0xFF333333)) = VectorCanvas(
    shapes = listOf(VShape.D("M10 3L5 8L10 13", stroke(tint, 1.8f))),
    viewport = 16f,
    modifier = Modifier.size(size),
)

@Composable
fun HelpIcon(size: Dp, tint: Color = Color(0xFF888888)) = VectorCanvas(
    shapes = listOf(
        VShape.Circle(7f, 7f, 6f, stroke(tint, 1.3f)),
        VShape.D(
            "M5.5 5.3C5.5 4.45 6.17 3.8 7 3.8s1.5.65 1.5 1.5c0 .7-.45 1.15-1 1.4-.4.18-.5.4-.5.8",
            stroke(tint, 1.3f),
        ),
        VShape.Circle(7f, 10f, 0.7f, fill(tint)),
    ),
    viewport = 14f,
    modifier = Modifier.size(size),
)

@Composable
fun EyeIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(
        VShape.Ellipse(10f, 10f, 3.5f, 2.5f, stroke(tint, 1.4f)),
        VShape.D(
            "M1 10C3 6.4 6.5 4 10 4s7 2.4 9 6c-2 3.6-5.5 6-9 6s-7-2.4-9-6z",
            stroke(tint, 1.4f),
        ),
    ),
    viewport = 20f,
    modifier = Modifier.size(size),
)

@Composable
fun EyeOffIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(
        VShape.D("M2 2L18 18", stroke(tint, 1.4f)),
        VShape.D("M8.5 8.6A2.5 2.5 0 0011.4 11.5", stroke(tint, 1.4f)),
        VShape.D(
            "M6 5.4C4 6.8 2.4 8.2 1 10c2 3.6 5.5 6 9 6 1.6 0 3.2-.5 4.6-1.4",
            stroke(tint, 1.4f),
        ),
        VShape.D(
            "M9 4.1C9.3 4 9.7 4 10 4c3.5 0 7 2.4 9 6-0.6 1.1-1.4 2.2-2.4 3",
            stroke(tint, 1.4f),
        ),
    ),
    viewport = 20f,
    modifier = Modifier.size(size),
)

@Composable
fun ArrowRightIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(VShape.D("M3 8h10M9 4l4 4-4 4", stroke(tint, 1.8f))),
    viewport = 16f,
    modifier = Modifier.size(size),
)

@Composable
fun CheckIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(VShape.D("M2.5 7L5.5 10L11.5 4", stroke(tint, 1.8f))),
    viewport = 14f,
    modifier = Modifier.size(size),
)

@Composable
fun ChevronDownIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(VShape.D("M1 1l7 7 7-7", stroke(tint, 1.8f))),
    viewport = 16f,
    modifier = Modifier.size(size),
)

@Composable
fun ChevronRightIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(VShape.D("M6 3l5 5-5 5", stroke(tint, 1.8f))),
    viewport = 16f,
    modifier = Modifier.size(size),
)

@Composable
fun CloseIcon(size: Dp, tint: Color = Color(0xFF333333)) = VectorCanvas(
    shapes = listOf(
        VShape.D("M4 4l12 12", stroke(tint, 1.8f)),
        VShape.D("M16 4L4 16", stroke(tint, 1.8f)),
    ),
    viewport = 20f,
    modifier = Modifier.size(size),
)

@Composable
fun SearchIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(
        VShape.Circle(8.5f, 8.5f, 5.5f, stroke(tint, 1.6f)),
        VShape.D("M12.6 12.6L17 17", stroke(tint, 1.6f)),
    ),
    viewport = 20f,
    modifier = Modifier.size(size),
)

@Composable
fun WifiIcon(size: Dp, tint: Color) = VectorCanvas(
    shapes = listOf(
        VShape.D("M1.6 6.2C5 2.8 11 2.8 14.4 6.2", stroke(tint, 1.5f)),
        VShape.D("M4.1 9.1C6.4 6.9 9.6 6.9 11.9 9.1", stroke(tint, 1.5f)),
        VShape.D("M6.6 11.9C7.7 10.8 8.3 10.8 9.4 11.9", stroke(tint, 1.5f)),
        VShape.Circle(8f, 14.2f, 1f, fill(tint)),
    ),
    viewport = 16f,
    modifier = Modifier.size(size),
)

@Composable
fun OtpBadgeIcon(size: Dp) = VectorCanvas(
    shapes = listOf(
        VShape.Box(4f, 8f, 28f, 20f, 5f, stroke(Dark.PrimarySoft, 1.5f)),
        VShape.D("M4 15h28", stroke(Dark.PrimarySoft, 1.5f)),
        VShape.Circle(11f, 22f, 2f, fill(Dark.MarkStart)),
        VShape.Circle(18f, 22f, 2f, fill(Dark.MarkEnd)),
        VShape.Circle(25f, 22f, 2f, fill(Dark.MarkEnd)),
    ),
    viewport = 36f,
    modifier = Modifier.size(size),
)

@Composable
fun Spinner(size: Dp, tint: Color = Color.White) {
    val rotation = rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation.value },
    ) {
        val width = this.size.minDimension * 0.1f
        val inset = width / 2f
        drawArc(
            color = tint.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - width,
                this.size.height - width,
            ),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - width,
                this.size.height - width,
            ),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}
