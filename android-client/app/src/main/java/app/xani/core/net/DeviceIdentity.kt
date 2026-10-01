package app.xani.core.net

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * The device key the server uses for "max accounts per device" and device bans.
 *
 * It is a random UUID generated once and kept in app-private storage — not the
 * hardware id, not the advertising id. Those are either unavailable, resettable, or
 * privacy-restricted on modern Android, and shipping a real hardware identifier to a
 * server is the kind of thing that gets an app pulled.
 *
 * Consequence, on purpose: reinstalling the app produces a new key. The server
 * treats the device limit as a speed bump on casual multi-accounting, and the raw
 * key is hashed before it is stored, so this file never sees the value the server
 * bans on.
 */
object DeviceIdentity {
    private const val PREFS = "xani.device"
    private const val KEY_ID = "device_key"

    @Volatile
    private var cached: String? = null

    fun key(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_ID, null)
            val key = if (existing.isNullOrBlank()) {
                UUID.randomUUID().toString().also { prefs.edit().putString(KEY_ID, it).apply() }
            } else {
                existing
            }
            cached = key
            return key
        }
    }

    /** Coarse model string for the admin audit log; no serial, no IMEI. */
    val model: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})".take(120)

    const val PLATFORM = "android"
}
