import ImageIO
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The profile picture: its own store, its own endpoint, its own storage
/// namespace.
///
/// A picture is not a post. It never enters the upload pipeline, never becomes
/// an asset, and never appears in a feed or a grid — so none of that machinery
/// is involved here, and a failed avatar cannot leave a half-published post
/// behind.
///
/// How it feels instant: the picked image is cropped to a square off the main
/// thread and shown immediately as `preview`, while the bytes go up. The
/// preview is only dropped once the stored picture is decoded and cached, so
/// the face on screen never flickers back to the old one in between. Every
/// other surface — feed cards, comment rows, the other devices on this account
/// — updates from the profile event the server publishes with the write.
@MainActor
final class AvatarStore: ObservableObject {
    static let shared = AvatarStore()

    /// Uploaded edge length. Matches what the server stores, so the picture the
    /// user approves is the picture that is kept: no second resample, and the
    /// upload is ~60 KB instead of the several MB a camera still would cost.
    static let edge: CGFloat = 512

    /// Quality for the upload. The server re-encodes, so this only has to be
    /// good enough to survive that once.
    private static let quality: CGFloat = 0.9

    /// The square the user just picked, shown while the upload is in flight.
    /// Nil when idle — every surface then reads the stored picture.
    @Published private(set) var preview: UIImage?
    @Published private(set) var uploading = false
    /// Set when the last attempt failed, so the sheet can say why.
    @Published private(set) var error = ""

    private var task: Task<Void, Never>?

    private init() {}

    func signedOut() {
        task?.cancel()
        task = nil
        preview = nil
        uploading = false
        error = ""
    }

    /// Dismisses a failure the user has read.
    func clearError() { error = "" }

    /// Sets `url` — a camera capture or a gallery pick — as the profile picture.
    ///
    /// A second call replaces the first: the user re-picking is them changing
    /// their mind, not a queue, and the server names each upload by its own
    /// revision so the last one to land is the one that sticks.
    func upload(_ url: URL) {
        task?.cancel()
        preview = nil
        error = ""
        uploading = true
        task = Task { [weak self] in
            guard let square = await Self.square(url) else {
                self?.fail(Self.readFailed)
                return
            }
            guard !Task.isCancelled else { return }
            // On screen before the request leaves: the user sees their new face
            // while the bytes are still going up.
            self?.preview = square

            guard let jpeg = square.jpegData(compressionQuality: Self.quality) else {
                self?.fail(Self.readFailed)
                return
            }
            let response = await Xani.profile.setAvatar(jpeg)
            guard !Task.isCancelled else { return }
            guard let profile = response.value else {
                self?.fail(response.isSessionExpired ? "" : response.message)
                return
            }
            // The stored picture is warmed before the preview is dropped, so the
            // swap is one frame with no gap and no flash of the old avatar.
            if let stored = MediaConfig.shared.url(profile.avatarUrl) {
                _ = await ImageLoader.shared.load(stored)
            }
            self?.preview = nil
            self?.uploading = false
        }
    }

    private func fail(_ message: String) {
        preview = nil
        uploading = false
        error = message
    }

    private static let readFailed = "That picture could not be read. Try another."

    /// Decodes `url` to an upright square of `edge` points.
    ///
    /// `CGImageSourceCreateThumbnailAtIndex` does the work the naive path gets
    /// wrong: it samples while decoding, so a 50-megapixel still never exists
    /// in memory at full size, and it applies the EXIF transform, so a portrait
    /// selfie is not stored on its side. The crop is centred rather than a
    /// squash, because a squashed face is worse than a tighter one.
    nonisolated private static func square(_ url: URL) async -> UIImage? {
        await Task.detached(priority: .userInitiated) { () -> UIImage? in
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
            // Two edges' worth of headroom: the crop throws away one dimension,
            // so decoding at exactly `edge` would leave the square soft.
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
                kCGImageSourceThumbnailMaxPixelSize: Int(edge) * 2,
            ]
            guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
            let decoded = UIImage(cgImage: cg)
            let side = min(decoded.size.width, decoded.size.height)
            guard side > 0 else { return nil }

            let format = UIGraphicsImageRendererFormat.default()
            format.scale = 1
            format.opaque = true
            return UIGraphicsImageRenderer(size: CGSize(width: edge, height: edge), format: format).image { _ in
                let scale = edge / side
                let size = CGSize(width: decoded.size.width * scale, height: decoded.size.height * scale)
                decoded.draw(in: CGRect(
                    x: (edge - size.width) / 2,
                    y: (edge - size.height) / 2,
                    width: size.width,
                    height: size.height
                ))
            }
        }.value
    }
}
