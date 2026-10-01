import SwiftUI

struct CompleteProfileForm {
    let fullName: String
    let username: String
    let birthday: String
    let password: String
    let confirmPassword: String
}

/// Post-Google onboarding — edit-profile style avatar + name / username /
/// birthday / password so the account can also sign in with credentials.
struct CompleteProfileView: View {
    let user: AccountUser
    let busy: Bool
    let error: String
    let onSubmit: (CompleteProfileForm) -> Void

    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale

    @State private var fullName = ""
    @State private var username = ""
    @State private var birthday = defaultBirthday()
    @State private var password = ""
    @State private var confirm = ""
    @State private var photoSheet = false
    @ObservedObject private var avatars = AvatarStore.shared

    private var photoLabel: String {
        if !avatars.error.isEmpty { return avatars.error }
        if avatars.uploading { return Copy.t(.profilePhotoUploading) }
        return Copy.t(.profilePhotoAdd)
    }

    private var age: Int? { ageYears(from: birthday) }
    private var tooYoung: Bool { (age ?? 13) < 13 }
    private var valid: Bool {
        fullName.trimmingCharacters(in: .whitespaces).count >= 2
            && username.count >= 3
            && password.count >= 8
            && password == confirm
            && (age ?? 0) >= 13
    }

    var body: some View {
        ZStack {
            form
            if photoSheet {
                AvatarPickerSheet(onClose: { photoSheet = false })
            }
        }
    }

    private var form: some View {
        AuthScaffold(background: Light.background) {
            VStack(spacing: 12 * scale) {
                AppText("Finish your profile", style: Type.h1, color: Light.text, alignment: .center)
                AppText(
                    "You can also log in with your username and password — please set a password.",
                    style: Type.label,
                    color: Light.sub,
                    alignment: .center
                )
                if !user.email.isEmpty {
                    AppText(user.email, style: Type.caption, color: Light.muted, alignment: .center)
                }

                // The picture is stored the moment it is picked, on its own
                // endpoint — it is not part of this form's submit. Someone who
                // abandons the rest of signup still has the face they chose,
                // and a failed name or password never costs them the upload.
                MyAvatar(size: 90, name: fullName.isEmpty ? username : fullName) { photoSheet = true }
                    .padding(.top, 8 * scale)
                AppText(photoLabel, style: Type.caption,
                        color: avatars.error.isEmpty ? ProfileTokens.accentDeep : ProfileTokens.danger)

                CountedField(value: $fullName, placeholder: "Full name", maxCharacters: 30)
                CountedField(
                    value: $username,
                    placeholder: "Username",
                    maxCharacters: 30,
                    kind: .username,
                    prefix: "@",
                    sanitize: sanitizeUsername
                )

                VStack(alignment: .leading, spacing: 0) {
                    AppText("When's your birthday?", style: Type.labelStrong, color: Light.text)
                    AppText(
                        "Your birthday won't be shown publicly.",
                        style: Type.caption,
                        color: Light.hint
                    )
                    .padding(.top, 4 * scale)
                    DatePicker(
                        "Birthday",
                        selection: $birthday,
                        in: earliestBirthday()...Date(),
                        displayedComponents: .date
                    )
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                    .frame(maxHeight: 140 * scale)
                    if tooYoung {
                        AppText(
                            "Sorry. Looks like you're not eligible for \(brand.name).",
                            style: Type.micro,
                            color: Light.error
                        )
                        .padding(.top, 4 * scale)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                PasswordField(
                    value: $password,
                    placeholder: "Password (at least 8 characters)",
                    showStrength: true
                )
                PasswordField(
                    value: $confirm,
                    placeholder: "Confirm password",
                    showStrength: false,
                    submitLabel: .done
                )
                if !password.isEmpty && !confirm.isEmpty && password != confirm {
                    AppText("Passwords do not match.", style: Type.caption, color: Light.error)
                }

                if !error.isEmpty {
                    AppText(error, style: Type.caption, color: Light.error, alignment: .center)
                }

                PrimaryCta(title: "Continue", enabled: valid, loading: busy, trailingArrow: true) {
                    let fmt = DateFormatter()
                    fmt.calendar = Calendar(identifier: .gregorian)
                    fmt.locale = Locale(identifier: "en_US_POSIX")
                    fmt.dateFormat = "yyyy-MM-dd"
                    onSubmit(
                        CompleteProfileForm(
                            fullName: fullName.trimmingCharacters(in: .whitespaces),
                            username: username.trimmingCharacters(in: .whitespaces),
                            birthday: fmt.string(from: birthday),
                            password: password,
                            confirmPassword: confirm
                        )
                    )
                }
            }
            .padding(.horizontal, 28 * scale)
            .padding(.bottom, 36 * scale)
        }
        .statusBar()
        .onAppear {
            if fullName.isEmpty { fullName = user.fullName }
            if username.isEmpty { username = user.username }
            if let parsed = parseBirthday(user.birthday) { birthday = parsed }
        }
    }

    private func parseBirthday(_ raw: String) -> Date? {
        let fmt = DateFormatter()
        fmt.calendar = Calendar(identifier: .gregorian)
        fmt.locale = Locale(identifier: "en_US_POSIX")
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.date(from: raw.trimmingCharacters(in: .whitespaces))
    }
}
