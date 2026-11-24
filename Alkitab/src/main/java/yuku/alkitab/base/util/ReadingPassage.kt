package yuku.alkitab.base.util

import yuku.alkitab.model.Book
import yuku.alkitab.util.Ari
import yuku.alkitabconverter.util.DesktopVerseParser

data class ReadingRange(val start: Int, val end: Int) {
    operator fun contains(ari: Int) = ari in start..end
}

/** Resolves chapter/book addresses to actual verse boundaries without extending invalid references. */
object ReadingPassage {
    @JvmStatic
    fun resolve(aris: IntArray, book: (Int) -> Book?): List<ReadingRange>? {
        if (aris.isEmpty() || aris.size % 2 != 0) return null
        fun boundary(ari: Int, end: Boolean): Int? {
            if (!isPassageBoundary(ari)) return null
            val b = book(Ari.toBook(ari)) ?: return null
            val chapter = Ari.toChapter(ari).let { if (it == 0) if (end) b.chapter_count else 1 else it }
            if (chapter !in 1..b.chapter_count || chapter > b.verse_counts.size) return null
            val verse = Ari.toVerse(ari).let { if (it == 0) if (end) b.verse_counts[chapter - 1] else 1 else it }
            if (verse !in 1..b.verse_counts[chapter - 1]) return null
            return Ari.encode(b.bookId, chapter, verse)
        }
        val ranges = ArrayList<ReadingRange>(aris.size / 2)
        for (index in aris.indices step 2) {
            val first = boundary(aris[index], false) ?: return null
            val last = boundary(aris[index + 1], true) ?: return null
            if (first > last) return null
            ranges.add(ReadingRange(first, last))
        }
        return ranges
    }

    /** Intersects each saved passage with the verse addresses available in a reader version. */
    fun resolveAvailable(aris: IntArray, book: (Int) -> Book?): List<ReadingRange> {
        if (aris.size % 2 != 0) return emptyList()
        return buildList {
            for (index in aris.indices step 2) {
                val start = aris[index]
                val end = aris[index + 1]
                if (!isPassageBoundary(start) || !isPassageBoundary(end)) continue

                val first = Ari.encode(
                    Ari.toBook(start),
                    Ari.toChapter(start).coerceAtLeast(1),
                    Ari.toVerse(start).coerceAtLeast(1),
                )
                val last = Ari.encode(
                    Ari.toBook(end),
                    Ari.toChapter(end).takeIf { it != 0 } ?: 0xff,
                    Ari.toVerse(end).takeIf { it != 0 } ?: 0xff,
                )
                if (first > last) continue
                val availableStart = findAvailableBoundary(first, last, book, isEnd = false) ?: continue
                val availableEnd = findAvailableBoundary(first, last, book, isEnd = true) ?: continue
                add(ReadingRange(availableStart, availableEnd))
            }
        }
    }

    private fun findAvailableBoundary(first: Int, last: Int, book: (Int) -> Book?, isEnd: Boolean): Int? {
        val bookIds = Ari.toBook(first)..Ari.toBook(last)
        for (bookId in if (isEnd) bookIds.reversed() else bookIds) {
            val currentBook = book(bookId) ?: continue
            val firstChapter = if (bookId == bookIds.first) Ari.toChapter(first) else 1
            val lastChapter = if (bookId == bookIds.last) Ari.toChapter(last) else 0xff
            val chapters = firstChapter..minOf(lastChapter, currentBook.chapter_count, currentBook.verse_counts.size)
            for (chapter in if (isEnd) chapters.reversed() else chapters) {
                val verseCount = currentBook.verse_counts[chapter - 1].coerceAtMost(0xff)
                if (verseCount < 1) continue
                val availableStart = maxOf(first, Ari.encode(bookId, chapter, 1))
                val availableEnd = minOf(last, Ari.encode(bookId, chapter, verseCount))
                if (availableStart <= availableEnd) return if (isEnd) availableEnd else availableStart
            }
        }
        return null
    }

    private fun isPassageBoundary(ari: Int) = ari in 0..0xffffff && (Ari.toChapter(ari) != 0 || Ari.toVerse(ari) == 0)

    @JvmStatic
    fun parse(reference: String, book: (Int) -> Book?): IntArray? {
        val text = reference.trim()
        val aris = if (text.startsWith("ari:")) {
            val ari = text.substring(4).toIntOrNull() ?: return null
            intArrayOf(ari, ari)
        } else {
            val parsed = DesktopVerseParser.parseReference(text) ?: return null
            IntArray(parsed.size()) { parsed.get(it) }
        }
        return aris.takeIf { resolve(it, book) != null }
    }
}
