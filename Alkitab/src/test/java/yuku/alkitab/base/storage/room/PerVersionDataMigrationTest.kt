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
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.Table

/**
 * Verifies the one-time copy from the legacy `PerVersion` table into Room.
 * Robolectric is required because [InternalDbHelper] extends
 * [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PerVersionDataMigrationTest {
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

    private fun insertLegacyPerVersion(versionId: String?, settings: String?) {
        val cv = ContentValues().apply {
            put(Table.PerVersion.versionId.name, versionId)
            put(Table.PerVersion.settings.name, settings)
        }
        legacy.writableDatabase.insert(Table.PerVersion.tableName(), null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.perVersionDao().listAll().isEmpty())
    }

    @Test
    fun `copy moves every legacy row into Room and round-trips every persisted field`() {
        insertLegacyPerVersion("preset/kjv", """{"fontSizeMultiplier":1.5}""")
        insertLegacyPerVersion("preset/asv", """{"fontSizeMultiplier":0.9}""")

        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val byId = room.perVersionDao().listAll().associateBy { it.versionId }
        assertEquals(2, byId.size)
        assertEquals("""{"fontSizeMultiplier":1.5}""", byId.getValue("preset/kjv").settings)
        assertEquals("""{"fontSizeMultiplier":0.9}""", byId.getValue("preset/asv").settings)
    }

    @Test
    fun `copy preserves a null settings blob`() {
        insertLegacyPerVersion("preset/kjv", null)

        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.perVersionDao().findByVersionId("preset/kjv")
        assertNotNull(row)
        assertNull(row!!.settings)
    }

    @Test
    fun `copy coalesces a null legacy versionId to the empty string`() {
        // The legacy schema permits NULL versionId; Room enforces non-null on
        // that column. PerVersionDataMigration coalesces NULL to "" rather
        // than dropping the row silently — these rows are unreachable by the
        // facade's keyed lookups anyway, but a placeholder preserves them
        // for any future audit.
        insertLegacyPerVersion(null, """{"fontSizeMultiplier":1.0}""")

        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.perVersionDao().listAll()
        assertEquals(1, rows.size)
        assertEquals("", rows.single().versionId)
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyPerVersion("preset/kjv", """{"fontSizeMultiplier":1.0}""")

        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.perVersionDao().listAll().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacyPerVersion("preset/kjv", """{"fontSizeMultiplier":1.0}""")

        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.perVersionDao().findByVersionId("preset/kjv")
        assertNotNull(row)
        assertTrue("expected positive Room _id, got ${row!!._id}", row._id > 0)
    }

    @Test
    fun `does not resurrect rows after the user clears every per-version settings entry (issue #195)`() {
        // Setup: legacy table has a settings row, mirroring a real upgrade
        // from the pre-Room schema. The legacy row is intentionally preserved
        // as a rollback safety net even after migration.
        insertLegacyPerVersion("preset/kjv", """{"fontSizeMultiplier":1.5}""")

        // First launch on the Room build: migration copies it across.
        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals(1, room.perVersionDao().listAll().size)

        // The user (or a future "reset settings" path) clears the row from
        // Room. The legacy table in AlkitabDb is deliberately not touched by
        // any write path.
        room.clearAllTables()
        assertTrue(room.perVersionDao().listAll().isEmpty())

        // Next launch: migration runs again. With a count-based gate it
        // would re-copy the legacy row. The flag-based gate must be a strict
        // no-op.
        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(
            "legacy per_version row was resurrected",
            room.perVersionDao().listAll().isEmpty(),
        )
    }

    @Test
    fun `successful copy sets the persistent done flag`() {
        insertLegacyPerVersion("preset/kjv", """{"fontSizeMultiplier":1.0}""")

        assertFalse(Preferences.getBoolean(Prefkey.per_version_data_migration_v1_done, false))
        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.per_version_data_migration_v1_done, false))
    }

    @Test
    fun `fresh install with no legacy data also sets the done flag`() {
        // No legacy rows inserted. The migration has nothing to copy but must
        // still mark itself done so subsequent launches don't keep querying
        // the legacy DB.
        assertFalse(Preferences.getBoolean(Prefkey.per_version_data_migration_v1_done, false))
        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.per_version_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but Room already has rows sets the flag without re-copying`() {
        // Simulates a user who somehow ended up with rows in Room but no done
        // flag set. The migration must NOT re-insert the legacy rows on top
        // of the existing Room state.
        room.perVersionDao().insert(
            PerVersionEntity(
                _id = 0L,
                versionId = "preset/already-migrated",
                settings = """{"fontSizeMultiplier":1.0}""",
            ),
        )
        insertLegacyPerVersion("preset/legacy", """{"fontSizeMultiplier":2.0}""")

        PerVersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.perVersionDao().listAll()
        assertEquals(1, rows.size)
        assertEquals("preset/already-migrated", rows.single().versionId)
        assertTrue(Preferences.getBoolean(Prefkey.per_version_data_migration_v1_done, false))
    }
}
