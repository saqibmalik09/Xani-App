package app.xani.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import app.xani.core.media.RemoteImage
import app.xani.core.net.BannerData
import app.xani.core.net.MediaConfig
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.vector.VShape
import app.xani.ui.vector.VectorCanvas
import app.xani.ui.vector.fill
import app.xani.ui.vector.stroke
import kotlinx.coroutines.delay

/**
 * Figma LIVE browse: category tabs, media/country filters, banner carousel,
 * 2-col live cards. Everything is a cache read — the pane never blocks on IO.
 */
@Composable
internal fun LiveBrowsePane(
    tiles: List<LiveTile>,
    copy: Copy,
    onOpen: (LiveTile) -> Unit,
    modifier: Modifier = Modifier,
    /** False until the first server read lands: an empty list then is "loading", not "nobody". */
    loaded: Boolean = true,
) {
    val bottom = LocalFeedBottomReserve.current
    var category by remember { mutableStateOf(LiveCategory.Explore) }
    var media by remember { mutableStateOf(LiveMedia.All) }
    var country by remember { mutableStateOf("ALL") }
    var countryOpen by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    var bannerIdx by remember { mutableIntStateOf(0) }
    val banners by BannerStore.items.collectAsState()

    LaunchedEffect(Unit) { BannerStore.load() }

    // Banner auto-advance — 3.2s loop; images already cached / prefetching.
    LaunchedEffect(banners.size) {
        if (banners.isEmpty()) return@LaunchedEffect
        if (bannerIdx >= banners.size) bannerIdx = 0
        while (true) {
            delay(3200)
            bannerIdx = (bannerIdx + 1) % banners.size
        }
    }

    val filtered = remember(tiles, category, media, country) {
        tiles.filter { t ->
            (country == "ALL" || t.country == country) &&
                when (media) {
                    LiveMedia.All -> true
                    LiveMedia.Video -> t.kind == LiveKind.Video
                    LiveMedia.Audio -> t.kind == LiveKind.Audio
                } &&
                when (category) {
                    LiveCategory.Pk -> t.pk
                    LiveCategory.Audio -> t.kind == LiveKind.Audio
                    else -> true
                }
        }
    }
    val selectedCountry = remember(country) {
        FeedStore.countries.firstOrNull { it.code == country } ?: FeedStore.countries[0]
    }

    Box(modifier.fillMaxSize().background(Color(0xFF07070F))) {
        Column(Modifier.fillMaxSize()) {
            // Category tabs
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = rdp(14), end = rdp(8)),
            ) {
                CategoryTab(copy.t(Copy.LiveExplore), "✦", category == LiveCategory.Explore) { category = LiveCategory.Explore }
                CategoryTab(copy.t(Copy.HomeLive), "●", category == LiveCategory.Live) { category = LiveCategory.Live }
                CategoryTab(copy.t(Copy.LivePkBattle), "⚔", category == LiveCategory.Pk) { category = LiveCategory.Pk }
                CategoryTab(copy.t(Copy.LiveAudioTab), "🎙", category == LiveCategory.Audio) { category = LiveCategory.Audio }
            }
            Box(Modifier.fillMaxWidth().height(rdp(1)).background(Color.White.copy(alpha = 0.06f)))

            // Filters row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(14), vertical = rdp(9)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                MediaPill(copy.t(Copy.LiveAll), media == LiveMedia.All) { media = LiveMedia.All }
                MediaPill(copy.t(Copy.LiveVideoPill), media == LiveMedia.Video) { media = LiveMedia.Video }
                MediaPill(copy.t(Copy.LiveAudioPill), media == LiveMedia.Audio) { media = LiveMedia.Audio }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(20)))
                        .background(if (country != "ALL") Color(0x388B5CF6) else Color.White.copy(alpha = 0.07f))
                        .border(
                            1.dp,
                            if (country != "ALL") Color(0x808B5CF6) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(rdp(20)),
                        )
                        .pressable(onClick = { countryOpen = !countryOpen })
                        .padding(horizontal = rdp(12), vertical = rdp(6)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                ) {
                    XaniText(selectedCountry.flag, Type.Label, fontSizeOverride = 15f)
                    XaniText(
                        selectedCountry.label,
                        Type.LabelBold,
                        color = if (country != "ALL") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSizeOverride = 12f,
                    )
                    val rot by animateFloatAsState(if (countryOpen) 180f else 0f, tween(200), label = "chev")
                    Box(Modifier.graphicsLayer { rotationZ = rot }) {
                        VectorCanvas(
                            shapes = listOf(VShape.D("M2 4L5 7L8 4", stroke(Color.White.copy(alpha = 0.5f), 1.4f))),
                            viewport = 10f,
                            modifier = Modifier.size(rdp(10)),
                        )
                    }
                }
            }

            // Content
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
                    start = rdp(12),
                    end = rdp(12),
                    bottom = bottom + rdp(20),
                ),
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                verticalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                if (banners.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }, key = "banner") {
                        BannerCarousel(banners, bannerIdx, onDot = { bannerIdx = it })
                    }
                }
                item(span = { GridItemSpan(2) }, key = "header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rdp(4), vertical = rdp(4)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(
                            when (category) {
                                LiveCategory.Explore -> copy.t(Copy.LiveTrendingNow)
                                LiveCategory.Pk -> copy.t(Copy.LivePkBattles)
                                else -> copy.t(Copy.LiveNow)
                            },
                            Type.LabelBold,
                            color = Color.White,
                            fontSizeOverride = 15f,
                        )
                        Spacer(Modifier.weight(1f))
                        XaniText(
                            copy.t(Copy.LiveCount, "n" to "${filtered.size}"),
                            Type.LabelBold,
                            color = Color(0xFF8B5CF6),
                            fontSizeOverride = 12f,
                        )
                    }
                }
                items(filtered, key = { it.id }) { tile ->
                    LiveCard(tile, copy, showPk = category == LiveCategory.Pk, onClick = { onOpen(tile) })
                }
                if (filtered.isEmpty() && loaded) {
                    item(span = { GridItemSpan(2) }, key = "empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = rdp(48), bottom = rdp(24)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(rdp(64))
                                    .clip(CircleShape)
                                    .background(Color(0x1F8B5CF6))
                                    .border(1.dp, Color(0x408B5CF6), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(rdp(10)).clip(CircleShape).background(Color(0xFFEC4899)))
                            }
                            XaniText(
                                copy.t(Copy.LiveNoneLive),
                                Type.LabelBold,
                                color = Color.White,
                                fontSizeOverride = 15f,
                                modifier = Modifier.padding(top = rdp(14)),
                            )
                            XaniText(
                                copy.t(Copy.LiveNoneLiveSub),
                                Type.Caption,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSizeOverride = 12f,
                                modifier = Modifier.padding(top = rdp(4)),
                            )
                        }
                    }
                }
            }
        }

        // Country dropdown — overlay
        if (countryOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pressable(onClick = { countryOpen = false; countryQuery = "" }),
            )
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = rdp(88), start = rdp(14), end = rdp(14))
                    .fillMaxWidth()
                    .heightIn(max = rdp(340))
                    .clip(RoundedCornerShape(rdp(18)))
                    .background(Color(0xF7120C23))
                    .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(rdp(18))),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rdp(16), vertical = rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                ) {
                    SearchGlyph(Color.White.copy(alpha = 0.4f), rdp(14))
                    BasicTextField(
                        value = countryQuery,
                        onValueChange = { countryQuery = it.take(30) },
                        textStyle = Type.Caption.copy(color = Color.White),
                        cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (countryQuery.isEmpty()) {
                                    XaniText(
                                        copy.t(Copy.LiveSearchCountry),
                                        Type.Caption,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSizeOverride = 13f,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                Box(Modifier.fillMaxWidth().height(rdp(1)).background(Color.White.copy(alpha = 0.07f)))
                val list = remember(countryQuery) {
                    if (countryQuery.isEmpty()) FeedStore.countries
                    else FeedStore.countries.filter {
                        it.label.contains(countryQuery, ignoreCase = true) ||
                            it.code.contains(countryQuery, ignoreCase = true)
                    }
                }
                if (list.isEmpty()) {
                    XaniText(
                        copy.t(Copy.LiveNoCountries),
                        Type.Caption,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSizeOverride = 13f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = rdp(24)),
                        align = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                } else {
                    LazyColumn {
                        items(list.size, key = { list[it].code }) { i ->
                            val c = list[i]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = {
                                        country = c.code
                                        countryOpen = false
                                        countryQuery = ""
                                    })
                                    .padding(horizontal = rdp(18), vertical = rdp(11)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(rdp(12)),
                            ) {
                                XaniText(c.flag, Type.Label, fontSizeOverride = 20f)
                                XaniText(
                                    c.label,
                                    if (country == c.code) Type.LabelBold else Type.Caption,
                                    color = if (country == c.code) Color.White else Color.White.copy(alpha = 0.72f),
                                    fontSizeOverride = 14f,
                                    modifier = Modifier.weight(1f),
                                )
                                if (country == c.code) {
                                    VectorCanvas(
                                        shapes = listOf(VShape.D("M2 7L5.5 10.5L12 4", stroke(Color(0xFF8B5CF6), 2f))),
                                        viewport = 14f,
                                        modifier = Modifier.size(rdp(14)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTab(label: String, icon: String, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .pressable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = rdp(14), vertical = rdp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rdp(5)),
        ) {
            XaniText(icon, Type.Label, color = if (on) Color(0xFFA78BFA) else Color.White.copy(alpha = 0.4f), fontSizeOverride = 11f)
            XaniText(
                label,
                if (on) Type.LabelBold else Type.Label,
                color = if (on) Color.White else Color.White.copy(alpha = 0.42f),
                fontSizeOverride = 13f,
            )
        }
        Box(
            Modifier
                .padding(top = rdp(6))
                .size(width = rdp(26), height = rdp(2))
                .clip(CircleShape)
                .background(if (on) Color(0xFF8B5CF6) else Color.Transparent),
        )
    }
}

@Composable
private fun MediaPill(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(20)))
            .background(if (on) Color(0x478B5CF6) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (on) Color(0x8C8B5CF6) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(rdp(20)),
            )
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(12), vertical = rdp(5)),
    ) {
        XaniText(
            label,
            if (on) Type.LabelBold else Type.Label,
            color = if (on) Color.White else Color.White.copy(alpha = 0.45f),
            fontSizeOverride = 12f,
        )
    }
}

@Composable
private fun BannerCarousel(banners: List<BannerData>, index: Int, onDot: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(2))
            .height(rdp(150))
            .clip(RoundedCornerShape(rdp(18)))
            .background(Color(0xFF110C22)),
    ) {
        banners.forEachIndexed { i, b ->
            val alpha by animateFloatAsState(if (i == index) 1f else 0f, tween(700), label = "banner$i")
            if (alpha > 0.01f) {
                val url = remember(b.imageKey) { BannerStore.imageUrl(b.imageKey) }
                RemoteImage(
                    url = url,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha },
                    contentScale = ContentScale.Crop,
                    maxEdge = 960,
                    fallback = Color(0xFF110C22),
                )
            }
        }
        if (banners.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = rdp(14), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(5)),
            ) {
                banners.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(width = if (i == index) rdp(18) else rdp(6), height = rdp(6))
                            .clip(CircleShape)
                            .background(if (i == index) Color.White else Color.White.copy(alpha = 0.38f))
                            .pressable(onClick = { onDot(i) }),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCard(tile: LiveTile, copy: Copy, showPk: Boolean, onClick: () -> Unit) {
    val color = Color(tile.color)
    Column(
        Modifier
            .clip(RoundedCornerShape(rdp(16)))
            .background(Color(0xFF110C22))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(rdp(16)))
            .pressable(role = Role.Button, pressedScale = 0.97f, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .drawWithCache {
                    val brush = Brush.linearGradient(
                        colors = listOf(color, Color(0xFF0A0514)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        ) {
            // The host's picture, stored at exactly this card's 3:4 frame. A host
            // who picked no cover shows their profile picture, not a bare tint:
            // the listing is faces, and a coloured block reads as broken.
            val coverUrl = remember(tile.coverUrl, tile.avatarUrl) {
                val cover = if (tile.coverUrl.startsWith("http")) tile.coverUrl else MediaConfig.url(tile.coverUrl.ifEmpty { null })
                cover.ifEmpty { if (tile.avatarUrl.startsWith("http")) tile.avatarUrl else MediaConfig.url(tile.avatarUrl.ifEmpty { null }) }
            }
            if (coverUrl.isNotEmpty()) {
                RemoteImage(url = coverUrl, modifier = Modifier.fillMaxSize(), maxEdge = 720, fallback = Color.Transparent)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE60A0514)),
                            startY = size.height * 0.45f,
                        )
                        onDrawBehind { drawRect(brush) }
                    },
            )
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(6)))
                        .background(Color(0xE6EC4899))
                        .padding(horizontal = rdp(8), vertical = rdp(3)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(4)),
                ) {
                    Box(Modifier.size(rdp(4)).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                    XaniText(copy.t(Copy.LiveBadge), Type.LabelBold, color = Color.White, fontSizeOverride = 9f)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = rdp(7), vertical = rdp(3)),
                ) {
                    XaniText(tile.flag, Type.Label, fontSizeOverride = 13f)
                }
            }
            if (showPk || tile.pk) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                        .padding(horizontal = rdp(10), vertical = rdp(4)),
                ) {
                    XaniText("⚔ PK", Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(rdp(8))
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = rdp(7), vertical = rdp(3)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(3)),
            ) {
                EyeGlyph()
                XaniText(tile.viewers, Type.LabelBold, color = Color.White, fontSizeOverride = 9f)
            }
            if (tile.locked || tile.friendsOnly) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(rdp(8))
                        .clip(RoundedCornerShape(rdp(10)))
                        .background(Color(0xCC17112B))
                        .padding(horizontal = rdp(7), vertical = rdp(3)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(3)),
                ) {
                    LockGlyph()
                    XaniText(
                        copy.t(if (tile.locked) Copy.LivePrivate else Copy.LiveFriends),
                        Type.LabelBold,
                        color = Color.White,
                        fontSizeOverride = 9f,
                    )
                }
            }
        }
        Column(Modifier.padding(rdp(10))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(6)),
            ) {
                if (tile.avatarUrl.isNotEmpty()) {
                    PersonAvatar(tile.hostId, tile.name, tile.avatarUrl, rdp(26))
                } else {
                    Box(
                        Modifier
                            .size(rdp(26))
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(tile.initials, Type.LabelBold, color = Color.White, fontSizeOverride = 10f)
                    }
                }
                Column {
                    XaniText(
                        tile.name,
                        Type.LabelBold,
                        color = Color.White,
                        fontSizeOverride = 12f,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    XaniText(tile.host, Type.Caption, color = Color.White.copy(alpha = 0.4f), fontSizeOverride = 10f)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(6)))
                        .background(Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = rdp(7), vertical = rdp(2)),
                ) {
                    XaniText(tile.category, Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 10f)
                }
                Spacer(Modifier.weight(1f))
                XaniText("💎", Type.Caption, fontSizeOverride = 11f)
                Spacer(Modifier.width(rdp(3)))
                XaniText(tile.diamonds, Type.LabelBold, color = Color(0xFFC4B5FD), fontSizeOverride = 10f)
            }
        }
    }
}

@Composable
internal fun LockGlyph(tint: Color = Color.White) {
    VectorCanvas(
        shapes = listOf(
            VShape.D("M3 4.5V3.5a2 2 0 0 1 4 0v1", stroke(tint, 1.1f)),
            VShape.D("M2 4.5h6v4H2z", fill(tint)),
        ),
        viewport = 10f,
        modifier = Modifier.size(rdp(9)),
    )
}

@Composable
internal fun EyeGlyph(tint: Color = Color.White) {
    VectorCanvas(
        shapes = listOf(
            VShape.D("M1 5C2.5 2.5 7.5 2.5 9 5c-1.5 2.5-6.5 2.5-8 0Z", stroke(tint, 1f)),
            VShape.Circle(5f, 5f, 1.2f, fill(tint)),
        ),
        viewport = 10f,
        modifier = Modifier.size(rdp(9)),
    )
}
