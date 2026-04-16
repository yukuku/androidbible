package yuku.alkitab.base.util

import org.junit.Assert.assertEquals
import org.junit.Test
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.model.Book
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.IntArrayList

class VerseTextFormatterTest {

    // ----- referenceFromSelectedVerses -----

    @Test
    fun `referenceFromSelectedVerses with an empty selection returns just the chapter (defensive — should not happen in practice)`() {
        val book = makeBook(shortName = "Gen")
        val ref = VerseTextFormatter.referenceFromSelectedVerses(IntArrayList(), book, chapter_1 = 1)
        assertEquals("Gen 1", ref)
    }

    @Test
    fun `referenceFromSelectedVerses with a single verse formats as the book name, chapter, and verse joined by a colon`() {
        val book = makeBook(shortName = "Gen")
        val ref = VerseTextFormatter.referenceFromSelectedVerses(ints(5), book, chapter_1 = 1)
        assertEquals("Gen 1:5", ref)
    }

    @Test
    fun `referenceFromSelectedVerses collapses a contiguous run into a dash range`() {
        val book = makeBook(shortName = "Gen")
        val ref = VerseTextFormatter.referenceFromSelectedVerses(ints(1, 2, 3), book, chapter_1 = 1)
        assertEquals("Gen 1:1-3", ref)
    }

    @Test
    fun `referenceFromSelectedVerses joins non-contiguous verses with commas`() {
        val book = makeBook(shortName = "Gen")
        val ref = VerseTextFormatter.referenceFromSelectedVerses(ints(1, 3, 5), book, chapter_1 = 1)
        assertEquals("Gen 1:1, 3, 5", ref)
    }

    // ----- prepareTextForCopyShare -----

    @Test
    fun `prepareTextForCopyShare with a single verse and no version-name preference omits the version suffix`() {
        val data = makeData("In the beginning...")
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1),
            reference = "Gen 1:1",
            data = data,
            versionShortName = null,
            includeVerseNumbers = false,
        )
        assertEquals("Gen 1:1  In the beginning...", result[0])
        assertEquals("In the beginning...", result[1])
    }

    @Test
    fun `prepareTextForCopyShare appends versionShortName in parentheses when provided`() {
        val data = makeData("In the beginning...")
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1),
            reference = "Gen 1:1",
            data = data,
            versionShortName = "KJV",
            includeVerseNumbers = false,
        )
        assertEquals("Gen 1:1 (KJV)  In the beginning...", result[0])
    }

    @Test
    fun `prepareTextForCopyShare joins multiple verses with newlines when verse-number prefix is off`() {
        val data = makeData("Line one", "Line two", "Line three")
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1, 2, 3),
            reference = "Gen 1:1-3",
            data = data,
            versionShortName = null,
            includeVerseNumbers = false,
        )
        assertEquals("Gen 1:1-3  Line one\nLine two\nLine three", result[0])
        assertEquals("Line one\nLine two\nLine three", result[1])
    }

    @Test
    fun `prepareTextForCopyShare prefixes each verse with its number when the verse-number preference is on and more than one verse is selected`() {
        val data = makeData("Line one", "Line two", "Line three")
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1, 2, 3),
            reference = "Gen 1:1-3",
            data = data,
            versionShortName = null,
            includeVerseNumbers = true,
        )
        assertEquals("Gen 1:1-3\n1 Line one\n2 Line two\n3 Line three", result[0])
        assertEquals("1 Line one\n2 Line two\n3 Line three", result[1])
    }

    @Test
    fun `prepareTextForCopyShare ignores the verse-number preference when only a single verse is selected`() {
        // The original IsiActivity code only honored the preference when selectedVerses_1.size() > 1.
        val data = makeData("In the beginning...")
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1),
            reference = "Gen 1:1",
            data = data,
            versionShortName = null,
            includeVerseNumbers = true,
        )
        assertEquals("Gen 1:1  In the beginning...", result[0])
    }

    @Test
    fun `prepareTextForCopyShare strips formatting codes from the copy text but preserves them in the submit text`() {
        // @9 / @7 are italic open/close codes; FormattedVerseText.removeSpecialCodes strips them.
        val data = makeData("@@Hello @9world@7!")
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1),
            reference = "Gen 1:1",
            data = data,
            versionShortName = null,
            includeVerseNumbers = false,
        )
        assertEquals("Gen 1:1  Hello world!", result[0])
        assertEquals("@@Hello @9world@7!", result[1])
    }

    @Test
    fun `prepareTextForCopyShare silently skips verses whose text is null (out-of-range), matching the original IsiActivity behavior`() {
        val data = makeData("Line one")   // only verse 1 exists
        val result = VerseTextFormatter.prepareTextForCopyShare(
            selectedVerses_1 = ints(1, 2),
            reference = "Gen 1:1-2",
            data = data,
            versionShortName = null,
            includeVerseNumbers = false,
        )
        assertEquals("Gen 1:1-2  Line one", result[0])
    }

    // ----- helpers -----

    private fun ints(vararg values: Int): IntArrayList {
        val list = IntArrayList(values.size)
        for (v in values) list.add(v)
        return list
    }

    private fun makeBook(shortName: String): Book {
        val book = Book()
        book.bookId = 0
        book.shortName = shortName
        book.chapter_count = 50
        book.verse_counts = IntArray(50) { 31 }
        book.abbreviation = shortName
        return book
    }

    private fun makeData(vararg verses: String): VersesDataModel {
        val scv = object : SingleChapterVerses {
            override val verseCount: Int get() = verses.size
            override fun getVerse(verse_0: Int): String = verses[verse_0]
        }
        return VersesDataModel(ari_bc_ = 0, verses_ = scv)
    }
}
