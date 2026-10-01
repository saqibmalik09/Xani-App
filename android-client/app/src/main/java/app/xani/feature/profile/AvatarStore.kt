package app.xani.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import app.xani.core.media.ImageLoader
import app.xani.core.media.PickedImage
import app.xani.core.net.MediaConfig
import app.xani.core.net.Xani
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The profile picture: its own store, its own endpoint, its own storage
 * namespace.
 *
 * A picture is not a post. It never enters the upload pipeline, never becomes
 * an asset, and never appears in a feed or a grid — so none of that machinery
 * is involved here, and a failed avatar cannot leave a half-published post
 * behind.
 *
 * How it feels instant: the picked image is cropped to a square on a background
 * thread and shown immediately as [State.preview], while the bytes go up. The
 * preview is only dropped once the stored picture is decoded and cached, so the
 * face on screen never flickers back to the old one in between. Every other
 * surface — feed cards, comment rows, the other devices on this account —
 * updates from the profile event the server publishes with the write.
 */
object AvatarStore {
    /**
     * Uploaded edge length. Matches what the server stores, so the picture the
     * user approves is the picture that is kept: no second resample, and the
     * upload is ~60 KB instead of the several MB a camera still would cost.
     */
    private const val EDGE = 512

    /** Quality for the upload. The server re-encodes, so this only has to be good enough to survive that once. */
    private const val QUALITY = 90

    @Immutable
    data class State(
        /**
         * The square the user just picked, shown while the upload is in flight.
         * Null when idle — every surface then reads the stored picture.
         */
        val preview: Bitmap? = null,
        val uploading: Boolean = false,
        /** Set when the last attempt failed, so the sheet can say why. */
        val error: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null
    private var job: Job? = null

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun signedOut() {
        job?.cancel()
        job = null
        _state.value = State()
    }

    /** Dismisses a failure the user has read. */
    fun clearError() {
        _state.update { it.copy(error = "") }
    }

    /**
     * Sets [uri] — a camera capture or a gallery pick — as the profile picture.
     *
     * A second call replaces the first: the user re-picking is them changing
     * their mind, not a queue, and the server names each upload by its own
     * revision so the last one to land is the one that sticks.
     */
    fun upload(uri: Uri) {
        val context = appContext ?: return
        job?.cancel()
        _state.value = State(uploading = true)
        job = scope.launch {
            val square = withContext(Dispatchers.IO) { square(context, uri) }
            if (square == null) {
                _state.value = State(error = READ_FAILED)
                return@launch
            }
            // On screen before the request leaves: the user sees their new face
            // while the bytes are still going up.
            _state.update { it.copy(preview = square) }

            val jpeg = withContext(Dispatchers.IO) { encode(square) }
            if (jpeg == null) {
                _state.value = State(error = READ_FAILED)
                return@launch
            }
            val response = Xani.profile.setAvatar(jpeg)
            val profile = response.value
            if (profile == null) {
                _state.value = State(error = if (response.isSessionExpired) "" else response.message)
                return@launch
            }
            // The stored picture is warmed before the preview is dropped, so the
            // swap is one frame with no gap and no flash of the old avatar.
            val url = MediaConfig.url(profile.avatarUrl.ifEmpty { null })
            if (url.isNotEmpty()) ImageLoader.load(url, EDGE)
            _state.value = State()
        }
    }

    private const val READ_FAILED = "That picture could not be read. Try another."

    /**
     * Decodes [uri] to an upright [EDGE]×[EDGE] square.
     *
     * Three things happen in order and all three matter: the image is sampled
     * down while decoding (so a 50-megapixel still never exists in memory at
     * full size), rotated to match its EXIF orientation (so a portrait selfie
     * is not stored on its side), and centre-cropped rather than squashed.
     */
    private fun square(context: Context, uri: Uri): Bitmap? {
        // Two edges' worth of headroom: the crop throws away one dimension, so
        // decoding at exactly EDGE would leave the square soft.
        val upright = PickedImage.decodeUpright(context, uri, EDGE * 2) ?: return null
        val out = PickedImage.cropTo(upright, EDGE, EDGE)
        if (out !== upright) upright.recycle()
        return out
    }

    private fun encode(bitmap: Bitmap): ByteArray? = PickedImage.encodeJpeg(bitmap, QUALITY)
}
