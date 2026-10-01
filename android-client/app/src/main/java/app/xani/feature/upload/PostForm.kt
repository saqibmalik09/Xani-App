package app.xani.feature.upload

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.xani.core.isolate.runFeature
import app.xani.core.net.MediaConfig
import app.xani.core.net.PostForm
import app.xani.feature.inbox.LocalThumb
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val Ink = Color(0xFF111111)
private val Sub = Color(0xFF7A7A8C)
private val Field = Color(0xFFF4F4F8)
private val Line = Color(0xFFE9E9F0)
private val Accent = Color(0xFF7C3AED)
private val Pink = Color(0xFFEC4899)
private val Danger = Color(0xFFDC2626)

/**
 * The details screen between capture and upload. Reads the file's facts off
 * the main thread while the user types; Post hands the work to
 * [UploadManager] and returns immediately — the strip takes it from there.
 */
@Composable
fun PostFormScreen(
    copy: Copy,
    uri: Uri,
    onClose: () -> Unit,
    onPosted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf("public") }
    var comments by rememberSaveable { mutableStateOf(true) }
    var duet by rememberSaveable { mutableStateOf(true) }
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var problem by remember { mutableStateOf<MediaProblem?>(null) }
    var probed by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }

    LaunchedEffect(Unit) { slide.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
    LaunchedEffect(uri) {
        val p = withContext(Dispatchers.IO) { MediaProbe.probe(context, uri) }
        info = p
        problem = if (p == null) MediaProblem.Unreadable else MediaProbe.check(p)
        probed = true
    }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            slide.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler(enabled = !closing) { dismiss() }

    val maxCaption = MediaConfig.maxCaption
    val canPost = probed && problem == null && info != null && caption.length <= maxCaption

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = size.width * slide.value }
            .background(Color.White)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(40)).clip(CircleShape).background(Field).pressable { dismiss() },
                contentAlignment = Alignment.Center,
            ) { XaniText("‹", Type.H2, color = Ink, fontSizeOverride = 24f) }
            Spacer(Modifier.width(rdp(12)))
            XaniText(copy.t(Copy.UploadFormTitle), Type.LabelBold, color = Ink, fontSizeOverride = 17f)
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = rdp(16)),
            verticalArrangement = Arrangement.spacedBy(rdp(16)),
        ) {
            // Cover + caption
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rdp(12))) {
                Box(
                    Modifier
                        .width(rdp(96))
                        .height(rdp(160))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Field),
                ) {
                    LocalThumb(uri = uri.toString(), modifier = Modifier.fillMaxSize(), maxEdge = 480)
                    info?.takeIf { it.isVideo && it.durationMs > 0 }?.let { i ->
                        XaniText(
                            clock(i.durationMs),
                            Type.Caption,
                            color = Color.White,
                            fontSizeOverride = 11f,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(rdp(6))
                                .clip(RoundedCornerShape(rdp(6)))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = rdp(6), vertical = rdp(2)),
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(160))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Field)
                        .padding(rdp(12)),
                ) {
                    if (caption.isEmpty()) {
                        XaniText(copy.t(Copy.UploadCaptionHint), Type.Body, color = Sub, fontSizeOverride = 15f)
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = { if (it.length <= maxCaption + 50) caption = it },
                        textStyle = Type.Body.copy(color = Ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.fillMaxSize(),
                    )
                    XaniText(
                        "${caption.length}/$maxCaption",
                        Type.Caption,
                        color = if (caption.length > maxCaption) Danger else Sub,
                        fontSizeOverride = 11f,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }

            // Facts about the file, or the reason it cannot be posted.
            DetailsCard(copy, info, problem, probed)

            // Audience
            Column(verticalArrangement = Arrangement.spacedBy(rdp(8))) {
                XaniText(copy.t(Copy.UploadWhoCanSee), Type.LabelBold, color = Ink, fontSizeOverride = 14f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                    listOf(
                        "public" to Copy.UploadPublic,
                        "friends" to Copy.UploadFriends,
                        "private" to Copy.UploadPrivate,
                    ).forEach { (value, key) ->
                        val on = visibility == value
                        Box(
                            Modifier
                                .weight(1f)
                                .height(rdp(40))
                                .clip(RoundedCornerShape(rdp(10)))
                                .background(if (on) Accent.copy(alpha = 0.1f) else Field)
                                .border(rdp(1.5f), if (on) Accent else Color.Transparent, RoundedCornerShape(rdp(10)))
                                .pressable(pressedScale = 0.98f) { visibility = value },
                            contentAlignment = Alignment.Center,
                        ) {
                            XaniText(copy.t(key), if (on) Type.LabelBold else Type.Label, color = if (on) Accent else Ink, fontSizeOverride = 13f)
                        }
                    }
                }
            }

            ToggleRow(copy.t(Copy.UploadAllowComments), comments) { comments = it }
            ToggleRow(copy.t(Copy.UploadAllowDuet), duet) { duet = it }
            Spacer(Modifier.height(rdp(8)))
        }

        // Post
        Box(Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(12))) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(52))
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(
                        if (canPost) Brush.horizontalGradient(listOf(Accent, Pink))
                        else Brush.horizontalGradient(listOf(Line, Line)),
                    )
                    .pressable(enabled = canPost && !posting, pressedScale = 0.98f) {
                        val i = info ?: return@pressable
                        posting = true
                        // Queueing must never take the app down with it: on a bad
                        // handoff the form stays put and the user can press again.
                        val queued = runFeature(false) {
                            UploadManager.enqueue(
                                i,
                                PostForm(caption = caption.trim(), visibility = visibility, allowComments = comments, allowDuet = duet),
                            )
                            true
                        }
                        if (queued) onPosted() else posting = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                XaniText(
                    if (probed) copy.t(Copy.UploadPostBtn) else copy.t(Copy.UploadReading),
                    Type.LabelBold,
                    color = if (canPost) Color.White else Sub,
                    fontSizeOverride = 16f,
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(copy: Copy, info: MediaInfo?, problem: MediaProblem?, probed: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(12)))
            .background(Field)
            .padding(rdp(12)),
        verticalArrangement = Arrangement.spacedBy(rdp(6)),
    ) {
        XaniText(copy.t(Copy.UploadDetails), Type.LabelBold, color = Ink, fontSizeOverride = 13f)
        if (!probed) {
            XaniText(copy.t(Copy.UploadReading), Type.Caption, color = Sub)
            return@Column
        }
        problem?.let {
            XaniText(problemText(copy, it), Type.Label, color = Danger, fontSizeOverride = 13f)
        }
        info?.let { i ->
            if (i.isVideo) Fact(copy.t(Copy.UploadDuration), clock(i.durationMs))
            Fact(copy.t(Copy.UploadSize), humanBytes(i.size))
            Fact(copy.t(Copy.UploadResolution), "${i.width} × ${i.height}")
            i.capturedAt?.let { Fact(copy.t(Copy.UploadTaken), localDate(it)) }
            if (!i.isPortrait916 && problem == null) {
                XaniText(copy.t(Copy.UploadNot916), Type.Caption, color = Sub)
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        XaniText(label, Type.Caption, color = Sub, modifier = Modifier.width(rdp(92)))
        XaniText(value, Type.Caption, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(rdp(44)).pressable(pressedScale = 1f) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(label, Type.Label, color = Ink, fontSizeOverride = 14f, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(rdp(44))
                .height(rdp(26))
                .clip(CircleShape)
                .background(if (on) Accent else Line)
                .padding(rdp(3)),
        ) {
            Box(
                Modifier
                    .size(rdp(20))
                    .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private fun problemText(copy: Copy, p: MediaProblem): String = when (p) {
    MediaProblem.TooLong -> copy.t(Copy.UploadErrTooLong, "min" to (MediaConfig.maxVideoSeconds / 60).toString())
    MediaProblem.TooLarge -> copy.t(Copy.UploadErrTooLarge, "max" to (MediaConfig.maxVideoBytes shr 20).toString())
    MediaProblem.Unsupported -> copy.t(Copy.UploadErrUnsupported)
    MediaProblem.Unreadable -> copy.t(Copy.UploadErrUnreadable)
    MediaProblem.BadAspect -> copy.t(Copy.UploadErrAspect)
}

internal fun clock(ms: Int): String {
    val s = ms / 1000
    val m = s / 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s % 60) else "%d:%02d".format(m, s % 60)
}

internal fun humanBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%d KB".format(b shr 10)
    else -> "$b B"
}

private fun localDate(rfc3339: String): String = runCatching {
    val inF = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val d = inF.parse(rfc3339) ?: return rfc3339
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(d)
}.getOrDefault(rfc3339)
