package app.xani.feature.upload

import androidx.compose.runtime.Immutable
import app.xani.core.net.PostForm
import org.json.JSONArray
import org.json.JSONObject

/** Where a task is in its life. Ordered: a task only moves forward. */
enum class UploadState {
    /** Waiting for its turn (one upload at a time keeps each one fast). */
    Queued,

    /** Parts in flight. [UploadTask.bytesSent] moves. */
    Uploading,

    /** Every part landed; the complete call is in flight. */
    Completing,

    /** Server has the post in `processing`; the encode ladder is running. */
    Processing,

    /** Post is live. Strip shows "Posted" then dismisses itself. */
    Posted,

    /** Gave up. Strip offers retry. */
    Failed,
    ;

    val isTerminal: Boolean get() = this == Posted || this == Failed
    val isActive: Boolean get() = this == Uploading || this == Completing
}

/**
 * One post in flight, persisted so a killed app finishes it on relaunch.
 *
 * Everything the resume needs is here: the source URI, the declared facts, the
 * form, the server session, and which parts already landed. Bytes are never
 * copied — the source is re-read by range on resume.
 */
@Immutable
data class UploadTask(
    val id: String,
    /** Idempotency key for the server session; rotated only if the session is lost. */
    val idem: String = id,
    val uri: String,
    val kind: String,
    val mime: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Int,
    val capturedAt: String?,
    val form: PostForm,
    val createdAt: Long,
    val state: UploadState = UploadState.Queued,
    val sessionId: Long = 0,
    val chunkSize: Int = 0,
    val parts: Int = 0,
    val done: Set<Int> = emptySet(),
    val bytesSent: Long = 0,
    val postId: Long = 0,
    val error: String = "",
    val errorCode: String = "",
    val thumbKey: String = "",
) {
    val isVideo: Boolean get() = kind == "video"

    /** 0..1 across upload; processing holds at 1 until the post is live. */
    val progress: Float
        get() = when (state) {
            UploadState.Queued -> 0f
            UploadState.Uploading -> if (size > 0) (bytesSent.toFloat() / size).coerceIn(0f, 0.98f) else 0f
            else -> 1f
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("idem", idem)
        put("uri", uri)
        put("kind", kind)
        put("mime", mime)
        put("size", size)
        put("width", width)
        put("height", height)
        put("duration_ms", durationMs)
        put("captured_at", capturedAt ?: JSONObject.NULL)
        put("caption", form.caption)
        put("visibility", form.visibility)
        put("allow_comments", form.allowComments)
        put("allow_duet", form.allowDuet)
        put("created_at", createdAt)
        put("state", state.name)
        put("session_id", sessionId)
        put("chunk_size", chunkSize)
        put("parts", parts)
        put("done", JSONArray(done.sorted()))
        put("bytes_sent", bytesSent)
        put("post_id", postId)
        put("error", error)
        put("error_code", errorCode)
        put("thumb_key", thumbKey)
    }

    companion object {
        fun from(json: JSONObject): UploadTask? {
            val id = json.optString("id")
            if (id.isEmpty()) return null
            val doneArr = json.optJSONArray("done")
            val done = HashSet<Int>()
            if (doneArr != null) for (i in 0 until doneArr.length()) done.add(doneArr.optInt(i))
            val state = runCatching { UploadState.valueOf(json.optString("state")) }.getOrDefault(UploadState.Queued)
            return UploadTask(
                id = id,
                idem = json.optString("idem").ifEmpty { id },
                uri = json.optString("uri"),
                kind = json.optString("kind", "video"),
                mime = json.optString("mime"),
                size = json.optLong("size"),
                width = json.optInt("width"),
                height = json.optInt("height"),
                durationMs = json.optInt("duration_ms"),
                capturedAt = if (json.isNull("captured_at")) null else json.optString("captured_at"),
                form = PostForm(
                    caption = json.optString("caption"),
                    visibility = json.optString("visibility", "public"),
                    allowComments = json.optBoolean("allow_comments", true),
                    allowDuet = json.optBoolean("allow_duet", true),
                ),
                createdAt = json.optLong("created_at"),
                // An interrupted upload resumes as Uploading; an interrupted
                // complete is re-sent (idempotent on the server).
                state = if (state.isActive) UploadState.Queued else state,
                sessionId = json.optLong("session_id"),
                chunkSize = json.optInt("chunk_size"),
                parts = json.optInt("parts"),
                done = done,
                bytesSent = json.optLong("bytes_sent"),
                postId = json.optLong("post_id"),
                error = json.optString("error"),
                errorCode = json.optString("error_code"),
                thumbKey = json.optString("thumb_key"),
            )
        }
    }
}
