package app.xani.feature.upload

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
import app.xani.feature.call.BRAND_COLOR
import app.xani.i18n.Copy
import app.xani.ui.brand.BrandStore
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while a post is uploading, so leaving the app or
 * turning the screen off does not stall the transfer. The notification is the
 * progress the user sees outside the app; inside, the strip is the source.
 *
 * Only alive while work is queued or in flight: [UploadManager] starts it on
 * enqueue and stops it when the queue drains.
 */
class UploadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observe: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The system started us as a foreground service, so it will kill the whole
        // process if startForeground never lands (ForegroundServiceDidNotStartInTime).
        // Swallowing that failure is not an option: either we go foreground or we
        // stop right now and let the upload run while the app is alive.
        if (!startForegroundCompat(build(0f, 0, active = true))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observe == null) {
            observe = scope.launch {
                UploadManager.tasks.collectLatest { tasks ->
                    val live = tasks.filter { !it.state.isTerminal }
                    if (live.isEmpty()) {
                        stopSelf()
                        return@collectLatest
                    }
                    val sent = live.sumOf { it.bytesSent }
                    val total = live.sumOf { it.size }.coerceAtLeast(1)
                    val uploading = live.any { it.state == UploadState.Uploading || it.state == UploadState.Queued }
                    notify(build(sent.toFloat() / total, live.size, active = uploading))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observe?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** True once the notification is showing. Tries the typed call, then the plain one. */
    private fun startForegroundCompat(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val typed = runFeature(false) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                true
            }
            if (typed) return true
        }
        return runFeature(false) {
            startForeground(NOTIFICATION_ID, n)
            true
        }
    }

    private fun notify(n: Notification) = runFeature(Unit) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
    }

    private fun build(progress: Float, count: Int, active: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pct = (progress * 100).toInt().coerceIn(0, 100)
        val copy = Copy(Locale.getDefault().language)
        val title = if (active) copy.t(Copy.UploadPostingPct, "pct" to pct.toString()) else copy.t(Copy.UploadProcessing)
        val text = if (count > 1) copy.t(Copy.UploadCount, "n" to count.toString()) else BrandStore.current.name
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brand)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct, !active)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "uploads"
        private const val NOTIFICATION_ID = 4101

        fun ensureRunning(context: Context) = runFeature(Unit) {
            val app = context.applicationContext
            ensureChannel(app)
            val intent = Intent(app, UploadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }

        fun stopIfIdle(context: Context) = runFeature(Unit) {
            if (UploadManager.tasks.value.none { !it.state.isTerminal }) {
                context.applicationContext.stopService(Intent(context.applicationContext, UploadService::class.java))
            }
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, Copy(Locale.getDefault().language).t(Copy.UploadChannel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
    }
}
