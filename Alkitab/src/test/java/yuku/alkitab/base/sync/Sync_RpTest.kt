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
    fun `Content equals is reflexive — a Content is always equal to itself`() {
        val c = content(1_000_000L, setOf(1, 2, 3))
        assertEquals(c, c)
    }

    @Test
    fun `Content equals returns true when startTime and done are both identical, and hashCode matches too`() {
        val a = content(1_000_000L, setOf(1, 2, 3))
        val b = content(1_000_000L, setOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `Content equals ignores insertion order of the done set (Set equality is order-insensitive)`() {
        // Set equality is order-insensitive by contract, regardless of concrete impl.
        val a = content(0L, linkedSetOf(1, 2, 3))
        val b = content(0L, linkedSetOf(3, 2, 1))
        assertEquals(a, b)
    }

    @Test
    fun `Content equals returns false when startTime differs`() {
        assertNotEquals(
            content(1_000_000L, setOf(1)),
            content(2_000_000L, setOf(1)),
        )
    }

    @Test
    fun `Content equals returns false when the done set contents differ`() {
        assertNotEquals(
            content(0L, setOf(1, 2)),
            content(0L, setOf(1, 3)),
        )
    }

    @Test
    fun `Content equals returns false when one side has a null startTime and the other has a value`() {
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
    fun `Content equals returns false when one side has a null done set and the other has a value`() {
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
    fun `Content equals returns true when both startTime and done are null on both sides`() {
        assertEquals(content(null, null), content(null, null))
    }

    @Test
    fun `Content equals returns true for a reading plan that has been started but has zero completions`() {
        // A reading plan started but with zero completions.
        assertEquals(content(5L, emptySet()), content(5L, emptySet()))
    }

    @Test
    fun `Content equals distinguishes an empty done set from a done set with entries`() {
        assertNotEquals(
            content(5L, emptySet()),
            content(5L, setOf(1)),
        )
    }

    @Test
    fun `Content equals returns false when compared to null`() {
        val c = content(0L, emptySet())
        assertFalse(c.equals(null))
    }

    @Test
    fun `Content equals returns false when compared to an object of a different type`() {
        val c = content(0L, emptySet())
        assertFalse(c.equals("not a content"))
    }

    @Test
    fun `Content hashCode is consistent across multiple invocations on the same instance`() {
        val c = content(1_000_000L, setOf(1, 2, 3))
        assertEquals(c.hashCode(), c.hashCode())
    }

    @Test
    fun `Content hashCode returns 0 when both startTime and done are null`() {
        assertEquals(0, Sync_Rp.Content().hashCode())
    }

    //endregion

    //region toString

    @Test
    fun `Content toString includes startTime, done, and is wrapped in Content{}`() {
        val s = content(1234L, setOf(5, 6)).toString()
        assertTrue("toString should contain startTime: $s", s.contains("startTime=1234"))
        assertTrue("toString should contain done set: $s", s.contains("done="))
        assertTrue("toString should start with Content{: $s", s.startsWith("Content{"))
    }

    @Test
    fun `Content toString shows 'null' for null startTime and done fields`() {
        val s = Sync_Rp.Content().toString()
        assertTrue(s.contains("startTime=null"))
        assertTrue(s.contains("done=null"))
    }

    //endregion
}
