package yuku.alkitab.base.storage.room

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the Room-generated [MarkerRoomDao]. Complements
 * [yuku.alkitab.base.storage.InternalDbTest] — that one exercises the
 * `Marker`-model facade end-to-end; this one verifies the Room-level
 * primitives (range queries, raw-query escape hatch, gid lookups) in
 * isolation against [MarkerEntity].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class MarkerRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MarkerRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.markerDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        gid: String,
        ari: Int,
        kind: Int = 1, // bookmark
        caption: String = "c",
        verseCount: Int = 1,
        createTime: Int = 0,
        modifyTime: Int = 0,
    ) = MarkerEntity(
        _id = 0L,
        gid = gid,
        ari = ari,
        kind = kind,
        caption = caption,
        verseCount = verseCount,
        createTime = createTime,
        modifyTime = modifyTime,
    )

    @Test
    fun `listAll on an empty database returns an empty list`() {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `insert assigns a positive _id and the row can be read back via findById`() {
        val id = dao.insert(entity("g1", 100))
        assertTrue("expected positive _id, got $id", id > 0)
        val loaded = dao.findById(id)
        assertNotNull(loaded)
        assertEquals("g1", loaded!!.gid)
        assertEquals(100, loaded.ari)
    }

    @Test
    fun `findByGid returns null for a gid that is not in the table`() {
        dao.insert(entity("g1", 100))
        assertNull(dao.findByGid("ghost"))
    }

    @Test
    fun `count reports the number of inserted rows`() {
        assertEquals(0, dao.count())
        dao.insert(entity("g1", 100))
        dao.insert(entity("g2", 101))
        assertEquals(2, dao.count())
    }

    @Test
    fun `listForAriKindOrderedByModifyTimeDesc returns rows in descending modifyTime order`() {
        dao.insert(entity("g1", 100, modifyTime = 100))
        dao.insert(entity("g2", 100, modifyTime = 300))
        dao.insert(entity("g3", 100, modifyTime = 200))
        // a marker with a different ari should be excluded
        dao.insert(entity("g4", 101, modifyTime = 999))
        // a marker with the same ari but a different kind should be excluded
        dao.insert(entity("g5", 100, kind = 2, modifyTime = 999))

        val rows = dao.listForAriKindOrderedByModifyTimeDesc(100, 1)
        assertEquals(listOf("g2", "g3", "g1"), rows.map { it.gid })
    }

    @Test
    fun `countInAriRangeInclusive includes both range boundaries`() {
        // Regression guard for the verse-255 inclusive bound — production
        // legacy SQL is `ari >= ariMin AND ari <= ariMax`, so a marker on
        // verse 255 (ari == ariMax) must be counted.
        dao.insert(entity("g1", 100))
        dao.insert(entity("g2", 105))
        dao.insert(entity("g3", 110))

        assertEquals(3, dao.countInAriRangeInclusive(100, 110))
        assertEquals(2, dao.countInAriRangeInclusive(101, 110))
        assertEquals(2, dao.countInAriRangeInclusive(100, 109))
        assertEquals(0, dao.countInAriRangeInclusive(200, 300))
    }

    @Test
    fun `listInAriRangeOrderedByModifyTimeAsc orders rows by modifyTime ascending and uses an inclusive range`() {
        dao.insert(entity("g1", 100, modifyTime = 300))
        dao.insert(entity("g2", 100, modifyTime = 100))
        dao.insert(entity("g3", 100, modifyTime = 200))
        // out-of-range, excluded
        dao.insert(entity("g4", 1000, modifyTime = 999))

        val rows = dao.listInAriRangeOrderedByModifyTimeAsc(100, 200)
        assertEquals(listOf("g2", "g3", "g1"), rows.map { it.gid })
    }

    @Test
    fun `listForAriRangeExclusiveMinAndKind excludes the lower boundary and includes the upper`() {
        // Matches the legacy SQL `ari > ariMin AND ari <= ariMax` used by
        // the multi-verse getHighlightColorRgb overload.
        dao.insert(entity("g1", 100, kind = 3)) // highlight at boundary -> excluded
        dao.insert(entity("g2", 101, kind = 3))
        dao.insert(entity("g3", 200, kind = 3))
        dao.insert(entity("g4", 200, kind = 1)) // wrong kind -> excluded

        val rows = dao.listForAriRangeExclusiveMinAndKind(100, 200, 3)
        assertEquals(setOf("g2", "g3"), rows.map { it.gid }.toSet())
    }

    @Test
    fun `update changes the persisted fields and preserves the _id`() {
        val id = dao.insert(entity("g1", 100, caption = "before"))
        val loaded = dao.findById(id)!!
        val updated = loaded.copy(caption = "after", modifyTime = 999)

        assertEquals(1, dao.update(updated))

        val reloaded = dao.findById(id)!!
        assertEquals("after", reloaded.caption)
        assertEquals(999, reloaded.modifyTime)
        assertEquals(id, reloaded._id)
    }

    @Test
    fun `deleteByGid returns the number of rows deleted and removes them`() {
        dao.insert(entity("g1", 100))
        dao.insert(entity("g2", 101))

        assertEquals(1, dao.deleteByGid("g1"))
        assertEquals(0, dao.deleteByGid("ghost"))
        assertEquals(listOf("g2"), dao.listAll().map { it.gid })
    }

    @Test
    fun `insertAll persists every row in a single call`() {
        dao.insertAll(
            listOf(
                entity("g1", 100),
                entity("g2", 101),
                entity("g3", 102),
            ),
        )
        assertEquals(3, dao.count())
    }

    @Test
    fun `listMarkersRaw round-trips an arbitrary SELECT with bound parameters`() {
        // Sanity check that the @RawQuery escape hatch used by
        // InternalDb.listMarkers actually returns MarkerEntity rows. The
        // facade builds the SQL — this test just verifies the bridge.
        dao.insert(entity("g1", 100, kind = 1))
        dao.insert(entity("g2", 200, kind = 1))
        dao.insert(entity("g3", 100, kind = 2))

        val rows = dao.listMarkersRaw(
            SimpleSQLiteQuery(
                "SELECT * FROM marker WHERE kind = ? ORDER BY ari ASC",
                arrayOf<Any?>(1),
            ),
        )
        assertEquals(listOf("g1", "g2"), rows.map { it.gid })
    }

    @Test
    fun `listMarkersRaw can join against marker_label without breaking the row mapping`() {
        // Mirrors the label-filter branch in InternalDb.listMarkers.
        dao.insert(entity("m1", 100, kind = 1))
        dao.insert(entity("m2", 200, kind = 1))
        db.markerLabelDao().insert(
            MarkerLabelEntity(_id = 0L, gid = "ml1", marker_gid = "m1", label_gid = "L1"),
        )

        val rows = dao.listMarkersRaw(
            SimpleSQLiteQuery(
                "SELECT marker.* FROM marker, marker_label" +
                    " WHERE marker.kind = ? AND marker.gid = marker_label.marker_gid" +
                    " AND marker_label.label_gid = ?" +
                    " ORDER BY marker.ari ASC",
                arrayOf<Any?>(1, "L1"),
            ),
        )
        assertEquals(listOf("m1"), rows.map { it.gid })
        assertFalse(rows.any { it.gid == "m2" })
    }
}
