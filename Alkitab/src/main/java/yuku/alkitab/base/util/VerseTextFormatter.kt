package yuku.alkitab.base.util

import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.model.Book
import yuku.alkitab.util.IntArrayList

/**
 * Pure formatting helpers for producing reference strings and copy/share text from
 * a selection of verses. Extracted from IsiActivity so they can be unit-tested in
 * isolation.
 */
object VerseTextFormatter {

    /**
     * Builds a human-readable reference string like "Genesis 1:1" or "Genesis 1:1-3"
     * from the selected verses. The chapter is resolved by the caller.
     */
    fun referenceFromSelectedVerses(
        selectedVerses_1: IntArrayList,
        book: Book,
        chapter_1: Int,
    ): String {
        return when (selectedVerses_1.size()) {
            // should not be possible. So we don't do anything.
            0 -> book.reference(chapter_1)
            1 -> book.reference(chapter_1, selectedVerses_1.get(0))
            else -> book.reference(chapter_1, selectedVerses_1)
        }
    }

    /**
     * Construct text for copying or sharing (in plain text).
     *
     * @param selectedVerses_1 the 1-based verse numbers selected by the user
     * @param reference the reference string to prepend (e.g. "Genesis 1:1-3")
     * @param data the [VersesDataModel] to read verse texts from
     * @param versionShortName the short name of the version to append in parentheses,
     *   or null to omit (the caller resolves the "copyWithVersionName" preference)
     * @param includeVerseNumbers whether to prefix each verse line with the verse
     *   number (the caller resolves the "copyWithVerseNumbers" preference); this
     *   only takes effect when more than one verse is selected
     * @return an array of length 2: [0] text to copy/share (with formatting codes
     *   stripped), [1] text to submit to the share URL service (with formatting
     *   codes preserved)
     */
    fun prepareTextForCopyShare(
        selectedVerses_1: IntArrayList,
        reference: CharSequence,
        data: VersesDataModel,
        versionShortName: String?,
        includeVerseNumbers: Boolean,
    ): Array<String> {
        val res0 = StringBuilder()
        val res1 = StringBuilder()

        res0.append(reference)

        if (versionShortName != null) {
            res0.append(" (").append(versionShortName).append(")")
        }

        if (includeVerseNumbers && selectedVerses_1.size() > 1) {
            res0.append('\n')

            // append each selected verse with verse number prepended
            var i = 0
            val len = selectedVerses_1.size()
            while (i < len) {
                val verse_1 = selectedVerses_1.get(i)
                val verseText = data.getVerseText(verse_1)

                if (verseText != null) {
                    val verseTextPlain = FormattedVerseText.removeSpecialCodes(verseText)

                    res0.append(verse_1)
                    res1.append(verse_1)
                    res0.append(' ')
                    res1.append(' ')

                    res0.append(verseTextPlain)
                    res1.append(verseText)

                    if (i != len - 1) {
                        res0.append('\n')
                        res1.append('\n')
                    }
                }
                i++
            }
        } else {
            res0.append("  ")

            // append each selected verse without verse number prepended
            for (i in 0 until selectedVerses_1.size()) {
                val verse_1 = selectedVerses_1.get(i)
                val verseText = data.getVerseText(verse_1)

                if (verseText != null) {
                    val verseTextPlain = FormattedVerseText.removeSpecialCodes(verseText)

                    if (i != 0) {
                        res0.append('\n')
                        res1.append('\n')
                    }
                    res0.append(verseTextPlain)
                    res1.append(verseText)
                }
            }
        }

        return arrayOf(res0.toString(), res1.toString())
    }
}
