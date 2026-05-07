package yuku.alkitab.base.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yuku.alkitab.base.audio.model.VerseTiming

/**
 * Maps an audio playback position to the currently-active 1-based verse number.
 *
 * Owns a [StateFlow] of the active `verse_1` (`0` when no verse is active —
 * either because the chapter hasn't reached verse 1 yet, or because no timing
 * data is loaded). The UI layer (M3) collects this flow to drive the verse
 * highlight and auto-scroll.
 *
 * Polling cadence (positionMs updates) is owned by the caller — typically the
 * service drives this at 100ms while the player is playing. This class is
 * intentionally pull-based on its position input rather than tying itself to
 * a Player; that keeps it deterministic and unit-testable.
 *
 * Optimisation: we cache the index of the last verse that matched (`lastIndex`)
 * so monotonic forward progress costs O(1) lookups (just a bound check on the
 * current cell). Backwards seeks fall through to a binary search.
 *
 * Threading: not synchronised — call `setTiming` and `update` from the same
 * thread (in practice, the main thread on the service side).
 */
class HighlightTracker {

    private val _verse1 = MutableStateFlow(0)
    val verse1: StateFlow<Int> = _verse1.asStateFlow()

    private var verses: List<VerseTiming> = emptyList()

    /** Hint into [verses]; -1 means no cached hit yet. */
    private var lastIndex: Int = -1

    /**
     * Replaces the timing data the tracker resolves against. Resets the active
     * verse to `0` and clears the binary-search hint.
     */
    fun setTiming(verses: List<VerseTiming>) {
        this.verses = verses
        lastIndex = -1
        _verse1.value = 0
    }

    /**
     * Updates the active verse based on [positionMs] (the player's current
     * position). Emits a new value into [verse1] only if the resolved verse
     * actually changed.
     */
    fun update(positionMs: Long) {
        val resolved = resolveVerse(positionMs)
        if (resolved != _verse1.value) {
            _verse1.value = resolved
        }
    }

    /**
     * Returns the `verse_1` whose `[startMs, endMs)` window contains
     * [positionMs], or `0` if no verse matches.
     *
     * Strategy:
     *  1. If [lastIndex] is valid and the position still falls inside that
     *     verse's window, return it without a search. (Steady-state hit.)
     *  2. If the position has advanced past [lastIndex] but lies inside a
     *     later verse, walk forward — bounded by the size of the gap, but
     *     usually 1 step. (Common forward-progress hit.)
     *  3. Otherwise binary-search the full list. (Cold start, rewind, or
     *     seek across many verses.)
     */
    private fun resolveVerse(positionMs: Long): Int {
        if (verses.isEmpty()) return 0

        // Fast path: still inside the cached verse.
        val li = lastIndex
        if (li in verses.indices) {
            val cached = verses[li]
            if (positionMs >= cached.startMs && positionMs < cached.endMs) {
                return cached.verse_1
            }
            // Forward walk — also fast for monotonic playback at 100ms ticks.
            if (positionMs >= cached.endMs) {
                var i = li + 1
                while (i < verses.size) {
                    val v = verses[i]
                    if (positionMs < v.startMs) {
                        // Position is in a gap between verses.
                        lastIndex = -1
                        return 0
                    }
                    if (positionMs < v.endMs) {
                        lastIndex = i
                        return v.verse_1
                    }
                    i++
                }
                // Past the last verse.
                lastIndex = -1
                return 0
            }
            // Backwards seek — fall through to binary search.
        }

        return binarySearch(positionMs)
    }

    private fun binarySearch(positionMs: Long): Int {
        var lo = 0
        var hi = verses.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = verses[mid]
            when {
                positionMs < v.startMs -> hi = mid - 1
                positionMs >= v.endMs -> lo = mid + 1
                else -> {
                    lastIndex = mid
                    return v.verse_1
                }
            }
        }
        lastIndex = -1
        return 0
    }
}
