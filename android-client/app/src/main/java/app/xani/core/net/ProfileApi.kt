package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** Wallet balances. Integers on the wire; formatting is a UI decision. */
@Immutable
data class Balance(
    val coins: Long = 0,
    val diamonds: Long = 0,
    val beans: Long = 0,
    val rev: Long = 0,
) {
    companion object {
        fun from(json: JSONObject) = Balance(
            coins = json.long("coins"),
            diamonds = json.long("diamonds"),
            beans = json.long("beans"),
            rev = json.long("rev"),
        )
    }
}

/**
 * The whole profile screen in one payload — identity, display fields and
 * balances — so opening Profile is one call, not five.
 *
 * [rev] increases on every server-side profile write. The client applies a
 * stream event only when its rev is newer than what it already shows, which is
 * what makes optimistic UI and out-of-order delivery safe.
 */
@Immutable
data class ProfileData(
    val id: Long = 0,
    val username: String = "",
    val fullName: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val avatarUrl: String = "",
    val bio: String = "",
    val status: String = "",
    val location: String = "",
    val birthday: String = "",
    val gender: String = "",
    val rev: Long = 0,
    val usernameCooldownDays: Int = 0,
    val balance: Balance = Balance(),
    val followers: Int = 0,
    val following: Int = 0,
    /** How many people have opened this profile. Only the owner is told. */
    val profileViews: Long = 0,
    /** Lifetime gifting and the rungs it has reached (null = none yet). */
    val sentCoins: Long = 0,
    val receivedCoins: Long = 0,
    val senderLevel: LevelData? = null,
    val receiverLevel: LevelData? = null,
) {
    fun toJson(): JSONObject = jsonOf(
        "followers" to followers,
        "following" to following,
        "profile_views" to profileViews,
        "sent_coins" to sentCoins,
        "received_coins" to receivedCoins,
        "sender_level" to senderLevel?.toJson(),
        "receiver_level" to receiverLevel?.toJson(),
        "id" to id.toString(),
        "username" to username,
        "full_name" to fullName,
        "email" to email,
        "email_verified" to emailVerified,
        "avatar_url" to avatarUrl,
        "bio" to bio,
        "status" to status,
        "location" to location,
        "birthday" to birthday,
        "gender" to gender,
        "rev" to rev,
        "username_cooldown_days" to usernameCooldownDays,
        "balance" to jsonOf(
            "coins" to balance.coins,
            "diamonds" to balance.diamonds,
            "beans" to balance.beans,
            "rev" to balance.rev,
        ),
    )

    companion object {
        fun from(json: JSONObject) = ProfileData(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            email = json.str("email"),
            emailVerified = json.bool("email_verified"),
            avatarUrl = json.str("avatar_url"),
            bio = json.str("bio"),
            status = json.str("status"),
            location = json.str("location"),
            birthday = json.str("birthday"),
            gender = json.str("gender"),
            rev = json.long("rev"),
            usernameCooldownDays = json.int("username_cooldown_days"),
            balance = json.obj("balance")?.let(Balance::from) ?: Balance(),
            followers = json.int("followers"),
            following = json.int("following"),
            profileViews = json.long("profile_views"),
            sentCoins = json.long("sent_coins"),
            receivedCoins = json.long("received_coins"),
            senderLevel = LevelData.fromOrNull(json.obj("sender_level")),
            receiverLevel = LevelData.fromOrNull(json.obj("receiver_level")),
        )
    }
}

/** The answer to "is this handle free?", asked while the user is still typing. */
@Immutable
data class UsernameCheck(
    val username: String,
    val available: Boolean,
    /** ok · invalid · reserved · taken · current · cooldown */
    val reason: String,
    val cooldownDays: Int,
) {
    companion object {
        fun from(json: JSONObject) = UsernameCheck(
            username = json.str("username"),
            available = json.bool("available"),
            reason = json.str("reason"),
            cooldownDays = json.int("cooldown_days"),
        )
    }
}

/**
 * Profile endpoints.
 *
 * Writes are partial: a field that is null is not sent, so the status editor and
 * the edit screen cannot overwrite each other's work, and the request body stays
 * small enough to fit one packet.
 */
class ProfileApi internal constructor(private val api: ApiClient) {

    suspend fun me(): ApiResponse<ProfileData> =
        api.get("/v1/profile/me", authenticated = true) { data ->
            ProfileData.from(data.obj("profile") ?: JSONObject())
        }

    suspend fun update(
        fullName: String? = null,
        bio: String? = null,
        status: String? = null,
        location: String? = null,
        gender: String? = null,
    ): ApiResponse<ProfileData> = api.post(
        "/v1/profile/update",
        jsonOf(
            "full_name" to fullName,
            "bio" to bio,
            "status" to status,
            "location" to location,
            "gender" to gender,
        ),
        authenticated = true,
    ) { data -> ProfileData.from(data.obj("profile") ?: JSONObject()) }

    /** The one-column write behind "what's on your mind". */
    suspend fun setStatus(status: String): ApiResponse<ProfileData> = api.post(
        "/v1/profile/status",
        jsonOf("status" to status),
        authenticated = true,
    ) { data -> ProfileData.from(data.obj("profile") ?: JSONObject()) }

    /** Changes the handle; the server enforces the 30-day window. */
    suspend fun setUsername(username: String): ApiResponse<ProfileData> = api.post(
        "/v1/profile/username",
        jsonOf("username" to username),
        authenticated = true,
    ) { data -> ProfileData.from(data.obj("profile") ?: JSONObject()) }

    suspend fun checkUsername(username: String): ApiResponse<UsernameCheck> =
        api.get("/v1/profile/username/check", mapOf("u" to username), authenticated = true) {
            UsernameCheck.from(it)
        }

    /**
     * Replaces the profile picture with [jpeg] and answers with the whole
     * profile, so the caller repaints from the reply instead of re-reading.
     *
     * The bytes go up raw — the picture is already a square JPEG cut by
     * [app.xani.feature.profile.AvatarPicker], and wrapping it in JSON would
     * cost a third more bytes on exactly the connections that can least afford
     * it. The server crops and re-encodes anyway, so a phone that sends
     * something else is corrected, not trusted.
     */
    suspend fun setAvatar(jpeg: ByteArray): ApiResponse<ProfileData> = api.putBytes(
        "/v1/profile/avatar",
        bytes = jpeg,
        contentType = "image/jpeg",
    ) { data -> ProfileData.from(data.obj("profile") ?: JSONObject()) }

    /**
     * Subscribes to profile and balance changes for the signed-in account. Runs
     * until the caller's scope is cancelled.
     */
    internal suspend fun stream(
        onProfile: (ProfileData) -> Unit,
        onBalance: (Balance) -> Unit,
        onPost: (PostData) -> Unit,
        onPostGone: (Long) -> Unit,
        onCounts: (JSONObject) -> Unit,
        onProfileViews: (Long) -> Unit,
        onResync: () -> Unit,
        onSessionEnd: (message: String) -> Unit = {},
        onChat: (name: String, data: JSONObject) -> Unit = { _, _ -> },
        onCall: (name: String, data: JSONObject) -> Unit = { _, _ -> },
        onActivity: (ActivityData) -> Unit = {},
        onNotice: (NoticeData) -> Unit = {},
    ) = api.stream("/v1/profile/stream") { name, json ->
        val data = json.obj("data") ?: JSONObject()
        when (name) {
            "profile" -> onProfile(ProfileData.from(data))
            // Something happened to me ("x viewed your story"): one lane row.
            "activity" -> onActivity(ActivityData.from(data))
            "notice" -> onNotice(NoticeData.from(data))
            "wallet" -> onBalance(Balance.from(data))
            "post" -> onPost(PostData.from(data))
            "post_gone" -> data.id("post_id").takeIf { it > 0 }?.let(onPostGone)
            // Someone engaged with one of my posts. This arrives on my own
            // stream because I am not subscribed to my post's topic while a
            // stranger is the one watching it.
            "counts" -> onCounts(data)
            // Somebody opened my profile: the header's visit count, nothing else.
            "profile_views" -> onProfileViews(data.long("profile_views"))
            "resync" -> onResync()
            "session_end" -> onSessionEnd(data.str("message").ifBlank { data.str("reason") })
            // Chat rides the same connection: one socket per device.
            else -> when {
                name.startsWith("chat_") -> onChat(name, data)
                // Call signalling and live-room frames share the socket too.
                name.startsWith("call_") || name.startsWith("live_") -> onCall(name, data)
            }
        }
    }
}