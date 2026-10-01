import CryptoKit
import Foundation
import Network
import UIKit

/// The posting outbox.
///
/// Submit a file and a form; everything after that is this object's problem.
/// It runs one upload at a time (so each one finishes as fast as the link
/// allows), sends parts in parallel with a checksum each, persists progress
/// after every part, and resumes from the server's bitmap after a kill. Once
/// the server has every byte it creates the post and this object waits — on
/// the shared live stream, not by polling — for the pipeline to finish.
///
/// Screens read `tasks`. They never see a socket, a file, or a retry.
@MainActor
final class UploadManager: ObservableObject {
    static let shared = UploadManager()

    /// Oldest first. Terminal tasks stay until dismissed (or briefly, for Posted).
    @Published private(set) var tasks: [UploadTask] = []

    private let store = UploadStore()
    private var started = false
    private var loop: Task<Void, Never>?
    private var running: Task<Void, Never>?
    private var runningId: String?
    private var watch: Task<Void, Never>?
    private var wake: CheckedContinuation<Void, Never>?
    private var wakePending = false
    private var background = UIBackgroundTaskIdentifier.invalid
    private let parallelism: Int
    private let monitor = NWPathMonitor()
    private var online = true

    private init() {
        switch DeviceClass.current() {
        case .low: parallelism = 1
        case .mid: parallelism = 2
        case .high: parallelism = 3
        }
    }

    // MARK: - Lifecycle

    func start() {
        guard !started else { return }
        started = true
        tasks = store.loadAll().compactMap { t in
            // A "Posted" strip nobody dismissed before the app died is stale.
            if t.state == .posted { store.delete(t); return nil }
            return t.normalizedForRelaunch()
        }
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.online = path.status == .satisfied
                if path.status == .satisfied { self?.kick() }
            }
        }
        monitor.start(queue: DispatchQueue.global(qos: .utility))
        LiveFeed.shared.listenPosts { [weak self] post in self?.onPostEvent(post) }
        loop = Task { [weak self] in await self?.runLoop() }
        ensureWatch()
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main
        ) { [weak self] _ in Task { @MainActor in self?.resume() } }
    }

    /// Called when the app returns to the foreground: pick up anything waiting.
    func resume() {
        kick()
        if tasks.contains(where: { $0.state == .processing }) {
            Task { await refreshProcessing() }
        }
    }

    /// Where the adopted source file lives, for thumbnails.
    func fileURL(_ task: UploadTask) -> URL { store.fileURL(task) }

    // MARK: - Commands

    /// Queues a post. Returns immediately; the strip shows progress.
    ///
    /// The source is adopted into the app's own directory first (a hard link when
    /// the volume allows, a copy otherwise) so a Photos temp URL that vanishes
    /// cannot strand a queued post. That copy happens off the main thread; the
    /// row appears as "Waiting…" meanwhile.
    func enqueue(_ info: MediaInfo, form: PostForm) {
        let id = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        let store = self.store
        let source = info.url
        Task.detached(priority: .userInitiated) { [weak self] in
            let file = store.adopt(source, id: id)
            await MainActor.run {
                guard let self else { return }
                guard let file else { return }
                let task = UploadTask(
                    id: id, idem: id, file: file, kind: info.kind, mime: info.mime, size: info.size,
                    width: info.width, height: info.height, durationMs: info.durationMs,
                    capturedAt: info.capturedAt, form: form, createdAt: Date().timeIntervalSince1970
                )
                self.tasks.append(task)
                store.save(task)
                self.kick()
            }
        }
    }

    func retry(_ id: String) {
        update(id) { $0.state = .queued; $0.error = ""; $0.errorCode = "" }
        kick()
    }

    /// Removes a finished strip.
    func dismiss(_ id: String) {
        guard let t = tasks.first(where: { $0.id == id }), t.state.isTerminal else { return }
        remove(t)
    }

    /// Stops an in-flight upload and forgets it.
    func cancel(_ id: String) {
        guard let t = tasks.first(where: { $0.id == id }) else { return }
        Task {
            if runningId == id, let r = running {
                r.cancel()
                await r.value
            }
            if t.sessionId != 0, t.state != .processing {
                await Xani.media.abort(t.sessionId)
            }
            remove(t)
            kick()
        }
    }

    // MARK: - Engine

    private func runLoop() async {
        while !Task.isCancelled {
            guard let next = tasks.first(where: { $0.state == .queued }) else {
                await sleepUntilKicked()
                continue
            }
            beginBackground()
            let job = Task { [weak self] in await self?.runTask(next) }
            running = job
            runningId = next.id
            await job.value
            running = nil
            runningId = nil
            if !tasks.contains(where: { !$0.state.isTerminal && $0.state != .processing }) {
                endBackground()
            }
        }
    }

    private func runTask(_ start: UploadTask) async {
        update(start.id) { $0.state = .uploading; $0.error = ""; $0.errorCode = "" }
        guard let session = await openSession(start) else { return }
        guard var task = current(start.id) else { return }
        if session.state == "done" {
            // Killed between the last part and the ack: the server finished for us.
            finishComplete(task, postId: session.postId, post: nil)
            return
        }
        update(task.id) {
            $0.sessionId = session.id
            $0.chunkSize = session.chunkSize
            $0.parts = session.parts
            $0.done = session.received
            $0.bytesSent = Self.bytesFor(size: $0.size, chunk: session.chunkSize, parts: session.parts, done: session.received)
        }
        guard let t2 = current(task.id) else { return }
        task = t2
        guard await uploadParts(task) else { return }
        guard let t3 = current(task.id) else { return }
        update(t3.id) { $0.state = .completing; $0.bytesSent = $0.size }
        await complete(t3)
    }

    /// Opens (or re-attaches to) the session; an aborted one gets a fresh key.
    private func openSession(_ task: UploadTask) async -> UploadSession? {
        var t = task
        var attempt = 0
        var rotated = false
        while !Task.isCancelled {
            let r = await Xani.media.open(UploadDeclaration(
                idem: t.idem, kind: t.kind, mime: t.mime, size: t.size, width: t.width, height: t.height,
                durationMs: t.durationMs, capturedAt: t.capturedAt
            ))
            if let s = r.value {
                if s.state == "aborted", !rotated {
                    rotated = true
                    guard let nt = rotateIdem(t.id) else { return nil }
                    t = nt
                    continue
                }
                return s
            }
            if r.isSessionExpired { park(t.id, r); return nil }
            if r.isTransport || r.statusCode >= 500 {
                if await !backoff(attempt) { fail(t.id, r); return nil }
                attempt += 1
                continue
            }
            fail(t.id, r)
            return nil
        }
        return nil
    }

    /// Sends every missing part; true when all landed.
    private func uploadParts(_ task: UploadTask) async -> Bool {
        let missing = (0..<task.parts).filter { !task.done.contains($0) }
        if missing.isEmpty { return true }
        let url = store.fileURL(task)
        let gate = Gate(parallelism)
        let failed = FirstFailure()

        await withTaskGroup(of: Void.self) { group in
            for n in missing {
                group.addTask { [weak self] in
                    await gate.wait()
                    defer { Task { await gate.signal() } }
                    if Task.isCancelled { return }
                    if await failed.get() != nil { return }
                    guard let self else { return }
                    let r = await self.sendPart(url: url, task: task, n: n, stop: { await failed.get() != nil })
                    if let r {
                        await failed.set(r)
                    } else {
                        await MainActor.run {
                            self.update(task.id) {
                                $0.done.insert(n)
                                $0.bytesSent = Self.bytesFor(size: $0.size, chunk: $0.chunkSize, parts: $0.parts, done: $0.done)
                            }
                        }
                    }
                }
            }
        }

        guard let f = await failed.get() else { return true }
        if Task.isCancelled || f.code == Self.stopped { return false }
        if f.isSessionExpired { park(task.id, f); return false }
        if f.code == "upload_closed" { return true } // another device (or a retry) already completed it
        if f.code == "not_found" || f.code == "upload_expired" {
            // Session vanished: start over with a new key, same file.
            _ = rotateIdem(task.id)
            kick()
            return false
        }
        fail(task.id, f)
        return false
    }

    /// One part: read the range, hash it, PUT it. Transport and 5xx failures back
    /// off and retry indefinitely (the user can cancel); a damaged part is
    /// re-sent three times; anything else is returned to the caller.
    private nonisolated func sendPart(
        url: URL, task: UploadTask, n: Int, stop: @escaping @Sendable () async -> Bool
    ) async -> ApiResponse<UploadSession>? {
        var attempt = 0
        var damaged = 0
        while !Task.isCancelled {
            if await stop() { return .failure(0, Self.stopped, "") }
            guard let bytes = Self.readPart(url: url, task: task, n: n) else {
                return .failure(0, "unreadable", "The file could not be read.")
            }
            let sha = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
            let r = await Xani.media.putPart(task.sessionId, n: n, bytes: bytes, sha256Hex: sha)
            if r.ok { return nil }
            if r.isTransport || r.statusCode >= 500 {
                if await !UploadManager.shared.backoff(attempt) { return r }
                attempt += 1
                continue
            }
            if r.code == "checksum_mismatch" || r.code == "invalid_checksum" {
                damaged += 1
                if damaged >= 3 { return r }
                continue
            }
            return r
        }
        return .failure(0, Self.stopped, "")
    }

    private nonisolated static func readPart(url: URL, task: UploadTask, n: Int) -> Data? {
        let offset = Int64(n) * Int64(task.chunkSize)
        let len = Int(min(Int64(task.chunkSize), task.size - offset))
        guard len > 0, let h = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? h.close() }
        do {
            try h.seek(toOffset: UInt64(offset))
            guard let d = try h.read(upToCount: len), d.count == len else { return nil }
            return d
        } catch {
            return nil
        }
    }

    /// Gives the task a new server session key and forgets any progress.
    private func rotateIdem(_ id: String) -> UploadTask? {
        update(id) {
            $0.idem = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
            $0.state = .queued
            $0.sessionId = 0
            $0.chunkSize = 0
            $0.parts = 0
            $0.done = []
            $0.bytesSent = 0
        }
    }

    private func complete(_ task: UploadTask) async {
        var attempt = 0
        while !Task.isCancelled {
            let r = await Xani.media.complete(task.sessionId, form: task.form)
            if let post = r.value {
                finishComplete(task, postId: post.id, post: post)
                // The grid reserves its "Posting…" tile from this same event.
                LiveFeed.shared.emitPost(post)
                return
            }
            if r.isSessionExpired { park(task.id, r); return }
            if r.isTransport || r.statusCode >= 500 {
                if await !backoff(attempt) { fail(task.id, r); return }
                attempt += 1
                continue
            }
            if r.code == "upload_incomplete" {
                // Server disagrees on parts: re-read its bitmap and resume.
                let s = await Xani.media.status(task.sessionId).value
                update(task.id) { $0.state = .queued; $0.done = s?.received ?? [] }
                kick()
                return
            }
            fail(task.id, r)
            return
        }
    }

    private func finishComplete(_ task: UploadTask, postId: Int64, post: PostData?) {
        if let post, post.isLive {
            update(task.id) {
                $0.state = .posted; $0.postId = postId; $0.bytesSent = $0.size; $0.thumbKey = post.thumbKey ?? ""
            }
            scheduleAutoDismiss(task.id)
        } else if let post, post.isFailed {
            update(task.id) {
                $0.state = .failed; $0.postId = postId
                $0.error = post.error.isEmpty ? "Processing failed." : post.error
                $0.errorCode = "processing_failed"
            }
        } else {
            update(task.id) { $0.state = .processing; $0.postId = postId; $0.bytesSent = $0.size }
            ensureWatch()
        }
    }

    // MARK: - Processing watch

    private func onPostEvent(_ post: PostData) {
        guard let t = tasks.first(where: { $0.postId == post.id && $0.state == .processing }) else { return }
        finishComplete(t, postId: post.id, post: post)
    }

    /// While anything is processing, hold the shared live stream so the server's
    /// "live" event arrives the moment the ladder is ready, and re-read once a
    /// minute as a safety net for a stream that could not connect.
    private func ensureWatch() {
        if let w = watch, !w.isCancelled { return }
        guard tasks.contains(where: { $0.state == .processing }) else { return }
        watch = Task { [weak self] in
            let hold = Task { await LiveFeed.shared.hold() }
            defer { hold.cancel() }
            while !Task.isCancelled {
                guard let self, self.tasks.contains(where: { $0.state == .processing }) else { break }
                try? await Task.sleep(nanoseconds: 45_000_000_000)
                await self.refreshProcessing()
            }
            self?.watch = nil
        }
    }

    private func refreshProcessing() async {
        for t in tasks where t.state == .processing && t.postId != 0 {
            if let p = await Xani.media.post(t.postId).value {
                finishComplete(t, postId: p.id, post: p)
                if p.isLive || p.isFailed { LiveFeed.shared.emitPost(p) }
            }
        }
    }

    private func scheduleAutoDismiss(_ id: String) {
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Self.postedLinger * 1_000_000_000))
            self?.dismiss(id)
        }
    }

    // MARK: - Helpers

    private func current(_ id: String) -> UploadTask? { tasks.first { $0.id == id } }

    @discardableResult
    private func update(_ id: String, _ f: (inout UploadTask) -> Void) -> UploadTask? {
        guard let i = tasks.firstIndex(where: { $0.id == id }) else { return nil }
        var t = tasks[i]
        f(&t)
        tasks[i] = t
        store.save(t)
        return t
    }

    private func remove(_ t: UploadTask) {
        tasks.removeAll { $0.id == t.id }
        store.delete(t)
        if !tasks.contains(where: { !$0.state.isTerminal }) { endBackground() }
    }

    private func fail(_ id: String, _ r: ApiResponse<some Any>) {
        update(id) { $0.state = .failed; $0.error = r.message; $0.errorCode = r.code }
    }

    /// Session over: keep the task, stop working; `resume` after sign-in picks it up.
    private func park(_ id: String, _ r: ApiResponse<some Any>) {
        update(id) { $0.state = .queued; $0.error = r.message; $0.errorCode = r.code }
    }

    /// Jittered exponential backoff that also waits for a network to exist.
    /// Returns false after too many consecutive server failures (transport
    /// failures never give up: the file is on the device and the user can cancel).
    fileprivate func backoff(_ attempt: Int) async -> Bool {
        if attempt >= Self.maxServerAttempts, online { return false }
        let base = min(Self.minBackoff * Double(1 << min(attempt, 6)), Self.maxBackoff)
        try? await Task.sleep(nanoseconds: UInt64((base + Double.random(in: 0...0.5)) * 1_000_000_000))
        while !online, !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
        }
        return true
    }

    private func kick() {
        if let w = wake {
            wake = nil
            w.resume()
        } else {
            wakePending = true
        }
    }

    private func sleepUntilKicked() async {
        if wakePending {
            wakePending = false
            return
        }
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in wake = c }
    }

    private func beginBackground() {
        guard background == .invalid else { return }
        background = UIApplication.shared.beginBackgroundTask(withName: "xani.upload") { [weak self] in
            Task { @MainActor in self?.endBackground() }
        }
    }

    private func endBackground() {
        guard background != .invalid else { return }
        UIApplication.shared.endBackgroundTask(background)
        background = .invalid
    }

    private static func bytesFor(size: Int64, chunk: Int, parts: Int, done: Set<Int>) -> Int64 {
        guard chunk > 0, parts > 0 else { return 0 }
        var total: Int64 = 0
        for n in done {
            total += n == parts - 1 ? size - Int64(chunk) * Int64(parts - 1) : Int64(chunk)
        }
        return max(0, min(size, total))
    }

    private static let minBackoff: Double = 1
    private static let maxBackoff: Double = 60
    private static let maxServerAttempts = 8
    private static let postedLinger: Double = 4.5
    private static let stopped = "stopped"
}

/// Counting semaphore for structured concurrency.
private actor Gate {
    private var permits: Int
    private var waiters: [CheckedContinuation<Void, Never>] = []

    init(_ n: Int) { permits = max(1, n) }

    func wait() async {
        if permits > 0 {
            permits -= 1
            return
        }
        await withCheckedContinuation { waiters.append($0) }
    }

    func signal() {
        if let w = waiters.first {
            waiters.removeFirst()
            w.resume()
        } else {
            permits += 1
        }
    }
}

/// First hard failure wins; later workers see it and stop.
private actor FirstFailure {
    private var value: ApiResponse<UploadSession>?
    func get() -> ApiResponse<UploadSession>? { value }
    func set(_ r: ApiResponse<UploadSession>) { if value == nil { value = r } }
}
