package yuku.alkitab.base.dialog

import yuku.alkitab.model.SingleChapterVerses

internal class XrefDialogVerses(
    private val notAvailableText: String,
    private val displayedVerseTexts: List<String?>,
    private val displayedVerseNumberTexts: List<String>
) : SingleChapterVerses {

    override val verseCount: Int
        get() = displayedVerseTexts.size

    override fun getVerse(verse_0: Int): String {
        // prevent crash if the target xref is not available
        return displayedVerseTexts[verse_0] ?: notAvailableText
    }

    override fun getVerseNumberText(verse_0: Int): String {
        return displayedVerseNumberTexts[verse_0]
    }
}
