import Compression
import Foundation
import UIKit

/// SVGA 2.0 — the gift animation format (zlib-compressed protobuf: sprite PNGs
/// plus per-frame affine transforms). Decoded with a small wire reader, so the
/// app carries no protobuf runtime for it.
///
/// Memory is the whole design:
///   - Sprites are decoded once per movie, downsampled so the widest is never
///     wider than the screen, and the movie's pixel bytes are kept under a
///     per-movie budget by raising the sample size until they fit.
///   - Frames are flat float arrays (nine per sprite per frame), not objects.
///   - Decoded movies sit in one `NSCache` costed in bytes and sized to the
///     device. Playing a gift twice costs nothing the second time.
///   - Decoding never touches the main thread and runs at most two at a time.
final class SvgaMovie {
    let width: CGFloat
    let height: CGFloat
    let fps: Int
    let frames: Int
    let sprites: [SvgaSprite]
    let images: [String: CGImage]
    let byteCount: Int

    init(width: CGFloat, height: CGFloat, fps: Int, frames: Int, sprites: [SvgaSprite], images: [String: CGImage], byteCount: Int) {
        self.width = width; self.height = height; self.fps = fps; self.frames = frames
        self.sprites = sprites; self.images = images; self.byteCount = byteCount
    }

    var durationMs: Int { fps <= 0 ? 0 : frames * 1000 / fps }
}

/// One sprite: [alpha, a, b, c, d, tx, ty, w, h] × frames, packed flat.
struct SvgaSprite {
    static let stride = 9
    let imageKey: String
    let frames: Int
    let data: [Float]

    @inline(__always) func alpha(_ f: Int) -> Float { data[f * Self.stride] }
    @inline(__always) func a(_ f: Int) -> Float { data[f * Self.stride + 1] }
    @inline(__always) func b(_ f: Int) -> Float { data[f * Self.stride + 2] }
    @inline(__always) func c(_ f: Int) -> Float { data[f * Self.stride + 3] }
    @inline(__always) func d(_ f: Int) -> Float { data[f * Self.stride + 4] }
    @inline(__always) func tx(_ f: Int) -> Float { data[f * Self.stride + 5] }
    @inline(__always) func ty(_ f: Int) -> Float { data[f * Self.stride + 6] }
    @inline(__always) func w(_ f: Int) -> Float { data[f * Self.stride + 7] }
    @inline(__always) func h(_ f: Int) -> Float { data[f * Self.stride + 8] }
}

final class SvgaLoader {
    static let shared = SvgaLoader()

    private let cache = NSCache<NSString, SvgaMovie>()
    private var inflight: [String: Task<(SvgaMovie?, Bool), Never>] = [:]
    private var failed: [String: TimeInterval] = [:]
    private let lock = NSLock()
    private let gate = AsyncSemaphore(2)
    /// Set once from the overlay; sprites are never decoded finer than this.
    var screenWidthPx: CGFloat = 1170

    private init() {
        // A sixth of physical memory, 24–128 MB: room for ~6 big gifts.
        let budget = Int(min(max(ProcessInfo.processInfo.physicalMemory / 24, 24 << 20), 128 << 20))
        cache.totalCostLimit = budget
    }

    private var perMovieBudget: Int { cache.totalCostLimit / 2 }

    // Every entry normalizes through `MediaBase.resolve`: the catalog hands
    // bare storage keys, and cache key + download + decode must agree on one
    // absolute URL or the animation silently 404s into the still fallback.
    func cached(_ rawUrl: String) -> SvgaMovie? { cache.object(forKey: MediaBase.resolve(rawUrl) as NSString) }

    func prefetch(_ rawUrl: String) {
        guard !rawUrl.isEmpty, cached(rawUrl) == nil else { return }
        Task.detached(priority: .utility) { _ = await self.load(rawUrl) }
    }

    func load(_ rawUrl: String) async -> SvgaMovie? {
        guard !rawUrl.isEmpty else { return nil }
        let url = MediaBase.resolve(rawUrl)
        if let m = cached(url) { return m }
        lock.lock()
        // `failed` holds the time until which a URL is not retried: a minute
        // for a file that does not decode (one bad upload must not cost every
        // viewer a re-download per gift), a few seconds for a download that
        // merely failed on a weak network — the next gift should animate.
        if let until = failed[url], Date().timeIntervalSince1970 < until { lock.unlock(); return nil }
        if let running = inflight[url] { lock.unlock(); return await running.value.0 }
        let task = Task<(SvgaMovie?, Bool), Never>(priority: .userInitiated) { [self] in
            guard let file = await AssetCache.shared.load(url) else { return (nil, false) }
            await gate.wait()
            defer { gate.signal() }
            return (decode(file), true)
        }
        inflight[url] = task
        lock.unlock()
        let (movie, downloaded) = await task.value
        lock.lock()
        inflight[url] = nil
        if movie == nil { failed[url] = Date().timeIntervalSince1970 + (downloaded ? 60 : 4) }
        lock.unlock()
        if let movie { cache.setObject(movie, forKey: url as NSString, cost: max(1, movie.byteCount)) }
        return movie
    }

    func clear() { cache.removeAllObjects() }

    // MARK: - decode

    private func decode(_ file: URL) -> SvgaMovie? {
        guard let raw = try? Data(contentsOf: file, options: .mappedIfSafe), raw.count > 4 else { return nil }
        // SVGA 1.x is a zip ("PK"): movie.spec JSON + PNG entries. Most gift
        // packs in circulation are 1.x, so both dialects must animate.
        if raw[raw.startIndex] == 0x50, raw[raw.startIndex + 1] == 0x4B { return decodeV1(raw) }
        guard let body = inflate(raw) else { return nil }
        return parseMovie(body)
    }

    // MARK: - SVGA 1.x (zip + movie.spec JSON)

    private func decodeV1(_ raw: Data) -> SvgaMovie? {
        guard let files = unzip(raw), let spec = files["movie.spec"] else { return nil }
        guard let root = (try? JSONSerialization.jsonObject(with: spec)) as? [String: Any] else { return nil }
        return parseMovieV1(root, files: files)
    }

    /// Minimal zip reader: walks the central directory (local headers can lie
    /// about sizes when the writer streamed), inflates deflate entries with the
    /// same raw-deflate call the 2.0 path uses. Bounded against zip bombs.
    private func unzip(_ raw: Data) -> [String: Data]? {
        let b = raw.startIndex
        // End-of-central-directory: scan the tail for PK\x05\x06.
        var eocd = -1
        let lo = max(0, raw.count - 66_000)
        var i = raw.count - 22
        while i >= lo {
            if raw[b + i] == 0x50, raw[b + i + 1] == 0x4B, raw[b + i + 2] == 0x05, raw[b + i + 3] == 0x06 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { return nil }
        let u16 = { (o: Int) -> Int in Int(raw[b + o]) | Int(raw[b + o + 1]) << 8 }
        let u32 = { (o: Int) -> Int in
            Int(raw[b + o]) | Int(raw[b + o + 1]) << 8 | Int(raw[b + o + 2]) << 16 | Int(raw[b + o + 3]) << 24
        }
        let count = u16(eocd + 10)
        var pos = u32(eocd + 16)
        var out: [String: Data] = [:]
        var total = 0
        for _ in 0..<count {
            guard pos + 46 <= raw.count, u32(pos) == 0x0201_4B50 else { return nil }
            let method = u16(pos + 10)
            let compSize = u32(pos + 20)
            let uncompSize = u32(pos + 24)
            let nameLen = u16(pos + 28)
            let extraLen = u16(pos + 30)
            let commentLen = u16(pos + 32)
            let local = u32(pos + 42)
            guard pos + 46 + nameLen <= raw.count else { return nil }
            let name = String(decoding: raw[(b + pos + 46)..<(b + pos + 46 + nameLen)], as: UTF8.self)
            pos += 46 + nameLen + extraLen + commentLen
            let short = name.split(separator: "/").last.map(String.init) ?? name
            if short.isEmpty || short.hasPrefix(".") || name.hasSuffix("/") { continue }
            total += uncompSize
            if total > 160 << 20 { return nil } // refuse a zip bomb
            // Local header: its own name/extra lengths, then the data.
            guard local + 30 <= raw.count, u32(local) == 0x0403_4B50 else { return nil }
            let dataAt = local + 30 + u16(local + 26) + u16(local + 28)
            guard dataAt + compSize <= raw.count else { return nil }
            let body = raw.subdata(in: (b + dataAt)..<(b + dataAt + compSize))
            switch method {
            case 0: out[short] = body
            case 8:
                guard uncompSize > 0 else { continue }
                var dst = Data(count: uncompSize)
                let n = dst.withUnsafeMutableBytes { d -> Int in
                    body.withUnsafeBytes { s -> Int in
                        compression_decode_buffer(
                            d.bindMemory(to: UInt8.self).baseAddress!, uncompSize,
                            s.bindMemory(to: UInt8.self).baseAddress!, body.count,
                            nil, COMPRESSION_ZLIB
                        )
                    }
                }
                guard n == uncompSize else { continue }
                out[short] = dst
            default:
                continue
            }
        }
        return out.isEmpty ? nil : out
    }

    private func parseMovieV1(_ root: [String: Any], files: [String: Data]) -> SvgaMovie? {
        guard let movie = root["movie"] as? [String: Any] else { return nil }
        let vb = movie["viewBox"] as? [String: Any]
        let vw = Float((vb?["width"] as? NSNumber)?.doubleValue ?? 0)
        let vh = Float((vb?["height"] as? NSNumber)?.doubleValue ?? 0)
        let fps = (movie["fps"] as? NSNumber)?.intValue ?? 20
        let frames = (movie["frames"] as? NSNumber)?.intValue ?? 0

        // 1.x maps key → zip entry name; some packs inline the PNG itself as
        // base64 in the spec instead. Both must draw.
        var images: [String: Data] = [:]
        if let imgJson = root["images"] as? [String: Any] {
            for (k, any) in imgJson {
                let v = any as? String ?? ""
                if let bytes = files[v] ?? files["\(v).png"] ?? files[k] ?? files["\(k).png"] ?? inlineImage(v) {
                    images[k] = bytes
                }
            }
        } else {
            for (n, d) in files where n != "movie.spec" {
                images[n.hasSuffix(".png") ? String(n.dropLast(4)) : n] = d
            }
        }

        var sprites: [RawSprite] = []
        guard let sArr = root["sprites"] as? [[String: Any]] else { return nil }
        for sp in sArr {
            var rs = RawSprite()
            rs.imageKey = sp["imageKey"] as? String ?? ""
            for frAny in (sp["frames"] as? [Any]) ?? [] {
                var out = [Float](repeating: 0, count: SvgaSprite.stride)
                out[1] = 1; out[4] = 1
                if let fr = frAny as? [String: Any] {
                    out[0] = Float((fr["alpha"] as? NSNumber)?.doubleValue ?? 0)
                    if let l = fr["layout"] as? [String: Any] {
                        out[7] = Float((l["width"] as? NSNumber)?.doubleValue ?? 0)
                        out[8] = Float((l["height"] as? NSNumber)?.doubleValue ?? 0)
                    }
                    if let t = fr["transform"] as? [String: Any] {
                        out[1] = Float((t["a"] as? NSNumber)?.doubleValue ?? 1)
                        out[2] = Float((t["b"] as? NSNumber)?.doubleValue ?? 0)
                        out[3] = Float((t["c"] as? NSNumber)?.doubleValue ?? 0)
                        out[4] = Float((t["d"] as? NSNumber)?.doubleValue ?? 1)
                        out[5] = Float((t["tx"] as? NSNumber)?.doubleValue ?? 0)
                        out[6] = Float((t["ty"] as? NSNumber)?.doubleValue ?? 0)
                    }
                }
                rs.frames.append(out)
            }
            sprites.append(rs)
        }
        return assemble(vw: vw, vh: vh, fps: fps, frames: frames, images: images, sprites: sprites, audioKey: nil)
    }

    /// Base64-inlined PNG in a 1.x movie.spec; nil when the value is a file name.
    private func inlineImage(_ v: String) -> Data? {
        guard v.count >= 64, let d = Data(base64Encoded: v, options: .ignoreUnknownCharacters) else { return nil }
        return isPng(d) ? d : nil
    }

    /// zlib stream → bytes. Compression wants raw deflate: skip the 2-byte header.
    private func inflate(_ z: Data) -> Data? {
        guard z.count > 6 else { return nil }
        let src = z.subdata(in: (z.startIndex + 2)..<z.endIndex)
        var capacity = max(src.count * 4, 64 << 10)
        while capacity <= 160 << 20 {
            var out = Data(count: capacity)
            let n = out.withUnsafeMutableBytes { dst -> Int in
                src.withUnsafeBytes { s -> Int in
                    compression_decode_buffer(
                        dst.bindMemory(to: UInt8.self).baseAddress!, capacity,
                        s.bindMemory(to: UInt8.self).baseAddress!, src.count,
                        nil, COMPRESSION_ZLIB
                    )
                }
            }
            if n == 0 { return nil }
            if n < capacity { out.count = n; return out }
            capacity *= 2 // filled the buffer exactly: may be truncated, retry larger
        }
        return nil
    }

    private struct Reader {
        let buf: UnsafeBufferPointer<UInt8>
        var pos: Int
        let end: Int
        var hasMore: Bool { pos < end }

        mutating func varint() throws -> UInt64 {
            var shift: UInt64 = 0
            var result: UInt64 = 0
            while shift < 64 {
                guard pos < end else { throw SvgaError.truncated }
                let b = buf[pos]; pos += 1
                result |= UInt64(b & 0x7F) << shift
                if b & 0x80 == 0 { return result }
                shift += 7
            }
            throw SvgaError.malformed
        }
        mutating func float() throws -> Float {
            guard pos + 4 <= end else { throw SvgaError.truncated }
            let bits = UInt32(buf[pos]) | UInt32(buf[pos + 1]) << 8 | UInt32(buf[pos + 2]) << 16 | UInt32(buf[pos + 3]) << 24
            pos += 4
            return Float(bitPattern: bits)
        }
        mutating func sub() throws -> Reader {
            let len = Int(try varint())
            guard pos + len <= end else { throw SvgaError.truncated }
            let r = Reader(buf: buf, pos: pos, end: pos + len)
            pos += len
            return r
        }
        mutating func bytes() throws -> Data {
            let len = Int(try varint())
            guard pos + len <= end else { throw SvgaError.truncated }
            let d = Data(buf[pos..<(pos + len)])
            pos += len
            return d
        }
        mutating func string() throws -> String {
            let d = try bytes()
            return String(decoding: d, as: UTF8.self)
        }
        mutating func skip(_ wire: UInt64) throws {
            switch wire {
            case 0: _ = try varint()
            case 1: pos += 8
            // Two steps on purpose: the length varint advances pos itself, and
            // folding it into `pos +=` must never capture pos before that
            // advance (the Kotlin twin had exactly that desync bug).
            case 2:
                let n = Int(try varint())
                pos += n
            case 5: pos += 4
            default: throw SvgaError.malformed
            }
            guard pos <= end else { throw SvgaError.truncated }
        }
    }

    private enum SvgaError: Error { case truncated, malformed }

    private struct RawSprite { var imageKey = ""; var frames: [[Float]] = [] }

    private func parseMovie(_ body: Data) -> SvgaMovie? {
        body.withUnsafeBytes { (rawBuf: UnsafeRawBufferPointer) -> SvgaMovie? in
            let buf = rawBuf.bindMemory(to: UInt8.self)
            var r = Reader(buf: buf, pos: 0, end: buf.count)
            var vw: Float = 0, vh: Float = 0
            var fps = 20, frames = 0
            var images: [String: Data] = [:]
            var sprites: [RawSprite] = []
            var audioKey: String?
            do {
                while r.hasMore {
                    let key = try r.varint()
                    let field = key >> 3, wire = key & 7
                    switch field {
                    case 2:
                        var p = try r.sub()
                        while p.hasMore {
                            let k = try p.varint()
                            switch k >> 3 {
                            case 1: vw = try p.float()
                            case 2: vh = try p.float()
                            case 3: fps = Int(try p.varint())
                            case 4: frames = Int(try p.varint())
                            default: try p.skip(k & 7)
                            }
                        }
                    case 3:
                        var e = try r.sub()
                        var k = ""; var v: Data?
                        while e.hasMore {
                            let ek = try e.varint()
                            switch ek >> 3 {
                            case 1: k = try e.string()
                            case 2: v = try e.bytes()
                            default: try e.skip(ek & 7)
                            }
                        }
                        if let v { images[k] = v }
                    case 4:
                        var s = try r.sub()
                        sprites.append(try parseSprite(&s))
                    case 5:
                        var a = try r.sub()
                        while a.hasMore {
                            let ak = try a.varint()
                            if ak >> 3 == 1 { let s = try a.string(); if audioKey == nil { audioKey = s } } else { try a.skip(ak & 7) }
                        }
                    default:
                        try r.skip(wire)
                    }
                }
            } catch { return nil }
            return assemble(vw: vw, vh: vh, fps: fps, frames: frames, images: images, sprites: sprites, audioKey: audioKey)
        }
    }

    /// Parsed fields (either dialect) → decoded, subsampled, packed movie.
    private func assemble(vw: Float, vh: Float, fps rawFps: Int, frames: Int, images: [String: Data], sprites: [RawSprite], audioKey: String?) -> SvgaMovie? {
        guard frames > 0, vw > 0, vh > 0 else { return nil }
        let fps = rawFps <= 0 ? 20 : rawFps

        let sample = chooseSample(images, sprites, viewBoxW: CGFloat(vw))
        var cg: [String: CGImage] = [:]
        var bytes = 0
        for (k, png) in images {
            if k == audioKey || k == (audioKey ?? "") + ".mp3" { continue }
            guard isPng(png), let img = decodePng(png, sample: sample) else { continue }
            cg[k] = img
            bytes += img.bytesPerRow * img.height
        }
        var packed: [SvgaSprite] = []
        for rs in sprites {
            guard let key = resolveKey(rs.imageKey, cg) else { continue }
            var data = [Float](repeating: 0, count: frames * SvgaSprite.stride)
            let n = min(frames, rs.frames.count)
            for i in 0..<n {
                let f = rs.frames[i]
                for j in 0..<SvgaSprite.stride { data[i * SvgaSprite.stride + j] = f[j] }
            }
            bytes += data.count * 4
            packed.append(SvgaSprite(imageKey: key, frames: frames, data: data))
        }
        guard !packed.isEmpty else { return nil }
        return SvgaMovie(width: CGFloat(vw), height: CGFloat(vh), fps: fps, frames: frames, sprites: packed, images: cg, byteCount: bytes)
    }

    private func parseSprite(_ s: inout Reader) throws -> RawSprite {
        var out = RawSprite()
        while s.hasMore {
            let k = try s.varint()
            switch k >> 3 {
            case 1: out.imageKey = try s.string()
            case 2: var f = try s.sub(); out.frames.append(try parseFrame(&f))
            default: try s.skip(k & 7)
            }
        }
        return out
    }

    /// [alpha, a, b, c, d, tx, ty, w, h]; a missing transform is identity.
    private func parseFrame(_ f: inout Reader) throws -> [Float] {
        var out = [Float](repeating: 0, count: SvgaSprite.stride)
        out[1] = 1; out[4] = 1
        var hasTransform = false
        while f.hasMore {
            let k = try f.varint()
            switch k >> 3 {
            case 1: out[0] = try f.float()
            case 2:
                var l = try f.sub()
                while l.hasMore {
                    let lk = try l.varint()
                    switch lk >> 3 {
                    case 3: out[7] = try l.float()
                    case 4: out[8] = try l.float()
                    default: try l.skip(lk & 7)
                    }
                }
            case 3:
                hasTransform = true
                out[1] = 0; out[4] = 0 // proto3 omits zero fields
                var t = try f.sub()
                while t.hasMore {
                    let tk = try t.varint()
                    switch tk >> 3 {
                    case 1: out[1] = try t.float()
                    case 2: out[2] = try t.float()
                    case 3: out[3] = try t.float()
                    case 4: out[4] = try t.float()
                    case 5: out[5] = try t.float()
                    case 6: out[6] = try t.float()
                    default: try t.skip(tk & 7)
                    }
                }
            default: try f.skip(k & 7)
            }
        }
        if !hasTransform { out[1] = 1; out[4] = 1 }
        return out
    }

    private func chooseSample(_ images: [String: Data], _ sprites: [RawSprite], viewBoxW: CGFloat) -> Int {
        let fit = min(max(screenWidthPx / viewBoxW, 0.1), 1)
        var sample = 1
        while CGFloat(sample * 2) <= 1 / fit { sample *= 2 }
        var pixels: Int64 = 0
        for (_, png) in images where isPng(png) {
            if let (w, h) = pngSize(png) { pixels += Int64(w) * Int64(h) }
        }
        let transforms = sprites.reduce(0) { $0 + $1.frames.count } * SvgaSprite.stride * 4
        while sample < 16, (pixels * 4 / Int64(sample * sample) + Int64(transforms)) > Int64(perMovieBudget) { sample *= 2 }
        return sample
    }

    private func decodePng(_ data: Data, sample: Int) -> CGImage? {
        guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        guard let (w, _) = pngSize(data) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceShouldCache: false,
            kCGImageSourceCreateThumbnailFromImageAlways: sample > 1,
            kCGImageSourceCreateThumbnailWithTransform: false,
            kCGImageSourceThumbnailMaxPixelSize: max(1, w / sample),
        ]
        if sample > 1 { return CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) }
        return CGImageSourceCreateImageAtIndex(src, 0, [kCGImageSourceShouldCache: false] as CFDictionary)
    }

    /// PNG IHDR is fixed at bytes 16..24: width, height big-endian.
    private func pngSize(_ d: Data) -> (Int, Int)? {
        guard d.count >= 24 else { return nil }
        let b = d.startIndex
        let w = Int(d[b + 16]) << 24 | Int(d[b + 17]) << 16 | Int(d[b + 18]) << 8 | Int(d[b + 19])
        let h = Int(d[b + 20]) << 24 | Int(d[b + 21]) << 16 | Int(d[b + 22]) << 8 | Int(d[b + 23])
        return w > 0 && h > 0 ? (w, h) : nil
    }

    private func isPng(_ d: Data) -> Bool {
        d.count > 8 && d[d.startIndex] == 0x89 && d[d.startIndex + 1] == 0x50 && d[d.startIndex + 2] == 0x4E && d[d.startIndex + 3] == 0x47
    }

    private func resolveKey(_ key: String, _ images: [String: CGImage]) -> String? {
        if key.isEmpty { return nil }
        if images[key] != nil { return key }
        let bare = key.hasSuffix(".png") ? String(key.dropLast(4)) : key
        if images[bare] != nil { return bare }
        if images[bare + ".png"] != nil { return bare + ".png" }
        return nil
    }
}

/// Plays one movie: one `CALayer` per sprite, its contents the sprite image
/// uploaded to the GPU once; each vsync only sets transforms and opacity inside
/// a transaction with actions disabled. No CPU drawing, no allocation per frame,
/// and a dropped frame skips forward — the gift always takes its real duration.
final class SvgaPlayerView: UIView {
    private var movie: SvgaMovie?
    private var layers: [CALayer] = []
    private var link: CADisplayLink?
    private var startedAt: CFTimeInterval = 0
    private var loops = 1
    private var onDone: (() -> Void)?
    private var scale: CGFloat = 1
    private var offset = CGPoint.zero
    private var lastFrame = -1

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        isOpaque = false
        backgroundColor = .clear
    }

    required init?(coder: NSCoder) { fatalError() }

    func play(_ m: SvgaMovie, loops: Int = 1, onDone: (() -> Void)? = nil) {
        stop()
        movie = m
        self.loops = loops
        self.onDone = onDone
        CATransaction.begin(); CATransaction.setDisableActions(true)
        for s in m.sprites {
            let l = CALayer()
            l.contents = m.images[s.imageKey]
            l.contentsGravity = .resize
            l.anchorPoint = .zero
            l.isOpaque = false
            l.opacity = 0
            layer.addSublayer(l)
            layers.append(l)
        }
        CATransaction.commit()
        fit()
        lastFrame = -1
        startedAt = CACurrentMediaTime()
        let dl = CADisplayLink(target: WeakProxy(self), selector: #selector(WeakProxy.tick))
        dl.preferredFramesPerSecond = min(60, max(m.fps, 24))
        dl.add(to: .main, forMode: .common)
        link = dl
    }

    func stop() {
        link?.invalidate(); link = nil
        layers.forEach { $0.removeFromSuperlayer() }
        layers.removeAll()
        movie = nil
        onDone = nil
    }

    override func layoutSubviews() { super.layoutSubviews(); fit() }

    private func fit() {
        guard let m = movie, bounds.width > 0, bounds.height > 0 else { return }
        scale = min(bounds.width / m.width, bounds.height / m.height)
        offset = CGPoint(x: (bounds.width - m.width * scale) / 2, y: (bounds.height - m.height * scale) / 2)
        lastFrame = -1
    }

    fileprivate func tick() {
        guard let m = movie else { return }
        let total = Double(m.durationMs) / 1000
        guard total > 0 else { complete(); return }
        let elapsed = CACurrentMediaTime() - startedAt
        let loop = Int(elapsed / total)
        if loops > 0, loop >= loops { draw(m, frame: m.frames - 1); complete(); return }
        let frame = min(m.frames - 1, max(0, Int((elapsed - Double(loop) * total) * Double(m.fps))))
        if frame == lastFrame { return }
        lastFrame = frame
        draw(m, frame: frame)
    }

    private func draw(_ m: SvgaMovie, frame: Int) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (i, s) in m.sprites.enumerated() {
            let l = layers[i]
            let alpha = s.alpha(frame)
            if alpha <= 0.001 { l.opacity = 0; continue }
            let w = CGFloat(s.w(frame)), h = CGFloat(s.h(frame))
            guard let img = m.images[s.imageKey] else { l.opacity = 0; continue }
            let bw = w > 0 ? w : CGFloat(img.width), bh = h > 0 ? h : CGFloat(img.height)
            l.bounds = CGRect(x: 0, y: 0, width: bw, height: bh)
            // sprite → frame transform → aspect-fit into our bounds
            var t = CGAffineTransform(a: CGFloat(s.a(frame)), b: CGFloat(s.b(frame)), c: CGFloat(s.c(frame)), d: CGFloat(s.d(frame)),
                                      tx: CGFloat(s.tx(frame)), ty: CGFloat(s.ty(frame)))
            t = t.concatenating(CGAffineTransform(scaleX: scale, y: scale))
            t = t.concatenating(CGAffineTransform(translationX: offset.x, y: offset.y))
            l.position = .zero
            l.setAffineTransform(t)
            l.opacity = alpha
        }
        CATransaction.commit()
    }

    private func complete() {
        link?.invalidate(); link = nil
        let cb = onDone
        onDone = nil
        cb?()
    }

    deinit { link?.invalidate() }

    /// Breaks the CADisplayLink → view retain cycle.
    private final class WeakProxy: NSObject {
        weak var target: SvgaPlayerView?
        init(_ t: SvgaPlayerView) { target = t }
        @objc func tick() { target?.tick() }
    }
}
