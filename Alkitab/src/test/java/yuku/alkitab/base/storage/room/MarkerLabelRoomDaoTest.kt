package yuku.alkitab.base.storage.room

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Tests for the Room-generated [MarkerLabelRoomDao]. Verifies the by-gid
 * delete cascades used by `InternalDb.deleteMarkerById` and
 * `InternalDb.deleteLabelAndMarker_LabelsByLabelId`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class MarkerLabelRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MarkerLabelRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.markerLabelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(gid: String, markerGid: String, labelGid: String) =
        MarkerLabelEntity(_id = 0L, gid = gid, marker_gid = markerGid, label_gid = labelGid)

    @Test
    fun `listAll on an empty database returns an empty list`() {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `insert assigns a positive _id and the row is retrievable by gid`() {
        val id = dao.insert(entity("ml1", "M1", "L1"))
        assertTrue(id > 0)
        val loaded = dao.findByGid("ml1")
        assertNotNull(loaded)
        assertEquals("M1", loaded!!.marker_gid)
        assertEquals("L1", loaded.label_gid)
    }

    @Test
    fun `findByGid returns null for a gid that is not in the table`() {
        dao.insert(entity("ml1", "M1", "L1"))
        assertNull(dao.findByGid("ghost"))
    }

    @Test
    fun `listByMarkerGid filters to a single marker`() {
        dao.insert(entity("ml1", "M1", "L1"))
        dao.insert(entity("ml2", "M1", "L2"))
        dao.insert(entity("ml3", "M2", "L1"))

        val rows = dao.listByMarkerGid("M1")
        assertEquals(setOf("ml1", "ml2"), rows.map { it.gid }.toSet())
    }

    @Test
    fun `countByLabelGid counts only rows that reference the label`() {
        dao.insert(entity("ml1", "M1", "L1"))
        dao.insert(entity("ml2", "M2", "L1"))
        dao.insert(entity("ml3", "M1", "L2"))

        assertEquals(2, dao.countByLabelGid("L1"))
        assertEquals(1, dao.countByLabelGid("L2"))
        assertEquals(0, dao.countByLabelGid("ghost"))
    }

    @Test
    fun `deleteByMarkerGid removes every association for the marker and returns the count`() {
        dao.insert(entity("ml1", "M1", "L1"))
        dao.insert(entity("ml2", "M1", "L2"))
        dao.insert(entity("ml3", "M2", "L1"))

        assertEquals(2, dao.deleteByMarkerGid("M1"))
        assertEquals(listOf("ml3"), dao.listAll().map { it.gid })
    }

    @Test
    fun `deleteByLabelGid removes every association for the label and returns the count`() {
        dao.insert(entity("ml1", "M1", "L1"))
        dao.insert(entity("ml2", "M2", "L1"))
        dao.insert(entity("ml3", "M1", "L2"))

        assertEquals(2, dao.deleteByLabelGid("L1"))
        assertEquals(listOf("ml3"), dao.listAll().map { it.gid })
    }

    @Test
    fun `deleteByGid returns zero when the gid does not exist`() {
        assertEquals(0, dao.deleteByGid("ghost"))
    }

    @Test
    fun `insertAll persists every row in a single call`() {
        dao.insertAll(
            listOf(
                entity("ml1", "M1", "L1"),
                entity("ml2", "M2", "L1"),
                entity("ml3", "M3", "L1"),
            ),
        )
        assertEquals(3, dao.count())
    }
}
