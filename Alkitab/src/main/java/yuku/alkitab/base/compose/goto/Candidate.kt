package yuku.alkitab.base.compose.goto

import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.Levenshtein
import yuku.alkitab.model.Book

data class Candidate(
    val title: String,
    val score: Int,
    val bookOnly: Boolean,
    val hasVerse: Boolean,
)

fun computeCandidates(query: String, books: Array<Book>): List<Candidate> {
    val jumper = Jumper(query)
    val rawBookName = jumper.unparsedBook ?: return emptyList()
    val bookName = rawBookName.trim().lowercase()
    if (bookName.isEmpty()) return emptyList()

    val out = ArrayList<Candidate>()
    val addedBookIds = HashSet<Int>()

    for (book in books) {
        val n = book.shortName.lowercase()
        val title: String?
        val score: Int
        when {
            n.startsWith(bookName) -> { title = book.shortName; score = 20 }
            n.contains(bookName) -> { title = book.shortName; score = 10 }
            else -> { title = null; score = 0 }
        }
        if (score != 0 && title != null) {
            if (addCandidate(jumper, out, title, score, book) != null) {
                addedBookIds.add(book.bookId)
            }
        }
    }

    if (out.size < 5) {
        val bookRefs = Jumper.createBookCandidates(books)
        val bookIndex = books.associateBy { it.bookId }
        for (bookRef in bookRefs) {
            if (addedBookIds.contains(bookRef.bookId)) continue
            val distance = Levenshtein.distance(bookName, bookRef.condensed)
            val book = bookIndex[bookRef.bookId] ?: continue
            if (addCandidate(jumper, out, book.shortName, -distance, book) != null) {
                addedBookIds.add(bookRef.bookId)
            }
        }
    }

    return out.sortedByDescending { it.score }
}

private fun addCandidate(
    jumper: Jumper,
    sink: ArrayList<Candidate>,
    titleIn: String,
    score: Int,
    book: Book,
): Candidate? {
    var title = titleIn
    var bookOnly = true
    var hasVerse = false
    val chapter_1 = jumper.chapter
    if (chapter_1 != 0) {
        bookOnly = false
        title += " $chapter_1"
        val verse_1 = jumper.verse
        if (verse_1 != 0) {
            title += ":$verse_1"
            hasVerse = true
        }
        if (chapter_1 < 1 || chapter_1 > book.chapter_count) return null
        if (verse_1 != 0 && (verse_1 < 1 || verse_1 > book.verse_counts[chapter_1 - 1])) return null
    }
    val c = Candidate(title = title, score = score, bookOnly = bookOnly, hasVerse = hasVerse)
    sink.add(c)
    return c
}
