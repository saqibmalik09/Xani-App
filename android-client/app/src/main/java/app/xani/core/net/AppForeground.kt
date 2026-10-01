package app.xani.core.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a screen of this app is in front of the user.
 *
 * The realtime socket follows this, not any one screen: presence ("online"),
 * incoming messages and delivery ticks have to work on every tab, and they
 * have to stop when the app is put away — a backgrounded phone holding a
 * stream is a battery cost and a lie about who is available. Push takes over
 * from there.
 *
 * Driven by the activity's started/stopped edges, which is the same boundary
 * the platform uses, so a rotation or a transparent dialog does not flap it.
 */
object AppForeground {
    private val _visible = MutableStateFlow(false)

    /** True between the activity starting and stopping. */
    val visible: StateFlow<Boolean> = _visible

    private var started = 0

    fun onStart() {
        started++
        _visible.value = true
    }

    fun onStop() {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) _visible.value = false
    }
}
