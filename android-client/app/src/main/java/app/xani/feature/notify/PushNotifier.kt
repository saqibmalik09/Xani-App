package app.xani.feature.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import app.xani.MainActivity
import app.xani.R
import app.xani.core.isolate.runFeature
import app.xani.core.net.AppForeground
import app.xani.feature.call.BRAND_COLOR
import app.xani.feature.call.BrandNotifyIcon
import app.xani.feature.inbox.InboxController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android side of the server's category registry: one channel per kind of
 * notification (so the user can silence live alerts without losing messages
 * in system settings), and the foreground renderer for typed notes.
 *
 * When the app is closed the OS draws the FCM `notification` block itself
 * into the same channel with the same tag; this class only draws when the
 * process is alive (FCM hands us the message instead). Either way the tap
 * carries the same extras into [MainActivity] → [PushRouter].
 */
object PushNotifier {
    const val CH_MESSAGES = "xani_messages"
    const val CH_LIVE = "xani_live"
    const val CH_SOCIAL = "xani_social"
    const val CH_NOTICES = "xani_notices"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Keys copied from FCM data into the tap intent; nothing else rides along. */
    private val ROUTE_KEYS = listOf(
        PushRouter.EXTRA_DEST, PushRouter.EXTRA_UID, "conv_id", "peer_id", "room_id", "host_id",
        "post_id", "comment_id", "user_id", "ticket_id", "category", "event_id", "request_id",
    )

    /** Idempotent; runs at process start so a background FCM draw finds its channel. */
    fun ensureChannels(context: Context) = runFeature(Unit) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@runFeature
        fun make(id: String, name: String, importance: Int, desc: String) {
            if (nm.getNotificationChannel(id) != null) return
            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    description = desc
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            )
        }
        make(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH, "New chat messages")
        make(CH_LIVE, "Live", NotificationManager.IMPORTANCE_HIGH, "People you follow going live")
        make(CH_SOCIAL, "Comments and mentions", NotificationManager.IMPORTANCE_DEFAULT, "Comments, replies and mentions on your videos")
        make(CH_NOTICES, "Account and announcements", NotificationManager.IMPORTANCE_DEFAULT, "Account, safety and product notices")
    }

    private fun channelFor(category: String): String = when (category) {
        "messages" -> CH_MESSAGES
        "live" -> CH_LIVE
        "comments", "replies", "mentions" -> CH_SOCIAL
        else -> CH_NOTICES
    }

    /** Draws one typed note (FCM `type = notify`) while the process is alive. */
    fun show(context: Context, data: Map<String, String>) = runFeature(Unit) {
        val app = context.applicationContext
        if (!PushRouter.forCurrentAccount { data[it] }) return@runFeature
        val category = data["category"].orEmpty()
        // Already reading that chat: the thread repaints from the stream.
        if (category == "messages" && AppForeground.visible.value) {
            val conv = data["conv_id"]?.toLongOrNull() ?: 0L
            if (conv != 0L && conv == InboxController.openConversation()) return@runFeature
        }
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        if (title.isBlank() && body.isBlank()) return@runFeature
        val nm = app.getSystemService(NotificationManager::class.java) ?: return@runFeature
        ensureChannels(app)
        val tag = data["tag"].orEmpty().ifBlank { "n" + (data["event_id"] ?: title).hashCode() }
        val open = tapIntent(app, tag, data)
        val channel = channelFor(category)
        fun build(large: Bitmap?): Notification {
            val b = Notification.Builder(app, channel)
                .setSmallIcon(R.drawable.ic_stat_brand)
                .setColor(BRAND_COLOR)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .setGroup("xani.$category")
                .setCategory(if (category == "messages") Notification.CATEGORY_MESSAGE else Notification.CATEGORY_SOCIAL)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
            if (large != null) b.setLargeIcon(large)
            return b.build()
        }
        nm.notify(tag, 0, build(null))
        scope.launch {
            val large = BrandNotifyIcon.large(app) ?: return@launch
            nm.notify(tag, 0, build(large).apply { flags = flags or Notification.FLAG_ONLY_ALERT_ONCE })
        }
        Unit
    }

    /** Older "notice" payloads (title/body only) open the notices lane. */
    fun showNotice(context: Context, title: String, body: String) {
        show(context, mapOf("category" to "account", "title" to title, "body" to body, PushRouter.EXTRA_DEST to "notices"))
    }

    private fun tapIntent(app: Context, tag: String, data: Map<String, String>): PendingIntent {
        val intent = Intent(app, MainActivity::class.java)
            .setAction("app.xani.NOTIFY.$tag")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        for (k in ROUTE_KEYS) data[k]?.takeIf { it.isNotBlank() }?.let { intent.putExtra(k, it) }
        return PendingIntent.getActivity(
            app, tag.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
