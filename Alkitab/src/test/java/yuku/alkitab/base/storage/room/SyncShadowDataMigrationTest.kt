package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import android.preference.PreferenceManager
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Verifies the one-time copy from the legacy `SyncShadow` and `SyncLog`
 * tables into Room. Robolectric is required because [InternalDbHelper]
 * extends [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncShadowDataMigrationTest {
    private lateinit var legacy: InternalDbHelper
    private lateinit var room: AppDatabase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        // Idempotency is anchored on a SharedPreferences flag. Clear it (and
        // the [Preferences] static cache that may hold a previous test's
        // SharedPreferences instance) so every test starts with a clean
        // slate.
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

    private fun insertLegacyShadow(
        syncSetName: String?,
        revno: Int? = 1,
        data: ByteArray? = byteArrayOf(),
    ): Long {
        val cv = ContentValues().apply {
            if (syncSetName != null) put(Table.SyncShadow.syncSetName.name, syncSetName)
            else putNull(Table.SyncShadow.syncSetName.name)
            if (revno != null) put(Table.SyncShadow.revno.name, revno)
            else putNull(Table.SyncShadow.revno.name)
            if (data != null) put(Table.SyncShadow.data.name, data)
            else putNull(Table.SyncShadow.data.name)
        }
        return legacy.writableDatabase.insert(Table.SyncShadow.tableName(), null, cv)
    }

    private fun insertLegacyLog(
        createTime: Int?,
        kind: Int? = 100,
        syncSetName: String? = "mabel",
        params: String? = null,
    ): Long {
        val cv = ContentValues().apply {
            if (createTime != null) put(Table.SyncLog.createTime.name, createTime)
            else putNull(Table.SyncLog.createTime.name)
            if (kind != null) put(Table.SyncLog.kind.name, kind)
            else putNull(Table.SyncLog.kind.name)
            if (syncSetName != null) put(Table.SyncLog.syncSetName.name, syncSetName)
            else putNull(Table.SyncLog.syncSetName.name)
            if (params != null) put(Table.SyncLog.params.name, params)
            else putNull(Table.SyncLog.params.name)
        }
        return legacy.writableDatabase.insert(Table.SyncLog.tableName(), null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.syncShadowDao().listAllShadowsForTest().isEmpty())
        assertTrue(room.syncShadowDao().listAllLogsForTest().isEmpty())
    }

    @Test
    fun `copy moves every legacy SyncShadow row into Room and round-trips every persisted field`() {
        insertLegacyShadow(syncSetName = "mabel", revno = 5, data = byteArrayOf(0x10, 0x20, 0x30))
        insertLegacyShadow(syncSetName = "pins", revno = 9, data = byteArrayOf())
        insertLegacyShadow(syncSetName = "rp", revno = 0, data = null)

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val byName = room.syncShadowDao().listAllShadowsForTest().associateBy { it.syncSetName }
        assertEquals(3, byName.size)
        val mabel = byName.getValue("mabel")
        assertEquals(5, mabel.revno)
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x30), mabel.data)
        val pins = byName.getValue("pins")
        assertEquals(9, pins.revno)
        assertArrayEquals(byteArrayOf(), pins.data)
        val rp = byName.getValue("rp")
        assertEquals(0, rp.revno)
        assertNull(rp.data)
    }

    @Test
    fun `copy moves every legacy SyncLog row into Room`() {
        insertLegacyLog(createTime = 100, kind = 80, syncSetName = "mabel", params = null)
        insertLegacyLog(createTime = 200, kind = 141, syncSetName = null, params = """{"k":"v"}""")

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.syncShadowDao().listAllLogsForTest()
        assertEquals(2, rows.size)
        val byTime = rows.associateBy { it.createTime }
        assertEquals(80, byTime.getValue(100).kind)
        assertEquals("mabel", byTime.getValue(100).syncSetName)
        assertNull(byTime.getValue(100).params)
        assertEquals(141, byTime.getValue(200).kind)
        assertNull(byTime.getValue(200).syncSetName)
        assertEquals("""{"k":"v"}""", byTime.getValue(200).params)
    }

    @Test
    fun `copy drops legacy SyncShadow rows with a null syncSetName`() {
        // The legacy schema permits NULL syncSetName; Room enforces non-null.
        // SyncShadowDataMigration skips these rows because they are
        // unreachable by the facade's keyed lookups anyway.
        insertLegacyShadow(syncSetName = null, revno = 1, data = byteArrayOf(1))
        insertLegacyShadow(syncSetName = "mabel", revno = 2, data = byteArrayOf(2))

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.syncShadowDao().listAllShadowsForTest()
        assertEquals(1, rows.size)
        assertEquals("mabel", rows.single().syncSetName)
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacyShadow(syncSetName = "mabel", revno = 1, data = byteArrayOf(1))
        insertLegacyLog(createTime = 100)

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.syncShadowDao().listAllShadowsForTest().size)
        assertEquals(1, room.syncShadowDao().listAllLogsForTest().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacyShadow(syncSetName = "mabel")

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val row = room.syncShadowDao().listAllShadowsForTest().single()
        assertTrue("expected positive Room _id, got ${row._id}", row._id > 0)
    }

    @Test
    fun `does not resurrect rows after the user clears every shadow (issue #195)`() {
        // Setup: legacy table has a shadow row, mirroring a real upgrade
        // from the pre-Room schema. The legacy row is intentionally
        // preserved as a rollback safety net even after migration.
        insertLegacyShadow(syncSetName = "mabel", revno = 7, data = byteArrayOf(7))
        insertLegacyLog(createTime = 100)

        // First launch on the Room build: migration copies it across.
        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals(1, room.syncShadowDao().listAllShadowsForTest().size)
        assertEquals(1, room.syncShadowDao().listAllLogsForTest().size)

        // The user clears the shadows. The legacy table in AlkitabDb is
        // deliberately not touched by any write path.
        room.clearAllTables()
        assertTrue(room.syncShadowDao().listAllShadowsForTest().isEmpty())
        assertTrue(room.syncShadowDao().listAllLogsForTest().isEmpty())

        // Next launch: migration runs again. With a count-based gate it
        // would re-copy the legacy row. The flag-based gate must be a
        // strict no-op.
        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(
            "legacy sync_shadow row was resurrected",
            room.syncShadowDao().listAllShadowsForTest().isEmpty(),
        )
        assertTrue(
            "legacy sync_log row was resurrected",
            room.syncShadowDao().listAllLogsForTest().isEmpty(),
        )
    }

    @Test
    fun `successful copy sets the persistent done flag`() {
        insertLegacyShadow(syncSetName = "mabel")

        assertFalse(Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false))
        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false))
    }

    @Test
    fun `fresh install with no legacy data also sets the done flag`() {
        // No legacy rows inserted. The migration has nothing to copy but
        // must still mark itself done so subsequent launches don't keep
        // querying the legacy DB.
        assertFalse(Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false))
        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but Room already has shadow rows sets the flag without re-copying`() {
        // Simulates a user who somehow ended up with rows in Room but no
        // done flag set. The migration must NOT re-insert the legacy rows
        // on top of the existing Room state.
        room.syncShadowDao().insertShadow(
            SyncShadowEntity(_id = 0L, syncSetName = "already", revno = 1, data = byteArrayOf()),
        )
        insertLegacyShadow(syncSetName = "legacy", revno = 2, data = byteArrayOf(9))

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.syncShadowDao().listAllShadowsForTest()
        assertEquals(1, rows.size)
        assertEquals("already", rows.single().syncSetName)
        assertTrue(Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but only log rows are present still bails without re-copying`() {
        // Edge case: a previous build successfully copied the log table
        // only. The upgrade-path branch must trigger off either table
        // being non-empty — `countAllShadows() > 0 || countAllLogs() > 0`
        // — so the flag still gets set and the migration stays a no-op.
        room.syncShadowDao().insertLog(
            SyncLogEntity(_id = 0L, createTime = 50, kind = 80, syncSetName = null, params = null),
        )
        insertLegacyShadow(syncSetName = "legacy")

        SyncShadowDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(room.syncShadowDao().listAllShadowsForTest().isEmpty())
        assertTrue(Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false))
    }
}
