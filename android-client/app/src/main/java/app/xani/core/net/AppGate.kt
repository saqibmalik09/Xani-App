package app.xani.core.net

import androidx.compose.runtime.Immutable
import app.xani.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The app-version gate. The server holds a list of released builds, each on or
 * off; this device reports its own build (the `X-App-Version` header the
 * transport attaches) and asks whether it is blocked.
 *
 * When blocked, [state] carries the admin's message and a WhatsApp number, and
 * the app draws a full-screen wall over everything — the user cannot move past
 * it until they update. The check runs at startup, every time the app comes to
 * the front, and the instant an admin flips a version (the realtime stream
 * carries a `version` event, routed here through [LiveFeed]). While blocked the
 * device keeps the stream open, so turning the version back on releases it
 * without a restart.
 *
 * Fail-open: a network blip never fabricates a block. [state] only becomes
 * blocked on an explicit server answer.
 */
object AppGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * This build's version, set at build time with `-PAPP_VERSION`.
     *
     * The single spelling of it: reported in `X-App-Version`, gated on by the
     * dashboard's App versions table, and printed in the profile drawer — so
     * what a user reads out loud is exactly the row an admin has to find.
     */
    val version: String = BuildConfig.VERSION_NAME.trim()

    @Immutable
    data class Block(val message: String, val whatsapp: String)

    private val _state = MutableStateFlow<Block?>(null)

    /** Non-null when this build is blocked and the wall must show. */
    val state: StateFlow<Block?> = _state

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        // Re-check on every foreground, and hold the realtime socket while in
        // front so an admin flipping a version reaches this device at once.
        scope.launch {
            AppForeground.visible.collectLatest { visible ->
                if (!visible) return@collectLatest
                refresh()
                LiveFeed.hold()
            }
        }
    }

    /** Reads the gate for this build. Leaves the last answer on any failure. */
    suspend fun refresh() {
        val r = Xani.api.get("/v1/app/version", authenticated = false) { data ->
            Block(
                message = data.str("message"),
                whatsapp = data.str("whatsapp"),
            ) to data.bool("blocked")
        }
        val pair = r.value ?: return
        _state.value = if (pair.second) pair.first else null
    }
}
