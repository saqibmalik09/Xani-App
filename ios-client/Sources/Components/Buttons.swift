import SwiftUI
import UIKit

/// Light-surface CTA: 52pt tall, 6pt corners, brand gradient, greys out when invalid.
struct PrimaryCta<Leading: View>: View {
    let title: String
    let enabled: Bool
    /// Keeps the button lit but un-tappable while a request is in flight, so a double
    /// tap cannot submit the same form twice.
    var loading: Bool = false
    var trailingArrow: Bool = false
    private let leading: Leading
    let action: () -> Void

    @Environment(\.uiScale) private var scale

    init(
        title: String,
        enabled: Bool,
        loading: Bool = false,
        trailingArrow: Bool = false,
        @ViewBuilder leading: () -> Leading,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.enabled = enabled
        self.loading = loading
        self.trailingArrow = trailingArrow
        self.leading = leading()
        self.action = action
    }

    var body: some View {
        Button(action: {
            Haptics.tap()
            action()
        }) {
            ZStack {
                if loading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    HStack(spacing: 8 * scale) {
                        AppText(
                            title,
                            style: Type.labelStrong,
                            color: enabled ? .white : Light.muted,
                            alignment: .center
                        )
                        if trailingArrow {
                            Icon.arrowRight(16 * scale, tint: enabled ? .white : Light.muted)
                        }
                    }
                    HStack {
                        leading
                            .padding(.leading, 16 * scale)
                        Spacer(minLength: 0)
                    }
                    .allowsHitTesting(false)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52 * scale)
            .background(
                Group {
                    if enabled || loading {
                        Gradients.brand
                    } else {
                        Light.disabled
                    }
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
            .shadow(
                color: enabled ? Dark.primary.opacity(0.32) : .clear,
                radius: 10 * scale,
                y: 4 * scale
            )
        }
        .buttonStyle(PressScaleStyle(enabled: enabled && !loading))
        .disabled(!enabled || loading)
    }
}

extension PrimaryCta where Leading == EmptyView {
    init(
        title: String,
        enabled: Bool,
        loading: Bool = false,
        trailingArrow: Bool = false,
        action: @escaping () -> Void
    ) {
        self.init(
            title: title,
            enabled: enabled,
            loading: loading,
            trailingArrow: trailingArrow,
            leading: { EmptyView() },
            action: action
        )
    }
}

/// Dark-surface CTA from the Figma `Btn` component: 56pt tall, 18pt corners.
struct DarkCta: View {
    let title: String
    let enabled: Bool
    var loading: Bool = false
    let action: () -> Void

    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: {
            Haptics.tap()
            action()
        }) {
            Group {
                if loading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    AppText(title, style: Type.labelStrong, color: .white, alignment: .center)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56 * scale)
            .background(Gradients.brand)
            .clipShape(RoundedRectangle(cornerRadius: 18 * scale, style: .continuous))
            .shadow(color: Dark.primary.opacity(0.38), radius: 14 * scale, y: 6 * scale)
            .opacity(enabled || loading ? 1 : 0.38)
        }
        .buttonStyle(PressScaleStyle(enabled: enabled && !loading))
        .disabled(!enabled || loading)
    }
}

/// Provider row: white pill with the brand glyph pinned to the leading edge and the
/// label optically centred, matching the Figma auth sheet.
struct ProviderButton<Glyph: View>: View {
    let label: String
    let action: () -> Void
    @ViewBuilder let glyph: () -> Glyph

    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: action) {
            ZStack {
                AppText(label, style: Type.labelStrong, color: Light.text, alignment: .center)
                    .frame(maxWidth: .infinity)
                HStack {
                    glyph()
                    Spacer(minLength: 0)
                }
            }
            .padding(.horizontal, 16 * scale)
            .frame(maxWidth: .infinity)
            .frame(height: 52 * scale)
            .background(Light.background)
            .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 6 * scale, style: .continuous)
                    .strokeBorder(Light.borderStrong, lineWidth: 1.5 * scale)
            )
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(label)
    }
}

/// Compact provider tile used on the login screen (glyph only).
struct ProviderTile<Glyph: View>: View {
    let accessibilityName: String
    let action: () -> Void
    @ViewBuilder let glyph: () -> Glyph

    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: action) {
            glyph()
                .frame(maxWidth: .infinity)
                .frame(height: 48 * scale)
                .background(Light.background)
                .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 6 * scale, style: .continuous)
                        .strokeBorder(Light.borderStrong, lineWidth: 1.5 * scale)
                )
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(accessibilityName)
    }
}

struct CircleIconButton<Glyph: View>: View {
    let diameter: CGFloat
    let borderColor: Color
    let accessibilityName: String
    let action: () -> Void
    @ViewBuilder let glyph: () -> Glyph

    @Environment(\.uiScale) private var scale

    var body: some View {
        Button(action: action) {
            glyph()
                .frame(width: diameter, height: diameter)
                .overlay(Circle().strokeBorder(borderColor, lineWidth: 1.5 * scale))
                .contentShape(Circle())
        }
        .buttonStyle(PressScaleStyle(pressedScale: 0.92))
        .accessibilityLabel(accessibilityName)
    }
}

struct TextLink: View {
    let title: String
    var color: Color = Dark.primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppText(title, style: Type.labelBold, color: color)
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// One generator, created lazily and reused; allocating a new one per tap is a
/// documented source of first-tap latency.
@MainActor
enum Haptics {
    private static let light = UIImpactFeedbackGenerator(style: .light)

    static func tap() {
        light.impactOccurred()
    }
}
