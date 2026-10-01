import SwiftUI

/// Who has been looking at this profile — the month, newest first.
///
/// A row is a person, not a tap: somebody who comes back every day appears
/// once, at the top. That is what makes the list readable and what keeps it a
/// keyset read instead of a growing log.
///
/// It stays live while open: a visit that lands on the profile stream re-reads
/// the first page, so the name of whoever just looked appears without a pull.
struct ProfileVisitsView: View {
    @ObservedObject var store: ProfileStore
    var onBack: () -> Void

    @Environment(\.uiScale) private var scale
    @Environment(\.systemSafeInsets) private var insets

    @State private var items: [VisitData] = []
    @State private var cursor = ""
    @State private var loaded = false
    @State private var loading = false
    /// Bumped by the live stream; re-reads the head page.
    @State private var revision = 0
    @State private var token: LiveFeed.Token?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12 * scale) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16 * scale, weight: .semibold))
                        .foregroundColor(ProfileTokens.text)
                        .frame(width: 32 * scale, height: 32 * scale)
                        .background(ProfileTokens.bg2)
                        .clipShape(Circle())
                }
                .buttonStyle(PressScaleStyle())
                VStack(alignment: .leading, spacing: 1 * scale) {
                    AppText(Copy.t(.profileVisitsTitle), style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 17)
                    AppText(Copy.t(.profileVisitsMonth), style: Type.micro, color: ProfileTokens.sub, sizeOverride: 11)
                }
                Spacer()
                HStack(spacing: 5 * scale) {
                    Image(systemName: "eye.fill")
                        .font(.system(size: 13 * scale))
                        .foregroundColor(ProfileTokens.accentDeep)
                    AppText(store.snapshot.profileViews, style: Type.labelBold, color: ProfileTokens.accentDeep, sizeOverride: 15)
                }
            }
            .padding(.horizontal, 20 * scale)
            .padding(.vertical, 14 * scale)
            Rectangle().fill(ProfileTokens.border).frame(height: 1)

            ScrollView {
                LazyVStack(spacing: 0) {
                    if loaded, items.isEmpty {
                        AppText(Copy.t(.profileVisitsEmpty), style: Type.caption, color: ProfileTokens.muted,
                                alignment: .center, sizeOverride: 14)
                            .frame(maxWidth: .infinity)
                            .padding(32 * scale)
                    }
                    ForEach(items) { visit in
                        VisitRow(visit: visit)
                            .onAppear { if visit.id == items.last?.id { loadMore() } }
                        Rectangle().fill(ProfileTokens.rowLine).frame(height: 1)
                            .padding(.horizontal, 20 * scale)
                    }
                }
            }
        }
        .background(ProfileTokens.bg.ignoresSafeArea())
        .padding(.top, insets.top)
        .task(id: revision) { await load(after: "") }
        // A new visitor arrived while the list is open: re-read the head only.
        // The listener is dropped with the screen so a reopened list does not
        // stack another one.
        .onAppear { token = LiveFeed.shared.listenProfileViews { _ in revision += 1 } }
        .onDisappear {
            if let t = token { LiveFeed.shared.stopListening(t) }
            token = nil
        }
    }

    private func loadMore() {
        guard !cursor.isEmpty, !loading else { return }
        Task { await load(after: cursor) }
    }

    private func load(after: String) async {
        guard !loading else { return }
        loading = true
        defer { loading = false }
        if let page = await Xani.people.visits(cursor: after).value {
            let rows = page.items.filter { $0.person.id > 0 }
            FollowStore.shared.learn(rows.map(\.person))
            if after.isEmpty {
                items = rows
            } else {
                var seen = Set(items.map(\.id))
                items.append(contentsOf: rows.filter { seen.insert($0.id).inserted })
            }
            cursor = page.next
        }
        loaded = true
    }
}

/// One visitor: picture, handle, when they came by, and one button.
private struct VisitRow: View {
    let visit: VisitData
    @Environment(\.uiScale) private var scale
    @ObservedObject private var follows = FollowStore.shared

    var body: some View {
        let person = visit.person
        let following = follows.relations[person.id]?.following ?? person.following
        return HStack(spacing: 12 * scale) {
            PersonAvatar(
                id: person.id,
                name: person.fullName.isEmpty ? person.username : person.fullName,
                avatarUrl: person.avatarUrl,
                size: 44 * scale
            )
            VStack(alignment: .leading, spacing: 2 * scale) {
                HStack(spacing: 4 * scale) {
                    AppText(person.username, style: Type.labelBold, color: ProfileTokens.text, sizeOverride: 14)
                    if person.verified {
                        AppText("✓", style: Type.labelBold, color: ProfileTokens.accentDeep, sizeOverride: 11)
                    }
                }
                AppText(FeedRepo.relativeTime(visit.at), style: Type.micro, color: ProfileTokens.sub, sizeOverride: 11)
            }
            Spacer(minLength: 10 * scale)
            FollowButton(following: following, followsYou: person.followsYou) {
                follows.set(person.id, on: !following)
            }
        }
        .padding(.horizontal, 20 * scale)
        .padding(.vertical, 10 * scale)
        .contentShape(Rectangle())
        .onTapGesture { PersonRouter.shared.open(person.id) }
    }
}
