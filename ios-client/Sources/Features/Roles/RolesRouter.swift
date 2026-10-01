import Combine
import Foundation

/// Opens the Roles surface from anywhere (push tap, inbox notice).
/// `pending` is 0 when idle, a request id to land on that request, or
/// `RolesRouter.home` to open the roles home with no particular request.
@MainActor
final class RolesRouter: ObservableObject {
    static let shared = RolesRouter()
    static let home: Int64 = -1

    @Published private(set) var pending: Int64 = 0

    func openRequest(_ requestId: Int64) {
        pending = requestId > 0 ? requestId : Self.home
    }

    func openHome() {
        pending = Self.home
    }

    /// Returns the request id to open (0 for home) and clears the signal.
    func consume() -> Int64 {
        let id = pending
        pending = 0
        return id == Self.home ? 0 : id
    }
}
