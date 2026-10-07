package yuku.alkitab.base.util

import android.text.style.StyleSpan
import android.util.SparseBooleanArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.model.Book
import yuku.alkitab.model.FootnoteEntry
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 max; project targetSdkVersion is 35
class SearchOptionsTest {
    private val verses = arrayOf(
        "For God so loved the world, that he gave his only begotten Son",
        "And God said, Let us make man in our image",
        "The LORD is my shepherd; I shall not want.",
        "hath been tamed of mankind",
        "Come unto him, all ye that labour",
        "and they worshipped Him",
        "@@@9God@7, who at sundry times spake",
        "also loved God",
    )

    private val book = Book().apply {
        bookId = 0
        shortName = "Aaa"
        abbreviation = "Aaa"
        chapter_count = 1
        verse_counts = intArrayOf(verses.size)
    }

    /** Leaves loadChapterTextWithoutSplit alone so its default gets tested too. */
    private val version = object : Version() {
        override fun getShortName(): String? = "TEST"
        override fun getLongName(): String? = "Test Version"
        override fun getLocale(): String? = "en"
        override fun getMaxBookIdPlusOne(): Int = 1
        override fun getConsecutiveBooks(): Array<Book> = arrayOf(book)
        override fun getBook(bookId: Int): Book? = if (bookId == 0) book else null
        override fun getFirstBook(): Book = book
        override fun loadVerseText(ari: Int): String? = null
        override fun loadVerseText(book: Book?, chapter_1: Int, verse_1: Int): String? = null
        override fun loadVersesByAriRanges(ariRanges: IntArrayList?, result_aris: IntArrayList?, result_verses: MutableList<String>?): Int = 0
        override fun loadPericope(bookId: Int, chapter_1: Int, aris: IntArrayList?, pericopeBlocks: MutableList<PericopeBlock>?): Int = 0
        override fun loadChapterText(book: Book?, chapter_1: Int): SingleChapterVerses = object : SingleChapterVerses {
            override val verseCount: Int get() = verses.size
            override fun getVerse(verse_0: Int): String = verses[verse_0]
        }
        override fun loadChapterTextLowercasedWithoutSplit(book: Book, chapter_1: Int): String =
            verses.joinToString("") { it.lowercase() + "\n" }
        override fun getXrefEntry(arif: Int): XrefEntry? = null
        override fun getFootnoteEntry(arif: Int): FootnoteEntry? = null
    }

    private fun search(
        queryString: String,
        exactPhrase: Boolean = false,
        wholeWords: Boolean = false,
        matchCapitals: Boolean = false,
    ): List<Int> {
        val bookIds = SparseBooleanArray().apply { put(0, true) }
        val query = SearchEngineQuery(queryString, bookIds, SearchOptions(exactPhrase, wholeWords, matchCapitals))
        val res = SearchEngine.searchByGrep(version, query)
        return (0 until res.size()).map { Ari.toVerse(res[it]) }
    }

    private fun hiliteRanges(text: String, queryString: String, options: SearchOptions): List<String> {
        val hilited = SearchEngine.hilite(text, SearchEngine.ReadyTokens.forQuery(queryString, options), 0)
        return hilited.getSpans(0, hilited.length, StyleSpan::class.java)
            .sortedBy { hilited.getSpanStart(it) }
            .map { text.substring(hilited.getSpanStart(it), hilited.getSpanEnd(it)) }
    }

    @Test
    fun `with every option off a word still matches inside longer words`() {
        assertEquals(listOf(2, 4), search("man"))
    }

    @Test
    fun `Whole Words stops a word from matching inside a longer word like mankind`() {
        assertEquals(listOf(2), search("man", wholeWords = true))
    }

    @Test
    fun `without Exact Phrase several words match in any order`() {
        assertEquals(listOf(1, 8), search("loved god"))
    }

    @Test
    fun `Exact Phrase requires the words together and in the typed order`() {
        assertEquals(listOf(1), search("god so loved", exactPhrase = true))
        assertEquals(listOf(8), search("loved god", exactPhrase = true))
    }

    @Test
    fun `Exact Phrase without Whole Words lets the first and last words sit inside longer words`() {
        assertEquals(listOf(1, 8), search("so love", exactPhrase = true))
        assertEquals(listOf(1), search("od so", exactPhrase = true))
    }

    @Test
    fun `Exact Phrase with Whole Words requires the first and last words to be whole`() {
        assertEquals(emptyList<Int>(), search("so love", exactPhrase = true, wholeWords = true))
        assertEquals(emptyList<Int>(), search("od so", exactPhrase = true, wholeWords = true))
        assertEquals(listOf(1), search("so loved", exactPhrase = true, wholeWords = true))
    }

    @Test
    fun `Exact Phrase keeps a whole-word edge that the query marks with a plus or quotes`() {
        assertEquals(emptyList<Int>(), search("+od so", exactPhrase = true))
        assertEquals(emptyList<Int>(), search("\"so love\"", exactPhrase = true))
    }

    @Test
    fun `Exact Phrase skips punctuation and formatting codes between the words`() {
        assertEquals(listOf(7), search("god who", exactPhrase = true))
    }

    @Test
    fun `Exact Phrase does not match across the end of a verse`() {
        assertEquals(emptyList<Int>(), search("son and god", exactPhrase = true))
    }

    @Test
    fun `without Match Capitals the case of the query and the text is ignored`() {
        assertEquals(listOf(5, 6), search("Him"))
    }

    @Test
    fun `Match Capitals means Him does not find him`() {
        assertEquals(listOf(6), search("Him", matchCapitals = true))
        assertEquals(listOf(5), search("him", matchCapitals = true))
    }

    @Test
    fun `Match Capitals still finds a word printed in ALL CAPS`() {
        assertEquals(listOf(3), search("Lord", matchCapitals = true))
        assertEquals(listOf(3), search("lord", matchCapitals = true))
    }

    @Test
    fun `Match Capitals applies to each word of an Exact Phrase`() {
        assertEquals(listOf(3), search("The LORD", exactPhrase = true, matchCapitals = true))
        assertEquals(emptyList<Int>(), search("the LORD", exactPhrase = true, matchCapitals = true))
    }

    @Test
    fun `Match Capitals finds a match in the last verse of a chapter`() {
        assertEquals(listOf(8), search("loved God", exactPhrase = true, matchCapitals = true))
    }

    @Test
    fun `Match Capitals combines with Whole Words`() {
        assertEquals(listOf(6), search("Him", wholeWords = true, matchCapitals = true))
        assertEquals(emptyList<Int>(), search("Hi", wholeWords = true, matchCapitals = true))
    }

    @Test
    fun `forQuery with every option off sorts longest first and removes duplicate tokens`() {
        val rt = SearchEngine.ReadyTokens.forQuery("a bb a", SearchOptions())
        assertEquals(listOf("bb", "a"), rt.tokens.toList())
    }

    @Test
    fun `hilite marks an Exact Phrase as one run`() {
        val text = "For God so loved the world, God so loved"
        assertEquals(
            listOf("God so loved", "God so loved"),
            hiliteRanges(text, "god so loved", SearchOptions(exactPhrase = true)),
        )
    }

    @Test
    fun `hilite with Whole Words skips the word inside longer words`() {
        assertEquals(listOf("man"), hiliteRanges("man of mankind", "man", SearchOptions(wholeWords = true)))
    }

    @Test
    fun `hilite with Match Capitals marks only the matching capitalization and ALL CAPS`() {
        assertEquals(listOf("Him", "HIM"), hiliteRanges("him and Him and HIM", "Him", SearchOptions(matchCapitals = true)))
    }
}
