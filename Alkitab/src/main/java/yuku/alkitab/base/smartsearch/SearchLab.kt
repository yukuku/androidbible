package yuku.alkitab.base.smartsearch

/**
 * Lets the search screen detect settings changes when returning from Search Lab.
 */
object SearchLab {
    @Volatile
    var generation = 0
        private set

    fun changed() {
        generation++
    }
}
