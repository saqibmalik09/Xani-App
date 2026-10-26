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
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.lens.Lens
import app.xani.core.lens.LensCatalog
import app.xani.core.lens.LensTray
import app.xani.core.net.LiveChatData
import app.xani.core.net.LivePersonData
import app.xani.core.net.StageData
import app.xani.core.net.StageMemberData
import app.xani.feature.call.AudioStage
import app.xani.feature.call.CoHostRail
import app.xani.feature.call.CoHostSheet
import app.xani.feature.call.GiftBoxMark
import app.xani.feature.call.HangUpMark
import app.xani.feature.call.RaiseHandMark
import app.xani.feature.inbox.ChatIco
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
import app.xani.feature.call.ModeratorMark
import app.xani.feature.call.MoreMark
import app.xani.feature.call.PkArena
import app.xani.feature.call.PkIncomingCard
import app.xani.feature.call.PkInviteSheet
import app.xani.feature.call.PkMark
import app.xani.feature.call.SeatsSheet
import app.xani.feature.call.StageNotices
import app.xani.feature.call.StagePersonSheet
import app.xani.feature.call.StageRequestCard
import app.xani.feature.call.VideoSurface
import app.xani.feature.home.LivePeopleSheet
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.xani.feature.gift.GameWinOverlay
import app.xani.feature.gift.GiftOverlay
import app.xani.feature.gift.GiftingPanel
import app.xani.feature.gift.LiveGiftSheet
import app.xani.feature.gift.LiveStickerSheet
import app.xani.feature.gift.StickerMark
import app.xani.feature.gift.formatCoins
import app.xani.feature.gift.rememberGiftOverlayState
import app.xani.feature.home.LiveShare
import app.xani.feature.inbox.CameraPreview
import app.xani.feature.main.GamesMark
import app.xani.feature.social.NameBadge
import app.xani.i18n.Copy
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.KeepScreenOn
import app.xani.ui.components.LiveSheet
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
    onMinimize: () -> Unit,
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
    val brand = LocalBrand.current
    val scope = rememberCoroutineScope()
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
            seats = if (config.isVideo) 0 else config.seats.coerceIn(2, StageData.AudioSeatMax),
        )
        onDispose {
            LiveSession.leave()
            Lens.live.reset()
        }
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
    val focus = LocalFocusManager.current
    var peopleSheet by remember { mutableStateOf(false) }
    var giftSheet by remember { mutableStateOf(false) }
    // The host's own gift record for this live: opens from the 💎 counter.
    var giftRecords by remember { mutableStateOf(false) }
    var stickerSheet by remember { mutableStateOf(false) }
    var coHostSheet by remember { mutableStateOf(false) }
    var seatsSheet by remember { mutableStateOf(false) }
    var pkSheet by remember { mutableStateOf(false) }
    // Overflow menu for the video bar's secondary tools (stickers, games,
    // beauty, PK) so the bar never overflows on narrow phones.
    var toolsMenu by remember { mutableStateOf(false) }
    var stagePerson by remember { mutableStateOf<StageMemberData?>(null) }
    // Who the gift sheet opens on (a seat tap → gift that person).
    var giftTo by remember { mutableStateOf(0L) }
    var lensOpen by remember { mutableStateOf(false) }
    // Someone in chat: their card (tap) or the reaction row (hold); the moderators list.
    var chatUser by remember { mutableStateOf<LivePersonData?>(null) }
    var reactLine by remember { mutableStateOf<LiveChatData?>(null) }
    var modsSheet by remember { mutableStateOf(false) }
    // The close button asks before it ends the room.
    var exitSheet by remember { mutableStateOf(false) }
    // Viewers' hearts, floating over the chat as they land.
    val hearts = rememberLiveHearts()
    val pk = live?.pk
    val pkInvite = live?.pkInvite
    val inPk = pk != null && pk.phase != "ended"
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
    val listState = rememberLazyListState()
    val gamesState = rememberLiveGamesState()
    val giftOverlay = rememberGiftOverlayState(live?.roomId ?: 0L)
    val chat = live?.chat ?: emptyList()

    // The clock is the ledger's: verified seconds from the last server ack,
    // advanced on the monotonic clock while the server is counting and frozen
    // by itself once the last ack is older than the allowance. It is the
    // number the end card, My Live and the dashboard all show — never how
    // long this screen has been composed. Ticks land on second boundaries so
    // the digits never skip or stutter.
    var counting by remember { mutableStateOf(true) }
    LaunchedEffect(live?.timeAtMs, live?.time?.counting, over) {
        if (over) return@LaunchedEffect
        while (true) {
            val ui = live
            val ms = ui?.verifiedMs() ?: 0L
            elapsed = (ms / 1000L).toInt()
            counting = ui?.time == null || ui.timeCounting()
            delay(1000L - (ms % 1000L))
        }
    }
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }
    // Rewards: the server's projection of the next threshold (re-read when a
    // reward lands and every minute), the countdown beside the clock derived
    // from the same verified figure the clock shows, and the landing toast.
    val onAir = !over && (live?.roomId ?: 0L) > 0L
    val rewardEl = rememberRewardEligibility(isVideo = config.isVideo, live = onAir)
    val rewardLine = if (onAir) rewardHudText(copy, rewardEl, elapsed * 1000L) else null
    val rewardToast = rememberRewardToasts(live = onAir)

    BackHandler(enabled = peopleSheet || giftSheet || stickerSheet || coHostSheet || seatsSheet || pkSheet || toolsMenu || exitSheet || stagePerson != null || composing || lensOpen) {
        if (toolsMenu) toolsMenu = false
        else if (lensOpen) lensOpen = false
        else {
            peopleSheet = false; giftSheet = false; stickerSheet = false; coHostSheet = false; seatsSheet = false; pkSheet = false
            stagePerson = null; composing = false; exitSheet = false
        }
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
                VideoSurface(hostView, Modifier.fillMaxSize())
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    front = config.frontCamera,
                    enabled = active && previewOwnsCamera,
                    fallback = Color(0xFF0D0D12),
                    lens = Lens.live,
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

        // Typing: a tap anywhere off the composer puts the keyboard away. Sits
        // under the chrome below, so the bar's own buttons still get the tap.
        if (composing) {
            Box(Modifier.fillMaxSize().pressable(pressedScale = 1f) { composing = false })
        }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Amber and still while the server is not crediting:
                            // the host sees the exact number the record holds.
                            if (!counting) {
                                Box(Modifier.size(rdp(6)).clip(CircleShape).background(Color(0xFFF59E0B)))
                                Spacer(Modifier.width(rdp(4)))
                            }
                            XaniText(
                                formatLiveClock(elapsed),
                                Type.Micro,
                                color = if (counting) sub else Color(0xFFF59E0B),
                                fontSizeOverride = 10f,
                            )
                            if (!counting) {
                                Spacer(Modifier.width(rdp(4)))
                                XaniText(
                                    copy.t(Copy.LiveTimeNotCounting),
                                    Type.Micro,
                                    color = Color(0xFFF59E0B),
                                    fontSizeOverride = 10f,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (rewardLine != null) {
                            XaniText(
                                rewardLine,
                                Type.Micro,
                                color = if (light) Color(0xFFB45309) else Color(0xFFFBBF24),
                                fontSizeOverride = 10f,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    CreateIco.Chevron(sub, rdp(12))
                }
                // Everyone in the room — the audience plus the host — top
                // right where a user looks for the room's size. Tap: the roster.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(pill)
                        .border(1.dp, pillBorder, RoundedCornerShape(rdp(20)))
                        .pressable(onClick = { if ((live?.roomId ?: 0L) != 0L) peopleSheet = true })
                        .padding(horizontal = rdp(10), vertical = rdp(6)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CreateIco.Users(ink, rdp(13))
                    Spacer(Modifier.width(rdp(4)))
                    XaniText(
                        formatViewers(if ((live?.roomId ?: 0L) != 0L) viewers + 1 else viewers),
                        Type.LabelBold, color = ink, fontSizeOverride = 12f,
                    )
                }
                Box(
                    Modifier
                        .size(rdp(36))
                        .clip(CircleShape)
                        .background(pill)
                        .border(1.dp, pillBorder, CircleShape)
                        // A live room is never closed by one stray tap: the
                        // sheet asks first, and offers the background instead.
                        // Before the server has a room there is nothing to end.
                        .pressable(
                            onClick = {
                                if ((live?.roomId ?: 0L) != 0L) exitSheet = true else LiveSession.endLive()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) { CreateIco.Close(ink, rdp(14)) }
            }

            // Row 2 — diamonds pill · co-host stack
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Diamonds credited to the host after the gift split — never the
                // senders' coin total. Tapping opens the live's gift record.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(pill)
                        .border(1.dp, pillBorder, RoundedCornerShape(rdp(14)))
                        .pressable(onClick = { if ((live?.roomId ?: 0L) != 0L) giftRecords = true })
                        .padding(horizontal = rdp(11), vertical = rdp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText("💎", Type.Label, fontSizeOverride = 11f)
                    Spacer(Modifier.width(rdp(5)))
                    XaniText(formatCoins(live?.roomDiamonds ?: 0L), Type.LabelBold, color = Color(0xFF7C3AED), fontSizeOverride = 11f)
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
                // Moderators: who helps run the room. Video keeps this behind
                // the "…", beside the co-host rules, so the picture is not
                // covered by chrome; audio has no overflow card, so the shield
                // stays inline there.
                if (!config.isVideo) {
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
                        ModeratorMark(ink, rdp(14))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (config.isVideo) {
                    // Co-host request and camera rules live in the "…" menu.
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
                                VideoSurface(hostView, Modifier.fillMaxSize())
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
                }
            } else {
                // The seats: host top-left, then 1…n in a 5–6 column grid.
                // Leftover height is this stage (scrolls), so chat stays put.
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    AudioStage(
                        stage = if (stage.seats > 0) stage else stage.copy(seats = config.seats.coerceIn(2, StageData.AudioSeatMax)),
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

            // Co-hosts: 1 2 3 4 down the right edge, the last tile just above
            // the chat, so they sit opposite the comments rather than over my
            // own picture. Below the weighted spacer, so the stack rides the
            // bottom cluster and stays where it is as the room fills.
            if (config.isVideo && !(inPk && pk != null) && stage.guests.isNotEmpty()) {
                CoHostRail(
                    stage = stage,
                    mediaRev = live?.mediaRev ?: 0,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = rdp(14))
                        .padding(bottom = rdp(6)),
                    onTile = { stagePerson = it },
                    onMore = { coHostSheet = true },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (lensOpen && config.isVideo) rdp(96) else rdp(160))
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
                        likeLabel = copy.t(Copy.LiveSentLike),
                        enterLabel = copy.t(Copy.LiveEnteredRoom),
                        leaveLabel = copy.t(Copy.LiveLeftRoom),
                    )
                }
            }

            if (lensOpen && config.isVideo) {
                LensTray(
                    controller = Lens.live,
                    copy = copy,
                    compact = true,
                    initial = LensCatalog.Category.Background,
                    modifier = Modifier.padding(bottom = rdp(4)),
                )
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
                        ChatIco.Send(Color.White, rdp(17))
                    }
                } else {
                    // Resting: a message icon and the tools, same as the viewer's bar.
                    LiveTool(active = true, onClick = { composing = true }) {
                        MessageMark(Color.White, rdp(19))
                    }
                    Spacer(Modifier.weight(1f))
                    if (config.isVideo) {
                        // Video has more controls than fit the bar, so only the
                        // primaries stay inline — gift, mic, cam — and the rest
                        // (stickers, games, beauty, PK, co-host rules) live behind the "…".
                        LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) { giftTo = 0L; giftSheet = true } }) {
                            GiftBoxMark(Color.White, rdp(19))
                        }
                        LiveTool(active = micOn, onClick = { LiveSession.toggleMute() }) {
                            CreateIco.Mic(if (micOn) Color.White else Color.White.copy(alpha = 0.35f), rdp(18), off = !micOn)
                        }
                        LiveTool(active = camOn, onClick = { LiveSession.toggleCamera() }) {
                            CreateIco.Cam(if (camOn) Color.White else Color.White.copy(alpha = 0.35f), rdp(18), off = !camOn)
                        }
                        // Front/back flip, inline: a host mid-broadcast must not
                        // dig through a menu to turn the camera around.
                        LiveTool(active = camOn, onClick = { if (camOn) LiveSession.switchCamera() }) {
                            CreateIco.FlipCam(if (camOn) Color.White else Color.White.copy(alpha = 0.35f), rdp(18))
                        }
                        LiveTool(active = toolsMenu, onClick = { toolsMenu = !toolsMenu }) {
                            MoreMark(Color.White, rdp(18))
                        }
                    } else {
                        // Stickers: a pick lands in chat at once.
                        LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) stickerSheet = true }) {
                            StickerMark(size = rdp(20), tint = Color.White)
                        }
                        LiveTool(active = true, onClick = { gamesState.showSheet = true }) {
                            GamesMark(size = rdp(20), tint = Color.White)
                        }
                        LiveTool(active = true, onClick = { if ((live?.roomId ?: 0L) != 0L) { giftTo = 0L; giftSheet = true } }) {
                            GiftBoxMark(Color.White, rdp(19))
                        }
                        LiveTool(active = micOn, onClick = { LiveSession.toggleMute() }) {
                            CreateIco.Mic(if (micOn) Color.White else Color.White.copy(alpha = 0.35f), rdp(18), off = !micOn)
                        }
                        // Share before seats: audio has no tools overflow card.
                        LiveTool(active = true, onClick = {
                            val id = live?.roomId ?: 0L
                            if (id != 0L) LiveShare.share(ctx, scope, copy, brand, id)
                        }) {
                            VectorCanvas(
                                shapes = listOf(
                                    VShape.D(
                                        "M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8M16 6l-4-4-4 4M12 2v13",
                                        stroke(Color.White, 1.7f),
                                    ),
                                ),
                                viewport = 24f,
                                modifier = Modifier.size(rdp(18)),
                            )
                        }
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
        GiftOverlay(state = giftOverlay, copy = copy, onUser = { chatUser = it })

        // A live-time reward just landed: one toast per ledger row, over the chrome.
        rewardToast?.let { t ->
            RewardToastView(copy, t, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = rdp(62), start = rdp(16), end = rdp(16)))
        }

        // Someone with an entrance effect walking in — same layer as gifts.
        EntranceOverlay(roomId = live?.roomId ?: 0L, copy = copy)

        // A game win somewhere on the platform — a short centered strip.
        GameWinOverlay(roomId = live?.roomId ?: 0L, copy = copy)

        // Half-mode games layer over the broadcast — catalog sheet + game sheet.
        LiveGamesOverlay(state = gamesState, copy = copy)

        if (peopleSheet) {
            val id = live?.roomId ?: 0L
            if (id != 0L) {
                LivePeopleSheet(
                    roomId = id,
                    viewers = live?.viewers ?: 0L,
                    copy = copy,
                    // Their card takes the list's place: the host can gift or
                    // moderate the person they tapped without leaving the room.
                    onUser = { peopleSheet = false; chatUser = it },
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
                stage = if (stage.seats > 0) stage else stage.copy(seats = config.seats.coerceIn(2, StageData.AudioSeatMax)),
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

        if (exitSheet) {
            LiveHostExitSheet(
                copy = copy,
                onMinimize = { exitSheet = false; onMinimize() },
                onEndLive = { exitSheet = false; LiveSession.endLive() },
                onClose = { exitSheet = false },
            )
        }

        // Overflow for the video bar's secondary tools: a tap-away scrim and a
        // small card anchored above the "…" — not a full sheet, so a quick pick
        // (sticker, game, beauty, PK) is one tap and a tap outside closes it.
        if (toolsMenu && config.isVideo) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pressable(pressedScale = 1f, onClick = { toolsMenu = false }),
            )
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(end = rdp(14), bottom = rdp(68))
                    .width(rdp(224))
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color(0xF017112B))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(rdp(16)))
                    .padding(vertical = rdp(6)),
            ) {
                LiveToolMenuItem(label = copy.t(Copy.LiveStickers), onClick = {
                    toolsMenu = false
                    if ((live?.roomId ?: 0L) != 0L) stickerSheet = true
                }) { StickerMark(size = rdp(20), tint = Color.White) }
                LiveToolMenuItem(label = copy.t(Copy.TabGames), onClick = {
                    toolsMenu = false
                    gamesState.showSheet = true
                }) { GamesMark(size = rdp(20), tint = Color.White) }
                LiveToolMenuItem(label = copy.t(Copy.CreateBeauty), active = lensOpen, onClick = {
                    toolsMenu = false
                    lensOpen = !lensOpen
                }) { CreateIco.Beauty(Color.White, rdp(18)) }
                if (!inPk) {
                    LiveToolMenuItem(label = copy.t(Copy.PkTitle), onClick = {
                        toolsMenu = false
                        if ((live?.roomId ?: 0L) != 0L) pkSheet = true
                    }) { PkMark(Color.White, rdp(20)) }
                } else if (pk?.fighting == true) {
                    // The same door out of the fight that the arena's chip is,
                    // in the place the host already looks for PK. Either side
                    // may stop it, so this is on both hosts' menus.
                    LiveToolMenuItem(label = copy.t(Copy.PkEnd), onClick = {
                        toolsMenu = false
                        LiveSession.endPk()
                    }) { PkMark(Color.White, rdp(20)) }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = rdp(12), vertical = rdp(4)).height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                // Co-host rules: toggles stay open so the host sees the new state.
                // Requests off clears the queue for everyone.
                LiveToolMenuItem(
                    label = copy.t(if (requestsOpen) Copy.LiveRequestsOn else Copy.LiveRequestsOff),
                    on = requestsOpen,
                    onClick = { if ((live?.roomId ?: 0L) != 0L) LiveSession.setStageRequests(!requestsOpen) },
                ) { RaiseHandMark(Color.White, rdp(20)) }
                // Whether co-hosts may turn their camera on; off by default,
                // enforced by the seat token the server mints.
                val guestCam = stage.guestCam
                LiveToolMenuItem(
                    label = copy.t(if (guestCam) Copy.LiveGuestCamOn else Copy.LiveGuestCamOff),
                    on = guestCam,
                    onClick = { if ((live?.roomId ?: 0L) != 0L) LiveSession.setGuestCam(!guestCam) },
                ) { CreateIco.Cam(Color.White, rdp(18), off = !guestCam) }
                // Who helps run the room: alongside the co-host rules rather
                // than on the broadcast's top bar.
                LiveToolMenuItem(label = copy.t(Copy.LiveModerators), onClick = {
                    toolsMenu = false
                    if ((live?.roomId ?: 0L) != 0L) modsSheet = true
                }) { ModeratorMark(Color.White, rdp(20)) }
                Box(Modifier.fillMaxWidth().padding(horizontal = rdp(12), vertical = rdp(4)).height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                LiveToolMenuItem(label = copy.t(Copy.LiveShare), onClick = {
                    val id = live?.roomId ?: 0L
                    if (id == 0L) return@LiveToolMenuItem
                    toolsMenu = false
                    LiveShare.share(ctx, scope, copy, brand, id)
                }) {
                    VectorCanvas(
                        shapes = listOf(
                            VShape.D(
                                "M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8M16 6l-4-4-4 4M12 2v13",
                                stroke(Color.White, 1.7f),
                            ),
                        ),
                        viewport = 24f,
                        modifier = Modifier.size(rdp(20)),
                    )
                }
            }
        }

        // The host's gift record, scoped to this live: who sent, what landed,
        // in diamonds — the same panel a viewer sees on the host card.
        if (giftRecords) {
            val hostId = live?.room?.hostId ?: 0L
            val roomId = live?.roomId ?: 0L
            if (hostId != 0L && roomId != 0L) {
                LiveSheet(onClose = { giftRecords = false }) { _ ->
                    GiftingPanel(
                        userId = hostId,
                        copy = copy,
                        roomId = roomId,
                        modifier = Modifier.padding(top = rdp(16), bottom = rdp(12)),
                    )
                }
            }
        }

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
                XaniText(
                    if (counting) copy.t(Copy.CallReconnecting) else copy.t(Copy.LiveTimeNotCounting),
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = 12f,
                )
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
    // The headline is the ledger's figure: the server's when it answered,
    // else the last verified number this screen was showing. Wall time
    // (start → end) is secondary and only shown when it differs.
    val verifiedMs = summary?.verifiedMs?.takeIf { it >= 0L } ?: ui.verifiedMs()
    val seconds = verifiedMs / 1000L
    val wallSeconds = summary?.durationSeconds?.toLong() ?: ui.elapsedSeconds()
    val pausedMs = summary?.pausedMs ?: ui.pausedMs
    val todayMs = summary?.todayMs ?: -1L
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
                    copy.t(Copy.LiveSummaryVerified),
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSizeOverride = 12f,
                )
                // Why the number may be smaller than the time on air, and the
                // day's running total — the same lines My Live shows.
                val lines = buildList {
                    if (wallSeconds - seconds > 1L) add(copy.t(Copy.LiveSummaryWall, "t" to formatLiveClock(wallSeconds.toInt())))
                    if (pausedMs >= 1000L) add(copy.t(Copy.LiveSummaryPaused, "t" to formatLiveClock((pausedMs / 1000L).toInt())))
                    if (todayMs >= 0L) add(copy.t(Copy.LiveSummaryToday, "t" to formatLiveClock((todayMs / 1000L).toInt())))
                }
                if (lines.isNotEmpty()) {
                    Column(
                        Modifier.padding(top = rdp(10)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        lines.forEach { line ->
                            XaniText(
                                line,
                                Type.Caption,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSizeOverride = 12f,
                                align = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
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
                    // What the host was actually credited after the split,
                    // not the senders' coin total.
                    SummaryStat(
                        label = copy.t(Copy.LiveHostDiamonds),
                        value = "💎 " + formatCoins(summary?.giftDiamonds ?: ui.roomDiamonds),
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

/** One row in the video bar's overflow card: icon · label, full-width tap. */
@Composable
private fun LiveToolMenuItem(
    label: String,
    active: Boolean = false,
    /** A toggle row: green dot on, red off. Null: a plain action. */
    on: Boolean? = null,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(10)))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(10)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(12)),
    ) {
        Box(Modifier.size(rdp(22)), contentAlignment = Alignment.Center) { icon() }
        XaniText(
            label,
            Type.LabelBold,
            color = if (active) Color(0xFFB794F6) else Color.White,
            fontSizeOverride = 13f,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (on != null) {
            Box(
                Modifier
                    .size(rdp(8))
                    .clip(CircleShape)
                    .background(if (on) Color(0xFF34D399) else Color(0xFFEF4444)),
            )
        }
    }
}

/**
 * What the close button opens. Ending a live is irreversible — the room shuts
 * for every viewer and the clock stops — so one misplaced tap must not do it.
 * Minimize is the real alternative, not a softener: the foreground service
 * owns capture, so the broadcast carries on while the host uses the phone.
 */
@Composable
private fun LiveHostExitSheet(
    copy: Copy,
    onMinimize: () -> Unit,
    onEndLive: () -> Unit,
    onClose: () -> Unit,
) {
    LiveSheet(onClose = onClose, background = SolidColor(Color(0xFF17112B))) { close ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(18))
                .padding(top = rdp(6), bottom = rdp(28)),
        ) {
            XaniText(
                copy.t(Copy.LiveHostExitTitle),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 17f,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = rdp(16)),
            )
            HostExitOption(
                title = copy.t(Copy.LiveMinimize),
                sub = copy.t(Copy.LiveHostMinimizeSub),
                tint = Color(0xFFB794F6),
                // Straight to the handler, not through the slide-out: the task
                // leaves the foreground on this tap and a half-run animation
                // would still be on screen when the host comes back.
                onClick = onMinimize,
            ) {
                VectorCanvas(
                    shapes = listOf(
                        VShape.D(
                            "M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3",
                            stroke(Color(0xFFB794F6), 1.9f),
                        ),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(20)),
                )
            }
            Spacer(Modifier.height(rdp(10)))
            HostExitOption(
                title = copy.t(Copy.LiveHostEnd),
                sub = copy.t(Copy.LiveHostEndSub),
                tint = Color(0xFFEF4444),
                onClick = onEndLive,
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
                copy.t(Copy.LiveHostStay),
                Type.LabelBold,
                color = Color.White.copy(alpha = 0.6f),
                fontSizeOverride = 14f,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = rdp(14))
                    .pressable(onClick = { close() })
                    .padding(rdp(8)),
            )
        }
    }
}

@Composable
private fun HostExitOption(
    title: String,
    sub: String,
    tint: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(16)))
            .background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(rdp(16)))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(16), vertical = rdp(14)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(14)),
    ) {
        Box(Modifier.size(rdp(28)), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f)) {
            XaniText(title, Type.LabelBold, color = tint, fontSizeOverride = 15f)
            XaniText(
                sub,
                Type.Caption,
                color = Color.White.copy(alpha = 0.55f),
                fontSizeOverride = 12f,
                modifier = Modifier.padding(top = rdp(2)),
            )
        }
    }
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
