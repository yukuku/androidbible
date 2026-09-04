package yuku.alkitab.base.speech

import java.util.Locale
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

/** Builds stable TTS utterances from the text of the currently selected Bible version. */
class BiblePassageFactory {
    fun fromAris(
        version: Version,
        versionId: String,
        aris: List<Int>,
    ): List<SpeechPassage> {
        val languageTag = languageTag(versionId, version.locale)
        return aris.asSequence()
            .distinct()
            .sorted()
            .mapNotNull { ari ->
                val text = version.loadVerseText(ari)?.takeUnless { it.isBlank() } ?: return@mapNotNull null
                SpeechPassage(
                    id = ari.toString(),
                    reference = version.reference(ari),
                    languageTag = languageTag,
                    text = text,
                )
            }
            .toList()
    }

    fun fromChapter(
        version: Version,
        versionId: String,
        book: Book,
        chapter1: Int,
        startVerse1: Int = 1,
    ): List<SpeechPassage> {
        val verseCount = book.verse_counts?.getOrNull(chapter1 - 1) ?: return emptyList()
        if (verseCount <= 0) return emptyList()
        val firstVerse = startVerse1.coerceIn(1, verseCount)
        return fromAris(
            version,
            versionId,
            (firstVerse..verseCount).map { verse1 -> Ari.encode(book.bookId, chapter1, verse1) },
        )
    }

    internal fun languageTag(versionId: String, versionLocale: String?): String {
        val key = versionId.substringAfterLast('/').lowercase(Locale.ROOT)
        if (key == "in" || key.startsWith("in-") || key == "id" || key.startsWith("id-")) {
            return "id-ID"
        }
        if (key == "en" || key.startsWith("en-")) return "en-US"

        val normalized = versionLocale.orEmpty().trim().replace('_', '-')
        if (normalized.isBlank()) return DEFAULT_LANGUAGE_TAG
        val modernized = when {
            normalized.equals("in", ignoreCase = true) -> "id-ID"
            normalized.startsWith("in-", ignoreCase = true) -> "id-${normalized.substringAfter('-')}"
            else -> normalized
        }
        val locale = Locale.forLanguageTag(modernized)
        return locale.toLanguageTag().takeUnless { locale.language.isBlank() || it == "und" }
            ?: DEFAULT_LANGUAGE_TAG
    }

    private companion object {
        const val DEFAULT_LANGUAGE_TAG = "id-ID"
    }
}
