@file:OptIn(ExperimentalUnsignedTypes::class)

package yuku.alkitab.yes2.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UnsignedBinarySearchKtTest {

    private val allPositiveArray = IntArray(100) { it + 10 }
    private val allNegativeArray = IntArray(100) { (0xc000_0000L + it.toLong()).toInt() }
    private val mixedSignArray = IntArray(100) { it } + IntArray(100) { (0xc000_0000L + it.toLong()).toInt() }
    private val skipArray = IntArray(100) { it * 2 } + IntArray(100) { (0xc000_0000L + (it * 2).toLong()).toInt() }


    @Before
    fun checkSorted() {
        // all arrays under test must be sorted
        fun assertSorted(a: IntArray) {
            val u = UIntArray(a.size) { a[it].toUInt() }
            assertArrayEquals(u.toTypedArray(), u.sorted().toTypedArray())
        }

        assertSorted(allPositiveArray)
        assertSorted(allNegativeArray)
        assertSorted(mixedSignArray)
    }

    @Test
    fun unsignedBinarySearch() {
        for (i in allPositiveArray.indices) {
            assertEquals(i, unsignedIntBinarySearch(allPositiveArray, i + 10))
        }
        assertEquals(0.inv(), unsignedIntBinarySearch(allPositiveArray, 0))
        assertEquals(100.inv(), unsignedIntBinarySearch(allPositiveArray, -1))
        assertEquals(100.inv(), unsignedIntBinarySearch(allPositiveArray, -2))
        assertEquals(100.inv(), unsignedIntBinarySearch(allPositiveArray, 400))

        for (i in allNegativeArray.indices) {
            assertEquals(i, unsignedIntBinarySearch(allNegativeArray, allNegativeArray[i]))
        }
        assertEquals(0.inv(), unsignedIntBinarySearch(allNegativeArray, 0))
        assertEquals(100.inv(), unsignedIntBinarySearch(allNegativeArray, -1))
        assertEquals(100.inv(), unsignedIntBinarySearch(allNegativeArray, -2))
        assertEquals(0.inv(), unsignedIntBinarySearch(allNegativeArray, 400))

        for (i in mixedSignArray.indices) {
            assertEquals(i, unsignedIntBinarySearch(mixedSignArray, mixedSignArray[i]))
        }
        assertEquals(0, unsignedIntBinarySearch(mixedSignArray, 0))
        assertEquals(200.inv(), unsignedIntBinarySearch(mixedSignArray, -1))
        assertEquals(200.inv(), unsignedIntBinarySearch(mixedSignArray, -2))
        // in the middle
        assertEquals(100.inv(), unsignedIntBinarySearch(mixedSignArray, 400))

        for (i in skipArray.indices) {
            assertEquals(i, unsignedIntBinarySearch(skipArray, skipArray[i]))
        }
        assertEquals(0, unsignedIntBinarySearch(skipArray, 0))
        assertEquals(2.inv(), unsignedIntBinarySearch(skipArray, 3))
        assertEquals(103.inv(), unsignedIntBinarySearch(skipArray, (0xc000_0000L + 5L).toInt()))
    }
}
