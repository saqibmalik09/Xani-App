package app.xani.feature.call

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class CallPhase {
    /** Dialling: the invite is out, nobody has picked up. */
    Outgoing,

    /** Ringing on this device. */
    Incoming,

    /** Accepted on both ends; ICE/DTLS still settling. */
    Connecting,
    Connected,

    /** Media path dropped; ICE restarts running inside the server grace window. */
    Reconnecting,

    /** Terminal; the surface lingers briefly to show why, then clears. */
    Ended,
}

/** Why a call finished, in UI terms. */
enum class CallOutcome { None, Ended, Busy, NoAnswer, Declined, Missed, Lost, Failed, Unavailable }

@Immutable
data class CallPeer(
    val id: Long,
    val name: String,
    val initials: String,
    val tint: Color,
    val avatarUrl: String = "",
)

@Immutable
data class CallUi(
    val callId: Long,
    val conversationId: Long,
    /** Inbox row that opened the call; null when it rang in from elsewhere. */
    val chatId: Int?,
    val outgoing: Boolean,
    val video: Boolean,
    val phase: CallPhase,
    val peer: CallPeer,
    /** p2p | sfu */
    val mode: String = "p2p",
    val muted: Boolean = false,
    val cameraOff: Boolean = false,
    val speaker: Boolean = false,
    val frontCamera: Boolean = true,
    val remoteVideo: Boolean = false,
    /** Bumped whenever the media session behind the renderers is replaced; a remember key. */
    val mediaRev: Int = 0,
    val remoteMuted: Boolean = false,
    /** Callee device reported ringing, so the caller hears ringback. */
    val ringing: Boolean = false,
    /** `SystemClock.elapsedRealtime()` when media first connected; 0 before. */
    val connectedAt: Long = 0L,
    val participants: List<Long> = emptyList(),
    val outcome: CallOutcome = CallOutcome.None,
    val minimized: Boolean = false,
) {
    val active: Boolean get() = phase != CallPhase.Ended
    val inMedia: Boolean get() = phase == CallPhase.Connected || phase == CallPhase.Reconnecting
}
