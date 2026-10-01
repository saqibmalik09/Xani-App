package app.xani.feature.gift

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.media.RemoteImage
import app.xani.core.net.GiftData
import app.xani.core.net.LiveGiftEvent
import app.xani.core.net.LivePersonData
import app.xani.core.net.MediaConfig
import app.xani.feature.call.LiveSession
import app.xani.feature.social.LevelMark
import app.xani.feature.social.PersonAvatar
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gifts landing on the broadcast, for host and viewers alike.
 *
 * Two surfaces, both bounded:
 *   - Banners (top-left, two slots): "A sent Rose to B ×12". The same sender
 *     repeating the same gift updates one banner's counter instead of stacking;
 *     the oldest slot yields to a new pair. A slot lingers 3 s after its last hit.
 *   - The animation lane (full screen, one at a time): a queue of at most
 *     [QUEUE_CAP] events, coalesced by sender+gift so a lucky streak plays one
 *     animation with a counter instead of thirty. When the queue is full the
 *     oldest waiting gift is dropped — its banner has already been seen, and a
 *     4 000-viewer room would rather stay at 60 fps than replay history.
 *
 * Playback never waits on the network: an SVGA not decoded within 1.4 s is
 * shown as its listing image with a pop, so every viewer sees the gift at the
 * same moment; the decode continues and the next send of it animates. The
 * sender's own gift renders from the optimistic frame the instant they tap,
 * and the server's echo (same clientId) is recognised and skipped.
 */
@Stable
internal class GiftOverlayState(private val scope: CoroutineScope) {
    class Banner(
        val key: Long,
        val sender: LivePersonData,
        val receiver: LivePersonData,
        val gift: GiftData,
        count: Int,
    ) {
        var count by mutableStateOf(count)
        var bump by mutableStateOf(0)
        var lastAt = System.currentTimeMillis()
        var expiry: Job? = null
    }

    class Playing(val ev: LiveGiftEvent, val movie: SvgaMovie?, val lottie: LottieComposition? = null) {
        var repeats by mutableStateOf(1)
    }

    val banners = mutableStateListOf<Banner>()
    var playing by mutableStateOf<Playing?>(null)
        private set

    /** The one animation surface; drawn by [GiftOverlay]. */
    internal val player = SvgaPlayerState()

    private val queue = ArrayDeque<LiveGiftEvent>(QUEUE_CAP)
    private val queueRepeats = HashMap<Long, Int>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val seenClientIds = LinkedHashSet<String>()

    fun onEvent(ev: LiveGiftEvent) {
        if (ev.gift.id == 0L) return
        // The sender's own echo: the optimistic frame already played.
        if (ev.clientId.isNotEmpty()) {
            if (!seenClientIds.add(ev.clientId)) return
            if (seenClientIds.size > 256) {
                val it = seenClientIds.iterator(); it.next(); it.remove()
            }
        }
        // Warm what the next repeat (or the next viewer) will need.
        if (ev.gift.isSvga) SvgaLoader.prefetch(ev.gift.animationUrl)
        if (ev.gift.isLottie) LottieLoader.prefetch(giftImageUrl(ev.gift.animationUrl))
        if (ev.gift.isAnimatedImage) app.xani.core.media.AssetCache.prefetch(giftImageUrl(ev.gift.animationUrl))
        if (ev.gift.soundUrl.isNotEmpty()) GiftSound.prefetch(ev.gift.soundUrl)
        banner(ev)
        enqueue(ev)
    }

    private fun banner(ev: LiveGiftEvent) {
        val key = (ev.sender.id shl 20) xor ev.gift.id
        val hit = ev.count.coerceAtLeast(1)
        val existing = banners.firstOrNull { it.key == key }
        val b = if (existing != null) {
            existing.count = if (ev.combo > existing.count) ev.combo else existing.count + hit
            existing.bump += 1
            existing.lastAt = System.currentTimeMillis()
            existing
        } else {
            if (banners.size >= BANNER_SLOTS) {
                val oldest = banners.minByOrNull { it.lastAt } ?: banners[0]
                oldest.expiry?.cancel()
                banners.remove(oldest)
            }
            Banner(key, ev.sender, ev.receiver, ev.gift, if (ev.combo > 1) ev.combo else hit).also { banners.add(it) }
        }
        b.expiry?.cancel()
        b.expiry = scope.launch {
            delay(BANNER_MS)
            banners.remove(b)
        }
    }

    private fun enqueue(ev: LiveGiftEvent) {
        val key = (ev.sender.id shl 20) xor ev.gift.id
        val cur = playing
        if (cur != null && (cur.ev.sender.id shl 20) xor cur.ev.gift.id == key) {
            cur.repeats += 1
            return
        }
        val tail = queue.lastOrNull()
        if (tail != null && (tail.sender.id shl 20) xor tail.gift.id == key) {
            queueRepeats[key] = (queueRepeats[key] ?: 1) + 1
            return
        }
        if (queue.size >= QUEUE_CAP) {
            val dropped = queue.removeFirst()
            queueRepeats.remove((dropped.sender.id shl 20) xor dropped.gift.id)
        }
        queue.addLast(ev)
        wake.trySend(Unit)
    }

    /** The lane: one animation at a time, forever, until the scope dies. */
    suspend fun run() {
        while (true) {
            val ev = queue.removeFirstOrNull()
            if (ev == null) { wake.receive(); continue }
            val key = (ev.sender.id shl 20) xor ev.gift.id
            val repeats = queueRepeats.remove(key) ?: 1
            play(ev, repeats)
        }
    }

    private suspend fun play(ev: LiveGiftEvent, repeats: Int) {
        val gift = ev.gift
        val movie = if (gift.isSvga) {
            // Already decoded (prefetched on selection / on the banner) costs
            // nothing; a first-ever play waits a short beat for the decode so
            // the room sees the animation, not its poster.
            SvgaLoader.cached(gift.animationUrl)
                ?: withTimeoutOrNull(READY_MS) { SvgaLoader.load(gift.animationUrl) }
        } else null
        // Lottie takes the same beat: parsed once, then every play is instant.
        // Any animation that is not SVGA and not a plain image gets the try —
        // a JSON uploaded before the mime fix has a generic type in the
        // catalog, and the decode (cached, failure-marked) is the real test.
        val lottie = if (gift.isLottie || (movie == null && !gift.isSvga && !gift.isStill)) {
            val url = giftImageUrl(gift.animationUrl)
            LottieLoader.cached(url) ?: withTimeoutOrNull(READY_MS) { LottieLoader.load(url) }
        } else null
        val p = Playing(ev, movie, lottie).also { it.repeats = repeats }
        playing = p
        val hold = when {
            gift.durationMs > 0 -> gift.durationMs.toLong()
            movie != null -> movie.durationMs
            lottie != null -> lottie.duration.toLong()
            gift.isLottie -> LOTTIE_MS
            // Animated WebP/GIF: the decoder owns frame timing, we own the hold.
            gift.isAnimatedImage -> LOTTIE_MS
            else -> STILL_MS
        }.coerceIn(MIN_MS, MAX_MS)
        if (gift.soundUrl.isNotEmpty()) GiftSound.play(gift.soundUrl, hold)
        if (movie != null) {
            val loops = if (movie.durationMs <= 0L) 1 else ((hold + movie.durationMs / 2) / movie.durationMs).toInt().coerceAtLeast(1)
            withTimeoutOrNull(hold + 800) { player.play(movie, loops) }
            player.stop()
        } else if (gift.isSvga && lottie == null) {
            // The still is a placeholder, not the performance: the decode is
            // still running, and if it lands while this gift holds the lane
            // the animation takes over mid-hold instead of waiting for the
            // next send of the same gift.
            val started = System.currentTimeMillis()
            val late = withTimeoutOrNull(hold) { SvgaLoader.load(gift.animationUrl) }
            if (late != null) {
                playing = Playing(ev, late).also { it.repeats = p.repeats }
                val left = (hold - (System.currentTimeMillis() - started)).coerceAtLeast(late.durationMs).coerceAtMost(MAX_MS)
                val loops = if (late.durationMs <= 0L) 1 else ((left + late.durationMs / 2) / late.durationMs).toInt().coerceAtLeast(1)
                withTimeoutOrNull(left + 800) { player.play(late, loops) }
                player.stop()
            } else {
                val left = hold - (System.currentTimeMillis() - started)
                if (left > 0) delay(left)
            }
        } else {
            delay(hold)
        }
        playing = null
        delay(GAP_MS)
    }

    fun clear() {
        banners.forEach { it.expiry?.cancel() }
        banners.clear()
        queue.clear()
        queueRepeats.clear()
        player.stop()
        playing = null
        GiftSound.stop()
    }

    private companion object {
        const val QUEUE_CAP = 30
        const val BANNER_SLOTS = 2
        const val BANNER_MS = 3_000L
        const val READY_MS = 1_400L
        const val STILL_MS = 1_800L
        const val LOTTIE_MS = 3_000L
        const val MIN_MS = 900L
        const val MAX_MS = 12_000L
        const val GAP_MS = 120L
    }
}

/** Catalog URLs are absolute; a bare storage key falls back to the media base. */
internal fun giftImageUrl(s: String): String =
    if (s.isEmpty()) "" else if (s.startsWith("http")) s else MediaConfig.url(s)

@Composable
internal fun rememberGiftOverlayState(roomId: Long): GiftOverlayState {
    val scope = rememberCoroutineScope()
    val state = remember(roomId) { GiftOverlayState(scope) }
    LaunchedEffect(state) {
        launch { LiveSession.gifts.collect { if (it.roomId == roomId || roomId == 0L) state.onEvent(it) } }
        state.run()
    }
    DisposableEffect(state) {
        onDispose {
            state.clear()
            GiftSound.release()
        }
    }
    return state
}

/** Draws over the broadcast; place last-but-one in the room's Box (under dialogs). */
@Composable
internal fun GiftOverlay(state: GiftOverlayState, copy: Copy, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        SvgaLoader.screenWidthPx = ctx.resources.displayMetrics.widthPixels
        // Sprites decode on a background thread while the first gift is still
        // in flight; the surface itself costs nothing until a movie is set.
    }
    // Nothing in this Box takes pointer input: every tap falls through to the
    // broadcast chrome beneath, even while a gift is playing.
    Box(modifier.fillMaxSize()) {
        SvgaPlayer(state.player, Modifier.fillMaxSize())
        val p = state.playing
        if (p != null && p.movie == null) {
            val c = p.lottie
            when {
                c != null -> LottieGift(p, c)
                p.ev.gift.isAnimatedImage -> AnimatedGift(p)
                else -> StillGift(p)
            }
        }
        if (p != null && p.repeats > 1) ComboBadge(p.repeats, Modifier.align(Alignment.CenterEnd).padding(end = rdp(18)))

        Column(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = rdp(150), start = rdp(12))
                .fillMaxWidth(0.72f),
            verticalArrangement = Arrangement.spacedBy(rdp(8)),
        ) {
            for (b in state.banners) {
                androidx.compose.runtime.key(b.key) { GiftBanner(b, copy) }
            }
        }
    }
}

/**
 * A gift uploaded as Lottie JSON, full screen, looping for its hold. The
 * composition was decoded by [LottieLoader] before this composable exists
 * (see [GiftOverlayState.play]), so the first frame is the animation itself.
 */
@Composable
private fun LottieGift(p: GiftOverlayState.Playing, c: LottieComposition) {
    val enter = remember(p) { Animatable(0f) }
    LaunchedEffect(p) { enter.animateTo(1f, tween(140)) }
    val progress by animateLottieCompositionAsState(c, iterations = LottieConstants.IterateForever)
    LottieAnimation(
        composition = c,
        progress = { progress },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .padding(rdp(24)),
    )
}

/**
 * A gift uploaded as animated WebP / GIF / APNG, full screen with the same
 * pop-in as the still. The platform decoder loops it for the hold; a file that
 * turns out to be a plain still draws once — same surface either way.
 */
@Composable
private fun AnimatedGift(p: GiftOverlayState.Playing) {
    val enter = remember(p) { Animatable(0f) }
    LaunchedEffect(p) { enter.animateTo(1f, tween(140)) }
    app.xani.core.media.AnimatedRemoteImage(
        url = giftImageUrl(p.ev.gift.animationUrl),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .padding(rdp(24)),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        maxEdge = 1080,
    )
}

/** The listing image with a pop: what plays when there is no animation (yet). */
@Composable
private fun StillGift(p: GiftOverlayState.Playing) {
    val scale = remember(p) { Animatable(0.4f) }
    val alpha = remember(p) { Animatable(0f) }
    LaunchedEffect(p) {
        launch { alpha.animateTo(1f, tween(160)) }
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value },
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = giftImageUrl(p.ev.gift.listingUrl),
            modifier = Modifier.size(rdp(180)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            maxEdge = 480,
            fallback = Color.Transparent,
        )
    }
}

@Composable
private fun ComboBadge(n: Int, modifier: Modifier) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(n) {
        pop.snapTo(1.35f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Box(
        modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    ) {
        XaniText(
            "×$n",
            Type.LabelBold,
            brush = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
            fontSizeOverride = 44f,
        )
    }
}

@Composable
private fun GiftBanner(b: GiftOverlayState.Banner, copy: Copy) {
    val slide = remember(b) { Animatable(-1f) }
    val pop = remember(b) { Animatable(1f) }
    LaunchedEffect(b) { slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
    LaunchedEffect(b.bump) {
        if (b.bump > 0) {
            pop.snapTo(1.3f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val senderName = b.sender.fullName.ifBlank { b.sender.username }.ifBlank { "…" }
    val toName = when {
        b.receiver.role == "host" -> copy.t(Copy.LiveGiftHost)
        b.receiver.fullName.isNotBlank() -> b.receiver.fullName
        b.receiver.username.isNotBlank() -> "@" + b.receiver.username
        else -> copy.t(Copy.LiveGiftHost)
    }
    Row(
        Modifier
            .graphicsLayer { translationX = slide.value * size.width; alpha = 1f + slide.value * 0.6f }
            .clip(RoundedCornerShape(rdp(22)))
            .background(Brush.horizontalGradient(listOf(Color(0xCC7C3AED), Color(0x66EC4899), Color(0x0007070F))))
            .padding(start = rdp(4), end = rdp(14), top = rdp(4), bottom = rdp(4)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rdp(8)),
    ) {
        PersonAvatar(b.sender.id, senderName, b.sender.avatarUrl, rdp(34), frameUrl = b.sender.frameUrl, frameMime = b.sender.frameMime)
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(rdp(5))) {
                XaniText(
                    senderName, Type.LabelBold, color = Color.White, fontSizeOverride = 12f, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                // The sender's gifting level: this gift may be the one that lifted it.
                LevelMark(b.sender.senderLevel, rdp(14))
            }
            XaniText(
                copy.t(Copy.LiveGiftSentLine, "gift" to b.gift.title, "to" to toName),
                Type.Caption,
                color = Color.White.copy(alpha = 0.85f),
                fontSizeOverride = 11f,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RemoteImage(
            url = giftImageUrl(b.gift.listingUrl),
            modifier = Modifier.size(rdp(36)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            maxEdge = 160,
            fallback = Color.Transparent,
        )
        if (b.count > 1) {
            XaniText(
                "×${b.count}",
                Type.LabelBold,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                brush = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
                fontSizeOverride = 20f,
            )
        }
    }
}
