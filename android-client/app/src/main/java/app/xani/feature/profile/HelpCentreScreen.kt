package app.xani.feature.profile

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.xani.core.isolate.runFeature
import app.xani.core.media.RemoteImage
import app.xani.core.net.HelpArticleData
import app.xani.core.net.MediaConfig
import app.xani.core.net.SupportReplyData
import app.xani.core.net.SupportTicketData
import app.xani.core.net.SupportTicketDetail
import app.xani.core.net.Xani
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HelpPane { Home, Article, Report, Tickets, Ticket }

@Composable
internal fun HelpCentreScreen(
    copy: Copy,
    onBack: () -> Unit,
    startReport: Boolean = false,
    startTicketId: Long = 0L,
) {
    var pane by remember {
        mutableStateOf(
            when {
                startTicketId > 0L -> HelpPane.Ticket
                startReport -> HelpPane.Report
                else -> HelpPane.Home
            },
        )
    }
    var articleId by remember { mutableStateOf(0L) }
    var ticketId by remember { mutableStateOf(startTicketId) }

    BackHandler {
        when (pane) {
            HelpPane.Home -> onBack()
            HelpPane.Report, HelpPane.Tickets, HelpPane.Article -> pane = HelpPane.Home
            HelpPane.Ticket -> if (startTicketId > 0L) onBack() else pane = HelpPane.Tickets
        }
    }

    when (pane) {
        HelpPane.Home -> HelpHome(
            copy = copy,
            onBack = onBack,
            onArticle = { articleId = it; pane = HelpPane.Article },
            onReport = { pane = HelpPane.Report },
            onTickets = { pane = HelpPane.Tickets },
        )
        HelpPane.Article -> HelpArticleDetail(
            copy = copy,
            id = articleId,
            onBack = { pane = HelpPane.Home },
        )
        HelpPane.Report -> ReportProblemForm(
            copy = copy,
            onBack = { pane = HelpPane.Home },
            onSubmitted = { ticketId = it; pane = HelpPane.Ticket },
        )
        HelpPane.Tickets -> MyTicketsList(
            copy = copy,
            onBack = { pane = HelpPane.Home },
            onOpen = { ticketId = it; pane = HelpPane.Ticket },
        )
        HelpPane.Ticket -> TicketDetailPane(
            copy = copy,
            id = ticketId,
            onBack = { if (startTicketId > 0L) onBack() else pane = HelpPane.Tickets },
        )
    }
}

@Composable
private fun HelpChrome(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileTokens.Bg2)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        // The white of the header runs up behind the status bar (no grey strip
        // above it), and the title keeps a clear gap from the back arrow.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = rdp(20), vertical = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(rdp(40)).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                ProfileIco.Back(ProfileTokens.Text)
            }
            Spacer(Modifier.width(rdp(10)))
            XaniText(
                title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 17f,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.Hairline))
        content()
    }
}

@Composable
private fun HelpHome(
    copy: Copy,
    onBack: () -> Unit,
    onArticle: (Long) -> Unit,
    onReport: () -> Unit,
    onTickets: () -> Unit,
) {
    var articles by remember { mutableStateOf<List<HelpArticleData>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val r = runFeature(null) { Xani.support.articles() }
        if (r?.ok == true) articles = r.value ?: emptyList()
        else error = r?.message ?: "Could not load help."
    }
    HelpChrome(copy.t(Copy.ProfileHelpCentre), onBack) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = rdp(40) + LocalFeedBottomReserve.current),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(rdp(16)),
            verticalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
                    HelpChip(copy.t(Copy.ProfileReportProblem), onReport)
                    HelpChip(copy.t(Copy.HelpMyReports), onTickets)
                }
            }
            if (error.isNotEmpty()) {
                item { XaniText(error, Type.Caption, color = Color(0xFFDC2626), fontSizeOverride = 13f) }
            }
            items(articles, key = { it.id }) { a ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color.White)
                        .border(1.dp, ProfileTokens.Hairline, RoundedCornerShape(rdp(12)))
                        .pressable(onClick = { onArticle(a.id) })
                        .padding(rdp(14)),
                ) {
                    XaniText(a.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 15f)
                    if (a.preview.isNotEmpty()) {
                        XaniText(
                            a.preview,
                            Type.Caption,
                            color = ProfileTokens.Sub,
                            fontSizeOverride = 13f,
                            modifier = Modifier.padding(top = rdp(6)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(ProfileTokens.AccentSoft)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(8)),
    ) {
        XaniText(label, Type.LabelBold, color = ProfileTokens.Accent, fontSizeOverride = 12f)
    }
}

@Composable
private fun HelpArticleDetail(copy: Copy, id: Long, onBack: () -> Unit) {
    var article by remember { mutableStateOf<HelpArticleData?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        val r = runFeature(null) { Xani.support.article(id) }
        if (r?.ok == true) article = r.value
        else error = r?.message ?: "Not found."
    }
    HelpChrome(article?.title ?: copy.t(Copy.ProfileHelpCentre), onBack) {
        val a = article
        if (a == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                XaniText(error.ifEmpty { "…" }, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f)
            }
            return@HelpChrome
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(rdp(16))
                .padding(bottom = rdp(40) + LocalFeedBottomReserve.current),
        ) {
            XaniText(a.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 20f)
            XaniText(
                a.body,
                Type.Body,
                color = ProfileTokens.Text,
                fontSizeOverride = 15f,
                modifier = Modifier.padding(top = rdp(12)),
            )
            Row(
                Modifier.padding(top = rdp(20)),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                VoteChip("Like ${a.likeCount}", a.myVote == 1) {
                    scope.launch {
                        val next = if (a.myVote == 1) 0 else 1
                        val r = runFeature(null) { Xani.support.vote(id, next) }
                        if (r?.ok == true) article = r.value
                    }
                }
                VoteChip("Dislike ${a.dislikeCount}", a.myVote == -1) {
                    scope.launch {
                        val next = if (a.myVote == -1) 0 else -1
                        val r = runFeature(null) { Xani.support.vote(id, next) }
                        if (r?.ok == true) article = r.value
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(if (on) ProfileTokens.Accent else Color.White)
            .border(1.dp, if (on) ProfileTokens.Accent else ProfileTokens.Hairline, RoundedCornerShape(rdp(20)))
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(8)),
    ) {
        XaniText(label, Type.LabelBold, color = if (on) Color.White else ProfileTokens.Text, fontSizeOverride = 12f)
    }
}

@Composable
private fun ReportProblemForm(copy: Copy, onBack: () -> Unit, onSubmitted: (Long) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        attachments.clear()
        attachments.addAll(uris.take(3))
    }

    HelpChrome(copy.t(Copy.ProfileReportProblem), onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(rdp(16))
                .padding(bottom = rdp(40) + LocalFeedBottomReserve.current),
            verticalArrangement = Arrangement.spacedBy(rdp(12)),
        ) {
            HelpField(copy.t(Copy.HelpSubject), subject) { subject = it }
            HelpField(copy.t(Copy.HelpMessage), message, multiline = true) { message = it }
            Row(horizontalArrangement = Arrangement.spacedBy(rdp(8)), verticalAlignment = Alignment.CenterVertically) {
                HelpChip("Add images (${attachments.size}/3)") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            if (error.isNotEmpty()) {
                XaniText(error, Type.Caption, color = Color(0xFFDC2626), fontSizeOverride = 13f)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rdp(12)))
                    .background(if (busy) ProfileTokens.Sub else ProfileTokens.Accent)
                    .pressable(enabled = !busy, onClick = {
                        if (subject.isBlank() || message.isBlank()) {
                            error = "Subject and message are required."
                            return@pressable
                        }
                        busy = true
                        error = ""
                        scope.launch {
                            val keys = mutableListOf<String>()
                            for (uri in attachments) {
                                val bytes = withContext(Dispatchers.IO) { readBytes(context.contentResolver, uri) }
                                    ?: continue
                                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
                                val up = runFeature(null) { Xani.support.uploadTicketMedia(bytes, mime, ext) }
                                if (up?.ok != true || up.value.isNullOrEmpty()) {
                                    error = up?.message ?: "Upload failed."
                                    busy = false
                                    return@launch
                                }
                                keys += up.value!!
                            }
                            val r = runFeature(null) {
                                Xani.support.createTicket(subject.trim(), message.trim(), keys)
                            }
                            busy = false
                            if (r?.ok == true && r.value != null) onSubmitted(r.value!!.id)
                            else error = r?.message ?: "Could not submit."
                        }
                    })
                    .padding(vertical = rdp(14)),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(
                    if (busy) "…" else copy.t(Copy.HelpSubmit),
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = 15f,
                )
            }
        }
    }
}

private fun readBytes(cr: ContentResolver, uri: Uri): ByteArray? =
    try {
        cr.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

@Composable
private fun HelpField(label: String, value: String, multiline: Boolean = false, onChange: (String) -> Unit) {
    Column {
        XaniText(label, Type.LabelBold, color = ProfileTokens.Sub, fontSizeOverride = 12f)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = androidx.compose.ui.text.TextStyle(color = ProfileTokens.Text, fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
            cursorBrush = SolidColor(ProfileTokens.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = rdp(6))
                .heightIn(min = if (multiline) rdp(120) else rdp(44))
                .clip(RoundedCornerShape(rdp(10)))
                .background(Color.White)
                .border(1.dp, ProfileTokens.Hairline, RoundedCornerShape(rdp(10)))
                .padding(horizontal = rdp(12), vertical = rdp(10)),
        )
    }
}

@Composable
private fun MyTicketsList(copy: Copy, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    var items by remember { mutableStateOf<List<SupportTicketData>>(emptyList()) }
    LaunchedEffect(Unit) {
        val r = runFeature(null) { Xani.support.myTickets() }
        if (r?.ok == true) items = r.value ?: emptyList()
    }
    HelpChrome(copy.t(Copy.HelpMyReports), onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(rdp(16)),
            verticalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            if (items.isEmpty()) {
                item {
                    XaniText("No reports yet.", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f)
                }
            }
            items(items, key = { it.id }) { t ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Color.White)
                        .pressable(onClick = { onOpen(t.id) })
                        .padding(rdp(14)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XaniText(t.subject, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, modifier = Modifier.weight(1f))
                        XaniText(t.status, Type.Caption, color = ProfileTokens.Accent, fontSizeOverride = 11f)
                    }
                    XaniText(
                        t.body.take(100),
                        Type.Caption,
                        color = ProfileTokens.Sub,
                        fontSizeOverride = 12f,
                        modifier = Modifier.padding(top = rdp(4)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketDetailPane(copy: Copy, id: Long, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<SupportTicketDetail?>(null) }
    var reply by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            val r = runFeature(null) { Xani.support.ticket(id) }
            if (r?.ok == true) detail = r.value
        }
    }
    LaunchedEffect(id) { reload() }
    HelpChrome(detail?.ticket?.subject ?: copy.t(Copy.ProfileReportProblem), onBack) {
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                XaniText("…", Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 13f)
            }
            return@HelpChrome
        }
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(rdp(16)),
                verticalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                XaniText("Status: ${d.ticket.status}", Type.LabelBold, color = ProfileTokens.Accent, fontSizeOverride = 12f)
                XaniText(d.ticket.body, Type.Body, color = ProfileTokens.Text, fontSizeOverride = 14f)
                d.ticket.attachmentKeys.forEach { key ->
                    val url = MediaConfig.url(key)
                    if (url.isNotEmpty()) {
                        RemoteImage(
                            url = url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rdp(160))
                                .clip(RoundedCornerShape(rdp(10))),
                            maxEdge = 960,
                            fallback = ProfileTokens.Bg3,
                        )
                    }
                }
                d.replies.forEach { r: SupportReplyData ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rdp(10)))
                            .background(if (r.isStaff) ProfileTokens.AccentSoft else Color.White)
                            .padding(rdp(12)),
                    ) {
                        XaniText(
                            if (r.isStaff) "Support" else "@${r.username.ifEmpty { "you" }}",
                            Type.Caption,
                            color = ProfileTokens.Sub,
                            fontSizeOverride = 11f,
                        )
                        XaniText(r.body, Type.Body, color = ProfileTokens.Text, fontSizeOverride = 13f, modifier = Modifier.padding(top = rdp(4)))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = rdp(12), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = rdp(40))
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(ProfileTokens.Bg2)
                        .padding(rdp(10)),
                    cursorBrush = SolidColor(ProfileTokens.Accent),
                )
                Spacer(Modifier.width(rdp(8)))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(ProfileTokens.Accent)
                        .pressable(enabled = !busy && reply.isNotBlank(), onClick = {
                            busy = true
                            scope.launch {
                                val r = runFeature(null) { Xani.support.reply(id, reply.trim()) }
                                busy = false
                                if (r?.ok == true) {
                                    reply = ""
                                    reload()
                                }
                            }
                        })
                        .padding(horizontal = rdp(14), vertical = rdp(10)),
                ) {
                    XaniText("Send", Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
                }
            }
        }
    }
}
