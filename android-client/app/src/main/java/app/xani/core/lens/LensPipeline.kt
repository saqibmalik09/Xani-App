package app.xani.core.lens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Build
import android.os.SystemClock
import app.xani.core.isolate.runFeature
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A camera frame as the pipeline wants it: a texture plus the matrix that
 * maps quad texcoords onto upright, cover-cropped content (see [LensMath]).
 */
internal class LensSource(
    val tex: Int,
    val oes: Boolean,
    val texMatrix: FloatArray,
)

/**
 * The GPU half of Xani Lens. Context-agnostic: whoever calls owns an EGL
 * context current on this thread ([LensEngine] for Camera2, the RTC video
 * processor for libwebrtc/LiveKit). All GL objects are created lazily on
 * first use and sized to the working canvas, which follows the device class.
 *
 * Per frame with effects on: source → full-res RGBA, half-res bilateral
 * (skin), quarter-res gaussian (background), one composite pass, stickers.
 * With everything off the caller never enters [render] at all.
 */
internal class LensPipeline(
    context: Context,
    private val controller: LensController,
    private val caps: LensCaps,
) {
    private val app = context.applicationContext
    private val analyzer = LensAnalyzer()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "lens-io").apply { isDaemon = true } }

    private var progOes: Prog? = null
    private var prog2d: Prog? = null
    private var progBilateral: Prog? = null
    private var progBlur: Prog? = null
    private var progMix: Prog? = null
    private var progBeauty: Prog? = null
    private var progSticker: Prog? = null
    private var progBgPrep: Prog? = null

    private var pw = 0
    private var ph = 0
    private var srcT: Target? = null
    private var halfT: Target? = null
    private var smoothT: Target? = null
    private var qA: Target? = null
    private var qB: Target? = null
    private var outT: Target? = null
    private var anaT: Target? = null
    private var segA: Target? = null
    private var segB: Target? = null
    private var segCurIsA = true
    private var segNewTex = 0
    private var segNewW = 0
    private var segNewH = 0
    private var segUpload: ByteBuffer? = null
    private var whiteTex = 0

    private var pbo = 0
    private var pboPending = false
    private var anaBitmap: Bitmap? = null
    private var frame = 0L
    private var seenSerial = -1L
    private var faces: List<FaceInfo> = emptyList()

    // Render-rate face smoothing. The analyzer delivers raw measurements at
    // 10–25 Hz; aiming each rendered frame at the measurement plus a short
    // velocity lead, with a time constant that collapses under motion, is what
    // keeps a mask glued to a moving face instead of trailing it.
    private var targetFaces: List<FaceInfo> = emptyList()
    private var shownFaces: List<FaceInfo> = emptyList()
    private val faceVel = HashMap<Int, FloatArray>()
    private var faceLead = 0f
    private var lastTargetNs = 0L
    private var lastStepNs = 0L

    private val stickerTex = HashMap<String, Int>()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val faceArr = FloatArray(12)
    private val eyeLArr = FloatArray(6)
    private val eyeRArr = FloatArray(6)
    private val chinArr = FloatArray(6)

    private var bgImageUri: Uri? = null
    private var bgImageTex = 0
    private var bgImageW = 1
    private var bgImageH = 1
    @Volatile private var bgDecoded: Pair<Uri, Bitmap>? = null
    @Volatile private var bgDecoding: Uri? = null

    /** Working canvas size for an output of this aspect (w/h). */
    fun workSize(aspect: Float): Pair<Int, Int> {
        val long = caps.workLongEdge
        return if (aspect < 1f) Pair(round16((long * aspect).roundToInt()), round16(long))
        else Pair(round16(long), round16((long / aspect).roundToInt()))
    }

    /**
     * Copy the source straight to the bound framebuffer — the identity path,
     * one draw, no intermediate. Viewport must already be set.
     */
    fun blitSource(src: LensSource) {
        val p = if (src.oes) oes() else twoD()
        p.use()
        p.setMat4("uTexMatrix", src.texMatrix)
        GlKit.bind(0, if (src.oes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D, src.tex, p.u("uTex"))
        GlKit.drawQuad(p.aPos, p.aTex)
    }

    /** Draw a 2D texture to the bound framebuffer through [texMatrix]. */
    fun blit2D(tex: Int, texMatrix: FloatArray = GlKit.IDENTITY) {
        val p = twoD()
        p.use()
        p.setMat4("uTexMatrix", texMatrix)
        GlKit.bind(0, GLES30.GL_TEXTURE_2D, tex, p.u("uTex"))
        GlKit.drawQuad(p.aPos, p.aTex)
    }

    /**
     * Compile the programs and size the targets before the first effect frame,
     * so switching from passthrough to a look costs one frame, not a compile.
     *
     * Every program, not just the common three: a lazily compiled shader runs
     * on the frame thread at the exact moment the user taps the filter that
     * needs it — the beauty composite alone is a few hundred milliseconds on a
     * mid chip, which reads as the whole phone hanging mid-broadcast. Paying
     * all of them here moves that cost behind the camera-open / connect phase,
     * where nobody is watching frames yet.
     */
    fun prewarm(aspect: Float) {
        runFeature(Unit) { oes() }
        runFeature(Unit) { twoD() }
        runFeature(Unit) { beautyProg() }
        runFeature(Unit) { bilateral() }
        runFeature(Unit) { blur() }
        runFeature(Unit) { mix() }
        runFeature(Unit) { sticker() }
        runFeature(Unit) { bgPrep() }
        val (w, h) = workSize(aspect)
        runFeature(Unit) { ensureTargets(w, h) }
    }

    /**
     * Run the effect chain for the current state. Returns the result texture
     * (sized [workSize]) — valid until the next [render] or [release].
     */
    fun render(src: LensSource, aspect: Float): Int {
        val state = controller.value
        val (w, h) = workSize(aspect)
        ensureTargets(w, h)
        val s = srcT!!
        frame++

        // 1. Source → full-res working frame.
        s.bind()
        blitSource(src)

        // 2. ML analysis on a small copy, bounded to one round in flight.
        if (state.needsFace || state.needsSegmentation) {
            analyze(state)
            consumeAnalysis()
            advanceFaces()
        } else {
            faces = emptyList()
            targetFaces = emptyList()
            shownFaces = emptyList()
            faceVel.clear()
        }

        // 3. Skin layer: half-res bilateral.
        val beauty = state.beauty
        if (beauty.smooth > 0f || beauty.sharpen > 0f || beauty.whiten > 0f || beauty.glow > 0f) {
            halfT!!.bind()
            blit2D(s.tex)
            val bp = bilateral()
            smoothT!!.bind()
            bp.use()
            bp.set("uTexel", 1f / halfT!!.w, 1f / halfT!!.h)
            bp.set("uRange", 0.08f + 0.06f * beauty.smooth)
            GlKit.bind(0, GLES30.GL_TEXTURE_2D, halfT!!.tex, bp.u("uTex"))
            bp.setMat4("uTexMatrix", GlKit.IDENTITY)
            GlKit.drawQuad(bp.aPos, bp.aTex)
        }

        // 4. Background layer.
        val bg = state.background
        var bgMode = 0
        var bgTex = whiteTex
        when (bg) {
            Background.None -> Unit
            Background.Blur -> {
                bgMode = 1
                bgTex = blurBackground(s)
            }
            is Background.Gradient -> bgMode = 2
            is Background.Image -> {
                bgMode = 3
                bgTex = imageBackground(bg.uri) ?: run { bgMode = 2; whiteTex }
            }
        }

        // 5. Composite: reshape, skin, background, grade.
        val look = LensCatalog.look(state.lookId) ?: LensCatalog.look(LensCatalog.NaturalLook)!!
        val out = outT!!
        out.bind()
        val p = beautyProg()
        p.use()
        p.setMat4("uTexMatrix", GlKit.IDENTITY)
        GlKit.bind(0, GLES30.GL_TEXTURE_2D, s.tex, p.u("uSrc"))
        GlKit.bind(1, GLES30.GL_TEXTURE_2D, smoothT?.tex ?: s.tex, p.u("uSmooth"))
        GlKit.bind(2, GLES30.GL_TEXTURE_2D, segCur().tex, p.u("uSeg"))
        GlKit.bind(3, GLES30.GL_TEXTURE_2D, bgTex, p.u("uBg"))
        p.set("uSize", w.toFloat(), h.toFloat())
        p.set("uSmoothAmt", beauty.smooth)
        p.set("uWhiten", beauty.whiten)
        p.set("uSharpen", beauty.sharpen)
        p.set("uEyes", if (faces.isEmpty()) 0f else beauty.eyes)
        p.set("uSlim", if (faces.isEmpty()) 0f else beauty.slim)
        p.set("uGlow", beauty.glow)
        pushFaces(p)
        p.set("uBgMode", bgMode)
        if (bg is Background.Gradient) {
            p.setColor("uBgTop", bg.top)
            p.setColor("uBgBottom", bg.bottom)
        } else {
            p.set("uBgTop", 0.05f, 0.05f, 0.08f)
            p.set("uBgBottom", 0f, 0f, 0f)
        }
        p.set("uBgEdge", 0.10f)
        bgRect(p, w, h)
        p.set("uExposure", look.exposure)
        p.set("uContrast", look.contrast)
        p.set("uSaturation", look.saturation)
        p.set("uTemp", look.temperature)
        p.set("uTint", look.tint)
        p.set("uFade", look.fade)
        p.set("uVignette", look.vignette)
        p.set("uHue", look.hue)
        p.set("uSplit", look.split)
        p.set("uMono", look.mono)
        p.set("uGrain", look.grain)
        p.set("uTime", (SystemClock.uptimeMillis() % 100_000L) / 1000f)
        p.setColor("uShadow", look.shadowRgb)
        p.setColor("uHigh", look.highRgb)
        GlKit.drawQuad(p.aPos, p.aTex)

        // 6. Face masks.
        if (state.masks.isNotEmpty() && faces.isNotEmpty()) drawMasks(state, w, h)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return out.tex
    }

    fun release() {
        analyzer.release()
        listOf(progOes, prog2d, progBilateral, progBlur, progMix, progBeauty, progSticker, progBgPrep).forEach { it?.release() }
        progOes = null; prog2d = null; progBilateral = null; progBlur = null; progMix = null; progBeauty = null; progSticker = null; progBgPrep = null
        dropTargets()
        stickerTex.values.forEach { GlKit.deleteTexture(it) }
        stickerTex.clear()
        GlKit.deleteTexture(bgImageTex)
        bgImageTex = 0
        GlKit.deleteTexture(whiteTex)
        whiteTex = 0
        if (pbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(pbo), 0)
            pbo = 0
        }
        anaBitmap?.recycle()
        anaBitmap = null
        io.shutdownNow()
    }

    // ─── programs ────────────────────────────────────────────────────────────

    private fun oes() = progOes ?: Prog(LensShaders.VERT, LensShaders.FRAG_OES).also { progOes = it }
    private fun twoD() = prog2d ?: Prog(LensShaders.VERT, LensShaders.FRAG_2D).also { prog2d = it }
    private fun bilateral() = progBilateral ?: Prog(LensShaders.VERT, LensShaders.FRAG_BILATERAL).also { progBilateral = it }
    private fun blur() = progBlur ?: Prog(LensShaders.VERT, LensShaders.FRAG_BLUR).also { progBlur = it }
    private fun mix() = progMix ?: Prog(LensShaders.VERT, LensShaders.FRAG_MIX).also { progMix = it }
    private fun beautyProg() = progBeauty ?: Prog(LensShaders.VERT, LensShaders.FRAG_BEAUTY).also { progBeauty = it }
    private fun sticker() = progSticker ?: Prog(LensShaders.VERT_STICKER, LensShaders.FRAG_STICKER).also { progSticker = it }
    private fun bgPrep() = progBgPrep ?: Prog(LensShaders.VERT, LensShaders.FRAG_BGPREP).also { progBgPrep = it }

    // ─── targets ─────────────────────────────────────────────────────────────

    private fun ensureTargets(w: Int, h: Int) {
        if (w == pw && h == ph && srcT != null) return
        dropTargets()
        pw = w
        ph = h
        srcT = Target(w, h)
        outT = Target(w, h)
        halfT = Target(w / 2, h / 2)
        smoothT = Target(w / 2, h / 2)
        qA = Target(w / 4, h / 4)
        qB = Target(w / 4, h / 4)
        val aw = 256
        val ah = ((256f * h / w).roundToInt() / 2) * 2
        anaT = Target(aw, ah)
        anaBitmap?.recycle()
        anaBitmap = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888)
        if (pbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            pbo = ids[0]
        }
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo)
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, aw * ah * 4, null, GLES30.GL_STREAM_READ)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        pboPending = false
        segA = Target(256, 256, GLES30.GL_R8, GLES30.GL_RED).also { clearWhite(it) }
        segB = Target(256, 256, GLES30.GL_R8, GLES30.GL_RED).also { clearWhite(it) }
        segCurIsA = true
        if (whiteTex == 0) {
            whiteTex = GlKit.texture2D(1, 1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex)
            GLES30.glTexSubImage2D(
                GLES30.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).also { it.position(0) },
            )
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun clearWhite(t: Target) {
        t.bind()
        GLES30.glClearColor(1f, 1f, 1f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun dropTargets() {
        listOf(srcT, outT, halfT, smoothT, qA, qB, anaT, segA, segB).forEach { it?.release() }
        srcT = null; outT = null; halfT = null; smoothT = null; qA = null; qB = null; anaT = null; segA = null; segB = null
        GlKit.deleteTexture(segNewTex)
        segNewTex = 0
        segNewW = 0
        segNewH = 0
    }

    private fun segCur(): Target = if (segCurIsA) segA!! else segB!!

    private fun round16(v: Int): Int = ((v + 8) / 16 * 16).coerceAtLeast(64)

    // ─── analysis ────────────────────────────────────────────────────────────

    private fun analyze(state: LensState) {
        val ana = anaT ?: return
        val bmp = anaBitmap ?: return
        if (pboPending) {
            if (analyzer.isBusy) return
            // Last frame's readback is done by now: copy out and hand to ML.
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo)
            val mapped = GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER, 0, ana.w * ana.h * 4, GLES30.GL_MAP_READ_BIT,
            ) as? ByteBuffer
            if (mapped != null) {
                runFeature(Unit) {
                    mapped.order(ByteOrder.nativeOrder())
                    mapped.position(0)
                    bmp.copyPixelsFromBuffer(mapped)
                }
                GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
            }
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
            pboPending = false
            if (mapped != null) {
                analyzer.submit(bmp, faces = state.needsFace, seg = state.needsSegmentation && caps.background)
            }
            return
        }
        if (frame % caps.analyzeEvery != 0L || analyzer.isBusy) return
        // Flip V so the bitmap comes out row-0-top for ML Kit.
        ana.bind()
        blit2D(srcT!!.tex, FLIP_V)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo)
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
        GLES30.glReadPixels(0, 0, ana.w, ana.h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        pboPending = true
    }

    private fun consumeAnalysis() {
        val a = analyzer.latest ?: return
        if (a.serial == seenSerial) return
        seenSerial = a.serial
        val now = System.nanoTime()
        val dtA = if (lastTargetNs == 0L) 0.1f else ((now - lastTargetNs) / 1e9f).coerceIn(0.02f, 0.3f)
        lastTargetNs = now
        // Velocity per tracked face, and how much the frame moved overall —
        // the lead for prediction and the weight for the mask blend below.
        var motion = 0f
        val prevTargets = targetFaces
        faceVel.clear()
        for (t in a.faces) {
            val p = prevTargets.firstOrNull { it.id == t.id } ?: continue
            val dx = t.cx - p.cx
            val dy = t.cy - p.cy
            motion = max(motion, hypot(dx, dy))
            faceVel[t.id] = floatArrayOf((dx / dtA).coerceIn(-1.5f, 1.5f), (dy / dtA).coerceIn(-1.5f, 1.5f))
        }
        faceLead = min(dtA * 0.7f, 0.12f)
        targetFaces = a.faces
        val seg = a.seg ?: return
        if (a.segW <= 0 || a.segH <= 0 || seg.size < a.segW * a.segH) return
        if (segNewTex == 0 || segNewW != a.segW || segNewH != a.segH) {
            GlKit.deleteTexture(segNewTex)
            segNewTex = GlKit.texture2D(a.segW, a.segH, GLES30.GL_R8, GLES30.GL_RED)
            segNewW = a.segW
            segNewH = a.segH
            segUpload = ByteBuffer.allocateDirect(a.segW * a.segH).order(ByteOrder.nativeOrder())
        }
        val buf = segUpload ?: return
        buf.clear()
        buf.put(seg, 0, a.segW * a.segH)
        buf.position(0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, segNewTex)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, a.segW, a.segH, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        // Temporal blend into the other ping-pong target; it becomes current.
        val prev = segCur()
        val next = if (segCurIsA) segB!! else segA!!
        next.bind()
        val m = mix()
        m.use()
        m.setMat4("uTexMatrix", GlKit.IDENTITY)
        GlKit.bind(0, GLES30.GL_TEXTURE_2D, prev.tex, m.u("uPrev"))
        GlKit.bind(1, GLES30.GL_TEXTURE_2D, segNewTex, m.u("uNext"))
        // Motion-adaptive temporal blend: at rest keep some history to kill
        // flicker, under motion take the new mask almost whole — a mask that
        // trails a moving person reads worse than one that shimmers slightly.
        m.set("uMix", if (a.faces.isEmpty()) 0.78f else (0.62f + motion * 6f).coerceAtMost(0.92f))
        m.set("uFlipNext", 1)
        GlKit.drawQuad(m.aPos, m.aTex)
        segCurIsA = !segCurIsA
    }

    /**
     * One step of the render-rate smoother: each shown face eases toward the
     * latest measurement extrapolated by its velocity over [faceLead]. The
     * time constant falls from 90 ms at rest (steady, no jitter) to ~20 ms
     * under motion (visually instant), so the art never floats behind a turn.
     */
    private fun advanceFaces() {
        val now = System.nanoTime()
        val dt = if (lastStepNs == 0L) 0.033f else ((now - lastStepNs) / 1e9f).coerceIn(0.004f, 0.1f)
        lastStepNs = now
        if (targetFaces.isEmpty()) {
            faces = emptyList()
            shownFaces = emptyList()
            return
        }
        val prev = shownFaces.associateBy { it.id }
        faces = targetFaces.map { t ->
            val v = faceVel[t.id]
            val aim = if (v != null) t.shift(v[0] * faceLead, v[1] * faceLead) else t
            val p = prev[t.id] ?: return@map aim
            val d = hypot(aim.cx - p.cx, aim.cy - p.cy)
            val tau = 0.09f - 0.07f * min(1f, d * 16f)
            p.lerp(aim, 1f - exp(-dt / tau))
        }
        shownFaces = faces
    }

    private fun pushFaces(p: Prog) {
        val n = faces.size.coerceAtMost(3)
        for (i in 0 until 3) {
            val f = faces.getOrNull(i)
            faceArr[i * 4] = f?.cx ?: 0f
            faceArr[i * 4 + 1] = f?.cy ?: 0f
            faceArr[i * 4 + 2] = f?.rx ?: 0f
            faceArr[i * 4 + 3] = f?.ry ?: 0f
            eyeLArr[i * 2] = f?.eyeL?.x ?: 0f
            eyeLArr[i * 2 + 1] = f?.eyeL?.y ?: 0f
            eyeRArr[i * 2] = f?.eyeR?.x ?: 0f
            eyeRArr[i * 2 + 1] = f?.eyeR?.y ?: 0f
            chinArr[i * 2] = f?.chin?.x ?: 0f
            chinArr[i * 2 + 1] = f?.chin?.y ?: 0f
        }
        p.set("uFaceCount", n)
        GLES30.glUniform4fv(p.u("uFace"), 3, faceArr, 0)
        GLES30.glUniform2fv(p.u("uEyeL"), 3, eyeLArr, 0)
        GLES30.glUniform2fv(p.u("uEyeR"), 3, eyeRArr, 0)
        GLES30.glUniform2fv(p.u("uChin"), 3, chinArr, 0)
    }

    // ─── background ──────────────────────────────────────────────────────────

    private fun blurBackground(s: Target): Int {
        val a = qA!!
        val b = qB!!
        // Knock the person out before blurring (colour premultiplied by the
        // background weight, weight in alpha). Blurring the raw frame smears
        // the person into a ghost halo around their own silhouette — the
        // single biggest "cheap portrait mode" tell. The composite divides
        // the blurred colour by the blurred weight to get pure background.
        a.bind()
        val pp = bgPrep()
        pp.use()
        pp.setMat4("uTexMatrix", GlKit.IDENTITY)
        GlKit.bind(0, GLES30.GL_TEXTURE_2D, s.tex, pp.u("uTex"))
        GlKit.bind(1, GLES30.GL_TEXTURE_2D, segCur().tex, pp.u("uSeg"))
        GlKit.drawQuad(pp.aPos, pp.aTex)
        val bp = blur()
        bp.use()
        bp.setMat4("uTexMatrix", GlKit.IDENTITY)
        repeat(3) {
            b.bind()
            bp.set("uDir", 2.0f / a.w, 0f)
            GlKit.bind(0, GLES30.GL_TEXTURE_2D, a.tex, bp.u("uTex"))
            GlKit.drawQuad(bp.aPos, bp.aTex)
            a.bind()
            bp.set("uDir", 0f, 2.0f / a.h)
            GlKit.bind(0, GLES30.GL_TEXTURE_2D, b.tex, bp.u("uTex"))
            GlKit.drawQuad(bp.aPos, bp.aTex)
        }
        return a.tex
    }

    private fun imageBackground(uri: Uri): Int? {
        if (bgImageTex != 0 && bgImageUri == uri) return bgImageTex
        bgDecoded?.let { (u, bmp) ->
            if (u == uri) {
                GlKit.deleteTexture(bgImageTex)
                bgImageTex = GlKit.textureFrom(bmp)
                bgImageW = bmp.width
                bgImageH = bmp.height
                bgImageUri = uri
                bgDecoded = null
                bmp.recycle()
                return bgImageTex
            }
        }
        if (bgDecoding != uri) {
            bgDecoding = uri
            io.execute {
                val bmp = runFeature(null) { decode(uri, 1280) }
                if (bmp != null) bgDecoded = uri to bmp
                if (bgDecoding == uri) bgDecoding = null
            }
        }
        return if (bgImageTex != 0) bgImageTex else null
    }

    private fun decode(uri: Uri, maxEdge: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(app.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longEdge = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                decoder.setTargetSampleSize(maxOf(1, longEdge / maxEdge))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxEdge)
        }
        return app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun bgRect(p: Prog, w: Int, h: Int) {
        val work = w.toFloat() / h
        val img = bgImageW.toFloat() / bgImageH.coerceAtLeast(1)
        if (img > work) {
            val sx = work / img
            p.u("uBgRect").let { GLES30.glUniform4f(it, sx, 1f, (1f - sx) / 2f, 0f) }
        } else {
            val sy = img / work
            p.u("uBgRect").let { GLES30.glUniform4f(it, 1f, sy, 0f, (1f - sy) / 2f) }
        }
    }

    // ─── masks ───────────────────────────────────────────────────────────────

    private fun drawMasks(state: LensState, w: Int, h: Int) {
        val sp = sticker()
        sp.use()
        sp.set("uAlpha", 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        for (id in state.masks) {
            val mask = LensCatalog.mask(id) ?: continue
            for (layer in mask.layers) {
                val tex = stickerTex.getOrPut(layer.art) {
                    val bmp = LensStickers.draw(layer.art, layer.aspect)
                    GlKit.textureFrom(bmp).also { bmp.recycle() }
                }
                GlKit.bind(0, GLES30.GL_TEXTURE_2D, tex, sp.u("uTex"))
                for (f in faces) drawLayer(sp, f, layer, w, h)
            }
        }
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawLayer(sp: Prog, f: FaceInfo, layer: MaskLayer, w: Int, h: Int) {
        // Pixel space so the art keeps its shape on a non-square canvas.
        val elx = f.eyeL.x * w
        val ely = f.eyeL.y * h
        val erx = f.eyeR.x * w
        val ery = f.eyeR.y * h
        val inter = hypot(erx - elx, ery - ely).coerceAtLeast(4f)
        var ux = f.forehead.x * w - f.chin.x * w
        var uy = f.forehead.y * h - f.chin.y * h
        val ul = hypot(ux, uy)
        if (ul < 1f) {
            ux = 0f
            uy = 1f
        } else {
            ux /= ul
            uy /= ul
        }
        // Right vector from the eye line keeps glasses level with a tilted head.
        var rx = erx - elx
        var ry = ery - ely
        val rl = hypot(rx, ry).coerceAtLeast(1f)
        rx /= rl
        ry /= rl
        // Anchor art consistently regardless of which eye ML Kit called "left".
        if (rx * uy - ry * ux < 0f) {
            rx = -rx
            ry = -ry
        }
        val midX = (elx + erx) / 2f
        val midY = (ely + ery) / 2f
        var ax: Float
        var ay: Float
        when (layer.anchor) {
            MaskLayer.Anchor.EyesMid -> { ax = midX; ay = midY }
            MaskLayer.Anchor.NoseTip -> { ax = f.nose.x * w; ay = f.nose.y * h }
            MaskLayer.Anchor.Mouth -> { ax = f.mouth.x * w; ay = f.mouth.y * h }
            MaskLayer.Anchor.Forehead -> { ax = midX + ux * inter * 1.0f; ay = midY + uy * inter * 1.0f }
        }
        ax += ux * inter * layer.up
        ay += uy * inter * layer.up
        val hw = inter * layer.width / 2f
        val hh = hw * layer.aspect
        // Corners: bottom-left, bottom-right, top-left, top-right (strip order).
        fun put(i: Int, sx: Float, sy: Float, u: Float, v: Float) {
            val px = ax + rx * sx * hw + ux * sy * hh
            val py = ay + ry * sx * hw + uy * sy * hh
            quad.put(i * 4, px / w * 2f - 1f)
            quad.put(i * 4 + 1, py / h * 2f - 1f)
            quad.put(i * 4 + 2, u)
            quad.put(i * 4 + 3, v)
        }
        // Bitmap row 0 is the top of the art and lands at v = 0 after upload.
        put(0, -1f, -1f, 0f, 1f)
        put(1, 1f, -1f, 1f, 1f)
        put(2, -1f, 1f, 0f, 0f)
        put(3, 1f, 1f, 1f, 0f)
        GlKit.drawQuad(sp.aPos, sp.aTex, quad)
    }

    private companion object {
        val FLIP_V: FloatArray = FloatArray(16).also {
            Matrix.setIdentityM(it, 0)
            Matrix.translateM(it, 0, 0f, 1f, 0f)
            Matrix.scaleM(it, 0, 1f, -1f, 1f)
        }
    }
}

/** Texture-matrix arithmetic shared by every host of the pipeline. */
internal object LensMath {
    /**
     * Compose the SurfaceTexture transform with crop / rotate / mirror so quad
     * texcoords land on upright content covering an output of [outAspect].
     *
     * @param st 4×4 transform from `SurfaceTexture.getTransformMatrix`, or identity.
     * @param uprightW/H content size after that transform (sensor buffer swapped by sensor orientation).
     * @param rotationDeg extra rotation (display rotation) applied to texcoords, counter-clockwise.
     * @param mirror flip horizontally (selfie preview).
     */
    fun sourceMatrix(
        st: FloatArray,
        uprightW: Int,
        uprightH: Int,
        outAspect: Float,
        rotationDeg: Int,
        mirror: Boolean,
    ): FloatArray {
        val swap = rotationDeg % 180 != 0
        val cw = (if (swap) uprightH else uprightW).toFloat().coerceAtLeast(1f)
        val ch = (if (swap) uprightW else uprightH).toFloat().coerceAtLeast(1f)
        val contentAspect = cw / ch
        var sx = 1f
        var sy = 1f
        if (contentAspect > outAspect) sx = outAspect / contentAspect else sy = contentAspect / outAspect
        val adj = FloatArray(16)
        Matrix.setIdentityM(adj, 0)
        Matrix.translateM(adj, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(adj, 0, sx, sy, 1f)
        if (rotationDeg != 0) Matrix.rotateM(adj, 0, rotationDeg.toFloat(), 0f, 0f, 1f)
        if (mirror) Matrix.scaleM(adj, 0, -1f, 1f, 1f)
        Matrix.translateM(adj, 0, -0.5f, -0.5f, 0f)
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, st, 0, adj, 0)
        return out
    }

    /** Cover-crop a [srcAspect] texture onto a [dstAspect] viewport (identity when equal). */
    fun coverMatrix(srcAspect: Float, dstAspect: Float): FloatArray {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        if (kotlin.math.abs(srcAspect - dstAspect) < 1e-3f) return m
        var sx = 1f
        var sy = 1f
        if (srcAspect > dstAspect) sx = dstAspect / srcAspect else sy = srcAspect / dstAspect
        Matrix.translateM(m, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(m, 0, sx, sy, 1f)
        Matrix.translateM(m, 0, -0.5f, -0.5f, 0f)
        return m
    }
}
