import Foundation

/// One full-mode game from the Wave catalog, ready to open in a WebView.
struct GameCard: Decodable, Equatable, Identifiable {
    var id: Int64 = 0
    var gameId: Int64 = 0
    var name: String = ""
    var routeSlug: String = ""
    var listingImage: String = ""
    /// Full-mode URL; still contains the literal `{token}` placeholder.
    var playUrl: String = ""
    var sheetHeightPercent: Int = 100

    /// Prefer Wave's gameId when present so list keys stay stable across refreshes.
    var identity: Int64 { gameId > 0 ? gameId : id }

    /// Replace `{token}` with a URL-encoded access token for the game WebView.
    func openUrl(accessToken: String) -> String {
        let t = accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !playUrl.isEmpty else { return playUrl }
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        let encoded = t.addingPercentEncoding(withAllowedCharacters: allowed) ?? t
        return playUrl.replacingOccurrences(of: "{token}", with: encoded)
    }
}

struct GamesCatalog: Decodable, Equatable {
    var appKey: String = ""
    var frontendBaseUrl: String = ""
    /// Full-mode tray — Games tab opens these fullscreen.
    var games: [GameCard] = []
    /// Half-mode tray — live broadcast opens these in a partial-height sheet.
    var halfModeGames: [GameCard] = []
}

private struct CatalogEnvelope: Decodable {
    var catalog: GamesCatalog
}

/// Typed Games endpoints bound to the live session.
struct GamesApi {
    let api: ApiClient

    /// Full-mode catalog Wave lists for this tenant — refreshed on every open.
    func catalog() async -> ApiResponse<GamesCatalog> {
        await api.get("/v1/games/catalog", authenticated: true, as: CatalogEnvelope.self).map(\.catalog)
    }
}
