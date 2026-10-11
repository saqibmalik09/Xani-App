package app.xani.feature.call.rtc

import android.content.Context
import android.graphics.Matrix
import android.opengl.GLES30
import android.os.Handler
import android.os.Looper
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
import app.xani.core.lens.Lens
import app.xani.core.lens.LensCaps
import app.xani.core.lens.LensController
import app.xani.core.lens.LensMath
import app.xani.core.lens.LensPipeline
import app.xani.core.lens.LensSource
import app.xani.core.lens.Target
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.TextureBufferImpl
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoProcessor
import livekit.org.webrtc.VideoSink
import livekit.org.webrtc.YuvConverter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Host-side GPU processor: the same [LensPipeline] Create uses, applied to the
 * LiveKit camera track before encode. Viewers receive already-composited
 * frames — millions of seats never run a shader. Identity is a passthrough
 * (zero extra GPU). A failed wrap or missing EGL falls back to the raw frame
 * so a live never goes black because of a filter.
 */
internal class LensRtcProcessor(
    context: Context,
    private val controller: LensController = Lens.live,
) : VideoProcessor {
    private val app = context.applicationContext
    private val caps = LensCaps.forDevice(DeviceClass.read(app))
    private var sink: VideoSink? = null

    /**
     * The host's own renderer. It cannot read the published track: LiveKit
     * feeds track renderers from the capture dispatch, upstream of this
     * processor, so the host would watch an ungraded picture while viewers get
     * the filtered one — and after a capturer restart that dispatch is dead
     * and the host sees nothing at all. Fed from the processor output instead,
     * the self-view is frame-for-frame what goes on the wire.
     */
    private val mirrors = CopyOnWriteArrayList<VideoSink>()
    private var pipeline: LensPipeline? = null
    private var yuv: YuvConverter? = null
    private var toI420: Handler? = null

    // Output textures, reused frame to frame. A slot stays busy until the
    // encoder releases the wrapped buffer — recycling earlier hands the
    // encoder a texture we are already overwriting.
    private class Slot {
        var target: Target? = null
        @Volatile var busy = false
    }

    // Six: the encoder and the host's renderer hold a slot each at the same
    // time, and a brief encoder stall (thermal dip mid-broadcast) holds a few
    // more. A pool that runs dry costs a frame — dropped under a replaced
    // background, raw fallback otherwise — so the margin is cheap insurance
    // (~2 MB a slot at the working size).
    private val pool = Array(6) { Slot() }

    init {
        controller.caps = caps
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    /** Show the host what is being published. Safe to call repeatedly. */
    fun addMirror(sink: VideoSink) {
        if (!mirrors.contains(sink)) mirrors.add(sink)
    }

    fun removeMirror(sink: VideoSink) {
        mirrors.remove(sink)
    }

    override fun onCapturerStarted(success: Boolean) = Unit

    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        val out = runFeature(null) { process(frame) }
        // Raw fallback keeps a live from going black when the pipeline cannot
        // serve a frame — but not when a replaced background is on: the raw
        // frame is the host's real room, and one of them flashing through the
        // virtual background every time the pool runs dry is both the
        // "background glitches after a while" report and a privacy leak.
        // Dropping a single frame at 30 fps is invisible; the room is not.
        if (out == null && !controller.value.background.isNone) return
        val send = out ?: frame
        // Encoder first: the wire must not wait on a local renderer, and a
        // renderer that throws must not cost viewers a frame.
        sink?.onFrame(send)
        for (m in mirrors) runFeature(Unit) { m.onFrame(send) }
        if (send !== frame) send.release()
    }

    /**
     * Rotate, cover-crop and grade the capture into an upright portrait frame.
     *
     * Runs on every frame, not only when a look is selected, because the
     * geometry matters on its own. The capturer hands over a landscape sensor
     * buffer plus a "rotate me" tag, and anything downstream that does not act
     * on that tag paints a 16:9 picture into a 9:16 window — a roughly 4x
     * centre crop, which is the "host looks zoomed in" report. Standing the
     * frame up here and publishing it at rotation 0 leaves nothing to
     * interpret, spends no encoded pixels on the sides every full-screen
     * viewer would crop away anyway, and frames the broadcast like the host's
     * own viewfinder. With no look selected it costs one copy.
     */
    private fun process(frame: VideoFrame): VideoFrame? {
        val tb = frame.buffer as? VideoFrame.TextureBuffer ?: return null
        val looper = Looper.myLooper() ?: return null
        if (toI420 == null) toI420 = Handler(looper)
        val pipe = pipeline ?: LensPipeline(app, controller, caps).also {
            pipeline = it
            // Compile every shader and allocate the render targets now, on the
            // very first captured frame — the broadcast is still in its connect
            // phase, so the one-time cost is invisible. Without this the first
            // filter tap mid-broadcast pays the compiles on this same capture
            // thread, stalling both the encoder and the host's self-view: the
            // "phone hangs when I change filters" report.
            runFeature(Unit) { it.prewarm(Portrait) }
        }
        val converter = yuv ?: YuvConverter().also { yuv = it }
        val handler = toI420 ?: Handler(looper).also { toI420 = it }
        val oes = tb.type == VideoFrame.TextureBuffer.Type.OES
        val st = RendererCommon.convertMatrixFromAndroidGraphicsMatrix(tb.transformMatrix)
        // Clockwise degrees that stand this buffer up, per the capturer.
        val rot = ((frame.rotation % 360) + 360) % 360
        val src = LensSource(
            tb.textureId,
            oes,
            // Not mirrored: viewers see the host the way a room would. The
            // host's own renderer mirrors itself, the way a selfie should.
            LensMath.sourceMatrix(st, tb.width, tb.height, Portrait, rot, false),
        )
        // A look that fails to render still publishes upright, just ungraded —
        // geometry is not something a filter gets to break.
        val work = if (controller.value.identity) 0 else runFeature(0) { pipe.render(src, Portrait) }
        val (w, h) = pipe.workSize(Portrait)
        val slot = nextSlot(w, h) ?: return null
        val dst = slot.target ?: return null
        dst.bind()
        if (work == 0) pipe.blitSource(src) else pipe.blit2D(work)
        // Finish, not flush: the encoder and the self-view read this texture
        // from their own GL contexts on other threads. A flush only queues
        // the work — minutes into a broadcast, with the chip hot and the
        // effect chain at its longest (segmented background), the read can
        // land before the write completes and torn half-rendered frames
        // glitch through the replaced background. Finishing pins the cost
        // here on the capture thread, single-digit ms at the working size.
        GLES30.glFinish()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        slot.busy = true
        val wrapped = TextureBufferImpl(
            w,
            h,
            VideoFrame.TextureBuffer.Type.RGB,
            dst.tex,
            Matrix(),
            handler,
            converter,
            Runnable { slot.busy = false },
        )
        return VideoFrame(wrapped, 0, frame.timestampNs)
    }

    /** A free slot at this size, or null — the caller then sends the raw frame. */
    private fun nextSlot(w: Int, h: Int): Slot? {
        for (slot in pool) {
            if (slot.busy) continue
            val t = slot.target
            if (t != null && (t.w != w || t.h != h)) {
                runFeature(Unit) { t.release() }
                slot.target = null
            }
            if (slot.target == null) {
                slot.target = runFeature(null) { Target(w, h) } ?: return null
            }
            return slot
        }
        return null
    }

    private companion object {
        /**
         * Broadcast shape. 9:16 rather than the host's own screen aspect: a
         * taller frame only gives every 16:9 viewer something to crop, and
         * portrait video is what a full-screen live is watched in.
         */
        const val Portrait = 9f / 16f
    }

    fun release() {
        mirrors.clear()
        runFeature(Unit) {
            pipeline?.release()
            yuv?.release()
            pool.forEach { it.target?.release() }
        }
        pipeline = null
        yuv = null
        pool.forEach {
            it.target = null
            it.busy = false
        }
    }
}
