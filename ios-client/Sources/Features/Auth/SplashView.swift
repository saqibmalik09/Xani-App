import SwiftUI

struct SplashView: View {
    let onDone: () -> Void

    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var phase = 0

    private static let wordmarkAt = 180
    private static let taglineAt = 380

    var body: some View {
        AuthScaffold(background: Dark.background, glow: true, scrollable: false) {
            Spacer(minLength: 0)

            VStack(spacing: 20 * scale) {
                BrandMark(side: 88 * scale)
                    .opacity(phase >= 1 ? 1 : 0)
                    .scaleEffect(phase >= 1 ? 1 : 0.72)

                AppText(
                    brand.wordmark,
                    style: Type.wordmark,
                    gradient: Gradients.wordmark,
                    alignment: .center
                )
                .opacity(phase >= 2 ? 1 : 0)
                .offset(y: phase >= 2 ? 0 : 10 * scale)

                if !brand.tagline.isEmpty {
                    AppText(
                        brand.tagline,
                        style: Type.tagline,
                        color: Dark.faint,
                        alignment: .center
                    )
                    .opacity(phase >= 3 ? 1 : 0)
                    .offset(y: phase >= 3 ? 0 : 8 * scale)
                }
            }

            Spacer(minLength: 0)
        }
        .statusBar(lightBackground: false)
        .task { await run() }
    }

    private func run() async {
        guard !reduceMotion else {
            phase = 3
            await sleepMillis(LaunchTiming.splashMinimumMillis)
            onDone()
            return
        }

        withAnimation(.spring(response: 0.5, dampingFraction: 0.78)) { phase = 1 }
        await sleepMillis(Self.wordmarkAt)
        withAnimation(.easeOut(duration: 0.3)) { phase = 2 }
        await sleepMillis(Self.taglineAt - Self.wordmarkAt)
        withAnimation(.easeOut(duration: 0.26)) { phase = 3 }

        await sleepMillis(LaunchTiming.splashMinimumMillis - Self.taglineAt)
        onDone()
    }
}
