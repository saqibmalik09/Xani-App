import SwiftUI

struct AuthChoiceView: View {
    var busy: Bool = false
    var error: String = ""
    let onEmail: () -> Void
    let onProvider: (String) -> Void
    let onLogIn: () -> Void
    let onHelp: () -> Void

    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale

    private var country: String { DialCountries.suggested().name }

    var body: some View {
        AuthScaffold(background: Light.background) {
            HStack {
                Spacer(minLength: 0)
                CircleIconButton(
                    diameter: 30 * scale,
                    borderColor: Light.borderIcon,
                    accessibilityName: "Help",
                    action: onHelp
                ) {
                    Icon.help(14 * scale)
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 12 * scale)

            VStack(spacing: 0) {
                BrandMark(side: 68 * scale)
                    .padding(.top, 8 * scale)
                    .revealScale()

                AppText(
                    "Log in to \(brand.name)",
                    style: Type.h1,
                    color: Light.text,
                    alignment: .center
                )
                .padding(.top, 20 * scale)
                .revealUp(delayMillis: 50)

                AppText(
                    "Watch videos, go LIVE, and connect with millions of creators.",
                    style: Type.label,
                    color: Light.sub,
                    alignment: .center
                )
                .frame(maxWidth: 270 * scale)
                .padding(.top, 10 * scale)
                .revealUp(delayMillis: 120)

                (Text("You're in ")
                    + Text(country)
                        .foregroundColor(Dark.primary)
                        .fontWeight(.bold))
                    .font(Type.caption.family.font(size: Type.caption.size * scale, weight: Type.caption.weight))
                    .foregroundStyle(Light.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8 * scale)

                VStack(spacing: 10 * scale) {
                    PrimaryCta(
                        title: "Continue with Email",
                        enabled: !busy,
                        loading: busy,
                        leading: { Icon.mail(20 * scale) },
                        action: onEmail
                    )

                    ProviderButton(label: "Continue with Google") {
                        onProvider("google")
                    } glyph: {
                        Icon.google(20 * scale)
                    }

                    ProviderButton(label: "Continue with Apple") {
                        onProvider("apple")
                    } glyph: {
                        Icon.apple(20 * scale)
                    }

                    ProviderButton(label: "Continue with Facebook") {
                        onProvider("facebook")
                    } glyph: {
                        Icon.facebook(20 * scale)
                    }

                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: Light.error, alignment: .center)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.top, 32 * scale)

                Icon.chevronDown(16 * scale, tint: Light.muted)
                    .padding(.top, 24 * scale)
            }
            .padding(.horizontal, 32 * scale)

            VStack(spacing: 16 * scale) {
                (Text("By continuing, you confirm you are located in ")
                    + Text(" \(country)")
                        .foregroundColor(Dark.primary)
                        .fontWeight(.bold)
                    + Text(
                        " and agree to our Terms of Service and acknowledge that you have read our Privacy Policy."
                    ))
                    .font(Type.micro.family.font(size: Type.micro.size * scale, weight: Type.micro.weight))
                    .foregroundStyle(Light.muted)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 4 * scale) {
                    AppText("Already have an account?", style: Type.label, color: Light.textSoft)
                    TextLink(title: "Log in", action: onLogIn)
                }
            }
            .padding(.horizontal, 32 * scale)
            .padding(.top, 24 * scale)
            .padding(.bottom, 36 * scale)
        }
        .statusBar()
    }
}
