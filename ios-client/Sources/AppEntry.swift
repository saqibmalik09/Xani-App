import SwiftUI

@main
struct AppEntry: App {
    init() {
        // Binds the transport to the session and kicks off restoring a saved login.
        // Both are non-blocking; the splash covers the round trip. Google Sign-In
        // is installed inside `start` when GOOGLE_IOS_CLIENT_ID is set.
        Xani.start()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// The responsive factor is resolved once, above every screen, so nested layouts
/// never disagree mid-pass. Nothing else happens at launch: no network, no disk, no
/// analytics init between process start and first frame.
struct RootView: View {
    @State private var safeInsets = currentWindowSafeInsets()

    var body: some View {
        GeometryReader { geometry in
            SessionExpiredGate {
                AuthFlow(onAuthenticated: {})
            }
            .environmentObject(Xani.session)
            .environment(\.uiScale, Layout.scale(forWidth: geometry.size.width))
            .environment(\.brand, .current)
            .environment(\.systemSafeInsets, safeInsets)
            .frame(width: geometry.size.width, height: geometry.size.height)
            .overlay(alignment: .topLeading) {
                NetworkSpeedBadge()
            }
        }
        .ignoresSafeArea()
        .overlay(
            SystemSafeProbe(insets: $safeInsets)
                .allowsHitTesting(false)
        )
        // Fixed surfaces per screen; Dynamic Type is honoured through the responsive
        // factor rather than by letting the system rescale a pixel-matched layout.
        .dynamicTypeSize(.large ... .accessibility1)
    }
}
