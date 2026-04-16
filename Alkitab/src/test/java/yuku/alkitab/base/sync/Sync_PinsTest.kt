package yuku.alkitab.base.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Sync_Pins]:
 * - [Sync_Pins.Content.Pin] equals / hashCode / toString
 * - [Sync_Pins.Content] equals / hashCode
 */
class Sync_PinsTest {

    private fun pin(presetId: Int, ari: Int, caption: String? = null, modifyTime: Int = 0): Sync_Pins.Content.Pin {
        val p = Sync_Pins.Content.Pin()
        p.preset_id = presetId
        p.ari = ari
        p.caption = caption
        p.modifyTime = modifyTime
        return p
    }

    //region Pin.equals / hashCode

    @Test
    fun pin_equals_reflexive() {
        val p = pin(1, 100, "cap", 500)
        assertEquals(p, p)
    }

    @Test
    fun pin_equals_sameValues() {
        val a = pin(1, 100, "cap", 500)
        val b = pin(1, 100, "cap", 500)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun pin_equals_null_false() {
        val a = pin(1, 100, "cap", 500)
        assertFalse(a.equals(null))
    }

    @Test
    fun pin_equals_differentType_false() {
        val a = pin(1, 100, "cap", 500)
        assertFalse(a.equals("not a pin"))
    }

    @Test
    fun pin_equals_differentPresetId_false() {
        assertNotEquals(pin(1, 100, "cap", 500), pin(2, 100, "cap", 500))
    }

    @Test
    fun pin_equals_differentAri_false() {
        assertNotEquals(pin(1, 100, "cap", 500), pin(1, 200, "cap", 500))
    }

    @Test
    fun pin_equals_differentCaption_false() {
        assertNotEquals(pin(1, 100, "a", 500), pin(1, 100, "b", 500))
    }

    @Test
    fun pin_equals_differentModifyTime_false() {
        assertNotEquals(pin(1, 100, "cap", 500), pin(1, 100, "cap", 999))
    }

    @Test
    fun pin_equals_bothCaptionsNull_true() {
        assertEquals(pin(1, 100, null, 500), pin(1, 100, null, 500))
    }

    @Test
    fun pin_equals_oneCaptionNull_false() {
        assertNotEquals(pin(1, 100, null, 500), pin(1, 100, "cap", 500))
        assertNotEquals(pin(1, 100, "cap", 500), pin(1, 100, null, 500))
    }

    @Test
    fun pin_toString_containsKeyFields() {
        val s = pin(3, 42, "hello", 12345).toString()
        assertTrue("preset_id present: $s", s.contains("preset_id=3"))
        assertTrue("ari present: $s", s.contains("ari=42"))
        assertTrue("caption present: $s", s.contains("hello"))
        assertTrue("modifyTime present: $s", s.contains("12345"))
    }

    //endregion

    //region Content.equals / hashCode

    private fun content(vararg pins: Sync_Pins.Content.Pin): Sync_Pins.Content {
        val c = Sync_Pins.Content()
        c.pins = pins.toMutableList()
        return c
    }

    @Test
    fun content_equals_bothNullPins_true() {
        val a = Sync_Pins.Content()
        val b = Sync_Pins.Content()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun content_equals_oneNullPins_false() {
        val a = Sync_Pins.Content()
        val b = content()
        assertNotEquals(a, b)
        assertNotEquals(b, a)
    }

    @Test
    fun content_equals_bothEmptyPins_true() {
        assertEquals(content(), content())
    }

    @Test
    fun content_equals_samePinsSameOrder_true() {
        val a = content(pin(0, 10), pin(1, 20), pin(2, 30))
        val b = content(pin(0, 10), pin(1, 20), pin(2, 30))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun content_equals_samePinsDifferentOrder_true() {
        // Content.equals is intentionally order-insensitive: it sorts both pin lists
        // by preset_id before comparing. This matters when a deserialized shadow
        // happens to list pins in a different order than getEntitiesFromCurrent() —
        // an order-sensitive comparison would spuriously flag "mod".
        val a = content(pin(0, 10), pin(1, 20), pin(2, 30))
        val b = content(pin(2, 30), pin(0, 10), pin(1, 20))
        assertEquals(a, b)
        // hashCode must be order-insensitive too, to satisfy the Object contract
        // (equal objects must have equal hash codes). If this is broken, these
        // Contents misbehave inside HashMap / HashSet.
        assertEquals(
            "hashCode must be order-insensitive to match equals",
            a.hashCode(),
            b.hashCode(),
        )
    }

    @Test
    fun content_equals_differentPinCount_false() {
        val a = content(pin(0, 10), pin(1, 20))
        val b = content(pin(0, 10))
        assertNotEquals(a, b)
    }

    @Test
    fun content_equals_differentPinContent_false() {
        val a = content(pin(0, 10))
        val b = content(pin(0, 20))
        assertNotEquals(a, b)
    }

    @Test
    fun content_equals_reflexive() {
        val c = content(pin(0, 10), pin(1, 20))
        assertEquals(c, c)
    }

    @Test
    fun content_equals_null_false() {
        val c = content(pin(0, 10))
        assertFalse(c.equals(null))
    }

    @Test
    fun content_equals_differentType_false() {
        val c = content(pin(0, 10))
        assertFalse(c.equals("not a content"))
    }

    @Test
    fun content_hashCode_nullPins_returnsZero() {
        // hashCode() explicitly: `pins != null ? pins.hashCode() : 0`
        assertEquals(0, Sync_Pins.Content().hashCode())
    }

    //endregion

    //region toString

    @Test
    fun content_toString_returnsNonEmpty() {
        val c = content(pin(0, 10))
        val s = c.toString()
        assertTrue("toString should start with Content{: $s", s.startsWith("Content{"))
        assertTrue("toString should end with }: $s", s.endsWith("}"))
    }

    //endregion
}
