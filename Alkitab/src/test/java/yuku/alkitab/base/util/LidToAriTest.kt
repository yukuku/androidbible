package yuku.alkitab.base.util

import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LidToAriTest {
    @Test
    fun `lidToAri maps the first and last verses of the Bible`() {
        assertEquals(0x000101, LidToAri.lidToAri(1))
        assertEquals(0x411615, LidToAri.lidToAri(31102))
    }

    @Test
    fun `lidToAri maps verses inside and at the start of a chapter`() {
        assertEquals(0x00011f, LidToAri.lidToAri(31))
        assertEquals(0x000201, LidToAri.lidToAri(32))
        assertEquals(0x270101, LidToAri.lidToAri(23146))
    }

    @Test
    fun `lidToAri returns 0 for lids out of range`() {
        assertEquals(0, LidToAri.lidToAri(0))
        assertEquals(0, LidToAri.lidToAri(-1))
        assertEquals(0, LidToAri.lidToAri(31103))
    }

    @Test
    fun `lidToAri increases strictly and starts exactly 1189 chapters at verse 1`() {
        var previous = 0
        var chapterStarts = 0
        for (lid in 1..31102) {
            val ari = LidToAri.lidToAri(lid)
            assertTrue("lid $lid", ari > previous)
            if (ari and 0xff == 1) chapterStarts++
            previous = ari
        }
        assertEquals(1189, chapterStarts)
    }

    @Test
    fun `startLidForBookId and endLidForBookId bound each book`() {
        assertEquals(1, LidToAri.startLidForBookId(0))
        assertEquals(1533, LidToAri.endLidForBookId(0))
        assertEquals(1534, LidToAri.startLidForBookId(1))
        assertEquals(23145, LidToAri.endLidForBookId(38))
        assertEquals(23146, LidToAri.startLidForBookId(39))
        assertEquals(30699, LidToAri.startLidForBookId(65))
        assertEquals(31102, LidToAri.endLidForBookId(65))
    }

    @Test
    fun `the start lid of every book maps to chapter 1 verse 1 of that book`() {
        for (bookId in 0..65) {
            assertEquals((bookId shl 16) or 0x0101, LidToAri.lidToAri(LidToAri.startLidForBookId(bookId)))
        }
    }

    @Test
    fun `bookIdForLid finds the book containing a lid`() {
        assertEquals(0, LidToAri.bookIdForLid(1))
        assertEquals(0, LidToAri.bookIdForLid(1533))
        assertEquals(1, LidToAri.bookIdForLid(1534))
        assertEquals(39, LidToAri.bookIdForLid(23146))
        assertEquals(65, LidToAri.bookIdForLid(31102))
    }

    @Test
    fun `bookIdForLid returns -1 for lids out of range`() {
        assertEquals(-1, LidToAri.bookIdForLid(0))
        assertEquals(-1, LidToAri.bookIdForLid(-5))
        assertEquals(-1, LidToAri.bookIdForLid(31103))
    }

    @Test
    fun `every lookup over the whole lid range matches the recorded checksum`() {
        val crc = CRC32()
        fun add(value: Int) = crc.update(ByteBuffer.allocate(4).putInt(value).array())

        for (lid in -1..31103) {
            add(LidToAri.lidToAri(lid))
            add(LidToAri.bookIdForLid(lid))
        }
        for (bookId in 0..65) {
            add(LidToAri.startLidForBookId(bookId))
            add(LidToAri.endLidForBookId(bookId))
        }

        assertEquals(2786351239L, crc.value)
    }
}
