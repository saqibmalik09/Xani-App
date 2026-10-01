package app.xani.feature.call

import android.content.Context
import android.view.View
import app.xani.core.net.IceServerData
import org.json.JSONObject

/**
 * Media engine contract. Everything above this line is signalling and UI state;
 * everything below it is libwebrtc (1:1) and the SFU client (rooms). The
 * implementation lives in a build-flag-gated source set so the app compiles and
 * ships with calling reported as unavailable when the native media stack is not
 * bundled, instead of crashing at first call.
 *
 * All callbacks may arrive on engine threads; [CallController] hops to Main.
 */
enum class RtcLink { New, Connecting, Connected, Disconnected, Failed, Closed }

/** One peer-to-peer media session. Created ringing, closed on end. */
interface RtcPeer {
    interface Listener {
        /** A local SDP is ready to be relayed: `offer` or `answer`. */
        fun onLocalDescription(type: String, sdp: String)

        /** A local ICE candidate; forwarded verbatim as the signal payload. */
        fun onIceCandidate(candidate: JSONObject)

        /** Transport state as seen by ICE + DTLS. */
        fun onLink(link: RtcLink)

        /** The remote side is (or stopped) sending video frames. */
        fun onRemoteVideo(present: Boolean)
    }

    /** Create and emit a local offer. `iceRestart` forces new candidates on the same session. */
    fun offer(iceRestart: Boolean)

    /** Apply a remote SDP; an `offer` triggers a local answer via [Listener.onLocalDescription]. */
    fun setRemote(type: String, sdp: String)

    fun addCandidate(candidate: JSONObject)

    fun setMuted(muted: Boolean)

    /** Turn the camera track on or off; turning it on for the first time renegotiates. */
    fun setVideoEnabled(enabled: Boolean)

    fun switchCamera()

    /** Cap on outbound video, driven by the controller's ladder. */
    fun setMaxVideoKbps(kbps: Int)

    /** Platform views that paint the local preview and remote stream; null when audio-only. */
    fun localView(context: Context): View?
    fun remoteView(context: Context): View?

    fun close()
}

/** One SFU room session: group calls, live host, live guest or low-latency viewer. */
interface RtcRoom {
    interface Listener {
        fun onLink(link: RtcLink)

        /** Remote participant identities currently publishing. */
        fun onParticipants(userIds: List<Long>)

        fun onRemoteVideo(userId: Long, present: Boolean)
    }

    fun setMuted(muted: Boolean)
    fun setVideoEnabled(enabled: Boolean)
    fun switchCamera()
    fun localView(context: Context): View?
    fun remoteView(context: Context, userId: Long): View?
    fun close()
}

interface RtcEngine {
    fun createPeer(ice: List<IceServerData>, relayOnly: Boolean, video: Boolean, listener: RtcPeer.Listener): RtcPeer

    /**
     * Join an SFU room with a server-minted token. `publish` false is a viewer
     * seat: no capture, no mic permission needed.
     */
    fun joinRoom(url: String, token: String, video: Boolean, publish: Boolean, listener: RtcRoom.Listener): RtcRoom

    fun release()
}
