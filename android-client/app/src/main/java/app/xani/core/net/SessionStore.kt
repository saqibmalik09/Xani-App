package app.xani.core.net

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved login on this device. Tokens stay local so switching accounts is a
 * cache swap + server check, not a full password form.
 */
@Immutable
data class SavedAccount(
    val userId: Long,
    val username: String,
    val fullName: String,
    val avatarUrl: String,
    val email: String,
    val passwordLoginActive: Boolean,
    val tokens: Tokens,
    val access: Access,
    val lastUsedAt: Long,
)

/**
 * Persists the active session and every other account saved on this device.
 *
 * Layout: `accounts` is a JSON array of slots; `active_user_id` points at the
 * one [SessionManager] is using. Cold-start migration keeps a single legacy
 * session as the first (and only) slot.
 */
internal class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAccounts(): List<SavedAccount> {
        migrateLegacyIfNeeded()
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return parseAccounts(raw)
    }

    fun activeUserId(): Long = prefs.getLong(KEY_ACTIVE_ID, 0L)

    fun saveAccounts(accounts: List<SavedAccount>, activeUserId: Long) {
        val arr = JSONArray()
        for (a in accounts) arr.put(a.toJson())
        prefs.edit()
            .putString(KEY_ACCOUNTS, arr.toString())
            .putLong(KEY_ACTIVE_ID, activeUserId)
            .apply()
        // Mirror active into legacy keys so older code paths / debugging still work.
        accounts.firstOrNull { it.userId == activeUserId }?.let { mirrorActive(it) }
            ?: clearLegacy()
    }

    fun loadActiveTokens(): Tokens? =
        loadAccounts().firstOrNull { it.userId == activeUserId() }?.tokens

    fun loadActiveUser(): AccountUser? {
        val a = loadAccounts().firstOrNull { it.userId == activeUserId() } ?: return null
        return AccountUser.Empty.copy(
            id = a.userId,
            username = a.username,
            fullName = a.fullName,
            email = a.email,
            avatarUrl = a.avatarUrl,
            passwordLoginActive = a.passwordLoginActive,
        )
    }

    fun loadActiveAccess(): Access =
        loadAccounts().firstOrNull { it.userId == activeUserId() }?.access ?: Access.None

    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_ACCOUNTS)) return
        val access = prefs.getString(KEY_ACCESS, null) ?: return
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return
        if (access.isBlank() || refresh.isBlank()) return
        val id = prefs.getLong(KEY_USER_ID, 0L)
        if (id == 0L) return
        val tokens = Tokens(
            accessToken = access,
            refreshToken = refresh,
            accessExpiresAtMillis = prefs.getLong(KEY_ACCESS_EXP, 0L),
            refreshExpiresAtMillis = prefs.getLong(KEY_REFRESH_EXP, 0L),
        )
        val slot = SavedAccount(
            userId = id,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            fullName = prefs.getString(KEY_FULL_NAME, "").orEmpty(),
            avatarUrl = prefs.getString(KEY_AVATAR, "").orEmpty(),
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            passwordLoginActive = prefs.getBoolean(KEY_PASSWORD_LOGIN, false),
            tokens = tokens,
            access = Access(
                roles = prefs.getStringSet(KEY_ROLES, emptySet())?.toList().orEmpty(),
                permissions = prefs.getStringSet(KEY_PERMISSIONS, emptySet()).orEmpty(),
            ),
            lastUsedAt = System.currentTimeMillis(),
        )
        saveAccounts(listOf(slot), id)
    }

    private fun mirrorActive(a: SavedAccount) {
        prefs.edit()
            .putString(KEY_ACCESS, a.tokens.accessToken)
            .putString(KEY_REFRESH, a.tokens.refreshToken)
            .putLong(KEY_ACCESS_EXP, a.tokens.accessExpiresAtMillis)
            .putLong(KEY_REFRESH_EXP, a.tokens.refreshExpiresAtMillis)
            .putLong(KEY_USER_ID, a.userId)
            .putString(KEY_USERNAME, a.username)
            .putString(KEY_FULL_NAME, a.fullName)
            .putString(KEY_EMAIL, a.email)
            .putBoolean(KEY_EMAIL_VERIFIED, false)
            .putString(KEY_AVATAR, a.avatarUrl)
            .putBoolean(KEY_PASSWORD_LOGIN, a.passwordLoginActive)
            .putStringSet(KEY_ROLES, a.access.roles.toSet())
            .putStringSet(KEY_PERMISSIONS, a.access.permissions)
            .apply()
    }

    private fun clearLegacy() {
        prefs.edit()
            .remove(KEY_ACCESS).remove(KEY_REFRESH)
            .remove(KEY_ACCESS_EXP).remove(KEY_REFRESH_EXP)
            .remove(KEY_USER_ID).remove(KEY_USERNAME).remove(KEY_FULL_NAME)
            .remove(KEY_EMAIL).remove(KEY_EMAIL_VERIFIED).remove(KEY_AVATAR)
            .remove(KEY_PASSWORD_LOGIN).remove(KEY_ROLES).remove(KEY_PERMISSIONS)
            .apply()
    }

    private fun parseAccounts(raw: String): List<SavedAccount> {
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    fromJson(o)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun SavedAccount.toJson(): JSONObject = JSONObject()
        .put("user_id", userId.toString())
        .put("username", username)
        .put("full_name", fullName)
        .put("avatar_url", avatarUrl)
        .put("email", email)
        .put("password_login_active", passwordLoginActive)
        .put("access_token", tokens.accessToken)
        .put("refresh_token", tokens.refreshToken)
        .put("access_expires_at", tokens.accessExpiresAtMillis)
        .put("refresh_expires_at", tokens.refreshExpiresAtMillis)
        .put("roles", JSONArray(access.roles))
        .put("permissions", JSONArray(access.permissions.toList()))
        .put("memberships", JSONArray(access.memberships.map { it.toJson() }))
        .put("last_used_at", lastUsedAt)

    private fun MembershipContext.toJson(): JSONObject = JSONObject()
        .put("membership_id", membershipId.toString())
        .put("parent_id", parentId.toString())
        .put("root_id", rootId.toString())
        .put("role_id", roleId)
        .put("role_slug", roleSlug)
        .put("role_title", roleTitle)
        .put("role_code", roleCode)
        .put("permissions", JSONArray(permissions.map { (slug, reach) -> JSONObject().put("slug", slug).put("reach", reach) }))

    private fun fromJson(o: JSONObject): SavedAccount? {
        val id = o.optString("user_id").toLongOrNull() ?: o.optLong("user_id", 0L)
        if (id == 0L) return null
        val accessTok = o.optString("access_token")
        val refreshTok = o.optString("refresh_token")
        if (accessTok.isBlank() || refreshTok.isBlank()) return null
        val roles = buildList {
            val a = o.optJSONArray("roles") ?: JSONArray()
            for (i in 0 until a.length()) add(a.optString(i))
        }
        val perms = buildSet {
            val a = o.optJSONArray("permissions") ?: JSONArray()
            for (i in 0 until a.length()) add(a.optString(i))
        }
        return SavedAccount(
            userId = id,
            username = o.optString("username"),
            fullName = o.optString("full_name"),
            avatarUrl = o.optString("avatar_url"),
            email = o.optString("email"),
            passwordLoginActive = o.optBoolean("password_login_active", false),
            tokens = Tokens(
                accessToken = accessTok,
                refreshToken = refreshTok,
                accessExpiresAtMillis = o.optLong("access_expires_at", 0L),
                refreshExpiresAtMillis = o.optLong("refresh_expires_at", 0L),
            ),
            access = Access(roles = roles, permissions = perms, memberships = o.objects("memberships").map(MembershipContext::from)),
            lastUsedAt = o.optLong("last_used_at", 0L),
        )
    }

    private companion object {
        const val PREFS = "xani.session"
        const val KEY_ACCOUNTS = "accounts_v1"
        const val KEY_ACTIVE_ID = "active_user_id"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ACCESS_EXP = "access_expires_at"
        const val KEY_REFRESH_EXP = "refresh_expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_FULL_NAME = "full_name"
        const val KEY_EMAIL = "email"
        const val KEY_EMAIL_VERIFIED = "email_verified"
        const val KEY_AVATAR = "avatar_url"
        const val KEY_PASSWORD_LOGIN = "password_login_active"
        const val KEY_ROLES = "roles"
        const val KEY_PERMISSIONS = "permissions"
    }
}
