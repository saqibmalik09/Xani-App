import Foundation

/// "Open this person", from anywhere.
///
/// A profile is reachable from a feed card, a search hit, a follower row, a
/// visit row and a chat header — five surfaces on four different tabs.
/// Threading a callback through all of them would put navigation in every
/// signature, so the destination is one piece of app state instead and the
/// shell renders it over whichever tab is showing.
///
/// It is a stack because profiles lead to profiles: their followers, then one
/// of those, then back. Depth is capped so a chain of taps cannot grow without
/// bound.
@MainActor
final class PersonRouter: ObservableObject {
    static let shared = PersonRouter()

    private static let maxDepth = 8

    /// Open profiles, oldest first; the last one is what is on screen.
    @Published private(set) var stack: [Int64] = []

    var top: Int64? { stack.last }

    private init() {}

    func open(_ userId: Int64) {
        guard userId > 0, stack.last != userId else { return }
        stack.append(userId)
        if stack.count > Self.maxDepth { stack.removeFirst(stack.count - Self.maxDepth) }
    }

    /// Closes the top profile.
    func back() {
        if !stack.isEmpty { stack.removeLast() }
    }

    /// Closes every open profile — a tab change, or signing out.
    func clear() {
        if !stack.isEmpty { stack = [] }
    }
}
