package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import android.preference.PreferenceManager
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
 * Verifies the one-time copy from the legacy `ReadingPlan` and
 * `ReadingPlanProgress` tables into Room. Robolectric is required because
 * [InternalDbHelper] extends [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ReadingPlanDataMigrationTest {
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
        room = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        legacy.close()
    }

    private fun insertLegacyPlan(
        version: Int? = 1,
        name: String? = null,
        title: String? = null,
        description: String? = null,
        duration: Int? = null,
        startTime: Long? = null,
        data: ByteArray? = null,
    ): Long {
        val cv = ContentValues().apply {
            if (version != null) put(Db.ReadingPlan.version, version) else putNull(Db.ReadingPlan.version)
            if (name != null) put(Db.ReadingPlan.name, name) else putNull(Db.ReadingPlan.name)
            if (title != null) put(Db.ReadingPlan.title, title) else putNull(Db.ReadingPlan.title)
            if (description != null) put(Db.ReadingPlan.description, description) else putNull(Db.ReadingPlan.description)
            if (duration != null) put(Db.ReadingPlan.duration, duration) else putNull(Db.ReadingPlan.duration)
            if (startTime != null) put(Db.ReadingPlan.startTime, startTime) else putNull(Db.ReadingPlan.startTime)
            if (data != null) put(Db.ReadingPlan.data, data) else putNull(Db.ReadingPlan.data)
        }
        return legacy.writableDatabase.insert(Db.TABLE_ReadingPlan, null, cv)
    }

    private fun insertLegacyProgress(
        gid: String?,
        readingCode: Int?,
        checkTime: Long? = null,
    ): Long {
        val cv = ContentValues().apply {
            if (gid != null) put(Db.ReadingPlanProgress.reading_plan_progress_gid, gid)
            else putNull(Db.ReadingPlanProgress.reading_plan_progress_gid)
            if (readingCode != null) put(Db.ReadingPlanProgress.reading_code, readingCode)
            else putNull(Db.ReadingPlanProgress.reading_code)
            if (checkTime != null) put(Db.ReadingPlanProgress.checkTime, checkTime)
            else putNull(Db.ReadingPlanProgress.checkTime)
        }
        return legacy.writableDatabase.insert(Db.TABLE_ReadingPlanProgress, null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.readingPlanDao().listAllForTest().isEmpty())
        assertTrue(room.readingPlanDao().listAllProgressForTest().isEmpty())
    }

    @Test
    fun `copy moves every legacy plan row into Room and round-trips every persisted field`() {
        insertLegacyPlan(
            version = 1,
            name = "rp-a",
            title = "Plan A",
            description = "desc",
            duration = 365,
            startTime = 1_700_000_000_000L,
            data = byteArrayOf(0x10, 0x20, 0x30),
        )
        insertLegacyPlan(
            version = 2,
            name = "rp-b",
            title = "Plan B",
            duration = 30,
            startTime = 1_700_000_100_000L,
            data = byteArrayOf(),
        )

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val byName = room.readingPlanDao().listAllForTest().associateBy { it.name }
        assertEquals(2, byName.size)
        val a = byName.getValue("rp-a")
        assertEquals(1, a.version)
        assertEquals("Plan A", a.title)
        assertEquals(365, a.duration)
        assertEquals(1_700_000_000_000L, a.startTime)
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x30), a.data)
        val b = byName.getValue("rp-b")
        assertNull(b.description)
        assertArrayEquals(byteArrayOf(), b.data)
    }

    @Test
    fun `copy moves every legacy progress row into Room`() {
        insertLegacyProgress(gid = "g1", readingCode = 1, checkTime = 10L)
        insertLegacyProgress(gid = "g1", readingCode = 2, checkTime = 20L)
        insertLegacyProgress(gid = "g2", readingCode = 7, checkTime = 30L)

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(listOf(1, 2), room.readingPlanDao().listReadingCodesForGid("g1"))
        assertEquals(listOf(7), room.readingPlanDao().listReadingCodesForGid("g2"))
    }

    @Test
    fun `copy round-trips a plan whose every nullable column is NULL`() {
        // Legacy schema declares every column except _id as nullable. Make
        // sure a fully-null plan survives the round-trip without coercion.
        insertLegacyPlan(
            version = null, name = null, title = null, description = null,
            duration = null, startTime = null, data = null,
        )

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.readingPlanDao().listAllForTest().single()
        assertNull(row.version)
        assertNull(row.name)
        assertNull(row.title)
        assertNull(row.description)
        assertNull(row.duration)
        assertNull(row.startTime)
        assertNull(row.data)
    }

    @Test
    fun `copy drops legacy progress rows with a null gid`() {
        // The legacy schema permits NULL gid; Room enforces non-null on that
        // column. ReadingPlanDataMigration skips these rows because they are
        // unreachable by the facade's keyed lookups.
        insertLegacyProgress(gid = null, readingCode = 1)
        insertLegacyProgress(gid = "g1", readingCode = 1, checkTime = 100L)

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.readingPlanDao().listAllProgressForTest()
        assertEquals(1, rows.size)
        assertEquals("g1", rows.single().reading_plan_progress_gid)
    }

    @Test
    fun `copy drops legacy progress rows with a null reading_code`() {
        insertLegacyProgress(gid = "g1", readingCode = null)
        insertLegacyProgress(gid = "g1", readingCode = 2)

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.readingPlanDao().listAllProgressForTest()
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().reading_code)
    }

    @Test
    fun `copy survives multiple legacy progress rows with a null gid without crashing the migration`() {
        // Regression guard for the failure mode Gemini caught on REM-28: if
        // the migration coalesced NULL gids to a placeholder, several such
        // rows would collide on the
        // (reading_plan_progress_gid, reading_code) UNIQUE index and roll
        // the migration back mid-startup. Skip-on-NULL must keep the
        // migration green even with several NULL-keyed rows in the legacy
        // table.
        insertLegacyProgress(gid = null, readingCode = 1)
        insertLegacyProgress(gid = null, readingCode = 2)
        insertLegacyProgress(gid = null, readingCode = 3)
        insertLegacyProgress(gid = "g1", readingCode = 1, checkTime = 99L)

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.readingPlanDao().listAllProgressForTest()
        assertEquals(1, rows.size)
        assertEquals("g1", rows.single().reading_plan_progress_gid)
        assertEquals(1, rows.single().reading_code)
        assertEquals(99L, rows.single().checkTime)
        assertTrue(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyPlan(name = "rp-a", data = byteArrayOf(1))
        insertLegacyProgress(gid = "g1", readingCode = 1)

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.readingPlanDao().listAllForTest().size)
        assertEquals(1, room.readingPlanDao().listAllProgressForTest().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacyPlan(name = "rp-a")

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.readingPlanDao().listAllForTest().single()
        assertTrue("expected positive Room _id, got ${row._id}", row._id > 0)
    }

    @Test
    fun `does not resurrect rows after the user deletes every plan (issue #195)`() {
        // Setup: legacy table has a user plan, mirroring a real upgrade from
        // the pre-Room schema. The legacy row is intentionally preserved as
        // a rollback safety net even after migration.
        insertLegacyPlan(name = "rp-a", data = byteArrayOf(7))
        insertLegacyProgress(gid = "g2:rp_progress:rp-a", readingCode = 1, checkTime = 100L)

        // First launch on the Room build: migration copies it across.
        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals(1, room.readingPlanDao().listAllForTest().size)
        assertEquals(1, room.readingPlanDao().listAllProgressForTest().size)

        // The user deletes the plan. The legacy table in AlkitabDb is
        // deliberately not touched by any write path.
        room.clearAllTables()
        assertTrue(room.readingPlanDao().listAllForTest().isEmpty())
        assertTrue(room.readingPlanDao().listAllProgressForTest().isEmpty())

        // Next launch: migration runs again. With a count-based gate it
        // would re-copy the legacy row. The flag-based gate must be a strict
        // no-op.
        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(
            "legacy reading_plan row was resurrected",
            room.readingPlanDao().listAllForTest().isEmpty(),
        )
        assertTrue(
            "legacy reading_plan_progress row was resurrected",
            room.readingPlanDao().listAllProgressForTest().isEmpty(),
        )
    }

    @Test
    fun `successful copy sets the persistent done flag`() {
        insertLegacyPlan(name = "rp-a")

        assertFalse(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
    }

    @Test
    fun `fresh install with no legacy data also sets the done flag`() {
        // No legacy rows inserted. The migration has nothing to copy but
        // must still mark itself done so subsequent launches don't keep
        // querying the legacy DB.
        assertFalse(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but Room already has rows sets the flag without re-copying`() {
        // Simulates a user who somehow ended up with rows in Room but no
        // done flag set. The migration must NOT re-insert the legacy rows
        // on top of the existing Room state.
        room.readingPlanDao().insert(
            ReadingPlanEntity(
                _id = 0L,
                version = 1,
                name = "already-migrated",
                title = "t",
                description = "d",
                duration = 30,
                startTime = 1L,
                data = byteArrayOf(),
            ),
        )
        insertLegacyPlan(name = "legacy", data = byteArrayOf(9))

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.readingPlanDao().listAllForTest()
        assertEquals(1, rows.size)
        assertEquals("already-migrated", rows.single().name)
        assertTrue(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but only progress rows are present still bails without re-copying`() {
        // Edge case: a previous build successfully copied the progress
        // table only (perhaps the user's legacy ReadingPlan table was empty
        // but the progress table had rows from a previous install). The
        // upgrade-path branch must trigger off either table being non-empty
        // — `countAll() > 0 || countAllProgress() > 0` — so the flag still
        // gets set and the migration stays a no-op.
        room.readingPlanDao().insertOrReplaceProgress(
            ReadingPlanProgressEntity(
                _id = 0L,
                reading_plan_progress_gid = "g1",
                reading_code = 1,
                checkTime = 0L,
            ),
        )
        insertLegacyPlan(name = "legacy")

        ReadingPlanDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertNotNull(room.readingPlanDao())
        assertTrue(room.readingPlanDao().listAllForTest().isEmpty())
        assertTrue(Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false))
    }
}
