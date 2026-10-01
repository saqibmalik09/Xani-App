import SwiftUI

private struct Slide {
    let tag: String
    let title: String
    let body: String
    let accent: Color
}

private let slides = [
    Slide(
        tag: "WATCH",
        title: "Stories that\nmove you",
        body: "Discover short videos, live streams and creator content tailored to your taste.",
        accent: Dark.cyan
    ),
    Slide(
        tag: "CREATE",
        title: "Your stage,\nyour rules",
        body: "Go live, post clips, share stories. Build an audience on your own terms.",
        accent: Dark.primarySoft
    ),
    Slide(
        tag: "EARN",
        title: "Passion\nthat pays",
        body: "Receive gifts, unlock rewards, and turn creativity into real earnings.",
        accent: Dark.gold
    ),
]

struct OnboardingView: View {
    let onDone: () -> Void

    @Environment(\.uiScale) private var scale

    @SceneStorage("onboarding.index") private var index = 0

    private var slide: Slide {
        slides[min(max(index, 0), slides.count - 1)]
    }

    var body: some View {
        AuthScaffold(background: Dark.background, scrollable: false) {
            HStack {
                Spacer(minLength: 0)
                Button(action: onDone) {
                    AppText("Skip", style: Type.caption, color: Dark.mutedForeground)
                        .padding(.horizontal, 16 * scale)
                        .padding(.vertical, 6 * scale)
                        .background(Color.white.opacity(0.06))
                        .clipShape(Capsule())
                        .overlay(Capsule().strokeBorder(Dark.primary.opacity(0.20), lineWidth: 1))
                }
                .buttonStyle(PressScaleStyle())
            }
            .padding(.horizontal, 24 * scale)
            .padding(.vertical, 12 * scale)

            Spacer(minLength: 0)

            // Re-identifying on the slide index remounts the artwork and copy, so the
            // shared Reveal modifier replays the entrance without a second animation
            // state machine here.
            illustration
                .id(index)
                .revealScale()

            Spacer(minLength: 0)

            VStack(alignment: .leading, spacing: 0) {
                AppText(slide.tag, style: Type.slideTag, color: slide.accent)
                    .padding(.horizontal, 10 * scale)
                    .padding(.vertical, 4 * scale)
                    .background(slide.accent.opacity(0.10))
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(slide.accent.opacity(0.25), lineWidth: 1))

                AppText(slide.title, style: Type.slideTitle, color: Dark.foreground)
                    .padding(.top, 12 * scale)

                AppText(slide.body, style: Type.bodyLight, color: Dark.mutedForeground)
                    .frame(maxWidth: 300 * scale, alignment: .leading)
                    .padding(.top, 12 * scale)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 32 * scale)
            .id(index)
            .revealUp()

            VStack(alignment: .leading, spacing: 20 * scale) {
                HStack(spacing: 6 * scale) {
                    ForEach(slides.indices, id: \.self) { position in
                        Button {
                            advance(to: position)
                        } label: {
                            Capsule()
                                .fill(
                                    position == index
                                        ? AnyShapeStyle(Gradients.brand)
                                        : AnyShapeStyle(Dark.primary.opacity(0.20))
                                )
                                .frame(width: position == index ? 28 * scale : 6 * scale, height: 4 * scale)
                                .contentShape(Rectangle().size(width: 24 * scale, height: 24 * scale))
                        }
                        .buttonStyle(PressScaleStyle())
                        .accessibilityLabel("Slide \(position + 1)")
                    }
                }

                DarkCta(
                    title: index < slides.count - 1 ? "Continue" : "Get started",
                    enabled: true
                ) {
                    if index < slides.count - 1 {
                        advance(to: index + 1)
                    } else {
                        onDone()
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 32 * scale)
            .padding(.top, 28 * scale)
            .padding(.bottom, 36 * scale)
        }
        .statusBar(lightBackground: false)
    }

    @ViewBuilder
    private var illustration: some View {
        switch index {
        case 0: WatchIllustration(baselineSide: 200)
        case 1: CreateIllustration(baselineSide: 200)
        default: EarnIllustration(baselineSide: 200)
        }
    }

    private func advance(to position: Int) {
        guard position != index else { return }
        Haptics.tap()
        index = position
    }
}
