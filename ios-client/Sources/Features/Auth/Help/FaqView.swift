import SwiftUI

struct FaqView: View {
    let onBack: () -> Void
    let onRecover: () -> Void
    let onLoggedOut: () -> Void
    let onSocial: () -> Void

    @Environment(\.uiScale) private var scale

    var body: some View {
        AuthScaffold(background: Light.background) {
            HelpTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 0) {
                AppText("Frequently asked questions", style: Type.h2, color: Light.text)
                    .padding(.bottom, 20 * scale)

                FaqRow(title: "Recover your account", action: onRecover)
                FaqRow(title: "Why was my account logged out automatically?", action: onLoggedOut)
                FaqRow(
                    title: "Why can't I log in with my Facebook or Google Account?",
                    divider: false,
                    action: onSocial
                )
            }
            .padding(.horizontal, 20 * scale)
        }
        .statusBar()
    }
}

struct FaqArticleView: View {
    let title: String
    let body: String
    let onBack: () -> Void

    @Environment(\.uiScale) private var scale

    var body: some View {
        AuthScaffold(background: Light.background) {
            HelpTopBar(onBack: onBack)
            VStack(alignment: .leading, spacing: 16 * scale) {
                AppText(title, style: Type.h2, color: Light.text)
                AppText(body, style: Type.body, color: Light.textSoft)
            }
            .padding(.horizontal, 20 * scale)
        }
        .statusBar()
    }
}

struct HelpTopBar: View {
    let onBack: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
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
        .padding(.horizontal, 16 * scale)
        .padding(.vertical, 10 * scale)
    }
}

private struct FaqRow: View {
    let title: String
    var divider: Bool = true
    let action: () -> Void

    @Environment(\.uiScale) private var scale

    var body: some View {
        VStack(spacing: 0) {
            Button(action: action) {
                HStack(spacing: 12 * scale) {
                    AppText(title, style: Type.body, color: Light.text)
                    Spacer(minLength: 8 * scale)
                    Icon.chevronRight(14 * scale, tint: Light.muted)
                }
                .padding(.vertical, 16 * scale)
                .contentShape(Rectangle())
            }
            .buttonStyle(PressScaleStyle())
            if divider {
                Light.divider.frame(height: 1 * scale)
            }
        }
    }
}

extension HelpFlow {
    var loggedOutBody: String {
        let name = Brand.current.name
        return "You may be signed out automatically if you signed in on too many devices, "
            + "changed your password, or \(name) detected unusual activity. This protects your account.\n\n"
            + "If you were logged out and you did not authorize any new changes, your account "
            + "may be at risk. Sign in now. Once you are in, set a strong new password.\n\n"
            + "If you cannot sign in, recover the account first."
    }

    var socialLoginBody: String {
        let name = Brand.current.name
        return "Facebook and Google only work when that profile is already linked to your \(name) "
            + "account. If you created this account with email, or the provider revoked access, "
            + "sign in with email, username, or phone instead.\n\n"
            + "After you're in, you can link Facebook or Google from Settings."
    }
}
