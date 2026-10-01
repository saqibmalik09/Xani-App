package app.xani.feature.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * "Open this person", from anywhere.
 *
 * A profile is reachable from a feed card, a search hit, a follower row, a
 * visit row and a chat header — five surfaces on four different tabs. Threading
 * a callback through all of them would put navigation in every signature, so
 * the destination is one piece of app state instead and the shell renders it
 * over whichever tab is showing.
 *
 * It is a stack because profiles lead to profiles: their followers, then one of
 * those, then back. Depth is capped so a chain of taps cannot grow without
 * bound.
 */
object PersonRouter {
    private const val MAX_DEPTH = 8

    private val _stack = MutableStateFlow<List<Long>>(emptyList())

    /** Open profiles, oldest first; the last one is what is on screen. */
    val stack: StateFlow<List<Long>> = _stack.asStateFlow()

    fun open(userId: Long) {
        if (userId <= 0L) return
        _stack.update { current ->
            if (current.lastOrNull() == userId) current else (current + userId).takeLast(MAX_DEPTH)
        }
    }

    /** Closes the top profile. */
    fun back() {
        _stack.update { it.dropLast(1) }
    }

    /** Closes every open profile — a tab change, or signing out. */
    fun clear() {
        if (_stack.value.isNotEmpty()) _stack.value = emptyList()
    }
}
