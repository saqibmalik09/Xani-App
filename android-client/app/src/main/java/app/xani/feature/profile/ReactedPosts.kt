package app.xani.feature.profile

import androidx.compose.runtime.Immutable
import app.xani.core.media.ImageLoader
import app.xani.core.net.LiveFeed
import app.xani.core.net.MediaConfig
import app.xani.core.net.PostCounts
import app.xani.core.net.PostData
import app.xani.core.net.PostReaction
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
 * The owner's "Liked" and "Saved for later" grids, keyset paged from
 * `/v1/posts/liked` and `/v1/posts/saved`.
 *
 * These lists are the reaction rows themselves, so a tap anywhere in the app
 * moves them: un-liking from the feed drops the cell here without a read, and
 * a new like marks the tab stale so the next visit picks it up in order. A
 * post whose author deletes it leaves both lists on the shared post bus.
 */
class ReactedPosts private constructor(private val like: Boolean) {

    @Immutable
    data class State(
        val items: List<ProfilePost> = emptyList(),
        val cursor: String = "",
        val loading: Boolean = false,
        val loaded: Boolean = false,
        /** The wire post behind each cell, so a tap opens it with no re-read. */
        val raw: Map<Long, PostData> = emptyMap(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var listening = false

    /** True when a reaction arrived for a post this list does not hold yet. */
    @Volatile
    private var stale = false

    /** Whose reactions these are, so a switched account never sees them. */
    @Volatile
    private var owner = 0L

    /** Called when the tab comes on screen; costs a read only when it has to. */
    fun open() {
        val me = Xani.session.currentUserId
        if (me != owner) {
            owner = me
            job?.cancel()
            job = null
            stale = false
            _state.value = State()
        }
        synchronized(this) {
            if (!listening) {
                listening = true
                scope.launch { LiveFeed.reactions.collect(::onReaction) }
                scope.launch { LiveFeed.postsGone.collect(::onGone) }
                scope.launch { LiveFeed.counts.collect { onCounts(it.postId, it.counts) } }
            }
        }
        if (!_state.value.loaded || stale) refresh()
    }

    fun signedOut() {
        job?.cancel()
        job = null
        stale = false
        owner = 0L
        _state.value = State()
    }

    fun refresh() {
        if (job?.isActive == true) return
        stale = false
        _state.update { it.copy(loading = true) }
        job = scope.launch {
            val page = (if (like) Xani.media.liked() else Xani.media.saved()).value
            if (page == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val items = page.items.map(::cell)
            _state.value = State(items = items, cursor = page.next, loaded = true, raw = page.items.associateBy { it.id })
            items.take(12).forEach { if (it.thumbUrl.isNotEmpty()) ImageLoader.prefetch(it.thumbUrl, 360) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.cursor.isEmpty() || job?.isActive == true) return
        _state.update { it.copy(loading = true) }
        job = scope.launch {
            val before = s.cursor.toLongOrNull() ?: 0L
            val page = (if (like) Xani.media.liked(before) else Xani.media.saved(before)).value
            if (page == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            _state.update { cur ->
                val seen = cur.items.mapTo(HashSet()) { it.id }
                cur.copy(
                    items = cur.items + page.items.map(::cell).filter { seen.add(it.id) },
                    cursor = page.next,
                    loading = false,
                    raw = cur.raw + page.items.associateBy { it.id },
                )
            }
        }
    }

    private fun onReaction(r: PostReaction) {
        if (r.like != like) return
        if (!r.on) {
            onGone(r.postId)
            return
        }
        // Added: its place in the list is the server's, so take the next open
        // of the tab rather than guess at the order here.
        if (r.postId !in _state.value.raw) stale = true
    }

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
                raw = if (post != null) s.raw + (postId to post) else s.raw,
            )
        }
    }

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

    companion object {
        val Liked = ReactedPosts(like = true)
        val Saved = ReactedPosts(like = false)

        fun signedOut() {
            Liked.signedOut()
            Saved.signedOut()
        }
    }
}
