package app.xani.feature.gift

import android.media.AudioAttributes
import android.media.MediaPlayer
import app.xani.core.isolate.runFeature
import app.xani.core.media.AssetCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gift sound effects. One MediaPlayer for the whole app, reset between gifts,
 * prepared off the main thread from a disk-cached file. A gift whose sound is
 * not on disk yet plays silently rather than late — the animation must never
 * wait on audio, and audio must never arrive after the gift has left the
 * screen. Volume ducks under a live call's own audio session (USAGE_GAME so
 * the mixer treats it as short-lived SFX rather than media).
 */
internal object GiftSound {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var current: Job? = null
    private var generation = 0

    @Volatile
    var muted: Boolean = false

    fun prefetch(url: String) = AssetCache.prefetch(giftImageUrl(url))

    /** Play [url] once; a later call replaces the current sound. */
    fun play(rawUrl: String, maxMs: Long) {
        // A bare storage key resolves through the media config like every
        // other catalog asset; a relative path must not fail the download.
        val url = giftImageUrl(rawUrl)
        if (url.isEmpty() || muted) return
        val file = AssetCache.cached(url) ?: run { AssetCache.prefetch(url); return }
        current?.cancel()
        val gen = ++generation
        current = scope.launch {
            val p = player ?: newPlayer().also { player = it }
            runFeature(Unit) { p.reset() }
            val ok = withContext(Dispatchers.IO) { prepare(p, file) }
            if (!ok || gen != generation) return@launch
            runFeature(Unit) { p.start() }
        }
        // Belt and braces: never let a long sound outlive the gift.
        if (maxMs > 0) scope.launch {
            kotlinx.coroutines.delay(maxMs + 400)
            if (gen == generation) stop()
        }
    }

    fun stop() {
        current?.cancel()
        current = null
        generation++
        val p = player ?: return
        runFeature(Unit) { if (p.isPlaying) p.stop() }
        runFeature(Unit) { p.reset() }
    }

    /** Free the decoder when leaving the room; the next gift re-creates it. */
    fun release() {
        stop()
        runFeature(Unit) { player?.release() }
        player = null
    }

    private fun newPlayer(): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        setOnErrorListener { _, _, _ -> true }
    }

    private fun prepare(p: MediaPlayer, f: File): Boolean = runFeature(false) {
        p.setDataSource(f.absolutePath)
        p.prepare()
        true
    }
}
