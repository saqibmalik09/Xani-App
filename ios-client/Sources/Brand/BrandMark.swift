import SwiftUI

/// The mark: two rounded bars crossed at 45 degrees, with a cyan and a pink echo
/// offset behind the gradient face. Pure geometry, so it is crisp on every device
/// and costs no image decode at launch.
///
/// Replacing the logo means replacing this one view.
struct BrandMark: View {
    let side: CGFloat

    var body: some View {
        Canvas(opaque: false, rendersAsynchronously: false) { context, size in
            let extent = min(size.width, size.height)
            let shape = Self.crossPath(side: extent)

            // Each echo is a single fill of the whole union, so the bars' overlap
            // does not blend twice and show a bright cross in the middle.
            context.fill(
                shape.offsetBy(dx: extent * 0.09, dy: -extent * 0.065),
                with: .color(Dark.cyan.opacity(0.55))
            )
            context.fill(
                shape.offsetBy(dx: -extent * 0.065, dy: extent * 0.09),
                with: .color(Dark.accentSoft.opacity(0.42))
            )
            context.fill(
                shape,
                with: .linearGradient(
                    Gradient(colors: [Dark.markStart, Dark.markEnd]),
                    startPoint: .zero,
                    endPoint: CGPoint(x: extent, y: extent)
                )
            )
        }
        .frame(width: side, height: side)
        .allowsHitTesting(false)
    }

    /// Both bars live in one path and are filled non-zero, which renders as their
    /// union without needing the iOS 16 boolean path operators.
    private static func crossPath(side: CGFloat) -> Path {
        let barHeight = side * 0.225
        let barWidth = side * 1.32
        let rect = CGRect(
            x: (side - barWidth) / 2,
            y: (side - barHeight) / 2,
            width: barWidth,
            height: barHeight
        )
        let center = CGPoint(x: side / 2, y: side / 2)

        func bar(_ degrees: CGFloat) -> Path {
            let transform = CGAffineTransform(translationX: center.x, y: center.y)
                .rotated(by: degrees * .pi / 180)
                .translatedBy(x: -center.x, y: -center.y)
            return Path(roundedRect: rect, cornerRadius: barHeight / 2).applying(transform)
        }

        var combined = bar(45)
        combined.addPath(bar(-45))
        return combined
    }
}

/// Mark plus wordmark. Sizes are baseline (390pt frame) numbers; scaling happens
/// inside, so callers must not pre-scale them.
struct BrandLockup: View {
    let wordmark: String
    var markSize: CGFloat = 36
    var textSize: CGFloat = 36

    @Environment(\.uiScale) private var scale

    var body: some View {
        HStack(spacing: markSize * 0.28 * scale) {
            BrandMark(side: markSize * scale)
            AppText(wordmark, style: Type.wordmark, gradient: Gradients.wordmark, sizeOverride: textSize)
        }
    }
}
