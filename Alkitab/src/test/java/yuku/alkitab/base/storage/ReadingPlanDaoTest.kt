package yuku.alkitab.base.storage

import android.app.Application
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
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReadingPlanDaoTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var dao: ReadingPlanDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        helper = InternalDbHelper(app)
        dao = ReadingPlanDao(helper)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun newInfo(name: String, title: String = "t") = ReadingPlan.ReadingPlanInfo().apply {
        version = 1
        this.name = name
        this.title = title
        description = "desc"
        duration = 365
        startTime = 1_700_000_000_000L
    }

    private fun codes(vararg c: Int): IntArrayList {
        val res = IntArrayList()
        c.forEach(res::add)
        return res
    }

    @Test
    fun `insert and listAllInfo round-trip a plan and assign an id`() {
        val id = dao.insert(newInfo("plan-a"), byteArrayOf(1, 2, 3))
        assertTrue(id > 0)

        val infos = dao.listAllInfo()
        assertEquals(1, infos.size)
        assertEquals("plan-a", infos[0].name)
        assertEquals(id, infos[0].id)
    }

    @Test
    fun `getNameAndData returns null for a missing id and round-trips name + data for an inserted plan`() {
        assertNull(dao.getNameAndData(9999))

        val data = byteArrayOf(0x0a, 0x0b, 0x0c)
        val id = dao.insert(newInfo("plan-b"), data)
        val nameAndData = dao.getNameAndData(id)!!
        assertEquals("plan-b", nameAndData.first)
        assertArrayEquals(data, nameAndData.second)
    }

    @Test
    fun `deleteById removes the ReadingPlan row but leaves ReadingPlanProgress untouched`() {
        val id = dao.insert(newInfo("plan-c"), byteArrayOf())
        val gid = ReadingPlan.gidFromName("plan-c")
        dao.insertOrUpdateProgress(gid, 1, checkTime = 1234L)

        dao.deleteById(id)

        assertTrue(dao.listAllInfo().isEmpty())
        // progress survives delete — this is by design; see the javadoc on the
        // original InternalDb.deleteReadingPlanById.
        assertEquals(listOf(1), dao.getAllReadingCodesByProgressGid(gid).toCodeList())
    }

    @Test
    fun `updateStartDate rewrites only the startTime column`() {
        val id = dao.insert(newInfo("plan-d").apply { startTime = 10L }, byteArrayOf())

        dao.updateStartDate(id, 999L)

        val info = dao.listAllInfo().single()
        assertEquals(999L, info.startTime)
        assertEquals("plan-d", info.name)
    }

    @Test
    fun `listNames returns the names of every ReadingPlan row`() {
        dao.insert(newInfo("a"), byteArrayOf())
        dao.insert(newInfo("b"), byteArrayOf())
        assertEquals(setOf("a", "b"), dao.listNames().toSet())
    }

    @Test
    fun `insertOrUpdateProgress is a replacing upsert for the (gid, readingCode) pair`() {
        val gid = "g2:rp_progress:plan-e"
        dao.insertOrUpdateProgress(gid, 1, checkTime = 10L)
        dao.insertOrUpdateProgress(gid, 1, checkTime = 20L)
        dao.insertOrUpdateProgress(gid, 2, checkTime = 30L)

        assertEquals(listOf(1, 2), dao.getAllReadingCodesByProgressGid(gid).toCodeList())
    }

    @Test
    fun `replaceProgress drops all existing progress for gid and writes only the new codes`() {
        val gid = "g2:rp_progress:plan-f"
        dao.insertOrUpdateProgress(gid, 1, checkTime = 10L)
        dao.insertOrUpdateProgress(gid, 2, checkTime = 20L)

        dao.replaceProgress(gid, codes(5, 6), checkTime = 100L)

        assertEquals(listOf(5, 6), dao.getAllReadingCodesByProgressGid(gid).toCodeList())
    }

    @Test
    fun `insertOrUpdateMultipleProgresses upserts a batch without removing unrelated codes`() {
        val gid = "g2:rp_progress:plan-g"
        dao.insertOrUpdateProgress(gid, 1, checkTime = 10L)
        dao.insertOrUpdateProgress(gid, 2, checkTime = 20L)

        // 2 gets its checkTime refreshed; 3 is added; 1 is untouched.
        dao.insertOrUpdateMultipleProgresses(gid, codes(2, 3), checkTime = 100L)

        assertEquals(listOf(1, 2, 3), dao.getAllReadingCodesByProgressGid(gid).toCodeList())
    }

    @Test
    fun `deleteProgress removes exactly the one (gid, readingCode) pair`() {
        val gid = "g2:rp_progress:plan-h"
        dao.insertOrUpdateProgress(gid, 1, checkTime = 10L)
        dao.insertOrUpdateProgress(gid, 2, checkTime = 20L)

        dao.deleteProgress(gid, 1)
        assertEquals(listOf(2), dao.getAllReadingCodesByProgressGid(gid).toCodeList())
    }

    @Test
    fun `deleteAllProgressForGid clears every row matching gid and returns the affected count`() {
        val gid = "g2:rp_progress:plan-i"
        dao.insertOrUpdateProgress(gid, 1, checkTime = 10L)
        dao.insertOrUpdateProgress(gid, 2, checkTime = 20L)
        dao.insertOrUpdateProgress("other-gid", 1, checkTime = 30L)

        val deleted = dao.deleteAllProgressForGid(gid)
        assertEquals(2, deleted)
        assertTrue(dao.getAllReadingCodesByProgressGid(gid).toCodeList().isEmpty())
        assertEquals(listOf(1), dao.getAllReadingCodesByProgressGid("other-gid").toCodeList())
    }

    @Test
    fun `getProgressSummaryForSync groups reading codes by gid across the whole progress table`() {
        dao.insertOrUpdateProgress("gid-1", 1, checkTime = 10L)
        dao.insertOrUpdateProgress("gid-1", 2, checkTime = 20L)
        dao.insertOrUpdateProgress("gid-2", 7, checkTime = 30L)

        val summary = dao.getProgressSummaryForSync()
        assertEquals(setOf(1, 2), summary["gid-1"])
        assertEquals(setOf(7), summary["gid-2"])
    }

    private fun IntArrayList.toCodeList(): List<Int> =
        (0 until size()).map { this[it] }
}
