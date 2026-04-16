package yuku.alkitab.base.sync

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label

/**
 * Unit tests for [Sync_Mabel]:
 * - [Sync_Mabel.Content] equals / hashCode / toString
 * - [Sync_Mabel.updateMarkerWithEntityContent]
 * - [Sync_Mabel.updateLabelWithEntityContent]
 * - [Sync_Mabel.updateMarker_LabelWithEntityContent]
 */
class Sync_MabelTest {

    //region updateMarkerWithEntityContent

    @Test
    fun updateMarker_nullMarker_createsNewMarker() {
        val content = Sync_Mabel.Content().apply {
            ari = 0x010203
            kind = Marker.Kind.note.code
            caption = "my note"
            verseCount = 2
            createTime = 1_600_000_000
            modifyTime = 1_600_000_500
        }

        val res = Sync_Mabel.updateMarkerWithEntityContent(null, "gid-1", content)

        assertNotNull(res)
        assertEquals("gid-1", res.gid)
        assertEquals(0x010203, res.ari)
        assertEquals(Marker.Kind.note, res.kind)
        assertEquals("my note", res.caption)
        assertEquals(2, res.verseCount)
        assertEquals(1_600_000_000L * 1000, res.createTime.time)
        assertEquals(1_600_000_500L * 1000, res.modifyTime.time)
    }

    @Test
    fun updateMarker_existingMarker_overwritesFieldsPreservesId() {
        val existing = Marker.createEmptyMarker().apply {
            _id = 42
            gid = "old-gid"
            ari = 1
            kind = Marker.Kind.bookmark
            caption = "old caption"
            verseCount = 1
            createTime = Date(1_000_000L * 1000)
            modifyTime = Date(1_000_500L * 1000)
        }

        val content = Sync_Mabel.Content().apply {
            ari = 99
            kind = Marker.Kind.highlight.code
            caption = "new caption"
            verseCount = 5
            createTime = 2_000_000
            modifyTime = 2_000_500
        }

        val res = Sync_Mabel.updateMarkerWithEntityContent(existing, "new-gid", content)

        assertSame("should mutate and return the same instance", existing, res)
        assertEquals(42, res._id)                   // _id preserved
        assertEquals("new-gid", res.gid)            // gid overwritten
        assertEquals(99, res.ari)
        assertEquals(Marker.Kind.highlight, res.kind)
        assertEquals("new caption", res.caption)
        assertEquals(5, res.verseCount)
        assertEquals(2_000_000L * 1000, res.createTime.time)
        assertEquals(2_000_500L * 1000, res.modifyTime.time)
    }

    @Test
    fun updateMarker_allThreeKinds_roundTripCorrectly() {
        for (k in Marker.Kind.values()) {
            val content = Sync_Mabel.Content().apply {
                ari = 1
                kind = k.code
                caption = ""
                verseCount = 1
                createTime = 0
                modifyTime = 0
            }
            val res = Sync_Mabel.updateMarkerWithEntityContent(null, "gid-$k", content)
            assertEquals(k, res.kind)
        }
    }

    //endregion

    //region updateLabelWithEntityContent

    @Test
    fun updateLabel_nullLabel_createsNewLabel() {
        val content = Sync_Mabel.Content().apply {
            title = "Favorites"
            ordering = 3
            backgroundColor = "#ff0000"
        }

        val res = Sync_Mabel.updateLabelWithEntityContent(null, "lb-1", content)

        assertNotNull(res)
        assertEquals("lb-1", res.gid)
        assertEquals("Favorites", res.title)
        assertEquals(3, res.ordering)
        assertEquals("#ff0000", res.backgroundColor)
    }

    @Test
    fun updateLabel_existingLabel_preservesId() {
        val existing = Label.createEmptyLabel().apply {
            _id = 7
            gid = "old"
            title = "old"
            ordering = 0
            backgroundColor = "#000000"
        }

        val content = Sync_Mabel.Content().apply {
            title = "new"
            ordering = 99
            backgroundColor = "#123456"
        }

        val res = Sync_Mabel.updateLabelWithEntityContent(existing, "new-gid", content)

        assertSame(existing, res)
        assertEquals(7, res._id)
        assertEquals("new-gid", res.gid)
        assertEquals("new", res.title)
        assertEquals(99, res.ordering)
        assertEquals("#123456", res.backgroundColor)
    }

    //endregion

    //region updateMarker_LabelWithEntityContent

    @Test
    fun updateMarker_Label_nullExisting_createsNewAssociation() {
        val content = Sync_Mabel.Content().apply {
            marker_gid = "marker-gid-1"
            label_gid = "label-gid-1"
        }

        val res = Sync_Mabel.updateMarker_LabelWithEntityContent(null, "ml-gid", content)

        assertEquals("ml-gid", res.gid)
        assertEquals("marker-gid-1", res.marker_gid)
        assertEquals("label-gid-1", res.label_gid)
    }

    @Test
    fun updateMarker_Label_existing_preservesId() {
        val existing = Marker_Label.createEmptyMarker_Label().apply {
            _id = 5
            gid = "old"
            marker_gid = "old-marker"
            label_gid = "old-label"
        }

        val content = Sync_Mabel.Content().apply {
            marker_gid = "new-marker"
            label_gid = "new-label"
        }

        val res = Sync_Mabel.updateMarker_LabelWithEntityContent(existing, "new-gid", content)

        assertSame(existing, res)
        assertEquals(5, res._id)
        assertEquals("new-gid", res.gid)
        assertEquals("new-marker", res.marker_gid)
        assertEquals("new-label", res.label_gid)
    }

    //endregion

    //region Content.equals / hashCode

    private fun fullMarkerContent(): Sync_Mabel.Content = Sync_Mabel.Content().apply {
        ari = 0x010203
        kind = 1
        caption = "hi"
        verseCount = 2
        createTime = 100
        modifyTime = 200
    }

    @Test
    fun content_equals_reflexive() {
        val c = fullMarkerContent()
        assertEquals(c, c)
    }

    @Test
    fun content_equals_sameValues() {
        val a = fullMarkerContent()
        val b = fullMarkerContent()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun content_equals_null_false() {
        val c = fullMarkerContent()
        assertFalse(c.equals(null))
    }

    @Test
    fun content_equals_differentType_false() {
        val c = fullMarkerContent()
        assertFalse(c.equals("not a content"))
    }

    @Test
    fun content_equals_differentAri_false() {
        val a = fullMarkerContent()
        val b = fullMarkerContent().apply { ari = 999 }
        assertNotEquals(a, b)
    }

    @Test
    fun content_equals_differentCaption_false() {
        val a = fullMarkerContent()
        val b = fullMarkerContent().apply { caption = "other" }
        assertNotEquals(a, b)
    }

    @Test
    fun content_equals_differentModifyTime_false() {
        // Important: sync uses Content equality to detect whether a mod op is needed.
        val a = fullMarkerContent()
        val b = fullMarkerContent().apply { modifyTime = 999 }
        assertNotEquals(a, b)
    }

    @Test
    fun content_equals_oneFieldNull_false() {
        val a = fullMarkerContent()
        val b = fullMarkerContent().apply { caption = null }
        assertNotEquals(a, b)
        assertNotEquals(b, a)
    }

    @Test
    fun content_equals_bothFieldsNull_true() {
        val a = Sync_Mabel.Content().apply { ari = 1; kind = 1 }
        val b = Sync_Mabel.Content().apply { ari = 1; kind = 1 }
        assertEquals(a, b)
    }

    @Test
    fun content_equals_labelFields() {
        val a = Sync_Mabel.Content().apply {
            title = "Favorites"
            ordering = 0
            backgroundColor = "#ff0000"
        }
        val b = Sync_Mabel.Content().apply {
            title = "Favorites"
            ordering = 0
            backgroundColor = "#ff0000"
        }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = Sync_Mabel.Content().apply {
            title = "Favorites"
            ordering = 0
            backgroundColor = "#00ff00"  // different color
        }
        assertNotEquals(a, c)
    }

    @Test
    fun content_equals_markerLabelFields() {
        val a = Sync_Mabel.Content().apply {
            marker_gid = "m1"
            label_gid = "l1"
        }
        val b = Sync_Mabel.Content().apply {
            marker_gid = "m1"
            label_gid = "l1"
        }
        assertEquals(a, b)

        val c = Sync_Mabel.Content().apply {
            marker_gid = "m1"
            label_gid = "l2"  // different
        }
        assertNotEquals(a, c)
    }

    @Test
    fun content_emptyEquals() {
        assertEquals(Sync_Mabel.Content(), Sync_Mabel.Content())
    }

    @Test
    fun content_hashCode_consistent() {
        val a = fullMarkerContent()
        val h1 = a.hashCode()
        val h2 = a.hashCode()
        assertEquals(h1, h2)
    }

    //endregion

    //region Content.toString

    @Test
    fun content_toString_returnsNonEmpty() {
        val c = fullMarkerContent()
        val s = c.toString()
        assertNotNull(s)
        assertTrue(s.startsWith("{") && s.endsWith("}"))
    }

    @Test
    fun content_toString_includesValuesThatArePresent() {
        val c = Sync_Mabel.Content().apply {
            ari = 0x010203
            caption = "hello"
        }
        val s = c.toString()
        // ARI is printed as-is (decimal form)
        assertTrue("toString should contain ari: $s", s.contains("66051"))
        assertTrue("toString should contain caption: $s", s.contains("hello"))
    }

    @Test
    fun content_toString_skipsNullFields() {
        val c = Sync_Mabel.Content().apply { ari = 1 }
        val s = c.toString()
        // should not contain "null"
        assertFalse("toString should not contain 'null': $s", s.contains("null"))
    }

    @Test
    fun content_toString_longCaptionTruncated() {
        // The q() helper truncates strings longer than 20 chars with an ellipsis.
        val longCaption = "a".repeat(100)
        val c = Sync_Mabel.Content().apply { caption = longCaption }
        val s = c.toString()
        assertTrue("long caption should be truncated with ellipsis: $s", s.contains("…"))
        // Must not contain the full 100 chars of 'a's.
        assertFalse(s.contains("a".repeat(30)))
    }

    @Test
    fun content_toString_newlinesEscaped() {
        val c = Sync_Mabel.Content().apply { caption = "line1\nline2" }
        val s = c.toString()
        assertTrue("newlines should be escaped: $s", s.contains("\\n"))
        // The RAW newline char should not be in the output (because of escaping).
        // But the test file's toString is single-line, so the direct char shouldn't leak.
        val rawNewline = "line1\nline2"
        assertFalse(s.contains(rawNewline))
    }

    @Test
    fun content_toString_truncatesLongGids() {
        val longGid = "abcdefghijklmnopqrstuvwxyz"
        val c = Sync_Mabel.Content().apply {
            marker_gid = longGid
            label_gid = longGid
        }
        val s = c.toString()
        // Should contain only first 10 chars of each gid
        assertTrue(s.contains("abcdefghij"))
        assertFalse("gid should be truncated: $s", s.contains("abcdefghijk"))
    }

    //endregion

    //region End-to-end: applying server delta to local Marker objects

    @Test
    fun applyServerAddOp_toLocalMarker_producesCorrectMarker() {
        // Simulates: server sends an `add` op for a new marker; client creates the Marker.
        val content = Sync_Mabel.Content().apply {
            ari = 0x020406
            kind = Marker.Kind.bookmark.code
            caption = "bookmarked verse"
            verseCount = 1
            createTime = 1_700_000_000
            modifyTime = 1_700_000_000
        }

        val marker = Sync_Mabel.updateMarkerWithEntityContent(null, "server-gid", content)

        assertNotNull(marker)
        assertEquals(Marker.Kind.bookmark, marker.kind)
        assertEquals("server-gid", marker.gid)
    }

    @Test
    fun applyServerModOp_toLocalMarker_preservesLocalId() {
        // Simulates: server sends a `mod` op for an existing marker.
        val localMarker = Marker.createEmptyMarker().apply {
            _id = 123
            gid = "server-gid"
            ari = 1
            kind = Marker.Kind.bookmark
            caption = "original"
            verseCount = 1
            createTime = Date(0)
            modifyTime = Date(0)
        }

        val content = Sync_Mabel.Content().apply {
            ari = 2
            kind = Marker.Kind.note.code
            caption = "edited remotely"
            verseCount = 3
            createTime = 0
            modifyTime = 5000
        }

        val updated = Sync_Mabel.updateMarkerWithEntityContent(localMarker, "server-gid", content)

        assertSame(localMarker, updated)
        assertEquals(123L, updated._id) // _id preserved — critical so local DB row is updated, not duplicated
        assertEquals(Marker.Kind.note, updated.kind)
        assertEquals("edited remotely", updated.caption)
    }

    //endregion
}
