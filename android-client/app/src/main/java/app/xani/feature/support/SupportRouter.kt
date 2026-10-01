package app.xani.feature.support

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens a Help Centre ticket from anywhere (inbox notice tap, push).
 * Profile tab consumes [pending] once composed.
 */
object SupportRouter {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    fun openTicket(ticketId: Long) {
        if (ticketId <= 0L) return
        _pending.value = ticketId
    }

    fun consume(): Long? {
        val id = _pending.value ?: return null
        _pending.value = null
        return id
    }
}
