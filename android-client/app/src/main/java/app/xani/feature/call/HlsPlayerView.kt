package app.xani.feature.call

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.xani.core.isolate.runFeature

/**
 * Plays the CDN live playlist with the platform decoder: hardware-decoded,
 * no third-party player, no CPU frame copies. Prepares off the main thread
 * (`prepareAsync`) and recovers from a stall by re-preparing the same URL,
 * which is what a live playlist wants — there is no position to resume.
 */
@Composable
fun HlsPlayerView(url: String, modifier: Modifier = Modifier, onState: (playing: Boolean) -> Unit = {}) {
    val player = remember(url) { HlsPlayer(url, onState) }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                sv.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = player.attach(holder)
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) = player.detach()
                })
            }
        },
        modifier = modifier,
    )
}

private class HlsPlayer(private val url: String, private val onState: (Boolean) -> Unit) {
    private var mp: MediaPlayer? = null
    private var holder: SurfaceHolder? = null
    private var released = false
    private var retries = 0

    fun attach(h: SurfaceHolder) {
        holder = h
        start()
    }

    fun detach() {
        holder = null
        stop()
    }

    fun release() {
        released = true
        stop()
    }

    private fun start() {
        if (released || url.isBlank()) return
        val h = holder ?: return
        stop()
        mp = runFeature(null) {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
                )
                setDisplay(h)
                setDataSource(url)
                setOnPreparedListener { it.start(); retries = 0; onState(true) }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) onState(false)
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) onState(true)
                    false
                }
                setOnErrorListener { _, _, _ ->
                    onState(false)
                    // A live edge that went away: back off and rejoin, bounded.
                    if (!released && retries < MAX_RETRIES) {
                        retries++
                        h.surface?.let { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start() }, 800L * retries) }
                    }
                    true
                }
                setOnCompletionListener { onState(false) }
                prepareAsync()
            }
        }
    }

    private fun stop() {
        mp?.let { p -> runFeature(Unit) { p.reset(); p.release() } }
        mp = null
    }

    private companion object {
        const val MAX_RETRIES = 8
    }
}
