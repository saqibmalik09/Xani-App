import SwiftUI

enum MainTab: String, CaseIterable {
    case home, games, create, inbox, profile

    var cinematic: Bool { self == .home || self == .games || self == .create }
    var lightSurface: Bool { self == .profile || self == .inbox }
}

struct MainShell: View {
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.uiScale) private var scale
    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var scenePhase

    @SceneStorage("main.tab") private var stored = MainTab.home.rawValue
    @State private var visited: Set<MainTab> = [.home]
    @State private var inboxImmersive = false
    @State private var profileImmersive = false
    // Games/Home go full-bleed too: an open game (Games tab) and a live broadcast
    // (Home tab) each own the whole screen, so the bottom nav steps aside.
    @State private var gamesImmersive = false
    @State private var homeImmersive = false
    @State private var inboxUnread = 0
    @ObservedObject private var people = PersonRouter.shared
    @ObservedObject private var inbox = InboxController.shared
    @EnvironmentObject private var session: SessionManager
    private let device = DeviceClass.current()

    private var tab: MainTab { MainTab(rawValue: stored) ?? .home }

    private var myId: Int64 {
        if case let .signedIn(user, _) = session.state { return Int64(user.id) ?? 0 }
        return 0
    }

    var body: some View {
        let reserve = 70 * scale + insets.bottom
        let pinOff = device.pinHomeOffTab
        let person = people.top
        let hideBar = (tab == .inbox && inboxImmersive) || tab == .create
            || (tab == .profile && profileImmersive)
            || (tab == .games && gamesImmersive)
            || (tab == .home && homeImmersive)
            || person != nil
        let chromeLight = person != nil ? scheme != .dark : (tab.cinematic ? false : scheme != .dark)

        ZStack {
            (chromeLight ? Light.background : Dark.background)
                .ignoresSafeArea()

            if pinOff || tab == .home {
                alive(.home) {
                    FeatureGate(light: false) {
                        HomeView(active: tab == .home, device: device, onImmersive: { homeImmersive = $0 })
                    }
                }
            }
            if keep(.games) {
                alive(.games) {
                    FeatureGate(light: chromeLight) { GamesView(onImmersive: { gamesImmersive = $0 }) }
                }
            }
            if keep(.create) {
                alive(.create) {
                    FeatureGate(light: chromeLight) {
                        CreateView(active: tab == .create, onClose: { go(.home) })
                    }
                }
            }
            if keep(.inbox) {
                alive(.inbox) {
                    FeatureGate(light: chromeLight) {
                        InboxView(
                            onImmersive: { inboxImmersive = $0 },
                            onUnread: { inboxUnread = $0 }
                        )
                    }
                }
            }
            if keep(.profile) {
                alive(.profile) {
                    FeatureGate(light: chromeLight) {
                        ProfileView(device: device, onImmersive: { profileImmersive = $0 })
                    }
                }
            }

            // Somebody else's profile, over whichever tab is showing. It is a
            // stack because a profile leads to more profiles: back unwinds one
            // at a time, and the tab underneath is never disturbed.
            if let id = person {
                PersonProfileView(userId: id, onClose: { people.back() })
                    .id(id)
                    .transition(.opacity)
                    .zIndex(10)
            }

            if !hideBar {
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    MainBottomBar(tab: tab, inboxUnread: inboxUnread) { go($0) }
                }
                .zIndex(2)
            }

            // Posting / Posted strip follows the user across tabs; hidden while
            // the camera owns the top edge.
            if tab != .create {
                PostingStrip().zIndex(3)
            }

            // Calls cover everything, including the person sheet and the bar,
            // and survive tab switches because the controller owns them.
            CallHost().zIndex(20)
        }
        .environment(\.feedBottomReserve, reserve)
        .statusBar(lightBackground: statusBarLight(overProfile: person != nil))
        // "Message" on a profile: the thread lives on the Inbox tab, so the
        // shell goes there and the profile stack closes behind it.
        // One realtime socket for the whole signed-in session, owned by the
        // shell rather than by whichever screen happens to want freshness.
        //
        // This is what makes chat behave: a message, a tick or "online" has to
        // arrive while the user is on Home, and the other side has to see them
        // as online for as long as the app is in front of them — not only while
        // the inbox is the visible tab. The task dies with the scene going to
        // the background, so a phone in a pocket holds no stream.
        .task(id: scenePhase == .active) {
            guard scenePhase == .active else { return }
            inbox.attach()
            CallController.shared.attach()
            LiveSession.shared.attach()
            await LiveFeed.shared.hold()
        }
        .onChange(of: inbox.openRequest) { request in
            guard request != nil else { return }
            people.clear()
            go(.inbox)
        }
        .onChange(of: SupportRouter.shared.pendingTicketId) { id in
            guard id > 0 else { return }
            people.clear()
            go(.profile)
        }
        .onChange(of: RolesRouter.shared.pending) { signal in
            guard signal != 0 else { return }
            people.clear()
            go(.profile)
        }
        // Tapping your own handle in the feed is a request for your profile,
        // not a read-only copy of it.
        .onChange(of: people.stack) { stack in
            guard let id = stack.last, id == myId else { return }
            people.clear()
            go(.profile)
        }
    }

    /// A public profile is a light surface whichever tab it covers, so the
    /// status bar has to follow it rather than the tab underneath.
    private func statusBarLight(overProfile: Bool) -> Bool? {
        if overProfile { return true }
        return tab.cinematic ? false : nil
    }

    private func keep(_ t: MainTab) -> Bool {
        tab == t || (device.pinHomeOffTab && visited.contains(t))
    }

    private func go(_ next: MainTab) {
        people.clear()
        guard tab != next else { return }
        inboxImmersive = false
        profileImmersive = false
        visited.insert(next)
        stored = next.rawValue
    }

    @ViewBuilder
    private func alive<V: View>(_ t: MainTab, @ViewBuilder content: () -> V) -> some View {
        let on = tab == t
        content()
            .opacity(on ? 1 : 0)
            .offset(x: on ? 0 : -10000)
            .allowsHitTesting(on)
            .zIndex(on ? 1 : 0)
    }
}

struct MainBottomBar: View {
    let tab: MainTab
    var inboxUnread: Int = 0
    let onTab: (MainTab) -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @Environment(\.colorScheme) private var scheme

    private var light: Bool { tab.cinematic ? false : scheme != .dark }

    var body: some View {
        VStack(spacing: 0) {
            (light ? Light.divider : Color.white.opacity(0.07)).frame(height: 1)
            HStack {
                nav(.home, Copy.t(.tabHome)) { HomeGlyph(on: tab == .home, light: light, side: 24 * scale) }
                nav(.games, Copy.t(.tabGames)) { GamesGlyph(on: tab == .games, light: light, side: 24 * scale) }
                createItem
                nav(.inbox, Copy.t(.tabInbox), badge: inboxUnread) { InboxGlyph(on: tab == .inbox, light: light, side: 24 * scale) }
                nav(.profile, Copy.t(.tabProfile)) { ProfileGlyph(on: tab == .profile, light: light, side: 24 * scale) }
            }
            .frame(height: 70 * scale)
            .padding(.horizontal, 6 * scale)
            .padding(.bottom, 2 * scale)
            Color.clear.frame(height: insets.bottom)
        }
        .background(light ? Light.background : Dark.background.opacity(0.96))
    }

    private func tabTint(_ on: Bool) -> Color {
        let ink = light ? Light.text : Color.white
        return on ? ink : ink.opacity(0.4)
    }

    private func nav<G: View>(_ item: MainTab, _ label: String, badge: Int = 0, @ViewBuilder glyph: () -> G) -> some View {
        let on = tab == item
        let tint = tabTint(on)
        return Button(action: { onTab(item) }) {
            VStack(spacing: 3 * scale) {
                ZStack(alignment: .topTrailing) {
                    glyph()
                    if badge > 0 {
                        Text(badge > 9 ? "9+" : "\(badge)")
                            .font(.system(size: 8 * scale, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 14 * scale, height: 14 * scale)
                            .background(Dark.accent)
                            .clipShape(Circle())
                            .offset(x: 4 * scale, y: -4 * scale)
                    }
                }
                AppText(label, style: on ? Type.labelBold : Type.label, color: tint, sizeOverride: 11)
            }
            .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var createItem: some View {
        let on = tab == .create
        return Button(action: { onTab(.create) }) {
            VStack(spacing: 3 * scale) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                        .fill(Dark.primary)
                        .padding(.trailing, 5 * scale)
                    RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                        .fill(Dark.accent)
                        .padding(.leading, 5 * scale)
                    RoundedRectangle(cornerRadius: 8 * scale, style: .continuous)
                        .fill(Color.white)
                        .padding(.horizontal, 3 * scale)
                    AppText("+", style: Type.h1, color: Color(rgb: 0x111111), sizeOverride: 20)
                }
                .frame(width: 50 * scale, height: 34 * scale)
                AppText(
                    Copy.t(.tabCreate),
                    style: on ? Type.labelBold : Type.label,
                    color: tabTint(on),
                    sizeOverride: 11
                )
            }
            .frame(minWidth: 44, minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Copy.t(.tabCreate))
    }
}

struct HomeGlyph: View {
    let on: Bool
    var light: Bool = false
    var side: CGFloat = 24
    var body: some View {
        let ink = light ? Color(rgb: 0x111111) : Color.white
        let tint: Color = on ? ink : ink.opacity(0.4)
        VectorCanvas(
            [.data("M3 12L12 4L21 12V21H15V16H9V21H3V12Z", on ? .strokedFill(tint, tint, 2) : .stroke(tint, 1.5))],
            viewport: 24,
            side: side
        )
    }
}

struct GamesGlyph: View {
    let on: Bool
    var light: Bool = false
    var side: CGFloat = 24
    var body: some View {
        let ink = light ? Color(rgb: 0x111111) : Color.white
        let tint: Color = on ? ink : ink.opacity(0.4)
        VectorCanvas(
            [
                .box(2, 7, 20, 12, 4, .stroke(tint, 1.5)),
                .data("M9 11v4M7 13h4", .stroke(tint, 1.5)),
                .circle(15, 13, 1.2, .fill(tint)),
                .circle(17.5, 11, 1, .fill(tint)),
            ],
            viewport: 24,
            side: side
        )
    }
}

struct InboxGlyph: View {
    let on: Bool
    var light: Bool = false
    var side: CGFloat = 24
    var body: some View {
        let ink = light ? Color(rgb: 0x111111) : Color.white
        let tint: Color = on ? ink : ink.opacity(0.4)
        VectorCanvas(
            [
                .data("M4 4h16v12H4z", .stroke(tint, 1.5)),
                .data("M4 4l8 8 8-8", .stroke(tint, 1.5)),
            ],
            viewport: 24,
            side: side
        )
    }
}

struct ProfileGlyph: View {
    let on: Bool
    var light: Bool = false
    var side: CGFloat = 24
    var body: some View {
        let ink = light ? Color(rgb: 0x111111) : Color.white
        let tint: Color = on ? ink : ink.opacity(0.4)
        VectorCanvas(
            [
                .circle(12, 8, 4, .stroke(tint, 1.5)),
                .data("M4 20C4 16.7 7.6 14 12 14s8 2.7 8 6", .stroke(tint, 1.5)),
            ],
            viewport: 24,
            side: side
        )
    }
}
