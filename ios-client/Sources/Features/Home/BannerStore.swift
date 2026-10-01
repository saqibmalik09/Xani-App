import Foundation
import SwiftUI

/// Live-browse promo banners (under All / Video / Audio + Country).
///
/// Cache-then-network: the last good list paints immediately; a background refresh
/// runs when older than a minute. Images are warmed into `ImageLoader` so the
/// carousel never blocks the main thread on decode.
@MainActor
final class BannerStore: ObservableObject {
    static let shared = BannerStore()
    static let placementLiveBrowse = "live_browse"

    @Published private(set) var items: [BannerData] = []
    private var loadedAt: TimeInterval = 0
    private var failedAt: TimeInterval = 0
    private var loading: Task<[BannerData], Never>?

    private init() {}

    /// Absolute media URL for a banner image key.
    func imageURL(_ key: String) -> URL? {
        if key.hasPrefix("http://") || key.hasPrefix("https://") { return URL(string: key) }
        return MediaConfig.shared.url(key)
    }

    @discardableResult
    func load(placement: String = BannerStore.placementLiveBrowse) async -> [BannerData] {
        let now = Date().timeIntervalSince1970
        if !items.isEmpty, now - loadedAt < 60 { return items }
        if items.isEmpty, now - failedAt < 5 { return items }
        if let running = loading { return await running.value }
        let task = Task<[BannerData], Never> { [weak self] in
            let r = await Xani.banners.list(placement: placement)
            guard let self else { return [] }
            if r.ok, let data = r.value {
                self.loadedAt = Date().timeIntervalSince1970
                self.items = data
                for b in data {
                    if let u = self.imageURL(b.imageKey) { ImageLoader.shared.prefetch(u) }
                }
                return data
            }
            self.failedAt = Date().timeIntervalSince1970
            return self.items
        }
        loading = task
        let out = await task.value
        loading = nil
        return out
    }
}
