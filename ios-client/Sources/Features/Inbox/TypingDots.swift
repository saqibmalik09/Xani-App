import SwiftUI

/// Three dots riding a wave — "the other side is writing".
///
/// One repeating animation per dot, each a third of a cycle behind the last,
/// which is what reads as a wave rather than a blink. Offset and opacity only,
/// so it costs nothing beyond the layer it already has.
struct TypingDots: View {
    var tint: Color
    var dot: CGFloat = 6

    @State private var lifted = false

    var body: some View {
        HStack(spacing: dot * 0.55) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(tint)
                    .frame(width: dot, height: dot)
                    .offset(y: lifted ? -dot * 0.6 : 0)
                    .opacity(lifted ? 1 : 0.45)
                    .animation(
                        .easeInOut(duration: 0.45)
                            .repeatForever(autoreverses: true)
                            .delay(Double(i) * 0.15),
                        value: lifted
                    )
            }
        }
        .onAppear { lifted = true }
    }
}
