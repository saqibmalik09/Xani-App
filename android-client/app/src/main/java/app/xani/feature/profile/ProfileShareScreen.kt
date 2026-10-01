package app.xani.feature.profile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.isolate.runFeature
import app.xani.core.net.MediaConfig
import app.xani.core.net.Xani
import app.xani.feature.social.FrameStore
import app.xani.feature.social.LevelMark
import app.xani.i18n.Copy
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.Spinner
import app.xani.ui.components.XaniMark
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Share profile": the card somebody would screenshot anyway, made properly,
 * plus the three things they want to do with it — send it, copy the link, keep
 * the picture.
 *
 * The link is whatever the deployment serves the web on, read from
 * `/v1/config`; nothing here knows a domain. Until the bootstrap has answered
 * once the actions say so rather than sharing a broken address.
 */
@Composable
internal fun ProfileShareScreen(
    copy: Copy,
    snapshot: ProfileSnapshot,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val brand = LocalBrand.current
    val scope = rememberCoroutineScope()
    val link = MediaConfig.profileLink(snapshot.username)
    val tagline = copy.t(Copy.ProfileShareTagline, "app" to brand.name)

    // The card is recorded as it is drawn, so "save" writes exactly the pixels
    // on screen instead of a second, slightly different, rendering of it.
    val card = rememberGraphicsLayer()
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val bitmap = runCatching { card.toImageBitmap().asAndroidBitmap() }.getOrNull()
            val name = "${brand.wordmark}-${snapshot.username.ifBlank { "profile" }}-${System.currentTimeMillis()}.png"
            val ok = bitmap != null && saveToGallery(context, bitmap, name)
            saving = false
            onToast(copy.t(if (ok) Copy.ProfileShareSaved else Copy.ProfileShareSaveFailed))
        }
    }

    // Only pre-Q needs a permission to put a picture in the gallery; from Q on
    // the insert is scoped to our own collection and needs none.
    val storagePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else onToast(copy.t(Copy.ProfileShareSaveFailed))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(14)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(rdp(32))
                    .clip(CircleShape)
                    .background(ProfileTokens.Bg2)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
            Spacer(Modifier.width(rdp(12)))
            XaniText(copy.t(Copy.ProfileShareTitle), Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f)
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Border))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = rdp(28)),
            contentAlignment = Alignment.Center,
        ) {
            ShareCard(
                copy = copy,
                snapshot = snapshot,
                handle = "@${snapshot.username}",
                tagline = tagline,
                link = link,
                brandName = brand.name,
                layer = card,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(24))
                .padding(top = rdp(8), bottom = rdp(22)),
            horizontalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            ShareAction(
                label = copy.t(Copy.ProfileShare),
                modifier = Modifier.weight(1f),
                onClick = {
                    if (link.isEmpty()) {
                        onToast(copy.t(Copy.ProfileShareLinkPending))
                    } else {
                        shareText(context, snapshot.displayName, "$tagline\n$link")
                    }
                },
            ) { ProfileIco.Share(Color.White, rdp(20)) }

            ShareAction(
                label = copy.t(Copy.ProfileShareCopy),
                modifier = Modifier.weight(1f),
                onClick = {
                    if (link.isEmpty()) {
                        onToast(copy.t(Copy.ProfileShareLinkPending))
                    } else {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("profile", link))
                        onToast(copy.t(Copy.ProfileShareLinkCopied))
                    }
                },
            ) { ProfileIco.CopyLink(Color.White, rdp(20)) }

            ShareAction(
                label = copy.t(Copy.ProfileShareSave),
                modifier = Modifier.weight(1f),
                onClick = {
                    when {
                        saving -> Unit
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> save()
                        else -> storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
            ) {
                if (saving) Spinner(rdp(20)) else ProfileIco.Download(Color.White, rdp(20))
            }
        }
    }
}

/**
 * The card itself. Everything inside is drawn by us, so it records cleanly into
 * [layer] — no window pixels, no screenshot permission, no surprise chrome.
 */
@Composable
private fun ShareCard(
    copy: Copy,
    snapshot: ProfileSnapshot,
    handle: String,
    tagline: String,
    link: String,
    brandName: String,
    layer: GraphicsLayer,
) {
    val shape = RoundedCornerShape(rdp(28))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(shape)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .background(
                Brush.linearGradient(
                    0f to Color(0xFF7C3AED),
                    0.45f to Color(0xFFDB2777),
                    1f to Color(0xFFF59E0B),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            )
            // Two soft highlights so the gradient reads as light on a surface
            // rather than a flat ramp.
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.width * 0.55f,
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.78f),
                        radius = size.width * 0.5f,
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.92f, size.height * 0.78f),
                )
            }
            .padding(rdp(22)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(6)),
        ) {
            XaniMark(rdp(18))
            XaniText(brandName, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
        }

        Spacer(Modifier.weight(1f))

        // A worn frame wraps the face a third wider and is the ring itself:
        // the slot grows to hold it and the flat ring steps aside, so the
        // frame is never cut by a clip.
        val selfId = if (Xani.isReady) Xani.session.lastKnownUser?.id ?: 0L else 0L
        val frames by FrameStore.frames.collectAsState()
        val framed = selfId != 0L && frames[selfId]?.url?.isNotEmpty() == true
        Box(
            Modifier
                .size(rdp(if (framed) 156 else 124))
                .then(
                    if (framed) Modifier
                    else Modifier.background(Color.White.copy(alpha = 0.28f), CircleShape).padding(rdp(4)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            MyAvatar(size = rdp(116))
        }

        Row(
            Modifier.padding(top = rdp(14)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(6)),
        ) {
            XaniText(
                snapshot.displayName,
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 21f,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (snapshot.verified) ProfileIco.Verified(rdp(15))
            LevelMark(snapshot.senderLevel, rdp(18))
            LevelMark(snapshot.receiverLevel, rdp(18))
        }

        Box(
            Modifier
                .padding(top = rdp(6))
                .clip(RoundedCornerShape(rdp(14)))
                .background(Color.Black.copy(alpha = 0.18f))
                .padding(horizontal = rdp(12), vertical = rdp(5)),
        ) {
            XaniText(handle, Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
        }

        Row(
            Modifier.padding(top = rdp(14)),
            horizontalArrangement = Arrangement.spacedBy(rdp(22)),
        ) {
            CardStat(snapshot.followers, copy.t(Copy.ProfileFollowers))
            CardStat(snapshot.following, copy.t(Copy.ProfileFollowing))
            CardStat(snapshot.receiving, copy.t(Copy.ProfileReceiving))
        }

        Spacer(Modifier.weight(1f))

        XaniText(
            tagline,
            Type.Caption,
            color = Color.White.copy(alpha = 0.9f),
            fontSizeOverride = 12f,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (link.isNotEmpty()) {
            Box(
                Modifier
                    .padding(top = rdp(8))
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(rdp(1), Color.White.copy(alpha = 0.3f), RoundedCornerShape(rdp(12)))
                    .padding(horizontal = rdp(12), vertical = rdp(9)),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(
                    link,
                    Type.Caption,
                    color = Color.White,
                    fontSizeOverride = 12f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CardStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        XaniText(value, Type.LabelBold, color = Color.White, fontSizeOverride = 15f)
        XaniText(label, Type.Micro, color = Color.White.copy(alpha = 0.78f), fontSizeOverride = 10f)
    }
}

@Composable
private fun ShareAction(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(ProfileTokens.Bg2)
            .border(rdp(1), ProfileTokens.BorderStrong, RoundedCornerShape(rdp(16)))
            .pressable(onClick = onClick)
            .padding(vertical = rdp(12)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(rdp(40))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777)))),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(rdp(6)))
        XaniText(label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 12f, maxLines = 1)
    }
}

private fun shareText(context: Context, subject: String, body: String) {
    runFeature(Unit) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}

/**
 * Writes the card into the device gallery. Pending until the bytes are there,
 * so no picker ever shows a half-written file.
 */
private suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Boolean =
    withContext(Dispatchers.IO) {
        runFeature(false) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runFeature false
            val written = resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: false
            if (!written) {
                resolver.delete(uri, null, null)
                return@runFeature false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }
    }
