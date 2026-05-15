package yuku.alkitab.base.storage

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.sync.SyncRecorder

/**
 * Robolectric tests for the [SyncShadowDao] facade. The facade routes
 * through Room (see `Alkitab/src/main/java/yuku/alkitab/base/storage/SyncShadowDao.kt`);
 * the test installs an in-memory [AppDatabase] in [setUp] via
 * [AppDatabase.setForTesting].
 *
 * Includes a >2 MB blob round-trip case to exercise the chunked `substr()`
 * read path — that path is the reason this facade is the only sync-table
 * code path that does not route every operation through generated Room
 * code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncShadowDaoTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var roomDb: AppDatabase
    private lateinit var dao: SyncShadowDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        helper = InternalDbHelper(app)
        roomDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setForTesting(roomDb)
        dao = SyncShadowDao(helper)
    }

    @After
    fun tearDown() {
        AppDatabase.setForTesting(null)
        helper.close()
    }

    private fun ss(syncSetName: String, revno: Int = 1, data: ByteArray = byteArrayOf()) =
        SyncShadow().apply {
            this.syncSetName = syncSetName
            this.revno = revno
            this.data = data
        }

    @Test
    fun `getBySyncSetName returns null when no row matches`() {
        assertNull(dao.getBySyncSetName("missing"))
    }

    @Test
    fun `insertOrUpdateBySyncSetName inserts a new row and getBySyncSetName round-trips every field`() {
        dao.insertOrUpdateBySyncSetName(ss("mabel", revno = 5, data = byteArrayOf(1, 2, 3)))

        val loaded = dao.getBySyncSetName("mabel")!!
        assertEquals("mabel", loaded.syncSetName)
        assertEquals(5, loaded.revno)
        assertArrayEquals(byteArrayOf(1, 2, 3), loaded.data)
    }

    @Test
    fun `insertOrUpdateBySyncSetName updates an existing row in place (no duplicates)`() {
        dao.insertOrUpdateBySyncSetName(ss("mabel", revno = 1, data = byteArrayOf(1)))
        dao.insertOrUpdateBySyncSetName(ss("mabel", revno = 2, data = byteArrayOf(9, 9)))

        val loaded = dao.getBySyncSetName("mabel")!!
        assertEquals(2, loaded.revno)
        assertArrayEquals(byteArrayOf(9, 9), loaded.data)
    }

    @Test
    fun `getRevnoBySyncSetName returns 0 when missing and the revno when present`() {
        assertEquals(0, dao.getRevnoBySyncSetName("missing"))
        dao.insertOrUpdateBySyncSetName(ss("rp", revno = 42))
        assertEquals(42, dao.getRevnoBySyncSetName("rp"))
    }

    @Test
    fun `deleteBySyncSetName removes the row`() {
        dao.insertOrUpdateBySyncSetName(ss("mabel"))
        dao.insertOrUpdateBySyncSetName(ss("pins"))

        assertEquals(1, dao.deleteBySyncSetName("mabel"))
        assertNull(dao.getBySyncSetName("mabel"))
        assertEquals(0, dao.getRevnoBySyncSetName("mabel"))
        // unrelated set survives
        assertEquals(1, dao.getRevnoBySyncSetName("pins"))
    }

    @Test
    fun `getBySyncSetName can round-trip a data blob larger than the 2 MB CursorWindow limit`() {
        // The default Android CursorWindow holds ~2 MB. SyncShadow data
        // routinely exceeds that for full Mabel snapshots; the facade's
        // chunked substr() read is the workaround. 3.5 MB is comfortably
        // above the cap.
        val big = ByteArray(3_500_000) { (it % 251).toByte() }
        dao.insertOrUpdateBySyncSetName(ss("mabel", revno = 7, data = big))

        val loaded = dao.getBySyncSetName("mabel")!!
        assertEquals(7, loaded.revno)
        assertArrayEquals(big, loaded.data)
    }

    @Test
    fun `insertLog and listLatest round-trip the EventKind code, syncSetName, and params (parsed back into a Map)`() {
        dao.insertLog(100, SyncRecorder.EventKind.apply_result, "mabel", """{"k1":"v1","k2":42}""")

        val log = dao.listLatest(maxrows = 10).single()
        assertEquals(SyncRecorder.EventKind.apply_result.code, log.kind_code)
        assertEquals("mabel", log.syncSetName)
        assertEquals("v1", log.params["k1"])
        // JSON numbers come back as Double from Gson when typed as Map<String, Any>.
        assertEquals(42.0, log.params["k2"])
    }

    @Test
    fun `listLatest returns rows newest-first respecting maxrows`() {
        dao.insertLog(100, SyncRecorder.EventKind.sync_forced, null, null)
        dao.insertLog(300, SyncRecorder.EventKind.apply_result, null, null)
        dao.insertLog(200, SyncRecorder.EventKind.fcm_send_attempt, null, null)

        val logs = dao.listLatest(maxrows = 2)
        assertEquals(listOf(300L * 1000, 200L * 1000), logs.map { it.createTime.time })
        // params is null so the parsed Map is null
        assertTrue(logs.all { it.params == null })
    }
}
