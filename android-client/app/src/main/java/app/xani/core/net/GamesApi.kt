package app.xani.core.net

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** One full-mode game from the Wave catalog, ready to open in a WebView. */
@Immutable
data class GameCard(
    val id: Long,
    val gameId: Long,
    val name: String,
    val routeSlug: String,
    val listingImage: String,
    /** Full-mode URL; still contains the literal `{token}` placeholder. */
    val playUrl: String,
    val sheetHeightPercent: Int,
) {
    companion object {
        fun from(json: JSONObject) = GameCard(
            id = json.long("id"),
            gameId = json.long("game_id").takeIf { it > 0 } ?: json.long("id"),
            name = json.str("name"),
            routeSlug = json.str("route_slug"),
            listingImage = json.str("listing_image"),
            playUrl = json.str("play_url"),
            sheetHeightPercent = json.int("sheet_height_percent", 100),
        )
    }

    /** Replace `{token}` with a URL-encoded access token for the game WebView. */
    fun openUrl(accessToken: String): String {
        val t = accessToken.trim()
        if (t.isEmpty() || playUrl.isEmpty()) return playUrl
        val encoded = java.net.URLEncoder.encode(t, Charsets.UTF_8.name())
            .replace("+", "%20")
        return playUrl.replace("{token}", encoded)
    }
}

@Immutable
data class GamesCatalog(
    val appKey: String,
    val frontendBaseUrl: String,
    /** Full-mode tray — Games tab opens these fullscreen. */
    val games: List<GameCard>,
    /** Half-mode tray — live broadcast opens these in a partial-height sheet. */
    val halfModeGames: List<GameCard>,
) {
    companion object {
        fun from(json: JSONObject) = GamesCatalog(
            appKey = json.str("app_key"),
            frontendBaseUrl = json.str("frontend_base_url"),
            games = json.objects("games").map(GameCard::from),
            halfModeGames = json.objects("half_mode_games").map(GameCard::from),
        )
    }
}

/** Typed Games endpoints bound to the live session. */
class GamesApi internal constructor(private val api: ApiClient) {
    /** Full-mode catalog Wave lists for this tenant — refreshed on every open. */
    suspend fun catalog(): ApiResponse<GamesCatalog> = api.get(
        "/v1/games/catalog",
        emptyMap(),
        authenticated = true,
    ) { GamesCatalog.from(it.obj("catalog") ?: JSONObject()) }
}
