package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Another account as a list row shows it: a search hit, a follower, someone the
 * user follows, or a post's author opened from the feed.
 */
@Immutable
data class PersonData(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val verified: Boolean,
    val followers: Int,
    /** The signed-in user follows this person. */
    val following: Boolean,
    /** This person follows the signed-in user. */
    val followsYou: Boolean,
    val bio: String = "",
    val status: String = "",
    val followingCount: Int = 0,
    /** The avatar frame this person is wearing; key or absolute URL. */
    val frameUrl: String = "",
    val frameMime: String = "",
    /** The badge worn next to the name; key or absolute URL. */
    val badgeUrl: String = "",
    val badgeMime: String = "",
    /** Gifting rungs reached; null = none yet (nothing is drawn). */
    val senderLevel: LevelData? = null,
    val receiverLevel: LevelData? = null,
) {
    companion object {
        fun from(json: JSONObject) = PersonData(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
            verified = json.bool("verified"),
            followers = json.int("followers"),
            following = json.bool("following"),
            followsYou = json.bool("follows_you"),
            bio = json.str("bio"),
            status = json.str("status"),
            followingCount = json.int("following_count"),
            frameUrl = json.str("frame_url"),
            frameMime = json.str("frame_mime"),
            badgeUrl = json.str("badge_url"),
            badgeMime = json.str("badge_mime"),
            senderLevel = LevelData.fromOrNull(json.obj("sender_level")),
            receiverLevel = LevelData.fromOrNull(json.obj("receiver_level")),
        )
    }
}

/** Server ack for a follow toggle: both counters after the change. */
@Immutable
data class FollowAck(val userId: Long, val following: Boolean, val followers: Int, val myFollowing: Int)

/** One page of people. */
@Immutable
data class PeoplePage(val items: List<PersonData>, val next: String) {
    companion object {
        fun from(data: JSONObject) = PeoplePage(
            items = data.objects("items").map(PersonData::from),
            next = data.str("next_cursor").ifEmpty { data.str("next") },
        )
    }
}

/** One row of "who viewed my profile": the person and when they last came by. */
@Immutable
data class VisitData(val person: PersonData, val at: String, val visits: Int) {
    companion object {
        fun from(json: JSONObject) = VisitData(
            person = PersonData.from(json.obj("person") ?: JSONObject()),
            at = json.str("at"),
            visits = json.int("visits"),
        )
    }
}

/** One page of profile visits. */
@Immutable
data class VisitPage(val items: List<VisitData>, val next: String) {
    companion object {
        fun from(data: JSONObject) = VisitPage(
            items = data.objects("items").map(VisitData::from),
            next = data.str("next_cursor").ifEmpty { data.str("next") },
        )
    }
}

/** Typed people + follow-graph endpoints bound to the live session. */
class PeopleApi internal constructor(private val api: ApiClient) {

    /** Search by @username, email or name. Answers per keystroke. */
    suspend fun search(q: String, limit: Int = 20): ApiResponse<List<PersonData>> =
        api.get("/v1/users/search", mapOf("q" to q, "limit" to limit.toString()), authenticated = true) {
            it.objects("items").map(PersonData::from)
        }

    /**
     * One person's public profile. [visit] is the profile screen opening it,
     * which is what counts as a profile visit for its owner — any other reader
     * of this route (a chat header, a post's author) passes false and moves no
     * number.
     */
    suspend fun get(id: Long, visit: Boolean = false): ApiResponse<PersonData> = api.get(
        "/v1/users/$id",
        if (visit) mapOf("visit" to "1") else emptyMap(),
        authenticated = true,
    ) { PersonData.from(it.obj("user") ?: JSONObject()) }

    /** Who opened my profile this month, latest first. */
    suspend fun visits(cursor: String = "", limit: Int = 30): ApiResponse<VisitPage> = api.get(
        "/v1/profile/visits",
        buildMap {
            put("limit", limit.toString())
            if (cursor.isNotEmpty()) put("cursor", cursor)
        },
        authenticated = true,
    ) { VisitPage.from(it) }

    suspend fun follow(id: Long, on: Boolean): ApiResponse<FollowAck> =
        api.post("/v1/users/$id/follow", jsonOf("on" to on), authenticated = true) { data ->
            val f = data.obj("follow") ?: JSONObject()
            FollowAck(
                userId = f.id("user_id").takeIf { it > 0 } ?: id,
                following = f.bool("following", on),
                followers = f.int("followers"),
                myFollowing = f.int("my_following"),
            )
        }

    suspend fun followers(id: Long, cursor: String = "", limit: Int = 30): ApiResponse<PeoplePage> =
        page("/v1/users/$id/followers", cursor, limit)

    suspend fun following(id: Long, cursor: String = "", limit: Int = 30): ApiResponse<PeoplePage> =
        page("/v1/users/$id/following", cursor, limit)

    /** Accounts the signed-in user has blocked, newest first. */
    suspend fun blocked(cursor: String = "", limit: Int = 30): ApiResponse<PeoplePage> =
        page("/v1/me/blocked", cursor, limit)

    /** Accounts the signed-in user, as a host, keeps out of their lives. */
    suspend fun liveBlocked(cursor: String = "", limit: Int = 30): ApiResponse<PeoplePage> =
        page("/v1/me/live-blocked", cursor, limit)

    suspend fun liveUnblock(id: Long): ApiResponse<Unit> =
        api.post("/v1/me/live-blocked/$id/unblock", JSONObject(), authenticated = true)

    private suspend fun page(path: String, cursor: String, limit: Int): ApiResponse<PeoplePage> = api.get(
        path,
        buildMap {
            put("limit", limit.toString())
            if (cursor.isNotEmpty()) put("cursor", cursor)
        },
        authenticated = true,
    ) { PeoplePage.from(it) }
}
