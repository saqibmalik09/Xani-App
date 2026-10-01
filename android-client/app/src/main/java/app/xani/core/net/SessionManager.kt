package app.xani.core.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/** What the UI needs to know about the signed-in state. */
sealed interface AuthState {
    /** Before [SessionManager.restore] has decided — show the splash, not the login. */
    data object Unknown : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val user: AccountUser, val access: Access) : AuthState
}

/**
 * Owns sessions for every account saved on this device.
 *
 * Switching is a local token swap + [resync]; adding an account never clears the
 * others. A ban / dead refresh on the active slot drops that slot and falls back
 * to another saved account when one exists.
 */
class SessionManager internal constructor(
    context: Context,
    private val api: ApiClient,
) : TokenSource {

    private val store = SessionStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()
    private val switchLock = Mutex()
    private var expiryWatch: Job? = null

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    /** Saved logins on this device, newest-used first. */
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _addingAccount = MutableStateFlow(false)
    /** True while the login sheet is open to add another account without signing out. */
    val addingAccount: StateFlow<Boolean> = _addingAccount.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)

    /** True while the forced, non-cancellable sign-in sheet must be shown. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private val _switchError = MutableStateFlow("")
    /** One-shot message when a switch fails (banned / expired slot). */
    val switchError: StateFlow<String> = _switchError.asStateFlow()

    @Volatile
    private var tokens: Tokens? = null

    /** The account whose session ended, so the modal can say who to sign back in as. */
    @Volatile
    var lastKnownUser: AccountUser? = null
        private set

    val isSignedIn: Boolean get() = _state.value is AuthState.SignedIn

    val currentUserId: Long
        get() = (_state.value as? AuthState.SignedIn)?.user?.id ?: 0L

    val currentAccess: Access
        get() = (_state.value as? AuthState.SignedIn)?.access ?: Access.None

    fun can(permission: String): Boolean = currentAccess.can(permission)

    fun can(permission: String, scopeType: Int, scopeId: Long): Boolean =
        currentAccess.can(permission, scopeType, scopeId)

    fun clearSwitchError() {
        _switchError.value = ""
    }

    fun restore() {
        scope.launch {
            val all = store.loadAccounts().sortedByDescending { it.lastUsedAt }
            _accounts.value = all
            val activeId = store.activeUserId()
            val active = all.firstOrNull { it.userId == activeId } ?: all.firstOrNull()
            if (active == null || active.tokens.isRefreshExpired(System.currentTimeMillis())) {
                if (active != null) {
                    // Drop only the dead slot; try another.
                    val rest = all.filterNot { it.userId == active.userId }
                    if (rest.isNotEmpty()) {
                        persist(rest, rest.first().userId)
                        activateLocal(rest.first())
                        armExpiryWatch()
                        resync()
                        return@launch
                    }
                    lastKnownUser = AccountUser.Empty.copy(
                        id = active.userId,
                        username = active.username,
                        fullName = active.fullName,
                        email = active.email,
                        avatarUrl = active.avatarUrl,
                        passwordLoginActive = active.passwordLoginActive,
                    )
                    store.clearAll()
                    _accounts.value = emptyList()
                    _sessionExpired.value = true
                }
                tokens = null
                _state.value = AuthState.SignedOut
                return@launch
            }
            activateLocal(active)
            armExpiryWatch()
            resync()
        }
    }

    suspend fun resync() {
        val response = AuthApi(api).me()
        when {
            response.ok && response.data != null -> {
                val (user, access) = response.data
                lastKnownUser = user
                upsertActive(user, access, tokens ?: return)
                _state.value = AuthState.SignedIn(user, access)
            }
            response.isSessionExpired || response.code == "blocked" -> dropActiveAndFallback(
                message = response.message.ifBlank { "This account can’t be used right now." },
            )
            response.isTransport -> Log.i(TAG, "resync skipped: offline")
            else -> {
                if (_state.value is AuthState.Unknown) _state.value = AuthState.SignedOut
            }
        }
    }

    /** Adopts a freshly issued session from signup, login or Google. */
    fun adopt(session: Session) {
        val now = System.currentTimeMillis()
        val slot = SavedAccount(
            userId = session.user.id,
            username = session.user.username,
            fullName = session.user.fullName,
            avatarUrl = session.user.avatarUrl,
            email = session.user.email,
            passwordLoginActive = session.user.passwordLoginActive,
            tokens = session.tokens,
            access = session.access,
            lastUsedAt = now,
        )
        val others = _accounts.value.filterNot { it.userId == slot.userId }
        persist(others + slot, slot.userId)
        tokens = session.tokens
        lastKnownUser = session.user
        _sessionExpired.value = false
        _addingAccount.value = false
        _addAccountStartSignup.value = false
        _state.value = AuthState.SignedIn(session.user, session.access)
        armExpiryWatch()
    }

    fun updateUser(user: AccountUser) {
        val access = currentAccess
        val tok = tokens ?: return
        lastKnownUser = user
        upsertActive(user, access, tok)
        _state.value = AuthState.SignedIn(user, access)
    }

    /**
     * Switches to a saved account. Validates with the server; banned / dead slots
     * are removed and an error is published.
     */
    fun switchTo(userId: Long) {
        if (userId == currentUserId) return
        scope.launch {
            switchLock.withLock {
                val slot = _accounts.value.firstOrNull { it.userId == userId }
                if (slot == null) {
                    _switchError.value = "That account isn’t saved on this device."
                    return@withLock
                }
                if (slot.tokens.isRefreshExpired(System.currentTimeMillis())) {
                    removeSlot(userId)
                    _switchError.value = "That account’s session expired. Sign in again."
                    return@withLock
                }
                activateLocal(slot.copy(lastUsedAt = System.currentTimeMillis()))
                persist(_accounts.value.map {
                    if (it.userId == slot.userId) it.copy(lastUsedAt = System.currentTimeMillis()) else it
                }.sortedByDescending { it.lastUsedAt }, slot.userId)
                armExpiryWatch()
                val response = AuthApi(api).me()
                when {
                    response.ok && response.data != null -> {
                        val (user, access) = response.data
                        lastKnownUser = user
                        upsertActive(user, access, tokens ?: slot.tokens)
                        _state.value = AuthState.SignedIn(user, access)
                        _switchError.value = ""
                    }
                    response.isTransport -> {
                        // Offline: keep the cached swap; user can use the app.
                        _switchError.value = ""
                    }
                    else -> {
                        val msg = when (response.code) {
                            "blocked" -> response.message.ifBlank {
                                "This account is blocked or restricted."
                            }
                            else -> response.message.ifBlank {
                                "Couldn’t switch to that account."
                            }
                        }
                        dropActiveAndFallback(msg)
                    }
                }
            }
        }
    }

    private val _addAccountStartSignup = MutableStateFlow(false)
    /** When true, the add-account overlay opens on Sign up instead of Log in. */
    val addAccountStartSignup: StateFlow<Boolean> = _addAccountStartSignup.asStateFlow()

    /** Opens login/signup to add another account without signing out. */
    fun beginAddAccount(startSignup: Boolean = false) {
        _addAccountStartSignup.value = startSignup
        _addingAccount.value = true
    }

    /** Closes add-account login and keeps the current session. */
    fun cancelAddAccount() {
        _addingAccount.value = false
        _addAccountStartSignup.value = false
    }

    /** Removes one saved account. If it was active, falls back to another or signs out. */
    fun removeAccount(userId: Long) {
        scope.launch {
            switchLock.withLock {
                val wasActive = userId == currentUserId
                val rest = _accounts.value.filterNot { it.userId == userId }
                if (wasActive) {
                    val refresh = tokens?.refreshToken
                    if (!refresh.isNullOrBlank()) {
                        scope.launch { AuthApi(api).logout(refresh) }
                    }
                    if (rest.isNotEmpty()) {
                        persist(rest, rest.first().userId)
                        activateLocal(rest.first())
                        armExpiryWatch()
                        resync()
                    } else {
                        clearActiveLocal()
                        persist(emptyList(), 0L)
                        _state.value = AuthState.SignedOut
                        _sessionExpired.value = false
                    }
                } else {
                    persist(rest, currentUserId)
                }
            }
        }
    }

    /** Signs out the active account only; another saved account becomes active if any. */
    fun signOut() {
        val id = currentUserId
        if (id == 0L) {
            clearActiveLocal()
            store.clearAll()
            _accounts.value = emptyList()
            _state.value = AuthState.SignedOut
            _sessionExpired.value = false
            _addingAccount.value = false
            _addAccountStartSignup.value = false
            return
        }
        removeAccount(id)
    }

    fun expire() {
        scope.launch {
            switchLock.withLock {
                dropActiveAndFallback(
                    message = "",
                    raiseExpiryIfEmpty = true,
                )
            }
        }
    }

    fun dismissExpiryAfterSignIn() {
        _sessionExpired.value = false
    }

    private fun activateLocal(slot: SavedAccount) {
        tokens = slot.tokens
        val user = AccountUser.Empty.copy(
            id = slot.userId,
            username = slot.username,
            fullName = slot.fullName,
            email = slot.email,
            avatarUrl = slot.avatarUrl,
            passwordLoginActive = slot.passwordLoginActive,
        )
        lastKnownUser = user
        _state.value = AuthState.SignedIn(user, slot.access)
    }

    private fun upsertActive(user: AccountUser, access: Access, tok: Tokens) {
        val now = System.currentTimeMillis()
        val slot = SavedAccount(
            userId = user.id,
            username = user.username,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            email = user.email,
            passwordLoginActive = user.passwordLoginActive,
            tokens = tok,
            access = access,
            lastUsedAt = now,
        )
        val others = _accounts.value.filterNot { it.userId == user.id }
        persist(others + slot, user.id)
    }

    private fun persist(accounts: List<SavedAccount>, activeId: Long) {
        val ordered = accounts.sortedByDescending { it.lastUsedAt }
        store.saveAccounts(ordered, activeId)
        _accounts.value = ordered
    }

    private fun removeSlot(userId: Long) {
        val rest = _accounts.value.filterNot { it.userId == userId }
        persist(rest, if (rest.any { it.userId == currentUserId }) currentUserId else rest.firstOrNull()?.userId ?: 0L)
    }

    private suspend fun dropActiveAndFallback(message: String, raiseExpiryIfEmpty: Boolean = false) {
        val deadId = currentUserId
        val remembered = lastKnownUser
        val refresh = tokens?.refreshToken
        val rest = _accounts.value.filterNot { it.userId == deadId }
        clearActiveLocal()
        if (rest.isNotEmpty()) {
            persist(rest, rest.first().userId)
            activateLocal(rest.first())
            armExpiryWatch()
            if (message.isNotEmpty()) _switchError.value = message
            resync()
            return
        }
        persist(emptyList(), 0L)
        if (!refresh.isNullOrBlank()) {
            scope.launch { AuthApi(api).logout(refresh) }
        }
        if (raiseExpiryIfEmpty || message.isNotEmpty()) {
            lastKnownUser = remembered
            _sessionExpired.value = raiseExpiryIfEmpty
            if (message.isNotEmpty()) _switchError.value = message
        }
        _state.value = AuthState.SignedOut
    }

    private fun clearActiveLocal() {
        expiryWatch?.cancel()
        expiryWatch = null
        tokens = null
    }

    private fun armExpiryWatch() {
        expiryWatch?.cancel()
        val deadline = tokens?.refreshExpiresAtMillis ?: return
        val wait = max(0L, deadline - System.currentTimeMillis())
        expiryWatch = scope.launch {
            delay(wait)
            val current = tokens ?: return@launch
            if (current.isRefreshExpired(System.currentTimeMillis())) {
                expire()
            }
        }
    }

    override suspend fun accessToken(): String? {
        val current = tokens ?: return null
        if (!current.isAccessExpired(System.currentTimeMillis())) return current.accessToken
        return renew(current.accessToken)
    }

    override suspend fun renew(staleToken: String?): String? = refreshLock.withLock {
        val current = tokens
        if (current != null && current.accessToken != staleToken &&
            !current.isAccessExpired(System.currentTimeMillis())
        ) {
            return@withLock current.accessToken
        }

        val refreshToken = current?.refreshToken
        if (refreshToken.isNullOrBlank() || current.isRefreshExpired(System.currentTimeMillis())) {
            expire()
            return@withLock null
        }

        val response = AuthApi(api).refresh(refreshToken)
        when {
            response.ok && response.data != null -> {
                adopt(response.data)
                response.data.tokens.accessToken
            }
            response.isTransport -> null
            else -> {
                expire()
                null
            }
        }
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}
