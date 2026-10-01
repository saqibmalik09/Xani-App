package app.xani

import android.app.Application
import app.xani.feature.call.PushRegistrar

/**
 * Process entry. Kept to what a background start needs: an FCM message can
 * start the process with no activity, and Firebase plus the notification
 * channels must exist before the messaging service runs.
 */
class XaniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PushRegistrar.boot(this)
    }
}
