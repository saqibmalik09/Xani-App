package app.xani.feature.upload

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.xani.feature.inbox.LocalThumb
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

private val Accent = Color(0xFF7C3AED)
private val Pink = Color(0xFFEC4899)
private val Good = Color(0xFF10B981)
private val Danger = Color(0xFFDC2626)

/**
 * The "Posting… / Posted" strip. Lives in the main shell so it rides along
 * wherever the user goes; one row per task, newest at the bottom. A Posted row
 * removes itself; a failed row offers retry and cancel.
 */
@Composable
fun PostingStrip(copy: Copy, modifier: Modifier = Modifier) {
    val tasks by UploadManager.tasks.collectAsState()
    AnimatedVisibility(
        visible = tasks.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rdp(12), vertical = rdp(6)),
            verticalArrangement = Arrangement.spacedBy(rdp(6)),
        ) {
            for (t in tasks.takeLast(3)) {
                key(t.id) { StripRow(copy, t) }
            }
        }
    }
}

@Composable
private fun StripRow(copy: Copy, t: UploadTask) {
    val progress by animateFloatAsState(t.progress, tween(300), label = "upload")
    val label = when (t.state) {
        UploadState.Queued -> copy.t(Copy.UploadQueued)
        UploadState.Uploading -> copy.t(Copy.UploadPostingPct, "pct" to (progress * 100).toInt().toString())
        UploadState.Completing, UploadState.Processing -> copy.t(Copy.UploadProcessing)
        UploadState.Posted -> copy.t(Copy.UploadPosted)
        UploadState.Failed -> copy.t(Copy.UploadFailed)
    }
    val tint = when (t.state) {
        UploadState.Posted -> Good
        UploadState.Failed -> Danger
        else -> Accent
    }

    Row(
        Modifier
            .fillMaxWidth()
            .shadow(rdp(6), RoundedCornerShape(rdp(14)), clip = false, ambientColor = Color.Black.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color.White)
            .padding(rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
    ) {
        Box(Modifier.size(rdp(40)).clip(RoundedCornerShape(rdp(8))).background(Color(0xFFF1F1F6))) {
            // The source file is still on the device; decode a tiny sample of it.
            LocalThumb(uri = t.uri, modifier = Modifier.fillMaxSize(), maxEdge = 160)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(rdp(5))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                if (t.state == UploadState.Posted) Tick(tint)
                XaniText(
                    label, Type.LabelBold, color = if (t.state == UploadState.Failed) Danger else Color(0xFF111111),
                    fontSizeOverride = 13f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (t.state == UploadState.Failed) {
                XaniText(t.error, Type.Caption, color = Color(0xFF7A7A8C), maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (t.state != UploadState.Posted) {
                Bar(progress = progress, indeterminate = t.state == UploadState.Processing || t.state == UploadState.Completing, tint = tint)
            } else if (t.form.caption.isNotBlank()) {
                XaniText(t.form.caption, Type.Caption, color = Color(0xFF7A7A8C), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (t.state) {
            UploadState.Failed -> {
                Pill(copy.t(Copy.UploadRetry), Accent) { UploadManager.retry(t.id) }
                Pill("×", Color(0xFF7A7A8C)) { UploadManager.cancel(t.id) }
            }
            UploadState.Posted -> Pill("×", Color(0xFF7A7A8C)) { UploadManager.dismiss(t.id) }
            UploadState.Processing -> Unit
            else -> Pill("×", Color(0xFF7A7A8C)) { UploadManager.cancel(t.id) }
        }
    }
}

@Composable
private fun Bar(progress: Float, indeterminate: Boolean, tint: Color) {
    val sweep by animateFloatAsState(if (indeterminate) 1f else 0f, tween(600), label = "sweep")
    Canvas(Modifier.fillMaxWidth().height(rdp(4))) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color(0xFFEDEDF3), cornerRadius = r)
        val w = if (indeterminate) size.width else size.width * progress
        drawRoundRect(
            Brush.horizontalGradient(listOf(tint, Pink.copy(alpha = 0.6f + 0.4f * sweep))),
            size = Size(w, size.height), topLeft = Offset.Zero, cornerRadius = r,
        )
    }
}

@Composable
private fun Tick(color: Color) {
    Box(Modifier.size(rdp(16)).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        XaniText("✓", Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
    }
}

@Composable
private fun Pill(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(rdp(30))
            .clip(RoundedCornerShape(rdp(10)))
            .background(color.copy(alpha = 0.1f))
            .pressable(pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = rdp(10)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(label, Type.LabelBold, color = color, fontSizeOverride = 12f)
    }
}
