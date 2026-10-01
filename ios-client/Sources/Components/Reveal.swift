import SwiftUI

/// Staggered entrance used across the auth screens. Only opacity, offset and scale
/// animate, all of which stay on the GPU and never trigger a layout pass.
private struct Reveal: ViewModifier {
    let delayMillis: Int
    let rise: CGFloat
    let fromScale: CGFloat

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .scaleEffect(shown ? 1 : fromScale)
            .offset(y: shown ? 0 : rise)
            .task {
                guard !reduceMotion else {
                    shown = true
                    return
                }
                await sleepMillis(delayMillis)
                withAnimation(.spring(response: 0.28, dampingFraction: 0.9)) {
                    shown = true
                }
            }
    }
}

extension View {
    func revealUp(delayMillis: Int = 0) -> some View {
        modifier(Reveal(delayMillis: delayMillis, rise: 14, fromScale: 1))
    }

    func revealScale(delayMillis: Int = 0) -> some View {
        modifier(Reveal(delayMillis: delayMillis, rise: 0, fromScale: 0.86))
    }
}

/// Press feedback for every tappable surface, matching the Android `pressable`
/// modifier's 0.97 squeeze.
struct PressScaleStyle: ButtonStyle {
    var enabled: Bool = true
    var pressedScale: CGFloat = 0.97

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && enabled ? pressedScale : 1)
            .animation(.easeOut(duration: 0.04), value: configuration.isPressed)
    }
}
