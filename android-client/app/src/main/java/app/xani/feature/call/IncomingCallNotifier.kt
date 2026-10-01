package app.xani.feature.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import app.xani.MainActivity
import app.xani.R
import app.xani.core.isolate.runFeature
import app.xani.i18n.Copy
import java.util.Locale

/**
 * The ring you see when the app is not in front of you: a full-screen,
 * high-importance call notification with Answer / Decline, raised either
 * from the realtime stream (app in background) or from the FCM data message
 * (app killed). Tapping any of it lands in [MainActivity] with the call id
 * and the chosen action; [CallController.openFromNotification] takes it
 * from there. The in-app ring (ringtone, overlay) stays with the controller;
 * this only exists so a locked phone rings at all.
 */
object IncomingCallNotifier {
    const val EXTRA_CALL_ID = "xani.call_id"
    const val EXTRA_ACTION = "xani.call_action"
    const val ACTION_OPEN = "open"
    const val ACTION_ACCEPT = "accept"
    const val ACTION_DECLINE = "decline"

    private const val CHANNEL = "calls_incoming"
    private const val NOTIFICATION_ID = 4202

    /** One ring per call: the same id replaces itself, a new id replaces the old ring. */
    fun show(context: Context, callId: Long, callerName: String, video: Boolean) = runFeature(Unit) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return@runFeature
        ensureChannel(app, nm)
        val copy = Copy(Locale.getDefault().language)
        val name = callerName.ifBlank { "…" }
        val title = copy.t(if (video) Copy.CallIncomingVideo else Copy.CallIncomingAudio)

        val open = pending(app, callId, ACTION_OPEN, 0)
        val accept = pending(app, callId, ACTION_ACCEPT, 1)
        val decline = pending(app, callId, ACTION_DECLINE, 2)

        val b = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brand)
            .setColor(BRAND_COLOR)
            .setContentTitle(name)
            .setContentText(title)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setTimeoutAfter(RING_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder().setName(name).setImportant(true).build()
            b.setStyle(Notification.CallStyle.forIncomingCall(person, decline, accept).setIsVideo(video))
        } else {
            b.addAction(Notification.Action.Builder(null, copy.t(Copy.CallDecline), decline).build())
            b.addAction(Notification.Action.Builder(null, copy.t(Copy.CallAccept), accept).build())
        }
        nm.notify(NOTIFICATION_ID, b.build())
    }

    fun cancel(context: Context) = runFeature(Unit) {
        context.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun pending(app: Context, callId: Long, action: String, req: Int): PendingIntent {
        val intent = Intent(app, MainActivity::class.java)
            .setAction("app.xani.CALL_$action")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_CALL_ID, callId)
            .putExtra(EXTRA_ACTION, action)
        return PendingIntent.getActivity(
            app, req, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * High importance so it heads-up and may go full-screen; silent, because the
     * looping ringtone belongs to [CallAudio] (the channel would ring once and
     * stop). Vibration stays here so a phone on silent-but-vibrate still moves.
     */
    private fun ensureChannel(app: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, Copy(Locale.getDefault().language).t(Copy.CallIncomingChannel), NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 900, 600, 900, 600)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setBypassDnd(false)
            },
        )
    }

    private const val RING_MS = 60_000L
}
