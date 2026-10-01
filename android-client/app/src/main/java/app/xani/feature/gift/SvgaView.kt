package app.xani.feature.gift

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Plays one [SvgaMovie] straight onto the Compose hardware canvas.
 *
 * Why not a View: a full-screen `AndroidView` is a pointer-input node, and in
 * Compose the topmost sibling under the finger takes the touch — it would
 * swallow every tap on the broadcast beneath it. A `drawBehind` surface has
 * no pointer input at all, so the chrome under a playing gift stays live.
 *
 * Frame index is derived from the clock, not counted: a dropped frame skips
 * forward instead of stretching the gift, so every viewer sees the same
 * three-second gift take three seconds. The draw allocates nothing — one
 * Matrix and one Paint are reused, sprite bitmaps are drawn straight from the
 * movie (uploaded to the GPU once), transforms are read from flat float arrays.
 * Only the draw phase re-runs per frame; nothing recomposes.
 */
@Stable
internal class SvgaPlayerState {
    var movie by mutableStateOf<SvgaMovie?>(null)
        private set
    private var frame by mutableIntStateOf(0)

    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scratch = FloatArray(9)

    /**
     * Plays [m] for [loops] passes (≤ 0 = until cancelled) and returns when
     * done. Cancelling the caller stops the gift at once.
     */
    suspend fun play(m: SvgaMovie, loops: Int = 1) {
        val total = m.durationMs
        if (total <= 0L || m.frames <= 0) return
        movie = m
        frame = 0
        try {
            var start = -1L
            while (true) {
                val now = withFrameNanos { it } / 1_000_000L
                if (start < 0L) start = now
                val elapsed = now - start
                val loop = (elapsed / total).toInt()
                if (loops > 0 && loop >= loops) { frame = m.frames - 1; break }
                frame = ((elapsed % total) * m.fps / 1000L).toInt().coerceIn(0, m.frames - 1)
            }
        } finally {
            movie = null
        }
    }

    fun stop() { movie = null }

    internal fun draw(canvas: android.graphics.Canvas, size: Size) {
        val m = movie ?: return
        val f = frame
        if (size.width <= 0f || size.height <= 0f || m.width <= 0f || m.height <= 0f) return
        val scale = minOf(size.width / m.width, size.height / m.height)
        val offsetX = (size.width - m.width * scale) / 2f
        val offsetY = (size.height - m.height * scale) / 2f
        val sprites = m.sprites
        for (i in sprites.indices) {
            val s = sprites[i]
            val alpha = s.alpha(f)
            if (alpha <= 0.001f) continue
            val bmp = m.bitmaps[s.imageKey] ?: continue
            if (bmp.isRecycled) continue
            val w = s.w(f)
            val h = s.h(f)
            // Sub-sampled sprite → its layout box in view-box units → frame
            // transform → aspect-fit into our bounds.
            val sx = if (w > 0f) w / bmp.width else 1f
            val sy = if (h > 0f) h / bmp.height else 1f
            scratch[0] = s.a(f) * sx; scratch[1] = s.c(f) * sy; scratch[2] = s.tx(f)
            scratch[3] = s.b(f) * sx; scratch[4] = s.d(f) * sy; scratch[5] = s.ty(f)
            scratch[6] = 0f; scratch[7] = 0f; scratch[8] = 1f
            matrix.setValues(scratch)
            matrix.postScale(scale, scale)
            matrix.postTranslate(offsetX, offsetY)
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp, matrix, paint)
        }
    }
}

/** The surface: no pointer input, redraws only while a movie is playing. */
@Composable
internal fun SvgaPlayer(state: SvgaPlayerState, modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawBehind {
            if (state.movie == null) return@drawBehind
            drawIntoCanvas { c -> state.draw(c.nativeCanvas, size) }
        },
    )
}
