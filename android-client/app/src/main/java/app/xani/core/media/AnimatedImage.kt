package app.xani.core.media

import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import app.xani.core.isolate.runFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An animated WebP / GIF / APNG, playing in a loop — or the plain still it
 * turns out to be. The platform decoder (API 28+) does the frame timing; the
 * surface is a `drawBehind` like the SVGA player, so it takes no pointer input
 * and nothing under it goes deaf while a gift or a frame animates. Files come
 * through [AssetCache], so the bytes are downloaded once and decoded from disk
 * on every surface that shows them.
 *
 * On API 26–27 (no ImageDecoder) and for files that are not animated at all,
 * this is exactly [RemoteImage].
 */
@Composable
fun AnimatedRemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxEdge: Int = 1080,
    fallback: Color = Color.Transparent,
) {
    if (url.isEmpty()) {
        Box(modifier.background(fallback))
        return
    }
    if (Build.VERSION.SDK_INT < 28) {
        RemoteImage(url = url, modifier = modifier, contentScale = contentScale, maxEdge = maxEdge, fallback = fallback)
        return
    }
    // null = still loading; the sentinel distinguishes "decoded, not animated".
    var state by remember(url) { mutableStateOf<Any?>(null) }
    LaunchedEffect(url) {
        state = decodeAnimated(url, maxEdge) ?: NOT_ANIMATED
    }
    when (val s = state) {
        is AnimatedImageDrawable -> AnimatedDrawableSurface(s, modifier, contentScale)
        NOT_ANIMATED -> RemoteImage(url = url, modifier = modifier, contentScale = contentScale, maxEdge = maxEdge, fallback = fallback)
        else -> Box(modifier.background(fallback))
    }
}

private val NOT_ANIMATED = Any()

/**
 * Decodes [url] (downloading through [AssetCache] once) and returns the
 * animated drawable, or null when the file is a still / cannot decode. Each
 * caller gets its own drawable: a Drawable has exactly one callback, so two
 * avatars wearing the same frame cannot share one playback.
 */
private suspend fun decodeAnimated(url: String, maxEdge: Int): AnimatedImageDrawable? =
    withContext(Dispatchers.IO) {
        runFeature<AnimatedImageDrawable?>(null) {
            if (Build.VERSION.SDK_INT < 28) return@runFeature null
            val file = AssetCache.load(url) ?: return@runFeature null
            val src = android.graphics.ImageDecoder.createSource(file)
            val d = android.graphics.ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                val edge = maxOf(info.size.width, info.size.height)
                if (maxEdge in 1 until edge) {
                    val scale = maxEdge.toFloat() / edge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
            d as? AnimatedImageDrawable
        }
    }

/**
 * Draws [d] on the Compose canvas and lets it self-schedule frames through a
 * plain Drawable.Callback; each frame bumps one int state, which invalidates
 * only the draw phase — no recomposition per frame, same discipline as the
 * SVGA player.
 */
@Composable
private fun AnimatedDrawableSurface(d: AnimatedImageDrawable, modifier: Modifier, contentScale: ContentScale) {
    var tick by remember(d) { mutableIntStateOf(0) }
    DisposableEffect(d) {
        val handler = Handler(Looper.getMainLooper())
        d.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { tick++ }
            override fun scheduleDrawable(who: Drawable, what: Runnable, at: Long) { handler.postAtTime(what, at) }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what) }
        }
        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        d.start()
        onDispose {
            d.stop()
            d.callback = null
        }
    }
    Spacer(
        modifier.drawBehind {
            @Suppress("UNUSED_EXPRESSION")
            tick // subscribe: each frame invalidates this draw, nothing else
            val iw = d.intrinsicWidth.toFloat()
            val ih = d.intrinsicHeight.toFloat()
            if (iw <= 0f || ih <= 0f || size.width <= 0f || size.height <= 0f) return@drawBehind
            val s = when (contentScale) {
                ContentScale.Crop -> maxOf(size.width / iw, size.height / ih)
                else -> minOf(size.width / iw, size.height / ih)
            }
            val ox = (size.width - iw * s) / 2f
            val oy = (size.height - ih * s) / 2f
            drawIntoCanvas { c ->
                val save = c.nativeCanvas.save()
                c.nativeCanvas.translate(ox, oy)
                c.nativeCanvas.scale(s, s)
                d.setBounds(0, 0, iw.toInt(), ih.toInt())
                d.draw(c.nativeCanvas)
                c.nativeCanvas.restoreToCount(save)
            }
        },
    )
}
