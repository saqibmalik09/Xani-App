import Foundation

/// Every identity endpoint, one method each.
///
/// The device block is attached here rather than at each call site, because the server
/// enforces per-device account limits and device bans on signup, login *and* refresh —
/// a call that forgets it gets rejected, and that bug is invisible until someone tries
/// to create a fourth account on one phone.
struct AuthApi {
    let api: ApiClient

    private var device: [String: Any] {
        [
            "key": DeviceIdentity.key,
            "platform": DeviceIdentity.platform,
            "model": DeviceIdentity.model,
        ]
    }

    // MARK: - Bootstrap

    func config() async -> ApiResponse<AppConfig> {
        await api.get("/v1/config", as: AppConfig.self)
    }

    func countries() async -> ApiResponse<[Country]> {
        let response = await api.get("/v1/countries", as: ItemsResponse<Country>.self)
        return response.map(\.items)
    }

    /// Accepts "PK", "586", "92", "+92" or "Pakistan" and returns the match.
    func resolveCountry(_ query: String) async -> ApiResponse<Country> {
        let response = await api.get("/v1/countries/resolve", query: ["q": query],
                                     as: CountryLookup.self)
        return response.map(\.country)
    }

    /// Derives the country from a full phone number by its calling code.
    func country(fromPhone phone: String) async -> ApiResponse<Country> {
        let response = await api.get("/v1/countries/resolve", query: ["phone": phone],
                                     as: CountryLookup.self)
        return response.map(\.country)
    }

    // MARK: - Sign up / sign in

    func signUpWithEmail(
        fullName: String,
        username: String,
        email: String,
        password: String,
        country: String,
        location: String = ""
    ) async -> ApiResponse<Session> {
        await api.post("/v1/auth/signup", body: [
            "full_name": fullName,
            "username": username,
            "email": email,
            "password": password,
            "country": country,
            "location": location,
            "device": device,
        ], as: Session.self)
    }

    /// One call for Google sign-up and sign-in. The client cannot know which it is, and
    /// making it guess produces the "account already exists, go back and press the
    /// other button" dead end.
    func signInWithGoogle(idToken: String, country: String = "") async -> ApiResponse<Session> {
        await api.post("/v1/auth/google", body: [
            "id_token": idToken,
            "country": country,
            "device": device,
        ], as: Session.self)
    }

    /// `identifier` is an email, a username, or a phone number — the server decides.
    func logIn(identifier: String, password: String) async -> ApiResponse<Session> {
        await api.post("/v1/auth/login", body: [
            "identifier": identifier,
            "password": password,
            "device": device,
        ], as: Session.self)
    }

    func refresh(refreshToken: String) async -> ApiResponse<Session> {
        await api.post("/v1/auth/refresh", body: ["refresh_token": refreshToken], as: Session.self)
    }

    @discardableResult
    func logout(refreshToken: String) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/auth/logout", body: ["refresh_token": refreshToken])
    }

    /// Current identity and permissions. Used on cold start to re-sync authority.
    func me() async -> ApiResponse<MeResponse> {
        await api.get("/v1/auth/me", authenticated: true, as: MeResponse.self)
    }

    // MARK: - Recovery

    /// "Which account is this?" Returns a masked destination when there is a match and
    /// an empty result otherwise — the response deliberately cannot be used to test
    /// whether an email is registered.
    func findAccount(identifier: String) async -> ApiResponse<AccountHint> {
        await api.post("/v1/auth/find-account", body: ["identifier": identifier],
                       as: AccountHint.self)
    }

    func requestPasswordReset(identifier: String) async -> ApiResponse<CodeIssued> {
        await api.post("/v1/auth/password/forgot", body: ["identifier": identifier],
                       as: CodeIssued.self)
    }

    @discardableResult
    func resetPassword(
        identifier: String,
        code: String,
        newPassword: String,
        confirmPassword: String
    ) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/auth/password/reset", body: [
            "identifier": identifier,
            "code": code,
            "new_password": newPassword,
            "confirm_password": confirmPassword,
        ])
    }

    /// Lets an account created through Google add a password, so it can afterwards sign
    /// in with email or username too.
    @discardableResult
    func activatePassword(
        newPassword: String,
        confirmPassword: String
    ) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/auth/password/activate", body: [
            "new_password": newPassword,
            "confirm_password": confirmPassword,
        ], authenticated: true)
    }

    /// Updates password for accounts that already have email/username login.
    @discardableResult
    func changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/auth/password/change", body: [
            "current_password": currentPassword,
            "new_password": newPassword,
            "confirm_password": confirmPassword,
        ], authenticated: true)
    }

    /// Post-Google onboarding: name, username, birthday + password.
    func completeSocialProfile(
        fullName: String,
        username: String,
        birthday: String,
        newPassword: String,
        confirmPassword: String
    ) async -> ApiResponse<CompleteSocialPayload> {
        await api.post("/v1/auth/profile/complete", body: [
            "full_name": fullName,
            "username": username,
            "birthday": birthday,
            "new_password": newPassword,
            "confirm_password": confirmPassword,
        ], authenticated: true, as: CompleteSocialPayload.self)
    }

    @discardableResult
    func verifyEmail(code: String) async -> ApiResponse<EmptyPayload> {
        await api.post("/v1/auth/email/verify", body: ["code": code], authenticated: true)
    }

    func resendEmailCode() async -> ApiResponse<CodeIssued> {
        await api.post("/v1/auth/email/resend", body: [:], authenticated: true,
                       as: CodeIssued.self)
    }

    private struct CountryLookup: Decodable {
        let country: Country
    }
}
