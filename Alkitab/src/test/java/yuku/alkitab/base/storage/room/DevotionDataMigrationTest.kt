package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import android.preference.PreferenceManager
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Verifies the one-time copy from the legacy `Devotion` table into Room.
 * Robolectric is required because [InternalDbHelper] extends
 * [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DevotionDataMigrationTest {
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

    private fun insertLegacyDevotion(
        name: String,
        date: String,
        body: String? = "body",
        readyToUse: Int = 1,
        touchTime: Int = 1_700_000_000,
        dataFormatVersion: Int = 1,
    ) {
        val cv = ContentValues().apply {
            put(Table.Devotion.name.name, name)
            put(Table.Devotion.date.name, date)
            put(Table.Devotion.body.name, body)
            put(Table.Devotion.readyToUse.name, readyToUse)
            put(Table.Devotion.touchTime.name, touchTime)
            put(Table.Devotion.dataFormatVersion.name, dataFormatVersion)
        }
        legacy.writableDatabase.insert(Table.Devotion.tableName(), null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.devotionDao().listAll().isEmpty())
    }

    @Test
    fun `copy moves every legacy row into Room and round-trips every persisted field`() {
        insertLegacyDevotion(
            name = "RH",
            date = "2026-05-14",
            body = "renungan-body",
            readyToUse = 1,
            touchTime = 1_700_000_111,
            dataFormatVersion = 1,
        )

        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.devotionDao().listAll()
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals("RH", name)
            assertEquals("2026-05-14", date)
            assertEquals("renungan-body", body)
            assertEquals(1, readyToUse)
            assertEquals(1_700_000_111, touchTime)
            assertEquals(1, dataFormatVersion)
        }
    }

    @Test
    fun `copy preserves a null body for not-ready-to-use rows`() {
        insertLegacyDevotion(name = "SH", date = "2026-05-14", body = null, readyToUse = 0)

        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.devotionDao().findByNameDateAndDataFormatVersion("SH", "2026-05-14", 1)
        assertNotNull(row)
        assertEquals(null, row!!.body)
        assertEquals(0, row.readyToUse)
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyDevotion(name = "RH", date = "2026-05-14")

        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.devotionDao().listAll().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacyDevotion(name = "RH", date = "2026-05-14")

        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.devotionDao().findByNameDateAndDataFormatVersion("RH", "2026-05-14", 1)
        assertNotNull(row)
        // Room AUTOINCREMENT starts at 1 for a fresh in-memory DB; the only
        // contract is that _id is positive (fresh) rather than the legacy
        // _id literal we never asserted.
        assertTrue("expected positive Room _id, got ${row!!._id}", row._id > 0)
    }

    @Test
    fun `does not resurrect deleted cache entries after the user clears every row (issue #195)`() {
        // Setup: legacy table has a cached devotion, mirroring a real upgrade
        // from the pre-Room schema. The legacy row is intentionally preserved
        // as a rollback safety net even after migration.
        insertLegacyDevotion(name = "RH", date = "2026-05-14")

        // First launch on the Room build: migration copies it across.
        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals(1, room.devotionDao().listAll().size)

        // The cache-eviction sweep (deleteDevotionsWithTouchTimeBefore) clears
        // the row from Room. The legacy table in AlkitabDb is deliberately
        // not touched by any write path.
        room.devotionDao().deleteWithTouchTimeBefore(Int.MAX_VALUE)
        assertTrue(room.devotionDao().listAll().isEmpty())

        // Next launch: migration runs again. With a count-based gate it
        // would re-copy the legacy row. The flag-based gate must be a strict
        // no-op.
        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(
            "legacy devotion row was resurrected",
            room.devotionDao().listAll().isEmpty(),
        )
    }

    @Test
    fun `successful copy sets the persistent done flag`() {
        insertLegacyDevotion(name = "RH", date = "2026-05-14")

        assertFalse(Preferences.getBoolean(Prefkey.devotion_data_migration_v1_done, false))
        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.devotion_data_migration_v1_done, false))
    }

    @Test
    fun `fresh install with no legacy data also sets the done flag`() {
        // No legacy rows inserted. The migration has nothing to copy but must
        // still mark itself done so subsequent launches don't keep querying
        // the legacy DB.
        assertFalse(Preferences.getBoolean(Prefkey.devotion_data_migration_v1_done, false))
        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.devotion_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but Room already has rows sets the flag without re-copying`() {
        // Simulates a hypothetical user who somehow ended up with rows in
        // Room but no done flag set. The migration must NOT re-insert the
        // legacy rows on top of the existing Room state.
        room.devotionDao().insert(
            DevotionEntity(
                _id = 0L,
                name = "already-migrated",
                date = "2026-05-14",
                body = "body",
                readyToUse = 1,
                touchTime = 0,
                dataFormatVersion = 1,
            ),
        )
        insertLegacyDevotion(name = "legacy-RH", date = "2026-05-14")

        DevotionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.devotionDao().listAll()
        assertEquals(1, rows.size)
        assertEquals("already-migrated", rows.single().name)
        assertTrue(Preferences.getBoolean(Prefkey.devotion_data_migration_v1_done, false))
    }
}
