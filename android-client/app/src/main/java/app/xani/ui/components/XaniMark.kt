package app.xani.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.xani.core.media.RemoteImage
import app.xani.ui.brand.LocalBrand
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.WordmarkGradient
import app.xani.ui.theme.rdp

/**
 * The Xani mark: two rounded bars crossed at 45 degrees, with a cyan and a pink
 * echo offset behind the gradient face. Pure geometry, so it stays crisp at any
 * density and costs no drawable decode at startup.
 */
@Composable
fun XaniMark(size: Dp, modifier: Modifier = Modifier) {
    val logo = LocalBrand.current.logoUrl
    if (logo.isNotEmpty()) {
        // The dashboard logo (Settings → Branding) replaces the drawn mark
        // everywhere it appears. Prefetched on config load, so this is a
        // memory/disk hit; the drawn mark never shows under a transparent logo.
        val edge = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 512)
        RemoteImage(
            url = logo,
            modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
            contentScale = ContentScale.Fit,
            maxEdge = edge,
            fallback = Color.Transparent,
        )
        return
    }
    val px = with(LocalDensity.current) { size.toPx() }
    val shape = remember(px) { crossPath(px) }
    val face = remember(px) {
        Brush.linearGradient(
            colors = listOf(Dark.MarkStart, Dark.MarkEnd),
            start = Offset.Zero,
            end = Offset(px, px),
        )
    }

    Canvas(modifier.size(size)) {
        translate(left = px * 0.09f, top = -px * 0.065f) {
            clipPath(shape) { drawRect(color = Dark.Cyan, alpha = 0.55f) }
        }
        translate(left = -px * 0.065f, top = px * 0.09f) {
            clipPath(shape) { drawRect(color = Dark.AccentSoft, alpha = 0.42f) }
        }
        clipPath(shape) { drawRect(brush = face) }
    }
}

/**
 * Sizes are baseline (390dp-frame) values; responsive scaling is applied inside, so
 * callers must not pre-scale them.
 */
@Composable
fun XaniWordmark(
    wordmark: String,
    markSize: Float = 36f,
    textSize: Float = 36f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(markSize * 0.28f)),
    ) {
        XaniMark(size = rdp(markSize))
        XaniText(
            text = wordmark,
            style = Type.Wordmark,
            brush = WordmarkGradient,
            fontSizeOverride = textSize,
        )
    }
}

private fun crossPath(size: Float): Path {
    val barHeight = size * 0.225f
    val barWidth = size * 1.32f
    val left = (size - barWidth) / 2f
    val top = (size - barHeight) / 2f
    val radius = barHeight / 2f
    val center = size / 2f

    fun bar(degrees: Float) = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + barWidth,
                bottom = top + barHeight,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
        transform(
            Matrix().apply {
                translate(center, center)
                rotateZ(degrees)
                translate(-center, -center)
            },
        )
    }

    return Path().apply { op(bar(45f), bar(-45f), PathOperation.Union) }
}
