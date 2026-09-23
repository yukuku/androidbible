package yuku.alkitab.base.smartsearch

import android.util.SparseBooleanArray
import yuku.alkitab.model.Book
import yuku.alkitab.model.FootnoteEntry
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitab.yes2.lexicon.LexiconCodec
import yuku.alkitab.yes2.lexicon.LexiconPrefixTable

/**
 * An in-memory [Version] for search tests. [verses] maps `(bookId, chapter)` to that chapter's
 * verses in their original case, formatting codes included, exactly as a reader would store them.
 */
fun versionOf(
    verses: Map<Pair<Int, Int>, List<String>>,
    locale: String = "in",
    shortName: String = "TEST",
    lexicon: Map<String, List<String>>? = null,
): Version {
    val books = verses.keys.map { it.first }.distinct().sorted().map { bookId ->
        val chapters = verses.keys.filter { it.first == bookId }.maxOf { it.second }
        Book().apply {
            this.bookId = bookId
            this.shortName = "B$bookId"
            abbreviation = "B$bookId"
            chapter_count = chapters
            verse_counts = IntArray(chapters) { c -> verses[bookId to c + 1]?.size ?: 0 }
        }
    }
    val byId = books.associateBy { it.bookId }
    val lowercased = verses.mapValues { (_, vs) -> vs.joinToString("") { it.lowercase() + "\n" } }

    return object : Version() {
        override fun getShortName(): String = shortName
        override fun getLongName(): String = shortName
        override fun getLocale(): String = locale
        override fun getMaxBookIdPlusOne(): Int = (books.maxOfOrNull { it.bookId } ?: -1) + 1
        override fun getConsecutiveBooks(): Array<Book> = books.toTypedArray()
        override fun getBook(bookId: Int): Book? = byId[bookId]
        override fun getFirstBook(): Book = books.first()
        override fun loadVerseText(ari: Int): String? = verses[Ari.toBook(ari) to Ari.toChapter(ari)]?.getOrNull(Ari.toVerse(ari) - 1)
        override fun loadVerseText(book: Book?, chapter_1: Int, verse_1: Int): String? = book?.let { verses[it.bookId to chapter_1]?.getOrNull(verse_1 - 1) }
        override fun loadVersesByAriRanges(ariRanges: IntArrayList?, result_aris: IntArrayList?, result_verses: MutableList<String>?): Int = 0
        override fun loadPericope(bookId: Int, chapter_1: Int, aris: IntArrayList?, pericopeBlocks: MutableList<PericopeBlock>?): Int = 0
        override fun loadChapterText(book: Book?, chapter_1: Int): SingleChapterVerses? = null
        override fun loadChapterTextLowercasedWithoutSplit(book: Book, chapter_1: Int): String? = lowercased[book.bookId to chapter_1]
        override fun getXrefEntry(arif: Int): XrefEntry? = null
        override fun getFootnoteEntry(arif: Int): FootnoteEntry? = null
        override fun loadLexicon(): Map<String, List<String>>? = lexicon
    }
}

/** The prefix rules the Indonesian word lists use. */
val nasalPrefixTable = LexiconPrefixTable(
    listOf(
        LexiconPrefixTable.Rule("k", "ng"),
        LexiconPrefixTable.Rule("t", "n"),
        LexiconPrefixTable.Rule("s", "ny"),
        LexiconPrefixTable.Rule("p", "m"),
    )
)

/** Word families from lines in the lexicon's own encoding, e.g. `kasih ~ me<i di~i`. */
fun familiesOf(vararg lines: String): Map<String, List<String>> =
    lines.associate { LexiconCodec.decodeFamily(it, nasalPrefixTable) }

fun lexiconOf(vararg lines: String) = SearchLexicon("TEST", LexiconOrigin.VERSION, familiesOf(*lines), 0)

fun allBooksOf(version: Version) = SparseBooleanArray().also { a ->
    for (b in version.consecutiveBooks) a.put(b.bookId, true)
}

fun IntArrayList.toList(): List<Int> = (0 until size()).map { get(it) }
