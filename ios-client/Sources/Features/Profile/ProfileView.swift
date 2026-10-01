import SwiftUI

struct ProfileView: View {
    let device: DeviceClass
    var onImmersive: (Bool) -> Void = { _ in }

    @Environment(\.brand) private var brand
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.feedBottomReserve) private var reserve
    @EnvironmentObject private var session: SessionManager
    @StateObject private var store = ProfileStore.shared
    @ObservedObject private var mine = MyPosts.shared

    @State private var filter: ProfileFilter = .posts
    @State private var sort: ProfileSort = .latest
    @State private var statusSheet = false
    /// Camera or gallery for the profile picture.
    @State private var photoSheet = false
    @State private var pictureOpen = false
    @State private var toast = ""
    @State private var drawer = false
    @State private var accountSheet = false
    @State private var boundUserId = ""
    @State private var route: ProfileRoute = .home
    @State private var statsKind: ProfileStatsKind = .following
    @State private var helpTicketId: Int64 = 0
    @ObservedObject private var supportRouter = SupportRouter.shared
    /// Role request to land on inside the Roles surface; 0 = roles home.
    @State private var roleRequestId: Int64 = 0
    @ObservedObject private var rolesRouter = RolesRouter.shared
    /// A grid cell opened full-screen.
    @State private var viewing: PostData?
    /// Long-pressed cell: the author's own edit / delete actions.
    @State private var acting: PostData?
    /// The post whose caption and audience are being edited.
    @State private var editing: PostData?

    private var handle: String { "@\(store.snapshot.username)" }

    private var immersive: Bool {
        drawer || route != .home || statusSheet || photoSheet || pictureOpen || accountSheet
            || viewing != nil || acting != nil || editing != nil
    }

    private var signedInID: String {
        if case let .signedIn(user, _) = session.state { return user.id }
        return ""
    }

    private var posts: [ProfilePost] {
        let raw: [ProfilePost]
        switch filter {
        case .posts: raw = mine.items.filter { !$0.isPrivate }
        case .locked: raw = mine.items.filter { $0.isPrivate }
        default: raw = []
        }
        return sort == .popular ? raw.sorted { $0.rank > $1.rank } : raw
    }

    var body: some View {
        let columns = [GridItem(.flexible(), spacing: 2), GridItem(.flexible(), spacing: 2), GridItem(.flexible(), spacing: 2)]
        ZStack {
            ScrollView {
                VStack(spacing: 0) {
                    header
                    if posts.isEmpty, mine.loaded, filter == .posts {
                        AppText(Copy.t(.profileNoPosts), style: Type.caption, color: ProfileTokens.muted, alignment: .center)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 40 * scale)
                    }
                    LazyVGrid(columns: columns, spacing: 2 * scale) {
                        ForEach(posts) { post in
                            postCell(post)
                                .aspectRatio(9 / 14, contentMode: .fit)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    // Only a finished post plays; a processing tile has nothing to show yet.
                                    if post.state == "live", let raw = mine.raw[post.id] { viewing = raw }
                                }
                                // Hold is where the author's own edit and delete
                                // live, as on every grid this app's users know.
                                .onLongPressGesture { if let raw = mine.raw[post.id] { acting = raw } }
                                .onAppear { if post.id == posts.last?.id { mine.loadMore() } }
                        }
                    }
                }
                .padding(.bottom, reserve)
            }
            .background(ProfileTokens.bg.ignoresSafeArea())
            .padding(.top, insets.top)

            if drawer {
                ProfileDrawer(
                    store: store,
                    onClose: { drawer = false },
                    onSettings: { drawer = false; route = .settings },
                    onHelp: { drawer = false; helpTicketId = 0; route = .help },
                    onReport: { drawer = false; route = .report },
                    onTerms: { drawer = false; route = .terms },
                    onRoles: { drawer = false; roleRequestId = 0; route = .roles },
                    onLogout: {
                        drawer = false
                        Xani.session.signOut()
                    }
                )
                .transition(.opacity)
                .zIndex(20)
            }

            switch route {
            case .edit:
                ProfileEditView(store: store, onBack: { route = .home })
                .transition(.move(edge: .trailing))
                .zIndex(30)
            case .settings:
                ProfileSettingsView(
                    onBack: { route = .home },
                    onLogout: { Xani.session.signOut() },
                    onPassword: { route = .password },
                    onSwitchAccounts: { accountSheet = true },
                    onHelp: { helpTicketId = 0; route = .help },
                    onTerms: { route = .terms },
                    passwordLoginActive: Xani.session.lastKnownUser?.passwordLoginActive == true
                )
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .password:
                ProfilePasswordView(onBack: { route = .settings })
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .stats:
                ProfileStatsView(store: store, kind: statsKind, onBack: { route = .home })
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .visits:
                ProfileVisitsView(store: store, onBack: { route = .home })
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .help:
                HelpCentreView(
                    onBack: { route = .home; helpTicketId = 0 },
                    start: helpTicketId > 0 ? .ticket(helpTicketId) : .home
                )
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .report:
                HelpCentreView(onBack: { route = .home }, start: .report)
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .terms:
                TermsPoliciesView(onBack: { route = .home })
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .roles:
                RolesView(onBack: { route = .home; roleRequestId = 0 }, startRequestId: roleRequestId)
                    .transition(.move(edge: .trailing))
                    .zIndex(30)
            case .home:
                EmptyView()
            }

            if statusSheet {
                ProfileStatusSheet(
                    displayName: store.snapshot.displayName,
                    initial: store.snapshot.status,
                    onClose: { statusSheet = false }
                )
                .transition(.move(edge: .trailing))
                .zIndex(40)
            }

            if photoSheet {
                AvatarPickerSheet(onClose: { photoSheet = false })
                    .transition(.opacity)
                    .zIndex(42)
            }

            if pictureOpen {
                MyPicturePane(handle: handle, onClose: { pictureOpen = false })
                    .transition(.opacity)
                    .zIndex(43)
            }

            if let raw = viewing {
                PostViewerView(initial: raw, onClose: { viewing = nil })
                    .transition(.opacity)
                    .zIndex(45)
            }

            if let raw = acting {
                ProfilePostActionsSheet(
                    post: raw,
                    onEdit: { editing = raw },
                    onDeleted: {
                        // The grid drops the cell from the post bus; close
                        // anything still pointing at it.
                        acting = nil
                        if viewing?.id == raw.id { viewing = nil }
                    },
                    onClose: { acting = nil }
                )
                .zIndex(46)
            }

            if let raw = editing {
                ProfilePostEditSheet(post: raw, onClose: { editing = nil })
                    .transition(.move(edge: .trailing))
                    .zIndex(47)
            }

            if accountSheet {
                AccountSwitcherSheet(
                    onClose: { accountSheet = false },
                    onCreateAccount: {
                        accountSheet = false
                        Xani.session.beginAddAccount(startSignup: true)
                    },
                    onAddAccount: {
                        accountSheet = false
                        Xani.session.beginAddAccount(startSignup: false)
                    }
                )
                .transition(.opacity)
                .zIndex(48)
            }

            if !toast.isEmpty {
                AppText(toast, style: Type.labelBold, color: .white, sizeOverride: 13)
                    .padding(.horizontal, 16 * scale)
                    .padding(.vertical, 8 * scale)
                    .background(Color.black.opacity(0.9))
                    .clipShape(Capsule())
                    .padding(.bottom, reserve + 24 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .zIndex(50)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.28), value: route)
        .animation(.easeInOut(duration: 0.28), value: statusSheet)
        .animation(.easeOut(duration: 0.18), value: toast)
        .animation(.easeOut(duration: 0.2), value: viewing?.id)
        .onAppear { syncAccount() }
        .onChange(of: session.state) { _ in syncAccount() }
        // Live profile and balance updates, held only while this screen is on screen.
        // Keyed on the account, not the session: a silent token renew must not drop
        // the stream.
        .task(id: signedInID) {
            guard !signedInID.isEmpty else { return }
            await store.observeLive()
        }
        .onChange(of: drawer) { _ in onImmersive(immersive) }
        .onChange(of: route) { _ in onImmersive(immersive) }
        .onChange(of: statusSheet) { _ in onImmersive(immersive) }
        .onChange(of: photoSheet) { _ in onImmersive(immersive) }
        .onChange(of: viewing?.id) { _ in onImmersive(immersive) }
        .onChange(of: acting?.id) { _ in onImmersive(immersive) }
        .onChange(of: editing?.id) { _ in onImmersive(immersive) }
        .onChange(of: accountSheet) { _ in onImmersive(immersive) }
        .onChange(of: supportRouter.pendingTicketId) { id in
            guard id > 0 else { return }
            let ticket = supportRouter.consume()
            guard ticket > 0 else { return }
            drawer = false
            accountSheet = false
            helpTicketId = ticket
            route = .help
        }
        .onChange(of: rolesRouter.pending) { _ in openPendingRole() }
        .onAppear { openPendingRole() }
        // A post can go away while the app is showing it — deleted here, or on
        // the user's other device. Close whatever is pointing at it rather than
        // leave a viewer playing a post that no longer exists.
        .onChange(of: mine.raw.count) { _ in
            if let v = viewing, mine.raw[v.id] == nil { viewing = nil }
            if let a = acting, mine.raw[a.id] == nil { acting = nil }
            if let e = editing, mine.raw[e.id] == nil { editing = nil }
        }
        .onChange(of: toast) { text in
            guard !text.isEmpty else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { toast = "" }
        }
        // Saves are optimistic, so the only thing left to say is when one was refused.
        .onChange(of: store.notice) { notice in
            guard let notice else { return }
            switch notice {
            case .usernameLocked(let days):
                toast = Copy.t(.profileUsernameAgainIn, ["n": "\(days)"])
            case .usernameTaken(let username):
                toast = Copy.t(.profileUsernameTaken, ["u": username])
            case .saveFailed:
                toast = Copy.t(.profileSaveFailed)
            }
            store.notice = nil
        }
        .onChange(of: session.switchError) { err in
            guard !err.isEmpty else { return }
            toast = err
            session.clearSwitchError()
        }
    }

    /// Push tap or inbox notice asked for the Roles surface. Consumes the signal
    /// so a later re-mount of this tab does not reopen it.
    private func openPendingRole() {
        guard rolesRouter.pending != 0 else { return }
        let request = rolesRouter.consume()
        drawer = false
        accountSheet = false
        roleRequestId = request
        route = .roles
    }

    private func syncAccount() {
        switch session.state {
        case .signedIn(let user, _):
            // Account switch: clear the previous account's surfaces before binding
            // the new one so coins, grid and avatar never flash the wrong person.
            if !boundUserId.isEmpty && boundUserId != user.id {
                AvatarStore.shared.signedOut()
                InboxController.shared.signedOut()
                PersonRouter.shared.clear()
                store.signedOut()
            }
            boundUserId = user.id
            store.signedIn(user: user)
            mine.bind(Int64(user.id) ?? 0)
            // Rebind inbox after a switch (attach is otherwise scene-phase only).
            InboxController.shared.attach()
        case .signedOut:
            boundUserId = ""
            store.signedOut()
            mine.signedOut()
            PersonRouter.shared.clear()
            AvatarStore.shared.signedOut()
            InboxController.shared.signedOut()
        default: break
        }
    }

    private var header: some View {
        VStack(spacing: 0) {
            HStack {
                // How many people have opened this profile, and the way in to
                // who they were. It sits where the old profile search did:
                // searching your own grid is a niche thing to want, and knowing
                // who is looking at you is not.
                Button(action: { route = .visits }) {
                    HStack(spacing: 5 * scale) {
                        VectorCanvas(
                            [
                                .data("M1 9s3-5.5 8-5.5S17 9 17 9s-3 5.5-8 5.5S1 9 1 9z", .stroke(ProfileTokens.text, 1.5)),
                                .circle(9, 9, 2.4, .fill(ProfileTokens.text)),
                            ],
                            viewport: 18,
                            side: 19 * scale
                        )
                        AppText(store.snapshot.profileViews, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 13)
                    }
                    .frame(minHeight: 44, alignment: .leading)
                }
                .buttonStyle(PressScaleStyle())
                Spacer()
                Button(action: { accountSheet = true }) {
                    AppText(handle, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                        .padding(.horizontal, 6 * scale)
                        .padding(.vertical, 4 * scale)
                }
                .buttonStyle(PressScaleStyle())
                Spacer()
                Button(action: {}) {
                    VectorCanvas(
                        [
                            .circle(18, 5, 3, .stroke(ProfileTokens.text, 1.8)),
                            .circle(6, 12, 3, .stroke(ProfileTokens.text, 1.8)),
                            .circle(18, 19, 3, .stroke(ProfileTokens.text, 1.8)),
                            .data("M8.59 13.51l6.83 3.98M15.41 6.51l-6.82 3.98", .stroke(ProfileTokens.text, 1.8)),
                        ],
                        viewport: 24,
                        side: 18 * scale
                    )
                    .frame(width: 34 * scale, height: 34 * scale)
                    .background(ProfileTokens.bg2)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(ProfileTokens.border, lineWidth: 1 * scale))
                }
                .buttonStyle(PressScaleStyle())
                Button(action: { drawer = true }) {
                    VectorCanvas(
                        [
                            .box(2, 5, 18, 2, 1, .fill(ProfileTokens.text)),
                            .box(2, 10, 18, 2, 1, .fill(ProfileTokens.text)),
                            .box(2, 15, 18, 2, 1, .fill(ProfileTokens.text)),
                        ],
                        viewport: 22,
                        side: 22 * scale
                    )
                    .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(PressScaleStyle())
            }
            .padding(.horizontal, 20 * scale)
            .padding(.top, 14 * scale)

            VStack(spacing: 12 * scale) {
                ZStack(alignment: .top) {
                    MyAvatar(size: 90, onPick: { photoSheet = true }, onHold: { pictureOpen = true })
                        .padding(.top, 18 * scale)

                    Button(action: { statusSheet = true }) {
                        VStack(spacing: 0) {
                            AppText(
                                store.snapshot.status.isEmpty
                                    ? Copy.t(.profileWhatsOnMind)
                                    : store.snapshot.status,
                                style: Type.caption,
                                color: .white.opacity(store.snapshot.status.isEmpty ? 0.72 : 1),
                                alignment: .center,
                                lineLimit: 2,
                                sizeOverride: 12
                            )
                            .padding(.horizontal, 12 * scale)
                            .padding(.vertical, 7 * scale)
                            .frame(maxWidth: 200 * scale)
                            .background(ProfileTokens.text)
                            .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))

                            Triangle()
                                .fill(ProfileTokens.text)
                                .frame(width: 10 * scale, height: 7 * scale)
                                .offset(y: -1 * scale)
                        }
                    }
                    .buttonStyle(PressScaleStyle())
                    .offset(y: -32 * scale)
                }

                HStack(spacing: 6 * scale) {
                    AppText(store.snapshot.displayName, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 20)
                    if store.snapshot.verified {
                        VectorCanvas(
                            [
                                .circle(5.5, 5.5, 5.5, .fill(ProfileTokens.accent)),
                                .data("M3 5.5l1.8 1.8 3.2-3.6", .stroke(.white, 1.5)),
                            ],
                            viewport: 11,
                            side: 16 * scale
                        )
                    }
                }

                Button(action: { accountSheet = true }) {
                    AppText(
                        handle,
                        style: Type.caption,
                        color: ProfileTokens.sub
                    )
                    .padding(.horizontal, 8 * scale)
                    .padding(.vertical, 2 * scale)
                }
                .buttonStyle(PressScaleStyle())

                HStack(spacing: 0) {
                    stat(store.snapshot.following, Copy.t(.profileFollowing)) { statsKind = .following; route = .stats }
                    stat(store.snapshot.followers, Copy.t(.profileFollowers)) { statsKind = .followers; route = .stats }
                    stat(store.snapshot.receiving, Copy.t(.profileReceiving)) { statsKind = .receiving; route = .stats }
                }
                .overlay(Rectangle().fill(ProfileTokens.border).frame(height: 1), alignment: .top)
                .overlay(Rectangle().fill(ProfileTokens.border).frame(height: 1), alignment: .bottom)

                HStack(spacing: 10 * scale) {
                    wallet("🪙", store.snapshot.coins, Copy.t(.profileCoins))
                    wallet("💎", store.snapshot.diamonds, Copy.t(.profileDiamonds))
                }

                AppText(store.snapshot.bio, style: Type.caption, color: ProfileTokens.sub)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 10 * scale)
                    .background(ProfileTokens.bg2)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                            .stroke(ProfileTokens.border, lineWidth: 1 * scale)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))

                HStack(spacing: 8 * scale) {
                    pill(Copy.t(.profileEdit)) { route = .edit }
                    pill(Copy.t(.profileShare)) {}
                    Button(action: {}) {
                        VectorCanvas(
                            [
                                .box(1, 1, 5, 5, 1, .stroke(ProfileTokens.text, 1.3)),
                                .box(1, 10, 5, 5, 1, .stroke(ProfileTokens.text, 1.3)),
                                .box(10, 1, 5, 5, 1, .stroke(ProfileTokens.text, 1.3)),
                                .box(10, 10, 5, 5, 1, .stroke(ProfileTokens.text, 1.3)),
                                .box(2.5, 2.5, 2, 2, 0, .fill(ProfileTokens.text)),
                            ],
                            viewport: 16,
                            side: 16 * scale
                        )
                        .frame(width: 38 * scale, height: 38 * scale)
                        .background(ProfileTokens.bg2)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                                .stroke(ProfileTokens.borderStrong, lineWidth: 1 * scale)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
                    }
                    .buttonStyle(PressScaleStyle())
                }
            }
            .padding(.horizontal, 24 * scale)
            .padding(.top, 18 * scale)

            HStack(spacing: 0) {
                filterTab(.posts) {
                    VectorCanvas(
                        [
                            .box(2, 2, 6, 6, 1, .stroke(filter == .posts ? ProfileTokens.accent : ProfileTokens.muted, 1.4)),
                            .box(10, 2, 6, 6, 1, .stroke(filter == .posts ? ProfileTokens.accent : ProfileTokens.muted, 1.4)),
                            .box(2, 10, 6, 6, 1, .stroke(filter == .posts ? ProfileTokens.accent : ProfileTokens.muted, 1.4)),
                            .box(10, 10, 6, 6, 1, .stroke(filter == .posts ? ProfileTokens.accent : ProfileTokens.muted, 1.4)),
                        ],
                        viewport: 18,
                        side: 18 * scale
                    )
                }
                filterTab(.locked) {
                    VectorCanvas(
                        [
                            .box(7, 11, 10, 9, 1.8, .stroke(filter == .locked ? ProfileTokens.accent : ProfileTokens.muted, 1.6)),
                            .data("M9.2 11V8.4C9.2 6.9 10.4 5.6 12 5.6C13.6 5.6 14.8 6.9 14.8 8.4V11", .stroke(filter == .locked ? ProfileTokens.accent : ProfileTokens.muted, 1.6)),
                        ],
                        viewport: 24,
                        side: 22 * scale
                    )
                }
                filterTab(.saved) {
                    VectorCanvas(
                        [.data("M6 3h12v18l-6-4-6 4V3z", .stroke(filter == .saved ? ProfileTokens.accent : ProfileTokens.muted, 1.6))],
                        viewport: 24,
                        side: 22 * scale
                    )
                }
                filterTab(.liked) {
                    VectorCanvas(
                        [.data("M12 20.2C6.4 15.6 3.8 12.9 3.8 9.4C3.8 6.9 5.7 5.1 8.1 5.1C9.6 5.1 11 5.9 12 7.3C13 5.9 14.4 5.1 15.9 5.1C18.3 5.1 20.2 6.9 20.2 9.4C20.2 12.9 17.6 15.6 12 20.2Z", .stroke(filter == .liked ? ProfileTokens.accent : ProfileTokens.muted, 1.6))],
                        viewport: 24,
                        side: 22 * scale
                    )
                }
            }
            .padding(.top, 14 * scale)

            if filter == .posts {
                HStack(spacing: 8 * scale) {
                    sortChip(Copy.t(.profileLatest), sort == .latest) { sort = .latest }
                    sortChip(Copy.t(.profilePopular), sort == .popular) { sort = .popular }
                    Spacer()
                }
                .padding(.horizontal, 16 * scale)
                .padding(.vertical, 8 * scale)
            }
        }
    }

    /// One grid tile: thumbnail over the author gradient (the gradient is what a
    /// text post or a still-processing upload shows); like count bottom-left.
    private func postCell(_ post: ProfilePost) -> some View {
        ZStack(alignment: .bottomLeading) {
            Rectangle().fill(Color(rgb: post.color))
            if let u = post.thumbURL {
                RemoteImage(url: u, contentMode: .fill, fallback: .clear)
            } else if post.kind == "text", !post.caption.isEmpty {
                AppText(post.caption, style: Type.labelBold, color: .white, alignment: .center, lineLimit: 6, sizeOverride: 11)
                    .padding(8 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            if post.state == "processing" || post.state == "failed" {
                AppText(Copy.t(post.state == "processing" ? .homePosting : .homeFailed), style: Type.labelBold, color: .white, sizeOverride: 10)
                    .padding(.horizontal, 8 * scale)
                    .padding(.vertical, 4 * scale)
                    .background(Color.black.opacity(0.55))
                    .clipShape(RoundedRectangle(cornerRadius: 8 * scale, style: .continuous))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            HStack(spacing: 3 * scale) {
                VectorCanvas(
                    [.data("M12 20.2C6.4 15.6 3.8 12.9 3.8 9.4C3.8 6.9 5.7 5.1 8.1 5.1C9.6 5.1 11 5.9 12 7.3C13 5.9 14.4 5.1 15.9 5.1C18.3 5.1 20.2 6.9 20.2 9.4C20.2 12.9 17.6 15.6 12 20.2Z", .fill(.white))],
                    viewport: 24,
                    side: 10 * scale
                )
                AppText(post.likes, style: Type.labelBold, color: .white, sizeOverride: 10)
            }
            .padding(4 * scale)
            if post.kind == "video", post.durationMs > 0 {
                AppText(String(format: "%d:%02d", post.durationMs / 60000, (post.durationMs / 1000) % 60), style: Type.labelBold, color: .white, sizeOverride: 9)
                    .padding(4 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
            if post.isPrivate {
                VectorCanvas(
                    [
                        .box(7, 11, 10, 9, 1.8, .stroke(.white, 1.6)),
                        .data("M9.2 11V8.4C9.2 6.9 10.4 5.6 12 5.6C13.6 5.6 14.8 6.9 14.8 8.4V11", .stroke(.white, 1.6)),
                    ],
                    viewport: 24,
                    side: 12 * scale
                )
                .padding(4 * scale)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
            if post.isRestricted {
                AppText(Copy.t(.profileRestricted), style: Type.labelBold, color: .white, sizeOverride: 8)
                    .padding(.horizontal, 5 * scale)
                    .padding(.vertical, 2 * scale)
                    .background(Color.black.opacity(0.62))
                    .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                    .padding(4 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .clipped()
    }

    private func stat(_ value: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2 * scale) {
                AppText(value, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 18)
                AppText("\(label) ›", style: Type.caption, color: ProfileTokens.accentDeep, sizeOverride: 11)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10 * scale)
        }
        .buttonStyle(.plain)
    }

    private func wallet(_ icon: String, _ value: String, _ label: String) -> some View {
        VStack(spacing: 2 * scale) {
            Text(icon).font(.system(size: 18 * scale))
            AppText(value, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14)
            AppText(label, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 10)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10 * scale)
        .background(ProfileTokens.bg2)
        .overlay(
            RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                .stroke(ProfileTokens.border, lineWidth: 1 * scale)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
    }

    private func pill(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(title, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 13)
                .frame(maxWidth: .infinity)
                .frame(height: 38 * scale)
                .background(ProfileTokens.bg2)
                .overlay(
                    RoundedRectangle(cornerRadius: 10 * scale, style: .continuous)
                        .stroke(ProfileTokens.borderStrong, lineWidth: 1 * scale)
                )
                .clipShape(RoundedRectangle(cornerRadius: 10 * scale, style: .continuous))
        }
        .buttonStyle(PressScaleStyle())
    }

    private func filterTab<G: View>(_ value: ProfileFilter, @ViewBuilder glyph: () -> G) -> some View {
        Button(action: { filter = value }) {
            VStack(spacing: 6 * scale) {
                glyph()
                Rectangle()
                    .fill(filter == value ? ProfileTokens.accent : Color.clear)
                    .frame(height: 2 * scale)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 40 * scale)
        }
        .buttonStyle(PressScaleStyle())
    }

    private func sortChip(_ title: String, _ on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            AppText(title, style: Type.labelBold, color: on ? ProfileTokens.accentDeep : ProfileTokens.sub, sizeOverride: 12)
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 4 * scale)
                .background(on ? ProfileTokens.accentSoft : Color.clear)
                .overlay(Capsule().stroke(on ? ProfileTokens.accent : ProfileTokens.borderStrong, lineWidth: 1 * scale))
                .clipShape(Capsule())
        }
        .buttonStyle(PressScaleStyle())
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

/// Own picture, large — same centred circle as holding anyone else's.
private struct MyPicturePane: View {
    let handle: String
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @State private var grow: CGFloat = 0.82

    var body: some View {
        ZStack {
            Color.black.opacity(0.86).ignoresSafeArea()
            VStack(spacing: 18 * scale) {
                MyAvatar(size: 240)
                    .scaleEffect(grow)
                AppText(handle, style: Type.labelBold, color: .white, sizeOverride: 15)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onClose() }
        .onAppear { withAnimation(.easeOut(duration: 0.2)) { grow = 1 } }
    }
}
