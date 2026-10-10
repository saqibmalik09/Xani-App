package app.xani.feature.games

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.media.RemoteImage
import app.xani.core.net.GameCard
import app.xani.core.net.Xani
import app.xani.feature.main.GamesMark
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

/** In-game back (wave-game-front `closeWaveGame`) asks native to dismiss via this URL. */
private const val WAVE_GAME_CLOSE_SCHEME = "xani-games"
private const val WAVE_GAME_CLOSE_HOST = "close"

/**
 * Shared Wave game surfaces: the WebView itself, an immersive full-screen host
 * (Games tab), and the half-mode pieces that layer a game over a live broadcast
 * — a catalog bottom sheet plus a partial-height game sheet.
 *
 * Every surface substitutes the signed-in access token for `{token}` so Wave's
 * userInfo / submitFlow settle against this app's coin balance, and none of
 * them show app chrome (top bar, bottom nav): a game owns its viewport, back is
 * the only affordance the host adds.
 */

/** The configured game WebView. One place owns the cookie/DOM/media flags. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GameWebView(
    url: String,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val closeLatest by rememberUpdatedState(onClose)
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(AndroidColor.BLACK)
                // Games call the tenant host from JS (userInfo / submitFlow).
                // Third-party cookies + DOM storage are required for that hop
                // and for the Wave socket bootstrap that follows.
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.userAgentString = settings.userAgentString + " XaniGames/1.0"
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val uri = request?.url ?: return false
                        if (
                            uri.scheme.equals(WAVE_GAME_CLOSE_SCHEME, ignoreCase = true) &&
                            uri.host.equals(WAVE_GAME_CLOSE_HOST, ignoreCase = true)
                        ) {
                            closeLatest()
                            return true
                        }
                        return false
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
        modifier = modifier,
    )
}

/** A small circular back button, white on a translucent scrim. */
@Composable
internal fun GameBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(rdp(38))
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(rdp(1), Color.White.copy(alpha = 0.16f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XaniText("←", Type.LabelBold, color = Color.White, fontSizeOverride = 18f)
    }
}

/**
 * Full-mode game: the WebView owns the whole screen, no top bar and no bottom
 * nav — only a floating back icon in the top-left corner.
 */
@Composable
internal fun GameFullScreen(url: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        GameWebView(
            url = url,
            onClose = onClose,
            modifier = Modifier.fillMaxSize(),
        )
        GameBackButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = rdp(12), top = rdp(8)),
        )
    }
}

/**
 * Wave's suggested half-mode sheet height as a 0–1 fraction of the screen.
 * Full-mode trays report 100 — never use that over a live; clamp to a band that
 * keeps the stream visible above the game.
 */
internal fun halfSheetFraction(percent: Int): Float {
    val p = when {
        percent in 35..80 -> percent
        percent in 1..34 -> 40
        else -> 55 // missing / 100 (full-mode) → honest half
    }
    return p / 100f
}

/**
 * Half-mode game: a partial-height sheet layered over the live broadcast, so
 * the stream keeps playing behind it. Height follows Wave's suggested percent
 * for the half tray. Slide-up + fade scrim; reverse on dismiss.
 */
@Composable
internal fun GameHalfSheet(game: GameCard, url: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    val scrim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val fraction = halfSheetFraction(game.sheetHeightPercent)

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            launch { scrim.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
            slide.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    BackHandler(onBack = ::dismiss)
    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        slide.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim.value * 0.48f }
                .background(Color.Black)
                .pressable(pressedScale = 1f, onClick = ::dismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .graphicsLayer { translationY = size.height * slide.value }
                .clip(RoundedCornerShape(topStart = rdp(20), topEnd = rdp(20)))
                .background(Color.Black),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12121C))
                    .padding(horizontal = rdp(12), vertical = rdp(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(10)),
            ) {
                GameBackButton(onClick = ::dismiss)
                XaniText(
                    game.name,
                    Type.LabelBold,
                    color = Color.White,
                    fontSizeOverride = 14f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                GameWebView(
                    url = url,
                    onClose = ::dismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Bottom sheet that lists Wave's half-mode tray for a live broadcast. Picking a
 * title hands back the game so the caller can open it half-height. Loads the
 * half tray on show (falls back to full list rewritten to embed=half).
 */
@Composable
internal fun GamesCatalogSheet(copy: Copy, onPick: (GameCard) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    val scrim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf<List<GameCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            launch { scrim.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
            slide.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            onClose()
        }
    }

    BackHandler(onBack = ::dismiss)
    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        slide.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        )
        val page = Xani.games.catalog().value
        games = when {
            page == null -> emptyList()
            page.halfModeGames.isNotEmpty() -> page.halfModeGames
            // Older identity builds only shipped the full tray — still open half.
            else -> page.games.map { it.asHalfModeFallback() }
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrim.value * 0.48f }
                .background(Color.Black)
                .pressable(pressedScale = 1f, onClick = ::dismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .graphicsLayer { translationY = size.height * slide.value }
                .clip(RoundedCornerShape(topStart = rdp(22), topEnd = rdp(22)))
                .background(Color(0xFF0C0C14))
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = rdp(10))
                    .size(width = rdp(38), height = rdp(4))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rdp(18), vertical = rdp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GamesMark(size = rdp(24), tint = Dark.PrimarySoft)
                Spacer(Modifier.size(rdp(8)))
                XaniText(copy.t(Copy.GamesTitle), Type.LabelBold, color = Color.White, fontSizeOverride = 17f)
            }
            when {
                loading && games.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    XaniText(copy.t(Copy.GamesLoading), Type.Caption, color = Dark.Dim)
                }
                games.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    XaniText(copy.t(Copy.GamesEmpty), Type.Caption, color = Dark.Dim, align = TextAlign.Center)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = rdp(14), vertical = rdp(4)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(10)),
                    verticalArrangement = Arrangement.spacedBy(rdp(12)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(games, key = { it.gameId }) { game ->
                        GameSheetTile(game = game, onOpen = {
                            if (closing) return@GameSheetTile
                            closing = true
                            scope.launch {
                                launch { scrim.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) }
                                slide.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                                onPick(game)
                            }
                        })
                    }
                }
            }
        }
    }
}

/** Rewrite a full-mode card so a live sheet can still open it half-height. */
private fun GameCard.asHalfModeFallback(): GameCard {
    val halfUrl = playUrl
        .replace("embed=full", "embed=half")
        .let { if (it.contains("embed=")) it else "$it${if (it.contains("?")) "&" else "?"}embed=half" }
    val h = sheetHeightPercent.takeIf { it in 35..80 } ?: 55
    return copy(playUrl = halfUrl, sheetHeightPercent = h)
}

@Composable
private fun GameSheetTile(game: GameCard, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(14)))
            .background(Color(0xFF15151F))
            .border(rdp(1), Color.White.copy(alpha = 0.06f), RoundedCornerShape(rdp(14)))
            .pressable(pressedScale = 0.97f, onClick = onOpen)
            .padding(rdp(6)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(rdp(10)))
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center,
        ) {
            if (game.listingImage.isNotEmpty()) {
                RemoteImage(
                    url = game.listingImage,
                    modifier = Modifier.fillMaxSize(),
                    maxEdge = 256,
                    fallback = Color(0xFF1A1A2E),
                )
            } else {
                GamesMark(size = rdp(28), tint = Dark.PrimarySoft)
            }
        }
        XaniText(
            game.name,
            Type.Label,
            color = Color.White,
            fontSizeOverride = 11f,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = rdp(6), bottom = rdp(2)),
        )
    }
}

/**
 * A self-contained launcher for half-mode games over a live surface: the games
 * icon the caller places, plus the catalog sheet and the half-mode sheet it
 * raises. Drop [LiveGamesOverlay] into a broadcast Box and trigger it with the
 * icon the host/viewer shares through [state].
 */
internal class LiveGamesState {
    var showSheet by mutableStateOf(false)
    var active by mutableStateOf<Pair<GameCard, String>?>(null)
}

@Composable
internal fun rememberLiveGamesState(): LiveGamesState = remember { LiveGamesState() }

/** Catalog sheet + half-mode sheet for a live broadcast. Place at the top of the Box. */
@Composable
internal fun LiveGamesOverlay(state: LiveGamesState, copy: Copy) {
    val scope = rememberCoroutineScope()
    if (state.showSheet) {
        GamesCatalogSheet(
            copy = copy,
            onPick = { game ->
                scope.launch {
                    val token = Xani.session.accessToken().orEmpty()
                    state.showSheet = false
                    if (token.isEmpty()) return@launch
                    state.active = game to game.openUrl(token)
                }
            },
            onClose = { state.showSheet = false },
        )
    }
    state.active?.let { (game, url) ->
        GameHalfSheet(game = game, url = url, onClose = { state.active = null })
    }
}
