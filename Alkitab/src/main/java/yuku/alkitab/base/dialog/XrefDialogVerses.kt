package yuku.alkitab.base.dialog

import android.content.Context
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses

internal class XrefDialogVerses(
    private val context: Context,
    private val displayedVerseTexts: List<String?>,
    private val displayedVerseNumberTexts: List<String>
) : SingleChapterVerses {

    override val verseCount: Int
        get() = displayedVerseTexts.size

    override fun getVerse(verse_0: Int): String {
        // prevent crash if the target xref is not available
        return displayedVerseTexts[verse_0] ?: context.getString(R.string.generic_verse_not_available_in_this_version)
    }

    override fun getVerseNumberText(verse_0: Int): String {
        return displayedVerseNumberTexts[verse_0]
    }
}
