import SwiftUI

// Same viewport and same geometry as the Android illustrations, so the onboarding
// artwork is identical on both platforms without shipping a single bitmap.

private let viewport: CGFloat = 200

private let frameColor = Color(rgb: 0x111128)
private let screenColor = Color(rgb: 0x1A1A35)
private let coinCore = Color(rgb: 0x1A1510)
private let giftBody = Color(rgb: 0x1E1A35)
private let gold = Dark.gold

private let watchShapes: [VShape] = [
    .box(54, 20, 92, 160, 18, .strokedFill(frameColor, Dark.primary.opacity(0.30), 1.5)),
    .box(60, 26, 80, 148, 13, .gradientFill([Dark.cyan, Dark.primary], alpha: 0.12)),
    .box(60, 26, 80, 148, 13, .fill(screenColor)),
    .circle(100, 90, 28, .strokedFill(Dark.primary.opacity(0.15), Dark.primary.opacity(0.30), 1)),
    .circle(100, 80, 10, .fill(Dark.primarySoft.opacity(0.50))),
    .ellipse(100, 100, 16, 8, .fill(Dark.primarySoft.opacity(0.30))),
    .data("M96 84 L96 98 L107 91 Z", .fill(.white, alpha: 0.9)),
    .circle(126, 108, 8, .fill(Color.white.opacity(0.07))),
    .circle(126, 108, 3, .fill(Dark.primarySoft.opacity(0.60))),
    .circle(126, 126, 8, .fill(Color.white.opacity(0.07))),
    .circle(126, 126, 3, .fill(Dark.primarySoft.opacity(0.60))),
    .circle(126, 144, 8, .fill(Color.white.opacity(0.07))),
    .circle(126, 144, 3, .fill(Dark.primarySoft.opacity(0.60))),
    .box(66, 148, 48, 3, 1.5, .fill(Color.white.opacity(0.25))),
    .box(66, 155, 36, 2, 1, .fill(Color.white.opacity(0.12))),
    .box(88, 27, 24, 4, 2, .fill(Dark.background)),
    .box(64, 30, 26, 12, 4, .fill(Dark.accent)),
]

private let createShapes: [VShape] = [
    .box(30, 40, 140, 110, 16, .strokedFill(frameColor, Dark.primary.opacity(0.25), 1.5)),
    .data("M46 56 L36 56 L36 46", .stroke(Dark.markStart, 2)),
    .data("M154 56 L164 56 L164 46", .stroke(Dark.markStart, 2)),
    .data("M46 134 L36 134 L36 144", .stroke(Dark.markEnd, 2)),
    .data("M154 134 L164 134 L164 144", .stroke(Dark.markEnd, 2)),
    .circle(100, 82, 18, .fill(Dark.primarySoft.opacity(0.20))),
    .ellipse(100, 108, 28, 14, .fill(Dark.primarySoft.opacity(0.12))),
    .line(77, 40, 77, 150, .stroke(Dark.primary.opacity(0.08), 1)),
    .line(123, 40, 123, 150, .stroke(Dark.primary.opacity(0.08), 1)),
    .line(30, 77, 170, 77, .stroke(Dark.primary.opacity(0.08), 1)),
    .line(30, 113, 170, 113, .stroke(Dark.primary.opacity(0.08), 1)),
    .box(30, 158, 140, 34, 10, .fill(frameColor)),
    .circle(54, 175, 7, .strokedFill(Color.white.opacity(0.08), Dark.primary.opacity(0.20), 1)),
    .circle(78, 175, 7, .strokedFill(Color.white.opacity(0.08), Dark.primary.opacity(0.20), 1)),
    .circle(122, 175, 7, .strokedFill(Color.white.opacity(0.08), Dark.primary.opacity(0.20), 1)),
    .circle(146, 175, 7, .strokedFill(Color.white.opacity(0.08), Dark.primary.opacity(0.20), 1)),
    .circle(100, 175, 9, .gradientFill([Dark.markStart, Dark.markEnd])),
    .circle(100, 175, 5, .fill(.white)),
    .data("M158 166 L155 174 L159 174 L156 182", .stroke(Dark.warning, 1.5)),
]

private let earnShapes: [VShape] = [
    .circle(100, 105, 42, .strokedFill(gold.opacity(0.08), gold.opacity(0.30), 1.5)),
    .circle(100, 105, 32, .strokedFill(coinCore, gold.opacity(0.50), 1.5)),
    .box(36, 128, 32, 28, 5, .strokedFill(giftBody, Dark.primary.opacity(0.40), 1)),
    .box(36, 120, 32, 10, 3, .strokedFill(Dark.primarySoft.opacity(0.20), Dark.primary.opacity(0.40), 1)),
    .line(52, 120, 52, 156, .stroke(Dark.primarySoft.opacity(0.50), 1.5)),
    .data("M52 120 C52 115 44 112 44 116 C44 120 52 120 52 120", .stroke(Dark.primarySoft.opacity(0.70), 1.5)),
    .data("M52 120 C52 115 60 112 60 116 C60 120 52 120 52 120", .stroke(Dark.primarySoft.opacity(0.70), 1.5)),
    .data("M130 134 L134 120 L140 128 L146 116 L152 128 L158 120 L162 134 Z", .strokedFill(gold.opacity(0.15), gold, 1.5)),
    .circle(72, 72, 7, .strokedFill(gold.opacity(0.15), gold.opacity(0.40), 1)),
    .circle(148, 88, 7, .strokedFill(gold.opacity(0.15), gold.opacity(0.40), 1)),
    .circle(58, 145, 7, .strokedFill(gold.opacity(0.15), gold.opacity(0.40), 1)),
    .circle(68, 56, 2.5, .fill(gold, alpha: 0.9)),
    .circle(148, 62, 2.5, .fill(gold, alpha: 0.7)),
    .circle(40, 90, 2.5, .fill(gold, alpha: 0.5)),
    .circle(164, 98, 2.5, .fill(gold, alpha: 0.6)),
    .circle(90, 36, 2.5, .fill(gold, alpha: 0.8)),
]

/// `baselineSide` is a 390pt-frame number. Scaling happens here so the embedded
/// labels are not scaled twice: `AppText` already applies `uiScale` to its size.
struct WatchIllustration: View {
    let baselineSide: CGFloat

    @Environment(\.uiScale) private var scale

    var body: some View {
        let side = baselineSide * scale
        ZStack(alignment: .topLeading) {
            VectorCanvas(watchShapes, viewport: viewport, side: side)
            AppText(
                "LIVE",
                style: Type.slideTag,
                color: .white,
                sizeOverride: baselineSide * (7 / viewport)
            )
            .offset(x: side * (67 / viewport), y: side * (32 / viewport))
        }
        .frame(width: side, height: side)
    }
}

struct CreateIllustration: View {
    let baselineSide: CGFloat

    @Environment(\.uiScale) private var scale

    var body: some View {
        VectorCanvas(createShapes, viewport: viewport, side: baselineSide * scale)
    }
}

struct EarnIllustration: View {
    let baselineSide: CGFloat

    @Environment(\.uiScale) private var scale

    var body: some View {
        let side = baselineSide * scale
        ZStack(alignment: .topLeading) {
            VectorCanvas(earnShapes, viewport: viewport, side: side)
            AppText(
                "¥",
                style: Type.h1,
                color: gold,
                sizeOverride: baselineSide * (26 / viewport)
            )
            .offset(x: side * (92 / viewport), y: side * (90 / viewport))
        }
        .frame(width: side, height: side)
    }
}
