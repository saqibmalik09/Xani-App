import SwiftUI

struct LogInView: View {
    let busy: Bool
    let error: String
    let onBack: () -> Void
    let onSignUp: () -> Void
    let onHelp: () -> Void
    let onRecover: () -> Void
    let onProvider: (String) -> Void
    let onSubmit: (String, String) -> Void

    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale

    @State private var identifier = ""
    @State private var password = ""

    /// Length only. The server decides whether an identifier resolves to an account and
    /// whether the password is right; guessing here would just refuse valid logins for
    /// people whose password predates a policy change.
    private var valid: Bool {
        identifier.trimmingCharacters(in: .whitespaces).count >= 3 && password.count >= 6
    }

    private func submit() {
        guard valid else { return }
        onSubmit(identifier, password)
    }

    var body: some View {
        AuthScaffold(background: Light.background) {
            HStack {
                CircleIconButton(
                    diameter: 36 * scale,
                    borderColor: Light.borderIcon,
                    accessibilityName: "Back",
                    action: onBack
                ) {
                    Icon.back(16 * scale)
                }
                Spacer(minLength: 0)
                CircleIconButton(
                    diameter: 36 * scale,
                    borderColor: Light.borderIcon,
                    accessibilityName: "Help",
                    action: onHelp
                ) {
                    Icon.help(16 * scale)
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 12 * scale)

            VStack(spacing: 0) {
                BrandMark(side: 60 * scale)

                AppText(
                    "Log in to \(brand.name)",
                    style: Type.h2,
                    color: Light.text,
                    alignment: .center
                )
                .padding(.top, 18 * scale)

                AppText("Enter your details to continue", style: Type.caption, color: Light.hint)
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
                        onSubmit: submit
                    )

                    HStack {
                        Spacer(minLength: 0)
                        TextLink(title: "Forgot password?") { onRecover() }
                    }

                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: Light.error, alignment: .center)
                            .frame(maxWidth: .infinity)
                    }

                    PrimaryCta(title: "Log in", enabled: valid, loading: busy, action: submit)

                    HStack(spacing: 12 * scale) {
                        Rectangle().fill(Light.divider).frame(height: 1 * scale)
                        AppText("or", style: Type.micro, color: Light.muted)
                        Rectangle().fill(Light.divider).frame(height: 1 * scale)
                    }

                    HStack(spacing: 10 * scale) {
                        ProviderTile(accessibilityName: "Continue with Google") {
                            onProvider("google")
                        } glyph: {
                            Icon.google(20 * scale)
                        }
                        ProviderTile(accessibilityName: "Continue with Apple") {
                            onProvider("apple")
                        } glyph: {
                            Icon.apple(20 * scale)
                        }
                        ProviderTile(accessibilityName: "Continue with Facebook") {
                            onProvider("facebook")
                        } glyph: {
                            Icon.facebook(20 * scale)
                        }
                    }
                }
                .padding(.top, 28 * scale)
            }
            .padding(.horizontal, 28 * scale)

            HStack(spacing: 4 * scale) {
                AppText("Don't have an account?", style: Type.label, color: Light.textSoft)
                TextLink(title: "Sign up", action: onSignUp)
            }
            .padding(.horizontal, 28 * scale)
            .padding(.top, 20 * scale)
            .padding(.bottom, 40 * scale)
        }
        .statusBar()
    }
}
