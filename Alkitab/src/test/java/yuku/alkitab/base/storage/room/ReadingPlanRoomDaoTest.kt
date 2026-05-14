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
import yuku.alkitab.util.IntArrayList

/**
 * Tests for the Room-generated [ReadingPlanRoomDao]. Verifies the
 * metadata-vs-blob projection split, the gid-keyed progress lookups, and the
 * transactional `replaceProgress` / `insertOrUpdateMultipleProgresses` paths
 * that mirror the legacy [yuku.alkitab.base.storage.ReadingPlanDao]
 * `beginTransactionNonExclusive` semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReadingPlanRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReadingPlanRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.readingPlanDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newPlan(
        name: String,
        title: String = "t",
        data: ByteArray = byteArrayOf(),
    ) = ReadingPlanEntity(
        _id = 0L,
        version = 1,
        name = name,
        title = title,
        description = "desc",
        duration = 365,
        startTime = 1_700_000_000_000L,
        data = data,
    )

    private fun progress(gid: String, readingCode: Int, checkTime: Long = 1_000L) =
        ReadingPlanProgressEntity(
            _id = 0L,
            reading_plan_progress_gid = gid,
            reading_code = readingCode,
            checkTime = checkTime,
        )

    private fun codes(vararg c: Int): IntArrayList = IntArrayList().also { list ->
        c.forEach { list.add(it) }
    }

    @Test
    fun `insert assigns a positive _id and round-trips every column`() {
        val id = dao.insert(newPlan("rp-a", data = byteArrayOf(1, 2, 3)))
        assertTrue(id > 0)
        val loaded = dao.listAllForTest().single()
        assertEquals("rp-a", loaded.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), loaded.data)
        assertEquals(365, loaded.duration)
        assertEquals(1_700_000_000_000L, loaded.startTime)
    }

    @Test
    fun `listAllMeta projects every column except the binary data blob`() {
        dao.insert(newPlan("rp-a", data = byteArrayOf(99)))
        dao.insert(newPlan("rp-b", data = byteArrayOf()))
        val meta = dao.listAllMeta().sortedBy { it.name }
        assertEquals(listOf("rp-a", "rp-b"), meta.map { it.name })
        assertEquals(listOf(365, 365), meta.map { it.duration })
    }

    @Test
    fun `findNameAndDataById returns null when no row matches`() {
        assertNull(dao.findNameAndDataById(99))
    }

    @Test
    fun `findNameAndDataById returns the name plus binary blob for an existing plan`() {
        val id = dao.insert(newPlan("rp-c", data = byteArrayOf(0x0a, 0x0b)))
        val row = dao.findNameAndDataById(id)!!
        assertEquals("rp-c", row.name)
        assertArrayEquals(byteArrayOf(0x0a, 0x0b), row.data)
    }

    @Test
    fun `listNames returns one entry per plan and skips rows with a null name`() {
        dao.insert(newPlan("rp-a"))
        dao.insert(newPlan("rp-b"))
        // A row with a null `name` is unreachable through the facade but the
        // entity declares it nullable; make sure listNames isn't surprised.
        dao.insert(newPlan("placeholder").copy(name = null))
        assertEquals(listOf("rp-a", "rp-b"), dao.listNames())
    }

    @Test
    fun `deleteById removes the row and returns the affected count`() {
        val id = dao.insert(newPlan("rp-d"))
        assertEquals(1, dao.deleteById(id))
        assertTrue(dao.listAllForTest().isEmpty())
    }

    @Test
    fun `updateStartTime rewrites only the startTime column`() {
        val id = dao.insert(newPlan("rp-e"))
        assertEquals(1, dao.updateStartTime(id, 42L))
        val row = dao.listAllForTest().single()
        assertEquals(42L, row.startTime)
        assertEquals("rp-e", row.name)
    }

    @Test
    fun `insertOrReplaceProgress upserts on the (gid, readingCode) UNIQUE index`() {
        dao.insertOrReplaceProgress(progress("g1", 1, checkTime = 10L))
        dao.insertOrReplaceProgress(progress("g1", 1, checkTime = 20L))
        dao.insertOrReplaceProgress(progress("g1", 2, checkTime = 30L))

        val rows = dao.listAllProgressForTest()
            .filter { it.reading_plan_progress_gid == "g1" }
            .sortedBy { it.reading_code }
        assertEquals(2, rows.size)
        assertEquals(listOf(1, 2), rows.map { it.reading_code })
        // The (g1, 1) row got its checkTime refreshed by the upsert.
        assertEquals(20L, rows.first { it.reading_code == 1 }.checkTime)
    }

    @Test
    fun `deleteProgress removes only the matching (gid, readingCode) pair`() {
        dao.insertOrReplaceProgress(progress("g1", 1))
        dao.insertOrReplaceProgress(progress("g1", 2))

        assertEquals(1, dao.deleteProgress("g1", 1))
        assertEquals(listOf(2), dao.listReadingCodesForGid("g1"))
    }

    @Test
    fun `deleteAllProgressForGid clears every row matching gid and returns the affected count`() {
        dao.insertOrReplaceProgress(progress("g1", 1))
        dao.insertOrReplaceProgress(progress("g1", 2))
        dao.insertOrReplaceProgress(progress("g2", 1))

        val deleted = dao.deleteAllProgressForGid("g1")
        assertEquals(2, deleted)
        assertTrue(dao.listReadingCodesForGid("g1").isEmpty())
        assertEquals(listOf(1), dao.listReadingCodesForGid("g2"))
    }

    @Test
    fun `listAllProgressGidAndReadingCode returns every row in the progress table`() {
        dao.insertOrReplaceProgress(progress("g1", 1))
        dao.insertOrReplaceProgress(progress("g1", 2))
        dao.insertOrReplaceProgress(progress("g2", 7))

        val pairs = dao.listAllProgressGidAndReadingCode()
            .map { it.reading_plan_progress_gid to it.reading_code }
            .toSet()
        assertEquals(setOf("g1" to 1, "g1" to 2, "g2" to 7), pairs)
    }

    @Test
    fun `listReadingCodesForGid is ordered ascending and scoped to gid`() {
        dao.insertOrReplaceProgress(progress("g1", 3))
        dao.insertOrReplaceProgress(progress("g1", 1))
        dao.insertOrReplaceProgress(progress("g1", 2))
        dao.insertOrReplaceProgress(progress("g2", 99))
        assertEquals(listOf(1, 2, 3), dao.listReadingCodesForGid("g1"))
    }

    @Test
    fun `replaceProgress wipes every existing row for the gid before inserting the new codes`() {
        dao.insertOrReplaceProgress(progress("g1", 1, checkTime = 10L))
        dao.insertOrReplaceProgress(progress("g1", 2, checkTime = 20L))
        dao.insertOrReplaceProgress(progress("g2", 7, checkTime = 30L))

        dao.replaceProgress("g1", codes(5, 6), checkTime = 100L)

        assertEquals(listOf(5, 6), dao.listReadingCodesForGid("g1"))
        // unrelated gid is untouched
        assertEquals(listOf(7), dao.listReadingCodesForGid("g2"))
    }

    @Test
    fun `insertOrUpdateMultipleProgresses upserts a batch and refreshes existing checkTimes`() {
        dao.insertOrReplaceProgress(progress("g1", 1, checkTime = 10L))
        dao.insertOrReplaceProgress(progress("g1", 2, checkTime = 20L))

        // 2 has its checkTime refreshed; 3 is added; 1 is untouched.
        dao.insertOrUpdateMultipleProgresses("g1", codes(2, 3), checkTime = 100L)

        assertEquals(listOf(1, 2, 3), dao.listReadingCodesForGid("g1"))
        val row2 = dao.listAllProgressForTest()
            .single { it.reading_plan_progress_gid == "g1" && it.reading_code == 2 }
        assertEquals(100L, row2.checkTime)
    }

    @Test
    fun `null checkTime round-trips through insert`() {
        dao.insertOrReplaceProgress(
            ReadingPlanProgressEntity(
                _id = 0L,
                reading_plan_progress_gid = "g1",
                reading_code = 1,
                checkTime = null,
            ),
        )
        val row = dao.listAllProgressForTest().single()
        assertNotNull(row)
        assertNull(row.checkTime)
    }
}
