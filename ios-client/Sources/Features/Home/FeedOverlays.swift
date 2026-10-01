import SwiftUI

// MARK: - Comments sheet

/// Comment order. Both are server-side index reads on the thread, and both are
/// what the wire calls them, so the sheet hands the key straight to the repo.
///
/// ("With media" is not here: a comment cannot carry an attachment yet, so the
/// filter would have nothing to hide.)
enum CommentSort: String {
    case top, newest

    var wire: String { self == .top ? "top" : "new" }
}

struct FeedCommentsSheet: View {
    let post: FeedPost
    let comments: [FeedComment]
    var onClose: () -> Void
    var onSend: (String) -> Void = { _ in }
    var onLoadMore: () -> Void = {}
    var onSort: (String) -> Void = { _ in }
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var ready = false
    @State private var dragY: CGFloat = 0
    @State private var sortMenu = false
    @State private var sort: CommentSort = .top
    @State private var likedIds: Set<Int64> = []
    @State private var dislikedIds: Set<Int64> = []
    @State private var openReplies: Set<Int64> = []
    @State private var draft = ""
    @FocusState private var composing: Bool

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Color(rgb: post.creator.color).opacity(0.5)
                if let u = post.posterURL {
                    RemoteImage(url: u, contentMode: .fill, fallback: .clear)
                } else {
                    AppText(post.creator.initials, style: Type.wordmark, color: Color.white.opacity(0.25), sizeOverride: 56)
                }
            }
            .clipped()
            .frame(height: UIScreen.main.bounds.height * (ready ? 0.38 : 1.0))
            .animation(.easeInOut(duration: 0.42), value: ready)
            .contentShape(Rectangle())
            .onTapGesture { dismiss() }

            VStack(spacing: 0) {
                Capsule()
                    .fill(Color(rgb: 0xDDDDDD))
                    .frame(width: 40 * scale, height: 4 * scale)
                    .padding(.top, 10 * scale)
                    .padding(.bottom, 8 * scale)

                HStack {
                    AppText(Copy.t(.homeCommentsCount, ["n": post.comments]), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 15)
                    Spacer()
                    Button(action: { sortMenu.toggle() }) {
                        AppText("≡", style: Type.label, color: Color(rgb: 0x555555), sizeOverride: 20)
                            .frame(width: 32 * scale, height: 32 * scale)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 18 * scale)
                .padding(.bottom, 12 * scale)
                Color(rgb: 0xF0F0F0).frame(height: 1)

                ZStack(alignment: .topTrailing) {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            if comments.isEmpty {
                                AppText(Copy.t(.homeCommentsEmpty), style: Type.caption, color: Color(rgb: 0xAAAAAA), alignment: .center, sizeOverride: 14)
                                    .frame(maxWidth: .infinity)
                                    .padding(28 * scale)
                            }
                            ForEach(comments) { c in
                                commentRow(c)
                                    // Ask for the next older page while the last row is still off-screen.
                                    .onAppear { if c.id == comments.last?.id { onLoadMore() } }
                            }
                        }
                        .padding(.bottom, 16 * scale)
                    }
                    if sortMenu {
                        VStack(alignment: .leading, spacing: 0) {
                            sortBtn(Copy.t(.homeSortTop), .top)
                            sortBtn(Copy.t(.homeSortNewest), .newest)
                        }
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14 * scale, style: .continuous))
                        .shadow(color: Color.black.opacity(0.16), radius: 12, y: 4)
                        .padding(.trailing, 14 * scale)
                        .padding(.top, 4 * scale)
                    }
                }
                .frame(maxHeight: .infinity)

                HStack(spacing: 10 * scale) {
                    MyAvatar(size: 34)
                    ZStack(alignment: .leading) {
                        if draft.isEmpty {
                            AppText(
                                Copy.t(post.allowComments ? .homeAddComment : .homeCommentsOff),
                                style: Type.caption, color: Color(rgb: 0xAAAAAA), sizeOverride: 14
                            )
                        }
                        if post.allowComments {
                            TextField("", text: $draft)
                                .font(.system(size: 14 * scale))
                                .foregroundColor(Color(rgb: 0x111111))
                                .tint(Color(rgb: 0x7C3AED))
                                .submitLabel(.send)
                                .focused($composing)
                                .onSubmit(send)
                                .onChange(of: draft) { v in if v.count > 1000 { draft = String(v.prefix(1000)) } }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 10 * scale)
                    .background(Color(rgb: 0xF5F5F5))
                    .clipShape(Capsule())
                    // The whole pill is the tap target, not just the glyphs
                    // inside it: anywhere in the capsule puts the caret in the
                    // field and raises the keyboard.
                    .contentShape(Capsule())
                    .onTapGesture { if post.allowComments { composing = true } }
                    if post.allowComments {
                        // Always on screen, so there is visibly somewhere to
                        // send to; it only lights up once there is something
                        // to send.
                        let ready = !draft.trimmingCharacters(in: .whitespaces).isEmpty
                        Button(action: send) {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.system(size: 28 * scale))
                                .foregroundColor(ready ? Color(rgb: 0x7C3AED) : Color(rgb: 0xDDDDDD))
                        }
                        .buttonStyle(.plain)
                        .disabled(!ready)
                    }
                }
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 10 * scale)
                .padding(.bottom, max(insets.bottom, 8 * scale))
                .background(Color.white)
            }
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20 * scale, style: .continuous))
            .offset(y: dragY)
            .gesture(
                DragGesture()
                    .onChanged { dragY = max(0, $0.translation.height) }
                    .onEnded { v in
                        if v.translation.height > 80 { dismiss() }
                        dragY = 0
                    }
            )
        }
        // `.container` and not `.all`: the sheet still runs under the status bar
        // and home indicator, but the keyboard region stays a real inset, so the
        // input the user just tapped rises above it instead of hiding under it.
        .ignoresSafeArea(.container)
        .background(Color.black.opacity(0.001))
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.02) {
                withAnimation(.easeInOut(duration: 0.42)) { ready = true }
            }
        }
        // Re-reads the thread in the chosen order. Also the first read, so
        // opening the sheet and flipping the filter take the same path.
        .onAppear { onSort(sort.wire) }
        .onChange(of: sort) { s in onSort(s.wire) }
    }

    private func dismiss() {
        withAnimation(.easeInOut(duration: 0.38)) { ready = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) { onClose() }
    }

    private func send() {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        onSend(body)
        draft = ""
    }

    private func sortBtn(_ label: String, _ value: CommentSort) -> some View {
        Button(action: { sort = value; sortMenu = false }) {
            AppText(label, style: sort == value ? Type.labelBold : Type.label,
                    color: Color(rgb: sort == value ? 0x111111 : 0x333333), sizeOverride: 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 18 * scale)
                .padding(.vertical, 12 * scale)
        }
        .buttonStyle(.plain)
    }

    private func commentRow(_ c: FeedComment) -> some View {
        let liked = likedIds.contains(c.id)
        let disliked = dislikedIds.contains(c.id)
        let open = openReplies.contains(c.id)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 10 * scale) {
                ZStack {
                    Circle().fill(Color(rgb: c.color))
                    if let u = c.avatarURL {
                        RemoteImage(url: u, contentMode: .fill, fallback: .clear)
                    } else {
                        AppText(String(c.user.prefix(1)).uppercased(), style: Type.labelBold, color: .white, sizeOverride: 14)
                    }
                }
                .frame(width: 38 * scale, height: 38 * scale)
                .clipShape(Circle())
                .onTapGesture { PersonRouter.shared.open(c.authorId) }
                VStack(alignment: .leading, spacing: 3 * scale) {
                    AppText(c.user, style: Type.label, color: Color(rgb: 0x888888), sizeOverride: 13)
                        .contentShape(Rectangle())
                        .onTapGesture { PersonRouter.shared.open(c.authorId) }
                    AppText(c.text, style: Type.caption, color: Color(rgb: 0x111111), sizeOverride: 14)
                    HStack {
                        AppText(c.time, style: Type.caption, color: Color(rgb: 0xAAAAAA), sizeOverride: 12)
                        AppText(Copy.t(.homeReply), style: Type.labelBold, color: Color(rgb: 0x888888), sizeOverride: 12)
                            .padding(.leading, 14 * scale)
                        Spacer()
                        Button(action: {
                            if liked { likedIds.remove(c.id) } else { likedIds.insert(c.id); dislikedIds.remove(c.id) }
                        }) {
                            HStack(spacing: 3 * scale) {
                                Image(systemName: liked ? "heart.fill" : "heart")
                                    .foregroundColor(liked ? Color(rgb: 0xEC4899) : Color(rgb: 0xAAAAAA))
                                    .font(.system(size: 14 * scale))
                                AppText("\(liked ? c.likes + 1 : c.likes)", style: Type.caption, color: liked ? Color(rgb: 0xEC4899) : Color(rgb: 0xAAAAAA), sizeOverride: 12)
                            }
                        }
                        .buttonStyle(.plain)
                        Button(action: {
                            if disliked { dislikedIds.remove(c.id) } else { dislikedIds.insert(c.id); likedIds.remove(c.id) }
                        }) {
                            Image(systemName: "hand.thumbsdown")
                                .foregroundColor(disliked ? Color(rgb: 0x6366F1) : Color(rgb: 0xAAAAAA))
                                .font(.system(size: 14 * scale))
                        }
                        .buttonStyle(.plain)
                        .padding(.leading, 10 * scale)
                    }
                    .padding(.top, 4 * scale)
                    if !c.replies.isEmpty {
                        Button(action: {
                            if open { openReplies.remove(c.id) } else { openReplies.insert(c.id) }
                        }) {
                            HStack(spacing: 6 * scale) {
                                Rectangle().fill(Color(rgb: 0xDDDDDD)).frame(width: 24 * scale, height: 1)
                                AppText(
                                    Copy.t(.homeViewReplies, ["n": "\(c.replies.count)"]) + (open ? " ▲" : " ▾"),
                                    style: Type.labelBold,
                                    color: Color(rgb: 0x7C3AED),
                                    sizeOverride: 13
                                )
                            }
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 8 * scale)
                    }
                    if open {
                        ForEach(c.replies) { r in
                            HStack(alignment: .top, spacing: 8 * scale) {
                                Circle()
                                    .fill(Color(rgb: r.color))
                                    .frame(width: 28 * scale, height: 28 * scale)
                                    .overlay(AppText(String(r.user.prefix(1)).uppercased(), style: Type.labelBold, color: .white, sizeOverride: 11))
                                VStack(alignment: .leading, spacing: 2 * scale) {
                                    AppText(r.user, style: Type.label, color: Color(rgb: 0x888888), sizeOverride: 12)
                                    AppText(r.text, style: Type.caption, color: Color(rgb: 0x111111), sizeOverride: 13)
                                    HStack {
                                        AppText(r.time, style: Type.caption, color: Color(rgb: 0xBBBBBB), sizeOverride: 11)
                                        Spacer()
                                        AppText("♡ \(r.likes)", style: Type.caption, color: Color(rgb: 0xBBBBBB), sizeOverride: 11)
                                    }
                                }
                            }
                            .padding(.top, 12 * scale)
                            .padding(.leading, 4 * scale)
                        }
                    }
                }
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, 14 * scale)
            Color(rgb: 0xF5F5F5).frame(height: 1).padding(.leading, 48 * scale).padding(.top, 14 * scale)
        }
    }
}

// MARK: - Sound page

struct FeedSoundPage: View {
    let post: FeedPost
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var slide: CGFloat = 1

    private let seeds: [UInt32] = [0x7C3AED, 0xEC4899, 0x0EA5E9, 0xF59E0B, 0x10B981, 0x6366F1, 0xF472B6, 0xEF4444, 0x34D399]
    private let views = ["847K", "523K", "312K", "289K", "204K", "187K", "156K", "121K", "98K"]
    private let cols = [GridItem(.flexible(), spacing: 2), GridItem(.flexible(), spacing: 2), GridItem(.flexible(), spacing: 2)]

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.white.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack(spacing: 10 * scale) {
                    Button(action: dismiss) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16 * scale, weight: .semibold))
                            .foregroundColor(Color(rgb: 0x111111))
                            .frame(width: 32 * scale, height: 32 * scale)
                    }
                    .buttonStyle(.plain)
                    HStack(spacing: 8 * scale) {
                        Image(systemName: "magnifyingglass").foregroundColor(Color(rgb: 0x888888)).font(.system(size: 14 * scale))
                        AppText(Copy.t(.homeSoundFind), style: Type.caption, color: Color(rgb: 0x888888), sizeOverride: 14)
                    }
                    .padding(.horizontal, 14 * scale)
                    .padding(.vertical, 9 * scale)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(rgb: 0xF3F3F3))
                    .clipShape(Capsule())
                }
                .padding(.horizontal, 14 * scale)
                .padding(.top, insets.top + 8 * scale)
                .padding(.bottom, 10 * scale)
                Color.black.opacity(0.08).frame(height: 1)

                ScrollView {
                    HStack(spacing: 16 * scale) {
                        Circle()
                            .fill(LinearGradient(colors: [Color(rgb: 0x7C3AED), Color(rgb: 0xEC4899)], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(width: 80 * scale, height: 80 * scale)
                            .overlay(Circle().stroke(Color(rgb: 0x8B5CF6), lineWidth: 3 * scale))
                            .overlay(AppText(post.creator.initials, style: Type.labelBold, color: .white, sizeOverride: 22))
                        VStack(alignment: .leading, spacing: 4 * scale) {
                            AppText(Copy.t(.homeSoundOriginal), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 18)
                            AppText(post.creator.handle, style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 14)
                            AppText(Copy.t(.homeSoundPosts, ["n": "20"]), style: Type.caption, color: Color(rgb: 0x888888), sizeOverride: 13)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(18 * scale)

                    AppText(Copy.t(.homeSoundSave), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 15)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13 * scale)
                        .background(Color(rgb: 0xF3F3F3))
                        .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                        .padding(.horizontal, 18 * scale)
                        .padding(.bottom, 20 * scale)

                    LazyVGrid(columns: cols, spacing: 2 * scale) {
                        ForEach(0..<seeds.count, id: \.self) { i in
                            ZStack(alignment: .bottomLeading) {
                                LinearGradient(
                                    colors: [Color(rgb: seeds[i]).opacity(0.8), Color(rgb: 0x111111)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                if i == 0 {
                                    AppText("Original", style: Type.labelBold, color: .white, sizeOverride: 11)
                                        .padding(.horizontal, 7 * scale)
                                        .padding(.vertical, 2 * scale)
                                        .background(Color(rgb: 0xFF3B30))
                                        .clipShape(RoundedRectangle(cornerRadius: 6 * scale, style: .continuous))
                                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                                        .padding(6 * scale)
                                }
                                AppText(views[i], style: Type.labelBold, color: .white, sizeOverride: 11)
                                    .padding(6 * scale)
                            }
                            .aspectRatio(9 / 16, contentMode: .fit)
                        }
                    }
                    .padding(.bottom, 120 * scale)
                }
            }

            HStack(spacing: 10 * scale) {
                AppText(Copy.t(.homeSoundStory), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 14)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13 * scale)
                    .background(Color(rgb: 0xF3F3F3))
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                AppText(Copy.t(.homeSoundUse), style: Type.labelBold, color: .white, sizeOverride: 15)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13 * scale)
                    .background(LinearGradient(colors: [Color(rgb: 0xEC4899), Color(rgb: 0x8B5CF6)], startPoint: .leading, endPoint: .trailing))
                    .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                    .frame(maxWidth: .infinity)
                    .layoutPriority(1)
            }
            .padding(.horizontal, 16 * scale)
            .padding(.top, 12 * scale)
            .padding(.bottom, max(insets.bottom, 12 * scale))
            .background(Color.white)
        }
        .offset(y: UIScreen.main.bounds.height * slide)
        .onAppear { withAnimation(.easeInOut(duration: 0.32)) { slide = 0 } }
    }

    private func dismiss() {
        withAnimation(.easeInOut(duration: 0.28)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) { onClose() }
    }
}

// MARK: - Search page

struct FeedSearchPage: View {
    var onClose: () -> Void
    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets
    @State private var slide: CGFloat = 1
    @State private var query = ""
    // People results for the current query. Searches fire per keystroke after a
    // short settle so the list tracks typing without a request per character.
    @State private var people: [PersonData] = []
    @State private var searched = ""
    @State private var searching = false
    @State private var recents: [(id: Int, text: String, thumb: UInt32?)] = [
        (1, "trending dance challenge", nil),
        (2, "cooking with asmr", nil),
        (3, "", 0x7C3AED),
        (4, "viral cat videos funny", nil),
        (5, "", 0xEC4899),
    ]
    private let suggestions: [(text: String, sub: String, hot: Bool)] = [
        ("trending sounds this week", "Related to your search", true),
        ("funny fails compilation", "Just watched", true),
        ("morning routine aesthetic", "", false),
        ("street food pakistan", "", false),
        ("diy home decor ideas", "Related to your search", false),
        ("satisfying videos compilation", "", false),
        ("cricket highlights today", "", false),
        ("study with me lofi", "Related to your search", false),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10 * scale) {
                Button(action: dismiss) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16 * scale, weight: .semibold))
                        .foregroundColor(Color(rgb: 0x111111))
                        .frame(width: 32 * scale, height: 32 * scale)
                }
                .buttonStyle(.plain)
                HStack(spacing: 8 * scale) {
                    Image(systemName: "magnifyingglass").foregroundColor(Color(rgb: 0x888888)).font(.system(size: 14 * scale))
                    TextField(Copy.t(.homeSearchHint), text: $query)
                        .font(.system(size: 15 * scale))
                        .foregroundColor(Color(rgb: 0x111111))
                }
                .padding(.horizontal, 14 * scale)
                .padding(.vertical, 10 * scale)
                .background(Color(rgb: 0xF3F3F3))
                .clipShape(Capsule())
                AppText(Copy.t(.homeSearchGo), style: Type.labelBold, color: Color(rgb: 0x7C3AED), sizeOverride: 14)
            }
            .padding(.horizontal, 14 * scale)
            .padding(.top, insets.top + 8 * scale)
            .padding(.bottom, 10 * scale)

            let trimmed = query.trimmingCharacters(in: .whitespaces)
            if trimmed.count >= 2 {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        AppText(Copy.t(.homeSearchPeople), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                            .padding(.horizontal, 18 * scale)
                            .padding(.top, 10 * scale)
                            .padding(.bottom, 4 * scale)
                        if people.isEmpty, searched == trimmed, !searching {
                            AppText(Copy.t(.homeSearchNoPeople, ["q": trimmed]), style: Type.caption, color: Color(rgb: 0x888888), alignment: .center, sizeOverride: 14)
                                .frame(maxWidth: .infinity)
                                .padding(24 * scale)
                        }
                        ForEach(people) { p in
                            PersonRow(person: p) { hit in
                                // Leave search behind: the profile is the
                                // destination, and coming back to a stale
                                // result list is not what the tap asked for.
                                dismiss()
                                PersonRouter.shared.open(hit.id)
                            }
                        }
                        Spacer().frame(height: 40 * scale)
                    }
                }
                .task(id: trimmed) {
                    searching = true
                    try? await Task.sleep(nanoseconds: 180_000_000)
                    guard !Task.isCancelled else { return }
                    if let hits = await Xani.people.search(trimmed).value, !Task.isCancelled {
                        FollowStore.shared.learn(hits)
                        people = hits
                        searched = trimmed
                    }
                    searching = false
                }
            } else {
            ScrollView {
                ForEach(recents, id: \.id) { r in
                    HStack(spacing: 12 * scale) {
                        Group {
                            if let thumb = r.thumb {
                                RoundedRectangle(cornerRadius: 8 * scale, style: .continuous).fill(Color(rgb: thumb))
                            } else {
                                Circle().fill(Color(rgb: 0xF3F3F3))
                                    .overlay(Image(systemName: "clock").foregroundColor(Color(rgb: 0x888888)).font(.system(size: 12 * scale)))
                            }
                        }
                        .frame(width: 32 * scale, height: 32 * scale)
                        AppText(r.text.isEmpty ? Copy.t(.homeSearchRecent) : r.text, style: Type.caption, color: Color(rgb: 0x111111), sizeOverride: 15)
                        Spacer()
                        Button(action: { recents.removeAll { $0.id == r.id } }) {
                            AppText("×", style: Type.label, color: Color(rgb: 0x888888), sizeOverride: 18)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 18 * scale)
                    .padding(.vertical, 11 * scale)
                }
                AppText(Copy.t(.homeSearchSeeMore) + " ▾", style: Type.caption, color: Color(rgb: 0x888888), sizeOverride: 14, alignment: .center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10 * scale)

                HStack {
                    AppText(Copy.t(.homeSearchYouMayLike), style: Type.labelBold, color: Color(rgb: 0x111111), sizeOverride: 16)
                    Spacer()
                    AppText(Copy.t(.homeSearchRefresh), style: Type.labelBold, color: Color(rgb: 0x888888), sizeOverride: 13)
                }
                .padding(.horizontal, 18 * scale)
                .padding(.top, 10 * scale)
                .padding(.bottom, 6 * scale)

                ForEach(Array(suggestions.enumerated()), id: \.offset) { _, s in
                    HStack(alignment: .top, spacing: 14 * scale) {
                        Circle()
                            .fill(s.hot ? Color(rgb: 0xEC4899) : Color(rgb: 0xCCCCCC))
                            .frame(width: 8 * scale, height: 8 * scale)
                            .padding(.top, 6 * scale)
                        VStack(alignment: .leading, spacing: 2 * scale) {
                            AppText(s.text, style: s.hot ? Type.labelBold : Type.caption, color: s.hot ? Color(rgb: 0xEC4899) : Color(rgb: 0x111111), sizeOverride: 15)
                            if !s.sub.isEmpty {
                                AppText(s.sub, style: Type.caption, color: Color(rgb: 0x888888), sizeOverride: 12)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 18 * scale)
                    .padding(.vertical, 11 * scale)
                }
                Spacer().frame(height: 40 * scale)
            }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .offset(y: UIScreen.main.bounds.height * slide)
        .onAppear { withAnimation(.easeInOut(duration: 0.28)) { slide = 0 } }
    }

    private func dismiss() {
        withAnimation(.easeInOut(duration: 0.26)) { slide = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) { onClose() }
    }
}
