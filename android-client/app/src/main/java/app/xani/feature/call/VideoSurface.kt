package app.xani.feature.call

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Mounts an engine-owned video renderer.
 *
 * The engine hands out one cached renderer per participant, and a layout
 * change (the PK arena rising, a seat moving tiles, full-screen folding into a
 * card) can compose the new home before the old `AndroidView` is disposed.
 * `addView` on a view that still has a parent is a RuntimeException that takes
 * the whole app — and the broadcast — down. Stealing the view from its old
 * parent first makes the move safe: the old holder's own cleanup `removeView`
 * is a no-op on a child it no longer has.
 */
@Composable
fun VideoSurface(view: View, modifier: Modifier = Modifier) {
    key(view) {
        AndroidView(
            factory = {
                (view.parent as? ViewGroup)?.removeView(view)
                view
            },
            modifier = modifier,
        )
    }
}
