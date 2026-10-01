package app.xani.core.net

import android.content.Context
import app.xani.BuildConfig
import app.xani.core.isolate.CrashLog
import app.xani.core.media.AssetCache
import app.xani.feature.call.CallController
import app.xani.feature.call.LiveSession
import app.xani.feature.call.PushRegistrar
import app.xani.feature.home.FeedRepo
import app.xani.feature.profile.AvatarStore
import app.xani.feature.profile.ProfileStore
import app.xani.feature.upload.UploadManager

/**
 * Obtains a Google ID token from the device.
 *
 * This is an interface because the token can only come from Google's own SDK
 * (Credential Manager / `com.google.android.libraries.identity.googleid`), and this
 * module ships no third-party dependencies by policy. The rest of the Google flow —
 * the endpoint, the request, session adoption, account linking — is complete and
 * exercised by [AuthApi.signInWithGoogle]; only this one step is pluggable.
 *
 * To turn Google sign-in on, set `GOOGLE_WEB_CLIENT_ID` (env / gradle property /
 * brand.env). [MainActivity] installs [CredentialGoogleSignIn]; when the id is
 * empty the provider reports unavailable and the button soft-fails. Pass the
 * same web client id to the identity service as `GOOGLE_CLIENT_IDS`.
 */
sealed class GoogleTokenResult {
    data class Ok(val idToken: String) : GoogleTokenResult()
    /** User backed out of the account picker — not an error. */
    data object Cancelled : GoogleTokenResult()
    data class Failed(val message: String) : GoogleTokenResult()
}

interface GoogleIdTokenProvider {
    suspend fun idToken(context: Context): GoogleTokenResult

    /** False when this build cannot do Google sign-in, so the UI hides the button. */
    val available: Boolean
}

internal object NoGoogleSignIn : GoogleIdTokenProvider {
    override suspend fun idToken(context: Context) =
        GoogleTokenResult.Failed("Google sign-in isn't available in this build.")
    override val available: Boolean = false
}

/**
 * The app's single entry point to the network layer.
 *
 * A plain object rather than a DI graph: there are exactly three long-lived
 * collaborators, they are all created at startup, and a container would add
 * indirection and cold-start cost for nothing. [init] runs once from the Activity
 * before any screen composes.
 */
object Xani {

    @Volatile
    private var initialized = false

    /** False in previews and unit tests, where [init] never ran. */
    val isReady: Boolean get() = initialized

    lateinit var api: ApiClient
        private set

    lateinit var session: SessionManager
        private set

    /** The stable per-install device key the server enforces limits against. */
    lateinit var deviceKey: String
        private set

    internal var google: GoogleIdTokenProvider = NoGoogleSignIn
        private set

    val googleAvailable: Boolean get() = google.available

    /** Typed identity endpoints bound to the live session. */
    val auth: AuthApi get() = AuthApi(api)

    /** Typed profile + wallet endpoints bound to the live session. */
    val profile: ProfileApi get() = ProfileApi(api)

    /** Typed upload + post endpoints bound to the live session. */
    val media: MediaApi get() = MediaApi(api)

    /** Typed people, search and follow-graph endpoints bound to the live session. */
    val people: PeopleApi get() = PeopleApi(api)

    /** Typed one-to-one chat endpoints bound to the live session. */
    val chat: ChatApi get() = ChatApi(api)

    /** Typed Wave Games catalog. */
    val games: GamesApi get() = GamesApi(api)

    /** Typed organisation roles: memberships, upline/downline, referral codes, approvals. */
    val roles: RolesApi get() = RolesApi(api)

    /** Typed call and live signalling endpoints bound to the live session. */
    val calls: CallApi get() = CallApi(api)

    /** Typed live gift catalog, send and ledger endpoints. */
    val gifts: GiftApi get() = GiftApi(api)

    /** Public promotional banners by placement. */
    val banners: BannerApi get() = BannerApi(api)

    /** Help centre articles + support tickets. */
    val support: SupportApi get() = SupportApi(api)

    /** Typed stories (24-hour status) and activity-lane endpoints. */
    val stories: StoryApi get() = StoryApi(api)

    /** Typed avatar-frames shop endpoints. */
    val frames: FramesApi get() = FramesApi(api)

    /** Typed gifting-level endpoints: the ladders and the caller's record. */
    val levels: LevelApi get() = LevelApi(api)

    /** Typed wallet statement + diamond withdrawal endpoints. */
    val wallet: WalletApi get() = WalletApi(api)

    fun init(context: Context, googleProvider: GoogleIdTokenProvider = NoGoogleSignIn) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            // Before anything else: a death during startup is the one we most
            // need the reason for.
            CrashLog.install(app)
            deviceKey = DeviceIdentity.key(app)
            google = googleProvider
            api = ApiClient(baseUrl = baseUrl(), userAgent = userAgent())
            session = SessionManager(app, api)
            api.tokens = session
            ProfileStore.attach(app)
            AvatarStore.attach(app)
            MediaConfig.attach(app)
            AssetCache.attach(app)
            UploadManager.attach(app)
            FeedRepo.attach(app)
            CallController.attach(app)
            LiveSession.attach(app)
            PushRegistrar.attach(app)
            initialized = true
        }
        session.restore()
    }

    /**
     * The API host comes from the build (env → gradle property → default), so the
     * same source builds against staging and production without a code change.
     */
    private fun baseUrl(): String = BuildConfig.API_BASE_URL.trim().trimEnd('/')

    private fun userAgent(): String =
        "${BuildConfig.APP_NAME}/${BuildConfig.VERSION_NAME} (Android; ${DeviceIdentity.model})"
}
