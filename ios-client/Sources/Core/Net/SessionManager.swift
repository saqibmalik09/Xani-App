import Foundation

/// What the UI needs to know about the signed-in state.
enum AuthState: Equatable {
    /// Before `restore()` has decided — show the splash, not the login.
    case unknown
    case signedOut
    case signedIn(AccountUser, Access)
}

/// Owns sessions for every account saved on this device.
///
/// Switching is a local token swap + [resync]; adding an account never clears the
/// others. A ban / dead refresh on the active slot drops that slot and falls back
/// to another saved account when one exists.
///
/// Two further behaviours stay as they were for a single session:
///
///  1. **Silent renewal.** Concurrent 401s funnel into one refresh task.
///  2. **Hard stop at refresh expiry.** When the last saved account dies,
///     `sessionExpired` raises the non-dismissible sign-in sheet.
@MainActor
final class SessionManager: ObservableObject {

    @Published private(set) var state: AuthState = .unknown

    /// Saved logins on this device, newest-used first.
    @Published private(set) var accounts: [SavedAccount] = []

    /// True while the login overlay is open to add another account without signing out.
    @Published private(set) var addingAccount = false
    /// When true, the add-account overlay opens on Sign up instead of Log in.
    @Published private(set) var addAccountStartSignup = false

    /// True while the forced, non-cancellable sign-in sheet must be shown.
    @Published private(set) var sessionExpired = false

    /// One-shot message when a switch fails (banned / expired slot).
    @Published private(set) var switchError = ""

    /// The account whose session ended, so the sheet can say who to sign back in as.
    private(set) var lastKnownUser: AccountUser?

    private let api: ApiClient
    private let store = SessionStore()
    private var tokens: Tokens?

    /// The single in-flight refresh. Everyone else awaits this task's result.
    private var refreshTask: Task<String?, Never>?
    private var expiryWatch: Task<Void, Never>?
    private var switching = false

    init(api: ApiClient) {
        self.api = api
    }

    var isSignedIn: Bool {
        if case .signedIn = state { return true }
        return false
    }

    var currentUserId: String {
        if case let .signedIn(user, _) = state { return user.id }
        return ""
    }

    var currentAccess: Access {
        if case let .signedIn(_, access) = state { return access }
        return .none
    }

    /// Convenience for UI gating: `if session.can("live.go_live") { … }`.
    func can(_ permission: String) -> Bool { currentAccess.can(permission) }

    func can(_ permission: String, scopeType: Int, scopeId: String) -> Bool {
        currentAccess.can(permission, scopeType: scopeType, scopeId: scopeId)
    }

    func clearSwitchError() {
        switchError = ""
    }

    /// Restores persisted accounts. Called once at startup.
    func restore() async {
        let all = store.loadAccounts().sorted { $0.lastUsedAt > $1.lastUsedAt }
        accounts = all
        let activeId = store.activeUserId()
        let active = all.first(where: { $0.userId == activeId }) ?? all.first
        guard let active else {
            state = .signedOut
            return
        }
        if active.tokens.isRefreshExpired() {
            let rest = all.filter { $0.userId != active.userId }
            if !rest.isEmpty {
                persist(rest, activeId: rest[0].userId)
                activateLocal(rest[0])
                armExpiryWatch()
                await resync()
                return
            }
            lastKnownUser = AccountUser(
                id: active.userId,
                username: active.username,
                fullName: active.fullName,
                email: active.email,
                emailVerified: false,
                avatarURL: active.avatarURL,
                passwordLoginActive: active.passwordLoginActive
            )
            store.clearAll()
            accounts = []
            sessionExpired = true
            tokens = nil
            state = .signedOut
            return
        }
        activateLocal(active)
        armExpiryWatch()
        await resync()
    }

    /// Re-reads identity and permissions from the server.
    func resync() async {
        let response = await AuthApi(api: api).me()
        if response.ok, let payload = response.value {
            lastKnownUser = payload.user
            upsertActive(user: payload.user, access: payload.access, tokens: tokens ?? .empty)
            state = .signedIn(payload.user, payload.access)
        } else if response.isSessionExpired || response.code == "blocked" {
            await dropActiveAndFallback(
                message: response.message.isEmpty
                    ? "This account can’t be used right now."
                    : response.message
            )
        } else if response.isTransport {
            // Offline: keep the cached session.
        } else if state == .unknown {
            state = .signedOut
        }
    }

    /// Adopts a freshly issued session from signup, login or Google.
    func adopt(_ session: Session) {
        applySession(session, clearAdding: true)
    }

    /// Updates cached identity after profile completion without rotating tokens.
    func updateUser(_ user: AccountUser) {
        let access = currentAccess
        guard let tok = tokens else { return }
        lastKnownUser = user
        upsertActive(user: user, access: access, tokens: tok)
        state = .signedIn(user, access)
    }

    /// Switches to a saved account. Validates with the server; banned / dead slots
    /// are removed and an error is published.
    func switchTo(_ userId: String) {
        guard userId != currentUserId else { return }
        Task { await performSwitch(to: userId) }
    }

    /// Opens login/signup to add another account without signing out.
    func beginAddAccount(startSignup: Bool = false) {
        addAccountStartSignup = startSignup
        addingAccount = true
    }

    /// Closes add-account login and keeps the current session.
    func cancelAddAccount() {
        addingAccount = false
        addAccountStartSignup = false
    }

    /// Removes one saved account. If it was active, falls back to another or signs out.
    func removeAccount(_ userId: String) {
        Task { await performRemove(userId) }
    }

    /// Signs out the active account only; another saved account becomes active if any.
    func signOut() {
        let id = currentUserId
        if id.isEmpty {
            clearActiveLocal()
            store.clearAll()
            accounts = []
            state = .signedOut
            sessionExpired = false
            addingAccount = false
            addAccountStartSignup = false
            return
        }
        removeAccount(id)
    }

    /// Ends the active slot because credentials are gone. Falls back when possible;
    /// otherwise raises `sessionExpired`.
    func expire() {
        Task {
            await dropActiveAndFallback(message: "", raiseExpiryIfEmpty: true)
        }
    }

    /// Called once the user signs back in through the forced sheet.
    func dismissExpiryAfterSignIn() {
        sessionExpired = false
    }

    // MARK: - Switch / remove

    private func applySession(_ session: Session, clearAdding: Bool) {
        let slot = SavedAccount(
            userId: session.user.id,
            username: session.user.username,
            fullName: session.user.fullName,
            avatarURL: session.user.avatarURL,
            email: session.user.email,
            passwordLoginActive: session.user.passwordLoginActive,
            tokens: session.tokens,
            access: session.access,
            lastUsedAt: Date()
        )
        let others = accounts.filter { $0.userId != slot.userId }
        persist(others + [slot], activeId: slot.userId)
        tokens = session.tokens
        lastKnownUser = session.user
        sessionExpired = false
        if clearAdding {
            addingAccount = false
            addAccountStartSignup = false
        }
        state = .signedIn(session.user, session.access)
        armExpiryWatch()
    }
    private func performSwitch(to userId: String) async {
        guard !switching else { return }
        switching = true
        defer { switching = false }

        guard let slot = accounts.first(where: { $0.userId == userId }) else {
            switchError = "That account isn’t saved on this device."
            return
        }
        if slot.tokens.isRefreshExpired() {
            removeSlot(userId)
            switchError = "That account’s session expired. Sign in again."
            return
        }
        let now = Date()
        let touched = SavedAccount(
            userId: slot.userId,
            username: slot.username,
            fullName: slot.fullName,
            avatarURL: slot.avatarURL,
            email: slot.email,
            passwordLoginActive: slot.passwordLoginActive,
            tokens: slot.tokens,
            access: slot.access,
            lastUsedAt: now
        )
        activateLocal(touched)
        persist(
            accounts.map { $0.userId == slot.userId ? touched : $0 },
            activeId: slot.userId
        )
        armExpiryWatch()

        let response = await AuthApi(api: api).me()
        if response.ok, let payload = response.value {
            lastKnownUser = payload.user
            upsertActive(user: payload.user, access: payload.access, tokens: tokens ?? slot.tokens)
            state = .signedIn(payload.user, payload.access)
            switchError = ""
        } else if response.isTransport {
            // Offline: keep the cached swap.
            switchError = ""
        } else {
            let msg: String
            if response.code == "blocked" {
                msg = response.message.isEmpty
                    ? "This account is blocked or restricted."
                    : response.message
            } else {
                msg = response.message.isEmpty
                    ? "Couldn’t switch to that account."
                    : response.message
            }
            await dropActiveAndFallback(message: msg)
        }
    }

    private func performRemove(_ userId: String) async {
        guard !switching else { return }
        switching = true
        defer { switching = false }

        let wasActive = userId == currentUserId
        let rest = accounts.filter { $0.userId != userId }
        if wasActive {
            let refresh = tokens?.refreshToken
            if let refresh, !refresh.isEmpty {
                Task { await AuthApi(api: api).logout(refreshToken: refresh) }
            }
            if let next = rest.first {
                persist(rest, activeId: next.userId)
                activateLocal(next)
                armExpiryWatch()
                await resync()
            } else {
                clearActiveLocal()
                persist([], activeId: "")
                state = .signedOut
                sessionExpired = false
            }
        } else {
            persist(rest, activeId: currentUserId)
        }
    }

    // MARK: - Internals

    private func activateLocal(_ slot: SavedAccount) {
        tokens = slot.tokens
        let user = AccountUser(
            id: slot.userId,
            username: slot.username,
            fullName: slot.fullName,
            email: slot.email,
            emailVerified: false,
            avatarURL: slot.avatarURL,
            passwordLoginActive: slot.passwordLoginActive
        )
        lastKnownUser = user
        state = .signedIn(user, slot.access)
    }

    private func upsertActive(user: AccountUser, access: Access, tokens tok: Tokens) {
        let slot = SavedAccount(
            userId: user.id,
            username: user.username,
            fullName: user.fullName,
            avatarURL: user.avatarURL,
            email: user.email,
            passwordLoginActive: user.passwordLoginActive,
            tokens: tok,
            access: access,
            lastUsedAt: Date()
        )
        let others = accounts.filter { $0.userId != user.id }
        persist(others + [slot], activeId: user.id)
    }

    private func persist(_ list: [SavedAccount], activeId: String) {
        let ordered = list.sorted { $0.lastUsedAt > $1.lastUsedAt }
        store.saveAccounts(ordered, activeUserId: activeId)
        accounts = ordered
    }

    private func removeSlot(_ userId: String) {
        let rest = accounts.filter { $0.userId != userId }
        let nextActive: String
        if rest.contains(where: { $0.userId == currentUserId }) {
            nextActive = currentUserId
        } else {
            nextActive = rest.first?.userId ?? ""
        }
        persist(rest, activeId: nextActive)
    }

    private func dropActiveAndFallback(message: String, raiseExpiryIfEmpty: Bool = false) async {
        let deadId = currentUserId
        let remembered = lastKnownUser
        let refresh = tokens?.refreshToken
        let rest = accounts.filter { $0.userId != deadId }
        clearActiveLocal(keepStore: true)
        if let next = rest.first {
            persist(rest, activeId: next.userId)
            activateLocal(next)
            armExpiryWatch()
            if !message.isEmpty { switchError = message }
            await resync()
            return
        }
        persist([], activeId: "")
        if let refresh, !refresh.isEmpty {
            Task { await AuthApi(api: api).logout(refreshToken: refresh) }
        }
        if raiseExpiryIfEmpty || !message.isEmpty {
            lastKnownUser = remembered
            sessionExpired = raiseExpiryIfEmpty
            if !message.isEmpty { switchError = message }
        }
        state = .signedOut
    }

    private func clearActiveLocal(keepStore: Bool = false) {
        expiryWatch?.cancel()
        expiryWatch = nil
        tokens = nil
        refreshTask = nil
        if !keepStore {
            store.clearAll()
            accounts = []
        }
    }

    /// Raises [expire] at the refresh deadline even when the app is idle.
    private func armExpiryWatch() {
        expiryWatch?.cancel()
        guard let deadline = tokens?.refreshExpiresAt else { return }
        let wait = max(0, deadline.timeIntervalSinceNow)
        expiryWatch = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard !Task.isCancelled, let self else { return }
            if self.tokens?.isRefreshExpired() == true {
                self.expire()
            }
        }
    }
}

extension SessionManager: TokenSource {
    func accessToken() async -> String? {
        guard let current = tokens else { return nil }
        if !current.isAccessExpired() { return current.accessToken }
        return await renew(staleToken: current.accessToken)
    }

    func renew(staleToken: String?) async -> String? {
        if let current = tokens, current.accessToken != staleToken, !current.isAccessExpired() {
            return current.accessToken
        }
        if let inFlight = refreshTask {
            return await inFlight.value
        }

        guard let refreshToken = tokens?.refreshToken,
              !refreshToken.isEmpty,
              tokens?.isRefreshExpired() == false
        else {
            expire()
            return nil
        }

        let task = Task<String?, Never> {
            let response = await AuthApi(api: self.api).refresh(refreshToken: refreshToken)
            if response.ok, let session = response.value {
                // Token rotate must not dismiss an in-progress add-account overlay.
                self.applySession(session, clearAdding: false)
                return session.tokens.accessToken
            }
            if response.isTransport {
                return nil
            }
            self.expire()
            return nil
        }
        refreshTask = task
        let result = await task.value
        refreshTask = nil
        return result
    }
}
