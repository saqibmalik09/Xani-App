package app.xani.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.xani.ui.theme.LocalUiScale
import app.xani.ui.theme.responsive
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp

/**
 * Single text entry point so responsive scaling is applied once, centrally, and
 * no screen can accidentally hardcode an unscaled size.
 */
@Composable
fun XaniText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    brush: Brush? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontSizeOverride: Float? = null,
) {
    val scale = LocalUiScale.current
    val resolved = style.responsive()
    val finalStyle = remember(resolved, color, brush, align, fontSizeOverride, scale) {
        var s = resolved
        if (fontSizeOverride != null) s = s.copy(fontSize = (fontSizeOverride * scale).sp)
        if (brush != null) s = s.copy(brush = brush)
        else if (color != Color.Unspecified) s = s.copy(color = color)
        if (align != null) s = s.copy(textAlign = align)
        s
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = finalStyle,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun XaniText(
    text: androidx.compose.ui.text.AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val scale = LocalUiScale.current
    val resolved = style.responsive()
    val finalStyle = remember(resolved, align, scale) {
        if (align != null) resolved.copy(textAlign = align) else resolved
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = finalStyle,
        maxLines = maxLines,
        overflow = overflow,
    )
}
