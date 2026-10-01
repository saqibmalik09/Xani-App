package app.xani.feature.auth

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.xani.core.net.ApiResponse
import app.xani.core.net.GoogleTokenResult
import app.xani.core.net.Xani
import app.xani.feature.auth.help.DialCountries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the signup form collected. */
data class SignUpForm(
    val fullName: String,
    val username: String,
    val email: String,
    val password: String,
)

/**
 * Drives every auth screen's network work and owns the busy/error state they render.
 *
 * It exists so screens stay declarative: a screen reports what the user did, reads
 * [busy] and [error], and never touches the API, tokens or navigation. One in-flight
 * request at a time, enforced by [busy], because double-tapping "Sign up" must not
 * create two accounts.
 */
@Stable
class AuthController(private val scope: CoroutineScope) {

    var busy by mutableStateOf(false)
        private set

    /** Human-readable failure for the current form. Cleared on the next attempt. */
    var error by mutableStateOf("")
        private set

    /** Non-error status, e.g. "We sent a new code." */
    var notice by mutableStateOf("")
        private set

    /** Seconds until a code can be resent, counted down by the OTP screen. */
    var resendSeconds by mutableIntStateOf(0)
        private set

    /** Masked destination a code was sent to, for "Sent to jo****n@gmail.com". */
    var codeDestination by mutableStateOf("")
        private set

    val googleAvailable: Boolean get() = Xani.isReady && Xani.googleAvailable

    fun clearMessages() {
        error = ""
        notice = ""
    }

    /**
     * Apple and Facebook are drawn in the Figma auth sheet but are not enabled
     * server-side yet. Saying so beats a button that silently does nothing.
     */
    fun reportUnsupportedProvider(provider: String) {
        error = "${provider.replaceFirstChar(Char::titlecase)} sign-in isn't available yet. " +
            "Use Google or your email."
    }

    // ─── sign up / sign in ───────────────────────────────────────────────────

    /**
     * The country is detected from the SIM, then the carrier network, then the
     * locale. Asking a new user to scroll a 240-row picker before they can create an
     * account is the single easiest place to lose them, and the value is correctable
     * later from the profile.
     */
    fun signUp(context: Context, form: SignUpForm, onDone: (createdEmail: String) -> Unit) {
        run {
            val country = DialCountries.deviceIso(context)
            val response = Xani.auth.signUpWithEmail(
                fullName = form.fullName.trim(),
                username = form.username.trim(),
                email = form.email.trim(),
                password = form.password,
                country = country,
            )
            handle(response) { session ->
                Xani.session.adopt(session)
                codeDestination = maskDestination(form.email)
                resendSeconds = 60
                onDone(form.email.trim())
            }
        }
    }

    fun logIn(identifier: String, password: String, onDone: () -> Unit) {
        run {
            val response = Xani.auth.logIn(identifier.trim(), password)
            handle(response) { session ->
                Xani.session.adopt(session)
                onDone()
            }
        }
    }

    fun signInWithGoogle(context: Context, onDone: (created: Boolean) -> Unit) {
        run {
            when (val got = Xani.google.idToken(context)) {
                is GoogleTokenResult.Cancelled -> return@run
                is GoogleTokenResult.Failed -> {
                    error = got.message
                    return@run
                }
                is GoogleTokenResult.Ok -> {
                    val response = Xani.auth.signInWithGoogle(got.idToken, DialCountries.deviceIso(context))
                    handle(response) { session ->
                        Xani.session.adopt(session)
                        onDone(session.created)
                    }
                }
            }
        }
    }

    fun completeSocialProfile(form: CompleteProfileForm, onDone: () -> Unit) {
        run {
            val response = Xani.auth.completeSocialProfile(
                fullName = form.fullName,
                username = form.username,
                birthday = form.birthday,
                newPassword = form.password,
                confirmPassword = form.confirmPassword,
            )
            handle(response) { user ->
                Xani.session.updateUser(user)
                onDone()
            }
        }
    }

    // ─── email verification ──────────────────────────────────────────────────

    /**
     * Verification is not a gate: the account already works. Failing to receive an
     * email must not be able to lock someone out of an account they just made, so
     * this screen is skippable and the flag only affects privileged actions.
     */
    fun verifyEmail(code: String, onDone: () -> Unit) {
        run {
            val response = Xani.auth.verifyEmail(code)
            if (response.ok) {
                // Re-sync before navigating: the verified flag can unlock permissions
                // the next screen gates on.
                Xani.session.resync()
                onDone()
            } else {
                error = response.message
            }
        }
    }

    fun resendCode() {
        run {
            val response = Xani.auth.resendEmailCode()
            handle(response) { issued ->
                resendSeconds = issued.retryAfterSeconds
                codeDestination = issued.maskedTo.ifEmpty { codeDestination }
                notice = "We sent a new code."
            }
        }
    }

    fun tickResend() {
        if (resendSeconds > 0) resendSeconds -= 1
    }

    // ─── recovery ────────────────────────────────────────────────────────────

    /** Sends a reset code. Always reports success — see the server-side reasoning. */
    fun requestPasswordReset(identifier: String, onSent: () -> Unit) {
        run {
            val response = Xani.auth.requestPasswordReset(identifier.trim())
            handle(response) { issued ->
                codeDestination = issued.maskedTo
                resendSeconds = issued.retryAfterSeconds
                onSent()
            }
        }
    }

    /**
     * Resets the password and signs straight back in.
     *
     * The sign-in happens inside this one action rather than as a second call from the
     * screen: [run] rejects a second request while [busy], so a chained
     * `resetPassword { logIn() }` would silently do nothing. The reset revoked every
     * session server-side, so a fresh login is required either way — doing it here
     * saves making the user retype a password they chose ten seconds ago.
     */
    fun resetPassword(
        identifier: String,
        code: String,
        newPassword: String,
        confirmPassword: String,
        onSignedIn: () -> Unit,
    ) {
        run {
            val reset = Xani.auth.resetPassword(identifier.trim(), code, newPassword, confirmPassword)
            if (!reset.ok) {
                error = reset.message
                return@run
            }
            handle(Xani.auth.logIn(identifier.trim(), newPassword)) { session ->
                Xani.session.adopt(session)
                onSignedIn()
            }
        }
    }

    // ─── plumbing ────────────────────────────────────────────────────────────

    private fun <T> handle(response: ApiResponse<T>, onSuccess: (T) -> Unit) {
        val value = response.value
        if (response.ok && value != null) {
            onSuccess(value)
        } else {
            error = response.message.ifBlank {
                when {
                    response.code.isNotBlank() -> response.code
                    else -> "Request failed. Please try again."
                }
            }
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        clearMessages()
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                // The transport already folds IO failures into envelopes, so reaching
                // here means a genuine bug. One screen reports it; the app survives.
                Log.w(TAG, "auth action failed", t)
                error = "Something went wrong. Please try again."
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val TAG = "AuthController"
    }
}

@Composable
fun rememberAuthController(): AuthController {
    val scope = rememberCoroutineScope()
    return remember(scope) { AuthController(scope) }
}
