package yuku.alkitab.model

/**
 * Multiple verses that are presented as "single chapter".
 *
 * They are not necessarily in a real chapter, for example when presenting verses of different translation
 * at once, they are considered as if they were in a single chapter, only by making the [getVerseNumberText]
 * return the translation name instead of numbers.
 */
interface SingleChapterVerses {
    val verseCount: Int

    fun getVerse(verse_0: Int): String

    fun getVerseNumberText(verse_0: Int): String {
        val verse_1 = verse_0 + 1
        return if (verse_1 < 40) {
            VERSE_NUMBER_STRINGS[verse_1]
        } else {
            verse_1.toString()
        }
    }

    /**
     * Implement this when displaying verses in different font size for each verse.
     * For example, during comparison of different translations.
     */
    interface WithTextSizeMult : SingleChapterVerses {
        fun getTextSizeMult(verse_0: Int): Float
    }

    companion object {
        private val VERSE_NUMBER_STRINGS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39") // up to [39]

        val EMPTY = object : SingleChapterVerses {
            override val verseCount: Int
                get() = 0

            override fun getVerse(verse_0: Int): String {
                throw IllegalArgumentException("@@getVerse requesting verse_0=$verse_0 but verseCount=$verseCount")
            }
        }
    }
}
