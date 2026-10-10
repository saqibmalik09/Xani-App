package app.xani.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import app.xani.ui.brand.LocalBrand
import app.xani.core.net.LiveChatData
import app.xani.core.net.LivePersonData
import app.xani.core.net.StageData
import app.xani.core.net.StageMemberData
import app.xani.feature.call.AudioStage
import app.xani.feature.call.CoHostRail
import app.xani.feature.call.CoHostSheet
import app.xani.feature.call.GiftBoxMark
import app.xani.feature.call.HangUpMark
import app.xani.feature.call.HlsPlayerView
import app.xani.feature.call.LiveChatReactOverlay
import app.xani.feature.call.LiveHeartsLayer
import app.xani.feature.call.LiveModeratorsSheet
import app.xani.feature.call.rememberLiveHearts
import app.xani.feature.call.LivePhase
import app.xani.feature.call.LiveRole
import app.xani.feature.call.LiveSession
import app.xani.feature.call.LiveUserSheet
import app.xani.feature.call.PkArena
import app.xani.feature.call.RaiseHandMark
import app.xani.feature.call.StageNotices
import app.xani.feature.call.StagePersonSheet
import app.xani.feature.call.VideoSurface
import app.xani.feature.call.rememberCallPermissions
import app.xani.feature.create.CreateIco
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.xani.core.isolate.runFeature
import app.xani.core.net.PersonData
import app.xani.core.net.Xani
import app.xani.feature.games.LiveGamesOverlay
import app.xani.feature.games.rememberLiveGamesState
import app.xani.feature.gift.CardTabs
import app.xani.feature.gift.EntranceOverlay
import app.xani.feature.gift.GameWinOverlay
import app.xani.feature.gift.GiftOverlay
import app.xani.feature.gift.GiftingPanel
import app.xani.feature.gift.LiveGiftSheet
import app.xani.feature.gift.LiveStickerSheet
import app.xani.feature.gift.StickerMark
import app.xani.feature.call.LiveChatLine
import app.xani.feature.gift.formatCoins
import app.xani.feature.gift.rememberGiftOverlayState
import app.xani.feature.main.GamesMark
import app.xani.feature.social.FollowStore
import app.xani.feature.social.FrameStore
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.KeepScreenOn
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Viewer side of a broadcast. Slides in from the right; swipe the video zone
 * up/down to hop streams; back opens the leave sheet (Minimize → PiP / Close).
 * Chat is the room's real stream — bounded to 40 lines on the session.
 */
@Composable
internal fun LiveViewerScreen(
    stream: LiveTile,
    copy: Copy,
    onSwitch: (Int) -> Unit, // +1 next, -1 previous
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    /** The password the viewer typed for a locked room; empty for a public one. */
    password: String = "",
    /**
     * The room needs a password the caller did not have (`wrong` false) or
     * refused the one it sent (`wrong` true): re-ask; the room itself is fine.
     */
    onPasswordRejected: (wrong: Boolean) -> Unit = { onClose() },
) {
    val enter = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var exitSheet by remember { mutableStateOf(false) }
    var peopleSheet by remember { mutableStateOf(false) }
    var giftSheet by remember { mutableStateOf(false) }
    var stickerSheet by remember { mutableStateOf(false) }
    val giftOverlay = rememberGiftOverlayState(stream.roomId)
    var swipeY by remember { mutableStateOf(0f) }
    var chatDraft by remember { mutableStateOf("") }
    // The composer starts as a comment bubble; tapping it opens the field.
    var composing by remember { mutableStateOf(false) }
    val chatFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // The host as a person: card sheet from the top pill, follow state shared
    // with every other surface through FollowStore.
    var hostSheet by remember { mutableStateOf(false) }
    // Which tab the host card opens on: 0 profile (pill, Following chip),
    // 1 gifting — the 💎 pill goes straight to the live's gift record.
    var hostSheetTab by remember { mutableIntStateOf(0) }
    var hostPerson by remember { mutableStateOf<PersonData?>(null) }
    // The stage: the co-host list, one person's card, and who a gift opens on.
    var coHostSheet by remember { mutableStateOf(false) }
    var stagePerson by remember { mutableStateOf<StageMemberData?>(null) }
    var giftTo by remember { mutableStateOf(0L) }
    val gamesState = rememberLiveGamesState()
    // Mine float on the tap; everyone else's arrive on the room stream.
    val hearts = rememberLiveHearts()
    // Someone in chat: their card (tap) or the reaction row (hold); the ⋯ moderators list.
    var chatUser by remember { mutableStateOf<LivePersonData?>(null) }
    var reactLine by remember { mutableStateOf<LiveChatData?>(null) }
    var modsSheet by remember { mutableStateOf(false) }

    // The broadcast behind this screen. Joining registers presence; the server's
    // audience count and end notice arrive on the stream, so nothing here polls.
    val live by LiveSession.state.collectAsState()
    val ctx = LocalContext.current
    if (stream.roomId != 0L) {
        DisposableEffect(stream.roomId) {
            LiveSession.watch(stream.roomId, password)
            onDispose { LiveSession.leave() }
        }
    }
    val session = live?.takeIf { it.roomId == stream.roomId }
    val stage = session?.stage ?: StageData.Empty
    val myRole = session?.role ?: LiveRole.Viewer
    val pk = session?.pk
    val inPk = pk != null && pk.phase != "ended"
    val audioRoom = (session?.room?.kind ?: (if (stream.kind == LiveKind.Audio) "audio" else "video")) == "audio"
    // A viewer never touched mic or camera, so going on stage is the first
    // time this app asks for them. Every road up (raise hand, take a seat,
    // the host's invite) passes here; without the grant LiveKit publishes
    // nothing and the guest is a silent, black tile.
    val permissions = rememberCallPermissions()
    LaunchedEffect(myRole, audioRoom) {
        if (myRole == LiveRole.Guest) permissions(!audioRoom) { LiveSession.republish() }
    }
    val viewerCount = session?.viewers ?: stream.viewerCount
    // The pill counts everyone in the room, host included: the server's
    // viewers number is the audience only, so the host is the +1.
    val viewersLabel = if (session != null || stream.viewerCount > 0) compactCount(viewerCount + 1) else stream.viewers

    // Do I follow this host — the same relation every other surface reads, so
    // a follow here flips the button on their profile and in the feed too.
    val hostUid = session?.room?.hostId?.takeIf { it != 0L } ?: stream.hostId
    val relations by FollowStore.relations.collectAsState()
    val following = relations[hostUid]?.following ?: false
    LaunchedEffect(hostUid) {
        if (hostUid == 0L) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { runFeature(null) { Xani.people.get(hostUid) } }
        r?.value?.let { p ->
            hostPerson = p
            FollowStore.learn(listOf(p))
            FrameStore.learn(p.id, p.frameUrl, p.frameMime)
            FrameStore.learnBadge(p.id, p.badgeUrl, p.badgeMime)
        }
    }
    // The room payload carries the host's frame; every avatar of them rings.
    LaunchedEffect(session?.room?.host) {
        session?.room?.host?.let { h ->
            if (h.id != 0L) {
                FrameStore.learn(h.id, h.frameUrl, h.frameMime)
                FrameStore.learnBadge(h.id, h.badgeUrl, h.badgeMime)
            }
        }
    }
    LaunchedEffect(composing) {
        if (composing) {
            chatFocus.requestFocus()
            keyboard?.show()
        } else {
            // Closing the composer has to put the keyboard away too. A half-typed
            // draft keeps the field composed, so the focus has to go as well or
            // the system brings the keyboard straight back.
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    LaunchedEffect(Unit) { enter.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            enter.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    fun minimize() {
        if (closing) return
        closing = true
        scope.launch {
            enter.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            onMinimize()
        }
    }
    BackHandler {
        when {
            giftSheet -> giftSheet = false
            stagePerson != null -> stagePerson = null
            coHostSheet -> coHostSheet = false
            hostSheet -> hostSheet = false
            composing -> composing = false
            peopleSheet -> peopleSheet = false
            exitSheet -> exitSheet = false
            else -> exitSheet = true
        }
    }

    // Watching a broadcast holds the screen awake: no dim, no lock, no "the
    // stream froze because the phone slept".
    KeepScreenOn()

    val color = Color(stream.color)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * enter.value
                translationY = swipeY
                alpha = if (abs(swipeY) > 1f) (1f - abs(swipeY) / (size.height * 0.4f)).coerceAtLeast(0.3f) else 1f
            }
            .background(Color(0xFF07070F)),
    ) {
        // Stream canvas: the broadcast when the tile has a room (SFU seat or the
        // CDN playlist, whichever the server handed us), the tinted wash until then.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush = Brush.linearGradient(
                        colors = listOf(color.copy(alpha = 0.75f), Color(0xFF07070F), color.copy(alpha = 0.35f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(brush) }
                },
            contentAlignment = Alignment.Center,
        ) {
            XaniText(stream.initials, Type.Wordmark, color = Color.White.copy(alpha = 0.14f), fontSizeOverride = 88f)
            if (stream.roomId != 0L) {
                when {
                    session == null || session.over -> Unit
                    audioRoom -> Unit // audio rooms keep the tinted wash; the seats draw in the column
                    inPk -> Unit // PK arena draws in the column; keep the wash behind
                    session.transport == "sfu" -> {
                        // Re-ask for the renderer when the media session or the host's track changes.
                        var hostRetry by remember(session.roomId) { mutableIntStateOf(0) }
                        val host = remember(session.roomId, session.mediaRev, session.room.hostId, hostRetry) {
                            LiveSession.hostView(ctx)
                        }
                        // The track can land before the room says who the host
                        // is (or a beat after the ask): while there is no
                        // renderer, re-ask on a short clock instead of leaving
                        // the viewer on the wash until some unrelated event.
                        if (host == null) {
                            LaunchedEffect(session.roomId, session.mediaRev, hostRetry) {
                                kotlinx.coroutines.delay(600)
                                hostRetry++
                            }
                        }
                        // Keyed on the renderer instance: AndroidView runs its factory
                        // once, so a rejoin (new room, new renderer) would otherwise
                        // keep painting the released one — a black host, at random.
                        if (host != null) VideoSurface(host, Modifier.fillMaxSize())
                    }
                    session.hlsUrl.isNotBlank() -> HlsPlayerView(session.hlsUrl, Modifier.fillMaxSize())
                }
                if (session?.phase == LivePhase.Connecting) {
                    XaniText(
                        copy.t(Copy.InboxConnecting),
                        Type.LabelBold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = rdp(160)),
                    )
                }
                // This device's own link is down — distinct from the host being away.
                if (session?.phase == LivePhase.Reconnecting && session.hostAway != true) {
                    XaniText(
                        copy.t(Copy.CallReconnecting),
                        Type.LabelBold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = rdp(160)),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xB807070F),
                            0.22f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color(0xF207070F),
                        ),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        )

        // Swipe zone — right side, between top chrome and chat, hops streams
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.42f)
                .fillMaxHeight(0.5f)
                .pointerInput(stream.id) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            when {
                                swipeY < -55f * density -> onSwitch(1)
                                swipeY > 55f * density -> onSwitch(-1)
                            }
                            swipeY = 0f
                        },
                        onDragCancel = { swipeY = 0f },
                    ) { _, delta -> swipeY += delta * 0.38f }
                },
        )

        // Typing: a tap anywhere off the composer puts the keyboard away. Sits
        // under the chrome below, so the bar's own buttons still get the tap,
        // and over the swipe zone, so a stray drag cannot hop streams mid-line.
        if (composing) {
            Box(Modifier.fillMaxSize().pressable(pressedScale = 1f) { composing = false })
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // The keyboard lifts the composer (and the chat above it); the
                // picture behind stays put.
                .imePadding(),
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(14), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                // No back chevron here: the "…" button at the end of the row
                // (and the system back) opens the same exit sheet, so the bar
                // is just the host pill, the room's size, and the dots.
                // Host pill — the host's profile, not the broadcast title.
                // Tapping it opens the host card.
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(rdp(28)))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(rdp(28)))
                        .pressable(pressedScale = 0.98f, onClick = { hostSheetTab = 0; hostSheet = true })
                        .padding(start = rdp(6), end = rdp(10), top = rdp(6), bottom = rdp(6)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                ) {
                    Box {
                        if (stream.avatarUrl.isNotEmpty()) {
                            Box(Modifier.border(rdp(2), Color.White.copy(alpha = 0.25f), CircleShape)) {
                                PersonAvatar(stream.hostId, stream.name, stream.avatarUrl, rdp(36))
                            }
                        } else {
                            Box(
                                Modifier
                                    .size(rdp(36))
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(rdp(2), Color.White.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                XaniText(stream.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(rdp(11))
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                                .border(rdp(2), Color(0xFF07070F), CircleShape),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(5)),
                        ) {
                            XaniText(stream.name, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                            LevelMark(stream.hostLevel, rdp(14))
                        }
                        XaniText(stream.host, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 10f)
                    }
                    // Where LIVE used to sit: Follow. Once following, the chip
                    // opens the host card (message, gifts, their numbers).
                    if (!following) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(14)))
                                .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                                .pressable(pressedScale = 0.92f, onClick = { if (hostUid != 0L) FollowStore.set(hostUid, true) })
                                .padding(horizontal = rdp(12), vertical = rdp(6)),
                        ) {
                            XaniText("+ " + copy.t(Copy.LiveFollow), Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
                        }
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(14)))
                                .background(Color.White.copy(alpha = 0.1f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(rdp(14)))
                                .pressable(pressedScale = 0.92f, onClick = { hostSheetTab = 0; hostSheet = true })
                                .padding(horizontal = rdp(12), vertical = rdp(6)),
                        ) {
                            XaniText("✓ " + copy.t(Copy.LiveFollowing), Type.LabelBold, color = Color.White.copy(alpha = 0.9f), fontSizeOverride = 11f)
                        }
                    }
                }
                // Everyone in the room (host included), right beside the host
                // pill where a viewer looks for the room's size; tap opens
                // who is here. It used to sit on the stats row below.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(18)))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(rdp(18)))
                        .pressable(onClick = { if (stream.roomId != 0L) peopleSheet = true })
                        .padding(horizontal = rdp(10), vertical = rdp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EyeGlyph()
                    Spacer(Modifier.width(rdp(5)))
                    XaniText(viewersLabel, Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
                }
                Box(
                    Modifier
                        .size(rdp(36))
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .pressable(onClick = { exitSheet = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    ThreeDots()
                }
            }

            // Stats row. The broadcast title and the country flag used to sit
            // here too; by request the title stays off the watch screen and the
            // flag is gone, so the picture keeps the room to itself.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(14), vertical = rdp(4)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                // Diamonds credited to the host after the split, never the
                // coin spend. Tapping opens the host's gift record for this live.
                StatPill(onClick = { hostSheetTab = 1; hostSheet = true }) {
                    XaniText("💎", Type.Caption, fontSizeOverride = 12f)
                    Spacer(Modifier.width(rdp(5)))
                    XaniText(
                        if (session != null) formatCoins(session.roomDiamonds) else stream.diamonds,
                        Type.LabelBold,
                        color = Color(0xFFC4B5FD),
                        fontSizeOverride = 11f,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            val audioStageShown = session != null && !session.over && audioRoom && !inPk
            if (session != null && !session.over) {
                if (inPk && pk != null && !audioRoom) {
                    val hostId = session.room.hostId
                    PkArena(
                        pk = pk,
                        myHostId = hostId,
                        myRoomId = session.roomId,
                        isHost = myRole == LiveRole.Host,
                        mediaRev = session.mediaRev,
                        copy = copy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rdp(8), vertical = rdp(6))
                            .aspectRatio(1.04f),
                        myHostVideo = {
                            when {
                                session.transport == "sfu" -> {
                                    val host = remember(session.roomId, session.mediaRev, hostId) {
                                        LiveSession.hostView(ctx)
                                    }
                                    if (host != null) VideoSurface(host, Modifier.fillMaxSize())
                                }
                                session.hlsUrl.isNotBlank() -> HlsPlayerView(session.hlsUrl, Modifier.fillMaxSize())
                            }
                        },
                    )
                } else if (audioRoom) {
                    // The seats: host top-left, guests 1…n in a 5–6 column grid.
                    // Empty seat is a door — tap it to sit; a taken one opens that person.
                    Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                        AudioStage(
                            stage = stage,
                            copy = copy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = rdp(10), vertical = rdp(8)),
                            onSeat = { seat, member ->
                                when {
                                    member != null -> stagePerson = member
                                    myRole == LiveRole.Viewer -> permissions(false) { LiveSession.takeSeat(seat) }
                                }
                            },
                        )
                    }
                }
            }
            StageNotices(copy, Modifier.align(Alignment.CenterHorizontally).padding(top = rdp(4)))

            if (!audioStageShown) Spacer(Modifier.weight(1f))

            // Floating hearts — folded away while a line is being typed so the
            // keyboard has room without pushing the field out of view.
            if (!composing) {
                LiveHeartsLayer(hearts, Modifier.fillMaxWidth().height(rdp(120)))
            }

            // Co-hosts: 1 2 3 4 on the far side from the comments, directly
            // above them. Below the weighted spacer, so the row rides the
            // bottom cluster with the chat and the action bar and keeps its
            // place as the room fills, instead of sitting over the picture.
            if (session != null && !session.over && !audioRoom &&
                !(inPk && pk != null) && stage.guests.isNotEmpty()
            ) {
                CoHostRail(
                    stage = stage,
                    mediaRev = session.mediaRev,
                    horizontal = true,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = rdp(14))
                        .padding(bottom = rdp(8)),
                    onTile = { stagePerson = it },
                    onMore = { coHostSheet = true },
                )
            }

            // Chat — real room lines from the session. A keyed lazy list that
            // sticks to the newest line: a new message recomposes one row, not
            // the screen, and 40 lines stay 40 rows however busy the room is.
            val chat = session?.chat ?: emptyList()
            val chatState = rememberLazyListState()
            LaunchedEffect(chat.size, chat.lastOrNull()?.id) {
                if (chat.isNotEmpty()) chatState.scrollToItem(chat.lastIndex)
            }
            LazyColumn(
                state = chatState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rdp(132))
                    .padding(horizontal = rdp(14))
                    .padding(bottom = rdp(10)),
                verticalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                items(chat, key = { if (it.id != 0L) it.id else it.clientId.hashCode().toLong() }) { m ->
                    LiveChatLine(
                        m,
                        nameTint = chatTint(m.user.id),
                        background = Color.Black.copy(alpha = 0.28f),
                        corner = rdp(12),
                        badgeSize = rdp(14),
                        onUser = { chatUser = m.user },
                        onHold = { reactLine = m },
                        onReact = { LiveSession.reactChat(m, it) },
                        likeLabel = copy.t(Copy.LiveSentLike),
                        enterLabel = copy.t(Copy.LiveEnteredRoom),
                        leaveLabel = copy.t(Copy.LiveLeftRoom),
                    )
                }
            }

            // Action bar. Resting: a comment bubble and the tools (gift box,
            // games, heart, more). Composing: the field takes the row, Send
            // beside it; the tools come back when the message is away.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xB807070F))
                    .padding(horizontal = rdp(10), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                // Up to seven tools sit here on a narrow phone: 40dp circles
                // with 8dp between them fit a 360dp screen without overlap.
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                if (composing || chatDraft.isNotBlank()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(rdp(22)))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(rdp(22)))
                            .padding(horizontal = rdp(14), vertical = rdp(9)),
                    ) {
                        BasicTextField(
                            value = chatDraft,
                            onValueChange = { chatDraft = it.take(200) },
                            textStyle = Type.Caption.copy(color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                val t = chatDraft.trim()
                                if (t.isNotEmpty()) {
                                    LiveSession.sendChat(t)
                                    chatDraft = ""
                                }
                                composing = false
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(chatFocus),
                            decorationBox = { inner ->
                                Box {
                                    if (chatDraft.isEmpty()) {
                                        XaniText(
                                            copy.t(Copy.LiveSaySomething),
                                            Type.Caption,
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSizeOverride = 13f,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    // Stickers sit right of the field: a tap opens the tray, a
                    // pick lands in chat at once.
                    ActionCircle(onClick = { if (stream.roomId != 0L) { keyboard?.hide(); stickerSheet = true } }) {
                        StickerMark(size = rdp(20), tint = Color.White)
                    }
                    Box(
                        Modifier
                            .height(rdp(40))
                            .clip(RoundedCornerShape(rdp(20)))
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                            .pressable(pressedScale = 0.92f, onClick = {
                                val t = chatDraft.trim()
                                if (t.isNotEmpty()) { LiveSession.sendChat(t); chatDraft = "" }
                                composing = false
                            })
                            .padding(horizontal = rdp(16)),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(copy.t(Copy.LiveChatSend), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                    }
                } else {
                    ActionCircle(onClick = { composing = true }) {
                        // Comment bubble
                        VectorCanvas(
                            shapes = listOf(
                                VShape.D(
                                    "M13 2.5H3c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h1.5V13l3.2-2.5H13c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1z",
                                    stroke(Color.White, 1.5f),
                                ),
                            ),
                            viewport = 16f,
                            modifier = Modifier.size(rdp(19)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // On stage: my mic and the way down. Off stage in a video
                    // room with the door open: a raised hand asks the host.
                    if (myRole == LiveRole.Guest) {
                        val micOn = session?.muted != true
                        ActionCircle(
                            background = if (micOn) Color(0x3334D399) else Color(0x33EF4444),
                            border = if (micOn) Color(0x6634D399) else Color(0x66EF4444),
                            onClick = { LiveSession.toggleMute() },
                        ) {
                            CreateIco.Mic(Color.White, rdp(18), off = !micOn)
                        }
                        // The camera button appears only while the host allows
                        // co-host cameras; off by default — turning it on is my
                        // choice, and the seat token enforces the same rule.
                        if (!audioRoom && stage.guestCam) {
                            val camOn = session?.cameraOff == false
                            ActionCircle(
                                background = if (camOn) Color(0x3334D399) else Color.Black.copy(alpha = 0.5f),
                                border = if (camOn) Color(0x6634D399) else Color.White.copy(alpha = 0.12f),
                                onClick = { permissions(true) { LiveSession.toggleCamera() } },
                            ) {
                                CreateIco.Cam(Color.White, rdp(18), off = !camOn)
                            }
                            if (camOn) {
                                ActionCircle(onClick = { LiveSession.switchCamera() }) {
                                    CreateIco.FlipCam(Color.White, rdp(18))
                                }
                            }
                        }
                        Box(
                            Modifier
                                .size(rdp(40))
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .pressable(pressedScale = 0.88f, onClick = { LiveSession.leaveStage() }),
                            contentAlignment = Alignment.Center,
                        ) { HangUpMark(Color.White, rdp(20)) }
                    } else if (!audioRoom && stage.requests && session != null && !session.over) {
                        val asked = session.requested
                        ActionCircle(
                            background = if (asked) Color(0x338B5CF6) else Color.Black.copy(alpha = 0.5f),
                            border = if (asked) Color(0xAA8B5CF6) else Color.White.copy(alpha = 0.12f),
                            // Cancelling needs nothing; asking gets mic + camera
                            // granted now, so the moment the host says yes we publish.
                            onClick = { if (asked) LiveSession.requestStage() else permissions(true) { LiveSession.requestStage() } },
                        ) {
                            RaiseHandMark(if (asked) Color(0xFFC4B5FD) else Color.White, rdp(20))
                        }
                    }
                    ActionCircle(onClick = { if (stream.roomId != 0L) stickerSheet = true }) {
                        StickerMark(size = rdp(20), tint = Color.White)
                    }
                    // Gift box, not a diamond: the diamond is the host's counter.
                    Box(
                        Modifier
                            .size(rdp(40))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                            .pressable(pressedScale = 0.88f, onClick = { if (stream.roomId != 0L) { giftTo = 0L; giftSheet = true } }),
                        contentAlignment = Alignment.Center,
                    ) {
                        GiftBoxMark()
                    }
                    ActionCircle(onClick = { gamesState.showSheet = true }) {
                        GamesMark(size = rdp(20), tint = Color.White)
                    }
                    ActionCircle(
                        background = Color(0x33EC4899),
                        border = Color(0x66EC4899),
                        onClick = {
                            hearts.add()
                            LiveSession.like()
                        },
                    ) {
                        XaniText("❤️", Type.Label, fontSizeOverride = 18f)
                    }
                    ActionCircle(onClick = { exitSheet = true }) {
                        ThreeDots()
                    }
                }
            }
        }

        // Gifts landing in the room — over the video and chrome, under the sheets.
        GiftOverlay(state = giftOverlay, copy = copy, onUser = { chatUser = it })

        // Someone with an entrance effect walking in — same layer as gifts.
        EntranceOverlay(roomId = stream.roomId, copy = copy)

        // A game win somewhere on the platform — a short centered strip.
        GameWinOverlay(roomId = stream.roomId, copy = copy)

        // Exit sheet
        if (exitSheet) {
            val brand = LocalBrand.current
            LiveExitSheet(
                stream = stream,
                copy = copy,
                onMinimize = { exitSheet = false; minimize() },
                onShare = {
                    LiveShare.share(
                        context = ctx,
                        scope = scope,
                        copy = copy,
                        brand = brand,
                        roomId = stream.roomId,
                        onDone = { exitSheet = false },
                    )
                },
                onCloseStream = { exitSheet = false; dismiss() },
                onCancel = { exitSheet = false },
                onModerators = if (session?.canModerate == true) ({ exitSheet = false; modsSheet = true }) else null,
            )
        }

        chatUser?.let { u ->
            LiveUserSheet(
                user = u,
                copy = copy,
                onGift = { giftTo = it; giftSheet = true },
                onClose = { chatUser = null },
            )
        }
        reactLine?.let { line ->
            LiveChatReactOverlay(
                line = line,
                copy = copy,
                onReact = { LiveSession.reactChat(line, it) },
                onUser = { chatUser = line.user },
                onClose = { reactLine = null },
            )
        }
        if (modsSheet) LiveModeratorsSheet(copy = copy, onClose = { modsSheet = false })

        if (peopleSheet && stream.roomId != 0L) {
            LivePeopleSheet(
                roomId = stream.roomId,
                viewers = viewerCount,
                copy = copy,
                // Their card takes the list's place, still inside the room.
                onUser = { peopleSheet = false; chatUser = it },
                onClose = { peopleSheet = false },
            )
        }

        if (giftSheet && stream.roomId != 0L) {
            LiveGiftSheet(
                roomId = stream.roomId,
                hostId = session?.room?.hostId?.takeIf { it != 0L } ?: stream.hostId,
                copy = copy,
                onClose = { giftSheet = false },
                overlay = giftOverlay,
                receiverId = giftTo,
                stage = stage,
                pk = pk,
            )
        }

        if (coHostSheet) {
            CoHostSheet(
                stage = stage,
                isHost = false,
                copy = copy,
                onPerson = { coHostSheet = false; stagePerson = it },
                onDrop = {},
                onClose = { coHostSheet = false },
            )
        }

        // A co-host or a seat: their card, same shape as the host's.
        stagePerson?.let { m ->
            StagePersonSheet(
                member = m,
                isHost = false,
                copy = copy,
                onGift = { giftTo = it; giftSheet = true },
                onDrop = {},
                onClose = { stagePerson = null },
            )
        }

        if (stickerSheet && stream.roomId != 0L) {
            LiveStickerSheet(
                roomId = stream.roomId,
                hostId = session?.room?.hostId?.takeIf { it != 0L } ?: stream.hostId,
                copy = copy,
                onClose = { stickerSheet = false },
            )
        }

        // The host's card: profile, follow, a line into chat, what the room
        // has given them. Opened from the top pill or the Following chip.
        if (hostSheet) {
            LiveHostSheet(
                stream = stream,
                person = hostPerson,
                following = following,
                diamonds = session?.roomDiamonds ?: 0L,
                viewers = viewerCount,
                copy = copy,
                onFollowToggle = { if (hostUid != 0L) FollowStore.set(hostUid, !following) },
                onMessage = { composing = true },
                onGift = { if (stream.roomId != 0L) giftSheet = true },
                onClose = { hostSheet = false },
                roomId = stream.roomId,
                initialTab = hostSheetTab,
            )
        }

        // Host's link went quiet: tell the room it is them, not this device.
        if (session?.hostAway == true && session.phase != LivePhase.Ended) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = rdp(96))
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color(0xE6F59E0B))
                    .padding(horizontal = rdp(14), vertical = rdp(8)),
            ) {
                XaniText(copy.t(Copy.LiveHostAway), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
        }

        // Half-mode games layer over the broadcast — catalog sheet + game sheet.
        LiveGamesOverlay(state = gamesState, copy = copy)

        // The room is over: say so once, and leave on OK. Nothing else on this
        // screen is tappable behind it, so a viewer never sits in a dead room.
        if (session?.needsPassword == true) {
            // Wrong (or missing) password: hand back to the prompt, not "ended".
            val wrong = session.endReason == "wrong_password"
            LaunchedEffect(session.roomId) { LiveSession.dismiss(); onPasswordRejected(wrong) }
        } else if (session?.phase == LivePhase.Ended) {
            LiveEndedDialog(
                copy = copy,
                reason = session.endReason,
                error = session.error,
                summary = session.summary,
                elapsedSeconds = session.elapsedSeconds(),
                onOk = { LiveSession.dismiss(); dismiss() },
            )
        }
    }
}

/**
 * The door of a private room: asked on the listing tap, before anything joins.
 * [wrong] is set when the server refused the previous attempt.
 */
@Composable
internal fun LivePasswordDialog(
    stream: LiveTile,
    copy: Copy,
    wrong: Boolean,
    onJoin: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(stream.roomId) { mutableStateOf("") }
    BackHandler(onBack = onCancel)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .pressable(pressedScale = 1f, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = rdp(32))
                .fillMaxWidth()
                .clip(RoundedCornerShape(rdp(22)))
                .background(Color(0xFF17112B))
                .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(rdp(22)))
                .pressable(pressedScale = 1f, onClick = { })
                .padding(horizontal = rdp(22), vertical = rdp(22)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                PersonAvatar(stream.hostId, stream.name, stream.avatarUrl, rdp(40))
                Column {
                    XaniText(stream.name, Type.LabelBold, color = Color.White, fontSizeOverride = 15f)
                    if (stream.host.isNotEmpty()) {
                        XaniText(stream.host, Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 12f)
                    }
                }
            }
            XaniText(
                copy.t(Copy.LivePasswordTitle),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 15f,
                align = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = rdp(16)),
            )
            Box(
                Modifier
                    .padding(top = rdp(12))
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(14)))
                    .border(
                        1.dp,
                        if (wrong) Color(0xFFEC4899) else Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(rdp(14)),
                    )
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = rdp(14), vertical = rdp(12)),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(32) },
                    singleLine = true,
                    textStyle = Type.Label.copy(color = Color.White),
                    cursorBrush = SolidColor(Color(0xFFEC4899)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onJoin(text.trim()) }),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) {
                                XaniText(copy.t(Copy.LivePasswordHint), Type.Label, color = Color.White.copy(alpha = 0.4f), fontSizeOverride = 14f)
                            }
                            inner()
                        }
                    },
                )
            }
            if (wrong) {
                XaniText(
                    copy.t(Copy.LivePasswordWrong),
                    Type.Caption,
                    color = Color(0xFFF472B6),
                    fontSizeOverride = 12f,
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = rdp(8)),
                )
            }
            Row(
                Modifier.padding(top = rdp(18)).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Color.White.copy(alpha = 0.08f))
                        .pressable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(copy.t(Copy.LivePasswordCancel), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
                }
                val ready = text.isNotBlank()
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                        .graphicsLayer { alpha = if (ready) 1f else 0.45f }
                        .pressable(enabled = ready, onClick = { onJoin(text.trim()) }),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(copy.t(Copy.LivePasswordJoin), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
                }
            }
        }
    }
}

@Composable
internal fun LiveEndedDialog(
    copy: Copy,
    reason: String,
    error: String,
    summary: app.xani.core.net.LiveSummaryData?,
    elapsedSeconds: Long,
    onOk: () -> Unit,
) {
    val seconds = (summary?.durationSeconds?.toLong() ?: elapsedSeconds).coerceAtLeast(0L)
    val message = when {
        reason == "lost" -> copy.t(Copy.LiveConnectionLost)
        reason == "live_ended" || reason == "host_ended" || reason == "host_lost" || reason == "max_length" ->
            copy.t(Copy.LiveHostEnded)
        reason == "kicked" -> copy.t(Copy.LiveKickedNotice)
        reason == "blocked" || reason == "live_banned" -> copy.t(Copy.LiveBannedNotice)
        else -> error.ifBlank { copy.t(Copy.LiveHostEnded) }
    }
    BackHandler(onBack = onOk)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .pressable(onClick = { }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = rdp(36))
                .fillMaxWidth()
                .clip(RoundedCornerShape(rdp(22)))
                .background(Color(0xFF17112B))
                .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(rdp(22)))
                .padding(horizontal = rdp(22), vertical = rdp(24)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(rdp(52))
                    .clip(CircleShape)
                    .background(Color(0x33EC4899))
                    .border(1.dp, Color(0x66EC4899), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(rdp(10)).clip(CircleShape).background(Color(0xFFEC4899)))
            }
            XaniText(
                copy.t(Copy.LiveWatchEndedTitle),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 16f,
                modifier = Modifier.padding(top = rdp(14)),
            )
            XaniText(
                message,
                Type.Caption,
                color = Color.White.copy(alpha = 0.7f),
                fontSizeOverride = 13f,
                align = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = rdp(6)),
            )
            if (seconds > 0L) {
                XaniText(
                    formatLiveDuration(seconds.toInt()),
                    Type.Wordmark,
                    color = Color.White,
                    fontSizeOverride = 36f,
                    modifier = Modifier.padding(top = rdp(16)),
                )
                XaniText(
                    copy.t(Copy.LiveWatchEndedDuration),
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSizeOverride = 12f,
                )
            }
            Box(
                Modifier
                    .padding(top = rdp(20))
                    .fillMaxWidth()
                    .height(rdp(44))
                    .clip(RoundedCornerShape(rdp(22)))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                    .pressable(onClick = onOk),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(copy.t(Copy.LiveOk), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
            }
        }
    }
}

private fun formatLiveDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun chatTint(userId: Long): Color {
    val palette = listOf(
        Color(0xFFF472B6), Color(0xFF34D399), Color(0xFFA78BFA),
        Color(0xFF60A5FA), Color(0xFFFBBF24), Color(0xFFF87171),
    )
    val i = ((userId % palette.size) + palette.size) % palette.size
    return palette[i.toInt()]
}

/** A 44dp round tool on the action bar. */
@Composable
private fun ActionCircle(
    background: Color = Color.White.copy(alpha = 0.08f),
    border: Color = Color.White.copy(alpha = 0.14f),
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(rdp(40))
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .pressable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ThreeDots() {
    VectorCanvas(
        shapes = listOf(
            VShape.Circle(3f, 8f, 1.5f, app.xani.ui.vector.fill(Color.White.copy(alpha = 0.8f))),
            VShape.Circle(8f, 8f, 1.5f, app.xani.ui.vector.fill(Color.White.copy(alpha = 0.8f))),
            VShape.Circle(13f, 8f, 1.5f, app.xani.ui.vector.fill(Color.White.copy(alpha = 0.8f))),
        ),
        viewport = 16f,
        modifier = Modifier.size(rdp(16)),
    )
}

/**
 * The host as a card over their own broadcast: avatar, follow state, their
 * numbers (diamonds received this live, watchers, followers), and the two
 * doors — Message drops into the composer, Send gift opens the gift sheet.
 */
@Composable
private fun LiveHostSheet(
    stream: LiveTile,
    person: PersonData?,
    following: Boolean,
    diamonds: Long,
    viewers: Long,
    copy: Copy,
    onFollowToggle: () -> Unit,
    onMessage: () -> Unit,
    onGift: () -> Unit,
    onClose: () -> Unit,
    /** The room, for the Gifting tab's "This LIVE" window. */
    roomId: Long = 0L,
    /** 0 profile, 1 gifting — the 💎 pill opens straight onto the record. */
    initialTab: Int = 0,
) {
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }
    LiveSheet(onClose = onClose) { close ->
        val name = person?.fullName?.takeIf { it.isNotBlank() } ?: stream.name
        val handle = person?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: stream.host
        CardTabs(tab, copy, onSelect = { tab = it }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = rdp(10)))
        if (tab == 1) {
            GiftingPanel(userId = stream.hostId, copy = copy, roomId = roomId, modifier = Modifier.padding(top = rdp(10), bottom = rdp(12)))
            return@LiveSheet
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20))
                .padding(top = rdp(12), bottom = rdp(18)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .border(rdp(2), Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), CircleShape)
                    .padding(rdp(3)),
            ) {
                PersonAvatar(stream.hostId, name, person?.avatarUrl ?: stream.avatarUrl, rdp(72))
            }
            XaniText(name, Type.LabelBold, color = Color.White, fontSizeOverride = 17f, modifier = Modifier.padding(top = rdp(10)))
            if (handle.isNotBlank()) {
                XaniText(handle, Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 12f)
            }
            if (person != null && person.bio.isNotBlank()) {
                XaniText(
                    person.bio,
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSizeOverride = 12f,
                    maxLines = 2,
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = rdp(6)),
                )
            }

            Row(
                Modifier.padding(top = rdp(14)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                HostStat("💎 " + formatCoins(diamonds), copy.t(Copy.LiveHostDiamonds))
                HostStat(compactCount(viewers), copy.t(Copy.LiveHostWatching))
                if (person != null) HostStat(compactCount(person.followers.toLong()), copy.t(Copy.LiveHostFollowers))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(18)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .then(
                            if (following) Modifier
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(rdp(22)))
                            else Modifier.background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
                        )
                        .pressable(pressedScale = 0.95f, onClick = onFollowToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(
                        if (following) "✓ " + copy.t(Copy.LiveFollowing) else "+ " + copy.t(Copy.LiveFollow),
                        Type.LabelBold, color = Color.White, fontSizeOverride = 12f,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(rdp(22)))
                        .pressable(pressedScale = 0.95f, onClick = { onMessage(); close() }),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(copy.t(Copy.LiveHostMessage), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899))))
                        .pressable(pressedScale = 0.95f, onClick = { onGift(); close() }),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(copy.t(Copy.LiveSendGift), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                }
            }
        }
    }
}

@Composable
private fun HostStat(value: String, label: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = rdp(14), vertical = rdp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(value, Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
        XaniText(label, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 10f, modifier = Modifier.padding(top = rdp(2)))
    }
}

@Composable
private fun StatPill(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(rdp(20)))
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = rdp(12), vertical = rdp(5)),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun LiveExitSheet(
    stream: LiveTile,
    copy: Copy,
    onMinimize: () -> Unit,
    onShare: () -> Unit,
    onCloseStream: () -> Unit,
    onCancel: () -> Unit,
    /** Host / moderator only: open the moderators list. */
    onModerators: (() -> Unit)? = null,
) {
    val color = Color(stream.color)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .pressable(onClick = onCancel),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24)))
                .background(Brush.verticalGradient(listOf(Color(0xFFF0EEFF), Color(0xFFF5F4FF))))
                .padding(horizontal = rdp(20))
                .padding(top = rdp(20), bottom = rdp(36))
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = rdp(36), height = rdp(4))
                    .clip(CircleShape)
                    .background(Color(0x2E6D28D9)),
            )
            // Stream preview card
            Row(
                Modifier
                    .padding(top = rdp(16))
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color(0x126D28D9))
                    .border(1.dp, Color(0x1F6D28D9), RoundedCornerShape(rdp(16)))
                    .padding(horizontal = rdp(14), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(12)),
            ) {
                Box(
                    Modifier
                        .size(rdp(46))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Brush.linearGradient(listOf(color, Color(0xFFEC4899)))),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(stream.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
                }
                Column(Modifier.weight(1f)) {
                    XaniText(stream.name, Type.LabelBold, color = Color(0xFF1E0A50), fontSizeOverride = 14f)
                    XaniText(
                        "${stream.flag} · ${stream.category}",
                        Type.Caption,
                        color = Color(0x801E0A50),
                        fontSizeOverride = 12f,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(4))) {
                    EyeGlyph(Color(0xFF5B21B6))
                    XaniText(stream.viewers, Type.LabelBold, color = Color(0xFF5B21B6), fontSizeOverride = 12f)
                }
            }
            XaniText(
                copy.t(Copy.LiveLeaveTitle),
                Type.LabelBold,
                color = Color(0xFF1E0A50),
                fontSizeOverride = 15f,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = rdp(18)),
            )
            // Minimize
            ExitOption(
                bg = Color(0x126D28D9),
                border = Color(0x296D28D9),
                title = copy.t(Copy.LiveMinimize),
                titleColor = Color(0xFF1E0A50),
                sub = copy.t(Copy.LiveMinimizeSub),
                badge = "PiP",
                onClick = onMinimize,
            ) {
                VectorCanvas(
                    shapes = listOf(
                        VShape.D(
                            "M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3",
                            stroke(Color(0xFF6D28D9), 2f),
                        ),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(20)),
                )
            }
            Spacer(Modifier.height(rdp(10)))
            // Share: copies the live link and opens the system sheet. Same URL
            // shape as a profile share so a tap opens this room, not just the app.
            ExitOption(
                bg = Color(0x126D28D9),
                border = Color(0x296D28D9),
                title = copy.t(Copy.LiveShare),
                titleColor = Color(0xFF1E0A50),
                sub = copy.t(Copy.LiveShareSub),
                badge = null,
                onClick = onShare,
            ) {
                VectorCanvas(
                    shapes = listOf(
                        VShape.D(
                            "M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8M16 6l-4-4-4 4M12 2v13",
                            stroke(Color(0xFF6D28D9), 2f),
                        ),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(20)),
                )
            }
            if (onModerators != null) {
                Spacer(Modifier.height(rdp(10)))
                ExitOption(
                    bg = Color(0x1210B981),
                    border = Color(0x2910B981),
                    title = copy.t(Copy.LiveModerators),
                    titleColor = Color(0xFF1E0A50),
                    sub = copy.t(Copy.LiveModeration),
                    badge = null,
                    onClick = onModerators,
                ) {
                    VectorCanvas(
                        shapes = listOf(
                            VShape.D("M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z", stroke(Color(0xFF059669), 2f)),
                            VShape.D("M9 12l2 2 4-4", stroke(Color(0xFF059669), 2f)),
                        ),
                        viewport = 24f,
                        modifier = Modifier.size(rdp(20)),
                    )
                }
            }
            Spacer(Modifier.height(rdp(10)))
            // Close
            ExitOption(
                bg = Color(0x0DEF4444),
                border = Color(0x24EF4444),
                title = copy.t(Copy.LiveClose),
                titleColor = Color(0xFFEF4444),
                sub = copy.t(Copy.LiveCloseSub),
                badge = null,
                onClick = onCloseStream,
            ) {
                VectorCanvas(
                    shapes = listOf(
                        VShape.Line(18f, 6f, 6f, 18f, stroke(Color(0xFFEF4444), 2f)),
                        VShape.Line(6f, 6f, 18f, 18f, stroke(Color(0xFFEF4444), 2f)),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(20)),
                )
            }
            XaniText(
                copy.t(Copy.LiveCancelStay),
                Type.LabelBold,
                color = Color(0x731E0A50),
                fontSizeOverride = 14f,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = rdp(14))
                    .pressable(onClick = onCancel)
                    .padding(rdp(8)),
            )
        }
    }
}

@Composable
private fun ExitOption(
    bg: Color,
    border: Color,
    title: String,
    titleColor: Color,
    sub: String,
    badge: String?,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(16)))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(rdp(16)))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(18), vertical = rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(14)),
    ) {
        Box(
            Modifier
                .size(rdp(44))
                .clip(RoundedCornerShape(rdp(12)))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.weight(1f)) {
            XaniText(title, Type.LabelBold, color = titleColor, fontSizeOverride = 14f)
            XaniText(sub, Type.Caption, color = Color(0x801E0A50), fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(2)))
        }
        if (badge != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color(0x1A6D28D9))
                    .padding(horizontal = rdp(10), vertical = rdp(4)),
            ) {
                XaniText(badge, Type.LabelBold, color = Color(0xFF6D28D9), fontSizeOverride = 10f)
            }
        }
    }
}

/** Draggable PiP mini-card. Tap restores the stream; ✕ dismisses. */
@Composable
internal fun LivePipCard(
    stream: LiveTile,
    copy: Copy,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    val color = Color(stream.color)
    Column(
        modifier
            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            .width(rdp(140))
            .clip(RoundedCornerShape(rdp(14)))
            .background(Brush.linearGradient(listOf(Color(0xFF1A0836), Color(0xFF2D0F5C))))
            .border(1.5.dp, Color(0x598B5CF6), RoundedCornerShape(rdp(14)))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offX += drag.x
                    offY += drag.y
                }
            }
            .pressable(onClick = onRestore),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rdp(80))
                .drawWithCache {
                    val brush = Brush.linearGradient(
                        listOf(color.copy(alpha = 0.55f), Color(0x66EC4899)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(rdp(38))
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(Brush.linearGradient(listOf(color, Color(0xFFEC4899))))
                    .border(rdp(2), Color.White.copy(alpha = 0.4f), RoundedCornerShape(rdp(10))),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(stream.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(rdp(6))
                    .size(rdp(22))
                    .clip(RoundedCornerShape(rdp(6)))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pressable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                VectorCanvas(
                    shapes = listOf(
                        VShape.Line(18f, 6f, 6f, 18f, stroke(Color.White.copy(alpha = 0.85f), 2.8f)),
                        VShape.Line(6f, 6f, 18f, 18f, stroke(Color.White.copy(alpha = 0.85f), 2.8f)),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(10)),
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = rdp(7), bottom = rdp(5))
                    .clip(RoundedCornerShape(rdp(8)))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = rdp(6), vertical = rdp(2)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(3)),
            ) {
                EyeGlyph()
                XaniText(stream.viewers, Type.LabelBold, color = Color.White, fontSizeOverride = 9f)
            }
        }
        Column(Modifier.padding(horizontal = rdp(8), vertical = rdp(6))) {
            XaniText(
                stream.name,
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 11f,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            XaniText(
                "${stream.flag} ${stream.category}",
                Type.Caption,
                color = Color.White.copy(alpha = 0.5f),
                fontSizeOverride = 10f,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
