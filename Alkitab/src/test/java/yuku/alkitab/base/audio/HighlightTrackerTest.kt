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
}
