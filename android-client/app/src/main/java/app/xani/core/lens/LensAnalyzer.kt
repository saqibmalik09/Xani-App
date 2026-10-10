package app.xani.core.lens

import android.graphics.Bitmap
import android.graphics.PointF
import app.xani.core.isolate.runFeature
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * One tracked face in frame coordinates: u right, v up, both 0..1.
 * Everything the shaders and the sticker pass need, nothing more.
 * [id] is ML Kit's tracking id, stable across frames for the same face, so
 * the render-rate smoother can match measurements to what it is showing.
 */
internal class FaceInfo(
    val id: Int,
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val eyeL: PointF,
    val eyeR: PointF,
    val nose: PointF,
    val mouth: PointF,
    val chin: PointF,
    val forehead: PointF,
) {
    fun lerp(o: FaceInfo, t: Float): FaceInfo = FaceInfo(
        o.id,
        cx + (o.cx - cx) * t, cy + (o.cy - cy) * t, rx + (o.rx - rx) * t, ry + (o.ry - ry) * t,
        eyeL.lerp(o.eyeL, t), eyeR.lerp(o.eyeR, t), nose.lerp(o.nose, t),
        mouth.lerp(o.mouth, t), chin.lerp(o.chin, t), forehead.lerp(o.forehead, t),
    )

    /** The whole face translated: how the smoother applies velocity prediction. */
    fun shift(dx: Float, dy: Float): FaceInfo = FaceInfo(
        id, cx + dx, cy + dy, rx, ry,
        PointF(eyeL.x + dx, eyeL.y + dy), PointF(eyeR.x + dx, eyeR.y + dy),
        PointF(nose.x + dx, nose.y + dy), PointF(mouth.x + dx, mouth.y + dy),
        PointF(chin.x + dx, chin.y + dy), PointF(forehead.x + dx, forehead.y + dy),
    )

    private fun PointF.lerp(o: PointF, t: Float) = PointF(x + (o.x - x) * t, y + (o.y - y) * t)
}

/** Result of one analysis round. `seg` is a row-major 8-bit person mask, row 0 at the TOP. */
internal class Analysis(
    val faces: List<FaceInfo>,
    val seg: ByteArray?,
    val segW: Int,
    val segH: Int,
    val serial: Long,
)

/**
 * ML Kit face contours + selfie segmentation on a small RGBA readback of the
 * frame. Bundled models, no download, works offline. Strictly one round in
 * flight: when the chip is slow the frame rate stays, the mask just updates
 * less often. Results are published through a volatile and picked up by the
 * GL thread on its next frame — no cross-thread GL.
 */
internal class LensAnalyzer {
    @Volatile var latest: Analysis? = null
        private set

    private val busy = AtomicBoolean(false)
    private val serial = AtomicInteger(0)
    private val executor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "lens-ml").apply { isDaemon = true } }
    private var faceDetector: FaceDetector? = null
    private var segmenter: Segmenter? = null
    @Volatile private var released = false

    val isBusy: Boolean get() = busy.get()

    /**
     * Analyse [bitmap] (upright, row 0 top). The bitmap is read until the
     * callbacks complete — the caller must not reuse it while [isBusy].
     */
    fun submit(bitmap: Bitmap, faces: Boolean, seg: Boolean): Boolean {
        if (released || (!faces && !seg)) return false
        if (!busy.compareAndSet(false, true)) return false
        val image = runFeature(null) { InputImage.fromBitmap(bitmap, 0) }
        if (image == null) {
            busy.set(false)
            return false
        }
        val pending = AtomicInteger((if (faces) 1 else 0) + (if (seg) 1 else 0))
        val id = serial.incrementAndGet().toLong()
        var faceOut: List<FaceInfo> = emptyList()
        var segOut: ByteArray? = null
        var segW = 0
        var segH = 0
        val w = bitmap.width
        val h = bitmap.height
        fun done() {
            if (pending.decrementAndGet() == 0) {
                // Raw measurements out. Smoothing happens at render rate in the
                // pipeline, where it can also predict motion — doing it here at
                // analysis rate only adds a second stage of lag.
                latest = Analysis(faceOut, segOut, segW, segH, id)
                busy.set(false)
            }
        }
        if (faces) {
            val det = faceDetector ?: buildFaces().also { faceDetector = it }
            det.process(image)
                .addOnSuccessListener(executor) { list ->
                    faceOut = runFeature(emptyList()) { list.take(3).mapIndexed { i, f -> toInfo(f, i, w, h) } }
                    done()
                }
                .addOnFailureListener(executor) { done() }
        }
        if (seg) {
            val s = segmenter ?: buildSegmenter().also { segmenter = it }
            s.process(image)
                .addOnSuccessListener(executor) { mask ->
                    runFeature(Unit) {
                        val bytes = toBytes(mask)
                        segOut = bytes
                        segW = mask.width
                        segH = mask.height
                    }
                    done()
                }
                .addOnFailureListener(executor) { done() }
        }
        return true
    }

    fun release() {
        released = true
        runFeature(Unit) { faceDetector?.close() }
        runFeature(Unit) { segmenter?.close() }
        faceDetector = null
        segmenter = null
    }

    private fun buildFaces(): FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .enableTracking()
            .build(),
    )

    private fun buildSegmenter(): Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build(),
    )

    private fun toInfo(face: Face, index: Int, w: Int, h: Int): FaceInfo {
        fun n(p: PointF): PointF = PointF(p.x / w, 1f - p.y / h)
        val box = face.boundingBox
        val oval = face.getContour(FaceContour.FACE)?.points
        val forehead = oval?.getOrNull(0)?.let(::n) ?: PointF(box.exactCenterX() / w, 1f - box.top.toFloat() / h)
        val chin = oval?.getOrNull(18)?.let(::n) ?: PointF(box.exactCenterX() / w, 1f - box.bottom.toFloat() / h)
        val eyeL = (face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: centroid(face.getContour(FaceContour.LEFT_EYE)?.points))
            ?.let(::n) ?: PointF(box.left + box.width() * 0.32f, box.top + box.height() * 0.4f).let(::n)
        val eyeR = (face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: centroid(face.getContour(FaceContour.RIGHT_EYE)?.points))
            ?.let(::n) ?: PointF(box.left + box.width() * 0.68f, box.top + box.height() * 0.4f).let(::n)
        val nose = (face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: centroid(face.getContour(FaceContour.NOSE_BOTTOM)?.points))
            ?.let(::n) ?: PointF(box.exactCenterX(), box.top + box.height() * 0.6f).let(::n)
        val mouth = (face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: centroid(face.getContour(FaceContour.UPPER_LIP_TOP)?.points))
            ?.let(::n) ?: PointF(box.exactCenterX(), box.top + box.height() * 0.78f).let(::n)
        return FaceInfo(
            id = face.trackingId ?: (-1 - index),
            cx = box.exactCenterX() / w,
            cy = 1f - box.exactCenterY() / h,
            rx = box.width() * 0.52f / w,
            ry = box.height() * 0.56f / h,
            eyeL = eyeL,
            eyeR = eyeR,
            nose = nose,
            mouth = mouth,
            chin = chin,
            forehead = forehead,
        )
    }

    private fun centroid(points: List<PointF>?): PointF? {
        if (points.isNullOrEmpty()) return null
        var x = 0f
        var y = 0f
        for (p in points) {
            x += p.x
            y += p.y
        }
        return PointF(x / points.size, y / points.size)
    }

    private fun toBytes(mask: SegmentationMask): ByteArray {
        val buf = mask.buffer
        buf.rewind()
        val n = mask.width * mask.height
        val out = ByteArray(n)
        for (i in 0 until n) {
            val f = buf.getFloat()
            out[i] = (f * 255f).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }
}
