import SwiftUI
import UIKit

struct ProfileDrawer: View {
    @ObservedObject var store: ProfileStore
    var onClose: () -> Void
    var onSettings: () -> Void
    var onHelp: () -> Void = {}
    var onReport: () -> Void = {}
    var onTerms: () -> Void = {}
    var onRoles: () -> Void = {}
    var onRewards: () -> Void = {}
    var onLogout: () -> Void = {}
    @Environment(\.uiScale) private var scale
    @Environment(\.brand) private var brand
    @Environment(\.systemSafeInsets) private var insets
    @State private var shown = false

    var body: some View {
        ZStack(alignment: .trailing) {
            ProfileTokens.scrim
                .opacity(shown ? 1 : 0)
                .ignoresSafeArea()
                .onTapGesture { close() }

            VStack(spacing: 0) {
                HStack(spacing: 12 * scale) {
                    Circle()
                        .fill(ProfileTokens.accentWash)
                        .frame(width: 46 * scale, height: 46 * scale)
                        .overlay(BrandMark(side: 24 * scale))
                    VStack(alignment: .leading, spacing: 2 * scale) {
                        AppText(store.snapshot.displayName, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 15)
                        AppText("@\(store.snapshot.username)", style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                    }
                    Spacer()
                    Button(action: close) {
                        VectorCanvas([.data("M4 4l12 12M16 4L4 16", .stroke(Color(rgb: 0x999999), 1.8))], viewport: 20, side: 20 * scale)
                            .frame(width: 36 * scale, height: 36 * scale)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20 * scale)
                .padding(.top, insets.top + 18 * scale)
                .padding(.bottom, 18 * scale)

                Rectangle().fill(ProfileTokens.hairline).frame(height: 1)

                ScrollView {
                    VStack(spacing: 0) {
                        section(Copy.t(.profileDrawerAccount))
                        drawerRow(Copy.t(.profileBalance), sub: "\(store.snapshot.balanceCoins) coins") {
                            VectorCanvas([.data("M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.profileWallet)) {
                            VectorCanvas([.data("M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.profileDiamonds), sub: "\(store.snapshot.diamonds) ♦") {
                            VectorCanvas([.data("M12 3l9 7-9 11L3 10z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.profileTxnHistory)) {
                            VectorCanvas([.data("M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }

                        divider
                        section(Copy.t(.profileDrawerCreator))
                        drawerRow(Copy.t(.profileCreatorTools), accent: true) {
                            VectorCanvas([.data("M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7", .stroke(ProfileTokens.accent, 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.profileAnalytics), accent: true) {
                            VectorCanvas([.data("M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z", .stroke(ProfileTokens.accent, 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.profileLiveCenter), accent: true) {
                            VectorCanvas([.data("M15 10l4.553-2.069A1 1 0 0121 8.845v6.31a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z", .stroke(ProfileTokens.accent, 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.rolesTitle), accent: true, action: onRoles) {
                            VectorCanvas([.data("M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z", .stroke(ProfileTokens.accent, 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.rewardsTitle), sub: Copy.t(.rewardsLiveTime), accent: true, action: onRewards) {
                            VectorCanvas([.data("M12 15l-3.5 2 .8-4L6.5 10l4-.5L12 6l1.5 3.5 4 .5-2.8 3 .8 4z", .stroke(Color(rgb: 0xD97706), 1.7))], viewport: 24, side: 18 * scale)
                        }

                        divider
                        section(Copy.t(.profileDrawerSettings))
                        drawerRow(Copy.t(.profileSettings), action: onSettings) {
                            VectorCanvas([.data("M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065zM15 12a3 3 0 11-6 0 3 3 0 016 0z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }

                        divider
                        section(Copy.t(.profileDrawerSupport))
                        drawerRow(Copy.t(.profileHelpCentre), action: onHelp) {
                            VectorCanvas([.data("M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }
                        drawerRow(Copy.t(.profileReportProblem), action: onReport) {
                            VectorCanvas([.data("M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }

                        divider
                        section(Copy.t(.profileDrawerLegal))
                        drawerRow(Copy.t(.profileTerms), action: onTerms) {
                            VectorCanvas([.data("M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z", .stroke(Color(rgb: 0x555555), 1.7))], viewport: 24, side: 18 * scale)
                        }

                        Button(action: {
                            close()
                            onLogout()
                        }) {
                            HStack(spacing: 10 * scale) {
                                VectorCanvas([.data("M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1", .stroke(ProfileTokens.danger, 1.7))], viewport: 24, side: 18 * scale)
                                AppText(Copy.t(.profileLogOut), style: Type.labelBold, color: ProfileTokens.danger, sizeOverride: 15)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14 * scale)
                            .background(ProfileTokens.dangerSoft)
                            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                        }
                        .buttonStyle(PressScaleStyle(pressedScale: 0.97))
                        .padding(.horizontal, 20 * scale)
                        .padding(.top, 20 * scale)
                        .padding(.bottom, 32 * scale)
                    }
                }

                Rectangle().fill(ProfileTokens.hairline).frame(height: 1)
                AppText(Copy.t(.profileVersion, ["app": brand.wordmark.lowercased()]), style: Type.micro, color: Color(rgb: 0xCCCCCC), sizeOverride: 11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20 * scale)
                    .padding(.vertical, 12 * scale)
                    .padding(.bottom, insets.bottom)
            }
            .frame(width: UIScreen.main.bounds.width * 0.78)
            .background(Color.white)
            .offset(x: shown ? 0 : UIScreen.main.bounds.width * 0.78)
        }
        .onAppear {
            withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: 0.32)) { shown = true }
        }
    }

    private var divider: some View {
        Rectangle().fill(ProfileTokens.rowLine).frame(height: 1).padding(.top, 4 * scale)
    }

    private func section(_ title: String) -> some View {
        AppText(title.uppercased(), style: Type.micro, color: Color(rgb: 0xAAAAAA), sizeOverride: 11)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20 * scale)
            .padding(.top, 18 * scale)
            .padding(.bottom, 6 * scale)
    }

    private func drawerRow<G: View>(
        _ label: String,
        sub: String? = nil,
        accent: Bool = false,
        action: (() -> Void)? = nil,
        @ViewBuilder icon: () -> G
    ) -> some View {
        Button(action: {
            if let action { action() } else { close() }
        }) {
            HStack(spacing: 14 * scale) {
                icon()
                    .frame(width: 38 * scale, height: 38 * scale)
                    .background(accent ? ProfileTokens.accentSoft : Color(rgb: 0xF5F5F5))
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                VStack(alignment: .leading, spacing: 1 * scale) {
                    AppText(label, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14)
                    if let sub {
                        AppText(sub, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 12)
                    }
                }
                Spacer()
                VectorCanvas([.data("M5 3l5 4.5L5 12", .stroke(Color(rgb: 0xCCCCCC), 1.6))], viewport: 15, side: 15 * scale)
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 13 * scale)
        }
        .buttonStyle(.plain)
    }

    private func close() {
        withAnimation(.timingCurve(0.22, 1, 0.36, 1, duration: 0.3)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3, execute: onClose)
    }
}

struct ProfileEditView: View {
    @ObservedObject var store: ProfileStore
    var onBack: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.brand) private var brand
    @Environment(\.systemSafeInsets) private var insets
    @State private var name = ""
    @State private var username = ""
    @State private var bio = ""
    @State private var gender = ProfileGender.preferNot
    @State private var genderSheet = false
    @State private var copied = false
    @State private var error = ""
    @State private var handleState: UsernameState = .idle
    private var usernameLocked: Bool { !store.canChangeUsername() }
    private var daysLeft: Int { store.usernameCooldownDays() }
    private var handleBlocks: Bool {
        handleState == .taken || handleState == .reserved || handleState == .invalid
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(Copy.t(.profileEditTitle), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 16 * scale)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)

            ScrollView {
                VStack(spacing: 20 * scale) {
                    VStack(spacing: 10 * scale) {
                        ZStack(alignment: .bottomTrailing) {
                            Circle()
                                .fill(Color(rgb: 0xF0EEFF))
                                .frame(width: 90 * scale, height: 90 * scale)
                                .overlay(Circle().stroke(ProfileTokens.hairline, lineWidth: 3 * scale))
                                .overlay(BrandMark(side: 44 * scale))
                            Circle()
                                .fill(ProfileTokens.text)
                                .frame(width: 28 * scale, height: 28 * scale)
                                .overlay(Circle().stroke(Color.white, lineWidth: 2 * scale))
                                .overlay(
                                    VectorCanvas([
                                        .data("M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z", .stroke(.white, 1.8)),
                                        .circle(12, 13, 4, .stroke(.white, 1.8)),
                                    ], viewport: 24, side: 14 * scale)
                                )
                        }
                        Button(action: {}) {
                            AppText(Copy.t(.profileChangePhoto), style: Type.labelBold, color: ProfileTokens.accent, sizeOverride: 14)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 28 * scale)

                    field(Copy.t(.profileNameLabel)) {
                        TextField("", text: $name)
                            .font(.system(size: 15 * scale, weight: .medium))
                            .foregroundColor(ProfileTokens.text)
                            .onChange(of: name) { _ in error = "" }
                    }
                    field(Copy.t(.profileUsername)) {
                        HStack(spacing: 4 * scale) {
                            AppText("@", style: Type.label, color: Color(rgb: 0xAAAAAA), sizeOverride: 15)
                            TextField("", text: $username)
                                .font(.system(size: 15 * scale, weight: .medium))
                                .foregroundColor(usernameLocked ? ProfileTokens.muted : ProfileTokens.text)
                                .autocapitalization(.none)
                                .disabled(usernameLocked)
                                .onChange(of: username) { v in
                                    guard !usernameLocked else { return }
                                    error = ""
                                    username = String(v.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "." }.prefix(24))
                                }
                        }
                    }
                    AppText(
                        usernameHint,
                        style: Type.caption,
                        color: usernameHintColor,
                        sizeOverride: 12
                    )
                    HStack {
                        AppText("\(brand.wordmark.lowercased()).app/@\(username)", style: Type.caption, color: ProfileTokens.sub, sizeOverride: 13)
                        Spacer()
                        Button(action: {
                            UIPasteboard.general.string = "\(brand.wordmark.lowercased()).app/@\(username)"
                            copied = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { copied = false }
                        }) {
                            if copied {
                                VectorCanvas([.data("M5 13l4 4L19 7", .stroke(Color(rgb: 0x22C55E), 2))], viewport: 24, side: 16 * scale)
                            } else {
                                VectorCanvas([
                                    .box(9, 9, 13, 13, 2, .stroke(ProfileTokens.sub, 1.7)),
                                    .data("M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1", .stroke(ProfileTokens.sub, 1.7)),
                                ], viewport: 24, side: 16 * scale)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 10 * scale)
                    .background(ProfileTokens.bg2)
                    .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))

                    field(Copy.t(.profileGender)) {
                        Button(action: { genderSheet = true }) {
                            HStack {
                                AppText(genderLabel(gender), style: Type.label, color: ProfileTokens.text, sizeOverride: 15)
                                Spacer()
                                VectorCanvas([.data("M4 6l4 4 4-4", .stroke(Color(rgb: 0xAAAAAA), 1.6))], viewport: 16, side: 16 * scale)
                            }
                        }
                        .buttonStyle(.plain)
                    }

                    field(Copy.t(.profileBio)) {
                        TextEditor(text: $bio)
                            .frame(minHeight: 110 * scale)
                            .font(.system(size: 15 * scale))
                            .foregroundColor(ProfileTokens.text)
                            .onChange(of: bio) { v in
                                error = ""
                                if v.count > 150 { bio = String(v.prefix(150)) }
                            }
                    }
                    AppText("\(bio.count)/150", style: Type.micro, color: Color(rgb: 0xCCCCCC), sizeOverride: 12)
                        .frame(maxWidth: .infinity, alignment: .trailing)

                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: ProfileTokens.danger, sizeOverride: 13)
                    }
                }
                .padding(.horizontal, 20 * scale)
                .padding(.bottom, 24 * scale)
            }

            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)
            // Saving closes the screen at once: the store applies the edit to the
            // snapshot and sends it, so there is nothing to wait for.
            Button(action: {
                let result = store.save(ProfileDraft(
                    displayName: name.isEmpty ? store.snapshot.displayName : name,
                    username: username.isEmpty ? store.snapshot.username : username,
                    bio: bio,
                    gender: gender,
                    status: store.snapshot.status
                ))
                switch result {
                case .ok:
                    onBack()
                case .usernameLocked(let days):
                    error = Copy.t(.profileUsernameAgainIn, ["n": "\(days)"])
                }
            }) {
                AppText(Copy.t(.profileSave), style: Type.labelBold, color: .white, sizeOverride: 16)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52 * scale)
                    .background(handleBlocks ? AnyShapeStyle(ProfileTokens.muted) : AnyShapeStyle(ProfileTokens.cta))
                    .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.97))
            .disabled(handleBlocks)
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 12 * scale)
            .padding(.bottom, insets.bottom + 8 * scale)
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            name = store.snapshot.displayName
            username = store.snapshot.username
            bio = store.snapshot.bio
            gender = store.snapshot.gender
        }
        // Availability while typing. A new keystroke cancels this task, so only the
        // handle the user stopped on is ever asked about, and names already answered
        // this session come back from memory with no request at all.
        .task(id: username) {
            guard !usernameLocked, username != store.snapshot.username else {
                handleState = .idle
                return
            }
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }
            handleState = .checking
            handleState = await store.checkUsername(username)
        }
        .overlay {
            if genderSheet {
                genderSheetView
            }
        }
    }

    /// The line under the username field: the rule when untouched, the outcome once
    /// checked, the remaining days when the change window is closed — never a
    /// spinner the user has to wait out.
    private var usernameHint: String {
        if usernameLocked { return Copy.t(.profileUsernameAgainIn, ["n": "\(daysLeft)"]) }
        switch handleState {
        case .available: return Copy.t(.profileUsernameAvailable, ["u": username])
        case .taken, .reserved: return Copy.t(.profileUsernameTaken, ["u": username])
        case .invalid: return Copy.t(.profileUsernameRule)
        case .cooldown: return Copy.t(.profileUsernameAgainIn, ["n": "\(daysLeft)"])
        case .checking: return Copy.t(.profileUsernameChecking)
        default: return Copy.t(.profileUsernameLocked)
        }
    }

    private var usernameHintColor: Color {
        if usernameLocked { return ProfileTokens.danger }
        switch handleState {
        case .available: return ProfileTokens.ok
        case .taken, .reserved, .invalid, .cooldown: return ProfileTokens.danger
        default: return ProfileTokens.sub
        }
    }

    private func field<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8 * scale) {
            AppText(title.uppercased(), style: Type.micro, color: ProfileTokens.sub, sizeOverride: 12)
            content()
                .padding(.horizontal, 16 * scale)
                .padding(.vertical, 13 * scale)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                        .stroke(Color(rgb: 0xE8E8E8), lineWidth: 1.5 * scale)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
        }
    }

    private var genderSheetView: some View {
        ZStack(alignment: .bottom) {
            ProfileTokens.sheetScrim.ignoresSafeArea().onTapGesture { genderSheet = false }
            VStack(spacing: 0) {
                Capsule().fill(Color(rgb: 0xE0E0E0)).frame(width: 36 * scale, height: 4 * scale).padding(.top, 12 * scale).padding(.bottom, 16 * scale)
                AppText(Copy.t(.profileGender), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20 * scale)
                    .padding(.bottom, 12 * scale)
                Rectangle().fill(ProfileTokens.hairline).frame(height: 1)
                ForEach([ProfileGender.male, .female, .preferNot], id: \.self) { g in
                    Button(action: { gender = g; genderSheet = false }) {
                        HStack {
                            AppText(genderLabel(g), style: gender == g ? Type.labelBold : Type.label, color: gender == g ? ProfileTokens.accent : ProfileTokens.text, sizeOverride: 15)
                            Spacer()
                            if gender == g {
                                VectorCanvas([.data("M5 13l4 4L19 7", .stroke(ProfileTokens.accent, 2.2))], viewport: 24, side: 18 * scale)
                            }
                        }
                        .padding(.horizontal, 20 * scale)
                        .padding(.vertical, 16 * scale)
                    }
                    .buttonStyle(.plain)
                    if g != .preferNot { Rectangle().fill(ProfileTokens.rowLine).frame(height: 1) }
                }
            }
            .padding(.bottom, insets.bottom + 20 * scale)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
        }
    }

    private func genderLabel(_ g: ProfileGender) -> String {
        switch g {
        case .male: return Copy.t(.profileGenderMale)
        case .female: return Copy.t(.profileGenderFemale)
        case .preferNot: return Copy.t(.profileGenderPreferNot)
        }
    }
}

extension ProfileGender: Hashable {}

struct ProfileSettingsView: View {
    var onBack: () -> Void
    var onLogout: () -> Void = {}
    var onPassword: () -> Void = {}
    var onSwitchAccounts: () -> Void = {}
    var onHelp: () -> Void = {}
    var onTerms: () -> Void = {}
    var passwordLoginActive: Bool = false
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    private var passwordLabel: String {
        passwordLoginActive ? Copy.t(.profileChangePassword) : Copy.t(.profileActivatePassword)
    }

    private var groups: [(String, [(String, String)])] {
        [
            (Copy.t(.profileDrawerAccount), [
                (Copy.t(.profileSwitchAccounts), "switch"),
                (passwordLabel, "password"),
            ]),
            (Copy.t(.profilePrivacySafety), [
                (Copy.t(.profilePrivacy), "privacy"),
                (Copy.t(.profileDms), "dms"),
                (Copy.t(.profileBlocked), "blocked"),
                (Copy.t(.profileSafeMode), "safe"),
            ]),
            (Copy.t(.profileContentDisplay), [
                (Copy.t(.profileDisplay), "display"),
                (Copy.t(.profileLanguage), "language"),
                (Copy.t(.profileTranslation), "translation"),
            ]),
            (Copy.t(.profileNotifications), [(Copy.t(.profileNotifications), "notifications")]),
            (Copy.t(.profileSupportLegal), [
                (Copy.t(.profileHelpCentre), "help"),
                (Copy.t(.profileTerms), "terms"),
                (Copy.t(.profileLocationContacts), "location"),
            ]),
        ]
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(Copy.t(.profileSettings), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 16 * scale)
            .background(Color.white)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(groups.enumerated()), id: \.offset) { _, group in
                        AppText(group.0.uppercased(), style: Type.micro, color: Color(rgb: 0xAAAAAA), sizeOverride: 12)
                            .padding(.horizontal, 20 * scale)
                            .padding(.top, 24 * scale)
                            .padding(.bottom, 8 * scale)
                        VStack(spacing: 0) {
                            ForEach(Array(group.1.enumerated()), id: \.offset) { i, item in
                                Button {
                                    switch item.1 {
                                    case "password": onPassword()
                                    case "switch": onSwitchAccounts()
                                    case "help": onHelp()
                                    case "terms": onTerms()
                                    default: break
                                    }
                                } label: {
                                    HStack {
                                        AppText(item.0, style: Type.label, color: ProfileTokens.text, sizeOverride: 15)
                                        Spacer()
                                        VectorCanvas([.data("M6 4l4 4-4 4", .stroke(Color(rgb: 0xCCCCCC), 1.6))], viewport: 16, side: 16 * scale)
                                    }
                                    .padding(.horizontal, 18 * scale)
                                    .padding(.vertical, 15 * scale)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                if i < group.1.count - 1 {
                                    Rectangle().fill(ProfileTokens.rowLine).frame(height: 1)
                                }
                            }
                        }
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))
                        .padding(.horizontal, 16 * scale)
                    }

                    Button(action: onLogout) {
                        HStack(spacing: 10 * scale) {
                            VectorCanvas(
                                [.data("M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1", .stroke(ProfileTokens.danger, 1.7))],
                                viewport: 24,
                                side: 18 * scale
                            )
                            AppText(Copy.t(.profileLogOut), style: Type.labelBold, color: ProfileTokens.danger, sizeOverride: 15)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14 * scale)
                        .background(ProfileTokens.dangerSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                    }
                    .buttonStyle(PressScaleStyle(pressedScale: 0.97))
                    .padding(.horizontal, 16 * scale)
                    .padding(.top, 28 * scale)
                }
                .padding(.bottom, 40 * scale + insets.bottom)
            }
        }
        .background(ProfileTokens.bg2.ignoresSafeArea())
    }
}

struct ProfilePasswordView: View {
    var onBack: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var passwordActive = Xani.session.lastKnownUser?.passwordLoginActive == true
    @State private var current = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var busy = false
    @State private var error = ""

    private var match: Bool {
        password.isEmpty || confirm.isEmpty || password == confirm
    }

    private var valid: Bool {
        if passwordActive {
            return current.count >= 8 && password.count >= 8 && password == confirm && password != current
        }
        return password.count >= 8 && password == confirm
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))], viewport: 22, side: 22 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                }
                .buttonStyle(.plain)
                AppText(
                    passwordActive ? Copy.t(.profileChangePassword) : Copy.t(.profileActivatePassword),
                    style: Type.labelBold,
                    color: ProfileTokens.text,
                    sizeOverride: 17
                )
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 16 * scale)
            .background(Color.white)
            Rectangle().fill(ProfileTokens.hairline).frame(height: 1)

            ScrollView {
                VStack(alignment: .leading, spacing: 16 * scale) {
                    AppText(
                        passwordActive ? Copy.t(.profileChangePasswordHint) : Copy.t(.profileActivatePasswordHint),
                        style: Type.caption,
                        color: Color(rgb: 0x888888),
                        sizeOverride: 13
                    )

                    VStack(spacing: 12 * scale) {
                        if passwordActive {
                            PasswordField(value: $current, placeholder: Copy.t(.profileCurrentPassword))
                        }
                        PasswordField(value: $password, placeholder: Copy.t(.profileNewPassword), showStrength: true)
                        PasswordField(value: $confirm, placeholder: Copy.t(.profileConfirmPassword), submitLabel: .done)
                        if !match {
                            AppText(Copy.t(.profilePasswordMismatch), style: Type.caption, color: Light.error)
                        }
                    }
                    .padding(16 * scale)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16 * scale, style: .continuous))

                    if !error.isEmpty {
                        AppText(error, style: Type.caption, color: Light.error, sizeOverride: 13)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }

                    PrimaryCta(
                        title: passwordActive ? Copy.t(.profileUpdatePassword) : Copy.t(.profileActivatePassword),
                        enabled: valid && !busy,
                        loading: busy
                    ) {
                        Task { await submit() }
                    }
                }
                .padding(.horizontal, 20 * scale)
                .padding(.top, 24 * scale)
                .padding(.bottom, 40 * scale + insets.bottom)
            }
        }
        .background(ProfileTokens.bg2.ignoresSafeArea())
        .task {
            let me = await Xani.auth.me()
            if me.ok, let user = me.data?.user {
                passwordActive = user.passwordLoginActive
                Xani.session.updateUser(user)
            }
        }
    }

    @MainActor
    private func submit() async {
        busy = true
        error = ""
        let response: ApiResponse<EmptyPayload>
        if passwordActive {
            response = await Xani.auth.changePassword(
                currentPassword: current,
                newPassword: password,
                confirmPassword: confirm
            )
        } else {
            response = await Xani.auth.activatePassword(
                newPassword: password,
                confirmPassword: confirm
            )
        }
        busy = false
        guard response.ok else {
            error = response.message.isEmpty ? "Could not update password." : response.message
            return
        }
        if let user = Xani.session.lastKnownUser {
            Xani.session.updateUser(user.withPasswordLoginActive(true))
        }
        passwordActive = true
        onBack()
    }
}

struct ProfileStatsView: View {
    @ObservedObject var store: ProfileStore
    var kind: ProfileStatsKind
    var onBack: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12 * scale) {
                Button(action: onBack) {
                    VectorCanvas([.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2.2))], viewport: 22, side: 18 * scale)
                        .frame(width: 32 * scale, height: 32 * scale)
                        .background(ProfileTokens.bg2)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                AppText(title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                Spacer()
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 10 * scale)
            .padding(.bottom, 12 * scale)
            Rectangle().fill(ProfileTokens.border).frame(height: 1)

            if kind == .receiving {
                receivingBody
            } else {
                ScrollView { peopleBody }
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
    }

    private var title: String {
        switch kind {
        case .following: return Copy.t(.profileFollowing)
        case .followers: return Copy.t(.profileFollowers)
        case .receiving: return Copy.t(.profileReceiving)
        }
    }

    /// The owner's own statement from the gifting engine: received and sent,
    /// this live / today / week / month / dates, top counterparts or latest,
    /// moving as frames land. The list takes the rest of the screen.
    private var receivingBody: some View {
        GeometryReader { geo in
            GiftingPanel(
                userId: Xani.currentUserId,
                dark: false,
                listHeight: max(200, (geo.size.height - 150 * scale) / scale)
            )
            .padding(.top, 10 * scale)
        }
    }

    /// The real follow graph, keyset paged from the server. The follow button on
    /// each row is the app-wide relation, so unfollowing here flips the feed card.
    private var peopleBody: some View {
        PeopleList(kind: kind)
            .padding(.top, 8 * scale)
            .padding(.bottom, 24 * scale)
    }

}
