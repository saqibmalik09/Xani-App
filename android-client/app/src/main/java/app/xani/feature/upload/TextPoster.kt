package app.xani.feature.upload

import android.util.Log
import app.xani.core.net.LiveFeed
import app.xani.core.net.Xani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Text posts have no bytes to move, so they skip the upload queue: one request,
 * live on return. The result is fanned out on the post bus so For You and the
 * profile grid show it at once.
 */
internal object TextPoster {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun post(text: String, visibility: String = "public", onResult: (ok: Boolean, message: String) -> Unit = { _, _ -> }) {
        val body = text.trim()
        if (body.isEmpty()) return
        scope.launch {
            val r = Xani.media.postText(body, visibility)
            val post = r.value
            if (post != null) {
                LiveFeed.emitPost(post)
                onResult(true, r.message)
            } else {
                Log.w("TextPoster", "post failed: ${r.code}")
                onResult(false, r.message)
            }
        }
    }
}
