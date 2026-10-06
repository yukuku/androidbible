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
            if (ari !in 0..0xffffff) return null
            if (Ari.toChapter(ari) == 0 && Ari.toVerse(ari) != 0) return null
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
