package yuku.alkitab.util

import org.junit.Assert.assertEquals
import org.junit.Test

class IntArrayListTest {
    @Test
    fun `a list created with zero capacity accepts added elements`() {
        val list = IntArrayList(0)

        list.add(7)
        list.add(8)
        list.add(9)

        assertEquals(3, list.size())
        assertEquals(listOf(7, 8, 9), List(list.size()) { list.get(it) })
    }

    @Test
    fun `growing past the initial capacity keeps every element`() {
        val list = IntArrayList(2)

        repeat(100) { list.add(it * 3) }

        assertEquals(100, list.size())
        assertEquals(List(100) { it * 3 }, List(list.size()) { list.get(it) })
    }
}
