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
import app.xani.core.isolate.runFeature
import app.xani.core.media.ImageLoader
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
) {
    val context = LocalContext.current
    val session = remember { Camera2Session() }
    DisposableEffect(enabled, front) {
        onDispose {
            if (holder != null && holder.camera === session) holder.camera = null
            session.close()
        }
    }
    if (!enabled) {
        Box(modifier.background(fallback))
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                holder?.view = this
                holder?.camera = session
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        session.open(context, this@apply, front)
                        if (w < 32 || h < 32) post { session.open(context, this@apply, front) }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        session.open(context, this@apply, front)
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
            if (enabled && view.isAvailable) session.open(context, view, front)
        },
    )
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

    @Volatile
    private var recorder: MediaRecorder? = null

    @Volatile
    override var recording: Boolean = false
        private set

    fun open(context: Context, view: TextureView, front: Boolean) {
        val mgr = context.getSystemService(CameraManager::class.java) ?: return
        val id = pick(mgr, front) ?: return
        val vw = view.width.coerceAtLeast(1)
        val vh = view.height.coerceAtLeast(1)
        if (vw < 32 || vh < 32) return
        val size = chooseSize(mgr, id, vw, vh)
        val rot = view.display?.rotation ?: Surface.ROTATION_0
        val key = "$id-${size.width}x${size.height}-$front-$rot"
        if (openedKey == key && device != null) {
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
        videoSize = chooseVideoSize(mgr, id, size)
        val ht = HandlerThread("chat-cam").also { it.start() }
        thread = ht
        val h = Handler(ht.looper)
        handler = h
        val texture = view.surfaceTexture ?: return
        texture.setDefaultBufferSize(size.width, size.height)
        view.setTransform(fillCenter(vw, vh, size.width, size.height, rot, front))
        val surface = Surface(texture)
        previewSurface = surface
        runFeature(Unit) {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    startPreview(camera, surface, h)
                }
                override fun onDisconnected(camera: CameraDevice) { close() }
                override fun onError(camera: CameraDevice, error: Int) { close() }
            }, h)
        }
    }

    private fun startPreview(camera: CameraDevice, surface: Surface, h: Handler) {
        runFeature(Unit) {
            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
            camera.createCaptureSession(listOf(surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                    capture = session
                    runFeature(Unit) { session.setRepeatingRequest(req.build(), null, h) }
                }
                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) = Unit
            }, h)
        }
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
        val target = rec.surface
        val started = runFeature(false) {
            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(preview)
                addTarget(target)
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
        // stop() throws when the encoder never got a frame (a tap-length
        // "recording"); the file is then unusable and the caller is told.
        val ok = runFeature(false) { rec.stop(); true } && wasRecording
        runFeature(Unit) { rec.reset() }
        runFeature(Unit) { rec.release() }
        val camera = device
        val preview = previewSurface
        val h = handler
        if (camera != null && preview != null && h != null) startPreview(camera, preview, h)
        return ok
    }

    private fun dropRecorder() {
        val rec = recorder ?: return
        recorder = null
        recording = false
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
        view.setTransform(fillCenter(vw, vh, previewW, previewH, rot, facingFront))
    }

    /**
     * Never blocks the caller: CameraDevice.close() waits for the HAL, which
     * can take seconds when another client (the live encoder) is opening the
     * same sensor. That wait belongs on the camera thread, not on Main.
     */
    fun close() {
        openedKey = null
        // A clip in flight is finalised rather than abandoned: the file is
        // already on disk and the encoder holds a surface we are about to
        // drop. The preview is cleared first so stopping does not rebuild a
        // session we are in the middle of tearing down.
        previewSurface = null
        if (recorder != null) {
            if (recording) stopVideo() else dropRecorder()
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
        val map = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return Size(viewW, viewH)
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return Size(viewW, viewH)
        val target = maxOf(viewW, viewH).toFloat() / minOf(viewW, viewH).coerceAtLeast(1)
        return sizes
            .filter { maxOf(it.width, it.height) in 720..1920 }
            .minByOrNull {
                val br = maxOf(it.width, it.height).toFloat() / minOf(it.width, it.height).coerceAtLeast(1)
                kotlin.math.abs(br - target)
            }
            ?: sizes.first()
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
            .filter { maxOf(it.width, it.height) <= 1920 }
            .maxWithOrNull(
                compareBy(
                    { -kotlin.math.abs(it.width.toFloat() / it.height.coerceAtLeast(1) - target) },
                    { it.width.toLong() * it.height },
                ),
            )
            ?: sizes.first()
    }
}

private fun bitrateFor(size: Size): Int {
    val pixels = size.width.toLong() * size.height
    return (pixels * 4L).coerceIn(2_000_000L, 12_000_000L).toInt()
}

private fun fillCenter(viewW: Int, viewH: Int, bufW: Int, bufH: Int, rotation: Int, front: Boolean): Matrix {
    val matrix = Matrix()
    if (viewW <= 0 || viewH <= 0 || bufW <= 0 || bufH <= 0) return matrix
    val swap = viewH > viewW && bufW > bufH
    val srcW = if (swap) bufH.toFloat() else bufW.toFloat()
    val srcH = if (swap) bufW.toFloat() else bufH.toFloat()
    val scale = maxOf(viewW / srcW, viewH / srcH)
    matrix.setScale(scale, scale)
    matrix.postTranslate((viewW - srcW * scale) * 0.5f, (viewH - srcH * scale) * 0.5f)
    if (front) matrix.postScale(-1f, 1f, viewW / 2f, viewH / 2f)
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
        if (ok) scope.launch { people = withContext(Dispatchers.IO) { loadDeviceContacts(context) } }
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

