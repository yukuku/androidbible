package yuku.alkitab.base.audio

import yuku.alkitab.base.audio.model.AudioSet
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

/**
 * Pure-function helper that walks a [Version]'s book list to resolve the
 * chapter immediately before or after the given (book, chapter_1) pair.
 *
 * Used by [BibleAudioService.skipChapter] when the user taps a chapter-skip
 * button — including from places that have no [yuku.alkitab.base.IsiActivity]
 * available (lock-screen notification, Bluetooth media buttons, Android Auto).
 *
 * Returns `null` at the boundaries of the Bible (i.e. when there is no
 * neighbor in the requested direction). The caller is expected to no-op in
 * that case, matching the in-app audio bar's "invisible-not-gone" pattern at
 * Genesis 1 / Revelation 22.
 *
 * Mirrors the activity-side `audioNeighborChapter` logic in
 * [yuku.alkitab.base.IsiActivity.audioNeighborChapter]. Kept separate so it
 * can be unit-tested without Android, and so the service does not depend on
 * an activity callback when navigating from the notification.
 */
object BibleNeighborResolver {
    /**
     * @param direction `-1` for previous, `+1` for next. Other values return `null`.
     * @return the neighbor `(book, chapter_1)` or `null` at the Bible boundary or
     *  when [bookId] / [chapter_1] are not in [version].
     */
    fun neighbor(version: Version, bookId: Int, chapter_1: Int, direction: Int): Pair<Book, Int>? {
        if (direction != 1 && direction != -1) return null
        val book = version.getBook(bookId) ?: return null
        val target = chapter_1 + direction
        if (target in 1..book.chapter_count) return book to target
        val books = version.consecutiveBooks
        val idx = books.indexOf(book)
        if (idx == -1) return null
        return when {
            direction > 0 && idx < books.size - 1 -> books[idx + 1].let { it to 1 }
            direction < 0 && idx > 0 -> books[idx - 1].let { it to it.chapter_count }
            else -> null
        }
    }

    /**
     * Like [neighbor], but additionally treats the edge of [set]'s book
     * coverage as a boundary: a neighbor that crosses into a book the selected
     * recording does not cover resolves to null, so chapter prev/next disables
     * and auto-advance stops exactly as at a Bible boundary — jumping over the
     * gap would be more confusing than stopping.
     *
     * A null [set] applies no coverage gate (coverage unknown, e.g. a cold
     * cache after process death — the chapter load itself then surfaces any
     * real gap). Within-book steps are never gated: the chapter currently
     * playing proves the book is covered.
     */
    fun coverageAwareNeighbor(
        version: Version,
        bookId: Int,
        chapter_1: Int,
        direction: Int,
        set: AudioSet?,
    ): Pair<Book, Int>? {
        val neighbor = neighbor(version, bookId, chapter_1, direction) ?: return null
        if (set != null && neighbor.first.bookId != bookId && !set.coversBook(neighbor.first.bookId)) return null
        return neighbor
    }
}
