package app.xani.feature.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import app.xani.core.device.DeviceClass
import app.xani.core.isolate.runFeature
import app.xani.core.media.ImageLoader
import app.xani.core.net.ApiResponse
import app.xani.core.net.CommentData
import app.xani.core.net.LiveFeed
import app.xani.core.net.MediaConfig
import app.xani.core.net.PostData
import app.xani.core.net.PostPage
import app.xani.core.net.Xani
import app.xani.feature.profile.ProfileStore
import app.xani.feature.social.FollowStore
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
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

/**
 * The For You feed as the app sees it.
 *
 * Open: the last head page paints from disk before any request, then the network
 * page replaces it. Scroll: the next page is requested while the user is still
 * four cards away, so the pager never waits; posters for the next three cards are
 * warmed into the image cache as each page lands. Interact: like/save flip the
 * card and the count immediately, the server's true count snaps in with the ack,
 * and a failure rolls the card back. A post the user publishes arrives over the
 * live stream and is inserted at the head, so their own upload is in For You the
 * moment the ladder is ready.
 */
object FeedRepo {
    private const val PREFS = "xani.feed"
    private const val KEY_HEAD = "head"
    private const val KEY_USER = "user"
    private const val LOAD_AHEAD = 4
    private const val WARM_AHEAD = 3
    /** Anything older than this is re-read when Home comes back on screen. */
    private const val STALE_MS = 90_000L

    /**
     * Share of a clip that has to be watched before it counts as a view — 20
     * seconds of a 100-second video. The server reports the same number in
     * /v1/config; this is the floor if that read has not landed yet.
     */
    private const val VIEW_THRESHOLD_PCT = 20

    @Immutable
    data class State(
        val items: List<FeedPost> = emptyList(),
        val cursor: String = "",
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        /** True when the visible page came from disk and the network has not answered yet. */
        val fromCache: Boolean = false,
        val offline: Boolean = false,
        val loaded: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _comments = MutableStateFlow<Map<Long, List<FeedComment>>>(emptyMap())
    val comments: StateFlow<Map<Long, List<FeedComment>>> = _comments.asStateFlow()

    /**
     * Server counters per post, independent of which list the post is in.
     *
     * The feed patches its own cards, but a post opened full-screen from a
     * profile grid is not in the feed list at all — without this it would sit on
     * the numbers it was opened with while everyone else's likes landed. Bounded
     * so a long session cannot accumulate an entry per card ever scrolled past.
     */
    private val _counts = MutableStateFlow<Map<Long, LiveCounts>>(emptyMap())
    val counts: StateFlow<Map<Long, LiveCounts>> = _counts.asStateFlow()

    private const val COUNTS_KEPT = 64

    private fun noteCounts(postId: Long, fn: (LiveCounts) -> LiveCounts) {
        _counts.update { m ->
            val next = LinkedHashMap<Long, LiveCounts>(m.size + 1)
            // Re-insert so the freshest post is last and the oldest falls off.
            for ((k, v) in m) if (k != postId) next[k] = v
            next[postId] = fn(m[postId] ?: LiveCounts())
            while (next.size > COUNTS_KEPT) next.remove(next.keys.first())
            next
        }
    }

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var maxVideoHeight = 720

    @Volatile
    private var userId = 0L
    private var loadJob: Job? = null

    /** Wall-clock of the last successful head read from the server; 0 = never this process. */
    @Volatile
    private var revalidatedAt = 0L

    /** The pager's settled index; a refresh only swaps the list when the user is at the top. */
    @Volatile
    private var currentPage = 0

    /** A fresh head fetched while the user was scrolled deep; applied when they return to the top. */
    private var pendingHead: Pair<List<FeedPost>, String>? = null

    /** Rung ceiling per device class: a Low phone decodes 360p, not 1080p. */
    private fun rungFor(device: DeviceClass) = when (device) {
        DeviceClass.Low -> 360
        DeviceClass.Mid -> 480
        DeviceClass.High -> 720
    }

    fun attach(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val app = context.applicationContext
            ImageLoader.attach(app)
            maxVideoHeight = rungFor(DeviceClass.read(app))
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            userId = p.getLong(KEY_USER, 0L)
            restore(p)
            scope.launch { LiveFeed.posts.collect(::onPostEvent) }
            scope.launch { LiveFeed.postsGone.collect(::onPostGone) }
            // Engagement on the signed-in account's own posts. Everyone else's
            // counters arrive on the watched post's topic; these arrive here
            // because nobody is watching their own post while a stranger does.
            scope.launch {
                LiveFeed.counts.collect { e ->
                    applyCounts(
                        e.postId,
                        LiveCounts(
                            likes = e.counts.likes,
                            comments = e.counts.comments,
                            saves = e.counts.saves,
                            shares = e.counts.shares,
                            views = e.counts.views,
                        ),
                    )
                }
            }
            // My picture changed (here or on another device): repaint my own
            // cards so the face on my posts is the one I just set, without
            // re-reading the feed.
            scope.launch {
                ProfileStore.state.collect { me -> applyMyAvatar(MediaConfig.avatarUrl(me.avatarUrl)) }
            }
        }
    }

    private fun applyMyAvatar(url: String) {
        val id = userId
        if (id == 0L) return
        _state.update { s ->
            if (s.items.none { it.authorId == id && it.creator.avatarUrl != url }) return@update s
            s.copy(
                items = s.items.map { post ->
                    if (post.authorId != id || post.creator.avatarUrl == url) post
                    else post.copy(creator = post.creator.copy(avatarUrl = url))
                },
            )
        }
    }

    /** Called by the session: the head cache belongs to one account. */
    fun signedIn(id: Long) {
        if (userId != 0L && userId != id) {
            _state.value = State()
            revalidatedAt = 0L
            pendingHead = null
            prefs?.edit()?.remove(KEY_HEAD)?.apply()
        }
        userId = id
        prefs?.edit()?.putLong(KEY_USER, id)?.apply()
    }

    fun signedOut() {
        userId = 0L
        revalidatedAt = 0L
        pendingHead = null
        _state.value = State()
        _comments.value = emptyMap()
        _counts.value = emptyMap()
        counted.clear()
        synchronized(commentJobs) {
            commentCursor.clear()
            commentSort.clear()
        }
        unwatch()
        FollowStore.signedOut()
        prefs?.edit()?.remove(KEY_HEAD)?.remove(KEY_USER)?.apply()
    }

    /**
     * Home came on screen. The cache (or the user's own post) may already be
     * painted, but that is not the server's word: read the head if this process
     * has never done so, or if the last read is older than [STALE_MS].
     */
    fun ensureLoaded() {
        if (_state.value.refreshing) return
        if (revalidatedAt == 0L || System.currentTimeMillis() - revalidatedAt > STALE_MS) refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        scope.launch {
            val r = Xani.media.feed()
            val page = r.value
            if (page != null) {
                revalidatedAt = System.currentTimeMillis()
                prefs?.edit()?.putString(KEY_HEAD, page.raw)?.apply()
                val items = page.items.map(::toCard)
                val s = _state.value
                // Swapping the list under a user who is ten cards deep would jump
                // them; keep the fresh head until they are back at the top.
                if (currentPage <= 0 || s.items.isEmpty() || s.fromCache) {
                    pendingHead = null
                    applyHead(items, page.next)
                } else {
                    pendingHead = items to page.next
                    _state.update { it.copy(refreshing = false, offline = false, loaded = true) }
                }
                warm(items, 0)
            } else {
                _state.update { it.copy(refreshing = false, offline = r.isOffline, loaded = it.items.isNotEmpty()) }
            }
        }
    }

    private fun applyHead(items: List<FeedPost>, next: String) {
        _state.update { s ->
            // Anything already on screen that the head no longer lists (a post the
            // user just published that is newer than the cached head) stays in front.
            val ids = items.mapTo(HashSet()) { it.id }
            val keepAhead = s.items.filter { it.id !in ids && it.id > (items.firstOrNull()?.id ?: 0L) }
            s.copy(items = keepAhead + items, cursor = next, refreshing = false, fromCache = false, offline = false, loaded = true)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.cursor.isEmpty() || loadJob?.isActive == true) return
        _state.update { it.copy(loading = true) }
        loadJob = scope.launch {
            val r = Xani.media.feed(cursor = s.cursor)
            val page = r.value
            if (page != null) {
                val seen = HashSet<Long>(s.items.size + page.items.size)
                _state.update { cur ->
                    cur.items.forEach { seen.add(it.id) }
                    val fresh = page.items.map(::toCard).filter { seen.add(it.id) }
                    cur.copy(items = cur.items + fresh, cursor = page.next, loading = false)
                }
                warm(_state.value.items, s.items.size)
            } else {
                _state.update { it.copy(loading = false, offline = r.isOffline) }
            }
        }
    }

    /** The pager's settled index; drives prefetch, the next page, and a held head. */
    fun onPage(index: Int) {
        currentPage = index
        if (index == 0) {
            pendingHead?.let { (items, next) ->
                pendingHead = null
                applyHead(items, next)
            }
        }
        val s = _state.value
        if (index >= s.items.size - LOAD_AHEAD) loadMore()
        warm(s.items, index + 1)
        s.items.getOrNull(index)?.let { watchVisible(it.id) }
    }

    // ─── realtime for the card on screen ─────────────────────────────────────

    /**
     * The post the user is looking at right now. Holding its channel is what
     * makes somebody else's like land on this screen: the card is repainted from
     * the server's counters while it is visible, with no polling and no re-read
     * of the feed.
     *
     * Exactly one subscription at a time, and it is keyed by post, so the pager
     * moving, the comment sheet opening on the same card, and a full-screen
     * viewer all share one socket instead of stacking three.
     */
    private var watchJob: Job? = null

    @Volatile
    private var watching = 0L

    fun watchVisible(postId: Long) {
        if (postId == watching && watchJob?.isActive == true) return
        watching = postId
        watchJob?.cancel()
        watchJob = if (postId > 0) watch(postId) else null
    }

    /** The last surface showing that post went away: stop paying for the channel. */
    fun unwatch() {
        watching = 0L
        watchJob?.cancel()
        watchJob = null
    }

    private fun warm(items: List<FeedPost>, from: Int) {
        for (i in from until minOf(items.size, from + WARM_AHEAD)) {
            val p = items[i]
            if (p.posterUrl.isNotEmpty()) ImageLoader.prefetch(p.posterUrl, 1080)
        }
    }

    // ─── views ───────────────────────────────────────────────────────────────

    /**
     * Posts this process has already counted a watch for, so a looping clip
     * sends one request and not one per lap. It is a hint, not the rule: the
     * server stores one row per viewer per post, so the count stays honest
     * across reinstalls and across devices no matter what this set says.
     */
    private val counted = Collections.synchronizedSet(mutableSetOf<Long>())

    /**
     * Playback progress for the clip on screen, sampled twice a second by
     * [app.xani.core.media.FeedVideo].
     *
     * A view is [VIEW_THRESHOLD_PCT] of the clip watched — 20 seconds of a
     * 100-second video. Position, not elapsed time, is what is measured, so
     * scrubbing to the end does not earn a view and leaving the phone paused
     * does not either.
     */
    fun noteWatch(postId: Long, positionMs: Long, durationMs: Long) {
        if (postId <= 0 || durationMs <= 0) return
        if (positionMs * 100 < durationMs * VIEW_THRESHOLD_PCT) return
        if (!counted.add(postId)) return
        scope.launch {
            val r = Xani.media.view(postId)
            // A failure that is not "your session ended" means the watch was real
            // but unrecorded; allow the next lap to try again.
            if (!r.ok && !r.isSessionExpired) counted.remove(postId)
        }
    }

    // ─── reactions ───────────────────────────────────────────────────────────

    fun toggleLike(id: Long) = react(id, like = true)

    fun toggleSave(id: Long) = react(id, like = false)

    /**
     * Turns the like on and leaves it on. A double tap on a post is affection
     * repeated, not a switch: doing it twice must not take the like back.
     */
    fun likeOn(id: Long) {
        if (_state.value.items.firstOrNull { it.id == id }?.liked == true) return
        react(id, like = true)
    }

    private fun react(id: Long, like: Boolean) {
        val before = _state.value.items.firstOrNull { it.id == id } ?: return
        val on = if (like) !before.liked else !before.saved
        patch(id) { p ->
            if (like) p.copy(liked = on, likeCount = (p.likeCount + if (on) 1 else -1).coerceAtLeast(0))
            else p.copy(saved = on, saveCount = (p.saveCount + if (on) 1 else -1).coerceAtLeast(0))
        }
        // The owner's Liked and Saved tabs are these rows: they move now, and
        // roll back with the feed card if the server refuses.
        LiveFeed.emitReaction(id, like, on)
        scope.launch {
            val r = if (like) Xani.media.like(id, on) else Xani.media.save(id, on)
            val ack = r.value
            if (ack != null) {
                patch(id) { p -> if (like) p.copy(liked = ack.on, likeCount = ack.count) else p.copy(saved = ack.on, saveCount = ack.count) }
                if (ack.on != on) LiveFeed.emitReaction(id, like, ack.on)
            } else if (!r.isSessionExpired) {
                // Roll back to what the server last told us.
                patch(id) { p ->
                    if (like) p.copy(liked = before.liked, likeCount = before.likeCount)
                    else p.copy(saved = before.saved, saveCount = before.saveCount)
                }
                LiveFeed.emitReaction(id, like, if (like) before.liked else before.saved)
            }
        }
    }

    private fun patch(id: Long, fn: (FeedPost) -> FeedPost) {
        _state.update { s ->
            val i = s.items.indexOfFirst { it.id == id }
            if (i < 0) s else s.copy(items = s.items.toMutableList().also { it[i] = fn(it[i]).withCounts() })
        }
    }

    // ─── comments ────────────────────────────────────────────────────────────

    /** postId → cursor for the next (older) page; absent = not loaded, "" = all loaded. */
    private val commentCursor = HashMap<Long, String>()
    private val commentJobs = HashMap<Long, Job>()

    /** postId → the order its list is currently in, so paging stays consistent. */
    private val commentSort = HashMap<Long, String>()

    /**
     * Reads the first page of a thread. [sort] is "top" or "" for newest; the
     * cursor belongs to one order, so changing it discards the list and starts
     * over rather than interleaving two orders.
     */
    fun loadComments(postId: Long, sort: String = "") {
        val reload = synchronized(commentJobs) {
            if (commentJobs[postId]?.isActive == true && commentSort[postId] == sort) return
            val changed = commentSort[postId] != sort
            commentSort[postId] = sort
            changed
        }
        if (reload) _comments.update { it - postId }
        val job = scope.launch {
            val r = Xani.media.comments(postId, sort = sort)
            r.value?.let { (list, next) ->
                synchronized(commentJobs) { commentCursor[postId] = next }
                _comments.update { m ->
                    // A comment that arrived live while this read was in flight stays.
                    val live = m[postId].orEmpty().filter { c -> list.none { it.id == c.id } }
                    m + (postId to live + list.map(::toComment))
                }
            }
        }
        synchronized(commentJobs) { commentJobs[postId] = job }
    }

    /** The sheet reached its last row: fetch the next older page, if any. */
    fun loadMoreComments(postId: Long) {
        val (cursor, sort) = synchronized(commentJobs) {
            if (commentJobs[postId]?.isActive == true) return
            (commentCursor[postId] ?: return) to commentSort[postId].orEmpty()
        }
        if (cursor.isEmpty()) return
        val job = scope.launch {
            val r = Xani.media.comments(postId, cursor = cursor, sort = sort)
            r.value?.let { (list, next) ->
                synchronized(commentJobs) { commentCursor[postId] = next }
                _comments.update { m ->
                    val cur = m[postId].orEmpty()
                    val seen = cur.mapTo(HashSet()) { it.id }
                    m + (postId to cur + list.map(::toComment).filter { seen.add(it.id) })
                }
            }
        }
        synchronized(commentJobs) { commentJobs[postId] = job }
    }

    fun addComment(postId: Long, body: String, parentId: Long = 0, imageKey: String = "") {
        val text = body.trim()
        if (text.isEmpty() && imageKey.isEmpty()) return
        scope.launch {
            val r = Xani.media.comment(postId, text, parentId, imageKey)
            r.value?.let { (c, count) -> receiveComment(c, count) }
        }
    }

    /**
     * Flips the caller's heart on one comment: the row moves before the request
     * leaves, the server count snaps it straight (or back, on failure).
     */
    fun toggleCommentLike(postId: Long, commentId: Long) {
        var turnedOn = false
        patchComment(postId, commentId) { c ->
            turnedOn = !c.liked
            c.copy(liked = turnedOn, likes = (c.likes + if (turnedOn) 1 else -1).coerceAtLeast(0))
        }
        scope.launch {
            val r = Xani.media.likeComment(postId, commentId, turnedOn)
            val likes = r.value
            if (likes != null) {
                patchComment(postId, commentId) { it.copy(likes = likes) }
            } else if (!r.isSessionExpired) {
                patchComment(postId, commentId) { c ->
                    c.copy(liked = !turnedOn, likes = (c.likes + if (turnedOn) -1 else 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun patchComment(postId: Long, commentId: Long, f: (FeedComment) -> FeedComment) {
        _comments.update { m ->
            val cur = m[postId] ?: return@update m
            m + (postId to cur.map { if (it.id == commentId) f(it) else it })
        }
    }

    /**
     * Holds the post's realtime channel while its comment sheet is open: every
     * comment from any viewer lands in the list as it is written, and the counters
     * move with the room. Cancel the returned job when the sheet closes.
     */
    fun watch(postId: Long): Job = scope.launch {
        Xani.media.postStream(
            postId,
            onComment = { c, count -> receiveComment(c, count) },
            onCounts = { data -> applyCounts(postId, data) },
            onCommentLike = { commentId, likes ->
                // Another viewer's heart: only the number moves; my own flag is mine.
                patchComment(postId, commentId) { it.copy(likes = likes) }
            },
        )
    }

    /**
     * A counter frame for one post. The server sends the whole rail, so one frame
     * is enough to repaint likes, comments and saves together; the per-kind keys
     * are the fallback for a server that predates that.
     */
    private fun applyCounts(postId: Long, data: JSONObject) {
        val all = data.optJSONObject("counts")
        val likes = all?.optInt("likes", -1) ?: data.optInt("like", -1)
        val saves = all?.optInt("saves", -1) ?: data.optInt("save", -1)
        val comments = all?.optInt("comments", -1) ?: -1
        val shares = all?.optInt("shares", -1) ?: -1
        val views = all?.optLong("views", -1L) ?: -1L
        applyCounts(postId, LiveCounts(likes, comments, saves, shares, views))
    }

    /**
     * Applies a counter frame, whatever channel it arrived on: the post's own
     * topic while the viewer watches it, or the author's profile stream when a
     * stranger is the one engaging. A `-1` field means the server did not say,
     * so the surface keeps the number it had rather than painting a zero.
     */
    private fun applyCounts(postId: Long, c: LiveCounts) {
        patch(postId) { p ->
            p.copy(
                likeCount = if (c.likes >= 0) c.likes else p.likeCount,
                saveCount = if (c.saves >= 0) c.saves else p.saveCount,
                commentCount = if (c.comments >= 0) c.comments else p.commentCount,
                shareCount = if (c.shares >= 0) c.shares else p.shareCount,
                viewCount = if (c.views >= 0) c.views else p.viewCount,
            )
        }
        noteCounts(postId) { prev ->
            prev.copy(
                likes = if (c.likes >= 0) c.likes else prev.likes,
                saves = if (c.saves >= 0) c.saves else prev.saves,
                comments = if (c.comments >= 0) c.comments else prev.comments,
                shares = if (c.shares >= 0) c.shares else prev.shares,
                views = if (c.views >= 0) c.views else prev.views,
            )
        }
    }

    /** One entry point for a comment from our own send and from the stream: dedupe by id. */
    private fun receiveComment(c: CommentData, count: Int) {
        val postId = c.postId
        _comments.update { m ->
            val cur = m[postId].orEmpty()
            if (cur.any { it.id == c.id }) m else m + (postId to listOf(toComment(c)) + cur)
        }
        if (count > 0) {
            patch(postId) { it.copy(commentCount = count) }
            noteCounts(postId) { it.copy(comments = count) }
        }
    }

    // ─── author edits ────────────────────────────────────────────────────────

    /**
     * Saves an author's edit. The new caption is on screen before the request
     * leaves (the grid and any open card patch from the same event), and the
     * server's copy replaces it when the ack lands.
     */
    fun edit(
        postId: Long,
        caption: String? = null,
        visibility: String? = null,
        allowComments: Boolean? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        scope.launch {
            val r = Xani.media.editPost(postId, caption, visibility, allowComments)
            val post = r.value
            if (post != null) LiveFeed.emitPost(post)
            onDone(post != null)
        }
    }

    /** Retires the author's post, then drops it from every surface at once. */
    fun delete(postId: Long, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val r = Xani.media.deletePost(postId)
            val ok = r.value != null
            if (ok) LiveFeed.emitPostGone(postId)
            onDone(ok)
        }
    }

    // ─── live ────────────────────────────────────────────────────────────────

    /**
     * The author's own post went live: put it at the head so it shows for them at
     * once. This is a local insert, not a server read — it never marks the feed
     * as revalidated, so the next Home open still asks for everyone else's posts.
     */
    private fun onPostEvent(post: PostData) {
        // An edit can also take a post out of For You: made private, or hidden.
        if (post.isLive && post.visibility != "public") {
            onPostGone(post.id)
            return
        }
        if (!post.isLive) {
            onPostGone(post.id)
            return
        }
        val card = toCard(post)
        _state.update { s ->
            val i = s.items.indexOfFirst { it.id == post.id }
            // Already on screen: this is an edit, so swap it in place. Replacing
            // the card rather than the list keeps the pager's position.
            if (i >= 0) s.copy(items = s.items.toMutableList().also { it[i] = card })
            else s.copy(items = listOf(card) + s.items, loaded = true)
        }
        pendingHead?.let { (items, next) ->
            val i = items.indexOfFirst { it.id == post.id }
            pendingHead = if (i >= 0) items.toMutableList().also { it[i] = card } to next
            else (listOf(card) + items) to next
        }
    }

    /** The author retired a post: drop it wherever it is showing. */
    private fun onPostGone(postId: Long) {
        _state.update { s ->
            if (s.items.none { it.id == postId }) s else s.copy(items = s.items.filter { it.id != postId })
        }
        _comments.update { if (it.containsKey(postId)) it - postId else it }
        pendingHead?.let { (items, next) -> pendingHead = items.filter { it.id != postId } to next }
        if (watching == postId) unwatch()
    }

    // ─── mapping ─────────────────────────────────────────────────────────────

    private fun restore(p: SharedPreferences) {
        val raw = p.getString(KEY_HEAD, null) ?: return
        val page = runFeature<PostPage?>(null) { PostPage.from(JSONObject(raw)) } ?: return
        val items = page.items.map(::toCard)
        _state.value = State(items = items, cursor = page.next, fromCache = true, loaded = items.isNotEmpty())
    }

    internal fun toCard(p: PostData): FeedPost {
        var a = p.author
        if (p.authorId != 0L && p.authorId == userId) {
            // My own post. The profile cache is fresher than the row the post
            // was written with, so the card carries the name and the picture I
            // have right now — including a post published before my first
            // avatar, which would otherwise show initials forever.
            val me = ProfileStore.state.value
            a = a.copy(
                username = a.username.ifEmpty { me.username },
                fullName = a.fullName.ifEmpty { me.displayName },
                avatarUrl = me.avatarUrl.ifEmpty { a.avatarUrl },
                verified = a.verified || me.verified,
            )
        }
        if (p.authorId != 0L && p.authorId != userId) FollowStore.learn(p.authorId, p.following)
        val handle = if (a.username.isNotEmpty()) "@${a.username}" else "@user"
        val name = a.fullName.ifBlank { a.username }
        val kind = when (p.kind) {
            "photo" -> FeedKind.Photo
            "text" -> FeedKind.Text
            else -> FeedKind.Video
        }
        return FeedPost(
            id = p.id,
            creator = Creator(
                handle = handle,
                name = name,
                verified = a.verified,
                initials = initials(name.ifBlank { a.username }),
                color = paletteFor(p.authorId),
                avatarUrl = MediaConfig.avatarUrl(a.avatarUrl),
            ),
            description = p.caption,
            sound = if (kind == FeedKind.Video) {
                val who = name.ifBlank { handle.removePrefix("@") }.ifBlank { "user" }
                "Original sound – $who"
            } else {
                ""
            },
            likes = "", comments = "", shares = "", saves = "",
            kind = kind,
            authorId = p.authorId,
            posterUrl = MediaConfig.url(p.posterKey),
            videoUrl = if (kind == FeedKind.Video) MediaConfig.url(p.videoKey(maxVideoHeight)) else "",
            aspect = p.aspect,
            liked = p.liked,
            saved = p.saved,
            likeCount = p.counts.likes,
            commentCount = p.counts.comments,
            saveCount = p.counts.saves,
            shareCount = p.counts.shares,
            viewCount = p.counts.views,
            durationMs = p.durationMs.toLong(),
            allowComments = p.allowComments,
            following = p.following,
        ).withCounts()
    }

    private fun FeedPost.withCounts() = copy(
        likes = compact(likeCount.toLong()),
        comments = compact(commentCount.toLong()),
        saves = compact(saveCount.toLong()),
        shares = compact(shareCount.toLong()),
    )

    private fun toComment(c: CommentData): FeedComment {
        var user = c.author.username
        var avatar = c.author.avatarUrl
        if (c.author.id != 0L && c.author.id == userId) {
            val me = ProfileStore.state.value
            if (user.isEmpty()) user = me.username
            // My own comment, posted optimistically: the server has not echoed
            // the row yet, so my picture comes from the profile cache.
            if (avatar.isEmpty()) avatar = me.avatarUrl
        }
        return FeedComment(
            id = c.id,
            user = user.ifBlank { "user" },
            text = c.body,
            time = relativeTime(c.createdAt),
            likes = c.likes,
            color = paletteFor(c.author.id),
            parentId = c.parentId,
            liked = c.liked,
            imageUrl = if (c.imageKey.isEmpty()) "" else MediaConfig.url(c.imageKey),
            authorId = c.author.id,
            avatarUrl = MediaConfig.avatarUrl(avatar),
        )
    }

    private val palette = longArrayOf(0xFF7C3AED, 0xFFEC4899, 0xFF0EA5E9, 0xFFF59E0B, 0xFF10B981, 0xFF6366F1, 0xFFEF4444, 0xFFF472B6)

    internal fun paletteFor(id: Long): Long = palette[((id ushr 7) % palette.size).toInt().coerceAtLeast(0)]

    internal fun initials(name: String): String {
        val parts = name.trim().split(' ', '_', '.').filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "•"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    /** 847300 → "847.3K", 1200000 → "1.2M", 0 → "0". */
    internal fun compact(n: Long): String = when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> trim1(n / 1_000.0) + "K"
        n < 1_000_000_000 -> trim1(n / 1_000_000.0) + "M"
        else -> trim1(n / 1_000_000_000.0) + "B"
    }

    private fun trim1(v: Double): String {
        val s = String.format(Locale.US, "%.1f", v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    /** RFC3339 → "2h"; the sheet renders it verbatim. */
    internal fun relativeTime(rfc3339: String): String {
        val t = runFeature(0L) { java.time.Instant.parse(rfc3339).toEpochMilli() }
        if (t == 0L) return ""
        val d = (System.currentTimeMillis() - t).coerceAtLeast(0) / 1000
        return when {
            d < 60 -> "now"
            d < 3600 -> "${d / 60}m"
            d < 86400 -> "${d / 3600}h"
            d < 86400 * 7 -> "${d / 86400}d"
            else -> "${d / (86400 * 7)}w"
        }
    }

    private val <T> ApiResponse<T>.isOffline: Boolean get() = isTransport
}
