import SwiftUI

struct LensTray: View {
    @ObservedObject var controller: LensController
    var compact: Bool = false
    var initial: LensCatalog.Category = .looks
    @State private var cat: LensCatalog.Category
    @State private var pickBg = false
    @Environment(\.uiScale) private var scale

    init(controller: LensController, compact: Bool = false, initial: LensCatalog.Category = .looks) {
        self.controller = controller
        self.compact = compact
        self.initial = initial
        _cat = State(initialValue: initial)
    }

    var body: some View {
        VStack(spacing: 8 * scale) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6 * scale) {
                    catChip(Copy.t(.createBeauty), cat == .beauty) { cat = .beauty }
                    catChip(Copy.t(.createLooks), cat == .looks) { cat = .looks }
                    if controller.capsBackground {
                        catChip(Copy.t(.createBackground), cat == .background) { cat = .background }
                    }
                    if controller.capsMasks {
                        catChip(Copy.t(.createMasks), cat == .masks) { cat = .masks }
                    }
                }
                .padding(.horizontal, 12 * scale)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10 * scale) {
                    switch cat {
                    case .beauty:
                        ForEach(LensCatalog.beauty) { b in
                            chip(b.name, glyph: b.glyph, color: Color(rgb: 0xEC4899), on: LensCatalog.beautyPreset(of: controller.state.beauty)?.id == b.id) {
                                controller.setBeauty(b.beauty)
                            }
                        }
                    case .looks:
                        ForEach(LensCatalog.looks) { look in
                            chip(look.name, glyph: nil, color: Color(rgb: look.swatch), on: controller.state.lookId == look.id) {
                                controller.setLook(look.id)
                            }
                        }
                    case .background:
                        ForEach(LensCatalog.backgrounds, id: \.id) { bg in
                            chip(bgLabel(bg), glyph: bgGlyph(bg), color: bgColor(bg), on: controller.state.background.id == bg.id) {
                                controller.setBackground(bg)
                            }
                        }
                        chip(Copy.t(.createBannerPhoto), glyph: "🖼", color: Color(rgb: 0x6366F1), on: controller.state.background.id == "bg.image") {
                            pickBg = true
                        }
                    case .masks:
                        ForEach(LensCatalog.masks) { mask in
                            chip(mask.name, glyph: mask.glyph, color: Color(rgb: 0xA855F7), on: controller.state.masks.contains(mask.id)) {
                                controller.toggleMask(mask.id)
                            }
                        }
                    }
                }
                .padding(.horizontal, 12 * scale)
            }
        }
        .sheet(isPresented: $pickBg) {
            MediaPicker(source: .photoLibrary) { url in
                controller.setBackground(.image(url))
                pickBg = false
            }
        }
    }

    private var ring: CGFloat { compact ? 56 * scale : 68 * scale }

    private func catChip(_ label: String, _ on: Bool, tap: @escaping () -> Void) -> some View {
        AppText(label, style: Type.labelBold, color: on ? Color(rgb: 0x111111) : .white, sizeOverride: 12)
            .padding(.horizontal, 12 * scale)
            .padding(.vertical, 6 * scale)
            .background(on ? Color.white : Color.white.opacity(0.16))
            .clipShape(Capsule())
            .onTapGesture(perform: tap)
    }

    private func chip(_ label: String, glyph: String?, color: Color, on: Bool, tap: @escaping () -> Void) -> some View {
        VStack(spacing: 4 * scale) {
            ZStack {
                Circle().fill(color)
                if let glyph { Text(glyph).font(.system(size: ring * 0.32)) }
            }
            .frame(width: ring, height: ring)
            .overlay(Circle().stroke(Color.white, lineWidth: on ? 2.5 : 1).opacity(on ? 1 : 0.25))
            AppText(label, style: Type.micro, color: on ? .white : Color.white.opacity(0.7), sizeOverride: 10)
                .lineLimit(1)
        }
        .onTapGesture(perform: tap)
    }

    private func bgLabel(_ bg: LensBackground) -> String {
        switch bg {
        case .none: return Copy.t(.createLensOff)
        case .blur: return Copy.t(.createBgBlur)
        case .gradient(let id, _, _):
            let raw = id.replacingOccurrences(of: "bg.", with: "")
            return raw.prefix(1).uppercased() + raw.dropFirst()
        case .image: return Copy.t(.createBannerPhoto)
        }
    }
    private func bgGlyph(_ bg: LensBackground) -> String {
        switch bg {
        case .none: return "🚫"
        case .blur: return "🌫"
        case .gradient: return "🎨"
        case .image: return "🖼"
        }
    }
    private func bgColor(_ bg: LensBackground) -> Color {
        switch bg {
        case .none: return Color(rgb: 0x374151)
        case .blur: return Color(rgb: 0x64748B)
        case .gradient(_, let top, _): return Color(rgb: top)
        case .image: return Color(rgb: 0x6366F1)
        }
    }
}
