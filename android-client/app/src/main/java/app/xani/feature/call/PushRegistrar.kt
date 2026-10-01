package app.xani.feature.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.xani.BuildConfig
import app.xani.core.isolate.runFeature
import app.xani.core.net.AuthState
import app.xani.core.net.FirebaseWebConfig
import app.xani.core.net.MediaConfig
import app.xani.core.net.Xani
import app.xani.feature.notify.PushNotifier
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Where this install stands with push, for the settings screen and logs. */
enum class PushStatus { Unknown, NotConfigured, TokenFailed, ServerRejected, Registered }

/**
 * Keeps this install's FCM token registered with the identity service.
 *
 * Lifecycle:
 * - [boot] (Application.onCreate): channels + Firebase from the options cached
 *   by the last successful init, so an FCM message that starts a killed process
 *   finds Firebase ready.
 * - [attach] (Xani.init): follows the session. Sign-in / account switch →
 *   register; sign-out → forget locally (the server drops the device on logout).
 * - [sync] on resume and on `onNewToken`: cheap when nothing changed.
 *
 * Token fetch does not need POST_NOTIFICATIONS; that permission only gates the
 * tray on API 33+. [status] never claims Registered unless the server said OK.
 */
object PushRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initLock = Mutex()
    private lateinit var prefs: SharedPreferences
    private var appCtx: Context? = null
    private var attached = false

    @Volatile private var lastSentToken: String? = null
    @Volatile private var lastSentUser: Long = 0L
    @Volatile private var noticePermAsked = false

    private val _status = MutableStateFlow(PushStatus.Unknown)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    /** Process start, before any activity: must stay cheap (one prefs read). */
    fun boot(context: Context) = runFeature(Unit) {
        val app = context.applicationContext
        appCtx = app
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        PushNotifier.ensureChannels(app)
        if (FirebaseApp.getApps(app).isNotEmpty()) return@runFeature
        val cached = prefs.getString(KEY_OPTIONS, null) ?: return@runFeature
        runCatching { optionsFrom(JSONObject(cached)) }.getOrNull()?.let { FirebaseApp.initializeApp(app, it) }
        Unit
    }

    fun attach(context: Context) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        appCtx = app
        if (!::prefs.isInitialized) prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        PushNotifier.ensureChannels(app)
        scope.launch {
            Xani.session.state.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> {
                        if (state.user.id != lastSentUser) lastSentToken = null
                        syncOnce(null)
                    }
                    is AuthState.SignedOut -> clearLocal()
                    else -> Unit
                }
            }
        }
    }

    fun sync(token: String? = null) {
        if (!attached) return
        scope.launch { syncOnce(token) }
    }

    /** Tray alerts allowed by the OS (always true below API 33 unless the user blocked the app). */
    fun notificationsAllowed(context: Context): Boolean {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return false
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Asks for tray permission at most once per install, and only after a
     * token is on the server — so the prompt is never shown for a push path
     * that cannot work anyway. Settings links to system settings afterwards.
     */
    fun maybeAskNoticePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33 || !::prefs.isInitialized) return
        if (noticePermAsked || prefs.getBoolean(KEY_PERM_ASKED, false)) return
        if (_status.value != PushStatus.Registered) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        noticePermAsked = true
        prefs.edit().putBoolean(KEY_PERM_ASKED, true).apply()
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTICE)
    }

    private suspend fun syncOnce(token: String?) {
        val signed = Xani.session.state.value as? AuthState.SignedIn ?: return
        if (!ensureFirebase()) {
            _status.value = PushStatus.NotConfigured
            Log.w(TAG, "firebase not configured: add the Android app id to Settings → Firebase")
            return
        }
        val t = token?.takeIf { it.isNotBlank() } ?: fetchTokenWithRetry()
        if (t.isNullOrBlank()) {
            _status.value = PushStatus.TokenFailed
            Log.w(TAG, "no FCM token after retries (check the Android app id / API key)")
            return
        }
        if (t == lastSentToken && signed.user.id == lastSentUser) {
            _status.value = PushStatus.Registered
            return
        }
        val r = Xani.api.post(
            "/v1/devices/push-token",
            JSONObject().put("token", t).put("platform", "android").put("app_version", BuildConfig.VERSION_NAME),
            authenticated = true,
        )
        if (r.ok) {
            lastSentToken = t
            lastSentUser = signed.user.id
            _status.value = PushStatus.Registered
            Log.i(TAG, "push registered")
        } else {
            lastSentToken = null
            _status.value = PushStatus.ServerRejected
            Log.w(TAG, "push-token rejected: ${r.statusCode} ${r.code}")
        }
    }

    private suspend fun ensureFirebase(): Boolean = initLock.withLock {
        val ctx = appCtx ?: return false
        var cfg = MediaConfig.firebaseWeb
        if (cfg == null && FirebaseApp.getApps(ctx).isEmpty()) {
            MediaConfig.refresh()
            cfg = MediaConfig.firebaseWeb
        }
        val wanted = cfg?.let { optionsJson(it) }
        // Remember the newest options for the next cold start, even when this
        // process already runs on older ones (FirebaseApp cannot be re-inited).
        if (wanted != null) prefs.edit().putString(KEY_OPTIONS, wanted.toString()).apply()
        if (FirebaseApp.getApps(ctx).isNotEmpty()) return true
        val options = wanted?.let { runCatching { optionsFrom(it) }.getOrNull() } ?: return false
        runFeature(false) {
            FirebaseApp.initializeApp(ctx, options)
            true
        }
    }

    private fun optionsJson(cfg: FirebaseWebConfig): JSONObject? {
        val appId = cfg.androidAppId.ifBlank { cfg.appId }
        if (cfg.apiKey.isBlank() || appId.isBlank() || cfg.projectId.isBlank()) return null
        if (cfg.androidAppId.isBlank() && appId.contains(":web:")) {
            Log.w(TAG, "only a web app id is configured; FCM on Android needs the Android app id")
        }
        val sender = cfg.messagingSenderId.ifBlank { appId.split(':').getOrNull(1).orEmpty() }
        if (sender.isBlank()) return null
        return JSONObject()
            .put("apiKey", cfg.apiKey)
            .put("appId", appId)
            .put("projectId", cfg.projectId)
            .put("sender", sender)
            .put("bucket", cfg.storageBucket)
    }

    private fun optionsFrom(o: JSONObject): FirebaseOptions {
        val b = FirebaseOptions.Builder()
            .setApiKey(o.getString("apiKey"))
            .setApplicationId(o.getString("appId"))
            .setProjectId(o.getString("projectId"))
            .setGcmSenderId(o.getString("sender"))
        o.optString("bucket").takeIf { it.isNotBlank() }?.let { b.setStorageBucket(it) }
        return b.build()
    }

    private suspend fun fetchTokenWithRetry(): String? {
        repeat(4) { attempt ->
            val t = fetchToken()
            if (!t.isNullOrBlank()) return t
            delay(1_000L shl attempt)
        }
        return null
    }

    private suspend fun fetchToken(): String? = runFeature(null) {
        FirebaseMessaging.getInstance().token.await()?.takeIf { it.isNotBlank() }
    }

    private fun clearLocal() {
        lastSentToken = null
        lastSentUser = 0L
        _status.value = PushStatus.Unknown
    }

    private const val TAG = "XaniPush"
    private const val PREFS = "xani.push"
    private const val KEY_OPTIONS = "fb_options"
    private const val KEY_PERM_ASKED = "perm_asked"
    private const val REQ_NOTICE = 4721
}
