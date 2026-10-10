package app.xani.feature.inbox

import android.media.AudioAttributes
import android.media.MediaPlayer
import app.xani.core.isolate.runFeature
import app.xani.core.media.AssetCache
import app.xani.core.net.AppForeground
import app.xani.core.net.MediaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * One voice-note player for the whole app. Tap a bubble to play; tap the same
 * bubble again (or another) to stop. Downloads go through [AssetCache] so a
 * replay never blocks the UI thread on the network.
 */
internal object VoicePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var generation = 0

    init {
        // A voice note is listened to, not left running: when the app goes to
        // the background the note stops rather than playing on over whatever
        // the user switched to. Tapping the bubble again replays it.
        scope.launch {
            AppForeground.visible.collect { visible -> if (!visible) stop() }
        }
    }

    private val _playingId = MutableStateFlow(0L)
    val playingId: StateFlow<Long> = _playingId.asStateFlow()

    fun toggle(msgId: Long, rawUrl: String?) {
        if (rawUrl.isNullOrBlank()) return
        if (_playingId.value == msgId) {
            stop()
            return
        }
        play(msgId, rawUrl)
    }

    fun play(msgId: Long, rawUrl: String) {
        if (rawUrl.isBlank()) return
        val url = when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") || rawUrl.startsWith("file:") -> rawUrl
            else -> MediaConfig.url(rawUrl)
        }
        if (url.isEmpty()) return
        stop()
        val gen = ++generation
        _playingId.value = msgId
        job = scope.launch {
            val file = resolve(url) ?: run {
                if (gen == generation) _playingId.value = 0L
                return@launch
            }
            if (gen != generation) return@launch
            val p = player ?: newPlayer().also { player = it }
            runFeature(Unit) { p.reset() }
            val ok = withContext(Dispatchers.IO) { prepare(p, file) }
            if (!ok || gen != generation) {
                if (gen == generation) _playingId.value = 0L
                return@launch
            }
            p.setOnCompletionListener {
                if (gen == generation) _playingId.value = 0L
            }
            runFeature(Unit) { p.start() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        generation++
        _playingId.value = 0L
        val p = player ?: return
        runFeature(Unit) { if (p.isPlaying) p.stop() }
        runFeature(Unit) { p.reset() }
    }

    fun release() {
        stop()
        runFeature(Unit) { player?.release() }
        player = null
    }

    private suspend fun resolve(url: String): File? {
        if (url.startsWith("file:")) {
            return runFeature(null) { File(URI(url)) }
        }
        return AssetCache.load(url)
    }

    private fun newPlayer(): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        setOnErrorListener { _, _, _ ->
            _playingId.value = 0L
            true
        }
    }

    private fun prepare(p: MediaPlayer, f: File): Boolean = runFeature(false) {
        p.setDataSource(f.absolutePath)
        p.prepare()
        true
    }
}
