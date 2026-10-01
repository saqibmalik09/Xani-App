package app.xani.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * The store and the user's own items, one screen. Owned items lead — active
 * first, expiring ones with their countdown, expired ones greyed — with the
 * shop a scroll below, grouped by what the item does: avatar frames, badges,
 * entrance effects. One of each kind can be worn at once (a frame AND a badge
 * AND an entrance together; never two frames). Buying picks a duration
 * package; the coins move on the server and the ring appears everywhere the
 * moment the ack lands, because the ack teaches [FrameStore].
 */
@Composable
internal fun FramesScreen(copy: Copy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val selfId = Xani.session.lastKnownUser?.id ?: 0L

    var catalog by remember { mutableStateOf(FrameCatalogData.Empty) }
    var owned by remember { mutableStateOf<List<OwnedFrameData>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf<FrameItemData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    fun ownedNow(list: List<OwnedFrameData>) {
        owned = list
        // Whatever frame is active is what the rest of the app should ring —
        // and the badge worn next to the name follows the same rule.
        val active = list.firstOrNull { it.active && !it.expired && it.kind == "frame" }
        FrameStore.learn(selfId, active?.assetKey.orEmpty(), active?.assetMime.orEmpty())
        val badge = list.firstOrNull { it.active && !it.expired && it.kind == "badge" }
        FrameStore.learnBadge(selfId, badge?.assetKey.orEmpty(), badge?.assetMime.orEmpty())
    }

    LaunchedEffect(Unit) {
        Xani.frames.catalog().value?.let { catalog = it }
        Xani.frames.mine().value?.let { ownedNow(it) }
        loaded = true
    }
    LaunchedEffect(toast) {
        if (toast.isEmpty()) return@LaunchedEffect
        delay(2000)
        toast = ""
    }

    val snapshot by ProfileStore.state.collectAsState()

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
                XaniText(copy.t(Copy.StoreTitle), Type.H2, color = ProfileTokens.Text, fontSizeOverride = 18f)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(rdp(16)))
                        .background(ProfileTokens.AccentSoft)
                        .padding(horizontal = rdp(12), vertical = rdp(6)),
                ) {
                    XaniText("🪙 ${snapshot.balanceCoins}", Type.LabelBold, color = ProfileTokens.AccentDeep, fontSizeOverride = 12f)
                }
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rdp(32))) {
                if (owned.isNotEmpty()) {
                    item(key = "mine-head") { SectionHead(copy.t(Copy.StoreMine)) }
                    items(owned, key = { "own-${it.id}" }) { f ->
                        OwnedFrameRow(
                            frame = f,
                            copy = copy,
                            busy = busy,
                            onActivate = {
                                if (busy) return@OwnedFrameRow
                                busy = true
                                // Optimistic: the row flips now, the server ack settles
                                // it. Only rivals of the same kind park — wearing a badge
                                // does not undress the frame.
                                owned = owned.map {
                                    if (it.kind == f.kind) it.copy(active = it.id == f.id && !it.expired) else it
                                }
                                if (f.kind == "frame") FrameStore.learn(selfId, f.assetKey, f.assetMime)
                                if (f.kind == "badge") FrameStore.learnBadge(selfId, f.assetKey, f.assetMime)
                                scope.launch {
                                    val r = Xani.frames.activate(f.id)
                                    if (r.value == null) toast = r.message
                                    Xani.frames.mine().value?.let { ownedNow(it) }
                                    busy = false
                                }
                            },
                        )
                    }
                } else if (loaded) {
                    item(key = "mine-empty") {
                        XaniText(
                            copy.t(Copy.FramesOwnedEmpty),
                            Type.Caption,
                            color = ProfileTokens.Muted,
                            align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = rdp(16)),
                        )
                    }
                }

                // The shop, one section per kind: frames, badges, entrance
                // effects… every kind the catalog carries is purchasable.
                val byKind = catalog.items.groupBy { it.kind }
                val kinds = kindOrder.filter { byKind.containsKey(it) } +
                    byKind.keys.filterNot { it in kindOrder }.sorted()
                for (kind in kinds) {
                    val inKind = byKind[kind] ?: continue
                    item(key = "kind-$kind") { SectionHead(kindTitle(copy, kind)) }
                    items(inKind, key = { "shop-${it.id}" }) { item ->
                        ShopFrameRow(item = item, copy = copy, onBuy = { buying = item })
                    }
                }
                if (loaded && catalog.items.isEmpty()) {
                    item(key = "shop-empty") {
                        XaniText(
                            copy.t(Copy.FramesEmpty),
                            Type.Caption,
                            color = ProfileTokens.Muted,
                            align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = rdp(28)),
                        )
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
                    scope.launch {
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
                            buying = null
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
private fun OwnedFrameRow(frame: OwnedFrameData, copy: Copy, busy: Boolean, onActivate: () -> Unit) {
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
            XaniText(frame.title, Type.LabelBold, color = ProfileTokens.Text.copy(alpha = faded), fontSizeOverride = 14f)
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
private fun ShopFrameRow(item: FrameItemData, copy: Copy, onBuy: () -> Unit) {
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
            XaniText(item.title, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 14f)
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
