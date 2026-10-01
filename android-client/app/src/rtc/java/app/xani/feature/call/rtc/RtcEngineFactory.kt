package app.xani.feature.call.rtc

import android.content.Context
import app.xani.core.isolate.runFeature
import app.xani.core.net.IceServerData
import app.xani.feature.call.RtcEngine
import app.xani.feature.call.RtcPeer
import app.xani.feature.call.RtcRoom
import livekit.org.webrtc.DefaultVideoDecoderFactory
import livekit.org.webrtc.DefaultVideoEncoderFactory
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.HardwareVideoEncoderFactory
import livekit.org.webrtc.PeerConnectionFactory
import livekit.org.webrtc.audio.JavaAudioDeviceModule

/**
 * Real media stack (compiled only with `XANI_RTC=1`). libwebrtc for 1:1 with
 * hardware H.264/Opus; the SFU client for rooms. One factory and one EGL
 * context per process — creating them per call costs ~150 ms and a GPU context.
 */
object RtcEngineFactory {
    const val AVAILABLE = true

    fun create(context: Context): RtcEngine? = runFeature(null) { WebRtcEngine(context.applicationContext) }
}

internal class WebRtcEngine(private val app: Context) : RtcEngine {
    val egl: EglBase = EglBase.create()
    val factory: PeerConnectionFactory

    /**
     * Whether libwebrtc will actually encode H.264 on this phone. Its hardware
     * factory only admits encoders named `OMX.qcom.*` / `OMX.Exynos.*` and it
     * ships no software H.264, so a MediaTek, Unisoc or Codec2-named Qualcomm
     * (`c2.qti.*`) host that is *told* to publish H.264 publishes a track with
     * no frames behind it: viewers hear the host and stare at black. Asked of
     * the same factory class LiveKit uses, once, off Main (see warmEngine).
     */
    val hardwareH264: Boolean = runFeature(false) {
        HardwareVideoEncoderFactory(null, true, true).supportedCodecs.any { it.name.equals("H264", ignoreCase = true) }
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        val adm = JavaAudioDeviceModule.builder(app)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setUseStereoInput(false)
            .setUseStereoOutput(false)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            // enableIntelVp8Encoder=true, enableH264HighProfile=true: hardware first.
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply { disableNetworkMonitor = false })
            .createPeerConnectionFactory()
        adm.release()
    }

    override fun createPeer(ice: List<IceServerData>, relayOnly: Boolean, video: Boolean, listener: RtcPeer.Listener): RtcPeer =
        WebRtcPeer(app, this, ice, relayOnly, video, listener)

    override fun joinRoom(url: String, token: String, video: Boolean, publish: Boolean, listener: RtcRoom.Listener): RtcRoom =
        LiveKitRoom(app, url, token, video, publish, listener, hardwareH264)

    override fun release() {
        runFeature(Unit) { factory.dispose() }
        runFeature(Unit) { egl.release() }
    }
}
