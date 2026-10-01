package app.xani.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.xani.core.net.AuthState
import app.xani.core.net.Xani
import app.xani.feature.auth.help.HelpFlow
import app.xani.feature.auth.help.HelpRoute
import app.xani.feature.main.MainShell
import app.xani.ui.brand.LocalBrand

/**
 * Auth navigation is one saveable value rather than a nav-graph library: the flow is
 * linear, it survives rotation and process death, and it adds nothing to the
 * dependency graph or to cold-start cost.
 *
 * The starting route is decided by the restored session, not by a fixed constant. An
 * already-signed-in user goes straight to the app: the splash covers the restore, and
 * onboarding is never shown twice.
 *
 * @param onInteractive fired once, when the first screen the user can act on is
 *   composed. Drives `reportFullyDrawn`.
 */
@Composable
fun AuthFlow(
    onInteractive: () -> Unit = { },
    onAuthenticated: () -> Unit,
) {
    val brand = LocalBrand.current
    val context = LocalContext.current
    val controller = rememberAuthController()
    val authState by Xani.session.state.collectAsState()
    val sessionExpired by Xani.session.sessionExpired.collectAsState()

    var route by rememberSaveable { mutableStateOf(AuthRoute.Splash) }
    var otpDestination by rememberSaveable { mutableStateOf("") }
    var help by rememberSaveable { mutableStateOf("") }
    var resetCodeSent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(route != AuthRoute.Splash) {
        if (route != AuthRoute.Splash) onInteractive()
    }

    fun enterApp() {
        route = AuthRoute.Authenticated
        onAuthenticated()
    }

    fun startProvider(provider: String) {
        if (provider == "google") {
            controller.signInWithGoogle(context) { created ->
                if (created) {
                    route = AuthRoute.CompleteProfile
                } else {
                    enterApp()
                }
            }
        } else {
            controller.reportUnsupportedProvider(provider)
        }
    }

    // Restoring a saved session is a network round trip, so it can land after the
    // splash has already moved on. Onboarding is the only route that auto-advances:
    // past it the user is deliberately on a form, and signup adopts a session of its
    // own that must not skip the verification step.
    //
    // Sign-out from Profile/Settings flips to SignedOut while we are still on
    // Authenticated — send the user back to the auth wall without losing the flow.
    // Keep Authenticated under the expiry card; only leave for a deliberate sign-out.
    // Adding another account must not treat adopt as "leave login" mid-overlay.
    val addingAccount by Xani.session.addingAccount.collectAsState()

    LaunchedEffect(authState, route, sessionExpired, addingAccount) {
        when {
            route == AuthRoute.Onboarding && authState is AuthState.SignedIn && !addingAccount -> enterApp()
            route == AuthRoute.Authenticated && authState is AuthState.SignedOut && !sessionExpired && !addingAccount -> {
                controller.clearMessages()
                route = AuthRoute.Choice
            }
            route == AuthRoute.Choice && authState is AuthState.SignedIn && !addingAccount -> enterApp()
            route == AuthRoute.LogIn && authState is AuthState.SignedIn && !addingAccount -> enterApp()
        }
    }

    if (help.isNotEmpty()) {
        HelpFlow(
            start = HelpRoute.entries.find { it.name == help } ?: HelpRoute.Faq,
            onClose = { help = "" },
        )
        return
    }

    val backTarget = when (route) {
        AuthRoute.SignUp, AuthRoute.LogIn, AuthRoute.ResetPassword -> AuthRoute.Choice
        // Verification is post-signup: the account exists and the session is live, so
        // "back" means "do it later", not "undo the signup".
        AuthRoute.VerifyOtp -> AuthRoute.Authenticated
        else -> null
    }
    BackHandler(enabled = backTarget != null) {
        backTarget?.let {
            controller.clearMessages()
            if (it == AuthRoute.Authenticated) enterApp() else route = it
        }
    }

    when (route) {
        AuthRoute.Splash -> SplashScreen(
            onDone = {
                // Restore has had the splash's minimum duration to finish. A session
                // that survived skips onboarding and the sign-in wall entirely.
                route = if (authState is AuthState.SignedIn) {
                    onAuthenticated()
                    AuthRoute.Authenticated
                } else {
                    AuthRoute.Onboarding
                }
            },
        )

        AuthRoute.Onboarding -> OnboardingScreen(
            onDone = { route = AuthRoute.Choice },
        )

        AuthRoute.Choice -> AuthChoiceScreen(
            appName = brand.name,
            busy = controller.busy,
            error = controller.error,
            onEmail = {
                controller.clearMessages()
                route = AuthRoute.SignUp
            },
            onProvider = { provider -> startProvider(provider) },
            onLogIn = {
                controller.clearMessages()
                route = AuthRoute.LogIn
            },
            onHelp = { help = HelpRoute.Faq.name },
        )

        AuthRoute.SignUp -> SignUpScreen(
            appName = brand.name,
            busy = controller.busy,
            error = controller.error,
            onBack = { route = AuthRoute.Choice },
            onHelp = { help = HelpRoute.Faq.name },
            onSubmit = { form ->
                controller.signUp(context, form) { email ->
                    otpDestination = maskDestination(email)
                    route = AuthRoute.VerifyOtp
                }
            },
        )

        AuthRoute.LogIn -> LogInScreen(
            appName = brand.name,
            busy = controller.busy,
            error = controller.error,
            onBack = { route = AuthRoute.Choice },
            onSignUp = {
                controller.clearMessages()
                route = AuthRoute.SignUp
            },
            onHelp = { help = HelpRoute.Faq.name },
            onRecover = {
                controller.clearMessages()
                resetCodeSent = false
                route = AuthRoute.ResetPassword
            },
            onProvider = { provider -> startProvider(provider) },
            // Password login needs no second factor: the server already verified the
            // credential, the device and the ban state in that one call.
            onSubmit = { identifier, password ->
                controller.logIn(identifier, password) { enterApp() }
            },
        )

        AuthRoute.ResetPassword -> PasswordResetScreen(
            busy = controller.busy,
            error = controller.error,
            maskedDestination = controller.codeDestination,
            codeSent = resetCodeSent,
            onBack = { route = AuthRoute.LogIn },
            onRequestCode = { identifier ->
                controller.requestPasswordReset(identifier) { resetCodeSent = true }
            },
            onSubmit = { identifier, code, password, confirm ->
                controller.resetPassword(identifier, code, password, confirm) {
                    resetCodeSent = false
                    enterApp()
                }
            },
        )

        AuthRoute.VerifyOtp -> OtpScreen(
            destination = otpDestination.ifEmpty { controller.codeDestination },
            busy = controller.busy,
            error = controller.error,
            notice = controller.notice,
            secondsLeft = controller.resendSeconds,
            onTick = controller::tickResend,
            onVerify = { code -> controller.verifyEmail(code) { enterApp() } },
            onResend = controller::resendCode,
            onSkip = { enterApp() },
        )

        AuthRoute.CompleteProfile -> {
            val signedIn = authState as? AuthState.SignedIn
            CompleteProfileScreen(
                appName = brand.name,
                user = signedIn?.user ?: Xani.session.lastKnownUser ?: app.xani.core.net.AccountUser.Empty,
                busy = controller.busy,
                error = controller.error,
                onSubmit = { form ->
                    controller.completeSocialProfile(form) { enterApp() }
                },
            )
        }

        AuthRoute.Authenticated -> {
            Box {
                MainShell()
                if (addingAccount) {
                    BackHandler { Xani.session.cancelAddAccount(); controller.clearMessages() }
                    val startSignup by Xani.session.addAccountStartSignup.collectAsState()
                    AddAccountGate(
                        controller = controller,
                        context = context,
                        brandName = brand.name,
                        initialStep = if (startSignup) "signup" else "login",
                        onCancel = {
                            Xani.session.cancelAddAccount()
                            controller.clearMessages()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Full-screen login over the app so the user can add another account without
 * signing out of the current one. Back cancels and returns to the previous account.
 */
@Composable
private fun AddAccountGate(
    controller: AuthController,
    context: android.content.Context,
    brandName: String,
    initialStep: String = "login",
    onCancel: () -> Unit,
) {
    // Local step so signup/recovery stays inside the overlay.
    var step by remember(initialStep) { mutableStateOf(initialStep) }
    var resetCodeSent by rememberSaveable { mutableStateOf(false) }

    when (step) {
        "signup" -> SignUpScreen(
            appName = brandName,
            busy = controller.busy,
            error = controller.error,
            onBack = { step = "login" },
            onHelp = { },
            onSubmit = { form ->
                controller.signUp(context, form) {
                    // New account is adopted; addingAccount clears in adopt().
                    controller.clearMessages()
                }
            },
        )
        "reset" -> PasswordResetScreen(
            busy = controller.busy,
            error = controller.error,
            maskedDestination = controller.codeDestination,
            codeSent = resetCodeSent,
            onBack = { step = "login" },
            onRequestCode = { identifier ->
                controller.requestPasswordReset(identifier) { resetCodeSent = true }
            },
            onSubmit = { identifier, code, password, confirm ->
                controller.resetPassword(identifier, code, password, confirm) {
                    resetCodeSent = false
                    controller.clearMessages()
                }
            },
        )
        else -> LogInScreen(
            appName = brandName,
            busy = controller.busy,
            error = controller.error,
            onBack = onCancel,
            onSignUp = {
                controller.clearMessages()
                step = "signup"
            },
            onHelp = { },
            onRecover = {
                controller.clearMessages()
                resetCodeSent = false
                step = "reset"
            },
            onProvider = { provider ->
                if (provider == "google") {
                    controller.signInWithGoogle(context) { _ ->
                        controller.clearMessages()
                    }
                } else {
                    controller.reportUnsupportedProvider(provider)
                }
            },
            onSubmit = { identifier, password ->
                controller.logIn(identifier, password) {
                    controller.clearMessages()
                }
            },
        )
    }
}
