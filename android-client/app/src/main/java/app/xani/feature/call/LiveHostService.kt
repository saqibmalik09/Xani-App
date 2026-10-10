package app.xani.feature.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.xani.MainActivity
import app.xani.R
import app.xani.core.isolate.runFeature
import app.xani.i18n.Copy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Keeps a host's (or on-stage co-host's) capture alive while they leave the
 * app, and names the end when the task is swiped away. Android 14+ kills
 * background camera/mic without a typed foreground service; this is it. It
 * carries no live logic: it mirrors [LiveSession.state] into one ongoing
 * notification and stops itself when the person is no longer on stage.
 *
 * Verified time is the server's, so the notification refreshes on each ack
 * (every heartbeat), never on a one-second timer — a background clock would
 * only drain the battery to show the same number.
 *
 * Why the room id is persisted: `onTaskRemoved` fires when the user swipes
 * the task, and the end request goes out from there — but a process the OS
 * kills outright gets no callback at all. The next launch finds the id and
 * ends the room as `app_closed`; the server stopped the clock at the last
 * heartbeat + allowance regardless, this only labels the record truthfully.
 */
class LiveHostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observe: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ui = LiveSession.state.value
        if (ui == null || ui.over || !ui.onStage) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startForegroundCompat(build(ui))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observe == null) {
            observe = scope.launch {
                // The notice only says the app is on air; it never carries the
                // time figure, so it is rebuilt solely when the role changes
                // (host ↔ guest), not on every heartbeat.
                var shown = ui.role
                LiveSession.state.collectLatest { s ->
                    if (s == null || s.over || !s.onStage) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (s.role != shown) {
                        shown = s.role
                        notify(build(s))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * The user swiped the app away while on air. The host's room ends as
     * `app_closed`; a co-host stands. The process may die within seconds, so
     * the request goes out immediately and nothing waits on it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runFeature(Unit) {
            val ui = LiveSession.state.value
            when {
                ui == null || ui.over -> Unit
                ui.role == LiveRole.Host -> LiveSession.endLive("app_closed")
                ui.onStage -> LiveSession.leave()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val both = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            val typed = runFeature(false) {
                startForeground(NOTIFICATION_ID, n, both)
                true
            }
            if (typed) return true
            // Camera type is refused when the app is not visible; the mic type alone keeps audio alive.
            val mic = runFeature(false) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                true
            }
            if (mic) return true
        }
        return runFeature(false) {
            startForeground(NOTIFICATION_ID, n)
            true
        }
    }

    private fun notify(n: Notification) = runFeature(Unit) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
    }

    private fun build(ui: LiveUi): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val copy = Copy(Locale.getDefault().language)
        // Title only — the system requires a visible notice for a camera /
        // microphone foreground service; the verified time stays on the HUD
        // and in My Live, never in a notification.
        val title = copy.t(if (ui.role == LiveRole.Host) Copy.LiveNotifHosting else Copy.LiveNotifOnStage)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brand)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        /** Minimum-importance channel: no sound, no peek, collapsed in the shade. */
        private const val CHANNEL = "live_quiet"
        private const val LEGACY_CHANNEL = "live"
        private const val NOTIFICATION_ID = 4202
        private const val PREFS = "xani.live"
        private const val KEY_HOSTING = "hosting_room"

        /**
         * Start (or keep) the service for the person now on stage. [roomId]
         * is persisted for the host only (non-zero): it is the room to end
         * as `app_closed` if the process dies without a callback.
         */
        fun ensureRunning(context: Context, roomId: Long) = runFeature(Unit) {
            val app = context.applicationContext
            if (roomId != 0L) {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_HOSTING, roomId).apply()
            }
            ensureChannel(app)
            val intent = Intent(app, LiveHostService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent) else app.startService(intent)
        }

        /** The room ended on purpose (or the server ended it): forget it. */
        fun clearHostingRoom(context: Context) = runFeature(Unit) {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.contains(KEY_HOSTING)) p.edit().remove(KEY_HOSTING).apply()
        }

        /** A room left over from a dead process, cleared on read; 0 when none. */
        fun takeHostingRoom(context: Context): Long = runFeature(0L) {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val id = p.getLong(KEY_HOSTING, 0L)
            if (id != 0L) p.edit().remove(KEY_HOSTING).apply()
            id
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            // The earlier channel showed the running time; a channel's
            // importance cannot be lowered in place, so it is retired.
            if (nm.getNotificationChannel(LEGACY_CHANNEL) != null) nm.deleteNotificationChannel(LEGACY_CHANNEL)
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, Copy(Locale.getDefault().language).t(Copy.LiveChannel), NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                },
            )
        }
    }
}
