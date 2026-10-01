package app.xani.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xani.core.device.DeviceClass
import app.xani.core.media.FeedVideo
import app.xani.core.media.RemoteImage
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.gradientFill
import app.xani.ui.vector.stroke
import kotlin.math.sin

/** One heart at a time; a re-tap replaces it (restarts the animation). */
private data class HeartSpawn(val key: Int, val x: Float, val y: Float, val a: Color, val b: Color)

private val HeartGrads = listOf(
    Color(0xFFA855F7) to Color(0xFFEC4899),
    Color(0xFFFF3CAC) to Color(0xFF784BA0),
    Color(0xFFFF6B6B) to Color(0xFFFF4FD8),
    Color(0xFFFC466B) to Color(0xFF3F5EFB),
    Color(0xFF12C2E9) to Color(0xFFC471ED),
    Color(0xFFF953C6) to Color(0xFFB91D73),
)

@Composable
internal fun FeedPager(
    posts: List<FeedPost>,
    active: Boolean,
    device: DeviceClass,
    @Suppress("UNUSED_PARAMETER") reduceMotion: Boolean,
    liked: Set<Long>,
    saved: Set<Long>,
    followed: Set<String>,
    muted: Boolean,
    paused: Boolean,
    holding: Boolean,
    copy: Copy,
    onPauseToggle: () -> Unit,
    onHold: (Boolean) -> Unit,
    onMute: () -> Unit,
    onLike: (Long) -> Unit,
    /** Turns the like on, idempotently — what a double tap means. */
    onLikeOn: (Long) -> Unit,
    onSave: (Long) -> Unit,
    onFollow: (String) -> Unit,
    onPage: (Int) -> Unit,
    onComments: (FeedPost) -> Unit,
    onSound: (FeedPost) -> Unit,
    /** The picture or the handle was tapped: open that account's profile. */
    onOpenProfile: (FeedPost) -> Unit = {},
) {
    val bottom = LocalFeedBottomReserve.current
    val pagerState = rememberPagerState(pageCount = { posts.size })
    var heart by remember { mutableStateOf<HeartSpawn?>(null) }
    var heartSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(posts) {
        val safe = pagerState.currentPage.coerceIn(0, (posts.size - 1).coerceAtLeast(0))
        if (safe != pagerState.currentPage) pagerState.scrollToPage(safe)
        onPage(safe)
    }
    LaunchedEffect(pagerState.settledPage) {
        onPage(pagerState.settledPage)
        heart = null
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = if (active) device.pagerNeighbours else 0,
        userScrollEnabled = active && !holding,
        key = { posts[it].id },
    ) { page ->
        val post = posts[page]
        val isLiked = post.id in liked
        FeedPage(
            post = post,
            liked = isLiked,
            saved = post.id in saved,
            followed = post.creator.handle in followed,
            playing = active && !paused && page == pagerState.settledPage,
            paused = paused,
            holding = holding,
            muted = muted,
            reduceMotion = reduceMotion,
            heart = heart,
            copy = copy,
            bottomReserve = bottom,
            onTap = onPauseToggle,
            onHold = onHold,
            onMute = onMute,
            // A double tap is affection, never its withdrawal: it plays the
            // heart every time and only ever turns the like on. Taking a like
            // back is the rail button's job, where it is deliberate.
            onDoubleTap = { pos ->
                heartSeq += 1
                val g = HeartGrads[heartSeq % HeartGrads.size]
                heart = HeartSpawn(heartSeq, pos.x, pos.y, g.first, g.second)
                onLikeOn(post.id)
            },
            onHeartDone = { key -> if (heart?.key == key) heart = null },
            // Pressing the heart in the rail reads as the same gesture, so it
            // gets the same animation — centred, since there is no tap point.
            onLike = {
                if (!isLiked) {
                    heartSeq += 1
                    val g = HeartGrads[heartSeq % HeartGrads.size]
                    heart = HeartSpawn(heartSeq, Float.NaN, Float.NaN, g.first, g.second)
                }
                onLike(post.id)
            },
            onSave = { onSave(post.id) },
            onFollow = { onFollow(post.creator.handle) },
            onComments = { onComments(post) },
            onSound = { onSound(post) },
            onOpenProfile = { onOpenProfile(post) },
        )
    }
}

@Composable
private fun FeedPage(
    post: FeedPost,
    liked: Boolean,
    saved: Boolean,
    followed: Boolean,
    playing: Boolean,
    paused: Boolean,
    holding: Boolean,
    muted: Boolean,
    reduceMotion: Boolean,
    heart: HeartSpawn?,
    copy: Copy,
    bottomReserve: Dp,
    onTap: () -> Unit,
    onHold: (Boolean) -> Unit,
    onMute: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onHeartDone: (Int) -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onFollow: () -> Unit,
    onComments: () -> Unit,
    onSound: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val heartScale by animateFloatAsState(
        targetValue = if (liked) 1.14f else 1f,
        animationSpec = tween(160),
        label = "heart",
    )
    // Chrome fades out while holding (2× scrub) — GPU alpha only, no relayout.
    val chromeAlpha by animateFloatAsState(
        targetValue = if (holding) 0f else 1f,
        animationSpec = tween(180),
        label = "chrome",
    )
    val dimAlpha by animateFloatAsState(
        targetValue = if (paused) 0.28f else 0f,
        animationSpec = tween(300),
        label = "dim",
    )

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(post.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { pos -> onDoubleTap(pos) },
                    onLongPress = { onHold(true) },
                    onPress = {
                        tryAwaitRelease()
                        onHold(false)
                    },
                )
            },
    ) {
        Poster(post, playing = playing, muted = muted)
        if (dimAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = dimAlpha }.background(Color.Black))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(rdp(180))
                .align(Alignment.TopCenter)
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(rdp(340))
                .align(Alignment.BottomCenter)
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.88f)),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        )

        // Double-tap heart — floats up, pops, wobbles; one alive at a time.
        heart?.let {
            DoubleTapHeart(
                it,
                onDone = onHeartDone,
                modifier = if (it.x.isNaN()) Modifier.align(Alignment.Center) else Modifier,
            )
        }

        // Paused overlay: mute (tappable) + play state (taps fall through)
        PauseOverlay(
            visible = paused && !holding,
            paused = paused,
            muted = muted,
            onMute = onMute,
            modifier = Modifier.align(Alignment.Center),
        )

        // Hold-to-2× badge
        if (holding) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(rdp(14)))
                    .padding(horizontal = rdp(22), vertical = rdp(10)),
            ) {
                XaniText(copy.t(Copy.Home2x), Type.LabelBold, color = Color.White, fontSizeOverride = 17f)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = rdp(10), bottom = bottomReserve + rdp(24))
                .graphicsLayer { alpha = chromeAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rdp(20)),
        ) {
            Box(Modifier.padding(bottom = rdp(4))) {
                Box(
                    modifier = Modifier
                        .size(rdp(48))
                        .clip(CircleShape)
                        .background(Color(post.creator.color))
                        .border(rdp(2), Color.White, CircleShape)
                        .pressable(pressedScale = 0.94f, onClick = onOpenProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    // The author's picture when they have one. It is the same
                    // cached decode the rest of the app uses, at rail size, so a
                    // scroll costs no new bitmap.
                    if (post.creator.avatarUrl.isNotEmpty()) {
                        RemoteImage(
                            url = post.creator.avatarUrl,
                            modifier = Modifier.fillMaxSize(),
                            maxEdge = 160,
                            fallback = Color.Transparent,
                        )
                    } else {
                        XaniText(
                            text = post.creator.initials,
                            style = Type.LabelBold,
                            color = Color.White,
                            fontSizeOverride = 15f,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = rdp(8))
                        .size(rdp(20))
                        .clip(CircleShape)
                        .background(if (followed) Dark.Primary else Dark.Accent)
                        .border(rdp(2), Color.Black, CircleShape)
                        .pressable(pressedScale = 0.9f, onClick = onFollow),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(
                        text = if (followed) "✓" else "+",
                        style = Type.LabelBold,
                        color = Color.White,
                        fontSizeOverride = 11f,
                    )
                }
            }

            Action(
                label = post.likes,
                active = liked,
                activeColor = Dark.Accent,
                onClick = onLike,
            ) {
                Box(Modifier.graphicsLayer { scaleX = heartScale; scaleY = heartScale }) {
                    HeartIcon(liked)
                }
            }
            Action(label = post.comments, onClick = onComments) { CommentIcon() }
            Action(
                label = post.saves,
                active = saved,
                activeColor = Color(0xFFFBBF24),
                onClick = onSave,
            ) { BookmarkIcon(saved) }
            Action(label = post.shares, onClick = { }) { ShareIcon() }
            if (post.sound.isNotEmpty()) {
                SoundDisc(
                    spinning = playing && !muted && !reduceMotion && !holding,
                    avatarUrl = post.creator.avatarUrl,
                    initials = post.creator.initials,
                    color = Color(post.creator.color),
                    onClick = onSound,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = rdp(14), end = rdp(68), bottom = bottomReserve + rdp(10))
                .graphicsLayer { alpha = chromeAlpha },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                modifier = Modifier.pressable(pressedScale = 0.98f, onClick = onOpenProfile),
            ) {
                XaniText(
                    text = post.creator.name.ifBlank { post.creator.handle.removePrefix("@") },
                    style = Type.LabelBold.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 4f),
                    ),
                    color = Color.White,
                    fontSizeOverride = 15f,
                )
                if (post.creator.verified) VerifiedBadge()
            }
            val caption = remember(post.description) {
                if (post.description.length > 90) post.description.take(90) + "…" else post.description
            }
            XaniText(
                text = caption,
                style = Type.Caption,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = rdp(5), bottom = rdp(10)),
            )
            if (post.sound.isNotEmpty()) Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                modifier = Modifier.pressable(onClick = onSound),
            ) {
                SoundGlyph()
                XaniText(
                    text = post.sound,
                    style = Type.Label,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSizeOverride = 12f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/**
 * Figma heart: pop scale-in, float up ~130px, pendulum wobble, fade at the end.
 * One Animatable drives everything through graphicsLayer — zero recomposition
 * of siblings per frame.
 */
@Composable
private fun DoubleTapHeart(spawn: HeartSpawn, onDone: (Int) -> Unit, modifier: Modifier = Modifier) {
    val progress = remember(spawn.key) { Animatable(0f) }
    LaunchedEffect(spawn.key) {
        progress.animateTo(1f, tween(1150, easing = LinearEasing))
        onDone(spawn.key)
    }
    // NaN means "no tap point" — the rail button — and the caller has aligned
    // us to the middle of the card instead, so only the float-up is applied.
    val centred = spawn.x.isNaN()
    val side = rdp(110)
    Box(
        modifier
            .size(side)
            .graphicsLayer {
                val p = progress.value
                translationX = if (centred) 0f else spawn.x - size.width / 2f
                translationY = (if (centred) 0f else spawn.y - size.height / 2f) - 130f * density * p
                val pop = if (p < 0.14f) 0.4f + (p / 0.14f) * 0.75f else 1.15f - 0.15f * ((p - 0.14f) / 0.86f)
                scaleX = pop
                scaleY = pop
                rotationZ = sin(p * 9f) * 7f * (1f - p)
                alpha = if (p < 0.72f) 1f else 1f - (p - 0.72f) / 0.28f
            },
    ) {
        VectorCanvas(
            shapes = listOf(
                VShape.D(
                    "M55 90 C55 90 14 62 14 36 C14 22 24 13 35 13 C43 13 50 18 55 26 C60 18 67 13 75 13 C86 13 96 22 96 36 C96 62 55 90 55 90 Z",
                    gradientFill(listOf(spawn.a, spawn.b)),
                ),
            ),
            viewport = 110f,
            modifier = Modifier.size(side),
        )
    }
}

@Composable
private fun PauseOverlay(
    visible: Boolean,
    paused: Boolean,
    muted: Boolean,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 200 else 400),
        label = "pauseFade",
    )
    if (alpha <= 0.01f) return
    Column(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rdp(14)),
    ) {
        // Mute toggle — tappable, never unpauses
        Box(
            Modifier
                .size(rdp(46))
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .pressable(pressedScale = 0.9f, enabled = visible, onClick = onMute),
            contentAlignment = Alignment.Center,
        ) {
            SpeakerGlyph(muted)
        }
        // Play/pause indicator — decorative, taps pass through to the video
        Box(
            Modifier
                .size(rdp(66))
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (paused) {
                VectorCanvas(
                    shapes = listOf(VShape.D("M5 3L19 12L5 21V3Z", fill(Color.White))),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(26)),
                )
            } else {
                VectorCanvas(
                    shapes = listOf(
                        VShape.Box(6f, 4f, 4f, 16f, 1.5f, fill(Color.White)),
                        VShape.Box(14f, 4f, 4f, 16f, 1.5f, fill(Color.White)),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(26)),
                )
            }
        }
    }
}

@Composable
private fun SpeakerGlyph(muted: Boolean) {
    if (muted) {
        VectorCanvas(
            shapes = listOf(
                VShape.D("M11 5L6 9H2v6h4l5 4V5z", fill(Color.White.copy(alpha = 0.9f))),
                VShape.Line(23f, 9f, 17f, 15f, stroke(Color.White.copy(alpha = 0.9f), 2.2f)),
                VShape.Line(17f, 9f, 23f, 15f, stroke(Color.White.copy(alpha = 0.9f), 2.2f)),
            ),
            viewport = 24f,
            modifier = Modifier.size(rdp(19)),
        )
    } else {
        VectorCanvas(
            shapes = listOf(
                VShape.D("M11 5L6 9H2v6h4l5 4V5z", fill(Color.White.copy(alpha = 0.9f))),
                VShape.D("M15.54 8.46a5 5 0 0 1 0 7.07", stroke(Color.White.copy(alpha = 0.9f), 2f)),
                VShape.D("M19.07 4.93a10 10 0 0 1 0 14.14", stroke(Color.White.copy(alpha = 0.55f), 2f)),
            ),
            viewport = 24f,
            modifier = Modifier.size(rdp(19)),
        )
    }
}

/**
 * The media layer under the chrome.
 *  - video: poster still first (from cache or one request), the clip fades in on
 *    its first rendered frame — never a black page, never a spinner
 *  - photo: the full rendition, letterboxed to its true aspect on the gradient
 *  - text: the author's gradient with the body set large
 * A post whose aspect matches the screen fills it; anything else is fitted, so a
 * landscape clip or a square photo is never cropped or stretched.
 */
@Composable
private fun Poster(post: FeedPost, playing: Boolean, muted: Boolean) {
    val color = Color(post.creator.color)
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(color.copy(alpha = 0.85f), Color.Black, color.copy(alpha = 0.45f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind { drawRect(brush) }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (post.kind) {
            FeedKind.Text -> {
                XaniText(
                    text = post.description,
                    style = Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = if (post.description.length > 120) 20f else 26f,
                    align = TextAlign.Center,
                    maxLines = 14,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = rdp(28), vertical = rdp(120)),
                )
            }
            FeedKind.Photo, FeedKind.Video -> {
                val fill = fillsScreen(post.aspect)
                val media = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(post.aspect)
                Box(media, contentAlignment = Alignment.Center) {
                    if (post.posterUrl.isNotEmpty()) {
                        RemoteImage(
                            url = post.posterUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                            fallback = Color.Transparent,
                        )
                    } else if (post.videoUrl.isEmpty()) {
                        XaniText(
                            text = post.creator.initials,
                            style = Type.Wordmark,
                            color = Color.White.copy(alpha = 0.18f),
                            fontSizeOverride = 96f,
                        )
                    }
                    if (post.kind == FeedKind.Video && post.videoUrl.isNotEmpty()) {
                        FeedVideo(
                            url = post.videoUrl,
                            playing = playing,
                            muted = muted,
                            modifier = Modifier.fillMaxSize(),
                            // The repo decides what counts as a watch and sends at
                            // most one view per post; this only reports position.
                            onProgress = { position, duration ->
                                FeedRepo.noteWatch(post.id, position, duration)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Within ~12% of the screen's own aspect: fill and crop, like every short-video app. */
@Composable
private fun fillsScreen(aspect: Float): Boolean {
    val cfg = LocalConfiguration.current
    val screen = cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.coerceAtLeast(1)
    return aspect <= screen * 1.12f
}

@Composable
private fun Action(
    label: String,
    active: Boolean = false,
    activeColor: Color = Color.White,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .pressable(pressedScale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rdp(4)),
    ) {
        icon()
        XaniText(
            text = label,
            style = Type.LabelBold,
            color = if (active) activeColor else Color.White,
            fontSizeOverride = 12f,
        )
    }
}

@Composable
private fun HeartIcon(liked: Boolean) {
    val color = if (liked) Dark.Accent else Color.White
    VectorCanvas(
        shapes = listOf(
            VShape.D(
                "M16 27S5 20 5 12.5C5 9.4 7.5 7 10.5 7c1.8 0 3.5.9 4.6 2.3a2 2 0 002.8 0C18.5 8 20.2 7 22 7c3 0 5.5 2.4 5.5 5.5C27.5 20 16 27 16 27z",
                if (liked) fill(color) else stroke(color, 1.8f),
            ),
        ),
        viewport = 32f,
        modifier = Modifier.size(rdp(32)),
    )
}

@Composable
private fun CommentIcon() {
    VectorCanvas(
        shapes = listOf(VShape.D("M4 4h22v18H17l-4 4-4-4H4V4z", stroke(Color.White, 1.8f))),
        viewport = 30f,
        modifier = Modifier.size(rdp(30)),
    )
}

@Composable
private fun BookmarkIcon(saved: Boolean) {
    val color = if (saved) Color(0xFFFBBF24) else Color.White
    VectorCanvas(
        shapes = listOf(
            VShape.D(
                "M3 3h20v24l-10-7L3 27V3z",
                if (saved) fill(color) else stroke(color, 1.8f),
            ),
        ),
        viewport = 26f,
        modifier = Modifier.size(rdp(26), rdp(30)),
    )
}

@Composable
private fun ShareIcon() {
    VectorCanvas(
        shapes = listOf(
            VShape.Circle(20f, 5f, 3f, stroke(Color.White, 1.8f)),
            VShape.Circle(6f, 14f, 3f, stroke(Color.White, 1.8f)),
            VShape.Circle(20f, 23f, 3f, stroke(Color.White, 1.8f)),
            VShape.D("M9 12.5l8-6M9 15.5l8 6", stroke(Color.White, 1.8f)),
        ),
        viewport = 28f,
        modifier = Modifier.size(rdp(28)),
    )
}

@Composable
private fun VerifiedBadge() {
    VectorCanvas(
        shapes = listOf(
            VShape.Circle(7f, 7f, 7f, fill(Dark.Primary)),
            VShape.D("M4 7l2 2 4-4", stroke(Color.White, 1.6f)),
        ),
        viewport = 14f,
        modifier = Modifier.size(rdp(14)),
    )
}

@Composable
private fun SoundGlyph() {
    VectorCanvas(
        shapes = listOf(
            VShape.Circle(7f, 7f, 5f, stroke(Color.White.copy(alpha = 0.7f), 1.2f)),
            VShape.Circle(7f, 7f, 2f, fill(Color.White.copy(alpha = 0.7f))),
        ),
        viewport = 14f,
        modifier = Modifier.size(rdp(14)),
    )
}

/**
 * Corner vinyl: original-sound entry on the action rail. Spins while the clip
 * is audible; a tap opens the sound page. The author's picture sits in the
 * hub so the disc always belongs to this creator's original sound.
 */
@Composable
private fun SoundDisc(
    spinning: Boolean,
    avatarUrl: String,
    initials: String,
    color: Color,
    onClick: () -> Unit,
) {
    val angle by rememberInfiniteTransition(label = "sound-disc").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart),
        label = "disc-angle",
    )
    Box(
        Modifier
            .padding(top = rdp(4))
            .size(rdp(46))
            .graphicsLayer { if (spinning) rotationZ = angle }
            .clip(CircleShape)
            .border(rdp(3), Color.White.copy(alpha = 0.12f), CircleShape)
            .pressable(pressedScale = 0.92f, onClick = onClick)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    listOf(Color(0xFF1A1A2E), Color(0xFF7C3AED)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind { drawRect(brush) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(rdp(22))
                .clip(CircleShape)
                .background(color)
                .border(rdp(2), Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl.isNotEmpty()) {
                RemoteImage(
                    url = avatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    maxEdge = 96,
                    fallback = Color.Transparent,
                )
            } else {
                XaniText(initials, Type.LabelBold, color = Color.White, fontSizeOverride = 8f)
            }
        }
    }
}

@Composable
internal fun MuteButton(
    muted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(rdp(34))
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .pressable(pressedScale = 0.92f, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (muted) {
            VectorCanvas(
                shapes = listOf(
                    VShape.D("M8 2.5L4 6H1v4h3l4 3.5V2.5z", stroke(Color.White, 1.4f)),
                    VShape.D("M12 5L15 11M15 5L12 11", stroke(Color.White, 1.4f)),
                ),
                viewport = 16f,
                modifier = Modifier.size(rdp(16)),
            )
        } else {
            VectorCanvas(
                shapes = listOf(
                    VShape.D("M8 2.5L4 6H1v4h3l4 3.5V2.5z", stroke(Color.White, 1.4f)),
                    VShape.D(
                        "M11 5.5C12 6.5 12.5 7.2 12.5 8s-.5 1.5-1.5 2.5M13 3.5C15 5.5 15.5 6.7 15.5 8S15 10.5 13 12.5",
                        stroke(Color.White, 1.4f),
                    ),
                ),
                viewport = 16f,
                modifier = Modifier.size(rdp(16)),
            )
        }
    }
}

