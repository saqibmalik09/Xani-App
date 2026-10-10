package app.xani.feature.profile

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xani.core.media.RemoteImage
import app.xani.core.net.MediaConfig
import app.xani.core.net.Xani
import app.xani.feature.home.FeedRepo
import app.xani.feature.social.AvatarFrame
import app.xani.feature.social.FrameStore
import app.xani.feature.inbox.newCaptureUri
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

/**
 * The signed-in account's own picture, wherever it is shown.
 *
 * Three sources in order of freshness: the square being uploaded right now, the
 * stored picture, then the user's initials. The upload preview is what makes a
 * change feel done the moment it is picked — see [AvatarStore].
 */
@Composable
fun MyAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    /**
     * Initials to fall back on. Signup passes the name being typed, because the
     * profile cache has nothing to show yet at that point.
     */
    name: String? = null,
    /** Shows the camera badge and makes the whole avatar tappable. */
    onPick: (() -> Unit)? = null,
    /** Press and hold to see the picture full size. */
    onHold: (() -> Unit)? = null,
) {
    val profile by ProfileStore.state.collectAsState()
    val upload by AvatarStore.state.collectAsState()
    val initialsFrom = name?.takeIf { it.isNotBlank() }
        ?: profile.displayName.ifBlank { profile.username }

    // Layout is larger than the face when the camera badge is on, so parents
    // that clip to the reported size cannot cut the badge in half.
    val badge = if (onPick != null) size * 0.3f else 0.dp
    val pad = badge * 0.28f
    Box(
        modifier
            .size(size + pad * 2)
            .then(
                when {
                    onPick == null && onHold == null -> Modifier
                    else -> Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick?.invoke() },
                        onLongClick = onHold?.let { { it() } },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ProfileTokens.Accent, ProfileTokens.AccentDeep))),
            contentAlignment = Alignment.Center,
        ) {
            val preview = upload.preview
            val stored = MediaConfig.avatarUrl(profile.avatarUrl)
            when {
                preview != null -> Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                stored.isNotEmpty() -> RemoteImage(
                    url = stored,
                    modifier = Modifier.fillMaxSize(),
                    maxEdge = 512,
                    fallback = Color.Transparent,
                )

                else -> XaniText(
                    FeedRepo.initials(initialsFrom),
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = size.value * 0.34f,
                )
            }
            // A ring, not a spinner over the face: the user is looking at their
            // new picture, and covering it to say "working" would hide the thing
            // they are waiting to see.
            if (upload.uploading) UploadRing(Modifier.fillMaxSize())
        }
        // The frame the account wears, same source of truth as everyone else's.
        run {
            val selfId = if (Xani.isReady) Xani.session.lastKnownUser?.id ?: 0L else 0L
            if (selfId != 0L) {
                val frames by FrameStore.frames.collectAsState()
                frames[selfId]?.takeIf { it.url.isNotEmpty() }?.let { AvatarFrame(it.url, it.mime, size) }
            }
        }
        if (onPick != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = pad * 0.15f, bottom = pad * 0.15f)
                    .size(badge)
                    .clip(CircleShape)
                    .background(ProfileTokens.AccentDeep)
                    .border(rdp(2), Color.White, CircleShape)
                    .pressable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                ProfileIco.Camera(Color.White, size * 0.17f)
            }
        }
    }
}

@Composable
private fun UploadRing(modifier: Modifier) {
    val spin = rememberInfiniteTransition(label = "avatar-upload")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Box(
        modifier
            .rotate(angle)
            .border(
                width = rdp(2.5f),
                brush = Brush.sweepGradient(listOf(Color.Transparent, Color.White)),
                shape = CircleShape,
            ),
    )
}

/**
 * Camera or gallery, then straight to the upload.
 *
 * There is no crop screen on purpose: the picture is centre-cropped to a square
 * on both the device and the server, which is what a circular avatar shows
 * anyway, and one fewer step between "I picked a photo" and "that is my photo"
 * is worth more here than a framing control nobody asks for.
 */
@Composable
internal fun AvatarPickerSheet(copy: Copy, onClose: () -> Unit) {
    val context = LocalContext.current
    val upload by AvatarStore.state.collectAsState()
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }

    fun dismiss() {
        scope.launch {
            slide.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler { dismiss() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingCapture
        pendingCapture = null
        if (taken && uri != null) {
            AvatarStore.upload(uri)
            dismiss()
        }
    }
    val cameraPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) return@rememberLauncherForActivityResult
        val uri = newCaptureUri(context)
        pendingCapture = uri
        camera.launch(uri)
    }
    // The photo picker needs no storage permission on any supported version:
    // the user hands over one image, not the library.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            AvatarStore.upload(uri)
            dismiss()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.SheetScrim)
            .pressable(pressedScale = 1f) { dismiss() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = size.height * slide.value }
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                .background(ProfileTokens.Bg)
                .navigationBarsPadding()
                .padding(vertical = rdp(8)),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = rdp(6))
                    .width(rdp(36))
                    .height(rdp(4))
                    .clip(CircleShape)
                    .background(ProfileTokens.BorderStrong),
            )
            XaniText(
                copy.t(Copy.ProfilePhotoTitle),
                Type.LabelBold,
                color = ProfileTokens.Text,
                fontSizeOverride = 15f,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = rdp(4), bottom = rdp(8)),
            )
            if (upload.error.isNotEmpty()) {
                XaniText(
                    upload.error,
                    Type.Caption,
                    color = ProfileTokens.Danger,
                    fontSizeOverride = 12f,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = rdp(24), vertical = rdp(4)),
                )
            }
            AvatarAction(copy.t(Copy.ProfilePhotoCamera)) {
                AvatarStore.clearError()
                cameraPerm.launch(Manifest.permission.CAMERA)
            }
            AvatarAction(copy.t(Copy.ProfilePhotoGallery)) {
                AvatarStore.clearError()
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            AvatarAction(copy.t(Copy.UploadCancel)) { dismiss() }
            Spacer(Modifier.height(rdp(6)))
        }
    }
}

@Composable
private fun AvatarAction(label: String, onClick: () -> Unit) {
    XaniText(
        label,
        Type.LabelBold,
        color = ProfileTokens.Text,
        fontSizeOverride = 15f,
        align = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.99f, onClick = onClick)
            .padding(vertical = rdp(15)),
    )
}
