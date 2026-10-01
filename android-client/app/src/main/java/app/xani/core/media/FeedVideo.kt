package app.xani.core.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.isolate.runFeature
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * One looping feed clip on a hardware surface.
 *
 * Lifecycle: the player prepares as soon as the page is composed — the pager keeps
 * one neighbour composed, so the next clip has buffered before the swipe lands —
 * and starts only when [playing] is true. The poster underneath stays visible
 * until the decoder reports its first rendered frame, so there is never a black
 * flash and never a spinner. Everything blocking (prepare, release) runs on a
 * single background executor; the UI thread only flips flags.
 */
@Composable
fun FeedVideo(
    url: String,
    playing: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onFirstFrame: () -> Unit = {},
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
) {
    val appContext = LocalContext.current.applicationContext
    val holder = remember(url) { PlayerHolder(url, appContext) }
    var rendered by remember(url) { mutableStateOf(false) }

    LaunchedEffect(holder) {
        holder.onFirstFrame = {
            rendered = true
            onFirstFrame()
        }
    }
    LaunchedEffect(holder, playing) { holder.setPlaying(playing) }
    LaunchedEffect(holder, muted) { holder.setMuted(muted) }
    // Watch time is sampled, not observed: MediaPlayer has no position
    // callback, and a poll on the frame clock would cost a read per frame for a
    // number that only has to be roughly right. The loop exists only while this
    // clip is the one playing, so a composed neighbour costs nothing.
    LaunchedEffect(holder, playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_SAMPLE_MS)
            val (position, duration) = holder.progress() ?: continue
            onProgress(position, duration)
        }
    }
    DisposableEffect(holder) { onDispose { holder.release() } }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            holder.attach(Surface(st))
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            holder.detach()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (rendered) 1f else 0f },
        )
    }
}

/**
 * How often playback position is sampled. Half a second is fine enough to spot
 * a 20% threshold on the shortest clip the product allows and coarse enough to
 * be invisible in a trace.
 */
private const val PROGRESS_SAMPLE_MS = 500L

private val playerExec = Executors.newSingleThreadExecutor { r -> Thread(r, "xani-video").apply { isDaemon = true } }

/**
 * [url] is an https rendition in the feed, or a `content://` / `file://` URI for
 * a local preview (capture, gallery pick) — the latter needs a context to resolve.
 */
private class PlayerHolder(private val url: String, private val context: Context) {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var released = false
    private var wantPlaying = false
    private var wantMuted = false

    @Volatile
    var onFirstFrame: () -> Unit = {}

    init {
        playerExec.execute { create() }
    }

    private fun create() = runFeature(Unit) {
        if (released) return@runFeature
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        mp.isLooping = true
        mp.setOnPreparedListener {
            synchronized(this) {
                prepared = true
                applyVolume()
                if (wantPlaying && surface != null) runFeature(Unit) { it.start() }
            }
        }
        mp.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame()
            true
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.w("FeedVideo", "error what=$what extra=$extra")
            true // swallow: the poster stays; no crash, no dialog
        }
        if (url.startsWith("content://") || url.startsWith("file://")) {
            mp.setDataSource(context, Uri.parse(url))
        } else {
            mp.setDataSource(url)
        }
        synchronized(this) {
            player = mp
            surface?.let { mp.setSurface(it) }
        }
        mp.prepareAsync()
    }

    fun attach(s: Surface) = synchronized(this) {
        surface = s
        val mp = player ?: return
        runFeature(Unit) {
            mp.setSurface(s)
            if (prepared && wantPlaying && !mp.isPlaying) mp.start()
        }
    }

    fun detach() = synchronized(this) {
        surface?.release()
        surface = null
        runFeature(Unit) { player?.setSurface(null) }
    }

    fun setPlaying(on: Boolean) = synchronized(this) {
        wantPlaying = on
        val mp = player ?: return
        if (!prepared) return
        runFeature(Unit) {
            if (on && surface != null && !mp.isPlaying) mp.start()
            if (!on && mp.isPlaying) mp.pause()
        }
    }

    fun setMuted(on: Boolean) = synchronized(this) {
        wantMuted = on
        applyVolume()
    }

    /**
     * Playback position and clip length, or null before the decoder knows
     * either. Both getters are local reads on a prepared player, so this is
     * safe to call from the sampling loop without hopping threads.
     */
    fun progress(): Pair<Long, Long>? = synchronized(this) {
        val mp = player ?: return null
        if (!prepared || released) return null
        runCatching {
            val duration = mp.duration.toLong()
            if (duration <= 0) return null
            mp.currentPosition.toLong() to duration
        }.getOrNull()
    }

    private fun applyVolume() {
        val v = if (wantMuted) 0f else 1f
        runFeature(Unit) { player?.setVolume(v, v) }
    }

    fun release() {
        synchronized(this) {
            released = true
            wantPlaying = false
        }
        playerExec.execute {
            runFeature(Unit) {
                val mp = synchronized(this) { player.also { player = null } }
                mp?.reset()
                mp?.release()
            }
        }
    }
}
