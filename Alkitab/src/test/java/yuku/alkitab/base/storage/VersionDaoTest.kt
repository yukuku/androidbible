package yuku.alkitab.base.storage

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.model.MVersionDb

/**
 * Robolectric tests for [VersionDao]. Mirrors the setup in [InternalDbTest] —
 * Robolectric is required because [InternalDbHelper] extends
 * [android.database.sqlite.SQLiteOpenHelper].
 *
 * The `Version` table's schema is owned by [InternalDbHelper.createTableVersion],
 * so these tests also implicitly exercise that schema stays in sync with what
 * the DAO expects to read/write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VersionDaoTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var dao: VersionDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        helper = InternalDbHelper(app)
        dao = VersionDao(helper)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun newMv(
        filename: String,
        presetName: String? = null,
        ordering: Int = 100,
        shortName: String = "SN",
        longName: String = "Long Name",
        locale: String = "en",
        description: String = "desc",
        modifyTime: Int = 0,
    ) = MVersionDb().apply {
        this.filename = filename
        this.preset_name = presetName
        this.ordering = ordering
        this.shortName = shortName
        this.longName = longName
        this.locale = locale
        this.description = description
        this.modifyTime = modifyTime
    }

    @Test
    fun `listAll returns an empty list when no versions have been inserted`() {
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `insertOrUpdateWithActive inserts a row and round-trips every persisted field`() {
        val mv = newMv(
            filename = "/data/a.yes",
            presetName = "kjv",
            ordering = 101,
            shortName = "KJV",
            longName = "King James",
            locale = "en-US",
            description = "a desc",
            modifyTime = 1_700_000_000,
        )
        dao.insertOrUpdateWithActive(mv, active = true)

        val loaded = dao.listAll().single()
        assertEquals("/data/a.yes", loaded.filename)
        assertEquals("kjv", loaded.preset_name)
        assertEquals(101, loaded.ordering)
        assertEquals("KJV", loaded.shortName)
        assertEquals("King James", loaded.longName)
        assertEquals("en-US", loaded.locale)
        assertEquals("a desc", loaded.description)
        assertEquals(1_700_000_000, loaded.modifyTime)
        assertTrue(loaded.cache_active)
    }

    @Test
    fun `listAll orders rows by ordering ascending regardless of insertion order`() {
        dao.insertOrUpdateWithActive(newMv(filename = "/b.yes", ordering = 103), true)
        dao.insertOrUpdateWithActive(newMv(filename = "/a.yes", ordering = 101), true)
        dao.insertOrUpdateWithActive(newMv(filename = "/c.yes", ordering = 102), true)

        assertEquals(
            listOf("/a.yes", "/c.yes", "/b.yes"),
            dao.listAll().map { it.filename },
        )
    }

    @Test
    fun `insertOrUpdateWithActive preserves the existing ordering when filename already exists`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", ordering = 101, longName = "v1"),
            true,
        )

        // Re-insert the same filename with a different ordering and name.
        // Per contract, the existing row's ordering wins; the passed-in mv.ordering
        // is overwritten in-place as a side effect.
        val mv = newMv(filename = "/a.yes", ordering = 999, longName = "v2")
        dao.insertOrUpdateWithActive(mv, true)

        assertEquals(101, mv.ordering)
        val loaded = dao.listAll().single()
        assertEquals(101, loaded.ordering)
        assertEquals("v2", loaded.longName)
    }

    @Test
    fun `setActive by preset_name toggles the cache_active flag`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", presetName = "kjv", ordering = 101),
            active = true,
        )

        dao.setActive(newMv(filename = "/a.yes", presetName = "kjv"), active = false)
        assertFalse(dao.listAll().single().cache_active)

        dao.setActive(newMv(filename = "/a.yes", presetName = "kjv"), active = true)
        assertTrue(dao.listAll().single().cache_active)
    }

    @Test
    fun `setActive by filename is used when preset_name is null`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", presetName = null, ordering = 101),
            active = true,
        )

        dao.setActive(newMv(filename = "/a.yes", presetName = null), active = false)
        assertFalse(dao.listAll().single().cache_active)
    }

    @Test
    fun `getMaxOrdering returns the largest ordering across all rows`() {
        dao.insertOrUpdateWithActive(newMv(filename = "/a.yes", ordering = 101), true)
        dao.insertOrUpdateWithActive(newMv(filename = "/b.yes", ordering = 105), true)
        dao.insertOrUpdateWithActive(newMv(filename = "/c.yes", ordering = 102), true)

        assertEquals(105, dao.getMaxOrdering())
    }

    @Test
    fun `delete by preset_name removes matching rows and short-circuits filename fallback`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", presetName = "kjv", ordering = 101), true,
        )
        dao.insertOrUpdateWithActive(
            newMv(filename = "/b.yes", presetName = null, ordering = 102), true,
        )

        dao.delete(newMv(filename = "/a.yes", presetName = "kjv"))
        assertEquals(listOf("/b.yes"), dao.listAll().map { it.filename })
    }

    @Test
    fun `delete falls back to filename when preset_name is null`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", presetName = null, ordering = 101), true,
        )

        dao.delete(newMv(filename = "/a.yes", presetName = null))
        assertTrue(dao.listAll().isEmpty())
    }

    @Test
    fun `delete falls back to filename when preset_name is set but does not match any row`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", presetName = null, ordering = 101), true,
        )

        // preset_name "ghost" doesn't exist in the table; the method should fall
        // through to deleting by filename instead.
        dao.delete(newMv(filename = "/a.yes", presetName = "ghost"))
        assertTrue(dao.listAll().isEmpty())
    }

    /**
     * Documents a pre-existing behavior (not introduced by the DAO extraction):
     * [VersionDao.insertOrUpdateWithActive] dedupes only by `filename`, so two
     * rows can legitimately share the same `preset_name`. In that case,
     * [VersionDao.setActive] matches by `preset_name` first and flips both rows.
     *
     * If this ever needs to change, update the production code and this test
     * together.
     */
    @Test
    fun `setActive by preset_name affects all rows that share the preset_name (pre-existing behavior)`() {
        dao.insertOrUpdateWithActive(
            newMv(filename = "/a.yes", presetName = "kjv", ordering = 101), active = true,
        )
        dao.insertOrUpdateWithActive(
            newMv(filename = "/b.yes", presetName = "kjv", ordering = 102), active = true,
        )

        dao.setActive(newMv(filename = "/a.yes", presetName = "kjv"), active = false)

        val rows = dao.listAll()
        assertEquals(2, rows.size)
        assertTrue("both rows should flip to inactive", rows.all { !it.cache_active })
    }
}
