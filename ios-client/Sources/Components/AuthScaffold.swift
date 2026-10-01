import SwiftUI

/// Shared frame for every auth screen. Colour fills the display, including under
/// the status bar and home indicator. Interactive content is inset. Keyboard
/// handled here; width is capped on iPads and landscape phones.
struct AuthScaffold<Content: View>: View {
    let background: Color
    var glow: Bool = false
    var scrollable: Bool = true
    @ViewBuilder var content: () -> Content

    @Environment(\.systemSafeInsets) private var insets

    var body: some View {
        ZStack {
            background
                .ignoresSafeArea()

            if glow {
                Gradients.darkGlow
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }

            if scrollable {
                ScrollView(.vertical, showsIndicators: false) {
                    frame
                }
                .padding(insets)
                .modifier(InteractiveKeyboardDismiss())
            } else {
                // Only the non-scrolling branch gets an unbounded height, so a
                // Spacer inside a scrolling column can never collapse or, worse,
                // try to fill an infinite scroll extent.
                frame
                    .frame(maxHeight: .infinity)
                    .padding(insets)
            }
        }
    }

    private var frame: some View {
        VStack(spacing: 0) {
            content()
        }
        .frame(maxWidth: Layout.contentMaxWidth)
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

/// Drag-to-dismiss on the keyboard is iOS 16; on 15 the field's own return key and
/// the tap-outside gesture cover it, so the modifier degrades instead of gating the
/// deployment target.
private struct InteractiveKeyboardDismiss: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.scrollDismissesKeyboard(.interactively)
        } else {
            content
        }
    }
}

/// Omit `lightBackground` to follow the device. Cinematic screens pass `false`
/// so splash, onboarding, and OTP stay on the dark brand surface.
struct StatusBarStyle: ViewModifier {
    var lightBackground: Bool?

    @ViewBuilder
    func body(content: Content) -> some View {
        if let lightBackground {
            content.preferredColorScheme(lightBackground ? .light : .dark)
        } else {
            content
        }
    }
}

extension View {
    func statusBar(lightBackground: Bool? = nil) -> some View {
        modifier(StatusBarStyle(lightBackground: lightBackground))
    }
}
