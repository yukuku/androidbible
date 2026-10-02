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
        return aris.toList().chunked(2).map { (start, end) ->
            val first = boundary(start, false) ?: return null
            val last = boundary(end, true) ?: return null
            if (first > last) return null
            ReadingRange(first, last)
        }
    }

    /** Decodes devotional links, including lists whose later verses inherit their chapter. */
    @JvmStatic
    fun parse(reference: String, book: (Int) -> Book?): IntArray? {
        val normalized = reference.trim().replace(Regex("[–—]"), "-")
            .replace(Regex("\\s+"), " ").replace(Regex("\\bdan\\b", RegexOption.IGNORE_CASE), ",")
        if (normalized.startsWith("ari:")) {
            val ari = normalized.substring(4).toIntOrNull() ?: return null
            return intArrayOf(ari, ari).takeIf { resolve(it, book) != null }
        }
        val match = Regex("^(.+?)\\s+(\\d[\\d\\s:.,;\\-]*)$").matchEntire(normalized)
        val name = match?.groupValues?.get(1) ?: normalized
        val bookId = DesktopVerseParser.bookIdFromName(name)
        if (bookId < 0) return null
        if (match == null) {
            return intArrayOf(Ari.encode(bookId, 0, 0), Ari.encode(bookId, 0, 0))
                .takeIf { resolve(it, book) != null }
        }
        var previousChapter = 0
        var verseMode = false
        fun address(text: String): Pair<Int, Int>? {
            val pieces = text.trim().split(Regex("[:.]"))
            val numbers = pieces.map { it.trim().toIntOrNull() ?: return null }
            if (numbers.any { it !in 1..255 }) return null
            return when (numbers.size) {
                2 -> numbers[0] to numbers[1]
                1 -> if (verseMode) previousChapter to numbers[0] else numbers[0] to 0
                else -> null
            }
        }
        val result = mutableListOf<Int>()
        for (part in match.groupValues[2].split(Regex("[,;]"))) {
            val ends = part.trim().split(Regex("\\s*-\\s*"))
            if (ends.size !in 1..2) return null
            val start = address(ends[0]) ?: return null
            previousChapter = start.first
            verseMode = start.second != 0
            val end = if (ends.size == 2) address(ends[1]) ?: return null else start
            previousChapter = end.first
            verseMode = end.second != 0
            result += Ari.encode(bookId, start.first, start.second)
            result += Ari.encode(bookId, end.first, end.second)
        }
        return result.toIntArray().takeIf { resolve(it, book) != null }
    }
}
