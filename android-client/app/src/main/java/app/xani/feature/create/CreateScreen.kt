package app.xani.feature.create

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.xani.core.isolate.runFeature
import app.xani.feature.inbox.CameraPreview
import app.xani.feature.inbox.LocalThumb
import app.xani.feature.inbox.TextureHolder
import app.xani.feature.inbox.captureDir
import app.xani.feature.upload.PostFormScreen
import app.xani.feature.upload.TextPoster
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal enum class CreateLane { Post, Live }

/** Figma modes: 5m · 60s · 15s · PHOTO (Text opens its own screen) */
internal enum class ShootMode { Fifteen, Sixty, FiveMin, Photo }

/**
 * TikTok-class Create. Camera binds only while Post is active and visible.
 * Recording ticks stay in leaf chrome — never invalidate the TextureView tree.
 * Live is a separate lane so Post camera stays warm until Live mounts.
 *
 * This pane stays composed inside AlivePane after close, so every activation
 * must reset `closing` and replay the slide-in — otherwise reopen shows the
 * slid-out frame forever (black screen, dead back button).
 */
@Composable
fun CreateScreen(
    copy: Copy,
    active: Boolean,
    onClose: () -> Unit,
) {
    var lane by rememberSaveable { mutableStateOf(CreateLane.Post) }
    var closing by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        closing = false
        slide.snapTo(1f)
        slide.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
    }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            slide.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            onClose()
            // Fresh start on next open: Live flow unmounts, timers stop, lane is Post.
            lane = CreateLane.Post
        }
    }

    BackHandler(enabled = active && !closing) {
        if (lane == CreateLane.Live) lane = CreateLane.Post else dismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * slide.value
                alpha = 0.9f + 0.1f * (1f - slide.value)
            },
    ) {
        if (lane == CreateLane.Live) {
            CreateLiveFlow(
                copy = copy,
                active = active && !closing,
                onPost = { lane = CreateLane.Post },
            )
        } else {
            PostStudio(
                copy = copy,
                active = active && !closing,
                onClose = ::dismiss,
                onLive = { lane = CreateLane.Live },
            )
        }
    }
}

@Composable
private fun PostStudio(
    copy: Copy,
    active: Boolean,
    onClose: () -> Unit,
    onLive: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { TextureHolder() }
    var front by rememberSaveable { mutableStateOf(false) }
    var flash by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ShootMode.Fifteen) }
    var textPost by rememberSaveable { mutableStateOf(false) }
    var camOk by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var timerSec by rememberSaveable { mutableIntStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<Uri?>(null) }
    /** A picked or captured file waiting for its caption. Non-null shows the post form. */
    var composing by remember { mutableStateOf<Uri?>(null) }
    /** The user has watched the preview and tapped Next; the form is up. */
    var previewed by remember { mutableStateOf(false) }
    LaunchedEffect(composing) { previewed = false }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var gallery by remember { mutableStateOf(false) }
    val cameraNeed = active && !gallery && !textPost && composing == null
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        camOk = granted
    }
    LaunchedEffect(cameraNeed) {
        if (!cameraNeed) return@LaunchedEffect
        val ok = context.checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (ok) camOk = true else camPerm.launch(Manifest.permission.CAMERA)
    }
    // One gallery thumb load when Create becomes active — never on every frame.
    LaunchedEffect(active) {
        if (!active || thumb != null) return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) { lastGallery(context) }
    }
    val capSec = when (mode) {
        ShootMode.FiveMin -> 300
        ShootMode.Sixty -> 60
        ShootMode.Fifteen -> 15
        else -> 0
    }
    /** Where the take in progress is being written. */
    var clipFile by remember { mutableStateOf<File?>(null) }
    /** Set while the clip is being finalised, so a second tap cannot race it. */
    var closingClip by remember { mutableStateOf(false) }
    /** Why the last take produced nothing, shown over the viewfinder. */
    var captureError by remember { mutableStateOf("") }
    LaunchedEffect(captureError) {
        if (captureError.isEmpty()) return@LaunchedEffect
        delay(2200)
        captureError = ""
    }

    fun stopClip() {
        if (closingClip) return
        closingClip = true
        recording = false
        val file = clipFile
        clipFile = null
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                val ok = holder.stopVideo()
                if (ok && file != null && file.length() > 0L) clipUri(context, file) else {
                    file?.delete()
                    null
                }
            }
            closingClip = false
            // Straight into preview → Next → the post form, exactly where a
            // gallery pick lands.
            if (uri != null) composing = uri else captureError = copy.t(Copy.CreateClipTooShort)
        }
    }

    // 1 Hz tick — progress ring + clock only; camera surface is a sibling.
    LaunchedEffect(recording, capSec) {
        if (!recording) return@LaunchedEffect
        val start = SystemClock.elapsedRealtime()
        while (recording) {
            delay(1000)
            elapsed = ((SystemClock.elapsedRealtime() - start) / 1000L).toInt()
            // The mode's cap is the take's length: stop it here exactly as a
            // tap would, so the clip still reaches the preview.
            if (capSec > 0 && elapsed >= capSec) stopClip()
        }
    }

    fun startClip() {
        if (recording || closingClip) return
        elapsed = 0
        recording = true
        scope.launch {
            // Denied mic records a silent clip rather than refusing the take.
            val withAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val file = withContext(Dispatchers.IO) {
                val f = newClipFile(context)
                if (holder.startVideo(f, withAudio)) f else { f.delete(); null }
            }
            if (file != null) {
                clipFile = file
            } else {
                recording = false
                captureError = copy.t(Copy.CreateCameraBusy)
            }
        }
    }

    // Asked once, on the first take, and the answer either way starts it.
    var micAsked by rememberSaveable { mutableStateOf(false) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startClip() }

    // Leaving mid-take drops it: the camera is about to be closed under the
    // encoder, and half a clip is not something to hand anyone.
    LaunchedEffect(active) {
        if (active || !recording) return@LaunchedEffect
        recording = false
        val file = clipFile
        clipFile = null
        withContext(Dispatchers.IO) {
            holder.stopVideo()
            file?.delete()
        }
    }

    fun shutter() {
        when {
            mode == ShootMode.Photo -> snap(holder, context, scope) { uri -> composing = uri }
            recording || closingClip -> stopClip()
            timerSec > 0 -> {
                elapsed = 0
                scope.launch {
                    delay(timerSec * 1000L)
                    if (active) startClip()
                }
            }
            !micAsked && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                micAsked = true
                micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> startClip()
        }
    }

    if (textPost) {
        CreateTextPost(
            copy = copy,
            onClose = { textPost = false },
            onPost = { body ->
                CreateSession.remember(CreateDraft(kind = CreateDraftKind.Text, text = body))
                TextPoster.post(body) { ok, message ->
                    Handler(Looper.getMainLooper()).post {
                        if (ok) {
                            textPost = false
                            onClose()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                message.ifBlank { "Couldn't post right now." },
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
        )
        return
    }

    composing?.let { uri ->
        if (!previewed) {
            // Capture / pick → watch it back → Next → details form.
            MediaPreviewScreen(
                copy = copy,
                uri = uri,
                onBack = { composing = null },
                onNext = { previewed = true },
            )
            return
        }
        PostFormScreen(
            copy = copy,
            uri = uri,
            onClose = { previewed = false },
            onPosted = {
                // The strip in the shell owns progress from here; Create goes home.
                composing = null
                previewed = false
                onClose()
            },
        )
        return
    }

    if (gallery) {
        DeviceGallery(
            copy = copy,
            onPick = { uri ->
                thumb = uri
                composing = uri
                gallery = false
            },
            onClose = { gallery = false },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(CreateTokens.Scrim)) {
        // Figma viewfinder: below top chrome, rounded top — fillCenter TextureView stays sharp.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = rdp(56))
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20))),
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                front = front,
                enabled = cameraNeed && camOk,
                holder = holder,
                fallback = Color(0xFF050510),
            )
            if (grid) GridOverlay()
            if (flash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
        }

        // Top bar — glass, Figma placement
        Row(
            Modifier
                .fillMaxWidth()
                .background(CreateTokens.TopBarScrim)
                .statusBarsPadding()
                .padding(horizontal = rdp(16), vertical = rdp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassHit(onClose) { CreateIco.Close(Color.White, rdp(18)) }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(rdp(24)))
                    .background(CreateTokens.ChipFill)
                    .pressable { }
                    .padding(horizontal = rdp(16), vertical = rdp(9)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreateIco.Music(Color.White, rdp(14))
                Spacer(Modifier.width(rdp(7)))
                XaniText(copy.t(Copy.CreateAddSound), Type.Label, color = Color.White, fontSizeOverride = 13f)
            }
            Spacer(Modifier.weight(1f))
            GlassHit({ front = !front }, soft = true) { CreateIco.FlipCam(Color.White, rdp(24)) }
        }

        // Right tools — Flash / Timer / Grid only (Figma); hidden while recording
        if (!recording) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = rdp(108), end = rdp(14)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rdp(22)),
            ) {
                LabeledTool(
                    label = copy.t(if (flash) Copy.CreateFlashOn else Copy.CreateFlashOff),
                    active = flash,
                    onClick = { flash = !flash },
                ) { CreateIco.Flash(Color.White, rdp(28), off = !flash) }
                LabeledTool(
                    label = if (timerSec > 0) "${timerSec}s" else copy.t(Copy.CreateTimer),
                    active = timerSec > 0,
                    onClick = { timerSec = when (timerSec) { 0 -> 3; 3 -> 10; else -> 0 } },
                ) { CreateIco.Timer(Color.White, rdp(28)) }
                LabeledTool(
                    label = copy.t(Copy.CreateGrid),
                    active = grid,
                    onClick = { grid = !grid },
                ) { CreateIco.Grid(if (grid) Color.White else CreateTokens.ToolDim, rdp(28)) }
            }
        }

        // Recording clock — leaf only
        if (recording) {
            RecordClock(
                elapsed = elapsed,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = rdp(108)),
            )
        }
        // A take that produced nothing says so where the user is looking,
        // rather than leaving the studio silently sitting there.
        if (captureError.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(Color(0xCC000000))
                    .padding(horizontal = rdp(16), vertical = rdp(10)),
            ) {
                XaniText(captureError, Type.Label, color = Color.White, fontSizeOverride = 13f)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CreateTokens.BottomFade)
                .navigationBarsPadding()
                .padding(bottom = rdp(8)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!recording) {
                ModeRail(
                    copy = copy,
                    mode = mode,
                    onPick = {
                        mode = it
                        recording = false
                    },
                    onText = { textPost = true },
                )
                Spacer(Modifier.height(rdp(14)))
            }

            // Figma record row: gallery pinned left, capture ring always centered.
            Box(Modifier.fillMaxWidth().padding(horizontal = rdp(28)).height(rdp(92)), contentAlignment = Alignment.Center) {
                if (!recording) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(rdp(56))
                            .clip(CircleShape)
                            .border(rdp(2.5f), CreateTokens.GalleryRing, CircleShape)
                            .background(Color(0xFF333333))
                            .pressable { gallery = true },
                    ) {
                        if (thumb != null) {
                            LocalThumb(uri = thumb.toString(), modifier = Modifier.fillMaxSize(), fallback = Color(0xFF333333), maxEdge = 112)
                        }
                    }
                }

                ShutterButton(
                    recording = recording,
                    progress = if (capSec > 0) (elapsed / capSec.toFloat()).coerceIn(0f, 1f) else 0f,
                    photoMode = mode == ShootMode.Photo,
                    onClick = { shutter() },
                )
            }

            if (!recording) {
                Spacer(Modifier.height(rdp(6)))
                Row(
                    Modifier.fillMaxWidth().padding(top = rdp(6), bottom = rdp(4)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LaneTab(copy.t(Copy.CreatePost), true) { }
                    Spacer(Modifier.width(rdp(36)))
                    LaneTab(copy.t(Copy.CreateGoLive), false, onLive)
                }
            } else {
                Spacer(Modifier.height(rdp(40)))
            }
        }
    }
}

@Composable
private fun RecordClock(elapsed: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(rdp(22)))
            .background(CreateTokens.ChipFill)
            .padding(horizontal = rdp(18), vertical = rdp(6)),
    ) {
        XaniText(formatClock(elapsed), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
    }
}

@Composable
private fun ShutterButton(
    recording: Boolean,
    progress: Float,
    photoMode: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.size(rdp(92)), contentAlignment = Alignment.Center) {
        if (recording) {
            Canvas(Modifier.size(rdp(92))) {
                val stroke = Stroke(width = size.minDimension * 0.038f, cap = StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.2f), radius = size.minDimension * 0.435f, style = stroke)
                drawArc(
                    color = Color.White.copy(alpha = 0.65f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        Box(
            Modifier
                .size(rdp(80))
                .clip(CircleShape)
                .then(
                    if (recording) Modifier.background(CreateTokens.RecordWash)
                    else Modifier.border(if (photoMode) rdp(7) else rdp(6), Color.White, CircleShape),
                )
                .pressable(pressedScale = 0.92f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                recording -> Box(
                    Modifier.size(rdp(22)).clip(RoundedCornerShape(rdp(5))).background(Color.White.copy(alpha = 0.92f)),
                )
                photoMode -> Unit
                else -> Box(Modifier.size(rdp(44)).clip(CircleShape).background(CreateTokens.RecordAccent))
            }
        }
    }
}

@Composable
private fun ModeRail(copy: Copy, mode: ShootMode, onPick: (ShootMode) -> Unit, onText: () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = rdp(12)),
        horizontalArrangement = Arrangement.spacedBy(rdp(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figma order: 5m · 60s · 15s · Photo · Text (Text opens its own screen)
        listOf(ShootMode.FiveMin, ShootMode.Sixty, ShootMode.Fifteen, ShootMode.Photo).forEach { m ->
            val on = mode == m
            val label = when (m) {
                ShootMode.FiveMin -> copy.t(Copy.Create5m)
                ShootMode.Sixty -> copy.t(Copy.Create60s)
                ShootMode.Fifteen -> copy.t(Copy.Create15s)
                ShootMode.Photo -> copy.t(Copy.CreatePhoto)
            }
            ModeChip(label = label, on = on) { onPick(m) }
        }
        ModeChip(label = copy.t(Copy.CreateText), on = false, onClick = onText)
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(if (on) CreateTokens.ModeOnBg else Color.Transparent)
            .pressable(onClick = onClick)
            .padding(horizontal = if (on) rdp(14) else rdp(10), vertical = rdp(5)),
    ) {
        XaniText(
            label,
            if (on) Type.LabelBold else Type.Label,
            color = if (on) CreateTokens.ModeOnFg else CreateTokens.ModeOffFg,
            fontSizeOverride = if (on) 15f else 14f,
        )
    }
}

@Composable
private fun LaneTab(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.pressable(onClick = onClick).padding(horizontal = rdp(8), vertical = rdp(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(
            label,
            if (active) Type.LabelBold else Type.Label,
            color = if (active) CreateTokens.LaneActive else CreateTokens.LaneIdle,
            fontSizeOverride = if (active) 17f else 15f,
        )
        Box(
            Modifier
                .padding(top = rdp(4))
                .width(rdp(28))
                .height(rdp(3))
                .clip(RoundedCornerShape(rdp(2)))
                .background(if (active) Color.White else Color.Transparent),
        )
    }
}

@Composable
private fun LabeledTool(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        Modifier.pressable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rdp(4)),
    ) {
        icon()
        XaniText(
            label,
            Type.Micro,
            color = if (active) Color.White else CreateTokens.ToolMuted,
            fontSizeOverride = 10f,
        )
    }
}

@Composable
private fun GlassHit(onClick: () -> Unit, soft: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(if (soft) rdp(44) else rdp(40))
            .clip(CircleShape)
            .background(if (soft) CreateTokens.GlassSoft else CreateTokens.Glass)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val c = CreateTokens.GridLine
        drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height / 3f), androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), 1f)
        drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), 1f)
        drawLine(c, androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), 1f)
        drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), 1f)
    }
}

private fun formatClock(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}

private fun snap(holder: TextureHolder, context: Context, scope: CoroutineScope, onDone: (Uri) -> Unit) {
    scope.launch(Dispatchers.Default) {
        val bmp = holder.snapshot() ?: return@launch
        val uri = withContext(Dispatchers.IO) {
            runFeature(null as Uri?) {
                val file = File(captureDir(context), "cap_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out) }
                clipUri(context, file)
            }
        }
        if (uri != null) withContext(Dispatchers.Main) { onDone(uri) }
    }
}

private fun newClipFile(context: Context): File =
    File(captureDir(context), "clip_${System.currentTimeMillis()}.mp4")

/**
 * A capture becomes a content:// URI through the app's own provider — the same
 * shape a gallery pick has, so preview, probing and upload take one path.
 */
private fun clipUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

private fun lastGallery(context: Context): Uri? = runFeature(null) {
    val proj = arrayOf(MediaStore.Images.Media._ID)
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        proj,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { c ->
        if (!c.moveToFirst()) return@runFeature null
        val id = c.getLong(0)
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }
}
