package app.xani.feature.roles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens the roles screen from anywhere (inbox notice tap, push). The Profile
 * tab consumes [pending] once composed. A request id > 0 opens that request;
 * 0 opens the roles home.
 */
object RolesRouter {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    fun openRequest(requestId: Long) {
        _pending.value = requestId.coerceAtLeast(0L)
    }

    fun openHome() {
        _pending.value = 0L
    }

    fun consume(): Long? {
        val id = _pending.value ?: return null
        _pending.value = null
        return id
    }

    /** Referral code from a tapped join link: prefills the join form. */
    private val _pendingJoin = MutableStateFlow<String?>(null)
    val pendingJoin: StateFlow<String?> = _pendingJoin.asStateFlow()

    fun openJoin(code: String) {
        _pendingJoin.value = code
        _pending.value = 0L // land on the roles home, which consumes the code
    }

    fun consumeJoin(): String? {
        val c = _pendingJoin.value ?: return null
        _pendingJoin.value = null
        return c
    }
}
