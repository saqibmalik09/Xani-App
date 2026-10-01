import SwiftUI

/// "Do I follow them" for every account the app has seen, and the one place a
/// follow is toggled from — the feed card, a search hit, a follower list — so a
/// tap on one surface flips the button on every other one at once.
///
/// Optimistic: the relation flips on tap, the server ack (both counters) snaps it
/// to truth, a failure rolls it back. The signed-in user's own following count
/// comes back in the same ack and is pushed into the profile cache, so Profile
/// shows the new number without a refresh.
@MainActor
final class FollowStore: ObservableObject {
    static let shared = FollowStore()

    struct Relation: Equatable {
        var following: Bool
        var followsYou: Bool = false
        /// -1 = unknown.
        var followers: Int = -1
    }

    @Published private(set) var relations: [Int64: Relation] = [:]

    private init() {}

    func isFollowing(_ userId: Int64, fallback: Bool = false) -> Bool {
        relations[userId]?.following ?? fallback
    }

    /// Seed from anything the server said about a person (list rows, search hits).
    func learn(_ people: [PersonData]) {
        guard !people.isEmpty else { return }
        for p in people { relations[p.id] = Relation(following: p.following, followsYou: p.followsYou, followers: p.followers) }
    }

    /// Seed from a post's viewer flags — the feed is where most follows begin.
    func learn(_ userId: Int64, following: Bool) {
        guard userId != 0 else { return }
        // Server truth for the relation, but never overwrite a tap still in flight.
        if relations[userId]?.following == following { return }
        var r = relations[userId] ?? Relation(following: following)
        r.following = following
        relations[userId] = r
    }

    func toggle(_ userId: Int64) {
        set(userId, on: !(relations[userId]?.following ?? false))
    }

    func set(_ userId: Int64, on: Bool) {
        guard userId != 0 else { return }
        let before = relations[userId] ?? Relation(following: false)
        if before.following == on { return }
        var next = before
        next.following = on
        if before.followers >= 0 { next.followers = max(0, before.followers + (on ? 1 : -1)) }
        relations[userId] = next
        Task {
            let r = await Xani.people.follow(userId, on: on)
            if let ack = r.value {
                var cur = relations[userId] ?? before
                cur.following = ack.following
                cur.followers = ack.followers
                relations[userId] = cur
                ProfileStore.shared.setFollowing(count: ack.myFollowing)
            } else if !r.isSessionExpired {
                relations[userId] = before
            }
        }
    }

    func signedOut() {
        relations = [:]
    }
}
