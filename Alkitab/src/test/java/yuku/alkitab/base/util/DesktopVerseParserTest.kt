package yuku.alkitab.base.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import yuku.alkitabconverter.util.DesktopVerseParser

class DesktopVerseParserTest {
    @Test
    fun `legacy prose references retain inherited verses and single chapter notation`() {
        for ((text, expected) in listOf(
            "Read John 3:16-18 today" to intArrayOf(0x2a0310, 0x2a0312),
            "Gen 1:1, 3, 5" to intArrayOf(0x000101, 0x000101, 0x000103, 0x000103, 0x000105, 0x000105),
            "jud 9–12" to intArrayOf(0x400109, 0x40010c),
            "jud 0" to intArrayOf(0x400100, 0x400100),
            "Yl. 2:10, 31, 3:15" to intArrayOf(0x1c020a, 0x1c020a, 0x1c021f, 0x1c021f, 0x1c030f, 0x1c030f),
            "ps 9—12" to intArrayOf(0x120900, 0x120c00),
        )) {
            val actual = DesktopVerseParser.verseStringToAri(text)!!
            assertArrayEquals(text, expected, IntArray(actual.size()) { actual.get(it) })
        }
    }

    @Test
    fun `complete references reject incomplete or overflowing suffixes`() {
        for (text in listOf("John 1:1,", "John 1:1;", "John 1:1 junk", "John 1:256", "John 999999999999999999:1", "John 3:10-4", "John 0:1")) {
            assertNull(text, DesktopVerseParser.parseReference(text))
        }
    }

    @Test
    fun `book lookup normalizes punctuation case and repeated whitespace`() {
        assertEquals(53, DesktopVerseParser.bookIdFromName("  I.  TiMoTiUs.  "))
        assertEquals(42, DesktopVerseParser.bookIdFromName(" JOHN. "))
        assertEquals(-1, DesktopVerseParser.bookIdFromName("orchard"))
    }

    @Test
    fun `complete references share chapter lists and case insensitive separators`() {
        val parsed = DesktopVerseParser.parseReference("Mat.1 DAN 3")!!
        assertArrayEquals(intArrayOf(0x270100, 0x270100, 0x270300, 0x270300), IntArray(parsed.size()) { parsed.get(it) })
    }
}
