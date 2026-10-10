package app.xani.core.net

import android.content.Context
import android.content.SharedPreferences
import app.xani.BuildConfig
import app.xani.core.media.AssetCache
import app.xani.feature.call.PushRegistrar
import app.xani.ui.brand.BrandStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val KEY_LOGO = "logo"
    private const val KEY_DEFAULT_AVATAR = "default_avatar"
    private const val KEY_APP_NAME = "app_name"
    private const val KEY_TAGLINE = "tagline"
    private const val KEY_USERNAME_DAYS = "username_change_days"
    private const val KEY_GOOGLE_CLIENT = "google_web_client_id"
    private const val KEY_AT = "at"
    // Short enough that a dashboard rebrand reaches devices on their next cold
    // start within the half hour; the read is one small public, cached GET.
    private const val REFRESH_MS = 30 * 60 * 1000L
    private const val DEFAULT_MB = 500L
    private const val FRESHEN_DEADLINE_MS = 2_500L
    private const val FRESHEN_GAP_MS = 3_000L

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

    /**
     * Days a handle is locked after a change (Settings → Profile). Stated on
     * signup and on edit profile, so it is the server's window and not a number
     * written into a screen.
     */
    @Volatile
    var usernameChangeDays: Int = 30
        private set

    /** Relative key for the dashboard favicon; resolve with [faviconUrl]. */
    @Volatile
    var faviconKey: String = ""
        private set

    /** Relative key for the dashboard logo; resolve with [logoUrl]. */
    @Volatile
    var logoKey: String = ""
        private set

    /** Relative key for the picture an account without one wears. */
    @Volatile
    var defaultAvatarKey: String = ""
        private set

    /** Dashboard app name, or empty until `/v1/config` has answered once. */
    @Volatile
    var appName: String = ""
        private set

    @Volatile
    private var tagline: String = ""

    /**
     * OAuth web client for Google sign-in, from the dashboard. Falls back to the
     * build's value so a deployment that has not set it keeps working, and is
     * cached in prefs so the sign-in button is right on the first frame.
     */
    val googleWebClientId: String
        get() = dashboardGoogleClientId.ifEmpty { BuildConfig.GOOGLE_WEB_CLIENT_ID.trim() }

    @Volatile
    private var dashboardGoogleClientId: String = ""

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
        usernameChangeDays = p.getInt(KEY_USERNAME_DAYS, 30)
        faviconKey = p.getString(KEY_FAVICON, "").orEmpty()
        logoKey = p.getString(KEY_LOGO, "").orEmpty()
        defaultAvatarKey = p.getString(KEY_DEFAULT_AVATAR, "").orEmpty()
        appName = p.getString(KEY_APP_NAME, "").orEmpty()
        tagline = p.getString(KEY_TAGLINE, "").orEmpty()
        dashboardGoogleClientId = p.getString(KEY_GOOGLE_CLIENT, "").orEmpty()
        firebaseWeb = p.getString(KEY_FIREBASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let(FirebaseWebConfig::from)
        // The last brand the server gave, before the first frame: a cold start
        // never flashes the build's name after the dashboard changed it.
        if (appName.isNotEmpty()) publishBrand()
        // appName empty = this install has never read the brand (fresh install, or
        // an update from a build that did not cache it). Read it now rather than
        // waiting out the refresh window showing the build's fallback name.
        if (baseUrl.isEmpty() || appName.isEmpty() ||
            System.currentTimeMillis() - p.getLong(KEY_AT, 0L) > REFRESH_MS
        ) {
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

    /** Absolute URL for the product logo, or empty when unset. */
    fun logoUrl(): String = url(logoKey.ifEmpty { null })

    /**
     * A person's picture: their own when they have one, otherwise the
     * dashboard's default profile image. Every avatar in the app resolves
     * through here, so one upload in Settings → Branding replaces initials
     * everywhere — feed, profile, share card, chat, live.
     *
     * Already-absolute URLs pass through: some payloads carry a full URL.
     */
    fun avatarUrl(key: String?): String {
        val own = key.orEmpty()
        if (own.startsWith("http://") || own.startsWith("https://")) return own
        return url(own.ifEmpty { null }).ifEmpty { url(defaultAvatarKey.ifEmpty { null }) }
    }

    /** Absolute URL of the default profile image, or empty when unset. */
    fun defaultAvatarUrl(): String = url(defaultAvatarKey.ifEmpty { null })

    // Falls back to the favicon so a deployment that only ever uploaded one is
    // still its own brand on screen, in the tray and on the home screen — the
    // launcher sync and the notification icon resolve it the same way.
    private fun publishBrand() = BrandStore.learn(appName, tagline, logoUrl().ifEmpty { faviconUrl() })

    /** The public link to a profile, or empty when the server has not said. */
    fun profileLink(username: String): String {
        val base = webBaseUrl
        if (base.isEmpty() || username.isEmpty()) return ""
        return "$base/u/${username.trim().removePrefix("@")}"
    }

    /**
     * Public link to a live room. Opens the broadcast when it is still on;
     * the landing and the app both say ended when it is not. Room id, not
     * username: a host can restream, and the share must open that room.
     */
    fun liveLink(roomId: Long): String {
        val base = webBaseUrl
        if (base.isEmpty() || roomId <= 0L) return ""
        return "$base/live/$roomId"
    }

    /** Public link that opens the join form for a specific role under a referral code. */
    fun joinLink(code: String, roleSlug: String = ""): String {
        val base = webBaseUrl
        if (base.isEmpty() || code.isEmpty()) return ""
        val slug = roleSlug.trim().trim('/')
        return if (slug.isEmpty()) "$base/join/$code" else "$base/join/$code/$slug"
    }

    /** Re-reads config. Collapses concurrent callers. */
    suspend fun refresh(): Boolean = refreshing.withLock {
        val cfg = Xani.auth.config().value ?: return false
        remember(cfg)
        true
    }

    /**
     * Re-reads config before an action that must not run on a stale value —
     * Google sign-in, where a client id rotated on the dashboard has to apply on
     * the next tap, not after a cache window. Bounded by [FRESHEN_DEADLINE_MS] so
     * a slow or unreachable server delays the tap by no more than that and then
     * falls through to the cached value, and rate-limited so the three cold
     * retries behind one tap do not each make a request.
     */
    suspend fun freshen() {
        val now = System.currentTimeMillis()
        if (now - lastFreshen < FRESHEN_GAP_MS) return
        lastFreshen = now
        withTimeoutOrNull(FRESHEN_DEADLINE_MS) { refresh() }
    }

    @Volatile
    private var lastFreshen = 0L

    fun remember(cfg: AppConfig) {
        if (cfg.mediaBaseUrl.isNotEmpty()) baseUrl = cfg.mediaBaseUrl
        if (cfg.webBaseUrl.isNotEmpty()) webBaseUrl = cfg.webBaseUrl
        maxVideoSeconds = cfg.maxVideoSeconds
        maxVideoBytes = cfg.maxVideoMb.toLong() shl 20
        maxImageBytes = cfg.maxImageMb.toLong() shl 20
        maxCaption = cfg.maxCaption
        if (cfg.usernameChangeDays > 0) usernameChangeDays = cfg.usernameChangeDays
        if (cfg.faviconKey.isNotEmpty() || faviconKey.isNotEmpty()) {
            faviconKey = cfg.faviconKey
        }
        logoKey = cfg.logoKey
        defaultAvatarKey = cfg.defaultAvatarKey
        tagline = cfg.tagline
        if (cfg.appName.isNotBlank()) appName = cfg.appName
        dashboardGoogleClientId = cfg.googleWebClientId.trim()
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
            ?.putString(KEY_LOGO, logoKey)
            ?.putString(KEY_DEFAULT_AVATAR, defaultAvatarKey)
            ?.putString(KEY_APP_NAME, appName)
            ?.putString(KEY_TAGLINE, tagline)
            ?.putString(KEY_GOOGLE_CLIENT, dashboardGoogleClientId)
            ?.putInt(KEY_VIDEO_SEC, cfg.maxVideoSeconds)
            ?.putInt(KEY_VIDEO_MB, cfg.maxVideoMb)
            ?.putInt(KEY_IMAGE_MB, cfg.maxImageMb)
            ?.putInt(KEY_CAPTION, cfg.maxCaption)
            ?.putInt(KEY_USERNAME_DAYS, usernameChangeDays)
            ?.putLong(KEY_AT, System.currentTimeMillis())
        if (fbJson != null) ed?.putString(KEY_FIREBASE, fbJson)
        ed?.apply()
        publishBrand()
        warmBrand()
        if (firebaseWeb != null) PushRegistrar.sync()
    }

    private fun warmBrand() {
        logoUrl().takeIf { it.isNotEmpty() }?.let(AssetCache::prefetch)
        faviconUrl().takeIf { it.isNotEmpty() }?.let(AssetCache::prefetch)
        // Stands in for every account without a picture, so it is on the first
        // feed and live frame rather than a scroll later.
        defaultAvatarUrl().takeIf { it.isNotEmpty() }?.let(AssetCache::prefetch)
    }
}
