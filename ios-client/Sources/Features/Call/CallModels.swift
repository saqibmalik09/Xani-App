import Foundation

enum CallPhase: Equatable {
    /// Dialling: the invite is out, nobody has picked up.
    case outgoing
    /// Ringing on this device.
    case incoming
    /// Accepted on both ends; ICE/DTLS still settling.
    case connecting
    case connected
    /// Media path dropped; ICE restarts running inside the server grace window.
    case reconnecting
    /// Terminal; the surface lingers briefly to show why, then clears.
    case ended
}

/// Why a call finished, in UI terms.
enum CallOutcome: Equatable { case none, ended, busy, noAnswer, declined, missed, lost, failed, unavailable }

struct CallPeer: Equatable {
    let id: Int64
    let name: String
    let initials: String
    let color: UInt32
    var avatarUrl = ""
}

struct CallUi: Equatable {
    var callId: Int64
    var conversationId: Int64
    /// Inbox row that opened the call; nil when it rang in from elsewhere.
    var chatId: Int?
    var outgoing: Bool
    var video: Bool
    var phase: CallPhase
    var peer: CallPeer
    /// p2p | sfu
    var mode = "p2p"
    var muted = false
    var cameraOff = false
    var speaker = false
    var frontCamera = true
    var remoteVideo = false
    var remoteMuted = false
    /// Callee device reported ringing, so the caller hears ringback.
    var ringing = false
    /// Uptime seconds when media first connected; 0 before.
    var connectedAt: TimeInterval = 0
    var participants: [Int64] = []
    var outcome: CallOutcome = .none
    var minimized = false

    var active: Bool { phase != .ended }
    var inMedia: Bool { phase == .connected || phase == .reconnecting }
}
