package yuku.alkitab.base.util


class BackForwardList {
    class Entry(
        /**
         * The ari that was requested when this entry is created,
         * i.e. the "entrance" ari.
         */
        val initialAri: Int
    ) {
        /**
         * The ari that is displayed after user scrolls,
         * this can change. In the beginning it will be the same as [initialAri].
         */
        var currentAri: Int = initialAri
            set(value) {
                field = value
                modifyTime = System.currentTimeMillis()
            }

        val createTime: Long = System.currentTimeMillis()

        var modifyTime: Long = createTime
            private set
    }

    private val entries = mutableListOf<Entry>()

    /**
     * Get the index of the current entry.
     * @return -1 if the list is empty.
     */
    var currentIndex: Int = -1
        private set

    /**
     * Return the current entry. This method returns `null` if the list is empty.
     */
    val currentEntry: Entry? get() = getEntry(currentIndex)

    /**
     * Get the entry at the given [index].
     */
    fun getEntry(index: Int): Entry? = entries.getOrNull(index)

    /**
     * Get the number of entries of the back forward list.
     */
    val size: Int get() = entries.size

    /**
     * Update the [Entry.currentAri] of the current entry with the given [ari].
     * If there is no current entry, nothing happens.
     */
    fun updateCurrentEntry(ari: Int) {
        currentEntry?.currentAri = ari
    }

    /**
     * Set the current index to the given [index].
     * The index must be in 0..[size].
     */
    fun moveTo(index: Int) {
        if (index !in 0..size) throw IllegalArgumentException("wrong index given $index, must be in 0..$size")
        currentIndex = index
    }

    /**
     * Remove entries (if any) after the current index,
     * and add a new entry with the given [initialAri],
     * and set the currentIndex to point to the newly added entry.
     */
    fun newEntry(initialAri: Int) {
        for (i in size - 1 downTo currentIndex + 1) {
            entries.removeAt(i)
        }
        entries += Entry(initialAri)
        currentIndex = entries.lastIndex
    }
}
