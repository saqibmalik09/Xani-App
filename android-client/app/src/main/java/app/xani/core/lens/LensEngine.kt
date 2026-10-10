package app.xani.core.lens

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import app.xani.core.isolate.runFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Camera2 host for the lens pipeline. Owns one GL thread and EGL context,
 * an input SurfaceTexture the camera renders into, and up to two outputs:
 * the viewfinder (a TextureView's SurfaceTexture) and an encoder Surface
 * (MediaRecorder). Both receive the same processed frame, so what the user
 * sees is exactly what the clip contains.
 *
 * Camera buffers arrive on the GL thread; nothing here touches Main. When
 * the state is identity the frame is copied once per output — the cost of
 * having the engine in the path is one blit, never a stall.
 */
internal class LensEngine(
    context: Context,
    private val controller: LensController,
    private val caps: LensCaps,
) {
    private val app = context.applicationContext
    private val thread = HandlerThread("lens-gl").also { it.start() }
    private val handler = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var config: EGLConfig? = null
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pipeline: LensPipeline? = null

    private var inputTex = 0
    private var inputST: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val st = FloatArray(16)
    private val pending = AtomicInteger(0)

    private class Output(val window: Any, var w: Int, var h: Int, var egl: EGLSurface, val encoder: Boolean) {
        val aspect: Float get() = w.toFloat() / h.coerceAtLeast(1)
        var drawn = false
        var currentFailed = false
        val name: String get() = if (encoder) "encoder" else "preview"
    }

    private var preview: Output? = null
    private var record: Output? = null

    private var bufW = 1280
    private var bufH = 720
    private var sensorOrientation = 90
    private var front = false
    private var displayRotation = Surface.ROTATION_0

    @Volatile private var released = false
    @Volatile var failed = false
        private set

    /**
     * Bring the context up (first call) and point the input at this camera
     * geometry. Returns the Surface the camera must render into, or null when
     * the GPU cannot give us an ES 3.0 context — the caller then previews
     * directly, filters off, instead of a black viewfinder.
     */
    fun start(bufW: Int, bufH: Int, sensorOrientation: Int, front: Boolean, displayRotation: Int): Surface? {
        if (released) return null
        var out: Surface? = null
        runSync {
            if (eglContext == EGL14.EGL_NO_CONTEXT && !initEgl()) {
                Log.w(TAG, "EGL init failed, lens disabled err=0x${Integer.toHexString(EGL14.eglGetError())}")
                failed = true
                return@runSync
            }
            this.bufW = bufW
            this.bufH = bufH
            this.sensorOrientation = sensorOrientation
            this.front = front
            this.displayRotation = displayRotation
            if (inputST == null) {
                inputTex = GlKit.oesTexture()
                val tex = SurfaceTexture(inputTex)
                tex.setOnFrameAvailableListener({ onFrame() }, handler)
                inputST = tex
                inputSurface = Surface(tex)
            }
            inputST?.setDefaultBufferSize(bufW, bufH)
            out = inputSurface
        }
        Log.i(TAG, "start ok=${out != null} buf=${bufW}x$bufH sensor=$sensorOrientation front=$front")
        return out
    }

    /**
     * Attach the viewfinder. Synchronous because the caller has to know whether
     * EGL accepted the window: a texture whose producer is still connected
     * elsewhere is refused, and without an answer the host would leave the
     * TextureView holding its last frame — a frozen picture, not a preview.
     */
    fun attachPreview(texture: SurfaceTexture, w: Int, h: Int): Boolean {
        if (released) return false
        runSync {
            val cur = preview
            if (cur != null && cur.window === texture) {
                cur.w = w
                cur.h = h
            } else {
                cur?.let { destroy(it) }
                preview = createOutput(texture, w, h, encoder = false)
            }
        }
        val ok = preview != null
        if (ok) warm(w.toFloat() / h.coerceAtLeast(1))
        return ok
    }

    /**
     * Build the effect pipeline while the viewfinder is still showing the
     * passthrough copy. Compiling the composite shader and allocating its
     * targets costs a few hundred ms on a mid chip, and the GL thread is the
     * one that paints: paying it on the user's first filter tap freezes the
     * picture at exactly the moment they are looking for a change.
     */
    private fun warm(aspect: Float) {
        handler.post {
            if (released || eglContext == EGL14.EGL_NO_CONTEXT) return@post
            val anchor = preview?.egl ?: pbuffer
            if (!EGL14.eglMakeCurrent(display, anchor, anchor, eglContext)) return@post
            runFeature(Unit) {
                val pipe = pipeline ?: LensPipeline(app, controller, caps).also { pipeline = it }
                pipe.prewarm(aspect)
            }
        }
    }

    /** New bounds for an attached viewfinder; never blocks the caller. */
    fun resizePreview(texture: SurfaceTexture, w: Int, h: Int) {
        if (released) return
        handler.post {
            val cur = preview
            if (cur != null && cur.window === texture) {
                cur.w = w
                cur.h = h
            } else {
                cur?.let { destroy(it) }
                preview = createOutput(texture, w, h, encoder = false)
            }
        }
    }

    /** Detach, synchronously, so the texture's producer is free the moment this returns. */
    fun detachPreview() {
        if (released) return
        runSync {
            preview?.let { destroy(it) }
            preview = null
            Log.i(TAG, "preview detached")
        }
    }

    /** An encoder surface; frames are timestamped from the camera so the clip plays at speed. */
    fun setRecord(surface: Surface?, w: Int, h: Int) {
        if (released) return
        runSync {
            record?.let { destroy(it) }
            record = if (surface == null) null else createOutput(surface, w, h, encoder = true)
        }
    }

    /**
     * Clip size matching the viewfinder's shape at a sharpness the device can
     * encode in real time; never below 720 on the long edge.
     */
    fun recordSize(previewW: Int, previewH: Int): Size {
        val aspect = previewW.toFloat() / previewH.coerceAtLeast(1)
        val long = caps.workLongEdge.coerceIn(1280, 1920)
        return if (aspect < 1f) Size(round16((long * aspect).toInt()), round16(long))
        else Size(round16(long), round16((long / aspect).toInt()))
    }

    fun release() {
        if (released) return
        released = true
        handler.post {
            runFeature(Unit) { pipeline?.release() }
            pipeline = null
            preview?.let { destroy(it) }
            record?.let { destroy(it) }
            preview = null
            record = null
            runFeature(Unit) { inputSurface?.release() }
            runFeature(Unit) { inputST?.release() }
            inputSurface = null
            inputST = null
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
                eglContext = EGL14.EGL_NO_CONTEXT
                pbuffer = EGL14.EGL_NO_SURFACE
            }
            thread.quitSafely()
        }
    }

    // ─── GL thread ───────────────────────────────────────────────────────────

    private fun onFrame() {
        // Coalesce: a slow frame leaves one pending draw, never a queue of them.
        if (pending.incrementAndGet() > 1) return
        drawFrame()
        pending.set(0)
    }

    private fun drawFrame() {
        val tex = inputST ?: return
        if (released || eglContext == EGL14.EGL_NO_CONTEXT) return
        val prev = preview
        val rec = record
        val anchor = prev?.egl ?: rec?.egl ?: pbuffer
        if (!EGL14.eglMakeCurrent(display, anchor, anchor, eglContext)) return
        runFeature(Unit) {
            tex.updateTexImage()
            tex.getTransformMatrix(st)
        }
        val ts = tex.timestamp
        val pipe = pipeline ?: LensPipeline(app, controller, caps).also { pipeline = it }
        val swap = sensorOrientation % 180 != 0
        val uw = if (swap) bufH else bufW
        val uh = if (swap) bufW else bufH
        val rot = when (displayRotation) {
            Surface.ROTATION_90 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 90
            else -> 0
        }
        val state = controller.value
        val identity = state.identity
        var workTex = 0
        var workAspect = 1f
        if (!identity) {
            val ref = prev ?: rec
            workAspect = ref?.aspect ?: (uw.toFloat() / uh)
            val (ww, wh) = pipe.workSize(workAspect)
            workAspect = ww.toFloat() / wh
            workTex = runFeature(0) {
                pipe.render(LensSource(inputTex, true, LensMath.sourceMatrix(st, uw, uh, workAspect, rot, front)), workAspect)
            }
        }
        for (out in listOfNotNull(prev, rec)) {
            if (!EGL14.eglMakeCurrent(display, out.egl, out.egl, eglContext)) {
                if (!out.currentFailed) {
                    out.currentFailed = true
                    Log.w(TAG, "${out.name} makeCurrent failed err=0x${Integer.toHexString(EGL14.eglGetError())}")
                }
                continue
            }
            out.currentFailed = false
            GLES30.glViewport(0, 0, out.w, out.h)
            val ok = runFeature(false) {
                if (identity || workTex == 0) {
                    pipe.blitSource(LensSource(inputTex, true, LensMath.sourceMatrix(st, uw, uh, out.aspect, rot, front)))
                } else {
                    pipe.blit2D(workTex, LensMath.coverMatrix(workAspect, out.aspect))
                }
                true
            }
            if (!ok) continue
            if (out.encoder) EGLExt.eglPresentationTimeANDROID(display, out.egl, ts)
            if (!EGL14.eglSwapBuffers(display, out.egl)) {
                // Abandoned window (view gone, encoder stopped): drop it rather than spin.
                Log.w(TAG, "${out.name} swap failed, dropping err=0x${Integer.toHexString(EGL14.eglGetError())}")
                destroy(out)
                if (out === preview) preview = null
                if (out === record) record = null
            } else if (!out.drawn) {
                out.drawn = true
                Log.i(TAG, "first frame → ${out.name} ${out.w}x${out.h} identity=$identity")
            }
        }
    }

    private fun createOutput(window: Any, w: Int, h: Int, encoder: Boolean): Output? {
        val name = if (encoder) "encoder" else "preview"
        val cfg = config ?: run {
            Log.w(TAG, "$name bind skipped: no EGL config")
            return null
        }
        val s = runFeature(EGL14.EGL_NO_SURFACE) {
            EGL14.eglCreateWindowSurface(display, cfg, window, intArrayOf(EGL14.EGL_NONE), 0)
        }
        if (s == EGL14.EGL_NO_SURFACE) {
            Log.w(TAG, "$name bind failed ${w}x$h err=0x${Integer.toHexString(EGL14.eglGetError())}")
            return null
        }
        Log.i(TAG, "$name bound ${w}x$h")
        return Output(window, w, h, s, encoder)
    }

    private fun destroy(o: Output) {
        if (o.egl != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, pbuffer, pbuffer, eglContext)
            EGL14.eglDestroySurface(display, o.egl)
            o.egl = EGL14.EGL_NO_SURFACE
        }
    }

    private fun initEgl(): Boolean = runFeature(false) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return@runFeature false
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return@runFeature false
        config = chooseConfig(recordable = true) ?: chooseConfig(recordable = false) ?: return@runFeature false
        eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) return@runFeature false
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        if (pbuffer == EGL14.EGL_NO_SURFACE) return@runFeature false
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, eglContext)
    }

    private fun chooseConfig(recordable: Boolean): EGLConfig? {
        val attribs = ArrayList<Int>().apply {
            add(EGL14.EGL_RED_SIZE); add(8)
            add(EGL14.EGL_GREEN_SIZE); add(8)
            add(EGL14.EGL_BLUE_SIZE); add(8)
            add(EGL14.EGL_ALPHA_SIZE); add(8)
            add(EGL14.EGL_RENDERABLE_TYPE); add(EGLExt.EGL_OPENGL_ES3_BIT_KHR)
            add(EGL14.EGL_SURFACE_TYPE); add(EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT)
            if (recordable) { add(EGL_RECORDABLE_ANDROID); add(1) }
            add(EGL14.EGL_NONE)
        }.toIntArray()
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) || n[0] <= 0) return null
        return configs[0]
    }

    private fun runSync(block: () -> Unit) {
        if (Looper.myLooper() == thread.looper) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        if (!handler.post {
                try { runFeature(Unit, block) } finally { latch.countDown() }
            }
        ) return
        latch.await(2, TimeUnit.SECONDS)
    }

    private fun round16(v: Int): Int = ((v + 8) / 16 * 16).coerceAtLeast(64)

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val TAG = "XaniLens"
    }
}
