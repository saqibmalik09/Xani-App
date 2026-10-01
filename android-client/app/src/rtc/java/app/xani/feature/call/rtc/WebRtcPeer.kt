package app.xani.feature.call.rtc

import android.content.Context
import android.view.View
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
import app.xani.core.net.IceServerData
import app.xani.feature.call.RtcLink
import app.xani.feature.call.RtcPeer
import io.livekit.android.renderer.TextureViewRenderer
import org.json.JSONObject
import livekit.org.webrtc.Camera2Enumerator
import livekit.org.webrtc.CameraVideoCapturer
import livekit.org.webrtc.DataChannel
import livekit.org.webrtc.IceCandidate
import livekit.org.webrtc.MediaConstraints
import livekit.org.webrtc.MediaStream
import livekit.org.webrtc.PeerConnection
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.RtpParameters
import livekit.org.webrtc.RtpReceiver
import livekit.org.webrtc.RtpSender
import livekit.org.webrtc.RtpTransceiver
import livekit.org.webrtc.SdpObserver
import livekit.org.webrtc.SessionDescription
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.VideoSource
import livekit.org.webrtc.VideoTrack

/**
 * One libwebrtc PeerConnection: Unified Plan, bundled, RTCP-muxed, continual ICE
 * gathering so a network flip produces candidates without a full restart.
 * Opus is pinned to in-band FEC + DTX at 24 kbps so voice survives 10 kB/s links.
 */
internal class WebRtcPeer(
    private val app: Context,
    private val engine: WebRtcEngine,
    ice: List<IceServerData>,
    relayOnly: Boolean,
    video: Boolean,
    private val listener: RtcPeer.Listener,
) : RtcPeer {
    private val pc: PeerConnection
    private val audioSender: RtpSender
    private var videoSender: RtpSender? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var localTrack: VideoTrack? = null
    private var remoteTrack: VideoTrack? = null
    // TextureViews, not SurfaceViews: a SurfaceView hole-punches the window and
    // any Compose parent with alpha/translation covers the hole with black.
    private var localView: TextureViewRenderer? = null
    private var remoteView: TextureViewRenderer? = null
    private var front = true
    private var makingOffer = false
    private var closed = false
    private val capture = when (DeviceClass.read(app)) {
        DeviceClass.Low -> Capture(480, 360, 20, 350)
        DeviceClass.Mid -> Capture(640, 480, 24, 600)
        DeviceClass.High -> Capture(960, 540, 24, 900)
    }

    init {
        val servers = ice.map { s ->
            PeerConnection.IceServer.builder(s.urls).apply {
                if (s.username.isNotEmpty()) setUsername(s.username).setPassword(s.credential)
            }.createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = if (relayOnly) PeerConnection.IceTransportsType.RELAY else PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            iceCandidatePoolSize = 2
            keyType = PeerConnection.KeyType.ECDSA
        }
        pc = engine.factory.createPeerConnection(config, observer()) ?: error("peer connection")

        val audio = engine.factory.createAudioSource(
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            },
        )
        audioSender = pc.addTrack(engine.factory.createAudioTrack("a0", audio), listOf(STREAM))
        if (video) startVideo()
    }

    // ─── RtcPeer ─────────────────────────────────────────────────────────────

    override fun offer(iceRestart: Boolean) {
        if (closed || makingOffer) return
        makingOffer = true
        val constraints = MediaConstraints().apply {
            if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        pc.createOffer(
            sdp { sdp ->
                val tuned = SessionDescription(sdp.type, tuneOpus(sdp.description))
                pc.setLocalDescription(
                    done(
                        onOk = { makingOffer = false; listener.onLocalDescription("offer", tuned.description) },
                        onFail = { makingOffer = false },
                    ),
                    tuned,
                )
            },
            constraints,
        )
    }

    override fun setRemote(type: String, sdp: String) {
        if (closed) return
        val desc = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
        pc.setRemoteDescription(
            done(onOk = { if (desc.type == SessionDescription.Type.OFFER) answer() }, onFail = { }),
            desc,
        )
    }

    private fun answer() {
        pc.createAnswer(
            sdp { sdp ->
                val tuned = SessionDescription(sdp.type, tuneOpus(sdp.description))
                pc.setLocalDescription(done(onOk = { listener.onLocalDescription("answer", tuned.description) }, onFail = { }), tuned)
            },
            MediaConstraints(),
        )
    }

    override fun addCandidate(candidate: JSONObject) {
        if (closed) return
        val sdp = candidate.optString("candidate")
        if (sdp.isEmpty()) return
        pc.addIceCandidate(IceCandidate(candidate.optString("sdpMid"), candidate.optInt("sdpMLineIndex"), sdp))
    }

    override fun setMuted(muted: Boolean) {
        audioSender.track()?.setEnabled(!muted)
    }

    override fun setVideoEnabled(enabled: Boolean) {
        if (closed) return
        if (enabled && videoSender == null) {
            startVideo() // onRenegotiationNeeded fires → the controller-side offerer re-offers
            return
        }
        localTrack?.setEnabled(enabled)
        if (enabled) capturer?.startCapture(capture.width, capture.height, capture.fps) else runFeature(Unit) { capturer?.stopCapture() }
    }

    override fun switchCamera() {
        front = !front
        capturer?.switchCamera(null)
        localView?.setMirror(front)
    }

    override fun setMaxVideoKbps(kbps: Int) {
        val sender = videoSender ?: return
        val params = sender.parameters
        params.encodings.forEach { it.maxBitrateBps = kbps * 1000 }
        params.degradationPreference = RtpParameters.DegradationPreference.BALANCED
        sender.parameters = params
    }

    override fun localView(context: Context): View? {
        val track = localTrack ?: return null
        return localView ?: TextureViewRenderer(context).also {
            it.init(engine.egl.eglBaseContext, null)
            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            it.setMirror(front)
            it.setEnableHardwareScaler(true)
            track.addSink(it)
            localView = it
        }
    }

    override fun remoteView(context: Context): View? {
        return remoteView ?: TextureViewRenderer(context).also {
            it.init(engine.egl.eglBaseContext, null)
            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            it.setEnableHardwareScaler(true)
            remoteTrack?.addSink(it)
            remoteView = it
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runFeature(Unit) { capturer?.stopCapture() }
        runFeature(Unit) { capturer?.dispose() }
        runFeature(Unit) { helper?.dispose() }
        runFeature(Unit) { localView?.release() }
        runFeature(Unit) { remoteView?.release() }
        runFeature(Unit) { pc.close() }
        runFeature(Unit) { pc.dispose() }
        capturer = null; helper = null; localView = null; remoteView = null; remoteTrack = null
    }

    // ─── capture ─────────────────────────────────────────────────────────────

    private fun startVideo() {
        val enumerator = Camera2Enumerator(app)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull() ?: return
        front = enumerator.isFrontFacing(name)
        val cap = enumerator.createCapturer(name, null) ?: return
        val src = engine.factory.createVideoSource(false)
        val h = SurfaceTextureHelper.create("xani-cap", engine.egl.eglBaseContext)
        cap.initialize(h, app, src.capturerObserver)
        cap.startCapture(capture.width, capture.height, capture.fps)
        val track = engine.factory.createVideoTrack("v0", src)
        capturer = cap; helper = h; videoSource = src; localTrack = track
        videoSender = pc.addTrack(track, listOf(STREAM)).also { s ->
            val p = s.parameters
            p.encodings.forEach { it.maxBitrateBps = capture.startKbps * 1000 }
            p.degradationPreference = RtpParameters.DegradationPreference.BALANCED
            s.parameters = p
        }
        localView?.let { track.addSink(it) }
    }

    /** Opus: in-band FEC, DTX and a 24 kbps ceiling; nothing else in the SDP is touched. */
    private fun tuneOpus(sdp: String): String {
        val lines = sdp.split("\r\n")
        val pt = lines.firstOrNull { it.startsWith("a=rtpmap:") && it.contains("opus/48000", ignoreCase = true) }
            ?.removePrefix("a=rtpmap:")?.substringBefore(' ') ?: return sdp
        val extra = "minptime=10;useinbandfec=1;usedtx=1;maxaveragebitrate=24000;stereo=0;sprop-stereo=0"
        var seen = false
        val out = lines.map { l ->
            if (l.startsWith("a=fmtp:$pt ")) {
                seen = true
                val have = l.removePrefix("a=fmtp:$pt ").split(';').map { it.substringBefore('=') }.toSet()
                val add = extra.split(';').filter { it.substringBefore('=') !in have }
                if (add.isEmpty()) l else "$l;${add.joinToString(";")}"
            } else l
        }.toMutableList()
        if (!seen) {
            val i = out.indexOfFirst { it.startsWith("a=rtpmap:$pt ") }
            if (i >= 0) out.add(i + 1, "a=fmtp:$pt $extra")
        }
        return out.joinToString("\r\n")
    }

    // ─── observers ───────────────────────────────────────────────────────────

    // A function, not a property: `init` runs before later property initializers.
    private fun observer() = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            listener.onIceCandidate(JSONObject().put("candidate", c.sdp).put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex))
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            listener.onLink(
                when (state) {
                    PeerConnection.PeerConnectionState.NEW -> RtcLink.New
                    PeerConnection.PeerConnectionState.CONNECTING -> RtcLink.Connecting
                    PeerConnection.PeerConnectionState.CONNECTED -> RtcLink.Connected
                    PeerConnection.PeerConnectionState.DISCONNECTED -> RtcLink.Disconnected
                    PeerConnection.PeerConnectionState.FAILED -> RtcLink.Failed
                    PeerConnection.PeerConnectionState.CLOSED -> RtcLink.Closed
                },
            )
        }

        // Unified Plan fires onTrack; older observers only onAddTrack. Either is
        // the moment the remote camera exists, so both funnel into one attach.
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            attachRemote(receiver.track() as? VideoTrack ?: return)
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            attachRemote(transceiver.receiver?.track() as? VideoTrack ?: return)
        }

        override fun onRemoveTrack(receiver: RtpReceiver) {
            if (receiver.track()?.id() == remoteTrack?.id()) {
                remoteTrack = null
                listener.onRemoteVideo(false)
            }
        }

        // The offerer re-offers when a camera track is added mid-call; the answerer waits.
        override fun onRenegotiationNeeded() = Unit
        override fun onSignalingChange(s: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(b: Boolean) = Unit
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) = Unit
        override fun onAddStream(s: MediaStream) = Unit
        override fun onRemoveStream(s: MediaStream) = Unit
        override fun onDataChannel(d: DataChannel) = Unit
    }

    private fun attachRemote(t: VideoTrack) {
        if (closed || remoteTrack?.id() == t.id()) return
        remoteTrack = t
        remoteView?.let { runFeature(Unit) { t.addSink(it) } }
        listener.onRemoteVideo(true)
    }

    private fun sdp(ok: (SessionDescription) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) = ok(d)
        override fun onCreateFailure(e: String) { makingOffer = false }
        override fun onSetSuccess() = Unit
        override fun onSetFailure(e: String) = Unit
    }

    private fun done(onOk: () -> Unit, onFail: () -> Unit) = object : SdpObserver {
        override fun onSetSuccess() = onOk()
        override fun onSetFailure(e: String) = onFail()
        override fun onCreateSuccess(d: SessionDescription) = Unit
        override fun onCreateFailure(e: String) = Unit
    }

    /** Capture size, frame rate and the opening send ceiling for one device class. */
    private class Capture(val width: Int, val height: Int, val fps: Int, val startKbps: Int)

    private companion object {
        const val STREAM = "xani"
    }
}
