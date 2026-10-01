package app.xani.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Figma frame is 390dp wide. Every dimension in the screens is authored at that
 * baseline and multiplied by this factor, clamped so small phones stay legible
 * and tablets do not inflate into cartoon proportions.
 */
val LocalUiScale = staticCompositionLocalOf { 1f }

const val BaselineWidthDp = 390f
const val ContentMaxWidthDp = 460f

fun scaleFor(widthDp: Float): Float = (widthDp / BaselineWidthDp).coerceIn(0.82f, 1.18f)

@Composable
@ReadOnlyComposable
fun rdp(value: Number): Dp = (value.toFloat() * LocalUiScale.current).dp

@Composable
@ReadOnlyComposable
fun TextStyle.responsive(): TextStyle {
    val s = LocalUiScale.current
    if (s == 1f) return this
    return copy(
        fontSize = fontSize.scale(s),
        lineHeight = lineHeight.scale(s),
        letterSpacing = letterSpacing.scale(s),
    )
}

private fun TextUnit.scale(factor: Float): TextUnit = if (isSpecified) this * factor else this
