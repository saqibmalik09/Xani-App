package app.xani.core.net

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Permission gates for UI, read from the live session rather than a snapshot.
 *
 * The session is a StateFlow, and a moderator's change lands on the realtime
 * stream as an `access` event which re-reads /v1/auth/me — so a surface gated
 * with [can] appears or disappears while the person is holding the phone. No
 * restart, no sign-out, no pull-to-refresh.
 *
 * Hiding is courtesy only: every one of these permissions is also enforced on
 * the server, so an older build that still draws the button gets a 403.
 */
@Composable
fun rememberAccess(): Access {
    val state by Xani.session.state.collectAsState()
    return remember(state) { (state as? AuthState.SignedIn)?.access ?: Access.None }
}

/** True while the signed-in account holds [permission] platform-wide. */
@Composable
fun can(permission: String): Boolean = rememberAccess().can(permission)

/** Permission slugs the client gates a surface on. Server-side is the real gate. */
object Perm {
    /** The Home tab in the bottom bar. */
    const val HomeView = "home.view"

    /** The Live lane in Home and watching any broadcast. */
    const val LiveView = "live.view"

    /** The Go Live option in Create. */
    const val GoLive = "live.go_live"

    /** The Create (+) tab in the bottom bar. */
    const val CreateUse = "create.use"

    /** The Post lane inside Create (camera / upload). */
    const val PostCreate = "post.create"

    /** The Inbox tab in the bottom bar. */
    const val InboxView = "inbox.view"

    /** The chat module: Chats lane, direct messages, the Message button. */
    const val Chat = "chat.use"

    /** Screenshots and screen recording (otherwise the window stays secure). */
    const val ScreenCapture = "screen.capture"

    /** Opens the in-app moderation screen. */
    const val ModerateUsers = "mod.app_users"

    /** May allow or revoke screen capture for other accounts. */
    const val ManageCapture = "screen.capture.manage"

    /** May use the microphone in lives and calls. */
    const val Mic = "voice.mic"
}
