package app.xani.feature.profile

import androidx.compose.runtime.Immutable
import app.xani.core.media.ImageLoader
import app.xani.core.net.LiveFeed
import app.xani.core.net.MediaConfig
import app.xani.core.net.PostCounts
import app.xani.core.net.PostData
import app.xani.core.net.Xani
import app.xani.feature.home.FeedRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The signed-in user's own grid: every post in every state, newest first,
 * keyset paged from `/v1/posts/mine`. A post that goes live (or fails) arrives
 * over the shared stream and replaces its cell in place — the "Posting…" tile
 * becomes the real thumbnail with no re-read.
 */
object MyPosts {
    @Immutable
    data class State(
        val items: List<ProfilePost> = emptyList(),
        val cursor: String = "",
        val loading: Boolean = false,
        val loaded: Boolean = false,
        /** The wire post behind each cell, so a tap opens it full-screen with no re-read. */
        val raw: Map<Long, PostData> = emptyMap(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var userId = 0L
    private var job: Job? = null
    private var listening = false

    fun bind(id: Long) {
        synchronized(this) {
            if (!listening) {
                listening = true
                scope.launch { LiveFeed.posts.collect(::onEvent) }
                scope.launch { LiveFeed.postsGone.collect(::onGone) }
                scope.launch { LiveFeed.counts.collect { onCounts(it.postId, it.counts) } }
            }
        }
        if (userId != id) {
            userId = id
            _state.value = State()
        }
        if (!_state.value.loaded) refresh()
    }

    fun signedOut() {
        userId = 0L
        _state.value = State()
    }

    fun refresh() {
        if (job?.isActive == true) return
        _state.update { it.copy(loading = true) }
        job = scope.launch {
            val page = Xani.media.mine().value
            if (page != null) {
                val items = page.items.map(::cell)
                _state.value = State(items = items, cursor = page.next, loaded = true, raw = page.items.associateBy { it.id })
                items.take(12).forEach { if (it.thumbUrl.isNotEmpty()) ImageLoader.prefetch(it.thumbUrl, 360) }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.cursor.isEmpty() || job?.isActive == true) return
        _state.update { it.copy(loading = true) }
        job = scope.launch {
            val before = s.cursor.toLongOrNull() ?: 0L
            val page = Xani.media.mine(before = before).value
            if (page != null) {
                _state.update { cur ->
                    val seen = cur.items.mapTo(HashSet()) { it.id }
                    cur.copy(
                        items = cur.items + page.items.map(::cell).filter { seen.add(it.id) },
                        cursor = page.next,
                        loading = false,
                        raw = cur.raw + page.items.associateBy { it.id },
                    )
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun onEvent(p: PostData) {
        if (userId == 0L || (p.authorId != 0L && p.authorId != userId)) return
        if (p.state == "removed") {
            onGone(p.id)
            return
        }
        val c = cell(p)
        _state.update { s ->
            val i = s.items.indexOfFirst { it.id == p.id }
            // In place for an edit or a state change, at the head for a new post,
            // so an edited caption lands without the grid jumping.
            val items = if (i >= 0) s.items.toMutableList().also { it[i] = c } else listOf(c) + s.items
            s.copy(items = items, loaded = true, raw = s.raw + (p.id to p))
        }
    }

    /**
     * Somebody watched, liked or commented on one of these posts. The counters
     * are the whole rail, so the cell's view and like numbers move together
     * from one frame while the author has the grid open.
     */
    private fun onCounts(postId: Long, counts: PostCounts) {
        _state.update { s ->
            val i = s.items.indexOfFirst { it.id == postId }
            if (i < 0) return@update s
            val post = s.raw[postId]?.copy(counts = counts)
            s.copy(
                items = s.items.toMutableList().also {
                    it[i] = it[i].copy(
                        views = FeedRepo.compact(counts.views),
                        likes = FeedRepo.compact(counts.likes.toLong()),
                        rank = counts.likes.toFloat(),
                    )
                },
                // Keep the wire post in step: a tap opens the viewer on these numbers.
                raw = if (post != null) s.raw + (postId to post) else s.raw,
            )
        }
    }

    /** The author deleted this post: the cell goes, here and everywhere else. */
    private fun onGone(postId: Long) {
        _state.update { s ->
            if (s.items.none { it.id == postId }) s
            else s.copy(items = s.items.filter { it.id != postId }, raw = s.raw - postId)
        }
    }

    private fun cell(p: PostData) = ProfilePost(
        id = p.id,
        color = FeedRepo.paletteFor(p.id),
        views = FeedRepo.compact(p.counts.views),
        rank = p.counts.likes.toFloat(),
        thumbUrl = MediaConfig.url(p.thumbKey),
        likes = FeedRepo.compact(p.counts.likes.toLong()),
        kind = p.kind,
        state = p.state,
        caption = p.caption,
        isPrivate = p.visibility == "private",
        isRestricted = p.isRestricted,
        durationMs = p.durationMs,
    )
}
