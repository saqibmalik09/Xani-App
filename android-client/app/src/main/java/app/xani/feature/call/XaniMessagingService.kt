package app.xani.feature.call

import app.xani.feature.notify.PushNotifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point. Call invites are data-only (the ring is ours via
 * [IncomingCallNotifier]). Typed notes (`type = notify`) carry both a
 * notification block — drawn by the OS when the app is closed — and data with
 * the route; while the process is alive FCM hands them here and
 * [PushNotifier] draws the same entry into the same channel.
 */
class XaniMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistrar.sync(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "call_invite" -> {
                val callId = data["call_id"]?.toLongOrNull() ?: return
                val name = data["name"].orEmpty()
                val video = data["kind"] == "video"
                CallController.ringFromPush(callId, name, video)
            }
            "notify" -> PushNotifier.show(this, data)
            else -> {
                val title = data["title"] ?: message.notification?.title ?: return
                val body = data["body"] ?: message.notification?.body.orEmpty()
                PushNotifier.showNotice(this, title, body)
            }
        }
    }
}
