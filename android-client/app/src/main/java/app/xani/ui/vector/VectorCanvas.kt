package app.xani.ui.vector

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Renders Figma vector geometry directly onto a Compose canvas.
 *
 * Icons are drawn, not decoded: no bitmaps, no icon font, and no icon library on
 * the dependency graph. Geometry is parsed once and reused across recompositions.
 */
@Immutable
data class VPaint(
    val fill: Color? = null,
    val fillGradient: List<Color>? = null,
    val stroke: Color? = null,
    val strokeWidth: Float = 1.5f,
    val alpha: Float = 1f,
)

fun fill(color: Color, alpha: Float = 1f) = VPaint(fill = color, alpha = alpha)

fun gradientFill(colors: List<Color>, alpha: Float = 1f) =
    VPaint(fillGradient = colors, alpha = alpha)

fun stroke(color: Color, width: Float = 1.5f, alpha: Float = 1f) =
    VPaint(stroke = color, strokeWidth = width, alpha = alpha)

fun strokedFill(fill: Color, stroke: Color, width: Float = 1.5f, alpha: Float = 1f) =
    VPaint(fill = fill, stroke = stroke, strokeWidth = width, alpha = alpha)

@Immutable
sealed interface VShape {
    val paint: VPaint

    @Immutable
    data class D(val data: String, override val paint: VPaint) : VShape

    @Immutable
    data class Circle(
        val cx: Float,
        val cy: Float,
        val r: Float,
        override val paint: VPaint,
    ) : VShape

    @Immutable
    data class Ellipse(
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
        override val paint: VPaint,
    ) : VShape

    @Immutable
    data class Box(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val radius: Float = 0f,
        override val paint: VPaint,
    ) : VShape

    @Immutable
    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        override val paint: VPaint,
    ) : VShape
}

@Composable
fun VectorCanvas(
    shapes: List<VShape>,
    viewport: Float,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(shapes, viewport) { shapes.map { it to it.toPath() } }
    Canvas(modifier) {
        val k = size.minDimension / viewport
        geometry.forEach { (shape, path) -> drawVector(shape.paint, path, k) }
    }
}

private fun DrawScope.drawVector(paint: VPaint, source: Path, k: Float) {
    val path = Path().apply {
        addPath(source)
        transform(Matrix().apply { scale(k, k, 1f) })
    }

    val gradient = paint.fillGradient
    if (gradient != null) {
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = gradient,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            alpha = paint.alpha,
        )
    } else {
        paint.fill?.let { drawPath(path = path, color = it, alpha = paint.alpha) }
    }

    paint.stroke?.let {
        drawPath(
            path = path,
            color = it,
            alpha = paint.alpha,
            style = Stroke(
                width = paint.strokeWidth * k,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun VShape.toPath(): Path = when (this) {
    is VShape.D -> PathParser().parsePathString(data).toPath()

    is VShape.Circle -> Path().apply { addOval(Rect(Offset(cx, cy), r)) }

    is VShape.Ellipse -> Path().apply {
        addOval(Rect(Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f)))
    }

    is VShape.Box -> Path().apply {
        addRoundRect(
            RoundRect(
                left = x,
                top = y,
                right = x + w,
                bottom = y + h,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }

    is VShape.Line -> Path().apply {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }
}
