package app.xani.feature.gift

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.xani.core.isolate.runFeature
import app.xani.core.net.LivePersonData
import app.xani.core.net.StickerData
import app.xani.core.net.Xani
import app.xani.feature.call.LiveSession
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.AssetAnimation
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.LocalLiveSheetDrag
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

/**
 * The sticker tray, opened from the right of the chat composer. Categories
 * are the dashboard's sticker categories; a tap on a sticker sends it into
 * the room chat at once and the tray slides away, like a keyboard sticker.
 *
 * The "To" row aims the sticker at one person in the room (host first): the
 * line then reads "@name" with the picture under it, for everyone. "Everyone"
 * (the default) is a plain sticker line.
 */
@Composable
internal fun LiveStickerSheet(
    roomId: Long,
    hostId: Long,
    copy: Copy,
    onClose: () -> Unit,
) {
    val catalog by StickerCatalogStore.state.collectAsState()
    var people by remember(roomId) { mutableStateOf<List<LivePersonData>>(emptyList()) }
    var toId by remember(roomId) { mutableLongStateOf(0L) }
    var categoryId by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(catalog.categories.isEmpty()) }

    LaunchedEffect(roomId) {
        launch {
            withContext(Dispatchers.IO) { StickerCatalogStore.load() }
            loading = false
        }
        launch {
            val r = withContext(Dispatchers.IO) { runFeature(null) { Xani.calls.livePeople(roomId, limit = 50) } }
            val items = r?.value?.items ?: emptyList()
            val me = ProfileStore.currentUserId
            // Host first, then the stage, then viewers; never yourself — a
            // sticker "@me" is not a thing.
            val host = items.firstOrNull { it.role == "host" }
                ?: LivePersonData(hostId, "", "", "", "host").takeIf { hostId > 0L }
            val rest = items.filter { it.role != "host" }.sortedBy { if (it.role == "guest") 0 else 1 }
            people = (listOfNotNull(host) + rest).filter { it.id != me }
        }
    }
    LaunchedEffect(catalog.rev, catalog.categories.size) {
        if (catalog.categories.isEmpty()) return@LaunchedEffect
        if (catalog.categories.none { it.id == categoryId }) categoryId = catalog.categories[0].id
    }

    LiveSheet(onClose = onClose) { close ->
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .then(LocalLiveSheetDrag.current)
                .padding(top = rdp(12), start = rdp(16), end = rdp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XaniText(copy.t(Copy.LiveStickers), Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
        }

        // Who it is for
        if (people.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(LocalLiveSheetDrag.current)
                    .padding(top = rdp(10), start = rdp(16), end = rdp(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XaniText(copy.t(Copy.LiveStickerTo), Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 12f)
                Spacer(Modifier.width(rdp(10)))
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
                    item(key = 0L) {
                        TargetChip(selected = toId == 0L, onTap = { toId = 0L }) {
                            XaniText(
                                copy.t(Copy.LiveStickerEveryone),
                                Type.LabelBold,
                                color = Color.White,
                                fontSizeOverride = 10f,
                                maxLines = 1,
                            )
                        }
                    }
                    items(people, key = { it.id }) { p ->
                        val name = p.fullName.ifBlank { p.username }.ifBlank { if (p.role == "host") copy.t(Copy.LiveGiftHost) else "…" }
                        TargetChip(selected = toId == p.id, onTap = { toId = p.id }) {
                            PersonAvatar(p.id, name, p.avatarUrl, rdp(40), frameUrl = p.frameUrl, frameMime = p.frameMime)
                            if (p.role == "host") {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .clip(RoundedCornerShape(rdp(6)))
                                        .background(Color(0xFF7C3AED))
                                        .padding(horizontal = rdp(4), vertical = 1.dp),
                                ) {
                                    XaniText(copy.t(Copy.LiveGiftHost), Type.LabelBold, color = Color.White, fontSizeOverride = 8f)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Categories
        if (catalog.categories.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = rdp(6)).then(LocalLiveSheetDrag.current),
                contentPadding = PaddingValues(horizontal = rdp(14)),
                horizontalArrangement = Arrangement.spacedBy(rdp(18)),
            ) {
                items(catalog.categories, key = { it.id }) { c ->
                    val on = c.id == categoryId
                    Column(
                        Modifier.pressable(pressedScale = 0.96f, onClick = { categoryId = c.id }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        XaniText(
                            c.title,
                            Type.LabelBold,
                            color = if (on) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSizeOverride = 13f,
                            modifier = Modifier.padding(vertical = rdp(8)),
                        )
                        Box(
                            Modifier
                                .height(rdp(2))
                                .width(rdp(22))
                                .clip(CircleShape)
                                .background(if (on) Color(0xFFA78BFA) else Color.Transparent),
                        )
                    }
                }
            }
        }

        // Grid: a tap sends and closes.
        val cat = catalog.categories.firstOrNull { it.id == categoryId }
        val target = people.firstOrNull { it.id == toId }
        Box(Modifier.fillMaxWidth().height(rdp(250)).navigationBarsPadding()) {
            when {
                loading && catalog.categories.isEmpty() -> Centered(copy.t(Copy.LiveStickerLoading))
                cat == null || cat.stickers.isEmpty() -> Centered(copy.t(Copy.LiveStickerEmpty))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = rdp(10), vertical = rdp(8)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                    verticalArrangement = Arrangement.spacedBy(rdp(6)),
                ) {
                    items(cat.stickers, key = { it.id }) { s ->
                        StickerCell(s) {
                            LiveSession.sendSticker(s, target)
                            close()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetChip(selected: Boolean, onTap: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) { scale.animateTo(if (selected) 1.08f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Box(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .size(rdp(46))
            .clip(CircleShape)
            .then(
                if (selected) Modifier.border(2.dp, Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), CircleShape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            )
            .background(if (selected) Color(0x338B5CF6) else Color.White.copy(alpha = 0.04f))
            .padding(3.dp)
            .pressable(pressedScale = 0.92f, onClick = onTap),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun StickerCell(s: StickerData, onTap: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(rdp(12)))
            .background(Color.White.copy(alpha = 0.04f))
            .pressable(pressedScale = 0.9f, onClick = onTap)
            .padding(rdp(6)),
        contentAlignment = Alignment.Center,
    ) {
        AssetAnimation(s.imageUrl, s.imageMime, Modifier.fillMaxSize(), maxEdge = 160)
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        XaniText(text, Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 13f)
    }
}

/** The tray's button: a smiling face with a folded sticker corner. */
@Composable
internal fun StickerMark(size: androidx.compose.ui.unit.Dp, tint: Color) {
    VectorCanvas(
        shapes = listOf(
            // Rounded square with the bottom-right corner peeled.
            VShape.D("M8 3h8a5 5 0 0 1 5 5v5l-8 8H8a5 5 0 0 1-5-5V8a5 5 0 0 1 5-5z", stroke(tint, 1.5f)),
            VShape.D("M21 13h-4a4 4 0 0 0-4 4v4", stroke(tint, 1.5f)),
            VShape.Circle(9f, 10f, 1.1f, fill(tint)),
            VShape.Circle(15f, 10f, 1.1f, fill(tint)),
            VShape.D("M8.5 14c1 1.2 2.2 1.8 3.5 1.8", stroke(tint, 1.5f)),
        ),
        viewport = 24f,
        modifier = Modifier.size(size),
    )
}
