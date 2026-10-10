import SwiftUI

/// Account picker: switch between saved accounts, or add another via a one-tap
/// "Log in to another account" that then offers Create vs Log in.
///
/// Whole-surface drag (not just the handle): pull up to 80% of the screen,
/// pull down to dismiss.
struct AccountSwitcherSheet: View {
    var onClose: () -> Void
    var onCreateAccount: () -> Void
    var onAddAccount: () -> Void

    @EnvironmentObject private var session: SessionManager
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var choosing = false
    @State private var frac: CGFloat = 0.08
    @State private var dragOrigin: CGFloat?

    private let sheetMax: CGFloat = 0.80
    private let sheetRest: CGFloat = 0.62
    private let sheetMin: CGFloat = 0.08

    private var activeId: String {
        if case let .signedIn(user, _) = session.state { return user.id }
        return ""
    }

    var body: some View {
        GeometryReader { geo in
            let h = geo.size.height
            ZStack(alignment: .bottom) {
                Color.black.opacity(0.42 * Double(frac))
                    .ignoresSafeArea()
                    .onTapGesture(perform: dismiss)

                VStack(spacing: 0) {
                    Capsule()
                        .fill(Color(rgb: 0xE0E0E0))
                        .frame(width: 40 * scale, height: 4 * scale)
                        .padding(.top, 12 * scale)
                        .padding(.bottom, 14 * scale)

                    if choosing {
                        choosePane
                    } else {
                        accountsPane
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: h * frac, alignment: .top)
                .background(
                    LinearGradient(
                        colors: [Color.white, Color(rgb: 0xF7F7F8)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
                .simultaneousGesture(
                    DragGesture(minimumDistance: 8, coordinateSpace: .local)
                        .onChanged { v in
                            if dragOrigin == nil { dragOrigin = frac }
                            let next = (dragOrigin ?? frac) - v.translation.height / h
                            frac = min(sheetMax, max(sheetMin, next))
                        }
                        .onEnded { v in
                            let origin = dragOrigin ?? frac
                            dragOrigin = nil
                            let predicted = origin - v.predictedEndTranslation.height / h
                            let close = predicted < 0.34 || (v.translation.height > 120 && frac < sheetRest)
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.88)) {
                                if close {
                                    frac = sheetMin
                                } else if frac > 0.72 {
                                    frac = sheetMax
                                } else {
                                    frac = sheetRest
                                }
                            }
                            if close {
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.28, execute: onClose)
                            }
                        }
                )
            }
        }
        .ignoresSafeArea()
        .onAppear {
            choosing = false
            session.clearSwitchError()
            frac = sheetMin
            withAnimation(.spring(response: 0.42, dampingFraction: 0.9)) { frac = sheetRest }
        }
    }

    private func dismiss() {
        withAnimation(.spring(response: 0.36, dampingFraction: 0.9)) { frac = sheetMin }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28, execute: onClose)
    }

    private var accountsPane: some View {
        VStack(spacing: 0) {
            AppText(
                Copy.t(.profileSwitchAccounts),
                style: Type.labelBold,
                color: ProfileTokens.text,
                sizeOverride: 17
            )
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16 * scale)
            .padding(.bottom, 10 * scale)

            if !session.switchError.isEmpty {
                AppText(
                    session.switchError,
                    style: Type.caption,
                    color: ProfileTokens.danger,
                    sizeOverride: 12
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16 * scale)
                .padding(.bottom, 8 * scale)
            }

            ScrollView {
                LazyVStack(spacing: 6 * scale) {
                    ForEach(session.accounts) { account in
                        accountRow(account)
                    }
                    // Keep Add inside the list so it stays reachable when the
                    // sheet is short or the account list scrolls.
                    actionButton(Copy.t(.profileAddAccount), filled: true, inset: false) {
                        withAnimation(.easeInOut(duration: 0.2)) { choosing = true }
                    }
                }
                .padding(.horizontal, 16 * scale)
                .padding(.bottom, insets.bottom + 16 * scale)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(maxHeight: .infinity, alignment: .top)
    }

    private var choosePane: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.2)) { choosing = false }
                }) {
                    VectorCanvas(
                        [.data("M14 4L7 11l7 7", .stroke(ProfileTokens.text, 2))],
                        viewport: 22,
                        side: 20 * scale
                    )
                    .frame(width: 36 * scale, height: 36 * scale)
                }
                .buttonStyle(.plain)
                AppText(
                    Copy.t(.profileAddAccount),
                    style: Type.labelBold,
                    color: ProfileTokens.text,
                    sizeOverride: 17
                )
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12 * scale)
            .padding(.bottom, 16 * scale)

            actionButton(Copy.t(.profileCreateAccount), filled: false) {
                onClose()
                onCreateAccount()
            }
            actionButton(Copy.t(.profileUseExisting), filled: true) {
                onClose()
                onAddAccount()
            }
            .padding(.top, 8 * scale)
            .padding(.bottom, insets.bottom + 16 * scale)
        }
    }

    private func actionButton(_ label: String, filled: Bool, inset: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(
                label,
                style: Type.labelBold,
                color: ProfileTokens.accent,
                sizeOverride: 14
            )
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14 * scale)
            .background(filled ? ProfileTokens.accentSoft : Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                    .stroke(filled ? Color.clear : ProfileTokens.hairline, lineWidth: 1 * scale)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.97))
        .padding(.horizontal, inset ? 16 * scale : 0)
    }

    private func accountRow(_ account: SavedAccount) -> some View {
        let active = account.userId == activeId
        return HStack(spacing: 12 * scale) {
            avatar(account)
            VStack(alignment: .leading, spacing: 2 * scale) {
                AppText(
                    account.fullName.isEmpty ? account.username : account.fullName,
                    style: Type.labelBold,
                    color: ProfileTokens.text,
                    sizeOverride: 14
                )
                AppText(
                    "@\(account.username)",
                    style: Type.caption,
                    color: ProfileTokens.sub,
                    sizeOverride: 12
                )
            }
            Spacer(minLength: 0)
            if active {
                AppText("✓", style: Type.labelBold, color: ProfileTokens.accent, sizeOverride: 16)
            } else {
                Button(action: {
                    let only = session.accounts.count <= 1
                    session.removeAccount(account.userId)
                    if only { onClose() }
                }) {
                    AppText(Copy.t(.inboxRemove), style: Type.caption, color: ProfileTokens.sub, sizeOverride: 11)
                        .padding(.horizontal, 8 * scale)
                        .padding(.vertical, 6 * scale)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12 * scale)
        .padding(.vertical, 10 * scale)
        .background(active ? ProfileTokens.accentSoft : Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                .stroke(
                    active ? ProfileTokens.accent.opacity(0.35) : ProfileTokens.hairline,
                    lineWidth: 1 * scale
                )
        )
        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture {
            if account.userId != activeId {
                session.switchTo(account.userId)
            }
            onClose()
        }
    }

    private func avatar(_ account: SavedAccount) -> some View {
        let url = MediaConfig.shared.url(account.avatarURL.isEmpty ? nil : account.avatarURL)
        let initial = String((account.fullName.isEmpty ? account.username : account.fullName).prefix(1)).uppercased()
        return ZStack {
            Circle().fill(Color(rgb: 0xEEEEEE))
            if url != nil {
                RemoteImage(url: url, contentMode: .fill, fallback: .clear)
                    .clipShape(Circle())
            } else {
                AppText(initial, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
            }
        }
        .frame(width: 44 * scale, height: 44 * scale)
        .clipShape(Circle())
    }
}
