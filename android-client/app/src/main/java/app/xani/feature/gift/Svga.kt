package app.xani.feature.gift

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.xani.core.isolate.runFeature
import app.xani.core.media.AssetCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * SVGA 2.0 — the gift animation format TikTok-class apps ship (zlib-compressed
 * protobuf: sprite PNGs + per-frame affine transforms). Decoded here with a
 * 120-line wire reader instead of a protobuf runtime, so the app carries no
 * extra dependency for it.
 *
 * Memory is the whole design:
 *   - Sprites are decoded once per movie, sub-sampled so the largest sprite is
 *     never wider than the screen it will be drawn on, and the movie's total
 *     pixel bytes are held under a per-movie budget by raising the sample size
 *     until they fit. A 1080p gift decoded for a 720p phone is a 720p gift.
 *   - Frames are arrays of floats (six per sprite per frame): a 3-second,
 *     30 fps gift with twenty sprites is ~45 KB of transforms, not objects.
 *   - Decoded movies sit in one LRU sized to the device heap. Playing a gift
 *     twice costs nothing the second time; a hundred distinct gifts in one
 *     live evict the ones nobody sent recently. Nothing is recycled by hand —
 *     a player holding a movie keeps it alive, eviction only forgets it.
 *   - Decoding never touches the main thread and never runs more than two at a
 *     time, so a burst of new gifts cannot starve the encoder or the frame.
 */
internal class SvgaMovie(
    val width: Float,
    val height: Float,
    val fps: Int,
    val frames: Int,
    val sprites: List<SvgaSprite>,
    /** Sprite bitmaps by image key; shared by every sprite that uses the key. */
    val bitmaps: Map<String, Bitmap>,
    /** Embedded audio (MP3 bytes) if the file carries one; null otherwise. */
    val audio: ByteArray?,
    val byteCount: Int,
) {
    val durationMs: Long get() = if (fps <= 0) 0L else frames * 1000L / fps
}

/**
 * One sprite: which bitmap and, per frame, its alpha + affine transform +
 * layout size. Packed as flat floats: [alpha, a, b, c, d, tx, ty, w, h] × frames.
 */
internal class SvgaSprite(val imageKey: String, val frames: Int, val data: FloatArray) {
    fun alpha(frame: Int): Float = data[frame * STRIDE]
    fun a(frame: Int): Float = data[frame * STRIDE + 1]
    fun b(frame: Int): Float = data[frame * STRIDE + 2]
    fun c(frame: Int): Float = data[frame * STRIDE + 3]
    fun d(frame: Int): Float = data[frame * STRIDE + 4]
    fun tx(frame: Int): Float = data[frame * STRIDE + 5]
    fun ty(frame: Int): Float = data[frame * STRIDE + 6]
    fun w(frame: Int): Float = data[frame * STRIDE + 7]
    fun h(frame: Int): Float = data[frame * STRIDE + 8]

    companion object {
        const val STRIDE = 9
    }
}

internal object SvgaLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Semaphore(2)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<SvgaMovie?>>()
    private val failed = ConcurrentHashMap<String, Long>()

    /** Movies in memory, by URL, accounted in bytes. */
    private val cache = object : LruCache<String, SvgaMovie>(budgetBytes()) {
        override fun sizeOf(key: String, value: SvgaMovie): Int = value.byteCount.coerceAtLeast(1)
    }

    /** A sixth of the heap, between 24 and 128 MB: room for ~6 big gifts. */
    private fun budgetBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 6).coerceIn(24L shl 20, 128L shl 20).toInt()
    }

    /** One movie may take half the cache, so two large gifts can alternate. */
    private val perMovieBudget: Int get() = cache.maxSize() / 2

    @Volatile
    var screenWidthPx: Int = 1080

    // Catalog URLs are absolute when the server has a media base configured;
    // otherwise they are bare storage keys ("gf/1/anim.svga"). Every entry
    // point resolves through the media config, so the cache key, the download
    // and the decode all see the same absolute URL — a bare key must animate,
    // not 404 into the still fallback.
    fun cached(url: String): SvgaMovie? {
        val u = giftImageUrl(url)
        return synchronized(cache) { cache.get(u) }
    }

    /** Warms disk + memory for a gift the room is likely to see. */
    fun prefetch(url: String) {
        if (url.isEmpty() || cached(url) != null) return
        scope.launch { load(url) }
    }

    /** Decoded movie for [url]; null when the file is missing or not SVGA. */
    suspend fun load(rawUrl: String): SvgaMovie? {
        val url = giftImageUrl(rawUrl)
        if (url.isEmpty()) return null
        synchronized(cache) { cache.get(url) }?.let { return it }
        // A file that does not decode is not retried for a minute: one bad
        // upload must not cost every viewer a re-download per gift. A download
        // that merely failed (weak network) is retried after a few seconds —
        // the next gift of the same kind should animate, not stay a still.
        failed[url]?.let { if (System.currentTimeMillis() < it) return null }
        val mine = CompletableDeferred<SvgaMovie?>()
        val prior = inflight.putIfAbsent(url, mine)
        if (prior != null) return prior.await()
        scope.launch {
            var downloaded = false
            val movie = runFeature<SvgaMovie?>(null) {
                val file = AssetCache.load(url) ?: return@runFeature null
                downloaded = true
                gate.withPermit { decode(file) }
            }
            when {
                movie != null -> synchronized(cache) { cache.put(url, movie) }
                downloaded -> failed[url] = System.currentTimeMillis() + BAD_FILE_MS
                else -> failed[url] = System.currentTimeMillis() + RETRY_NET_MS
            }
            inflight.remove(url)
            mine.complete(movie)
        }
        return mine.await()
    }

    private const val BAD_FILE_MS = 60_000L
    private const val RETRY_NET_MS = 4_000L

    /** Drop everything (sign-out, memory pressure). Players keep what they hold. */
    fun clear() = synchronized(cache) { cache.evictAll() }

    // ─── decode ──────────────────────────────────────────────────────────────

    private fun decode(file: File): SvgaMovie? {
        val raw = file.readBytes()
        if (raw.size < 4) return null
        // SVGA 1.x is a zip ("PK"): movie.spec JSON + PNG entries. Most gift
        // packs in circulation are 1.x, so both dialects have to animate —
        // a format nobody can tell apart in the dashboard must not be the
        // difference between the animation and a frozen box.
        if (raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()) return decodeV1(raw)
        val body = inflate(raw) ?: return null
        return parseMovie(body)
    }

    // ─── SVGA 1.x (zip + movie.spec JSON) ───────────────────────────────────

    private fun decodeV1(raw: ByteArray): SvgaMovie? {
        val entries = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
                var total = 0L
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.substringAfterLast('/')
                    if (name.isEmpty() || name.startsWith(".")) continue
                    val out = ByteArrayOutputStream()
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 160L shl 20) return null // refuse a zip bomb
                        out.write(buf, 0, n)
                    }
                    entries[name] = out.toByteArray()
                }
            }
        } catch (_: Exception) {
            return null
        }
        val spec = entries.remove("movie.spec") ?: return null
        return try {
            parseMovieV1(JSONObject(String(spec, Charsets.UTF_8)), entries)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMovieV1(root: JSONObject, files: Map<String, ByteArray>): SvgaMovie? {
        val movie = root.optJSONObject("movie") ?: return null
        val vb = movie.optJSONObject("viewBox")
        val vw = (vb?.optDouble("width", 0.0) ?: 0.0).toFloat()
        val vh = (vb?.optDouble("height", 0.0) ?: 0.0).toFloat()
        var fps = movie.optInt("fps", 20)
        val frames = movie.optInt("frames", 0)
        if (frames <= 0 || vw <= 0f || vh <= 0f) return null
        if (fps <= 0) fps = 20

        // 1.x keeps images as zip entries; movie.spec maps key → entry name.
        // Some packs inline the PNG itself as base64 in the spec instead —
        // both dialects must draw, or the gift is a frozen box.
        val images = HashMap<String, ByteArray>()
        val imgJson = root.optJSONObject("images")
        if (imgJson != null) {
            for (k in imgJson.keys()) {
                val v = imgJson.optString(k)
                val bytes = files[v] ?: files["$v.png"] ?: files[k] ?: files["$k.png"]
                    ?: inlineImage(v) ?: continue
                images[k] = bytes
            }
        } else {
            for ((n, b) in files) images[n.removeSuffix(".png")] = b
        }

        val sprites = ArrayList<RawSprite>()
        val sArr = root.optJSONArray("sprites") ?: return null
        for (i in 0 until sArr.length()) {
            val sp = sArr.optJSONObject(i) ?: continue
            val list = ArrayList<FloatArray>(64)
            val fArr = sp.optJSONArray("frames")
            if (fArr != null) {
                for (j in 0 until fArr.length()) {
                    val fr = fArr.optJSONObject(j)
                    val out = FloatArray(SvgaSprite.STRIDE)
                    out[1] = 1f; out[4] = 1f
                    if (fr != null) {
                        out[0] = fr.optDouble("alpha", 0.0).toFloat()
                        fr.optJSONObject("layout")?.let { l ->
                            out[7] = l.optDouble("width", 0.0).toFloat()
                            out[8] = l.optDouble("height", 0.0).toFloat()
                        }
                        fr.optJSONObject("transform")?.let { t ->
                            out[1] = t.optDouble("a", 1.0).toFloat()
                            out[2] = t.optDouble("b", 0.0).toFloat()
                            out[3] = t.optDouble("c", 0.0).toFloat()
                            out[4] = t.optDouble("d", 1.0).toFloat()
                            out[5] = t.optDouble("tx", 0.0).toFloat()
                            out[6] = t.optDouble("ty", 0.0).toFloat()
                        }
                    }
                    list.add(out)
                }
            }
            sprites.add(RawSprite(sp.optString("imageKey"), list))
        }
        return buildMovie(vw, vh, fps, frames, images, sprites, audioKey = null)
    }

    /** Base64-inlined PNG in a 1.x movie.spec; null when [v] is a file name. */
    private fun inlineImage(v: String): ByteArray? {
        if (v.length < 64) return null
        return try {
            val b = android.util.Base64.decode(v, android.util.Base64.DEFAULT)
            if (b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) b else null
        } catch (_: Exception) {
            null
        }
    }

    private fun inflate(z: ByteArray): ByteArray? {
        val inf = Inflater()
        return try {
            inf.setInput(z)
            var out = ByteArray((z.size * 4).coerceAtLeast(64 * 1024))
            var n = 0
            while (!inf.finished()) {
                if (n == out.size) {
                    if (out.size >= 160 shl 20) return null // refuse a zip bomb
                    out = out.copyOf(out.size * 2)
                }
                val k = inf.inflate(out, n, out.size - n)
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break
                n += k
            }
            out.copyOf(n)
        } catch (_: Exception) {
            null
        } finally {
            inf.end()
        }
    }

    private class Reader(val buf: ByteArray, var pos: Int, val end: Int) {
        fun hasMore() = pos < end
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (shift < 64) {
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            throw IllegalStateException("varint")
        }
        fun float(): Float {
            val bits = (buf[pos].toInt() and 0xFF) or
                ((buf[pos + 1].toInt() and 0xFF) shl 8) or
                ((buf[pos + 2].toInt() and 0xFF) shl 16) or
                ((buf[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return Float.fromBits(bits)
        }
        /** Length-delimited sub-reader; advances past it. */
        fun sub(): Reader {
            val len = varint().toInt()
            val r = Reader(buf, pos, pos + len)
            pos += len
            return r
        }
        fun bytes(): ByteArray {
            val len = varint().toInt()
            val out = buf.copyOfRange(pos, pos + len)
            pos += len
            return out
        }
        fun string(): String {
            val len = varint().toInt()
            val s = String(buf, pos, len, Charsets.UTF_8)
            pos += len
            return s
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                // Two steps on purpose: `pos += varint()` reads the OLD pos
                // before varint() advances it, silently dropping the length
                // byte(s) and desyncing the reader — which made every SVGA 2.0
                // file (they all open with a skipped `version` string) fail to
                // decode and play as a frozen still.
                2 -> { val n = varint().toInt(); pos += n }
                5 -> pos += 4
                else -> throw IllegalStateException("wire $wire")
            }
        }
    }

    private class RawSprite(val imageKey: String, val frames: MutableList<FloatArray>)

    private fun parseMovie(body: ByteArray): SvgaMovie? {
        val r = Reader(body, 0, body.size)
        var vw = 0f
        var vh = 0f
        var fps = 20
        var frames = 0
        val images = HashMap<String, ByteArray>()
        val sprites = ArrayList<RawSprite>()
        var audioKey: String? = null
        while (r.hasMore()) {
            val key = r.varint().toInt()
            val field = key ushr 3
            val wire = key and 7
            when (field) {
                2 -> { // params
                    val p = r.sub()
                    while (p.hasMore()) {
                        val k = p.varint().toInt()
                        when (k ushr 3) {
                            1 -> vw = p.float()
                            2 -> vh = p.float()
                            3 -> fps = p.varint().toInt()
                            4 -> frames = p.varint().toInt()
                            else -> p.skip(k and 7)
                        }
                    }
                }
                3 -> { // images map entry
                    val e = r.sub()
                    var k = ""
                    var v: ByteArray? = null
                    while (e.hasMore()) {
                        val ek = e.varint().toInt()
                        when (ek ushr 3) {
                            1 -> k = e.string()
                            2 -> v = e.bytes()
                            else -> e.skip(ek and 7)
                        }
                    }
                    if (v != null) images[k] = v
                }
                4 -> sprites.add(parseSprite(r.sub()))
                5 -> { // audios: first one wins
                    val a = r.sub()
                    while (a.hasMore()) {
                        val ak = a.varint().toInt()
                        when (ak ushr 3) {
                            1 -> { val s = a.string(); if (audioKey == null) audioKey = s }
                            else -> a.skip(ak and 7)
                        }
                    }
                }
                else -> r.skip(wire)
            }
        }
        if (frames <= 0 || vw <= 0f || vh <= 0f) return null
        if (fps <= 0) fps = 20
        return buildMovie(vw, vh, fps, frames, images, sprites, audioKey)
    }

    /** Decoded bitmaps + packed transforms, shared by the 1.x and 2.0 parsers. */
    private fun buildMovie(
        vw: Float,
        vh: Float,
        fps: Int,
        frames: Int,
        images: Map<String, ByteArray>,
        sprites: List<RawSprite>,
        audioKey: String?,
    ): SvgaMovie? {
        // Sub-sample so the widest sprite is at most the screen width and the
        // movie fits its budget; both are one sample-size decision per movie.
        val sample = chooseSample(images, sprites, vw)
        val bitmaps = HashMap<String, Bitmap>(images.size)
        var bytes = 0
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val audio = audioKey?.let { images[it] ?: images["$it.mp3"] }
        for ((k, png) in images) {
            if (k == audioKey || k == "$audioKey.mp3") continue
            if (!png.isPng()) continue
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size, opts) ?: continue
            bitmaps[k] = bmp
            bytes += bmp.byteCount
        }
        val packed = sprites.mapNotNull { rs ->
            val imgKey = resolveKey(rs.imageKey, bitmaps) ?: return@mapNotNull null
            val n = minOf(frames, rs.frames.size)
            val data = FloatArray(frames * SvgaSprite.STRIDE)
            for (i in 0 until n) System.arraycopy(rs.frames[i], 0, data, i * SvgaSprite.STRIDE, SvgaSprite.STRIDE)
            bytes += data.size * 4
            SvgaSprite(imgKey, frames, data)
        }
        if (packed.isEmpty()) return null
        return SvgaMovie(vw, vh, fps, frames, packed, bitmaps, audio, bytes + (audio?.size ?: 0))
    }

    private fun parseSprite(s: Reader): RawSprite {
        var imageKey = ""
        val frames = ArrayList<FloatArray>(64)
        while (s.hasMore()) {
            val k = s.varint().toInt()
            when (k ushr 3) {
                1 -> imageKey = s.string()
                2 -> frames.add(parseFrame(s.sub()))
                else -> s.skip(k and 7)
            }
        }
        return RawSprite(imageKey, frames)
    }

    /** [alpha, a, b, c, d, tx, ty, w, h]; a missing transform is identity. */
    private fun parseFrame(f: Reader): FloatArray {
        val out = FloatArray(SvgaSprite.STRIDE)
        out[1] = 1f; out[4] = 1f
        var hasTransform = false
        while (f.hasMore()) {
            val k = f.varint().toInt()
            when (k ushr 3) {
                1 -> out[0] = f.float()
                2 -> { // layout
                    val l = f.sub()
                    while (l.hasMore()) {
                        val lk = l.varint().toInt()
                        when (lk ushr 3) {
                            3 -> out[7] = l.float()
                            4 -> out[8] = l.float()
                            else -> l.skip(lk and 7)
                        }
                    }
                }
                3 -> { // transform
                    hasTransform = true
                    // proto3 omits zero fields: start from zeros, not identity.
                    out[1] = 0f; out[4] = 0f
                    val t = f.sub()
                    while (t.hasMore()) {
                        val tk = t.varint().toInt()
                        when (tk ushr 3) {
                            1 -> out[1] = t.float()
                            2 -> out[2] = t.float()
                            3 -> out[3] = t.float()
                            4 -> out[4] = t.float()
                            5 -> out[5] = t.float()
                            6 -> out[6] = t.float()
                            else -> t.skip(tk and 7)
                        }
                    }
                }
                else -> f.skip(k and 7)
            }
        }
        if (!hasTransform) { out[1] = 1f; out[4] = 1f }
        return out
    }

    private fun chooseSample(images: Map<String, ByteArray>, sprites: List<RawSprite>, viewBoxW: Float): Int {
        // Screen-fit: a sprite laid out at layout.width viewBox units is drawn
        // at layout.width * (screen / viewBox) pixels; decoding finer is waste.
        val fit = (screenWidthPx / viewBoxW).coerceIn(0.1f, 1f)
        var sample = 1
        while (sample * 2 <= (1f / fit).toInt()) sample *= 2

        // Budget-fit: bounds-decode every PNG (no pixels allocated) and raise
        // the sample until the movie's pixel bytes fit its share of the cache.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var pixels = 0L
        for ((_, png) in images) {
            if (!png.isPng()) continue
            BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) pixels += bounds.outWidth.toLong() * bounds.outHeight
        }
        val transforms = sprites.sumOf { it.frames.size.toLong() } * SvgaSprite.STRIDE * 4
        while (sample < 16 && (pixels * 4 / (sample * sample) + transforms) > perMovieBudget) sample *= 2
        return sample
    }

    private fun resolveKey(key: String, bitmaps: Map<String, Bitmap>): String? {
        if (key.isEmpty()) return null
        if (bitmaps.containsKey(key)) return key
        val bare = key.removeSuffix(".png")
        if (bitmaps.containsKey(bare)) return bare
        if (bitmaps.containsKey("$bare.png")) return "$bare.png"
        return null
    }

    private fun ByteArray.isPng(): Boolean =
        size > 8 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() && this[2] == 'N'.code.toByte() && this[3] == 'G'.code.toByte()
}
