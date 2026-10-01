package app.xani.feature.gift

import android.util.LruCache
import app.xani.core.isolate.runFeature
import app.xani.core.media.AssetCache
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Decoded Lottie gifts, by URL. Same contract as [SvgaLoader]: [prefetch] on
 * selection or on a banner so the play that follows finds the composition in
 * memory; [load] downloads through [AssetCache] and parses off the main
 * thread; a file that will not parse is not retried for a minute.
 *
 * Parsing is the expensive part of Lottie (a 2 MB JSON is tens of ms on a
 * flagship, hundreds on a 2 GB phone), so it happens exactly once per URL and
 * never on the frame that shows the gift.
 */
internal object LottieLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Semaphore(2)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<LottieComposition?>>()
    private val failed = ConcurrentHashMap<String, Long>()

    /** Compositions are keyframe trees, not bitmaps: a dozen is a few MB. */
    private val cache = LruCache<String, LottieComposition>(12)

    fun cached(url: String): LottieComposition? = synchronized(cache) { cache.get(url) }

    fun prefetch(url: String) {
        if (url.isEmpty() || cached(url) != null) return
        scope.launch { load(url) }
    }

    suspend fun load(url: String): LottieComposition? {
        if (url.isEmpty()) return null
        cached(url)?.let { return it }
        failed[url]?.let { if (System.currentTimeMillis() < it) return null }
        val mine = CompletableDeferred<LottieComposition?>()
        val prior = inflight.putIfAbsent(url, mine)
        if (prior != null) return prior.await()
        scope.launch {
            var downloaded = false
            val composition = runFeature<LottieComposition?>(null) {
                val file = AssetCache.load(url) ?: return@runFeature null
                downloaded = true
                gate.withPermit { decode(file) }
            }
            when {
                composition != null -> synchronized(cache) { cache.put(url, composition) }
                downloaded -> failed[url] = System.currentTimeMillis() + BAD_FILE_MS
                else -> failed[url] = System.currentTimeMillis() + RETRY_NET_MS
            }
            inflight.remove(url)
            mine.complete(composition)
        }
        return mine.await()
    }

    fun clear() = synchronized(cache) { cache.evictAll() }

    private fun decode(file: File): LottieComposition? {
        if (file.length() <= 0L || file.length() > MAX_BYTES) return null
        // cacheKey null: Lottie's own in-process cache is bypassed; this one is
        // bounded and shared with the rest of the gift pipeline.
        return FileInputStream(file).use { LottieCompositionFactory.fromJsonInputStreamSync(it, null).value }
    }

    private const val BAD_FILE_MS = 60_000L
    private const val RETRY_NET_MS = 4_000L
    private const val MAX_BYTES = 24L shl 20
}
