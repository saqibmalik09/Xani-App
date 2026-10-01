import SwiftUI

private let codeLength = 6

struct OtpView: View {
    let destination: String
    let busy: Bool
    let error: String
    let notice: String
    let secondsLeft: Int
    let onTick: () -> Void
    let onVerify: (String) -> Void
    let onResend: () -> Void
    /// Verification is deliberately skippable: the account is already usable, and a
    /// code that never arrives must not be able to trap someone outside it.
    let onSkip: () -> Void

    @Environment(\.uiScale) private var scale

    @State private var code = ""
    @FocusState private var codeFocused: Bool

    private var complete: Bool { code.count == codeLength }

    var body: some View {
        AuthScaffold(background: Dark.background, glow: true) {
            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    Icon.otpBadge(36 * scale)
                        .frame(width: 80 * scale, height: 80 * scale)
                        .background(Dark.primary.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 24 * scale, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 24 * scale, style: .continuous)
                                .strokeBorder(Dark.primary.opacity(0.20), lineWidth: 1 * scale)
                        )

                    AppText("Verify your email", style: Type.h2, color: Dark.foreground, alignment: .center)
                        .padding(.top, 28 * scale)

                    AppText(
                        destination.isEmpty
                            ? "We sent a \(codeLength)-digit code to your inbox"
                            : "Sent to \(destination)",
                        style: Type.label,
                        color: Dark.dim,
                        alignment: .center
                    )
                    .padding(.top, 8 * scale)

                    OtpInput(
                        code: $code,
                        length: codeLength,
                        verified: complete,
                        focused: $codeFocused
                    )
                    .padding(.top, 36 * scale)
                }
                .contentShape(Rectangle())
                .onTapGesture { codeFocused = true }

                if !error.isEmpty {
                    AppText(error, style: Type.caption, color: Dark.error, alignment: .center)
                        .padding(.top, 20 * scale)
                } else if !notice.isEmpty {
                    AppText(notice, style: Type.caption, color: Dark.success, alignment: .center)
                        .padding(.top, 20 * scale)
                } else if complete {
                    HStack(spacing: 8 * scale) {
                        Icon.check(14 * scale, tint: Dark.success)
                        AppText("Code complete", style: Type.caption, color: Dark.success)
                    }
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(Dark.success.opacity(0.10))
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(Dark.success.opacity(0.30), lineWidth: 1 * scale))
                    .padding(.top, 24 * scale)
                }

                DarkCta(title: "Verify code", enabled: complete, loading: busy) {
                    onVerify(code)
                }
                .padding(.top, 24 * scale)

                if secondsLeft > 0 {
                    AppText(
                        String(format: "Resend in 0:%02d", secondsLeft),
                        style: Type.caption,
                        color: Dark.dim,
                        alignment: .center
                    )
                    .padding(.top, 20 * scale)
                } else {
                    TextLink(title: "Resend code", color: Dark.primarySoft, action: onResend)
                        .padding(.top, 20 * scale)
                }

                TextLink(title: "Skip for now", color: Dark.dim, action: onSkip)
                    .padding(.top, 18 * scale)
            }
            .revealUp()
            .padding(.horizontal, 32 * scale)
            .padding(.vertical, 40 * scale)
        }
        .statusBar(lightBackground: false)
        .task(id: secondsLeft) {
            guard secondsLeft > 0 else { return }
            await sleepMillis(1_000)
            guard !Task.isCancelled else { return }
            onTick()
        }
    }
}
