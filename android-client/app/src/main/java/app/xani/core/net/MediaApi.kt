package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** A resumable upload session as the server sees it. */
@Immutable
data class UploadSession(
    val id: Long,
    val chunkSize: Int,
    val parts: Int,
    val received: Set<Int>,
    val state: String,
    val postId: Long = 0,
) {
    companion object {
        fun from(json: JSONObject): UploadSession {
            val arr = json.optJSONArray("received")
            val received = HashSet<Int>(arr?.length() ?: 0)
            if (arr != null) for (i in 0 until arr.length()) received.add(arr.optInt(i))
            return UploadSession(
                id = json.id("id"),
                chunkSize = json.int("chunk_size"),
                parts = json.int("parts"),
                received = received,
                state = json.str("state", "open"),
                postId = json.id("post_id"),
            )
        }
    }
}

/** One produced file. [key] is relative; prefix with `MediaConfig.baseUrl`. */
@Immutable
data class Rendition(
    val key: String,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0,
    val kbps: Int = 0,
) {
    companion object {
        fun from(json: JSONObject) = Rendition(
            key = json.str("key"),
            mime = json.str("mime"),
            width = json.int("w"),
            height = json.int("h"),
            bytes = json.long("bytes"),
            kbps = json.int("kbps"),
        )
    }
}

/** The author slice a feed card shows. */
@Immutable
data class PostAuthor(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val verified: Boolean,
) {
    companion object {
        val Unknown = PostAuthor(0, "", "", "", false)
        fun from(json: JSONObject?): PostAuthor = json?.let {
            PostAuthor(
                id = it.id("id"),
                username = it.str("username"),
                fullName = it.str("full_name"),
                avatarUrl = it.str("avatar_url"),
                verified = it.bool("verified"),
            )
        } ?: Unknown
    }
}

/** Display counters — straight off the post row, no client math. */
@Immutable
data class PostCounts(
    val likes: Int = 0,
    val comments: Int = 0,
    val saves: Int = 0,
    val shares: Int = 0,
    val views: Long = 0,
) {
    companion object {
        fun from(json: JSONObject?): PostCounts = json?.let {
            PostCounts(it.int("likes"), it.int("comments"), it.int("saves"), it.int("shares"), it.long("views"))
        } ?: PostCounts()
    }
}

/**
 * A counter move for one post, as the realtime channel delivers it.
 *
 * Every counter travels together, so one frame repaints a whole card rail (and
 * a grid cell's view count) with no follow-up read.
 */
@Immutable
data class PostCountsEvent(val postId: Long, val counts: PostCounts) {
    companion object {
        /** Returns null for a frame without a usable post id. */
        fun from(json: JSONObject): PostCountsEvent? {
            val id = json.id("post_id").takeIf { it > 0 } ?: return null
            return PostCountsEvent(id, PostCounts.from(json.obj("counts")))
        }
    }
}

/** A post: what the author sees while it processes and what the feed plays. */
@Immutable
data class PostData(
    val id: Long,
    val authorId: Long,
    val kind: String, // video | photo | text
    val state: String, // processing | live | failed | hidden | removed
    val caption: String,
    val visibility: String,
    val createdAt: String,
    val author: PostAuthor = PostAuthor.Unknown,
    val counts: PostCounts = PostCounts(),
    val liked: Boolean = false,
    val saved: Boolean = false,
    /** The viewer follows this post's author. */
    val following: Boolean = false,
    val allowComments: Boolean = true,
    val mediaState: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Int = 0,
    val renditions: Map<String, Rendition> = emptyMap(),
    val error: String = "",
) {
    val isLive: Boolean get() = state == "live"
    val isRestricted: Boolean get() = state == "restricted" || state == "hidden"
    val isFailed: Boolean get() = state == "failed"
    val isVideo: Boolean get() = kind == "video"
    val isText: Boolean get() = kind == "text"

    /** Best still for a grid cell. */
    val thumbKey: String? get() = renditions["thumb"]?.key ?: renditions["poster"]?.key ?: renditions["md"]?.key

    /** Full-screen still: the poster for video, the largest image otherwise. */
    val posterKey: String? get() = renditions["poster"]?.key ?: renditions["full"]?.key ?: renditions["md"]?.key ?: renditions["thumb"]?.key

    /** Width/height as the feed should letterbox it; 9:16 when unknown. */
    val aspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 9f / 16f

    /**
     * Video rendition for this device class: the tallest ladder rung that fits
     * [maxHeight]; the smallest rung when nothing fits (a 144p rung always plays).
     */
    fun videoKey(maxHeight: Int): String? {
        var best: Rendition? = null
        var smallest: Rendition? = null
        for ((name, r) in renditions) {
            if (!name.endsWith("p") || r.mime.startsWith("video/").not()) continue
            if (smallest == null || r.height < smallest.height) smallest = r
            if (r.height <= maxHeight && (best == null || r.height > best.height)) best = r
        }
        return (best ?: smallest)?.key
    }

    companion object {
        fun from(json: JSONObject): PostData {
            val media = json.obj("media")
            val rends = LinkedHashMap<String, Rendition>()
            media?.obj("renditions")?.let { r ->
                val keys = r.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    r.optJSONObject(name)?.let { rends[name] = Rendition.from(it) }
                }
            }
            val viewer = json.obj("viewer")
            return PostData(
                id = json.id("id"),
                authorId = json.id("author_id"),
                kind = json.str("kind"),
                state = json.str("state", "processing"),
                caption = json.str("caption"),
                visibility = json.str("visibility", "public"),
                createdAt = json.str("created_at"),
                author = PostAuthor.from(json.obj("author")),
                counts = PostCounts.from(json.obj("counts")),
                liked = viewer?.bool("liked") ?: false,
                saved = viewer?.bool("saved") ?: false,
                following = viewer?.bool("following") ?: false,
                allowComments = json.bool("allow_comments", true),
                mediaState = media?.str("state").orEmpty(),
                width = media?.int("w") ?: 0,
                height = media?.int("h") ?: 0,
                durationMs = media?.int("duration_ms") ?: 0,
                renditions = rends,
                error = media?.str("error").orEmpty(),
            )
        }
    }
}

/** One page of posts plus the raw envelope data (for the on-disk head cache). */
@Immutable
data class PostPage(val items: List<PostData>, val next: String, val raw: String) {
    companion object {
        fun from(data: JSONObject) = PostPage(
            items = data.objects("items").map(PostData::from),
            next = data.str("next_cursor").ifEmpty { data.str("next") },
            raw = data.toString(),
        )
    }
}

/** One comment with its author. */
@Immutable
data class CommentData(
    val id: Long,
    val postId: Long,
    val parentId: Long,
    val author: PostAuthor,
    val body: String,
    /** Storage key of an attached picture; empty for a text-only comment. */
    val imageKey: String,
    val likes: Int,
    /** Whether the caller has liked this comment (list reads only). */
    val liked: Boolean,
    val createdAt: String,
) {
    companion object {
        fun from(json: JSONObject) = CommentData(
            id = json.id("id"),
            postId = json.id("post_id"),
            parentId = json.id("parent_id"),
            author = PostAuthor.from(json.obj("author")),
            body = json.str("body"),
            imageKey = json.str("image_key"),
            likes = json.int("likes"),
            liked = json.bool("liked"),
            createdAt = json.str("created_at"),
        )
    }
}

/** Server ack for a like/save: the true count the optimistic number snaps to. */
@Immutable
data class ReactionAck(val postId: Long, val on: Boolean, val count: Int)

/** Everything the client declares before the first byte. */
data class UploadDeclaration(
    val idem: String,
    val kind: String, // video | image
    val mime: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Int,
    val capturedAt: String?, // RFC3339 or null
)

/** The post form. */
data class PostForm(
    val caption: String,
    val visibility: String = "public",
    val allowComments: Boolean = true,
    val allowDuet: Boolean = true,
)

/**
 * Typed media endpoints. The upload protocol is: [open] → [putPart] for every
 * part (parallel, any order, retried individually) → [complete]. [status] is the
 * resume read after a relaunch.
 */
class MediaApi internal constructor(private val api: ApiClient) {

    suspend fun open(d: UploadDeclaration): ApiResponse<UploadSession> = api.post(
        "/v1/media/uploads",
        jsonOf(
            "idem" to d.idem,
            "kind" to d.kind,
            "mime" to d.mime,
            "size" to d.size,
            "width" to d.width,
            "height" to d.height,
            "duration_ms" to d.durationMs,
            "captured_at" to d.capturedAt,
        ),
        authenticated = true,
    ) { UploadSession.from(it.obj("upload") ?: JSONObject()) }

    suspend fun status(id: Long): ApiResponse<UploadSession> =
        api.get("/v1/media/uploads/$id", authenticated = true) {
            UploadSession.from(it.obj("upload") ?: JSONObject())
        }

    suspend fun putPart(id: Long, n: Int, bytes: ByteArray, sha256Hex: String): ApiResponse<UploadSession> =
        api.putBytes("/v1/media/uploads/$id/parts/$n", bytes, sha256Hex) {
            UploadSession.from(it.obj("upload") ?: JSONObject())
        }

    suspend fun complete(id: Long, form: PostForm): ApiResponse<PostData> = api.post(
        "/v1/media/uploads/$id/complete",
        jsonOf(
            "caption" to form.caption,
            "visibility" to form.visibility,
            "allow_comments" to form.allowComments,
            "allow_duet" to form.allowDuet,
        ),
        authenticated = true,
    ) { PostData.from(it.obj("post") ?: JSONObject()) }

    suspend fun abort(id: Long): ApiResponse<Unit> =
        api.post("/v1/media/uploads/$id/abort", JSONObject(), authenticated = true)

    suspend fun post(id: Long): ApiResponse<PostData> =
        api.get("/v1/posts/$id", authenticated = true) { PostData.from(it.obj("post") ?: JSONObject()) }

    suspend fun mine(before: Long = 0, limit: Int = 24): ApiResponse<PostPage> = api.get(
        "/v1/posts/mine",
        buildMap {
            put("limit", limit.toString())
            if (before > 0) put("before", before.toString())
        },
        authenticated = true,
    ) { PostPage.from(it) }

    /** The owner's own tabs: posts they liked, or saved for later. */
    suspend fun liked(before: Long = 0, limit: Int = 24): ApiResponse<PostPage> = reacted("liked", before, limit)

    suspend fun saved(before: Long = 0, limit: Int = 24): ApiResponse<PostPage> = reacted("saved", before, limit)

    private suspend fun reacted(tab: String, before: Long, limit: Int): ApiResponse<PostPage> = api.get(
        "/v1/posts/$tab",
        buildMap {
            put("limit", limit.toString())
            if (before > 0) put("before", before.toString())
        },
        authenticated = true,
    ) { PostPage.from(it) }

    suspend fun byUser(userId: Long, before: Long = 0, limit: Int = 24): ApiResponse<PostPage> = api.get(
        "/v1/users/$userId/posts",
        buildMap {
            put("limit", limit.toString())
            if (before > 0) put("before", before.toString())
        },
        authenticated = true,
    ) { PostPage.from(it) }

    /** The For You page; empty cursor is the head. */
    suspend fun feed(cursor: String = "", limit: Int = 12): ApiResponse<PostPage> = api.get(
        "/v1/feed",
        buildMap {
            put("limit", limit.toString())
            if (cursor.isNotEmpty()) put("cursor", cursor)
        },
        authenticated = true,
    ) { PostPage.from(it) }

    suspend fun postText(text: String, visibility: String = "public"): ApiResponse<PostData> = api.post(
        "/v1/posts/text",
        jsonOf("text" to text, "visibility" to visibility),
        authenticated = true,
    ) { PostData.from(it.obj("post") ?: JSONObject()) }

    suspend fun like(postId: Long, on: Boolean): ApiResponse<ReactionAck> =
        api.post("/v1/posts/$postId/like", jsonOf("on" to on), authenticated = true) { ack(it.obj("like"), postId) }

    suspend fun save(postId: Long, on: Boolean): ApiResponse<ReactionAck> =
        api.post("/v1/posts/$postId/save", jsonOf("on" to on), authenticated = true) { ack(it.obj("save"), postId) }

    /**
     * Counts a watch. Fire-and-forget: the server stores one row per viewer per
     * post, so a duplicate is a cheap success and the caller never has to
     * remember across installs whether it already sent this.
     */
    suspend fun view(postId: Long): ApiResponse<Unit> =
        api.post("/v1/posts/$postId/view", JSONObject(), authenticated = true)

    /** [sort] is "top" (most liked) or "" / "new" for newest first. */
    suspend fun comments(
        postId: Long,
        cursor: String = "",
        limit: Int = 30,
        sort: String = "",
    ): ApiResponse<Pair<List<CommentData>, String>> =
        api.get(
            "/v1/posts/$postId/comments",
            buildMap {
                put("limit", limit.toString())
                if (cursor.isNotEmpty()) put("cursor", cursor)
                if (sort.isNotEmpty()) put("sort", sort)
            },
            authenticated = true,
        ) { data -> data.objects("items").map(CommentData::from) to data.str("next_cursor").ifEmpty { data.str("next") } }

    /**
     * An author's edit. Only the fields passed are sent, so saving the caption
     * cannot silently overwrite the audience the user set on another screen.
     */
    suspend fun editPost(
        postId: Long,
        caption: String? = null,
        visibility: String? = null,
        allowComments: Boolean? = null,
    ): ApiResponse<PostData> = api.post(
        "/v1/posts/$postId/edit",
        jsonOf("caption" to caption, "visibility" to visibility, "allow_comments" to allowComments),
        authenticated = true,
    ) { PostData.from(it.obj("post") ?: JSONObject()) }

    /** Retires the author's own post. */
    suspend fun deletePost(postId: Long): ApiResponse<Long> =
        api.delete("/v1/posts/$postId", authenticated = true) { it.id("post_id").takeIf { id -> id > 0 } ?: postId }

    /** Returns the stored comment and the post's new comment count. */
    suspend fun comment(postId: Long, body: String, parentId: Long = 0, imageKey: String = ""): ApiResponse<Pair<CommentData, Int>> = api.post(
        "/v1/posts/$postId/comments",
        jsonOf(
            "body" to body,
            "parent_id" to (if (parentId > 0) parentId.toString() else null),
            "image_key" to imageKey.ifEmpty { null },
        ),
        authenticated = true,
    ) { data -> CommentData.from(data.obj("comment") ?: JSONObject()) to data.int("count") }

    /** Likes or unlikes one comment; answers the true count. */
    suspend fun likeComment(postId: Long, commentId: Long, on: Boolean): ApiResponse<Int> = api.post(
        "/v1/posts/$postId/comments/$commentId/like",
        jsonOf("on" to on),
        authenticated = true,
    ) { it.int("likes") }

    /** Uploads a comment picture the device already downscaled; answers its key. */
    suspend fun putCommentImage(jpeg: ByteArray): ApiResponse<String> =
        api.putBytes("/v1/comments/image", jpeg, contentType = "image/jpeg") { it.str("image_key") }

    /**
     * The per-post realtime channel while a comment sheet is open: new comments
     * and counter moves from every other viewer. Runs until the caller's scope
     * is cancelled.
     */
    internal suspend fun postStream(
        postId: Long,
        onComment: (CommentData, Int) -> Unit,
        onCounts: (JSONObject) -> Unit,
        onCommentLike: (Long, Int) -> Unit = { _, _ -> },
    ) = api.stream("/v1/posts/$postId/stream") { name, json ->
        val data = json.obj("data") ?: JSONObject()
        when (name) {
            "comment" -> data.obj("comment")?.let { onComment(CommentData.from(it), data.int("count")) }
            "comment_like" -> onCommentLike(data.id("comment_id"), data.int("likes"))
            "counts" -> onCounts(data)
        }
    }

    private fun ack(json: JSONObject?, postId: Long) = ReactionAck(
        postId = json?.id("post_id")?.takeIf { it > 0 } ?: postId,
        on = json?.bool("on") ?: true,
        count = json?.int("count") ?: 0,
    )
}
