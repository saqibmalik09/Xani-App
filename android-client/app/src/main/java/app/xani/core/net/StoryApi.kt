package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.io.InputStream

/** The poster / viewer brief every story surface draws. */
@Immutable
data class StoryPersonData(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val frameUrl: String = "",
    val frameMime: String = "",
) {
    val name: String get() = fullName.ifBlank { username }

    companion object {
        fun from(json: JSONObject?) = StoryPersonData(
            id = json?.id("id") ?: 0L,
            username = json?.str("username").orEmpty(),
            fullName = json?.str("full_name").orEmpty(),
            avatarUrl = json?.str("avatar_url").orEmpty(),
            frameUrl = json?.str("frame_url").orEmpty(),
            frameMime = json?.str("frame_mime").orEmpty(),
        )
    }
}

/** One 24-hour story. [mediaUrl] is a key or absolute URL; empty on a text card. */
@Immutable
data class StoryData(
    val id: Long,
    val userId: Long,
    /** image | video | text */
    val kind: String,
    val mediaUrl: String,
    val text: String,
    /** "#rrggbb" tint of a text card, or empty. */
    val bg: String,
    /** public | friends | only_me */
    val audience: String,
    val views: Int,
    val seen: Boolean,
    val createdMs: Long,
    val expiresMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = StoryData(
            id = json.id("id"),
            userId = json.id("user_id"),
            kind = json.str("kind", "image"),
            mediaUrl = json.str("media_url"),
            text = json.str("text"),
            bg = json.str("bg"),
            audience = json.str("audience", "public"),
            views = json.int("views"),
            seen = json.bool("seen"),
            createdMs = json.long("created_ms"),
            expiresMs = json.long("expires_ms"),
        )
    }
}

/** One poster's ring on the rail: their live stories, oldest first. */
@Immutable
data class StoryGroupData(
    val user: StoryPersonData,
    val stories: List<StoryData>,
    val unseen: Int,
    val mine: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = StoryGroupData(
            user = StoryPersonData.from(json.obj("user")),
            stories = json.objects("stories").map(StoryData::from),
            unseen = json.int("unseen"),
            mine = json.bool("mine"),
        )
    }
}

/** One "seen by" row. */
@Immutable
data class StoryViewerData(val user: StoryPersonData, val viewedMs: Long) {
    companion object {
        fun from(json: JSONObject) = StoryViewerData(
            user = StoryPersonData.from(json.obj("user")),
            viewedMs = json.long("viewed_ms"),
        )
    }
}

/** One row of my activity lane: [actor] did [kind] to [refId]. */
@Immutable
data class ActivityData(
    val id: Long,
    /** story_view | … */
    val kind: String,
    val actor: StoryPersonData,
    val refId: Long,
    val createdMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = ActivityData(
            id = json.id("id"),
            kind = json.str("kind"),
            actor = StoryPersonData.from(json.obj("actor")),
            refId = json.id("ref_id"),
            createdMs = json.long("created_ms"),
        )
    }
}

@Immutable
data class ActivityPageData(val items: List<ActivityData>, val nextCursor: Long)

@Immutable
data class NoticeData(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String,
    val refId: Long,
    val createdMs: Long,
) {
    companion object {
        fun from(json: JSONObject) = NoticeData(
            id = json.id("id"),
            kind = json.str("kind"),
            title = json.str("title"),
            body = json.str("body"),
            refId = json.id("ref_id"),
            createdMs = json.long("created_ms"),
        )
    }
}

@Immutable
data class NoticePageData(val items: List<NoticeData>, val nextCursor: String)

/** Stories (24-hour status) and the activity lane. */
class StoryApi internal constructor(private val api: ApiClient) {

    /** Everyone with a live story I may see; [userId] narrows to one poster. */
    suspend fun rail(userId: Long = 0L): ApiResponse<List<StoryGroupData>> = api.get(
        "/v1/stories",
        if (userId != 0L) mapOf("user_id" to userId.toString()) else emptyMap(),
        authenticated = true,
    ) { data -> data.objects("items").map(StoryGroupData::from) }

    /**
     * Uploads a story picture or clip the device already encoded; the key
     * comes back for [post]. Streams from [open] so a clip never sits in memory.
     */
    suspend fun putMedia(contentType: String, length: Long, open: () -> InputStream?): ApiResponse<String> =
        api.putStream("/v1/stories/media", emptyMap(), emptyMap(), contentType, length, open) { it.str("media_key") }

    suspend fun putImage(jpeg: ByteArray): ApiResponse<String> =
        api.putBytes("/v1/stories/media", jpeg, contentType = "image/jpeg") { it.str("media_key") }

    suspend fun post(kind: String, mediaKey: String, text: String, bg: String, audience: String): ApiResponse<StoryData> = api.post(
        "/v1/stories",
        jsonOf("kind" to kind, "media_key" to mediaKey, "text" to text, "bg" to bg, "audience" to audience),
        authenticated = true,
    ) { data -> StoryData.from(data.obj("story") ?: JSONObject()) }

    suspend fun delete(id: Long): ApiResponse<Unit> = api.delete("/v1/stories/$id", authenticated = true) { }

    suspend fun view(id: Long): ApiResponse<Unit> = api.post("/v1/stories/$id/view", JSONObject(), authenticated = true)

    suspend fun viewers(id: Long): ApiResponse<List<StoryViewerData>> =
        api.get("/v1/stories/$id/viewers", authenticated = true) { data -> data.objects("items").map(StoryViewerData::from) }

    suspend fun activity(cursor: Long = 0L, limit: Int = 40): ApiResponse<ActivityPageData> = api.get(
        "/v1/activity",
        buildMap {
            if (cursor != 0L) put("cursor", cursor.toString())
            put("limit", limit.toString())
        },
        authenticated = true,
    ) { data -> ActivityPageData(data.objects("items").map(ActivityData::from), data.id("next_cursor")) }

    suspend fun notices(before: String = "", limit: Int = 30): ApiResponse<NoticePageData> = api.get(
        "/v1/notices",
        buildMap {
            if (before.isNotEmpty()) put("before", before)
            put("limit", limit.toString())
        },
        authenticated = true,
    ) { data -> NoticePageData(data.objects("items").map(NoticeData::from), data.str("next_cursor")) }

    /** Notices above the read boundary — the Notifications tab badge (capped at 100). */
    suspend fun noticesUnread(): ApiResponse<Int> =
        api.get("/v1/notices/unread", emptyMap(), authenticated = true) { it.int("count") }

    /** The tab was opened: move the read boundary up to [lastId] (0 = everything so far). */
    suspend fun noticesSeen(lastId: Long = 0L): ApiResponse<Unit> =
        api.post("/v1/notices/seen", jsonOf("last_id" to lastId.toString()), authenticated = true) { }
}
