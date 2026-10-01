package app.xani.feature.home

import app.xani.core.isolate.runFeature
import app.xani.core.media.ImageLoader
import app.xani.core.net.BannerData
import app.xani.core.net.MediaConfig
import app.xani.core.net.Xani
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Live-browse promo banners (under All / Video / Audio + Country).
 *
 * Cache-then-network: the last good list paints immediately; a background refresh
 * runs when older than a minute. Images are warmed into [ImageLoader] (memory +
 * disk) so the carousel never blocks the UI thread on decode.
 */
internal object BannerStore {
    const val PLACEMENT_LIVE_BROWSE = "live_browse"
    private const val FRESH_MS = 60_000L
    private const val RETRY_MS = 5_000L
    private const val EDGE = 960

    private val _items = MutableStateFlow<List<BannerData>>(emptyList())
    val items: StateFlow<List<BannerData>> = _items

    private val lock = Mutex()

    @Volatile
    private var loadedAt = 0L

    @Volatile
    private var failedAt = 0L

    /** Absolute media URL for a banner image key. */
    fun imageUrl(key: String): String =
        if (key.startsWith("http://") || key.startsWith("https://")) key else MediaConfig.url(key)

    /** Returns cached list; refreshes when stale. Safe to call from composition. */
    suspend fun load(placement: String = PLACEMENT_LIVE_BROWSE): List<BannerData> {
        val now = System.currentTimeMillis()
        val cur = _items.value
        if (cur.isNotEmpty() && now - loadedAt < FRESH_MS) return cur
        if (cur.isEmpty() && now - failedAt < RETRY_MS) return cur
        return lock.withLock {
            val again = _items.value
            if (again.isNotEmpty() && System.currentTimeMillis() - loadedAt < FRESH_MS) return again
            val r = runFeature(null) { Xani.banners.list(placement) }
            val data = r?.value
            if (r?.ok == true && data != null) {
                loadedAt = System.currentTimeMillis()
                _items.value = data
                warm(data)
                data
            } else {
                failedAt = System.currentTimeMillis()
                again
            }
        }
    }

    private fun warm(list: List<BannerData>) {
        for (b in list) {
            val url = imageUrl(b.imageKey)
            if (url.isNotEmpty()) ImageLoader.prefetch(url, EDGE)
        }
    }
}
