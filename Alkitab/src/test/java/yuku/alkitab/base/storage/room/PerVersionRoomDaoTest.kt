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
 * Tests for the Room-generated [PerVersionRoomDao]. Verifies the
 * `versionId`-keyed upsert semantics that mirror the legacy
 * [yuku.alkitab.base.storage.PerVersionDao] `SQLiteDatabase.replace`
 * behaviour, plus the unique-index conflict resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PerVersionRoomDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PerVersionRoomDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.perVersionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(versionId: String, settings: String? = """{"fontSizeMultiplier":1.0}""") =
        PerVersionEntity(_id = 0L, versionId = versionId, settings = settings)

    @Test
    fun `listAll on an empty database returns an empty list`() {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `insert assigns a positive _id and the row can be read back via findByVersionId`() {
        val id = dao.insert(entity("preset/kjv"))
        assertTrue("expected positive _id, got $id", id > 0)
        val loaded = dao.findByVersionId("preset/kjv")
        assertNotNull(loaded)
        assertEquals("""{"fontSizeMultiplier":1.0}""", loaded!!.settings)
    }

    @Test
    fun `findByVersionId returns null for an unknown versionId`() {
        dao.insert(entity("preset/kjv"))
        assertNull(dao.findByVersionId("preset/asv"))
    }

    @Test
    fun `upsert replaces the existing row for the same versionId`() {
        dao.insert(entity("preset/kjv", settings = """{"fontSizeMultiplier":1.0}"""))
        dao.upsert(entity("preset/kjv", settings = """{"fontSizeMultiplier":1.5}"""))

        val rows = dao.listAll()
        assertEquals(1, rows.size)
        assertEquals("""{"fontSizeMultiplier":1.5}""", rows.single().settings)
    }

    @Test
    fun `upsert inserts when no row exists for the versionId`() {
        dao.upsert(entity("preset/kjv"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `upsert does not touch rows with a different versionId`() {
        dao.insert(entity("preset/kjv", settings = "kjv-original"))
        dao.insert(entity("preset/asv", settings = "asv-original"))

        dao.upsert(entity("preset/kjv", settings = "kjv-replaced"))

        assertEquals("asv-original", dao.findByVersionId("preset/asv")!!.settings)
        assertEquals("kjv-replaced", dao.findByVersionId("preset/kjv")!!.settings)
        assertEquals(2, dao.count())
    }

    @Test
    fun `upsert preserves null settings`() {
        dao.upsert(entity("preset/kjv", settings = null))
        assertNull(dao.findByVersionId("preset/kjv")!!.settings)
    }

    @Test
    fun `count reflects the number of rows currently in the table`() {
        assertEquals(0, dao.count())
        dao.insert(entity("preset/a"))
        dao.insert(entity("preset/b"))
        dao.insert(entity("preset/c"))
        assertEquals(3, dao.count())
    }
}
