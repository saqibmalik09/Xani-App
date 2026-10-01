import SwiftUI

struct LookupAccountView: View {
    let onBack: () -> Void
    let onSearchByPhone: () -> Void

    @State private var query = ""
    @State private var searched = false
    @Environment(\.uiScale) private var scale

    var body: some View {
        LookupScaffold(
            title: "Look up your account",
            message: "Enter a username or email to find the account you want to recover.",
            searched: searched,
            searchEnabled: !query.trimmingCharacters(in: .whitespaces).isEmpty,
            altLabel: "Search by phone number",
            onBack: {
                searched = false
                onBack()
            },
            onSearch: { searched = true },
            onAlt: onSearchByPhone,
        ) {
            LightField(
                value: $query,
                placeholder: "Enter an email or username",
                kind: .email,
                maxCharacters: 254,
                submitLabel: .search,
                onSubmit: { if !query.isEmpty { searched = true } }
            ) {
                ClearFieldButton(visible: !query.isEmpty) {
                    query = ""
                    searched = false
                }
            }
            .onChange(of: query) { _ in searched = false }
        }
        .statusBar()
    }
}

struct LookupPhoneView: View {
    let onBack: () -> Void
    let onSearchByEmail: () -> Void

    @State private var country: DialCountry = DialCountries.suggested()
    @State private var number = ""
    @State private var searched = false
    @State private var picking = false
    @Environment(\.uiScale) private var scale

    var body: some View {
        LookupScaffold(
            title: "Look up your account",
            message: "Enter your phone number to find the account you want to recover.",
            searched: searched,
            searchEnabled: number.count >= 6,
            altLabel: "Search by email or username",
            onBack: {
                searched = false
                onBack()
            },
            onSearch: { searched = true },
            onAlt: onSearchByEmail,
        ) {
            PhoneLookupField(
                country: country,
                number: $number,
                onPickCountry: { picking = true }
            )
            .onChange(of: number) { _ in searched = false }
        }
        .statusBar()
        .fullScreenCover(isPresented: $picking) {
            CountryPickerView(
                selectedIso: country.iso,
                onSelect: { next in
                    country = next
                    picking = false
                },
                onClose: { picking = false }
            )
        }
    }
}

private struct LookupScaffold<Field: View>: View {
    let title: String
    let message: String
    let searched: Bool
    let searchEnabled: Bool
    let altLabel: String
    let onBack: () -> Void
    let onSearch: () -> Void
    let onAlt: () -> Void
    @ViewBuilder let field: () -> Field

    @Environment(\.uiScale) private var scale

    var body: some View {
        AuthScaffold(background: Light.background, scrollable: false) {
            HelpTopBar(onBack: onBack)

            VStack(alignment: .leading, spacing: 0) {
                AppText(title, style: Type.h2, color: Light.text)
                AppText(message, style: Type.caption, color: Light.sub)
                    .padding(.top, 8 * scale)
                    .padding(.bottom, 24 * scale)
                field()
            }
            .padding(.horizontal, 20 * scale)

            if searched {
                NoResultsState(onAlt: onAlt)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Spacer(minLength: 0)
            }

            TextLink(title: altLabel, action: onAlt)
                .padding(.top, 12 * scale)

            PrimaryCta(title: "Search", enabled: searchEnabled, action: onSearch)
                .padding(.horizontal, 20 * scale)
                .padding(.vertical, 16 * scale)
                .padding(.bottom, 12 * scale)
        }
    }
}

private struct PhoneLookupField: View {
    let country: DialCountry
    @Binding var number: String
    let onPickCountry: () -> Void

    @Environment(\.uiScale) private var scale
    @FocusState private var focused: Bool

    var body: some View {
        let bounded = Binding(
            get: { number },
            set: { number = String($0.filter(\.isNumber).prefix(15)) }
        )
        HStack(spacing: 8 * scale) {
            Button(action: onPickCountry) {
                HStack(spacing: 4 * scale) {
                    AppText(country.label, style: Type.body, color: Light.text)
                    Icon.chevronDown(10 * scale, tint: Light.sub)
                }
            }
            .buttonStyle(PressScaleStyle())
            Light.borderStrong.frame(width: 1 * scale, height: 18 * scale)
            ZStack(alignment: .leading) {
                if number.isEmpty {
                    AppText("Phone number", style: Type.body, color: Light.muted, lineLimit: 1)
                        .allowsHitTesting(false)
                }
                TextField("", text: bounded)
                    .font(Type.body.family.font(size: Type.body.size * scale, weight: Type.body.weight))
                    .foregroundStyle(Light.text)
                    .tint(Dark.primary)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .focused($focused)
            }
            ClearFieldButton(visible: !number.isEmpty) { number = "" }
        }
        .padding(.horizontal, 16 * scale)
        .frame(height: 52 * scale)
        .background(Light.input)
        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 6 * scale, style: .continuous)
                .strokeBorder(focused ? Dark.primary : Light.borderStrong, lineWidth: 1.5 * scale)
        )
        .animation(.easeOut(duration: 0.16), value: focused)
    }
}

private struct NoResultsState: View {
    let onAlt: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        VStack(spacing: 0) {
            Icon.search(28 * scale, tint: Light.muted)
                .frame(width: 72 * scale, height: 72 * scale)
                .background(Light.surface)
                .clipShape(Circle())
            AppText("No results", style: Type.h2, color: Light.text, alignment: .center, sizeOverride: 20)
                .padding(.top, 16 * scale)
            AppText(
                "Try again or can't find account?",
                style: Type.caption,
                color: Light.sub,
                alignment: .center
            )
            .padding(.top, 8 * scale)
            TextLink(title: "Can't find account?", action: onAlt)
                .padding(.top, 16 * scale)
        }
        .padding(.horizontal, 32 * scale)
    }
}

struct ClearFieldButton: View {
    let visible: Bool
    let action: () -> Void
    @Environment(\.uiScale) private var scale

    var body: some View {
        Group {
            if visible {
                Button(action: action) {
                    Icon.close(10 * scale, tint: .white)
                        .frame(width: 22 * scale, height: 22 * scale)
                        .background(Light.borderStrong)
                        .clipShape(Circle())
                }
                .buttonStyle(PressScaleStyle())
                .accessibilityLabel("Clear")
                .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.16), value: visible)
    }
}
