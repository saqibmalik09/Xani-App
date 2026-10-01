import SwiftUI

/// Auth navigation is one enum rather than a `NavigationStack`: the flow is linear,
/// `NavigationStack` needs iOS 16, and a switch keeps exactly one screen alive at a
/// time instead of retaining a back stack of view state.
struct AuthFlow: View {
    let onAuthenticated: () -> Void

    @Environment(\.brand) private var brand
    @EnvironmentObject private var session: SessionManager
    @StateObject private var controller = AuthController()

    @SceneStorage("auth.route") private var storedRoute: String = AuthRoute.splash.rawValue
    @SceneStorage("auth.help") private var help: String = ""

    private var route: AuthRoute {
        AuthRoute(rawValue: storedRoute) ?? .splash
    }

    var body: some View {
        Group {
            if let helpRoute = HelpRoute(rawValue: help) {
                HelpFlow(start: helpRoute, onClose: { help = "" })
            } else {
                content
            }
        }
        .animation(nil, value: storedRoute)
        .onChange(of: session.state) { state in
            handleAuthChange(state)
        }
        .onChange(of: session.sessionExpired) { expired in
            if !expired { handleAuthChange(session.state) }
        }
        .onChange(of: session.addingAccount) { adding in
            if !adding { controller.clearMessages() }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case .splash:
            SplashView {
                // A returning user with a live session goes straight in; onboarding and
                // the auth screens are for people who have no session at all.
                if session.isSignedIn {
                    enterApp()
                } else {
                    go(.onboarding)
                }
            }

        case .onboarding:
            OnboardingView { go(.choice) }

        case .choice:
            AuthChoiceView(
                busy: controller.busy,
                error: controller.error,
                onEmail: { go(.signUp) },
                onProvider: { provider in start(provider) },
                onLogIn: { go(.logIn) },
                onHelp: { help = HelpRoute.faq.rawValue }
            )

        case .signUp:
            SignUpView(
                busy: controller.busy,
                error: controller.error,
                onBack: { go(.choice) },
                onHelp: { help = HelpRoute.faq.rawValue },
                onSubmit: { form in
                    controller.signUp(form) { _ in go(.verifyOtp) }
                }
            )

        case .logIn:
            LogInView(
                busy: controller.busy,
                error: controller.error,
                onBack: { go(.choice) },
                onSignUp: { go(.signUp) },
                onHelp: { help = HelpRoute.faq.rawValue },
                onRecover: { go(.resetPassword) },
                onProvider: { provider in start(provider) },
                onSubmit: { identifier, password in
                    controller.logIn(identifier: identifier, password: password) {
                        enterApp()
                    }
                }
            )

        case .resetPassword:
            PasswordResetView(
                busy: controller.busy,
                error: controller.error,
                destination: controller.codeDestination,
                onBack: { go(.logIn) },
                onRequestCode: { identifier in
                    controller.requestPasswordReset(identifier: identifier) {}
                },
                onSubmit: { identifier, code, password, confirm in
                    controller.resetPassword(
                        identifier: identifier,
                        code: code,
                        newPassword: password,
                        confirmPassword: confirm
                    ) {
                        enterApp()
                    }
                }
            )

        case .verifyOtp:
            OtpView(
                destination: controller.codeDestination,
                busy: controller.busy,
                error: controller.error,
                notice: controller.notice,
                secondsLeft: controller.resendSeconds,
                onTick: controller.tickResend,
                onVerify: { code in controller.verifyEmail(code: code) { enterApp() } },
                onResend: controller.resendCode,
                onSkip: { enterApp() }
            )

        case .completeProfile:
            CompleteProfileView(
                user: {
                    if case let .signedIn(user, _) = session.state { return user }
                    return session.lastKnownUser ?? .empty
                }(),
                busy: controller.busy,
                error: controller.error,
                onSubmit: { form in
                    controller.completeSocialProfile(form) { enterApp() }
                }
            )

        case .authenticated:
            ZStack {
                MainShell()
                if session.addingAccount {
                    AddAccountGate(
                        controller: controller,
                        startSignup: session.addAccountStartSignup,
                        onCancel: {
                            session.cancelAddAccount()
                            controller.clearMessages()
                        }
                    )
                    .transition(.opacity)
                    .zIndex(100)
                }
            }
            .animation(.easeOut(duration: 0.2), value: session.addingAccount)
        }
    }

    private func start(_ provider: String) {
        guard provider == "google" else {
            controller.reportUnsupportedProvider(provider)
            return
        }
        controller.signInWithGoogle { created in
            if created {
                go(.completeProfile)
            } else {
                enterApp()
            }
        }
    }

    private func handleAuthChange(_ state: AuthState) {
        // Adding another account must not treat adopt as "leave login" mid-overlay,
        // and must not bounce Authenticated → Choice while the overlay is open.
        switch state {
        case .signedIn where route == .onboarding && !session.addingAccount:
            enterApp()
        case .signedIn where (route == .choice || route == .logIn) && !session.addingAccount:
            enterApp()
        case .signedOut where route == .authenticated && !session.sessionExpired && !session.addingAccount:
            go(.choice)
        default:
            break
        }
    }

    private func enterApp() {
        go(.authenticated)
        onAuthenticated()
    }

    private func go(_ next: AuthRoute) {
        controller.clearMessages()
        storedRoute = next.rawValue
    }
}

/// Full-screen login over the app so the user can add another account without
/// signing out of the current one. Back cancels and returns to the previous account.
private struct AddAccountGate: View {
    @ObservedObject var controller: AuthController
    var startSignup: Bool = false
    var onCancel: () -> Void

    @State private var step: String

    init(controller: AuthController, startSignup: Bool = false, onCancel: @escaping () -> Void) {
        self.controller = controller
        self.startSignup = startSignup
        self.onCancel = onCancel
        _step = State(initialValue: startSignup ? "signup" : "login")
    }

    var body: some View {
        Group {
            switch step {
            case "signup":
                SignUpView(
                    busy: controller.busy,
                    error: controller.error,
                    onBack: { step = "login" },
                    onHelp: {},
                    onSubmit: { form in
                        controller.signUp(form) { _ in
                            // New account is adopted; addingAccount clears in adopt().
                            controller.clearMessages()
                        }
                    }
                )
            case "reset":
                PasswordResetView(
                    busy: controller.busy,
                    error: controller.error,
                    destination: controller.codeDestination,
                    onBack: { step = "login" },
                    onRequestCode: { identifier in
                        controller.requestPasswordReset(identifier: identifier) {}
                    },
                    onSubmit: { identifier, code, password, confirm in
                        controller.resetPassword(
                            identifier: identifier,
                            code: code,
                            newPassword: password,
                            confirmPassword: confirm
                        ) {
                            controller.clearMessages()
                        }
                    }
                )
            default:
                LogInView(
                    busy: controller.busy,
                    error: controller.error,
                    onBack: onCancel,
                    onSignUp: {
                        controller.clearMessages()
                        step = "signup"
                    },
                    onHelp: {},
                    onRecover: {
                        controller.clearMessages()
                        step = "reset"
                    },
                    onProvider: { provider in
                        guard provider == "google" else {
                            controller.reportUnsupportedProvider(provider)
                            return
                        }
                        controller.signInWithGoogle { _ in
                            controller.clearMessages()
                        }
                    },
                    onSubmit: { identifier, password in
                        controller.logIn(identifier: identifier, password: password) {
                            controller.clearMessages()
                        }
                    }
                )
            }
        }
        .background(Light.background.ignoresSafeArea())
    }
}
