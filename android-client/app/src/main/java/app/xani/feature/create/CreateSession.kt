package app.xani.feature.create

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Create draft + upload façade.
 *
 * Capture stays on-device and optimistic. Network work is queued for a later
 * outbox/worker — never blocks the UI thread, never waits on the camera hop.
 * When create APIs land, swap [CreateUploadTransport] only.
 */
internal enum class CreateDraftKind { Photo, Video, Text }

internal data class CreateDraft(
    val id: String = UUID.randomUUID().toString(),
    val kind: CreateDraftKind,
    val localUri: Uri? = null,
    val text: String = "",
    val durationSec: Int = 0,
    val lensId: Int = 0,
    val soundId: String? = null,
)

internal enum class CreateUploadState { Queued, Uploading, Done, Failed }

internal data class CreateUploadJob(
    val draft: CreateDraft,
    val state: CreateUploadState = CreateUploadState.Queued,
)

internal fun interface CreateUploadTransport {
    /** Fire-and-forget. Implementations must be non-blocking and idempotent. */
    fun enqueue(job: CreateUploadJob)
}

/** In-memory queue until outbox + resumable upload land. */
internal object CreateSession {
    private val queue = ConcurrentLinkedQueue<CreateUploadJob>()
    private var transport: CreateUploadTransport = CreateUploadTransport { job ->
        queue.offer(job)
    }

    var lastDraft by mutableStateOf<CreateDraft?>(null)
        private set

    fun remember(draft: CreateDraft) {
        lastDraft = draft
    }

    fun submit(draft: CreateDraft) {
        lastDraft = draft
        transport.enqueue(CreateUploadJob(draft))
    }

    fun bindTransport(next: CreateUploadTransport) {
        transport = next
    }

    fun pendingCount(): Int = queue.size
}

internal fun ShootMode.toDraftKind(): CreateDraftKind = when (this) {
    ShootMode.Photo -> CreateDraftKind.Photo
    ShootMode.Fifteen, ShootMode.Sixty, ShootMode.FiveMin -> CreateDraftKind.Video
}
