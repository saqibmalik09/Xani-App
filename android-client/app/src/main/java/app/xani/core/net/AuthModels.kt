package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * A country carries all three identifiers at once — numeric id, ISO alpha-2 and
 * calling code — so a picker can show "Pakistan +92" and a signup can post an
 * unambiguous value without a second lookup.
 */
@Immutable
data class Country(
    val id: Int,
    val iso2: String,
    val iso3: String,
    val name: String,
    val phoneCode: Int,
) {
    val dialCode: String get() = "+$phoneCode"

    companion object {
        fun from(json: JSONObject) = Country(
            id = json.int("id"),
            iso2 = json.str("iso2"),
            iso3 = json.str("iso3"),
            name = json.str("name"),
            phoneCode = json.int("phone_code"),
        )
    }
}

@Immutable
data class AccountUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val emailVerified: Boolean,
    val avatarUrl: String,
    val location: String,
    val bio: String,
    val birthday: String,
    val country: Country?,
    val passwordLoginActive: Boolean = false,
) {
    companion object {
        val Empty = AccountUser(0, "", "", "", "", false, "", "", "", "", null, false)

        fun from(json: JSONObject) = AccountUser(
            id = json.id("id"),
            username = json.str("username"),
            fullName = json.str("full_name"),
            email = json.str("email"),
            phone = json.str("phone"),
            emailVerified = json.bool("email_verified"),
            avatarUrl = json.str("avatar_url"),
            location = json.str("location"),
            bio = json.str("bio"),
            birthday = json.str("birthday"),
            country = json.obj("country")?.let(Country::from),
            passwordLoginActive = json.bool("password_login_active"),
        )
    }
}

/**
 * Permissions that apply only inside one target — one live room, one agency, one
 * country. This is how a single account can be an ordinary user everywhere and an
 * agency admin inside agency 42.
 */
@Immutable
data class ScopedPermissions(
    val scopeType: Int,
    val scopeId: Long,
    val permissions: Set<String>,
) {
    companion object {
        fun from(json: JSONObject) = ScopedPermissions(
            scopeType = json.int("scope_type"),
            scopeId = json.id("scope_id"),
            permissions = json.strings("permissions").toSet(),
        )
    }
}

/** Scope ids agreed with the server (`store.Scope*`). */
object Scope {
    const val GLOBAL = 1
    const val LIVE_ROOM = 2
    const val AGENCY = 3
    const val COUNTRY = 4
}

/**
 * What the signed-in account may do.
 *
 * Roles are labels for display ("Host", "Agency Admin"); every gate in the UI
 * checks a [permissions] entry instead, so adding a role on the server never needs
 * a client release.
 */
/**
 * One role the account holds inside the organisation tree, with the permissions
 * that role grants and how far down the tree each one reaches (1 self, 2 direct
 * downline, 3 whole downline, 4 whole platform).
 */
@Immutable
data class MembershipContext(
    val membershipId: Long,
    val parentId: Long,
    val rootId: Long,
    val roleId: Int,
    val roleSlug: String,
    val roleTitle: String,
    val roleCode: String,
    val permissions: Map<String, Int>,
) {
    /** True when this membership grants [permission] at any reach. */
    fun holds(permission: String): Boolean = permissions.containsKey(permission)

    companion object {
        fun from(json: JSONObject): MembershipContext {
            val perms = HashMap<String, Int>()
            for (p in json.objects("permissions")) {
                val slug = p.str("slug")
                if (slug.isNotEmpty()) perms[slug] = p.int("reach")
            }
            return MembershipContext(
                membershipId = json.id("membership_id"),
                parentId = json.id("parent_id"),
                rootId = json.id("root_id"),
                roleId = json.int("role_id"),
                roleSlug = json.str("role_slug"),
                roleTitle = json.str("role_title"),
                roleCode = json.str("role_code"),
                permissions = perms,
            )
        }
    }
}

@Immutable
data class Access(
    val roles: List<String> = emptyList(),
    val permissions: Set<String> = emptySet(),
    val scoped: List<ScopedPermissions> = emptyList(),
    val memberships: List<MembershipContext> = emptyList(),
) {
    /** Platform-wide check. */
    fun can(permission: String): Boolean = permission in permissions

    /**
     * Held anywhere: platform-wide, or through any membership at any reach. Use
     * this to decide whether to *show* an organisation surface (approvals inbox,
     * downline); the server decides per row what the user may actually touch.
     */
    fun holdsAnywhere(permission: String): Boolean =
        permission in permissions || memberships.any { it.holds(permission) }

    /** True when the account sits somewhere in the organisation tree. */
    val hasMembership: Boolean get() = memberships.isNotEmpty()

    /**
     * Scoped check. A permission held globally wins everywhere, which is what makes
     * a platform moderator able to act inside a room they were never assigned to.
     */
    fun can(permission: String, scopeType: Int, scopeId: Long): Boolean =
        permission in permissions ||
            scoped.any {
                it.scopeType == scopeType && it.scopeId == scopeId && permission in it.permissions
            }

    fun hasRole(role: String): Boolean = role in roles

    companion object {
        val None = Access()

        fun from(json: JSONObject) = Access(
            roles = json.strings("roles"),
            permissions = json.strings("permissions").toSet(),
            scoped = json.objects("scoped_permissions").map(ScopedPermissions::from),
            memberships = json.objects("memberships").map(MembershipContext::from),
        )
    }
}

/**
 * Tokens as the server issued them. [accessExpiresAtMillis] is computed from the
 * server's *relative* `expires_in` rather than its absolute timestamp, because phone
 * clocks are wrong often enough that trusting wall time logs people out at random.
 */
@Immutable
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtMillis: Long,
    val refreshExpiresAtMillis: Long,
) {
    fun isAccessExpired(nowMillis: Long, skewMillis: Long = 30_000L): Boolean =
        nowMillis >= accessExpiresAtMillis - skewMillis

    fun isRefreshExpired(nowMillis: Long): Boolean = nowMillis >= refreshExpiresAtMillis

    companion object {
        val Empty = Tokens("", "", 0L, 0L)

        fun from(json: JSONObject, nowMillis: Long): Tokens {
            val accessIn = json.long("access_expires_in", 600L)
            val refreshIn = json.long("refresh_expires_in", 72 * 3600L)
            return Tokens(
                accessToken = json.str("access_token"),
                refreshToken = json.str("refresh_token"),
                accessExpiresAtMillis = nowMillis + accessIn * 1000L,
                refreshExpiresAtMillis = nowMillis + refreshIn * 1000L,
            )
        }
    }
}

/** One response that carries identity, authority and credentials. */
@Immutable
data class Session(
    val user: AccountUser,
    val access: Access,
    val tokens: Tokens,
    val created: Boolean,
) {
    companion object {
        fun from(json: JSONObject, nowMillis: Long) = Session(
            user = json.obj("user")?.let(AccountUser::from) ?: AccountUser.Empty,
            access = Access.from(json),
            tokens = json.obj("tokens")?.let { Tokens.from(it, nowMillis) } ?: Tokens.Empty,
            created = json.bool("created"),
        )
    }
}

/** Server bootstrap: the app name and which sign-in methods are live. */
@Immutable
data class AppConfig(
    val appName: String,
    val signupEnabled: Boolean,
    val googleEnabled: Boolean,
    val sessionHours: Int,
    val codeResendSeconds: Int,
    /** Public prefix for relative media keys. Never stored in rows. */
    val mediaBaseUrl: String = "",
    /** The site a shared link points at, e.g. https://example.com. */
    val webBaseUrl: String = "",
    val maxVideoSeconds: Int = 600,
    val maxVideoMb: Int = 2048,
    val maxImageMb: Int = 25,
    val maxCaption: Int = 2200,
    /** Relative media key for the product favicon (Settings → Branding). */
    val faviconKey: String = "",
    /**
     * Public Firebase web SDK config from Settings → Firebase. Apps use it to
     * initialise Messaging without shipping `google-services.json`.
     */
    val firebaseWeb: FirebaseWebConfig? = null,
) {
    companion object {
        fun from(json: JSONObject) = AppConfig(
            appName = json.str("app_name"),
            signupEnabled = json.bool("signup_enabled", true),
            googleEnabled = json.bool("google_enabled"),
            sessionHours = json.int("session_hours", 72),
            codeResendSeconds = json.int("code_resend_seconds", 60),
            mediaBaseUrl = json.str("media_base_url").trimEnd('/'),
            webBaseUrl = json.str("web_base_url").trimEnd('/'),
            maxVideoSeconds = json.int("media_max_video_seconds", 600),
            maxVideoMb = json.int("media_max_video_mb", 2048),
            maxImageMb = json.int("media_max_image_mb", 25),
            maxCaption = json.int("media_max_caption", 2200),
            faviconKey = json.str("favicon_key"),
            firebaseWeb = json.obj("firebase_web")?.let(FirebaseWebConfig::from),
        )
    }
}

/**
 * Subset of the Firebase console "Web app" config needed to start FCM on a
 * phone that does not embed `google-services.json`.
 */
@Immutable
data class FirebaseWebConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val messagingSenderId: String,
    val storageBucket: String = "",
    /** Optional Android mobilesdk app id when different from [appId]. */
    val androidAppId: String = "",
) {
    companion object {
        fun from(json: JSONObject): FirebaseWebConfig? {
            val apiKey = json.str("apiKey").ifBlank { json.str("api_key") }
            val projectId = json.str("projectId").ifBlank { json.str("project_id") }
            val appId = json.str("appId").ifBlank { json.str("app_id") }
            if (apiKey.isBlank() || projectId.isBlank() || appId.isBlank()) return null
            val sender = json.str("messagingSenderId")
                .ifBlank { json.str("messaging_sender_id") }
                .ifBlank { json.str("gcmSenderId") }
                .ifBlank { json.str("gcm_sender_id") }
                .ifBlank { senderFromAppId(appId) }
            val androidId = json.str("androidAppId")
                .ifBlank { json.str("android_app_id") }
            return FirebaseWebConfig(
                apiKey = apiKey,
                appId = appId,
                projectId = projectId,
                messagingSenderId = sender,
                storageBucket = json.str("storageBucket").ifBlank { json.str("storage_bucket") },
                androidAppId = androidId,
            )
        }

        /** `1:123456789012:web:…` → `123456789012`. */
        private fun senderFromAppId(appId: String): String {
            val parts = appId.split(':')
            return if (parts.size >= 2) parts[1] else ""
        }
    }
}

/** Answer to "which account is this?" — a masked hint, never a yes/no. */
@Immutable
data class AccountHint(
    val found: Boolean,
    val maskedTo: String,
    val channel: String,
    val hasSocial: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = AccountHint(
            found = json.bool("found"),
            maskedTo = json.str("masked_to"),
            channel = json.str("channel"),
            hasSocial = json.bool("has_social"),
        )
    }
}

/** Result of asking for a verification or reset code. */
@Immutable
data class CodeIssued(
    val sent: Boolean,
    val maskedTo: String,
    val retryAfterSeconds: Int,
    val debugCode: String,
) {
    companion object {
        fun from(json: JSONObject) = CodeIssued(
            sent = json.bool("sent"),
            maskedTo = json.str("masked_to"),
            retryAfterSeconds = json.int("retry_after_seconds", 60),
            debugCode = json.str("debug_code"),
        )
    }
}
