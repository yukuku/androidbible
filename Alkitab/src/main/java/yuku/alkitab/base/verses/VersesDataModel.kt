package yuku.alkitab.base.verses

import yuku.alkitab.base.util.AppLog
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

private const val TAG = "VersesDataModel"

inline class LocateResult constructor(private val raw: Long) {
    constructor(verse_1: Int, distanceToNextVerse: Int) : this(
        verse_1.toLong() or (distanceToNextVerse.toLong() shl 32)
    )

    val verse_1 get() = raw.toInt()
    val distanceToNextVerse get() = (raw shr 32).toInt()

    companion object {
        val EMPTY = LocateResult(0L)
    }
}

data class VersesDataModel(
    @JvmField
    val ari_bc_: Int,
    @JvmField
    val verses_: SingleChapterVerses,
    @JvmField
    val pericopeBlockCount_: Int = 0,
    @JvmField
    val pericopeAris_: IntArray = IntArray(0),
    @JvmField
    val pericopeBlocks_: Array<PericopeBlock> = emptyArray(),
    @JvmField
    val version_: Version? = null,
    @JvmField
    val versionId_: String? = null,
    @JvmField
    val versesAttributes: VersesAttributes = VersesAttributes.createEmpty(verses_.verseCount)
) {
    enum class ItemType {
        verseText,
        pericope,
    }

    /**
     * For each element, if 0 or more, it refers to the 0-based verse number.
     * If negative, -1 is the index 0 of pericope, -2 (a) is index 1 (b) of pericope, etc.
     *
     * Convert a to b: b = -a-1;
     * Convert b to a: a = -b-1;
     */
    private val itemPointer_: IntArray by lazy {
        val nverse = verses_.verseCount
        val res = IntArray(nverse + pericopeBlockCount_)

        var pos_block = 0
        var pos_verse = 0
        var pos_itemPointer = 0

        while (true) {
            // check if we still have pericopes remaining
            if (pos_block < pericopeBlockCount_) {
                // still possible
                if (Ari.toVerse(pericopeAris_[pos_block]) - 1 == pos_verse) {
                    // We have a pericope.
                    res[pos_itemPointer++] = -pos_block - 1
                    pos_block++
                    continue
                }
            }

            // check if there is no verses remaining
            if (pos_verse >= nverse) {
                break
            }

            // there is no more pericopes, OR not the time yet for pericopes. So we insert a verse.
            res[pos_itemPointer++] = pos_verse
            pos_verse++
        }

        if (res.size != pos_itemPointer) {
            throw RuntimeException("Algorithm to insert pericopes error!! pos_itemPointer=$pos_itemPointer pos_verse=$pos_verse pos_block=$pos_block nverse=$nverse pericopeBlockCount_=$pericopeBlockCount_ pericopeAris_:${pericopeAris_.contentToString()} pericopeBlocks_:${pericopeBlocks_.contentToString()}")
        }

        res
    }

    val itemCount get() = itemPointer_.size

    fun getItemViewType(position: Int): ItemType {
        val id = itemPointer_[position]
        return if (id >= 0) {
            ItemType.verseText
        } else {
            ItemType.pericope
        }
    }

    fun getVerse_0(position: Int): Int {
        val id = itemPointer_[position]
        return if (id >= 0) {
            id
        } else {
            throw IllegalArgumentException("getVerse_0: position=$position has id of $id")
        }
    }

    fun getPericopeIndex(position: Int): Int {
        val id = itemPointer_[position]
        return if (id < 0) {
            id.inv()
        } else {
            throw IllegalArgumentException("getPericopeIndex: position=$position has id of $id")
        }
    }

    /**
     * For example, when pos=0 is a pericope and pos=1 is the first verse,
     * this method returns 0.
     *
     * @return position on this adapter, or -1 if not found
     */
    fun getPositionOfPericopeBeginningFromVerse(verse_1: Int): Int {
        val verse_0 = verse_1 - 1

        var i = 0
        val len = itemPointer_.size
        while (i < len) {
            if (itemPointer_[i] == verse_0) {
                // we've found it, but if we can move back to pericopes, it is better.
                for (j in i - 1 downTo 0) {
                    if (itemPointer_[j] < 0) {
                        // it's still pericope, so let's continue
                        i = j
                    } else {
                        // no longer a pericope (means, we are on the previous verse)
                        break
                    }
                }
                return i
            }
            i++
        }

        return -1
    }

    /**
     * Let's say pos 0 is pericope and pos 1 is verse_1 1;
     * then this method called with verse_1=1 returns 1.
     *
     * @return position or -1 if not found
     */
    fun getPositionIgnoringPericopeFromVerse(verse_1: Int): Int {
        val verse_0 = verse_1 - 1

        var i = 0
        val len = itemPointer_.size
        while (i < len) {
            if (itemPointer_[i] == verse_0) return i
            i++
        }

        return -1
    }

    /**
     * Get the 1-based verse number from the position given.
     * If the position points to a pericope, the next verse number is returned.
     * @return verse_1 or 0 if doesn't make sense
     */
    fun getVerse_1FromPosition(position: Int): Int {
        if (position < 0) return 0

        val pos = position.coerceAtMost(itemPointer_.size - 1)

        var id = itemPointer_[pos]

        if (id >= 0) {
            return id + 1
        }

        // it's a pericope. Let's move forward until we get a verse
        for (i in pos + 1 until itemPointer_.size) {
            id = itemPointer_[i]

            if (id >= 0) {
                return id + 1
            }
        }

        AppLog.w(TAG, "pericope title at the last position? does not make sense.")
        return 0
    }

    /**
     * Get the 1-based verse number and the distance to the verse from the position given.
     * If the position points to a pericope, the next verse number is returned.
     * @return [LocateResult.EMPTY] if the position points to a pericope without following verse.
     */
    fun locateVerse_1FromPosition(position: Int): LocateResult {
        val pos = position.coerceAtMost(itemPointer_.size - 1)

        var id = itemPointer_[pos]

        if (id >= 0) {
            return LocateResult(id + 1, 0)
        }

        // it's a pericope. Let's move forward until we get a verse
        var distance = 0
        for (i in pos + 1 until itemPointer_.size) {
            distance++
            id = itemPointer_[i]

            if (id >= 0) {
                return LocateResult(id + 1, distance)
            }
        }

        AppLog.w(TAG, "pericope title at the last position? does not make sense.")
        return LocateResult(0, 0)
    }

    /**
     * Similar to [getVerse_1FromPosition], but returns 0 if the specified position is a pericope or doesn't make sense.
     */
    fun getVerseOrPericopeFromPosition(position: Int): Int {
        if (position < 0 || position >= itemPointer_.size) {
            return 0
        }

        val id = itemPointer_[position]

        return if (id >= 0) {
            id + 1
        } else {
            0
        }
    }

    fun getVerseText(verse_1: Int): String? {
        return if (verse_1 in 1..verses_.verseCount) {
            verses_.getVerse(verse_1 - 1)
        } else {
            null
        }
    }

    fun isEnabled(position: Int): Boolean {
        // guard against wild ListView.onInitializeAccessibilityNodeInfoForItem
        return when {
            position >= itemPointer_.size -> false
            itemPointer_[position] >= 0 -> true
            else -> false
        }
    }

    companion object {
        @JvmField
        val EMPTY = VersesDataModel(
            ari_bc_ = 0,
            verses_ = SingleChapterVerses.EMPTY,
            pericopeBlockCount_ = 0,
            pericopeAris_ = IntArray(0),
            pericopeBlocks_ = emptyArray(),
            version_ = null,
            versionId_ = null,
            versesAttributes = VersesAttributes.createEmpty(0)
        )
    }
}