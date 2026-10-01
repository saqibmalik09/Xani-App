import SwiftUI

struct FeatureGate<Content: View>: View {
    let light: Bool
    @ViewBuilder var content: () -> Content
    @State private var failed = false

    var body: some View {
        if failed {
            FeatureFallback(light: light)
        } else {
            content()
        }
    }

    func fail() {
        failed = true
    }
}

struct FeatureFallback: View {
    let light: Bool
    @Environment(\.uiScale) private var scale

    var body: some View {
        ZStack {
            (light ? Light.background : Dark.background)
            AppText(
                Copy.t(.featureUnavailable),
                style: Type.label,
                color: light ? Light.sub : Dark.mutedForeground,
                alignment: .center
            )
            .padding(.horizontal, 32 * scale)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
