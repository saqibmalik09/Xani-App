package app.xani.feature.roles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prefill for the join-with-code form from a shared application link. */
data class JoinPrefill(val code: String, val roleSlug: String = "")

/**
 * Opens the roles screen from anywhere (inbox notice tap, push, join link).
 * The Profile tab consumes [pending] once composed. A request id > 0 opens that
 * request; 0 opens the roles home.
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

    private val _pendingJoin = MutableStateFlow<JoinPrefill?>(null)
    val pendingJoin: StateFlow<JoinPrefill?> = _pendingJoin.asStateFlow()

    fun openJoin(code: String, roleSlug: String = "") {
        _pendingJoin.value = JoinPrefill(code.trim().uppercase(), roleSlug.trim().lowercase())
        _pending.value = 0L // land on the roles home, which consumes the prefill
    }

    fun consumeJoin(): JoinPrefill? {
        val c = _pendingJoin.value ?: return null
        _pendingJoin.value = null
        return c
    }
}
