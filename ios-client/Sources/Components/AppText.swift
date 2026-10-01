import SwiftUI

/// Single text entry point so responsive scaling is applied once, centrally, and no
/// screen can accidentally hardcode an unscaled size.
struct AppText: View {
    private let text: String
    private let style: TypeStyle
    private var color: Color = Light.text
    private var gradient: LinearGradient?
    private var alignment: TextAlignment = .leading
    private var lineLimit: Int?
    private var sizeOverride: CGFloat?

    @Environment(\.uiScale) private var scale

    init(
        _ text: String,
        style: TypeStyle,
        color: Color = Light.text,
        gradient: LinearGradient? = nil,
        alignment: TextAlignment = .leading,
        lineLimit: Int? = nil,
        sizeOverride: CGFloat? = nil
    ) {
        self.text = text
        self.style = style
        self.color = color
        self.gradient = gradient
        self.alignment = alignment
        self.lineLimit = lineLimit
        self.sizeOverride = sizeOverride
    }

    var body: some View {
        let styled = Text(text)
            .font(style.family.font(size: (sizeOverride ?? style.size) * scale, weight: style.weight))
            .kerning(style.kerning * scale)

        Group {
            if let gradient {
                styled.foregroundStyle(gradient)
            } else {
                styled.foregroundStyle(color)
            }
        }
        .multilineTextAlignment(alignment)
        .lineSpacing(style.lineSpacing * scale)
        .lineLimit(lineLimit)
        // Text inside a ScrollView otherwise gets one line of height and truncates.
        .fixedSize(horizontal: false, vertical: true)
    }
}
