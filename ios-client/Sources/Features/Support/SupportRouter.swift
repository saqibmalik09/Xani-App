import Combine
import Foundation

/// Opens a Help Centre ticket from anywhere (inbox notice tap).
@MainActor
final class SupportRouter: ObservableObject {
    static let shared = SupportRouter()

    @Published private(set) var pendingTicketId: Int64 = 0

    func openTicket(_ ticketId: Int64) {
        guard ticketId > 0 else { return }
        pendingTicketId = ticketId
    }

    func consume() -> Int64 {
        let id = pendingTicketId
        pendingTicketId = 0
        return id
    }
}
