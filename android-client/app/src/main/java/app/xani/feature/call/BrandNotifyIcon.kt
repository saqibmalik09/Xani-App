package app.xani.feature.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import app.xani.R
import app.xani.core.isolate.runFeature
import app.xani.core.media.AssetCache
import app.xani.core.net.MediaConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Accent tint for every tray entry's small icon; matches the server's FCM `color`. */
val BRAND_COLOR: Int = 0xFF7C3AED.toInt()

/**
 * Large notification icon: dashboard logo, else favicon, otherwise the
 * launcher foreground mark. Status-bar small icons stay [R.drawable.ic_stat_brand]
 * (monochrome) — Android will not tint a full-colour favicon in the status bar.
 */
object BrandNotifyIcon {
    private val lock = Mutex()

    @Volatile
    private var cached: Bitmap? = null

    @Volatile
    private var cachedUrl: String = ""

    suspend fun large(context: Context): Bitmap? = lock.withLock {
        val url = MediaConfig.logoUrl().ifEmpty { MediaConfig.faviconUrl() }
        if (url.isNotEmpty() && url == cachedUrl) cached?.let { return it }
        if (url.isNotEmpty()) {
            val fromNet = loadUrl(url)
            if (fromNet != null) {
                cached = fromNet
                cachedUrl = url
                return fromNet
            }
        }
        cached?.let { return it }
        val fallback = loadFallback(context.applicationContext)
        cached = fallback
        cachedUrl = ""
        fallback
    }

    private suspend fun loadUrl(url: String): Bitmap? = runFeature(null) {
        val file = AssetCache.load(url) ?: return@runFeature null
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return@runFeature null
        round(raw, 192)
    }

    private fun loadFallback(ctx: Context): Bitmap? = runFeature(null) {
        val raw = BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_launcher_foreground)
            ?: return@runFeature null
        round(raw, 192)
    }

    private fun round(src: Bitmap, edge: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, edge, edge, true)
        val out = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.clipPath(
            Path().apply {
                addRoundRect(
                    0f, 0f, edge.toFloat(), edge.toFloat(),
                    edge * 0.22f, edge * 0.22f,
                    Path.Direction.CW,
                )
            },
        )
        c.drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== src) scaled.recycle()
        return out
    }
}
