import AVFoundation
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// What the device knows about a file before a byte is uploaded.
struct MediaInfo: Equatable, Sendable {
    let url: URL
    let kind: String // video | image
    let mime: String
    let size: Int64
    let width: Int // display orientation applied
    let height: Int
    let durationMs: Int
    let capturedAt: String? // RFC3339 UTC or nil

    var isVideo: Bool { kind == "video" }
    var aspect: Double { height == 0 ? 0 : Double(width) / Double(height) }

    /// True for the feed's native 9:16 (±3%).
    var isPortrait916: Bool { (0.545...0.58).contains(aspect) }
}

/// Why a file cannot be posted. `nil` means it can.
enum MediaProblem { case unreadable, unsupported, tooLong, tooLarge, badAspect }

/// Reads container metadata off the main thread. Nothing here decodes a frame:
/// video facts come from the container index, image bounds from the header,
/// dates from EXIF or the container's creation tag.
enum MediaProbe {
    private static let videoMimes: Set<String> = ["video/mp4", "video/quicktime", "video/webm", "video/3gpp", "video/x-matroska"]
    private static let imageMimes: Set<String> = ["image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"]

    static func probe(_ url: URL) async -> MediaInfo? {
        let mime = mimeOf(url)
        let size = sizeOf(url)
        if mime.hasPrefix("video/") { return await probeVideo(url, mime: mime, size: size) }
        if mime.hasPrefix("image/") {
            return await Task.detached(priority: .userInitiated) { probeImage(url, mime: mime, size: size) }.value
        }
        return nil
    }

    /// Applies the server's rules locally so a 12-minute clip is refused instantly.
    @MainActor
    static func check(_ info: MediaInfo) -> MediaProblem? {
        if info.size <= 0 || info.width <= 0 || info.height <= 0 { return .unreadable }
        let cfg = MediaConfig.shared
        if info.isVideo {
            if !videoMimes.contains(info.mime) { return .unsupported }
            if info.durationMs > cfg.maxVideoSeconds * 1000 + 500 { return .tooLong }
            if info.size > cfg.maxVideoBytes { return .tooLarge }
        } else {
            if !imageMimes.contains(info.mime) { return .unsupported }
            if info.size > cfg.maxImageBytes { return .tooLarge }
        }
        let ratio = info.aspect
        if ratio < 1.0 / 2.6 || ratio > 2.6 { return .badAspect }
        return nil
    }

    private static func probeVideo(_ url: URL, mime: String, size: Int64) async -> MediaInfo? {
        let asset = AVURLAsset(url: url)
        let duration: CMTime
        let tracks: [AVAssetTrack]
        let creation: AVMetadataItem?
        do {
            duration = try await asset.load(.duration)
            tracks = try await asset.loadTracks(withMediaType: .video)
            creation = try await asset.load(.creationDate)
        } catch {
            return nil
        }
        guard let track = tracks.first else { return nil }
        let natural = (try? await track.load(.naturalSize)) ?? .zero
        let transform = (try? await track.load(.preferredTransform)) ?? .identity
        let rotated = natural.applying(transform)
        let w = Int(abs(rotated.width).rounded())
        let h = Int(abs(rotated.height).rounded())
        let ms = duration.isNumeric ? Int((duration.seconds * 1000).rounded()) : 0
        var captured: String?
        if let date = creation?.dateValue { captured = rfc3339.string(from: date) }
        else if let s = creation?.stringValue, let d = ISO8601DateFormatter().date(from: s) { captured = rfc3339.string(from: d) }
        return MediaInfo(url: url, kind: "video", mime: mime, size: size, width: w, height: h, durationMs: ms, capturedAt: captured)
    }

    private static func probeImage(_ url: URL, mime: String, size: Int64) -> MediaInfo? {
        guard let src = CGImageSourceCreateWithURL(url as CFURL, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any]
        else { return nil }
        var w = (props[kCGImagePropertyPixelWidth] as? Int) ?? 0
        var h = (props[kCGImagePropertyPixelHeight] as? Int) ?? 0
        if w <= 0 || h <= 0 { return nil }
        let orientation = (props[kCGImagePropertyOrientation] as? UInt32) ?? 1
        if orientation >= 5 { swap(&w, &h) } // EXIF 5–8 are transposed
        var captured: String?
        if let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any] {
            let raw = (exif[kCGImagePropertyExifDateTimeOriginal] as? String) ?? (exif[kCGImagePropertyExifDateTimeDigitized] as? String)
            captured = parseExif(raw)
        }
        return MediaInfo(url: url, kind: "image", mime: mime, size: size, width: w, height: h, durationMs: 0, capturedAt: captured)
    }

    private static func mimeOf(_ url: URL) -> String {
        if let type = UTType(filenameExtension: url.pathExtension.lowercased()), let mime = type.preferredMIMEType {
            return mime.lowercased()
        }
        switch url.pathExtension.lowercased() {
        case "mp4", "m4v": return "video/mp4"
        case "mov": return "video/quicktime"
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "heic": return "image/heic"
        case "webp": return "image/webp"
        default: return "application/octet-stream"
        }
    }

    private static func sizeOf(_ url: URL) -> Int64 {
        let attrs = try? FileManager.default.attributesOfItem(atPath: url.path)
        return (attrs?[.size] as? NSNumber)?.int64Value ?? 0
    }

    private static let rfc3339: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        return f
    }()

    /// EXIF dates are local time "yyyy:MM:dd HH:mm:ss" with no zone.
    private static func parseExif(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return f.date(from: raw).map(rfc3339.string(from:))
    }
}
