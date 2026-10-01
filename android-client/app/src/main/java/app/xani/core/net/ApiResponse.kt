package app.xani.core.net

import androidx.compose.runtime.Immutable

/**
 * The one shape every API answer takes, success or failure:
 *
 * ```
 * { "statusCode": 200, "error": false, "message": "Signed in.", "data": { ... } }
 * { "statusCode": 409, "error": true,  "message": "That username is taken.",
 *   "code": "duplicate_username", "data": {} }
 * ```
 *
 * Callers branch on [code], never on [message]: codes are a contract, messages are
 * copy that product can reword or localise at any time.
 *
 * Transport failures are folded into the same type with a synthetic [code], so a
 * caller handles "server said no" and "there is no network" through one path
 * instead of a `try`/`catch` wrapped around every call site.
 */
@Immutable
data class ApiResponse<out T>(
    val statusCode: Int,
    val error: Boolean,
    val message: String,
    val code: String,
    val data: T?,
    /** Validation failures name the offending input (`data.fields` on the wire): key → message. */
    val fields: Map<String, String> = emptyMap(),
) {
    val ok: Boolean get() = !error

    /** Data on success, null otherwise — the usual `?.let { }` entry point. */
    val value: T? get() = if (error) null else data

    inline fun <R> map(transform: (T) -> R): ApiResponse<R> =
        ApiResponse(statusCode, error, message, code, data?.let(transform), fields)

    /** True when the caller should surface a "check your connection" affordance. */
    val isTransport: Boolean get() = code == CODE_NETWORK || code == CODE_TIMEOUT

    /** True when this session is finished and the user must sign in again. */
    val isSessionExpired: Boolean get() = code == CODE_SESSION_EXPIRED

    companion object {
        const val CODE_OK = "ok"
        const val CODE_NETWORK = "network"
        const val CODE_TIMEOUT = "timeout"
        const val CODE_MALFORMED = "malformed_response"

        /** Emitted when refresh failed: the 72-hour window is over. */
        const val CODE_SESSION_EXPIRED = "session_expired"

        fun <T> success(statusCode: Int, message: String, data: T?): ApiResponse<T> =
            ApiResponse(statusCode, error = false, message = message, code = CODE_OK, data = data)

        fun <T> failure(statusCode: Int, code: String, message: String, fields: Map<String, String> = emptyMap()): ApiResponse<T> =
            ApiResponse(statusCode, error = true, message = message, code = code, data = null, fields = fields)

        fun <T> offline(): ApiResponse<T> =
            failure(0, CODE_NETWORK, "No connection. Check your internet and try again.")

        fun <T> timeout(): ApiResponse<T> =
            failure(0, CODE_TIMEOUT, "The server took too long to respond. Try again.")

        fun <T> sessionExpired(): ApiResponse<T> =
            failure(401, CODE_SESSION_EXPIRED, "Your session ended. Sign in to continue.")
    }
}
