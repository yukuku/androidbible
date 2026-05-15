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
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.SongDbHelper
import yuku.alkitab.base.storage.Table

/**
 * Verifies the one-time copy from the legacy `SongInfo` and `SongBookInfo`
 * tables in the `SongDb` SQLite file (managed by [SongDbHelper]) into
 * Room's `song_info` / `song_book_info` tables. Robolectric is required
 * because [SongDbHelper] extends [android.database.sqlite.SQLiteOpenHelper].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SongDbDataMigrationTest {
    private lateinit var legacy: SongDbHelper
    private lateinit var room: SongRoomDatabase

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
        legacy = SongDbHelper()
        room = Room.inMemoryDatabaseBuilder(app, SongRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        legacy.close()
    }

    private fun insertLegacySongInfo(
        bookName: String?,
        code: String?,
        title: String? = "t",
        title_original: String? = "to",
        ordering: Int? = 1,
        dataFormatVersion: Int? = 3,
        data: ByteArray? = byteArrayOf(),
        updateTime: Int? = 1_700_000_000,
    ): Long {
        val cv = ContentValues().apply {
            if (bookName != null) put(Table.SongInfo.bookName.name, bookName)
            else putNull(Table.SongInfo.bookName.name)
            if (code != null) put(Table.SongInfo.code.name, code)
            else putNull(Table.SongInfo.code.name)
            if (title != null) put(Table.SongInfo.title.name, title)
            else putNull(Table.SongInfo.title.name)
            if (title_original != null) put(Table.SongInfo.title_original.name, title_original)
            else putNull(Table.SongInfo.title_original.name)
            if (ordering != null) put(Table.SongInfo.ordering.name, ordering)
            else putNull(Table.SongInfo.ordering.name)
            if (dataFormatVersion != null) put(Table.SongInfo.dataFormatVersion.name, dataFormatVersion)
            else putNull(Table.SongInfo.dataFormatVersion.name)
            if (data != null) put(Table.SongInfo.data.name, data)
            else putNull(Table.SongInfo.data.name)
            if (updateTime != null) put(Table.SongInfo.updateTime.name, updateTime)
            else putNull(Table.SongInfo.updateTime.name)
        }
        return legacy.writableDatabase.insert(Table.SongInfo.tableName(), null, cv)
    }

    private fun insertLegacySongBookInfo(
        name: String?,
        title: String? = "t",
        copyright: String? = "©",
    ): Long {
        val cv = ContentValues().apply {
            if (name != null) put(Table.SongBookInfo.name.name, name)
            else putNull(Table.SongBookInfo.name.name)
            if (title != null) put(Table.SongBookInfo.title.name, title)
            else putNull(Table.SongBookInfo.title.name)
            if (copyright != null) put(Table.SongBookInfo.copyright.name, copyright)
            else putNull(Table.SongBookInfo.copyright.name)
        }
        return legacy.writableDatabase.insert(Table.SongBookInfo.tableName(), null, cv)
    }

    @Test
    fun `copy is a no-op when both databases are empty`() {
        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(room.songRoomDao().listAllSongInfos().isEmpty())
        assertTrue(room.songRoomDao().listAllSongBookInfos().isEmpty())
    }

    @Test
    fun `copy moves every legacy SongInfo row into Room and round-trips every persisted field`() {
        insertLegacySongInfo(
            bookName = "NKB",
            code = "001",
            title = "Hymn 1",
            title_original = "Original 1",
            ordering = 1,
            dataFormatVersion = 3,
            data = byteArrayOf(0x10, 0x20, 0x30),
            updateTime = 1_700_000_001,
        )
        insertLegacySongInfo(
            bookName = "NKB",
            code = "002",
            title = "Hymn 2",
            title_original = null,
            ordering = 2,
            dataFormatVersion = 3,
            data = byteArrayOf(),
            updateTime = 1_700_000_002,
        )
        insertLegacySongInfo(
            bookName = "PKJ",
            code = "001",
            data = null,
        )

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.songRoomDao().listAllSongInfos()
        assertEquals(3, rows.size)
        val byKey = rows.associateBy { "${it.bookName}/${it.code}" }
        val r1 = byKey.getValue("NKB/001")
        assertEquals("Hymn 1", r1.title)
        assertEquals("Original 1", r1.title_original)
        assertEquals(1, r1.ordering)
        assertEquals(3, r1.dataFormatVersion)
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x30), r1.data)
        assertEquals(1_700_000_001, r1.updateTime)
        val r2 = byKey.getValue("NKB/002")
        assertEquals("Hymn 2", r2.title)
        assertNull(r2.title_original)
        assertArrayEquals(byteArrayOf(), r2.data)
        val r3 = byKey.getValue("PKJ/001")
        assertNull(r3.data)
    }

    @Test
    fun `copy moves every legacy SongBookInfo row into Room`() {
        insertLegacySongBookInfo(name = "NKB", title = "Buku Nyanyian", copyright = "©NKB")
        insertLegacySongBookInfo(name = "PKJ", title = "Pelengkap Kidung Jemaat", copyright = null)

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.songRoomDao().listAllSongBookInfos()
        assertEquals(2, rows.size)
        val byName = rows.associateBy { it.name }
        assertEquals("Buku Nyanyian", byName.getValue("NKB").title)
        assertEquals("©NKB", byName.getValue("NKB").copyright)
        assertEquals("Pelengkap Kidung Jemaat", byName.getValue("PKJ").title)
        assertNull(byName.getValue("PKJ").copyright)
    }

    @Test
    fun `copy drops legacy SongInfo rows with a null bookName or null code`() {
        // The legacy schema permits NULL for both columns; the facade
        // always writes concrete values, but defence in depth still
        // drops them since they are unreachable by keyed lookups in Room.
        insertLegacySongInfo(bookName = null, code = "001")
        insertLegacySongInfo(bookName = "NKB", code = null)
        insertLegacySongInfo(bookName = "NKB", code = "good")

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.songRoomDao().listAllSongInfos()
        assertEquals(1, rows.size)
        assertEquals("good", rows.single().code)
    }

    @Test
    fun `copy drops legacy SongBookInfo rows with a null name`() {
        insertLegacySongBookInfo(name = null)
        insertLegacySongBookInfo(name = "NKB")

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.songRoomDao().listAllSongBookInfos()
        assertEquals(1, rows.size)
        assertEquals("NKB", rows.single().name)
    }

    @Test
    fun `copy is idempotent — running twice does not duplicate rows`() {
        insertLegacySongInfo(bookName = "NKB", code = "001")
        insertLegacySongBookInfo(name = "NKB")

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(1, room.songRoomDao().listAllSongInfos().size)
        assertEquals(1, room.songRoomDao().listAllSongBookInfos().size)
    }

    @Test
    fun `copy assigns fresh _ids and does not carry over legacy _ids`() {
        insertLegacySongInfo(bookName = "NKB", code = "001")
        insertLegacySongBookInfo(name = "NKB")

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val songRow = room.songRoomDao().listAllSongInfos().single()
        val bookRow = room.songRoomDao().listAllSongBookInfos().single()
        assertTrue("expected positive song_info _id, got ${songRow._id}", songRow._id > 0)
        assertTrue("expected positive song_book_info _id, got ${bookRow._id}", bookRow._id > 0)
    }

    @Test
    fun `does not resurrect rows after the user deletes a song book (issue #195)`() {
        // Setup: legacy SongDb has a song book and its songs, mirroring a
        // real upgrade. The legacy tables are intentionally preserved as a
        // rollback safety net even after migration.
        insertLegacySongBookInfo(name = "NKB", title = "Buku")
        insertLegacySongInfo(bookName = "NKB", code = "001")

        // First launch on the Room build: migration copies it across.
        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals(1, room.songRoomDao().listAllSongInfos().size)
        assertEquals(1, room.songRoomDao().listAllSongBookInfos().size)

        // The user deletes the song book. The legacy SongDb table in the
        // legacy SQLite file is deliberately not touched by any write path.
        room.clearAllTables()
        assertTrue(room.songRoomDao().listAllSongInfos().isEmpty())
        assertTrue(room.songRoomDao().listAllSongBookInfos().isEmpty())

        // Next launch: migration runs again. With a count-based gate it
        // would re-copy the legacy row. The flag-based gate must be a
        // strict no-op.
        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertTrue(
            "legacy song_info row was resurrected",
            room.songRoomDao().listAllSongInfos().isEmpty(),
        )
        assertTrue(
            "legacy song_book_info row was resurrected",
            room.songRoomDao().listAllSongBookInfos().isEmpty(),
        )
    }

    @Test
    fun `successful copy sets the persistent done flag`() {
        insertLegacySongInfo(bookName = "NKB", code = "001")

        assertFalse(Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false))
        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false))
    }

    @Test
    fun `fresh install with no legacy data also sets the done flag`() {
        // No legacy rows inserted. The migration has nothing to copy but
        // must still mark itself done so subsequent launches don't keep
        // querying the legacy DB.
        assertFalse(Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false))
        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertTrue(Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but Room already has song_info rows sets the flag without re-copying`() {
        // Simulates a user who somehow ended up with rows in Room but no
        // done flag set. The migration must NOT re-insert the legacy rows
        // on top of the existing Room state.
        room.songRoomDao().insertSongInfo(
            SongInfoEntity(
                _id = 0L,
                bookName = "already",
                code = "001",
                title = null,
                title_original = null,
                ordering = 1,
                dataFormatVersion = 3,
                data = byteArrayOf(),
                updateTime = 0,
            ),
        )
        insertLegacySongInfo(bookName = "legacy", code = "001")

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        val rows = room.songRoomDao().listAllSongInfos()
        assertEquals(1, rows.size)
        assertEquals("already", rows.single().bookName)
        assertTrue(Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false))
    }

    @Test
    fun `upgrade path - flag unset but only song_book_info rows are present still bails without re-copying`() {
        // Edge case: a previous build successfully copied the book-info
        // table only. The upgrade-path branch must trigger off either
        // table being non-empty — `countAllSongInfos() > 0 || countAllSongBookInfos() > 0`
        // — so the flag still gets set and the migration stays a no-op.
        room.songRoomDao().insertSongBookInfo(
            SongBookInfoEntity(_id = 0L, name = "already", title = null, copyright = null),
        )
        insertLegacySongBookInfo(name = "legacy")

        SongDbDataMigration.copyFromLegacyDbIfNeeded(room, legacy)

        assertEquals(
            listOf("already"),
            room.songRoomDao().listAllSongBookInfos().map { it.name },
        )
        assertTrue(Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false))
    }
}
