package yuku.alkitabconverter.util

import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopVerseParserReferenceTest {
    private fun assertPairs(text: String, vararg expected: Int) {
        val actual = DesktopVerseParser.parseReference(text)
        assertArrayEquals(text, expected, actual?.let { values -> IntArray(values.size()) { values.get(it) } })
    }

    @Test
    fun `whole books and chapters retain zero boundaries for version aware resolution`() {
        assertPairs("Genesis", 0x000000, 0x000000)
        assertPairs("  JOHN.  ", 0x2a0000, 0x2a0000)
        assertPairs("Kidung Agung", 0x150000, 0x150000)
        assertPairs("III John", 0x3f0000, 0x3f0000)
        assertPairs("Genesis 1", 0x000100, 0x000100)
        assertPairs("Matius 1-4", 0x270100, 0x270400)
        for ((name, book) in listOf("Obaja" to 30, "Philemon" to 56, "2 John" to 62, "3 John" to 63, "Jude" to 64)) {
            assertPairs("$name 1", (book shl 16) or 0x100, (book shl 16) or 0x100)
            assertPairs("$name 1:9-12", (book shl 16) or 0x109, (book shl 16) or 0x10c)
        }
    }

    @Test
    fun `disjoint verses inherit the preceding chapter including across a chapter range`() {
        assertPairs("Markus 9:5-6, 14-23", 0x280905, 0x280906, 0x28090e, 0x280917)
        assertPairs("John 3:16,18;4:1-3,5", 0x2a0310, 0x2a0310, 0x2a0312, 0x2a0312, 0x2a0401, 0x2a0403, 0x2a0405, 0x2a0405)
        assertPairs("Yl. 2:10, 31, 3:15", 0x1c020a, 0x1c020a, 0x1c021f, 0x1c021f, 0x1c030f, 0x1c030f)
        assertPairs("Matius 5:40-6:10,12", 0x270528, 0x27060a, 0x27060c, 0x27060c)
        assertPairs("Ibr  8:5;10:1", 0x390805, 0x390805, 0x390a01, 0x390a01)
    }

    @Test
    fun `chapter lists keep chapter semantics for every supported separator`() {
        for (separator in listOf(",", ";", " dan ", " DAN ", " Dan ")) {
            assertPairs("Mat.1${separator}3-5${separator}7", 0x270100, 0x270100, 0x270300, 0x270500, 0x270700, 0x270700)
        }
    }

    @Test
    fun `complete references accept Unicode dashes without changing legacy prose parsing`() {
        for (dash in listOf("-", "--", "–", "—")) {
            assertPairs("1 Timotius 6:6${dash}10", 0x350606, 0x35060a)
            assertPairs("ps 9${dash}12", 0x120900, 0x120c00)
            assertPairs("jud 1:9${dash}12", 0x400109, 0x40010c)
        }
    }

    @Test
    fun `punctuation and whitespace normalization apply to the complete reference`() {
        assertPairs("  I.  TiMoTiUs.  6 : 6 - 10  ", 0x350606, 0x35060a)
        assertPairs("John.3.16", 0x2a0310, 0x2a0310)
        assertPairs("John\t3:16", 0x2a0310, 0x2a0310)
        assertPairs("John\n3:16", 0x2a0310, 0x2a0310)
        assertPairs("Gen 255:255", 0x00ffff, 0x00ffff)
        assertPairs("Gen 001:002", 0x000102, 0x000102)
        assertEquals(53, DesktopVerseParser.bookIdFromName("  I.  TiMoTiUs.  "))
        assertEquals(42, DesktopVerseParser.bookIdFromName(" JOHN. "))
        assertEquals(-1, DesktopVerseParser.bookIdFromName("orchard"))
    }

    @Test
    fun `strict book lookup is independent of the device locale`() {
        val original = Locale.getDefault()
        try {
            for (language in listOf("en", "id", "tr")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                assertEquals(0, DesktopVerseParser.bookIdFromName("GENESIS"))
                assertPairs("I TIMOTIUS 1:1", 0x350101, 0x350101)
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `malformed references fail completely rather than returning valid prefixes`() {
        for (text in listOf(
            "", " ", "123", "orchard", "orchard 1:1", "Read John 3:16 today", "John3:16",
            "John 1:1,", "John 1:1;", "John 1:1 dan", "John 1:1 junk", "John 1:1,,2", "John 1:1;;2",
            "John 1:1 dan dan 2", "John 1:1-", "John 1:1---2", "John 1:1-2-3", "John 1:1:2", "John 1:1 and 2",
            "John 3:10-4", "John 4-3", "John 4:1-3:16", "John 0", "John 0:1", "John 1:0", "Jude 0",
            "John 256", "John 1:256", "John 256:1", "John 1:1-256", "John 1:1,256", "John 1:1;256:1",
            "John 999999999999999999:1", "John 1:999999999999999999", "John 999999999999999999",
            "John -1", "John +1", "John 1/2", "John 1..2", "John 1 2", "John 1:1−2",
            "John 1:1; Genesis 1:1", "John ١:٢", "John １:２",
        )) assertNull(text, DesktopVerseParser.parseReference(text))
    }

    @Test
    fun `chapter lists agree across APIs while single chapter shorthand remains supported`() {
        val legacy = DesktopVerseParser.verseStringToAri("Gen 1,3")!!
        assertArrayEquals(intArrayOf(0x000100, 0x000100, 0x000300, 0x000300), IntArray(legacy.size()) { legacy.get(it) })
        assertPairs("Gen 1,3", 0x000100, 0x000100, 0x000300, 0x000300)
        val jude = DesktopVerseParser.verseStringToAri("jud 9-12")!!
        assertArrayEquals(intArrayOf(0x400109, 0x40010c), IntArray(jude.size()) { jude.get(it) })
        assertPairs("jud 1:9-12", 0x400109, 0x40010c)
    }
}
