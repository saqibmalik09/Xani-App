import SwiftUI
import UIKit

/// Remote stills for feed posters, grid thumbs and avatars.
///
/// Three tiers keyed by URL: a decoded-image memory cache sized to the device,
/// `URLCache` on disk (media keys are content-addressed, so a cached file is
/// never stale), then the network. Concurrent requests for one URL share a
/// single download. Decoding happens off the main thread; only the paint is on it.
final class ImageLoader {
    static let shared = ImageLoader()

    private let mem = NSCache<NSURL, UIImage>()
    private let session: URLSession
    private var inflight: [URL: Task<UIImage?, Never>] = [:]
    private let lock = NSLock()

    private init() {
        mem.totalCostLimit = Int(min(ProcessInfo.processInfo.physicalMemory / 16, 160 << 20))
        let cfg = URLSessionConfiguration.default
        cfg.urlCache = URLCache(memoryCapacity: 8 << 20, diskCapacity: 256 << 20, diskPath: "xani-img")
        cfg.requestCachePolicy = .returnCacheDataElseLoad
        cfg.timeoutIntervalForRequest = 15
        cfg.httpMaximumConnectionsPerHost = 6
        session = URLSession(configuration: cfg)
    }

    func cached(_ url: URL) -> UIImage? { mem.object(forKey: url as NSURL) }

    /// Warms memory + disk for a URL the user is about to see.
    func prefetch(_ url: URL) {
        if cached(url) != nil { return }
        Task.detached(priority: .utility) { _ = await self.load(url) }
    }

    func load(_ url: URL) async -> UIImage? {
        if let hit = cached(url) { return hit }
        lock.lock()
        if let running = inflight[url] {
            lock.unlock()
            return await running.value
        }
        let task = Task<UIImage?, Never>(priority: .userInitiated) { [session, mem] in
            guard let (data, resp) = try? await session.data(from: url),
                  (resp as? HTTPURLResponse).map({ (200 ..< 300).contains($0.statusCode) }) ?? true,
                  let raw = UIImage(data: data) else { return nil }
            // Decode off the main thread so the first paint is a blit, not a decode.
            let img = await raw.byPreparingForDisplay() ?? raw
            mem.setObject(img, forKey: url as NSURL, cost: Int(img.size.width * img.size.height * img.scale * img.scale * 4))
            return img
        }
        inflight[url] = task
        lock.unlock()
        let img = await task.value
        lock.lock()
        inflight[url] = nil
        lock.unlock()
        return img
    }
}

/// Paints a remote still, or `fallback` until it arrives. A memory hit paints on
/// the first frame with no state change.
struct RemoteImage: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    var fallback: Color = Color(rgb: 0x1A1A1E)

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                fallback
            }
        }
        .task(id: url) {
            guard let url else { image = nil; return }
            if let hit = ImageLoader.shared.cached(url) { image = hit; return }
            let loaded = await ImageLoader.shared.load(url)
            if !Task.isCancelled { image = loaded }
        }
    }
}
