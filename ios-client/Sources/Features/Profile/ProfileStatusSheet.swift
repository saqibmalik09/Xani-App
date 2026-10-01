import SwiftUI

private let maxStatusChars = 60

/// Status editor — slides in from the trailing edge like Create Text.
/// Typing auto-saves; no Confirm button.
struct ProfileStatusSheet: View {
    let displayName: String
    let initial: String
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var draft = ""
    @State private var saveTask: Task<Void, Never>?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: dismiss) {
                    AppText("←", style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 18)
                        .frame(width: 40 * scale, height: 40 * scale)
                        .background(ProfileTokens.bg2)
                        .overlay(Circle().stroke(ProfileTokens.border, lineWidth: 1 * scale))
                        .clipShape(Circle())
                }
                .buttonStyle(PressScaleStyle())
                Spacer()
                AppText(Copy.t(.profileStatusTitle), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                Spacer()
                Color.clear.frame(width: 40 * scale, height: 40 * scale)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, insets.top + 12 * scale)
            .padding(.bottom, 12 * scale)

            Spacer().frame(height: 28 * scale)

            ZStack {
                Circle().fill(ProfileTokens.cta)
                Circle()
                    .fill(Color(rgb: 0xF0EEFF))
                    .padding(4 * scale)
                    .overlay(Circle().stroke(Color.white, lineWidth: 3 * scale))
                    .overlay(BrandMark(side: 56 * scale))
            }
            .frame(width: 120 * scale, height: 120 * scale)

            AppText(displayName, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 18)
                .padding(.top, 14 * scale)

            Spacer().frame(height: 28 * scale)

            ZStack(alignment: .top) {
                if draft.isEmpty {
                    AppText(
                        Copy.t(.profileWhatsOnMind),
                        style: Type.body,
                        color: ProfileTokens.muted,
                        sizeOverride: 28,
                        alignment: .center
                    )
                }
                TextEditor(text: $draft)
                    .font(.system(size: fontSize * scale, weight: .medium))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(ProfileTokens.text)
                    .scrollContentBackground(.hidden)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .onChange(of: draft) { value in
                        if value.count > maxStatusChars {
                            draft = String(value.prefix(maxStatusChars))
                        }
                        scheduleSave()
                    }
            }
            .padding(.horizontal, 28 * scale)

            AppText(
                "\(draft.count)/\(maxStatusChars)",
                style: Type.micro,
                color: ProfileTokens.muted,
                sizeOverride: 11
            )
            .padding(.bottom, insets.bottom + 24 * scale)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ProfileTokens.bg.ignoresSafeArea())
        .onAppear { draft = initial }
        .onDisappear { flushSave() }
    }

    private var fontSize: CGFloat {
        if draft.count > 40 { return 22 }
        if draft.count > 24 { return 26 }
        return 30
    }

    /// Typing saves itself: the store applies the text at once and collapses the
    /// burst into one request, so there is no Confirm button and no spinner.
    private func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run { flushSave() }
        }
    }

    private func flushSave() {
        ProfileStore.shared.updateStatus(draft)
    }

    private func dismiss() {
        flushSave()
        onClose()
    }
}
