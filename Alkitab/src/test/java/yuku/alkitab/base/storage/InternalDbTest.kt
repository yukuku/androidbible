package yuku.alkitab.base.storage

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import java.util.Date

/**
 * Robolectric-based tests for [InternalDb] covering the risk areas called out
 * by step 18c of the tech-debt remediation plan: marker CRUD, label ordering,
 * highlight storage, and attribute loading.
 *
 * Robolectric is used because [InternalDbHelper] extends Android's
 * [android.database.sqlite.SQLiteOpenHelper] — SQLite cursors, transactions,
 * and `getReadableDatabase()` / `getWritableDatabase()` cannot run under plain
 * JUnit.
 *
 * A bare [Application] is configured (rather than the real
 * `yuku.alkitab.base.App`) so we don't run the heavy onCreate path (Firebase,
 * FCM, extension receivers). The test-scope shadows of
 * `com.google.firebase.crashlytics.FirebaseCrashlytics` and `android.util.Log`
 * (under `Alkitab/src/test/java/…`) keep `AppLog`'s static initializer happy,
 * so we don't need to bootstrap FirebaseApp here either.
 *
 * [yuku.afw.App.context] is wired manually in [setUp] so indirect dependencies
 * (Gson singleton via `App.getDefaultGson()`, `Preferences`) can resolve.
 * Because no `sync_simpleToken` preference is set,
 * [yuku.alkitab.base.sync.Sync.notifySyncNeeded] early-returns, so the DB
 * mutations under test don't trigger any network or background work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class InternalDbTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var db: InternalDb

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app

        helper = InternalDbHelper(app)
        db = InternalDb(helper)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    // region Marker CRUD

    @Test
    fun `insertMarker assigns a positive _id and a freshly generated gid`() {
        val ari = Ari.encode(0, 1, 1)
        val now = Date(1_700_000_000_000L)
        val marker = db.insertMarker(ari, Marker.Kind.bookmark, "Hello", 1, now, now)

        assertTrue("_id should be > 0", marker._id > 0)
        assertNotNull("gid should be assigned", marker.gid)
        assertEquals(ari, marker.ari)
        assertEquals(Marker.Kind.bookmark, marker.kind)
        assertEquals("Hello", marker.caption)
        assertEquals(1, marker.verseCount)
    }

    @Test
    fun `getMarkerById round-trips every persisted field of an inserted marker`() {
        val ari = Ari.encode(42, 3, 16)
        val now = Date(1_700_000_000_000L)
        val inserted = db.insertMarker(ari, Marker.Kind.note, "John 3:16 note", 1, now, now)

        val loaded = db.getMarkerById(inserted._id)
        assertNotNull(loaded)
        assertEquals(inserted._id, loaded!!._id)
        assertEquals(inserted.gid, loaded.gid)
        assertEquals(ari, loaded.ari)
        assertEquals(Marker.Kind.note, loaded.kind)
        assertEquals("John 3:16 note", loaded.caption)
    }

    @Test
    fun `getMarkerByGid returns the same row as getMarkerById for the inserted marker`() {
        val ari = Ari.encode(0, 1, 1)
        val now = Date(1_700_000_000_000L)
        val inserted = db.insertMarker(ari, Marker.Kind.bookmark, "c", 1, now, now)

        val loaded = db.getMarkerByGid(inserted.gid)
        assertNotNull(loaded)
        assertEquals(inserted._id, loaded!!._id)
    }

    @Test
    fun `getMarkerByGid returns null when no row matches the gid`() {
        assertNull(db.getMarkerByGid("nonexistent-gid-0000"))
    }

    @Test
    fun `getMarkerById returns null when no row matches the _id`() {
        assertNull(db.getMarkerById(9999))
    }

    @Test
    fun `insertOrUpdateMarker inserts when _id is zero and updates in place when _id is set`() {
        val ari = Ari.encode(0, 1, 1)
        val now = Date(1_700_000_000_000L)

        val marker = Marker.createNewMarker(ari, Marker.Kind.bookmark, "initial", 1, now, now)
        db.insertOrUpdateMarker(marker)
        val insertedId = marker._id
        assertTrue(insertedId > 0)

        // update: mutate and re-save — the _id should not change
        marker.caption = "updated"
        db.insertOrUpdateMarker(marker)
        assertEquals(insertedId, marker._id)

        val loaded = db.getMarkerById(insertedId)
        assertEquals("updated", loaded!!.caption)
    }

    @Test
    fun `listMarkersForAriKind orders results by modifyTime with the newest first`() {
        val ari = Ari.encode(0, 1, 1)
        val t1 = Date(1_700_000_000_000L)
        val t2 = Date(1_700_000_100_000L)
        val t3 = Date(1_700_000_200_000L)
        db.insertMarker(ari, Marker.Kind.bookmark, "a", 1, t1, t1)
        db.insertMarker(ari, Marker.Kind.bookmark, "b", 1, t2, t2)
        db.insertMarker(ari, Marker.Kind.bookmark, "c", 1, t3, t3)

        val list = db.listMarkersForAriKind(ari, Marker.Kind.bookmark)
        assertEquals(3, list.size)
        assertEquals("c", list[0].caption)
        assertEquals("b", list[1].caption)
        assertEquals("a", list[2].caption)
    }

    @Test
    fun `listMarkersForAriKind filters results by both ari and kind`() {
        val ari1 = Ari.encode(0, 1, 1)
        val ari2 = Ari.encode(0, 1, 2)
        val now = Date(1_700_000_000_000L)
        db.insertMarker(ari1, Marker.Kind.bookmark, "a", 1, now, now)
        db.insertMarker(ari1, Marker.Kind.note, "b", 1, now, now)
        db.insertMarker(ari2, Marker.Kind.bookmark, "c", 1, now, now)

        val list = db.listMarkersForAriKind(ari1, Marker.Kind.bookmark)
        assertEquals(1, list.size)
        assertEquals("a", list[0].caption)
    }

    @Test
    fun `listAllMarkers returns every row regardless of kind`() {
        val now = Date(1_700_000_000_000L)
        db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "a", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 2), Marker.Kind.note, "b", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 3), Marker.Kind.highlight, "c", 1, now, now)

        assertEquals(3, db.listAllMarkers().size)
    }

    @Test
    fun `deleteMarkerById cascades to Marker_Label rows that reference the marker`() {
        val now = Date(1_700_000_000_000L)
        val marker = db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "a", 1, now, now)
        val label = db.insertLabel("L", "#ff0000")
        db.updateLabels(marker, setOf(label))

        assertEquals(1, db.listMarker_LabelsByMarker(marker).size)

        db.deleteMarkerById(marker._id)

        assertNull(db.getMarkerById(marker._id))
        assertEquals(0, db.listMarker_LabelsByMarker(marker).size)
    }

    @Test
    fun `countMarkersForBookChapter counts only markers whose ari falls inside the book-chapter range`() {
        val now = Date(1_700_000_000_000L)
        db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "a", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 15), Marker.Kind.note, "b", 1, now, now)
        // same book, different chapter
        db.insertMarker(Ari.encode(0, 2, 1), Marker.Kind.bookmark, "c", 1, now, now)
        // different book
        db.insertMarker(Ari.encode(1, 1, 1), Marker.Kind.bookmark, "d", 1, now, now)

        assertEquals(2, db.countMarkersForBookChapter(Ari.encode(0, 1, 0)))
        assertEquals(1, db.countMarkersForBookChapter(Ari.encode(0, 2, 0)))
        assertEquals(1, db.countMarkersForBookChapter(Ari.encode(1, 1, 0)))
        assertEquals(0, db.countMarkersForBookChapter(Ari.encode(2, 1, 0)))
    }

    @Test
    fun `countMarkersForBookChapter counts a marker on verse 255 (inclusive upper bound)`() {
        // Regression guard for a boundary bug that used to live in production:
        // countMarkersForBookChapter computed ariMax = ari_bookchapter | 0xff
        // and queried `ari >= ariMin AND ari < ariMax`, which silently dropped
        // any marker on verse 255 (ari == ariMax). The bound has been flipped
        // to inclusive `<=` to match the one used by
        // getHighlightColorRgb(int, IntArrayList). No Bible chapter has 255
        // verses (Psalm 119, the longest, has 176) so this didn't affect real
        // data, but it matters for consistency across the three range queries.
        val now = Date(1_700_000_000_000L)
        db.insertMarker(Ari.encode(0, 1, 254), Marker.Kind.bookmark, "a", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 255), Marker.Kind.bookmark, "b", 1, now, now)

        assertEquals(2, db.countMarkersForBookChapter(Ari.encode(0, 1, 0)))
    }

    @Test
    fun `insertMarker generates a unique gid for each new row`() {
        val now = Date(1_700_000_000_000L)
        val m1 = db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "a", 1, now, now)
        val m2 = db.insertMarker(Ari.encode(0, 1, 2), Marker.Kind.bookmark, "b", 1, now, now)
        assertNotEquals(m1.gid, m2.gid)
    }

    // endregion

    // region Label ordering

    @Test
    fun `insertLabel increments ordering starting from one and persists every field`() {
        val a = db.insertLabel("A", "#100000")
        val b = db.insertLabel("B", "#200000")
        val c = db.insertLabel("C", "#300000")

        assertEquals(1, a.ordering)
        assertEquals(2, b.ordering)
        assertEquals(3, c.ordering)
        assertTrue(a._id > 0)
        assertNotNull(a.gid)
        assertEquals("A", a.title)
        assertEquals("#100000", a.backgroundColor)

        assertEquals(3, db.getLabelMaxOrdering())
    }

    @Test
    fun `listAllLabels returns rows sorted by ordering ascending`() {
        val a = db.insertLabel("A", null)
        val b = db.insertLabel("B", null)
        val c = db.insertLabel("C", null)

        val labels = db.listAllLabels()
        assertEquals(3, labels.size)
        assertEquals(a._id, labels[0]._id)
        assertEquals(b._id, labels[1]._id)
        assertEquals(c._id, labels[2]._id)
    }

    @Test
    fun `reorderLabels moves a label up and shifts intervening labels one slot down`() {
        // insert 5 labels: ordering 1..5
        val labels = (1..5).map { db.insertLabel("L$it", null) }

        // move label at ordering 4 to ordering 2 (move up)
        db.reorderLabels(labels[3], labels[1])

        val reordered = db.listAllLabels().associateBy { it._id }
        // expected final ordering:
        //  _id1: 1 (unchanged)
        //  _id2: 3 (was 2, shifted down)
        //  _id3: 4 (was 3, shifted down)
        //  _id4: 2 (moved here)
        //  _id5: 5 (unchanged)
        assertEquals(1, reordered[labels[0]._id]!!.ordering)
        assertEquals(3, reordered[labels[1]._id]!!.ordering)
        assertEquals(4, reordered[labels[2]._id]!!.ordering)
        assertEquals(2, reordered[labels[3]._id]!!.ordering)
        assertEquals(5, reordered[labels[4]._id]!!.ordering)
    }

    @Test
    fun `reorderLabels moves a label down and shifts intervening labels one slot up`() {
        val labels = (1..5).map { db.insertLabel("L$it", null) }

        // move label at ordering 2 to ordering 4 (move down)
        db.reorderLabels(labels[1], labels[3])

        val reordered = db.listAllLabels().associateBy { it._id }
        // expected final ordering:
        //  _id1: 1 (unchanged)
        //  _id2: 4 (moved here)
        //  _id3: 2 (was 3, shifted up)
        //  _id4: 3 (was 4, shifted up)
        //  _id5: 5 (unchanged)
        assertEquals(1, reordered[labels[0]._id]!!.ordering)
        assertEquals(4, reordered[labels[1]._id]!!.ordering)
        assertEquals(2, reordered[labels[2]._id]!!.ordering)
        assertEquals(3, reordered[labels[3]._id]!!.ordering)
        assertEquals(5, reordered[labels[4]._id]!!.ordering)
    }

    @Test
    fun `sortLabelsAlphabetically reassigns ordering case-insensitively by title`() {
        db.insertLabel("banana", null)
        db.insertLabel("Apple", null)
        db.insertLabel("cherry", null)

        db.sortLabelsAlphabetically()

        val labels = db.listAllLabels()
        assertEquals(3, labels.size)
        assertEquals("Apple", labels[0].title)
        assertEquals(1, labels[0].ordering)
        assertEquals("banana", labels[1].title)
        assertEquals(2, labels[1].ordering)
        assertEquals("cherry", labels[2].title)
        assertEquals(3, labels[2].ordering)
    }

    @Test
    fun `getLabelById and getLabelByGid round-trip an inserted label and return null for misses`() {
        val label = db.insertLabel("tag", "#abcdef")

        val byId = db.getLabelById(label._id)
        assertNotNull(byId)
        assertEquals("tag", byId!!.title)

        val byGid = db.getLabelByGid(label.gid)
        assertNotNull(byGid)
        assertEquals(label._id, byGid!!._id)

        assertNull(db.getLabelById(9999))
        assertNull(db.getLabelByGid("no-such-gid"))
    }

    @Test
    fun `updateLabels adds associations for new labels and removes associations for missing ones`() {
        val now = Date(1_700_000_000_000L)
        val marker = db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "m", 1, now, now)
        val la = db.insertLabel("A", null)
        val lb = db.insertLabel("B", null)
        val lc = db.insertLabel("C", null)

        db.updateLabels(marker, setOf(la, lb))
        var gids = db.listLabelsByMarker(marker).map { it.gid }.toSet()
        assertEquals(setOf(la.gid, lb.gid), gids)

        // replace with a different set
        db.updateLabels(marker, setOf(lb, lc))
        gids = db.listLabelsByMarker(marker).map { it.gid }.toSet()
        assertEquals(setOf(lb.gid, lc.gid), gids)
    }

    @Test
    fun `countMarkersWithLabel counts the Marker_Label associations for a given label`() {
        val now = Date(1_700_000_000_000L)
        val m1 = db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "m1", 1, now, now)
        val m2 = db.insertMarker(Ari.encode(0, 1, 2), Marker.Kind.bookmark, "m2", 1, now, now)
        val label = db.insertLabel("L", null)

        assertEquals(0, db.countMarkersWithLabel(label))

        db.updateLabels(m1, setOf(label))
        db.updateLabels(m2, setOf(label))

        assertEquals(2, db.countMarkersWithLabel(label))
    }

    @Test
    fun `deleteLabelAndMarker_LabelsByLabelId deletes the label and its associations but leaves the markers intact`() {
        val now = Date(1_700_000_000_000L)
        val marker = db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "m", 1, now, now)
        val label = db.insertLabel("L", null)
        db.updateLabels(marker, setOf(label))

        assertEquals(1, db.listMarker_LabelsByMarker(marker).size)

        db.deleteLabelAndMarker_LabelsByLabelId(label._id)

        assertNull(db.getLabelById(label._id))
        assertEquals(0, db.listMarker_LabelsByMarker(marker).size)
        // the marker itself should still exist
        assertNotNull(db.getMarkerById(marker._id))
    }

    @Test
    fun `listLabelsByMarker returns labels ordered by Label ordering ascending regardless of insert order`() {
        val now = Date(1_700_000_000_000L)
        val marker = db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "m", 1, now, now)
        val la = db.insertLabel("A", null) // ordering 1
        val lb = db.insertLabel("B", null) // ordering 2
        val lc = db.insertLabel("C", null) // ordering 3
        db.updateLabels(marker, setOf(lc, la, lb))

        val labels = db.listLabelsByMarker(marker)
        assertEquals(listOf(la._id, lb._id, lc._id), labels.map { it._id })
    }

    // endregion

    // region Highlight storage

    @Test
    fun `updateOrInsertHighlights inserts one full-verse highlight marker per selected verse`() {
        val ariBc = Ari.encode(0, 1, 0)
        val verses = IntArrayList().apply { add(1); add(2); add(3) }

        db.updateOrInsertHighlights(ariBc, verses, 0x112233)

        val markers = db.listAllMarkers()
        assertEquals(3, markers.size)
        markers.forEach { m ->
            assertEquals(Marker.Kind.highlight, m.kind)
            val info = Highlights.decode(m.caption)
            assertNotNull(info)
            assertEquals(0x112233, info!!.colorRgb)
            assertNull(info.partial)
        }
    }

    @Test
    fun `updateOrInsertHighlights updates the color of the existing marker when reapplied to the same verse`() {
        val ariBc = Ari.encode(0, 1, 0)
        val verse = IntArrayList().apply { add(1) }

        db.updateOrInsertHighlights(ariBc, verse, 0x111111)
        assertEquals(1, db.listAllMarkers().size)

        db.updateOrInsertHighlights(ariBc, verse, 0x222222)

        val markers = db.listAllMarkers()
        assertEquals(1, markers.size)
        val info = Highlights.decode(markers[0].caption)
        assertEquals(0x222222, info!!.colorRgb)
    }

    @Test
    fun `updateOrInsertHighlights with color -1 deletes an existing highlight for the verse`() {
        val ariBc = Ari.encode(0, 1, 0)
        val verse = IntArrayList().apply { add(1) }

        db.updateOrInsertHighlights(ariBc, verse, 0x111111)
        assertEquals(1, db.listAllMarkers().size)

        db.updateOrInsertHighlights(ariBc, verse, -1)

        assertEquals(0, db.listAllMarkers().size)
    }

    @Test
    fun `updateOrInsertHighlights with color -1 is a no-op when no existing highlight is present`() {
        val ariBc = Ari.encode(0, 1, 0)
        val verse = IntArrayList().apply { add(1) }

        db.updateOrInsertHighlights(ariBc, verse, -1)
        assertEquals(0, db.listAllMarkers().size)
    }

    @Test
    fun `updateOrInsertPartialHighlight encodes the color along with the verse-text hashCode and offsets`() {
        val ari = Ari.encode(0, 1, 1)
        val verseText = "In the beginning God created the heaven and the earth."

        db.updateOrInsertPartialHighlight(ari, 0x445566, verseText, 7, 20)

        val info = db.getHighlightColorRgb(ari)
        assertNotNull(info)
        assertEquals(0x445566, info!!.colorRgb)
        assertNotNull(info.partial)
        assertEquals(7, info.partial!!.startOffset)
        assertEquals(20, info.partial!!.endOffset)
        assertEquals(Highlights.hashCode(verseText), info.partial!!.hashCode)
    }

    @Test
    fun `updateOrInsertPartialHighlight updates the newest highlight and deletes older duplicates for the same ari`() {
        val ari = Ari.encode(0, 1, 1)
        val text = "some verse text"

        // manually insert two highlight markers for the same ari (simulating a sync conflict)
        val t1 = Date(1_700_000_000_000L)
        val t2 = Date(1_700_000_100_000L)
        db.insertMarker(ari, Marker.Kind.highlight, Highlights.encode(0x111111), 1, t1, t1)
        db.insertMarker(ari, Marker.Kind.highlight, Highlights.encode(0x222222), 1, t2, t2)
        assertEquals(2, db.listMarkersForAriKind(ari, Marker.Kind.highlight).size)

        db.updateOrInsertPartialHighlight(ari, 0x333333, text, 0, 4)

        val remaining = db.listMarkersForAriKind(ari, Marker.Kind.highlight)
        assertEquals(1, remaining.size)
        val info = Highlights.decode(remaining[0].caption)
        assertEquals(0x333333, info!!.colorRgb)
    }

    @Test
    fun `getHighlightColorRgb for a single ari returns null when no highlight exists`() {
        assertNull(db.getHighlightColorRgb(Ari.encode(0, 1, 1)))
    }

    @Test
    fun `getHighlightColorRgb for multiple verses returns the shared color when every selected verse matches`() {
        val ariBc = Ari.encode(0, 1, 0)
        val verses = IntArrayList().apply { add(1); add(2); add(3) }
        db.updateOrInsertHighlights(ariBc, verses, 0x778899)

        val color = db.getHighlightColorRgb(ariBc, verses)
        assertEquals(0x778899, color)
    }

    @Test
    fun `getHighlightColorRgb for multiple verses returns -1 when selected verses have different colors`() {
        val ariBc = Ari.encode(0, 1, 0)
        db.updateOrInsertHighlights(ariBc, IntArrayList().apply { add(1) }, 0x111111)
        db.updateOrInsertHighlights(ariBc, IntArrayList().apply { add(2) }, 0x222222)

        val selected = IntArrayList().apply { add(1); add(2) }
        assertEquals(-1, db.getHighlightColorRgb(ariBc, selected))
    }

    @Test
    fun `getHighlightColorRgb for multiple verses returns -1 when none of the selected verses are highlighted`() {
        val ariBc = Ari.encode(0, 1, 0)
        val verses = IntArrayList().apply { add(1); add(2) }
        assertEquals(-1, db.getHighlightColorRgb(ariBc, verses))
    }

    // endregion

    // region Attribute loading

    @Test
    fun `putAttributes fills the bookmark and note count arrays indexed by verse-1`() {
        val ariBc = Ari.encode(0, 1, 0)
        val now = Date(1_700_000_000_000L)
        db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "b1", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.bookmark, "b2", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 3), Marker.Kind.note, "n1", 1, now, now)

        val bookmarkCounts = IntArray(31)
        val noteCounts = IntArray(31)
        val highlightColors = arrayOfNulls<Highlights.Info>(31)
        db.putAttributes(ariBc, bookmarkCounts, noteCounts, highlightColors)

        assertEquals(2, bookmarkCounts[0]) // verse 1 -> index 0
        assertEquals(0, bookmarkCounts[1])
        assertEquals(0, bookmarkCounts[2])
        assertEquals(1, noteCounts[2]) // verse 3 -> index 2
        assertEquals(0, noteCounts[0])
    }

    @Test
    fun `putAttributes spreads a multi-verse highlight across verseCount entries of the color array`() {
        val ariBc = Ari.encode(0, 1, 0)
        val now = Date(1_700_000_000_000L)
        // a single highlight marker that spans 3 verses, starting at verse 2
        db.insertMarker(
            Ari.encode(0, 1, 2),
            Marker.Kind.highlight,
            Highlights.encode(0xff0000),
            3,
            now,
            now,
        )

        val bookmarkCounts = IntArray(31)
        val noteCounts = IntArray(31)
        val highlightColors = arrayOfNulls<Highlights.Info>(31)
        db.putAttributes(ariBc, bookmarkCounts, noteCounts, highlightColors)

        assertNull(highlightColors[0]) // verse 1 not highlighted
        assertNotNull(highlightColors[1]) // verse 2
        assertEquals(0xff0000, highlightColors[1]!!.colorRgb)
        assertNotNull(highlightColors[2]) // verse 3
        assertEquals(0xff0000, highlightColors[2]!!.colorRgb)
        assertNotNull(highlightColors[3]) // verse 4
        assertEquals(0xff0000, highlightColors[3]!!.colorRgb)
        assertNull(highlightColors[4]) // verse 5 not highlighted
    }

    @Test
    fun `putAttributes lets the latest highlight overwrite earlier ones on the same verse`() {
        val ariBc = Ari.encode(0, 1, 0)
        val t1 = Date(1_700_000_000_000L)
        val t2 = Date(1_700_000_100_000L)
        db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.highlight, Highlights.encode(0x111111), 1, t1, t1)
        db.insertMarker(Ari.encode(0, 1, 1), Marker.Kind.highlight, Highlights.encode(0x222222), 1, t2, t2)

        val bookmarkCounts = IntArray(31)
        val noteCounts = IntArray(31)
        val highlightColors = arrayOfNulls<Highlights.Info>(31)
        db.putAttributes(ariBc, bookmarkCounts, noteCounts, highlightColors)

        // putAttributes iterates ordered by modifyTime ascending, so the later
        // (larger modifyTime) overwrites the earlier entry in the color array.
        assertEquals(0x222222, highlightColors[0]!!.colorRgb)
    }

    @Test
    fun `putAttributes ignores markers that live outside the requested book-chapter`() {
        val ariBcTarget = Ari.encode(0, 1, 0)
        val now = Date(1_700_000_000_000L)
        db.insertMarker(Ari.encode(0, 2, 1), Marker.Kind.bookmark, "other", 1, now, now)

        val bookmarkCounts = IntArray(31)
        val noteCounts = IntArray(31)
        val highlightColors = arrayOfNulls<Highlights.Info>(31)
        db.putAttributes(ariBcTarget, bookmarkCounts, noteCounts, highlightColors)

        assertEquals(0, bookmarkCounts.sum())
        assertEquals(0, noteCounts.sum())
        assertTrue(highlightColors.all { it == null })
    }

    @Test
    fun `putAttributes includes a marker on verse 255 (inclusive upper bound)`() {
        // Regression guard for the same boundary bug fixed in
        // countMarkersForBookChapter — putAttributes used to drop verse 255.
        val ariBc = Ari.encode(0, 1, 0)
        val now = Date(1_700_000_000_000L)
        db.insertMarker(Ari.encode(0, 1, 254), Marker.Kind.bookmark, "v254", 1, now, now)
        db.insertMarker(Ari.encode(0, 1, 255), Marker.Kind.bookmark, "v255", 1, now, now)

        val bookmarkCounts = IntArray(256)
        val noteCounts = IntArray(256)
        val highlightColors = arrayOfNulls<Highlights.Info>(256)
        db.putAttributes(ariBc, bookmarkCounts, noteCounts, highlightColors)

        assertEquals(1, bookmarkCounts[253]) // verse 254 -> index 253
        assertEquals(1, bookmarkCounts[254]) // verse 255 -> index 254
    }

    // endregion

    // region Marker_Label helpers

    @Test
    fun `insertOrUpdateMarker_Label assigns a positive _id and is retrievable by gid`() {
        val ml = Marker_Label.createNewMarker_Label("marker-gid", "label-gid")
        db.insertOrUpdateMarker_Label(ml)

        assertTrue(ml._id > 0)
        val loaded = db.getMarker_LabelByGid(ml.gid)
        assertNotNull(loaded)
        assertEquals("marker-gid", loaded!!.marker_gid)
        assertEquals("label-gid", loaded.label_gid)
    }

    // endregion
}
