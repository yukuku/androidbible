package yuku.alkitab.base.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yuku.alkitab.base.audio.model.VerseTiming

/**
 * Maps an audio playback position to the active 1-based verse number, `0` when
 * none is active. The UI collects [verse1] to drive the verse highlight and
 * auto-scroll.
 *
 * The caller owns the polling cadence (the service drives this at 100 ms while
 * playing); taking position as an argument rather than reading a Player keeps
 * the class deterministic and unit-testable.
 *
 * Not synchronised: call [setTiming] and [update] from the same thread.
 */
class HighlightTracker {

    private val _verse1 = MutableStateFlow(0)
    val verse1: StateFlow<Int> = _verse1.asStateFlow()

    private var verses: List<VerseTiming> = emptyList()

    /** Hint into [verses]; -1 means no cached hit yet. */
    private var lastIndex: Int = -1

    /** Resets the active verse and the search hint along with the timing. */
    fun setTiming(verses: List<VerseTiming>) {
        this.verses = verses
        lastIndex = -1
        _verse1.value = 0
    }

    /** Emits into [verse1] only when the resolved verse changes. */
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
     *  2. If the position has advanced past [lastIndex] but lies inside a later
     *     verse, walk forward, usually a single step. (Forward-progress hit.)
     *  3. Otherwise binary-search the full list. (Cold start, rewind, or seek
     *     across many verses.)
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
            // Forward walk, also fast for monotonic playback at 100 ms ticks.
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
            // Backwards seek; fall through to binary search.
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

    /**
     * Touches neither [lastIndex] nor [verse1], so the slider's drag preview can
     * ask what verse the thumb would land on without disturbing playback's
     * forward-walk fast path.
     */
    fun peekVerseAt(positionMs: Long): Int {
        if (verses.isEmpty()) return 0
        var lo = 0
        var hi = verses.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = verses[mid]
            when {
                positionMs < v.startMs -> hi = mid - 1
                positionMs >= v.endMs -> lo = mid + 1
                else -> return v.verse_1
            }
        }
        return 0
    }

    /** Null when no timing is loaded, or the chapter has none for that verse. */
    fun getVerseStartMs(verse_1: Int): Long? = verses.firstOrNull { it.verse_1 == verse_1 }?.startMs

    /**
     * "After" means strictly past the current verse window, so inside verse N the
     * target is N+1's start, and a tap during a gap jumps to the upcoming verse.
     */
    fun getNextVerseStartMs(positionMs: Long): Long? {
        if (verses.isEmpty()) return null
        return verses.firstOrNull { it.startMs > positionMs }?.startMs
    }

    /**
     * Follows the usual music-player behaviour. More than
     * [PREV_VERSE_RESTART_THRESHOLD_MS] into the current verse, it restarts that
     * verse; otherwise it skips back one. A tap during a gap between verses
     * targets the last finished verse.
     */
    fun getPrevVerseStartMs(positionMs: Long): Long? {
        if (verses.isEmpty()) return null

        val currentIdx = verses.indexOfFirst { positionMs >= it.startMs && positionMs < it.endMs }
        if (currentIdx >= 0) {
            val current = verses[currentIdx]
            if (positionMs - current.startMs > PREV_VERSE_RESTART_THRESHOLD_MS) {
                return current.startMs
            }
            return verses.getOrNull(currentIdx - 1)?.startMs
        }

        // In a gap: return the latest verse whose end is before us.
        return verses.lastOrNull { it.endMs <= positionMs }?.startMs
    }

    companion object {
        /**
         * "Restart-current vs. go-to-previous" cutoff for the prev-verse
         * button, in the 2-3 s range music players conventionally use. Picked at
         * the low end so a quick double-tap reliably skips two verses back.
         */
        const val PREV_VERSE_RESTART_THRESHOLD_MS = 2_000L
    }
}
