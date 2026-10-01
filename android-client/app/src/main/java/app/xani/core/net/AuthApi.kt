package app.xani.core.net

import org.json.JSONObject

/**
 * Every identity endpoint, one method each.
 *
 * The device block is attached here rather than at each call site, because the server
 * enforces per-device account limits and device bans on signup, login *and* refresh —
 * a call that forgets it gets rejected, and that bug is invisible until someone tries
 * to create a fourth account on one phone.
 */
class AuthApi internal constructor(private val api: ApiClient) {
    private val device: JSONObject
        get() = jsonOf(
            "key" to Xani.deviceKey,
            "platform" to DeviceIdentity.PLATFORM,
            "model" to DeviceIdentity.model,
        )

    // ─── bootstrap ───────────────────────────────────────────────────────────

    suspend fun config(): ApiResponse<AppConfig> =
        api.get("/v1/config") { AppConfig.from(it) }

    suspend fun countries(): ApiResponse<List<Country>> =
        api.get("/v1/countries") { data -> data.objects("items").map(Country::from) }

    /** Accepts "PK", "586", "92", "+92" or "Pakistan" and returns the match. */
    suspend fun resolveCountry(query: String): ApiResponse<Country> =
        api.get("/v1/countries/resolve", mapOf("q" to query)) { data ->
            Country.from(data.obj("country") ?: JSONObject())
        }

    /** Derives the country from a full phone number by its calling code. */
    suspend fun countryFromPhone(phone: String): ApiResponse<Country> =
        api.get("/v1/countries/resolve", mapOf("phone" to phone)) { data ->
            Country.from(data.obj("country") ?: JSONObject())
        }

    // ─── sign up / sign in ───────────────────────────────────────────────────

    suspend fun signUpWithEmail(
        fullName: String,
        username: String,
        email: String,
        password: String,
        country: String,
        location: String = "",
    ): ApiResponse<Session> = api.post(
        "/v1/auth/signup",
        jsonOf(
            "full_name" to fullName,
            "username" to username,
            "email" to email,
            "password" to password,
            "country" to country,
            "location" to location,
            "device" to device,
        ),
    ) { Session.from(it, System.currentTimeMillis()) }

    /**
     * One call for Google sign-up and sign-in. The client cannot know which it is,
     * and making it guess produces the "account already exists, go back and press
     * the other button" dead end.
     */
    suspend fun signInWithGoogle(idToken: String, country: String = ""): ApiResponse<Session> =
        api.post(
            "/v1/auth/google",
            jsonOf("id_token" to idToken, "country" to country, "device" to device),
        ) { Session.from(it, System.currentTimeMillis()) }

    /** [identifier] is an email, a username, or a phone number — the server decides. */
    suspend fun logIn(identifier: String, password: String): ApiResponse<Session> = api.post(
        "/v1/auth/login",
        jsonOf("identifier" to identifier, "password" to password, "device" to device),
    ) { Session.from(it, System.currentTimeMillis()) }

    internal suspend fun refresh(refreshToken: String): ApiResponse<Session> =
        api.post("/v1/auth/refresh", jsonOf("refresh_token" to refreshToken)) {
            Session.from(it, System.currentTimeMillis())
        }

    internal suspend fun logout(refreshToken: String): ApiResponse<Unit> =
        api.post("/v1/auth/logout", jsonOf("refresh_token" to refreshToken))

    /** Current identity and permissions. Used on cold start to re-sync authority. */
    suspend fun me(): ApiResponse<Pair<AccountUser, Access>> =
        api.get("/v1/auth/me", authenticated = true) { data ->
            val user = data.obj("user")?.let(AccountUser::from) ?: AccountUser.Empty
            user to Access.from(data)
        }

    // ─── recovery ────────────────────────────────────────────────────────────

    /**
     * "Which account is this?" Returns a masked destination when there is a match and
     * an empty result otherwise — the response deliberately cannot be used to test
     * whether an email is registered.
     */
    suspend fun findAccount(identifier: String): ApiResponse<AccountHint> =
        api.post("/v1/auth/find-account", jsonOf("identifier" to identifier)) {
            AccountHint.from(it)
        }

    suspend fun requestPasswordReset(identifier: String): ApiResponse<CodeIssued> =
        api.post("/v1/auth/password/forgot", jsonOf("identifier" to identifier)) {
            CodeIssued.from(it)
        }

    suspend fun resetPassword(
        identifier: String,
        code: String,
        newPassword: String,
        confirmPassword: String,
    ): ApiResponse<Unit> = api.post(
        "/v1/auth/password/reset",
        jsonOf(
            "identifier" to identifier,
            "code" to code,
            "new_password" to newPassword,
            "confirm_password" to confirmPassword,
        ),
    )

    /**
     * Lets an account created through Google add a password, so it can afterwards
     * sign in with email or username too.
     */
    suspend fun activatePassword(
        newPassword: String,
        confirmPassword: String,
    ): ApiResponse<Unit> = api.post(
        "/v1/auth/password/activate",
        jsonOf("new_password" to newPassword, "confirm_password" to confirmPassword),
        authenticated = true,
    )

    /** Updates password for accounts that already have email/username login. */
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): ApiResponse<Unit> = api.post(
        "/v1/auth/password/change",
        jsonOf(
            "current_password" to currentPassword,
            "new_password" to newPassword,
            "confirm_password" to confirmPassword,
        ),
        authenticated = true,
    )

    /**
     * Post-Google onboarding: name, username, birthday + password so the account
     * can also sign in with credentials.
     */
    suspend fun completeSocialProfile(
        fullName: String,
        username: String,
        birthday: String,
        newPassword: String,
        confirmPassword: String,
    ): ApiResponse<AccountUser> = api.post(
        "/v1/auth/profile/complete",
        jsonOf(
            "full_name" to fullName,
            "username" to username,
            "birthday" to birthday,
            "new_password" to newPassword,
            "confirm_password" to confirmPassword,
        ),
        authenticated = true,
    ) { data ->
        data.obj("user")?.let(AccountUser::from) ?: AccountUser.Empty
    }

    suspend fun verifyEmail(code: String): ApiResponse<Unit> =
        api.post("/v1/auth/email/verify", jsonOf("code" to code), authenticated = true)

    suspend fun resendEmailCode(): ApiResponse<CodeIssued> =
        api.post("/v1/auth/email/resend", JSONObject(), authenticated = true) {
            CodeIssued.from(it)
        }
}
