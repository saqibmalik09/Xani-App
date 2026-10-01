import CryptoKit
import Foundation

/// Immutable binary assets (gift animations, gift sounds) on disk, by URL.
///
/// Nothing lives in the heap here: callers get a file URL and stream from it.
/// Keys are content-addressed on the server so a file never goes stale; the
/// directory is trimmed to a byte budget by last use. One download per URL at a
/// time, at most three at once, and every download lands atomically — a killed
/// app never leaves a half file that decodes as garbage next launch.
final class AssetCache {
    static let shared = AssetCache()

    private let dir: URL
    private let session: URLSession
    private var inflight: [String: Task<URL?, Never>] = [:]
    private let lock = NSLock()
    private let gate = AsyncSemaphore(3)
    private var lastTrim: TimeInterval = 0

    private static let maxBytes: Int64 = 256 << 20
    private static let maxFile: Int64 = 48 << 20
    private static let trimEvery: TimeInterval = 600

    private init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first ?? FileManager.default.temporaryDirectory
        dir = base.appendingPathComponent("assets", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let cfg = URLSessionConfiguration.default
        cfg.requestCachePolicy = .reloadIgnoringLocalCacheData
        cfg.timeoutIntervalForRequest = 30
        cfg.timeoutIntervalForResource = 120
        session = URLSession(configuration: cfg)
    }

    /// The cached file if it is already on disk; never touches the network.
    func cached(_ url: String) -> URL? {
        guard !url.isEmpty else { return nil }
        let f = file(for: url)
        let size = (try? FileManager.default.attributesOfItem(atPath: f.path)[.size] as? Int64) ?? 0
        return size > 0 ? f : nil
    }

    /// Warms disk for a URL the room is about to need.
    func prefetch(_ url: String) {
        guard !url.isEmpty, cached(url) == nil else { return }
        Task.detached(priority: .utility) { _ = await self.load(url) }
    }

    /// The file for `url`, downloading once if needed. Nil on a failed fetch.
    func load(_ url: String) async -> URL? {
        guard !url.isEmpty else { return nil }
        if let f = cached(url) { touch(f); return f }
        lock.lock()
        if let running = inflight[url] {
            lock.unlock()
            return await running.value
        }
        let task = Task<URL?, Never>(priority: .userInitiated) { [self] in
            await gate.wait()
            defer { gate.signal() }
            return await fetch(url)
        }
        inflight[url] = task
        lock.unlock()
        let out = await task.value
        lock.lock(); inflight[url] = nil; lock.unlock()
        maybeTrim()
        return out
    }

    private func fetch(_ urlString: String) async -> URL? {
        guard let remote = URL(string: urlString) else { return nil }
        let target = file(for: urlString)
        if cached(urlString) != nil { return target }
        guard let (tmp, resp) = try? await session.download(from: remote),
              let http = resp as? HTTPURLResponse, http.statusCode == 200 else { return nil }
        let size = (try? FileManager.default.attributesOfItem(atPath: tmp.path)[.size] as? Int64) ?? 0
        guard size > 0, size <= Self.maxFile else { try? FileManager.default.removeItem(at: tmp); return nil }
        if http.expectedContentLength > 0, http.expectedContentLength != size {
            try? FileManager.default.removeItem(at: tmp); return nil
        }
        do {
            try? FileManager.default.removeItem(at: target)
            try FileManager.default.moveItem(at: tmp, to: target)
            return target
        } catch {
            try? FileManager.default.removeItem(at: tmp)
            return nil
        }
    }

    private func touch(_ f: URL) {
        let mtime = (try? FileManager.default.attributesOfItem(atPath: f.path)[.modificationDate] as? Date) ?? .distantPast
        if Date().timeIntervalSince(mtime) > 3600 {
            try? FileManager.default.setAttributes([.modificationDate: Date()], ofItemAtPath: f.path)
        }
    }

    /// LRU trim by mtime, at most every ten minutes, off the caller's path.
    private func maybeTrim() {
        let now = Date().timeIntervalSince1970
        guard now - lastTrim > Self.trimEvery else { return }
        lastTrim = now
        let dir = self.dir
        Task.detached(priority: .background) {
            let fm = FileManager.default
            guard let names = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.fileSizeKey, .contentModificationDateKey]) else { return }
            var entries: [(URL, Int64, Date)] = []
            var total: Int64 = 0
            for u in names {
                let v = try? u.resourceValues(forKeys: [.fileSizeKey, .contentModificationDateKey])
                let size = Int64(v?.fileSize ?? 0)
                total += size
                entries.append((u, size, v?.contentModificationDate ?? .distantPast))
            }
            guard total > Self.maxBytes else { return }
            for (u, size, _) in entries.sorted(by: { $0.2 < $1.2 }) {
                if total <= Self.maxBytes * 3 / 4 { break }
                try? fm.removeItem(at: u)
                total -= size
            }
        }
    }

    private func file(for url: String) -> URL {
        let digest = Insecure.SHA1.hash(data: Data(url.utf8)).map { String(format: "%02x", $0) }.joined()
        return dir.appendingPathComponent(digest)
    }
}

/// A counting semaphore for async code: no thread is parked while waiting.
final class AsyncSemaphore {
    private var permits: Int
    private var waiters: [CheckedContinuation<Void, Never>] = []
    private let lock = NSLock()

    init(_ permits: Int) { self.permits = permits }

    func wait() async {
        lock.lock()
        if permits > 0 { permits -= 1; lock.unlock(); return }
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
            waiters.append(c)
            lock.unlock()
        }
    }

    func signal() {
        lock.lock()
        if waiters.isEmpty { permits += 1; lock.unlock(); return }
        let c = waiters.removeFirst()
        lock.unlock()
        c.resume()
    }
}
