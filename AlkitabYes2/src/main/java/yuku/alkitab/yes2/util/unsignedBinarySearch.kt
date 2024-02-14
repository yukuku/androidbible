package yuku.alkitab.yes2.util

/**
 * Treat [a] as a sorted array of unsigned ints and do binary search on it.
 *
 * @param key element to look for, treated as an unsigned int.
 */
fun unsignedIntBinarySearch(a: IntArray, key: Int): Int {
    var low = 0
    var high = a.size - 1

    while (low <= high) {
        val mid = (low + high) ushr 1
        val midVal = a[mid]

        val cmp = uintCompare(midVal, key)
        if (cmp < 0) {
            low = mid + 1
        } else if (cmp > 0) {
            high = mid - 1
        } else {
            // key found
            return mid
        }
    }

    return -(low + 1) // key not found.
}

private fun uintCompare(v1: Int, v2: Int): Int = (v1 xor Int.MIN_VALUE).compareTo(v2 xor Int.MIN_VALUE)
