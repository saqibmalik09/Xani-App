import Foundation

/// Obtains a Google ID token from the device.
///
/// A protocol because the token can only come from Google's own SDK (Google Sign-In
/// for iOS), and this project ships no third-party packages by policy. The rest of the
/// Google flow — the endpoint, the request, session adoption, account linking — is
/// complete; only this one step is pluggable.
///
/// To turn Google sign-in on, set `GOOGLE_IOS_CLIENT_ID` (and matching
/// `GOOGLE_IOS_URL_SCHEME`) in Brand.xcconfig / brand.env. `WebGoogleSignIn` is
/// installed at `start()`; when the id is empty the button soft-fails. Put the
/// same iOS client id in the identity service `GOOGLE_CLIENT_IDS` (alongside the
/// Android web client id).
@MainActor
protocol GoogleIdTokenProvider {
    /// Returns the ID token, or nil when the user cancelled. The implementation finds
    /// its own presenting controller — `GIDSignIn` does this from the key window, and
    /// threading one through every call site buys nothing.
    func idToken() async -> String?

    /// False when this build cannot do Google sign-in, so the UI hides the button.
    var available: Bool { get }
}

struct NoGoogleSignIn: GoogleIdTokenProvider {
    func idToken() async -> String? { nil }
    var available: Bool { false }
}

/// The app's single entry point to the network layer.
///
/// An enum with static state rather than a DI container: there are exactly three
/// long-lived collaborators, they are all created at launch, and a container would add
/// indirection and cold-start cost for nothing.
@MainActor
enum Xani {
    private(set) static var api = ApiClient(baseURL: ApiConfig.baseURL, userAgent: ApiConfig.userAgent)
    private(set) static var session = SessionManager(api: api)
    private(set) static var google: GoogleIdTokenProvider = NoGoogleSignIn()

    static var googleAvailable: Bool { google.available }

    /// The stable per-install device key the server enforces limits against.
    static var deviceKey: String { DeviceIdentity.key }

    /// Typed identity endpoints.
    static var auth: AuthApi { AuthApi(api: api) }

    /// Typed profile + wallet endpoints.
    static var profile: ProfileApi { ProfileApi(api: api) }

    /// Typed media upload + post endpoints.
    static var media: MediaApi { MediaApi(api: api) }

    /// Typed people, search and follow-graph endpoints.
    static var people: PeopleApi { PeopleApi(api: api) }

    /// Typed chat endpoints.
    static var chat: ChatApi { ChatApi(api: api) }

    /// Typed Wave Games catalog.
    static var games: GamesApi { GamesApi(api: api) }

    /// Typed call and live signalling endpoints.
    static var calls: CallApi { CallApi(api: api) }

    /// Typed live gift endpoints (catalog, send, room stream).
    static var gifts: GiftApi { GiftApi(api: api) }

    /// Gifting engine: rule-aware catalog tree and per-user statements.
    static var gifting: GiftingApi { GiftingApi(api: api) }

    /// Public promotional banners by placement.
    static var banners: BannerApi { BannerApi(api: api) }

    /// Help Centre articles and Report-a-problem tickets.
    static var support: SupportApi { SupportApi(api: api) }

    /// Organisation roles: memberships, upline/downline, referral codes, requests.
    static var roles: RolesApi { RolesApi(api: api) }

    /// Live-time rewards: summary, history, eligibility before going live, claims.
    static var rewards: RewardsApi { RewardsApi(api: api) }

    /// The signed-in account's id; 0 when signed out.
    static var currentUserId: Int64 {
        if case .signedIn(let user, _) = session.state { return Int64(user.id) ?? 0 }
        return 0
    }

    private static var started = false

    /// Binds the transport to the session and restores any persisted login.
    ///
    /// `nonisolated` so `App.init` can call it without being main-actor itself; the work
    /// hops to the main actor and is never awaited, because nothing may sit between
    /// process start and the first frame.
    nonisolated static func start() {
        Task { @MainActor in
            guard !started else { return }
            started = true
            // Installs Google Sign-In when GOOGLE_IOS_CLIENT_ID is in Info.plist;
            // WebGoogleSignIn.available is false otherwise, so the button soft-fails.
            google = WebGoogleSignIn()
            await api.bind(tokens: session)
            await session.restore()
            MediaConfig.shared.start()
            UploadManager.shared.start()
        }
    }

    /// Installs a real Google Sign-In implementation. Separate from `start` so the
    /// provider never has to cross an isolation boundary.
    static func use(googleProvider: GoogleIdTokenProvider) {
        google = googleProvider
    }
}

/// Where the API lives, and who we say we are.
enum ApiConfig {
    /// `ApiBaseUrl` is generated into Info.plist from `API_BASE_URL` in
    /// `Config/Brand.xcconfig`, so staging and production are a config change.
    static let baseURL: String = {
        let raw = Bundle.main.object(forInfoDictionaryKey: "ApiBaseUrl") as? String ?? ""
        return raw.trimmingCharacters(in: .whitespaces)
    }()

    static let userAgent: String = {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        return "\(Brand.current.name)/\(version) (iOS; \(DeviceIdentity.model))"
    }()
}
