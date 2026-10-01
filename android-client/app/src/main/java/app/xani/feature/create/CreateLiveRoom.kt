package app.xani.feature.create

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.net.LiveChatData
import app.xani.core.net.LivePersonData
import app.xani.core.net.StageData
import app.xani.core.net.StageMemberData
import app.xani.feature.call.AudioStage
import app.xani.feature.call.CoHostRail
import app.xani.feature.call.CoHostSheet
import app.xani.feature.call.HangUpMark
import app.xani.feature.call.LiveChatLine
import app.xani.feature.call.LiveChatReactOverlay
import app.xani.feature.call.LiveHeartsLayer
import app.xani.feature.call.LiveModeratorsSheet
import app.xani.feature.call.LivePhase
import app.xani.feature.call.LiveSession
import app.xani.feature.call.LiveUi
import app.xani.feature.call.LiveUserSheet
import app.xani.feature.call.rememberLiveHearts
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.stroke
import app.xani.feature.call.MessageMark
import app.xani.feature.call.MoreMark
import app.xani.feature.call.PkArena
import app.xani.feature.call.PkIncomingCard
import app.xani.feature.call.PkInviteSheet
import app.xani.feature.call.PkMark
import app.xani.feature.call.SeatsSheet
import app.xani.feature.call.StageNotices
import app.xani.feature.call.StagePersonSheet
import app.xani.feature.call.StageRequestCard
import app.xani.feature.home.LivePeopleSheet
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.xani.feature.games.LiveGamesOverlay
import app.xani.feature.games.rememberLiveGamesState
import app.xani.feature.gift.EntranceOverlay
import app.xani.feature.gift.GiftOverlay
import app.xani.feature.gift.LiveGiftSheet
import app.xani.feature.gift.LiveStickerSheet
import app.xani.feature.gift.StickerMark
import app.xani.feature.gift.formatCoins
import app.xani.feature.gift.rememberGiftOverlayState
import app.xani.feature.inbox.CameraPreview
import app.xani.feature.main.GamesMark
import app.xani.feature.social.NameBadge
import app.xani.i18n.Copy
import app.xani.ui.components.KeepScreenOn
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay

/**
 * UpdatedFigma live room. Camera is a stable sibling — timer/viewer ticks never
 * remount the TextureView. Mic/Cam use Create icons (no emoji placeholders).
 */
@Composable
internal fun CreateLiveRoom(
    copy: Copy,
    active: Boolean,
    config: LiveRoomConfig,
    onEnd: () -> Unit,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    // Hosting holds the screen awake: a sleeping phone stops the camera and
    // freezes the broadcast for everyone watching.
    KeepScreenOn()
    // The broadcast itself: the session owns the room, the SFU seat and the
    // camera once it publishes; this screen renders it. Ending here ends it
    // for every viewer, and leaving the screen never leaves a ghost room.
    val live by LiveSession.state.collectAsState()
    val ctx = LocalContext.current
    DisposableEffect(config.isVideo, config.title) {
        LiveSession.goLive(
            config.isVideo,
            config.title,
            tint = hexOf(config.bannerTint),
            coverUrl = config.coverKey,
            audience = when (config.audience) {
                LiveAudience.Public -> "public"
                LiveAudience.Friends -> "friends"
                LiveAudience.Private -> "private"
            },
            // Only a Private room carries the password; the field may hold text
            // from a setup the host switched back to Public.
            password = if (config.audience == LiveAudience.Private) config.password.trim() else "",
            // Audio: the seats picked on the form, host included. Video: 0.
            seats = if (config.isVideo) 0 else config.seats.coerceIn(2, 35),
        )
        onDispose { LiveSession.leave() }
    }
    val viewers = live?.viewers?.toInt() ?: 0
    val micOn = live?.muted != true
    val camOn = live?.cameraOff != true
    val over = live?.over == true
    val stage = live?.stage ?: StageData.Empty
    val requests = live?.requests ?: emptyList()
    val requestsOpen = live?.room?.requests ?: true
    // The bridge preview runs only until the server has opened the room. From
    // then on the encoder owns the sensor: two clients on one camera evict each
    // other in a loop, and that loop is what froze the host's screen.
    val previewOwnsCamera = live?.let { it.roomId == 0L && !it.over } ?: true
    // The camera track exists only once the room reports Live; re-ask then, or
    // the host stares at a black frame while their viewers see them fine.
    val hostView = remember(live?.roomId, live?.mediaRev, live?.phase, over) { if (over) null else LiveSession.localView(ctx) }
    var draft by remember { mutableStateOf("") }
    // The composer starts as an icon; tapping it opens the field.
    var composing by remember { mutableStateOf(false) }
    val chatFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var peopleSheet by remember { mutableStateOf(false) }
    var giftSheet by remember { mutableStateOf(false) }
    var stickerSheet by remember { mutableStateOf(false) }
    var coHostSheet by remember { mutableStateOf(false) }
    var seatsSheet by remember { mutableStateOf(false) }
    var pkSheet by remember { mutableStateOf(false) }
    var stagePerson by remember { mutableStateOf<StageMemberData?>(null) }
    // Who the gift sheet opens on (a seat tap → gift that person).
    var giftTo by remember { mutableStateOf(0L) }
    // Someone in chat: their card (tap) or the reaction row (hold); the moderators list.
    var chatUser by remember { mutableStateOf<LivePersonData?>(null) }
    var reactLine by remember { mutableStateOf<LiveChatData?>(null) }
    var modsSheet by remember { mutableStateOf(false) }
    // Viewers' hearts, floating over the chat as they land.
    val hearts = rememberLiveHearts()
    val pk = live?.pk
    val pkInvite = live?.pkInvite
    val inPk = pk != null && pk.phase != "ended"
    LaunchedEffect(composing) {
        if (composing) {
            chatFocus.requestFocus()
            keyboard?.show()
        }
    }
    val listState = rememberLazyListState()
    val gamesState = rememberLiveGamesState()
    val giftOverlay = rememberGiftOverlayState(live?.roomId ?: 0L)
    val chat = live?.chat ?: emptyList()

    // The clock is the server's: what the summary will say, not how long this
    // screen has been composed. It stops when the room is over.
    LaunchedEffect(live?.syncAtMs, over) {
        if (over) return@LaunchedEffect
        while (true) {
            elapsed = (live?.elapsedSeconds() ?: 0L).toInt()
            delay(1000)
        }
    }
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }

    BackHandler(enabled = peopleSheet || giftSheet || stickerSheet || coHostSheet || seatsSheet || pkSheet || stagePerson != null || composing) {
        peopleSheet = false; giftSheet = false; stickerSheet = false; coHostSheet = false; seatsSheet = false; pkSheet = false
        stagePerson = null; composing = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (config.isVideo) Modifier.background(Color.Black)
                else Modifier.background(CreateTokens.LiveSetupWash),
            ),
    ) {
        if (config.isVideo && camOn && !over && !inPk) {
            if (hostView != null) {
                // The engine owns the camera while publishing; paint its preview.
                // Keyed on the instance: a rejoin hands out a new renderer and the
                // factory-once AndroidView must follow it, not keep the released one.
                key(hostView) { AndroidView(factory = { hostView }, modifier = Modifier.fillMaxSize()) }
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    front = config.frontCamera,
                    enabled = active && previewOwnsCamera,
                    fallback = Color(0xFF0D0D12),
                )
            }
        } else if (!config.isVideo) {
            // Audio: the wash alone; the seats draw in the column below.
        } else if (!inPk) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0D0D12)), contentAlignment = Alignment.Center) {
                CreateIco.Cam(Color.White.copy(alpha = 0.4f), rdp(40), off = true)
            }
        }

        // Figma HUD theming: audio rooms sit on the light lavender wash, video on camera.
        val light = !config.isVideo
        val pill = if (light) Color.White.copy(alpha = 0.72f) else CreateTokens.LivePill
        val pillBorder = if (light) CreateTokens.LiveBorder else Color.White.copy(alpha = 0.14f)
        val ink = if (light) CreateTokens.LiveInk else Color.White
        val sub = if (light) CreateTokens.LiveSub else Color.White.copy(alpha = 0.6f)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Row 1 — host pill (title + clock) · coins · end (X)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(14), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(rdp(24)))
                        .background(pill)
                        .border(1.dp, pillBorder, RoundedCornerShape(rdp(24)))
                        .padding(start = rdp(5), end = rdp(12), top = rdp(5), bottom = rdp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(rdp(34))
                            .clip(CircleShape)
                            .background(CreateTokens.LiveCta),
                        contentAlignment = Alignment.Center,
                    ) { CreateIco.Users(Color.White, rdp(16)) }
                    Spacer(Modifier.width(rdp(8)))
                    Column(Modifier.weight(1f)) {
                        XaniText(config.title, Type.LabelBold, color = ink, fontSizeOverride = 12f, maxLines = 1)
                        XaniText(formatLiveClock(elapsed), Type.Micro, color = sub, fontSizeOverride = 10f)
                    }
                    CreateIco.Chevron(sub, rdp(12))
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(pill)
                        .border(1.dp, pillBorder, RoundedCornerShape(rdp(20)))
                        .padding(horizontal = rdp(10), vertical = rdp(6)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(rdp(12))
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                            .border(rdp(2), Color(0xFFD97706), CircleShape),
                    )
                    Spacer(Modifier.width(rdp(4)))
                    XaniText(formatCoins(live?.roomCoins ?: 0L), Type.LabelBold, color = Color(0xFFD97706), fontSizeOverride = 12f)
                }
                Box(
                    Modifier
                        .size(rdp(36))
                        .clip(CircleShape)
                        .background(pill)
                        .border(1.dp, pillBorder, CircleShape)
                        .pressable(onClick = { LiveSession.endLive() }),
                    contentAlignment = Alignment.Center,
                ) { CreateIco.Close(ink, rdp(14)) }
            }

            // Row 2 — viewers pill · co-host stack
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(pill)
                        .border(1.dp, pillBorder, RoundedCornerShape(rdp(14)))
                        .pressable(onClick = {
                            val id = live?.roomId ?: 0L
                            if (id != 0L) peopleSheet = true
                        })
                        .padding(horizontal = rdp(11), vertical = rdp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CreateIco.Users(ink, rdp(13))
                    Spacer(Modifier.width(rdp(5)))
                    XaniText(formatViewers(viewers), Type.LabelBold, color = ink, fontSizeOverride = 11f)
                }
                // Hearts the room has tapped so far.
                val likes = live?.likes ?: 0L
                if (likes > 0L) {
                    Spacer(Modifier.width(rdp(6)))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(pill)
                            .border(1.dp, pillBorder, RoundedCornerShape(rdp(14)))
                            .padding(horizontal = rdp(10), vertical = rdp(5)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText("❤️", Type.Label, fontSizeOverride = 10f)
                        Spacer(Modifier.width(rdp(4)))
                        XaniText(formatViewers(likes.toInt()), Type.LabelBold, color = ink, fontSizeOverride = 11f)
                    }
                }
                // Moderators: who helps run the room.
                Spacer(Modifier.width(rdp(6)))
                Box(
                    Modifier
                        .size(rdp(28))
                        .clip(CircleShape)
                        .background(pill)
                        .border(1.dp, pillBorder, CircleShape)
                        .pressable(onClick = { if ((live?.roomId ?: 0L) != 0L) modsSheet = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    VectorCanvas(
                        shapes = listOf(
                            VShape.D("M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6z", stroke(ink, 1.8f)),
                            VShape.D("M9 12l2 2 4-4", stroke(ink, 1.8f)),
                        ),
                        viewport = 24f,
                        modifier = Modifier.size(rdp(14)),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (config.isVideo) {
                    // Co-host requests: on / off. Off clears the queue for everyone.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(pill)
                            .border(1.dp, pillBorder, RoundedCornerShape(rdp(14)))
                            .pressable(onClick = { if ((live?.roomId ?: 0L) != 0L) LiveSession.setStageRequests(!requestsOpen) })
                            .padding(horizontal = rdp(10), vertical = rdp(5)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                    ) {
                        Box(
                            Modifier
                                .size(rdp(8))
                                .clip(CircleShape)
                                .background(if (requestsOpen) Color(0xFF34D399) else Color(0xFFEF4444)),
                        )
                        XaniText(
                            copy.t(if (requestsOpen) Copy.LiveRequestsOn else Copy.LiveRequestsOff),
                            Type.LabelBold, color = ink, fontSizeOverride = 10f,
                        )
                    }
                    Spacer(Modifier.width(rdp(6)))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(pill)
                            .border(1.dp, pillBorder, RoundedCornerShape(rdp(14)))
                            .pressable(onClick = { coHostSheet = true })
                            .padding(horizontal = rdp(10), vertical = rdp(5)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText("${copy.t(Copy.LiveCoHosts)} ${stage.guests.size}", Type.LabelBold, color = ink, fontSizeOverride = 10f)
                    }
                } else {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(pill)
                            .border(1.dp, pillBorder, RoundedCornerShape(rdp(14)))
                            .pressable(onClick = { if ((live?.roomId ?: 0L) != 0L) seatsSheet = true })
                            .padding(horizontal = rdp(10), vertical = rdp(5)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(copy.t(Copy.LiveSeatsCount, "n" to "${stage.seats.coerceAtLeast(config.seats)}"), Type.LabelBold, color = ink, fontSizeOverride = 10f)
                    }
                }
            }

            if (config.isVideo) {
                pkInvite?.let { inv ->
                    PkIncomingCard(
                        invite = inv,
                        copy = copy,
                        modifier = Modifier.padding(horizontal = rdp(14), vertical = rdp(6)),
                    )
                }
                if (inPk && pk != null) {
                    PkArena(
                        pk = pk,
                        myHostId = live?.room?.hostId ?: 0L,
                        myRoomId = live?.roomId ?: 0L,
                        isHost = true,
                        mediaRev = live?.mediaRev ?: 0,
                        copy = copy,
                        // Two 9:16 halves plus the score bar: the arena is about as
                        // tall as it is wide, chat and gifts take the rest.
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rdp(8), vertical = rdp(6))
                            .aspectRatio(1.04f),
                        localHostView = {
                            if (hostView != null) {
                                key(hostView) { AndroidView(factory = { hostView }, modifier = Modifier.fillMaxSize()) }
                            } else {
                                Box(Modifier.fillMaxSize().background(Color(0xFF15122A)))
                            }
                        },
                    )
                } else {
                    // The host's inbox: one request at a time, top centre.
                    if (requests.isNotEmpty()) {
                        StageRequestCard(
                            requests = requests,
                            copy = copy,
                            modifier = Modifier.padding(horizontal = rdp(14), vertical = rdp(6)),
                            onAccept = { LiveSession.decideStage(it, "accept") },
                            onReject = { LiveSession.decideStage(it, "reject") },
                        )
                    }
                    // Co-hosts, top-right: 1 2 3 4 and "…" for the rest.
                    Row(Modifier.fillMaxWidth().padding(horizontal = rdp(14), vertical = rdp(6))) {
                        Spacer(Modifier.weight(1f))
                        CoHostRail(
                            stage = stage,
                            mediaRev = live?.mediaRev ?: 0,
                            onTile = { stagePerson = it },
                            onMore = { coHostSheet = true },
                        )
                    }
                }
            } else {
                // The seats: host in the middle, guests around, growing live.
                // The stage takes the height left over, so the keyboard shrinks
                // the rings rather than pushing the composer off the screen.
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    AudioStage(
                        stage = if (stage.seats > 0) stage else stage.copy(seats = config.seats.coerceIn(2, 35)),
                        copy = copy,
                        light = light,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = rdp(10), vertical = rdp(8)),
                        onSeat = { _, member -> if (member != null) stagePerson = member },
                    )
                }
            }
            StageNotices(copy, Modifier.align(Alignment.CenterHorizontally).padding(top = rdp(4)))

            if (config.isVideo) Spacer(Modifier.weight(1f))

            if (!composing) LiveHeartsLayer(hearts, Modifier.fillMaxWidth().height(rdp(96)))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rdp(160))
                    .padding(horizontal = rdp(12)),
                verticalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                items(chat, key = { if (it.id != 0L) it.id else it.clientId.hashCode().toLong() }) { m ->
                    LiveChatLine(
                        m,
                        nameTint = hostChatTint(m.user.id),
                        background = CreateTokens.LivePill,
                        corner = rdp(14),
                        badgeSize = rdp(15),
                        nameSize = 12f,
                        onUser = { chatUser = m.user },
                        onHold = { reactLine = m },
                        onReact = { LiveSession.reactChat(m, it) },
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(12), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                if (composing || draft.isNotBlank()) {
                    // The field takes the row while a line is being written.
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(rdp(22)))
                            .background(CreateTokens.LivePill)
                            .padding(horizontal = rdp(14), vertical = rdp(10)),
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it.take(200) },
                            textStyle = Type.Caption.copy(color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                val t = draft.trim()
                                if (t.isNotEmpty()) {
                                    LiveSession.sendChat(t)
                                    draft = ""
                                }
                                composing = false
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(chatFocus),
                            decorationBox = { inner ->
                                Box {
                                    if (draft.isEmpty()) {
                                        XaniText(
                                            copy.t(Copy.CreateLiveChatHint),
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
                    LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) { keyboard?.hide(); stickerSheet = true } }) {
                        StickerMark(size = rdp(20), tint = Color.White)
                    }
                    LiveTool(active = true, onClick = {
                        val t = draft.trim()
                        if (t.isNotEmpty()) { LiveSession.sendChat(t); draft = "" }
                        composing = false
                    }) {
                        CreateIco.Music(Color.White, rdp(16))
                    }
                } else {
                    // Resting: a message icon and the tools, same as the viewer's bar.
                    LiveTool(active = true, onClick = { composing = true }) {
                        MessageMark(Color.White, rdp(19))
                    }
                    Spacer(Modifier.weight(1f))
                    // Stickers: a pick lands in chat at once.
                    LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) stickerSheet = true }) {
                        StickerMark(size = rdp(20), tint = Color.White)
                    }
                    LiveTool(active = true, onClick = { gamesState.showSheet = true }) {
                        GamesMark(size = rdp(20), tint = Color.White)
                    }
                    LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) { giftTo = 0L; giftSheet = true } }) {
                        XaniText("💎", Type.Label, fontSizeOverride = 16f)
                    }
                    LiveTool(active = micOn, onClick = { LiveSession.toggleMute() }) {
                        CreateIco.Mic(if (micOn) Color.White else Color.White.copy(alpha = 0.35f), rdp(18), off = !micOn)
                    }
                    if (config.isVideo) {
                        LiveTool(active = camOn, onClick = { LiveSession.toggleCamera() }) {
                            CreateIco.Cam(if (camOn) Color.White else Color.White.copy(alpha = 0.35f), rdp(18), off = !camOn)
                        }
                        LiveTool(active = true, onClick = {
                            if ((live?.roomId ?: 0L) != 0L && !inPk) pkSheet = true
                        }) {
                            PkMark(Color.White, rdp(22))
                        }
                    } else {
                        // "…": seats grow from here while live.
                        LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) seatsSheet = true }) {
                            MoreMark(Color.White, rdp(18))
                        }
                        // End the room from the bar, red, like a call.
                        Box(
                            Modifier
                                .size(rdp(40))
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .pressable(pressedScale = 0.9f, onClick = { LiveSession.endLive() }),
                            contentAlignment = Alignment.Center,
                        ) { HangUpMark(Color.White, rdp(20)) }
                    }
                }
            }
        }

        // Gifts landing in the room, over the picture and chrome.
        GiftOverlay(state = giftOverlay, copy = copy)

        // Someone with an entrance effect walking in — same layer as gifts.
        EntranceOverlay(roomId = live?.roomId ?: 0L, copy = copy)

        // Half-mode games layer over the broadcast — catalog sheet + game sheet.
        LiveGamesOverlay(state = gamesState, copy = copy)

        if (peopleSheet) {
            val id = live?.roomId ?: 0L
            if (id != 0L) {
                LivePeopleSheet(
                    roomId = id,
                    viewers = live?.viewers ?: 0L,
                    copy = copy,
                    onClose = { peopleSheet = false },
                )
            }
        }

        if (giftSheet) {
            val id = live?.roomId ?: 0L
            if (id != 0L) {
                // The host gifts guests: the receiver row opens on the seat tapped, else the stage.
                LiveGiftSheet(
                    roomId = id,
                    hostId = live?.room?.hostId ?: 0L,
                    copy = copy,
                    onClose = { giftSheet = false },
                    overlay = giftOverlay,
                    receiverId = giftTo,
                    stage = stage,
                    pk = pk,
                )
            }
        }

        if (coHostSheet) {
            CoHostSheet(
                stage = stage,
                isHost = true,
                copy = copy,
                onPerson = { coHostSheet = false; stagePerson = it },
                onDrop = { LiveSession.decideStage(it, "drop") },
                onClose = { coHostSheet = false },
            )
        }

        if (seatsSheet) {
            SeatsSheet(
                stage = if (stage.seats > 0) stage else stage.copy(seats = config.seats.coerceIn(2, 35)),
                copy = copy,
                onSeats = { LiveSession.setSeats(it) },
                onClose = { seatsSheet = false },
            )
        }

        if (pkSheet) {
            val id = live?.roomId ?: 0L
            if (id != 0L) {
                PkInviteSheet(roomId = id, copy = copy, onClose = { pkSheet = false })
            }
        }

        stagePerson?.let { m ->
            StagePersonSheet(
                member = m,
                isHost = true,
                copy = copy,
                onGift = { giftTo = it; giftSheet = true },
                onDrop = { LiveSession.decideStage(it, "drop") },
                onClose = { stagePerson = null },
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

        if (stickerSheet) {
            val id = live?.roomId ?: 0L
            if (id != 0L) {
                LiveStickerSheet(
                    roomId = id,
                    hostId = live?.room?.hostId ?: 0L,
                    copy = copy,
                    onClose = { stickerSheet = false },
                )
            }
        }

        // Reconnecting banner: the room is still open for viewers while the
        // server's grace runs; the host sees that, not an ending.
        if (live?.phase == LivePhase.Reconnecting) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = rdp(64))
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color(0xE6F59E0B))
                    .padding(horizontal = rdp(14), vertical = rdp(6)),
            ) {
                XaniText(copy.t(Copy.CallReconnecting), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
        }

        // The end card: server clock and counters, whichever side ended it.
        live?.let { ui ->
            if (ui.over && ui.roomId == 0L && ui.endReason == "host_ended") {
                // Cancelled before the server ever opened a room: nothing to summarise.
                LaunchedEffect(Unit) { LiveSession.dismiss(); onEnd() }
            } else if (ui.over) {
                LiveEndCard(
                    copy = copy,
                    isVideo = config.isVideo,
                    ui = ui,
                    onOk = { LiveSession.dismiss(); onEnd() },
                )
            }
        }
    }
}

@Composable
private fun LiveEndCard(copy: Copy, isVideo: Boolean, ui: LiveUi, onOk: () -> Unit) {
    val summary = ui.summary
    val settled = ui.phase == LivePhase.Ended
    val failed = ui.roomId == 0L
    val seconds = summary?.durationSeconds?.toLong() ?: ui.elapsedSeconds()
    BackHandler(enabled = settled, onBack = onOk)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .pressable(onClick = { }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = rdp(28))
                .fillMaxWidth()
                .clip(RoundedCornerShape(rdp(24)))
                .background(Color(0xFF17112B))
                .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(rdp(24)))
                .padding(horizontal = rdp(22), vertical = rdp(24)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(rdp(64))
                    .clip(CircleShape)
                    .background(CreateTokens.LiveCta),
                contentAlignment = Alignment.Center,
            ) {
                if (isVideo) CreateIco.Cam(Color.White, rdp(28)) else CreateIco.SoftMic(Color.White, rdp(28))
            }
            XaniText(
                copy.t(if (failed) Copy.LiveStartFailedTitle else Copy.LiveSummaryTitle),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 18f,
                modifier = Modifier.padding(top = rdp(14)),
            )
            val note = when {
                // The server's own reason when it gave one (SFU not enabled, already
                // live elsewhere…); the connection line only for a real transport gap.
                failed -> ui.error.ifBlank { copy.t(Copy.LiveSummaryFailed) }
                (summary?.reason ?: ui.endReason) == "lost" || (summary?.reason ?: ui.endReason) == "host_lost" -> copy.t(Copy.LiveSummaryLost)
                else -> ""
            }
            if (note.isNotEmpty()) {
                XaniText(
                    note,
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSizeOverride = 12f,
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = rdp(6)),
                )
            }
            if (!failed) {
                XaniText(
                    formatLiveClock(seconds.toInt()),
                    Type.Wordmark,
                    color = Color.White,
                    fontSizeOverride = 40f,
                    modifier = Modifier.padding(top = rdp(16)),
                )
                XaniText(
                    copy.t(Copy.LiveSummaryDuration),
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSizeOverride = 12f,
                )
                Row(
                    Modifier
                        .padding(top = rdp(18))
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                ) {
                    SummaryStat(
                        label = copy.t(Copy.LiveSummaryPeak),
                        value = "${summary?.peakViewers ?: ui.peakViewers}",
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStat(
                        label = copy.t(Copy.LiveSummaryJoins),
                        value = "${summary?.totalJoins ?: 0}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                Modifier
                    .padding(top = rdp(22))
                    .fillMaxWidth()
                    .height(rdp(46))
                    .clip(RoundedCornerShape(rdp(23)))
                    .background(
                        if (settled) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                        else SolidColor(Color.White.copy(alpha = 0.12f)),
                    )
                    .pressable(enabled = settled, onClick = onOk),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(
                    if (settled) copy.t(Copy.LiveOk) else "…",
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = 15f,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(rdp(14)))
            .padding(vertical = rdp(12)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(value, Type.LabelBold, color = Color.White, fontSizeOverride = 20f)
        XaniText(label, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 11f, modifier = Modifier.padding(top = rdp(2)))
    }
}

/** `0xFFRRGGBB` banner pick → `#RRGGBB` for the server; the listing paints the same tint. */
private fun hexOf(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)

@Composable
private fun LiveTool(active: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(rdp(42))
            .clip(CircleShape)
            .background(if (active) CreateTokens.LivePill else Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

private fun formatViewers(n: Int): String =
    if (n >= 1000) "%.1fK".format(n / 1000f) else "$n"

private fun formatLiveClock(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun hostChatTint(userId: Long): Color {
    val palette = listOf(
        Color(0xFFA78BFA), Color(0xFFF472B6), Color(0xFF34D399),
        Color(0xFF60A5FA), Color(0xFFFBBF24), Color(0xFFF87171),
    )
    val i = ((userId % palette.size) + palette.size) % palette.size
    return palette[i.toInt()]
}
