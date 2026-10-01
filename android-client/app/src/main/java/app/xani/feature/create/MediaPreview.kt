package app.xani.feature.create

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.xani.core.media.FeedVideo
import app.xani.feature.inbox.LocalThumb
import app.xani.feature.upload.MediaInfo
import app.xani.feature.upload.MediaProbe
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the user just shot or picked, full-screen, before any form: a video plays
 * with sound on a loop, a photo fills the frame at its real aspect. Back returns
 * to the camera / gallery; Next goes on to the caption form. The same media
 * object flows through unchanged, so nothing is re-read.
 */
@Composable
internal fun MediaPreviewScreen(
    copy: Copy,
    uri: Uri,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    var info by remember(uri) { mutableStateOf<MediaInfo?>(null) }
    var muted by remember { mutableStateOf(false) }
    LaunchedEffect(uri) { info = withContext(Dispatchers.IO) { MediaProbe.probe(context, uri) } }
    BackHandler(onBack = onBack)

    val isVideo = info?.isVideo ?: (context.contentResolver.getType(uri)?.startsWith("video/") == true)
    val aspect = info?.aspect?.takeIf { it > 0f } ?: (9f / 16f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Media, letterboxed to its own aspect inside the screen.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // aspectRatio inside loose max constraints = the largest box of this
            // shape that fits the screen: fit, never crop.
            Box(Modifier.aspectRatio(aspect)) {
                // The still paints at once; the video replaces it on its first frame.
                LocalThumb(uri = uri.toString(), modifier = Modifier.fillMaxSize(), maxEdge = 1440, contentScale = ContentScale.Fit, fallback = Color.Black)
                if (isVideo) {
                    FeedVideo(url = uri.toString(), playing = true, muted = muted, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // Top: back · (mute for video)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = rdp(12), vertical = rdp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(36)).clip(CircleShape).background(Color(0x66000000)).pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { XaniText("‹", Type.H2, color = Color.White, fontSizeOverride = 24f) }
            Spacer(Modifier.weight(1f))
            if (isVideo) {
                Box(
                    Modifier.size(rdp(36)).clip(CircleShape).background(Color(0x66000000)).pressable(onClick = { muted = !muted }),
                    contentAlignment = Alignment.Center,
                ) { XaniText(if (muted) "🔇" else "🔊", Type.Label, color = Color.White, fontSizeOverride = 15f) }
            }
        }

        // Bottom: Next
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = rdp(16), vertical = rdp(18))
                .clip(RoundedCornerShape(rdp(24)))
                .background(Color(0xFFEC4899))
                .pressable(onClick = onNext)
                .padding(horizontal = rdp(28), vertical = rdp(12)),
        ) {
            XaniText(copy.t(Copy.CreateNext) + "  ›", Type.LabelBold, color = Color.White, fontSizeOverride = 15f)
        }
    }
}