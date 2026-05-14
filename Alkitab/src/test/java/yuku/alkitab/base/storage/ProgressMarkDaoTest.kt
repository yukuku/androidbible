package yuku.alkitab.base.storage

import android.app.Application
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.ProgressMarkEntity
import yuku.alkitab.model.ProgressMark
import yuku.alkitab.util.Ari
import java.util.Date

/**
 * Robolectric tests for [ProgressMarkDao]. Originally exercised the
 * SQLite-backed implementation against [InternalDbHelper]; after the
 * ProgressMark → Room migration the facade routes through Room, so this
 * test now installs an in-memory [AppDatabase] in [setUp] and pre-seeds the
 * five placeholder rows that legacy `InternalDbHelper.onCreate` used to
 * insert via `insertDefaultProgressMarks`.
 *
 * The behavioural contract is unchanged — these tests are the parity gate
 * for the Room migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ProgressMarkDaoTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var roomDb: AppDatabase
    private lateinit var dao: ProgressMarkDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        // InternalDbHelper is still needed by the legacy ProgressMarkDao
        // constructor signature, but the facade ignores it now.
        helper = InternalDbHelper(app)
        roomDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setForTesting(roomDb)
        dao = ProgressMarkDao(helper)

        // Pre-seed the five placeholder rows the way ProgressMarkDataMigration
        // would have on first launch (sourced from
        // `InternalDbHelper.insertDefaultProgressMarks`).
        val roomDao = roomDb.progressMarkDao()
        for (presetId in 0..4) {
            roomDao.insert(
                ProgressMarkEntity(
                    _id = 0L,
                    preset_id = presetId,
                    caption = null,
                    ari = 0,
                    modifyTime = null,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        AppDatabase.setForTesting(null)
        helper.close()
    }

    private fun newMark(presetId: Int, ari: Int, caption: String = "m") = ProgressMark().apply {
        preset_id = presetId
        this.ari = ari
        this.caption = caption
        modifyTime = Date(1_700_000_000_000L)
    }

    @Test
    fun `listAll returns only marks whose ari is non-zero so empty defaults are excluded`() {
        // The seeded placeholder rows all have ari=0 and must be filtered out.
        assertTrue(dao.listAll().isEmpty())

        dao.insertOrUpdate(newMark(presetId = 0, ari = Ari.encode(0, 1, 1)))
        dao.insertOrUpdate(newMark(presetId = 1, ari = 0)) // still empty — must not appear

        val rows = dao.listAll()
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().preset_id)
    }

    @Test
    fun `countAll matches listAll size`() {
        dao.insertOrUpdate(newMark(presetId = 0, ari = Ari.encode(0, 1, 1)))
        dao.insertOrUpdate(newMark(presetId = 1, ari = Ari.encode(0, 2, 1)))
        assertEquals(dao.listAll().size, dao.countAll())
        assertEquals(2, dao.countAll())
    }

    @Test
    fun `getByPresetId returns the seeded empty mark for preset ids 0 through 4`() {
        // The seed in @Before mirrors `insertDefaultProgressMarks`: five
        // placeholder rows with ari=0 that are reachable via getByPresetId
        // before any upsert.
        for (presetId in 0..4) {
            val seeded = dao.getByPresetId(presetId)!!
            assertEquals(0, seeded.ari)
        }
    }

    @Test
    fun `getByPresetId round-trips the fields set by insertOrUpdate`() {
        val ari = Ari.encode(42, 3, 16)
        dao.insertOrUpdate(newMark(presetId = 0, ari = ari, caption = "John 3:16"))
        val loaded = dao.getByPresetId(0)!!
        assertEquals(ari, loaded.ari)
        assertEquals("John 3:16", loaded.caption)
    }

    @Test
    fun `getByPresetId returns null when preset_id has no row`() {
        assertNull(dao.getByPresetId(99))
    }

    @Test
    fun `insertOrUpdate updates the existing row when preset_id matches`() {
        val ari1 = Ari.encode(0, 1, 1)
        val ari2 = Ari.encode(0, 2, 1)
        dao.insertOrUpdate(newMark(presetId = 2, ari = ari1, caption = "first"))
        dao.insertOrUpdate(newMark(presetId = 2, ari = ari2, caption = "second"))

        val loaded = dao.getByPresetId(2)!!
        assertEquals(ari2, loaded.ari)
        assertEquals("second", loaded.caption)
        // Only one mark row per preset_id.
        assertEquals(1, dao.listAll().count { it.preset_id == 2 })
    }

    @Test
    fun `insertOrUpdate appends history every call even when the preset is overwritten`() {
        dao.insertOrUpdate(newMark(presetId = 3, ari = Ari.encode(0, 1, 1), caption = "h1"))
        dao.insertOrUpdate(newMark(presetId = 3, ari = Ari.encode(0, 1, 2), caption = "h2"))
        dao.insertOrUpdate(newMark(presetId = 3, ari = Ari.encode(0, 1, 3), caption = "h3"))

        val history = dao.listHistoryByPresetId(3)
        assertEquals(3, history.size)
        assertEquals(listOf("h1", "h2", "h3"), history.map { it.progress_mark_caption })
    }

    @Test
    fun `listHistoryByPresetId returns empty list when no history was written`() {
        assertTrue(dao.listHistoryByPresetId(4).isEmpty())
    }
}
