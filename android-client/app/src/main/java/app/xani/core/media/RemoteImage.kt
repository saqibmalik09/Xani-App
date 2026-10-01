package app.xani.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import app.xani.core.isolate.runFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Remote stills for feed posters, grid thumbs and avatars.
 *
 * Three tiers, all keyed by URL: a bitmap LRU sized to the device heap, an
 * immutable on-disk cache (media keys are content-addressed, so a file never goes
 * stale), and the network. Concurrent requests for one URL share a single
 * download; at most six downloads run at once so a fast grid scroll cannot open
 * a hundred sockets. Nothing here touches the main thread but the final paint.
 */
object ImageLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(6)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    @Volatile
    private var dir: File? = null

    private val mem = object : LruCache<String, Bitmap>(memBudgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun memBudgetKb(): Int {
        val max = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (max / 8).coerceIn(16 * 1024, 96 * 1024)
    }

    fun attach(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.cacheDir, "img").apply { mkdirs() }
    }

    fun cached(url: String, maxEdge: Int): Bitmap? = synchronized(mem) { mem.get(key(url, maxEdge)) }

    /** Warms memory + disk for a URL the user is about to see (next feed page). */
    fun prefetch(url: String, maxEdge: Int) {
        if (url.isEmpty() || cached(url, maxEdge) != null) return
        scope.launch { load(url, maxEdge) }
    }

    suspend fun load(url: String, maxEdge: Int): Bitmap? {
        if (url.isEmpty()) return null
        val k = key(url, maxEdge)
        cached(url, maxEdge)?.let { return it }
        // Singleflight: the first caller owns the download, later ones await it.
        val mine = CompletableDeferred<Bitmap?>()
        val prior = inflight.putIfAbsent(k, mine)
        if (prior != null) return prior.await()
        scope.launch {
            val bmp = runFeature<Bitmap?>(null) { fetch(url, maxEdge) }
            inflight.remove(k)
            mine.complete(bmp)
        }
        return mine.await()
    }

    private suspend fun fetch(url: String, maxEdge: Int): Bitmap? = gate.withPermit {
        val file = diskFile(url)
        val bytes = if (file != null && file.length() > 0) {
            file.readBytes()
        } else {
            download(url)?.also { data -> file?.let { writeAtomic(it, data) } }
        } ?: return@withPermit null
        val bmp = decode(bytes, maxEdge) ?: return@withPermit null
        synchronized(mem) { mem.put(key(url, maxEdge), bmp) }
        bmp
    }

    private fun download(url: String): ByteArray? {
        var c: HttpURLConnection? = null
        return try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            if (c.responseCode != 200) return null
            c.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun decode(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val edge = maxOf(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = if (edge > maxEdge && maxEdge > 0) maxOf(1, edge / maxEdge) else 1
            inPreferredConfig = if (maxEdge <= 320) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun diskFile(url: String): File? = dir?.let { File(it, sha1(url)) }

    private fun writeAtomic(target: File, data: ByteArray) {
        runFeature(Unit) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeBytes(data)
            if (!tmp.renameTo(target)) tmp.delete()
        }
    }

    private fun key(url: String, maxEdge: Int) = "$url#$maxEdge"

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Paints a remote still, or [fallback] until it arrives. Memory hits paint on the
 * first frame with no state change; everything else resolves off the UI thread.
 */
@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 1080,
    fallback: Color = Color(0xFF1A1A1E),
    onReady: (() -> Unit)? = null,
) {
    var bitmap by remember(url) { mutableStateOf(if (url.isEmpty()) null else ImageLoader.cached(url, maxEdge)) }
    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { ImageLoader.load(url, maxEdge) }
        }
        if (bitmap != null) onReady?.invoke()
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            painter = BitmapPainter(bmp.asImageBitmap()),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier.background(fallback))
    }
}
