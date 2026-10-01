package app.xani.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.xani.core.device.DeviceClass
import app.xani.core.device.rememberReduceMotion
import app.xani.core.net.AuthState
import app.xani.core.net.Xani
import app.xani.feature.notify.PushRouter
import app.xani.feature.social.FollowStore
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.SearchIcon
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.stroke

private enum class HomeOverlay { None, Search, Comments, Sound }

/**
 * For You / Following / Live. Cache-first: the first page is a typed seed so the
 * surface never opens on an error wall. Player pool is not allocated here — posters
 * are GPU gradients, so a 2 GB device can sit on this screen all day.
 */
@Composable
fun HomeScreen(
    active: Boolean,
    device: DeviceClass,
    copy: Copy,
    onImmersive: (Boolean) -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()

    var lane by rememberSaveable { mutableStateOf(HomeLane.ForYou) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(HomeOverlay.None) }
    var overlayPost by remember { mutableStateOf<FeedPost?>(null) }
    var viewing by remember { mutableStateOf<LiveTile?>(null) }
    var minimized by remember { mutableStateOf<LiveTile?>(null) }
    // Private rooms: the tap asks for the password first; the answer travels
    // with the viewer and comes back here when the server refuses it.
    var askingPassword by remember { mutableStateOf<LiveTile?>(null) }
    var passwordWrong by remember { mutableStateOf(false) }
    var roomPassword by remember { mutableStateOf("") }
    val openLive: (LiveTile) -> Unit = { tile ->
        minimized = null
        if (tile.locked) {
            viewing = null
            passwordWrong = false
            askingPassword = tile
        } else {
            roomPassword = ""
            viewing = tile
        }
    }

    // Comments closing: the sheet is animating away, so the video is already
    // growing back to full while it slides.
    var commentsClosing by remember { mutableStateOf(false) }

    // A full-screen live broadcast owns the screen — and so does the comments
    // sheet: the shell drops its bottom nav while one is open, so the sheet's
    // own input bar sits on the bottom edge instead of under the nav.
    LaunchedEffect(viewing, overlay) { onImmersive(viewing != null || overlay == HomeOverlay.Comments) }

    // While comments are open the card keeps playing, scaled into the strip the
    // sheet leaves free on top — the TikTok move.
    val commentsUp = overlay == HomeOverlay.Comments && !commentsClosing
    val cardScale by animateFloatAsState(
        targetValue = if (commentsUp) 0.40f else 1f,
        animationSpec = tween(if (commentsUp) 420 else 380, easing = FastOutSlowInEasing),
        label = "commentShrink",
    )

    // The feed: disk head paints first, the network replaces it, pages stream in
    // ahead of the scroll. Liked/saved come from the cards themselves (server
    // truth + the optimistic flip), so one event updates every surface.
    val feedState by FeedRepo.state.collectAsState()
    val authState by Xani.session.state.collectAsState()
    LaunchedEffect((authState as? AuthState.SignedIn)?.user?.id ?: 0L) {
        val user = (authState as? AuthState.SignedIn)?.user
        if (user == null) {
            if (authState is AuthState.SignedOut) FeedRepo.signedOut()
            return@LaunchedEffect
        }
        FeedRepo.signedIn(user.id)
        FeedRepo.ensureLoaded()
    }
    LaunchedEffect(active, lane) {
        if (active && lane != HomeLane.Live) FeedRepo.ensureLoaded()
        // Off Home (or in Live): nothing here is showing a post, so stop paying
        // for its realtime channel. The pager re-takes it on the next settle.
        if (!active || lane == HomeLane.Live) FeedRepo.unwatch()
    }

    // Follow state is one app-wide relation (feed card, search hit, follower
    // list all flip together); the card's own flag is the server's first word.
    val relations by FollowStore.relations.collectAsState()
    val followedSet = remember(feedState.items, relations) {
        feedState.items.asSequence()
            .filter { relations[it.authorId]?.following ?: it.following }
            .map { it.creator.handle }
            .toSet()
    }
    val posts: List<FeedPost> = when (lane) {
        HomeLane.Live -> emptyList()
        HomeLane.Following -> remember(feedState.items, followedSet) {
            feedState.items.filter { it.creator.handle in followedSet }
        }
        HomeLane.ForYou -> feedState.items
    }
    val likedSet = remember(posts) { posts.asSequence().filter { it.liked }.map { it.id }.toSet() }
    val savedSet = remember(posts) { posts.asSequence().filter { it.saved }.map { it.id }.toSet() }

    // Live lane: the server pushes every go-live / end / audience change while
    // the tab is on screen; the list is patched in place, never re-polled.
    val directory by LiveDirectory.state.collectAsState()
    val signedIn = authState is AuthState.SignedIn
    LaunchedEffect(active, lane, signedIn) {
        if (active && lane == HomeLane.Live && signedIn) LiveDirectory.watch()
    }
    val liveTiles: List<LiveTile> = remember(directory.rooms) { directory.rooms.map { it.toTile() } }
    // "X is live" tapped: open that room once the directory lists it; a room
    // that already ended never appears, so the request lapses.
    val liveRequest by PushRouter.liveRequest.collectAsState()
    LaunchedEffect(liveRequest, liveTiles) {
        val want = liveRequest ?: return@LaunchedEffect
        liveTiles.firstOrNull { it.roomId == want }?.let {
            PushRouter.liveRequest.value = null
            openLive(it)
        }
    }
    LaunchedEffect(liveRequest) {
        if (liveRequest == null) return@LaunchedEffect
        kotlinx.coroutines.delay(10_000)
        PushRouter.liveRequest.value = null
    }
    // A viewer keeps following the room it opened even as the card moves or leaves the list.
    LaunchedEffect(liveTiles) {
        viewing?.let { v -> liveTiles.firstOrNull { it.id == v.id }?.let { if (it != v) viewing = it } }
        minimized?.let { m -> liveTiles.firstOrNull { it.id == m.id }?.let { if (it != m) minimized = it } }
    }

    // Leaving the surface releases gesture states — no stale pause/2× on return.
    LaunchedEffect(active) {
        if (!active) {
            holding = false
            paused = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (lane) {
            HomeLane.Live -> {
                LiveBrowsePane(
                    tiles = liveTiles,
                    copy = copy,
                    loaded = directory.loaded,
                    onOpen = openLive,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = rdp(56)),
                )
            }
            HomeLane.Following, HomeLane.ForYou -> {
                if (posts.isEmpty()) {
                    when {
                        lane == HomeLane.Following -> EmptyFollowing(copy)
                        feedState.refreshing && !feedState.loaded -> Unit // black until the head lands (sub-second)
                        else -> EmptyFeed(copy, offline = feedState.offline, onRetry = { FeedRepo.refresh() })
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Anchored top-center: the card shrinks up into
                                // the window above the comments sheet and keeps
                                // playing there (the player is a TextureView, so
                                // scaling is free).
                                scaleX = cardScale
                                scaleY = cardScale
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            },
                    ) {
                    FeedPager(
                        posts = posts,
                        active = active && (overlay == HomeOverlay.None || overlay == HomeOverlay.Comments),
                        device = device,
                        reduceMotion = reduceMotion,
                        liked = likedSet,
                        saved = savedSet,
                        followed = followedSet,
                        muted = muted,
                        paused = paused,
                        holding = holding,
                        copy = copy,
                        onPauseToggle = { paused = !paused },
                        onHold = { holding = it },
                        onMute = { muted = !muted },
                        onLike = { id -> FeedRepo.toggleLike(id) },
                        onLikeOn = { id -> FeedRepo.likeOn(id) },
                        onSave = { id -> FeedRepo.toggleSave(id) },
                        onFollow = { handle ->
                            posts.firstOrNull { it.creator.handle == handle }?.let { p ->
                                FollowStore.set(p.authorId, p.creator.handle !in followedSet)
                            }
                        },
                        onPage = {
                            if (it != page) paused = false
                            page = it
                            if (lane == HomeLane.ForYou) FeedRepo.onPage(it)
                        },
                        onComments = { post ->
                            overlayPost = post
                            commentsClosing = false
                            overlay = HomeOverlay.Comments
                        },
                        onSound = { post ->
                            overlayPost = post
                            overlay = HomeOverlay.Sound
                        },
                        onOpenProfile = { post -> PersonRouter.open(post.authorId) },
                    )
                    }
                }
            }
        }

        // Top bar hides while scrubbing at 2×
        val topAlpha by animateFloatAsState(
            targetValue = if (holding) 0f else 1f,
            animationSpec = tween(180),
            label = "topbar",
        )
        if (topAlpha > 0.01f) {
            HomeTopBar(
                lane = lane,
                onLane = {
                    lane = it
                    page = 0
                    paused = false
                },
                onSearch = { overlay = HomeOverlay.Search },
                copy = copy,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = topAlpha },
            )
        }

        // PiP mini player — Live lane only, hidden while a stream is fullscreen
        val pip = minimized
        if (lane == HomeLane.Live && pip != null && viewing == null) {
            LivePipCard(
                stream = pip,
                copy = copy,
                // A locked room whose password was cleared by a public one in between asks again.
                onRestore = { if (pip.locked && roomPassword.isEmpty()) openLive(pip) else { viewing = pip; minimized = null } },
                onDismiss = { minimized = null },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = rdp(14), bottom = rdp(96)),
            )
        }

        // Live viewer — full-screen slide-in
        viewing?.let { stream ->
            LiveViewerScreen(
                stream = stream,
                copy = copy,
                onSwitch = { dir ->
                    val others = liveTiles.filter { it.id != stream.id }
                    if (others.isNotEmpty()) {
                        val idx = liveTiles.indexOfFirst { it.id == stream.id }
                        // The current room may already be gone from the list; step from where it was.
                        val base = if (idx >= 0) idx else 0
                        val next = ((base + dir) % liveTiles.size + liveTiles.size) % liveTiles.size
                        openLive(liveTiles.getOrNull(next)?.takeIf { it.id != stream.id } ?: others.first())
                    }
                },
                onMinimize = {
                    minimized = stream
                    viewing = null
                },
                onClose = { viewing = null },
                password = roomPassword,
                onPasswordRejected = { wrong ->
                    viewing = null
                    passwordWrong = wrong
                    askingPassword = stream
                },
            )
        }

        askingPassword?.let { tile ->
            LivePasswordDialog(
                stream = tile,
                copy = copy,
                wrong = passwordWrong,
                onJoin = { pw ->
                    roomPassword = pw
                    askingPassword = null
                    viewing = tile
                },
                onCancel = { askingPassword = null },
            )
        }

        // Full-screen overlays
        when (overlay) {
            HomeOverlay.Search -> FeedSearchPage(copy = copy, onClose = { overlay = HomeOverlay.None })
            HomeOverlay.Comments -> overlayPost?.let { post ->
                // The card in the list is the live one (count moves as comments land).
                val current = posts.firstOrNull { it.id == post.id } ?: post
                val allComments by FeedRepo.comments.collectAsState()
                // The pager already holds this post's realtime channel, so the
                // sheet only has to say which order it wants the thread in.
                FeedCommentsSheet(
                    post = current,
                    comments = allComments[post.id].orEmpty(),
                    copy = copy,
                    onSend = { body, parentId, imageKey -> FeedRepo.addComment(post.id, body, parentId, imageKey) },
                    onLikeComment = { commentId -> FeedRepo.toggleCommentLike(post.id, commentId) },
                    onLoadMore = { FeedRepo.loadMoreComments(post.id) },
                    onSort = { order -> FeedRepo.loadComments(post.id, order) },
                    onClosing = { commentsClosing = true },
                    onClose = {
                        overlay = HomeOverlay.None
                        commentsClosing = false
                    },
                )
            }
            HomeOverlay.Sound -> overlayPost?.let { post ->
                FeedSoundPage(post = post, copy = copy, onClose = { overlay = HomeOverlay.None })
            }
            HomeOverlay.None -> Unit
        }
    }
}

@Composable
private fun HomeTopBar(
    lane: HomeLane,
    onLane: (HomeLane) -> Unit,
    onSearch: () -> Unit,
    copy: Copy,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Solid black, matching the bottom bar. The lanes used to read on
            // top of whatever poster happened to be behind them, which made
            // the strip a different colour on every card.
            .background(Color.Black)
            .statusBarsPadding()
            .padding(top = rdp(6), bottom = rdp(8)),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LaneTab(
                label = copy.t(Copy.HomeLive),
                selected = lane == HomeLane.Live,
                onClick = { onLane(HomeLane.Live) },
                liveDot = true,
            )
            LaneTab(
                label = copy.t(Copy.HomeFollowing),
                selected = lane == HomeLane.Following,
                onClick = { onLane(HomeLane.Following) },
            )
            LaneTab(
                label = copy.t(Copy.HomeForYou),
                selected = lane == HomeLane.ForYou,
                onClick = { onLane(HomeLane.ForYou) },
                larger = true,
            )
        }

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = rdp(14))
                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                .pressable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            if (lane == HomeLane.Live) BellIcon() else SearchIcon(size = rdp(22), tint = Color.White)
        }
    }
}

@Composable
private fun LaneTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    larger: Boolean = false,
    liveDot: Boolean = false,
) {
    Column(
        modifier = Modifier
            .sizeIn(minHeight = 44.dp)
            .pressable(role = Role.Tab, pressedScale = 0.96f, onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(6)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(5)),
        ) {
            if (liveDot) {
                Box(
                    Modifier
                        .size(rdp(6))
                        .background(Dark.Accent, androidx.compose.foundation.shape.CircleShape),
                )
            }
            XaniText(
                text = label,
                style = if (selected) Type.LabelBold else Type.Label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                fontSizeOverride = if (selected || larger) 16f else 14f,
            )
        }
        Box(
            Modifier
                .padding(top = rdp(4))
                .size(width = rdp(22), height = rdp(2.5f))
                .background(
                    if (selected) Color.White else Color.Transparent,
                    androidx.compose.foundation.shape.RoundedCornerShape(rdp(2)),
                ),
        )
    }
}

@Composable
private fun EmptyFollowing(copy: Copy) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VectorCanvas(
            shapes = listOf(
                VShape.Circle(28f, 20f, 12f, stroke(Color.White.copy(alpha = 0.3f), 2f)),
                VShape.D(
                    "M8 48C8 38 17 32 28 32s20 6 20 16",
                    stroke(Color.White.copy(alpha = 0.3f), 2f),
                ),
            ),
            viewport = 56f,
            modifier = Modifier.size(rdp(56)),
        )
        XaniText(
            text = copy.t(Copy.HomeFollowEmpty),
            style = Type.Label,
            color = Color.White.copy(alpha = 0.5f),
            align = TextAlign.Center,
            modifier = Modifier.padding(top = rdp(12), start = rdp(32), end = rdp(32)),
        )
    }
}

@Composable
private fun BellIcon() {
    VectorCanvas(
        shapes = listOf(
            VShape.D(
                "M11 3C7.5 3 4.5 5.7 4.5 9v4.5L2.5 16h17l-2-2.5V9C17.5 5.7 14.5 3 11 3z",
                stroke(Color.White, 1.8f),
            ),
            VShape.D("M9 17.5a2 2 0 004 0", stroke(Color.White, 1.8f)),
        ),
        viewport = 22f,
        modifier = Modifier.size(rdp(22)),
    )
}

/** Empty For You: nothing published yet, or offline with no cached head. */
@Composable
private fun EmptyFeed(copy: Copy, offline: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        XaniText(
            text = copy.t(if (offline) Copy.HomeFeedOffline else Copy.HomeFeedEmpty),
            style = Type.Label,
            color = Color.White.copy(alpha = 0.5f),
            align = TextAlign.Center,
            modifier = Modifier.padding(horizontal = rdp(32)),
        )
        if (offline) {
            XaniText(
                text = copy.t(Copy.HomeFeedRetry),
                style = Type.LabelBold,
                color = Color.White,
                modifier = Modifier.padding(top = rdp(14)).pressable(onClick = onRetry),
            )
        }
    }
}
