package yuku.alkitab.base.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphanumComparatorTest {
    private val comparator: Comparator<String> = AlphanumComparator()

    @Test
    fun `numeric runs compare by numeric value rather than by characters`() {
        assertTrue(comparator.compare("2", "10") < 0)
        assertTrue(comparator.compare("10", "9") > 0)
        assertTrue(comparator.compare("a2", "a10") < 0)
    }

    @Test
    fun `equal length numeric runs return the difference of the first differing digit`() {
        assertEquals(-1, comparator.compare("12", "13"))
        assertEquals(7, comparator.compare("91", "21"))
    }

    @Test
    fun `numeric runs of different lengths return the length difference so leading zeros sort after`() {
        assertEquals(1, comparator.compare("01", "1"))
        assertEquals(-2, comparator.compare("5", "100"))
    }

    @Test
    fun `text runs compare case sensitively`() {
        assertEquals('A' - 'a', comparator.compare("A", "a"))
    }

    @Test
    fun `identical strings compare equal`() {
        assertEquals(0, comparator.compare("abc12", "abc12"))
        assertEquals(0, comparator.compare("", ""))
    }

    @Test
    fun `a string that is a chunk-wise prefix of another sorts first`() {
        assertEquals(-1, comparator.compare("1", "1a"))
        assertEquals(-1, comparator.compare("abc", "abcd"))
        assertEquals(3, comparator.compare("abc", ""))
    }

    @Test
    fun `only ASCII digits start a numeric run`() {
        // U+0663 is ARABIC-INDIC DIGIT THREE, which is compared as text against the ASCII "5"
        assertEquals(0x0663 - '5'.code, comparator.compare("٣٣", "5"))
    }

    @Test
    fun `sorting song codes puts them in natural order`() {
        val codes = mutableListOf("10", "a10", "9", "1b", "100", "2", "1a", "a2", "1")
        codes.sortWith(comparator)
        assertEquals(listOf("1", "1a", "1b", "2", "9", "10", "100", "a2", "a10"), codes)
    }
}
