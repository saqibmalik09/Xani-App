package app.xani.feature.inbox

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
import app.xani.core.lens.LensCaps
import app.xani.core.lens.LensController
import app.xani.core.lens.LensEngine
import app.xani.core.media.ImageLoader
import app.xani.core.net.ContactSync
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal fun captureDir(context: Context): File =
    File(context.cacheDir, "captures").apply { mkdirs() }

internal fun newCaptureUri(context: Context, suffix: String = ".jpg"): Uri {
    val file = File.createTempFile("cap_", suffix, captureDir(context))
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

internal fun newVoiceFile(context: Context): File =
    File(captureDir(context), "voice_${System.nanoTime()}.m4a")

internal fun copyShareUri(context: Context, raw: String, video: Boolean): Uri? {
    val src = Uri.parse(raw)
    val ext = if (video) ".mp4" else ".jpg"
    val out = File(captureDir(context), "share_${System.nanoTime()}$ext")
    val copied = runCatching {
        context.contentResolver.openInputStream(src)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: run {
            val file = File(src.path ?: raw)
            if (file.exists()) file.copyTo(out, overwrite = true) else null
        }
        out.exists() && out.length() > 0L
    }.getOrDefault(false)
    if (copied) return FileProvider.getUriForFile(context, "${context.packageName}.files", out)
    return when (src.scheme) {
        "content" -> src
        "file" -> {
            val file = File(src.path ?: return null)
            if (!file.exists()) null else FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        else -> {
            val file = File(raw)
            if (!file.exists()) null else FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
    }
}

internal fun saveSharedMedia(context: Context, raw: String, video: Boolean): Boolean {
    val src = copyShareUri(context, raw, video) ?: return false
    val name = "chat_${System.nanoTime()}${if (video) ".mp4" else ".jpg"}"
    val values = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (video) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_PICTURES,
            )
        }
    }
    val table = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val dest = context.contentResolver.insert(table, values) ?: return false
    return runCatching {
        context.contentResolver.openInputStream(src)?.use { input ->
            context.contentResolver.openOutputStream(dest)?.use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)
}

class VoiceCapture(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start(): Boolean = runFeature(false) {
        val out = newVoiceFile(context)
        val rec = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(96_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(out.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        file = out
        true
    }

    fun stop(): File? {
        val out = file
        runFeature(Unit) {
            recorder?.apply {
                stop()
                release()
            }
        }
        recorder = null
        file = null
        return out
    }

    fun cancel() {
        stop()?.delete()
    }
}

/**
 * What kind of message a picked file becomes.
 *
 * The visual picker returns pictures and clips through one callback, and the
 * server validates the bytes against the kind it is told, so the kind has to
 * come from the file rather than from which button was pressed.
 */
fun attachmentKind(context: Context, uri: Uri): MsgKind {
    val mime = runFeature("") { context.contentResolver.getType(uri).orEmpty() }
    return when {
        mime.startsWith("video/") -> MsgKind.Video
        mime.startsWith("image/") -> MsgKind.Image
        mime.startsWith("audio/") -> MsgKind.Voice
        // Unknown type: the extension is the only hint left, and guessing
        // "picture" on a clip is the one guess the server always rejects.
        uri.toString().substringAfterLast('.', "").lowercase(Locale.US) in videoExts -> MsgKind.Video
        else -> MsgKind.Image
    }
}

private val videoExts = setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv", "avi")

data class CaptureLaunchers(
    val takePhoto: () -> Unit,
    val pickMedia: () -> Unit,
    val pickDocument: () -> Unit,
    val requestCamera: (onGranted: () -> Unit) -> Unit,
    val requestMic: (onGranted: () -> Unit) -> Unit,
)

@Composable
fun rememberCaptureLaunchers(
    onPhoto: (Uri) -> Unit,
    onMedia: (Uri) -> Unit,
    onDocument: (Uri) -> Unit,
): CaptureLaunchers {
    val context = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var afterCamera by remember { mutableStateOf<(() -> Unit)?>(null) }
    var afterMic by remember { mutableStateOf<(() -> Unit)?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (ok && uri != null) onPhoto(uri)
    }
    val pickVisual = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onMedia(uri)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runFeature(Unit) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onDocument(uri)
        }
    }
    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = afterCamera
        afterCamera = null
        if (granted) next?.invoke()
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = afterMic
        afterMic = null
        if (granted) next?.invoke()
    }

    return remember(takePicture, pickVisual, openDoc, cameraPerm, micPerm) {
        CaptureLaunchers(
            takePhoto = {
                val uri = newCaptureUri(context)
                pendingPhoto = uri
                takePicture.launch(uri)
            },
            pickMedia = {
                pickVisual.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                    ),
                )
            },
            pickDocument = { openDoc.launch(arrayOf("*/*")) },
            requestCamera = { granted ->
                val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (ok) granted() else {
                    afterCamera = granted
                    cameraPerm.launch(Manifest.permission.CAMERA)
                }
            },
            requestMic = { granted ->
                val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (ok) granted() else {
                    afterMic = granted
                    micPerm.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    }
}

@Composable
fun LocalThumb(
    uri: String?,
    modifier: Modifier = Modifier,
    fallback: Color = Color(0xFF8B5CF6),
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 480,
) {
    val context = LocalContext.current
    val key = if (uri.isNullOrBlank()) "" else "$uri#$maxEdge"
    var bitmap by remember(key) { mutableStateOf(if (key.isEmpty()) null else ThumbMem.get(key)) }
    LaunchedEffect(key) {
        if (key.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        ThumbMem.get(key)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            // Attachments that came over the wire are https URLs; the shared
            // image loader has the disk cache and the same bounded decode.
            if (uri!!.startsWith("http")) ImageLoader.load(uri, maxEdge)
            else decodeSampled(context, Uri.parse(uri), maxEdge)
        }
        if (decoded != null) {
            ThumbMem.put(key, decoded)
            bitmap = decoded
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            painter = BitmapPainter(bmp.asImageBitmap()),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier.background(fallback.copy(alpha = 0.35f)))
    }
}

private object ThumbMem {
    private val lru = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(key: String): Bitmap? = synchronized(this) { lru.get(key) }

    fun put(key: String, bmp: Bitmap) {
        synchronized(this) { lru.put(key, bmp) }
    }
}

private fun decodeSampled(context: Context, uri: Uri, maxEdge: Int): Bitmap? = runFeature(null) {
    // MediaStore items (photos AND videos) get a frame from the provider's own
    // thumbnail path: it is the only decoder that understands a video URI, and it
    // reuses the system thumbnail cache, so a 4-column grid does not decode
    // 160 full-size originals.
    if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
        runFeature<Bitmap?>(null) {
            context.contentResolver.loadThumbnail(uri, android.util.Size(maxEdge, maxEdge), null)
        }?.let { return@runFeature it }
    }
    if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width.coerceAtLeast(1)
            val h = info.size.height.coerceAtLeast(1)
            val sample = maxOf(1, maxOf(w, h) / maxEdge)
            decoder.setTargetSampleSize(sample)
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxEdge)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}

/**
 * Recording, as the camera owner implements it. A preview is not enough for
 * the create studio: the shutter has to produce a real clip with sound.
 */
internal interface VideoRecorder {
    val recording: Boolean

    /** Writes an MP4 into [file]. False when the camera could not take it. */
    fun startVideo(file: File, withAudio: Boolean): Boolean

    /** Stops and finalises the clip; false when it is unusable (too short). */
    fun stopVideo(): Boolean
}

class TextureHolder {
    @Volatile var view: TextureView? = null

    /** The open camera, while one is bound to this holder's TextureView. */
    @Volatile internal var camera: VideoRecorder? = null

    /** GPU surface grab — never decode frames on the UI thread. */
    fun snapshot(): android.graphics.Bitmap? {
        val tv = view ?: return null
        return runFeature(null) {
            if (!tv.isAvailable) return@runFeature null
            tv.bitmap
        }
    }

    internal val recording: Boolean get() = camera?.recording == true

    /** Blocking (MediaRecorder.prepare touches disk): call off the main thread. */
    internal fun startVideo(file: File, withAudio: Boolean): Boolean =
        camera?.startVideo(file, withAudio) == true

    internal fun stopVideo(): Boolean = camera?.stopVideo() == true
}

internal fun mediaReadPerms(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun hasMediaRead(context: Context): Boolean =
    mediaReadPerms().any {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

internal fun loadRecentUris(context: Context, limit: Int = 80): List<Uri> = runFeature(emptyList()) {
    val out = ArrayList<Uri>(limit)
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Images.Media._ID),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var n = 0
        while (c.moveToNext() && n < limit) {
            out.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
            n += 1
        }
    }
    out
}

/**
 * Camera2 preview for the video-call surface and in-chat camera. Open/close on a dedicated
 * handler thread; a failure paints the fallback color instead of crashing chat.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    front: Boolean = true,
    enabled: Boolean = true,
    fallback: Color = Color(0xFF1A1A1A),
    holder: TextureHolder? = null,
    /** Xani Lens effects for this viewfinder; null previews the sensor directly. */
    lens: LensController? = null,
) {
    val context = LocalContext.current
    val session = remember { Camera2Session() }
    // Keyed on nothing on purpose. Compose runs a node's update block before it
    // disposes effects whose keys changed, so keying this on front/lens closed
    // the session that `update` had just reopened and left the viewfinder black
    // for good. A camera/lens switch is re-keyed inside open() instead.
    DisposableEffect(Unit) {
        onDispose {
            if (holder != null && holder.camera === session) holder.camera = null
            session.close()
        }
    }
    if (!enabled) {
        // Free the sensor while the viewfinder is off. Entering this branch is
        // the signal, not an effect key, so it cannot race the reopen when the
        // viewfinder comes back.
        DisposableEffect(Unit) {
            session.close()
            onDispose {}
        }
        Box(modifier.background(fallback))
        return
    }
    // A flip gets its own TextureView. Handing the next camera a SurfaceTexture
    // whose previous producer — the old camera or the old EGL window — has not
    // let go yet is refused silently, and the view then sits on the last frame
    // it was given: the "switch sticks" freeze. Tearing the view down with the
    // camera guarantees the new one starts on a texture nobody owns.
    key(front) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureView(ctx).apply {
                    holder?.view = this
                    holder?.camera = session
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            session.open(context, this@apply, front, lens)
                            if (w < 32 || h < 32) post { session.open(context, this@apply, front, lens) }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            session.open(context, this@apply, front, lens)
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            session.close()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            update = { view ->
                holder?.view = view
                holder?.camera = session
                if (enabled && view.isAvailable) session.open(context, view, front, lens)
            },
        )
    }
}

private class Camera2Session : VideoRecorder {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var capture: android.hardware.camera2.CameraCaptureSession? = null
    private var openedKey: String? = null
    private var previewW = 1
    private var previewH = 1
    private var facingFront = false
    private var displayRot = Surface.ROTATION_0

    // Recording state. The preview surface is kept because a clip is a second
    // session over the same one: stopping puts the viewfinder straight back.
    private var appContext: Context? = null
    private var previewSurface: Surface? = null
    private var sensorOrientation = 90
    private var videoSize = Size(1280, 720)

    // Tuning picked from the device's own capabilities at open(). Applying a
    // mode the HAL does not list can throw on some chips, so each is vetted.
    private var afMode: Int? = null
    private var aeFpsRange: Range<Int>? = null
    private var ois = false
    private var noiseHq = false
    private var noiseFast = false

    // Xani Lens: camera renders into the engine's input; the engine paints the
    // TextureView (and the encoder). Null = raw sensor, identity blit skipped.
    private var lensEngine: LensEngine? = null

    // Bumped on every open()/close(). Camera2 cannot cancel an in-flight
    // openCamera; with the lens engine in the path the open is slow enough
    // that the composable can close this session (live room handed the sensor
    // to the encoder) before onOpened lands. A stale onOpened must release
    // the device immediately — keeping it evicts the encoder's capturer and
    // the broadcast goes black while the two clients fight over the sensor.
    @Volatile
    private var generation = 0

    @Volatile
    private var recorder: MediaRecorder? = null

    @Volatile
    override var recording: Boolean = false
        private set

    /** Set by the first completed capture; the black-viewfinder watchdog reads it. */
    @Volatile
    private var framed = false

    fun open(context: Context, view: TextureView, front: Boolean, lens: LensController? = null) {
        val mgr = context.getSystemService(CameraManager::class.java) ?: return
        val id = pick(mgr, front) ?: return
        val vw = view.width.coerceAtLeast(1)
        val vh = view.height.coerceAtLeast(1)
        if (vw < 32 || vh < 32) {
            android.util.Log.i("XaniCam", "open skipped: view ${vw}x$vh not laid out")
            return
        }
        val size = chooseSize(mgr, id, vw, vh)
        val rot = view.display?.rotation ?: Surface.ROTATION_0
        val key = "$id-${size.width}x${size.height}-$front-$rot-${if (lens != null) "L" else "R"}"
        if (openedKey == key && device != null) {
            relayout(view)
            return
        }
        if (recording && device != null) {
            // A clip is being written: reconfiguring the sensor would finalise it
            // mid-shot. The new camera or filter applies to the next take.
            android.util.Log.i("XaniCam", "open deferred: recording")
            relayout(view)
            return
        }
        close()
        openedKey = key
        previewW = size.width
        previewH = size.height
        facingFront = front
        displayRot = rot
        appContext = context.applicationContext
        sensorOrientation = runFeature(90) {
            mgr.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        }
        runFeature(Unit) {
            val chars = mgr.getCameraCharacteristics(id)
            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            afMode = when {
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO in afModes -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in afModes -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                CameraMetadata.CONTROL_AF_MODE_AUTO in afModes -> CameraMetadata.CONTROL_AF_MODE_AUTO
                else -> null
            }
            // Fastest range the sensor lists, and AE may halve it in the dark
            // but no further: a range like 10..24 lets the HAL run the
            // viewfinder at 10 fps in ordinary indoor light, which reads as lag.
            aeFpsRange = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.filter { it.upper >= 24 }
                ?.takeIf { it.isNotEmpty() }
                ?.let { ranges ->
                    val top = ranges.maxOf { it.upper }
                    val fast = ranges.filter { it.upper == top }
                    fast.filter { it.lower * 2 >= top }.minByOrNull { it.lower }
                        ?: fast.maxByOrNull { it.lower }
                }
            ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
            val nr = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            noiseHq = nr?.contains(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true
            noiseFast = nr?.contains(CameraMetadata.NOISE_REDUCTION_MODE_FAST) == true
        }
        videoSize = chooseVideoSize(mgr, id, size)
        val ht = HandlerThread("chat-cam").also { it.start() }
        thread = ht
        val h = Handler(ht.looper)
        handler = h
        val texture = view.surfaceTexture
        if (texture == null) {
            android.util.Log.i("XaniCam", "open skipped: no surface texture yet")
            return
        }
        val camSurface = bindPreview(context, view, texture, size, vw, vh, rot, lens) ?: return
        previewSurface = camSurface
        android.util.Log.i(
            "XaniCam",
            "open id=$id front=$front view=${vw}x$vh buf=${size.width}x${size.height} " +
                "sensor=$sensorOrientation rot=$rot video=$videoSize lens=${lensEngine != null} " +
                "af=$afMode fps=$aeFpsRange ois=$ois",
        )
        val gen = generation
        runFeature(Unit) {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (gen != generation) {
                        // This session was closed while the HAL was opening:
                        // someone else (the live encoder) owns the sensor now.
                        runFeature(Unit) { camera.close() }
                        return
                    }
                    device = camera
                    startPreview(camera, camSurface, h)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    runFeature(Unit) { camera.close() }
                    if (gen == generation) close()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    runFeature(Unit) { camera.close() }
                    if (gen == generation) close()
                }
            }, h)
        }
    }

    /**
     * Point the camera at either the TextureView (raw) or the lens engine
     * input. The engine already cover-crops, so the view's transform is identity
     * when filters are in the path — applying both is the 2x–7x zoom bug.
     */
    private fun bindPreview(
        context: Context,
        view: TextureView,
        texture: SurfaceTexture,
        size: Size,
        vw: Int,
        vh: Int,
        rot: Int,
        lens: LensController?,
    ): Surface? {
        if (lens != null) {
            val caps = LensCaps.forDevice(DeviceClass.read(context.applicationContext))
            lens.caps = caps
            val engine = LensEngine(context.applicationContext, lens, caps)
            val input = engine.start(size.width, size.height, sensorOrientation, facingFront, rot)
            if (input != null && !engine.failed && engine.attachPreview(texture, vw, vh)) {
                view.setTransform(Matrix())
                lensEngine = engine
                videoSize = engine.recordSize(vw, vh)
                return input
            }
            android.util.Log.w("XaniCam", "lens engine unavailable, raw preview")
            engine.release()
        }
        texture.setDefaultBufferSize(size.width, size.height)
        view.setTransform(
            previewTransform(vw, vh, size.width, size.height, rot, sensorOrientation, facingFront),
        )
        return Surface(texture)
    }

    /**
     * Brings the viewfinder up, and keeps a promise: a vendor HAL that accepts
     * our tuned request and then delivers nothing (no shutter, no buffers) gets
     * one more chance with the bare template. The user sees a late first frame
     * instead of a screen that stays black.
     */
    private fun startPreview(camera: CameraDevice, surface: Surface, h: Handler, tuned: Boolean = true) {
        runFeature(Unit) {
            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                if (tuned) tune(this, record = false)
            }
            val gen = generation
            framed = false
            camera.createCaptureSession(listOf(surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                    if (gen != generation) {
                        runFeature(Unit) { session.close() }
                        return
                    }
                    capture = session
                    runFeature(Unit) { session.setRepeatingRequest(req.build(), frameMark, h) }
                    if (!tuned) return
                    h.postDelayed({
                        if (gen != generation || framed || device !== camera) return@postDelayed
                        android.util.Log.w("XaniCam", "no frames from tuned request, retrying bare")
                        runFeature(Unit) { capture?.close() }
                        capture = null
                        startPreview(camera, surface, h, tuned = false)
                    }, FrameWaitMs)
                }
                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                    android.util.Log.w("XaniCam", "capture session config failed tuned=$tuned")
                    if (gen == generation && tuned) startPreview(camera, surface, h, tuned = false)
                }
            }, h)
        }
    }

    private val frameMark = object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: android.hardware.camera2.CameraCaptureSession,
            request: CaptureRequest,
            result: android.hardware.camera2.TotalCaptureResult,
        ) {
            framed = true
        }
    }

    /**
     * 3A + image pipeline: continuous autofocus, auto exposure with a wide
     * fps range (slower shutter in the dark → brighter), auto white balance.
     * Each key set alone so one unsupported value never loses the rest.
     *
     * FOV guards: EIS is forced OFF (it crops the frame — reads as "zoomed"
     * and the HAL turns it on by itself under TEMPLATE_RECORD), the scaler
     * crop is pinned to the full sensor, and zoom ratio to 1.0. OIS covers
     * shake instead; it shifts the lens and costs no pixels.
     */
    private fun tune(req: CaptureRequest.Builder, record: Boolean) {
        runFeature(Unit) { req.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO) }
        afMode?.let { runFeature(Unit) { req.set(CaptureRequest.CONTROL_AF_MODE, it) } }
        runFeature(Unit) { req.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON) }
        runFeature(Unit) { req.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO) }
        runFeature(Unit) {
            req.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
        }
        aeFpsRange?.let { runFeature(Unit) { req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) } }
        runFeature(Unit) {
            req.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }
        // No SCALER_CROP_REGION. The template already covers the full sensor,
        // and the crop rect is measured from the active array's own origin —
        // on sensors whose array starts at (8,8) the full-array rect runs 8px
        // past the edge, and the HAL answers an out-of-range crop by accepting
        // every request and returning no buffers at all: a black viewfinder.
        // Zoom ratio states the same intent and is bounds-free.
        if (Build.VERSION.SDK_INT >= 30) {
            runFeature(Unit) { req.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.0f) }
        }
        if (ois) {
            runFeature(Unit) {
                req.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }
        }
        // HQ denoise smears a live viewfinder on mid chips; it is worth the
        // latency only on the encoded clip.
        val nrMode = when {
            record && noiseHq -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            noiseFast -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
            else -> null
        }
        nrMode?.let { runFeature(Unit) { req.set(CaptureRequest.NOISE_REDUCTION_MODE, it) } }
    }

    /**
     * Starts writing a clip. The encoder surface joins the preview in one new
     * session, so the viewfinder never blanks while recording.
     */
    override fun startVideo(file: File, withAudio: Boolean): Boolean {
        if (recording || recorder != null) return false
        val camera = device ?: return false
        val h = handler ?: return false
        val preview = previewSurface ?: return false
        val ctx = appContext ?: return false
        val rec = buildRecorder(ctx, file, withAudio) ?: return false
        recorder = rec
        val engine = lensEngine
        if (engine != null) {
            engine.setRecord(rec.surface, videoSize.width, videoSize.height)
            val ok = runFeature(false) { rec.start(); true }
            if (ok) recording = true else {
                engine.setRecord(null, 0, 0)
                dropRecorder()
            }
            return ok
        }
        val target = rec.surface
        val started = runFeature(false) {
            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(preview)
                addTarget(target)
                tune(this, record = true)
            }
            camera.createCaptureSession(
                listOf(preview, target),
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        capture = session
                        runFeature(Unit) { session.setRepeatingRequest(req.build(), null, h) }
                        // Frames only count from here; starting the encoder
                        // earlier writes a clip with a stalled first second.
                        if (runFeature(false) { rec.start(); true }) recording = true else dropRecorder()
                    }
                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        dropRecorder()
                        startPreview(camera, preview, h)
                    }
                },
                h,
            )
            true
        }
        if (!started) dropRecorder()
        return started
    }

    override fun stopVideo(): Boolean {
        val rec = recorder ?: return false
        val wasRecording = recording
        recording = false
        recorder = null
        lensEngine?.setRecord(null, 0, 0)
        // stop() throws when the encoder never got a frame (a tap-length
        // "recording"); the file is then unusable and the caller is told.
        val ok = runFeature(false) { rec.stop(); true } && wasRecording
        runFeature(Unit) { rec.reset() }
        runFeature(Unit) { rec.release() }
        val camera = device
        val preview = previewSurface
        val h = handler
        if (lensEngine == null && camera != null && preview != null && h != null) startPreview(camera, preview, h)
        return ok
    }

    private fun dropRecorder() {
        val rec = recorder ?: return
        recorder = null
        recording = false
        lensEngine?.setRecord(null, 0, 0)
        runFeature(Unit) { rec.reset() }
        runFeature(Unit) { rec.release() }
    }

    private fun buildRecorder(context: Context, file: File, withAudio: Boolean): MediaRecorder? = runFeature(null) {
        val rec = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        if (withAudio) rec.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setOutputFile(file.absolutePath)
        rec.setVideoSize(videoSize.width, videoSize.height)
        rec.setVideoFrameRate(30)
        rec.setVideoEncodingBitRate(bitrateFor(videoSize))
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (withAudio) {
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
        }
        rec.setOrientationHint(orientationHint())
        rec.prepare()
        rec
    }

    /** Upright playback everywhere: the sensor's rotation against the screen's. */
    private fun orientationHint(): Int {
        val degrees = when (displayRot) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (facingFront) {
            (sensorOrientation + degrees) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
    }

    fun relayout(view: TextureView) {
        val vw = view.width.coerceAtLeast(1)
        val vh = view.height.coerceAtLeast(1)
        val rot = view.display?.rotation ?: displayRot
        displayRot = rot
        val engine = lensEngine
        if (engine != null) {
            view.setTransform(Matrix())
            view.surfaceTexture?.let { engine.resizePreview(it, vw, vh) }
            return
        }
        view.setTransform(
            previewTransform(vw, vh, previewW, previewH, rot, sensorOrientation, facingFront),
        )
    }

    /**
     * Never blocks the caller: CameraDevice.close() waits for the HAL, which
     * can take seconds when another client (the live encoder) is opening the
     * same sensor. That wait belongs on the camera thread, not on Main.
     */
    fun close() {
        generation++
        openedKey = null
        // A clip in flight is finalised rather than abandoned: the file is
        // already on disk and the encoder holds a surface we are about to
        // drop. The preview is cleared first so stopping does not rebuild a
        // session we are in the middle of tearing down.
        previewSurface = null
        if (recorder != null) {
            if (recording) stopVideo() else dropRecorder()
        }
        val engine = lensEngine
        lensEngine = null
        if (engine != null) {
            // The preview detaches synchronously: the next open() binds a new
            // EGL surface to the same TextureView texture, and EGL refuses a
            // window that still has a producer — that refusal was a silent,
            // permanent black viewfinder. The slow parts (encoder, context)
            // still leave on their own thread.
            runFeature(Unit) { engine.detachPreview() }
            Thread({
                runFeature(Unit) { engine.setRecord(null, 0, 0) }
                engine.release()
            }, "lens-close").start()
        }
        val cap = capture
        val dev = device
        val ht = thread
        val h = handler
        capture = null
        device = null
        thread = null
        handler = null
        val release = Runnable {
            runFeature(Unit) { cap?.close() }
            runFeature(Unit) { dev?.close() }
            ht?.quitSafely()
        }
        if (h != null && ht?.isAlive == true && Looper.myLooper() != ht.looper) {
            if (!h.post(release)) release.run()
        } else if (Looper.myLooper() == Looper.getMainLooper() && (cap != null || dev != null)) {
            Thread(release, "chat-cam-close").start()
        } else {
            release.run()
        }
    }

    private fun pick(mgr: CameraManager, front: Boolean): String? = runFeature(null) {
        val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        mgr.cameraIdList.firstOrNull { id ->
            mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == want
        } ?: mgr.cameraIdList.firstOrNull()
    }

    private fun chooseSize(mgr: CameraManager, id: String, viewW: Int, viewH: Int): Size {
        val chars = mgr.getCameraCharacteristics(id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(viewW, viewH)
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return Size(viewW, viewH)
        val sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        // Upright aspect the view needs after sensor rotation (portrait phone → ~9:16 buffer on screen).
        val viewAspect = viewW.toFloat() / viewH.coerceAtLeast(1)
        val wantLong = maxOf(viewW, viewH).coerceIn(1080, 1920)
        // Prefer a buffer whose upright aspect matches the view, then the sharpest
        // size near the view's long edge — never settle on the first 720p match.
        fun uprightAspect(s: Size): Float {
            val swap = sensor % 180 != 0
            val w = if (swap) s.height else s.width
            val h = if (swap) s.width else s.height
            return w.toFloat() / h.coerceAtLeast(1)
        }
        val wide = sizes.filter {
            val long = maxOf(it.width, it.height)
            long in 720..1920
        }
        // A buffer the view has to upscale is soft on screen and in the clip, and
        // some sensors offer odd 2:1 crops (800x400) whose aspect looks like a
        // perfect match. Demand 720 lines first; fall back only if none exist.
        val candidates = wide.filter { minOf(it.width, it.height) >= 720 }.ifEmpty { wide }
        return candidates.maxWithOrNull(
            compareBy<Size> {
                // Closer aspect wins first (less crop → less "zoomed" FILL).
                -kotlin.math.abs(uprightAspect(it) - viewAspect)
            }.thenBy {
                // Then prefer near the view's pixel demand.
                -kotlin.math.abs(maxOf(it.width, it.height) - wantLong)
            }.thenBy {
                it.width.toLong() * it.height
            },
        ) ?: sizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(viewW, viewH)
    }

    /**
     * The encoder takes its own set of sizes, not the preview's. Pick the one
     * closest to the preview's shape at 1080p or below — above that a mid
     * device drops frames and the upload is pointlessly large.
     */
    private fun chooseVideoSize(mgr: CameraManager, id: String, preview: Size): Size = runFeature(preview) {
        val map = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return@runFeature preview
        val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: return@runFeature preview
        val target = preview.width.toFloat() / preview.height.coerceAtLeast(1)
        sizes
            .filter { maxOf(it.width, it.height) in 720..1920 }
            .maxWithOrNull(
                compareBy(
                    { -kotlin.math.abs(it.width.toFloat() / it.height.coerceAtLeast(1) - target) },
                    { it.width.toLong() * it.height },
                ),
            )
            ?: sizes.first()
    }

    private companion object {
        /** Long enough for a cold HAL to deliver its first frame, short enough to not read as a hang. */
        const val FrameWaitMs = 1500L
    }
}

private fun bitrateFor(size: Size): Int {
    val pixels = size.width.toLong() * size.height
    return (pixels * 4L).coerceIn(2_000_000L, 12_000_000L).toInt()
}

/**
 * Camera2 → TextureView mapping.
 *
 * TextureView has ALREADY stretched the (auto-rotated) camera stream to the
 * view bounds before this matrix runs. The old code multiplied a buffer-pixel
 * "cover" scale on top of that implicit stretch — scales compounded and the
 * viewfinder looked 2x–7x zoomed. The fix: divide out the implicit per-axis
 * stretch and reapply one uniform cover scale. Result: real 1x FOV, square
 * pixels (no stretch), and only the thin aspect mismatch is cropped.
 */
private fun previewTransform(
    viewW: Int,
    viewH: Int,
    bufW: Int,
    bufH: Int,
    displayRotation: Int,
    sensorOrientation: Int,
    front: Boolean,
): Matrix {
    val matrix = Matrix()
    if (viewW <= 0 || viewH <= 0 || bufW <= 0 || bufH <= 0) return matrix
    val cx = viewW / 2f
    val cy = viewH / 2f

    // Upright content size in the device's natural orientation — the stream
    // transform has already rotated the sensor buffer for display.
    val swap = sensorOrientation % 180 != 0
    val uprightW = (if (swap) bufH else bufW).toFloat()
    val uprightH = (if (swap) bufW else bufH).toFloat()

    when (displayRotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> {
            // Display rotated against natural orientation (Camera2Basic):
            // map the view onto the swapped buffer, cover, counter-rotate.
            val viewRect = android.graphics.RectF(0f, 0f, viewW.toFloat(), viewH.toFloat())
            val bufferRect = android.graphics.RectF(0f, 0f, uprightH, uprightW)
            bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(viewH / uprightW, viewW / uprightH)
            matrix.postScale(scale, scale, cx, cy)
            matrix.postRotate(if (displayRotation == Surface.ROTATION_90) 270f else 90f, cx, cy)
        }
        else -> {
            // Natural orientation (and 180): undo the implicit stretch, then
            // one uniform scale to cover. stretch == cover on the long axis,
            // so that axis stays 1:1 — true field of view.
            val stretchX = viewW / uprightW
            val stretchY = viewH / uprightH
            val cover = maxOf(stretchX, stretchY)
            matrix.setScale(cover / stretchX, cover / stretchY, cx, cy)
            if (displayRotation == Surface.ROTATION_180) matrix.postRotate(180f, cx, cy)
        }
    }
    if (front) matrix.postScale(-1f, 1f, cx, cy)
    return matrix
}

internal fun lastKnownFix(context: Context): String? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    val loc = listOf(
        android.location.LocationManager.GPS_PROVIDER,
        android.location.LocationManager.NETWORK_PROVIDER,
        android.location.LocationManager.PASSIVE_PROVIDER,
    ).firstNotNullOfOrNull { p ->
        runFeature(null) {
            if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
        }
    }
    return loc?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
}

internal fun openHttp(context: Context, url: String) {
    runFeature(Unit) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

internal fun openMaps(context: Context, text: String) {
    val raw = text.removePrefix("📍 ").trim()
    val q = if (raw.startsWith("http://") || raw.startsWith("https://")) raw
    else "https://maps.google.com/?q=${Uri.encode(raw.ifBlank { "Location" })}"
    runFeature(Unit) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(q))) }
}

internal fun openCalendar(context: Context, ev: EventPayload) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, ev.name)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.at)
        putExtra(CalendarContract.Events.EVENT_LOCATION, ev.loc)
        putExtra(CalendarContract.Events.DESCRIPTION, ev.desc)
    }
    runFeature(Unit) { context.startActivity(intent) }
}

@Composable
fun ChatCameraSheet(
    copy: Copy,
    onClose: () -> Unit,
    onShot: (Uri) -> Unit,
    onGallery: () -> Unit,
    onPicked: (List<Uri>) -> Unit = { list -> list.firstOrNull()?.let(onShot) },
) {
    val context = LocalContext.current
    val holder = remember { TextureHolder() }
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var allowed by remember { mutableStateOf(hasMediaRead(context)) }
    var lift by remember { mutableFloatStateOf(0f) }
    val picked = remember { mutableStateListOf<Uri>() }
    val mediaPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        allowed = granted.values.any { it }
        if (allowed) {
            scope.launch {
                recents = withContext(Dispatchers.IO) { loadRecentUris(context) }
            }
        }
    }
    LaunchedEffect(allowed) {
        if (allowed && recents.isEmpty()) recents = withContext(Dispatchers.IO) { loadRecentUris(context) }
    }
    fun snap() {
        val view = holder.view
        if (view == null || !view.isAvailable) return
        view.post {
            val bmp = view.bitmap ?: return@post
            scope.launch(Dispatchers.IO) {
                val uri = newCaptureUri(context)
                val ok = runFeature(false) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    } != null
                }
                bmp.recycle()
                if (ok) withContext(Dispatchers.Main) { onShot(uri) }
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            front = front,
            enabled = true,
            holder = holder,
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(rdp(12))
                .size(rdp(40))
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .pressable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            ChatIco.Close(Color.White, rdp(16))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .navigationBarsPadding()
                .padding(bottom = rdp(16)),
        ) {
            if (recents.isNotEmpty()) {
                val open = lift > 0.12f
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (open) Modifier.fillMaxHeight(0.72f) else Modifier)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, amount ->
                                lift = (lift - amount / 900f).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = rdp(6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(rdp(28))
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                                .pressable { lift = if (open) 0f else 1f },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.graphicsLayer { rotationZ = if (open) 90f else -90f }) {
                                ChatIco.Chevron(Color.White, rdp(14))
                            }
                        }
                    }
                    if (open) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f).padding(horizontal = rdp(8)),
                            contentPadding = PaddingValues(rdp(4)),
                            horizontalArrangement = Arrangement.spacedBy(rdp(4)),
                            verticalArrangement = Arrangement.spacedBy(rdp(4)),
                        ) {
                            items(recents, key = { it.toString() }, contentType = { "thumb" }) { uri ->
                                val on = uri in picked
                                Box {
                                    LocalThumb(
                                        uri = uri.toString(),
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(rdp(6)))
                                            .pressable {
                                                if (on) picked.remove(uri) else picked.add(uri)
                                            },
                                        fallback = Color(0xFF333333),
                                        maxEdge = 160,
                                    )
                                    if (on) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(rdp(4))
                                                .size(rdp(18))
                                                .clip(CircleShape)
                                                .background(Dark.Primary),
                                            contentAlignment = Alignment.Center,
                                        ) { ChatIco.Tick(Color.White, rdp(10)) }
                                    }
                                }
                            }
                        }
                        if (picked.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.End)
                                    .padding(rdp(12))
                                    .clip(RoundedCornerShape(rdp(18)))
                                    .background(Dark.Primary)
                                    .pressable { onPicked(picked.toList()) }
                                    .padding(horizontal = rdp(16), vertical = rdp(8)),
                            ) {
                                XaniText(copy.t(Copy.InboxSendBtn), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = rdp(12)),
                            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                        ) {
                            items(recents.take(12), key = { it.toString() }, contentType = { "thumb" }) { uri ->
                                val on = uri in picked
                                Box {
                                    LocalThumb(
                                        uri = uri.toString(),
                                        modifier = Modifier
                                            .size(rdp(56))
                                            .clip(RoundedCornerShape(rdp(8)))
                                            .pressable {
                                                if (on) picked.remove(uri) else picked.add(uri)
                                            },
                                        fallback = Color(0xFF333333),
                                        maxEdge = 160,
                                    )
                                    if (on) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(rdp(3))
                                                .size(rdp(16))
                                                .clip(CircleShape)
                                                .background(Dark.Primary),
                                            contentAlignment = Alignment.Center,
                                        ) { ChatIco.Tick(Color.White, rdp(9)) }
                                    }
                                }
                            }
                        }
                        if (picked.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.End)
                                    .padding(end = rdp(12), top = rdp(8))
                                    .clip(RoundedCornerShape(rdp(18)))
                                    .background(Dark.Primary)
                                    .pressable { onPicked(picked.toList()) }
                                    .padding(horizontal = rdp(16), vertical = rdp(8)),
                            ) {
                                XaniText(copy.t(Copy.InboxSendBtn), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                            }
                        }
                    }
                }
            } else if (!allowed) {
                Row(
                    Modifier
                        .padding(horizontal = rdp(16), vertical = rdp(8))
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(Color.White.copy(alpha = 0.16f))
                        .pressable { mediaPerm.launch(mediaReadPerms()) }
                        .padding(horizontal = rdp(14), vertical = rdp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatIco.AttachGallery()
                    Spacer(Modifier.width(rdp(8)))
                    XaniText(copy.t(Copy.InboxAllowPhotos), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(28), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(rdp(52))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .pressable(onClick = onGallery),
                    contentAlignment = Alignment.Center,
                ) {
                    ChatIco.AttachGallery()
                }
                Box(
                    Modifier
                        .size(rdp(76))
                        .clip(CircleShape)
                        .background(Color.White)
                        .pressable(onClick = { snap() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(rdp(64))
                            .clip(CircleShape)
                            .background(Color.White)
                            .then(Modifier),
                    )
                    Box(
                        Modifier
                            .size(rdp(66))
                            .clip(CircleShape)
                            .background(Color.Transparent),
                    )
                    Box(
                        Modifier
                            .size(rdp(64))
                            .clip(CircleShape)
                            .background(Color(0xFFF3F4F6)),
                    )
                }
                Box(
                    Modifier
                        .size(rdp(52))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .pressable { front = !front },
                    contentAlignment = Alignment.Center,
                ) {
                    ChatIco.Camera(Color.White, rdp(22))
                }
            }
        }
    }
}

data class DeviceContact(
    val id: Long,
    val name: String,
    val number: String,
    val photo: String?,
)

internal fun hasContactsRead(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

internal fun loadDeviceContacts(context: Context): List<DeviceContact> = runFeature(emptyList()) {
    val out = ArrayList<DeviceContact>(64)
    val seen = HashSet<String>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
    )?.use { c ->
        val idCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photoCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
        while (c.moveToNext()) {
            val name = c.getString(nameCol)?.trim().orEmpty()
            val number = c.getString(numCol)?.trim().orEmpty()
            if (name.isEmpty() && number.isEmpty()) continue
            val key = name.lowercase() + "|" + number.filter { it.isDigit() }
            if (!seen.add(key)) continue
            out.add(
                DeviceContact(
                    id = c.getLong(idCol),
                    name = name.ifBlank { number },
                    number = number,
                    photo = c.getString(photoCol),
                ),
            )
        }
    }
    out
}

@Composable
fun DeviceGallerySheet(
    copy: Copy,
    onClose: () -> Unit,
    onPick: (Uri) -> Unit,
    confirmPick: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var allowed by remember { mutableStateOf(hasMediaRead(context)) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    val mediaPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        allowed = granted.values.any { it }
        if (allowed) scope.launch { uris = withContext(Dispatchers.IO) { loadRecentUris(context, 80) } }
    }
    LaunchedEffect(allowed) {
        if (allowed) uris = withContext(Dispatchers.IO) { loadRecentUris(context, 120) }
        else mediaPerm.launch(mediaReadPerms())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(36)).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                ChatIco.Close(Color(0xFF111111), rdp(16))
            }
            XaniText(copy.t(Copy.InboxGallery), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 16f, modifier = Modifier.weight(1f))
            if (confirmPick && selected != null) {
                Box(
                    Modifier
                        .size(rdp(36))
                        .clip(CircleShape)
                        .background(Dark.Primary)
                        .pressable { selected?.let(onPick) },
                    contentAlignment = Alignment.Center,
                ) { ChatIco.Tick(Color.White, rdp(14)) }
            }
        }
        if (!allowed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(Color(0xFFF3E8FF))
                        .pressable { mediaPerm.launch(mediaReadPerms()) }
                        .padding(horizontal = rdp(16), vertical = rdp(10)),
                ) {
                    XaniText(copy.t(Copy.InboxAllowPhotos), Type.LabelBold, color = Dark.Primary)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(rdp(4)),
                horizontalArrangement = Arrangement.spacedBy(rdp(4)),
                verticalArrangement = Arrangement.spacedBy(rdp(4)),
            ) {
                items(uris, key = { it.toString() }, contentType = { "thumb" }) { uri ->
                    val on = selected == uri
                    Box {
                        LocalThumb(
                            uri = uri.toString(),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(rdp(6)))
                                .pressable {
                                    if (confirmPick) selected = uri else onPick(uri)
                                },
                            fallback = Color(0xFFE5E7EB),
                            maxEdge = 180,
                        )
                        if (confirmPick && on) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(rdp(6))
                                    .size(rdp(22))
                                    .clip(CircleShape)
                                    .background(Dark.Primary),
                                contentAlignment = Alignment.Center,
                            ) { ChatIco.Tick(Color.White, rdp(12)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactPickSheet(
    copy: Copy,
    onClose: () -> Unit,
    onPick: (List<DeviceContact>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var allowed by remember { mutableStateOf(hasContactsRead(context)) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    val contactPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        if (ok) {
            // The contacts permission is the real consent gate: once it is
            // granted here, the full address book syncs to the server in the
            // background for friend matching, whether or not Find friends is on.
            ContactSync.syncNow()
            scope.launch { people = withContext(Dispatchers.IO) { loadDeviceContacts(context) } }
        }
    }
    LaunchedEffect(allowed) {
        if (allowed) people = withContext(Dispatchers.IO) { loadDeviceContacts(context) }
        else contactPerm.launch(Manifest.permission.READ_CONTACTS)
    }
    val shown = remember(people, query) {
        if (query.isBlank()) people
        else people.filter { it.name.contains(query, true) || it.number.contains(query, true) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(36)).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                ChatIco.Close(Color(0xFF111111), rdp(16))
            }
            if (searching) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    modifier = Modifier.weight(1f).padding(horizontal = rdp(8)),
                    textStyle = Type.Label.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Dark.Primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) XaniText(copy.t(Copy.InboxSearchShort), Type.Label, color = Color(0xFF9CA3AF))
                            inner()
                        }
                    },
                )
            } else {
                XaniText(copy.t(Copy.InboxContacts), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 16f, modifier = Modifier.weight(1f))
            }
            Box(Modifier.size(rdp(36)).pressable { searching = !searching }, contentAlignment = Alignment.Center) {
                ChatIco.Search(Color(0xFF555555), rdp(18))
            }
            if (picked.isNotEmpty()) {
                Box(
                    Modifier
                        .size(rdp(36))
                        .clip(CircleShape)
                        .background(Dark.Primary)
                        .pressable { onPick(people.filter { it.id in picked }) },
                    contentAlignment = Alignment.Center,
                ) { ChatIco.Tick(Color.White, rdp(14)) }
            }
        }
        if (!allowed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(Color(0xFFF3E8FF))
                        .pressable { contactPerm.launch(Manifest.permission.READ_CONTACTS) }
                        .padding(horizontal = rdp(16), vertical = rdp(10)),
                ) {
                    XaniText(copy.t(Copy.InboxAllowContacts), Type.LabelBold, color = Dark.Primary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(16))) {
                items(shown, key = { it.id.toString() + it.number }) { c ->
                    val on = c.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable {
                                if (on) picked.remove(c.id) else picked.add(c.id)
                            }
                            .padding(horizontal = rdp(16), vertical = rdp(8)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(rdp(44)).clip(CircleShape).background(Dark.Primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c.photo != null) {
                                LocalThumb(c.photo, Modifier.fillMaxSize(), fallback = Dark.Primary)
                            } else {
                                XaniText(c.name.take(1).uppercase(), Type.LabelBold, color = Dark.Primary, fontSizeOverride = 16f)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = rdp(12))) {
                            XaniText(c.name, Type.LabelStrong, color = Color(0xFF111111), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (c.number.isNotBlank()) {
                                XaniText(c.number, Type.Caption, color = Color(0xFF888888), maxLines = 1)
                            }
                        }
                        if (on) {
                            Box(
                                Modifier.size(rdp(22)).clip(CircleShape).background(Dark.Primary),
                                contentAlignment = Alignment.Center,
                            ) { ChatIco.Tick(Color.White, rdp(12)) }
                        }
                    }
                }
            }
        }
    }
}

