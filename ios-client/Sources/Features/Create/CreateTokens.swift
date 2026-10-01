import SwiftUI

/// Create visual tokens — one place to recolor the studio.
enum CreateTokens {
    static let scrim = Color.black
    static let viewfinder = Color(rgb: 0x0A0A12)
    static let topBarScrim = Color.black.opacity(0.55)
    static let glass = Color.black.opacity(0.4)
    static let glassSoft = Color.black.opacity(0.35)
    static let chipFill = Color.black.opacity(0.45)
    static let toolMuted = Color.white.opacity(0.72)
    static let toolDim = Color.white.opacity(0.45)
    static let modeOnBg = Color.white.opacity(0.92)
    static let modeOnFg = Color.black
    static let modeOffFg = Color.white.opacity(0.72)
    static let recordAccent = Color(rgb: 0xEF4444)
    static let recordRingA = Color(rgb: 0x8B5CF6)
    static let recordRingB = Color(rgb: 0xEC4899)
    static let laneActive = Color.white
    static let laneIdle = Color.white.opacity(0.45)
    static let galleryRing = Color.white.opacity(0.55)
    static let gridLine = Color.white.opacity(0.18)
    static var bottomFade: LinearGradient {
        LinearGradient(colors: [.clear, Color.black.opacity(0.82)], startPoint: .top, endPoint: .bottom)
    }
    static var recordWash: LinearGradient {
        LinearGradient(colors: [recordRingA, recordRingB], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    // Live setup (UpdatedFigma lavender)
    static let liveAccent = Color(rgb: 0x7C3AED)
    static let livePink = Color(rgb: 0xEC4899)
    static let liveInk = Color(rgb: 0x1E0A50)
    static let liveSub = Color(rgb: 0x6450A0).opacity(0.6)
    static let liveChip = Color(rgb: 0x5B21B6)
    static let liveSetupBg = Color(rgb: 0xF5F4FF)
    static var liveSetupWash: LinearGradient {
        LinearGradient(
            colors: [Color(rgb: 0xF0EEFF), Color(rgb: 0xF7F5FF), Color(rgb: 0xEEF2FF), Color(rgb: 0xF5F4FF)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
    static var liveCta: LinearGradient {
        LinearGradient(colors: [liveAccent, livePink], startPoint: .leading, endPoint: .trailing)
    }
    static let liveBorder = Color(rgb: 0x7C3AED).opacity(0.2)
    static let liveSoft = Color(rgb: 0x7C3AED).opacity(0.08)
    static let livePill = Color.black.opacity(0.52)
}

enum CreateDraftKind { case photo, video, text }

struct CreateDraft: Identifiable {
    let id: String
    let kind: CreateDraftKind
    var localURL: URL?
    var text: String
    var durationSec: Int
    var lensId: Int
    var soundId: String?

    init(
        id: String = UUID().uuidString,
        kind: CreateDraftKind,
        localURL: URL? = nil,
        text: String = "",
        durationSec: Int = 0,
        lensId: Int = 0,
        soundId: String? = nil
    ) {
        self.id = id
        self.kind = kind
        self.localURL = localURL
        self.text = text
        self.durationSec = durationSec
        self.lensId = lensId
        self.soundId = soundId
    }
}

enum CreateUploadState { case queued, uploading, done, failed }

struct CreateUploadJob {
    let draft: CreateDraft
    var state: CreateUploadState = .queued
}

protocol CreateUploadTransport: AnyObject {
    func enqueue(_ job: CreateUploadJob)
}

/// In-memory queue until outbox + resumable upload land. Never blocks UI.
final class CreateSession: ObservableObject {
    static let shared = CreateSession()
    @Published private(set) var lastDraft: CreateDraft?
    private var queue: [CreateUploadJob] = []
    private var transport: CreateUploadTransport?

    func remember(_ draft: CreateDraft) {
        lastDraft = draft
    }

    func submit(_ draft: CreateDraft) {
        lastDraft = draft
        let job = CreateUploadJob(draft: draft)
        if let transport {
            transport.enqueue(job)
        } else {
            queue.append(job)
        }
    }

    func bind(transport: CreateUploadTransport) {
        self.transport = transport
    }

    var pendingCount: Int { queue.count }
}
