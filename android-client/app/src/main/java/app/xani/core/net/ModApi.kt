package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * One account on the in-app moderation screen: identity plus the three levers
 * a delegated moderator holds — screen capture, microphone, account block.
 *
 * `captureOverride` is the direct per-user row: "allow" opens capture even when
 * the role does not, "deny" revokes it even when the role allows, "inherit"
 * means the role decides (`canCapture` is the effective answer either way).
 */
@Immutable
data class ModUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val state: Int,
    val banned: Boolean,
    val canCapture: Boolean = false,
    val captureOverride: String = "inherit",
    val micDisabled: Boolean = false,
    val banReason: String = "",
    val banPermanent: Boolean = false,
    val banUntil: String = "",
) {
    val displayName: String get() = fullName.ifBlank { "@$username" }

    companion object {
        fun from(json: JSONObject): ModUser {
            val ban = json.obj("account_ban")
            return ModUser(
                id = json.id("id"),
                username = json.str("username"),
                fullName = json.str("full_name"),
                avatarUrl = json.str("avatar_url"),
                state = json.int("state"),
                banned = json.bool("banned") || ban != null,
                canCapture = json.bool("can_capture"),
                captureOverride = json.str("capture_override").ifBlank { "inherit" },
                micDisabled = json.bool("mic_disabled"),
                banReason = ban?.str("reason").orEmpty(),
                banPermanent = ban?.bool("permanent") ?: false,
                banUntil = ban?.str("until").orEmpty(),
            )
        }
    }
}

/**
 * In-app moderation endpoints. Reaching them needs mod.app_users (or
 * screen.capture.manage for the capture call) — the server is the gate; the
 * client only decides whether to show the screen.
 */
class ModApi internal constructor(private val api: ApiClient) {

    /** Finds accounts by id, username or name. */
    suspend fun search(query: String): ApiResponse<List<ModUser>> =
        api.get(
            "/v1/mod/users",
            mapOf("q" to query.trim(), "limit" to "8"),
            authenticated = true,
        ) { it.objects("items").map(ModUser::from) }

    /** One account's moderation card with effective capture / mic / ban state. */
    suspend fun user(id: Long): ApiResponse<ModUser> =
        api.get("/v1/mod/users/$id", authenticated = true, map = ModUser::from)

    /** mode: "allow" | "revoke" | "inherit". */
    suspend fun setCapture(id: Long, mode: String): ApiResponse<String> =
        api.post("/v1/mod/users/$id/capture", jsonOf("mode" to mode), authenticated = true) {
            it.str("mode")
        }

    /** Disables (direct deny on voice.mic) or re-enables the microphone. */
    suspend fun setMic(id: Long, disabled: Boolean, reason: String = ""): ApiResponse<Boolean> =
        api.post(
            "/v1/mod/users/$id/mic",
            jsonOf("disabled" to disabled, "reason" to reason.trim()),
            authenticated = true,
        ) { it.bool("mic_disabled") }

    /** Sends a warning to the person's notifications, with the reason. */
    suspend fun warn(id: Long, reason: String): ApiResponse<Boolean> =
        api.post("/v1/mod/users/$id/warn", jsonOf("reason" to reason.trim()), authenticated = true) {
            it.bool("warned")
        }

    /** Account block — same restriction the dashboard applies. minutes 0 = permanent. */
    suspend fun block(id: Long, reason: String, minutes: Int = 0): ApiResponse<Boolean> =
        api.post(
            "/v1/mod/users/$id/block",
            jsonOf(
                "reason" to reason.trim(),
                "duration_minutes" to minutes,
                "permanent" to (minutes <= 0),
            ),
            authenticated = true,
        ) { it.bool("banned") }

    suspend fun unblock(id: Long): ApiResponse<Boolean> =
        api.post("/v1/mod/users/$id/unblock", JSONObject(), authenticated = true) {
            it.bool("banned")
        }
}
