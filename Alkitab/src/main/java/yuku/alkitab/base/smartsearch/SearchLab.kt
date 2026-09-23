package yuku.alkitab.base.smartsearch

/**
 * Change counter for everything the Search Lab can alter (the switches, the word list mode, the
 * word list files). The search screen compares it on return and reruns its search when it moved,
 * so what it shows always reflects the current setup.
 */
object SearchLab {
    @Volatile
    var generation = 0
        private set

    fun changed() {
        generation++
    }
}
