import Foundation
import SwiftUI
import Combine

struct LensBeauty: Equatable {
    var smooth: Float = 0
    var whiten: Float = 0
    var sharpen: Float = 0
    var eyes: Float = 0
    var slim: Float = 0
    var glow: Float = 0
    var active: Bool { smooth > 0 || whiten > 0 || sharpen > 0 || eyes > 0 || slim > 0 || glow > 0 }
    static let off = LensBeauty()
}

struct LensLook: Equatable, Identifiable {
    let id: String
    let name: String
    var exposure: Float = 0
    var contrast: Float = 0
    var saturation: Float = 0
    var temperature: Float = 0
    var tint: Float = 0
    var fade: Float = 0
    var vignette: Float = 0
    var hue: Float = 0
    var split: Float = 0
    var mono: Float = 0
    var grain: Float = 0
    var swatch: UInt32
    var neutral: Bool {
        exposure == 0 && contrast == 0 && saturation == 0 && temperature == 0 && tint == 0 &&
        fade == 0 && vignette == 0 && hue == 0 && split == 0 && mono == 0 && grain == 0
    }
}

enum LensBackground: Equatable {
    case none
    case blur
    case gradient(id: String, top: UInt32, bottom: UInt32)
    case image(URL)
    var isNone: Bool { if case .none = self { return true } else { return false } }
    var id: String {
        switch self {
        case .none: return "bg.none"
        case .blur: return "bg.blur"
        case .gradient(let id, _, _): return id
        case .image: return "bg.image"
        }
    }
}

struct LensMask: Equatable, Identifiable {
    let id: String
    let name: String
    let glyph: String
}

struct LensState: Equatable {
    var beauty: LensBeauty = .off
    var lookId: String = LensCatalog.naturalLook
    var background: LensBackground = .none
    var masks: [String] = []
    var identity: Bool {
        !beauty.active && lookId == LensCatalog.naturalLook && background.isNone && masks.isEmpty
    }
    var needsFace: Bool { beauty.active || !masks.isEmpty }
    var needsSegmentation: Bool { !background.isNone }
}

final class LensController: ObservableObject {
    @Published var state = LensState()
    var capsBackground = true
    var capsMasks = true
    static let maxMasks = 3

    init() {
        switch DeviceClass.current() {
        case .low: capsBackground = false
        default: capsBackground = true
        }
    }

    func setBeauty(_ b: LensBeauty) { state.beauty = b }
    func setLook(_ id: String) {
        state.lookId = LensCatalog.look(id) != nil ? id : LensCatalog.naturalLook
    }
    func setBackground(_ bg: LensBackground) {
        if capsBackground || bg.isNone { state.background = bg }
    }
    func toggleMask(_ id: String) {
        guard capsMasks, LensCatalog.mask(id) != nil else { return }
        if let i = state.masks.firstIndex(of: id) {
            state.masks.remove(at: i)
        } else {
            var next = state.masks + [id]
            if next.count > Self.maxMasks { next = Array(next.suffix(Self.maxMasks)) }
            state.masks = next
        }
    }
    func reset() { state = LensState() }
}

enum LensStore {
    static let post = LensController()
    static let live = LensController()
}

enum LensCatalog {
    static let naturalLook = "look.natural"
    enum Category { case beauty, looks, background, masks }

    struct BeautyPreset: Identifiable {
        let id: String
        let name: String
        let beauty: LensBeauty
        let glyph: String
    }

    static let beauty: [BeautyPreset] = [
        BeautyPreset(id: "b.off", name: "Off", beauty: .off, glyph: "○"),
        BeautyPreset(id: "b.soft", name: "Soft", beauty: LensBeauty(smooth: 0.48, sharpen: 0.14), glyph: "✦"),
        BeautyPreset(id: "b.natural", name: "Natural", beauty: LensBeauty(smooth: 0.62, whiten: 0.18, sharpen: 0.22, eyes: 0.07, glow: 0.12), glyph: "✧"),
        BeautyPreset(id: "b.glow", name: "Glow", beauty: LensBeauty(smooth: 0.72, whiten: 0.32, sharpen: 0.26, eyes: 0.1, glow: 0.55), glyph: "✨"),
        BeautyPreset(id: "b.glam", name: "Glam", beauty: LensBeauty(smooth: 0.86, whiten: 0.4, sharpen: 0.3, eyes: 0.16, slim: 0.16, glow: 0.42), glyph: "💄"),
        BeautyPreset(id: "b.porcelain", name: "Porcelain", beauty: LensBeauty(smooth: 0.94, whiten: 0.52, sharpen: 0.34, eyes: 0.18, slim: 0.2, glow: 0.55), glyph: "🤍"),
        BeautyPreset(id: "b.sculpt", name: "Sculpt", beauty: LensBeauty(smooth: 0.52, sharpen: 0.38, eyes: 0.22, slim: 0.28, glow: 0.1), glyph: "🪞"),
    ]

    static let looks: [LensLook] = [
        LensLook(id: naturalLook, name: "Natural", swatch: 0xFFE5E7EB),
        LensLook(id: "look.vivid", name: "Vivid", exposure: 0.03, contrast: 0.12, saturation: 0.28, swatch: 0xFFF97316),
        LensLook(id: "look.clarity", name: "Clarity", exposure: 0.05, contrast: 0.18, saturation: 0.06, swatch: 0xFF60A5FA),
        LensLook(id: "look.golden", name: "Golden Hour", exposure: 0.06, contrast: 0.06, saturation: 0, temperature: 0.32, tint: 0.06, fade: 0.04, split: 0.35, swatch: 0xFFF59E0B),
        LensLook(id: "look.teal", name: "Teal & Orange", contrast: 0.1, saturation: 0.08, split: 0.6, swatch: 0xFF14B8A6),
        LensLook(id: "look.film.warm", name: "Warm Film", contrast: -0.04, saturation: -0.08, temperature: 0.2, fade: 0.14, vignette: 0.2, grain: 0.18, swatch: 0xFFD97706),
        LensLook(id: "look.film.cool", name: "Cool Film", contrast: 0, saturation: -0.1, temperature: -0.22, tint: -0.04, fade: 0.12, vignette: 0.2, grain: 0.18, swatch: 0xFF3B82F6),
        LensLook(id: "look.pastel", name: "Pastel", exposure: 0.08, contrast: -0.1, saturation: -0.18, fade: 0.22, swatch: 0xFFF9A8D4),
        LensLook(id: "look.rose", name: "Rosé", saturation: 0.08, temperature: 0.08, tint: 0.16, fade: 0.06, swatch: 0xFFFB7185),
        LensLook(id: "look.peach", name: "Peach", exposure: 0.05, saturation: 0.1, temperature: 0.18, tint: 0.1, swatch: 0xFFFDBA74),
        LensLook(id: "look.cinematic", name: "Cinema", contrast: 0.22, saturation: -0.06, fade: 0.08, vignette: 0.28, split: 0.4, swatch: 0xFF1F2937),
        LensLook(id: "look.drama", name: "Drama", exposure: -0.04, contrast: 0.32, saturation: 0.12, vignette: 0.22, swatch: 0xFF7C3AED),
        LensLook(id: "look.ice", name: "Ice", exposure: 0.06, contrast: 0.1, saturation: 0.04, temperature: -0.34, tint: -0.06, swatch: 0xFF67E8F9),
        LensLook(id: "look.cherry", name: "Cherry", contrast: 0.08, saturation: 0.18, temperature: 0.12, tint: 0.22, swatch: 0xFFE11D48),
        LensLook(id: "look.cyber", name: "Cyber", contrast: 0.28, saturation: 0.42, temperature: -0.16, vignette: 0.24, split: 0.7, swatch: 0xFF7C3AED),
        LensLook(id: "look.sunset", name: "Sunset", exposure: 0.04, temperature: 0.38, tint: 0.08, split: 0.5, swatch: 0xFFEA580C),
        LensLook(id: "look.neon", name: "Neon Night", contrast: 0.22, saturation: 0.34, temperature: -0.1, vignette: 0.3, split: 0.45, swatch: 0xFFA855F7),
        LensLook(id: "look.vintage", name: "Vintage", contrast: -0.04, saturation: -0.22, temperature: 0.14, fade: 0.2, vignette: 0.34, grain: 0.26, swatch: 0xFFB45309),
        LensLook(id: "look.noir", name: "Noir", exposure: -0.02, contrast: 0.26, vignette: 0.36, mono: 1, grain: 0.2, swatch: 0xFF374151),
        LensLook(id: "look.silver", name: "Silver", exposure: 0.08, contrast: 0.08, fade: 0.1, mono: 1, swatch: 0xFF9CA3AF),
        LensLook(id: "look.sepia", name: "Sepia", contrast: 0.06, vignette: 0.2, split: 0.9, mono: 1, swatch: 0xFF92400E),
        LensLook(id: "look.punch", name: "Punch", exposure: 0.04, contrast: 0.28, saturation: 0.36, swatch: 0xFFEF4444),
        LensLook(id: "look.matte", name: "Matte", contrast: -0.08, saturation: -0.12, fade: 0.28, vignette: 0.16, swatch: 0xFF78716C),
        LensLook(id: "look.forest", name: "Forest", contrast: 0.1, saturation: 0.16, temperature: -0.08, tint: -0.12, split: 0.35, swatch: 0xFF16A34A),
        LensLook(id: "look.candy", name: "Candy", exposure: 0.06, saturation: 0.34, temperature: 0.1, tint: 0.18, swatch: 0xFFF472B6),
    ]

    static let backgrounds: [LensBackground] = [
        .none, .blur,
        .gradient(id: "bg.studio", top: 0xFF111827, bottom: 0xFF000000),
        .gradient(id: "bg.sunset", top: 0xFFFF7E5F, bottom: 0xFFFEB47B),
        .gradient(id: "bg.ocean", top: 0xFF2193B0, bottom: 0xFF6DD5ED),
        .gradient(id: "bg.candy", top: 0xFFEC4899, bottom: 0xFF8B5CF6),
        .gradient(id: "bg.mint", top: 0xFF10B981, bottom: 0xFFA7F3D0),
        .gradient(id: "bg.midnight", top: 0xFF0F172A, bottom: 0xFF4C1D95),
        .gradient(id: "bg.gold", top: 0xFFB45309, bottom: 0xFFFDE68A),
        .gradient(id: "bg.aurora", top: 0xFF022C22, bottom: 0xFF6D28D9),
        .gradient(id: "bg.stage", top: 0xFF111111, bottom: 0xFF7C3AED),
        .gradient(id: "bg.sky", top: 0xFF38BDF8, bottom: 0xFFE0F2FE),
        .gradient(id: "bg.rose", top: 0xFF9F1239, bottom: 0xFFFDA4AF),
        .gradient(id: "bg.neon", top: 0xFF0F172A, bottom: 0xFFDB2777),
        .gradient(id: "bg.forest", top: 0xFF052E16, bottom: 0xFF166534),
        .gradient(id: "bg.lava", top: 0xFF7C2D12, bottom: 0xFFF97316),
        .gradient(id: "bg.snow", top: 0xFFE2E8F0, bottom: 0xFFF8FAFC),
    ]

    static let masks: [LensMask] = [
        LensMask(id: "m.shades", name: "Shades", glyph: "🕶️"),
        LensMask(id: "m.hearts", name: "Heart Eyes", glyph: "😍"),
        LensMask(id: "m.neon", name: "Neon", glyph: "🟦"),
        LensMask(id: "m.cat", name: "Kitty", glyph: "🐱"),
        LensMask(id: "m.puppy", name: "Puppy", glyph: "🐶"),
        LensMask(id: "m.bunny", name: "Bunny", glyph: "🐰"),
        LensMask(id: "m.crown", name: "Crown", glyph: "👑"),
        LensMask(id: "m.flowers", name: "Flower Crown", glyph: "🌸"),
        LensMask(id: "m.devil", name: "Devil", glyph: "😈"),
        LensMask(id: "m.stache", name: "Moustache", glyph: "🥸"),
        LensMask(id: "m.blush", name: "Blush", glyph: "☺️"),
        LensMask(id: "m.freckles", name: "Freckles", glyph: "🙂"),
        LensMask(id: "m.stars", name: "Star Eyes", glyph: "⭐"),
        LensMask(id: "m.angel", name: "Angel", glyph: "😇"),
        LensMask(id: "m.sparkle", name: "Sparkle", glyph: "✨"),
        LensMask(id: "m.wings", name: "Wings", glyph: "🪽"),
        LensMask(id: "m.bow", name: "Bow", glyph: "🎀"),
        LensMask(id: "m.tears", name: "Tears", glyph: "😢"),
    ]

    static func look(_ id: String) -> LensLook? { looks.first { $0.id == id } }
    static func mask(_ id: String) -> LensMask? { masks.first { $0.id == id } }
    static func beautyPreset(of b: LensBeauty) -> BeautyPreset? { beauty.first { $0.beauty == b } }
}
