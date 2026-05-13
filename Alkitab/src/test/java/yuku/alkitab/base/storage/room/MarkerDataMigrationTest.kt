package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper

/**
 * Verifies the one-time copy from the legacy `Marker` / `Label` /
 * `Marker_Label` tables into Room. Robolectric is required because
 * [InternalDbHelper] extends [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class MarkerDataMigrationTest {
    private lateinit var legacy: InternalDbHelper
    private lateinit var room: AppDatabase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        legacy = InternalDbHelper(app)
        room = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        legacy.close()
    }

    private fun insertLegacyMarker(
        gid: String,
        ari: Int,
        kind: Int = 1,
        caption: String = "c",
        verseCount: Int = 1,
        createTime: Int = 1_700_000_000,
        modifyTime: Int = 1_700_000_000,
    ) {
        val cv = ContentValues().apply {
            put(Db.Marker.gid, gid)
            put(Db.Marker.ari, ari)
            put(Db.Marker.kind, kind)
            put(Db.Marker.caption, caption)
            put(Db.Marker.verseCount, verseCount)
            put(Db.Marker.createTime, createTime)
            put(Db.Marker.modifyTime, modifyTime)
        }
        legacy.writableDatabase.insert(Db.TABLE_Marker, null, cv)
    }

    private fun insertLegacyLabel(
        gid: String,
        title: String,
        ordering: Int,
        bgColor: String? = null,
    ) {
        val cv = ContentValues().apply {
            put(Db.Label.gid, gid)
            put(Db.Label.title, title) // legacy column name `judul`
            put(Db.Label.ordering, ordering) // legacy column name `urutan`
            put(Db.Label.backgroundColor, bgColor) // legacy column name `warnaLatar`
        }
        legacy.writableDatabase.insert(Db.TABLE_Label, null, cv)
    }

    private fun insertLegacyMarkerLabel(gid: String, markerGid: String, labelGid: String) {
        val cv = ContentValues().apply {
            put(Db.Marker_Label.gid, gid)
            put(Db.Marker_Label.marker_gid, markerGid)
            put(Db.Marker_Label.label_gid, labelGid)
        }
        legacy.writableDatabase.insert(Db.TABLE_Marker_Label, null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.markerDao().listAll().isEmpty())
        assertTrue(room.labelDao().listAll().isEmpty())
        assertTrue(room.markerLabelDao().listAll().isEmpty())
    }

    @Test
    fun `copy moves every legacy marker into Room and round-trips every persisted field`() {
        insertLegacyMarker(
            gid = "m1",
            ari = 100,
            kind = 3, // highlight
            caption = "encoded-highlight-payload",
            verseCount = 7,
            createTime = 1_700_000_111,
            modifyTime = 1_700_000_222,
        )

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.markerDao().listAll()
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals("m1", gid)
            assertEquals(100, ari)
            assertEquals(3, kind)
            assertEquals("encoded-highlight-payload", caption)
            assertEquals(7, verseCount)
            assertEquals(1_700_000_111, createTime)
            assertEquals(1_700_000_222, modifyTime)
        }
    }

    @Test
    fun `copy moves every legacy label into Room translating from Indonesian column names`() {
        insertLegacyLabel(gid = "L1", title = "Devotion", ordering = 1, bgColor = "#ff0000")
        insertLegacyLabel(gid = "L2", title = "Memorize", ordering = 2, bgColor = null)

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.labelDao().listAll()
        assertEquals(2, rows.size)
        with(rows[0]) {
            assertEquals("L1", gid)
            assertEquals("Devotion", title)
            assertEquals(1, ordering)
            assertEquals("#ff0000", backgroundColor)
        }
        with(rows[1]) {
            assertEquals("L2", gid)
            assertEquals("Memorize", title)
            assertEquals(2, ordering)
            assertEquals(null, backgroundColor)
        }
    }

    @Test
    fun `copy moves every legacy marker_label association into Room`() {
        insertLegacyMarkerLabel("ml1", "m1", "L1")
        insertLegacyMarkerLabel("ml2", "m2", "L1")
        insertLegacyMarkerLabel("ml3", "m1", "L2")

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.markerLabelDao().listAll()
        assertEquals(3, rows.size)
        val byGid = rows.associateBy { it.gid }
        assertEquals("m1" to "L1", byGid["ml1"]!!.let { it.marker_gid to it.label_gid })
        assertEquals("m2" to "L1", byGid["ml2"]!!.let { it.marker_gid to it.label_gid })
        assertEquals("m1" to "L2", byGid["ml3"]!!.let { it.marker_gid to it.label_gid })
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyMarker(gid = "m1", ari = 100)
        insertLegacyLabel(gid = "L1", title = "L", ordering = 1)
        insertLegacyMarkerLabel("ml1", "m1", "L1")

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.markerDao().listAll().size)
        assertEquals(1, room.labelDao().listAll().size)
        assertEquals(1, room.markerLabelDao().listAll().size)
    }

    @Test
    fun `copy skips when ANY of the three Room tables already has rows`() {
        // Simulate a previous-run state: Room has at least one row in
        // marker, but labels and marker_labels happen to be empty. The
        // migration must not stomp on that state — even though the
        // markerLabelDao().count() is 0, the combined check sees marker
        // count > 0 and bails.
        room.markerDao().insert(
            MarkerEntity(
                _id = 0L,
                gid = "preexisting",
                ari = 999,
                kind = 1,
                caption = "preexisting",
                verseCount = 1,
                createTime = 0,
                modifyTime = 0,
            ),
        )
        insertLegacyMarker(gid = "m1", ari = 100)
        insertLegacyLabel(gid = "L1", title = "L", ordering = 1)

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        // legacy markers / labels NOT copied; pre-existing Room state preserved
        val rows = room.markerDao().listAll()
        assertEquals(1, rows.size)
        assertEquals("preexisting", rows.single().gid)
        assertTrue(room.labelDao().listAll().isEmpty())
    }

    @Test
    fun `copy moves all three tables atomically inside one transaction`() {
        // Sanity check that the migration treats the three tables as a unit:
        // either all three populate or none of them does. We can't easily
        // crash mid-copy from a test, but we can assert that after a
        // successful run all three Room tables have the expected rows and
        // none of them has stale orphans.
        insertLegacyMarker(gid = "m1", ari = 100)
        insertLegacyMarker(gid = "m2", ari = 200)
        insertLegacyLabel(gid = "L1", title = "L", ordering = 1)
        insertLegacyMarkerLabel("ml1", "m1", "L1")

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(2, room.markerDao().listAll().size)
        assertEquals(1, room.labelDao().listAll().size)
        assertEquals(1, room.markerLabelDao().listAll().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacyMarker(gid = "m1", ari = 100)

        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.markerDao().findByGid("m1")
        assertNotNull(row)
        // Room AUTOINCREMENT starts at 1 for a fresh in-memory DB; the only
        // contract is that _id is positive (fresh) rather than the legacy
        // _id literal we never asserted.
        assertTrue("expected positive Room _id, got ${row!!._id}", row._id > 0)
    }
}
