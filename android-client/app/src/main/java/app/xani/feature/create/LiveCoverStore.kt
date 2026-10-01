package app.xani.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import app.xani.core.media.PickedImage
import app.xani.core.net.Xani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The picture on the host's card in the Live listing.
 *
 * Picked from the gallery, cut to the card's 3:4 frame at the size the server
 * stores (540×720, ~60 KB) on a background thread, shown at once as a
 * thumbnail in the setup form, and uploaded while the host keeps filling in
 * the form. Go Live sends only the returned key; the server refuses a key the
 * host did not upload.
 */
internal object LiveCoverStore {
    private const val W = 540
    private const val H = 720
    private const val QUALITY = 85

    @Immutable
    data class State(
        /** The cut picture, on screen from the moment it is picked. */
        val preview: Bitmap? = null,
        /** Storage key once the upload landed; empty while in flight or on failure. */
        val key: String = "",
        val uploading: Boolean = false,
        val error: String = "",
    ) {
        val ready: Boolean get() = key.isNotEmpty()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    /** A new pick replaces the previous one; the last upload to land is the card. */
    fun pick(context: Context, uri: Uri) {
        val app = context.applicationContext
        job?.cancel()
        _state.value = State(uploading = true)
        job = scope.launch {
            val cut = withContext(Dispatchers.IO) {
                val upright = PickedImage.decodeUpright(app, uri, H * 2) ?: return@withContext null
                val out = PickedImage.cropTo(upright, W, H)
                if (out !== upright) upright.recycle()
                out
            }
            if (cut == null) {
                _state.value = State(error = READ_FAILED)
                return@launch
            }
            _state.value = State(preview = cut, uploading = true)
            val jpeg = withContext(Dispatchers.IO) { PickedImage.encodeJpeg(cut, QUALITY) }
            if (jpeg == null) {
                _state.value = State(error = READ_FAILED)
                return@launch
            }
            var r = Xani.calls.liveCover(jpeg)
            if (!r.ok && r.isTransport) r = Xani.calls.liveCover(jpeg)
            val key = r.value
            if (!r.ok || key.isNullOrEmpty()) {
                _state.value = State(preview = cut, error = if (r.isSessionExpired) "" else r.message)
                return@launch
            }
            _state.value = State(preview = cut, key = key)
        }
    }

    /** Back to a tinted card. */
    fun clear() {
        job?.cancel()
        job = null
        _state.value = State()
    }

    private const val READ_FAILED = "That picture could not be read. Try another."
}
