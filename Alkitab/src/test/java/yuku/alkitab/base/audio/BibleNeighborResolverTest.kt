package yuku.alkitab.base.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

/**
 * Pure-logic tests for [BibleNeighborResolver]. We use a tiny fake [Version]
 * with three books (10 / 1 / 5 chapters) to exercise within-book navigation,
 * cross-book navigation, and Bible-boundary handling without needing a real
 * loaded YES2 file.
 */
class BibleNeighborResolverTest {

    private fun book(bookId: Int, shortName: String, chapter_count: Int): Book {
        val b = Book()
        b.bookId = bookId
        b.shortName = shortName
        b.chapter_count = chapter_count
        b.verse_counts = IntArray(chapter_count) { 10 }
        return b
    }

    /** Fake Version exposing exactly the three books we care about for the resolver. */
    private class FakeVersion(private val books: Array<Book>) : Version() {
        override fun getShortName(): String = "FAKE"
        override fun getLongName(): String = "Fake Version"
        override fun getLocale(): String = "xx"
        override fun getMaxBookIdPlusOne(): Int = books.maxOf { it.bookId } + 1
        override fun getConsecutiveBooks(): Array<Book> = books
        override fun getBook(bookId: Int): Book? = books.firstOrNull { it.bookId == bookId }
        override fun getFirstBook(): Book = books.first()
        override fun loadVerseText(ari: Int): String? = null
        override fun loadVerseText(book: Book?, chapter_1: Int, verse_1: Int): String? = null
        override fun loadVersesByAriRanges(
            ariRanges: yuku.alkitab.util.IntArrayList?,
            result_aris: yuku.alkitab.util.IntArrayList?,
            result_verses: MutableList<String>?,
        ): Int = 0
        override fun loadPericope(
            bookId: Int,
            chapter_1: Int,
            aris: yuku.alkitab.util.IntArrayList?,
            pericopeBlocks: MutableList<yuku.alkitab.model.PericopeBlock>?,
        ): Int = 0
        override fun loadChapterText(
            book: Book?,
            chapter_1: Int,
        ): yuku.alkitab.model.SingleChapterVerses? = null
        override fun loadChapterTextLowercasedWithoutSplit(book: Book?, chapter_1: Int): String? = null
        override fun getXrefEntry(arif: Int): yuku.alkitab.model.XrefEntry? = null
        override fun getFootnoteEntry(arif: Int): yuku.alkitab.model.FootnoteEntry? = null
    }

    private val genesis = book(0, "Gen", 10)
    private val obadiah = book(30, "Ob", 1)
    private val matthew = book(39, "Mt", 5)
    private val version = FakeVersion(arrayOf(genesis, obadiah, matthew))

    @Test
    fun `next within a book steps the chapter by one`() {
        val (book, chapter) = BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 3, direction = 1)!!
        assertEquals(0, book.bookId)
        assertEquals(4, chapter)
    }

    @Test
    fun `previous within a book steps the chapter by one`() {
        val (book, chapter) = BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 3, direction = -1)!!
        assertEquals(0, book.bookId)
        assertEquals(2, chapter)
    }

    @Test
    fun `next at the last chapter of a book crosses into chapter 1 of the following book`() {
        val (book, chapter) = BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 10, direction = 1)!!
        assertEquals(30, book.bookId)
        assertEquals(1, chapter)
    }

    @Test
    fun `previous at chapter 1 crosses into the last chapter of the preceding book`() {
        val (book, chapter) = BibleNeighborResolver.neighbor(version, bookId = 30, chapter_1 = 1, direction = -1)!!
        assertEquals(0, book.bookId)
        assertEquals(10, chapter)
    }

    @Test
    fun `previous at the very first chapter of the Bible returns null`() {
        val result = BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 1, direction = -1)
        assertNull(result)
    }

    @Test
    fun `next at the very last chapter of the Bible returns null`() {
        val result = BibleNeighborResolver.neighbor(version, bookId = 39, chapter_1 = 5, direction = 1)
        assertNull(result)
    }

    @Test
    fun `direction other than plus or minus 1 returns null`() {
        assertNull(BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 3, direction = 0))
        assertNull(BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 3, direction = 2))
        assertNull(BibleNeighborResolver.neighbor(version, bookId = 0, chapter_1 = 3, direction = -2))
    }

    @Test
    fun `unknown bookId returns null`() {
        assertNull(BibleNeighborResolver.neighbor(version, bookId = 99, chapter_1 = 1, direction = 1))
    }

    @Test
    fun `single-chapter book navigates correctly across both boundaries`() {
        val nextOut = BibleNeighborResolver.neighbor(version, bookId = 30, chapter_1 = 1, direction = 1)!!
        assertEquals(39, nextOut.first.bookId)
        assertEquals(1, nextOut.second)

        val prevOut = BibleNeighborResolver.neighbor(version, bookId = 30, chapter_1 = 1, direction = -1)!!
        assertEquals(0, prevOut.first.bookId)
        assertEquals(10, prevOut.second)
    }

    // -- coverageAwareNeighbor ----------------------------------------------------

    private fun setCovering(vararg bookIds: Int): AudioSet = AudioSet(
        audioId = "x",
        title = "X",
        hasTiming = false,
        books_1 = bookIds.map { it + 1 }.toSet(),
        mp3UrlTemplate = "/audio/file/p/x/{book_1}/{chapter_1}.mp3",
        timingUrlTemplate = null,
    )

    @Test
    fun `a coverage edge behaves like a Bible boundary in both directions`() {
        // The set covers Genesis only, so leaving it in either direction stops.
        val genesisOnly = setCovering(0)
        assertNull(
            "auto-advance and next-chapter must stop at the coverage gap",
            BibleNeighborResolver.coverageAwareNeighbor(version, bookId = 0, chapter_1 = 10, direction = 1, set = genesisOnly),
        )
        assertNull(
            BibleNeighborResolver.coverageAwareNeighbor(version, bookId = 30, chapter_1 = 1, direction = -1, set = setCovering(30, 39)),
        )
    }

    @Test
    fun `a covered cross-book neighbor passes through`() {
        val (book, chapter) = BibleNeighborResolver.coverageAwareNeighbor(
            version, bookId = 0, chapter_1 = 10, direction = 1, set = setCovering(0, 30),
        )!!
        assertEquals(30, book.bookId)
        assertEquals(1, chapter)
    }

    @Test
    fun `within-book steps are never coverage-gated`() {
        // The chapter currently playing proves the book is covered, so a
        // same-book step needs no coverage consultation.
        val (book, chapter) = BibleNeighborResolver.coverageAwareNeighbor(
            version, bookId = 0, chapter_1 = 3, direction = 1, set = setCovering(30),
        )!!
        assertEquals(0, book.bookId)
        assertEquals(4, chapter)
    }

    @Test
    fun `a null set applies no coverage gate`() {
        val (book, chapter) = BibleNeighborResolver.coverageAwareNeighbor(
            version, bookId = 0, chapter_1 = 10, direction = 1, set = null,
        )!!
        assertEquals(30, book.bookId)
        assertEquals(1, chapter)
    }

    @Test
    fun `the Bible boundary still resolves to null regardless of coverage`() {
        assertNull(
            BibleNeighborResolver.coverageAwareNeighbor(version, bookId = 39, chapter_1 = 5, direction = 1, set = setCovering(0, 30, 39)),
        )
    }
}
