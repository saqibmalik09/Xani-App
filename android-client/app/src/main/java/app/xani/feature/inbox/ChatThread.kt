package app.xani.feature.inbox

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.xani.core.device.DeviceClass
import app.xani.feature.call.CallController
import app.xani.feature.call.CallPeer
import app.xani.feature.call.rememberCallPermissions
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.system.LocalSystemNav
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "No message" for the thread's selection state.
 *
 * Zero is the only id no bubble can have: the server hands out positive
 * snowflakes and a send in flight carries a negative local id.
 */
private const val NoMsg = Long.MIN_VALUE

@Composable
fun ChatThread(
    chat: InboxChat,
    inbox: InboxController,
    copy: Copy,
    device: DeviceClass,
    onBack: () -> Unit,
    onOpenContact: (String) -> Unit,
) {
    var pane by remember { mutableStateOf(ThreadPane.Chat) }
    // Dark is what "these messages disappear" looks like. It follows the rule
    // saved on the conversation, not just the swipe, so turning the timer on
    // from settings (or the other side turning it on) dims the thread too.
    var vanishOn by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf<ChatMessage?>(null) }
    // 0 is "nothing selected": a server id is a positive snowflake and a
    // message still being sent carries a negative local id, so zero is the one
    // value that can never name a bubble. (-1 cannot be used as the sentinel —
    // it is the first local id handed out, which would select the first message
    // the user sends and leave the overlay impossible to dismiss.)
    var longPress by remember { mutableLongStateOf(NoMsg) }
    val pins = remember { mutableStateListOf<Long>() }
    var pinShow by remember { mutableIntStateOf(0) }
    var share by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf<ChatMessage?>(null) }
    var emojiOpen by remember { mutableStateOf(false) }
    var infoMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var blinkId by remember { mutableLongStateOf(NoMsg) }
    var flashId by remember { mutableLongStateOf(NoMsg) }
    var copied by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var injectPaste by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<ChatMessage?>(null) }
    var viewMedia by remember { mutableStateOf<ChatMessage?>(null) }
    var editEvent by remember { mutableStateOf<ChatMessage?>(null) }
    var pulseId by remember { mutableLongStateOf(NoMsg) }
    var reactorsOf by remember { mutableStateOf<ChatMessage?>(null) }
    val ctx = LocalContext.current
    val vanishRule = inbox.vanish.value
    // Calls live above the shell (see CallHost); the thread only dials. The
    // conversation id is what the server rings, so an unbound row cannot dial.
    val callPermissions = rememberCallPermissions()
    fun dial(video: Boolean) {
        if (chat.blocked) return
        val conversationId = inbox.conversationOf(chat.id) ?: return
        callPermissions(video) {
            CallController.start(
                conversationId = conversationId,
                chatId = chat.id,
                peer = CallPeer(chat.peerId, inbox.displayName(chat), chat.initials, chat.tint, chat.avatarUrl),
                video = video,
            )
        }
    }
    LaunchedEffect(vanishRule) { vanishOn = vanishRule != VanishTimer.Off }
    LaunchedEffect(pane) {
        if (pane != ThreadPane.Chat) pulseId = NoMsg
    }
    LaunchedEffect(pulseId) {
        if (pulseId == NoMsg) return@LaunchedEffect
        delay(680)
        pulseId = NoMsg
    }

    BackHandler {
        when {
            pane != ThreadPane.Chat -> pane = ThreadPane.Chat
            viewMedia != null -> viewMedia = null
            reactorsOf != null -> reactorsOf = null
            editEvent != null -> editEvent = null
            moreOpen -> moreOpen = false
            emojiOpen -> emojiOpen = false
            infoMsg != null -> infoMsg = null
            menu -> menu = false
            longPress != NoMsg -> longPress = NoMsg
            share -> share = false
            forward != null -> forward = null
            vanishOn -> vanishOn = false
            edit != null -> edit = null
            else -> onBack()
        }
    }

    when (pane) {
        ThreadPane.Profile, ThreadPane.Search, ThreadPane.Theme, ThreadPane.Nicknames, ThreadPane.Disappear, ThreadPane.Media -> {
            ChatProfileScreen(chat, inbox, copy, pane, onPane = { pane = it }, onBack = { pane = ThreadPane.Chat })
            return
        }
        ThreadPane.Chat -> Unit
    }

    val theme = ChatThemes[inbox.themeOf(chat.id)]
    val sysBg = Colors.Background
    val bg = if (vanishOn) Color.Black else if (theme.label == "Default") sysBg else Color(theme.bg)
    val nameColor = if (vanishOn) Color.White else Colors.Text
    val subColor = if (vanishOn) Color(0xFF34D399) else Color(0xFF10B981)
    val headerBorder = if (vanishOn) Color.White.copy(alpha = 0.06f) else Colors.Border
    val callBg = if (vanishOn) Color.White.copy(alpha = 0.08f) else Colors.Surface
    val callIcon = if (vanishOn) Color.White.copy(alpha = 0.7f) else Colors.TextSoft
    val theirBg = if (vanishOn) Color.White.copy(alpha = 0.08f) else Colors.Surface
    val theirText = if (vanishOn) Color.White.copy(alpha = 0.9f) else Colors.Text
    val msgs = inbox.thread(chat.id)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    var vanishArmed by remember { mutableStateOf(false) }
    var swipe by remember { mutableFloatStateOf(0f) }
    val vanishMax = 248f
    val vanishToggle = 128f
    val view = LocalView.current
    val settleVanish: () -> Unit = {
        val crossed = swipe >= vanishToggle
        val from = swipe
        vanishArmed = false
        if (crossed) {
            vanishOn = !vanishOn
            inbox.vanish.value = if (vanishOn) VanishTimer.Seen else VanishTimer.Off
            vanishHaptic(view)
        }
        scope.launch {
            val anim = Animatable(from)
            anim.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 520f)) {
                swipe = value
            }
        }
    }
    val dragVanish: (Float) -> Unit = { amount ->
        val next = (swipe - amount).coerceIn(0f, vanishMax)
        swipe = next
        if (next >= vanishToggle && !vanishArmed) {
            vanishArmed = true
            vanishHaptic(view)
        }
    }
    val nav = WindowInsets.navigationBars.getBottom(density)
    val floor = LocalSystemNav.current.bottomFloorPx
    val navPad = with(density) { maxOf(nav, floor).toDp() }

    val imeOpen = WindowInsets.ime.getBottom(density) > 40
    val hideOnScroll = remember(keyboard, focus, imeOpen) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (imeOpen && source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 10f) {
                    keyboard?.hide()
                    focus.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(blinkId) {
        if (blinkId == NoMsg) return@LaunchedEffect
        delay(850)
        blinkId = NoMsg
    }
    LaunchedEffect(flashId) {
        if (flashId == NoMsg) return@LaunchedEffect
        delay(520)
        flashId = NoMsg
    }
    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(700)
        copied = false
    }

    val wall = inbox.wallpapers[chat.id]
    val bubbleSolid = inbox.bubbleColors[chat.id]
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D12)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (swipe > 4f) {
            Column(
                Modifier.padding(bottom = rdp(24)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VanishProgressRing(
                    progress = (swipe / vanishMax).coerceIn(0f, 1f),
                    vanish = vanishOn,
                    modifier = Modifier.size(rdp(48)),
                )
                Spacer(Modifier.height(rdp(8)))
                XaniText(
                    copy.t(if (vanishOn) Copy.InboxVanishHintOff else Copy.InboxVanishHintOn),
                    Type.Caption,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSizeOverride = 12f,
                )
            }
        }
    }
    if (wall != null && !vanishOn) {
        LocalThumb(uri = wall, modifier = Modifier.fillMaxSize(), fallback = Color(theme.bg))
    }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -swipe }
            .background(if (wall != null && !vanishOn) Color.Transparent else bg)
            .statusBarsPadding(),
    ) {
        if (vanishOn) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Dark.Primary, Dark.Accent)))
                    .padding(vertical = rdp(6)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.InboxVanish), Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .pointerInput(vanishOn) {
                    detectVerticalDragGestures(
                        onDragStart = { vanishArmed = false },
                        onDragEnd = settleVanish,
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragVanish(amount)
                        },
                    )
                }
                .padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron(onBack)
            Row(
                Modifier
                    .weight(1f)
                    // Name / chrome opens the chat's own settings (theme, media,
                    // disappear). The face alone is the way to their public profile.
                    .pressable { pane = ThreadPane.Profile }
                    .padding(horizontal = rdp(4)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chat.group) {
                    Box(
                        Modifier
                            .size(rdp(40))
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(chat.tint),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(chat.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                    }
                } else {
                    ChatAvatar(
                        chat,
                        rdp(40),
                        fontSize = 13f,
                        showOnline = true,
                        modifier = Modifier.pressable {
                            if (chat.peerId != 0L) PersonRouter.open(chat.peerId)
                            else pane = ThreadPane.Profile
                        },
                    )
                }
                Spacer(Modifier.width(rdp(10)))
                Column(Modifier.weight(1f)) {
                    XaniText(inbox.displayName(chat), Type.LabelBold, color = nameColor, fontSizeOverride = 17f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    XaniText(
                        when {
                            chat.group -> copy.t(Copy.InboxOnlineShort, "n" to chat.members.size.coerceAtLeast(1).toString())
                            chat.blocked -> copy.t(Copy.InboxBlocked)
                            chat.id in inbox.typing.value -> copy.t(Copy.InboxTyping)
                            vanishOn -> "Vanish mode on"
                            else -> inbox.presence(chat, copy)
                        },
                        Type.Micro,
                        color = subColor,
                        fontSizeOverride = 11f,
                    )
                    // Status sits under presence — chat settings stay on this
                    // header tap; their public profile is the face above.
                    if (!chat.group && chat.status.isNotBlank() && chat.id !in inbox.typing.value) {
                        XaniText(
                            chat.status,
                            Type.Micro,
                            color = if (vanishOn) Color.White.copy(alpha = 0.55f) else Colors.Muted,
                            fontSizeOverride = 10f,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            RoundIcon(callBg, onClick = { dial(false) }) {
                ChatIco.Phone(callIcon, rdp(18))
            }
            Spacer(Modifier.width(rdp(6)))
            RoundIcon(callBg, onClick = { dial(true) }) {
                ChatIco.Video(callIcon, rdp(18))
            }
            Spacer(Modifier.width(rdp(6)))
            RoundIcon(callBg, onClick = { menu = !menu }) {
                ChatIco.MoreV(callIcon)
            }
        }
        Box(Modifier.height(1.dp).fillMaxWidth().background(headerBorder))

        val pinId = pins.getOrNull(pinShow.coerceIn(0, (pins.size - 1).coerceAtLeast(0)))
        val pinMsg = pinId?.let { id -> msgs.firstOrNull { it.id == id } }
        if (pinMsg != null && pins.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (vanishOn) Dark.Primary.copy(alpha = 0.15f) else Color(0xFFF8F5FF))
                    .pressable {
                        val id = pins.getOrNull(pinShow.coerceIn(0, pins.lastIndex)) ?: return@pressable
                        val idx = msgs.asReversed().indexOfFirst { it.id == id }
                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                        blinkId = id
                        if (pins.size > 1) pinShow = (pinShow + 1) % pins.size
                    }
                    .padding(horizontal = rdp(14), vertical = rdp(7)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(rdp(3)).height(rdp(28)).clip(RoundedCornerShape(rdp(2))).background(Brush.verticalGradient(listOf(Dark.Primary, Dark.Accent))))
                Column(Modifier.weight(1f).padding(horizontal = rdp(10))) {
                    XaniText(
                        if (pins.size > 1) "${copy.t(Copy.InboxPinnedMessage)}  ${pinShow + 1}/${pins.size}"
                        else copy.t(Copy.InboxPinnedMessage),
                        Type.LabelBold,
                        color = Dark.Primary,
                        fontSizeOverride = 10f,
                    )
                    XaniText(previewOf(pinMsg), Type.Caption, color = if (vanishOn) Color.White.copy(alpha = 0.7f) else Color(0xFF555555), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(rdp(28)).pressable {
                        val i = pinShow.coerceIn(0, pins.lastIndex)
                        pins.removeAt(i)
                        pinShow = pinShow.coerceAtMost((pins.size - 1).coerceAtLeast(0))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    ChatIco.Close(Color(0xFF9CA3AF), rdp(14))
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = rdp(12), vertical = rdp(8))
                    .nestedScroll(hideOnScroll)
                    .pointerInput(vanishOn) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { vanishArmed = false },
                            onDragEnd = { settleVanish() },
                            onDragCancel = { settleVanish() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragVanish(amount.y)
                            },
                        )
                    }
                    .then(
                        if (msgs.isEmpty()) {
                            Modifier.pointerInput(vanishOn) {
                                detectVerticalDragGestures(
                                    onDragStart = { vanishArmed = false },
                                    onDragEnd = settleVanish,
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragVanish(amount)
                                    },
                                )
                            }
                        } else Modifier,
                    ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(8)),
                verticalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                // reverseLayout: the first item is the one at the bottom, which
                // is where a bubble of dots belongs while they write.
                if (chat.id in inbox.typing.value) {
                    item(key = "typing", contentType = "typing") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(rdp(18), rdp(18), rdp(18), rdp(6)))
                                    .background(theirBg)
                                    .padding(horizontal = rdp(16), vertical = rdp(12)),
                            ) {
                                TypingDots(if (vanishOn) Color.White.copy(alpha = 0.7f) else Colors.Sub)
                            }
                        }
                    }
                }
                itemsIndexed(
                    msgs.asReversed(),
                    key = { _, m -> m.id },
                    contentType = { _, m -> m.kind },
                ) { _, msg ->
                    MessageBubble(
                        modifier = Modifier,
                        msg = msg,
                        chat = chat,
                        inbox = inbox,
                        copy = copy,
                        vanish = vanishOn,
                        theme = theme,
                        bubbleSolid = bubbleSolid,
                        theirBg = theirBg,
                        theirText = theirText,
                        blink = blinkId == msg.id,
                        flash = flashId == msg.id,
                        pulse = pulseId == msg.id,
                        onReaction = { reactorsOf = msg },
                        onTap = {
                            keyboard?.hide()
                            focus.clearFocus()
                            when {
                                (msg.kind == MsgKind.Image || msg.kind == MsgKind.Video || msg.kind == MsgKind.Ai) && !msg.mediaUri.isNullOrBlank() -> viewMedia = msg
                                msg.kind == MsgKind.Location -> openMaps(ctx, msg.text)
                                msg.kind == MsgKind.Event -> editEvent = msg
                                msg.kind == MsgKind.Voice -> VoicePlayer.toggle(msg.id, msg.mediaUri)
                                msg.kind == MsgKind.System && parseCallMsg(msg.text) != null -> Unit
                                else -> flashId = msg.id
                            }
                        },
                        onHold = {
                            keyboard?.hide()
                            focus.clearFocus()
                            longPress = msg.id
                            emojiOpen = false
                            moreOpen = false
                        },
                        onHeart = {
                            inbox.react(chat.id, msg.id, "❤️")
                            pulseId = msg.id
                        },
                        onReplySwipe = {
                            reply = msg
                            longPress = NoMsg
                        },
                        onForward = {
                            keyboard?.hide()
                            focus.clearFocus()
                            forward = msg
                        },
                        reply = msg.replyTo?.let { id -> msgs.firstOrNull { it.id == id } },
                        onJumpReply = { id ->
                            val idx = msgs.asReversed().indexOfFirst { it.id == id }
                            if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            blinkId = id
                        },
                        onOpenContact = onOpenContact,
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(rdp(28))
                    .pointerInput(vanishOn) {
                        detectVerticalDragGestures(
                            onDragStart = { vanishArmed = false },
                            onDragEnd = settleVanish,
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragVanish(amount)
                            },
                        )
                    },
            )
        }

        if (chat.blocked && !chat.group) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = navPad)
                    .padding(horizontal = rdp(16), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.InboxBlocked), Type.Label, color = Colors.Sub, fontSizeOverride = 13f, modifier = Modifier.weight(1f))
                XaniText(
                    copy.t(Copy.InboxUnblock),
                    Type.LabelBold,
                    color = Dark.Primary,
                    fontSizeOverride = 14f,
                    modifier = Modifier.pressable { inbox.block(chat.id, on = false) }.padding(rdp(6)),
                )
            }
        } else ChatComposer(
            copy = copy,
            vanish = vanishOn,
            reply = reply,
            onClearReply = { reply = null },
            edit = edit,
            onClearEdit = { edit = null },
            onSend = { text ->
                inbox.send(chat.id, text, replyTo = reply?.id)
                reply = null
                scope.launch { listState.animateScrollToItem(0) }
            },
            onEditSend = { text ->
                val target = edit
                if (target != null) {
                    inbox.patch(chat.id, target.id, text)
                    edit = null
                }
            },
            onMedia = { uri, kind, caption ->
                inbox.send(chat.id, caption, kind = kind, mediaUri = uri.toString(), replyTo = reply?.id)
                reply = null
                scope.launch { listState.animateScrollToItem(0) }
            },
            onAttachKind = { kind, text ->
                inbox.send(chat.id, text, kind = kind, replyTo = reply?.id)
                reply = null
                scope.launch { listState.animateScrollToItem(0) }
            },
            device = device,
            modifier = Modifier
                .imePadding()
                .padding(bottom = navPad)
                .pointerInput(vanishOn) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { vanishArmed = false },
                        onDragEnd = { settleVanish() },
                        onDragCancel = { settleVanish() },
                        onDrag = { change, amount ->
                            change.consume()
                            dragVanish(amount.y)
                        },
                    )
                },
            injectPaste = injectPaste,
            onInjected = { injectPaste = null },
            onTyping = { inbox.typing(chat.id) },
        )
    }

    if (menu) {
        ThreadMenu(
            copy = copy,
            vanish = vanishOn,
            receipts = inbox.readReceipts.value,
            onReceipts = { inbox.readReceipts.value = !inbox.readReceipts.value },
            onMedia = { menu = false; pane = ThreadPane.Media },
            onRead = { menu = false; inbox.markRead(chat.id) },
            onMute = { menu = false; inbox.mute(chat.id) },
            onShare = { menu = false; share = true },
            onClear = { menu = false; inbox.clear(chat.id) },
            onDismiss = { menu = false },
        )
    }
    if (share) ShareSheet(inbox, copy, vanishOn, title = copy.t(Copy.InboxShareProfile), onDismiss = { share = false }) {
        inbox.send(chat.id, copy.t(Copy.InboxShareProfile))
        share = false
    }
    val actionMsg = msgs.firstOrNull { it.id == longPress }
    if (actionMsg != null && !emojiOpen && !moreOpen) {
        MessageActionOverlay(
            copy = copy,
            msg = actionMsg,
            vanish = vanishOn,
            selectedEmoji = actionMsg.reaction,
            onReact = {
                inbox.react(chat.id, actionMsg.id, it)
                pulseId = actionMsg.id
                longPress = NoMsg
            },
            onPlus = { emojiOpen = true },
            onReply = {
                reply = actionMsg
                edit = null
                longPress = NoMsg
            },
            onEdit = {
                edit = actionMsg
                reply = null
                longPress = NoMsg
            },
            onForward = {
                keyboard?.hide()
                focus.clearFocus()
                forward = actionMsg
                longPress = NoMsg
            },
            onCopy = {
                val text = actionMsg.text.ifBlank { previewOf(actionMsg) }
                clipboard.setText(AnnotatedString(text))
                copied = true
                longPress = NoMsg
            },
            onDownload = { longPress = NoMsg },
            onMore = {
                moreOpen = true
            },
            onDelete = {
                inbox.unsend(chat.id, actionMsg.id)
                longPress = NoMsg
            },
            onUnsend = {
                inbox.unsend(chat.id, actionMsg.id)
                longPress = NoMsg
            },
            onDismiss = { longPress = NoMsg },
        )
    }
    if (moreOpen && actionMsg != null) {
        MsgMoreSheet(
            copy = copy,
            msg = actionMsg,
            pinned = actionMsg.id in pins,
            onPin = {
                val id = actionMsg.id
                if (id in pins) pins.remove(id)
                else {
                    if (pins.size >= 3) pins.removeAt(0)
                    pins.add(id)
                    pinShow = pins.lastIndex
                }
                moreOpen = false
                longPress = NoMsg
            },
            onInfo = {
                infoMsg = actionMsg
                moreOpen = false
                longPress = NoMsg
            },
            onReport = {
                moreOpen = false
                longPress = NoMsg
            },
            onTranslate = {
                moreOpen = false
                longPress = NoMsg
            },
            onDismiss = {
                moreOpen = false
                longPress = NoMsg
            },
        )
    }
    if (emojiOpen && actionMsg != null) {
        EmojiPickSheet(
            copy = copy,
            selected = actionMsg.reaction,
            onPick = {
                inbox.react(chat.id, actionMsg.id, it)
                pulseId = actionMsg.id
                emojiOpen = false
                longPress = NoMsg
            },
            onDismiss = { emojiOpen = false },
        )
    }
    infoMsg?.let { info ->
        MessageInfoSheet(
            copy, info, theme,
            onDismiss = { infoMsg = null },
            seenAt = inbox.seenAt(chat.id, info),
            deliveredAt = inbox.deliveredAt(chat.id, info),
        )
    }
    if (copied) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rdp(96))
                .clip(RoundedCornerShape(rdp(20)))
                .background(Color(0xE6111111))
                .padding(horizontal = rdp(16), vertical = rdp(8)),
        ) {
            XaniText(copy.t(Copy.InboxCopied), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
        }
    }
    // What the server refused (blocked, too long, offline), shown once, same pill.
    val notice = inbox.notice.value
    LaunchedEffect(notice) {
        if (notice.isNotEmpty()) {
            delay(2400)
            if (inbox.notice.value == notice) inbox.notice.value = ""
        }
    }
    if (notice.isNotEmpty()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rdp(96))
                .clip(RoundedCornerShape(rdp(20)))
                .background(Color(0xE6111111))
                .padding(horizontal = rdp(16), vertical = rdp(8)),
        ) {
            XaniText(notice, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
        }
    }
    val liveReactors = reactorsOf?.let { open -> msgs.firstOrNull { it.id == open.id } }
    LaunchedEffect(liveReactors?.id, liveReactors?.reactors, liveReactors?.reaction, reactorsOf?.id) {
        if (reactorsOf != null && (liveReactors == null || (liveReactors.reactors.isEmpty() && liveReactors.reaction == null))) {
            reactorsOf = null
        }
    }
    if (liveReactors != null && (liveReactors.reactors.isNotEmpty() || liveReactors.reaction != null)) {
        ReactorsSheet(
            copy = copy,
            msg = liveReactors,
            onDismiss = { reactorsOf = null },
            onRemoveMine = {
                inbox.clearMineReaction(chat.id, liveReactors.id)
                pulseId = NoMsg
            },
        )
    }
    viewMedia?.let { m ->
        MediaViewer(
            msg = m,
            copy = copy,
            onClose = { viewMedia = null },
            onForward = { forward = m },
            onReply = { text ->
                inbox.send(chat.id, text, replyTo = m.id)
            },
        )
    }
    val fwd = forward
    if (fwd != null) {
        ForwardPickSheet(
            inbox = inbox,
            copy = copy,
            preview = fwd.text.ifBlank { previewOf(fwd) },
            mediaUri = fwd.mediaUri,
            kind = fwd.kind,
            onDismiss = { forward = null },
        ) { targets, note ->
            targets.forEach { dest ->
                inbox.send(dest.id, fwd.text.ifBlank { previewOf(fwd) }, kind = fwd.kind, mediaUri = fwd.mediaUri)
                if (note.isNotBlank()) inbox.send(dest.id, note)
            }
            forward = null
        }
    }
    editEvent?.let { ev ->
        Dialog(
            onDismissRequest = { editEvent = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CreateEventSheet(
                copy = copy,
                seed = parseEvent(ev.text),
                canEdit = ev.mine,
                onLeave = { editEvent = null },
                onSend = { body ->
                    if (ev.mine) inbox.patch(chat.id, ev.id, body)
                    editEvent = null
                },
            )
        }
    }
    }
}

@Composable
private fun MessageBubble(
    modifier: Modifier = Modifier,
    msg: ChatMessage,
    chat: InboxChat,
    inbox: InboxController,
    copy: Copy,
    vanish: Boolean,
    theme: ChatTheme,
    bubbleSolid: Long? = null,
    theirBg: Color,
    theirText: Color,
    blink: Boolean,
    flash: Boolean,
    pulse: Boolean,
    onReaction: () -> Unit,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onHeart: () -> Unit,
    onReplySwipe: () -> Unit,
    onForward: () -> Unit,
    reply: ChatMessage?,
    onJumpReply: (Long) -> Unit,
    onOpenContact: (String) -> Unit,
) {
    val mine = msg.mine
    val ctx = LocalContext.current
    val view = LocalView.current
    var dragX by remember { mutableFloatStateOf(0f) }
    val poll = msg.kind == MsgKind.Poll
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = rdp(4))
                .graphicsLayer { alpha = (dragX / 48f).coerceIn(0f, 1f) },
        ) { ChatIco.Reply(Dark.Primary, rdp(16)) }
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    when {
                        blink -> Modifier.background(Color(0x33A78BFA), RoundedCornerShape(rdp(12)))
                        flash -> Modifier.background(Color(0x3322C55E), RoundedCornerShape(rdp(12)))
                        else -> Modifier
                    },
                )
                .offset { IntOffset(dragX.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        dragX = (dragX + delta).coerceIn(0f, 72f)
                    },
                    onDragStopped = {
                        if (dragX > 48f) onReplySwipe()
                        dragX = 0f
                    },
                )
                .then(
                    if (poll) Modifier
                    else Modifier.pointerInput(msg.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                vanishHaptic(view)
                                onHeart()
                            },
                            onTap = { onTap() },
                            onLongPress = { onHold() },
                        )
                    },
                ),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            Box(
                Modifier
                    .size(rdp(28))
                    .clip(CircleShape)
                    .background(Color(msg.color)),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(msg.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 9f)
            }
            Spacer(Modifier.width(rdp(6)))
        }
        Column(
            modifier = Modifier
                .widthIn(max = rdp(280))
                .padding(bottom = if (msg.reaction != null) rdp(10) else 0.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (reply != null) {
                XaniText(
                    copy.t(if (mine) Copy.InboxYouReplied else if (reply.mine) Copy.InboxRepliedToYou else Copy.InboxReply),
                    Type.Micro,
                    color = if (vanish) Color.White.copy(alpha = 0.55f) else Color(0xFF7C6AA8),
                    fontSizeOverride = 11f,
                )
                Spacer(Modifier.height(rdp(4)))
                ChatReplyCite(
                    quoted = reply,
                    vanish = vanish,
                    onClick = { onJumpReply(reply.id) },
                )
                Spacer(Modifier.height(rdp(6)))
            }
            Box {
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine && chat.group) {
                XaniText(msg.from, Type.Micro, color = Color(msg.color), fontSizeOverride = 10f)
            }
            val isMedia = msg.kind == MsgKind.Image || msg.kind == MsgKind.Video || msg.kind == MsgKind.Ai
            if (isMedia) {
                val hasCaption = msg.text.isNotBlank()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(8)),
                ) {
                    if (mine) ForwardMediaBtn(onForward)
                    Column(
                        Modifier
                            .width(rdp(220))
                            .clip(RoundedCornerShape(rdp(16)))
                            .then(
                                if (hasCaption && mine) Modifier.background(Brush.linearGradient(listOf(Color(theme.start), Color(theme.end))))
                                else if (hasCaption) Modifier.background(theirBg)
                                else Modifier,
                            ),
                    ) {
                        Box {
                            LocalThumb(
                                uri = msg.mediaUri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rdp(160)),
                                fallback = chat.tint,
                            )
                            if (msg.kind == MsgKind.Video) {
                                Box(
                                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(rdp(48)).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                                        contentAlignment = Alignment.Center,
                                    ) { ChatIco.Play(Color(0xFF111111), rdp(18)) }
                                }
                            }
                            if (!hasCaption) {
                                Row(
                                    Modifier.align(Alignment.BottomEnd).padding(end = rdp(10), bottom = rdp(8)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    XaniText(msg.time, Type.Micro, color = Color.White.copy(alpha = 0.9f), fontSizeOverride = 10f)
                                    if (mine) {
                                        Spacer(Modifier.width(rdp(3)))
                                        ChatIco.DeliveryMark(msg.delivery, rdp(12))
                                    }
                                }
                            }
                        }
                        if (hasCaption) {
                            Column(Modifier.padding(horizontal = rdp(10), vertical = rdp(8))) {
                                ChatBodyText(msg.text, if (mine) Color.White else theirText, if (mine) Color(0xFFBFDBFE) else Color(0xFF2563EB))
                                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    XaniText(
                                        msg.time,
                                        Type.Micro,
                                        color = if (mine) Color.White.copy(alpha = 0.7f) else Color(0xFF888888),
                                        fontSizeOverride = 10f,
                                    )
                                    if (mine) {
                                        Spacer(Modifier.width(rdp(3)))
                                        ChatIco.DeliveryMark(msg.delivery, rdp(12))
                                    }
                                }
                            }
                        }
                    }
                    if (!mine) ForwardMediaBtn(onForward)
                }
            } else {
            val solid = bubbleSolid?.let { Color(it) }
            val bubble = if (mine && solid == null) Brush.linearGradient(listOf(Color(theme.start), Color(theme.end))) else null
            val shape = RoundedCornerShape(
                topStart = rdp(16),
                topEnd = rdp(16),
                bottomStart = rdp(if (mine) 16 else 4),
                bottomEnd = rdp(if (mine) 4 else 16),
            )
            Column(
                Modifier
                    .clip(shape)
                    .then(
                        when {
                            mine && solid != null -> Modifier.background(solid)
                            bubble != null -> Modifier.background(bubble)
                            else -> Modifier.background(theirBg)
                        },
                    )
                    .padding(start = rdp(12), end = rdp(12), top = rdp(8), bottom = rdp(8)),
            ) {
                when (msg.kind) {
                    MsgKind.Voice -> VoiceChip(msg, mine, if (mine) Color.White else theirText)
                    MsgKind.System -> {
                        val call = parseCallMsg(msg.text)
                        if (call != null) CallChip(call, mine, if (mine) Color.White else theirText, copy)
                        else XaniText(msg.text, Type.Label, color = if (mine) Color.White else theirText)
                    }
                    MsgKind.Contact -> ContactCard(
                        msg, mine, if (mine) Color.White else theirText, copy,
                        onMessage = {
                            val name = msg.text.substringBefore('\n').trim()
                            if (name.isNotBlank()) onOpenContact(name)
                        },
                    )
                    MsgKind.Location -> {
                        XaniText(
                            "📍 ${msg.text.removePrefix("📍 ").ifBlank { copy.t(Copy.InboxLocation) }}",
                            Type.Label,
                            color = if (mine) Color.White else theirText,
                            modifier = Modifier.pressable { openMaps(ctx, msg.text) },
                        )
                    }
                    MsgKind.Document -> XaniText("📄 ${msg.text.ifBlank { "Document" }}", Type.Label, color = if (mine) Color.White else theirText)
                    MsgKind.Poll -> PollBubble(
                        msg, mine, if (mine) Color.White else theirText, inbox,
                        onHeart = {
                            vanishHaptic(view)
                            onHeart()
                        },
                        onHold = onHold,
                    )
                    MsgKind.Event -> EventBubble(msg, mine, if (mine) Color.White else theirText, copy)
                    else -> if (msg.text.isNotBlank()) {
                        ChatBodyText(msg.text, if (mine) Color.White else theirText, if (mine) Color(0xFFBFDBFE) else Color(0xFF2563EB))
                    }
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (msg.edited) {
                        XaniText(copy.t(Copy.InboxEdited), Type.Micro, color = if (mine) Color.White.copy(alpha = 0.55f) else Colors.Muted, fontSizeOverride = 9f)
                        Spacer(Modifier.width(rdp(4)))
                    }
                    XaniText(msg.time, Type.Micro, color = if (mine) Color.White.copy(alpha = 0.7f) else Colors.Sub, fontSizeOverride = 11f)
                    if (mine) {
                        Spacer(Modifier.width(rdp(3)))
                        ChatIco.DeliveryMark(msg.delivery, rdp(12))
                    }
                }
            }
                }
            }
            if (msg.reaction != null) {
                ReactionIcon(
                    emoji = msg.reaction,
                    play = pulse,
                    count = msg.reactors.size.coerceAtLeast(1),
                    onClick = onReaction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(
                            x = if (mine) rdp(10) else rdp(6),
                            y = rdp(8),
                        ),
                )
            }
            }
        }
        }
    }
}

@Composable
private fun ChatBodyText(text: String, color: Color, link: Color) {
    val annotated = remember(text, color, link) {
        runCatching {
            buildAnnotatedString {
                var last = 0
                ChatLinkFind.findAll(text).forEach { match ->
                    val raw = match.value.trimEnd('.', ',', ';', ')', ']', '!', '?')
                    val start = match.range.first.coerceIn(0, text.length)
                    if (start > last) withStyle(SpanStyle(color = color)) { append(text.substring(last, start)) }
                    val url = httpUrl(raw)
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)),
                        ),
                    ) { append(raw) }
                    last = (start + raw.length).coerceAtMost(text.length)
                }
                if (last < text.length) withStyle(SpanStyle(color = color)) { append(text.substring(last)) }
            }
        }.getOrElse {
            buildAnnotatedString { withStyle(SpanStyle(color = color)) { append(text) } }
        }
    }
    XaniText(text = annotated, style = Type.Label)
}

@Composable
private fun ReactionIcon(
    emoji: String,
    play: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val scale = remember { Animatable(1f) }
    val lift = remember { Animatable(0f) }
    var played by remember(emoji) { mutableStateOf(false) }
    LaunchedEffect(play, emoji) {
        if (!play) {
            played = false
            scale.snapTo(1f)
            lift.snapTo(0f)
            return@LaunchedEffect
        }
        if (played) {
            scale.snapTo(1f)
            lift.snapTo(0f)
            return@LaunchedEffect
        }
        played = true
        scale.snapTo(0.82f)
        lift.snapTo(0f)
        val rise = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        val fall = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
        coroutineScope {
            launch { lift.animateTo(-52f, tween(420, easing = rise)) }
            launch { scale.animateTo(1.32f, tween(420, easing = rise)) }
        }
        vanishHaptic(view)
        scale.animateTo(1.44f, tween(90))
        coroutineScope {
            launch { scale.animateTo(1f, tween(500, easing = fall)) }
            launch { lift.animateTo(0f, tween(500, easing = fall)) }
        }
    }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(rdp(14)))
                .background(Color(0xFFEDE8F5))
                .border(rdp(0.5f), Color(0xFFD4CCE4), RoundedCornerShape(rdp(14)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = rdp(6), vertical = rdp(3)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText(emoji, Type.LabelBold, fontSizeOverride = 14f, modifier = Modifier.graphicsLayer { alpha = 0f })
            if (count > 1) {
                Spacer(Modifier.width(rdp(3)))
                XaniText(count.toString(), Type.Micro, color = Color(0xFF5B4B73), fontSizeOverride = 10f)
            }
        }
        XaniText(
            emoji,
            Type.LabelBold,
            fontSizeOverride = 14f,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = rdp(6))
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    translationY = lift.value
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
internal fun ChatReplyCite(
    quoted: ChatMessage,
    vanish: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    title: String? = null,
    modifier: Modifier = Modifier,
) {
    val media = quoted.kind == MsgKind.Image || quoted.kind == MsgKind.Video || quoted.kind == MsgKind.Ai
    Row(
        modifier
            .widthIn(max = rdp(260))
            .clip(RoundedCornerShape(rdp(10)))
            .background(if (vanish) Color.White.copy(alpha = 0.08f) else Color(0xFFF3EEF8))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(8), vertical = rdp(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(rdp(3))
                .height(rdp(36))
                .clip(RoundedCornerShape(rdp(2)))
                .background(Brush.verticalGradient(listOf(Dark.Primary, Dark.Accent))),
        )
        Column(Modifier.weight(1f).padding(horizontal = rdp(8))) {
            if (title != null) {
                XaniText(title, Type.LabelBold, color = Dark.Primary, fontSizeOverride = 11f)
            }
            XaniText(
                quoted.text.ifBlank { previewOf(quoted) },
                Type.Caption,
                color = if (vanish) Color.White.copy(alpha = 0.55f) else Color(0xFF6B6578),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = 0.78f },
            )
        }
        if (media) {
            Box(
                Modifier
                    .size(rdp(40))
                    .clip(RoundedCornerShape(rdp(8)))
                    .graphicsLayer { alpha = 0.82f },
            ) {
                LocalThumb(uri = quoted.mediaUri, modifier = Modifier.fillMaxSize(), fallback = Color(quoted.color), maxEdge = 160)
            }
            Spacer(Modifier.width(rdp(6)))
        }
        if (onClear != null) {
            Box(
                Modifier.size(rdp(28)).pressable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                ChatIco.Close(if (vanish) Color.White.copy(alpha = 0.5f) else Color(0xFF888888), rdp(14))
            }
        }
    }
}

@Composable
private fun PollBubble(
    msg: ChatMessage,
    mine: Boolean,
    ink: Color,
    inbox: InboxController,
    onHeart: () -> Unit,
    onHold: () -> Unit,
) {
    val opts = pollOptions(msg.text)
    val multi = pollMulti(msg.text)
    val mineSet = inbox.pollMine[msg.id] ?: emptySet()
    val counts = inbox.pollTally[msg.id] ?: List(opts.size) { 0 }
    val mark = if (multi) RoundedCornerShape(rdp(4)) else CircleShape
    Column(verticalArrangement = Arrangement.spacedBy(rdp(6))) {
        val q = pollQuestion(msg.text)
        if (q.isNotBlank()) {
            XaniText(
                q,
                Type.LabelBold,
                color = ink,
                fontSizeOverride = 14f,
                modifier = Modifier.pointerInput(msg.id) {
                    detectTapGestures(onDoubleTap = { onHeart() }, onLongPress = { onHold() })
                },
            )
        }
        opts.forEachIndexed { i, opt ->
            val on = i in mineSet
            val n = counts.getOrElse(i) { 0 }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(
                        when {
                            on && mine -> Color.White.copy(alpha = 0.28f)
                            on -> Dark.Primary.copy(alpha = 0.12f)
                            mine -> Color.White.copy(alpha = 0.16f)
                            else -> Color.Black.copy(alpha = 0.05f)
                        },
                    )
                    .clickable(
                        interactionSource = remember(msg.id, i) { MutableInteractionSource() },
                        indication = null,
                    ) { inbox.votePoll(msg.id, i, opts.size, multi) }
                    .padding(horizontal = rdp(10), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(rdp(18))
                        .clip(mark)
                        .border(rdp(1.5f), if (on) Dark.Primary else ink.copy(alpha = 0.55f), mark)
                        .background(if (on) Dark.Primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) ChatIco.Tick(Color.White, rdp(10))
                }
                Spacer(Modifier.width(rdp(8)))
                XaniText(opt, Type.Label, color = ink, fontSizeOverride = 13f, modifier = Modifier.weight(1f))
                XaniText(n.toString(), Type.LabelBold, color = ink.copy(alpha = 0.8f), fontSizeOverride = 12f)
            }
        }
    }
}

@Composable
private fun EventBubble(msg: ChatMessage, mine: Boolean, ink: Color, copy: Copy) {
    val ctx = LocalContext.current
    val ev = parseEvent(msg.text)
    val whenStr = if (ev.at > 0L) {
        java.text.SimpleDateFormat("EEE, d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ev.at))
    } else copy.t(Copy.InboxEventWhen)
    Column(Modifier.width(rdp(220)), verticalArrangement = Arrangement.spacedBy(rdp(4))) {
        XaniText(ev.name.ifBlank { copy.t(Copy.InboxEvent) }, Type.LabelBold, color = ink, fontSizeOverride = 14f)
        XaniText(whenStr, Type.Caption, color = ink.copy(alpha = 0.8f))
        if (ev.loc.isNotBlank()) {
            XaniText(
                ev.loc,
                Type.Caption,
                color = ink.copy(alpha = 0.8f),
                modifier = Modifier.pressable { openMaps(ctx, ev.loc) },
            )
        }
        if (ev.desc.isNotBlank()) XaniText(ev.desc, Type.Caption, color = ink.copy(alpha = 0.75f), maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(if (mine) Color.White.copy(alpha = 0.2f) else Dark.Primary.copy(alpha = 0.12f))
                    .pressable { openCalendar(ctx, ev) }
                    .padding(horizontal = rdp(10), vertical = rdp(6)),
            ) {
                XaniText(copy.t(Copy.InboxAddCalendar), Type.Micro, color = if (mine) Color.White else Dark.Primary, fontSizeOverride = 11f)
            }
            if (mine) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = rdp(10), vertical = rdp(6)),
                ) {
                    XaniText(copy.t(Copy.InboxEditEvent), Type.Micro, color = ink, fontSizeOverride = 11f)
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    msg: ChatMessage,
    mine: Boolean,
    ink: Color,
    copy: Copy,
    onMessage: () -> Unit,
) {
    val name = msg.text.substringBefore('\n').ifBlank { copy.t(Copy.InboxContact) }
    val note = msg.text.substringAfter('\n', missingDelimiterValue = "").trim()
    Column(Modifier.width(rdp(210))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            Box(
                Modifier
                    .size(rdp(52))
                    .clip(CircleShape)
                    .background(if (mine) Color.White.copy(alpha = 0.22f) else Color(msg.color)),
                contentAlignment = Alignment.Center,
            ) {
                if (msg.mediaUri != null) {
                    LocalThumb(uri = msg.mediaUri, modifier = Modifier.fillMaxSize(), fallback = Color(msg.color))
                } else {
                    XaniText(name.take(1).uppercase(), Type.LabelBold, color = Color.White, fontSizeOverride = 18f)
                }
            }
            Column(Modifier.weight(1f)) {
                XaniText(name, Type.LabelBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSizeOverride = 14f)
                if (note.isNotBlank()) {
                    XaniText(
                        note,
                        Type.Caption,
                        color = ink.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(rdp(8)))
        Box(
            Modifier
                .fillMaxWidth()
                .height(rdp(34))
                .clip(RoundedCornerShape(rdp(10)))
                .background(if (mine) Color.White.copy(alpha = 0.22f) else Dark.Primary.copy(alpha = 0.14f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMessage,
                ),
            contentAlignment = Alignment.Center,
        ) {
            XaniText(copy.t(Copy.InboxTypeMessage), Type.LabelBold, color = ink, fontSizeOverride = 13f)
        }
    }
}

@Composable
private fun VoiceChip(msg: ChatMessage, mine: Boolean, ink: Color) {
    val playingId by VoicePlayer.playingId.collectAsState()
    val playing = playingId == msg.id
    val bars = intArrayOf(10, 18, 8, 22, 14, 26, 12, 20, 16, 24, 9, 17)
    Row(
        Modifier.width(rdp(220)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
    ) {
        Box(
            Modifier
                .size(rdp(36))
                .clip(CircleShape)
                .background(if (mine) Color.White.copy(alpha = 0.22f) else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(3))) {
                    Box(Modifier.width(rdp(3)).height(rdp(12)).background(if (mine) Color.White else Color(0xFF111111)))
                    Box(Modifier.width(rdp(3)).height(rdp(12)).background(if (mine) Color.White else Color(0xFF111111)))
                }
            } else {
                ChatIco.Play(if (mine) Color.White else Color(0xFF111111), rdp(14))
            }
        }
        Row(
            Modifier.weight(1f).height(rdp(28)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bars.forEach { h ->
                Box(
                    Modifier
                        .width(rdp(3))
                        .height(rdp(h))
                        .clip(RoundedCornerShape(rdp(2)))
                        .background(ink.copy(alpha = if (playing) 1f else 0.85f)),
                )
            }
        }
        XaniText(msg.text.ifBlank { "0:07" }, Type.Micro, color = ink.copy(alpha = 0.85f), fontSizeOverride = 11f)
    }
}

@Composable
private fun CallChip(call: CallMsg, mine: Boolean, ink: Color, copy: Copy) {
    val label = if (call.video) "Video call" else "Voice call"
    val status = when (call.status) {
        "ongoing" -> copy.t(Copy.CallOngoing)
        "declined" -> copy.t(Copy.CallDeclined)
        "missed" -> copy.t(Copy.CallMissed)
        "cancelled" -> copy.t(Copy.CallEnded)
        "busy" -> copy.t(Copy.CallBusy)
        else -> if (call.durationSec > 0) formatCallClock(call.durationSec) else copy.t(Copy.CallEnded)
    }
    Row(
        Modifier.widthIn(max = rdp(220)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(10)),
    ) {
        Box(
            Modifier
                .size(rdp(36))
                .clip(CircleShape)
                .background(if (mine) Color.White.copy(alpha = 0.22f) else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            XaniText(if (call.video) "📹" else "📞", Type.Label, fontSizeOverride = 14f)
        }
        Column {
            XaniText(label, Type.LabelBold, color = ink, fontSizeOverride = 13f)
            XaniText(status, Type.Micro, color = ink.copy(alpha = 0.8f), fontSizeOverride = 11f)
        }
    }
}

@Composable
private fun ForwardMediaBtn(onClick: () -> Unit) {
    Box(
        Modifier
            .size(rdp(34))
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { ChatIco.ForwardFill(Color.White, rdp(17)) }
}

@Composable
private fun ThreadMenu(
    copy: Copy,
    vanish: Boolean,
    receipts: Boolean,
    onReceipts: () -> Unit,
    onMedia: () -> Unit,
    onRead: () -> Unit,
    onMute: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize().pressable(onClick = onDismiss)) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = rdp(58), end = rdp(10))
                .width(rdp(220))
                .clip(RoundedCornerShape(rdp(16)))
                .background(if (vanish) Color(0xFF1A1A2E) else Color.White)
                .border(rdp(1), if (vanish) Color.White.copy(alpha = 0.08f) else Color(0xFFEBEBEB), RoundedCornerShape(rdp(16))),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(16), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.InboxReadReceipts), Type.LabelStrong, color = if (vanish) Color.White else Color(0xFF111111), modifier = Modifier.weight(1f), fontSizeOverride = 14f)
                Box(
                    Modifier
                        .width(rdp(42))
                        .height(rdp(24))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(if (receipts) Color(0xFF3B82F6) else Color(0xFFD1D5DB))
                        .pressable { onReceipts() },
                ) {
                    Box(
                        Modifier
                            .padding(start = rdp(if (receipts) 20 else 3), top = rdp(3))
                            .size(rdp(18))
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
            MenuRow(copy.t(Copy.InboxMedia), vanish, onMedia)
            MenuRow(copy.t(Copy.InboxMarkRead), vanish, onRead)
            MenuRow(copy.t(Copy.InboxMuteNotifs), vanish, onMute)
            MenuRow(copy.t(Copy.InboxShareProfile), vanish, onShare)
            MenuRow(copy.t(Copy.InboxExportChat), vanish, onDismiss)
            MenuRow(copy.t(Copy.InboxClearChat), vanish, onClear, danger = true)
        }
    }
}

@Composable
private fun MenuRow(label: String, vanish: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    XaniText(
        label,
        Type.LabelStrong,
        color = if (danger) Color(0xFFEF4444) else if (vanish) Color.White else Color(0xFF111111),
        modifier = Modifier
            .fillMaxWidth()
            .pressable(role = Role.Button, onClick = onClick)
            .padding(horizontal = rdp(16), vertical = rdp(13)),
        fontSizeOverride = 14f,
    )
}

@Composable
private fun ShareSheet(
    inbox: InboxController,
    copy: Copy,
    vanish: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onPick: (InboxChat) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pressable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                .background(if (vanish) Color(0xFF1A1A2E) else Color.White)
                .navigationBarsPadding()
                .padding(rdp(16)),
        ) {
            XaniText(title, Type.LabelBold, color = if (vanish) Color.White else Color(0xFF111111), fontSizeOverride = 16f)
            Spacer(Modifier.height(rdp(12)))
            inbox.chats.filter { !it.group }.take(8).forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { onPick(c) }
                        .padding(vertical = rdp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(rdp(40)).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) {
                        XaniText(c.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                    }
                    Spacer(Modifier.width(rdp(12)))
                    XaniText(c.name, Type.LabelStrong, color = if (vanish) Color.White else Color(0xFF111111))
                }
            }
        }
    }
}

@Composable
private fun MediaViewer(
    msg: ChatMessage,
    copy: Copy,
    onClose: () -> Unit,
    onForward: () -> Unit,
    onReply: (String) -> Unit,
) {
    var chrome by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val video = msg.kind == MsgKind.Video
    Box(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        LocalThumb(
            uri = msg.mediaUri,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(chrome) {
                    detectTapGestures {
                        chrome = !chrome
                        if (!chrome) {
                            keyboard?.hide()
                            focus.clearFocus()
                        }
                    }
                },
            fallback = Color.Black,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            if (video) {
                Box(
                    Modifier
                        .size(rdp(64))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) { ChatIco.Play(Color(0xFF111111), rdp(24)) }
            }
        }
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.58f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = rdp(10), vertical = rdp(8)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(rdp(36))
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .pressable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { ChatIco.Close(Color.White, rdp(16)) }
                    Spacer(Modifier.width(rdp(10)))
                    Box(
                        Modifier.size(rdp(34)).clip(CircleShape).background(Color(msg.color)),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(msg.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 12f)
                    }
                    Spacer(Modifier.width(rdp(8)))
                    Column(Modifier.weight(1f)) {
                        XaniText(
                            if (msg.mine) copy.t(Copy.InboxYou) else msg.from,
                            Type.LabelBold,
                            color = Color.White,
                            fontSizeOverride = 14f,
                            maxLines = 1,
                        )
                        XaniText(msg.time, Type.Micro, color = Color.White.copy(alpha = 0.72f), fontSizeOverride = 11f)
                    }
                    Box(
                        Modifier
                            .size(rdp(36))
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .pressable {
                                val uri = msg.mediaUri
                                if (uri.isNullOrBlank()) return@pressable
                                scope.launch {
                                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        saveSharedMedia(context, uri, video)
                                    }
                                    hint = copy.t(if (ok) Copy.InboxCopied else Copy.InboxDownload)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { ChatIco.Download(Color.White, rdp(16)) }
                    Spacer(Modifier.width(rdp(8)))
                    Box(
                        Modifier
                            .size(rdp(36))
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .pressable(onClick = onForward),
                        contentAlignment = Alignment.Center,
                    ) { ChatIco.Forward(Color.White, rdp(16)) }
                }
            }
        }
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = rdp(12), vertical = rdp(10)),
            ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(24)))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(start = rdp(14), end = rdp(6), top = rdp(6), bottom = rdp(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(400) },
                    modifier = Modifier.weight(1f).padding(vertical = rdp(8)),
                    textStyle = Type.Label.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 4,
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                XaniText(copy.t(Copy.InboxWriteMsg), Type.Label, color = Color.White.copy(alpha = 0.55f))
                            }
                            inner()
                        }
                    },
                )
                val ready = draft.isNotBlank()
                Box(
                    Modifier
                        .size(rdp(40))
                        .clip(CircleShape)
                        .background(if (ready) Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)) else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF))))
                        .pressable(enabled = chrome && ready) {
                            onReply(draft.trim())
                            draft = ""
                            keyboard?.hide()
                            focus.clearFocus()
                        },
                    contentAlignment = Alignment.Center,
                ) { ChatIco.SendTri(if (ready) Color.White else Color.White.copy(alpha = 0.45f), rdp(18)) }
            }
            }
        }
        hint?.let { msgText ->
            LaunchedEffect(msgText) {
                kotlinx.coroutines.delay(1200)
                hint = null
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = rdp(88))
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(Color(0xE6111111))
                    .padding(horizontal = rdp(14), vertical = rdp(6)),
            ) {
                XaniText(msgText, Type.Micro, color = Color.White, fontSizeOverride = 12f)
            }
        }
    }
}

@Composable
private fun RoundIcon(bg: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(rdp(36))
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun vanishHaptic(view: android.view.View) {
    val flags = android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM, flags)
    }
    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK, flags)
    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, flags)
}
