package app.xani.core.media

import android.content.Context
import app.xani.core.isolate.runFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Immutable binary assets (gift animations, gift sounds) on disk, by URL.
 *
 * Nothing here lives in the heap: the cache hands back a [File] and the decoder
 * that needs it streams from disk. Keys are content-addressed on the server so
 * a file never goes stale; the directory is trimmed to [maxBytes] by last use
 * so a season of new gifts cannot fill a phone. One download per URL at a time
 * (later callers await the first), at most three at once, and every download
 * lands atomically — a killed app never leaves a half file that would decode
 * as garbage next launch.
 */
object AssetCache {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<File?>>()

    /** Disk budget for gift assets; a 40 MB SVGA is the ceiling for one file. */
    private const val MAX_BYTES = 256L shl 20
    private const val MAX_FILE = 48L shl 20
    private const val TRIM_EVERY_MS = 10 * 60 * 1000L

    @Volatile
    private var dir: File? = null

    @Volatile
    private var lastTrim = 0L

    fun attach(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.cacheDir, "assets").apply { mkdirs() }
    }

    /** The cached file if it is already on disk; never touches the network. */
    fun cached(url: String): File? {
        val f = fileFor(url) ?: return null
        return if (f.length() > 0L) f else null
    }

    /** Warms disk for a URL the room is about to need. Fire and forget. */
    fun prefetch(url: String) {
        if (url.isEmpty() || cached(url) != null) return
        scope.launch { load(url) }
    }

    /** The file for [url], downloading once if needed. Null on a failed fetch. */
    suspend fun load(url: String): File? {
        if (url.isEmpty()) return null
        cached(url)?.let { touch(it); return it }
        val mine = CompletableDeferred<File?>()
        val prior = inflight.putIfAbsent(url, mine)
        if (prior != null) return prior.await()
        scope.launch {
            val f = runFeature<File?>(null) { fetch(url) }
            inflight.remove(url)
            mine.complete(f)
            maybeTrim()
        }
        return mine.await()
    }

    private suspend fun fetch(url: String): File? = gate.withPermit {
        val target = fileFor(url) ?: return@withPermit null
        if (target.length() > 0L) return@withPermit target
        val tmp = File(target.parentFile, target.name + ".part")
        var c: HttpURLConnection? = null
        try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (c.responseCode != 200) return@withPermit null
            val declared = c.contentLengthLong
            if (declared > MAX_FILE) return@withPermit null
            var total = 0L
            c.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FILE) {
                            tmp.delete(); return@withPermit null
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total == 0L || (declared > 0 && total != declared)) {
                tmp.delete(); return@withPermit null
            }
            if (!tmp.renameTo(target)) {
                tmp.delete(); return@withPermit null
            }
            target
        } catch (_: Exception) {
            tmp.delete()
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun touch(f: File) {
        val now = System.currentTimeMillis()
        if (now - f.lastModified() > 60 * 60 * 1000L) f.setLastModified(now)
    }

    /** LRU trim by mtime, at most every ten minutes, never on a caller's path. */
    private fun maybeTrim() {
        val now = System.currentTimeMillis()
        if (now - lastTrim < TRIM_EVERY_MS) return
        lastTrim = now
        val d = dir ?: return
        runFeature(Unit) {
            val files = d.listFiles() ?: return@runFeature
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) return@runFeature
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_BYTES * 3 / 4) break
                if (inflight.containsKey(f.name)) continue
                total -= f.length()
                f.delete()
            }
        }
    }

    private fun fileFor(url: String): File? = dir?.let { File(it, sha1(url)) }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
