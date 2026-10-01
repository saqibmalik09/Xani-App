import Foundation

/// Where a task is in its life. A task only moves forward.
enum UploadState: String, Codable {
    /// Waiting for its turn (one upload at a time keeps each one fast).
    case queued
    /// Parts in flight. `bytesSent` moves.
    case uploading
    /// Every part landed; the complete call is in flight.
    case completing
    /// Server has the post in `processing`; the encode ladder is running.
    case processing
    /// Post is live. Strip shows "Posted" then dismisses itself.
    case posted
    /// Gave up. Strip offers retry.
    case failed

    var isTerminal: Bool { self == .posted || self == .failed }
    var isActive: Bool { self == .uploading || self == .completing }
}

/// One post in flight, persisted so a killed app finishes it on relaunch.
///
/// Everything the resume needs is here: the source path, the declared facts,
/// the form, the server session, and which parts already landed. Bytes are
/// never copied — the source is re-read by range on resume.
struct UploadTask: Codable, Equatable, Identifiable {
    let id: String
    /// Idempotency key for the server session; rotated only if the session is lost.
    var idem: String
    /// Path under the app's `uploads/` directory (the file is copied there so a
    /// Photos temp URL that vanishes cannot strand a queued post).
    let file: String
    let kind: String
    let mime: String
    let size: Int64
    let width: Int
    let height: Int
    let durationMs: Int
    let capturedAt: String?
    let form: PostForm
    let createdAt: TimeInterval
    var state: UploadState = .queued
    var sessionId: Int64 = 0
    var chunkSize: Int = 0
    var parts: Int = 0
    var done: Set<Int> = []
    var bytesSent: Int64 = 0
    var postId: Int64 = 0
    var error: String = ""
    var errorCode: String = ""
    var thumbKey: String = ""

    var isVideo: Bool { kind == "video" }

    /// 0..1 across upload; processing holds at 1 until the post is live.
    var progress: Double {
        switch state {
        case .queued: return 0
        case .uploading: return size > 0 ? min(0.98, Double(bytesSent) / Double(size)) : 0
        default: return 1
        }
    }

    /// An interrupted upload resumes as queued; an interrupted complete is re-sent
    /// (idempotent on the server).
    func normalizedForRelaunch() -> UploadTask {
        var t = self
        if t.state.isActive { t.state = .queued }
        return t
    }
}

/// Task files on disk: `Library/Application Support/uploads/<id>.json` beside
/// the copied source. Atomic writes; one file per task so a crash mid-write
/// loses one record, never the queue.
final class UploadStore: @unchecked Sendable {
    let root: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        root = base.appendingPathComponent("uploads", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        // Not backed up: the source file is large and the post will exist on the server.
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var r = root
        try? r.setResourceValues(values)
    }

    func fileURL(_ task: UploadTask) -> URL { root.appendingPathComponent(task.file) }

    func loadAll() -> [UploadTask] {
        guard let names = try? FileManager.default.contentsOfDirectory(atPath: root.path) else { return [] }
        var out: [UploadTask] = []
        for n in names where n.hasSuffix(".json") {
            if let data = try? Data(contentsOf: root.appendingPathComponent(n)),
               let t = try? decoder.decode(UploadTask.self, from: data) {
                out.append(t)
            }
        }
        return out.sorted { $0.createdAt < $1.createdAt }
    }

    func save(_ task: UploadTask) {
        guard let data = try? encoder.encode(task) else { return }
        try? data.write(to: root.appendingPathComponent("\(task.id).json"), options: .atomic)
    }

    func delete(_ task: UploadTask) {
        try? FileManager.default.removeItem(at: root.appendingPathComponent("\(task.id).json"))
        try? FileManager.default.removeItem(at: fileURL(task))
    }

    /// Copies (or hard-links, when possible) the source into the store so it
    /// outlives a Photos export temp directory. Returns the relative file name.
    func adopt(_ source: URL, id: String) -> String? {
        let name = "\(id).\(source.pathExtension.isEmpty ? "bin" : source.pathExtension)"
        let dest = root.appendingPathComponent(name)
        try? FileManager.default.removeItem(at: dest)
        if (try? FileManager.default.linkItem(at: source, to: dest)) != nil { return name }
        if (try? FileManager.default.copyItem(at: source, to: dest)) != nil { return name }
        return nil
    }
}
