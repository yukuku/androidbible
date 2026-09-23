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
    fun `numeric runs of different magnitudes return the difference in significant digit counts`() {
        assertEquals(-2, comparator.compare("5", "100"))
        assertEquals(2, comparator.compare("100", "5"))
    }

    @Test
    fun `leading zeros do not change the numeric value`() {
        assertTrue(comparator.compare("002", "10") < 0)
        assertTrue(comparator.compare("010", "9") > 0)
        assertTrue(comparator.compare("007", "8") < 0)
    }

    @Test
    fun `equal numbers with more leading zeros sort first`() {
        assertTrue(comparator.compare("01", "1") < 0)
        assertTrue(comparator.compare("1", "01") > 0)
        assertTrue(comparator.compare("001", "01") < 0)
        assertTrue(comparator.compare("00", "0") < 0)
    }

    @Test
    fun `equal numbers with the same leading zeros compare equal`() {
        assertEquals(0, comparator.compare("007", "007"))
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
        assertEquals(0x0663 - '5'.code, comparator.compare("\u0663\u0663", "5"))
    }

    @Test
    fun `sorting song codes puts them in natural order`() {
        val codes = mutableListOf("10", "a10", "9", "1b", "100", "2", "1a", "a2", "1")
        codes.sortWith(comparator)
        assertEquals(listOf("1", "1a", "1b", "2", "9", "10", "100", "a2", "a10"), codes)
    }

    @Test
    fun `sorting song codes with leading zeros puts them in natural order`() {
        val codes = mutableListOf("1", "01", "10", "001", "2", "010", "0", "00", "a01", "a1")
        codes.sortWith(comparator)
        assertEquals(listOf("00", "0", "001", "01", "1", "2", "010", "10", "a01", "a1"), codes)
    }
}
