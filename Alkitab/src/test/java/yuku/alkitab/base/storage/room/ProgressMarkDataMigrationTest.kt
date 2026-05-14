package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import android.preference.PreferenceManager
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
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey

/**
 * Verifies the one-time copy from the legacy `ProgressMark` and
 * `ProgressMarkHistory` tables into Room. Robolectric is required because
 * [InternalDbHelper] extends [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ProgressMarkDataMigrationTest {
    private lateinit var legacy: InternalDbHelper
    private lateinit var room: AppDatabase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        // Idempotency is anchored on a SharedPreferences flag. Clear it (and
        // the [Preferences] static cache that may hold a previous test's
        // SharedPreferences instance) so every test starts with a clean slate.
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()
        Preferences.invalidate()
        legacy = InternalDbHelper(app)
        // InternalDbHelper.onCreate seeds five placeholder ProgressMark rows
        // (preset_id 0..4, ari = 0). Drop them before each test so individual
        // tests start with a known empty legacy table and only insert exactly
        // what they care about — otherwise the seed mixes with the test's
        // own inserts and obscures the assertions.
        legacy.writableDatabase.execSQL("DELETE FROM " + Db.TABLE_ProgressMark)
        room = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        legacy.close()
    }

    private fun insertLegacyMark(
        presetId: Int?,
        ari: Int?,
        caption: String? = null,
        modifyTime: Int? = null,
    ) {
        val cv = ContentValues().apply {
            if (presetId != null) put(Db.ProgressMark.preset_id, presetId) else putNull(Db.ProgressMark.preset_id)
            if (ari != null) put(Db.ProgressMark.ari, ari) else putNull(Db.ProgressMark.ari)
            if (caption != null) put(Db.ProgressMark.caption, caption) else putNull(Db.ProgressMark.caption)
            if (modifyTime != null) put(Db.ProgressMark.modifyTime, modifyTime) else putNull(Db.ProgressMark.modifyTime)
        }
        legacy.writableDatabase.insert(Db.TABLE_ProgressMark, null, cv)
    }

    private fun insertLegacyHistory(
        presetId: Int?,
        ari: Int?,
        caption: String? = null,
        createTime: Int? = null,
    ) {
        val cv = ContentValues().apply {
            if (presetId != null) put(Db.ProgressMarkHistory.progress_mark_preset_id, presetId)
            else putNull(Db.ProgressMarkHistory.progress_mark_preset_id)
            if (ari != null) put(Db.ProgressMarkHistory.ari, ari) else putNull(Db.ProgressMarkHistory.ari)
            if (caption != null) put(Db.ProgressMarkHistory.progress_mark_caption, caption)
            else putNull(Db.ProgressMarkHistory.progress_mark_caption)
            if (createTime != null) put(Db.ProgressMarkHistory.createTime, createTime)
            else putNull(Db.ProgressMarkHistory.createTime)
        }
        legacy.writableDatabase.insert(Db.TABLE_ProgressMarkHistory, null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.progressMarkDao().listAllForTest().isEmpty())
        assertTrue(room.progressMarkDao().listAllHistoryForTest().isEmpty())
    }

    @Test
    fun `copy moves every legacy mark row into Room and round-trips every persisted field`() {
        insertLegacyMark(presetId = 0, ari = 100, caption = "John 3:16", modifyTime = 1_700_000_000)
        insertLegacyMark(presetId = 1, ari = 200, caption = "Gen 1:1", modifyTime = 1_700_000_100)

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val byPreset = room.progressMarkDao().listAllForTest().associateBy { it.preset_id }
        assertEquals(2, byPreset.size)
        assertEquals(100, byPreset.getValue(0).ari)
        assertEquals("John 3:16", byPreset.getValue(0).caption)
        assertEquals(1_700_000_000, byPreset.getValue(0).modifyTime)
        assertEquals(200, byPreset.getValue(1).ari)
        assertEquals("Gen 1:1", byPreset.getValue(1).caption)
    }

    @Test
    fun `copy moves every legacy history row into Room`() {
        insertLegacyHistory(presetId = 0, ari = 100, caption = "h1", createTime = 1_700_000_000)
        insertLegacyHistory(presetId = 0, ari = 101, caption = "h2", createTime = 1_700_000_100)
        insertLegacyHistory(presetId = 1, ari = 200, caption = "other", createTime = 1_700_000_200)

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(2, room.progressMarkDao().listHistoryByPresetId(0).size)
        assertEquals(1, room.progressMarkDao().listHistoryByPresetId(1).size)
    }

    @Test
    fun `copy preserves the placeholder seed with null modifyTime and null caption`() {
        // Mirrors what `InternalDbHelper.insertDefaultProgressMarks` writes:
        // five rows with `ari = 0`, no caption, no modifyTime. Room declares
        // those nullable so the migration must round-trip them as NULL rather
        // than coercing to 0/"".
        insertLegacyMark(presetId = 0, ari = 0)
        insertLegacyMark(presetId = 1, ari = 0)
        insertLegacyMark(presetId = 2, ari = 0)
        insertLegacyMark(presetId = 3, ari = 0)
        insertLegacyMark(presetId = 4, ari = 0)

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.progressMarkDao().listAllForTest().sortedBy { it.preset_id }
        assertEquals(5, rows.size)
        for ((idx, row) in rows.withIndex()) {
            assertEquals(idx, row.preset_id)
            assertEquals(0, row.ari)
            assertNull(row.caption)
            assertNull(row.modifyTime)
        }
    }

    @Test
    fun `copy drops legacy mark rows with a null preset_id`() {
        // The legacy schema permits NULL preset_id; Room enforces non-null on
        // that column. ProgressMarkDataMigration skips these rows because
        // they are unreachable by the facade's keyed lookups, and coalescing
        // a NULL preset_id to 0 would collide with the legitimate
        // `preset_id = 0` placeholder row that the legacy schema always
        // carries.
        insertLegacyMark(presetId = null, ari = 100)
        insertLegacyMark(presetId = 0, ari = 200)

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.progressMarkDao().listAllForTest()
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().preset_id)
    }

    @Test
    fun `copy drops legacy mark rows with a null ari`() {
        insertLegacyMark(presetId = 0, ari = null)
        insertLegacyMark(presetId = 1, ari = 100)

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.progressMarkDao().listAllForTest()
        assertEquals(1, rows.size)
        assertEquals(1, rows.single().preset_id)
    }

    @Test
    fun `copy survives multiple legacy mark rows with a null preset_id without crashing the migration`() {
        // Regression guard for the failure mode Gemini caught on REM-28: if
        // the migration coalesced NULL preset_ids to 0 the placeholder row
        // for preset_id = 0 would be quietly stomped over (or — with several
        // such rows — corrupted by duplicate entries). Skip-on-NULL must
        // keep the migration green even with several NULL-keyed rows in the
        // legacy table.
        insertLegacyMark(presetId = null, ari = 1)
        insertLegacyMark(presetId = null, ari = 2)
        insertLegacyMark(presetId = null, ari = 3)
        insertLegacyMark(presetId = 0, ari = 99, caption = "real-zero")

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.progressMarkDao().listAllForTest()
        assertEquals(1, rows.size)
        assertEquals(0, rows.single().preset_id)
        assertEquals("real-zero", rows.single().caption)
        assertTrue(Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false))
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyMark(presetId = 0, ari = 100)
        insertLegacyHistory(presetId = 0, ari = 100, caption = "h")

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.progressMarkDao().listAllForTest().size)
        assertEquals(1, room.progressMarkDao().listAllHistoryForTest().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacyMark(presetId = 0, ari = 100)

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.progressMarkDao().findByPresetId(0)
        assertNotNull(row)
        assertTrue("expected positive Room _id, got ${row!!._id}", row._id > 0)
    }

    @Test
    fun `does not resurrect rows after the user overwrites every pin (issue #195)`() {
        // Setup: legacy table has a user pin, mirroring a real upgrade from
        // the pre-Room schema. The legacy row is intentionally preserved as
        // a rollback safety net even after migration.
        insertLegacyMark(presetId = 0, ari = 100, caption = "user-pin")

        // First launch on the Room build: migration copies it across.
        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals(1, room.progressMarkDao().listAllForTest().size)

        // The user overwrites every pin with the empty placeholder (or a
        // future "reset pins" path clears them). The legacy table in
        // AlkitabDb is deliberately not touched by any write path.
        room.clearAllTables()
        assertTrue(room.progressMarkDao().listAllForTest().isEmpty())

        // Next launch: migration runs again. With a count-based gate it
        // would re-copy the legacy row. The flag-based gate must be a strict
        // no-op.
        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(
            "legacy progress_mark row was resurrected",
            room.progressMarkDao().listAllForTest().isEmpty(),
        )
    }

    @Test
    fun `successful copy sets the persistent done flag`() {
        insertLegacyMark(presetId = 0, ari = 100)

        assertFalse(Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false))
        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false))
    }

    @Test
    fun `fresh install with no legacy data also sets the done flag`() {
        // No legacy rows inserted. The migration has nothing to copy but
        // must still mark itself done so subsequent launches don't keep
        // querying the legacy DB.
        assertFalse(Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false))
        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but Room already has rows sets the flag without re-copying`() {
        // Simulates a user who somehow ended up with rows in Room but no
        // done flag set. The migration must NOT re-insert the legacy rows
        // on top of the existing Room state.
        room.progressMarkDao().insert(
            ProgressMarkEntity(
                _id = 0L,
                preset_id = 0,
                caption = "already-migrated",
                ari = 100,
                modifyTime = 1_700_000_000,
            ),
        )
        insertLegacyMark(presetId = 0, ari = 999, caption = "legacy")

        ProgressMarkDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.progressMarkDao().listAllForTest()
        assertEquals(1, rows.size)
        assertEquals("already-migrated", rows.single().caption)
        assertTrue(Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false))
    }
}
