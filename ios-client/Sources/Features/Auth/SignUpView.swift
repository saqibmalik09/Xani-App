import SwiftUI

private let minimumAgeYears = 13
private let fullNameMax = 30
private let usernameMax = 24
private let usernameMin = 2

/// Matches the server's password policy so the two cannot drift.
private let passwordMin = 8

struct SignUpView: View {
    let busy: Bool
    let error: String
    let onBack: () -> Void
    let onHelp: () -> Void
    let onSubmit: (SignUpForm) -> Void

    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale

    @State private var fullName = ""
    @State private var username = ""
    @State private var email = ""
    @State private var password = ""
    @State private var birthday = defaultBirthday()

    private var age: Int? { ageYears(from: birthday) }
    private var tooYoung: Bool { (age ?? minimumAgeYears) < minimumAgeYears }
    private var valid: Bool {
        fullName.trimmingCharacters(in: .whitespaces).count >= 2
            && username.count >= usernameMin
            && isPlausibleEmail(email)
            && password.count >= passwordMin
            && (age ?? 0) >= minimumAgeYears
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
                BrandMark(side: 56 * scale)

                AppText("Create your account", style: Type.h2, color: Light.text, alignment: .center)
                    .padding(.top, 16 * scale)

                AppText("Step 1 of 2 · Basic info", style: Type.caption, color: Light.hint)
                    .padding(.top, 6 * scale)

                progressBar
                    .padding(.top, 12 * scale)

                VStack(spacing: 12 * scale) {
                    CountedField(
                        value: $fullName,
                        placeholder: "Full name",
                        maxCharacters: fullNameMax
                    )

                    CountedField(
                        value: $username,
                        placeholder: "Username",
                        maxCharacters: usernameMax,
                        kind: .username,
                        prefix: "@",
                        footnote: "You can change your username after 30 days.",
                        sanitize: sanitizeUsername
                    )

                    LightField(
                        value: $email,
                        placeholder: "Email address",
                        kind: .email,
                        maxCharacters: 254
                    )

                    PasswordField(
                        value: $password,
                        placeholder: "Password (at least \(passwordMin) characters)",
                        showStrength: true,
                        submitLabel: .done
                    )

                    birthdaySection
                }
                .padding(.top, 24 * scale)
            }
            .padding(.horizontal, 28 * scale)

            VStack(spacing: 14 * scale) {
                if !error.isEmpty {
                    AppText(error, style: Type.caption, color: Light.error, alignment: .center)
                }

                PrimaryCta(title: "Continue", enabled: valid, loading: busy, trailingArrow: true) {
                    onSubmit(
                        SignUpForm(
                            fullName: fullName,
                            username: username,
                            email: email,
                            password: password
                        )
                    )
                }

                HStack(spacing: 4 * scale) {
                    AppText("Already have an account?", style: Type.label, color: Light.textSoft)
                    TextLink(title: "Log in", action: onBack)
                }
            }
            .padding(.horizontal, 28 * scale)
            .padding(.top, 20 * scale)
            .padding(.bottom, 40 * scale)
        }
        .statusBar()
    }

    private var progressBar: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(Light.border)
                Capsule().fill(Gradients.brand).frame(width: geometry.size.width * 0.5)
            }
        }
        .frame(maxWidth: 280 * scale)
        .frame(height: 3 * scale)
    }

    private var birthdaySection: some View {
        VStack(alignment: .leading, spacing: 0) {
            AppText("When's your birthday?", style: Type.labelStrong, color: Light.text)

            AppText(
                "Your birthday won't be shown publicly.",
                style: Type.caption,
                color: Light.hint
            )
            .padding(.top, 4 * scale)

            // The system wheel picker is localised, accessible and already matches the
            // drum-picker design, so there is no reason to reimplement three wheels.
            DatePicker(
                "Birthday",
                selection: $birthday,
                in: earliestBirthday()...Date(),
                displayedComponents: .date
            )
            .datePickerStyle(.wheel)
            .labelsHidden()
            .frame(maxWidth: .infinity)
            .frame(height: 180 * scale)
            .clipped()
            .background(Light.input)
            .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
            .overlay(alignment: .center) {
                Rectangle()
                    .fill(Dark.primary.opacity(0.08))
                    .frame(height: 36 * scale)
                    .allowsHitTesting(false)
            }
            .overlay(
                RoundedRectangle(cornerRadius: 6 * scale, style: .continuous)
                    .strokeBorder(Light.borderStrong, lineWidth: 1.5 * scale)
            )
            .padding(.top, 10 * scale)

            if tooYoung {
                AppText(
                    "Sorry. Looks like you're not eligible for \(brand.name) "
                        + "... but thanks for checking us out!",
                    style: Type.micro,
                    color: Light.error
                )
                .padding(.top, 8 * scale)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

func isPlausibleEmail(_ value: String) -> Bool {
    let trimmed = value.trimmingCharacters(in: .whitespaces)
    guard !trimmed.contains(" "),
          let at = trimmed.firstIndex(of: "@"),
          at != trimmed.startIndex,
          trimmed.lastIndex(of: "@") == at
    else { return false }

    let domain = trimmed[trimmed.index(after: at)...]
    guard let dot = domain.lastIndex(of: "."),
          dot != domain.startIndex,
          dot != domain.index(before: domain.endIndex)
    else { return false }
    return true
}

/// The age gate is evaluated on device purely to keep the CTA honest; the server
/// remains the authority on eligibility at registration time.
func ageYears(from birthday: Date, now: Date = Date()) -> Int? {
    Calendar.current.dateComponents([.year], from: birthday, to: now).year
}

func defaultBirthday() -> Date {
    Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()
}

func earliestBirthday() -> Date {
    Calendar.current.date(byAdding: .year, value: -120, to: Date()) ?? Date.distantPast
}
