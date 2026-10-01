package app.xani.feature.create

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.xani.core.isolate.runFeature
import app.xani.feature.inbox.LocalThumb
import app.xani.feature.inbox.hasMediaRead
import app.xani.feature.inbox.mediaReadPerms
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One device item. `uri` is the typed Video/Images URI so probes and uploads see the right MIME. */
@Immutable
internal data class GalleryItem(val uri: Uri, val video: Boolean, val durationMs: Long)

private enum class GalleryFilter { All, Videos, Photos }

@Composable
internal fun DeviceGallery(
    copy: Copy,
    onPick: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(hasMediaRead(context)) }
    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var filter by remember { mutableStateOf(GalleryFilter.All) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        allowed = granted.values.any { it }
    }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        if (!allowed) perm.launch(mediaReadPerms())
    }
    LaunchedEffect(allowed) {
        if (allowed) items = withContext(Dispatchers.IO) { loadDeviceMedia(context) }
    }
    val shown = remember(items, filter) {
        when (filter) {
            GalleryFilter.All -> items
            GalleryFilter.Videos -> items.filter { it.video }
            GalleryFilter.Photos -> items.filter { !it.video }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(12), vertical = rdp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(36)).pressable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { CreateIco.Close(Color.White, rdp(18)) }
            Spacer(Modifier.weight(1f))
            XaniText(copy.t(Copy.InboxGallery), Type.LabelStrong, color = Color.White, fontSizeOverride = 16f)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(rdp(36)))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(12), vertical = rdp(4)),
            horizontalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            GalleryChip(copy.t(Copy.CreateGalleryAll), filter == GalleryFilter.All) { filter = GalleryFilter.All }
            GalleryChip(copy.t(Copy.CreateGalleryVideos), filter == GalleryFilter.Videos) { filter = GalleryFilter.Videos }
            GalleryChip(copy.t(Copy.CreateGalleryPhotos), filter == GalleryFilter.Photos) { filter = GalleryFilter.Photos }
        }
        if (!allowed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                XaniText(copy.t(Copy.InboxAllowPhotos), Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 14f)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(rdp(6)),
                horizontalArrangement = Arrangement.spacedBy(rdp(3)),
                verticalArrangement = Arrangement.spacedBy(rdp(3)),
            ) {
                items(shown, key = { it.uri.toString() }, contentType = { if (it.video) "video" else "photo" }) { item ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(rdp(4)))
                            .pressable { onPick(item.uri) },
                    ) {
                        LocalThumb(
                            uri = item.uri.toString(),
                            modifier = Modifier.fillMaxSize(),
                            fallback = Color(0xFF2A2A2E),
                            maxEdge = 220,
                        )
                        if (item.video) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(rdp(4))
                                    .clip(RoundedCornerShape(rdp(4)))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = rdp(5), vertical = rdp(1)),
                            ) {
                                XaniText(
                                    formatDuration(item.durationMs),
                                    Type.LabelBold,
                                    color = Color.White,
                                    fontSizeOverride = 10f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(if (on) Color.White else Color.White.copy(alpha = 0.1f))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(6)),
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.Black else Color.White, fontSizeOverride = 12f)
    }
}

internal fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val m = s / 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s % 60) else "%d:%02d".format(m, s % 60)
}

/**
 * Newest photos and videos together, one query. Each row becomes a typed
 * Images/Video URI (not the generic files/ one) so `getType`, thumbnails and
 * the upload probe all see the real item kind.
 */
private fun loadDeviceMedia(context: Context, limit: Int = 240): List<GalleryItem> = runFeature(emptyList()) {
    val uri = MediaStore.Files.getContentUri("external")
    context.contentResolver.query(
        uri,
        arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
        ),
        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?",
        arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        ),
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
    )?.use { c ->
        val out = ArrayList<GalleryItem>(limit)
        while (c.moveToNext() && out.size < limit) {
            val id = c.getLong(0)
            val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val duration = if (c.isNull(2)) 0L else c.getLong(2)
            val typed = if (video) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
            out.add(GalleryItem(typed, video, duration))
        }
        out
    } ?: emptyList()
}
