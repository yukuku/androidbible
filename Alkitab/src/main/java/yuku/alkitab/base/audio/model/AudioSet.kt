package yuku.alkitab.base.audio.model

import kotlinx.serialization.Serializable

/**
 * One recording of a Bible version. Recordings of the same text differ in
 * narrator, in which books they cover, and in whether verse timing exists.
 *
 * The URL templates are relative paths on `BuildConfig.SERVER_HOST`; the
 * client expands `{book_1}` and `{chapter_1}` via literal string replace and
 * never assembles a media path from parts.
 *
 * Every field is required — see [AudioSets] for why the models declare no
 * default parameter values. [timingUrlTemplate] is explicitly `null` (not
 * omitted) exactly when [hasTiming] is false.
 *
 * [books_1] arrives as a JSON array; declaring it as a [Set] converts it once
 * at parse time so [coversBook] is O(1) — it is consulted on every menu
 * preparation.
 */
@Serializable
data class AudioSet(
    /** Opaque recording identifier; stable, used as the persisted selection key. */
    val audioId: String,
    /** Display name. The backend falls back to the raw [audioId] when it has no mapping. */
    val title: String,
    /** False → the recording plays but verse highlight and verse-skip are disabled. */
    val hasTiming: Boolean,
    /** 1-based book coverage. Ragged — a recording may omit whole books. */
    val books_1: Set<Int>,
    val mp3UrlTemplate: String,
    val timingUrlTemplate: String?,
) {
    /** Whether this recording covers the book with the app's 0-based [bookId]. */
    fun coversBook(bookId: Int): Boolean = (bookId + 1) in books_1
}
