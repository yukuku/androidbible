package yuku.alkitab.songs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [SongFilter.matchRanges], the helper that drives the
 * search-result hit highlighting in the song search sheet.
 */
class SongFilterTest {
    private fun ranges(text: String, filter: String): List<Pair<Int, Int>> {
        val cf = SongFilter.compileFilter(filter)
        return SongFilter.matchRanges(text, cf).map { it[0] to it[1] }
    }

    @Test
    fun `matchRanges reports every case-insensitive occurrence of a token`() {
        // "holy" appears three times in "Holy holy holy".
        assertEquals(listOf(0 to 4, 5 to 9, 10 to 14), ranges("Holy holy holy", "holy"))
    }

    @Test
    fun `matchRanges covers each token of a multi-token filter`() {
        // Two tokens, one match each; both hits are reported regardless of token order.
        val result = ranges("Amazing grace how sweet", "grace amazing")
        assertTrue("expected a range for 'grace' in $result", result.contains(8 to 13))
        assertTrue("expected a range for 'amazing' in $result", result.contains(0 to 7))
    }

    @Test
    fun `matchRanges returns nothing for an empty filter`() {
        assertEquals(emptyList<Pair<Int, Int>>(), ranges("Anything", "   "))
    }

    @Test
    fun `matchRanges returns nothing when the token is absent`() {
        assertEquals(emptyList<Pair<Int, Int>>(), ranges("Amazing grace", "hosanna"))
    }
}
