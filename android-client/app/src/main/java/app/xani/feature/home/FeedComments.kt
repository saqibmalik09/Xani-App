package app.xani.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xani.core.isolate.runFeature
import app.xani.core.media.PickedImage
import app.xani.core.media.RemoteImage
import app.xani.core.net.Xani
import app.xani.feature.inbox.DeviceGallerySheet
import app.xani.feature.profile.MyAvatar
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Comment order. Both are server-side index reads on the thread, and both are
 * what the wire calls them, so the sheet can hand the key straight to the repo.
 */
private enum class CommentSort(val wire: String) {
    Top("top"),
    Newest("new"),
}

/** Who a draft is answering: the thread it lands in and the name on the strip. */
private data class ReplyTarget(val parentId: Long, val name: String)

/**
 * TikTok comments: the sheet slides up over the playing card — the top strip is
 * transparent, so the video (shrunk by the host screen) keeps playing above it,
 * and tapping it dismisses. Replies thread under their comment, hearts are
 * server-backed, and a gallery picture can ride as a comment of its own.
 * Comment list is lazy — thousands of comments never live in composition.
 */
@Composable
internal fun FeedCommentsSheet(
    post: FeedPost,
    comments: List<FeedComment>,
    copy: Copy,
    onClose: () -> Unit,
    onSend: (body: String, parentId: Long, imageKey: String) -> Unit = { _, _, _ -> },
    onLikeComment: (Long) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSort: (String) -> Unit = {},
    /** Fires the moment a dismiss starts, so the host can grow the video in step. */
    onClosing: () -> Unit = {},
) {
    val enter = remember { Animatable(1f) } // 1 = offscreen, 0 = settled
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var closing by remember { mutableStateOf(false) }
    var dragY by remember { mutableStateOf(0f) }
    var sort by remember { mutableStateOf(CommentSort.Top) }
    var sortMenu by remember { mutableStateOf(false) }
    var dislikedIds by remember { mutableStateOf(setOf<Long>()) }
    var openReplies by remember { mutableStateOf(setOf<Long>()) }
    var draft by remember(post.id) { mutableStateOf("") }
    var replyTo by remember(post.id) { mutableStateOf<ReplyTarget?>(null) }
    var galleryOpen by remember { mutableStateOf(false) }
    var sendingImage by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Replies hang under the comment they answer; a reply whose parent is not
    // on this page (yet) stands alone rather than vanishing.
    val thread = remember(comments) {
        val ids = comments.mapTo(HashSet()) { it.id }
        val kids = LinkedHashMap<Long, MutableList<FeedComment>>()
        val top = ArrayList<FeedComment>(comments.size)
        for (c in comments) {
            if (c.parentId != 0L && c.parentId in ids) {
                kids.getOrPut(c.parentId) { mutableListOf() }.add(c)
            } else {
                top.add(c)
            }
        }
        top.map { t -> kids[t.id]?.let { r -> t.copy(replies = r.sortedBy { it.id }) } ?: t }
    }

    fun submit() {
        val body = draft.trim()
        if (body.isEmpty()) return
        onSend(body, replyTo?.parentId ?: 0L, "")
        draft = ""
        replyTo = null
    }

    fun sendPicture(uri: android.net.Uri) {
        if (sendingImage) return
        sendingImage = true
        val parent = replyTo?.parentId ?: 0L
        scope.launch {
            val key = withContext(Dispatchers.Default) {
                val bmp = PickedImage.decodeUpright(context, uri, 1280) ?: return@withContext null
                val jpeg = PickedImage.encodeJpeg(bmp, 84)
                bmp.recycle()
                if (jpeg == null || jpeg.size > 4L shl 20) return@withContext null
                runFeature(null) { Xani.media.putCommentImage(jpeg) }?.value
            }
            if (key != null) {
                onSend("", parent, key)
                replyTo = null
            }
            sendingImage = false
        }
    }

    LaunchedEffect(Unit) { enter.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
    // Re-reads the thread in the chosen order. Also the first read, so opening
    // the sheet and flipping the filter take the same path.
    LaunchedEffect(post.id, sort) { onSort(sort.wire) }

    fun dismiss() {
        if (closing) return
        closing = true
        onClosing()
        scope.launch {
            enter.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler(onBack = ::dismiss)

    Column(Modifier.fillMaxSize()) {
        // The video's stage: transparent, so the card the host screen shrank
        // keeps playing here. Tapping it closes the sheet, like every short-video app.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f + 0.62f * enter.value)
                .pressable(pressedScale = 1f, onClick = ::dismiss),
        )
        // Sheet
        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragY.roundToInt()) }
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                .background(Color.White),
        ) {
            // Drag zone: pill + header
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragY > 80f * density) dismiss()
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        ) { _, delta -> dragY = (dragY + delta).coerceAtLeast(0f) }
                    },
            ) {
                Box(
                    Modifier
                        .padding(top = rdp(10), bottom = rdp(8))
                        .size(width = rdp(40), height = rdp(4))
                        .clip(CircleShape)
                        .background(Color(0xFFDDDDDD))
                        .align(Alignment.CenterHorizontally),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(18))
                        .padding(bottom = rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText(
                        copy.t(Copy.HomeCommentsCount, "n" to post.comments),
                        Type.LabelBold,
                        color = Color(0xFF111111),
                        fontSizeOverride = 15f,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(rdp(32))
                            .pressable(onClick = { sortMenu = !sortMenu }),
                        contentAlignment = Alignment.Center,
                    ) {
                        VectorCanvas(
                            shapes = listOf(
                                VShape.Line(3f, 5f, 17f, 5f, stroke(Color(0xFF555555), 1.8f)),
                                VShape.Line(5f, 10f, 15f, 10f, stroke(Color(0xFF555555), 1.8f)),
                                VShape.Line(7f, 15f, 13f, 15f, stroke(Color(0xFF555555), 1.8f)),
                            ),
                            viewport = 20f,
                            modifier = Modifier.size(rdp(18)),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(rdp(1)).background(Color(0xFFF0F0F0)))
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(16)),
                ) {
                    if (thread.isEmpty()) {
                        item(key = "empty") {
                            XaniText(
                                copy.t(Copy.HomeCommentsEmpty),
                                Type.Caption,
                                color = Color(0xFFAAAAAA),
                                fontSizeOverride = 14f,
                                modifier = Modifier.fillMaxWidth().padding(rdp(28)),
                            )
                        }
                    }
                    items(thread, key = { it.id }) { c ->
                        // Ask for the next older page while the last row is still off-screen.
                        if (c.id == thread.lastOrNull()?.id) LaunchedEffect(c.id) { onLoadMore() }
                        CommentRow(
                            comment = c,
                            copy = copy,
                            disliked = c.id in dislikedIds,
                            repliesOpen = c.id in openReplies,
                            onLike = { id ->
                                onLikeComment(id)
                                dislikedIds = dislikedIds - id
                            },
                            onDislike = { dislikedIds = dislikedIds.flip(c.id) },
                            onReply = { target ->
                                replyTo = target
                                focus.requestFocus()
                            },
                            onToggleReplies = { openReplies = openReplies.flip(c.id) },
                        )
                    }
                }
                if (sortMenu) {
                    Column(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = rdp(14))
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(Color.White)
                            .padding(vertical = rdp(6)),
                    ) {
                        SortOption(copy.t(Copy.HomeSortTop), sort == CommentSort.Top) { sort = CommentSort.Top; sortMenu = false }
                        SortOption(copy.t(Copy.HomeSortNewest), sort == CommentSort.Newest) { sort = CommentSort.Newest; sortMenu = false }
                    }
                }
            }

            // Input bar. The app draws edge to edge, so the window does not shrink
            // when the keyboard opens: without this inset the box the user just
            // tapped sits underneath it. `union` takes the larger of keyboard and
            // navigation bar, so it is never padded for both at once.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            ) {
                replyTo?.let { target ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F5FC))
                            .padding(horizontal = rdp(16), vertical = rdp(7)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(
                            copy.t(Copy.HomeReplyingTo, "name" to target.name),
                            Type.Caption,
                            color = Color(0xFF7C3AED),
                            fontSizeOverride = 12f,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(rdp(22)).pressable(onClick = { replyTo = null }), contentAlignment = Alignment.Center) {
                            VectorCanvas(
                                shapes = listOf(
                                    VShape.Line(5f, 5f, 15f, 15f, stroke(Color(0xFF999999), 1.8f)),
                                    VShape.Line(15f, 5f, 5f, 15f, stroke(Color(0xFF999999), 1.8f)),
                                ),
                                viewport = 20f,
                                modifier = Modifier.size(rdp(12)),
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(14), vertical = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                ) {
                    MyAvatar(size = rdp(34))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(rdp(22)))
                            .background(Color(0xFFF5F5F5))
                            // The whole pill is the tap target, not just the glyphs
                            // inside it: anywhere in the box puts the caret in the
                            // field and raises the keyboard.
                            .then(
                                if (post.allowComments) {
                                    Modifier.pressable(pressedScale = 1f) { focus.requestFocus() }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = rdp(14), vertical = rdp(10)),
                    ) {
                        if (draft.isEmpty()) {
                            XaniText(
                                if (post.allowComments) copy.t(Copy.HomeAddComment) else copy.t(Copy.HomeCommentsOff),
                                Type.Caption,
                                color = Color(0xFFAAAAAA),
                                fontSizeOverride = 14f,
                            )
                        }
                        if (post.allowComments) {
                            BasicTextField(
                                value = draft,
                                onValueChange = { if (it.length <= 1000) draft = it },
                                textStyle = Type.Caption.copy(color = Color(0xFF111111), fontSize = 14.sp),
                                cursorBrush = SolidColor(Color(0xFF7C3AED)),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submit() }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                    }
                    if (post.allowComments) {
                        // A picture from the gallery is a comment of its own.
                        Box(
                            Modifier
                                .size(rdp(36))
                                .clip(CircleShape)
                                .pressable(onClick = { if (!sendingImage) galleryOpen = true }),
                            contentAlignment = Alignment.Center,
                        ) {
                            GalleryGlyph(if (sendingImage) Color(0xFFCCCCCC) else Color(0xFF555555))
                        }
                        // Always on screen, so there is visibly somewhere to send
                        // to; it only lights up once there is something to send.
                        val ready = draft.isNotBlank()
                        Box(
                            Modifier
                                .size(rdp(36))
                                .clip(CircleShape)
                                .background(if (ready) Color(0xFF7C3AED) else Color(0xFFEDEDED))
                                .pressable(onClick = { submit() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            SendGlyph(if (ready) Color.White else Color(0xFFAAAAAA))
                        }
                    }
                }
            }
        }
    }
    if (galleryOpen) {
        DeviceGallerySheet(
            copy = copy,
            onClose = { galleryOpen = false },
            onPick = { uri ->
                galleryOpen = false
                sendPicture(uri)
            },
        )
    }
}

/** Paper plane on the send button. */
@Composable
private fun SendGlyph(tint: Color) = VectorCanvas(
    shapes = listOf(VShape.D("M3 11.5L21 3l-8 18-2.2-7.2L3 11.5z", fill(tint))),
    viewport = 24f,
    modifier = Modifier.size(rdp(17)),
)

/** Picture frame with a hill — the "attach from gallery" glyph. */
@Composable
private fun GalleryGlyph(tint: Color) = VectorCanvas(
    shapes = listOf(
        VShape.Box(3f, 3f, 18f, 18f, 4f, stroke(tint, 1.8f)),
        VShape.Circle(8.6f, 8.6f, 1.7f, fill(tint)),
        VShape.D("M4.5 17.5l4.5-5 3.5 3.5 2.5-2.5 4.5 4", stroke(tint, 1.8f)),
    ),
    viewport = 24f,
    modifier = Modifier.size(rdp(20)),
)

@Composable
private fun SortOption(label: String, on: Boolean, onClick: () -> Unit) {
    XaniText(
        label,
        if (on) Type.LabelBold else Type.Label,
        color = if (on) Color(0xFF7C3AED) else Color(0xFF333333),
        fontSizeOverride = 14f,
        modifier = Modifier
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(18), vertical = rdp(12)),
    )
}

@Composable
private fun CommentRow(
    comment: FeedComment,
    copy: Copy,
    disliked: Boolean,
    repliesOpen: Boolean,
    onLike: (Long) -> Unit,
    onDislike: () -> Unit,
    onReply: (ReplyTarget) -> Unit,
    onToggleReplies: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = rdp(16), end = rdp(16), top = rdp(14))) {
        Row {
            CommentAvatar(comment, rdp(38), 14f)
            Spacer(Modifier.width(rdp(10)))
            Column(Modifier.weight(1f)) {
                XaniText(
                    comment.user,
                    Type.Label,
                    color = Color(0xFF888888),
                    fontSizeOverride = 13f,
                    modifier = Modifier.pressable(pressedScale = 1f) { PersonRouter.open(comment.authorId) },
                )
                CommentBody(comment, 14f)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText(comment.time, Type.Caption, color = Color(0xFFAAAAAA), fontSizeOverride = 12f)
                    Spacer(Modifier.width(rdp(14)))
                    XaniText(
                        copy.t(Copy.HomeReply),
                        Type.LabelBold,
                        color = Color(0xFF888888),
                        fontSizeOverride = 12f,
                        modifier = Modifier.pressable(onClick = { onReply(ReplyTarget(comment.id, comment.user)) }),
                    )
                    Spacer(Modifier.weight(1f))
                    LikeHeart(comment) { onLike(comment.id) }
                    Spacer(Modifier.width(rdp(10)))
                    Box(Modifier.pressable(onClick = onDislike)) {
                        VectorCanvas(
                            shapes = listOf(
                                VShape.D(
                                    "M10 15v4a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3H10z",
                                    if (disliked) fill(Color(0xFF6366F1)) else stroke(Color(0xFFAAAAAA), 1.8f),
                                ),
                            ),
                            viewport = 24f,
                            modifier = Modifier.size(rdp(16)),
                        )
                    }
                }
                if (comment.replies.isNotEmpty()) {
                    Row(
                        Modifier
                            .padding(top = rdp(8))
                            .pressable(onClick = onToggleReplies),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                    ) {
                        Box(Modifier.size(width = rdp(24), height = rdp(1)).background(Color(0xFFDDDDDD)))
                        XaniText(
                            copy.t(Copy.HomeViewReplies, "n" to "${comment.replies.size}") + if (repliesOpen) " ▲" else " ▾",
                            Type.LabelBold,
                            color = Color(0xFF7C3AED),
                            fontSizeOverride = 13f,
                        )
                    }
                }
                if (repliesOpen) {
                    comment.replies.forEach { r ->
                        Row(Modifier.padding(top = rdp(12), start = rdp(4))) {
                            CommentAvatar(r, rdp(28), 11f)
                            Spacer(Modifier.width(rdp(8)))
                            Column(Modifier.weight(1f)) {
                                XaniText(
                                    r.user,
                                    Type.Label,
                                    color = Color(0xFF888888),
                                    fontSizeOverride = 12f,
                                    modifier = Modifier.pressable(pressedScale = 1f) { PersonRouter.open(r.authorId) },
                                )
                                CommentBody(r, 13f)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    XaniText(r.time, Type.Caption, color = Color(0xFFBBBBBB), fontSizeOverride = 11f)
                                    Spacer(Modifier.width(rdp(12)))
                                    XaniText(
                                        copy.t(Copy.HomeReply),
                                        Type.LabelBold,
                                        color = Color(0xFF999999),
                                        fontSizeOverride = 11f,
                                        // Answering a reply still lands in the same thread; the
                                        // strip names the person being answered.
                                        modifier = Modifier.pressable(onClick = { onReply(ReplyTarget(comment.id, r.user)) }),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    LikeHeart(r) { onLike(r.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = rdp(14), start = rdp(48))
                .height(rdp(1))
                .background(Color(0xFFF5F5F5)),
        )
    }
}

/** Text and/or the attached picture of one comment or reply. */
@Composable
private fun CommentBody(comment: FeedComment, textSize: Float) {
    if (comment.text.isNotEmpty()) {
        XaniText(
            comment.text,
            Type.Caption,
            color = Color(0xFF111111),
            fontSizeOverride = textSize,
            modifier = Modifier.padding(top = rdp(3), bottom = rdp(7)),
        )
    } else {
        Spacer(Modifier.height(rdp(4)))
    }
    if (comment.imageUrl.isNotEmpty()) {
        RemoteImage(
            url = comment.imageUrl,
            modifier = Modifier
                .padding(bottom = rdp(7))
                .size(rdp(160))
                .clip(RoundedCornerShape(rdp(10))),
            maxEdge = 640,
            fallback = Color(0xFFF0F0F0),
        )
    }
}

/** The heart with its count; filled once the signed-in person has liked it. */
@Composable
private fun LikeHeart(comment: FeedComment, onLike: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(3)),
        modifier = Modifier.pressable(onClick = onLike),
    ) {
        VectorCanvas(
            shapes = listOf(
                VShape.D(
                    "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z",
                    if (comment.liked) fill(Color(0xFFEC4899)) else stroke(Color(0xFFAAAAAA), 1.8f),
                ),
            ),
            viewport = 24f,
            modifier = Modifier.size(rdp(16)),
        )
        XaniText(
            "${comment.likes}",
            Type.Caption,
            color = if (comment.liked) Color(0xFFEC4899) else Color(0xFFAAAAAA),
            fontSizeOverride = 12f,
        )
    }
}

/** The tinted-initial fallback avatar every comment row shares. */
@Composable
private fun CommentAvatar(comment: FeedComment, size: androidx.compose.ui.unit.Dp, letterSize: Float) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(comment.color))
            .pressable(pressedScale = 0.94f) { PersonRouter.open(comment.authorId) },
        contentAlignment = Alignment.Center,
    ) {
        if (comment.avatarUrl.isNotEmpty()) {
            RemoteImage(
                url = comment.avatarUrl,
                modifier = Modifier.fillMaxSize(),
                maxEdge = 160,
                fallback = Color.Transparent,
            )
        } else {
            XaniText(comment.user.take(1).uppercase(), Type.LabelBold, color = Color.White, fontSizeOverride = letterSize)
        }
    }
}

private fun Set<Long>.flip(id: Long): Set<Long> = if (id in this) this - id else this + id
