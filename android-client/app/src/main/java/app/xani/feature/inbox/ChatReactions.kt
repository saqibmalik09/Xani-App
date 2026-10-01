package app.xani.feature.inbox

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import app.xani.i18n.Copy
import app.xani.ui.brand.LocalBrand
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

private val EmojiSmileys = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😊", "😍", "🥰",
    "😘", "🤩", "🥳", "😏", "😒", "😞", "😔", "😢", "😭", "😤", "😠", "😡",
)
private val EmojiGestures = listOf(
    "👋", "🤚", "🖐", "✋", "🤙", "👌", "✌️", "🤞", "🤟", "🤘", "👍", "👎",
    "👏", "🙌", "🫶", "🤝", "🙏", "💪",
)
private val EmojiRecent = listOf("🤔", "😏", "🙌", "🙏", "😎", "🤩", "💀", "😢", "🥹", "❤️", "😂", "👍")
private val EmojiCats = listOf(EmojiRecent, EmojiSmileys, EmojiGestures)

@Composable
internal fun MessageActionOverlay(
    copy: Copy,
    msg: ChatMessage,
    vanish: Boolean,
    selectedEmoji: String?,
    onReact: (String) -> Unit,
    onPlus: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit,
    onDelete: () -> Unit,
    onUnsend: () -> Unit,
    onEdit: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val ink = if (vanish) Color.White else Color(0xFF111111)
    val icon = Color(0xFF333333)
    // A Dialog, not a sibling Box: outside taps and the system back button
    // dismiss it, and nothing under it can keep eating the gesture (which is
    // what left the old overlay stuck until the process died).
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pressable(pressedScale = 1f, onClick = onDismiss),
        ) {
            Column(
                modifier = Modifier
                    .align(if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = rdp(10))
                    .width(rdp(280))
                    // The menu itself is not a dismiss target.
                    .pressable(pressedScale = 1f, onClick = {}),
                horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(rdp(40)))
                        .background(Color.White.copy(alpha = 0.97f))
                        .padding(horizontal = rdp(6), vertical = rdp(4)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickReactions.forEach { em ->
                        val on = selectedEmoji == em
                        Box(
                            Modifier
                                .size(rdp(40))
                                .clip(CircleShape)
                                .background(if (on) Color(0xFFF3E8FF) else Color.Transparent)
                                .pressable { onReact(em) },
                            contentAlignment = Alignment.Center,
                        ) {
                            XaniText(em, Type.Label, fontSizeOverride = 26f)
                        }
                    }
                    Box(
                        Modifier
                            .size(rdp(40))
                            .clip(CircleShape)
                            .background(Color(0xFFF0F0F0))
                            .pressable(onClick = onPlus),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatIco.Plus(Color(0xFF555555), rdp(18))
                    }
                }
                Spacer(Modifier.height(rdp(6)))
                Column(
                    Modifier
                        .width(rdp(210))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(Color.White),
                ) {
                    var expandDel by remember { mutableStateOf(false) }
                    ActionRow(copy.t(Copy.InboxReply), { ChatIco.Reply(icon) }, ink, onReply)
                    if (msg.mine && msg.kind == MsgKind.Text) {
                        ActionRow(copy.t(Copy.InboxEdit), { ChatIco.Pencil(icon) }, ink, onEdit)
                    }
                    ActionRow(copy.t(Copy.InboxForward), { ChatIco.Forward(icon) }, ink, onForward)
                    ActionRow(copy.t(Copy.InboxDeleteMsg), { ChatIco.Trash(Color(0xFFEF4444), rdp(18)) }, Color(0xFFEF4444), { expandDel = !expandDel })
                    if (expandDel) {
                        ActionRow(copy.t(Copy.InboxFromYou), { ChatIco.Trash(icon, rdp(16)) }, ink, onDelete)
                        if (msg.mine) {
                            ActionRow(copy.t(Copy.InboxEveryone), { ChatIco.Unsend(Color(0xFFEF4444), rdp(16)) }, Color(0xFFEF4444), onUnsend)
                        }
                    }
                    ActionRow(copy.t(Copy.InboxCopyAction), { ChatIco.CopyLines(icon) }, ink, onCopy)
                    if (msg.kind == MsgKind.Image || msg.kind == MsgKind.Video) {
                        ActionRow(copy.t(Copy.InboxDownload), { ChatIco.Download(icon) }, ink, onDownload)
                    }
                    ActionRow(copy.t(Copy.InboxMore), { ChatIco.MoreH(icon, rdp(18)) }, ink, onMore, last = true)
                }
            }
        }
    }
}

@Composable
internal fun MsgMoreSheet(
    copy: Copy,
    msg: ChatMessage,
    pinned: Boolean,
    onPin: () -> Unit,
    onInfo: () -> Unit,
    onReport: () -> Unit,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val icon = Color(0xFF333333)
    val density = LocalDensity.current
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp * density.density }
    var dragging by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var targetY by remember { mutableFloatStateOf(screenPx) }
    LaunchedEffect(Unit) { targetY = 0f }
    val y by animateFloatAsState(
        targetValue = targetY,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.88f, stiffness = 380f),
        finishedListener = { if (closing) onDismiss() },
        label = "moreY",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (0.4f * (1f - (y / screenPx).coerceIn(0f, 1f)))))
                .pressable(onClick = {
                    if (dragging) return@pressable
                    closing = true
                    targetY = screenPx
                }),
        )
        val sheetDrag = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { dragging = true },
                onDragEnd = {
                    dragging = false
                    if (targetY > 140f) {
                        closing = true
                        targetY = screenPx
                    } else {
                        targetY = 0f
                    }
                },
                onDragCancel = {
                    dragging = false
                    targetY = 0f
                },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    targetY = (targetY + amount).coerceIn(0f, screenPx * 0.85f)
                },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, y.toInt()) }
                .clip(RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24)))
                .background(Color.White)
                .navigationBarsPadding()
                .then(sheetDrag),
        ) {
            Box(
                Modifier.fillMaxWidth().height(rdp(36)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(rdp(40)).height(rdp(4)).clip(RoundedCornerShape(rdp(2))).background(Color(0xFFE0E0E0)))
            }
            ActionRow(
                copy.t(if (pinned) Copy.InboxMsgUnpin else Copy.InboxMsgPin),
                { ChatIco.Pin(if (pinned) Dark.Primary else icon, filled = pinned) },
                Color(0xFF111111),
                onPin,
            )
            if (msg.mine) {
                ActionRow(copy.t(Copy.InboxInfo), { ChatIco.Info(icon) }, Color(0xFF111111), onInfo)
            }
            ActionRow(copy.t(Copy.InboxReport), { ChatIco.Report(Color(0xFFEF4444)) }, Color(0xFFEF4444), onReport)
            ActionRow(copy.t(Copy.InboxTranslate), { ChatIco.Translate(icon) }, Color(0xFF111111), onTranslate, last = true)
            Spacer(Modifier.height(rdp(8)))
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: @Composable () -> Unit,
    color: Color,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(role = Role.Button, onClick = onClick)
            .then(if (last) Modifier else Modifier.border(rdp(1), Color(0xFFF5F5F5), RoundedCornerShape(0)))
            .padding(horizontal = rdp(16), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(18)), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(rdp(12)))
        XaniText(label, Type.LabelStrong, color = color, fontSizeOverride = 14f)
    }
}

@Composable
internal fun EmojiPickSheet(
    copy: Copy,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableIntStateOf(0) }
    val pool = if (query.isBlank()) EmojiCats[cat.coerceIn(EmojiCats.indices)]
    else (EmojiRecent + EmojiSmileys + EmojiGestures).distinct()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pressable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = rdp(520))
                .clip(RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24)))
                .background(Color.White)
                .navigationBarsPadding()
                .pressable { },
        ) {
            Box(Modifier.fillMaxWidth().padding(top = rdp(10), bottom = rdp(6)), contentAlignment = Alignment.Center) {
                Box(Modifier.width(rdp(40)).height(rdp(4)).clip(RoundedCornerShape(rdp(2))).background(Color(0xFFE0E0E0)))
            }
            Row(
                Modifier
                    .padding(horizontal = rdp(16), vertical = rdp(6))
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = rdp(14), vertical = rdp(9)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatIco.Search(Color(0xFF9CA3AF), rdp(16))
                Spacer(Modifier.width(rdp(8)))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    modifier = Modifier.weight(1f),
                    textStyle = Type.Label.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Dark.Primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) XaniText(copy.t(Copy.InboxSearchShort), Type.Label, color = Color(0xFF9CA3AF))
                            inner()
                        }
                    },
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = rdp(16))) {
                if (query.isEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = rdp(10)), verticalAlignment = Alignment.CenterVertically) {
                        XaniText(copy.t(Copy.InboxYourReactions), Type.LabelBold, color = Color(0xFF111111), modifier = Modifier.weight(1f), fontSizeOverride = 15f)
                        XaniText(copy.t(Copy.InboxCustomize), Type.LabelBold, color = Color(0xFF3B82F6), fontSizeOverride = 13f)
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = rdp(16)), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                        QuickReactions.forEach { em ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(rdp(16)))
                                    .background(if (selected == em) Color(0xFFF3E8FF) else Color(0xFFF9FAFB))
                                    .pressable { onPick(em) }
                                    .padding(vertical = rdp(8)),
                                contentAlignment = Alignment.Center,
                            ) { XaniText(em, Type.Label, fontSizeOverride = 28f) }
                        }
                    }
                }
                val cells = pool.chunked(6)
                cells.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { em ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .pressable { onPick(em) }
                                    .padding(vertical = rdp(6)),
                                contentAlignment = Alignment.Center,
                            ) { XaniText(em, Type.Label, fontSizeOverride = 26f) }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(rdp(8)))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = rdp(10))) {
                listOf("🕒", "😊", "🤟").forEachIndexed { i, mark ->
                    Column(
                        Modifier.weight(1f).pressable { cat = i; query = "" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        XaniText(mark, Type.Label, fontSizeOverride = 20f)
                        Box(
                            Modifier
                                .width(rdp(20))
                                .height(rdp(2))
                                .clip(RoundedCornerShape(rdp(1)))
                                .background(if (cat == i && query.isEmpty()) Dark.Primary else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ForwardPickSheet(
    inbox: InboxController,
    copy: Copy,
    preview: String,
    mediaUri: String? = null,
    kind: MsgKind = MsgKind.Text,
    onDismiss: () -> Unit,
    onSend: (List<InboxChat>, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0.04f) }
    var hint by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<Int>() }
    val people = inbox.chats.filter {
        query.isBlank() || it.name.contains(query, true) || it.handle.contains(query, true)
    }
    val density = LocalDensity.current
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp * density.density }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val brand = LocalBrand.current
    val clipboard = LocalClipboardManager.current
    val shareLink = "https://${brand.wordmark}.app/s"
    val scope = rememberCoroutineScope()
    val isMedia = kind == MsgKind.Image || kind == MsgKind.Video || kind == MsgKind.Ai
    LaunchedEffect(Unit) {
        keyboard?.hide()
        focus.clearFocus()
        target = 0.62f
    }
    val frac by animateFloatAsState(
        targetValue = target,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.9f, stiffness = 260f),
        finishedListener = { if (closing && it < 0.06f) onDismiss() },
        label = "fwdH",
    )
    fun settle() {
        dragging = false
        if (target < 0.38f) {
            closing = true
            target = 0.02f
        } else {
            closing = false
            target = target.coerceIn(0.42f, 0.92f)
        }
    }
    val nested = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && target < 0.92f) {
                dragging = true
                closing = false
                val next = (target - available.y / screenPx).coerceIn(0.04f, 0.92f)
                val used = (target - next) * screenPx
                target = next
                return Offset(0f, -used)
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f) {
                dragging = true
                closing = false
                val next = (target - available.y / screenPx).coerceIn(0.04f, 0.92f)
                val used = (target - next) * screenPx
                target = next
                return Offset(0f, used)
            }
            return Offset.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }
    val sheetDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { dragging = true; closing = false },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
            onVerticalDrag = { change, amount ->
                change.consume()
                target = (target - amount / screenPx).coerceIn(0.04f, 0.92f)
            },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (0.45f * frac.coerceIn(0f, 1f))))
                .pressable(onClick = {
                    if (dragging) return@pressable
                    closing = true
                    target = 0.02f
                }),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(frac.coerceAtLeast(0.04f))
                .clip(RoundedCornerShape(topStart = rdp(24), topEnd = rdp(24)))
                .background(Color.White)
                .imePadding()
                .navigationBarsPadding()
                .nestedScroll(nested),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(28))
                    .then(sheetDrag),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(rdp(40)).height(rdp(4)).clip(RoundedCornerShape(rdp(2))).background(Color(0xFFE0E0E0)))
            }
            Row(
                Modifier.padding(horizontal = rdp(16), vertical = rdp(2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.InboxForward), Type.LabelBold, color = Color(0xFF111111), modifier = Modifier.weight(1f), fontSizeOverride = 16f)
                Box(Modifier.size(rdp(32)).pressable(onClick = {
                    closing = true
                    target = 0.02f
                }), contentAlignment = Alignment.Center) {
                    ChatIco.Close(Color(0xFF999999), rdp(16))
                }
            }
            Row(
                Modifier
                    .padding(horizontal = rdp(16), vertical = rdp(6))
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color(0xFFF8F8FF))
                    .padding(horizontal = rdp(10), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMedia && !mediaUri.isNullOrBlank()) {
                    Box(
                        Modifier
                            .size(rdp(56))
                            .clip(RoundedCornerShape(rdp(10)))
                            .background(Color(0xFF111111)),
                    ) {
                        LocalThumb(uri = mediaUri, modifier = Modifier.fillMaxSize(), fallback = Dark.Primary, maxEdge = 160)
                        if (kind == MsgKind.Video) {
                            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                                ChatIco.Play(Color.White, rdp(16))
                            }
                        }
                    }
                    Spacer(Modifier.width(rdp(10)))
                }
                Column(Modifier.weight(1f)) {
                    XaniText(copy.t(Copy.InboxForwarding), Type.LabelBold, color = Dark.Primary, fontSizeOverride = 12f)
                    XaniText(preview, Type.Caption, color = Color(0xFF555555), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                Modifier
                    .padding(horizontal = rdp(16), vertical = rdp(8))
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = rdp(14), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatIco.Search(Color(0xFF888888), rdp(16))
                Spacer(Modifier.width(rdp(8)))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    modifier = Modifier.weight(1f),
                    textStyle = Type.Label.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Dark.Primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) XaniText(copy.t(Copy.InboxSearchShort), Type.Label, color = Color(0xFF888888))
                            inner()
                        }
                    },
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = rdp(8)),
                contentPadding = PaddingValues(horizontal = rdp(16)),
                horizontalArrangement = Arrangement.spacedBy(rdp(16)),
            ) {
                items(people, key = { it.id }) { c ->
                    val on = c.id in picked
                    Column(
                        Modifier
                            .width(rdp(72))
                            .pointerInput(c.id, on) {
                                detectTapGestures {
                                    if (on) picked.remove(c.id) else picked.add(c.id)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Box(
                                Modifier
                                    .size(rdp(64))
                                    .clip(if (c.group) RoundedCornerShape(rdp(18)) else CircleShape)
                                    .background(c.tint),
                                contentAlignment = Alignment.Center,
                            ) { XaniText(c.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 18f) }
                            if (on) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(rdp(20))
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6))
                                        .border(rdp(2), Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { ChatIco.Tick(Color.White, rdp(10)) }
                            }
                        }
                        Spacer(Modifier.height(rdp(6)))
                        XaniText(c.name, Type.Micro, color = Color(0xFF333333), fontSizeOverride = 11f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (picked.isEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = rdp(16), end = rdp(16), top = rdp(14), bottom = rdp(22)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(16)),
                    verticalAlignment = Alignment.Top,
                ) {
                    FwdShareChip(copy.t(Copy.InboxFwdStory), Color(0xFF111111), { ChatIco.ShareStory(Color.White, rdp(22)) }) {
                        inbox.myStory.value = preview
                        hint = copy.t(Copy.InboxAddToStory)
                    }
                    FwdShareChip(copy.t(Copy.InboxWhatsApp), Color(0xFF25D366), { ChatIco.ShareWhatsApp(Color.White, rdp(26)) }) {
                        scope.launch { launchShare(context, preview, mediaUri, kind, "com.whatsapp") }
                    }
                    FwdShareChip(copy.t(Copy.InboxCopyAction), Color(0xFF6B7280), { ChatIco.Link(Color.White, rdp(18)) }) {
                        clipboard.setText(AnnotatedString(shareLink))
                        hint = copy.t(Copy.InboxCopied)
                    }
                    FwdShareChip(copy.t(Copy.InboxFwdStatus), Color(0xFF128C7E), { ChatIco.ShareStatus(Color.White, rdp(22)) }) {
                        scope.launch { launchShare(context, preview, mediaUri, kind, "com.whatsapp") }
                    }
                    FwdShareChip(copy.t(Copy.InboxSnapchat), Color(0xFFFFFC00), { ChatIco.ShareSnap(Color(0xFF111111), rdp(22)) }) {
                        scope.launch { launchShare(context, preview, mediaUri, kind, "com.snapchat.android") }
                    }
                    FwdShareChip(copy.t(Copy.InboxDownload), Color(0xFF8B5CF6), { ChatIco.Download(Color.White, rdp(20)) }) {
                        if (!mediaUri.isNullOrBlank()) scope.launch { launchShare(context, preview, mediaUri, kind, null) }
                        else {
                            clipboard.setText(AnnotatedString(preview))
                            hint = copy.t(Copy.InboxCopied)
                        }
                    }
                    FwdShareChip(copy.t(Copy.InboxMessenger), Color(0xFF006AFF), { ChatIco.ShareMessenger(Color.White, rdp(22)) }) {
                        scope.launch { launchShare(context, preview, mediaUri, kind, "com.facebook.orca") }
                    }
                    FwdShareChip(copy.t(Copy.InboxSms), Color(0xFF34C759), { ChatIco.ShareSms(Color.White, rdp(22)) }) {
                        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", preview)
                        try {
                            context.startActivity(sms)
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { launchShare(context, preview, mediaUri, kind, null) }
                        }
                    }
                    FwdShareChip(copy.t(Copy.InboxLink), Color(0xFF2563EB), { ChatIco.Link(Color.White, rdp(18)) }) {
                        scope.launch { launchShare(context, shareLink, null, MsgKind.Text, null) }
                    }
                    FwdShareChip(copy.t(Copy.InboxFacebook), Color(0xFF1877F2), { ChatIco.ShareFacebook(Color.White, rdp(22)) }) {
                        scope.launch { launchShare(context, preview, mediaUri, kind, "com.facebook.katana") }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = rdp(16), vertical = rdp(10))) {
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = rdp(14), vertical = rdp(10)),
                        textStyle = Type.Label.copy(color = Color(0xFF111111)),
                        cursorBrush = SolidColor(Dark.Primary),
                        decorationBox = { inner ->
                            Box {
                                if (note.isEmpty()) XaniText(copy.t(Copy.InboxWriteMsg), Type.Label, color = Color(0xFF888888))
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(rdp(10)))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rdp(48))
                            .clip(RoundedCornerShape(rdp(24)))
                            .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                            .pressable {
                                onSend(inbox.chats.filter { it.id in picked }, note.trim())
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText("${copy.t(Copy.InboxSendBtn)} (${picked.size})", Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
                    }
                }
            }
            hint?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1400)
                    hint = null
                }
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = rdp(8))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(Color(0xE6111111))
                        .padding(horizontal = rdp(14), vertical = rdp(6)),
                ) {
                    XaniText(msg, Type.Micro, color = Color.White, fontSizeOverride = 12f)
                }
            }
        }
    }
}

@Composable
private fun FwdShareChip(
    label: String,
    bg: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        Modifier.pressable(onClick = onClick).width(rdp(68)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(rdp(56)).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(rdp(8)))
        XaniText(
            label,
            Type.Micro,
            color = Color(0xFF333333),
            fontSizeOverride = 11f,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private suspend fun launchShare(
    context: android.content.Context,
    text: String,
    mediaUri: String?,
    kind: MsgKind,
    pkg: String?,
) {
    val video = kind == MsgKind.Video
    val stream = if (mediaUri.isNullOrBlank()) {
        null
    } else {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            copyShareUri(context, mediaUri, video)
        }
    }
    val mime = when {
        stream == null -> "text/plain"
        video -> "video/mp4"
        kind == MsgKind.Image || kind == MsgKind.Ai -> "image/jpeg"
        else -> "*/*"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        if (stream != null) {
            putExtra(Intent.EXTRA_STREAM, stream)
            clipData = ClipData.newUri(context.contentResolver, "media", stream)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!pkg.isNullOrBlank()) {
                context.grantUriPermission(pkg, stream, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            putExtra(Intent.EXTRA_TEXT, text)
        }
        if (!pkg.isNullOrBlank()) setPackage(pkg)
    }
    try {
        context.startActivity(send)
    } catch (_: ActivityNotFoundException) {
        send.setPackage(null)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        chooser.clipData = send.clipData
        context.startActivity(chooser)
    }
}

@Composable
internal fun MessageInfoSheet(
    copy: Copy,
    msg: ChatMessage,
    theme: ChatTheme,
    onDismiss: () -> Unit,
    /** Clock times from the other side's receipts; empty until they happen. */
    seenAt: String = "",
    deliveredAt: String = "",
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
            .pressable(onClick = onDismiss),
    ) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(8), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChevron(onDismiss)
                XaniText(copy.t(Copy.InboxInfo), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 17f)
            }
            Box(
                Modifier
                    .padding(horizontal = rdp(16), vertical = rdp(12))
                    .clip(RoundedCornerShape(rdp(16)))
                    .border(rdp(2), Color(0xFF22C55E), RoundedCornerShape(rdp(16)))
                    .padding(rdp(14)),
            ) {
                val media = msg.kind == MsgKind.Image || msg.kind == MsgKind.Video || msg.kind == MsgKind.Ai
                Column(
                    Modifier
                        .clip(RoundedCornerShape(rdp(14)))
                        .then(
                            if (media) Modifier.background(Color(0xFF111111))
                            else Modifier.background(Brush.linearGradient(listOf(Color(theme.start), Color(theme.end)))),
                        )
                        .padding(if (media) rdp(0) else rdp(10)),
                ) {
                    if (media) {
                        Box {
                            LocalThumb(
                                uri = msg.mediaUri,
                                modifier = Modifier.fillMaxWidth().height(rdp(280)),
                                fallback = Color(theme.start),
                            )
                            if (msg.kind == MsgKind.Video) {
                                Box(
                                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(rdp(52)).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                                        contentAlignment = Alignment.Center,
                                    ) { ChatIco.Play(Color(0xFF111111), rdp(20)) }
                                }
                            }
                        }
                    } else {
                        XaniText(msg.text.ifBlank { previewOf(msg) }, Type.Label, color = Color.White)
                    }
                    Row(
                        Modifier
                            .align(Alignment.End)
                            .padding(horizontal = rdp(10), vertical = rdp(6)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(msg.time, Type.Micro, color = Color.White.copy(alpha = 0.7f), fontSizeOverride = 10f)
                        Spacer(Modifier.width(rdp(4)))
                        if (msg.mine) ChatIco.DeliveryMark(msg.delivery, rdp(12))
                    }
                }
            }
            if (msg.mine) {
                Column(
                    Modifier
                        .padding(horizontal = rdp(16))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(Color.White)
                        .border(rdp(1), Color(0xFFF0F0F0), RoundedCornerShape(rdp(16))),
                ) {
                    InfoLine(copy.t(Copy.InboxSeen), seenAt.ifEmpty { "—" }, seenAt.isNotEmpty())
                    InfoLine(copy.t(Copy.InboxDelivered), deliveredAt.ifEmpty { "—" }, false)
                }
            }
        }
    }
}

@Composable
internal fun ReactorsSheet(
    copy: Copy,
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onRemoveMine: () -> Unit,
) {
    val rows = msg.reactors.ifEmpty {
        msg.reaction?.let { listOf(MsgReactor(copy.t(Copy.InboxYou), "ME", 0xFF8B5CF6, it, true)) }.orEmpty()
    }
    var dragging by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0.04f) }
    LaunchedEffect(Unit) { target = 0.58f }
    val frac by animateFloatAsState(
        targetValue = target,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.9f, stiffness = 260f),
        finishedListener = { if (closing && it < 0.06f) onDismiss() },
        label = "reactorsH",
    )
    val density = LocalDensity.current
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp * density.density }
    fun settle() {
        dragging = false
        if (target < 0.38f) {
            closing = true
            target = 0.02f
        } else {
            closing = false
            target = target.coerceIn(0.42f, 0.92f)
        }
    }
    val nested = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && target < 0.92f) {
                dragging = true
                closing = false
                val next = (target - available.y / screenPx).coerceIn(0.04f, 0.92f)
                val used = (target - next) * screenPx
                target = next
                return Offset(0f, -used)
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f) {
                dragging = true
                closing = false
                val next = (target - available.y / screenPx).coerceIn(0.04f, 0.92f)
                val used = (target - next) * screenPx
                target = next
                return Offset(0f, used)
            }
            return Offset.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }
    val sheetDrag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { dragging = true; closing = false },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
            onVerticalDrag = { change, amount ->
                change.consume()
                target = (target - amount / screenPx).coerceIn(0.04f, 0.92f)
            },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (0.38f * frac.coerceIn(0f, 1f))))
                .pressable(onClick = {
                    if (dragging) return@pressable
                    closing = true
                    target = 0.02f
                }),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(frac.coerceAtLeast(0.04f))
                .clip(RoundedCornerShape(topStart = rdp(22), topEnd = rdp(22)))
                .background(Color.White)
                .navigationBarsPadding()
                .nestedScroll(nested),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(28))
                    .then(sheetDrag),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(rdp(40)).height(rdp(4)).clip(RoundedCornerShape(rdp(2))).background(Color(0xFFE0E0E0)))
            }
            Box(Modifier.fillMaxWidth().padding(bottom = rdp(8)), contentAlignment = Alignment.Center) {
                XaniText(copy.t(Copy.InboxReactions), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 16f)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                rows.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (row.mine) Modifier.pressable(onClick = onRemoveMine) else Modifier)
                            .padding(horizontal = rdp(18), vertical = rdp(10)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(rdp(42)).clip(CircleShape).background(Color(row.color)),
                            contentAlignment = Alignment.Center,
                        ) {
                            XaniText(row.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                        }
                        Spacer(Modifier.width(rdp(12)))
                        Column(Modifier.weight(1f)) {
                            XaniText(
                                if (row.mine) copy.t(Copy.InboxYou) else row.name,
                                Type.LabelStrong,
                                color = Color(0xFF111111),
                                fontSizeOverride = 15f,
                            )
                            if (row.mine) {
                                XaniText(copy.t(Copy.InboxTapToRemove), Type.Caption, color = Color(0xFF8B8B8B), fontSizeOverride = 12f)
                            }
                        }
                        XaniText(row.emoji, Type.Label, fontSizeOverride = 22f)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(title: String, sub: String, seen: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rdp(18), vertical = rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatIco.DoubleTick(seen)
        Spacer(Modifier.width(rdp(12)))
        Column {
            XaniText(title, Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 15f)
            XaniText(sub, Type.Caption, color = Color(0xFF888888), fontSizeOverride = 12f)
        }
    }
}
