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
 * Tests for the Room-generated [LabelRoomDao]. Verifies the pair-wise
 * `reorderById` shifts the same way the legacy `InternalDb.reorderLabels`
 * raw SQL did, and exercises the cross-join `listByMarkerGid` query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LabelRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LabelRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.labelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        gid: String,
        ordering: Int,
        title: String = "t",
        bgColor: String? = null,
    ) = LabelEntity(
        _id = 0L,
        gid = gid,
        title = title,
        ordering = ordering,
        backgroundColor = bgColor,
    )

    @Test
    fun `listAll returns rows sorted by ordering ascending`() {
        dao.insert(entity("g3", 3))
        dao.insert(entity("g1", 1))
        dao.insert(entity("g2", 2))

        assertEquals(listOf("g1", "g2", "g3"), dao.listAll().map { it.gid })
    }

    @Test
    fun `getMaxOrdering returns zero on an empty table and the largest ordering otherwise`() {
        assertEquals(0, dao.getMaxOrdering())
        dao.insert(entity("g1", 1))
        dao.insert(entity("g2", 5))
        dao.insert(entity("g3", 3))
        assertEquals(5, dao.getMaxOrdering())
    }

    @Test
    fun `findByGid returns null for a gid that is not in the table`() {
        dao.insert(entity("g1", 1))
        assertNull(dao.findByGid("ghost"))
    }

    @Test
    fun `deleteByGid returns the number of rows deleted`() {
        dao.insert(entity("g1", 1))
        dao.insert(entity("g2", 2))

        assertEquals(1, dao.deleteByGid("g1"))
        assertEquals(0, dao.deleteByGid("ghost"))
        assertEquals(listOf("g2"), dao.listAll().map { it.gid })
    }

    @Test
    fun `listByMarkerGid joins against marker_label and orders by label ordering ascending`() {
        val l1Id = dao.insert(entity("L1", 1, title = "A"))
        val l2Id = dao.insert(entity("L2", 2, title = "B"))
        val l3Id = dao.insert(entity("L3", 3, title = "C"))

        db.markerLabelDao().insert(
            MarkerLabelEntity(_id = 0L, gid = "ml1", marker_gid = "M1", label_gid = "L2"),
        )
        db.markerLabelDao().insert(
            MarkerLabelEntity(_id = 0L, gid = "ml2", marker_gid = "M1", label_gid = "L1"),
        )
        // unrelated association — should be excluded from M1's results
        db.markerLabelDao().insert(
            MarkerLabelEntity(_id = 0L, gid = "ml3", marker_gid = "M2", label_gid = "L3"),
        )

        val rows = dao.listByMarkerGid("M1")
        assertEquals(listOf(l1Id, l2Id), rows.map { it._id })
        // sanity: l3 not in the result
        assertTrue(rows.none { it._id == l3Id })
    }

    @Test
    fun `reorderById move-up shifts a contiguous block up by one and slots the target into place`() {
        val ids = (1..5).map { dao.insert(entity("L$it", it, title = "L$it")) }

        // Move L4 (ordering 4) up to 2. Expectation: L1=1, L4=2, L2=3, L3=4, L5=5.
        dao.reorderById(ids[3], fromOrdering = 4, toOrdering = 2)

        val map = dao.listAll().associate { it.gid to it.ordering }
        assertEquals(1, map["L1"])
        assertEquals(2, map["L4"])
        assertEquals(3, map["L2"])
        assertEquals(4, map["L3"])
        assertEquals(5, map["L5"])
    }

    @Test
    fun `reorderById move-down shifts a contiguous block down by one and slots the target into place`() {
        val ids = (1..5).map { dao.insert(entity("L$it", it, title = "L$it")) }

        // Move L2 (ordering 2) down to 4. Expectation: L1=1, L3=2, L4=3, L2=4, L5=5.
        dao.reorderById(ids[1], fromOrdering = 2, toOrdering = 4)

        val map = dao.listAll().associate { it.gid to it.ordering }
        assertEquals(1, map["L1"])
        assertEquals(2, map["L3"])
        assertEquals(3, map["L4"])
        assertEquals(4, map["L2"])
        assertEquals(5, map["L5"])
    }

    @Test
    fun `reorderById with from equal to to is a no-op`() {
        dao.insert(entity("L1", 1))
        dao.insert(entity("L2", 2))

        dao.reorderById(dao.findByGid("L1")!!._id, fromOrdering = 1, toOrdering = 1)

        val map = dao.listAll().associate { it.gid to it.ordering }
        assertEquals(1, map["L1"])
        assertEquals(2, map["L2"])
    }

    @Test
    fun `insertAll persists every row in a single call`() {
        dao.insertAll(
            listOf(
                entity("L1", 1),
                entity("L2", 2),
                entity("L3", 3),
            ),
        )
        assertEquals(3, dao.count())
    }
}
