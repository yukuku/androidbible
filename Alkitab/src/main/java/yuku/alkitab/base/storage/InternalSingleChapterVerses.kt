package yuku.alkitab.base.storage

import yuku.alkitab.model.SingleChapterVerses

class InternalSingleChapterVerses(private val verses: Array<String>) : SingleChapterVerses {

    override val verseCount: Int
        get() = verses.size

    override fun getVerse(verse_0: Int): String {
        return verses[verse_0]
    }
}
