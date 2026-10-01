package app.xani.feature.call.rtc

import android.content.Context
import android.view.View
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
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
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.participant.VideoTrackPublishDefaults
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
    private val video: Boolean,
    private val publish: Boolean,
    private val listener: RtcRoom.Listener,
    hardwareH264: Boolean,
) : RtcRoom {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val room: Room = LiveKit.create(
        app,
        options(DeviceClass.read(app), publish, hardwareH264),
        overrides = LiveKitOverrides(audioOptions = audioOptions(app, publish)),
    )
    private val remoteTracks = HashMap<Long, VideoTrack>()
    private val views = HashMap<Long, TextureViewRenderer>()
    private var localView: TextureViewRenderer? = null
    private var front = true
    private var closed = false

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
                enableLocal(Track.Source.MICROPHONE)
                if (video) enableLocal(Track.Source.CAMERA)
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
     * Turn a local source on and make sure it actually published. The first
     * attempt can lose to a race — the preview's Camera2 session is still
     * closing, the audio device is mid-switch — and a swallowed failure here
     * is the "host is live but nobody sees them" report. Three tries with a
     * short back-off; the publication is the proof, not the call returning.
     */
    private suspend fun enableLocal(source: Track.Source) {
        repeat(3) { attempt ->
            if (closed) return
            runFeature(Unit) {
                if (source == Track.Source.CAMERA) room.localParticipant.setCameraEnabled(true)
                else room.localParticipant.setMicrophoneEnabled(true)
            }
            if (room.localParticipant.getTrackPublication(source)?.track != null) return
            delay(350L * (attempt + 1))
        }
    }

    private fun onEvent(e: RoomEvent) {
        when (e) {
            is RoomEvent.Reconnecting -> listener.onLink(RtcLink.Disconnected)
            is RoomEvent.Reconnected -> listener.onLink(RtcLink.Connected)
            is RoomEvent.Disconnected -> if (!closed) listener.onLink(RtcLink.Closed)
            is RoomEvent.ParticipantConnected, is RoomEvent.ParticipantDisconnected -> publishParticipants()
            is RoomEvent.TrackSubscribed -> {
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

    override fun setVideoEnabled(enabled: Boolean) {
        if (!publish) return
        scope.launch { runFeature(Unit) { room.localParticipant.setCameraEnabled(enabled) } }
    }

    override fun switchCamera() {
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        front = !front
        runFeature(Unit) { track.switchCamera() }
        localView?.setMirror(front)
    }

    override fun localView(context: Context): View? {
        if (!publish) return null
        val track = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return null
        return localView ?: TextureViewRenderer(context).also {
            room.initVideoRenderer(it)
            it.setMirror(front)
            runFeature(Unit) { track.addRenderer(it) }
            localView = it
        }
    }

    override fun remoteView(context: Context, userId: Long): View? {
        return views[userId] ?: TextureViewRenderer(context).also {
            room.initVideoRenderer(it)
            remoteTracks[userId]?.let { t -> runFeature(Unit) { t.addRenderer(it) } }
            views[userId] = it
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        runFeature(Unit) { localView?.release() }
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
            val (capture, encoding) = when (tier) {
                DeviceClass.Low -> VideoCaptureParameter(640, 360, 20) to VideoEncoding(600_000, 20)
                DeviceClass.Mid -> VideoCaptureParameter(960, 540, 24) to VideoEncoding(1_200_000, 24)
                DeviceClass.High -> VideoCaptureParameter(1280, 720, 24) to VideoEncoding(1_800_000, 24)
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
