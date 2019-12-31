package yuku.alkitab.base.verses

import yuku.alkitab.base.util.Highlights

class VersesAttributes(
    val bookmarkCountMap_: IntArray,
    val noteCountMap_: IntArray,
    val highlightInfoMap_: Array<Highlights.Info?>,
    val progressMarkBitsMap_: IntArray,
    val hasMapsMap_: BooleanArray
) {
    companion object {
        private val EMPTY = VersesAttributes(
            bookmarkCountMap_ = IntArray(0),
            noteCountMap_ = IntArray(0),
            highlightInfoMap_ = emptyArray(),
            progressMarkBitsMap_ = IntArray(0),
            hasMapsMap_ = BooleanArray(0)
        )

        fun createEmpty(verseCount: Int) = if (verseCount == 0) {
            EMPTY
        } else {
            VersesAttributes(
                bookmarkCountMap_ = IntArray(verseCount),
                noteCountMap_ = IntArray(verseCount),
                highlightInfoMap_ = arrayOfNulls(verseCount),
                progressMarkBitsMap_ = IntArray(verseCount),
                hasMapsMap_ = BooleanArray(verseCount)
            )
        }
    }
}