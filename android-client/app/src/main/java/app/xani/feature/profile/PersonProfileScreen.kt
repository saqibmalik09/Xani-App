package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xani.core.device.DeviceClass
import app.xani.core.media.RemoteImage
import app.xani.core.net.MediaConfig
import app.xani.core.net.PersonData
import app.xani.core.net.PostData
import app.xani.core.net.Xani
import app.xani.feature.home.FeedRepo
import app.xani.feature.home.PostViewer
import app.xani.feature.inbox.InboxController
import app.xani.feature.social.AvatarFrame
import app.xani.feature.social.FollowButton
import app.xani.feature.social.FollowStore
import app.xani.feature.social.LevelMark
import app.xani.feature.social.NameBadge
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

/**
 * Somebody else's profile.
 *
 * It is the profile screen the owner sees, minus everything only an owner may
 * do: no edit, no settings drawer, no wallet, no private tab, no camera badge
 * on the picture. What stays is what makes a profile worth opening — the
 * picture, the status card, the name and handle, the follow counts, the about
 * text and the public grid — plus the two things only a visitor needs: Follow
 * and Message.
 *
 * Opening it is what records a profile visit (see `?visit=1` on the read), so
 * the owner's visit counter and their "who viewed me" list are fed by the same
 * request that paints this screen.
 */
@Composable
fun PersonProfileScreen(
    userId: Long,
    copy: Copy,
    device: DeviceClass,
    onClose: () -> Unit,
) {
    val slide = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    var person by remember(userId) { mutableStateOf<PersonData?>(null) }
    var missing by remember(userId) { mutableStateOf(false) }
    var posts by remember(userId) { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var raw by remember(userId) { mutableStateOf<Map<Long, PostData>>(emptyMap()) }
    var cursor by remember(userId) { mutableStateOf("") }
    var loadingPosts by remember(userId) { mutableStateOf(false) }
    var loadedPosts by remember(userId) { mutableStateOf(false) }
    var viewing by remember(userId) { mutableStateOf<PostData?>(null) }
    /** The picture, opened big — a circle in the middle, not a full-width sheet. */
    var pictureOpen by remember(userId) { mutableStateOf(false) }

    LaunchedEffect(Unit) { slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }

    // The read that both paints the header and counts the visit.
    LaunchedEffect(userId) {
        val r = Xani.people.get(userId, visit = true)
        val hit = r.value
        if (hit == null) {
            missing = !r.isTransport && !r.isSessionExpired
            return@LaunchedEffect
        }
        FollowStore.learn(listOf(hit))
        person = hit
    }

    suspend fun fetchPosts(before: Long) {
        if (loadingPosts) return
        loadingPosts = true
        val page = Xani.media.byUser(userId, before = before).value
        if (page != null) {
            val cells = page.items.map(::personCell)
            posts = if (before == 0L) cells else posts + cells.filterNot { c -> posts.any { it.id == c.id } }
            raw = raw + page.items.associateBy { it.id }
            cursor = page.next
            loadedPosts = true
        }
        loadingPosts = false
    }
    LaunchedEffect(userId) { fetchPosts(0L) }

    BackHandler {
        when {
            pictureOpen -> pictureOpen = false
            viewing != null -> viewing = null
            else -> onClose()
        }
    }

    val relations by FollowStore.relations.collectAsState()
    val relation = relations[userId]
    val following = relation?.following ?: person?.following ?: false
    val followers = relation?.followers?.takeIf { it >= 0 } ?: person?.followers ?: 0
    val display = person?.fullName?.ifBlank { person?.username.orEmpty() }.orEmpty()
    val handle = person?.username?.let { "@$it" }.orEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = size.height * slide.value }
            .background(ProfileTokens.Bg),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = rdp(28)),
            horizontalArrangement = Arrangement.spacedBy(rdp(2)),
            verticalArrangement = Arrangement.spacedBy(rdp(2)),
        ) {
            item(span = { GridItemSpan(3) }, key = "header") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = rdp(14), end = rdp(20), top = rdp(12)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(rdp(36)).clip(CircleShape).pressable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) { ProfileIco.Back(ProfileTokens.Text, rdp(20)) }
                        Spacer(Modifier.weight(1f))
                        XaniText(handle, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 16f)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(rdp(36)))
                    }

                    Column(
                        Modifier.fillMaxWidth().padding(start = rdp(24), end = rdp(24), top = rdp(18)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            // A worn frame is drawn a third wider than the face
                            // and is the ring itself: the slot grows to hold it
                            // and the flat ring steps aside, so nothing clips.
                            val framed = person?.frameUrl?.isNotEmpty() == true
                            Box(
                                Modifier
                                    .size(rdp(if (framed) 116 else 90))
                                    .then(
                                        if (framed) Modifier
                                        else Modifier.background(ProfileTokens.AvatarRing, CircleShape).padding(rdp(3)),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Hold to see the picture big, as every profile
                                // the user already knows behaves.
                                PersonPicture(
                                    person = person,
                                    size = rdp(84),
                                    modifier = Modifier.combinedClickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onLongClick = { pictureOpen = true },
                                        onClick = { pictureOpen = true },
                                    ),
                                )
                            }
                            person?.status?.takeIf { it.isNotBlank() }?.let { status ->
                                ReadOnlyStatus(
                                    text = status,
                                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp),
                                )
                            }
                        }

                        Row(
                            Modifier.padding(top = rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                        ) {
                            person?.takeIf { it.badgeUrl.isNotEmpty() }?.let { p ->
                                NameBadge(p.badgeUrl, p.badgeMime, rdp(20))
                            }
                            XaniText(
                                display.ifBlank { copy.t(Copy.ProfileName) },
                                Type.LabelBold,
                                color = ProfileTokens.Text,
                                fontSizeOverride = 20f,
                            )
                            if (person?.verified == true) ProfileIco.Verified(rdp(16))
                            LevelMark(person?.senderLevel, rdp(20))
                            LevelMark(person?.receiverLevel, rdp(20))
                        }
                        XaniText(
                            handle,
                            Type.Caption,
                            color = ProfileTokens.Sub,
                            modifier = Modifier.padding(top = rdp(4)),
                        )

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = rdp(12))
                                .border(rdp(1), ProfileTokens.Border, RoundedCornerShape(0)),
                        ) {
                            PersonStat(
                                value = compact((person?.followingCount ?: 0).toLong()),
                                label = copy.t(Copy.ProfileFollowing),
                                divider = true,
                            )
                            PersonStat(
                                value = compact(followers.toLong()),
                                label = copy.t(Copy.ProfileFollowers),
                                divider = true,
                            )
                            PersonStat(
                                value = compact(posts.size.toLong()) + if (cursor.isNotEmpty()) "+" else "",
                                label = copy.t(Copy.ProfilePosts),
                                divider = false,
                            )
                        }

                        person?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = rdp(10))
                                    .clip(RoundedCornerShape(rdp(12)))
                                    .background(ProfileTokens.Bg2)
                                    .border(rdp(1), ProfileTokens.Border, RoundedCornerShape(rdp(12)))
                                    .padding(horizontal = rdp(14), vertical = rdp(10)),
                            ) {
                                XaniText(bio, Type.Caption, color = ProfileTokens.Sub)
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(top = rdp(12), bottom = rdp(4)),
                            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                FollowButton(
                                    following = following,
                                    followsYou = person?.followsYou ?: false,
                                    copy = copy,
                                    onClick = { FollowStore.set(userId, !following) },
                                )
                            }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .height(rdp(38))
                                    .clip(RoundedCornerShape(rdp(20)))
                                    .background(ProfileTokens.Bg2)
                                    .border(rdp(1), ProfileTokens.BorderStrong, RoundedCornerShape(rdp(20)))
                                    .pressable(onClick = {
                                        person?.let {
                                            InboxController.shared.requestChat(it)
                                            PersonRouter.clear()
                                        }
                                    }),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                ProfileIco.Message(ProfileTokens.Text, rdp(15))
                                Spacer(Modifier.width(rdp(6)))
                                XaniText(
                                    copy.t(Copy.ProfileMessage),
                                    Type.LabelBold,
                                    color = ProfileTokens.Text,
                                    fontSizeOverride = 13f,
                                )
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth().padding(top = rdp(14)).height(rdp(1)).background(ProfileTokens.Border))
                }
            }

            if (posts.isEmpty() && (loadedPosts || missing)) {
                item(span = { GridItemSpan(3) }, key = "empty") {
                    XaniText(
                        copy.t(Copy.ProfileNoPostsYet),
                        Type.Caption,
                        color = ProfileTokens.Muted,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = rdp(40)),
                    )
                }
            }

            items(posts, key = { it.id }) { post ->
                if (post.id == posts.lastOrNull()?.id && cursor.isNotEmpty()) {
                    LaunchedEffect(post.id) { scope.launch { fetchPosts(cursor.toLongOrNull() ?: 0L) } }
                }
                PersonPostCell(
                    post = post,
                    device = device,
                    onOpen = { raw[post.id]?.let { viewing = it } },
                )
            }
        }

        viewing?.let { post ->
            PostViewer(post = post, device = device, copy = copy, onClose = { viewing = null })
        }

        if (pictureOpen) {
            PicturePane(person = person, onClose = { pictureOpen = false })
        }
    }
}

/** The person's picture, with their initials underneath it as the fallback. */
@Composable
private fun PersonPicture(
    person: PersonData?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val url = MediaConfig.url(person?.avatarUrl?.ifEmpty { null })
    val name = person?.fullName?.ifBlank { person.username }.orEmpty()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ProfileTokens.Accent, ProfileTokens.AccentDeep))),
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotEmpty()) {
                RemoteImage(url = url, modifier = Modifier.fillMaxSize(), maxEdge = 512, fallback = Color.Transparent)
            } else {
                XaniText(
                    FeedRepo.initials(name.ifBlank { "?" }),
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = size.value * 0.34f,
                )
            }
        }
        // Their frame, from the profile read (or whatever surface saw it last).
        if (person != null && person.frameUrl.isNotEmpty()) {
            AvatarFrame(person.frameUrl, person.frameMime, size)
        }
    }
}

/**
 * The picture, large. A circle centred on a dimmed screen rather than a
 * full-width image: an avatar is a circle everywhere else in the app, and
 * stretching it edge to edge would show a crop nobody chose.
 */
@Composable
private fun PicturePane(person: PersonData?, onClose: () -> Unit) {
    val grow = remember { Animatable(0.82f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .pressable(pressedScale = 1f, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { scaleX = grow.value; scaleY = grow.value }) {
                PersonPicture(person = person, size = rdp(240))
            }
            XaniText(
                person?.username?.let { "@$it" }.orEmpty(),
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 15f,
                modifier = Modifier.padding(top = rdp(18)),
            )
        }
    }
}

/** The status card as a visitor sees it: the same bubble, with nothing to tap. */
@Composable
private fun ReadOnlyStatus(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .sizeIn(maxWidth = rdp(200))
                .clip(RoundedCornerShape(rdp(14)))
                .background(ProfileTokens.Text)
                .padding(horizontal = rdp(12), vertical = rdp(7)),
        ) {
            XaniText(
                text,
                Type.Caption,
                color = Color.White,
                fontSizeOverride = 12f,
                align = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(rdp(10))
                .offset(y = (-3).dp)
                .drawWithCache {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2f, size.height)
                        lineTo(0f, 0f)
                        lineTo(size.width, 0f)
                        close()
                    }
                    onDrawBehind { drawPath(path, ProfileTokens.Text) }
                },
        )
    }
}

@Composable
private fun RowScope.PersonStat(value: String, label: String, divider: Boolean) {
    Column(
        Modifier
            .weight(1f)
            .then(if (divider) Modifier.border(rdp(1), ProfileTokens.Border, RoundedCornerShape(0)) else Modifier)
            .padding(vertical = rdp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XaniText(value, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 18f)
        XaniText(label, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 11f)
    }
}

/** A tile on somebody else's grid: open it, nothing to edit. */
@Composable
private fun PersonPostCell(post: ProfilePost, device: DeviceClass, onOpen: () -> Unit) {
    val color = Color(post.color)
    Box(
        Modifier
            .aspectRatio(9f / 14f)
            .drawWithCache {
                if (device == DeviceClass.Low) {
                    onDrawBehind { drawRect(color) }
                } else {
                    val brush = Brush.linearGradient(
                        listOf(color, color.copy(alpha = 0.55f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(brush) }
                }
            }
            .pressable(pressedScale = 0.98f, onClick = onOpen),
    ) {
        if (post.thumbUrl.isNotEmpty()) {
            RemoteImage(
                url = post.thumbUrl,
                modifier = Modifier.fillMaxSize(),
                maxEdge = 360,
                fallback = Color.Transparent,
            )
        } else if (post.kind == "text" && post.caption.isNotEmpty()) {
            XaniText(
                post.caption,
                Type.LabelBold,
                color = Color.White,
                fontSizeOverride = 11f,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                align = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(rdp(8)),
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(rdp(4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(3)),
        ) {
            if (post.kind == "video") {
                ProfileIco.Play(Color.White, rdp(10))
                XaniText(post.views, Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
            } else {
                ProfileIco.HeartSmall(Color.White, rdp(10))
                XaniText(post.likes, Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
            }
        }
    }
}

private fun personCell(p: PostData) = ProfilePost(
    id = p.id,
    color = FeedRepo.paletteFor(p.id),
    views = FeedRepo.compact(p.counts.views),
    rank = p.counts.likes.toFloat(),
    thumbUrl = MediaConfig.url(p.thumbKey),
    likes = FeedRepo.compact(p.counts.likes.toLong()),
    kind = p.kind,
    state = p.state,
    caption = p.caption,
    isPrivate = p.visibility == "private",
    isRestricted = p.isRestricted,
    durationMs = p.durationMs,
)
