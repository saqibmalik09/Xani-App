package app.xani.feature.notify

import android.content.Intent
import app.xani.feature.profile.ProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screens a notification may open. Pushes carry a destination + ids, never a link. */
sealed interface PushRoute {
    data class Conversation(val convId: Long) : PushRoute
    data class Live(val roomId: Long) : PushRoute
    data class Post(val postId: Long, val commentId: Long) : PushRoute
    data class Profile(val userId: Long) : PushRoute
    /** Share link `@username` / `u/username` — resolve to id after sign-in. */
    data class ProfileUsername(val username: String) : PushRoute
    data class SupportTicket(val ticketId: Long) : PushRoute
    data class RoleRequest(val requestId: Long) : PushRoute
    /** Referral link: open join form with code and optional target role. */
    data class Join(val code: String, val roleSlug: String = "") : PushRoute
    data object Notices : PushRoute
    data object Home : PushRoute
}

/**
 * One place that turns a notification (FCM data in the foreground, or the
 * extras of a tapped tray entry from a killed/background state) into a typed
 * route. The shell consumes [pending] once it is composed, so a tap that
 * cold-starts the app waits through splash and sign-in restore instead of
 * being dropped.
 */
object PushRouter {
    const val EXTRA_DEST = "dest"
    const val EXTRA_UID = "uid"

    private val _pending = MutableStateFlow<PushRoute?>(null)
    val pending: StateFlow<PushRoute?> = _pending.asStateFlow()

    /** Live room the Home tab should open when its directory lists it. */
    val liveRequest = MutableStateFlow<Long?>(null)

    fun parse(get: (String) -> String?): PushRoute? {
        fun id(key: String): Long? = get(key)?.toLongOrNull()?.takeIf { it > 0L }
        return when (get(EXTRA_DEST)) {
            "conversation" -> id("conv_id")?.let { PushRoute.Conversation(it) }
            "live" -> id("room_id")?.let { PushRoute.Live(it) }
            "post" -> id("post_id")?.let { PushRoute.Post(it, id("comment_id") ?: 0L) }
            "profile" -> id("user_id")?.let { PushRoute.Profile(it) }
            "support_ticket" -> id("ticket_id")?.let { PushRoute.SupportTicket(it) }
            "role_request" -> PushRoute.RoleRequest(id("request_id") ?: 0L)
            "notices" -> PushRoute.Notices
            "home" -> PushRoute.Home
            else -> null
        }
    }

    /**
     * True when the note was addressed to the signed-in account (or carries no
     * owner). A tray entry left over from a previous account opens nothing.
     */
    fun forCurrentAccount(get: (String) -> String?): Boolean {
        val uid = get(EXTRA_UID)?.toLongOrNull() ?: return true
        val me = ProfileStore.currentUserId
        return me == 0L || uid == me
    }

    /**
     * A link the app owns becomes a typed route:
     * - https://host/live/{roomId} or scheme://live/{roomId}
     * - https://host/join/CODE[/ROLE] or scheme://join/CODE[/ROLE]
     * - https://host/@user, /u/user, or scheme://u/user / @user
     */
    fun parseLink(uri: android.net.Uri): PushRoute? {
        val parts = uri.pathSegments.orEmpty()
        val host = uri.host.orEmpty()
        // Live share: /live/{id} or scheme://live/{id} (host == "live").
        val liveId = when {
            parts.size >= 2 && parts[0].equals("live", ignoreCase = true) -> parts[1]
            parts.size == 1 && host.equals("live", ignoreCase = true) -> parts[0]
            else -> null
        }?.toLongOrNull()?.takeIf { it > 0L }
        if (liveId != null) return PushRoute.Live(liveId)
        // Profile: /@user, /u/user, host=@user (scheme://@user), scheme://u/user
        val profileUser = when {
            parts.size >= 2 && parts[0].equals("u", ignoreCase = true) -> parts[1]
            parts.size == 1 && parts[0].startsWith("@") -> parts[0].removePrefix("@")
            parts.size == 1 && host.equals("u", ignoreCase = true) -> parts[0]
            parts.isEmpty() && host.startsWith("@") -> host.removePrefix("@")
            parts.size == 1 && !parts[0].equals("join", ignoreCase = true) &&
                !parts[0].equals("live", ignoreCase = true) &&
                host.isNotEmpty() && !host.contains('.') && parts[0].matches(Regex("^[a-zA-Z0-9._]{3,30}$")) &&
                uri.scheme?.contains("http") != true -> parts[0] // scheme://username
            else -> null
        }?.trim()?.removePrefix("@")?.lowercase()
        if (!profileUser.isNullOrEmpty() && profileUser.matches(Regex("^[a-zA-Z0-9._]{3,30}$"))) {
            return PushRoute.ProfileUsername(profileUser)
        }
        val (codeRaw, roleRaw) = when {
            parts.size >= 2 && parts[0] == "join" -> parts[1] to parts.getOrNull(2).orEmpty()
            host == "join" && parts.isNotEmpty() -> parts[0] to parts.getOrNull(1).orEmpty()
            else -> uri.getQueryParameter("code").orEmpty() to uri.getQueryParameter("role").orEmpty()
        }
        val code = codeRaw.trim().uppercase()
        if (code.length !in 4..32 || !code.all { it.isLetterOrDigit() || it == '-' }) return null
        val role = roleRaw.trim().lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        return PushRoute.Join(code, role)
    }

    /** Called from the activity for launch and re-launch intents. */
    fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                val route = parseLink(uri)
                intent.data = null // consume: re-creates must not replay it
                if (route != null) _pending.value = route
                return
            }
        }
        val extras = intent?.extras ?: return
        if (!extras.containsKey(EXTRA_DEST)) return
        val get: (String) -> String? = { k -> extras.get(k)?.toString() }
        val route = if (forCurrentAccount(get)) parse(get) else PushRoute.Home
        // A rotation or process re-create replays the same intent: consume it.
        intent.removeExtra(EXTRA_DEST)
        if (route != null) _pending.value = route
    }

    /** The shell takes the route exactly once. */
    fun consume(): PushRoute? {
        val r = _pending.value ?: return null
        _pending.value = null
        return r
    }
}
