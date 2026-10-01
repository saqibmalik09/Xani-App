package app.xani.feature.social

import androidx.compose.runtime.Immutable
import app.xani.core.net.PersonData
import app.xani.core.net.Xani
import app.xani.feature.profile.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Do I follow them" for every account the app has seen, and the one place a
 * follow is toggled from — the feed card, a search hit, a follower list — so a
 * tap on one surface flips the button on every other one at once.
 *
 * Optimistic: the relation flips on tap, the server ack (both counters) snaps it
 * to truth, a failure rolls it back. The signed-in user's own following count
 * comes back in the same ack and is pushed into the profile cache, so Profile
 * shows the new number without a refresh.
 */
object FollowStore {
    @Immutable
    data class Relation(val following: Boolean, val followsYou: Boolean = false, val followers: Int = -1)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _relations = MutableStateFlow<Map<Long, Relation>>(emptyMap())

    /** userId → relation, for every account seen in this process. */
    val relations: StateFlow<Map<Long, Relation>> = _relations.asStateFlow()

    fun isFollowing(userId: Long, fallback: Boolean = false): Boolean =
        _relations.value[userId]?.following ?: fallback

    /** Seed from anything the server said about a person (list rows, search hits). */
    fun learn(people: List<PersonData>) {
        if (people.isEmpty()) return
        _relations.update { m ->
            val out = HashMap(m)
            for (p in people) out[p.id] = Relation(p.following, p.followsYou, p.followers)
            out
        }
    }

    /** Seed from a post's viewer flags — the feed is where most follows begin. */
    fun learn(userId: Long, following: Boolean) {
        if (userId == 0L) return
        _relations.update { m ->
            // Server truth for the relation, but never overwrite a tap still in flight.
            if (m[userId]?.following == following) m else m + (userId to (m[userId]?.copy(following = following) ?: Relation(following)))
        }
    }

    fun toggle(userId: Long) {
        val before = _relations.value[userId] ?: Relation(false)
        set(userId, !before.following)
    }

    fun set(userId: Long, on: Boolean) {
        if (userId == 0L) return
        val before = _relations.value[userId] ?: Relation(false)
        if (before.following == on) return
        _relations.update { m ->
            val delta = if (on) 1 else -1
            m + (userId to before.copy(following = on, followers = if (before.followers >= 0) (before.followers + delta).coerceAtLeast(0) else -1))
        }
        scope.launch {
            val r = Xani.people.follow(userId, on)
            val ack = r.value
            if (ack != null) {
                _relations.update { m -> m + (userId to (m[userId] ?: before).copy(following = ack.following, followers = ack.followers)) }
                ProfileStore.setFollowing(ack.myFollowing)
            } else if (!r.isSessionExpired) {
                _relations.update { m -> m + (userId to before) }
            }
        }
    }

    fun signedOut() {
        _relations.value = emptyMap()
    }
}
