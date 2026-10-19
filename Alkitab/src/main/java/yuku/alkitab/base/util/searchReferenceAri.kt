package yuku.alkitab.base.util

import yuku.alkitab.model.Book
import yuku.alkitab.util.Ari

internal fun searchReferenceAri(query: String, books: Array<Book>): Int? {
    val jumper = Jumper(query)
    if (!jumper.parseSucceeded || jumper.chapter < 1) return null
    val bookId = jumper.getBookId(books)
    val book = books.firstOrNull { it.bookId == bookId } ?: return null
    if (jumper.chapter > book.chapter_count) return null
    if (jumper.verse !in 0..book.verse_counts[jumper.chapter - 1]) return null
    return Ari.encode(bookId, jumper.chapter, jumper.verse)
}
