package app.xani.feature.call.rtc

import android.content.Context
import app.xani.feature.call.RtcEngine

/**
 * Builds without the media stack (no `XANI_RTC=1`). Calling surfaces a clear
 * "unavailable on this build" state instead of a crash; signalling still works,
 * so the server side and UI can be exercised end to end.
 */
object RtcEngineFactory {
    const val AVAILABLE = false

    fun create(@Suppress("UNUSED_PARAMETER") context: Context): RtcEngine? = null
}
