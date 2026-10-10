package app.xani.feature.profile

import android.content.Context
import android.content.SharedPreferences
import app.xani.core.isolate.runFeature
import app.xani.core.net.FrameCatalogData
import app.xani.core.net.LiveFeed
import app.xani.core.net.OwnedFrameData
import app.xani.core.net.ShelfBonusData
import app.xani.core.net.ShelfCategoryData
import app.xani.core.net.StoreShelfData
import app.xani.core.net.Xani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The profile's store boxes — one per active category (Frame · Badge · Entry ·
 * VIP · …) plus Free — and what the user holds in each.
 *
 * Cache-then-network: the last good shelf is restored from disk before the
 * first frame, so the row never shows a spinner; a refresh runs when the
 * cached copy is older than a minute, and immediately after anything that
 * changes ownership (purchase, activation, a grant pushed over the socket).
 * Keyed by user so an account switch never paints the previous account's boxes.
 */
internal object StoreShelfStore {
    private const val PREFS = "xani.shelf"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_USER_ID = "user_id"
    private const val FRESH_MS = 60_000L
    private const val RETRY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(StoreShelfData.Empty)
    val state: StateFlow<StoreShelfData> = _state

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var userId = 0L

    @Volatile
    private var loadedAt = 0L

    @Volatile
    private var failedAt = 0L

    @Volatile
    private var inFlight: Job? = null

    fun attach(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            scope.launch { restore(p) }
            // A reconnect may have missed a grant pushed while offline: re-read.
            scope.launch { LiveFeed.resync.collect { if (userId != 0L) invalidate() } }
        }
    }

    /**
     * Makes sure the shelf shown belongs to [id] and is fresh enough. Cheap
     * to call from composition on every profile open.
     */
    fun ensure(id: Long) {
        if (id == 0L) return
        if (id != userId) {
            userId = id
            loadedAt = 0L
            failedAt = 0L
            val p = prefs
            // Different account: drop the previous one's boxes before anything
            // paints, then try the disk copy for this one.
            if (p == null || p.getLong(KEY_USER_ID, 0L) != id) _state.value = StoreShelfData.Empty
            if (p != null) restore(p)
        }
        refresh(force = false)
    }

    /** Ownership changed (bought, activated, granted): refetch now. */
    fun invalidate() {
        loadedAt = 0L
        failedAt = 0L
        refresh(force = true)
    }

    private fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - loadedAt < FRESH_MS) return
            if (now - failedAt < RETRY_MS) return
        }
        if (inFlight?.isActive == true) return
        val mine = userId
        inFlight = scope.launch {
            val r = runFeature(null) { Xani.frames.shelf() }
            val data = if (r?.ok == true) r.value else fromCatalog()
            if (mine != userId) return@launch
            if (data != null) {
                loadedAt = System.currentTimeMillis()
                _state.value = data
                persist(mine, data)
            } else {
                failedAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * A server without /v1/store/shelf still has the shop and the user's
     * items, and the boxes can be counted from those — so the profile is
     * never bare while a deploy is in flight. One extra pair of reads, only
     * on the fallback path.
     */
    private suspend fun fromCatalog(): StoreShelfData? {
        val catalog = runFeature(null) { Xani.frames.catalog() }?.value ?: return null
        val owned = runFeature(null) { Xani.frames.mine() }?.value.orEmpty()
        return build(catalog, owned)
    }

    private class Counts {
        var items = 0
        var owned = 0
        var active = 0
        var expired = 0
        var expiring = 0L
        var latest = 0L
        val kinds = HashMap<String, Int>()
    }

    private fun build(catalog: FrameCatalogData, owned: List<OwnedFrameData>): StoreShelfData {
        val byId = catalog.categories.associateBy { it.id }
        // Sub-categories roll up into the root the profile shows.
        fun rootOf(id: Long): Long {
            var cur = byId[id] ?: return 0L
            var depth = 0
            while (cur.parentId != 0L && depth++ < 16) cur = byId[cur.parentId] ?: return cur.id
            return cur.id
        }
        val itemRoot = catalog.items.associate { it.id to rootOf(it.categoryId) }
        val counts = HashMap<Long, Counts>()
        fun at(root: Long): Counts? =
            if (root == 0L) null else counts.getOrPut(root) { Counts() }

        for (item in catalog.items) {
            val c = at(itemRoot[item.id] ?: 0L) ?: continue
            c.items++
            c.kinds[item.kind] = (c.kinds[item.kind] ?: 0) + 1
        }
        var freeOwned = 0
        var freeActive = 0
        var freeExpired = 0
        var freeLatest = 0L
        for (o in owned) {
            // An older server does not say which category an owned item came
            // from; the shop item it was bought from does.
            val root = if (o.categoryId != 0L) rootOf(o.categoryId) else (itemRoot[o.itemId] ?: 0L)
            val c = at(root)
            if (c != null) {
                if (o.expired) {
                    c.expired++
                } else {
                    c.owned++
                    if (o.active) c.active++
                    if (o.expiresAtMs > 0L && (c.expiring == 0L || o.expiresAtMs < c.expiring)) c.expiring = o.expiresAtMs
                }
                if (o.createdAtMs > c.latest) c.latest = o.createdAtMs
            }
            if (o.isFree) {
                if (o.expired) {
                    freeExpired++
                } else {
                    freeOwned++
                    if (o.active) freeActive++
                }
                if (o.createdAtMs > freeLatest) freeLatest = o.createdAtMs
            }
        }
        val list = catalog.categories
            .filter { it.parentId == 0L }
            .map { cat ->
                // Every active top-level category gets its box, items in it or
                // not — the admin's Active toggle is the visibility switch, and
                // a just-created "VIP" must not stay invisible until its first
                // item lands. An empty box opens an honest empty listing.
                val c = counts[cat.id] ?: Counts()
                val kind = c.kinds.maxWithOrNull(
                    compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key },
                )?.key.orEmpty()
                ShelfCategoryData(
                    id = cat.id,
                    title = cat.title,
                    icon = cat.icon.ifEmpty { kind },
                    kind = kind,
                    sortOrder = cat.sortOrder,
                    items = c.items,
                    owned = c.owned,
                    active = c.active,
                    expired = c.expired,
                    expiringMs = c.expiring,
                    latestMs = c.latest,
                )
            }
            .sortedWith(compareBy<ShelfCategoryData> { it.sortOrder }.thenBy { it.id })
        return StoreShelfData(
            categories = list,
            bonus = ShelfBonusData(
                available = freeOwned > 0 || freeExpired > 0,
                owned = freeOwned,
                active = freeActive,
                expired = freeExpired,
                latestMs = freeLatest,
            ),
        )
    }

    private fun persist(id: Long, data: StoreShelfData) {
        val p = prefs ?: return
        runFeature(Unit) {
            p.edit().putString(KEY_PAYLOAD, toJson(data).toString()).putLong(KEY_USER_ID, id).apply()
        }
    }

    private fun restore(p: SharedPreferences) {
        val owner = p.getLong(KEY_USER_ID, 0L)
        if (owner == 0L || (userId != 0L && owner != userId)) return
        val raw = p.getString(KEY_PAYLOAD, null) ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val data = runFeature(null) { StoreShelfData.from(json) } ?: return
        // Only fill an empty shelf: a live response must never be overwritten
        // by the disk copy that raced it.
        if (_state.value.categories.isEmpty() && loadedAt == 0L) _state.value = data
    }

    private fun toJson(data: StoreShelfData): JSONObject = JSONObject()
        .put("categories", JSONArray().also { arr -> data.categories.forEach { arr.put(toJson(it)) } })
        .put("bonus", toJson(data.bonus))

    private fun toJson(c: ShelfCategoryData): JSONObject = JSONObject()
        .put("id", c.id.toString())
        .put("title", c.title)
        .put("icon", c.icon)
        .put("kind", c.kind)
        .put("sort_order", c.sortOrder)
        .put("items", c.items)
        .put("owned", c.owned)
        .put("active", c.active)
        .put("expired", c.expired)
        .put("expiring_ms", c.expiringMs)
        .put("latest_ms", c.latestMs)

    private fun toJson(b: ShelfBonusData): JSONObject = JSONObject()
        .put("available", b.available)
        .put("owned", b.owned)
        .put("active", b.active)
        .put("expired", b.expired)
        .put("latest_ms", b.latestMs)
}
