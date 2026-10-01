import SwiftUI

/// What the signup form collected.
struct SignUpForm {
    let fullName: String
    let username: String
    let email: String
    let password: String
}

/// Drives every auth screen's network work and owns the busy/error state they render.
///
/// It exists so views stay declarative: a view reports what the user did, reads `busy`
/// and `error`, and never touches the API, tokens or navigation. One in-flight request
/// at a time, enforced by `busy`, because double-tapping "Sign up" must not create two
/// accounts.
@MainActor
final class AuthController: ObservableObject {

    @Published private(set) var busy = false

    /// Human-readable failure for the current form. Cleared on the next attempt.
    @Published private(set) var error = ""

    /// Non-error status, e.g. "We sent a new code."
    @Published private(set) var notice = ""

    /// Seconds until a code can be resent, counted down by the verification view.
    @Published private(set) var resendSeconds = 0

    /// Masked destination a code was sent to, for "Sent to jo****n@gmail.com".
    @Published private(set) var codeDestination = ""

    var googleAvailable: Bool { Xani.googleAvailable }

    func clearMessages() {
        error = ""
        notice = ""
    }

    /// Apple and Facebook are drawn in the Figma auth sheet but not enabled server-side
    /// yet. Saying so is better than a button that silently does nothing.
    func reportUnsupportedProvider(_ provider: String) {
        error = "\(provider.capitalized) sign-in isn't available yet. Use Google or your email."
    }

    // MARK: - Sign up / sign in

    /// The country is taken from the device region. Asking a new user to scroll a
    /// 240-row picker before they can create an account is the single easiest place to
    /// lose them, and the value is correctable later from the profile.
    func signUp(_ form: SignUpForm, onDone: @escaping (String) -> Void) {
        perform {
            let response = await Xani.auth.signUpWithEmail(
                fullName: form.fullName.trimmingCharacters(in: .whitespaces),
                username: form.username.trimmingCharacters(in: .whitespaces),
                email: form.email.trimmingCharacters(in: .whitespaces),
                password: form.password,
                country: DialCountries.deviceIso()
            )
            guard let session = self.unwrap(response) else { return }
            Xani.session.adopt(session)
            self.codeDestination = maskDestination(form.email)
            self.resendSeconds = 60
            onDone(form.email)
        }
    }

    func logIn(identifier: String, password: String, onDone: @escaping () -> Void) {
        perform {
            let response = await Xani.auth.logIn(
                identifier: identifier.trimmingCharacters(in: .whitespaces),
                password: password
            )
            guard let session = self.unwrap(response) else { return }
            Xani.session.adopt(session)
            onDone()
        }
    }

    func signInWithGoogle(onDone: @escaping (_ created: Bool) -> Void) {
        perform {
            guard let idToken = await Xani.google.idToken(), !idToken.isEmpty else {
                // Cancelled, or this build has no Google provider wired.
                if !Xani.googleAvailable {
                    self.error = "Google sign-in isn't available in this build. Use your email."
                }
                return
            }
            let response = await Xani.auth.signInWithGoogle(
                idToken: idToken,
                country: DialCountries.deviceIso()
            )
            guard let session = self.unwrap(response) else { return }
            Xani.session.adopt(session)
            onDone(session.created)
        }
    }

    func completeSocialProfile(_ form: CompleteProfileForm, onDone: @escaping () -> Void) {
        perform {
            let response = await Xani.auth.completeSocialProfile(
                fullName: form.fullName,
                username: form.username,
                birthday: form.birthday,
                newPassword: form.password,
                confirmPassword: form.confirmPassword
            )
            guard let payload = self.unwrap(response) else { return }
            Xani.session.updateUser(payload.user)
            onDone()
        }
    }

    // MARK: - Email verification

    /// Verification is not a gate: the account already works. Failing to receive an
    /// email must not be able to lock someone out of an account they just made, so this
    /// screen is skippable and the flag only affects privileged actions.
    func verifyEmail(code: String, onDone: @escaping () -> Void) {
        perform {
            let response = await Xani.auth.verifyEmail(code: code)
            if response.ok {
                await Xani.session.resync()
                onDone()
            } else {
                self.error = response.message
            }
        }
    }

    func resendCode() {
        perform {
            let response = await Xani.auth.resendEmailCode()
            guard let issued = self.unwrap(response) else { return }
            self.resendSeconds = issued.retryAfterSeconds
            if !issued.maskedTo.isEmpty { self.codeDestination = issued.maskedTo }
            self.notice = "We sent a new code."
        }
    }

    func tickResend() {
        if resendSeconds > 0 { resendSeconds -= 1 }
    }

    // MARK: - Recovery

    /// Sends a reset code. Always reports success — see the server-side reasoning.
    func requestPasswordReset(identifier: String, onSent: @escaping () -> Void) {
        perform {
            let response = await Xani.auth.requestPasswordReset(
                identifier: identifier.trimmingCharacters(in: .whitespaces)
            )
            guard let issued = self.unwrap(response) else { return }
            self.codeDestination = issued.maskedTo
            self.resendSeconds = issued.retryAfterSeconds
            onSent()
        }
    }

    /// Resets the password and signs straight back in.
    ///
    /// The sign-in happens inside this one action rather than as a second call from the
    /// view: `perform` rejects a second request while `busy`, so a chained
    /// `resetPassword { logIn() }` would silently do nothing. The reset revoked every
    /// session server-side, so a fresh login is required either way — doing it here
    /// saves making the user retype a password they chose ten seconds ago.
    func resetPassword(
        identifier: String,
        code: String,
        newPassword: String,
        confirmPassword: String,
        onSignedIn: @escaping () -> Void
    ) {
        perform {
            let trimmed = identifier.trimmingCharacters(in: .whitespaces)
            let reset = await Xani.auth.resetPassword(
                identifier: trimmed,
                code: code,
                newPassword: newPassword,
                confirmPassword: confirmPassword
            )
            guard reset.ok else {
                self.error = reset.message
                return
            }
            let login = await Xani.auth.logIn(identifier: trimmed, password: newPassword)
            guard let session = self.unwrap(login) else { return }
            Xani.session.adopt(session)
            onSignedIn()
        }
    }

    // MARK: - Plumbing

    private func unwrap<T>(_ response: ApiResponse<T>) -> T? {
        guard response.ok, let value = response.value else {
            error = response.message
            return nil
        }
        return value
    }

    private func perform(_ work: @escaping () async -> Void) {
        guard !busy else { return }
        busy = true
        clearMessages()
        Task {
            await work()
            busy = false
        }
    }
}
