package app.xani.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.xani.core.device.DeviceClass
import app.xani.core.device.rememberReduceMotion
import app.xani.core.net.PostData
import app.xani.core.net.Xani
import app.xani.feature.inbox.ChatIco
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.feature.profile.ProfilePostActionsSheet
import app.xani.feature.profile.ProfilePostEditSheet
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.FollowStore
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.pressable
import kotlinx.coroutines.launch

/**
 * One post full-screen — opened from a profile grid cell. It is the same card the
 * feed plays (same gestures, same like/comment/save rail), so a video plays with
 * sound and a photo fills the screen at its real aspect. Like/save are optimistic
 * against the server exactly as in the feed; comments share the feed's live
 * comment channel.
 */
@Composable
fun PostViewer(
    post: PostData,
    device: DeviceClass,
    copy: Copy,
    onClose: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var card by remember(post.id) { mutableStateOf(FeedRepo.toCard(post)) }
    var muted by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(false) }
    var ownerMenu by remember { mutableStateOf(false) }
    var ownerEdit by remember { mutableStateOf(false) }
    val mine = card.authorId == ProfileStore.currentUserId && ProfileStore.currentUserId != 0L
    val relations by FollowStore.relations.collectAsState()
    val following = relations[card.authorId]?.following ?: card.following

    // The feed may already hold this post (the user scrolled past it): mirror its
    // counters so the two surfaces never disagree.
    val feed by FeedRepo.state.collectAsState()
    LaunchedEffect(feed.items) {
        feed.items.firstOrNull { it.id == post.id }?.let { card = it }
    }

    // Opened from the profile grid, this post is usually not in the feed list at
    // all, so the card takes its numbers straight off the post's realtime
    // channel: another viewer's like moves the rail while this screen is open.
    DisposableEffect(post.id) {
        FeedRepo.watchVisible(post.id)
        onDispose { FeedRepo.unwatch() }
    }
    val liveCounts by FeedRepo.counts.collectAsState()
    LaunchedEffect(liveCounts, post.id) {
        val c = liveCounts[post.id] ?: return@LaunchedEffect
        card = card.copy(
            likeCount = if (c.likes >= 0) c.likes else card.likeCount,
            commentCount = if (c.comments >= 0) c.comments else card.commentCount,
            saveCount = if (c.saves >= 0) c.saves else card.saveCount,
            shareCount = if (c.shares >= 0) c.shares else card.shareCount,
        ).refreshCounts()
    }

    BackHandler { if (comments) comments = false else onClose() }

    fun react(like: Boolean) {
        val before = card
        val on = if (like) !before.liked else !before.saved
        card = if (like) before.copy(liked = on, likeCount = (before.likeCount + if (on) 1 else -1).coerceAtLeast(0))
        else before.copy(saved = on, saveCount = (before.saveCount + if (on) 1 else -1).coerceAtLeast(0))
        card = card.refreshCounts()
        // If the feed has this card, its own optimistic path is the source of truth.
        if (feed.items.any { it.id == post.id }) {
            if (like) FeedRepo.toggleLike(post.id) else FeedRepo.toggleSave(post.id)
            return
        }
        scope.launch {
            val r = if (like) Xani.media.like(post.id, on) else Xani.media.save(post.id, on)
            val ack = r.value
            card = if (ack != null) {
                if (like) card.copy(liked = ack.on, likeCount = ack.count) else card.copy(saved = ack.on, saveCount = ack.count)
            } else if (!r.isSessionExpired) {
                if (like) card.copy(liked = before.liked, likeCount = before.likeCount)
                else card.copy(saved = before.saved, saveCount = before.saveCount)
            } else card
            card = card.refreshCounts()
        }
    }

    // While comments are open the card keeps playing, scaled into the strip the
    // sheet leaves free on top — same move as the home feed.
    var commentsClosing by remember { mutableStateOf(false) }
    val commentsUp = comments && !commentsClosing
    val cardScale by animateFloatAsState(
        targetValue = if (commentsUp) 0.40f else 1f,
        animationSpec = tween(if (commentsUp) 420 else 380, easing = FastOutSlowInEasing),
        label = "commentShrink",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CompositionLocalProvider(LocalFeedBottomReserve provides 0.dp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            ) {
            FeedPager(
                posts = listOf(card),
                active = true,
                device = device,
                reduceMotion = reduceMotion,
                liked = if (card.liked) setOf(card.id) else emptySet(),
                saved = if (card.saved) setOf(card.id) else emptySet(),
                followed = if (following) setOf(card.creator.handle) else emptySet(),
                muted = muted,
                paused = paused,
                holding = holding,
                copy = copy,
                onPauseToggle = { paused = !paused },
                onHold = { holding = it },
                onMute = { muted = !muted },
                onLike = { react(like = true) },
                onLikeOn = { if (!card.liked) react(like = true) },
                onSave = { react(like = false) },
                onFollow = { FollowStore.set(card.authorId, !following) },
                onPage = {},
                onComments = {
                    commentsClosing = false
                    comments = true
                },
                onSound = {},
                onOpenProfile = {
                    // Closes this viewer first: the profile is the destination,
                    // not a layer over a playing video.
                    onClose()
                    PersonRouter.open(card.authorId)
                },
            )
            }
        }

        // Close top-left; owner overflow top-right — small, over the card.
        if (!holding && !comments && !ownerMenu && !ownerEdit) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { BackChevron(Color.White) }
                Spacer(Modifier.weight(1f))
                if (mine) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .pressable { ownerMenu = true },
                        contentAlignment = Alignment.Center,
                    ) { ChatIco.MoreH(Color.White, 16.dp) }
                }
            }
        }

        if (ownerMenu) {
            ProfilePostActionsSheet(
                copy = copy,
                post = post,
                onEdit = { ownerEdit = true },
                onDeleted = onClose,
                onClose = { ownerMenu = false },
            )
        }
        if (ownerEdit) {
            ProfilePostEditSheet(copy = copy, post = post, onClose = { ownerEdit = false })
        }

        if (comments) {
            val allComments by FeedRepo.comments.collectAsState()
            FeedCommentsSheet(
                post = card,
                comments = allComments[post.id].orEmpty(),
                copy = copy,
                onSend = { body, parentId, imageKey -> FeedRepo.addComment(post.id, body, parentId, imageKey) },
                onLikeComment = { commentId -> FeedRepo.toggleCommentLike(post.id, commentId) },
                onLoadMore = { FeedRepo.loadMoreComments(post.id) },
                onSort = { order -> FeedRepo.loadComments(post.id, order) },
                onClosing = { commentsClosing = true },
                onClose = {
                    comments = false
                    commentsClosing = false
                },
            )
        }
    }
}

private fun FeedPost.refreshCounts() = copy(
    likes = FeedRepo.compact(likeCount.toLong()),
    comments = FeedRepo.compact(commentCount.toLong()),
    saves = FeedRepo.compact(saveCount.toLong()),
    shares = FeedRepo.compact(shareCount.toLong()),
)
