import SwiftUI

private let codeLength = 6
private let passwordMin = 8

/// "Forgot password" in one screen: ask who you are, then take the code and the new
/// password. Splitting it over two pushed screens loses the identifier when the user
/// leaves to read the email and comes back to a cold-started app.
struct PasswordResetView: View {
    let busy: Bool
    let error: String
    let destination: String
    let onBack: () -> Void
    let onRequestCode: (String) -> Void
    let onSubmit: (String, String, String, String) -> Void

    @Environment(\.uiScale) private var scale

    @State private var identifier = ""
    @State private var code = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var codeSent = false
    @FocusState private var codeFocused: Bool

    private var canRequest: Bool {
        identifier.trimmingCharacters(in: .whitespaces).count >= 3
    }

    private var canSubmit: Bool {
        code.count == codeLength && password.count >= passwordMin && password == confirm
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
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 12 * scale)

            VStack(spacing: 0) {
                BrandMark(side: 56 * scale)

                AppText("Reset your password", style: Type.h2, color: Light.text, alignment: .center)
                    .padding(.top, 16 * scale)

                AppText(
                    codeSent
                        ? (destination.isEmpty
                            ? "Enter the code we sent you."
                            : "We sent a code to \(destination).")
                        : "Tell us your email, username or phone and we'll send a code.",
                    style: Type.caption,
                    color: Light.hint,
                    alignment: .center
                )
                .padding(.top, 6 * scale)

                VStack(spacing: 12 * scale) {
                    LightField(
                        value: $identifier,
                        placeholder: "Email, username or phone",
                        kind: .email,
                        maxCharacters: 254
                    )
                    .disabled(codeSent)

                    if codeSent {
                        OtpInput(
                            code: $code,
                            length: codeLength,
                            verified: code.count == codeLength,
                            focused: $codeFocused
                        )
                        .padding(.vertical, 4 * scale)

                        PasswordField(
                            value: $password,
                            placeholder: "New password (at least \(passwordMin) characters)",
                            showStrength: true
                        )

                        PasswordField(
                            value: $confirm,
                            placeholder: "Confirm new password",
                            submitLabel: .done
                        )

                        if !confirm.isEmpty, confirm != password {
                            AppText(
                                "Those passwords don't match.",
                                style: Type.caption,
                                color: Light.error
                            )
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: Light.error, alignment: .center)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.top, 24 * scale)
            }
            .padding(.horizontal, 28 * scale)

            VStack(spacing: 14 * scale) {
                if codeSent {
                    PrimaryCta(title: "Set new password", enabled: canSubmit, loading: busy) {
                        onSubmit(identifier, code, password, confirm)
                    }

                    TextLink(title: "Send a new code") { onRequestCode(identifier) }
                } else {
                    PrimaryCta(
                        title: "Send code",
                        enabled: canRequest,
                        loading: busy,
                        trailingArrow: true
                    ) {
                        // Flipped locally and unconditionally: the server answers the
                        // same way whether or not the account exists, so the UI cannot
                        // be used to probe which emails are registered.
                        codeSent = true
                        codeFocused = true
                        onRequestCode(identifier)
                    }
                }

                TextLink(title: "Back to log in", color: Light.textSoft, action: onBack)
            }
            .padding(.horizontal, 28 * scale)
            .padding(.top, 20 * scale)
            .padding(.bottom, 40 * scale)
        }
        .statusBar()
    }
}
