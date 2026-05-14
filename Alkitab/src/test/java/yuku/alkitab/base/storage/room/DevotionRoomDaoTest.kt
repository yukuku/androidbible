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
 * Tests for the Room-generated [DevotionRoomDao]. Verifies the `(name, date)`
 * upsert semantics that mirror the legacy [yuku.alkitab.base.storage.DevotionDao]
 * delete-then-insert pattern, plus the `dataFormatVersion`-filtered lookup
 * and the touchTime-based cache eviction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DevotionRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DevotionRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.devotionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        name: String,
        date: String,
        body: String? = "body",
        readyToUse: Int = 1,
        touchTime: Int = 1_700_000_000,
        dataFormatVersion: Int = 1,
    ) = DevotionEntity(
        _id = 0L,
        name = name,
        date = date,
        body = body,
        readyToUse = readyToUse,
        touchTime = touchTime,
        dataFormatVersion = dataFormatVersion,
    )

    @Test
    fun `listAll on an empty database returns an empty list`() {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `insert assigns a positive _id and the row can be read back via findByNameDate`() {
        val id = dao.insert(entity("RH", "2026-05-14"))
        assertTrue("expected positive _id, got $id", id > 0)
        val loaded = dao.findByNameDateAndDataFormatVersion("RH", "2026-05-14", 1)
        assertNotNull(loaded)
        assertEquals("body", loaded!!.body)
    }

    @Test
    fun `findByNameDateAndDataFormatVersion returns null when no row matches the dataFormatVersion`() {
        dao.insert(entity("RH", "2026-05-14", dataFormatVersion = 1))
        // No v2 row exists.
        assertNull(dao.findByNameDateAndDataFormatVersion("RH", "2026-05-14", 2))
    }

    @Test
    fun `findByNameDateAndDataFormatVersion returns null for an unknown (name, date) pair`() {
        dao.insert(entity("RH", "2026-05-14"))
        assertNull(dao.findByNameDateAndDataFormatVersion("SH", "2026-05-14", 1))
        assertNull(dao.findByNameDateAndDataFormatVersion("RH", "2026-05-15", 1))
    }

    @Test
    fun `upsertByNameAndDate replaces the existing row for the same (name, date) pair`() {
        dao.insert(entity("RH", "2026-05-14", body = "v1", touchTime = 100))
        dao.upsertByNameAndDate(entity("RH", "2026-05-14", body = "v2", touchTime = 200))

        val rows = dao.listAll()
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals("v2", body)
            assertEquals(200, touchTime)
        }
    }

    @Test
    fun `upsertByNameAndDate inserts when no row exists for the (name, date) pair`() {
        dao.upsertByNameAndDate(entity("RH", "2026-05-14"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `upsertByNameAndDate does not touch rows with a different (name, date) pair`() {
        dao.insert(entity("RH", "2026-05-14", body = "rh"))
        dao.insert(entity("SH", "2026-05-14", body = "sh"))

        dao.upsertByNameAndDate(entity("RH", "2026-05-14", body = "rh-v2"))

        assertEquals("sh", dao.findByNameDateAndDataFormatVersion("SH", "2026-05-14", 1)!!.body)
        assertEquals("rh-v2", dao.findByNameDateAndDataFormatVersion("RH", "2026-05-14", 1)!!.body)
    }

    @Test
    fun `deleteByNameAndDate returns the number of rows deleted and leaves unrelated rows in place`() {
        dao.insert(entity("RH", "2026-05-14"))
        dao.insert(entity("SH", "2026-05-14"))

        assertEquals(1, dao.deleteByNameAndDate("RH", "2026-05-14"))
        assertEquals(0, dao.deleteByNameAndDate("RH", "2026-05-14"))
        assertEquals(1, dao.count())
        assertNotNull(dao.findByNameDateAndDataFormatVersion("SH", "2026-05-14", 1))
    }

    @Test
    fun `deleteWithTouchTimeBefore evicts only rows older than the cutoff`() {
        dao.insert(entity("RH", "2026-05-10", touchTime = 100))
        dao.insert(entity("RH", "2026-05-12", touchTime = 200))
        dao.insert(entity("RH", "2026-05-14", touchTime = 300))

        // Cutoff is exclusive — rows with touchTime exactly equal to the cutoff are kept.
        assertEquals(1, dao.deleteWithTouchTimeBefore(200))

        val remaining = dao.listAll().map { it.date }
        assertTrue("2026-05-12 should still be present: $remaining", remaining.contains("2026-05-12"))
        assertTrue("2026-05-14 should still be present: $remaining", remaining.contains("2026-05-14"))
        assertEquals(2, remaining.size)
    }

    @Test
    fun `insertAll persists every row in a single call`() {
        dao.insertAll(
            listOf(
                entity("RH", "2026-05-12"),
                entity("RH", "2026-05-13"),
                entity("SH", "2026-05-12"),
            ),
        )
        assertEquals(3, dao.count())
    }
}
