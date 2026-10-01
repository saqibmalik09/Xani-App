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
 * Keeps microphone and camera capture alive while the user leaves the app
 * mid-call. Android 14+ kills background capture without a typed foreground
 * service; this is that service. It carries no call logic: it mirrors
 * [CallController.state] into one ongoing notification and stops itself on Idle.
 */
class CallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observe: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ui = CallController.state.value
        if (ui == null || !ui.active) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startForegroundCompat(build(ui))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observe == null) {
            observe = scope.launch {
                CallController.state.collectLatest { s ->
                    if (s == null || !s.active) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notify(build(s))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
            val typed = runFeature(false) {
                startForeground(NOTIFICATION_ID, n, types)
                true
            }
            if (typed) return true
            // Camera type is refused when the app is not visible; fall back to what is allowed.
            val voice = runFeature(false) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                true
            }
            if (voice) return true
        }
        return runFeature(false) {
            startForeground(NOTIFICATION_ID, n)
            true
        }
    }

    private fun notify(n: Notification) = runFeature(Unit) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
    }

    private fun build(ui: CallUi): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val copy = Copy(Locale.getDefault().language)
        val title = when (ui.phase) {
            CallPhase.Reconnecting -> copy.t(Copy.CallReconnecting)
            CallPhase.Connected -> copy.t(Copy.CallOngoing)
            else -> copy.t(Copy.InboxConnecting)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brand)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(ui.peer.name)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "calls"
        private const val NOTIFICATION_ID = 4201

        fun ensureRunning(context: Context) = runFeature(Unit) {
            val app = context.applicationContext
            ensureChannel(app)
            val intent = Intent(app, CallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent) else app.startService(intent)
        }

        fun stop(context: Context) = runFeature(Unit) {
            context.applicationContext.stopService(Intent(context.applicationContext, CallService::class.java))
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, Copy(Locale.getDefault().language).t(Copy.CallChannel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    setSound(null, null)
                },
            )
        }
    }
}
