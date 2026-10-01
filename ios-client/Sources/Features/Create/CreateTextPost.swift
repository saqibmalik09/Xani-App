import SwiftUI

private struct TextPalette {
    let colors: [Color]
    let dark: Bool
    var white: Bool = false
}

private let Palettes: [TextPalette] = [
    TextPalette(colors: [Color(rgb: 0x7C3AED), Color(rgb: 0xEC4899)], dark: false),
    TextPalette(colors: [Color(rgb: 0xF97316), Color(rgb: 0xEF4444)], dark: false),
    TextPalette(colors: [Color(rgb: 0x06B6D4), Color(rgb: 0x3B82F6)], dark: false),
    TextPalette(colors: [Color(rgb: 0x10B981), Color(rgb: 0x059669)], dark: false),
    TextPalette(colors: [Color(rgb: 0xF59E0B), Color(rgb: 0xEF4444)], dark: false),
    TextPalette(colors: [Color(rgb: 0x1A0533), Color(rgb: 0x07070F)], dark: true),
    TextPalette(colors: [Color(rgb: 0x0F2027), Color(rgb: 0x2C5364)], dark: true),
    TextPalette(colors: [Color(rgb: 0x111111), Color(rgb: 0x333333)], dark: true),
    TextPalette(colors: [.white, .white], dark: false, white: true),
    TextPalette(colors: [Color(rgb: 0xFDE68A), Color(rgb: 0xFCA5A5)], dark: false),
]

private let TextDesigns: [Font.Design] = [.default, .serif, .rounded, .monospaced]

private let maxChars = 300

/// UpdatedFigma "Share your thoughts" text post. Full screen; gradient card
/// with centered input, bold + font cycle, palette swatches, Next CTA.
struct CreateTextPostView: View {
    var onClose: () -> Void
    var onPost: (String) -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var text = ""
    @State private var bold = false
    @State private var fontIdx = 0
    @State private var palette = 0

    private var pal: TextPalette { Palettes[palette] }
    private var ink: Color {
        if pal.white { return Color(rgb: 0x111111) }
        return pal.dark ? Color.white.opacity(0.92) : .white
    }
    private var hint: Color {
        pal.white ? Color.black.opacity(0.25) : Color.white.opacity(0.3)
    }
    private var fontSize: CGFloat {
        if text.count > 120 { return 16 }
        if text.count > 60 { return 20 }
        if text.count > 30 { return 26 }
        return 32
    }
    private var canPost: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            // Top bar — close · title · bold + font cycle
            HStack {
                Button(action: onClose) {
                    CreateIco.close(.white, side: 18 * scale)
                        .frame(width: 40 * scale, height: 40 * scale)
                        .background(Color.white.opacity(0.1))
                        .clipShape(Circle())
                }
                .buttonStyle(PressScaleStyle(pressedScale: 0.92))
                Spacer()
                AppText(Copy.t(.createTextTitle), style: Type.labelBold, color: .white, sizeOverride: 16)
                Spacer()
                HStack(spacing: 6 * scale) {
                    Button(action: { bold.toggle() }) {
                        AppText("B", style: Type.labelBold, color: .white, sizeOverride: 15)
                            .frame(width: 38 * scale, height: 38 * scale)
                            .background(bold ? Color(rgb: 0x8B5CF6).opacity(0.35) : Color.white.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                                    .stroke(bold ? Color(rgb: 0x8B5CF6) : .clear, lineWidth: 1.5 * scale)
                            )
                    }
                    .buttonStyle(.plain)
                    Button(action: { fontIdx = (fontIdx + 1) % TextDesigns.count }) {
                        AppText("Aa", style: Type.label, color: .white, sizeOverride: 13)
                            .padding(.horizontal, 12 * scale)
                            .frame(height: 38 * scale)
                            .background(Color.white.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, insets.top + 12 * scale)
            .padding(.bottom, 12 * scale)

            // Gradient card with centered input
            ZStack {
                LinearGradient(colors: pal.colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                if text.isEmpty {
                    Text(Copy.t(.createTextType))
                        .font(.system(size: 32 * scale, weight: .medium, design: TextDesigns[fontIdx]))
                        .foregroundColor(hint)
                        .allowsHitTesting(false)
                }
                TextEditorCompat(text: $text, maxChars: maxChars)
                    .font(.system(size: fontSize * scale, weight: bold ? .heavy : .medium, design: TextDesigns[fontIdx]))
                    .foregroundColor(ink)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 28 * scale)
                if !text.isEmpty {
                    AppText("\(text.count)/\(maxChars)", style: Type.micro, color: ink.opacity(0.4), sizeOverride: 11)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                        .padding(.trailing, 16 * scale)
                        .padding(.bottom, 14 * scale)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
            .padding(.horizontal, 16 * scale)
            .animation(.easeInOut(duration: 0.3), value: palette)

            // Palette swatches
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8 * scale) {
                    ForEach(Palettes.indices, id: \.self) { i in
                        let on = i == palette
                        Circle()
                            .fill(LinearGradient(colors: Palettes[i].colors, startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 28 * scale, height: 28 * scale)
                            .overlay(Circle().stroke(on ? Color.white : Color.white.opacity(0.18), lineWidth: (on ? 3 : 2) * scale))
                            .onTapGesture { palette = i }
                    }
                }
                .padding(.horizontal, 16 * scale)
            }
            .padding(.vertical, 14 * scale)

            // Next
            Button(action: { if canPost { onPost(text.trimmingCharacters(in: .whitespacesAndNewlines)) } }) {
                AppText(
                    Copy.t(.createNext),
                    style: Type.labelBold,
                    color: canPost ? .white : Color.white.opacity(0.25),
                    sizeOverride: 16
                )
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15 * scale)
                .background(
                    Group {
                        if canPost {
                            LinearGradient(
                                colors: [Color(rgb: 0x8B5CF6), Color(rgb: 0xEC4899)],
                                startPoint: .topLeading, endPoint: .bottomTrailing
                            )
                        } else {
                            Color.white.opacity(0.07)
                        }
                    }
                )
                .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            }
            .buttonStyle(PressScaleStyle(pressedScale: 0.97))
            .padding(.horizontal, 16 * scale)
            .padding(.bottom, insets.bottom + 20 * scale)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
    }
}

/// Centered multiline input on iOS 15: TextEditor with transparent backing.
private struct TextEditorCompat: View {
    @Binding var text: String
    var maxChars: Int

    init(text: Binding<String>, maxChars: Int) {
        _text = text
        self.maxChars = maxChars
        UITextView.appearance().backgroundColor = .clear
    }

    var body: some View {
        TextEditor(text: $text)
            .frame(maxHeight: 260)
            .fixedSize(horizontal: false, vertical: true)
            .onChange(of: text) { value in
                if value.count > maxChars { text = String(value.prefix(maxChars)) }
            }
    }
}
