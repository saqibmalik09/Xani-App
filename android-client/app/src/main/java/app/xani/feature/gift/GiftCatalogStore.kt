package app.xani.feature.gift

import app.xani.core.isolate.runFeature
import app.xani.core.media.ImageLoader
import app.xani.core.net.GiftCatalogData
import app.xani.core.net.Xani
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The gift catalog every sheet reads. Loaded once, refreshed in the background
 * when older than a minute, served stale while a refresh is in flight so the
 * sheet opens with gifts on screen every time after the first. Listing images
 * are warmed into the image cache on load; animations and sounds are warmed
 * only when a gift is selected or lands in the room (see the overlay) — a
 * catalog of 300 gifts must not cost 300 downloads on open.
 */
internal object GiftCatalogStore {
    private val _state = MutableStateFlow(GiftCatalogData.Empty)
    val state: StateFlow<GiftCatalogData> = _state
    private val lock = Mutex()

    @Volatile
    private var loadedAt = 0L

    @Volatile
    private var failedAt = 0L

    private const val FRESH_MS = 60_000L
    private const val RETRY_MS = 5_000L
    private const val LISTING_EDGE = 240

    /** Returns the current catalog now; refreshes it if stale. */
    suspend fun load(): GiftCatalogData {
        val now = System.currentTimeMillis()
        val cur = _state.value
        if (cur.categories.isNotEmpty() && now - loadedAt < FRESH_MS) return cur
        if (cur.categories.isEmpty() && now - failedAt < RETRY_MS) return cur
        return lock.withLock {
            val again = _state.value
            if (again.categories.isNotEmpty() && System.currentTimeMillis() - loadedAt < FRESH_MS) return again
            val r = runFeature(null) { Xani.gifts.catalog() }
            val data = r?.value
            if (r?.ok == true && data != null) {
                loadedAt = System.currentTimeMillis()
                _state.value = data
                warmListings(data)
                data
            } else {
                failedAt = System.currentTimeMillis()
                again
            }
        }
    }

    /**
     * Warm the first visible screen of listing images (two rows past a 4-wide
     * grid). Prefetch runs on the loader's background lane, so even this burst
     * never delays a visible cell or starves a live stream of bandwidth —
     * anything the user scrolls to is promoted to the visible lane on demand.
     */
    private fun warmListings(data: GiftCatalogData) {
        var n = 0
        for (c in data.categories) {
            for (g in c.gifts) {
                if (n++ >= 16) return
                ImageLoader.prefetch(giftImageUrl(g.listingUrl), LISTING_EDGE)
            }
        }
    }

    /** A gift the user is about to send: get its heavy assets to disk now. */
    fun warm(gift: app.xani.core.net.GiftData) {
        if (gift.isSvga) SvgaLoader.prefetch(gift.animationUrl)
        if (gift.isLottie) LottieLoader.prefetch(giftImageUrl(gift.animationUrl))
        if (gift.isAnimatedImage) app.xani.core.media.AssetCache.prefetch(giftImageUrl(gift.animationUrl))
        if (gift.soundUrl.isNotEmpty()) GiftSound.prefetch(gift.soundUrl)
    }
}
