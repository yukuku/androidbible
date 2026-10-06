package yuku.alkitab.base.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import yuku.alkitab.base.verses.ReadingGuide
import yuku.alkitab.base.verses.ReadingGuideMode
import yuku.alkitab.model.Book
import yuku.alkitab.util.Ari

class ReadingPassageTest {
    private fun book(id: Int): Book? = if (id in 0..65) Book().apply {
        bookId = id
        chapter_count = when (id) { 36 -> 2; 40 -> 16; 53 -> 6; else -> 150 }
        verse_counts = IntArray(chapter_count) { 50 }
    } else null

    private fun ari(book: Int, chapter: Int, verse: Int) = Ari.encode(book, chapter, verse)
    private fun parse(text: String) = ReadingPassage.parse(text, ::book)

    @Test
    fun `the emailed devotional reference retains both boundaries`() {
        assertArrayEquals(intArrayOf(ari(53, 6, 6), ari(53, 6, 10)), parse("1 Timotius 6:6–10"))
    }

    @Test
    fun `comma separated ranges preserve the gap observed in My Utmost`() {
        val ranges = ReadingPassage.resolve(parse("Markus 9:5-6, 14-23")!!, ::book)!!
        val guide = ReadingGuide(ReadingGuideMode.LINE, ranges)
        assertEquals(listOf(ReadingRange(ari(40, 9, 5), ari(40, 9, 6)), ReadingRange(ari(40, 9, 14), ari(40, 9, 23))), ranges)
        assertFalse(guide.includes(ari(40, 9, 7)))
        assertFalse(guide.includes(ari(40, 9, 13)))
    }

    @Test
    fun `a later single verse inherits its chapter`() {
        assertArrayEquals(intArrayOf(ari(42, 13, 13), ari(42, 13, 13), ari(42, 13, 16), ari(42, 13, 16)), parse("Yohanes 13:13, 16"))
    }

    @Test
    fun `semicolon separated verses can address different chapters`() {
        assertArrayEquals(intArrayOf(ari(57, 8, 5), ari(57, 8, 5), ari(57, 10, 1), ari(57, 10, 1)), parse("Ibr  8:5;10:1"))
    }

    @Test
    fun `chapter ranges and full chapters resolve to the first and last verses`() {
        assertEquals(listOf(ReadingRange(ari(39, 1, 1), ari(39, 4, 50))), ReadingPassage.resolve(parse("Matius 1-4")!!, ::book))
        assertEquals(listOf(ReadingRange(ari(19, 21, 1), ari(19, 21, 50))), ReadingPassage.resolve(parse("AMSAL 21")!!, ::book))
    }

    @Test
    fun `a continuous passage can cross a chapter boundary`() {
        assertEquals(listOf(ReadingRange(ari(39, 5, 40), ari(39, 6, 10))), ReadingPassage.resolve(parse("Matius 5:40-6:10")!!, ::book))
    }

    @Test
    fun `a single chapter book with chapter one covers the chapter`() {
        assertEquals(listOf(ReadingRange(ari(30, 1, 1), ari(30, 1, 50))), ReadingPassage.resolve(parse("Obaja 1")!!, ::book))
    }

    @Test
    fun `English ARI links and Indonesian abbreviations address the same verse`() {
        val encoded = ari(50, 1, 5)
        assertArrayEquals(intArrayOf(encoded, encoded), parse("ari:$encoded"))
        assertArrayEquals(intArrayOf(ari(39, 28, 20), ari(39, 28, 20)), parse("Mat.  28:20"))
    }

    @Test
    fun `invalid source references are rejected instead of silently truncating or wrapping them`() {
        for (text in listOf("Hagai 1-3", "Matius 1:999", "Matius 3:10-4", "Matius 1:1 junk", "Matius 1:1,", "unknown 1:1", "ari:invalid")) {
            assertNull(text, parse(text))
        }
        assertNull(ReadingPassage.parse("Matius 1:1") { null })
    }

    @Test
    fun `a book without chapter numbers resolves the whole book`() {
        assertEquals(listOf(ReadingRange(ari(36, 1, 1), ari(36, 2, 50))), ReadingPassage.resolve(parse("Hagai")!!, ::book))
    }

    @Test
    fun `chapter lists retain chapter semantics instead of treating the second number as a verse`() {
        assertArrayEquals(intArrayOf(ari(39, 1, 0), ari(39, 1, 0), ari(39, 3, 0), ari(39, 3, 0)), parse("Matius 1, 3"))
    }

    @Test
    fun `unknown display preference values leave all guides off`() {
        assertEquals(ReadingGuideMode.OFF, ReadingGuideMode.fromPreference(null))
        assertEquals(ReadingGuideMode.OFF, ReadingGuideMode.fromPreference("unexpected"))
        assertEquals(ReadingGuideMode.OFF, ReadingGuideMode.fromPreference("labels"))
        assertEquals(ReadingGuideMode.OFF, ReadingGuideMode.fromPreference("caption"))
    }
}
