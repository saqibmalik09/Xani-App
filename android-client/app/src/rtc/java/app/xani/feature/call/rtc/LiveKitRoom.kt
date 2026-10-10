package app.xani.feature.call.rtc

import android.content.Context
import android.view.View
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
import app.xani.core.lens.Lens
import app.xani.feature.call.RtcLink
import app.xani.feature.call.RtcRoom
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.participant.VideoTrackPublishDefaults
import io.livekit.android.room.participant.VideoTrackPublishOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.VideoCodec
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import livekit.org.webrtc.RendererCommon

/**
 * SFU room over our own LiveKit deployment (see infra/rtc). Identity in the
 * token is the user id, so participant identities map straight to users.
 * Group calls, live host/guests and low-latency viewer seats all use this;
 * viewers pass `publish=false` and never touch mic or camera.
 *
 * Publish quality is a device-class ladder, not one number: a 2 GB phone
 * encodes 360p@20 so it does not cook itself over an hour-long broadcast, a
 * flagship sends 720p with simulcast so the SFU can hand weak viewers the
 * small layer. The codec is VP8 everywhere — the one codec every phone here
 * can both encode and decode — and chips without hardware encoders take a
 * lower rung with a single layer (see [options]).
 *
 * Renderers are TextureViews: a SurfaceView punches a hole in the window,
 * which any Compose parent with alpha/translation (every slide-in screen we
 * have) closes again, leaving the "I only see their profile" black frame.
 */
internal class LiveKitRoom(
    app: Context,
    url: String,
    token: String,
    video: Boolean,
    private val publish: Boolean,
    private val listener: RtcRoom.Listener,
    hardwareH264: Boolean,
    /** A second room beside a live one: do not touch device audio routing. */
    secondary: Boolean = false,
) : RtcRoom {
    /**
     * Whether this session has ever published a camera — what [localView] is
     * gated on. It starts as the join flag and turns on when the camera does:
     * a co-host joins mic-only and may enable their own camera later, and
     * without the flip they would publish to the room while their own tile
     * stayed on their profile picture.
     */
    private var video: Boolean = video
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appCtx = app
    private val room: Room = LiveKit.create(
        app,
        options(DeviceClass.read(app), publish, hardwareH264),
        overrides = if (secondary) LiveKitOverrides() else LiveKitOverrides(audioOptions = audioOptions(app, publish)),
    )
    private val remoteTracks = HashMap<Long, VideoTrack>()
    private val views = HashMap<Long, TextureViewRenderer>()
    private var localView: TextureViewRenderer? = null
    private var front = true
    private var closed = false
    private var lensProc: LensRtcProcessor? = null

    /**
     * Local playback mute (viewer backgrounded the app). Volatile because the
     * flag is read on the event thread when a late track subscribes.
     */
    @Volatile private var playbackMuted = false

    init {
        scope.launch {
            room.events.collect { e ->
                runFeature(Unit) { onEvent(e) }
            }
        }
        scope.launch {
            listener.onLink(RtcLink.Connecting)
            val ok = runFeature(false) { room.connect(url, token); true }
            if (!ok) { listener.onLink(RtcLink.Failed); return@launch }
            if (publish) {
                enableMic()
                if (video) {
                    publishCamera()
                    bindLocalView()
                    listener.onLocalVideo()
                }
            }
            listener.onLink(RtcLink.Connected)
            publishParticipants()
            // Tracks that were already flowing before we subscribed to events.
            room.remoteParticipants.values.forEach { p ->
                val id = p.userId() ?: return@forEach
                val t = p.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack ?: return@forEach
                attach(id, t)
            }
        }
    }

    /**
     * Turn the mic on and make sure it actually published. The first
     * attempt can lose to a race — the preview's Camera2 session is still
     * closing, the audio device is mid-switch — and a swallowed failure here
     * is the "host is live but nobody sees them" report. Three tries with a
     * short back-off; the publication is the proof, not the call returning.
     */
    private suspend fun enableMic() {
        repeat(3) { attempt ->
            if (closed) return
            runFeature(Unit) { room.localParticipant.setMicrophoneEnabled(true) }
            if (room.localParticipant.getTrackPublication(Track.Source.MICROPHONE)?.track != null) return
            delay(350L * (attempt + 1))
        }
    }

    /**
     * Publish the camera with the lens processor built in — the same GPU
     * pipeline Create uses, so every viewer gets composited frames and no seat
     * ever runs a shader.
     *
     * Deliberately not setCameraEnabled() followed by restartTrack(processor):
     * a restart disposes the capturer and swaps the track's capturer, source
     * and rtc track, but leaves its CaptureDispatchObserver pointing at the
     * disposed one. Local renderers register on that observer, so the host
     * ends up wired to a dead capture path — black self-view while every
     * viewer sees them perfectly. Creating the track with the processor skips
     * the restart entirely, which also removes a camera close/reopen from the
     * go-live path.
     *
     * If a publication already exists (camera toggled back on) LiveKit unmutes
     * it in place and the processor stays installed.
     */
    private suspend fun publishCamera() {
        val proc = lensProc ?: LensRtcProcessor(appCtx, Lens.live).also { lensProc = it }
        val me = room.localParticipant
        repeat(3) { attempt ->
            if (closed) return
            if (me.getTrackPublication(Track.Source.CAMERA)?.track != null) {
                runFeature(Unit) { me.setCameraEnabled(true) }
                return
            }
            val track = runFeature(null) { me.createVideoTrack("", me.videoTrackCaptureDefaults, proc) }
            if (track != null) {
                val ok = runFeature(false) {
                    // Capture is not part of publishing. publishVideoTrack()
                    // only announces the track to the SFU — setCameraEnabled()
                    // pairs it with startCapture() itself, and a hand-built
                    // publish must do the same. Without it the room accepts a
                    // camera track that never produces a frame: every screen
                    // shows black, and the server ends the "live" at its first
                    // heartbeat for having no video. Started before the
                    // announce so the first subscriber has frames waiting.
                    track.startCapture()
                    // Source stated rather than inferred from the track: every
                    // later lookup is by source, and a publication filed under
                    // anything else is one we can never find again — the
                    // camera would be republished on each toggle.
                    val opts = VideoTrackPublishOptions(
                        null,
                        me.videoTrackPublishDefaults,
                        Track.Source.CAMERA,
                        null,
                    )
                    me.publishVideoTrack(track, opts)
                    true
                }
                if (!ok) {
                    // A publish that died after capture began leaves a running
                    // camera owned by nothing, and the retry's fresh capturer
                    // would evict it — the open/close fight that wedges this
                    // vendor's HAL. Fold the failed attempt before trying again.
                    runFeature(Unit) { track.stopCapture() }
                    runFeature(Unit) { track.dispose() }
                }
            }
            if (me.getTrackPublication(Track.Source.CAMERA)?.track != null) return
            delay(350L * (attempt + 1))
        }
    }

    /**
     * Feed the host's renderer from the lens processor. Called after every
     * publish and on every ask for the view, because the renderer outlives the
     * track and the two are created in whichever order the UI happens to run.
     */
    private fun bindLocalView() {
        val v = localView ?: return
        lensProc?.addMirror(v)
    }

    private fun onEvent(e: RoomEvent) {
        when (e) {
            is RoomEvent.Reconnecting -> listener.onLink(RtcLink.Disconnected)
            is RoomEvent.Reconnected -> listener.onLink(RtcLink.Connected)
            is RoomEvent.Disconnected -> if (!closed) listener.onLink(RtcLink.Closed)
            is RoomEvent.ParticipantConnected, is RoomEvent.ParticipantDisconnected -> publishParticipants()
            is RoomEvent.TrackSubscribed -> {
                // A track that arrives while playback is muted (viewer in the
                // background, host re-published) must land silent too.
                (e.track as? AudioTrack)?.let { a ->
                    if (playbackMuted) runFeature(Unit) { a.rtcTrack.setEnabled(false) }
                }
                val id = e.participant.userId() ?: return
                val t = e.track as? VideoTrack ?: return
                attach(id, t)
            }
            is RoomEvent.TrackUnsubscribed -> {
                val id = e.participant.userId() ?: return
                if (e.track is VideoTrack) {
                    remoteTracks.remove(id)?.let { t -> views[id]?.let { runFeature(Unit) { t.removeRenderer(it) } } }
                    listener.onRemoteVideo(id, false)
                }
            }
            // The SFU confirmed it is receiving our track: the camera is live
            // on the wire, which is the moment the host's self-view can exist.
            is RoomEvent.LocalTrackSubscribed -> {
                if (e.publication.source == Track.Source.CAMERA) {
                    bindLocalView()
                    listener.onLocalVideo()
                }
            }
            else -> Unit
        }
    }

    private fun attach(id: Long, t: VideoTrack) {
        if (remoteTracks[id] === t) return
        remoteTracks[id] = t
        views[id]?.let { runFeature(Unit) { t.addRenderer(it) } }
        listener.onRemoteVideo(id, true)
    }

    private fun publishParticipants() {
        listener.onParticipants(room.remoteParticipants.values.mapNotNull { it.userId() })
    }

    override fun setMuted(muted: Boolean) {
        if (!publish) return
        scope.launch { runFeature(Unit) { room.localParticipant.setMicrophoneEnabled(!muted) } }
    }

    override fun setPlaybackMuted(muted: Boolean) {
        playbackMuted = muted
        scope.launch {
            runFeature(Unit) {
                room.remoteParticipants.values.forEach { p ->
                    p.trackPublications.values.forEach { pub ->
                        (pub.track as? AudioTrack)?.rtcTrack?.setEnabled(!muted)
                    }
                }
            }
        }
    }

    override fun setVideoEnabled(enabled: Boolean) {
        if (!publish) return
        scope.launch {
            if (enabled) {
                video = true
                publishCamera()
                bindLocalView()
                listener.onLocalVideo()
            } else {
                runFeature(Unit) { room.localParticipant.setCameraEnabled(false) }
            }
        }
    }

    override fun switchCamera() {
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        front = !front
        runFeature(Unit) { track.switchCamera() }
        localView?.setMirror(front)
    }

    override fun localView(context: Context): View? {
        if (!publish || !video) return null
        // Gated on the processor, not on the camera publication. The publication
        // appears asynchronously, so asking for it here answered "no view yet"
        // on exactly the composition that wanted one, and the host sat on the
        // empty fallback until some later recomposition happened to re-ask. The
        // processor is set the moment the camera publish starts, which is also
        // the moment the bridge preview must hand the sensor over — before that
        // the caller should keep showing Camera2.
        if (lensProc == null) return null
        if (localView == null) {
            localView = TextureViewRenderer(context).also {
                room.initVideoRenderer(it)
                it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                it.setEnableHardwareScaler(true)
                it.setMirror(front)
            }
        }
        // Bind on every ask, not just on creation: a rejoin builds a new
        // processor and the cached renderer has to re-enter its fan-out.
        bindLocalView()
        return localView
    }

    override fun remoteView(context: Context, userId: Long): View? {
        return views[userId] ?: TextureViewRenderer(context).also {
            room.initVideoRenderer(it)
            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            it.setEnableHardwareScaler(true)
            remoteTracks[userId]?.let { t -> runFeature(Unit) { t.addRenderer(it) } }
            views[userId] = it
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        // Processor first: it drops the host's renderer from its fan-out, so no
        // frame can land on a renderer we are about to release.
        runFeature(Unit) { lensProc?.release() }
        lensProc = null
        runFeature(Unit) { localView?.release() }
        localView = null
        views.values.forEach { v -> runFeature(Unit) { v.release() } }
        views.clear()
        remoteTracks.clear()
        // Leaving tears down peer connections, the encoder and the EGL context:
        // hundreds of milliseconds on a low-end phone. A viewer whose host just
        // ended is on Main here, about to draw the dialog, so it goes off-thread.
        val r = room
        Thread({
            runFeature(Unit) { r.disconnect() }
            runFeature(Unit) { r.release() }
        }, "livekit-close").start()
    }

    private fun RemoteParticipant.userId(): Long? = identity?.value?.toLongOrNull()

    private companion object {
        /**
         * Where the sound comes out. The SDK's default is a phone call: audio
         * mode IN_COMMUNICATION with the earpiece ahead of the speaker, which
         * for a broadcast viewer is the host whispering from the top of the
         * phone — the "I cannot hear the live" report. A viewer seat plays
         * as media (speaker, media volume, no voice-call processing); a
         * publisher keeps call mode for echo cancellation but prefers the
         * speaker so a guest on stage is heard too.
         */
        fun audioOptions(app: Context, publish: Boolean): AudioOptions {
            if (!publish) return AudioOptions(audioOutputType = AudioType.MediaAudioType())
            val handler = AudioSwitchHandler(app).apply {
                preferredDeviceList = listOf(
                    AudioDevice.BluetoothHeadset::class.java,
                    AudioDevice.WiredHeadset::class.java,
                    AudioDevice.Speakerphone::class.java,
                    AudioDevice.Earpiece::class.java,
                )
            }
            return AudioOptions(audioOutputType = AudioType.CallAudioType(), audioHandler = handler)
        }

        /**
         * Capture + encode ladder. Bitrates are ceilings; the SFU's congestion
         * control and dynacast trim below them. Viewers never publish, so a
         * viewer seat gets the defaults and pays nothing here.
         */
        fun options(device: DeviceClass, publish: Boolean, hardwareH264: Boolean): RoomOptions {
            // Viewer seats: adaptiveStream is deliberately OFF. It pauses the
            // track whenever it thinks the renderer is invisible — and a
            // TextureView under Compose overlays (gift animations, chrome)
            // trips that heuristic, which shows up as "video frozen but the
            // timer keeps counting". A broadcast viewer renders exactly one
            // full-screen video, so there is nothing for adaptive stream to
            // save; skipping the visibility handshake also starts the first
            // frame sooner after join.
            //
            // Dynacast is OFF on both sides. It lets the SFU pause a layer no
            // one is watching and ask the publisher to resume it when a viewer
            // subscribes; that resume is a signalling round-trip the host can
            // miss (backgrounded, reconnecting, still opening the camera), and
            // then the viewer subscribes to a track that never sends a frame —
            // the "host is live, audio plays, picture is black" report. A
            // broadcast always has watchers; there is nothing to save.
            if (!publish) return RoomOptions(adaptiveStream = false, dynacast = false)
            // VP8 on the wire, always. Every Android build of libwebrtc can
            // encode it (libvpx, or the Qualcomm/Exynos/Intel hardware VP8
            // encoder where there is one) and every Android and web viewer can
            // decode it in software, so a stream that publishes is a stream
            // that shows. H.264 is not that: publisher and viewer must agree
            // on a profile-level the SFU is willing to forward, and a mismatch
            // is a black frame with no error on either phone. [hardwareH264]
            // stands in for "this chip has hardware encoders": with them the
            // ladder runs at the device's own tier and sends simulcast layers;
            // software-only chips step one rung down and send a single layer —
            // two libvpx encoders at 720p is the "phone burns" report.
            val tier = if (hardwareH264) device else when (device) {
                DeviceClass.High -> DeviceClass.Mid
                DeviceClass.Mid, DeviceClass.Low -> DeviceClass.Low
            }
            // Capture sizes are sensor sizes: landscape, matched against what
            // Camera2 actually lists. Asking for a portrait 720x1280 does not
            // rotate anything — libwebrtc picks the listed size closest to it,
            // which is a small 4:3 mode, and a 4:3 frame cropped to fill a 9:16
            // phone is the "2-5x zoomed" broadcast. Rotation is carried as frame
            // metadata, so 16:9 capture arrives upright and crops almost nothing.
            // Each long edge equals the lens pipeline's canvas for that tier, so
            // the GPU pass neither up- nor downsamples.
            //
            // adaptOutputToDimensions is off. Left on (the SDK default) it
            // wraps a crop-and-scale pass around every frame to force the
            // buffer to these exact numbers — a full-frame copy at 30 fps that
            // can only ever be a no-op here, because the lens processor
            // already emits the published geometry itself.
            val (capture, encoding) = when (tier) {
                DeviceClass.Low ->
                    VideoCaptureParameter(640, 360, 24, adaptOutputToDimensions = false) to
                        VideoEncoding(900_000, 24)
                DeviceClass.Mid ->
                    VideoCaptureParameter(960, 540, 28, adaptOutputToDimensions = false) to
                        VideoEncoding(1_800_000, 28)
                DeviceClass.High ->
                    VideoCaptureParameter(1280, 720, 30, adaptOutputToDimensions = false) to
                        VideoEncoding(2_800_000, 30)
            }
            return RoomOptions(
                // The host also watches co-hosts in small tiles under Compose
                // chrome — the same visibility heuristic that froze viewers.
                adaptiveStream = false,
                dynacast = false,
                videoTrackCaptureDefaults = LocalVideoTrackOptions(captureParams = capture),
                videoTrackPublishDefaults = VideoTrackPublishDefaults(
                    videoEncoding = encoding,
                    // Low devices send one layer: a second encoder is the heat we are avoiding.
                    simulcast = hardwareH264 && device != DeviceClass.Low,
                    videoCodec = VideoCodec.VP8.codecName,
                ),
                audioTrackPublishDefaults = AudioTrackPublishDefaults(audioBitrate = 32_000, dtx = true),
            )
        }
    }
}
