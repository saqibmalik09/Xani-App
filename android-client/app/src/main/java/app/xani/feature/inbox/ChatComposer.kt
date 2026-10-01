package app.xani.feature.inbox

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlin.math.sin
import app.xani.core.device.DeviceClass
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay

@Composable
fun ChatComposer(
    copy: Copy,
    vanish: Boolean,
    reply: ChatMessage?,
    onClearReply: () -> Unit,
    edit: ChatMessage? = null,
    onClearEdit: () -> Unit = {},
    onSend: (String) -> Unit,
    onEditSend: (String) -> Unit = {},
    onMedia: (Uri, MsgKind, String) -> Unit,
    onAttachKind: (MsgKind, String) -> Unit,
    device: DeviceClass,
    modifier: Modifier = Modifier,
    injectPaste: String? = null,
    onInjected: () -> Unit = {},
    /** Fires as the draft grows so the other side can see "typing…". */
    onTyping: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var draft by remember { mutableStateOf(TextFieldValue()) }
    LaunchedEffect(draft.text.isNotEmpty(), draft.text.length) {
        if (draft.text.isNotEmpty()) onTyping()
    }
    var editMenu by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var paused by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<PendingAttach?>(null) }
    var cameraOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    var contactsOpen by remember { mutableStateOf(false) }
    var pollOpen by remember { mutableStateOf(false) }
    var eventOpen by remember { mutableStateOf(false) }
    var peekUri by remember { mutableStateOf<Uri?>(null) }
    val mediaQueue = remember { mutableStateListOf<Uri>() }
    var emojiPad by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    fun hideKb() {
        keyboard?.hide()
        focus.clearFocus()
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            onAttachKind(MsgKind.Location, lastKnownFix(ctx) ?: "📍 Location")
        }
        attach = false
    }
    val voice = remember { VoiceCapture(ctx) }
    val capture = rememberCaptureLaunchers(
        onPhoto = { mediaQueue.add(it) },
        onMedia = { uri ->
            mediaQueue.add(uri)
        },
        onDocument = { pending = PendingAttach(MsgKind.Document, it) },
    )

    fun fireSend() {
        val cap = draft.text.trim()
        val editing = edit
        if (editing != null) {
            if (cap.isNotBlank()) onEditSend(cap)
            draft = TextFieldValue()
            attach = false
            editMenu = false
            return
        }
        if (mediaQueue.isNotEmpty()) {
            mediaQueue.forEachIndexed { i, uri ->
                // The picker hands back pictures and clips through the same
                // callback. Sending a clip as a picture is refused by the
                // server (and rightly so), so the kind comes from the file.
                onMedia(uri, attachmentKind(ctx, uri), if (i == 0) cap else "")
            }
            mediaQueue.clear()
            pending = null
            draft = TextFieldValue()
            attach = false
            editMenu = false
            return
        }
        val p = pending
        when {
            p != null -> {
                val uri = p.uri
                val body = if (p.kind == MsgKind.Contact) {
                    listOf(p.label, cap).filter { it.isNotBlank() }.distinct().joinToString("\n")
                } else cap
                if (uri != null) onMedia(uri, p.kind, body)
                else onAttachKind(p.kind, body.ifBlank { p.label })
                pending = null
                draft = TextFieldValue()
                attach = false
                editMenu = false
            }
            cap.isNotBlank() -> {
                onSend(cap)
                draft = TextFieldValue()
                attach = false
                editMenu = false
            }
            else -> capture.requestMic {
                if (voice.start()) {
                    recording = true
                    paused = false
                    secs = 0
                    attach = false
                    editMenu = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voice.cancel() }
    }

    LaunchedEffect(recording, paused) {
        if (!recording || paused) return@LaunchedEffect
        while (true) {
            delay(1_000)
            secs += 1
        }
    }
    LaunchedEffect(reply?.id) {
        if (reply != null) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(edit?.id) {
        val t = edit?.text ?: return@LaunchedEffect
        draft = TextFieldValue(t, TextRange(t.length))
        fieldFocus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(injectPaste) {
        val t = injectPaste ?: return@LaunchedEffect
        val start = draft.selection.min
        val end = draft.selection.max
        val next = draft.text.replaceRange(start, end, t).take(4000)
        draft = TextFieldValue(next, TextRange((start + t.length).coerceAtMost(next.length)))
        onInjected()
    }

    val bg = if (vanish) Color.Black else Colors.Background
    val field = if (vanish) Color.White.copy(alpha = 0.06f) else Colors.Surface
    val border = if (vanish) Color.White.copy(alpha = 0.1f) else Colors.Border
    val textColor = if (vanish) Color.White else Colors.Text
    val hint = if (vanish) Color.White.copy(alpha = 0.3f) else Colors.Hint
    val icon = if (vanish) Color.White.copy(alpha = 0.5f) else Colors.TextSoft

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .border(rdp(1), border, RoundedCornerShape(0)),
    ) {
        if (edit != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(12), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(rdp(3))
                        .height(rdp(28))
                        .clip(RoundedCornerShape(rdp(2)))
                        .background(Brush.verticalGradient(listOf(Dark.Primary, Dark.Accent))),
                )
                Column(Modifier.weight(1f).padding(horizontal = rdp(10))) {
                    XaniText(copy.t(Copy.InboxEdit), Type.LabelBold, color = Dark.Primary, fontSizeOverride = 11f)
                    XaniText(
                        edit.text,
                        Type.Caption,
                        color = if (vanish) Color.White.copy(alpha = 0.7f) else Colors.TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .size(rdp(28))
                        .pressable {
                            draft = TextFieldValue()
                            onClearEdit()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ChatIco.Close(icon, rdp(14))
                }
            }
        } else if (reply != null) {
            Box(Modifier.padding(horizontal = rdp(12), vertical = rdp(8))) {
                ChatReplyCite(
                    quoted = reply,
                    vanish = vanish,
                    onClick = { },
                    onClear = onClearReply,
                    title = copy.t(Copy.InboxReply),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val hold = pending
        if (mediaQueue.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = rdp(12), vertical = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                items(mediaQueue, key = { it.toString() }) { uri ->
                    Box {
                        LocalThumb(
                            uri = uri.toString(),
                            modifier = Modifier
                                .size(rdp(64))
                                .clip(RoundedCornerShape(rdp(10)))
                                .pressable { peekUri = uri },
                            fallback = Color(0xFFF0F0F0),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(rdp(2))
                                .size(rdp(20))
                                .clip(CircleShape)
                                .background(Color(0xE6111111))
                                .pressable { mediaQueue.remove(uri) },
                            contentAlignment = Alignment.Center,
                        ) { ChatIco.Close(Color.White, rdp(10)) }
                    }
                }
            }
        } else if (hold != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(12), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(if (hold.kind == MsgKind.Contact) rdp(52) else rdp(48))
                        .clip(if (hold.kind == MsgKind.Contact) CircleShape else RoundedCornerShape(rdp(8)))
                        .background(if (hold.kind == MsgKind.Contact) Dark.Primary.copy(alpha = 0.18f) else Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center,
                ) {
                    val uri = hold.uri
                    if (uri != null && (hold.kind == MsgKind.Image || hold.kind == MsgKind.Video || hold.kind == MsgKind.Ai || hold.kind == MsgKind.Contact)) {
                        LocalThumb(
                            uri = uri.toString(),
                            modifier = Modifier.fillMaxSize().then(
                                if (hold.kind == MsgKind.Image || hold.kind == MsgKind.Video || hold.kind == MsgKind.Ai) {
                                    Modifier.pressable { peekUri = uri }
                                } else Modifier
                            ),
                            fallback = Dark.Primary,
                        )
                    } else if (hold.kind == MsgKind.Contact) {
                        XaniText(hold.label.take(1).uppercase(), Type.LabelBold, color = Dark.Primary, fontSizeOverride = 18f)
                    } else {
                        when (hold.kind) {
                            MsgKind.Location -> ChatIco.AttachLocation()
                            else -> ChatIco.Doc(Dark.Primary, rdp(22))
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = rdp(10))) {
                    XaniText(
                        when (hold.kind) {
                            MsgKind.Video -> copy.t(Copy.InboxVideo)
                            MsgKind.Document -> copy.t(Copy.InboxDocument)
                            MsgKind.Contact -> hold.label.ifBlank { copy.t(Copy.InboxContact) }
                            MsgKind.Location -> copy.t(Copy.InboxLocation)
                            else -> copy.t(Copy.InboxPhoto)
                        },
                        Type.LabelBold,
                        color = Dark.Primary,
                        fontSizeOverride = 12f,
                    )
                    XaniText(copy.t(Copy.InboxWriteMsg), Type.Caption, color = if (vanish) Color.White.copy(alpha = 0.7f) else Colors.TextSoft, maxLines = 1)
                }
                Box(
                    Modifier.size(rdp(28)).clip(CircleShape).background(Color(0xFFE5E7EB)).pressable { pending = null },
                    contentAlignment = Alignment.Center,
                ) { ChatIco.Close(Color(0xFF555555), rdp(14)) }
            }
        }

        if (recording) {
            VoiceBar(
                copy = copy,
                secs = secs,
                paused = paused,
                vanish = vanish,
                animate = device != DeviceClass.Low,
                onPause = { paused = !paused },
                onCancel = {
                    voice.cancel()
                    recording = false
                    paused = false
                    secs = 0
                },
                onSend = {
                    val file = voice.stop()
                    recording = false
                    paused = false
                    val label = "%02d:%02d".format(secs / 60, secs % 60)
                    secs = 0
                    if (file != null) onMedia(Uri.fromFile(file), MsgKind.Voice, label)
                    else onAttachKind(MsgKind.Voice, copy.t(Copy.InboxVoiceHint) + " · " + label)
                },
            )
        } else {
            val hasSel = draft.selection.length > 0
            val hasText = draft.text.isNotEmpty()
            if (editMenu && (hasSel || hasText)) {
                Row(
                    Modifier
                        .padding(start = rdp(14), top = rdp(6))
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(Color.White)
                        .border(rdp(1), Color(0xFFE5E7EB), RoundedCornerShape(rdp(10))),
                ) {
                    if (hasSel) {
                        EditChip(copy.t(Copy.InboxCopyAction)) {
                            clipboard.setText(AnnotatedString(draft.text.substring(draft.selection.min, draft.selection.max)))
                            editMenu = false
                        }
                    }
                    if (hasText) {
                        EditChip(copy.t(Copy.InboxSelectAll)) {
                            draft = draft.copy(selection = TextRange(0, draft.text.length))
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = rdp(10), end = rdp(10), top = rdp(8), bottom = rdp(10)),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(rdp(26)))
                        .background(field)
                        .padding(start = rdp(12), end = rdp(10), top = rdp(8), bottom = rdp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                ) {
                    Box(
                        Modifier
                            .size(rdp(28))
                            .pressable {
                                hideKb()
                                emojiPad = !emojiPad
                                attach = false
                            },
                        contentAlignment = Alignment.Center,
                    ) { ChatIco.Emoji(if (emojiPad) Dark.Primary else icon, rdp(22)) }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.text.length <= 4000) { draft = it; editMenu = false } },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(fieldFocus),
                        textStyle = Type.Label.copy(color = textColor),
                        cursorBrush = SolidColor(Dark.Primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { fireSend() }),
                        singleLine = false,
                        maxLines = 5,
                        decorationBox = { inner ->
                            Box {
                                if (draft.text.isEmpty()) {
                                    XaniText(copy.t(Copy.InboxTypeMessage), Type.Label, color = hint)
                                }
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier
                            .size(rdp(28))
                            .pressable {
                                hideKb()
                                attach = !attach
                                emojiPad = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatIco.Paperclip(if (attach) Dark.Primary else icon)
                    }
                    if (draft.text.isBlank() && pending == null && mediaQueue.isEmpty()) {
                        Box(
                            Modifier
                                .size(rdp(28))
                                .pressable {
                                    hideKb()
                                    capture.requestCamera { cameraOpen = true }
                                    attach = false
                                    emojiPad = false
                                },
                            contentAlignment = Alignment.Center,
                        ) { ChatIco.Camera(icon, rdp(22)) }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(rdp(46))
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                        .pressable(pressedScale = 0.9f) { fireSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pending != null || mediaQueue.isNotEmpty() || draft.text.isNotBlank()) ChatIco.Send(Color.White, rdp(18))
                    else ChatIco.Mic(Color.White, rdp(22))
                }
            }
        }

        if (attach && !recording) {
            AttachSheet(
                copy = copy,
                vanish = vanish,
                onGallery = {
                    hideKb()
                    galleryOpen = true
                    attach = false
                },
                onCamera = {
                    hideKb()
                    capture.requestCamera { cameraOpen = true }
                    attach = false
                },
                onContact = {
                    hideKb()
                    contactsOpen = true
                    attach = false
                },
                onLocation = {
                    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) {
                        onAttachKind(MsgKind.Location, lastKnownFix(ctx) ?: "📍 Location")
                        attach = false
                    } else {
                        locPerm.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                },
                onDocument = { capture.pickDocument(); attach = false },
                onPoll = {
                    hideKb()
                    pollOpen = true
                    attach = false
                },
                onEvent = {
                    hideKb()
                    eventOpen = true
                    attach = false
                },
                onAi = { onAttachKind(MsgKind.Ai, "AI image"); attach = false },
            )
        }
        if (emojiPad && !recording) {
            ComposerEmojiPad(
                vanish = vanish,
                onPick = { em ->
                    val start = draft.selection.min
                    val end = draft.selection.max
                    val next = draft.text.replaceRange(start, end, em).take(4000)
                    draft = TextFieldValue(next, TextRange((start + em.length).coerceAtMost(next.length)))
                },
            )
        }
    }
    if (cameraOpen) {
        Dialog(
            onDismissRequest = { cameraOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize()) {
                ChatCameraSheet(
                    copy = copy,
                    onClose = { cameraOpen = false },
                    onShot = { uri ->
                        mediaQueue.add(uri)
                        cameraOpen = false
                        attach = false
                    },
                    onGallery = {
                        cameraOpen = false
                        galleryOpen = true
                    },
                    onPicked = { uris ->
                        mediaQueue.addAll(uris)
                        cameraOpen = false
                        attach = false
                    },
                )
            }
        }
    }
    if (galleryOpen) {
        Dialog(
            onDismissRequest = { galleryOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            DeviceGallerySheet(
                copy = copy,
                onClose = { galleryOpen = false },
                onPick = { uri ->
                    mediaQueue.add(uri)
                    galleryOpen = false
                    attach = false
                },
            )
        }
    }
    if (contactsOpen) {
        Dialog(
            onDismissRequest = { contactsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            ContactPickSheet(
                copy = copy,
                onClose = { contactsOpen = false },
                onPick = { list ->
                    if (list.size == 1) {
                        val c = list.first()
                        pending = PendingAttach(
                            MsgKind.Contact,
                            uri = c.photo?.let { Uri.parse(it) },
                            label = c.name,
                        )
                    } else {
                        list.forEach { onAttachKind(MsgKind.Contact, it.name) }
                    }
                    contactsOpen = false
                    attach = false
                },
            )
        }
    }
    if (pollOpen) {
        Dialog(
            onDismissRequest = { pollOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CreatePollSheet(
                copy = copy,
                onLeave = { pollOpen = false },
                onSend = { body ->
                    onAttachKind(MsgKind.Poll, body)
                    pollOpen = false
                },
            )
        }
    }
    if (eventOpen) {
        Dialog(
            onDismissRequest = { eventOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CreateEventSheet(
                copy = copy,
                onLeave = { eventOpen = false },
                onSend = { body ->
                    onAttachKind(MsgKind.Event, body)
                    eventOpen = false
                },
            )
        }
    }
    peekUri?.let { uri ->
        Dialog(
            onDismissRequest = { peekUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                LocalThumb(uri = uri.toString(), modifier = Modifier.fillMaxSize(), fallback = Color.Black)
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(rdp(12))
                        .size(rdp(40))
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pressable { peekUri = null },
                    contentAlignment = Alignment.Center,
                ) { ChatIco.Close(Color.White, rdp(16)) }
            }
        }
    }
}

private val WaveHeights = intArrayOf(14, 22, 10, 28, 18, 32, 12, 26, 20, 16, 30, 8, 24, 34, 14, 20, 28, 10, 18, 24)

@Composable
private fun EditChip(label: String, onClick: () -> Unit) {
    XaniText(
        label,
        Type.LabelStrong,
        color = Color(0xFF111111),
        fontSizeOverride = 13f,
        modifier = Modifier
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(12), vertical = rdp(8)),
    )
}

@Composable
private fun VoiceBar(
    copy: Copy,
    secs: Int,
    paused: Boolean,
    vanish: Boolean,
    animate: Boolean,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val ink = if (vanish) Color.White else Color(0xFF111111)
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val stamp = "%02d:%02d".format(secs / 60, secs % 60)
    Column(Modifier.fillMaxWidth().padding(top = rdp(6), bottom = rdp(10))) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = rdp(16), end = rdp(16), top = rdp(8), bottom = rdp(6)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            if (paused) {
                Box(Modifier.size(rdp(22)).pressable { onPause() }, contentAlignment = Alignment.Center) {
                    ChatIco.Play(ink, rdp(22))
                }
                Box(Modifier.size(rdp(8)).clip(CircleShape).background(Dark.Primary))
            } else {
                XaniText(stamp, Type.LabelBold, color = ink, fontSizeOverride = 14f)
            }
            Row(
                Modifier.weight(1f).height(rdp(36)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaveHeights.forEachIndexed { i, h ->
                    val live = when {
                        animate && !paused -> {
                            val wobble = 0.42f + 0.58f * abs(sin((phase + i * 0.13f).toDouble())).toFloat()
                            (h * wobble).toInt().coerceIn(6, 36)
                        }
                        paused -> (h * 0.7f).toInt().coerceAtLeast(6)
                        else -> h
                    }
                    Box(
                        Modifier
                            .width(rdp(3))
                            .height(rdp(live))
                            .clip(RoundedCornerShape(rdp(2)))
                            .background(if (paused && i >= 10) Color(0xFFD1D5DB) else Dark.Primary),
                    )
                }
            }
            if (paused) {
                XaniText(stamp, Type.LabelBold, color = ink, fontSizeOverride = 14f)
            } else {
                Box(
                    Modifier
                        .size(rdp(26))
                        .clip(CircleShape)
                        .border(rdp(1.5f), Color(0xFF888888), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText("1×", Type.LabelBold, color = Color(0xFF888888), fontSizeOverride = 10f)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(4)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(rdp(52))
                    .clip(CircleShape)
                    .background(Color(0xFFFEE2E2))
                    .pressable { onCancel() },
                contentAlignment = Alignment.Center,
            ) { ChatIco.Trash(Color(0xFFEF4444), rdp(22)) }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = rdp(14))
                    .height(rdp(46))
                    .clip(RoundedCornerShape(rdp(23)))
                    .background(if (vanish) Color.White.copy(alpha = 0.08f) else Color(0xFFF5F5F5))
                    .pressable { onPause() },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                    if (paused) ChatIco.Mic(ink, rdp(18)) else ChatIco.Pause(ink, rdp(16))
                    XaniText(if (paused) copy.t(Copy.InboxResume) else copy.t(Copy.InboxPause), Type.LabelBold, fontSizeOverride = 15f, color = ink)
                }
            }
            Box(
                Modifier
                    .size(rdp(46))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                    .pressable { onSend() },
                contentAlignment = Alignment.Center,
            ) { ChatIco.Send(Color.White, rdp(18)) }
        }
    }
}

@Composable
private fun AttachSheet(
    copy: Copy,
    vanish: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onContact: () -> Unit,
    onLocation: () -> Unit,
    onDocument: () -> Unit,
    onPoll: () -> Unit,
    onEvent: () -> Unit,
    onAi: () -> Unit,
) {
    val items = listOf(
        Triple(copy.t(Copy.InboxGallery), 0, onGallery),
        Triple(copy.t(Copy.InboxCamera), 1, onCamera),
        Triple(copy.t(Copy.InboxContact), 2, onContact),
        Triple(copy.t(Copy.InboxLocation), 3, onLocation),
        Triple(copy.t(Copy.InboxDocument), 4, onDocument),
        Triple(copy.t(Copy.InboxPoll), 5, onPoll),
        Triple(copy.t(Copy.InboxEvent), 6, onEvent),
        Triple(copy.t(Copy.InboxAi), 7, onAi),
    )
    val bg = if (vanish) Color.Black else Colors.Background
    val tile = if (vanish) Color.White.copy(alpha = 0.08f) else Colors.Surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(bottom = rdp(8)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = rdp(8), bottom = rdp(4)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(rdp(36))
                    .height(rdp(4))
                    .clip(RoundedCornerShape(rdp(2)))
                    .background(Color(0xFFE0E0E0)),
            )
        }
        val rows = items.chunked(4)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = rdp(12), vertical = rdp(8)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                row.forEach { (label, kind, action) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pressable(role = Role.Button, onClick = action)
                            .padding(vertical = rdp(4)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(rdp(52))
                                .clip(CircleShape)
                                .background(tile),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (kind) {
                                0 -> ChatIco.AttachGallery()
                                1 -> ChatIco.AttachCamera()
                                2 -> ChatIco.AttachContact()
                                3 -> ChatIco.AttachLocation()
                                4 -> ChatIco.AttachDocument()
                                5 -> ChatIco.AttachPoll()
                                6 -> ChatIco.AttachEvent()
                                else -> ChatIco.AttachAi()
                            }
                        }
                        Spacer(Modifier.height(rdp(6)))
                        XaniText(label, Type.Micro, color = if (vanish) Color.White.copy(alpha = 0.75f) else Colors.TextSoft, fontSizeOverride = 11f)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(16), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText(copy.t(Copy.InboxRecents), Type.LabelBold, color = if (vanish) Color.White.copy(alpha = 0.7f) else Color(0xFF555555), fontSizeOverride = 12f, modifier = Modifier.weight(1f))
            XaniText(
                copy.t(Copy.InboxSeeAll),
                Type.LabelBold,
                color = Dark.Primary,
                fontSizeOverride = 12f,
                modifier = Modifier.pressable(onClick = onGallery),
            )
        }
    }
}

private val ComposerEmojis = listOf(
    "😀", "😂", "🥰", "😍", "🤩", "😘", "😊", "😉",
    "😭", "😡", "🤯", "🤔", "🙌", "👏", "🙏", "👍",
    "👎", "🔥", "❤️", "💯", "✨", "🎉", "😎", "💀",
)

@Composable
private fun ComposerEmojiPad(
    vanish: Boolean,
    onPick: (String) -> Unit,
) {
    val bg = if (vanish) Color.Black else Colors.Background
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(bottom = rdp(8)),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = rdp(8), bottom = rdp(4)), contentAlignment = Alignment.Center) {
            Box(Modifier.width(rdp(36)).height(rdp(4)).clip(RoundedCornerShape(rdp(2))).background(Color(0xFFE0E0E0)))
        }
        ComposerEmojis.chunked(8).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(8), vertical = rdp(4)),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { em ->
                    Box(
                        Modifier
                            .size(rdp(40))
                            .pressable { onPick(em) },
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(em, Type.Label, fontSizeOverride = 26f)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePollSheet(
    copy: Copy,
    onLeave: () -> Unit,
    onSend: (String) -> Unit,
) {
    val question = remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multi by remember { mutableStateOf(false) }
    val ready = question.value.isNotBlank() && options.count { it.isNotBlank() } >= 2
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(36)).pressable(onClick = onLeave), contentAlignment = Alignment.Center) {
                ChatIco.Close(Color(0xFF111111), rdp(16))
            }
            XaniText(copy.t(Copy.InboxLeavePoll), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(16)))
                    .background(if (ready) Dark.Primary else Color(0xFFE5E7EB))
                    .pressable(enabled = ready) {
                        val opts = options.map { it.trim() }.filter { it.isNotBlank() }
                        val body = buildString {
                            append(question.value.trim())
                            opts.forEach { append('\n').append(it) }
                            if (multi) append("\n#multi")
                        }
                        onSend(body)
                    }
                    .padding(horizontal = rdp(14), vertical = rdp(8)),
            ) {
                XaniText(copy.t(Copy.InboxSendBtn), Type.LabelBold, color = if (ready) Color.White else Color(0xFF9CA3AF), fontSizeOverride = 13f)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = rdp(16)),
        ) {
            XaniText(copy.t(Copy.InboxCreatePoll), Type.LabelBold, color = Color(0xFF111111), fontSizeOverride = 22f)
            Spacer(Modifier.height(rdp(16)))
            XaniText(copy.t(Copy.InboxAskQuestion), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.height(rdp(8)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = rdp(12), vertical = rdp(12)),
            ) {
                BasicTextField(
                    value = question.value,
                    onValueChange = { question.value = it.take(120) },
                    textStyle = Type.Label.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Dark.Primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (question.value.isEmpty()) {
                                XaniText(copy.t(Copy.InboxAskQuestion), Type.Label, color = Color(0xFF9CA3AF))
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(rdp(20)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                XaniText(copy.t(Copy.InboxOptions), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f, modifier = Modifier.weight(1f))
                Box(Modifier.size(rdp(32)).pressable { if (options.size < 12) options.add("") }, contentAlignment = Alignment.Center) {
                    ChatIco.Plus(Dark.Primary, rdp(18))
                }
            }
            Spacer(Modifier.height(rdp(8)))
            options.forEachIndexed { i, value ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = rdp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = rdp(12), vertical = rdp(12)),
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = { options[i] = it.take(80) },
                            textStyle = Type.Label.copy(color = Color(0xFF111111)),
                            cursorBrush = SolidColor(Dark.Primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (value.isEmpty()) {
                                        XaniText("${copy.t(Copy.InboxOptions)} ${i + 1}", Type.Label, color = Color(0xFF9CA3AF))
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    if (i >= 2) {
                        Box(Modifier.size(rdp(36)).pressable { options.removeAt(i) }, contentAlignment = Alignment.Center) {
                            ChatIco.Minus(Color(0xFFEF4444), rdp(16))
                        }
                    }
                }
            }
            Spacer(Modifier.height(rdp(12)))
            Row(
                Modifier.fillMaxWidth().pressable { multi = !multi }.padding(vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.InboxMultiple), Type.LabelStrong, color = Color(0xFF111111), fontSizeOverride = 14f, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .width(rdp(44))
                        .height(rdp(26))
                        .clip(CircleShape)
                        .background(if (multi) Dark.Primary else Color(0xFFE5E7EB)),
                ) {
                    Box(
                        Modifier
                            .align(if (multi) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(rdp(3))
                            .size(rdp(20))
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
            Spacer(Modifier.height(rdp(24)))
        }
    }
}

@Composable
internal fun CreateEventSheet(
    copy: Copy,
    seed: EventPayload? = null,
    canEdit: Boolean = true,
    onLeave: () -> Unit,
    onSend: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(seed?.name.orEmpty()) }
    var desc by remember { mutableStateOf(seed?.desc.orEmpty()) }
    var loc by remember { mutableStateOf(seed?.loc.orEmpty()) }
    var at by remember { mutableStateOf(seed?.at?.takeIf { it > 0 } ?: (System.currentTimeMillis() + 60L * 60_000L)) }
    var reminder by remember { mutableIntStateOf(seed?.reminder ?: 60) }
    var remindOpen by remember { mutableStateOf(false) }
    val minAt = System.currentTimeMillis() + 15L * 60_000L
    val tooSoon = at < minAt
    val ready = name.isNotBlank() && !tooSoon
    val whenLabel = remember(at) {
        java.text.SimpleDateFormat("EEE, d MMM yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))
    }
    fun pickDate() {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
        android.app.DatePickerDialog(ctx, { _, y, m, d ->
            c.set(java.util.Calendar.YEAR, y)
            c.set(java.util.Calendar.MONTH, m)
            c.set(java.util.Calendar.DAY_OF_MONTH, d)
            at = c.timeInMillis
        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }
    fun pickTime() {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
        android.app.TimePickerDialog(ctx, { _, h, min ->
            c.set(java.util.Calendar.HOUR_OF_DAY, h)
            c.set(java.util.Calendar.MINUTE, min)
            at = c.timeInMillis
        }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true).show()
    }
    fun words(s: String) = s.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = rdp(8), vertical = rdp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(36)).pressable(onClick = onLeave), contentAlignment = Alignment.Center) {
                ChatIco.Close(Color(0xFF111111), rdp(16))
            }
            XaniText(copy.t(Copy.InboxLeaveEvent), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.weight(1f))
            if (canEdit) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(if (ready) Dark.Primary else Color(0xFFE5E7EB))
                        .pressable(enabled = ready) { onSend(encodeEvent(name, desc, at, loc, reminder)) }
                        .padding(horizontal = rdp(14), vertical = rdp(8)),
                ) {
                    XaniText(copy.t(Copy.InboxSendBtn), Type.LabelBold, color = if (ready) Color.White else Color(0xFF9CA3AF), fontSizeOverride = 13f)
                }
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(Dark.Primary)
                        .pressable { openCalendar(ctx, parseEvent(encodeEvent(name, desc, at, loc, reminder))) }
                        .padding(horizontal = rdp(14), vertical = rdp(8)),
                ) {
                    XaniText(copy.t(Copy.InboxAddCalendar), Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = rdp(16)),
        ) {
            XaniText(
                copy.t(if (canEdit && seed != null) Copy.InboxEditEvent else Copy.InboxCreateEvent),
                Type.LabelBold,
                color = Color(0xFF111111),
                fontSizeOverride = 22f,
            )
            Spacer(Modifier.height(rdp(20)))
            BasicTextField(
                value = name,
                onValueChange = { if (canEdit) name = it.take(80) },
                enabled = canEdit,
                textStyle = Type.H2.copy(color = Color(0xFF111111)),
                cursorBrush = SolidColor(Dark.Primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) {
                            XaniText(copy.t(Copy.InboxEventName), Type.H2, color = Color(0xFFD1D5DB), fontSizeOverride = 28f)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(rdp(20)))
            XaniText(copy.t(Copy.InboxEventDesc), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.height(rdp(8)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = rdp(12), vertical = rdp(12)),
            ) {
                BasicTextField(
                    value = desc,
                    onValueChange = { if (canEdit && words(it) <= 100) desc = it },
                    enabled = canEdit,
                    textStyle = Type.Label.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Dark.Primary),
                    modifier = Modifier.fillMaxWidth().height(rdp(88)),
                    decorationBox = { inner ->
                        Box {
                            if (desc.isEmpty()) XaniText(copy.t(Copy.InboxEventDesc), Type.Label, color = Color(0xFF9CA3AF))
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(rdp(14)))
            XaniText(copy.t(Copy.InboxEventWhen), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.height(rdp(8)))
            Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color(0xFFF5F5F5))
                        .pressable(enabled = canEdit) { pickDate() }
                        .padding(horizontal = rdp(12), vertical = rdp(12)),
                ) { XaniText(whenLabel.substringBefore("·").trim(), Type.Label, color = Color(0xFF111111)) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color(0xFFF5F5F5))
                        .pressable(enabled = canEdit) { pickTime() }
                        .padding(horizontal = rdp(12), vertical = rdp(12)),
                ) { XaniText(whenLabel.substringAfter("·").trim(), Type.Label, color = Color(0xFF111111)) }
            }
            if (tooSoon) {
                Spacer(Modifier.height(rdp(6)))
                XaniText(copy.t(Copy.InboxEventSoon), Type.Caption, color = Color(0xFFEF4444), fontSizeOverride = 12f)
            }
            Spacer(Modifier.height(rdp(14)))
            XaniText(copy.t(Copy.InboxLocation), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.height(rdp(8)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = rdp(12), vertical = rdp(12)),
            ) {
                BasicTextField(
                    value = loc,
                    onValueChange = { if (canEdit) loc = it.take(80) },
                    enabled = canEdit,
                    textStyle = Type.Label.copy(color = Color(0xFF111111)),
                    cursorBrush = SolidColor(Dark.Primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (loc.isEmpty()) XaniText(copy.t(Copy.InboxLocation), Type.Label, color = Color(0xFF9CA3AF))
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(rdp(16)))
            XaniText(copy.t(Copy.InboxReminder), Type.LabelStrong, color = Color(0xFF555555), fontSizeOverride = 13f)
            Spacer(Modifier.height(rdp(8)))
            val reminders = listOf(60 to Copy.InboxHourBefore, 0 to Copy.InboxNever, 15 to Copy.InboxMinBefore, 1440 to Copy.InboxDayBefore)
            val remindLabel = reminders.firstOrNull { it.first == reminder }?.second ?: Copy.InboxHourBefore
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(Color(0xFFF5F5F5))
                    .pressable(enabled = canEdit) { remindOpen = !remindOpen }
                    .padding(horizontal = rdp(12), vertical = rdp(12)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XaniText(copy.t(remindLabel), Type.Label, color = Color(0xFF111111), modifier = Modifier.weight(1f))
                    ChatIco.Chevron(Color(0xFF9CA3AF), rdp(14))
                }
            }
            if (remindOpen && canEdit) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = rdp(6))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color(0xFFF5F5F5)),
                ) {
                    reminders.forEach { (mins, key) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable {
                                    reminder = mins
                                    remindOpen = false
                                }
                                .padding(horizontal = rdp(12), vertical = rdp(12)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            XaniText(copy.t(key), Type.Label, color = Color(0xFF111111), modifier = Modifier.weight(1f))
                            if (reminder == mins) ChatIco.Tick(Dark.Primary, rdp(14))
                        }
                    }
                }
            }
            Spacer(Modifier.height(rdp(24)))
        }
    }
}


