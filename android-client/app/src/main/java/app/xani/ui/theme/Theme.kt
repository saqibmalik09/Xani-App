package app.xani.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.xani.ui.system.ProvideSystemNav

/**
 * Reads the window width from the configuration rather than measuring with
 * `BoxWithConstraints`, so the theme root does not force a subcomposition of the
 * whole app on every launch. Appearance follows the device; light is the default
 * when night mode is off or unset.
 */
@Composable
fun XaniTheme(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val scale = remember(widthDp) { scaleFor(widthDp) }
    val colors = if (isSystemInDarkTheme()) NightColors else LightColors
    val selection = remember {
        TextSelectionColors(
            handleColor = Dark.Primary,
            backgroundColor = Dark.Primary.copy(alpha = 0.28f),
        )
    }

    CompositionLocalProvider(
        LocalUiScale provides scale,
        LocalTextSelectionColors provides selection,
        LocalXaniColors provides colors,
    ) {
        ProvideSystemNav(content)
    }
}

/**
 * Paints the window the same colour as the current screen so status and gesture
 * strips never flash a leftover black or white behind a transparent bar.
 */
@Composable
fun MatchWindowTo(color: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    val argb = color.toArgb()
    SideEffect {
        window.setBackgroundDrawable(ColorDrawable(argb))
    }
}

/**
 * Dark glyphs on light surfaces, light glyphs on dark ones. Omit [lightBackground]
 * to follow the device; cinematic screens pass false so they stay dark.
 */
@Composable
fun SystemBarIcons(lightBackground: Boolean? = null) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    val light = lightBackground ?: !Colors.isDark
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
