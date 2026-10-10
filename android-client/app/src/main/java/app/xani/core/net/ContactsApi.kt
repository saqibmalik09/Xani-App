package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * The caller's find-friends state. Never contains a contact — only whether the
 * user opted in, the gallery permission they reported, and the match counts.
 */
@Immutable
data class ContactSyncState(
    val enabled: Boolean = false,
    val galleryAllowed: Boolean = false,
    val contactsCount: Int = 0,
    val matchedCount: Int = 0,
) {
    companion object {
        fun from(json: JSONObject): ContactSyncState = ContactSyncState(
            enabled = json.bool("enabled"),
            galleryAllowed = json.bool("gallery_allowed"),
            contactsCount = json.int("contacts_count"),
            matchedCount = json.int("matched_count"),
        )
    }
}

/** Result of one upload batch. */
@Immutable
data class ContactUploadResult(
    val added: Int,
    val contactsCount: Int,
    val matchedCount: Int,
)

/** One person the matcher found in the caller's contacts. */
@Immutable
data class ContactMatch(
    val id: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
) {
    val displayName: String get() = fullName.ifBlank { "@$username" }

    companion object {
        fun from(json: JSONObject): ContactMatch = ContactMatch(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            avatarUrl = json.str("avatar_url"),
        )
    }
}

/**
 * Find-friends endpoints. The phone uploads numbers it already holds so the
 * server can match them against registered accounts. The OS contacts
 * permission on the device is the consent gate; uploads are accepted whether or
 * not the find-friends switch is on, and the switch only governs whether the
 * user is shown suggestions.
 */
class ContactsApi internal constructor(private val api: ApiClient) {

    suspend fun state(): ApiResponse<ContactSyncState> =
        api.get("/v1/contacts/sync", authenticated = true, map = ContactSyncState::from)

    suspend fun setEnabled(enabled: Boolean): ApiResponse<ContactSyncState> =
        api.post("/v1/contacts/sync", jsonOf("enabled" to enabled), authenticated = true) {
            ContactSyncState.from(it)
        }

    /** Reports the OS gallery permission; the app never reads gallery media. */
    suspend fun setGallery(allowed: Boolean): ApiResponse<ContactSyncState> =
        api.post("/v1/contacts/gallery", jsonOf("allowed" to allowed), authenticated = true) {
            ContactSyncState.from(it)
        }

    /** Uploads one batch of raw numbers; stored for matching, not displayed. */
    suspend fun upload(numbers: List<String>): ApiResponse<ContactUploadResult> =
        api.post(
            "/v1/contacts/upload",
            JSONObject().put("numbers", JSONArray(numbers)),
            authenticated = true,
        ) {
            ContactUploadResult(
                added = it.int("added"),
                contactsCount = it.int("contacts_count"),
                matchedCount = it.int("matched_count"),
            )
        }

    suspend fun matches(limit: Int = 60): ApiResponse<List<ContactMatch>> =
        api.get(
            "/v1/contacts/matches",
            mapOf("limit" to limit.toString()),
            authenticated = true,
        ) { it.objects("items").map(ContactMatch::from) }
}
