package yuku.alkitab.base.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LevenshteinTest {
    @Test
    fun `empty strings are at distance zero`() {
        assertEquals(0, Levenshtein.distance("", ""))
    }

    @Test
    fun `deleting every character costs 500 each`() {
        assertEquals(1500, Levenshtein.distance("abc", ""))
    }

    @Test
    fun `inserting every character costs 100 each`() {
        assertEquals(200, Levenshtein.distance("", "ab"))
    }

    @Test
    fun `a matching character costs twice its position in the target`() {
        assertEquals(2, Levenshtein.distance("a", "a"))
    }

    @Test
    fun `a single substitution costs 400`() {
        assertEquals(400, Levenshtein.distance("a", "b"))
    }

    @Test
    fun `swapped characters are scored with the weighted costs`() {
        assertEquals(602, Levenshtein.distance("ab", "ba"))
    }

    @Test
    fun `book name prefixes are scored with the weighted costs`() {
        assertEquals(527, Levenshtein.distance("kej", "kejadian"))
        assertEquals(318, Levenshtein.distance("mat", "matius"))
        assertEquals(712, Levenshtein.distance("mat", "markus"))
        assertEquals(535, Levenshtein.distance("1kor", "1korintus"))
        assertEquals(532, Levenshtein.distance("yoh", "1yohanes"))
    }

    @Test
    fun `the distance is asymmetric because deletions cost more than insertions`() {
        assertEquals(2512, Levenshtein.distance("kejadian", "kej"))
    }
}
