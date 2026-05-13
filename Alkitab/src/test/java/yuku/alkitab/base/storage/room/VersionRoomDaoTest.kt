package yuku.alkitab.base.storage.room

import android.app.Application
import androidx.room.Room
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
 * Tests for the Room-generated [VersionRoomDao]. Complements
 * [yuku.alkitab.base.storage.VersionDaoTest] — that one exercises the legacy
 * `MVersionDb` facade end-to-end; this one verifies the Room-level primitives
 * (`upsertByFilename`, `reorderByFilename`, the shift queries) in isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VersionRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: VersionRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.versionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        filename: String,
        ordering: Int,
        presetName: String? = null,
        active: Int = 1,
        longName: String = "Long",
    ) = VersionEntity(
        _id = 0L,
        locale = "en",
        shortName = "SN",
        longName = longName,
        description = "desc",
        filename = filename,
        preset_name = presetName,
        modifyTime = 1_700_000_000,
        active = active,
        ordering = ordering,
    )

    @Test
    fun `listAll on an empty database returns an empty list`() {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `insert assigns a non-zero _id and the row can be read back via findByFilename`() {
        val id = dao.insert(entity("/a.yes", 101))
        assertTrue("expected positive _id, got $id", id > 0)
        val loaded = dao.findByFilename("/a.yes")
        assertNotNull(loaded)
        assertEquals(id, loaded!!._id)
        assertEquals(101, loaded.ordering)
    }

    @Test
    fun `findByFilename returns null for a filename that is not in the table`() {
        dao.insert(entity("/a.yes", 101))
        assertNull(dao.findByFilename("/ghost.yes"))
    }

    @Test
    fun `getMaxOrdering returns zero for an empty table (IFNULL coalesces the NULL max)`() {
        assertEquals(0, dao.getMaxOrdering())
    }

    @Test
    fun `getMaxOrdering returns the largest ordering across all rows`() {
        dao.insert(entity("/a.yes", 101))
        dao.insert(entity("/b.yes", 105))
        dao.insert(entity("/c.yes", 103))
        assertEquals(105, dao.getMaxOrdering())
    }

    @Test
    fun `upsertByFilename inserts a fresh row when the filename does not already exist`() {
        val result = dao.upsertByFilename(entity("/a.yes", 101, longName = "v1"))
        assertTrue(result.wasInsert)
        assertEquals(101, result.ordering)
        val loaded = dao.findByFilename("/a.yes")!!
        assertEquals("v1", loaded.longName)
        assertEquals(101, loaded.ordering)
    }

    @Test
    fun `upsertByFilename overwrites a row matched by filename but preserves the existing ordering`() {
        dao.upsertByFilename(entity("/a.yes", 101, longName = "v1"))

        val result = dao.upsertByFilename(entity("/a.yes", 999, longName = "v2"))

        assertFalse("expected update, not insert", result.wasInsert)
        // Existing ordering wins.
        assertEquals(101, result.ordering)
        val rows = dao.listAll()
        assertEquals(1, rows.size)
        assertEquals("v2", rows.single().longName)
        assertEquals(101, rows.single().ordering)
    }

    @Test
    fun `setActiveByPresetName flips the active flag for all rows sharing the preset_name`() {
        dao.insert(entity("/a.yes", 101, presetName = "kjv", active = 1))
        dao.insert(entity("/b.yes", 102, presetName = "kjv", active = 1))
        dao.insert(entity("/c.yes", 103, presetName = "tb", active = 1))

        val touched = dao.setActiveByPresetName("kjv", 0)

        assertEquals(2, touched)
        val byFilename = dao.listAll().associateBy { it.filename }
        assertEquals(0, byFilename["/a.yes"]!!.active)
        assertEquals(0, byFilename["/b.yes"]!!.active)
        assertEquals(1, byFilename["/c.yes"]!!.active)
    }

    @Test
    fun `setActiveByFilename returns zero when the filename is not in the table`() {
        dao.insert(entity("/a.yes", 101))
        assertEquals(0, dao.setActiveByFilename("/ghost.yes", 0))
    }

    @Test
    fun `deleteByPresetName removes every row sharing the preset_name`() {
        dao.insert(entity("/a.yes", 101, presetName = "kjv"))
        dao.insert(entity("/b.yes", 102, presetName = "kjv"))
        dao.insert(entity("/c.yes", 103, presetName = "tb"))

        val deleted = dao.deleteByPresetName("kjv")

        assertEquals(2, deleted)
        assertEquals(listOf("/c.yes"), dao.listAll().map { it.filename })
    }

    @Test
    fun `reorderByFilename move-up shifts a contiguous block up by one and slots the target into place`() {
        dao.insert(entity("/a.yes", 101))
        dao.insert(entity("/b.yes", 102))
        dao.insert(entity("/c.yes", 103))
        dao.insert(entity("/d.yes", 104))
        dao.insert(entity("/e.yes", 105))

        // Move D (104) up to 102. Expectation: A=101, D=102, B=103, C=104, E=105.
        dao.reorderByFilename("/d.yes", fromOrdering = 104, toOrdering = 102)

        val map = dao.listAll().associate { it.filename to it.ordering }
        assertEquals(101, map["/a.yes"])
        assertEquals(102, map["/d.yes"])
        assertEquals(103, map["/b.yes"])
        assertEquals(104, map["/c.yes"])
        assertEquals(105, map["/e.yes"])
    }

    @Test
    fun `reorderByFilename move-down shifts a contiguous block down by one and slots the target into place`() {
        dao.insert(entity("/a.yes", 101))
        dao.insert(entity("/b.yes", 102))
        dao.insert(entity("/c.yes", 103))
        dao.insert(entity("/d.yes", 104))
        dao.insert(entity("/e.yes", 105))

        // Move B (102) down to 104. Expectation: A=101, C=102, D=103, B=104, E=105.
        dao.reorderByFilename("/b.yes", fromOrdering = 102, toOrdering = 104)

        val map = dao.listAll().associate { it.filename to it.ordering }
        assertEquals(101, map["/a.yes"])
        assertEquals(102, map["/c.yes"])
        assertEquals(103, map["/d.yes"])
        assertEquals(104, map["/b.yes"])
        assertEquals(105, map["/e.yes"])
    }

    @Test
    fun `reorderByFilename with from equal to to is a no-op`() {
        dao.insert(entity("/a.yes", 101))
        dao.insert(entity("/b.yes", 102))

        dao.reorderByFilename("/a.yes", fromOrdering = 101, toOrdering = 101)

        assertEquals(101, dao.findByFilename("/a.yes")!!.ordering)
        assertEquals(102, dao.findByFilename("/b.yes")!!.ordering)
    }
}
