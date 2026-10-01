import SwiftUI
import WebKit

/// Wave Games full-mode catalog. Listing tiles come from the identity proxy of
/// Wave's admin list; a tap opens the game WebView with the signed-in access
/// token substituted for `{token}` so Wave's userInfo/submitFlow hit this app's
/// coin balance.
struct GamesView: View {
    var onImmersive: (Bool) -> Void = { _ in }
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.feedBottomReserve) private var reserve

    @State private var games: [GameCard] = []
    @State private var loading = true
    @State private var failed = false
    @State private var playing: GameCard?
    @State private var playURL: String = ""

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        Group {
            if playing != nil, !playURL.isEmpty {
                // A game owns the whole screen: full-bleed WebView, floating back.
                GameFullScreen(url: playURL) {
                    playing = nil
                    playURL = ""
                    Task { await refresh() }
                }
            } else {
                catalogBody
            }
        }
        .task { await refresh() }
        // Tell the shell to drop its bottom nav while a game is open.
        .onChange(of: playing) { onImmersive($0 != nil) }
    }

    private var catalogBody: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                GamesGlyph(on: true, side: 28 * scale)
                AppText(Copy.t(.gamesTitle), style: Type.labelBold, color: Dark.foreground, sizeOverride: 20)
                Spacer()
                Button(action: { Task { await refresh() } }) {
                    AppText(Copy.t(.gamesRefresh), style: Type.labelBold, color: Dark.primarySoft, sizeOverride: 12)
                        .padding(.horizontal, 12 * scale)
                        .padding(.vertical, 7 * scale)
                        .background(Dark.primary.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                }
                .buttonStyle(PressScaleStyle())
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 14 * scale)

            if loading && games.isEmpty {
                Spacer()
                AppText(Copy.t(.gamesLoading), style: Type.caption, color: Dark.dim)
                Spacer()
            } else if failed && games.isEmpty {
                Spacer()
                AppText(Copy.t(.gamesUnavailable), style: Type.caption, color: Dark.dim, alignment: .center)
                    .padding(.horizontal, 32 * scale)
                Button(action: { Task { await refresh() } }) {
                    AppText(Copy.t(.gamesRetry), style: Type.labelBold, color: Dark.primarySoft, sizeOverride: 13)
                        .padding(.horizontal, 18 * scale)
                        .padding(.vertical, 10 * scale)
                        .background(Dark.primary.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                }
                .buttonStyle(PressScaleStyle())
                .padding(.top, 12 * scale)
                Spacer()
            } else if games.isEmpty {
                Spacer()
                AppText(Copy.t(.gamesEmpty), style: Type.caption, color: Dark.dim, alignment: .center)
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 14 * scale) {
                        ForEach(games) { game in
                            GameTile(game: game) {
                                Task {
                                    guard let token = await Xani.session.accessToken(), !token.isEmpty else { return }
                                    playURL = game.openUrl(accessToken: token)
                                    playing = game
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 8 * scale)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Dark.background.ignoresSafeArea())
        .padding(.top, insets.top)
        .padding(.bottom, reserve)
    }

    private func refresh() async {
        loading = true
        failed = false
        if let page = await Xani.games.catalog().value {
            games = page.games
            failed = false
        } else {
            failed = games.isEmpty
        }
        loading = false
    }
}

private struct GameTile: View {
    let game: GameCard
    var onOpen: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 8 * scale) {
                ZStack {
                    Color(rgb: 0x1A1A2E)
                    if !game.listingImage.isEmpty {
                        RemoteImage(url: URL(string: game.listingImage), contentMode: .fill, fallback: Color(rgb: 0x1A1A2E))
                    } else {
                        GamesGlyph(on: true, side: 36 * scale)
                    }
                }
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))

                AppText(game.name, style: Type.labelBold, color: Dark.foreground, sizeOverride: 13, lineLimit: 2)
                    .padding(.horizontal, 2 * scale)
                    .padding(.bottom, 4 * scale)
            }
            .padding(8 * scale)
            .background(Color(rgb: 0x12121C))
            .overlay(
                RoundedRectangle(cornerRadius: 16 * scale, style: .continuous)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1 * scale)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.97))
    }
}

/// A small circular back button, white on a translucent scrim.
struct GameBackButton: View {
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    var body: some View {
        Button(action: onClose) {
            Text("←")
                .font(.system(size: 18 * scale, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 38 * scale, height: 38 * scale)
                .background(Color.black.opacity(0.45))
                .overlay(Circle().stroke(Color.white.opacity(0.16), lineWidth: 1))
                .clipShape(Circle())
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// Full-mode game: the WebView owns the whole screen, no top bar and no bottom
/// nav — only a floating back icon in the top-left corner.
struct GameFullScreen: View {
    let url: String
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            GameWebView(url: url)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea()
            GameBackButton(onClose: onClose)
                .padding(.leading, 12 * scale)
                .padding(.top, insets.top + 8 * scale)
        }
    }
}

/// Wave's suggested half-mode sheet height as a 0–1 fraction. Full-mode trays
/// report 100 — never use that over a live; keep the stream visible above.
private func halfSheetFraction(_ percent: Int) -> CGFloat {
    let p: Int
    switch percent {
    case 35...80: p = percent
    case 1...34: p = 40
    default: p = 55
    }
    return CGFloat(p) / 100
}

/// Half-mode game: a partial-height sheet layered over the live broadcast, so
/// the stream keeps playing behind it. Height follows Wave's half-tray %.
struct GameHalfSheet: View {
    let game: GameCard
    let url: String
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var slide: CGFloat = 1
    @State private var scrim: CGFloat = 0
    @State private var closing = false

    private var fraction: CGFloat { halfSheetFraction(game.sheetHeightPercent) }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.48 * Double(scrim))
                    .ignoresSafeArea()
                    .onTapGesture(perform: dismiss)

                VStack(spacing: 0) {
                    HStack(spacing: 10 * scale) {
                        GameBackButton(onClose: dismiss)
                        AppText(game.name, style: Type.labelBold, color: .white, sizeOverride: 14, lineLimit: 1)
                        Spacer()
                    }
                    .padding(.horizontal, 12 * scale)
                    .padding(.vertical, 8 * scale)
                    .frame(maxWidth: .infinity)
                    .background(Color(rgb: 0x12121C))

                    GameWebView(url: url)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    Color.clear.frame(height: insets.bottom)
                }
                .frame(height: geo.size.height * fraction)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
                .offset(y: geo.size.height * fraction * slide)
            }
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.spring(response: 0.38, dampingFraction: 0.92)) { slide = 0 }
            withAnimation(.easeOut(duration: 0.22)) { scrim = 1 }
        }
    }

    private func dismiss() {
        guard !closing else { return }
        closing = true
        withAnimation(.easeIn(duration: 0.22)) { slide = 1; scrim = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) { onClose() }
    }
}

struct GameWebView: UIViewRepresentable {
    let url: String

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let view = WKWebView(frame: .zero, configuration: config)
        view.isOpaque = false
        view.backgroundColor = .black
        view.scrollView.backgroundColor = .black
        if let u = URL(string: url) {
            view.load(URLRequest(url: u))
        }
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        guard let u = URL(string: url) else { return }
        if uiView.url?.absoluteString != url {
            uiView.load(URLRequest(url: u))
        }
    }
}

// MARK: - Half-mode games over a live broadcast

/// One picked game, resolved to a token-bearing URL.
struct ActiveGame: Equatable {
    let game: GameCard
    let url: String
}

/// Drives the half-mode games flow over a live surface: the catalog sheet and
/// the half-mode game sheet a broadcast raises from its games icon.
final class LiveGamesModel: ObservableObject {
    @Published var showSheet = false
    @Published var active: ActiveGame?
}

/// Catalog sheet + half-mode sheet for a live broadcast. Place at the top of the
/// broadcast ZStack; trigger it by setting `model.showSheet = true`.
struct LiveGamesOverlay: View {
    @ObservedObject var model: LiveGamesModel

    var body: some View {
        ZStack {
            if model.showSheet {
                GamesCatalogSheet(
                    onPick: { game in
                        Task {
                            model.showSheet = false
                            guard let token = await Xani.session.accessToken(), !token.isEmpty else { return }
                            model.active = ActiveGame(game: game, url: game.openUrl(accessToken: token))
                        }
                    },
                    onClose: { model.showSheet = false }
                )
            }
            if let active = model.active {
                GameHalfSheet(game: active.game, url: active.url) { model.active = nil }
            }
        }
    }
}

/// Bottom sheet that lists Wave's half-mode tray. Picking a title hands back
/// the game so the caller can open it half-height over a live.
struct GamesCatalogSheet: View {
    var onPick: (GameCard) -> Void
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var games: [GameCard] = []
    @State private var loading = true
    @State private var slide: CGFloat = 1
    @State private var scrim: CGFloat = 0
    @State private var closing = false

    private let columns = [
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10),
        GridItem(.flexible(), spacing: 10),
    ]

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.48 * Double(scrim))
                    .ignoresSafeArea()
                    .onTapGesture(perform: dismiss)

                VStack(spacing: 0) {
                    Capsule()
                        .fill(Color.white.opacity(0.18))
                        .frame(width: 38 * scale, height: 4 * scale)
                        .padding(.top, 10 * scale)
                    HStack(spacing: 8 * scale) {
                        GamesGlyph(on: true, side: 24 * scale)
                        AppText(Copy.t(.gamesTitle), style: Type.labelBold, color: .white, sizeOverride: 17)
                        Spacer()
                    }
                    .padding(.horizontal, 18 * scale)
                    .padding(.vertical, 12 * scale)

                    if loading && games.isEmpty {
                        Spacer()
                        AppText(Copy.t(.gamesLoading), style: Type.caption, color: Dark.dim)
                        Spacer()
                    } else if games.isEmpty {
                        Spacer()
                        AppText(Copy.t(.gamesEmpty), style: Type.caption, color: Dark.dim, alignment: .center)
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 12 * scale) {
                                ForEach(games) { game in
                                    GameSheetTile(game: game) { pick(game) }
                                }
                            }
                            .padding(.horizontal, 14 * scale)
                            .padding(.vertical, 4 * scale)
                        }
                    }
                    Color.clear.frame(height: insets.bottom)
                }
                .frame(height: geo.size.height * 0.55)
                .background(Color(rgb: 0x0C0C14))
                .clipShape(RoundedRectangle(cornerRadius: 22 * scale, style: .continuous))
                .offset(y: geo.size.height * 0.55 * slide)
            }
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.spring(response: 0.38, dampingFraction: 0.92)) { slide = 0 }
            withAnimation(.easeOut(duration: 0.22)) { scrim = 1 }
        }
        .task {
            let page = await Xani.games.catalog().value
            if let half = page?.halfModeGames, !half.isEmpty {
                games = half
            } else {
                games = (page?.games ?? []).map { $0.asHalfModeFallback() }
            }
            loading = false
        }
    }

    private func dismiss() {
        guard !closing else { return }
        closing = true
        withAnimation(.easeIn(duration: 0.22)) { slide = 1; scrim = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) { onClose() }
    }

    private func pick(_ game: GameCard) {
        guard !closing else { return }
        closing = true
        withAnimation(.easeIn(duration: 0.2)) { slide = 1; scrim = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) { onPick(game) }
    }
}

private extension GameCard {
    /// Rewrite a full-mode card so a live sheet can still open it half-height.
    func asHalfModeFallback() -> GameCard {
        var next = self
        var u = playUrl.replacingOccurrences(of: "embed=full", with: "embed=half")
        if !u.contains("embed=") {
            u += (u.contains("?") ? "&" : "?") + "embed=half"
        }
        next.playUrl = u
        if !(35...80).contains(sheetHeightPercent) {
            next.sheetHeightPercent = 55
        }
        return next
    }
}

private struct GameSheetTile: View {
    let game: GameCard
    var onOpen: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: onOpen) {
            VStack(spacing: 6 * scale) {
                ZStack {
                    Color(rgb: 0x1A1A2E)
                    if !game.listingImage.isEmpty {
                        RemoteImage(url: URL(string: game.listingImage), contentMode: .fill, fallback: Color(rgb: 0x1A1A2E))
                    } else {
                        GamesGlyph(on: true, side: 28 * scale)
                    }
                }
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))

                AppText(game.name, style: Type.label, color: .white, sizeOverride: 11, alignment: .center, lineLimit: 1)
                    .padding(.bottom, 2 * scale)
            }
            .padding(6 * scale)
            .background(Color(rgb: 0x15151F))
            .overlay(
                RoundedRectangle(cornerRadius: 14 * scale, style: .continuous)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1 * scale)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.97))
    }
}
