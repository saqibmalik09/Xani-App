package app.xani.feature.gift

import app.xani.core.isolate.runFeature
import app.xani.core.media.ImageLoader
import app.xani.core.net.StickerCatalogData
import app.xani.core.net.Xani
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The chat sticker tray every room reads. Loaded once, refreshed in the
 * background when older than a minute, served stale while a refresh is in
 * flight so the tray opens with pictures on screen every time after the
 * first. The first two rows are warmed into the image cache on load; the
 * rest load as the grid scrolls.
 */
internal object StickerCatalogStore {
    private val _state = MutableStateFlow(StickerCatalogData.Empty)
    val state: StateFlow<StickerCatalogData> = _state
    private val lock = Mutex()

    @Volatile
    private var loadedAt = 0L

    @Volatile
    private var failedAt = 0L

    private const val FRESH_MS = 60_000L
    private const val RETRY_MS = 5_000L
    private const val CELL_EDGE = 160

    /** Returns the current tray now; refreshes it if stale. */
    suspend fun load(): StickerCatalogData {
        val now = System.currentTimeMillis()
        val cur = _state.value
        if (cur.categories.isNotEmpty() && now - loadedAt < FRESH_MS) return cur
        if (cur.categories.isEmpty() && now - failedAt < RETRY_MS) return cur
        return lock.withLock {
            val again = _state.value
            if (again.categories.isNotEmpty() && System.currentTimeMillis() - loadedAt < FRESH_MS) return again
            val r = runFeature(null) { Xani.gifts.stickerCatalog() }
            val data = r?.value
            if (r?.ok == true && data != null) {
                loadedAt = System.currentTimeMillis()
                _state.value = data
                warm(data)
                data
            } else {
                failedAt = System.currentTimeMillis()
                again
            }
        }
    }

    private fun warm(data: StickerCatalogData) {
        var n = 0
        for (c in data.categories) {
            for (s in c.stickers) {
                if (n++ >= 20) return
                // Still pictures only: an animated sticker decodes on first draw.
                if (s.imageMime == "image/png" || s.imageMime == "image/jpeg") {
                    ImageLoader.prefetch(giftImageUrl(s.imageUrl), CELL_EDGE)
                }
            }
        }
    }
}
