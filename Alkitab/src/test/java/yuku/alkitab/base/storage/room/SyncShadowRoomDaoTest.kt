package yuku.alkitab.base.storage.room

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
 * Tests for the Room-generated [SyncShadowRoomDao]. Verifies the two
 * sync-subsystem tables — `sync_shadow` and `sync_log` — and the
 * transactional `insertOrUpdateShadow` upsert that mirrors the legacy
 * facade's count-then-update-or-insert semantics under
 * `beginTransactionNonExclusive`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncShadowRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SyncShadowRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncShadowDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun shadow(syncSetName: String, revno: Int = 1, data: ByteArray? = byteArrayOf(1, 2, 3)) =
        SyncShadowEntity(_id = 0L, syncSetName = syncSetName, revno = revno, data = data)

    private fun log(
        createTime: Int,
        kind: Int = 100,
        syncSetName: String? = "mabel",
        params: String? = """{"k":"v"}""",
    ) = SyncLogEntity(
        _id = 0L,
        createTime = createTime,
        kind = kind,
        syncSetName = syncSetName,
        params = params,
    )

    @Test
    fun `insertShadow assigns a positive _id and round-trips every column`() {
        val id = dao.insertShadow(shadow("mabel", revno = 7, data = byteArrayOf(10, 20, 30)))
        assertTrue(id > 0)
        val loaded = dao.listAllShadowsForTest().single()
        assertEquals("mabel", loaded.syncSetName)
        assertEquals(7, loaded.revno)
        assertArrayEquals(byteArrayOf(10, 20, 30), loaded.data)
    }

    @Test
    fun `findShadowBySyncSetName returns null when no row matches`() {
        assertNull(dao.findShadowBySyncSetName("missing"))
    }

    @Test
    fun `findShadowBySyncSetName returns the row when present`() {
        dao.insertShadow(shadow("pins", revno = 3))
        val row = dao.findShadowBySyncSetName("pins")
        assertNotNull(row)
        assertEquals(3, row!!.revno)
    }

    @Test
    fun `findRevnoBySyncSetName returns null for missing and the revno for present`() {
        assertNull(dao.findRevnoBySyncSetName("missing"))
        dao.insertShadow(shadow("rp", revno = 42))
        assertEquals(42, dao.findRevnoBySyncSetName("rp"))
    }

    @Test
    fun `findShadowSummaryBySyncSetName returns _id, revno, and byte length`() {
        val id = dao.insertShadow(shadow("history", revno = 9, data = byteArrayOf(1, 2, 3, 4, 5)))
        val s = dao.findShadowSummaryBySyncSetName("history")
        assertNotNull(s)
        assertEquals(id, s!!._id)
        assertEquals(9, s.revno)
        assertEquals(5, s.dataLen)
    }

    @Test
    fun `findShadowSummaryBySyncSetName reports zero dataLen for a null data blob`() {
        dao.insertShadow(shadow("mabel", revno = 1, data = null))
        val s = dao.findShadowSummaryBySyncSetName("mabel")
        assertNotNull(s)
        assertEquals(0, s!!.dataLen)
    }

    @Test
    fun `insertOrUpdateShadow inserts when absent and updates in place when present`() {
        dao.insertOrUpdateShadow("mabel", revno = 1, data = byteArrayOf(1))
        assertEquals(1, dao.listAllShadowsForTest().size)
        assertEquals(1, dao.findRevnoBySyncSetName("mabel"))

        dao.insertOrUpdateShadow("mabel", revno = 2, data = byteArrayOf(9, 9))
        val rows = dao.listAllShadowsForTest()
        assertEquals("no duplicate row should be created on update", 1, rows.size)
        assertEquals(2, rows.single().revno)
        assertArrayEquals(byteArrayOf(9, 9), rows.single().data)
    }

    @Test
    fun `insertOrUpdateShadow does not duplicate rows when called twice with the same syncSetName`() {
        dao.insertOrUpdateShadow("pins", revno = 5, data = null)
        dao.insertOrUpdateShadow("pins", revno = 5, data = null)
        dao.insertOrUpdateShadow("pins", revno = 5, data = null)
        assertEquals(1, dao.listAllShadowsForTest().size)
    }

    @Test
    fun `deleteShadowBySyncSetName removes only the matching row and returns the affected count`() {
        dao.insertShadow(shadow("mabel"))
        dao.insertShadow(shadow("pins"))
        val deleted = dao.deleteShadowBySyncSetName("mabel")
        assertEquals(1, deleted)
        val remaining = dao.listAllShadowsForTest()
        assertEquals(1, remaining.size)
        assertEquals("pins", remaining.single().syncSetName)
    }

    @Test
    fun `data column round-trips null`() {
        dao.insertShadow(shadow("mabel", data = null))
        assertNull(dao.listAllShadowsForTest().single().data)
    }

    @Test
    fun `data column round-trips an empty ByteArray`() {
        dao.insertShadow(shadow("mabel", data = byteArrayOf()))
        assertArrayEquals(byteArrayOf(), dao.listAllShadowsForTest().single().data)
    }

    @Test
    fun `insertLog appends a row and assigns _id`() {
        val id = dao.insertLog(log(createTime = 100))
        assertTrue(id > 0)
        assertEquals(1, dao.countAllLogs())
    }

    @Test
    fun `listLatestLogs orders by createTime DESC and respects maxrows`() {
        dao.insertLog(log(createTime = 100))
        dao.insertLog(log(createTime = 300))
        dao.insertLog(log(createTime = 200))

        val rows = dao.listLatestLogs(maxrows = 2)
        assertEquals(listOf(300, 200), rows.map { it.createTime })
    }

    @Test
    fun `listLatestLogs returns empty when no rows`() {
        assertTrue(dao.listLatestLogs(maxrows = 10).isEmpty())
    }

    @Test
    fun `SyncLog row round-trips null syncSetName and null params`() {
        dao.insertLog(log(createTime = 50, syncSetName = null, params = null))
        val row = dao.listLatestLogs(maxrows = 1).single()
        assertNull(row.syncSetName)
        assertNull(row.params)
    }
}
