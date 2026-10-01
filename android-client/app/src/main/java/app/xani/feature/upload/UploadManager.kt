package app.xani.feature.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
import app.xani.core.net.ApiResponse
import app.xani.core.net.LiveFeed
import app.xani.core.net.PostData
import app.xani.core.net.PostForm
import app.xani.core.net.UploadDeclaration
import app.xani.core.net.UploadSession
import app.xani.core.net.Xani
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * The posting outbox.
 *
 * Submit a file and a form; everything after that is this object's problem.
 * It runs one upload at a time (so each one finishes as fast as the link
 * allows), sends parts in parallel with a checksum each, persists progress
 * after every part, and resumes from the server's bitmap after a kill. Once
 * the server has every byte it creates the post and this object waits — on
 * the shared live stream, not by polling — for the pipeline to finish.
 *
 * Screens read [tasks]. They never see a socket, a file, or a retry.
 */
object UploadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())

    /** Oldest first. Terminal tasks stay until dismissed (or briefly, for Posted). */
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    @Volatile
    private var app: Context? = null

    @Volatile
    private var store: UploadStore? = null

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var running: Job? = null
    private var runningId: String? = null
    private var watch: Job? = null
    private var parallelism = 2

    // ─── lifecycle ───────────────────────────────────────────────────────────

    fun attach(context: Context) {
        if (app != null) return
        synchronized(lock) {
            if (app != null) return
            val a = context.applicationContext
            app = a
            parallelism = when (DeviceClass.read(a)) {
                DeviceClass.Low -> 1
                DeviceClass.Mid -> 2
                DeviceClass.High -> 3
            }
            val s = UploadStore(a)
            store = s
            val loaded = s.loadAll().filterNot { t ->
                // A "Posted" strip nobody dismissed before the app died is stale.
                (t.state == UploadState.Posted).also { if (it) s.delete(t.id) }
            }
            _tasks.value = loaded
        }
        scope.launch { runLoop() }
        scope.launch { LiveFeed.posts.collect { onPostEvent(it) } }
        ensureWatch()
        if (_tasks.value.any { !it.state.isTerminal }) {
            UploadService.ensureRunning(context)
            wake.trySend(Unit)
        }
    }

    /** Called when the app returns to the foreground: pick up anything waiting. */
    fun resume() {
        wake.trySend(Unit)
        if (_tasks.value.any { it.state == UploadState.Processing }) {
            scope.launch { refreshProcessing() }
        }
    }

    // ─── commands ────────────────────────────────────────────────────────────

    /** Queues a post. Returns immediately; the strip shows progress. */
    fun enqueue(info: MediaInfo, form: PostForm): UploadTask {
        val task = UploadTask(
            id = UUID.randomUUID().toString().replace("-", ""),
            uri = info.uri.toString(),
            kind = info.kind,
            mime = info.mime,
            size = info.size,
            width = info.width,
            height = info.height,
            durationMs = info.durationMs,
            capturedAt = info.capturedAt,
            form = form,
            createdAt = System.currentTimeMillis(),
        )
        _tasks.update { it + task }
        store?.save(task)
        app?.let { UploadService.ensureRunning(it) }
        wake.trySend(Unit)
        return task
    }

    fun retry(id: String) {
        update(id) { it.copy(state = UploadState.Queued, error = "", errorCode = "") }
        app?.let { UploadService.ensureRunning(it) }
        wake.trySend(Unit)
    }

    /** Removes a finished strip. */
    fun dismiss(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (!t.state.isTerminal) return
        remove(id)
    }

    /** Stops an in-flight upload and forgets it. */
    fun cancel(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            synchronized(lock) { if (runningId == id) running else null }?.cancelAndJoin()
            if (t.sessionId != 0L && t.state != UploadState.Processing) {
                runFeature(Unit) { Xani.media.abort(t.sessionId) }
            }
            remove(id)
            wake.trySend(Unit)
        }
    }

    // ─── engine ──────────────────────────────────────────────────────────────

    private suspend fun runLoop() {
        while (true) {
            val next = _tasks.value.firstOrNull { it.state == UploadState.Queued }
            if (next == null) {
                wake.receive()
                continue
            }
            val job = scope.launch {
                try {
                    runTask(next)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // A bug in one task must not stop the queue: record, fail it, move on.
                    runFeature(Unit) { throw t }
                    update(next.id) { it.copy(state = UploadState.Failed, error = "Something went wrong.", errorCode = "internal") }
                }
            }
            synchronized(lock) {
                running = job
                runningId = next.id
            }
            job.join()
            synchronized(lock) {
                running = null
                runningId = null
            }
            if (_tasks.value.none { !it.state.isTerminal }) {
                app?.let { UploadService.stopIfIdle(it) }
            }
        }
    }

    private suspend fun runTask(start: UploadTask) {
        update(start.id) { it.copy(state = UploadState.Uploading, error = "", errorCode = "") }
        val session = openSession(start) ?: return
        var task = current(start.id) ?: return
        if (session.state == "done") {
            // Killed between the last part and the ack: the server finished for us.
            finishComplete(task, session.postId)
            return
        }
        task = update(task.id) {
            it.copy(sessionId = session.id, chunkSize = session.chunkSize, parts = session.parts,
                done = session.received, bytesSent = bytesFor(it.size, session.chunkSize, session.parts, session.received))
        } ?: return
        if (!uploadParts(task)) return
        task = current(task.id) ?: return
        update(task.id) { it.copy(state = UploadState.Completing, bytesSent = it.size) }
        complete(task)
    }

    /** Opens (or re-attaches to) the session; an aborted one gets a fresh key. */
    private suspend fun openSession(task: UploadTask): UploadSession? {
        var t = task
        var attempt = 0
        var rotated = false
        while (true) {
            val r = Xani.media.open(
                UploadDeclaration(
                    idem = t.idem, kind = t.kind, mime = t.mime, size = t.size, width = t.width, height = t.height,
                    durationMs = t.durationMs, capturedAt = t.capturedAt,
                ),
            )
            val s = r.value
            if (s != null) {
                if (s.state == "aborted" && !rotated) {
                    rotated = true
                    t = rotateIdem(t.id) ?: return null
                    continue
                }
                return s
            }
            when {
                r.isSessionExpired -> {
                    park(t.id, r); return null
                }
                r.isTransport || r.statusCode >= 500 -> {
                    if (!backoff(attempt++)) { fail(t.id, r); return null }
                }
                else -> {
                    fail(t.id, r); return null
                }
            }
        }
    }

    /** Sends every missing part; true when all landed. */
    private suspend fun uploadParts(task: UploadTask): Boolean {
        val ctx = app ?: return false
        val missing = (0 until task.parts).filter { it !in task.done }
        if (missing.isEmpty()) return true
        val gate = Semaphore(parallelism)
        // First hard failure wins; workers still waiting on the gate see it and
        // skip, workers in a retry loop see it and stop.
        val failed = AtomicReference<ApiResponse<*>?>(null)
        coroutineScope {
            for (n in missing) {
                launch {
                    gate.withPermit {
                        ensureActive()
                        if (failed.get() != null) return@withPermit
                        val r = sendPart(ctx, task, n) { failed.get() != null }
                        if (r == null) {
                            update(task.id) { t ->
                                val done = t.done + n
                                t.copy(done = done, bytesSent = bytesFor(t.size, t.chunkSize, t.parts, done))
                            }
                        } else {
                            failed.compareAndSet(null, r)
                        }
                    }
                }
            }
        }
        val f = failed.get() ?: return true
        return when {
            f.code == STOPPED -> false
            f.isSessionExpired -> {
                park(task.id, f); false
            }
            f.code == "upload_closed" -> true // another device (or a retry) already completed it
            f.code == "not_found" || f.code == "upload_expired" -> {
                // Session vanished: start over with a new key, same file.
                rotateIdem(task.id)
                wake.trySend(Unit)
                false
            }
            else -> {
                fail(task.id, f); false
            }
        }
    }

    /**
     * One part: read the range, hash it, PUT it. Transport and 5xx failures back
     * off and retry indefinitely (the user can cancel); a damaged part is
     * re-sent three times; anything else is returned to the caller.
     */
    private suspend fun sendPart(ctx: Context, task: UploadTask, n: Int, stop: () -> Boolean): ApiResponse<*>? {
        var attempt = 0
        var damaged = 0
        while (true) {
            if (stop()) return ApiResponse.failure<Unit>(0, STOPPED, "")
            val bytes = readPart(ctx, task, n) ?: return ApiResponse.failure<Unit>(0, "unreadable", "The file could not be read.")
            val sha = sha256Hex(bytes)
            val r = Xani.media.putPart(task.sessionId, n, bytes, sha)
            if (r.ok) return null
            when {
                r.isTransport || r.statusCode >= 500 -> {
                    if (!backoff(attempt++)) return r
                }
                r.code == "checksum_mismatch" || r.code == "invalid_checksum" -> {
                    if (++damaged >= 3) return r
                }
                else -> return r
            }
        }
    }

    /** Gives the task a new server session key and forgets any progress. */
    private fun rotateIdem(id: String): UploadTask? = update(id) {
        it.copy(
            idem = UUID.randomUUID().toString().replace("-", ""),
            state = UploadState.Queued, sessionId = 0, chunkSize = 0, parts = 0, done = emptySet(), bytesSent = 0,
        )
    }

    private fun readPart(ctx: Context, task: UploadTask, n: Int): ByteArray? = runFeature(null) {
        val offset = n.toLong() * task.chunkSize
        val len = min(task.chunkSize.toLong(), task.size - offset).toInt()
        if (len <= 0) return@runFeature null
        ctx.contentResolver.openFileDescriptor(Uri.parse(task.uri), "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val buf = ByteBuffer.allocate(len)
                ch.position(offset)
                while (buf.hasRemaining()) {
                    if (ch.read(buf) < 0) break
                }
                if (buf.hasRemaining()) return@runFeature null
                buf.array()
            }
        }
    }

    private suspend fun complete(task: UploadTask) {
        var attempt = 0
        while (true) {
            val r = Xani.media.complete(task.sessionId, task.form)
            val post = r.value
            if (post != null) {
                finishComplete(task, post.id, post)
                // The grid reserves its "Posting…" tile from this same event.
                LiveFeed.emitPost(post)
                return
            }
            when {
                r.isSessionExpired -> {
                    park(task.id, r); return
                }
                r.isTransport || r.statusCode >= 500 -> {
                    if (!backoff(attempt++)) { fail(task.id, r); return }
                }
                r.code == "upload_incomplete" -> {
                    // Server disagrees on parts: re-read its bitmap and resume.
                    val s = Xani.media.status(task.sessionId).value
                    update(task.id) { it.copy(state = UploadState.Queued, done = s?.received ?: emptySet()) }
                    wake.trySend(Unit)
                    return
                }
                else -> {
                    fail(task.id, r); return
                }
            }
        }
    }

    private fun finishComplete(task: UploadTask, postId: Long, post: PostData? = null) {
        when {
            post?.isLive == true -> update(task.id) {
                it.copy(state = UploadState.Posted, postId = postId, bytesSent = it.size, thumbKey = post.thumbKey.orEmpty())
            }.also { scheduleAutoDismiss(task.id) }
            post?.isFailed == true -> update(task.id) {
                it.copy(state = UploadState.Failed, postId = postId, error = post.error.ifBlank { "Processing failed." }, errorCode = "processing_failed")
            }
            else -> {
                update(task.id) { it.copy(state = UploadState.Processing, postId = postId, bytesSent = it.size) }
                ensureWatch()
            }
        }
    }

    // ─── processing watch ────────────────────────────────────────────────────

    private fun onPostEvent(post: PostData) {
        val t = _tasks.value.firstOrNull { it.postId == post.id && it.state == UploadState.Processing } ?: return
        finishComplete(t, post.id, post)
    }

    /**
     * While anything is processing, hold the shared live stream so the server's
     * "live" event arrives the moment the ladder is ready, and re-read once a
     * minute as a safety net for a stream that could not connect.
     */
    private fun ensureWatch() {
        synchronized(lock) {
            if (watch?.isActive == true) return
            if (_tasks.value.none { it.state == UploadState.Processing }) return
            watch = scope.launch {
                val hold = launch { LiveFeed.hold() }
                try {
                    while (_tasks.value.any { it.state == UploadState.Processing }) {
                        delay(45_000)
                        refreshProcessing()
                    }
                } finally {
                    hold.cancel()
                }
            }
        }
    }

    private suspend fun refreshProcessing() {
        for (t in _tasks.value.filter { it.state == UploadState.Processing && it.postId != 0L }) {
            Xani.media.post(t.postId).value?.let {
                finishComplete(t, it.id, it)
                if (it.isLive || it.isFailed) LiveFeed.emitPost(it)
            }
        }
    }

    private fun scheduleAutoDismiss(id: String) {
        scope.launch {
            delay(POSTED_LINGER_MS)
            dismiss(id)
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun current(id: String): UploadTask? = _tasks.value.firstOrNull { it.id == id }

    private fun update(id: String, f: (UploadTask) -> UploadTask): UploadTask? {
        var out: UploadTask? = null
        _tasks.update { list ->
            list.map { t -> if (t.id == id) f(t).also { out = it } else t }
        }
        out?.let { store?.save(it) }
        return out
    }

    private fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
        store?.delete(id)
        app?.let { if (_tasks.value.none { t -> !t.state.isTerminal }) UploadService.stopIfIdle(it) }
    }

    private fun fail(id: String, r: ApiResponse<*>) {
        update(id) { it.copy(state = UploadState.Failed, error = r.message, errorCode = r.code) }
    }

    /** Session over: keep the task, stop working; [resume] after sign-in picks it up. */
    private fun park(id: String, r: ApiResponse<*>) {
        update(id) { it.copy(state = UploadState.Queued, error = r.message, errorCode = r.code) }
    }

    /**
     * Jittered exponential backoff that also waits for a network to exist.
     * Returns false after too many consecutive server failures (transport
     * failures never give up: the file is on the device and the user can cancel).
     */
    private suspend fun backoff(attempt: Int): Boolean {
        if (attempt >= MAX_SERVER_ATTEMPTS && online()) return false
        val base = min(MIN_BACKOFF_MS shl min(attempt, 6), MAX_BACKOFF_MS)
        delay(base + (0..500).random())
        while (!online()) delay(3_000)
        return true
    }

    private fun online(): Boolean {
        val cm = app?.getSystemService(ConnectivityManager::class.java) ?: return true
        return cm.activeNetwork != null
    }

    private fun bytesFor(size: Long, chunk: Int, parts: Int, done: Set<Int>): Long {
        if (chunk <= 0 || parts <= 0) return 0
        var total = 0L
        for (n in done) {
            total += if (n == parts - 1) size - chunk.toLong() * (parts - 1) else chunk.toLong()
        }
        return total.coerceIn(0, size)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = CharArray(d.size * 2)
        val hex = "0123456789abcdef"
        for (i in d.indices) {
            val v = d[i].toInt() and 0xff
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0f]
        }
        return String(out)
    }

    private const val MIN_BACKOFF_MS = 1_000L
    private const val MAX_BACKOFF_MS = 60_000L
    private const val MAX_SERVER_ATTEMPTS = 8
    private const val POSTED_LINGER_MS = 4_500L
    private const val STOPPED = "stopped"
}
