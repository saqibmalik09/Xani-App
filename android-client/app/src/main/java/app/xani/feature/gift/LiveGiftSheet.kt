package app.xani.feature.gift

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xani.core.isolate.runFeature
import app.xani.core.media.RemoteImage
import app.xani.core.net.GiftData
import app.xani.core.net.LivePersonData
import app.xani.core.net.PkData
import app.xani.core.net.StageData
import app.xani.core.net.Xani
import app.xani.feature.call.LiveSession
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.LiveSheet
import app.xani.ui.components.LocalLiveSheetDrag
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The gift sheet: who to send to (host first, selected), categories, the
 * grid, and a bar with the sender's coins bottom-left and Send bottom-right.
 *
 * Send is optimistic: the tap plays the gift and drops the coin pill by the
 * price at once; the server's balance replaces the pill on reply and a refusal
 * puts the coins back in a modal. On every send the sheet slides away so the
 * sender watches their gift land on the room like everyone else, and slides
 * back the moment the animation lane is idle.
 *
 * Lucky gifts (the category is flagged in the dashboard) are the only ones
 * with a combo: the sheet stays hidden and a combo button floats bottom-right
 * with a 3 s ring; each tap sends again with combo+1 and everybody's banner
 * counts along. When the ring empties and the lane is idle, the sheet returns.
 */
@Composable
internal fun LiveGiftSheet(
    roomId: Long,
    hostId: Long,
    copy: Copy,
    onClose: () -> Unit,
    /** The room's animation lane: the sheet hides while it plays what was sent. */
    overlay: GiftOverlayState? = null,
    onRecharge: (() -> Unit)? = null,
    /** Who to open on (a seat the sender tapped); 0 = the host. */
    receiverId: Long = 0L,
    /** The stage right now: its people lead the receiver row before the roster read lands. */
    stage: StageData? = null,
    /** Active PK: both hosts lead the receiver row so either side can be gifted. */
    pk: PkData? = null,
) {
    val scope = rememberCoroutineScope()
    val catalog by GiftCatalogStore.state.collectAsState()
    val profile by ProfileStore.state.collectAsState()
    val stagePeople = remember(stage?.rev, pk?.id, pk?.left?.score, pk?.right?.score) {
        val seated = stage?.members?.map { it.user }.orEmpty()
        val pkPeople = listOfNotNull(
            pk?.left?.host?.let { h ->
                LivePersonData(h.id, h.username, h.fullName, h.avatarUrl, "host", frameUrl = h.frameUrl, frameMime = h.frameMime, senderLevel = h.senderLevel, receiverLevel = h.receiverLevel)
            },
            pk?.right?.host?.let { h ->
                LivePersonData(h.id, h.username, h.fullName, h.avatarUrl, "host", frameUrl = h.frameUrl, frameMime = h.frameMime, senderLevel = h.senderLevel, receiverLevel = h.receiverLevel)
            },
        )
        (pkPeople + seated).distinctBy { it.id }
    }
    var people by remember(roomId) { mutableStateOf(stagePeople) }
    var receiverId by remember(roomId) { mutableLongStateOf(if (receiverId > 0L) receiverId else hostId) }
    var categoryId by remember { mutableLongStateOf(0L) }
    var selectedId by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(catalog.categories.isEmpty()) }
    // A refusal is a modal over the sheet, never a line inside it.
    var dialog by remember { mutableStateOf<GiftDialog?>(null) }

    // Coins in flight: what this sheet has spent that the server has not
    // confirmed yet. Cleared per clientId on the echo or the refusal.
    val pending = remember { HashMap<String, Long>() }
    var pendingTotal by remember { mutableLongStateOf(0L) }

    var combo by remember { mutableIntStateOf(0) }
    val comboRing = remember { Animatable(0f) }
    var comboGift by remember { mutableLongStateOf(0L) }
    // Off-screen while what was just sent plays; state (selection, combo) stays.
    var hidden by remember { mutableStateOf(false) }
    var hiddenAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(roomId) {
        // The sheet opens on the ledger's number, not on whatever this process
        // last heard: a recharge from the dashboard is visible at once.
        ProfileStore.refresh()
        launch {
            withContext(Dispatchers.IO) { GiftCatalogStore.load() }
            loading = false
        }
        launch {
            val r = withContext(Dispatchers.IO) { runFeature(null) { Xani.calls.livePeople(roomId, limit = 50) } }
            val items = r?.value?.items ?: emptyList()
            // Host first; then guests; then viewers in join order. Yourself
            // included — a self-gift converts coins into diamonds on stage.
            val host = items.firstOrNull { it.role == "host" }
                ?: stagePeople.firstOrNull { it.role == "host" }
                ?: LivePersonData(hostId, "", "", "", "host").takeIf { hostId > 0L }
            // The stage leads (seat order), then guests the roster knows, then viewers.
            val seated = stagePeople.filter { it.role != "host" }
            val seen = HashSet<Long>().apply { host?.let { add(it.id) }; seated.forEach { add(it.id) } }
            val rest = items.filter { it.role != "host" && seen.add(it.id) }.sortedBy { if (it.role == "guest") 0 else 1 }
            val list = listOfNotNull(host) + seated + rest
            people = list
            if (receiverId == 0L || list.none { it.id == receiverId }) {
                receiverId = list.firstOrNull()?.id ?: 0L
            }
        }
    }
    // Auto-select: the first category and its first gift, once the catalog is here.
    LaunchedEffect(catalog.rev, catalog.categories.size) {
        if (catalog.categories.isEmpty()) return@LaunchedEffect
        if (catalog.categories.none { it.id == categoryId }) categoryId = catalog.categories[0].id
        val cat = catalog.categories.first { it.id == categoryId }
        if (cat.gifts.none { it.id == selectedId }) selectedId = cat.gifts.firstOrNull()?.id ?: 0L
    }
    LaunchedEffect(selectedId) {
        catalog.all.firstOrNull { it.id == selectedId }?.let(GiftCatalogStore::warm)
    }
    // Server replies settle the pending spend; refusals also explain themselves.
    LaunchedEffect(Unit) {
        launch {
            LiveSession.gifts.collect { ev ->
                if (ev.id != 0L && ev.clientId.isNotEmpty() && pending.remove(ev.clientId) != null) {
                    pendingTotal = pending.values.sum()
                }
            }
        }
        launch {
            LiveSession.giftErrors.collect { f ->
                if (pending.remove(f.clientId) != null) pendingTotal = pending.values.sum()
                combo = 0
                hidden = false // the refusal is read over the sheet, not over an empty room
                val g = catalog.all.firstOrNull { it.id == selectedId }
                dialog = when (f.code) {
                    "insufficient_funds" -> {
                        // The server's word on the balance beats ours: re-read it.
                        ProfileStore.refresh()
                        GiftDialog.NotEnough(g?.title ?: "", g?.coinPrice ?: 0L)
                    }
                    "too_fast" -> GiftDialog.Failed(copy.t(Copy.LiveGiftTooFast), retry = true)
                    else -> GiftDialog.Failed(f.message.ifBlank { copy.t(Copy.LiveGiftFailed) }, retry = true)
                }
            }
        }
    }
    // The server's balance arrived: nothing is pending against it any more.
    LaunchedEffect(profile.coinsValue) {
        if (pending.isNotEmpty()) { pending.clear(); pendingTotal = 0L }
    }
    // Hold the wallet stream while the sheet is open: a recharge from the
    // dashboard lands here in realtime, mid-broadcast, no reopen needed.
    LaunchedEffect(Unit) { ProfileStore.observeLive() }

    val selected = remember(selectedId, catalog) { catalog.all.firstOrNull { it.id == selectedId } }
    // Lucky is the category's flag from the dashboard (the server folds it into
    // the gift too). Only a lucky gift combos; everything else sends once.
    val selectedLucky = remember(selected, catalog) {
        val g = selected
        g != null && (g.lucky || catalog.categories.firstOrNull { it.id == g.categoryId }?.lucky == true)
    }
    // Reads the snapshot's exact number: a recharge landing on the wallet
    // stream recomposes this sheet, so the pill moves without reopening.
    val coins = (profile.coinsValue - pendingTotal).coerceAtLeast(0L)

    fun send() {
        val g = selected ?: return
        if (receiverId == 0L) return
        if (dialog != null) return
        val price = g.coinPrice
        // Refuse locally only on a balance the server has actually told us.
        // An unknown balance goes to the ledger, which is the only judge.
        if (ProfileStore.balanceKnown && coins < price) {
            combo = 0
            hidden = false
            dialog = GiftDialog.NotEnough(g.title, price)
            return
        }
        val lucky = selectedLucky
        val next = if (lucky && combo > 0 && comboGift == g.id) combo + 1 else 1
        val clientId = LiveSession.sendGift(g, receiverId, combo = next) ?: return
        pending[clientId] = price
        pendingTotal += price
        // Out of the way: the sender sees the gift land like everyone else.
        hidden = true
        hiddenAt = System.currentTimeMillis()
        if (lucky) {
            combo = next
            comboGift = g.id
            scope.launch {
                comboRing.snapTo(1f)
                comboRing.animateTo(0f, tween(3_000, easing = LinearEasing))
                if (comboGift == g.id && combo == next) combo = 0
            }
        } else {
            combo = 0
            comboGift = 0L
        }
    }

    // Come back when the lane is idle and no combo is running. A busy room
    // (other people's gifts queued behind ours) does not hold the sheet
    // hostage: it returns after [HIDDEN_MAX_MS] regardless.
    val lanePlaying = overlay?.playing != null
    LaunchedEffect(hidden, lanePlaying, combo) {
        if (!hidden) return@LaunchedEffect
        // The optimistic frame reaches the lane a few dispatches after the tap.
        delay(HIDDEN_MIN_MS)
        if (combo > 0) return@LaunchedEffect // the ring's end re-triggers this effect
        if (lanePlaying) {
            // Lane idle re-triggers this effect sooner; the cap is the ceiling.
            val left = HIDDEN_MAX_MS - (System.currentTimeMillis() - hiddenAt)
            if (left > 0L) delay(left)
        }
        hidden = false
    }

    Box(Modifier.fillMaxSize()) {
    LiveSheet(onClose = onClose, hidden = hidden) { _ ->
            // Receivers: everyone in the room, host selected by default.
            ReceiverRow(people, receiverId, copy) { receiverId = it }

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
                            Modifier.pressable(pressedScale = 0.96f, onClick = {
                                categoryId = c.id
                                selectedId = c.gifts.firstOrNull()?.id ?: 0L
                            }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            XaniText(
                                if (c.lucky) "🍀 " + c.title else c.title,
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

            // Grid
            val cat = catalog.categories.firstOrNull { it.id == categoryId }
            Box(Modifier.fillMaxWidth().height(rdp(268))) {
                when {
                    loading && catalog.categories.isEmpty() -> Centered(copy.t(Copy.LiveGiftLoading))
                    cat == null || cat.gifts.isEmpty() -> Centered(copy.t(Copy.LiveGiftEmpty))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = rdp(10), vertical = rdp(8)),
                        horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                        verticalArrangement = Arrangement.spacedBy(rdp(6)),
                    ) {
                        items(cat.gifts, key = { it.id }) { g ->
                            val luckyCell = cat.lucky || g.lucky
                            GiftCell(
                                g,
                                selected = g.id == selectedId,
                                lucky = luckyCell,
                                onTap = {
                                    // A lucky gift already selected: the cell is a send button too.
                                    if (selectedId == g.id && luckyCell && combo > 0) send() else { selectedId = g.id; combo = 0 }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(rdp(8)))

            // Bottom bar: coins (left) · Send / combo (right)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = rdp(14), vertical = rdp(10)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rdp(18)))
                        .background(Color.White.copy(alpha = 0.06f))
                        .pressable(pressedScale = 0.97f, onClick = { onRecharge?.invoke() })
                        .padding(horizontal = rdp(12), vertical = rdp(7)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rdp(6)),
                ) {
                    CoinGlyph(rdp(16))
                    XaniText(formatCoins(coins), Type.LabelBold, color = Color(0xFFFDE68A), fontSizeOverride = 14f)
                    if (onRecharge != null) {
                        XaniText(
                            copy.t(Copy.LiveGiftRecharge) + " ›",
                            Type.Caption,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSizeOverride = 12f,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selectedLucky && combo > 0 && selected != null && comboGift == selected.id) {
                    ComboButton(combo, { comboRing.value }, copy, onTap = ::send)
                } else {
                    Box(
                        Modifier
                            .height(rdp(40))
                            .clip(RoundedCornerShape(rdp(20)))
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                            .pressable(enabled = selected != null && receiverId != 0L, pressedScale = 0.94f, onClick = ::send)
                            .padding(horizontal = rdp(26)),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(copy.t(Copy.LiveGiftSend), Type.LabelBold, color = Color.White, fontSizeOverride = 14f)
                    }
                }
            }
    }
    // Sheet away, combo running: the send-again button floats over the room,
    // bottom-right, above the chrome, so the streak continues while the
    // gift plays full screen. It springs in and fades out with the sheet.
    val floating = hidden && selectedLucky && combo > 0 && selected != null && comboGift == selected.id
    val floatIn = remember { Animatable(0f) }
    LaunchedEffect(floating) {
        if (floating) floatIn.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f))
        else floatIn.animateTo(0f, tween(140))
    }
    if (floatIn.value > 0.01f) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = rdp(16), bottom = rdp(112))
                .graphicsLayer {
                    val s = 0.6f + 0.4f * floatIn.value
                    scaleX = s; scaleY = s; alpha = floatIn.value
                },
        ) {
            ComboButton(combo, { comboRing.value }, copy, onTap = { if (floating) send() })
        }
    }
    dialog?.let { d ->
        GiftModal(
            d, coins, copy,
            onDismiss = { dialog = null },
            onRecharge = onRecharge?.let { r -> { dialog = null; r() } },
            onRetry = { dialog = null; send() },
        )
    }
    }
}

/** How long the sheet waits for the lane to pick up the send, and how long it hides at most. */
private const val HIDDEN_MIN_MS = 350L
private const val HIDDEN_MAX_MS = 8_000L

/** What the sheet has to say when a send does not go through. */
private sealed interface GiftDialog {
    data class NotEnough(val gift: String, val price: Long) : GiftDialog
    data class Failed(val message: String, val retry: Boolean) : GiftDialog
}

/**
 * Centered card over the dimmed room, the way a refusal looks on any large
 * social app: a title, one sentence, and the actions that matter. Scales in
 * with a spring; the scrim fades with it. Taps outside dismiss.
 */
@Composable
private fun GiftModal(
    d: GiftDialog,
    coins: Long,
    copy: Copy,
    onDismiss: () -> Unit,
    onRecharge: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(d) {
        enter.snapTo(0f)
        enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * enter.value.coerceIn(0f, 1f))) }
            .pressable(pressedScale = 1f, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val s = 0.88f + 0.12f * enter.value.coerceIn(0f, 1f)
                    scaleX = s; scaleY = s
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(rdp(22)))
                .background(Brush.verticalGradient(listOf(Color(0xFF241A3D), Color(0xFF150F27))))
                .border(1.dp, Color(0x408B5CF6), RoundedCornerShape(rdp(22)))
                .pressable(pressedScale = 1f, onClick = {}) // swallow: taps on the card stay
                .padding(horizontal = rdp(20), vertical = rdp(22)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(rdp(56))
                    .clip(CircleShape)
                    .background(
                        when (d) {
                            is GiftDialog.NotEnough -> Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))
                            is GiftDialog.Failed -> Brush.linearGradient(listOf(Color(0xFFFCA5A5), Color(0xFFEF4444)))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(if (d is GiftDialog.NotEnough) "🪙" else "!", Type.LabelBold, color = Color.White, fontSizeOverride = 24f)
            }
            Spacer(Modifier.height(rdp(14)))
            XaniText(
                when (d) {
                    is GiftDialog.NotEnough -> copy.t(Copy.LiveGiftNotEnoughTitle)
                    is GiftDialog.Failed -> copy.t(Copy.LiveGiftFailedTitle)
                },
                Type.LabelBold, color = Color.White, fontSizeOverride = 17f,
            )
            Spacer(Modifier.height(rdp(8)))
            XaniText(
                when (d) {
                    is GiftDialog.NotEnough -> copy.t(
                        Copy.LiveGiftNotEnoughBody,
                        "price" to formatCoins(d.price),
                        "coins" to formatCoins(coins),
                        "gift" to d.gift.ifBlank { copy.t(Copy.LiveGiftsTitle).lowercase(Locale.getDefault()) },
                    )
                    is GiftDialog.Failed -> d.message
                },
                Type.Caption, color = Color.White.copy(alpha = 0.72f), fontSizeOverride = 13f,
                align = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(rdp(20)))
            val primary: (() -> Unit)? = when (d) {
                is GiftDialog.NotEnough -> onRecharge
                is GiftDialog.Failed -> if (d.retry) onRetry else null
            }
            if (primary != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rdp(44))
                        .clip(RoundedCornerShape(rdp(22)))
                        .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                        .pressable(pressedScale = 0.96f, onClick = primary),
                    contentAlignment = Alignment.Center,
                ) {
                    XaniText(
                        if (d is GiftDialog.NotEnough) copy.t(Copy.LiveGiftRecharge) else copy.t(Copy.LiveGiftRetry),
                        Type.LabelBold, color = Color.White, fontSizeOverride = 14f,
                    )
                }
                Spacer(Modifier.height(rdp(8)))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rdp(44))
                    .clip(RoundedCornerShape(rdp(22)))
                    .background(Color.White.copy(alpha = 0.06f))
                    .pressable(pressedScale = 0.96f, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                XaniText(copy.t(Copy.LiveGiftDismiss), Type.LabelBold, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 14f)
            }
        }
    }
}

@Composable
private fun ReceiverRow(people: List<LivePersonData>, receiverId: Long, copy: Copy, onPick: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // A vertical pull on the receivers row moves the sheet, not the list.
            .then(LocalLiveSheetDrag.current)
            .padding(top = rdp(12), start = rdp(14), end = rdp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(copy.t(Copy.LiveGiftSendTo), Type.Caption, color = Color.White.copy(alpha = 0.5f), fontSizeOverride = 12f)
        Spacer(Modifier.width(rdp(10)))
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(rdp(10)),
        ) {
            items(people, key = { it.id }) { p ->
                val on = p.id == receiverId
                val name = p.fullName.ifBlank { p.username }.ifBlank { if (p.role == "host") copy.t(Copy.LiveGiftHost) else "…" }
                val scale = remember { Animatable(1f) }
                LaunchedEffect(on) { scale.animateTo(if (on) 1.08f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                Box(
                    Modifier
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                        .size(rdp(46))
                        .clip(CircleShape)
                        .then(
                            if (on) Modifier.border(2.dp, Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), CircleShape)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        )
                        .padding(3.dp)
                        .pressable(pressedScale = 0.92f, onClick = { onPick(p.id) }),
                    contentAlignment = Alignment.Center,
                ) {
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

@Composable
private fun GiftCell(g: GiftData, selected: Boolean, lucky: Boolean, onTap: () -> Unit) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        scale.animateTo(if (selected) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val shape = RoundedCornerShape(rdp(14))
    Column(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(shape)
            .background(if (selected) Color(0x338B5CF6) else Color.White.copy(alpha = 0.035f))
            .then(
                if (selected) Modifier.border(1.5.dp, Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFF472B6))), shape)
                else Modifier,
            )
            .pressable(pressedScale = 0.95f, onClick = onTap)
            .padding(vertical = rdp(8), horizontal = rdp(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            RemoteImage(
                url = giftImageUrl(g.listingUrl),
                modifier = Modifier.fillMaxSize(0.82f),
                contentScale = ContentScale.Fit,
                maxEdge = 240,
                fallback = Color.Transparent,
            )
            if (lucky) {
                XaniText("🍀", Type.Caption, fontSizeOverride = 10f, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        XaniText(
            g.title,
            Type.Caption,
            color = Color.White.copy(alpha = 0.9f),
            fontSizeOverride = 11f,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = rdp(4)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(3))) {
            CoinGlyph(rdp(9))
            XaniText(formatCoins(g.coinPrice), Type.LabelBold, color = Color(0xFFFDE68A), fontSizeOverride = 11f)
        }
    }
}

/**
 * Send-again button: count in the middle, a ring that empties over 3 s.
 * [ring] is a lambda read inside the Canvas draw phase only, so the emptying
 * ring redraws one node per frame instead of recomposing the sheet — which
 * would stutter the gift grid for the whole streak.
 */
@Composable
private fun ComboButton(count: Int, ring: () -> Float, copy: Copy, onTap: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(count) {
        pop.snapTo(1.18f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Box(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .size(rdp(58))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899))))
            .pressable(pressedScale = 0.9f, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            )
            drawArc(
                color = Color.White,
                startAngle = -90f, sweepAngle = 360f * ring(), useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            XaniText("×$count", Type.LabelBold, color = Color.White, fontSizeOverride = 16f)
            XaniText(copy.t(Copy.LiveGiftCombo), Type.Caption, color = Color.White.copy(alpha = 0.85f), fontSizeOverride = 8f)
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        XaniText(text, Type.Caption, color = Color.White.copy(alpha = 0.55f), fontSizeOverride = 13f)
    }
}

@Composable
internal fun CoinGlyph(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))))
            .border(1.dp, Color(0x66FFFFFF), CircleShape),
    )
}

internal fun formatCoins(n: Long): String = when {
    n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1_000_000f)
    n >= 100_000L -> "${n / 1000}K"
    else -> String.format(Locale.US, "%,d", n)
}
