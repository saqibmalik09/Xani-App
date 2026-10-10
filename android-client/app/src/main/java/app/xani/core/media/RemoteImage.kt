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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remote stills for feed posters, grid thumbs and avatars.
 *
 * Three tiers, all keyed by URL: a bitmap LRU sized to the device heap, an
 * immutable on-disk cache (media keys are content-addressed, so a file never goes
 * stale), and the network. Concurrent requests for one URL share a single
 * download (singleflight).
 *
 * Two priority lanes keep the app responsive while a live stream plays:
 * what the user can SEE ([load]) runs on four dedicated workers plus two flex
 * workers; speculative warming ([prefetch]) runs only on the two flex workers,
 * and the flex workers always drain visible work first. A prefetch burst can
 * therefore never queue ahead of a visible cell, and at most two background
 * downloads compete with a live video for bandwidth. A visible request for a
 * URL still waiting in the prefetch lane is promoted to the visible lane.
 * Nothing here touches the main thread but the final paint.
 */
object ImageLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One keyed request: everyone waiting for this URL awaits [done]. */
    private class Task(val url: String, val maxEdge: Int, val k: String) {
        val done = CompletableDeferred<Bitmap?>()

        /** A task can sit in both lanes after a promotion; only one run wins. */
        val started = AtomicBoolean(false)
    }

    private val inflight = ConcurrentHashMap<String, Task>()
    private val visibleLane = Channel<Task>(Channel.UNLIMITED)
    private val prefetchLane = Channel<Task>(Channel.UNLIMITED)

    init {
        // Four lanes that only ever serve what is on screen…
        repeat(4) { scope.launch { for (t in visibleLane) perform(t) } }
        // …and two flex lanes: visible work first, prefetch when idle.
        repeat(2) {
            scope.launch {
                while (true) {
                    // select is biased to its first clause, which is the priority.
                    val t = select<Task> {
                        visibleLane.onReceive { it }
                        prefetchLane.onReceive { it }
                    }
                    perform(t)
                }
            }
        }
    }

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

    /** Warms memory + disk for a URL the user is about to see. Never delays a visible load. */
    fun prefetch(url: String, maxEdge: Int) {
        if (url.isEmpty() || cached(url, maxEdge) != null) return
        val t = Task(url, maxEdge, key(url, maxEdge))
        if (inflight.putIfAbsent(t.k, t) == null) prefetchLane.trySend(t)
    }

    /** Resolves a URL the user is looking at right now. */
    suspend fun load(url: String, maxEdge: Int): Bitmap? {
        if (url.isEmpty()) return null
        cached(url, maxEdge)?.let { return it }
        val k = key(url, maxEdge)
        val mine = Task(url, maxEdge, k)
        val prior = inflight.putIfAbsent(k, mine)
        if (prior != null) {
            // Already queued or downloading. If it is still waiting in the
            // prefetch lane, bump it to the visible lane; the started flag
            // turns the leftover queue entry into a no-op.
            if (!prior.started.get()) visibleLane.trySend(prior)
            return prior.done.await()
        }
        visibleLane.trySend(mine)
        return mine.done.await()
    }

    private suspend fun perform(t: Task) {
        if (!t.started.compareAndSet(false, true)) return // promoted duplicate
        val bmp = runFeature<Bitmap?>(null) { fetch(t.url, t.maxEdge) }
        inflight.remove(t.k)
        t.done.complete(bmp)
    }

    private fun fetch(url: String, maxEdge: Int): Bitmap? {
        val file = diskFile(url)
        val bytes = if (file != null && file.length() > 0) {
            file.readBytes()
        } else {
            download(url)?.also { data -> file?.let { writeAtomic(it, data) } }
        } ?: return null
        val bmp = decode(bytes, maxEdge) ?: return null
        synchronized(mem) { mem.put(key(url, maxEdge), bmp) }
        return bmp
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
        // Remembered so scroll/drag recompositions reuse the painter instead of
        // re-wrapping the bitmap every frame.
        val painter = remember(bmp) { BitmapPainter(bmp.asImageBitmap()) }
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier.background(fallback))
    }
}
