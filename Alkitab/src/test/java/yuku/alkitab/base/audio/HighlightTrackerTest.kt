package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import yuku.alkitab.base.audio.model.VerseTiming

/**
 * Pure-logic tests for [HighlightTracker]. No Android framework needed — the
 * class is a plain Kotlin object over `StateFlow`, and `StateFlow.value`
 * resolves synchronously in the same thread.
 */
class HighlightTrackerTest {

    /** Three contiguous verses: 0–1000, 1000–2000, 2000–3000. */
    private val contiguous = listOf(
        VerseTiming(verse_1 = 1, startMs = 0L, endMs = 1000L),
        VerseTiming(verse_1 = 2, startMs = 1000L, endMs = 2000L),
        VerseTiming(verse_1 = 3, startMs = 2000L, endMs = 3000L),
    )

    @Test
    fun `with no timing set, every position resolves to 0 (no-highlight sentinel)`() {
        val t = HighlightTracker()
        t.update(0L)
        assertEquals(0, t.verse1.value)
        t.update(50_000L)
        assertEquals(0, t.verse1.value)
    }

    @Test
    fun `setTiming with empty list keeps verse_1 at 0`() {
        val t = HighlightTracker()
        t.setTiming(emptyList())
        t.update(500L)
        assertEquals(0, t.verse1.value)
    }

    @Test
    fun `monotonic forward progress steps through each verse exactly once`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)

        // Walk position from 0ms to 3000ms in 100ms steps. Capture every
        // distinct value emitted by verse1.
        val emitted = mutableListOf<Int>()
        var last = -1
        for (ms in 0..3000 step 100) {
            t.update(ms.toLong())
            val now = t.verse1.value
            if (now != last) {
                emitted.add(now)
                last = now
            }
        }

        // We expect 1, 2, 3, 0 — each verse for its window, then 0 once we
        // pass endMs of the last verse (3000ms is exactly at the boundary).
        assertEquals(listOf(1, 2, 3, 0), emitted)
    }

    @Test
    fun `position before the first verse start resolves to 0`() {
        val t = HighlightTracker()
        // First verse starts at 500ms.
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 1, startMs = 500L, endMs = 1500L),
                VerseTiming(verse_1 = 2, startMs = 1500L, endMs = 2500L),
            )
        )
        t.update(0L)
        assertEquals(0, t.verse1.value)
        t.update(499L)
        assertEquals(0, t.verse1.value)
        t.update(500L)
        assertEquals(1, t.verse1.value)
    }

    @Test
    fun `rewind across verses resolves correctly via fallback search`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)

        // Forward to verse 3.
        t.update(2500L)
        assertEquals(3, t.verse1.value)
        // Rewind hard to verse 1.
        t.update(100L)
        assertEquals(1, t.verse1.value)
        // Forward again to verse 2.
        t.update(1500L)
        assertEquals(2, t.verse1.value)
    }

    @Test
    fun `seek past the last verse end resolves to 0`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        t.update(500L)
        assertEquals(1, t.verse1.value)
        // Seek to a position past the last verse's endMs.
        t.update(5000L)
        assertEquals(0, t.verse1.value)
    }

    @Test
    fun `gap between non-contiguous verses resolves to 0`() {
        val t = HighlightTracker()
        // Two verses with a 500ms gap: 0–1000, 1500–2500.
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 1, startMs = 0L, endMs = 1000L),
                VerseTiming(verse_1 = 2, startMs = 1500L, endMs = 2500L),
            )
        )
        t.update(500L)
        assertEquals(1, t.verse1.value)
        // Walk into the gap.
        t.update(1200L)
        assertEquals(0, t.verse1.value)
        // Out the other side.
        t.update(1700L)
        assertEquals(2, t.verse1.value)
    }

    @Test
    fun `setTiming clears the previously active verse`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        t.update(500L)
        assertEquals(1, t.verse1.value)
        t.setTiming(emptyList())
        assertEquals(0, t.verse1.value)
    }

    @Test
    fun `update only emits when the resolved verse actually changes`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)

        // First update: 0 -> 1, must emit.
        t.update(100L)
        val firstSnapshot = t.verse1.value
        // Multiple updates inside the same verse: must NOT emit a new value.
        t.update(200L)
        t.update(500L)
        t.update(999L)
        assertEquals(firstSnapshot, t.verse1.value)
        assertEquals(1, t.verse1.value)
    }

    @Test
    fun `position exactly at endMs of one verse resolves to the next verse`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        // [0, 1000) is verse 1; 1000 itself is verse 2 (endMs is exclusive).
        t.update(1000L)
        assertEquals(2, t.verse1.value)
    }

    // -- peekVerseAt -----------------------------------------------------------

    @Test
    fun `peekVerseAt does not mutate the cached lastIndex or the verse1 flow`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        t.update(500L)
        assertEquals(1, t.verse1.value)

        // Peek deep into a different verse — should NOT touch the StateFlow.
        assertEquals(3, t.peekVerseAt(2500L))
        assertEquals(1, t.verse1.value)

        // And the next steady-state forward walk should still hit the fast
        // path (verse 1 -> verse 2 in one step), proving the cached lastIndex
        // wasn't invalidated.
        t.update(1100L)
        assertEquals(2, t.verse1.value)
    }

    @Test
    fun `peekVerseAt returns 0 for positions in gaps and outside the timing range`() {
        val t = HighlightTracker()
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 1, startMs = 0L, endMs = 1000L),
                VerseTiming(verse_1 = 2, startMs = 1500L, endMs = 2500L),
            )
        )
        assertEquals(0, t.peekVerseAt(-100L))
        assertEquals(1, t.peekVerseAt(500L))
        assertEquals(0, t.peekVerseAt(1200L)) // gap
        assertEquals(2, t.peekVerseAt(2000L))
        assertEquals(0, t.peekVerseAt(9999L))
    }

    @Test
    fun `peekVerseAt on empty timing returns 0`() {
        val t = HighlightTracker()
        assertEquals(0, t.peekVerseAt(500L))
    }

    // -- getVerseStartMs -------------------------------------------------------

    @Test
    fun `getVerseStartMs returns the startMs of the matching verse`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        assertEquals(0L, t.getVerseStartMs(1))
        assertEquals(1000L, t.getVerseStartMs(2))
        assertEquals(2000L, t.getVerseStartMs(3))
    }

    @Test
    fun `getVerseStartMs returns null when the verse has no timing entry`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        assertEquals(null, t.getVerseStartMs(14))
        assertEquals(null, t.getVerseStartMs(0))
    }

    @Test
    fun `getVerseStartMs returns null when no timing is loaded`() {
        val t = HighlightTracker()
        assertEquals(null, t.getVerseStartMs(1))
    }

    @Test
    fun `getVerseStartMs honors a non-contiguous verse start offset`() {
        val t = HighlightTracker()
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 13, startMs = 0L, endMs = 1000L),
                VerseTiming(verse_1 = 14, startMs = 1500L, endMs = 2500L),
            )
        )
        assertEquals(1500L, t.getVerseStartMs(14))
    }

    // -- getNextVerseStartMs ---------------------------------------------------

    @Test
    fun `getNextVerseStartMs returns the start of the verse strictly after the position`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        // Inside verse 1 -> next is verse 2's start.
        assertEquals(1000L, t.getNextVerseStartMs(500L))
        // At the start of verse 2 -> next is verse 3.
        assertEquals(2000L, t.getNextVerseStartMs(1000L))
        // Inside verse 3 -> no next.
        assertEquals(null, t.getNextVerseStartMs(2500L))
        // Past the last verse -> no next.
        assertEquals(null, t.getNextVerseStartMs(9999L))
    }

    @Test
    fun `getNextVerseStartMs from a gap targets the upcoming verse`() {
        val t = HighlightTracker()
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 1, startMs = 0L, endMs = 1000L),
                VerseTiming(verse_1 = 2, startMs = 1500L, endMs = 2500L),
            )
        )
        assertEquals(1500L, t.getNextVerseStartMs(1200L))
    }

    // -- getPrevVerseStartMs ---------------------------------------------------

    @Test
    fun `getPrevVerseStartMs restarts the current verse when deep into it`() {
        val t = HighlightTracker()
        // Use long verses so we can sit > 2 s past startMs and trigger the
        // "restart current" branch of the threshold.
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 1, startMs = 0L, endMs = 5_000L),
                VerseTiming(verse_1 = 2, startMs = 5_000L, endMs = 10_000L),
            )
        )
        // 3 s into verse 2 -> restart verse 2.
        assertEquals(5_000L, t.getPrevVerseStartMs(8_000L))
    }

    @Test
    fun `getPrevVerseStartMs jumps to the prior verse when near the start of the current one`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        // 100 ms into verse 2 -> jump to verse 1's start.
        assertEquals(0L, t.getPrevVerseStartMs(1100L))
        // Exactly at verse 2's start -> jump to verse 1.
        assertEquals(0L, t.getPrevVerseStartMs(1000L))
    }

    @Test
    fun `getPrevVerseStartMs returns null when there is no earlier verse`() {
        val t = HighlightTracker()
        t.setTiming(contiguous)
        // At the very first verse, can't go further back.
        assertEquals(null, t.getPrevVerseStartMs(100L))
    }

    @Test
    fun `getPrevVerseStartMs from a gap targets the most recent finished verse`() {
        val t = HighlightTracker()
        t.setTiming(
            listOf(
                VerseTiming(verse_1 = 1, startMs = 0L, endMs = 1000L),
                VerseTiming(verse_1 = 2, startMs = 1500L, endMs = 2500L),
            )
        )
        // In the gap between 1 and 2 -> targets verse 1.
        assertEquals(0L, t.getPrevVerseStartMs(1200L))
    }
}
