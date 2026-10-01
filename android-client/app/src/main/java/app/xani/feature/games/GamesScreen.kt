package app.xani.feature.games

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import app.xani.core.media.RemoteImage
import app.xani.core.net.GameCard
import app.xani.core.net.Xani
import app.xani.feature.main.GamesMark
import app.xani.feature.main.LocalFeedBottomReserve
import app.xani.i18n.Copy
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import kotlinx.coroutines.launch

/**
 * Wave Games full-mode catalog. Listing tiles come from the identity proxy of
 * Wave's admin list; a tap opens the game WebView with the signed-in access
 * token substituted for `{token}` so Wave's userInfo/submitFlow hit this app's
 * coin balance.
 */
@Composable
fun GamesScreen(copy: Copy, onImmersive: (Boolean) -> Unit = {}) {
    val bottom = LocalFeedBottomReserve.current
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf<List<GameCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<GameCard?>(null) }
    var playUrl by remember { mutableStateOf("") }

    // A game owns the whole screen: tell the shell to drop its bottom nav while
    // one is open, and restore it when the player closes the game.
    LaunchedEffect(playing, playUrl) { onImmersive(playing != null && playUrl.isNotEmpty()) }

    suspend fun refresh() {
        loading = true
        failed = false
        val page = Xani.games.catalog().value
        if (page != null) {
            games = page.games
            failed = false
        } else {
            failed = games.isEmpty()
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    if (playing != null && playUrl.isNotEmpty()) {
        GameFullScreen(
            url = playUrl,
            onClose = {
                playing = null
                playUrl = ""
                scope.launch { refresh() }
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dark.Background)
            .statusBarsPadding()
            .padding(bottom = bottom),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20), vertical = rdp(14)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GamesMark(size = rdp(28), tint = Dark.PrimarySoft)
            Spacer(Modifier.size(rdp(10)))
            XaniText(copy.t(Copy.GamesTitle), Type.LabelBold, color = Dark.Foreground, fontSizeOverride = 20f)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(rdp(10)))
                    .background(Dark.Primary.copy(alpha = 0.12f))
                    .pressable(onClick = { scope.launch { refresh() } })
                    .padding(horizontal = rdp(12), vertical = rdp(7)),
            ) {
                XaniText(copy.t(Copy.GamesRefresh), Type.LabelBold, color = Dark.PrimarySoft, fontSizeOverride = 12f)
            }
        }

        when {
            loading && games.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    XaniText(copy.t(Copy.GamesLoading), Type.Caption, color = Dark.Dim)
                }
            }
            failed && games.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = rdp(32)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    XaniText(copy.t(Copy.GamesUnavailable), Type.Caption, color = Dark.Dim, align = TextAlign.Center)
                    Spacer(Modifier.height(rdp(12)))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(rdp(12)))
                            .background(Dark.Primary.copy(alpha = 0.18f))
                            .pressable(onClick = { scope.launch { refresh() } })
                            .padding(horizontal = rdp(18), vertical = rdp(10)),
                    ) {
                        XaniText(copy.t(Copy.GamesRetry), Type.LabelBold, color = Dark.PrimarySoft, fontSizeOverride = 13f)
                    }
                }
            }
            games.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    XaniText(copy.t(Copy.GamesEmpty), Type.Caption, color = Dark.Dim, align = TextAlign.Center)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = rdp(14), vertical = rdp(8)),
                    horizontalArrangement = Arrangement.spacedBy(rdp(12)),
                    verticalArrangement = Arrangement.spacedBy(rdp(14)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(games, key = { it.gameId }) { game ->
                        GameTile(game = game, onOpen = {
                            scope.launch {
                                val token = Xani.session.accessToken().orEmpty()
                                if (token.isEmpty()) return@launch
                                playUrl = game.openUrl(token)
                                playing = game
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTile(game: GameCard, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rdp(16)))
            .background(Color(0xFF12121C))
            .border(rdp(1), Color.White.copy(alpha = 0.06f), RoundedCornerShape(rdp(16)))
            .pressable(pressedScale = 0.97f, onClick = onOpen)
            .padding(rdp(8)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(rdp(12)))
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center,
        ) {
            if (game.listingImage.isNotEmpty()) {
                RemoteImage(
                    url = game.listingImage,
                    modifier = Modifier.fillMaxSize(),
                    maxEdge = 512,
                    fallback = Color(0xFF1A1A2E),
                )
            } else {
                GamesMark(size = rdp(36), tint = Dark.PrimarySoft)
            }
        }
        XaniText(
            game.name,
            Type.LabelBold,
            color = Dark.Foreground,
            fontSizeOverride = 13f,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = rdp(8), start = rdp(2), end = rdp(2), bottom = rdp(4)),
        )
    }
}

