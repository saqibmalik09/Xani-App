import SwiftUI

/// Figma uses Outfit (UI) and Fraunces (display). Until the licensed fonts are
/// bundled these alias to the system faces, which cost nothing to load and never
/// block first frame on a font file read. Swapping in the real families is two
/// lines here and no change anywhere else.
enum FontFamily {
    case ui
    case display

    func font(size: CGFloat, weight: Font.Weight) -> Font {
        switch self {
        case .ui: return .system(size: size, weight: weight, design: .default)
        case .display: return .system(size: size, weight: weight, design: .serif)
        }
    }
}

struct TypeStyle {
    var family: FontFamily = .ui
    var size: CGFloat
    var weight: Font.Weight = .regular
    /// Letter spacing in points at baseline scale.
    var kerning: CGFloat = 0
    /// Extra leading in points at baseline scale; SwiftUI adds it between lines.
    var lineSpacing: CGFloat = 0
}

enum Type {
    static let wordmark = TypeStyle(size: 52, weight: .heavy, kerning: -2.1)
    static let tagline = TypeStyle(size: 12, weight: .regular, kerning: 3.4)
    static let slideTitle = TypeStyle(family: .display, size: 38, weight: .bold, lineSpacing: 3)
    static let slideTag = TypeStyle(size: 10, weight: .bold, kerning: 2.2)
    static let h1 = TypeStyle(size: 28, weight: .bold, kerning: -0.56)
    static let h2 = TypeStyle(size: 26, weight: .bold, kerning: -0.52)
    static let body = TypeStyle(size: 16, weight: .regular, lineSpacing: 4)
    static let bodyLight = TypeStyle(size: 16, weight: .light, lineSpacing: 6)
    static let label = TypeStyle(size: 16, weight: .medium)
    static let labelStrong = TypeStyle(size: 16, weight: .semibold)
    static let labelBold = TypeStyle(size: 16, weight: .semibold)
    static let caption = TypeStyle(size: 14, weight: .regular, lineSpacing: 4)
    static let micro = TypeStyle(size: 12, weight: .regular, lineSpacing: 3)
    static let otp = TypeStyle(size: 24, weight: .bold)
}
