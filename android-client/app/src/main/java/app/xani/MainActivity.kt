package app.xani

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.ViewCompat
import app.xani.core.isolate.CrashLog
import app.xani.core.net.AppForeground
import app.xani.core.net.CredentialGoogleSignIn
import app.xani.core.net.Xani
import app.xani.feature.auth.AuthFlow
import app.xani.feature.auth.SessionExpiredGate
import app.xani.feature.call.CallController
import app.xani.feature.call.IncomingCallNotifier
import app.xani.feature.call.PushRegistrar
import app.xani.feature.notify.PushRouter
import app.xani.feature.social.FrameStore
import app.xani.feature.upload.UploadManager
import app.xani.ui.brand.Brand
import app.xani.ui.brand.LocalBrand
import app.xani.ui.theme.XaniTheme

class MainActivity : ComponentActivity() {

    /** `reportFullyDrawn` logs a warning if called twice, so it is fired exactly once. */
    private var reportedFullyDrawn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Resolved eagerly: three BuildConfig string reads, no IO, nothing that can
        // push work between process start and the first frame.
        val brand = Brand.fromBuildConfig()

        // Cheap on the main thread: one SharedPreferences read for the device key and
        // object construction. The session restore it kicks off — and the /v1/auth/me
        // that follows — runs on a background dispatcher, so the splash still draws
        // immediately and the network never sits between process start and frame one.
        Xani.init(this, googleProvider = CredentialGoogleSignIn())

        // The frame/badge this account wears, off disk: the profile header's
        // ring draws on the first frame after a cold start, not when the
        // network read lands seconds later.
        FrameStore.attach(this)

        // If the process died last time, say why. `adb logcat -s XaniCrash` turns
        // "it crashes when I post" into a stack trace without a crash SDK.
        CrashLog.consume()?.let { Log.e("XaniCrash", "previous death:\n$it") }

        // A tapped notification that started (or re-created) the activity.
        routeIntent(intent)

        setContent {
            CompositionLocalProvider(LocalBrand provides brand) {
                XaniTheme {
                    // Wraps everything: a session that dies while the user is deep in
                    // the app raises the sign-in sheet in place, rather than throwing
                    // them back to the auth flow and losing where they were.
                    SessionExpiredGate {
                        AuthFlow(
                            onInteractive = ::reportInteractive,
                            onAuthenticated = { },
                        )
                    }
                }
            }
        }
    }

    /**
     * `configChanges` swallows rotation and density so Compose keeps its state. The
     * window must still re-apply insets — otherwise gesture ↔ 3-button switches
     * (and fold/unfold) leave the previous bottom inset on screen.
     */
    /** singleTask: a notification tap while running arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIntent(intent)
    }

    private fun routeIntent(intent: Intent?) {
        val callId = intent?.getLongExtra(IncomingCallNotifier.EXTRA_CALL_ID, 0L) ?: 0L
        if (callId != 0L) {
            val action = intent?.getStringExtra(IncomingCallNotifier.EXTRA_ACTION) ?: IncomingCallNotifier.ACTION_OPEN
            intent?.removeExtra(IncomingCallNotifier.EXTRA_CALL_ID)
            CallController.openFromNotification(callId, action)
            return
        }
        PushRouter.handle(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ViewCompat.requestApplyInsets(window.decorView)
    }

    /**
     * Started/stopped is what "the user has this app in front of them" means,
     * and it is what the realtime socket — and therefore "online" — follows.
     */
    override fun onStart() {
        super.onStart()
        AppForeground.onStart()
    }

    override fun onStop() {
        AppForeground.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        ViewCompat.requestApplyInsets(window.decorView)
        // A post that was processing while we were away: one read, then the
        // stream takes over again.
        UploadManager.resume()
        // FCM token registration needs no notification permission — only a
        // signed-in session + Firebase (from google-services or /v1/config).
        // Re-POST so already-registered users without a device_push_tokens row
        // recover as soon as they open the app.
        PushRegistrar.sync()
        PushRegistrar.maybeAskNoticePermission(this)
    }

    /**
     * Time-to-fully-drawn is what Android vitals flags as a slow cold start. Without
     * this call the platform infers it from the first frame, which here is the branded
     * splash rather than the first screen the user can act on.
     */
    private fun reportInteractive() {
        if (reportedFullyDrawn) return
        reportedFullyDrawn = true
        reportFullyDrawn()
    }
}
