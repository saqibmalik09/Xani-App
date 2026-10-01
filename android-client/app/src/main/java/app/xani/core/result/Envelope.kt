package app.xani.core.result

import androidx.compose.runtime.Immutable

/**
 * Wire shape every future API maps onto. UI never sees a raw HTTP body; a feature
 * that cannot decode a payload returns [fail] and degrades only itself.
 *
 * `{ ok, code, message, data, cursor }`
 */
@Immutable
data class Envelope<T>(
    val ok: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
    val cursor: String? = null,
) {
    companion object {
        fun <T> ok(data: T, cursor: String? = null): Envelope<T> =
            Envelope(ok = true, code = "ok", message = "", data = data, cursor = cursor)

        fun <T> empty(cursor: String? = null): Envelope<T> =
            Envelope(ok = true, code = "empty", message = "", data = null, cursor = cursor)

        fun <T> fail(code: String, message: String): Envelope<T> =
            Envelope(ok = false, code = code, message = message, data = null, cursor = null)
    }
}
