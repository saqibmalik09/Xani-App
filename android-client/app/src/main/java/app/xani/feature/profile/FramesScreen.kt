package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.xani.core.media.RemoteImage
import app.xani.core.net.FrameCatalogData
import app.xani.core.net.FrameItemData
import app.xani.core.net.FramePriceData
import app.xani.core.net.MediaConfig
import app.xani.core.net.OwnedFrameData
import app.xani.core.net.Xani
import app.xani.feature.social.AvatarFrame
import app.xani.feature.social.FrameStore
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Something acquired this recently carries a "New" tag in the listing. */
private const val NEW_TAG_MS = 48 * 3_600_000L

/**
 * Last listing seen, so reopening a box paints at once and only revalidates.
 * One process-wide copy: the catalog is the same for every category box.
 */
private object StoreListingCache {
    @Volatile var catalog: FrameCatalogData = FrameCatalogData.Empty
    @Volatile var owned: List<OwnedFrameData> = emptyList()
    @Volatile var userId = 0L
    @Volatile var loaded = false

    fun forUser(id: Long) {
        if (id == userId) return
        userId = id
        catalog = FrameCatalogData.Empty
        owned = emptyList()
        loaded = false
    }
}

/**
 * One category's listing — the box the user tapped on the profile (Frame ·
 * Badge · Entry · VIP · Free …) — in two tabs: everything for sale there, and
 * "My <category>": what they hold, split into wearing now, owned but parked,
 * and expired, with a New tag on anything that just arrived. One of each kind
 * can be worn at once (a frame AND a badge AND an entrance together; never two
 * frames). Buying picks a duration package; the coins move on the server and
 * the ring appears everywhere the moment the ack lands, because the ack
 * teaches [FrameStore]. With [StoreScope.All] the same screen lists the whole shop.
 */
@Composable
internal fun FramesScreen(copy: Copy, scope: StoreScope = StoreScope.All, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val co = rememberCoroutineScope()
    val selfId = Xani.session.lastKnownUser?.id ?: 0L
    StoreListingCache.forUser(selfId)

    var catalog by remember { mutableStateOf(StoreListingCache.catalog) }
    var owned by remember { mutableStateOf(StoreListingCache.owned) }
    var loaded by remember { mutableStateOf(StoreListingCache.loaded) }
    var buying by remember { mutableStateOf<FrameItemData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    // This category and everything nested under it.
    val catIds = remember(catalog, scope) { scopeCategoryIds(catalog, scope) }
    val shopItems = remember(catalog, catIds, scope) {
        when {
            scope.free -> emptyList()
            scope.categoryId == 0L -> catalog.items
            else -> catalog.items.filter { it.categoryId in catIds }
        }
    }
    val mineItems = remember(owned, catalog, catIds, scope) {
        // An older server does not say which category an owned item came
        // from; the shop item it was bought from does.
        val itemCat = catalog.items.associate { it.id to it.categoryId }
        when {
            scope.free -> owned.filter { it.isFree }
            scope.categoryId == 0L -> owned
            else -> {
                // A free grant (signup / role / admin) may name no category and
                // no shop item. It still belongs somewhere: the box whose shop
                // sells its kind — a free frame under Frame, a free badge under
                // Badge — where it shows with its FREE tag.
                val kindsHere = catalog.items.filter { it.categoryId in catIds }.map { it.kind }.toSet()
                owned.filter { o ->
                    val cat = if (o.categoryId != 0L) o.categoryId else (itemCat[o.itemId] ?: 0L)
                    cat in catIds || (cat == 0L && o.isFree && o.kind in kindsHere)
                }
            }
        }
    }
    // Already in the bag (unexpired), keyed by shop item: the shop row says so.
    val ownedByItem = remember(mineItems) { mineItems.filter { !it.expired }.associateBy { it.itemId } }
    // Open on "My …" when there is already something to manage there; a box
    // with nothing owned opens on the shop. Free has no shop at all.
    var mineTab by remember(scope) { mutableStateOf(scope.free || mineItems.isNotEmpty()) }

    fun ownedNow(list: List<OwnedFrameData>) {
        owned = list
        StoreListingCache.owned = list
        // Whatever frame is active is what the rest of the app should ring —
        // and the badge worn next to the name follows the same rule.
        val active = list.firstOrNull { it.active && !it.expired && it.kind == "frame" }
        FrameStore.learn(selfId, active?.assetKey.orEmpty(), active?.assetMime.orEmpty())
        val badge = list.firstOrNull { it.active && !it.expired && it.kind == "badge" }
        FrameStore.learnBadge(selfId, badge?.assetKey.orEmpty(), badge?.assetMime.orEmpty())
    }

    LaunchedEffect(Unit) {
        Xani.frames.catalog().value?.let {
            catalog = it
            StoreListingCache.catalog = it
        }
        Xani.frames.mine().value?.let { ownedNow(it) }
        loaded = true
        StoreListingCache.loaded = true
    }
    LaunchedEffect(toast) {
        if (toast.isEmpty()) return@LaunchedEffect
        delay(2000)
        toast = ""
    }

    val snapshot by ProfileStore.state.collectAsState()
    val name = if (scope.categoryId == 0L && !scope.free) copy.t(Copy.StoreTitle) else scope.title
    val now = System.currentTimeMillis()

    Box(Modifier.fillMaxSize().background(ProfileTokens.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(14)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(rdp(32))
                        .clip(CircleShape)
                        .background(ProfileTokens.Bg2)
                        .pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { ProfileIco.Back(ProfileTokens.Text, rdp(18)) }
                Spacer(Modifier.width(rdp(12)))
                StoreBoxIcon(scope.icon, ProfileTokens.AccentDeep, rdp(22))
                Spacer(Modifier.width(rdp(8)))
                XaniText(name, Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f, maxLines = 1, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(rdp(8)))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(ProfileTokens.AccentSoft)
                        .padding(horizontal = rdp(12), vertical = rdp(6)),
                ) {
                    XaniText("🪙 ${snapshot.balanceCoins}", Type.LabelBold, color = ProfileTokens.AccentDeep, fontSizeOverride = 12f)
                }
            }

            if (scope.free) {
                XaniText(
                    copy.t(Copy.StoreFreeSub),
                    Type.Caption,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 12f,
                    modifier = Modifier.padding(start = rdp(20), end = rdp(20), bottom = rdp(8)),
                )
            } else {
                Row(
                    Modifier
                        .padding(start = rdp(20), end = rdp(20), bottom = rdp(6))
                        .clip(RoundedCornerShape(rdp(12)))
                        .background(ProfileTokens.Bg2)
                        .padding(rdp(3)),
                ) {
                    SegTab(copy.t(Copy.StoreTabAll, "name" to name), !mineTab, Modifier.weight(1f)) { mineTab = false }
                    SegTab(
                        copy.t(Copy.StoreTabMine, "name" to name) + if (mineItems.isNotEmpty()) " · ${mineItems.size}" else "",
                        mineTab,
                        Modifier.weight(1f),
                    ) { mineTab = true }
                }
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = rdp(32))) {
                if (mineTab) {
                    val wearing = mineItems.filter { it.active && !it.expired }
                    val parked = mineItems.filter { !it.active && !it.expired }
                    val lapsed = mineItems.filter { it.expired }
                    fun section(key: String, title: String, list: List<OwnedFrameData>) {
                        if (list.isEmpty()) return
                        item(key = "$key-head") { SectionHead(title) }
                        items(list, key = { "own-${it.id}" }) { f ->
                            OwnedFrameRow(
                                frame = f,
                                copy = copy,
                                busy = busy,
                                isNew = f.createdAtMs > 0L && now - f.createdAtMs < NEW_TAG_MS,
                                onActivate = {
                                    if (busy) return@OwnedFrameRow
                                    busy = true
                                    // Optimistic: the row flips now, the server ack settles
                                    // it. Only rivals of the same kind park — wearing a badge
                                    // does not undress the frame.
                                    ownedNow(owned.map {
                                        if (it.kind == f.kind) it.copy(active = it.id == f.id && !it.expired) else it
                                    })
                                    co.launch {
                                        val r = Xani.frames.activate(f.id)
                                        if (r.value == null) toast = r.message
                                        Xani.frames.mine().value?.let { ownedNow(it) }
                                        StoreShelfStore.invalidate()
                                        busy = false
                                    }
                                },
                            )
                        }
                    }
                    section("wearing", copy.t(Copy.StoreWearing), wearing)
                    section("parked", copy.t(Copy.StoreParked), parked)
                    section("lapsed", copy.t(Copy.StoreLapsed), lapsed)
                    if (loaded && mineItems.isEmpty()) {
                        item(key = "mine-empty") {
                            XaniText(
                                if (scope.free) copy.t(Copy.StoreFreeEmpty) else copy.t(Copy.StoreMineEmpty, "name" to name),
                                Type.Caption,
                                color = ProfileTokens.Muted,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = rdp(40)),
                            )
                        }
                    }
                } else {
                    // The shop, grouped by what the item does when the category
                    // mixes kinds; a single-kind category is just the list.
                    val byKind = shopItems.groupBy { it.kind }
                    val kinds = kindOrder.filter { byKind.containsKey(it) } +
                        byKind.keys.filterNot { it in kindOrder }.sorted()
                    for (kind in kinds) {
                        val inKind = byKind[kind] ?: continue
                        if (kinds.size > 1) item(key = "kind-$kind") { SectionHead(kindTitle(copy, kind)) }
                        items(inKind, key = { "shop-${it.id}" }) { item ->
                            ShopFrameRow(item = item, copy = copy, owned = ownedByItem[item.id], onBuy = { buying = item })
                        }
                    }
                    if (loaded && shopItems.isEmpty()) {
                        item(key = "shop-empty") {
                            XaniText(
                                copy.t(Copy.StoreShopEmpty, "name" to name),
                                Type.Caption,
                                color = ProfileTokens.Muted,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = rdp(40)),
                            )
                        }
                    }
                }
            }
        }

        buying?.let { item ->
            DurationSheet(
                item = item,
                copy = copy,
                busy = busy,
                onPick = { price ->
                    if (busy) return@DurationSheet
                    busy = true
                    val clientId = "fr-${item.id}-${price.id}-${System.currentTimeMillis()}"
                    co.launch {
                        val r = Xani.frames.purchase(item.id, price.id, clientId)
                        val data = r.value
                        if (data == null) {
                            toast = if (r.code == "insufficient_funds") copy.t(Copy.FramesInsufficient) else r.message
                        } else {
                            ProfileStore.setBalance(data.balance)
                            if (data.frame.kind == "frame") {
                                FrameStore.learn(selfId, data.frame.assetKey, data.frame.assetMime)
                            }
                            if (data.frame.kind == "badge") {
                                FrameStore.learnBadge(selfId, data.frame.assetKey, data.frame.assetMime)
                            }
                            Xani.frames.mine().value?.let { ownedNow(it) }
                            StoreShelfStore.invalidate()
                            buying = null
                            // The purchase is live: show it under "My …" with its New tag.
                            mineTab = true
                        }
                        busy = false
                    }
                },
                onClose = { buying = null },
            )
        }

        if (toast.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = rdp(48))
                    .clip(RoundedCornerShape(rdp(20)))
                    .background(Color(0xE6111111))
                    .padding(horizontal = rdp(16), vertical = rdp(8)),
            ) {
                XaniText(toast, Type.LabelBold, color = Color.White, fontSizeOverride = 13f)
            }
        }
    }
}

/** The scope's category plus every descendant the catalog knows about. */
private fun scopeCategoryIds(catalog: FrameCatalogData, scope: StoreScope): Set<Long> {
    if (scope.categoryId == 0L) return emptySet()
    val out = HashSet<Long>()
    out += scope.categoryId
    // Categories arrive parents-first (server orders parent_id NULLS FIRST),
    // but a couple of passes make the walk independent of that.
    repeat(4) {
        var grew = false
        for (c in catalog.categories) {
            if (c.parentId in out && out.add(c.id)) grew = true
        }
        if (!grew) return out
    }
    return out
}

@Composable
private fun SegTab(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(rdp(10)))
            .background(if (on) ProfileTokens.Bg else Color.Transparent)
            .pressable(onClick = onClick)
            .padding(vertical = rdp(8)),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(
            label,
            Type.LabelBold,
            color = if (on) ProfileTokens.Text else ProfileTokens.Sub,
            fontSizeOverride = 12f,
            maxLines = 1,
        )
    }
}

/** Kinds in the order the shop lists them; unknown kinds trail alphabetically. */
private val kindOrder = listOf("frame", "badge", "entrance", "vip", "bubble")

private fun kindTitle(copy: Copy, kind: String): String = when (kind) {
    "frame" -> copy.t(Copy.StoreKindFrames)
    "badge" -> copy.t(Copy.StoreKindBadges)
    "entrance" -> copy.t(Copy.StoreKindEntrance)
    "vip" -> copy.t(Copy.StoreKindVip)
    "bubble" -> copy.t(Copy.StoreKindBubbles)
    else -> kind.replaceFirstChar { it.uppercase() }
}

private fun durationLabel(copy: Copy, days: Int): String = when (days) {
    0 -> copy.t(Copy.FramesPermanent)
    1 -> copy.t(Copy.FramesDay1)
    7 -> copy.t(Copy.FramesWeek1)
    30 -> copy.t(Copy.FramesMonth1)
    90 -> copy.t(Copy.FramesMonths3)
    else -> copy.t(Copy.FramesDays, "n" to days.toString())
}

@Composable
private fun SectionHead(text: String) {
    XaniText(
        text,
        Type.LabelBold,
        color = ProfileTokens.Sub,
        fontSizeOverride = 13f,
        modifier = Modifier.padding(start = rdp(20), top = rdp(14), bottom = rdp(6)),
    )
}

/** Small coloured tag: New · Active · Owned. */
@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(rdp(6)))
            .background(bg)
            .padding(horizontal = rdp(6), vertical = rdp(2)),
    ) {
        XaniText(text, Type.Micro, color = fg, fontSizeOverride = 9.5f, maxLines = 1)
    }
}

/**
 * The item drawn live in the listing. A frame rings a neutral disc — the
 * listing shows the real thing; badges and entrance effects show their own
 * preview (or asset) with nothing underneath.
 */
@Composable
private fun FramePreview(kind: String, previewKey: String, assetKey: String, assetMime: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (kind == "frame") {
            Box(
                Modifier
                    .size(size * 0.72f)
                    .clip(CircleShape)
                    .background(ProfileTokens.Bg3),
            )
        }
        when {
            previewKey.isNotEmpty() ->
                RemoteImage(url = MediaConfig.url(previewKey), modifier = Modifier.size(size), maxEdge = 240, fallback = Color.Transparent)
            assetKey.isNotEmpty() -> AvatarFrame(assetKey, assetMime, size * 0.72f)
        }
    }
}

@Composable
private fun OwnedFrameRow(frame: OwnedFrameData, copy: Copy, busy: Boolean, isNew: Boolean, onActivate: () -> Unit) {
    val faded = if (frame.expired) 0.45f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rdp(20), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(rdp(56)), contentAlignment = Alignment.Center) {
            FramePreview(frame.kind, frame.previewKey, if (frame.expired) "" else frame.assetKey, frame.assetMime, rdp(56))
        }
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(
                    frame.title,
                    Type.LabelBold,
                    color = ProfileTokens.Text.copy(alpha = faded),
                    fontSizeOverride = 14f,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isNew && !frame.expired) {
                    Spacer(Modifier.width(rdp(6)))
                    Tag(copy.t(Copy.StoreNew), Color(0xFFEF4444), Color.White)
                }
                if (frame.isFree && !frame.expired) {
                    Spacer(Modifier.width(rdp(6)))
                    Tag(copy.t(Copy.StoreFree), Color(0xFFE4F5FE), Color(0xFF0369A1))
                }
            }
            val sub = when {
                frame.expired -> copy.t(Copy.FramesExpired)
                frame.expiresAtMs <= 0L -> copy.t(Copy.FramesPermanent)
                else -> {
                    val left = ((frame.expiresAtMs - System.currentTimeMillis()) / 86_400_000L + 1).coerceAtLeast(1)
                    copy.t(Copy.FramesDaysLeft, "n" to left.toString())
                }
            }
            XaniText(
                "${kindTitle(copy, frame.kind)} · $sub",
                Type.Micro,
                color = ProfileTokens.Sub.copy(alpha = faded),
                fontSizeOverride = 11f,
            )
        }
        Spacer(Modifier.width(rdp(10)))
        when {
            frame.expired -> XaniText(copy.t(Copy.FramesExpired), Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 11f)
            frame.active -> Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(ProfileTokens.AccentSoft)
                    .padding(horizontal = rdp(12), vertical = rdp(6)),
            ) {
                XaniText(copy.t(Copy.FramesActive), Type.LabelBold, color = ProfileTokens.AccentDeep, fontSizeOverride = 11f)
            }
            else -> Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(14)))
                    .background(if (busy) ProfileTokens.Bg3 else ProfileTokens.AccentDeep)
                    .pressable(onClick = onActivate)
                    .padding(horizontal = rdp(12), vertical = rdp(6)),
            ) {
                XaniText(copy.t(Copy.FramesActivate), Type.LabelBold, color = Color.White, fontSizeOverride = 11f)
            }
        }
    }
}

@Composable
private fun ShopFrameRow(item: FrameItemData, copy: Copy, owned: OwnedFrameData?, onBuy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onBuy)
            .padding(horizontal = rdp(20), vertical = rdp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FramePreview(item.kind, item.previewKey, item.assetKey, item.assetMime, rdp(56))
        Spacer(Modifier.width(rdp(12)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XaniText(item.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                // Already in the bag: say so, buying again only extends it.
                if (owned != null) {
                    Spacer(Modifier.width(rdp(6)))
                    if (owned.active) {
                        Tag(copy.t(Copy.FramesActive), ProfileTokens.AccentSoft, ProfileTokens.AccentDeep)
                    } else {
                        Tag(copy.t(Copy.StoreOwned), ProfileTokens.Bg3, ProfileTokens.Sub)
                    }
                }
            }
            val cheapest = item.prices.minByOrNull { it.coinPrice }
            if (cheapest != null) {
                XaniText(
                    "${durationLabel(copy, cheapest.days)} · ${cheapest.coinPrice} 🪙" +
                        if (item.prices.size > 1) "  +${item.prices.size - 1}" else "",
                    Type.Micro,
                    color = ProfileTokens.Sub,
                    fontSizeOverride = 11f,
                )
            }
        }
        Spacer(Modifier.width(rdp(10)))
        ProfileIco.Chevron(Color(0xFFCCCCCC), rdp(15))
    }
}

/** Pick a duration package: each row is "the length — its price"; a tap buys. */
@Composable
private fun DurationSheet(
    item: FrameItemData,
    copy: Copy,
    busy: Boolean,
    onPick: (FramePriceData) -> Unit,
    onClose: () -> Unit,
) {
    LiveSheet(onClose = onClose) {
        Column(Modifier.fillMaxWidth().padding(bottom = rdp(18))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FramePreview(item.kind, item.previewKey, item.assetKey, item.assetMime, rdp(64))
                Spacer(Modifier.width(rdp(14)))
                Column {
                    XaniText(item.title, Type.H2, color = Color.White, fontSizeOverride = 17f)
                    XaniText(copy.t(Copy.FramesPickDuration), Type.Caption, color = Color(0xFFAAAAAA), fontSizeOverride = 12f)
                }
            }
            val sorted = item.prices.sortedBy { if (it.days == 0) Int.MAX_VALUE else it.days }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = rdp(20)),
                verticalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                for (p in sorted) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rdp(14)))
                            .background(Color(0xFF1B1B22))
                            .pressable(onClick = { if (!busy) onPick(p) })
                            .padding(horizontal = rdp(16), vertical = rdp(14)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XaniText(durationLabel(copy, p.days), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
                        Spacer(Modifier.weight(1f))
                        XaniText(
                            copy.t(Copy.FramesBuy, "n" to p.coinPrice.toString()),
                            Type.LabelBold,
                            color = Color(0xFFB794F6),
                            fontSizeOverride = 13f,
                        )
                    }
                }
            }
        }
    }
}
