import SwiftUI

/// Somebody else's profile.
///
/// It is the profile screen the owner sees, minus everything only an owner may
/// do: no edit, no settings drawer, no wallet, no private tab, no camera badge
/// on the picture. What stays is what makes a profile worth opening — the
/// picture, the status card, the name and handle, the follow counts, the about
/// text and the public grid — plus the two things only a visitor needs: Follow
/// and Message.
///
/// Opening it is what records a profile visit (see `visit: true` on the read),
/// so the owner's counter and their "who viewed me" list are fed by the same
/// request that paints this screen.
struct PersonProfileView: View {
    let userId: Int64
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @Environment(\.feedBottomReserve) private var reserve
    @ObservedObject private var follows = FollowStore.shared

    @State private var person: PersonData?
    @State private var posts: [ProfilePost] = []
    @State private var raw: [Int64: PostData] = [:]
    @State private var cursor = ""
    @State private var loadedPosts = false
    @State private var loadingPosts = false
    @State private var viewing: PostData?
    /// The picture, opened big — a circle in the middle, not a full-width sheet.
    @State private var pictureOpen = false
    @State private var slide: CGFloat = 1

    private var handle: String { person.map { "@\($0.username)" } ?? "" }

    var body: some View {
        let columns = [GridItem(.flexible(), spacing: 2), GridItem(.flexible(), spacing: 2), GridItem(.flexible(), spacing: 2)]
        ZStack {
            ScrollView {
                VStack(spacing: 0) {
                    header
                    if posts.isEmpty, loadedPosts {
                        AppText(Copy.t(.profileNoPostsYet), style: Type.caption, color: ProfileTokens.muted, alignment: .center)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 40 * scale)
                    }
                    LazyVGrid(columns: columns, spacing: 2 * scale) {
                        ForEach(posts) { post in
                            PersonPostCell(post: post)
                                .aspectRatio(9 / 14, contentMode: .fit)
                                .contentShape(Rectangle())
                                .onTapGesture { if let r = raw[post.id] { viewing = r } }
                                .onAppear { if post.id == posts.last?.id { loadMore() } }
                        }
                    }
                }
                .padding(.bottom, reserve)
            }
            .background(ProfileTokens.bg.ignoresSafeArea())
            .padding(.top, insets.top)

            if let post = viewing {
                PostViewerView(initial: post, onClose: { viewing = nil })
                    .transition(.opacity)
                    .zIndex(45)
            }

            if pictureOpen {
                PicturePane(person: person, onClose: { pictureOpen = false })
                    .zIndex(50)
            }
        }
        .offset(y: UIScreen.main.bounds.height * slide)
        .onAppear { withAnimation(.easeInOut(duration: 0.26)) { slide = 0 } }
        .animation(.easeOut(duration: 0.2), value: viewing?.id)
        .animation(.easeOut(duration: 0.18), value: pictureOpen)
        // The read that both paints the header and counts the visit.
        .task(id: userId) {
            guard let hit = await Xani.people.get(userId, visit: true).value else { return }
            FollowStore.shared.learn([hit])
            person = hit
        }
        .task(id: userId) { await load(before: 0) }
    }

    private var header: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: dismiss) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 17 * scale, weight: .semibold))
                        .foregroundColor(ProfileTokens.text)
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(PressScaleStyle())
                Spacer()
                AppText(handle, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 16)
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, 14 * scale)
            .padding(.top, 10 * scale)

            VStack(spacing: 12 * scale) {
                ZStack(alignment: .top) {
                    PersonPicture(person: person, size: 90 * scale)
                        .overlay(Circle().stroke(ProfileTokens.avatarRing, lineWidth: 3 * scale))
                        .padding(.top, 18 * scale)
                        // Hold to see the picture big, as every profile the
                        // user already knows behaves.
                        .onTapGesture { pictureOpen = true }
                        .onLongPressGesture { pictureOpen = true }

                    if let status = person?.status, !status.isEmpty {
                        ReadOnlyStatus(text: status)
                            .offset(y: -32 * scale)
                    }
                }

                HStack(spacing: 6 * scale) {
                    AppText(displayName, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 20)
                    if person?.verified == true {
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
                AppText(handle, style: Type.caption, color: ProfileTokens.sub)

                HStack(spacing: 0) {
                    stat(compact(Int64(person?.followingCount ?? 0)), Copy.t(.profileFollowing))
                    stat(compact(Int64(followerCount)), Copy.t(.profileFollowers))
                    stat(compact(Int64(posts.count)) + (cursor.isEmpty ? "" : "+"), Copy.t(.profilePosts))
                }
                .overlay(Rectangle().fill(ProfileTokens.border).frame(height: 1), alignment: .top)
                .overlay(Rectangle().fill(ProfileTokens.border).frame(height: 1), alignment: .bottom)

                if let bio = person?.bio, !bio.isEmpty {
                    AppText(bio, style: Type.caption, color: ProfileTokens.sub)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14 * scale)
                        .padding(.vertical, 10 * scale)
                        .background(ProfileTokens.bg2)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12 * scale, style: .continuous)
                                .stroke(ProfileTokens.border, lineWidth: 1 * scale)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                }

                // The only two things a visitor can do here, as one even pair.
                HStack(spacing: 10 * scale) {
                    actionPill(
                        title: isFollowing
                            ? Copy.t(.profileFollowingBtn)
                            : (person?.followsYou == true ? Copy.t(.profileFollowBack) : Copy.t(.profileFollow)),
                        filled: !isFollowing
                    ) {
                        follows.set(userId, on: !isFollowing)
                    }
                    actionPill(title: Copy.t(.profileMessage), filled: false, icon: "paperplane") {
                        guard let p = person else { return }
                        InboxController.shared.requestChat(p)
                        PersonRouter.shared.clear()
                    }
                }
            }
            .padding(.horizontal, 24 * scale)
            .padding(.top, 8 * scale)

            Rectangle().fill(ProfileTokens.border).frame(height: 1).padding(.top, 14 * scale)
        }
    }

    private var displayName: String {
        let name = person?.fullName ?? ""
        return name.isEmpty ? (person?.username ?? "") : name
    }

    private var isFollowing: Bool {
        follows.relations[userId]?.following ?? person?.following ?? false
    }

    private var followerCount: Int {
        let known = follows.relations[userId]?.followers ?? -1
        return known >= 0 ? known : (person?.followers ?? 0)
    }

    private func actionPill(title: String, filled: Bool, icon: String? = nil, action: @escaping () -> Void) -> some View {
        let ink = filled ? Color.white : ProfileTokens.text
        return Button(action: action) {
            HStack(spacing: 6 * scale) {
                if let icon {
                    Image(systemName: icon).font(.system(size: 13 * scale, weight: .semibold))
                }
                AppText(title, style: Type.labelBold, color: ink, sizeOverride: 13)
            }
            .foregroundColor(ink)
            .frame(maxWidth: .infinity)
            .frame(height: 36 * scale)
            .background(Capsule().fill(filled ? ProfileTokens.accentDeep : ProfileTokens.bg2))
            .overlay(Capsule().stroke(filled ? .clear : ProfileTokens.borderStrong, lineWidth: 1 * scale))
        }
        .buttonStyle(PressScaleStyle())
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2 * scale) {
            AppText(value, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 18)
            AppText(label, style: Type.caption, color: ProfileTokens.sub, sizeOverride: 11)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10 * scale)
    }

    private func dismiss() {
        withAnimation(.easeInOut(duration: 0.24)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) { onClose() }
    }

    private func loadMore() {
        guard !cursor.isEmpty, !loadingPosts else { return }
        Task { await load(before: Int64(cursor) ?? 0) }
    }

    private func load(before: Int64) async {
        guard !loadingPosts else { return }
        loadingPosts = true
        defer { loadingPosts = false }
        guard let page = await Xani.media.byUser(userId, before: before).value else { return }
        let cells = page.items.map(PersonProfileView.cell)
        if before == 0 {
            posts = cells
        } else {
            var seen = Set(posts.map(\.id))
            posts.append(contentsOf: cells.filter { seen.insert($0.id).inserted })
        }
        for p in page.items { raw[p.id] = p }
        cursor = page.next
        loadedPosts = true
        for c in cells.prefix(12) { if let u = c.thumbURL { ImageLoader.shared.prefetch(u) } }
    }

    private static func cell(_ p: PostData) -> ProfilePost {
        ProfilePost(
            id: p.id,
            color: FeedRepo.palette(p.id),
            views: FeedRepo.compact(p.counts.views),
            rank: Float(p.counts.likes),
            thumbURL: MediaConfig.shared.url(p.thumbKey),
            likes: FeedRepo.compact(Int64(p.counts.likes)),
            kind: p.kind,
            state: p.state,
            caption: p.caption,
            isPrivate: p.visibility == "private",
            isRestricted: p.isRestricted,
            durationMs: p.durationMs
        )
    }
}

/// The person's picture, with their initials underneath it as the fallback.
struct PersonPicture: View {
    let person: PersonData?
    let size: CGFloat

    var body: some View {
        let name = (person?.fullName.isEmpty == false ? person?.fullName : person?.username) ?? ""
        ZStack {
            Circle().fill(ProfileTokens.cta)
            if let u = MediaConfig.shared.url(person?.avatarUrl) {
                RemoteImage(url: u, contentMode: .fill, fallback: .clear)
            } else if !name.isEmpty {
                AppText(FeedRepo.initials(name), style: Type.labelBold, color: .white, sizeOverride: size * 0.34)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

/// The picture, large. A circle centred on a dimmed screen rather than a
/// full-width image: an avatar is a circle everywhere else in the app, and
/// stretching it edge to edge would show a crop nobody chose.
private struct PicturePane: View {
    let person: PersonData?
    var onClose: () -> Void

    @Environment(\.uiScale) private var scale
    @State private var grow: CGFloat = 0.82

    var body: some View {
        ZStack {
            Color.black.opacity(0.86).ignoresSafeArea()
            VStack(spacing: 18 * scale) {
                PersonPicture(person: person, size: 240 * scale)
                    .scaleEffect(grow)
                AppText(person.map { "@\($0.username)" } ?? "", style: Type.labelBold, color: .white, sizeOverride: 15)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onClose() }
        .onAppear { withAnimation(.easeOut(duration: 0.2)) { grow = 1 } }
    }
}

/// The status card as a visitor sees it: the same bubble, with nothing to tap.
private struct ReadOnlyStatus: View {
    let text: String
    @Environment(\.uiScale) private var scale

    var body: some View {
        VStack(spacing: 0) {
            AppText(text, style: Type.caption, color: .white, alignment: .center, lineLimit: 2, sizeOverride: 12)
                .padding(.horizontal, 12 * scale)
                .padding(.vertical, 7 * scale)
                .frame(maxWidth: 200 * scale)
                .background(ProfileTokens.text)
                .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
            StatusTail()
                .fill(ProfileTokens.text)
                .frame(width: 10 * scale, height: 7 * scale)
                .offset(y: -1 * scale)
        }
    }
}

private struct StatusTail: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

/// A tile on somebody else's grid: open it, nothing to edit.
private struct PersonPostCell: View {
    let post: ProfilePost
    @Environment(\.uiScale) private var scale

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Rectangle().fill(Color(rgb: post.color))
            if let u = post.thumbURL {
                RemoteImage(url: u, contentMode: .fill, fallback: .clear)
            } else if post.kind == "text", !post.caption.isEmpty {
                AppText(post.caption, style: Type.labelBold, color: .white, alignment: .center, lineLimit: 6, sizeOverride: 11)
                    .padding(8 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            HStack(spacing: 3 * scale) {
                VectorCanvas(
                    [.data("M9 15s-7-4.5-7-9A4 4 0 019 4.6 4 4 0 0116 6c0 4.5-7 9-7 9z", .fill(.white))],
                    viewport: 18,
                    side: 10 * scale
                )
                AppText(post.likes, style: Type.labelBold, color: .white, sizeOverride: 10)
            }
            .padding(4 * scale)
            if post.kind == "video", post.durationMs > 0 {
                AppText(String(format: "%d:%02d", post.durationMs / 60000, (post.durationMs / 1000) % 60),
                        style: Type.labelBold, color: .white, sizeOverride: 9)
                    .padding(4 * scale)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
        }
        .clipped()
    }
}
