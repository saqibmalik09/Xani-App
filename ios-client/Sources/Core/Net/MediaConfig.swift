import Foundation

/// Nonisolated mirror of the media base for loaders that run off the main
/// actor (the SVGA and sound caches). The server hands catalog media as bare
/// storage keys; a loader that downloads the raw key 404s silently and the
/// gift freezes on its still. Written only from `MediaConfig`.
enum MediaBase {
    private static let lock = NSLock()
    private static var value = ""

    static func set(_ v: String) {
        lock.lock()
        value = v
        lock.unlock()
    }

    /// Absolute form of a possibly-bare storage key; untouched when already
    /// absolute or when no base is known yet.
    static func resolve(_ url: String) -> String {
        if url.isEmpty || url.hasPrefix("http") { return url }
        lock.lock()
        let base = value
        lock.unlock()
        guard !base.isEmpty else { return url }
        return "\(base)/\(url)"
    }
}

/// The media half of `/v1/config`: where relative keys resolve and what the
/// server will accept. Cached in `UserDefaults` so the first frame after a cold
/// start can resolve a thumbnail without a request; refreshed in the background.
@MainActor
final class MediaConfig {
    static let shared = MediaConfig()

    private(set) var baseURL = ""
    private(set) var maxVideoSeconds = 600
    // Fallbacks only, for the first launch before /v1/config answers. Kept equal to
    // the server's defaults so a file the server would take is never refused here.
    private(set) var maxVideoBytes: Int64 = 500 << 20
    private(set) var maxImageBytes: Int64 = 500 << 20
    private(set) var maxCaption = 2200

    private let defaults = UserDefaults.standard
    private let keyBase = "xani.media.base"
    private let keyVideoSec = "xani.media.video_sec"
    private let keyVideoMb = "xani.media.video_mb"
    private let keyImageMb = "xani.media.image_mb"
    private let keyCaption = "xani.media.caption"
    private let keyAt = "xani.media.at"
    private let refreshEvery: TimeInterval = 6 * 3600
    private var refreshing: Task<Bool, Never>?

    private init() {
        baseURL = defaults.string(forKey: keyBase) ?? ""
        MediaBase.set(baseURL)
        let sec = defaults.integer(forKey: keyVideoSec)
        if sec > 0 { maxVideoSeconds = sec }
        let vmb = defaults.integer(forKey: keyVideoMb)
        if vmb > 0 { maxVideoBytes = Int64(vmb) << 20 }
        let imb = defaults.integer(forKey: keyImageMb)
        if imb > 0 { maxImageBytes = Int64(imb) << 20 }
        let cap = defaults.integer(forKey: keyCaption)
        if cap > 0 { maxCaption = cap }
    }

    /// Kicks a background refresh when the cache is missing or stale.
    func start() {
        let at = defaults.double(forKey: keyAt)
        if baseURL.isEmpty || Date().timeIntervalSince1970 - at > refreshEvery {
            Task { _ = await refresh() }
        }
    }

    /// Absolute URL for a relative key, or nil when either half is missing.
    func url(_ key: String?) -> URL? {
        guard let key, !key.isEmpty, !baseURL.isEmpty else { return nil }
        return URL(string: "\(baseURL)/\(key)")
    }

    /// Re-reads config. Concurrent callers share one request.
    func refresh() async -> Bool {
        if let running = refreshing { return await running.value }
        let task = Task<Bool, Never> { [weak self] in
            guard let cfg = await Xani.auth.config().value else { return false }
            self?.remember(cfg)
            return true
        }
        refreshing = task
        let ok = await task.value
        refreshing = nil
        return ok
    }

    func remember(_ cfg: AppConfig) {
        if !cfg.mediaBaseUrl.isEmpty { baseURL = cfg.mediaBaseUrl }
        MediaBase.set(baseURL)
        maxVideoSeconds = cfg.maxVideoSeconds
        maxVideoBytes = Int64(cfg.maxVideoMb) << 20
        maxImageBytes = Int64(cfg.maxImageMb) << 20
        maxCaption = cfg.maxCaption
        defaults.set(baseURL, forKey: keyBase)
        defaults.set(cfg.maxVideoSeconds, forKey: keyVideoSec)
        defaults.set(cfg.maxVideoMb, forKey: keyVideoMb)
        defaults.set(cfg.maxImageMb, forKey: keyImageMb)
        defaults.set(cfg.maxCaption, forKey: keyCaption)
        defaults.set(Date().timeIntervalSince1970, forKey: keyAt)
    }
}
