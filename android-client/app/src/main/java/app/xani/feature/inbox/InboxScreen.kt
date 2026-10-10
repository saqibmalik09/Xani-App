package app.xani.feature.inbox

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.LocalFeatureHost
import app.xani.core.isolate.runFeature
import app.xani.core.net.Perm
import app.xani.core.net.PostData
import app.xani.core.net.StoryGroupData
import app.xani.core.net.Xani
import app.xani.core.net.can
import app.xani.core.result.Envelope
import app.xani.feature.home.PostViewer
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.SearchIcon
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.stroke
import kotlinx.coroutines.launch

@Composable
fun InboxScreen(
    copy: Copy,
    device: DeviceClass,
    onImmersive: (Boolean) -> Unit,
    onUnread: (Int) -> Unit,
) {
    val host = LocalFeatureHost.current
    val brand = LocalBrand.current
    val inbox = remember(brand.name) {
        InboxController.from(host.run(Envelope.fail("unavailable", "inbox")) { InboxController.seed(brand.name) })
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bind to the account and keep the realtime socket open while this pane
    // lives: messages, ticks and presence arrive on it, not by polling.
    LaunchedEffect(inbox) {
        inbox.attach(context)
        inbox.observeLive()
    }
    // Chat is a permission. Without it the lane is not offered and the inbox
    // opens on notifications — which still has to work, because moderator
    // warnings land there. The grant arrives on the realtime stream, so this
    // flips while the app is open.
    val canChat = can(Perm.Chat)
    var picked by rememberSaveable { mutableStateOf(InboxLane.Chats) }
    // Derived, not corrected after the fact: a revoke never draws one frame of
    // a lane this account is no longer allowed to see.
    val lane = if (!canChat && picked == InboxLane.Chats) InboxLane.Notifications else picked
    val lanes = remember(canChat) {
        InboxLane.entries.filter { canChat || it != InboxLane.Chats }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(InboxOverlay.None) }
    var openId by remember { mutableStateOf<Int?>(null) }
    var contextId by remember { mutableStateOf<Int?>(null) }
    var storyGroup by remember { mutableStateOf<StoryGroupData?>(null) }
    // "X commented on your post" tapped: that post, full-screen over the inbox.
    var viewingPost by remember { mutableStateOf<PostData?>(null) }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var chatError by remember { mutableStateOf(false) }

    val openChat = openId?.let { inbox.chat(it) }
    var heldChat by remember { mutableStateOf<InboxChat?>(null) }
    val shownChat = openChat ?: heldChat
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val slide by animateFloatAsState(
        targetValue = if (openId != null) 0f else 1f,
        animationSpec = if (openId != null) {
            tween(180, easing = FastOutSlowInEasing)
        } else {
            tween(140, easing = FastOutLinearInEasing)
        },
        finishedListener = { if (openId == null) heldChat = null },
        label = "chatSlide",
    )
    val immersive = openId != null || overlay != InboxOverlay.None || storyGroup != null || viewingPost != null
    LaunchedEffect(immersive) { onImmersive(immersive) }
    // The store acks reads for the thread on screen as messages land.
    LaunchedEffect(openId) { inbox.opened(openId) }
    // "Message" pressed on somebody's profile: the shell has already moved to
    // this tab, so all that is left is to open the thread it asked for.
    LaunchedEffect(inbox.openRequest.value) {
        inbox.openRequest.value?.let { requested ->
            openId = requested
            inbox.openRequestHandled()
        }
    }
    LaunchedEffect(inbox.postRequest.value) {
        val postId = inbox.postRequest.value ?: return@LaunchedEffect
        inbox.postRequest.value = null
        picked = InboxLane.Activity
        runFeature(null) { Xani.media.post(postId) }?.value?.let { viewingPost = it }
    }
    LaunchedEffect(inbox.noticesRequest.value) {
        if (!inbox.noticesRequest.value) return@LaunchedEffect
        inbox.noticesRequest.value = false
        openId = null
        picked = InboxLane.Notifications
    }
    LaunchedEffect(inbox.unreadTotal, inbox.chats.size, inbox.muted.value) { onUnread(inbox.unreadTotal) }
    // Opening the Notifications lane is the read: badge clears here and
    // server-side. Keyed on the count too, so a notice landing while the lane
    // is already open is read immediately.
    LaunchedEffect(lane, inbox.noticesUnread.value) {
        if (lane == InboxLane.Notifications) inbox.markNoticesSeen()
    }
    DisposableEffect(Unit) { onDispose { onImmersive(false) } }

    BackHandler(enabled = immersive) {
        when {
            openId != null -> {
                openId = null
                if (overlay == InboxOverlay.None && storyGroup == null) onImmersive(false)
            }
            // The composer, the player and the post viewer own their own Back.
            overlay == InboxOverlay.StoryCreate || storyGroup != null || viewingPost != null -> Unit
            overlay != InboxOverlay.None -> overlay = InboxOverlay.None
            contextId != null -> contextId = null
        }
    }

    val bottom = LocalFeedBottomReserve.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Colors.Background),
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = (slide - 1f) * widthPx * 0.3f },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = rdp(20), end = rdp(16), top = rdp(10), bottom = rdp(4)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.InboxTitle), Type.H2, color = Colors.Text, fontSizeOverride = 24f, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(rdp(36))
                        .clip(CircleShape)
                        .background(if (showSearch) Dark.Primary.copy(alpha = 0.1f) else Colors.Surface)
                        .border(rdp(1), Colors.Border, CircleShape)
                        .pressable {
                            showSearch = !showSearch
                            if (!showSearch) query = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SearchIcon(rdp(17), if (showSearch) Dark.Primary else Colors.TextSoft)
                }
            }
            if (showSearch) {
                Row(
                    Modifier
                        .padding(horizontal = rdp(20), vertical = rdp(8))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Colors.Input)
                        .border(rdp(1), Colors.BorderStrong, RoundedCornerShape(rdp(12)))
                        .padding(horizontal = rdp(14), vertical = rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchIcon(rdp(15), Dark.Primary)
                    Spacer(Modifier.width(rdp(10)))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(80) },
                        textStyle = Type.Label.copy(color = Colors.Text),
                        cursorBrush = SolidColor(Dark.Primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) XaniText(copy.t(Copy.InboxSearch), Type.Label, color = Colors.Hint)
                                inner()
                            }
                        },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(20), vertical = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                lanes.forEach { tab ->
                    val on = lane == tab
                    val label = when (tab) {
                        InboxLane.Chats -> copy.t(Copy.InboxChats)
                        InboxLane.Notifications -> copy.t(Copy.InboxNotifications) +
                            inbox.noticesUnread.value.let { if (it > 0) " · $it" else "" }
                        InboxLane.Activity -> copy.t(Copy.InboxActivity)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(32))
                            .clip(RoundedCornerShape(rdp(20)))
                            .background(if (on) Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)) else Brush.linearGradient(listOf(Colors.Surface, Colors.Surface)))
                            .then(if (on) Modifier else Modifier.border(rdp(1), Colors.Border, RoundedCornerShape(rdp(20))))
                            .pressable { picked = tab },
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(label, Type.LabelStrong, color = if (on) Color.White else Colors.Sub, fontSizeOverride = 13f)
                    }
                }
            }
            when (lane) {
                InboxLane.Chats -> ChatList(
                    inbox = inbox,
                    copy = copy,
                    query = query,
                    archivedOpen = archivedOpen,
                    onArchived = { archivedOpen = !archivedOpen },
                    onOpen = { id ->
                        try {
                            val chat = inbox.chat(id)
                            if (chat == null) {
                                chatError = true
                            } else {
                                onImmersive(true)
                                inbox.markRead(id)
                                heldChat = chat
                                openId = id
                            }
                        } catch (_: Throwable) {
                            chatError = true
                        }
                    },
                    onContext = { contextId = it },
                    onStory = { overlay = InboxOverlay.StoryCreate },
                    onViewStory = { storyGroup = it },
                    bottom = bottom,
                )
                InboxLane.Notifications -> NotifyList(inbox, copy, bottom)
                InboxLane.Activity -> ActivityLane(copy, bottom, onOpenPost = { postId ->
                    // One read brings the post; the viewer opens over the inbox
                    // exactly as it does over a profile grid.
                    scope.launch {
                        runFeature(null) { Xani.media.post(postId) }?.value?.let { viewingPost = it }
                    }
                })
            }
        }

        if (lane == InboxLane.Chats) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = rdp(16), bottom = bottom + rdp(12))
                    .size(rdp(52))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                    .pressable { overlay = InboxOverlay.NewChat },
                contentAlignment = Alignment.Center,
            ) {
                ChatIco.ChatBubble(Color.White, rdp(22))
            }
        }

        when (overlay) {
            InboxOverlay.NewChat -> NewChatScreen(
                inbox = inbox,
                copy = copy,
                onClose = { overlay = InboxOverlay.None },
                onNewGroup = { overlay = InboxOverlay.NewGroup },
                onPick = { person ->
                    overlay = InboxOverlay.None
                    val next = inbox.openDirect(person)
                    onImmersive(true)
                    inbox.markRead(next.id)
                    heldChat = next
                    openId = next.id
                },
            )
            InboxOverlay.NewGroup -> NewGroupScreen(inbox, copy, onClose = { overlay = InboxOverlay.None }) { created ->
                overlay = InboxOverlay.None
                onImmersive(true)
                heldChat = created
                openId = created.id
            }
            InboxOverlay.StoryCreate -> StoryComposer(
                copy = copy,
                onClose = { overlay = InboxOverlay.None },
                onPosted = { overlay = InboxOverlay.None },
            )
            else -> Unit
        }
        val ctxChat = contextId?.let { inbox.chat(it) }
        if (ctxChat != null) {
            ChatContextMenu(
                chat = ctxChat,
                inbox = inbox,
                copy = copy,
                onOpen = {
                    onImmersive(true)
                    heldChat = ctxChat
                    openId = ctxChat.id
                    contextId = null
                },
                onDismiss = { contextId = null },
            )
        }
        storyGroup?.let { g ->
            StoryPlayer(group = g, copy = copy, onClose = { storyGroup = null })
        }
        viewingPost?.let { p ->
            PostViewer(post = p, device = device, copy = copy, onClose = { viewingPost = null })
        }
        }
        shownChat?.let { chat ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = slide * widthPx },
            ) {
                ChatThread(
                    chat = chat,
                    inbox = inbox,
                    copy = copy,
                    device = device,
                    onBack = {
                        openId = null
                        if (overlay == InboxOverlay.None && storyGroup == null) onImmersive(false)
                    },
                    onOpenContact = { name ->
                        val next = inbox.openDirect(name)
                        onImmersive(true)
                        inbox.markRead(next.id)
                        heldChat = next
                        openId = next.id
                    },
                )
            }
        }
        if (chatError) {
            ChatErrorModal(copy = copy, onDismiss = { chatError = false })
        }
    }
}

@Composable
private fun ChatErrorModal(copy: Copy, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pressable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(rdp(280))
                .clip(RoundedCornerShape(rdp(18)))
                .background(Colors.Background)
                .padding(rdp(20)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            XaniText(copy.t(Copy.InboxChatOpenFail), Type.Label, color = Colors.Text, align = TextAlign.Center)
            Spacer(Modifier.height(rdp(16)))
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                    .pressable(onClick = onDismiss)
                    .padding(horizontal = rdp(28), vertical = rdp(10)),
            ) {
                XaniText(copy.t(Copy.InboxOk), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
            }
        }
    }
}

@Composable
private fun ChatList(
    inbox: InboxController,
    copy: Copy,
    query: String,
    archivedOpen: Boolean,
    onArchived: () -> Unit,
    onOpen: (Int) -> Unit,
    onContext: (Int) -> Unit,
    onStory: () -> Unit,
    onViewStory: (StoryGroupData) -> Unit,
    bottom: androidx.compose.ui.unit.Dp,
) {
    val seq = inbox.seq.longValue
    val pinned = inbox.pinned.value
    val muted = inbox.muted.value
    val archived = inbox.archived.value
    val searching = query.isNotBlank()
    // Observe directory hits so typing a name redraws this list the moment
    // the debounced search returns — reading `.value` outside remember is
    // what keeps the subscription alive.
    val peopleHits = inbox.peopleResults.value
    val typingNow = inbox.typing.value
    // A chat matches on who it is with *or* on something said in it, which is
    // how people actually look for a conversation ("where did she send the
    // address?"). Both sides are already on the device, so it answers per
    // keystroke with no request.
    val filtered = remember(query, inbox.chats.size, archived, pinned, seq) {
        inbox.chats.filter { c ->
            c.id !in archived && (!searching || c.name.contains(query, true) || c.handle.contains(query, true) || inbox.mentions(c.id, query))
        }.sortedByDescending { if (it.id in pinned) 1 else 0 }
    }
    val pinnedChats = remember(filtered, pinned) { filtered.filter { it.id in pinned } }
    val restChats = remember(filtered, pinned) { filtered.filter { it.id !in pinned } }
    val archivedChats = remember(inbox.chats.size, archived, seq) { inbox.chats.filter { it.id in archived } }

    // …and accounts the user has never messaged, so "start a chat with X" does
    // not mean going through the "+" screen first.
    LaunchedEffect(query) { inbox.searchPeople(if (searching) query else "") }
    val known = remember(inbox.chats.size, seq) { inbox.chats.mapNotNull { it.peerId.takeIf { p -> p != 0L } }.toSet() }
    val newPeople = if (searching) peopleHits.filter { it.peerId !in known } else emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottom + rdp(72)),
    ) {
        if (searching) {
            if (filtered.isEmpty() && newPeople.isEmpty()) {
                item(key = "no-hits") {
                    XaniText(
                        copy.t(Copy.InboxNoResults, "q" to query),
                        Type.Caption,
                        color = Colors.Muted,
                        modifier = Modifier.fillMaxWidth().padding(rdp(28)),
                        align = TextAlign.Center,
                    )
                }
            }
        } else item(key = "stories") {
            StoriesRail(copy = copy, onCreate = onStory, onOpen = onViewStory)
        }
        if (pinnedChats.isNotEmpty()) {
            item(key = "pinned-label") {
                XaniText(copy.t(Copy.InboxPinned), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(6)), fontSizeOverride = 12f)
            }
            items(pinnedChats, key = { it.id }, contentType = { "chat" }) { chat ->
                ChatRow(chat, muted = chat.id in muted, pinned = true, typing = chat.id in typingNow, onOpen = { onOpen(chat.id) }, onContext = { onContext(chat.id) })
            }
        }
        if (restChats.isNotEmpty()) {
            item(key = "all-label") {
                XaniText(copy.t(Copy.InboxAll), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(6)), fontSizeOverride = 12f)
            }
            items(restChats, key = { it.id }, contentType = { "chat" }) { chat ->
                ChatRow(chat, muted = chat.id in muted, pinned = false, typing = chat.id in typingNow, onOpen = { onOpen(chat.id) }, onContext = { onContext(chat.id) })
            }
        }
        if (newPeople.isNotEmpty()) {
            item(key = "people-label") {
                XaniText(copy.t(Copy.InboxPeople), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(6)), fontSizeOverride = 12f)
            }
            items(newPeople, key = { "p-${it.peerId}" }, contentType = { "person" }) { person ->
                ChatRow(
                    person,
                    muted = false,
                    pinned = false,
                    onOpen = { onOpen(inbox.openDirect(person).id) },
                    onContext = {},
                )
            }
        }
        if (archivedChats.isNotEmpty() && !searching) {
            item(key = "archived") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = onArchived)
                        .padding(horizontal = rdp(20), vertical = rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(rdp(44))
                            .clip(CircleShape)
                            .background(Colors.Surface)
                            .border(rdp(1), Colors.Border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        VectorCanvas(
                            shapes = listOf(VShape.Box(2f, 5f, 14f, 10f, 2f, stroke(Colors.TextSoft, 1.4f))),
                            viewport = 18f,
                            modifier = Modifier.size(rdp(18)),
                        )
                    }
                    Spacer(Modifier.width(rdp(12)))
                    Column(Modifier.weight(1f)) {
                        XaniText(copy.t(Copy.InboxArchived), Type.LabelStrong, color = Colors.Text)
                        XaniText("${archivedChats.size}", Type.Caption, color = Colors.Sub)
                    }
                }
            }
            if (archivedOpen) {
                items(archivedChats, key = { "a-${it.id}" }) { chat ->
                    ChatRow(chat, muted = true, pinned = false, dim = true, onOpen = { onOpen(chat.id) }, onContext = { onContext(chat.id) })
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: InboxChat,
    muted: Boolean,
    pinned: Boolean,
    dim: Boolean = false,
    typing: Boolean = false,
    onOpen: () -> Unit,
    onContext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
                onLongClick = onContext,
            )
            .padding(horizontal = rdp(20), vertical = rdp(11)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (chat.group) {
                Box(
                    Modifier
                        .size(rdp(50))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(chat.tint.copy(alpha = if (muted || dim) 0.45f else 1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(chat.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
                }
            } else {
                ChatAvatar(
                    chat,
                    rdp(50),
                    fontSize = 16f,
                    showOnline = chat.unread == 0 || muted,
                    modifier = Modifier.alpha(if (muted || dim) 0.45f else 1f),
                )
            }
            if (chat.unread > 0 && !muted) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(rdp(14))
                        .clip(CircleShape)
                        .background(Dark.Accent)
                        .border(rdp(2), Color.White, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(rdp(14)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(
                    chat.name,
                    if (chat.unread > 0) Type.LabelBold else Type.Label,
                    color = if (dim) Colors.Sub else Colors.Text,
                    fontSizeOverride = 17f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pinned) XaniText(" 📌", Type.Micro, fontSizeOverride = 10f)
                if (muted) XaniText(" 🔕", Type.Micro, fontSizeOverride = 10f)
                Spacer(Modifier.weight(1f))
                XaniText(chat.time, Type.Micro, color = Colors.Muted, fontSizeOverride = 12f)
            }
            Spacer(Modifier.height(rdp(3)))
            // What they are doing beats what they last said: while the other
            // side is typing, the row says so instead of showing history.
            if (typing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XaniText(
                        chat.name.substringBefore(' ').ifBlank { chat.name } + " typing…",
                        Type.Caption,
                        color = Dark.Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(rdp(4)))
                    TypingDots(Dark.Accent, rdp(4))
                }
            } else {
                XaniText(
                    chat.preview,
                    Type.Caption,
                    color = if (chat.unread > 0 && !muted) Color(0xFF7C3AED) else Colors.Sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chat.unread > 0 && !muted) {
            Spacer(Modifier.width(rdp(8)))
            Box(
                Modifier
                    .size(rdp(20))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(chat.unread.toString(), Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
            }
        }
    }
}

@Composable
private fun NotifyList(inbox: InboxController, copy: Copy, bottom: androidx.compose.ui.unit.Dp) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottom + rdp(16))) {
        item {
            XaniText(copy.t(Copy.InboxNewFollowers), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(8)), fontSizeOverride = 11f)
        }
        items(inbox.followers, key = { it.handle }) { u ->
            val followed = u.handle in inbox.followingBack.value
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(20), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(rdp(44)).clip(CircleShape).background(Color(u.color)), contentAlignment = Alignment.Center) {
                    XaniText(u.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                }
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    XaniText(u.name, Type.LabelBold, color = Colors.Text, fontSizeOverride = 15f)
                    XaniText("${copy.t(Copy.InboxFollowedYou)} · ${u.time}", Type.Caption, color = Colors.Sub)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(20)))
                        .then(
                            if (followed) Modifier.background(Colors.Surface)
                            else Modifier.background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))),
                        )
                        .pressable { inbox.follow(u.handle) }
                        .padding(horizontal = rdp(14), vertical = rdp(6)),
                ) {
                    XaniText(copy.t(Copy.InboxFollow), Type.LabelBold, color = if (followed) Colors.Sub else Color.White, fontSizeOverride = 12f)
                }
            }
        }
        item {
            XaniText(copy.t(Copy.InboxSystem), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(start = rdp(20), top = rdp(12), bottom = rdp(8)), fontSizeOverride = 11f)
        }
        items(inbox.system, key = { it.id }) { n ->
            val title = when (n.kind) {
                "post_restricted" -> copy.t(Copy.InboxVideoRestrictedTitle)
                "support_reply" -> copy.t(Copy.InboxSupportRepliedTitle)
                "role_request" -> n.title.ifBlank { copy.t(Copy.RolesNoticeRequest) }
                "role_decision" -> n.title.ifBlank { copy.t(Copy.RolesNoticeDecision) }
                "role_change" -> n.title.ifBlank { copy.t(Copy.RolesNoticeChange) }
                else -> n.title
            }
            val msg = when (n.kind) {
                "post_restricted" -> copy.t(Copy.InboxVideoRestrictedBody)
                else -> n.msg
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = {
                        when (n.kind) {
                            "support_reply" -> if (n.refId > 0L) app.xani.feature.support.SupportRouter.openTicket(n.refId)
                            "role_request", "role_decision" -> app.xani.feature.roles.RolesRouter.openRequest(n.refId)
                            "role_change" -> app.xani.feature.roles.RolesRouter.openHome()
                        }
                    })
                    .padding(horizontal = rdp(20), vertical = rdp(10)),
            ) {
                Box(
                    Modifier
                        .size(rdp(40))
                        .clip(CircleShape)
                        .background(Dark.Primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(rdp(10)).clip(CircleShape).background(Dark.Primary))
                }
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    XaniText(title, Type.LabelStrong, color = Colors.Text, fontSizeOverride = 15f)
                    XaniText(msg, Type.Caption, color = Colors.Sub)
                }
                XaniText(n.time, Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
            }
        }
    }
}

@Composable
internal fun NewChatScreen(
    inbox: InboxController,
    copy: Copy,
    onClose: () -> Unit,
    onNewGroup: () -> Unit,
    onPick: (InboxChat) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    // Empty query: the people already in the inbox. Typing: the account
    // directory (@username, name or email), answered per keystroke.
    LaunchedEffect(q) { inbox.searchPeople(q) }
    val people = if (q.isBlank()) {
        inbox.chats.filter { !it.group && it.peerId != 0L }
    } else {
        inbox.peopleResults.value
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText(copy.t(Copy.InboxCancel), Type.LabelStrong, color = Dark.Primary, modifier = Modifier.pressable(onClick = onClose))
            XaniText(copy.t(Copy.InboxNewChat), Type.LabelBold, color = Colors.Text, modifier = Modifier.weight(1f), align = TextAlign.Center, fontSizeOverride = 16f)
            Spacer(Modifier.width(rdp(52)))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onClick = onNewGroup)
                .padding(horizontal = rdp(20), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(46)).clip(CircleShape).background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))),
                contentAlignment = Alignment.Center,
            ) {
                ChatIco.AddPerson(Color.White, rdp(22))
            }
            Spacer(Modifier.width(rdp(14)))
            Column(Modifier.weight(1f)) {
                XaniText(copy.t(Copy.InboxNewGroup), Type.LabelStrong, color = Colors.Text)
                XaniText(copy.t(Copy.InboxCreateGroup), Type.Caption, color = Colors.Sub)
            }
        }
        Row(
            Modifier
                .padding(horizontal = rdp(20), vertical = rdp(8))
                .clip(RoundedCornerShape(rdp(12)))
                .background(Colors.Input)
                .padding(rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon(rdp(15), Colors.TextSoft)
            Spacer(Modifier.width(rdp(10)))
            BasicTextField(
                value = q,
                onValueChange = { q = it.take(80) },
                textStyle = Type.Label.copy(color = Colors.Text),
                cursorBrush = SolidColor(Dark.Primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) XaniText(copy.t(Copy.InboxSearchShort), Type.Label, color = Colors.Hint)
                        inner()
                    }
                },
            )
        }
        if (q.isBlank()) {
            XaniText(
                copy.t(Copy.InboxRecents),
                Type.Micro,
                color = Colors.Muted,
                modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(8)),
                fontSizeOverride = 11f,
            )
        }
        LazyColumn {
            // Search hits are unbound rows (id 0); the account id is the stable key.
            items(people, key = { it.peerId }) { chat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { onPick(chat) }
                        .padding(horizontal = rdp(20), vertical = rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatAvatar(chat, rdp(46), fontSize = 15f)
                    Spacer(Modifier.width(rdp(14)))
                    Column(Modifier.weight(1f)) {
                        XaniText(chat.name, Type.LabelStrong, color = Colors.Text)
                        XaniText(chat.handle, Type.Caption, color = Colors.Sub)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewGroupScreen(
    inbox: InboxController,
    copy: Copy,
    onClose: () -> Unit,
    onCreated: (InboxChat) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    val people = inbox.chats.filter { !it.group && (q.isBlank() || it.name.contains(q, true) || it.handle.contains(q, true)) }
    val brand = LocalBrand.current
    val nextId = (inbox.chats.maxOfOrNull { it.id } ?: 0) + 1
    val link = "https://${brand.slug}.app/g/$nextId"
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16), vertical = rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText(copy.t(Copy.InboxCancel), Type.LabelStrong, color = Dark.Primary, modifier = Modifier.pressable(onClick = onClose))
            XaniText(copy.t(Copy.InboxNewGroup), Type.LabelBold, color = Colors.Text, modifier = Modifier.weight(1f), align = TextAlign.Center, fontSizeOverride = 16f)
            val enabled = selected.isNotEmpty()
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(if (enabled) Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)) else Brush.linearGradient(listOf(Colors.Surface, Colors.Surface)))
                    .pressable(enabled = enabled) {
                        val members = inbox.chats.filter { it.id in selected }
                        val name = groupName.trim().ifBlank {
                            members.joinToString(", ") { it.name.substringBefore(' ') }.ifBlank { copy.t(Copy.InboxNewGroup) }
                        }
                        onCreated(inbox.createGroup(name, members))
                    }
                    .padding(horizontal = rdp(14), vertical = rdp(6)),
            ) {
                XaniText(copy.t(Copy.InboxCreate), Type.LabelBold, color = if (enabled) Color.White else Colors.Muted, fontSizeOverride = 13f)
            }
        }
        Column(Modifier.padding(horizontal = rdp(20), vertical = rdp(8))) {
            XaniText(copy.t(Copy.InboxGroupName), Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
            Spacer(Modifier.height(rdp(6)))
            BasicTextField(
                value = groupName,
                onValueChange = { groupName = it.take(40) },
                textStyle = Type.Label.copy(color = Colors.Text),
                cursorBrush = SolidColor(Dark.Primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Colors.Input)
                    .padding(rdp(12)),
                decorationBox = { inner ->
                    Box {
                        if (groupName.isEmpty()) XaniText(copy.t(Copy.InboxGroupName), Type.Label, color = Colors.Hint)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(rdp(12)))
            XaniText(copy.t(Copy.InboxInviteLink), Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
            Spacer(Modifier.height(rdp(6)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Colors.Input)
                    .padding(horizontal = rdp(12), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(link, Type.Caption, color = Dark.Primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                XaniText(
                    copy.t(Copy.InboxCopyAction),
                    Type.LabelBold,
                    color = Dark.Primary,
                    fontSizeOverride = 12f,
                    modifier = Modifier.pressable {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))
                    },
                )
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = rdp(20), vertical = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                inbox.chats.filter { it.id in selected }.forEach { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Box(Modifier.size(rdp(44)).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
                                XaniText(c.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(rdp(16))
                                    .clip(CircleShape)
                                    .background(Color(0xFF888888))
                                    .pressable { selected.remove(c.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                XaniText("×", Type.Micro, color = Color.White, fontSizeOverride = 10f)
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .padding(rdp(20))
                .clip(RoundedCornerShape(rdp(12)))
                .background(Colors.Input)
                .padding(rdp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon(rdp(15), Colors.TextSoft)
            Spacer(Modifier.width(rdp(10)))
            BasicTextField(
                value = q,
                onValueChange = { q = it.take(80) },
                textStyle = Type.Label.copy(color = Colors.Text),
                cursorBrush = SolidColor(Dark.Primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        LazyColumn {
            items(people, key = { it.id }) { chat ->
                val on = chat.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable {
                            if (on) selected.remove(chat.id) else selected.add(chat.id)
                        }
                        .padding(horizontal = rdp(20), vertical = rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(rdp(46)).clip(CircleShape).background(chat.tint), contentAlignment = Alignment.Center) {
                        XaniText(chat.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 15f)
                    }
                    Spacer(Modifier.width(rdp(14)))
                    Column(Modifier.weight(1f)) {
                        XaniText(chat.name, Type.LabelStrong, color = Colors.Text)
                        XaniText(chat.handle, Type.Caption, color = Colors.Sub)
                    }
                    Box(
                        Modifier
                            .size(rdp(22))
                            .clip(CircleShape)
                            .border(rdp(2), if (on) Dark.Primary else Colors.BorderStrong, CircleShape)
                            .background(if (on) Dark.Primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) XaniText("✓", Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatContextMenu(
    chat: InboxChat,
    inbox: InboxController,
    copy: Copy,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pinned = chat.id in inbox.pinned.value
    val muted = chat.id in inbox.muted.value
    val archived = chat.id in inbox.archived.value
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pressable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(rdp(260))
                .clip(RoundedCornerShape(rdp(18)))
                .background(Colors.Background)
                .padding(vertical = rdp(8)),
        ) {
            Row(Modifier.padding(horizontal = rdp(16), vertical = rdp(10)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(rdp(40)).clip(CircleShape).background(chat.tint), contentAlignment = Alignment.Center) {
                    XaniText(chat.initials, Type.LabelBold, color = Color.White)
                }
                Spacer(Modifier.width(rdp(10)))
                XaniText(chat.name, Type.LabelBold, color = Colors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val rows = listOf(
                copy.t(Copy.InboxMessage) to onOpen,
                (if (pinned) copy.t(Copy.InboxUnpin) else copy.t(Copy.InboxPin)) to { inbox.pin(chat.id); onDismiss() },
                (if (muted) copy.t(Copy.InboxUnmute) else copy.t(Copy.InboxMute)) to { inbox.mute(chat.id); onDismiss() },
                (if (archived) copy.t(Copy.InboxUnarchive) else copy.t(Copy.InboxArchive)) to { inbox.archive(chat.id); onDismiss() },
            ) + if (!chat.group && chat.peerId != 0L) {
                listOf((if (chat.blocked) copy.t(Copy.InboxUnblock) else copy.t(Copy.InboxBlock)) to { inbox.block(chat.id, on = !chat.blocked); onDismiss() })
            } else {
                emptyList()
            }
            rows.forEach { (label, action) ->
                XaniText(
                    label,
                    Type.LabelStrong,
                    color = Colors.Text,
                    modifier = Modifier.fillMaxWidth().pressable(role = Role.Button, onClick = action).padding(horizontal = rdp(20), vertical = rdp(12)),
                )
            }
            XaniText(
                copy.t(Copy.InboxDelete),
                Type.LabelStrong,
                color = Color(0xFFEF4444),
                modifier = Modifier.fillMaxWidth().pressable {
                    inbox.delete(chat.id)
                    onDismiss()
                }.padding(horizontal = rdp(20), vertical = rdp(12)),
            )
        }
    }
}
