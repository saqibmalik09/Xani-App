package app.xani.ui.system

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.xani.ui.components.NetworkSpeedBadge

/**
 * Gesture vs 3-button (or 2-button) navigation. Bottom inset is the window’s
 * navigation bar only. A floor is used only when the OEM reports 0, so the app
 * meets the bar with no extra strip and does not draw into the icons.
 */
enum class NavKind {
    Gesture,
    Buttons,
}

@Immutable
data class SystemNav(
    val kind: NavKind,
    val bottomFloorPx: Int,
) {
    companion object {
        val Unspecified = SystemNav(NavKind.Gesture, 0)
    }
}

val LocalSystemNav = compositionLocalOf { SystemNav.Unspecified }

private val NavSettingUris = arrayOf(
    "navigation_mode",
    "hide_navigationbar_enable",
)

private val NavGlobalUris = arrayOf(
    "force_fsg_nav_bar",
    "navigationbar_is_min",
)

/**
 * Owns detection for the whole tree under [app.xani.ui.theme.XaniTheme].
 * Does not read Compose [WindowInsets] for detection: that would recompose the
 * app on every IME frame. Screens paint edge-to-edge; content pads via
 * [systemContentInsets].
 */
@Composable
fun ProvideSystemNav(content: @Composable () -> Unit) {
    val view = LocalView.current
    val density = LocalDensity.current
    val navState = remember { mutableStateOf(SystemNav.read(view, density)) }

    DisposableEffect(view, density) {
        fun refresh() {
            val next = SystemNav.read(view, density)
            if (next != navState.value) navState.value = next
        }

        fun poke() {
            ViewCompat.requestApplyInsets(view)
            view.post { refresh() }
        }

        refresh()
        view.post { refresh() }

        val focus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) poke()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)

        val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = poke()
        }
        val resolver = view.context.contentResolver
        NavSettingUris.forEach { key ->
            runCatching {
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, settings)
            }
        }
        NavGlobalUris.forEach { key ->
            runCatching {
                resolver.registerContentObserver(Settings.Global.getUriFor(key), false, settings)
            }
        }

        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            }
            runCatching { resolver.unregisterContentObserver(settings) }
        }
    }

    CompositionLocalProvider(LocalSystemNav provides navState.value) {
        Box(Modifier.fillMaxSize()) {
            content()
            NetworkSpeedBadge(Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * Pads interactive content below the status/cutout and above the nav bar / IME.
 * Backgrounds must paint the full window; only content uses this.
 */
@Composable
fun Modifier.systemContentInsets(): Modifier {
    val floor = LocalSystemNav.current.bottomFloorPx
    val drawing = WindowInsets.safeDrawing
    val navBars = WindowInsets.navigationBars
    val ime = WindowInsets.ime
    val insets = remember(drawing, navBars, ime, floor) {
        ViewportInsets(drawing, navBars, ime, floor)
    }
    return this.then(Modifier.windowInsetsPadding(insets))
}

private class ViewportInsets(
    private val drawing: WindowInsets,
    private val navBars: WindowInsets,
    private val ime: WindowInsets,
    private val floorPx: Int,
) : WindowInsets {
    override fun getTop(density: Density) = drawing.getTop(density)

    override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
        drawing.getLeft(density, layoutDirection)

    override fun getRight(density: Density, layoutDirection: LayoutDirection) =
        drawing.getRight(density, layoutDirection)

    override fun getBottom(density: Density): Int {
        val nav = navBars.getBottom(density)
        val keyboard = ime.getBottom(density)
        val raw = maxOf(nav, keyboard)
        return if (raw > 0) raw else floorPx
    }
}

internal fun SystemNav.Companion.read(view: View, density: Density): SystemNav {
    val root = ViewCompat.getRootWindowInsets(view)
    val nav = root?.getInsets(WindowInsetsCompat.Type.navigationBars())
    val tappable = root?.getInsets(WindowInsetsCompat.Type.tappableElement())
    val navBottom = nav?.bottom ?: 0
    val navSide = (nav?.left ?: 0) + (nav?.right ?: 0)
    val tappableBottom = tappable?.bottom ?: 0
    val mode = navigationMode(view)
    val kind = detectKind(navBottom, tappableBottom, navSide, mode, density)
    val floor = when {
        navSide > 0 || navBottom > 0 -> 0
        kind == NavKind.Buttons -> with(density) { 48.dp.roundToPx() }
        else -> with(density) { 16.dp.roundToPx() }
    }
    return SystemNav(kind, floor)
}

private fun navigationMode(view: View): Int {
    if (Build.VERSION.SDK_INT < 29) return 0
    return runCatching {
        Settings.Secure.getInt(view.context.contentResolver, "navigation_mode", -1)
    }.getOrDefault(-1)
}

private fun detectKind(
    navBottom: Int,
    tappableBottom: Int,
    navSide: Int,
    mode: Int,
    density: Density,
): NavKind {
    if (navSide > 0) return NavKind.Buttons
    when (mode) {
        2 -> return NavKind.Gesture
        0, 1 -> return NavKind.Buttons
    }
    val buttonsMin = with(density) { 40.dp.roundToPx() }
    if (tappableBottom == 0 && navBottom < buttonsMin) return NavKind.Gesture
    if (tappableBottom >= buttonsMin || navBottom >= buttonsMin) return NavKind.Buttons
    return NavKind.Gesture
}
