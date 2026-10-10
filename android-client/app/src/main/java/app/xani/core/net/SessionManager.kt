package app.xani.core.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
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

    // One failing session task must not take the process down: an uncaught throw
    // in here reaches the thread's default handler, which closes the whole app
    // mid sign-out instead of landing the user on the next account or the login.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "session task failed", e) },
    )
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

    /** Guards the logout button against a double tap signing out two accounts. */
    private val _signingOut = MutableStateFlow(false)

    private val _switching = MutableStateFlow(0L)

    /**
     * The account id a switch is in flight for, 0 when idle. The picker holds
     * itself open on this: a switch the server refuses lands back on the
     * previous account, and closing on the tap would make that look like the
     * tap did nothing.
     */
    val switching: StateFlow<Long> = _switching.asStateFlow()

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
                    val rest = usableSlots(exceptUserId = active.userId)
                    val next = rest.firstOrNull()
                    if (next != null) {
                        persist(rest, next.userId)
                        activateLocal(next)
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
                    persist(emptyList(), 0L)
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
        // A renew re-adopts the same account; a login or an added account makes
        // a different one active, and only that has to re-dial the stream.
        val changed = currentUserId != session.user.id
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
        if (changed) LiveFeed.restart()
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
        // Marked before the lock is taken, so the picker sees the switch as in
        // flight even while it waits behind a resync. One at a time: a second
        // tap mid-flight would leave the picker reporting the wrong row.
        if (!_switching.compareAndSet(0L, userId)) return
        scope.launch {
            try {
                switchLock.withLock { switchInto(userId) }
            } finally {
                _switching.value = 0L
            }
        }
    }

    private suspend fun switchInto(userId: Long) {
        val slot = _accounts.value.firstOrNull { it.userId == userId }
        if (slot == null) {
            _switchError.value = "That account isn’t saved on this device."
            return
        }
        if (slot.tokens.isRefreshExpired(System.currentTimeMillis())) {
            removeSlot(userId)
            _switchError.value = "That account’s session expired. Sign in again."
            return
        }
        // Where to land if the server refuses: the account in use right now,
        // read before the swap, with whatever tokens it currently holds.
        val previous = _accounts.value.firstOrNull { it.userId == currentUserId }
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
            // The slot is unusable for good — a ban, or a refresh the server has
            // retired. Forget it and fall back, so the picker stops offering a
            // row that can never work.
            response.isSessionExpired || response.code == "blocked" -> dropActiveAndFallback(
                message = response.message.ifBlank {
                    if (response.code == "blocked") "This account is blocked or restricted."
                    else "That account’s session expired. Sign in again."
                },
            )
            // Anything else is this attempt failing, not the account dying. Put
            // the previous session back and keep the slot: a 500 must not delete
            // a saved login the user can still sign in to.
            else -> {
                if (previous != null) {
                    val restored = previous.copy(lastUsedAt = System.currentTimeMillis())
                    activateLocal(restored)
                    persist(
                        _accounts.value
                            .map { if (it.userId == restored.userId) restored else it }
                            .sortedByDescending { it.lastUsedAt },
                        restored.userId,
                    )
                    armExpiryWatch()
                }
                _switchError.value = response.message.ifBlank { "Couldn’t switch to that account." }
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
                if (userId == currentUserId) {
                    signOutActive()
                } else {
                    persist(_accounts.value.filterNot { it.userId == userId }, currentUserId)
                }
            }
        }
    }

    /**
     * Signs out the active account and nothing else.
     *
     * The next saved login becomes active; only when none is left does the app
     * fall back to the sign-in wall. A second tap while the first is still in
     * flight is dropped — re-entering here would sign out the account that just
     * became active and look like one tap clearing every saved login.
     */
    fun signOut() {
        if (!_signingOut.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                switchLock.withLock { signOutActive() }
            } finally {
                _signingOut.value = false
            }
        }
    }

    /**
     * Drops the active slot, then lands on the newest saved account that still
     * has a live refresh, or on [AuthState.SignedOut] when there is none.
     *
     * Never wipes the whole store: a deliberate sign-out of one account must
     * leave the other saved logins on the device. Never raises
     * [sessionExpired] either — the user asked to leave an account, and the
     * blocking sheet would cover the login screen they asked for.
     *
     * Caller holds [switchLock].
     */
    private suspend fun signOutActive() {
        val id = currentUserId
        val refresh = tokens?.refreshToken
        val rest = usableSlots(exceptUserId = id)
        clearActiveLocal()
        _addingAccount.value = false
        _addAccountStartSignup.value = false
        _switchError.value = ""
        _sessionExpired.value = false
        // Best-effort revoke: the slot is gone locally either way, so an offline
        // sign-out must not leave the user signed in.
        if (id != 0L && !refresh.isNullOrBlank()) {
            scope.launch { AuthApi(api).logout(refresh) }
        }
        val next = rest.firstOrNull()
        if (next == null) {
            persist(emptyList(), 0L)
            _state.value = AuthState.SignedOut
            return
        }
        persist(rest, next.userId)
        activateLocal(next)
        armExpiryWatch()
        resync()
    }

    fun expire() = expire(currentUserId)

    /**
     * Ends [userId]'s session and falls back to another saved account.
     *
     * The id matters: this waits on the switch lock, so by the time it runs the
     * user may have switched to a different account whose session is perfectly
     * good. Signing that one out instead would look like the app losing logins
     * at random.
     */
    private fun expire(userId: Long) {
        scope.launch {
            switchLock.withLock {
                if (userId != currentUserId) return@withLock
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
        val changed = currentUserId != slot.userId
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
        // A different account means a different bearer on the live stream. Dial
        // again, or the socket keeps pushing the previous account's profile,
        // balance and chat over the one that just became active.
        if (changed) LiveFeed.restart()
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

    /**
     * Saved logins other than [exceptUserId] that can still be activated without
     * a password, newest-used first. An expired slot would only arm the expiry
     * watch and bounce straight back, so it is never a fallback target.
     */
    private fun usableSlots(exceptUserId: Long): List<SavedAccount> {
        val now = System.currentTimeMillis()
        return _accounts.value
            .filterNot { it.userId == exceptUserId }
            .filterNot { it.tokens.isRefreshExpired(now) }
    }

    private suspend fun dropActiveAndFallback(message: String, raiseExpiryIfEmpty: Boolean = false) {
        val deadId = currentUserId
        val remembered = lastKnownUser
        val refresh = tokens?.refreshToken
        val rest = usableSlots(exceptUserId = deadId)
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
        val owner = currentUserId
        val wait = max(0L, deadline - System.currentTimeMillis())
        expiryWatch = scope.launch {
            delay(wait)
            val current = tokens ?: return@launch
            if (current.isRefreshExpired(System.currentTimeMillis())) {
                expire(owner)
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
        // Whose refresh this is. A switch can land on another account before an
        // expiry queued here is served, and only this account may be dropped.
        val owner = currentUserId
        if (current != null && current.accessToken != staleToken &&
            !current.isAccessExpired(System.currentTimeMillis())
        ) {
            return@withLock current.accessToken
        }

        val refreshToken = current?.refreshToken
        if (refreshToken.isNullOrBlank() || current.isRefreshExpired(System.currentTimeMillis())) {
            expire(owner)
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
                expire(owner)
                null
            }
        }
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}
