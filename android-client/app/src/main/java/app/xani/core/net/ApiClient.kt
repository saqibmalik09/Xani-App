package app.xani.core.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream

/**
 * Supplies a valid access token and knows how to renew one. Implemented by
 * [SessionManager]; kept as an interface so the transport has no knowledge of where
 * sessions live and can be exercised without one.
 */
internal interface TokenSource {
    /** A token believed valid, refreshing proactively if it is about to expire. */
    suspend fun accessToken(): String?

    /**
     * Called after a 401. Returns a new token, or null when the refresh window is
     * over and the user must sign in again. Implementations must collapse concurrent
     * callers into one network refresh.
     */
    suspend fun renew(staleToken: String?): String?
}

/**
 * The HTTP transport. One instance for the whole app.
 *
 * It is built on `HttpURLConnection` rather than a client library because this module
 * ships no third-party dependencies, and because the auth surface is a handful of
 * small JSON calls where a connection pool and a codec is all that is needed — which
 * the platform already provides and keeps warm across calls.
 *
 * Responsibilities, and deliberately nothing else:
 *  - build the request, attach the bearer token
 *  - decode the fixed envelope into [ApiResponse]
 *  - turn transport failures into envelopes rather than exceptions
 *  - on 401, renew once through [TokenSource] and replay the request
 */
class ApiClient internal constructor(
    private val baseUrl: String,
    private val userAgent: String,
) {
    internal var tokens: TokenSource? = null

    suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        authenticated: Boolean = false,
        map: (JSONObject) -> T,
    ): ApiResponse<T> = send("GET", path, query, null, authenticated, retries = 1, map = map)

    suspend fun <T> post(
        path: String,
        body: JSONObject,
        authenticated: Boolean = false,
        map: (JSONObject) -> T,
    ): ApiResponse<T> = send("POST", path, emptyMap(), body, authenticated, retries = 0, map = map)

    /**
     * Idempotent by definition, so a transport failure is safe to replay once.
     *
     * (There is no `patch` here on purpose: `HttpURLConnection` rejects PATCH as
     * a request method, so partial updates are POSTed to their own path.)
     */
    suspend fun <T> delete(
        path: String,
        authenticated: Boolean = false,
        map: (JSONObject) -> T,
    ): ApiResponse<T> = send("DELETE", path, emptyMap(), null, authenticated, retries = 1, map = map)

    /** For endpoints whose payload the caller does not need. */
    suspend fun post(
        path: String,
        body: JSONObject,
        authenticated: Boolean = false,
    ): ApiResponse<Unit> = post(path, body, authenticated) { }

    /**
     * Uploads one raw body. The bytes are streamed with a fixed Content-Length
     * (no chunked encoding, so nginx and the server can bound it).
     *
     * Used for upload parts, where [sha256Hex] travels in a header so the
     * server can refuse a damaged chunk, and for the profile picture, which is
     * a single [contentType] image with no checksum.
     *
     * Idempotent by design on the server (re-sending a stored part is a no-op,
     * and re-sending a picture just replaces it), so a transport failure is
     * retried once here; the upload manager owns the longer backoff.
     */
    internal suspend fun <T> putBytes(
        path: String,
        bytes: ByteArray,
        sha256Hex: String = "",
        contentType: String = "application/octet-stream",
        map: (JSONObject) -> T,
    ): ApiResponse<T> {
        if (baseUrl.isBlank()) {
            return ApiResponse.failure(0, "not_configured", "This build has no server configured.")
        }
        var bearer = tokens?.accessToken() ?: return ApiResponse.sessionExpired()
        var attempt = 0
        while (true) {
            val raw = withContext(Dispatchers.IO) { executePut(path, bytes, sha256Hex, contentType, bearer) }
            if (raw.status == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0) {
                bearer = tokens?.renew(bearer) ?: return ApiResponse.sessionExpired()
                attempt++
                continue
            }
            if (raw.status == STATUS_TRANSPORT && attempt < 1) {
                attempt++
                continue
            }
            return decode(raw, map)
        }
    }

    /**
     * Streams one file to a PUT endpoint without holding it in memory: [open]
     * is called per attempt and the stream is copied to the socket in 64 KiB
     * writes. Used for chat attachments, where a clip can be tens of MB and
     * the phone must keep scrolling while it goes.
     *
     * The server is idempotent on the client id in [query], so a transport
     * failure is retried once here.
     */
    internal suspend fun <T> putStream(
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        contentType: String,
        length: Long,
        open: () -> InputStream?,
        map: (JSONObject) -> T,
    ): ApiResponse<T> {
        if (baseUrl.isBlank()) {
            return ApiResponse.failure(0, "not_configured", "This build has no server configured.")
        }
        var bearer = tokens?.accessToken() ?: return ApiResponse.sessionExpired()
        var attempt = 0
        while (true) {
            val raw = withContext(Dispatchers.IO) { executeStream(path, query, headers, contentType, length, open, bearer) }
            if (raw.status == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0) {
                bearer = tokens?.renew(bearer) ?: return ApiResponse.sessionExpired()
                attempt++
                continue
            }
            if (raw.status == STATUS_TRANSPORT && attempt < 1) {
                attempt++
                continue
            }
            return decode(raw, map)
        }
    }

    private fun executeStream(
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        contentType: String,
        length: Long,
        open: () -> InputStream?,
        bearer: String,
    ): RawResponse {
        var connection: HttpURLConnection? = null
        return try {
            val input = open() ?: return RawResponse(0, "", timeout = false)
            connection = (URL(buildUrl(path, query)).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = STREAM_UPLOAD_READ_TIMEOUT_MS
                instanceFollowRedirects = false
                doOutput = true
                setFixedLengthStreamingMode(length)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Authorization", "Bearer $bearer")
                headers.forEach { (k, v) -> if (v.isNotEmpty()) setRequestProperty(k, v) }
            }
            input.use { src ->
                connection.outputStream.use { out ->
                    val buf = ByteArray(64 * 1024)
                    var left = length
                    while (left > 0) {
                        val n = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val payload = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            RawResponse(status, payload, timeout = false)
        } catch (e: SocketTimeoutException) {
            RawResponse(STATUS_TRANSPORT, "", timeout = true)
        } catch (e: IOException) {
            RawResponse(STATUS_TRANSPORT, "", timeout = false)
        } finally {
            connection?.disconnect()
        }
    }

    private fun executePut(
        path: String,
        bytes: ByteArray,
        sha256Hex: String,
        contentType: String,
        bearer: String,
    ): RawResponse {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(buildUrl(path, emptyMap())).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = UPLOAD_READ_TIMEOUT_MS
                instanceFollowRedirects = false
                doOutput = true
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", contentType)
                if (sha256Hex.isNotEmpty()) setRequestProperty("X-Part-Sha256", sha256Hex)
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            connection.outputStream.use { out ->
                // 64 KiB writes: enough to fill the socket buffer, small enough that
                // cancellation between parts is prompt.
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(64 * 1024, bytes.size - offset)
                    out.write(bytes, offset, n)
                    offset += n
                }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val payload = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            RawResponse(status, payload, timeout = false)
        } catch (e: SocketTimeoutException) {
            RawResponse(STATUS_TRANSPORT, "", timeout = true)
        } catch (e: IOException) {
            RawResponse(STATUS_TRANSPORT, "", timeout = false)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Opens a Server-Sent Events stream and reports every frame to [onEvent],
     * reconnecting until the calling coroutine is cancelled.
     *
     * Realtime is an enhancement, not a dependency: a stream that cannot connect
     * backs off and retries silently while the screen keeps serving its cache, so
     * a proxy that blocks streaming degrades freshness and nothing else. Frames
     * are parsed here and dispatched on the caller's dispatcher, so no screen ever
     * touches a socket.
     */
    internal suspend fun stream(
        path: String,
        onEvent: (name: String, data: JSONObject) -> Unit,
    ) {
        if (baseUrl.isBlank()) return
        var backoff = MIN_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            val bearer = tokens?.accessToken()
            if (bearer == null) {
                // No token: the session is finished. SessionManager drives re-login;
                // this loop must not spin against a closed door.
                return
            }
            val clean = withContext(Dispatchers.IO) { readStream(path, bearer, onEvent) }
            if (!currentCoroutineContext().isActive) return
            // A clean close is the server rotating the connection (it does that every
            // ten minutes), so reconnect immediately; a failure earns backoff.
            backoff = if (clean) MIN_BACKOFF_MS else (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            delay(if (clean) 0L else backoff + (0..500).random())
        }
    }

    /** Returns true when the server closed the stream cleanly. */
    private fun readStream(
        path: String,
        bearer: String,
        onEvent: (name: String, data: JSONObject) -> Unit,
    ): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(buildUrl(path, emptyMap())).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                // Longer than the server heartbeat: silence past this is a dead socket.
                readTimeout = STREAM_IDLE_TIMEOUT_MS
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false

            connection.inputStream.bufferedReader().use { reader ->
                var event = ""
                val payload = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith(":") -> Unit // heartbeat
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> payload.append(line.removePrefix("data:").trim())
                        line.isEmpty() -> {
                            if (event.isNotEmpty()) {
                                val json = runCatching { JSONObject(payload.toString()) }.getOrNull()
                                if (json != null) onEvent(event, json)
                            }
                            event = ""
                            payload.setLength(0)
                        }
                    }
                }
            }
            true // end of stream: the server rotated the connection
        } catch (e: IOException) {
            Log.d(TAG, "stream $path ended: ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun <T> send(
        method: String,
        path: String,
        query: Map<String, String>,
        body: JSONObject?,
        authenticated: Boolean,
        retries: Int,
        map: (JSONObject) -> T,
    ): ApiResponse<T> {
        if (baseUrl.isBlank()) {
            return ApiResponse.failure(0, "not_configured", "This build has no server configured.")
        }

        var bearer: String? = null
        if (authenticated) {
            bearer = tokens?.accessToken()
            if (bearer == null) return ApiResponse.sessionExpired()
        }

        var attempt = 0
        while (true) {
            val raw = withContext(Dispatchers.IO) {
                execute(method, path, query, body, bearer)
            }

            // A 401 on an authenticated call means the access token died early —
            // revoked, or rotated by another device. Renew once and replay; a second
            // 401 is a real dead end, not something to loop on.
            if (raw.status == HttpURLConnection.HTTP_UNAUTHORIZED && authenticated && attempt == 0) {
                val renewed = tokens?.renew(bearer)
                if (renewed == null) return ApiResponse.sessionExpired()
                bearer = renewed
                attempt++
                continue
            }

            // Retry idempotent reads once on a transport failure; never a POST, which
            // may already have created an account or consumed a one-time code.
            if (raw.status == STATUS_TRANSPORT && attempt < retries) {
                attempt++
                continue
            }

            return decode(raw, map)
        }
    }

    private fun <T> decode(raw: RawResponse, map: (JSONObject) -> T): ApiResponse<T> {
        if (raw.status == STATUS_TRANSPORT) {
            return if (raw.timeout) ApiResponse.timeout() else ApiResponse.offline()
        }
        val json = runCatching { JSONObject(raw.body) }.getOrNull()
            ?: run {
                // Not our envelope: a proxy answered instead of the service (413 on a
                // body the proxy thinks is too large, 502/504 when it gave up). Carry
                // the status in the message — an opaque string here costs hours.
                Log.w(TAG, "non-envelope response: HTTP ${raw.status} ${raw.body.take(120)}")
                return ApiResponse.failure(
                    raw.status,
                    ApiResponse.CODE_MALFORMED,
                    if (raw.status > 0) {
                        "The server sent something unexpected (HTTP ${raw.status}). Try again."
                    } else {
                        "The server sent something unexpected. Try again."
                    },
                )
            }

        val isError = json.bool("error", raw.status >= 400)
        val message = json.str("message")
        val statusCode = json.int("statusCode", raw.status)
        if (isError) {
            val code = json.str("code").ifEmpty { "error" }
            val fieldErrors = json.obj("data")?.obj("fields")?.let { f ->
                val out = HashMap<String, String>()
                for (k in f.keys()) if (!f.isNull(k)) out[k] = f.opt(k).toString()
                out
            } ?: emptyMap()
            return ApiResponse.failure(
                statusCode,
                code,
                message.ifEmpty { "Something went wrong. Try again." },
                fieldErrors,
            )
        }
        val data = json.obj("data") ?: JSONObject()
        val mapped = runCatching { map(data) }.getOrElse {
            Log.w(TAG, "response mapping failed", it)
            return ApiResponse.failure(
                statusCode,
                ApiResponse.CODE_MALFORMED,
                "The server sent something unexpected. Try again.",
            )
        }
        return ApiResponse.success(statusCode, message, mapped)
    }

    private fun execute(
        method: String,
        path: String,
        query: Map<String, String>,
        body: JSONObject?,
        bearer: String?,
    ): RawResponse {
        val url = buildUrl(path, query)
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("User-Agent", userAgent)
                bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            // Errors arrive on errorStream; reading the wrong one loses the envelope
            // and turns a precise "duplicate_username" into a generic failure.
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val payload = stream?.let { input ->
                val decoded = if (connection.contentEncoding?.contains("gzip") == true) {
                    GZIPInputStream(input)
                } else {
                    input
                }
                decoded.use { it.readBytes().toString(Charsets.UTF_8) }
            }.orEmpty()
            RawResponse(status, payload, timeout = false)
        } catch (e: SocketTimeoutException) {
            RawResponse(STATUS_TRANSPORT, "", timeout = true)
        } catch (e: UnknownHostException) {
            RawResponse(STATUS_TRANSPORT, "", timeout = false)
        } catch (e: IOException) {
            Log.w(TAG, "$method $path failed", e)
            RawResponse(STATUS_TRANSPORT, "", timeout = false)
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = baseUrl.trimEnd('/')
        val suffix = if (path.startsWith("/")) path else "/$path"
        if (query.isEmpty()) return base + suffix
        val encoded = query.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }
        return "$base$suffix?$encoded"
    }

    private data class RawResponse(val status: Int, val body: String, val timeout: Boolean)

    private companion object {
        const val TAG = "ApiClient"
        const val STATUS_TRANSPORT = -1
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        // A part is acknowledged only after the server has streamed it to storage
        // and checked the hash; on a slow link that is well past 20s.
        const val UPLOAD_READ_TIMEOUT_MS = 120_000
        // A chat clip is stored and its message written before the reply; give a
        // 100 MB file on a weak link the room the server gives it.
        const val STREAM_UPLOAD_READ_TIMEOUT_MS = 300_000
        const val STREAM_IDLE_TIMEOUT_MS = 45_000
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
