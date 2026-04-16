package yuku.alkitab.base.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Sync_Rp.Content] equals / hashCode / toString.
 *
 * Reading plan progress is keyed by reading plan gid. Content contains the plan's
 * startTime (when the user started) and a set of "done" reading codes (which days
 * the user has marked as read).
 */
class Sync_RpTest {

    private fun content(startTime: Long?, done: Set<Int>?): Sync_Rp.Content {
        val c = Sync_Rp.Content()
        c.startTime = startTime
        c.done = done
        return c
    }

    //region equals / hashCode

    @Test
    fun equals_reflexive() {
        val c = content(1_000_000L, setOf(1, 2, 3))
        assertEquals(c, c)
    }

    @Test
    fun equals_sameValues_true() {
        val a = content(1_000_000L, setOf(1, 2, 3))
        val b = content(1_000_000L, setOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equals_sameDoneSetDifferentOrder_true() {
        // Set equality is order-insensitive by contract, regardless of concrete impl.
        val a = content(0L, linkedSetOf(1, 2, 3))
        val b = content(0L, linkedSetOf(3, 2, 1))
        assertEquals(a, b)
    }

    @Test
    fun equals_differentStartTime_false() {
        assertNotEquals(
            content(1_000_000L, setOf(1)),
            content(2_000_000L, setOf(1)),
        )
    }

    @Test
    fun equals_differentDone_false() {
        assertNotEquals(
            content(0L, setOf(1, 2)),
            content(0L, setOf(1, 3)),
        )
    }

    @Test
    fun equals_oneStartTimeNull_false() {
        assertNotEquals(
            content(null, setOf(1)),
            content(1_000_000L, setOf(1)),
        )
        assertNotEquals(
            content(1_000_000L, setOf(1)),
            content(null, setOf(1)),
        )
    }

    @Test
    fun equals_oneDoneNull_false() {
        assertNotEquals(
            content(0L, null),
            content(0L, setOf(1)),
        )
        assertNotEquals(
            content(0L, setOf(1)),
            content(0L, null),
        )
    }

    @Test
    fun equals_bothNullFields_true() {
        assertEquals(content(null, null), content(null, null))
    }

    @Test
    fun equals_emptyDoneSet_true() {
        // A reading plan started but with zero completions.
        assertEquals(content(5L, emptySet()), content(5L, emptySet()))
    }

    @Test
    fun equals_emptyDoneVsNonEmpty_false() {
        assertNotEquals(
            content(5L, emptySet()),
            content(5L, setOf(1)),
        )
    }

    @Test
    fun equals_null_false() {
        val c = content(0L, emptySet())
        assertFalse(c.equals(null))
    }

    @Test
    fun equals_differentType_false() {
        val c = content(0L, emptySet())
        assertFalse(c.equals("not a content"))
    }

    @Test
    fun hashCode_consistent() {
        val c = content(1_000_000L, setOf(1, 2, 3))
        assertEquals(c.hashCode(), c.hashCode())
    }

    @Test
    fun hashCode_bothNullFields_zero() {
        assertEquals(0, Sync_Rp.Content().hashCode())
    }

    //endregion

    //region toString

    @Test
    fun toString_containsValues() {
        val s = content(1234L, setOf(5, 6)).toString()
        assertTrue("toString should contain startTime: $s", s.contains("startTime=1234"))
        assertTrue("toString should contain done set: $s", s.contains("done="))
        assertTrue("toString should start with Content{: $s", s.startsWith("Content{"))
    }

    @Test
    fun toString_nullFields_showsNull() {
        val s = Sync_Rp.Content().toString()
        assertTrue(s.contains("startTime=null"))
        assertTrue(s.contains("done=null"))
    }

    //endregion
}
