package app.xani.feature.inbox

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatProfileScreen(
    chat: InboxChat,
    inbox: InboxController,
    copy: Copy,
    pane: ThreadPane,
    onPane: (ThreadPane) -> Unit,
    onBack: () -> Unit,
) {
    when (pane) {
        ThreadPane.Search -> SearchChatScreen(chat, inbox, copy, onBack = { onPane(ThreadPane.Profile) })
        ThreadPane.Theme -> ThemePickerScreen(chat, inbox, copy, onBack = { onPane(ThreadPane.Profile) })
        ThreadPane.Nicknames -> NicknamesScreen(chat, inbox, copy, onBack = { onPane(ThreadPane.Profile) })
        ThreadPane.Disappear -> DisappearScreen(inbox, copy, onBack = { onPane(ThreadPane.Profile) })
        ThreadPane.Media -> ChatMediaScreen(chat, inbox, copy, onBack = { onPane(ThreadPane.Profile) })
        else -> ProfileMain(chat, inbox, copy, onPane, onBack)
    }
}

@Composable
private fun ProfileMain(
    chat: InboxChat,
    inbox: InboxController,
    copy: Copy,
    onPane: (ThreadPane) -> Unit,
    onBack: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }
    var makingGroup by remember { mutableStateOf(false) }
    val muted = chat.id in inbox.muted.value
    if (makingGroup) {
        NewGroupScreen(inbox, copy, onClose = { makingGroup = false }) { makingGroup = false }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron(onBack)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(rdp(36))
                    .pressable { menu = !menu; optionsMenu = false },
                contentAlignment = Alignment.Center,
            ) {
                VectorCanvas(
                    shapes = listOf(
                        VShape.Circle(12f, 6f, 1.4f, app.xani.ui.vector.fill(Colors.Text)),
                        VShape.Circle(12f, 12f, 1.4f, app.xani.ui.vector.fill(Colors.Text)),
                        VShape.Circle(12f, 18f, 1.4f, app.xani.ui.vector.fill(Colors.Text)),
                    ),
                    viewport = 24f,
                    modifier = Modifier.size(rdp(18)),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = rdp(8)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chat.group) {
                Box(
                    Modifier
                        .size(rdp(96))
                        .clip(RoundedCornerShape(rdp(28)))
                        .background(chat.tint),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(chat.initials, Type.H1, color = Color.White, fontSizeOverride = 32f)
                }
            } else {
                // Their real picture, and a tap goes to their profile — the same
                // thing tapping a face does anywhere else in the app.
                ChatAvatar(
                    chat,
                    rdp(96),
                    fontSize = 32f,
                    modifier = Modifier.pressable { PersonRouter.open(chat.peerId) },
                )
            }
            Spacer(Modifier.height(rdp(12)))
            XaniText(inbox.displayName(chat), Type.H2, color = Colors.Text, fontSizeOverride = 22f)
            XaniText(
                if (chat.group) copy.t(Copy.InboxOnlineShort, "n" to chat.members.size.toString()) else chat.handle,
                Type.Caption,
                color = Colors.Sub,
            )
            // "What's on your mind" belongs to the person, so it reads the same
            // here as it does on their profile.
            if (chat.status.isNotBlank()) {
                Spacer(Modifier.height(rdp(8)))
                Box(
                    Modifier
                        .padding(horizontal = rdp(28))
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(Colors.Surface)
                        .border(rdp(1), Colors.Border, RoundedCornerShape(rdp(14)))
                        .padding(horizontal = rdp(14), vertical = rdp(8)),
                ) {
                    XaniText(
                        chat.status,
                        Type.Caption,
                        color = Colors.Text,
                        align = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16), vertical = rdp(20)),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ProfileAction(copy.t(Copy.InboxProfile), false, { PersonRouter.open(chat.peerId) }) { ChatIco.Person(Colors.Text) }
            ProfileAction(copy.t(Copy.InboxSearch).substringBefore(" "), false, { onPane(ThreadPane.Search) }) {
                ChatIco.Search(Colors.Text)
            }
            ProfileAction(
                if (muted) copy.t(Copy.InboxUnmute) else copy.t(Copy.InboxMute),
                muted,
                { inbox.mute(chat.id) },
            ) { ChatIco.Bell(if (muted) Color(0xFFEF4444) else Colors.Text, muted = muted) }
            ProfileAction(copy.t(Copy.InboxOptions), false, { optionsMenu = !optionsMenu; menu = false }) { ChatIco.MoreH(Colors.Text) }
        }
        SettingRow(
            iconBg = Brush.linearGradient(ChatThemes[inbox.themeOf(chat.id)].let { listOf(Color(it.start), Color(it.end)) }),
            icon = { },
            label = copy.t(Copy.InboxTheme),
            sub = ChatThemes[inbox.themeOf(chat.id)].label,
            onClick = { onPane(ThreadPane.Theme) },
        )
        SettingRow(
            iconBg = Brush.linearGradient(listOf(Colors.Surface, Colors.Surface)),
            icon = { ChatIco.Pencil(Color(0xFF8B5CF6)) },
            label = copy.t(Copy.InboxNicknames),
            sub = "Set nicknames for members",
            onClick = { onPane(ThreadPane.Nicknames) },
        )
        SettingRow(
            iconBg = Brush.linearGradient(listOf(Colors.Surface, Colors.Surface)),
            icon = { ChatIco.Clock(Color(0xFFF97316)) },
            label = copy.t(Copy.InboxDisappear),
            sub = when (inbox.vanish.value) {
                VanishTimer.Off -> copy.t(Copy.InboxOff)
                VanishTimer.Seen -> copy.t(Copy.InboxOnceSeen)
                VanishTimer.Hours -> copy.t(Copy.InboxHours)
                VanishTimer.Days -> copy.t(Copy.InboxDays)
            },
            onClick = { onPane(ThreadPane.Disappear) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16), vertical = rdp(13)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(rdp(38)).clip(CircleShape).background(Colors.Surface),
                contentAlignment = Alignment.Center,
            ) { ChatIco.MediaVis(Color(0xFF10B981)) }
            Column(Modifier.weight(1f).padding(horizontal = rdp(14))) {
                XaniText(copy.t(Copy.InboxMediaVis), Type.LabelStrong, color = Colors.Text, fontSizeOverride = 14f)
                XaniText(
                    copy.t(if (inbox.mediaVisibility.value) Copy.InboxSavingMedia else Copy.InboxNotSavingMedia),
                    Type.Caption,
                    color = Colors.Sub,
                    fontSizeOverride = 12f,
                )
            }
            val on = inbox.mediaVisibility.value
            Box(
                Modifier
                    .width(rdp(44))
                    .height(rdp(26))
                    .clip(RoundedCornerShape(rdp(13)))
                    .background(if (on) Color(0xFF10B981) else Colors.Disabled)
                    .pressable { inbox.mediaVisibility.value = !on },
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(rdp(3))
                        .size(rdp(20))
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        if (!chat.group) {
            SettingRow(
                iconBg = Brush.linearGradient(listOf(Color(0xFFF0FFF4), Color(0xFFF0FFF4))),
                icon = { ChatIco.CreateGroup(Color(0xFF10B981)) },
                label = copy.t(Copy.InboxCreateGroup),
                sub = copy.t(Copy.InboxCreateGroupSub, "name" to chat.name),
                onClick = { makingGroup = true },
            )
        }
        SharedMediaTabs(chat, inbox, copy)
        Spacer(Modifier.height(rdp(48)))
        }
    }
        if (menu || optionsMenu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pressable { menu = false; optionsMenu = false },
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = rdp(if (optionsMenu) 300 else 48), end = rdp(12))
                        .width(rdp(210))
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(Colors.Background)
                        .border(rdp(1), Colors.Border, RoundedCornerShape(rdp(16))),
                ) {
                    ProfileMenuRow(copy.t(Copy.InboxRestrict), Colors.Text, { menu = false; optionsMenu = false }) {
                        ChatIco.Restrict(Colors.TextSoft)
                    }
                    ProfileMenuRow(
                        copy.t(if (chat.blocked) Copy.InboxUnblock else Copy.InboxBlock),
                        Colors.Text,
                        { menu = false; optionsMenu = false; inbox.block(chat.id, on = !chat.blocked) },
                    ) {
                        ChatIco.BlockUser(Colors.TextSoft)
                    }
                    ProfileMenuRow(copy.t(Copy.InboxReport), Color(0xFFEF4444), { menu = false; optionsMenu = false; inbox.report(chat.id) }) {
                        ChatIco.Report(Color(0xFFEF4444))
                    }
                    ProfileMenuRow(copy.t(Copy.InboxShareProfile), Colors.Text, { menu = false; optionsMenu = false }) {
                        ChatIco.Share(Colors.TextSoft, rdp(18))
                    }
                    ProfileMenuRow(copy.t(Copy.InboxExportChat), Colors.Text, { menu = false; optionsMenu = false }) {
                        ChatIco.Export(Colors.TextSoft)
                    }
                    ProfileMenuRow(copy.t(Copy.InboxClearChat), Color(0xFFEF4444), { menu = false; optionsMenu = false; inbox.clear(chat.id) }, last = true) {
                        ChatIco.Trash(Color(0xFFEF4444), rdp(18))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAction(
    label: String,
    danger: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(rdp(52))
                .clip(CircleShape)
                .background(if (danger) Color(0xFFFFF0F0) else Colors.Surface),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(rdp(5)))
        XaniText(label, Type.Micro, color = if (danger) Color(0xFFEF4444) else Colors.TextSoft, fontSizeOverride = 11f, maxLines = 1)
    }
}

@Composable
private fun ProfileMenuRow(
    label: String,
    color: Color,
    onClick: () -> Unit,
    last: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .then(if (last) Modifier else Modifier.border(rdp(1), Colors.Border, RoundedCornerShape(0)))
            .padding(horizontal = rdp(16), vertical = rdp(13)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(18)), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(rdp(12)))
        XaniText(label, Type.LabelStrong, color = color, fontSizeOverride = 14f)
    }
}

@Composable
private fun SettingRow(
    iconBg: Brush,
    icon: @Composable () -> Unit,
    label: String,
    sub: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(role = Role.Button, onClick = onClick)
            .padding(horizontal = rdp(16), vertical = rdp(13)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(rdp(38)).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.weight(1f).padding(horizontal = rdp(14))) {
            XaniText(label, Type.LabelStrong, color = Colors.Text, fontSizeOverride = 14f)
            XaniText(sub, Type.Caption, color = Colors.Sub, fontSizeOverride = 12f)
        }
        ChatIco.Chevron(Colors.Muted)
    }
}

@Composable
fun SearchChatScreen(chat: InboxChat, inbox: InboxController, copy: Copy, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val msgs = inbox.thread(chat.id)
    val results = remember(query, msgs.size) {
        if (query.isBlank()) emptyList()
        else msgs.filter { it.text.contains(query, ignoreCase = true) }
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
                .padding(horizontal = rdp(12), vertical = rdp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron(onBack)
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Colors.Input)
                    .padding(horizontal = rdp(12), vertical = rdp(9)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon(rdp(15), Colors.TextSoft)
                Spacer(Modifier.width(rdp(8)))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(120) },
                    textStyle = Type.Label.copy(color = Colors.Text),
                    cursorBrush = SolidColor(Dark.Primary),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) XaniText(copy.t(Copy.InboxSearchChat), Type.Label, color = Colors.Hint)
                            inner()
                        }
                    },
                )
            }
        }
        if (query.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SearchIcon(rdp(48), Colors.Muted)
                    Spacer(Modifier.height(rdp(12)))
                    XaniText(copy.t(Copy.InboxTypeSearch), Type.Caption, color = Colors.Sub)
                }
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                XaniText(copy.t(Copy.InboxNoResults, "q" to query), Type.Caption, color = Colors.Sub)
            }
        } else {
            LazyColumn {
                items(results, key = { it.id }) { msg ->
                    val hi = highlight(msg.text, query)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rdp(16), vertical = rdp(12)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(rdp(42))
                                .clip(CircleShape)
                                .background(if (msg.mine) Dark.Primary else chat.tint),
                            contentAlignment = Alignment.Center,
                        ) {
                            XaniText(if (msg.mine) "ME" else chat.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                        }
                        Spacer(Modifier.width(rdp(12)))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                XaniText(if (msg.mine) "You" else chat.name, Type.LabelBold, color = Colors.Text, fontSizeOverride = 13f)
                                XaniText(msg.time, Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
                            }
                            XaniText(hi, Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun highlight(text: String, query: String) = buildAnnotatedString {
    val idx = text.indexOf(query, ignoreCase = true)
    if (idx < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, idx))
    withStyle(SpanStyle(background = Color(0xFFFDE68A))) {
        append(text.substring(idx, idx + query.length))
    }
    append(text.substring(idx + query.length))
}

@Composable
fun ThemePickerScreen(chat: InboxChat, inbox: InboxController, copy: Copy, onBack: () -> Unit) {
    val sel = inbox.themeOf(chat.id)
    val theme = ChatThemes[sel.coerceIn(ChatThemes.indices)]
    var previewWall by remember { mutableStateOf(inbox.wallpapers[chat.id]) }
    var previewBubble by remember { mutableStateOf(inbox.bubbleColors[chat.id]) }
    var galleryOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    var recents by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(tab) {
        if (tab == 0 && hasMediaRead(ctx)) {
            recents = withContext(Dispatchers.IO) { loadRecentUris(ctx, 8) }
        }
    }
    val wall = previewWall
    val bubble = previewBubble
    if (galleryOpen) {
        Dialog(
            onDismissRequest = { galleryOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            DeviceGallerySheet(
                copy = copy,
                onClose = { galleryOpen = false },
                onPick = { uri ->
                    previewWall = uri.toString()
                    inbox.wallpapers[chat.id] = uri.toString()
                    galleryOpen = false
                },
                confirmPick = true,
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
        HeaderRow(copy.t(Copy.InboxChatTheme), onBack)
        Box(
            Modifier
                .padding(rdp(16))
                .fillMaxWidth()
                .height(rdp(160))
                .clip(RoundedCornerShape(rdp(16)))
                .background(Color(theme.bg)),
        ) {
            if (wall != null) {
                LocalThumb(uri = wall, modifier = Modifier.fillMaxSize(), fallback = Color(theme.bg))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(rdp(12)), verticalArrangement = Arrangement.spacedBy(rdp(8))) {
                Box(
                    Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(rdp(14)))
                        .then(
                            if (bubble != null) Modifier.background(Color(bubble))
                            else Modifier.background(Brush.linearGradient(listOf(Color(theme.start), Color(theme.end)))),
                        )
                        .padding(horizontal = rdp(13), vertical = rdp(8)),
                ) {
                    XaniText("Hey, looking great! 👋", Type.Caption, color = Color.White)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(14)))
                        .background(Color(0xFFF0F0F0))
                        .padding(horizontal = rdp(13), vertical = rdp(8)),
                ) {
                    XaniText("Love the new theme 🎨", Type.Caption, color = Color(0xFF111111))
                }
            }
        }
        XaniText(theme.label, Type.LabelBold, color = Dark.Primary, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)
        Row(
            Modifier
                .padding(horizontal = rdp(16), vertical = rdp(8))
                .clip(RoundedCornerShape(rdp(20)))
                .background(Color(0xFFF3F4F6)),
        ) {
            listOf(copy.t(Copy.InboxWallpaper), copy.t(Copy.InboxBubbleColor), copy.t(Copy.InboxChatTheme)).forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(if (tab == i) Dark.Primary else Color.Transparent)
                        .pressable { tab = i }
                        .padding(vertical = rdp(8)),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(label, Type.Micro, color = if (tab == i) Color.White else Colors.Sub, fontSizeOverride = 11f)
                }
            }
        }
        when (tab) {
            0 -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(8))) {
                    if (recents.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                            items(recents, key = { it.toString() }) { uri ->
                                val on = wall == uri.toString()
                                Box {
                                    LocalThumb(
                                        uri = uri.toString(),
                                        modifier = Modifier
                                            .size(rdp(64))
                                            .clip(RoundedCornerShape(rdp(10)))
                                            .pressable {
                                                previewWall = uri.toString()
                                                inbox.wallpapers[chat.id] = uri.toString()
                                            },
                                        fallback = Color(0xFFE5E7EB),
                                    )
                                    if (on) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(rdp(4))
                                                .size(rdp(18))
                                                .clip(CircleShape)
                                                .background(Dark.Primary),
                                            contentAlignment = Alignment.Center,
                                        ) { ChatIco.Tick(Color.White, rdp(10)) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(rdp(10)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(16)))
                                .background(Color(0xFFF3E8FF))
                                .pressable { galleryOpen = true }
                                .padding(horizontal = rdp(12), vertical = rdp(8)),
                        ) {
                            XaniText(copy.t(Copy.InboxSeeAll), Type.LabelBold, color = Dark.Primary, fontSizeOverride = 12f)
                        }
                        Spacer(Modifier.width(rdp(8)))
                        if (wall != null) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(rdp(16)))
                                    .background(Color(0xFFFEE2E2))
                                    .pressable {
                                        inbox.wallpapers.remove(chat.id)
                                        previewWall = null
                                    }
                                    .padding(horizontal = rdp(12), vertical = rdp(8)),
                            ) {
                                XaniText(copy.t(Copy.InboxRemove), Type.LabelBold, color = Color(0xFFEF4444), fontSizeOverride = 12f)
                            }
                        }
                    }
                }
            }
            1 -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(16), vertical = rdp(8)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                ) {
                    BubbleSolids.forEach { color ->
                        val on = bubble == color
                        Box(
                            Modifier
                                .size(rdp(32))
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(rdp(if (on) 3 else 0), Color(0xFF111111), CircleShape)
                                .pressable { previewBubble = color },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) ChatIco.Tick(Color.White, rdp(12))
                        }
                    }
                }
                Row(Modifier.padding(horizontal = rdp(16), vertical = rdp(6)), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                    if (bubble != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(16)))
                                .background(Dark.Primary)
                                .pressable { inbox.bubbleColors[chat.id] = bubble }
                                .padding(horizontal = rdp(16), vertical = rdp(8)),
                        ) {
                            XaniText(copy.t(Copy.InboxApply), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                        }
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(16)))
                            .background(Color(0xFFF3F4F6))
                            .pressable {
                                inbox.bubbleColors.remove(chat.id)
                                previewBubble = null
                            }
                            .padding(horizontal = rdp(16), vertical = rdp(8)),
                    ) {
                        XaniText(copy.t(Copy.InboxRemove), Type.LabelBold, color = Colors.Sub, fontSizeOverride = 13f)
                    }
                }
            }
            else -> {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(rdp(360)),
            contentPadding = PaddingValues(rdp(16)),
            horizontalArrangement = Arrangement.spacedBy(rdp(14)),
            verticalArrangement = Arrangement.spacedBy(rdp(14)),
        ) {
            itemsIndexed(ChatThemes, key = { _, t -> t.label }) { i, t ->
                Column(
                    modifier = Modifier.pressable {
                        inbox.themes[chat.id] = i
                        if (i == 0) {
                            inbox.bubbleColors.remove(chat.id)
                            previewBubble = null
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(Brush.linearGradient(listOf(Color(t.start), Color(t.end))))
                            .border(rdp(3), if (sel == i) Color(0xFF111111) else Color.Transparent, RoundedCornerShape(rdp(14))),
                    )
                    Spacer(Modifier.height(rdp(6)))
                    XaniText(t.label, Type.Micro, color = if (sel == i) Colors.Text else Colors.Sub, fontSizeOverride = 11f)
                }
            }
        }
            }
        }
    }
}

@Composable
fun NicknamesScreen(chat: InboxChat, inbox: InboxController, copy: Copy, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Member?>(null) }
    var draft by remember { mutableStateOf("") }
    val people = remember(chat) {
        val members = if (chat.group) chat.members else listOf(Member(chat.name, chat.handle, chat.initials, chat.color))
        members + Member("You", "me", "ME", 0xFF8B5CF6)
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
        HeaderRow(copy.t(Copy.InboxNicknames), onBack)
        XaniText(
            "Set a nickname for anyone in this chat. Only you can see it.",
            Type.Caption,
            color = Colors.Sub,
            modifier = Modifier.padding(horizontal = rdp(16), vertical = rdp(8)),
        )
        people.forEach { m ->
            val nick = inbox.nicknames[m.handle]
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable {
                        editing = m
                        draft = nick.orEmpty()
                    }
                    .padding(horizontal = rdp(16), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(rdp(44)).clip(CircleShape).background(Color(m.color)),
                    contentAlignment = Alignment.Center,
                ) { XaniText(m.initials, Type.LabelBold, color = Color.White) }
                Spacer(Modifier.width(rdp(14)))
                Column(Modifier.weight(1f)) {
                    XaniText(m.name, Type.LabelStrong, color = Colors.Text)
                    XaniText(nick ?: copy.t(Copy.InboxAddNick), Type.Caption, color = if (nick != null) Dark.Primary else Colors.Muted)
                }
            }
        }
    }
    val target = editing
    if (target != null) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pressable { editing = null })
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                    .background(Colors.Background)
                    .padding(rdp(20)),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(rdp(36)).height(rdp(4)).clip(RoundedCornerShape(rdp(2))).background(Color(0xFFE0E0E0)))
                Spacer(Modifier.height(rdp(16)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(rdp(48)).clip(CircleShape).background(Color(target.color)), contentAlignment = Alignment.Center) {
                        XaniText(target.initials, Type.LabelBold, color = Color.White)
                    }
                    Spacer(Modifier.width(rdp(12)))
                    Column {
                        XaniText(target.name, Type.LabelBold, color = Colors.Text, fontSizeOverride = 15f)
                        XaniText(copy.t(Copy.InboxAddNick), Type.Caption, color = Colors.Sub)
                    }
                }
                Spacer(Modifier.height(rdp(16)))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(30) },
                    textStyle = Type.Body.copy(color = Colors.Text),
                    cursorBrush = SolidColor(Dark.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color(0xFFF5F5F5))
                        .padding(rdp(14)),
                )
                Spacer(Modifier.height(rdp(14)))
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(46))
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(Color(0xFFF0F0F0))
                            .pressable { editing = null },
                        contentAlignment = Alignment.Center,
                    ) { XaniText(copy.t(Copy.InboxCancel), Type.LabelStrong, color = Color(0xFF555555)) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(46))
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                            .pressable {
                                if (draft.isBlank()) inbox.nicknames.remove(target.handle)
                                else inbox.nicknames[target.handle] = draft.trim()
                                editing = null
                            },
                        contentAlignment = Alignment.Center,
                    ) { XaniText(copy.t(Copy.ProfileSave), Type.LabelBold, color = Color.White) }
                }
            }
        }
    }
    }
}

@Composable
fun DisappearScreen(inbox: InboxController, copy: Copy, onBack: () -> Unit) {
    val opts = listOf(
        Triple(VanishTimer.Off, copy.t(Copy.InboxOff), "Messages will stay in the chat"),
        Triple(VanishTimer.Seen, copy.t(Copy.InboxOnceSeen), "Disappear after being viewed"),
        Triple(VanishTimer.Hours, copy.t(Copy.InboxHours), "Disappear 24 hours after sent"),
        Triple(VanishTimer.Days, copy.t(Copy.InboxDays), "Disappear 7 days after sent"),
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
        HeaderRow(copy.t(Copy.InboxDisappear), onBack)
        Column(Modifier.fillMaxWidth().padding(vertical = rdp(24)), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(rdp(100)).clip(CircleShape).background(Color(0xFFDCFCE7)), contentAlignment = Alignment.Center) {
                ChatIco.Clock(Color(0xFF10B981), rdp(44))
            }
            Spacer(Modifier.height(rdp(12)))
            XaniText(copy.t(Copy.InboxMakeVanish), Type.LabelBold, color = Colors.Text, fontSizeOverride = 17f)
            XaniText(
                "New messages will disappear from this chat after the selected duration.",
                Type.Caption,
                color = Colors.TextSoft,
                align = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = rdp(24), vertical = rdp(8)),
            )
        }
        XaniText(copy.t(Copy.InboxTimer).uppercase(), Type.Micro, color = Colors.Sub, modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(6)), fontSizeOverride = 11f)
        opts.forEach { (value, label, sub) ->
            val on = inbox.vanish.value == value
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable { inbox.vanish.value = value }
                    .padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(rdp(22))
                        .clip(CircleShape)
                        .border(rdp(2), if (on) Dark.Primary else Colors.Muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Box(Modifier.size(rdp(11)).clip(CircleShape).background(Dark.Primary))
                }
                Spacer(Modifier.width(rdp(16)))
                Column {
                    XaniText(label, Type.LabelStrong, color = Colors.Text)
                    XaniText(sub, Type.Caption, color = Colors.Sub)
                }
            }
        }
    }
}

@Composable
fun ChatMediaScreen(chat: InboxChat, inbox: InboxController, copy: Copy, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Background)
            .statusBarsPadding(),
    ) {
        HeaderRow(copy.t(Copy.InboxMedia), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SharedMediaTabs(chat, inbox, copy)
        }
    }
}

@Composable
private fun SharedMediaTabs(chat: InboxChat, inbox: InboxController, copy: Copy) {
    var tab by remember { mutableIntStateOf(0) }
    val thread = inbox.thread(chat.id)
    val images = remember(inbox.seq.longValue) { thread.filter { it.kind == MsgKind.Image } }
    val docs = remember(inbox.seq.longValue) { thread.filter { it.kind == MsgKind.Document } }
    val links = remember(inbox.seq.longValue) {
        thread.filter {
            it.kind == MsgKind.Location || it.text.contains("http://") || it.text.contains("https://")
        }
    }
    val tabs = listOf(
        0 to copy.t(Copy.InboxImages),
        1 to copy.t(Copy.InboxDocs),
        2 to copy.t(Copy.InboxLinks),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { (id, label) ->
                val on = tab == id
                Column(
                    Modifier
                        .weight(1f)
                        .height(rdp(44))
                        .pressable { tab = id },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(6))) {
                        when (id) {
                            0 -> ChatIco.ImagesGrid(if (on) Dark.Primary else Color(0xFF888888), rdp(16))
                            1 -> ChatIco.Doc(if (on) Dark.Primary else Color(0xFF888888), rdp(16))
                            else -> ChatIco.Link(if (on) Dark.Primary else Color(0xFF888888), rdp(16))
                        }
                        XaniText(label, Type.LabelStrong, color = if (on) Dark.Primary else Color(0xFF888888), fontSizeOverride = 13f)
                    }
                    Spacer(Modifier.height(rdp(6)))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rdp(2))
                            .background(if (on) Dark.Primary else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.height(rdp(1)).fillMaxWidth().background(Color(0xFFF0F0F0)))
        when (tab) {
            0 -> {
                val items = images
                if (items.isEmpty()) {
                    repeat(2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rdp(2))) {
                            repeat(3) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(chat.tint.copy(alpha = 0.18f)),
                                )
                            }
                        }
                    }
                } else {
                    items.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rdp(2))) {
                            row.forEach { msg ->
                                Box(Modifier.weight(1f).aspectRatio(1f)) {
                                    LocalThumb(uri = msg.mediaUri, modifier = Modifier.fillMaxSize(), fallback = chat.tint)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                        }
                    }
                }
            }
            1 -> {
                if (docs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(rdp(24)), contentAlignment = Alignment.Center) {
                        XaniText(copy.t(Copy.InboxDocs), Type.Caption, color = Colors.Sub)
                    }
                } else {
                    docs.forEach { msg ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(rdp(40)).clip(RoundedCornerShape(rdp(10))).background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center,
                            ) { ChatIco.Doc(Dark.Primary, rdp(18)) }
                            Spacer(Modifier.width(rdp(12)))
                            Column(Modifier.weight(1f)) {
                                XaniText(msg.text.ifBlank { copy.t(Copy.InboxDocument) }, Type.LabelStrong, color = Colors.Text, maxLines = 1)
                                XaniText(msg.time, Type.Caption, color = Colors.Sub)
                            }
                        }
                    }
                }
            }
            else -> {
                if (links.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(rdp(24)), contentAlignment = Alignment.Center) {
                        XaniText(copy.t(Copy.InboxLinks), Type.Caption, color = Colors.Sub)
                    }
                } else {
                    links.forEach { msg ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = rdp(16), vertical = rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(rdp(40)).clip(RoundedCornerShape(rdp(10))).background(Color(0xFFECFDF5)),
                                contentAlignment = Alignment.Center,
                            ) { ChatIco.Link(Color(0xFF10B981), rdp(18)) }
                            Spacer(Modifier.width(rdp(12)))
                            Column(Modifier.weight(1f)) {
                                XaniText(msg.text.ifBlank { copy.t(Copy.InboxLocation) }, Type.LabelStrong, color = Colors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                XaniText(msg.time, Type.Caption, color = Colors.Sub)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HeaderRow(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(12), vertical = rdp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackChevron(onBack)
        Spacer(Modifier.width(rdp(8)))
        XaniText(title, Type.LabelBold, color = Colors.Text, fontSizeOverride = 16f)
    }
}

@Composable
internal fun BackChevron(onBack: () -> Unit) {
    Box(
        Modifier
            .size(rdp(36))
            .pressable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        VectorCanvas(
            shapes = listOf(VShape.D("M14 4L7 11l7 7", stroke(Colors.Text, 2.6f))),
            viewport = 22f,
            modifier = Modifier.size(rdp(22)),
        )
    }
}
