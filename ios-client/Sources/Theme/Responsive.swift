import SwiftUI

/// Figma authors at a 390pt frame. Every dimension in the screens is a baseline
/// number multiplied by this factor, clamped so a 4.7" phone stays legible and an
/// iPad does not inflate the layout into cartoon proportions.
enum Layout {
    static let baselineWidth: CGFloat = 390
    static let contentMaxWidth: CGFloat = 460
    static let scaleRange: ClosedRange<CGFloat> = 0.82...1.18

    static func scale(forWidth width: CGFloat) -> CGFloat {
        min(max(width / baselineWidth, scaleRange.lowerBound), scaleRange.upperBound)
    }
}

private struct UIScaleKey: EnvironmentKey {
    static let defaultValue: CGFloat = 1
}

extension EnvironmentValues {
    /// Set once at the root so every screen reads the same factor. Screens must not
    /// re-derive it, or nested GeometryReaders start disagreeing mid-layout.
    var uiScale: CGFloat {
        get { self[UIScaleKey.self] }
        set { self[UIScaleKey.self] = newValue }
    }
}
