package yuku.alkitab.yes1

import yuku.alkitab.model.SingleChapterVerses

internal class Yes1SingleChapterVerses(private val verses: Array<String>) : SingleChapterVerses {
    override val verseCount: Int
        get() = verses.size

    override fun getVerse(verse_0: Int): String {
        return verses[verse_0]
    }
}
