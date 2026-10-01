import SwiftUI

/// The signed-in user's followers or following, newest first, keyset paged.
/// Rows are `PersonRow`, so every follow button is the shared relation.
struct PeopleList: View {
    let kind: ProfileStatsKind
    @Environment(\.uiScale) private var scale
    @ObservedObject private var session = Xani.session
    @State private var people: [PersonData] = []
    @State private var cursor = ""
    @State private var loaded = false
    @State private var loading = false

    var body: some View {
        LazyVStack(spacing: 0) {
            if loaded, people.isEmpty {
                AppText(Copy.t(.profileNoPeople), style: Type.caption, color: ProfileTokens.muted, alignment: .center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40 * scale)
            }
            ForEach(Array(people.enumerated()), id: \.element.id) { i, p in
                PersonRow(person: p, showMeta: false) { PersonRouter.shared.open($0.id) }
                    .onAppear { if i == people.count - 1 { loadMore() } }
                if i < people.count - 1 {
                    Rectangle().fill(ProfileTokens.border).frame(height: 1).padding(.horizontal, 20 * scale)
                }
            }
        }
        .task(id: "\(kind)-\(session.userId)") {
            people = []
            cursor = ""
            loaded = false
            await fetch(reset: true)
        }
    }

    private func loadMore() {
        guard !loading, !cursor.isEmpty else { return }
        Task { await fetch(reset: false) }
    }

    private func fetch(reset: Bool) async {
        let me = session.userId
        guard me != 0, !loading else { return }
        loading = true
        let r = kind == .followers
            ? await Xani.people.followers(me, cursor: reset ? "" : cursor)
            : await Xani.people.following(me, cursor: reset ? "" : cursor)
        if let page = r.value {
            FollowStore.shared.learn(page.items)
            if reset {
                people = page.items
            } else {
                var seen = Set(people.map(\.id))
                people.append(contentsOf: page.items.filter { seen.insert($0.id).inserted })
            }
            cursor = page.next
        }
        loaded = true
        loading = false
    }
}
