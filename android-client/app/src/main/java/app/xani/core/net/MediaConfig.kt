package app.xani.core.net

import android.content.Context
import android.content.SharedPreferences
import app.xani.core.media.AssetCache
import app.xani.feature.call.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The media half of `/v1/config`: where relative keys resolve and what the
 * server will accept. Cached in prefs so the first frame after a cold start can
 * resolve a thumbnail without a request; refreshed in the background.
 *
 * Also caches [firebaseWeb] and [faviconKey] so push can initialise FCM and
 * brand tray notifications without a shipped `google-services.json`.
 */
object MediaConfig {
    private const val PREFS = "xani.media"
    private const val KEY_BASE = "base"
    private const val KEY_WEB = "web"
    private const val KEY_VIDEO_SEC = "video_sec"
    private const val KEY_VIDEO_MB = "video_mb"
    private const val KEY_IMAGE_MB = "image_mb"
    private const val KEY_CAPTION = "caption"
    private const val KEY_FIREBASE = "firebase_web"
    private const val KEY_FAVICON = "favicon"
    private const val KEY_AT = "at"
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L
    private const val DEFAULT_MB = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = Mutex()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    var baseUrl: String = ""
        private set

    /**
     * The public site shared links point at, as the deployment set it. Empty
     * until `/v1/config` has answered once — a share sheet with nothing to
     * share is better than one carrying somebody else's domain.
     */
    @Volatile
    var webBaseUrl: String = ""
        private set

    @Volatile
    var maxVideoSeconds: Int = 600
        private set

    // Fallbacks only, for the first launch before /v1/config answers. Kept equal
    // to the server's defaults so a file the server would take is never refused
    // locally, and one it would reject is never uploaded for nothing.
    @Volatile
    var maxVideoBytes: Long = DEFAULT_MB shl 20
        private set

    @Volatile
    var maxImageBytes: Long = DEFAULT_MB shl 20
        private set

    @Volatile
    var maxCaption: Int = 2200
        private set

    /** Relative key for the dashboard favicon; resolve with [faviconUrl]. */
    @Volatile
    var faviconKey: String = ""
        private set

    @Volatile
    var firebaseWeb: FirebaseWebConfig? = null
        private set

    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        baseUrl = p.getString(KEY_BASE, "").orEmpty()
        webBaseUrl = p.getString(KEY_WEB, "").orEmpty()
        maxVideoSeconds = p.getInt(KEY_VIDEO_SEC, 600)
        maxVideoBytes = p.getInt(KEY_VIDEO_MB, DEFAULT_MB.toInt()).toLong() shl 20
        maxImageBytes = p.getInt(KEY_IMAGE_MB, DEFAULT_MB.toInt()).toLong() shl 20
        maxCaption = p.getInt(KEY_CAPTION, 2200)
        faviconKey = p.getString(KEY_FAVICON, "").orEmpty()
        firebaseWeb = p.getString(KEY_FIREBASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let(FirebaseWebConfig::from)
        if (baseUrl.isEmpty() || System.currentTimeMillis() - p.getLong(KEY_AT, 0L) > REFRESH_MS) {
            scope.launch { refresh() }
        } else {
            warmBrand()
            if (firebaseWeb != null) PushRegistrar.sync()
        }
    }

    /** Absolute URL for a relative key, or empty when the key or base is missing. */
    fun url(key: String?): String {
        if (key.isNullOrEmpty()) return ""
        val base = baseUrl
        if (base.isEmpty()) return ""
        return "$base/$key"
    }

    /** Absolute URL for the product favicon, or empty when unset. */
    fun faviconUrl(): String = url(faviconKey.ifEmpty { null })

    /** The public link to a profile, or empty when the server has not said. */
    fun profileLink(username: String): String {
        val base = webBaseUrl
        if (base.isEmpty() || username.isEmpty()) return ""
        return "$base/@$username"
    }

    /** Public link that opens the join form with a referral code prefilled. */
    fun joinLink(code: String): String {
        val base = webBaseUrl
        if (base.isEmpty() || code.isEmpty()) return ""
        return "$base/join/$code"
    }

    /** Re-reads config. Collapses concurrent callers. */
    suspend fun refresh(): Boolean = refreshing.withLock {
        val cfg = Xani.auth.config().value ?: return false
        remember(cfg)
        true
    }

    fun remember(cfg: AppConfig) {
        if (cfg.mediaBaseUrl.isNotEmpty()) baseUrl = cfg.mediaBaseUrl
        if (cfg.webBaseUrl.isNotEmpty()) webBaseUrl = cfg.webBaseUrl
        maxVideoSeconds = cfg.maxVideoSeconds
        maxVideoBytes = cfg.maxVideoMb.toLong() shl 20
        maxImageBytes = cfg.maxImageMb.toLong() shl 20
        maxCaption = cfg.maxCaption
        if (cfg.faviconKey.isNotEmpty() || faviconKey.isNotEmpty()) {
            faviconKey = cfg.faviconKey
        }
        if (cfg.firebaseWeb != null) firebaseWeb = cfg.firebaseWeb
        val fbJson = cfg.firebaseWeb?.let {
            JSONObject()
                .put("apiKey", it.apiKey)
                .put("appId", it.appId)
                .put("projectId", it.projectId)
                .put("messagingSenderId", it.messagingSenderId)
                .put("storageBucket", it.storageBucket)
                .put("androidAppId", it.androidAppId)
                .toString()
        }
        val ed = prefs?.edit()
            ?.putString(KEY_BASE, baseUrl)
            ?.putString(KEY_WEB, webBaseUrl)
            ?.putString(KEY_FAVICON, faviconKey)
            ?.putInt(KEY_VIDEO_SEC, cfg.maxVideoSeconds)
            ?.putInt(KEY_VIDEO_MB, cfg.maxVideoMb)
            ?.putInt(KEY_IMAGE_MB, cfg.maxImageMb)
            ?.putInt(KEY_CAPTION, cfg.maxCaption)
            ?.putLong(KEY_AT, System.currentTimeMillis())
        if (fbJson != null) ed?.putString(KEY_FIREBASE, fbJson)
        ed?.apply()
        warmBrand()
        if (firebaseWeb != null) PushRegistrar.sync()
    }

    private fun warmBrand() {
        val u = faviconUrl()
        if (u.isNotEmpty()) AssetCache.prefetch(u)
    }
}
