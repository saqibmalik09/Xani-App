package app.xani.feature.inbox

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.isolate.runFeature
import app.xani.core.media.PickedImage
import app.xani.core.media.RemoteImage
import app.xani.core.net.LiveFeed
import app.xani.core.net.MediaConfig
import app.xani.core.net.ActivityData
import app.xani.core.net.StoryData
import app.xani.core.net.StoryGroupData
import app.xani.core.net.StoryViewerData
import app.xani.core.net.Xani
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.PersonAvatar
import app.xani.feature.social.PersonRouter
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Stories (24-hour status): the rail on the inbox, the composer, the
 * full-screen player with a WhatsApp-style "seen by" list, and the activity
 * lane that says who looked. One read fills the rail; a view is one POST;
 * everything else is the server's frame arriving on the profile stream.
 */

/** The rail and the activity lane, cached for the session. */
object StoryStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _rail = MutableStateFlow<List<StoryGroupData>>(emptyList())
    val rail: StateFlow<List<StoryGroupData>> = _rail

    private val _activity = MutableStateFlow<List<ActivityData>>(emptyList())
    val activity: StateFlow<List<ActivityData>> = _activity.asStateFlow()

    private var railAt = 0L
    private var activityLoaded = false
    private var listening = false

    /** The rail, at most once a minute unless [force]. */
    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - railAt < 60_000L) return
        railAt = now
        scope.launch {
            val r = runFeature(null) { Xani.stories.rail() } ?: return@launch
            r.value?.let { _rail.value = it }
        }
    }

    /** The activity lane; live rows are prepended as they arrive. */
    fun loadActivity() {
        if (!listening) {
            listening = true
            scope.launch {
                LiveFeed.activity.collect { a ->
                    _activity.update { cur -> if (cur.any { it.id == a.id }) cur else (listOf(a) + cur).take(200) }
                }
            }
        }
        if (activityLoaded) return
        activityLoaded = true
        scope.launch {
            val r = runFeature(null) { Xani.stories.activity() } ?: run { activityLoaded = false; return@launch }
            r.value?.let { page -> _activity.update { cur -> (cur + page.items).distinctBy { it.id }.sortedByDescending { it.createdMs } } }
        }
    }

    /** A story of mine went away: drop it from the rail at once. */
    fun removed(storyId: Long) {
        _rail.update { groups ->
            groups.mapNotNull { g ->
                val rest = g.stories.filterNot { it.id == storyId }
                if (rest.isEmpty()) null else g.copy(stories = rest)
            }
        }
    }

    /** I watched [storyId]: the ring dims without a second read. */
    fun seen(storyId: Long) {
        _rail.update { groups ->
            groups.map { g ->
                if (g.stories.none { it.id == storyId && !it.seen }) g
                else g.copy(stories = g.stories.map { if (it.id == storyId) it.copy(seen = true) else it }, unseen = (g.unseen - 1).coerceAtLeast(0))
            }
        }
    }
}

private fun storyMediaUrl(s: StoryData): String =
    if (s.mediaUrl.startsWith("http://") || s.mediaUrl.startsWith("https://")) s.mediaUrl else MediaConfig.url(s.mediaUrl)

private fun storyTint(bg: String): Color = runFeature(Color(0xFF7C3AED)) {
    if (bg.length == 7 && bg[0] == '#') Color(0xFF000000 or bg.substring(1).toLong(16)) else Color(0xFF7C3AED)
}

private fun ago(ms: Long): String {
    val d = ((System.currentTimeMillis() - ms) / 60_000L).coerceAtLeast(0L)
    return when {
        d < 1 -> "now"
        d < 60 -> "${d}m"
        d < 24 * 60 -> "${d / 60}h"
        else -> "${d / (24 * 60)}d"
    }
}

// ─── rail ────────────────────────────────────────────────────────────────────

/**
 * The story rail: my tile first (tap opens mine when I have one, the "+"
 * badge always composes), then everyone with something unseen, then the rest.
 */
@Composable
internal fun StoriesRail(copy: Copy, onCreate: () -> Unit, onOpen: (StoryGroupData) -> Unit) {
    val groups by StoryStore.rail.collectAsState()
    LaunchedEffect(Unit) { StoryStore.refresh() }
    val me by ProfileStore.state.collectAsState()
    val mine = groups.firstOrNull { it.mine }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = rdp(20), vertical = rdp(10)),
        horizontalArrangement = Arrangement.spacedBy(rdp(12)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(rdp(56))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .then(
                            if (mine != null) Modifier.background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))).padding(rdp(2.5f))
                            else Modifier.border(rdp(2), Dark.Primary.copy(alpha = 0.35f), CircleShape).padding(rdp(2.5f)),
                        )
                        .pressable(onClick = { if (mine != null) onOpen(mine) else onCreate() }),
                    contentAlignment = Alignment.Center,
                ) {
                    PersonAvatar(ProfileStore.currentUserId, me.displayName.ifBlank { me.username }, me.avatarUrl, rdp(48))
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(rdp(20))
                        .clip(CircleShape)
                        .background(Dark.Primary)
                        .border(rdp(2), Colors.Background, CircleShape)
                        .pressable(onClick = onCreate),
                    contentAlignment = Alignment.Center,
                ) { XaniText("+", Type.LabelBold, color = Color.White, fontSizeOverride = 13f) }
            }
            Spacer(Modifier.height(rdp(4)))
            XaniText(copy.t(Copy.InboxMyStory), Type.Micro, color = Colors.Sub, fontSizeOverride = 10f)
        }
        groups.filterNot { it.mine }.forEach { g ->
            key(g.user.id) {
                Column(
                    modifier = Modifier.pressable { onOpen(g) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(rdp(56))
                            .clip(CircleShape)
                            .background(
                                if (g.unseen > 0) Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))
                                else SolidColor(Colors.BorderStrong),
                            )
                            .padding(rdp(2.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Colors.Background).padding(rdp(2)), contentAlignment = Alignment.Center) {
                            PersonAvatar(g.user.id, g.user.name, g.user.avatarUrl, rdp(46))
                        }
                    }
                    Spacer(Modifier.height(rdp(4)))
                    XaniText(g.user.name.substringBefore(' ').ifBlank { "…" }, Type.Micro, color = Colors.Sub, fontSizeOverride = 10f, maxLines = 1)
                }
            }
        }
    }
}

// ─── composer ────────────────────────────────────────────────────────────────

/**
 * Post a story: a picture (camera or gallery), a clip (gallery), or a text
 * card on a colour; audience public / friends / only me. Media is resized
 * off the UI thread and streamed up; the card lands on the rail when the
 * server says so.
 */
@Composable
internal fun StoryComposer(copy: Copy, onClose: () -> Unit, onPosted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var bg by remember { mutableIntStateOf(0) }
    var audience by remember { mutableIntStateOf(0) } // 0 public · 1 friends · 2 only me
    var galleryOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val audiences = listOf("public", "friends", "only_me")

    fun finish(r: Boolean) {
        busy = false
        if (r) { StoryStore.refresh(force = true); onPosted() } else error = copy.t(Copy.StoryFailed)
    }
    fun postMedia(uri: Uri) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            val ok = withContext(Dispatchers.IO) { uploadStory(context, uri, audiences[audience]) }
            finish(ok)
        }
    }
    fun postText() {
        if (busy) return
        val body = text.trim().ifBlank { "✨" }.take(240)
        busy = true
        error = ""
        scope.launch {
            val r = runFeature(null) { Xani.stories.post("text", "", body, "#%06x".format(StoryBgs[bg] and 0xFFFFFFL), audiences[audience]) }
            finish(r?.ok == true)
        }
    }
    val capture = rememberCaptureLaunchers(onPhoto = ::postMedia, onMedia = ::postMedia, onDocument = {})

    BackHandler { if (type != null) type = null else onClose() }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(if (type == "text") Color(StoryBgs[bg]) else Colors.Background)
                .statusBarsPadding(),
        ) {
            if (type == null) {
                HeaderRow(copy.t(Copy.InboxCreateStory), onClose)
                Box(
                    Modifier
                        .padding(rdp(20))
                        .fillMaxWidth()
                        .height(rdp(180))
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent))),
                    contentAlignment = Alignment.Center,
                ) { XaniText(copy.t(Copy.InboxYourStory), Type.LabelStrong, color = Color.White) }
                XaniText(copy.t(Copy.InboxChooseType), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(horizontal = rdp(20)), fontSizeOverride = 12f)
                Row(Modifier.padding(rdp(20)), horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                    listOf("photo" to copy.t(Copy.InboxPhoto), "video" to copy.t(Copy.InboxVideo), "text" to copy.t(Copy.InboxText)).forEach { (k, label) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rdp(16)))
                                .background(Colors.Surface)
                                .border(rdp(1), Colors.Border, RoundedCornerShape(rdp(16)))
                                .pressable { if (k == "video") galleryOpen = true else type = k }
                                .padding(vertical = rdp(14)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { XaniText(label, Type.LabelBold, color = Colors.Text, fontSizeOverride = 13f) }
                    }
                }
                XaniText(copy.t(Copy.InboxAudience), Type.Micro, color = Colors.Muted, modifier = Modifier.padding(horizontal = rdp(20)), fontSizeOverride = 12f)
                AudienceChips(copy, audience, dark = false) { audience = it }
                if (error.isNotEmpty()) XaniText(error, Type.Caption, color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = rdp(20)))
            } else if (type == "text") {
                Row(Modifier.fillMaxWidth().padding(rdp(16)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    XaniText(copy.t(Copy.InboxCancel), Type.LabelStrong, color = Color.White, modifier = Modifier.pressable { type = null })
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(20)))
                            .background(Color.White.copy(alpha = if (busy) 0.4f else 0.92f))
                            .pressable(enabled = !busy) { postText() }
                            .padding(horizontal = rdp(16), vertical = rdp(7)),
                    ) {
                        XaniText(if (busy) copy.t(Copy.StoryPosting) else copy.t(Copy.InboxShareStory), Type.LabelBold, color = Color(StoryBgs[bg]), fontSizeOverride = 13f)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(240) },
                        textStyle = Type.H2.copy(color = Color.White, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().padding(rdp(24)),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (text.isEmpty()) XaniText("Type something…", Type.H2, color = Color.White.copy(alpha = 0.6f), align = TextAlign.Center)
                                inner()
                            }
                        },
                    )
                }
                AudienceChips(copy, audience, dark = true) { audience = it }
                Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f)).padding(rdp(12)).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText(copy.t(Copy.InboxBackground), Type.Caption, color = Color.White.copy(alpha = 0.8f))
                    StoryBgs.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(rdp(30))
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(rdp(3), if (bg == i) Color.White else Color.Transparent, CircleShape)
                                .pressable { bg = i },
                        )
                    }
                }
                if (error.isNotEmpty()) XaniText(error, Type.Caption, color = Color.White, modifier = Modifier.padding(rdp(12)))
            } else {
                Column(Modifier.fillMaxSize().background(Color(0xFF111111)), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(rdp(16)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        XaniText(copy.t(Copy.InboxCancel), Type.LabelStrong, color = Color.White, modifier = Modifier.pressable { type = null })
                        XaniText(copy.t(Copy.InboxPhotoStory), Type.LabelBold, color = Color.White)
                        Spacer(Modifier.width(rdp(48)))
                    }
                    Spacer(Modifier.weight(1f))
                    if (busy) {
                        XaniText(copy.t(Copy.StoryPosting), Type.LabelBold, color = Color.White)
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(24)))
                                .background(Brush.linearGradient(listOf(Dark.Primary, Dark.Accent)))
                                .pressable { capture.requestCamera { capture.takePhoto() } }
                                .padding(horizontal = rdp(28), vertical = rdp(12)),
                        ) { XaniText(copy.t(Copy.InboxOpenCamera), Type.LabelBold, color = Color.White) }
                        Spacer(Modifier.height(rdp(12)))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(rdp(24)))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(rdp(1), Color.White.copy(alpha = 0.2f), RoundedCornerShape(rdp(24)))
                                .pressable { galleryOpen = true }
                                .padding(horizontal = rdp(28), vertical = rdp(12)),
                        ) { XaniText(copy.t(Copy.InboxChooseGallery), Type.LabelStrong, color = Color.White) }
                    }
                    if (error.isNotEmpty()) XaniText(error, Type.Caption, color = Color(0xFFFCA5A5), modifier = Modifier.padding(rdp(12)))
                    Spacer(Modifier.weight(1f))
                    AudienceChips(copy, audience, dark = true) { audience = it }
                    Spacer(Modifier.height(rdp(16)).navigationBarsPadding())
                }
            }
        }
        if (galleryOpen) {
            DeviceGallerySheet(copy = copy, onClose = { galleryOpen = false }, onPick = { uri -> galleryOpen = false; postMedia(uri) })
        }
    }
}

@Composable
private fun AudienceChips(copy: Copy, picked: Int, dark: Boolean, onPick: (Int) -> Unit) {
    val on = if (dark) Color.White else Color(0xFF7C3AED)
    val off = if (dark) Color.White.copy(alpha = 0.6f) else Colors.Sub
    Row(Modifier.padding(horizontal = rdp(20), vertical = rdp(12)), horizontalArrangement = Arrangement.spacedBy(rdp(8))) {
        listOf(copy.t(Copy.InboxEveryone), copy.t(Copy.InboxFriends), copy.t(Copy.InboxOnlyMe)).forEachIndexed { i, label ->
            val sel = i == picked
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(if (sel) on.copy(alpha = if (dark) 0.22f else 0.08f) else Color.Transparent)
                    .border(rdp(1), if (sel) on else (if (dark) Color.White.copy(alpha = 0.3f) else Colors.BorderStrong), RoundedCornerShape(rdp(20)))
                    .pressable { onPick(i) }
                    .padding(vertical = rdp(8)),
                contentAlignment = Alignment.Center,
            ) { XaniText(label, Type.LabelStrong, color = if (sel) on else off, fontSizeOverride = 12f) }
        }
    }
}

/** Resize + upload + post, off the UI thread. A clip streams straight from the picker's URI. */
private suspend fun uploadStory(context: Context, uri: Uri, audience: String): Boolean {
    val mime = runFeature("") { context.contentResolver.getType(uri).orEmpty() }
    if (mime.startsWith("video/")) {
        val size = runFeature(-1L) {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }
        if (size <= 0L || size > 40L shl 20) return false
        val key = runFeature(null) {
            Xani.stories.putMedia("video/mp4", size) { runFeature(null) { context.contentResolver.openInputStream(uri) } }
        }?.value ?: return false
        return runFeature(null) { Xani.stories.post("video", key, "", "", audience) }?.ok == true
    }
    val bmp = PickedImage.decodeUpright(context, uri, 1440) ?: return false
    val jpeg = PickedImage.encodeJpeg(bmp, 86)
    bmp.recycle()
    if (jpeg == null || jpeg.size > 4L shl 20) return false
    val key = runFeature(null) { Xani.stories.putImage(jpeg) }?.value ?: return false
    return runFeature(null) { Xani.stories.post("image", key, "", "", audience) }?.ok == true
}

// ─── player ──────────────────────────────────────────────────────────────────

/**
 * Full-screen player for one poster's stories: segments on top, name and
 * time, tap right / left to move, auto-advance (clips run to their end).
 * Mine show the eye with the view count; tapping it lists who looked, with
 * Delete. Tapping the name opens their profile.
 */
@Composable
internal fun StoryPlayer(group: StoryGroupData, copy: Copy, onClose: () -> Unit) {
    val stories = group.stories
    if (stories.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    var index by remember(group.user.id) { mutableIntStateOf(stories.indexOfFirst { !it.seen }.takeIf { it >= 0 } ?: 0) }
    var viewersOpen by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(setOf<Long>()) }
    val live = stories.filterNot { it.id in deleted }
    if (live.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val i = index.coerceIn(0, live.lastIndex)
    val s = live[i]
    val scope = rememberCoroutineScope()

    fun next() { if (i < live.lastIndex) index = i + 1 else onClose() }
    fun prev() { if (i > 0) index = i - 1 }

    // One view per story per person; the server dedupes and tells the poster.
    LaunchedEffect(s.id) {
        if (!group.mine && !s.seen) {
            StoryStore.seen(s.id)
            runFeature(null) { Xani.stories.view(s.id) }
        }
    }
    // Stills and text cards advance on a timer; clips on completion. The
    // sheet holds the clock.
    LaunchedEffect(s.id, viewersOpen) {
        if (s.kind == "video" || viewersOpen) return@LaunchedEffect
        delay(5_000)
        next()
    }
    BackHandler { if (viewersOpen) viewersOpen = false else onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (s.kind) {
            "text" -> Box(Modifier.fillMaxSize().background(storyTint(s.bg)), contentAlignment = Alignment.Center) {
                XaniText(s.text, Type.H2, color = Color.White, modifier = Modifier.padding(rdp(28)), align = TextAlign.Center)
            }
            "video" -> key(s.id) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnCompletionListener { next() }
                            setOnErrorListener { _, _, _ -> next(); true }
                            setVideoURI(Uri.parse(storyMediaUrl(s)))
                            setOnPreparedListener { it.isLooping = false; start() }
                        }
                    },
                    onRelease = { runFeature(Unit) { it.stopPlayback() } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> RemoteImage(storyMediaUrl(s), Modifier.fillMaxSize(), maxEdge = 1440, fallback = Color.Black)
        }
        // Tap zones: left third back, the rest forward.
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().pressable(pressedScale = 1f) { prev() })
            Box(Modifier.weight(2f).fillMaxHeight().pressable(pressedScale = 1f) { next() })
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rdp(12)).padding(top = rdp(8))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rdp(3))) {
                live.forEachIndexed { k, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(3))
                            .clip(RoundedCornerShape(rdp(2)))
                            .background(Color.White.copy(alpha = if (k <= i) 0.95f else 0.35f)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = rdp(12)), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).pressable(pressedScale = 1f) { onClose(); PersonRouter.open(group.user.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(group.user.id, group.user.name, group.user.avatarUrl, rdp(36), frameUrl = group.user.frameUrl, frameMime = group.user.frameMime)
                    Spacer(Modifier.width(rdp(10)))
                    XaniText(if (group.mine) copy.t(Copy.StoryYou) else group.user.name, Type.LabelBold, color = Color.White, maxLines = 1)
                    Spacer(Modifier.width(rdp(8)))
                    XaniText(ago(s.createdMs), Type.Caption, color = Color.White.copy(alpha = 0.7f))
                }
                XaniText("✕", Type.LabelBold, color = Color.White, fontSizeOverride = 18f, modifier = Modifier.pressable(pressedScale = 1f) { onClose() }.padding(rdp(6)))
            }
        }
        if (group.mine) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = rdp(18))
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .pressable(pressedScale = 0.96f) { viewersOpen = true }
                    .padding(horizontal = rdp(16), vertical = rdp(9)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                XaniText("👁", Type.Label, fontSizeOverride = 14f)
                XaniText("${s.views}", Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
            }
        }
        if (viewersOpen) {
            StoryViewersSheet(
                story = s,
                copy = copy,
                onClose = { viewersOpen = false },
                onDelete = {
                    scope.launch {
                        val r = runFeature(null) { Xani.stories.delete(s.id) }
                        if (r?.ok == true) {
                            viewersOpen = false
                            StoryStore.removed(s.id)
                            deleted = deleted + s.id
                        }
                    }
                },
            )
        }
    }
}

/** WhatsApp-style bottom list of who looked, newest first; tap a row for their profile. */
@Composable
private fun StoryViewersSheet(story: StoryData, copy: Copy, onClose: () -> Unit, onDelete: () -> Unit) {
    var rows by remember(story.id) { mutableStateOf<List<StoryViewerData>?>(null) }
    LaunchedEffect(story.id) {
        val r = runFeature(null) { Xani.stories.viewers(story.id) }
        rows = r?.value ?: emptyList()
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).pressable(pressedScale = 1f, onClick = onClose))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rdp(22), topEnd = rdp(22)))
                .background(Colors.Background)
                .navigationBarsPadding()
                .padding(bottom = rdp(12)),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = rdp(10)).size(width = rdp(36), height = rdp(4)).clip(CircleShape).background(Colors.BorderStrong))
            Row(Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(12)), verticalAlignment = Alignment.CenterVertically) {
                XaniText(copy.t(Copy.StorySeenBy, "n" to "${rows?.size ?: story.views}"), Type.LabelBold, color = Colors.Text, modifier = Modifier.weight(1f))
                XaniText(
                    copy.t(Copy.InboxDelete), Type.LabelStrong, color = Color(0xFFEF4444),
                    modifier = Modifier.pressable(onClick = onDelete).padding(horizontal = rdp(8), vertical = rdp(4)),
                )
            }
            val list = rows
            when {
                list == null -> Spacer(Modifier.height(rdp(120)))
                list.isEmpty() -> XaniText(
                    copy.t(Copy.StoryNoViews), Type.Caption, color = Colors.Muted, align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = rdp(36)),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = rdp(420)), contentPadding = PaddingValues(bottom = rdp(8))) {
                    items(list, key = { it.user.id }) { v ->
                        Row(
                            Modifier.fillMaxWidth().pressable(pressedScale = 1f) { onClose(); PersonRouter.open(v.user.id) }.padding(horizontal = rdp(20), vertical = rdp(9)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonAvatar(v.user.id, v.user.name, v.user.avatarUrl, rdp(42), frameUrl = v.user.frameUrl, frameMime = v.user.frameMime)
                            Spacer(Modifier.width(rdp(12)))
                            Column(Modifier.weight(1f)) {
                                XaniText(v.user.name, Type.LabelStrong, color = Colors.Text, maxLines = 1)
                                if (v.user.username.isNotBlank()) XaniText("@${v.user.username}", Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
                            }
                            XaniText(ago(v.viewedMs), Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
                        }
                    }
                }
            }
        }
    }
}

// ─── activity lane ───────────────────────────────────────────────────────────

/** "X viewed your status", newest first, live. Tap a row for their profile. */
@Composable
internal fun ActivityLane(copy: Copy, bottom: androidx.compose.ui.unit.Dp, onOpenPost: (Long) -> Unit = {}) {
    val rows by StoryStore.activity.collectAsState()
    LaunchedEffect(Unit) { StoryStore.loadActivity() }
    if (rows.isEmpty()) {
        XaniText(
            copy.t(Copy.ActivityEmpty), Type.Caption, color = Colors.Muted, align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = rdp(32), vertical = rdp(48)),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottom + rdp(16))) {
        items(rows, key = { it.id }) { a ->
            // Engagement rows lead to the post they happened on; everything
            // else leads to the person who did it.
            val postKind = a.kind == "post_comment" || a.kind == "comment_reply" || a.kind == "comment_like"
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(pressedScale = 1f) {
                        if (postKind && a.refId != 0L) onOpenPost(a.refId) else PersonRouter.open(a.actor.id)
                    }
                    .padding(horizontal = rdp(20), vertical = rdp(11)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(a.actor.id, a.actor.name, a.actor.avatarUrl, rdp(44), frameUrl = a.actor.frameUrl, frameMime = a.actor.frameMime)
                Spacer(Modifier.width(rdp(12)))
                Column(Modifier.weight(1f)) {
                    XaniText(
                        a.actor.name + " " + when (a.kind) {
                            "story_view" -> copy.t(Copy.StoryViewedYours)
                            "post_comment" -> copy.t(Copy.ActivityCommented)
                            "comment_reply" -> copy.t(Copy.ActivityReplied)
                            "comment_like" -> copy.t(Copy.ActivityCommentLiked)
                            else -> a.kind.replace('_', ' ')
                        },
                        Type.Caption, color = Colors.Text,
                    )
                    XaniText(ago(a.createdMs), Type.Micro, color = Colors.Muted, fontSizeOverride = 11f)
                }
                when (a.kind) {
                    "story_view" -> XaniText("👁", Type.Label, fontSizeOverride = 14f)
                    "post_comment", "comment_reply" -> XaniText("💬", Type.Label, fontSizeOverride = 14f)
                    "comment_like" -> XaniText("❤️", Type.Label, fontSizeOverride = 14f)
                }
            }
        }
    }
}
