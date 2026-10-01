package app.xani.feature.social

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.xani.core.media.AnimatedRemoteImage
import app.xani.core.net.LevelData
import app.xani.core.net.MediaConfig
import app.xani.feature.gift.LottieLoader
import app.xani.feature.gift.SvgaLoader
import app.xani.feature.gift.SvgaPlayer
import app.xani.feature.gift.SvgaPlayerState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

/**
 * Who is wearing which avatar frame (and name badge), by user id — the one map
 * every avatar on every surface reads. Payloads that carry frame fields (people
 * lists, live rosters, profile reads, a purchase ack) teach it; an avatar that
 * only knows the user id still draws the right ring. Values are storage keys or
 * absolute URLs, resolved at draw time like avatars are.
 *
 * The maps persist across process death: the ring on your own profile header
 * draws on the first frame after a cold start, not seconds later when the
 * network read lands.
 */
object FrameStore {
    data class Frame(val url: String, val mime: String)

    private val _frames = MutableStateFlow<Map<Long, Frame>>(emptyMap())
    val frames: StateFlow<Map<Long, Frame>> = _frames.asStateFlow()

    /** Badges worn next to the name, same contract as frames. */
    private val _badges = MutableStateFlow<Map<Long, Frame>>(emptyMap())
    val badges: StateFlow<Map<Long, Frame>> = _badges.asStateFlow()

    private var prefs: SharedPreferences? = null

    /** One cheap SharedPreferences read at process start; safe to call twice. */
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("xani.worn", Context.MODE_PRIVATE)
        prefs = p
        // What the network taught this session already wins over the disk copy.
        _frames.update { fresh -> load(p, "frames") + fresh }
        _badges.update { fresh -> load(p, "badges") + fresh }
    }

    /**
     * Record what [id] is wearing. An empty [url] means "none" and clears a
     * stale ring — an expiry or a swap seen by any surface updates all of them.
     */
    fun learn(id: Long, url: String, mime: String) {
        if (id == 0L) return
        val cur = _frames.value[id]
        if (cur != null && cur.url == url && cur.mime == mime) return
        _frames.update { it + (id to Frame(url, mime)) }
        save("frames", _frames.value)
    }

    /** Record the badge [id] wears next to their name; empty [url] clears it. */
    fun learnBadge(id: Long, url: String, mime: String) {
        if (id == 0L) return
        val cur = _badges.value[id]
        if (cur != null && cur.url == url && cur.mime == mime) return
        _badges.update { it + (id to Frame(url, mime)) }
        save("badges", _badges.value)
    }

    fun signedOut() {
        _frames.value = emptyMap()
        _badges.value = emptyMap()
        prefs?.edit()?.clear()?.apply()
    }

    /** Flat [id, url, mime, …] triples; bounded so the file never grows past a page. */
    private fun save(key: String, map: Map<Long, Frame>) {
        val p = prefs ?: return
        val arr = JSONArray()
        var left = 64
        for ((id, f) in map) {
            if (left-- <= 0) break
            arr.put(id.toString()).put(f.url).put(f.mime)
        }
        p.edit().putString(key, arr.toString()).apply()
    }

    private fun load(p: SharedPreferences, key: String): Map<Long, Frame> = runCatching {
        val arr = JSONArray(p.getString(key, "") ?: "")
        val out = HashMap<Long, Frame>(arr.length() / 3)
        var i = 0
        while (i + 2 < arr.length()) {
            val id = arr.optString(i).toLongOrNull() ?: 0L
            if (id != 0L) out[id] = Frame(arr.optString(i + 1), arr.optString(i + 2))
            i += 3
        }
        out as Map<Long, Frame>
    }.getOrDefault(emptyMap())
}

/** Frames are drawn a third larger than the avatar so the ring wraps it. */
private const val FRAME_SCALE = 1.34f

/**
 * The ring itself, centered on (and overflowing) an avatar of [size]. Static
 * images draw at once; SVGA loops on the gift player (draw-phase only, no
 * recomposition per frame); Lottie loops on its own runtime. All three come
 * through the same caches the gift pipeline warms, so a frame seen once is
 * free everywhere after.
 */
@Composable
fun AvatarFrame(url: String, mime: String, size: Dp) {
    if (url.isEmpty()) return
    AssetAnimation(url, mime, Modifier.requiredSize(size * FRAME_SCALE))
}

/**
 * A badge worn next to the name — same animation stack as the frame ring,
 * boxed to a small square so a lavish SVGA cannot shove the name aside.
 */
@Composable
fun NameBadge(url: String, mime: String, size: Dp) {
    if (url.isEmpty()) return
    AssetAnimation(url, mime, Modifier.requiredSize(size))
}

/**
 * The gifting-level picture next to a name: the rung's still or animated
 * asset in a small box. Draws nothing for `null` — a person below the first
 * rung, or a ladder the dashboard has not set up — so no surface has to ask.
 * Level pictures are wider than tall on most ladders; the box keeps the
 * height and lets the width follow (capped at two heights).
 */
@Composable
fun LevelMark(level: LevelData?, height: Dp) {
    if (level == null || level.assetUrl.isEmpty()) return
    AssetAnimation(
        level.assetUrl,
        level.assetMime,
        Modifier
            .requiredHeight(height)
            .widthIn(min = height, max = height * 2),
        maxEdge = 160,
    )
}

/**
 * One store asset playing forever inside [modifier]'s bounds: SVGA on the
 * gift player (draw-phase only, no recomposition per frame), Lottie on its
 * own runtime, animated WebP / GIF / APNG on the platform decoder, and a
 * plain still draws once. Frames, badges, and entrance effects all come
 * through here, so every surface shares the same caches and quirks.
 */
@Composable
fun AssetAnimation(url: String, mime: String, modifier: Modifier, maxEdge: Int = 480) {
    if (url.isEmpty()) return
    val resolved = if (url.startsWith("http")) url else MediaConfig.url(url)
    if (resolved.isEmpty()) return
    val isSvga = mime.contains("svga") || url.endsWith(".svga", ignoreCase = true)
    val isLottie = !isSvga &&
        (mime == "application/json" || mime.contains("lottie") || url.endsWith(".json", ignoreCase = true))
    when {
        isSvga -> {
            val state = remember { SvgaPlayerState() }
            LaunchedEffect(resolved) {
                val movie = SvgaLoader.load(resolved) ?: return@LaunchedEffect
                state.play(movie, loops = 0) // until this surface leaves the screen
            }
            SvgaPlayer(state, modifier)
        }
        isLottie -> {
            var comp by remember(resolved) { mutableStateOf(LottieLoader.cached(resolved)) }
            if (comp == null) {
                LaunchedEffect(resolved) { comp = LottieLoader.load(resolved) }
            }
            comp?.let { c ->
                val progress by animateLottieCompositionAsState(c, iterations = LottieConstants.IterateForever)
                LottieAnimation(c, { progress }, modifier = modifier)
            }
        }
        else -> AnimatedRemoteImage(url = resolved, modifier = modifier, maxEdge = maxEdge, fallback = Color.Transparent)
    }
}
