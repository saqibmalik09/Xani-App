package app.xani.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Holds the screen awake while the composable is on screen — a broadcast
 * (hosting or watching) must never dim or lock mid-stream. Scoped to the
 * composition: leaving the room releases the flag, so the rest of the app
 * keeps the system's normal sleep behaviour.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
