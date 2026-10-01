import SwiftUI

/// Wraps the app and, when the refresh window closes, covers it with a sign-in
/// sheet that cannot be dismissed.
///
/// Presented as an overlay rather than a `.sheet` on purpose: a sheet is swipe-to-close
/// on iOS and the requirement is that this one is not cancellable. The content below
/// stays mounted, so scroll position, drafts and playback survive signing back in.
struct SessionExpiredGate<Content: View>: View {
    @ViewBuilder let content: () -> Content

    @EnvironmentObject private var session: SessionManager
    @StateObject private var controller = AuthController()
    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale

    @State private var identifier = ""
    @State private var password = ""

    var body: some View {
        ZStack {
            content()

            if session.sessionExpired {
                sheet
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .animation(.easeOut(duration: 0.18), value: session.sessionExpired)
        .onChange(of: session.sessionExpired) { expired in
            if expired {
                identifier = session.lastKnownUser?.email.isEmpty == false
                    ? session.lastKnownUser?.email ?? ""
                    : session.lastKnownUser?.username ?? ""
                password = ""
                controller.clearMessages()
            }
        }
    }

    private var valid: Bool {
        identifier.trimmingCharacters(in: .whitespaces).count >= 3 && password.count >= 6
    }

    private var sheet: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.72)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {}

            VStack(spacing: 0) {
                AppText("Your session ended", style: Type.h2, color: Dark.foreground)

                AppText(
                    "Sign in again to keep using \(brand.name).",
                    style: Type.caption,
                    color: Dark.dim
                )
                .padding(.top, 6 * scale)

                VStack(spacing: 12 * scale) {
                    LightField(
                        value: $identifier,
                        placeholder: "Email, username or phone",
                        kind: .email,
                        maxCharacters: 254
                    )

                    PasswordField(
                        value: $password,
                        placeholder: "Password",
                        submitLabel: .done,
                        onSubmit: signIn
                    )

                    if !controller.error.isEmpty {
                        AppText(
                            controller.error,
                            style: Type.caption,
                            color: Dark.error,
                            alignment: .center
                        )
                        .frame(maxWidth: .infinity)
                    }
                }
                .padding(.top, 20 * scale)

                DarkCta(title: "Sign in", enabled: valid, loading: controller.busy, action: signIn)
                    .padding(.top, 18 * scale)

                if controller.googleAvailable {
                    TextLink(title: "Continue with Google", color: Dark.primarySoft) {
                        controller.signInWithGoogle { session.dismissExpiryAfterSignIn() }
                    }
                    .padding(.top, 16 * scale)
                }

                TextLink(title: "Use a different account", color: Dark.dim) {
                    session.signOut()
                }
                .padding(.top, 16 * scale)
            }
            .padding(.horizontal, 24 * scale)
            .padding(.top, 28 * scale)
            .padding(.bottom, 36 * scale)
            .frame(maxWidth: .infinity)
            .background(Dark.card)
            .clipShape(
                RoundedRectangle(cornerRadius: 28 * scale, style: .continuous)
            )
            .ignoresSafeArea(edges: .bottom)
        }
    }

    private func signIn() {
        guard valid else { return }
        controller.logIn(identifier: identifier, password: password) {
            password = ""
            session.dismissExpiryAfterSignIn()
        }
    }
}
