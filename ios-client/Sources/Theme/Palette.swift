import SwiftUI
import UIKit

extension Color {
    /// Explicit sRGB so a screen renders identically on Android and iOS.
    init(rgb: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255,
            opacity: opacity
        )
    }

    /// Follows the device appearance. Light is the default when the trait is
    /// unspecified; dark values match the cinematic [Dark] surfaces.
    init(light: UInt32, dark: UInt32) {
        self.init(
            UIColor { trait in
                let rgb = trait.userInterfaceStyle == .dark ? dark : light
                return UIColor(
                    red: CGFloat((rgb >> 16) & 0xFF) / 255,
                    green: CGFloat((rgb >> 8) & 0xFF) / 255,
                    blue: CGFloat(rgb & 0xFF) / 255,
                    alpha: 1
                )
            }
        )
    }
}

enum Dark {
    static let background = Color(rgb: 0x07070F)
    static let foreground = Color(rgb: 0xF0EEFF)
    static let card = Color(rgb: 0x111124)
    static let primary = Color(rgb: 0x8B5CF6)
    static let primarySoft = Color(rgb: 0xA78BFA)
    static let accent = Color(rgb: 0xEC4899)
    static let accentSoft = Color(rgb: 0xF472B6)
    static let secondary = Color(rgb: 0x1A1A35)
    static let muted = Color(rgb: 0x1E1E38)
    static let mutedForeground = Color(rgb: 0x7C7CA8)
    static let dim = Color(rgb: 0x5C5C84)
    static let faint = Color(rgb: 0x4A4A72)
    static let cyan = Color(rgb: 0x22D3EE)
    static let gold = Color(rgb: 0xFCD34D)
    static let success = Color(rgb: 0x34D399)
    static let warning = Color(rgb: 0xF59E0B)
    /// Lighter than `Light.error`: the same 0xEF4444 on a near-black surface fails
    /// contrast for body text.
    static let error = Color(rgb: 0xF87171)
    static let markStart = Color(rgb: 0xA78BFA)
    static let markEnd = Color(rgb: 0xF472B6)
}

enum Light {
    static let background = Color(light: 0xFFFFFF, dark: 0x07070F)
    static let surface = Color(light: 0xF7F7F7, dark: 0x111124)
    static let input = Color(light: 0xFAFAFA, dark: 0x1A1A35)
    static let text = Color(light: 0x111111, dark: 0xF0EEFF)
    static let textSoft = Color(light: 0x555555, dark: 0xC4C4DC)
    static let sub = Color(light: 0x888888, dark: 0x7C7CA8)
    static let hint = Color(light: 0x999999, dark: 0x5C5C84)
    static let muted = Color(light: 0xAAAAAA, dark: 0x4A4A72)
    static let border = Color(light: 0xEBEBEB, dark: 0x252542)
    static let borderStrong = Color(light: 0xDBDBDB, dark: 0x3D3D5C)
    static let borderIcon = Color(light: 0xD0D0D0, dark: 0x4A4A72)
    static let disabled = Color(light: 0xE5E5E5, dark: 0x1A1A35)
    static let divider = Color(light: 0xE8E8E8, dark: 0x252542)
    static let error = Color(rgb: 0xEF4444)
}

enum Gradients {
    static let brand = LinearGradient(
        colors: [Dark.primary, Dark.accent],
        startPoint: .leading,
        endPoint: .trailing
    )
    static let wordmark = LinearGradient(
        colors: [Color(rgb: 0xE2D9FF), Color(rgb: 0xF9D6EE)],
        startPoint: .leading,
        endPoint: .trailing
    )
    static let mark = LinearGradient(
        colors: [Dark.markStart, Dark.markEnd],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
    static let darkGlow = RadialGradient(
        colors: [Dark.primary.opacity(0.18), .clear],
        center: .center,
        startRadius: 0,
        endRadius: 460
    )
}

enum UIPalette {
    static let background = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 7 / 255, green: 7 / 255, blue: 15 / 255, alpha: 1)
            : .white
    }
    static let text = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 240 / 255, green: 238 / 255, blue: 255 / 255, alpha: 1)
            : UIColor(white: 0.067, alpha: 1)
    }
    static let sub = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 124 / 255, green: 124 / 255, blue: 168 / 255, alpha: 1)
            : UIColor(white: 0.533, alpha: 1)
    }
}
