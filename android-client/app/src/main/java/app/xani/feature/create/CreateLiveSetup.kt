package app.xani.feature.create

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.xani.core.lens.Beauty
import app.xani.core.lens.Lens
import app.xani.core.lens.LensCatalog
import app.xani.core.lens.LensTray
import app.xani.core.lens.lensTrayScrim
import app.xani.core.net.RewardEligibility
import app.xani.core.net.StageData
import app.xani.feature.call.rememberCallPermissions
import app.xani.feature.inbox.CameraPreview
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

internal enum class LiveBroadcast { Video, Audio }

internal enum class LiveAudience { Public, Friends, Private }

internal data class LiveRoomConfig(
    val isVideo: Boolean,
    val title: String,
    val audience: LiveAudience,
    val password: String,
    val seats: Int,
    val hostSeat: Int,
    val bannerTint: Long,
    /** Storage key of the gallery cover (PUT /v1/live/cover); empty → tinted card. */
    val coverKey: String = "",
    val frontCamera: Boolean = true,
)

/** [CreateLiveFlow]'s banner index for "my picture" rather than a tint. */
private const val BannerCover = -2

private val TitleSuggestions = listOf(
    "Vibing with good music",
    "Late night talk sesh",
    "Let's chat & chill",
    "Gaming with the squad",
    "Q&A — ask me anything",
    "Study with me",
    "Just hanging out",
    "Fan appreciation stream",
)

private val BannerTints = listOf(
    0xFF7C3AEDL, 0xFFEC4899L, 0xFF06B6D4L, 0xFFF59E0BL, 0xFF10B981L, 0xFF6366F1L,
)

/**
 * UpdatedFigma Live setup: lavender light form → seat arrange (audio) → live room.
 * Slide transitions match Figma; no dark leftover Go-Live form.
 *
 * Navigation contract (Figma + requested UX): closing the setup returns to the
 * Post camera (onPost), never straight home; ending a live returns to the setup
 * so back/forth toggles cleanly. Only the Post lane closes Create itself.
 */
@Composable
internal fun CreateLiveFlow(
    copy: Copy,
    active: Boolean,
    onPost: () -> Unit,
    onMinimize: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) } // 0 setup, 1 seats, 2 room
    var broadcast by rememberSaveable { mutableStateOf(LiveBroadcast.Video) }
    var title by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf(LiveAudience.Public) }
    var password by rememberSaveable { mutableStateOf("") }
    var seats by rememberSaveable { mutableStateOf<Int?>(null) }
    var customSeats by rememberSaveable { mutableStateOf("") }
    var banner by rememberSaveable { mutableStateOf(-1) }
    var hostSeat by rememberSaveable { mutableIntStateOf(0) }
    var frontCam by rememberSaveable { mutableStateOf(true) }
    val suggestions = remember { TitleSuggestions.shuffled().take(5) }
    val cover by LiveCoverStore.state.collectAsState()
    // Mic (and camera for video) are granted before the room opens. Publishing
    // without RECORD_AUDIO does not throw: the capture silently fails and the
    // host broadcasts dead air — every "viewers cannot hear me" report.
    val permissions = rememberCallPermissions()

    val resolvedSeats = when {
        seats == -1 -> customSeats.toIntOrNull()?.coerceIn(1, StageData.AudioSeatMax) ?: 0
        seats != null -> seats!!
        else -> 0
    }
    val audioReady = broadcast == LiveBroadcast.Audio && resolvedSeats > 0
    // A private room without a password is refused by the server; gate it here.
    val passwordOk = audience != LiveAudience.Private || password.isNotBlank()
    val canGoVideo = broadcast == LiveBroadcast.Video && passwordOk
    val canNextAudio = audioReady && passwordOk
    // "Will this live earn rewards?" — asked per broadcast type, shown under the button.
    val rewards = rememberRewardEligibility(isVideo = broadcast == LiveBroadcast.Video, live = false)

    fun buildConfig(isVideo: Boolean) = LiveRoomConfig(
        isVideo = isVideo,
        title = title.ifBlank { copy.t(Copy.CreateLiveHint) },
        audience = audience,
        password = password,
        seats = if (isVideo) 0 else resolvedSeats,
        hostSeat = hostSeat,
        bannerTint = BannerTints.getOrElse(banner) { BannerTints[0] },
        // An upload still in flight or failed falls back to the tint: going
        // live never waits on a picture.
        coverKey = if (banner == BannerCover) cover.key else "",
        frontCamera = frontCam,
    )

    // Room/seats back → setup; setup back → Post camera.
    BackHandler(enabled = step > 0) { step = 0 }
    BackHandler(enabled = step == 0) { onPost() }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(220)))
            } else {
                (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220)))
            }
        },
        label = "live-flow",
    ) { page ->
        when (page) {
            // Audio rooms open straight from the form: seats are laid out live
            // (host in the middle, guests around) and can grow while on air.
            2 -> CreateLiveRoom(
                copy = copy,
                active = active,
                config = buildConfig(broadcast == LiveBroadcast.Video),
                onEnd = { step = 0 },
                onMinimize = onMinimize,
            )
            else -> LiveSetupForm(
                copy = copy,
                broadcast = broadcast,
                onBroadcast = { broadcast = it },
                title = title,
                onTitle = { title = it },
                suggestions = suggestions,
                audience = audience,
                onAudience = { audience = it },
                password = password,
                onPassword = { password = it },
                seats = seats,
                onSeats = { seats = it },
                customSeats = customSeats,
                onCustomSeats = { customSeats = it.filter(Char::isDigit).take(2) },
                banner = banner,
                onBanner = { banner = it },
                cover = cover,
                onPickCover = { uri ->
                    LiveCoverStore.pick(context, uri)
                    banner = BannerCover
                },
                canGoVideo = canGoVideo,
                canNextAudio = canNextAudio,
                rewards = rewards,
                front = frontCam,
                // Off the moment the room page takes over: the broadcast bridge
                // preview is about to open the same sensor, and two clients on
                // one camera evict each other in a loop.
                previewOn = active && step == 0,
                onClose = onPost,
                onFlipCam = { frontCam = !frontCam },
                onGoLive = { permissions(true) { step = 2 } },
                onNextSeats = { permissions(false) { step = 2 } },
            )
        }
    }
}

@Composable
private fun LiveSetupForm(
    copy: Copy,
    broadcast: LiveBroadcast,
    onBroadcast: (LiveBroadcast) -> Unit,
    title: String,
    onTitle: (String) -> Unit,
    suggestions: List<String>,
    audience: LiveAudience,
    onAudience: (LiveAudience) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    seats: Int?,
    onSeats: (Int?) -> Unit,
    customSeats: String,
    onCustomSeats: (String) -> Unit,
    banner: Int,
    onBanner: (Int) -> Unit,
    cover: LiveCoverStore.State,
    onPickCover: (Uri) -> Unit,
    canGoVideo: Boolean,
    canNextAudio: Boolean,
    rewards: RewardEligibility?,
    front: Boolean,
    previewOn: Boolean,
    onClose: () -> Unit,
    onFlipCam: () -> Unit,
    onGoLive: () -> Unit,
    onNextSeats: () -> Unit,
) {
    val isVideo = broadcast == LiveBroadcast.Video
    Box(
        Modifier
            .fillMaxSize()
            .background(CreateTokens.LiveSetupWash),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(rdp(220))
                .background(
                    Brush.radialGradient(listOf(CreateTokens.LiveAccent.copy(alpha = 0.12f), Color.Transparent)),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(18), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SoftHit(onClose) {
                    CreateIco.Close(CreateTokens.LiveAccent, rdp(16))
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(24)))
                        .background(CreateTokens.LiveSoft)
                        .border(1.dp, CreateTokens.LiveAccent.copy(alpha = 0.15f), RoundedCornerShape(rdp(24)))
                        .padding(rdp(3)),
                ) {
                    BroadcastChip(copy.t(Copy.CreateVideo), isVideo) { onBroadcast(LiveBroadcast.Video) }
                    BroadcastChip(copy.t(Copy.CreateAudioOnly), !isVideo) { onBroadcast(LiveBroadcast.Audio) }
                }
                Spacer(Modifier.weight(1f))
                if (isVideo) SoftHit(onFlipCam) { CreateIco.FlipCam(CreateTokens.LiveAccent, rdp(20)) }
                else Spacer(Modifier.size(rdp(40)))
            }

            // Title block — no AnimatedContent remount (keeps form scroll position stable)
            Column(Modifier.padding(horizontal = rdp(18))) {
                XaniText(
                    if (isVideo) copy.t(Copy.CreateLiveVideoTitle) else copy.t(Copy.CreateLiveAudioTitle),
                    Type.H2,
                    color = CreateTokens.LiveInk,
                    fontSizeOverride = 22f,
                )
                Spacer(Modifier.height(rdp(3)))
                XaniText(
                    if (isVideo) copy.t(Copy.CreateLiveVideoSub) else copy.t(Copy.CreateLiveAudioSub),
                    Type.Caption,
                    color = CreateTokens.LiveSub,
                    fontSizeOverride = 13f,
                )
            }

            if (!isVideo) {
                Row(
                    Modifier.padding(horizontal = rdp(18), vertical = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(14)),
                ) {
                    Box(
                        Modifier
                            .size(rdp(52))
                            .clip(CircleShape)
                            .background(CreateTokens.LiveCta),
                        contentAlignment = Alignment.Center,
                    ) {
                        CreateIco.SoftMic(Color.White, rdp(24))
                    }
                    Column {
                        XaniText(copy.t(Copy.CreateAudioRoomLabel), Type.LabelBold, color = CreateTokens.LiveInk, fontSizeOverride = 15f)
                        XaniText(copy.t(Copy.CreateAudioRoomSub), Type.Micro, color = CreateTokens.LiveSub, fontSizeOverride = 12f)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = rdp(18), vertical = rdp(14)),
                verticalArrangement = Arrangement.spacedBy(rdp(18)),
            ) {
                SectionLabel(copy.t(Copy.CreateBanner))
                // The photo picker hands over one image, no storage permission needed.
                val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) onPickCover(uri)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                ) {
                    CoverChip(
                        cover = cover,
                        selected = banner == BannerCover,
                        label = copy.t(Copy.CreateBannerPhoto),
                        onClick = {
                            if (cover.preview != null && !cover.uploading) onBanner(BannerCover)
                            else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onReplace = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                    BannerTints.forEachIndexed { i, tint ->
                        val on = banner == i
                        Box(
                            Modifier
                                .size(rdp(88), rdp(54))
                                .clip(RoundedCornerShape(rdp(12)))
                                .border(
                                    if (on) rdp(2) else rdp(1),
                                    if (on) CreateTokens.LiveAccent else CreateTokens.LiveAccent.copy(alpha = 0.12f),
                                    RoundedCornerShape(rdp(12)),
                                )
                                .background(Color(tint))
                                .pressable { onBanner(i) },
                        )
                    }
                }

                SectionLabel(copy.t(Copy.CreateTitleField))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(14)))
                        .border(rdp(1.5f), CreateTokens.LiveBorder, RoundedCornerShape(rdp(14)))
                        .background(CreateTokens.LiveCard)
                        .padding(horizontal = rdp(14), vertical = rdp(12)),
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { onTitle(it.take(80)) },
                        textStyle = Type.Label.copy(color = CreateTokens.LiveInk),
                        cursorBrush = SolidColor(CreateTokens.LiveAccent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) {
                                    XaniText(
                                        copy.t(Copy.CreateLiveTitleHint),
                                        Type.Label,
                                        color = CreateTokens.LiveSub.copy(alpha = 0.45f),
                                        fontSizeOverride = 15f,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(rdp(7)),
                ) {
                    suggestions.forEach { s ->
                        val on = title == s
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(22)))
                                .border(
                                    rdp(1.5f),
                                    if (on) CreateTokens.LiveAccent else CreateTokens.LiveAccent.copy(alpha = 0.18f),
                                    RoundedCornerShape(rdp(22)),
                                )
                                .background(if (on) CreateTokens.LiveAccent else Color.White)
                                .pressable { onTitle(s) }
                                .padding(horizontal = rdp(14), vertical = rdp(7)),
                        ) {
                            XaniText(
                                s,
                                Type.Label,
                                color = if (on) Color.White else CreateTokens.LiveChip,
                                fontSizeOverride = 12.5f,
                            )
                        }
                    }
                }

                if (!isVideo) {
                    SectionLabel(copy.t(Copy.CreateSpeakerSeats))
                    Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                        listOf(4, 6, 8, 10, -1).forEach { n ->
                            val on = seats == n
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(rdp(12)))
                                    .border(
                                        rdp(1.5f),
                                        if (on) CreateTokens.LiveAccent else CreateTokens.LiveAccent.copy(alpha = 0.15f),
                                        RoundedCornerShape(rdp(12)),
                                    )
                                    .background(if (on) CreateTokens.LiveAccent else Color.White)
                                    .pressable { onSeats(n) }
                                    .padding(vertical = rdp(13)),
                                contentAlignment = Alignment.Center,
                            ) {
                                XaniText(
                                    if (n == -1) copy.t(Copy.CreateCustom) else "$n",
                                    if (on) Type.LabelBold else Type.Label,
                                    color = if (on) Color.White else CreateTokens.LiveChip,
                                    fontSizeOverride = if (n == -1) 11f else 14f,
                                    align = TextAlign.Center,
                                )
                            }
                        }
                    }
                    if (seats == -1) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(rdp(12)))
                                .border(rdp(1.5f), CreateTokens.LiveBorder, RoundedCornerShape(rdp(12)))
                                .background(Color.White)
                                .padding(horizontal = rdp(16), vertical = rdp(12)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = customSeats,
                                    onValueChange = onCustomSeats,
                                    textStyle = Type.LabelBold.copy(color = CreateTokens.LiveInk),
                                    cursorBrush = SolidColor(CreateTokens.LiveAccent),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        Box {
                                            if (customSeats.isEmpty()) {
                                                XaniText(
                                                    copy.t(Copy.CreateSeatsHint),
                                                    Type.Caption,
                                                    color = CreateTokens.LiveSub.copy(alpha = 0.45f),
                                                    fontSizeOverride = 14f,
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                )
                                XaniText(copy.t(Copy.CreateSeatsMax), Type.Micro, color = CreateTokens.LiveSub.copy(alpha = 0.4f), fontSizeOverride = 12f)
                            }
                        }
                    }
                }

                SectionLabel(copy.t(Copy.CreateAudience))
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                    listOf(
                        LiveAudience.Public to Copy.CreateAudiencePublic,
                        LiveAudience.Friends to Copy.CreateAudienceFriends,
                        LiveAudience.Private to Copy.CreateAudiencePrivate,
                    ).forEach { (key, labelKey) ->
                        val on = audience == key
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rdp(14)))
                                .border(
                                    rdp(1.5f),
                                    if (on) CreateTokens.LiveAccent else CreateTokens.LiveAccent.copy(alpha = 0.15f),
                                    RoundedCornerShape(rdp(14)),
                                )
                                .background(if (on) CreateTokens.LiveAccent else Color.White)
                                .pressable { onAudience(key) }
                                .padding(vertical = rdp(12)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            XaniText(
                                copy.t(labelKey),
                                Type.LabelBold,
                                color = if (on) Color.White else CreateTokens.LiveChip,
                                fontSizeOverride = 13f,
                            )
                            XaniText(
                                copy.t(
                                    when (key) {
                                        LiveAudience.Public -> Copy.CreateAudiencePublicSub
                                        LiveAudience.Friends -> Copy.CreateAudienceFriendsSub
                                        LiveAudience.Private -> Copy.CreateAudiencePrivateSub
                                    },
                                ),
                                Type.Micro,
                                color = if (on) Color.White.copy(alpha = 0.65f) else CreateTokens.LiveSub.copy(alpha = 0.45f),
                                fontSizeOverride = 10f,
                            )
                        }
                    }
                }
                if (audience == LiveAudience.Private) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rdp(14)))
                            .border(rdp(1.5f), CreateTokens.LiveAccent.copy(alpha = 0.25f), RoundedCornerShape(rdp(14)))
                            .background(Color.White)
                            .padding(horizontal = rdp(14), vertical = rdp(12)),
                    ) {
                        BasicTextField(
                            value = password,
                            onValueChange = { onPassword(it.take(32)) },
                            textStyle = Type.Label.copy(color = CreateTokens.LiveInk),
                            cursorBrush = SolidColor(CreateTokens.LiveAccent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (password.isEmpty()) {
                                        XaniText(
                                            copy.t(Copy.CreatePasswordHint),
                                            Type.Label,
                                            color = CreateTokens.LiveSub.copy(alpha = 0.45f),
                                            fontSizeOverride = 14f,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }

                // See yourself before anyone else does: the live camera with
                // the same Lens controller the broadcast publishes through, so
                // the beauty (Auto one tap away), look or background dialled in
                // here is exactly the first frame viewers get. Go LIVE itself
                // is untouched — it starts the room with whatever is on screen.
                if (isVideo) {
                    SectionLabel(copy.t(Copy.CreateLivePreview))
                    LiveSetupPreview(copy = copy, front = front, enabled = previewOn)
                }
                Spacer(Modifier.height(rdp(72)))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, CreateTokens.LiveSetupBg)))
                    .padding(horizontal = rdp(18), vertical = rdp(14)),
            ) {
                RewardEligibilityChip(copy, rewards)
                val enabled = if (isVideo) canGoVideo else canNextAudio
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rdp(56))
                        .clip(RoundedCornerShape(rdp(28)))
                        .then(
                            if (enabled) Modifier.background(CreateTokens.LiveCta)
                            else Modifier.background(CreateTokens.LiveSoft),
                        )
                        .pressable(enabled = enabled) {
                            if (isVideo) onGoLive() else onNextSeats()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(
                        when {
                            isVideo -> copy.t(Copy.CreateGoLiveBtn)
                            canNextAudio -> copy.t(Copy.CreateStartAudioRoom)
                            else -> copy.t(Copy.CreateSelectSeats)
                        },
                        Type.LabelBold,
                        color = if (enabled) Color.White else CreateTokens.LiveSub.copy(alpha = 0.4f),
                        fontSizeOverride = 16f,
                    )
                }
            }
        }
    }
}

/**
 * The viewfinder before Go Live: a rounded card with the camera through
 * [Lens.live] and the full effect tray riding the picture's lower edge,
 * Beauty category first so Auto is one tap away. Until the host has chosen
 * anything, Auto is the starting point — a host expects to look finished
 * before being seen, and Off stays one tap away. Camera permission is asked
 * here (it was previously asked at Go LIVE; answering now means the room
 * opens without a dialog in the way).
 */
@Composable
private fun LiveSetupPreview(copy: Copy, front: Boolean, enabled: Boolean) {
    val context = LocalContext.current
    var camOk by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camOk = it }
    LaunchedEffect(enabled) {
        if (enabled && !camOk) ask.launch(Manifest.permission.CAMERA)
    }
    // One write, only while nothing was chosen; a host's own pick (including
    // Off) is never overridden, and the room resets the controller on end.
    LaunchedEffect(Unit) {
        if (Lens.live.value.beauty == Beauty.Off) {
            LensCatalog.beautyPreset("b.auto")?.let { Lens.live.setBeauty(it.beauty) }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(rdp(340))
            .clip(RoundedCornerShape(rdp(18)))
            .border(rdp(1.5f), CreateTokens.LiveAccent.copy(alpha = 0.15f), RoundedCornerShape(rdp(18)))
            .background(Color(0xFF0B0818)),
    ) {
        if (camOk) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                front = front,
                enabled = enabled,
                fallback = Color(0xFF0B0818),
                lens = Lens.live,
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .pressable { ask.launch(Manifest.permission.CAMERA) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                XaniText("📷", Type.Label, fontSizeOverride = 30f)
                XaniText(
                    copy.t(Copy.CreateLiveCamAsk),
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSizeOverride = 13f,
                    modifier = Modifier.padding(top = rdp(8)),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(lensTrayScrim())
                .padding(top = rdp(14), bottom = rdp(10)),
        ) {
            LensTray(
                controller = Lens.live,
                copy = copy,
                compact = true,
                initial = LensCatalog.Category.Beauty,
            )
        }
    }
}

/**
 * First chip of the banner row: "add a photo" until one is picked, then the
 * cut thumbnail (tap selects it, long-press picks another). A failed upload
 * shows the picture with a warning ring so the host knows the card will fall
 * back to a tint.
 */
@Composable
private fun CoverChip(
    cover: LiveCoverStore.State,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    onReplace: () -> Unit,
) {
    val preview = cover.preview
    val ring = when {
        cover.error.isNotEmpty() -> Color(0xFFEF4444)
        selected -> CreateTokens.LiveAccent
        else -> CreateTokens.LiveAccent.copy(alpha = 0.12f)
    }
    Box(
        Modifier
            .size(rdp(88), rdp(54))
            .clip(RoundedCornerShape(rdp(12)))
            .border(if (selected) rdp(2) else rdp(1), ring, RoundedCornerShape(rdp(12)))
            .background(Color.White)
            .combinedClickable(onClick = onClick, onLongClick = onReplace),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(
                painter = BitmapPainter(preview.asImageBitmap()),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (cover.uploading) {
                // Uploading: a dimmed picture with a small pulsing dot; no spinner library.
                val pulse = rememberInfiniteTransition(label = "cover-up")
                val a by pulse.animateFloat(
                    0.35f, 1f,
                    infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "cover-dot",
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(rdp(10)).clip(CircleShape).background(Color.White.copy(alpha = a)))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                XaniText("+", Type.LabelBold, color = CreateTokens.LiveAccent, fontSizeOverride = 18f)
                XaniText(label, Type.Micro, color = CreateTokens.LiveSub, fontSizeOverride = 10f)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    XaniText(
        text.uppercase(),
        Type.Micro,
        color = CreateTokens.LiveAccent.copy(alpha = 0.55f),
        fontSizeOverride = 11f,
    )
}

@Composable
private fun BroadcastChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(if (on) CreateTokens.LiveAccent else Color.Transparent)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(18), vertical = rdp(8)),
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else CreateTokens.LiveSub, fontSizeOverride = 13f)
    }
}

@Composable
private fun SoftHit(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(rdp(40))
            .clip(CircleShape)
            .background(CreateTokens.LiveSoft)
            .border(1.dp, CreateTokens.LiveAccent.copy(alpha = 0.15f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
