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
 * Tests for the Room-generated [ProgressMarkRoomDao]. Verifies the
 * `preset_id`-keyed lookups and the transactional history-plus-mark write
 * path that mirrors the legacy [yuku.alkitab.base.storage.ProgressMarkDao]
 * `beginTransactionNonExclusive` semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ProgressMarkRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ProgressMarkRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.progressMarkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun mark(presetId: Int, ari: Int, caption: String? = "m", modifyTime: Int? = 1_700_000_000) =
        ProgressMarkEntity(
            _id = 0L,
            preset_id = presetId,
            caption = caption,
            ari = ari,
            modifyTime = modifyTime,
        )

    private fun history(presetId: Int, ari: Int, caption: String? = "h", createTime: Int? = 1_700_000_000) =
        ProgressMarkHistoryEntity(
            _id = 0L,
            progress_mark_preset_id = presetId,
            progress_mark_caption = caption,
            ari = ari,
            createTime = createTime,
        )

    @Test
    fun `listAllWithNonZeroAri excludes rows whose ari is zero`() {
        dao.insert(mark(presetId = 0, ari = 0))
        dao.insert(mark(presetId = 1, ari = 100))
        dao.insert(mark(presetId = 2, ari = 0))

        val rows = dao.listAllWithNonZeroAri()
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().preset_id)
    }

    @Test
    fun `countAllWithNonZeroAri matches the listAllWithNonZeroAri size`() {
        dao.insert(mark(presetId = 0, ari = 100))
        dao.insert(mark(presetId = 1, ari = 0))
        dao.insert(mark(presetId = 2, ari = 200))
        assertEquals(2, dao.countAllWithNonZeroAri())
    }

    @Test
    fun `findByPresetId returns the matching row`() {
        dao.insert(mark(presetId = 2, ari = 42, caption = "two"))
        val loaded = dao.findByPresetId(2)
        assertNotNull(loaded)
        assertEquals(42, loaded!!.ari)
        assertEquals("two", loaded.caption)
    }

    @Test
    fun `findByPresetId returns null when no row exists`() {
        dao.insert(mark(presetId = 0, ari = 1))
        assertNull(dao.findByPresetId(7))
    }

    @Test
    fun `insertHistoryAndUpsertMark inserts a fresh mark when none exists for the preset`() {
        dao.insertHistoryAndUpsertMark(
            history = history(presetId = 0, ari = 100, caption = "h"),
            mark = mark(presetId = 0, ari = 100, caption = "m"),
        )

        val loaded = dao.findByPresetId(0)
        assertNotNull(loaded)
        assertEquals("m", loaded!!.caption)
        assertEquals(1, dao.listHistoryByPresetId(0).size)
    }

    @Test
    fun `insertHistoryAndUpsertMark updates the existing mark in place for the same preset`() {
        dao.insertHistoryAndUpsertMark(history(0, 1, "h1"), mark(0, 1, "first"))
        dao.insertHistoryAndUpsertMark(history(0, 2, "h2"), mark(0, 2, "second"))

        val rows = dao.listAllForTest().filter { it.preset_id == 0 }
        assertEquals(1, rows.size)
        assertEquals("second", rows.single().caption)
    }

    @Test
    fun `insertHistoryAndUpsertMark appends a history row on every call`() {
        dao.insertHistoryAndUpsertMark(history(0, 1, "h1"), mark(0, 1))
        dao.insertHistoryAndUpsertMark(history(0, 2, "h2"), mark(0, 2))
        dao.insertHistoryAndUpsertMark(history(0, 3, "h3"), mark(0, 3))

        val history = dao.listHistoryByPresetId(0)
        assertEquals(3, history.size)
        assertEquals(listOf("h1", "h2", "h3"), history.map { it.progress_mark_caption })
    }

    @Test
    fun `listHistoryByPresetId orders by createTime ascending`() {
        dao.insertHistory(history(presetId = 0, ari = 3, caption = "third", createTime = 300))
        dao.insertHistory(history(presetId = 0, ari = 1, caption = "first", createTime = 100))
        dao.insertHistory(history(presetId = 0, ari = 2, caption = "second", createTime = 200))

        val history = dao.listHistoryByPresetId(0)
        assertEquals(listOf("first", "second", "third"), history.map { it.progress_mark_caption })
    }

    @Test
    fun `listHistoryByPresetId is scoped to the requested preset_id only`() {
        dao.insertHistory(history(presetId = 0, ari = 1, caption = "preset0"))
        dao.insertHistory(history(presetId = 1, ari = 1, caption = "preset1"))
        dao.insertHistory(history(presetId = 2, ari = 1, caption = "preset2"))

        val history = dao.listHistoryByPresetId(1)
        assertEquals(1, history.size)
        assertEquals("preset1", history.single().progress_mark_caption)
    }

    @Test
    fun `null modifyTime and caption round-trip through insert`() {
        dao.insert(mark(presetId = 0, ari = 0, caption = null, modifyTime = null))
        val loaded = dao.findByPresetId(0)!!
        assertNull(loaded.caption)
        assertNull(loaded.modifyTime)
    }

    @Test
    fun `insert assigns positive _ids and history is independent of mark _id`() {
        val markId = dao.insert(mark(presetId = 0, ari = 1))
        val historyId = dao.insertHistory(history(presetId = 0, ari = 1))
        assertTrue("expected positive mark _id, got $markId", markId > 0)
        assertTrue("expected positive history _id, got $historyId", historyId > 0)
    }
}
