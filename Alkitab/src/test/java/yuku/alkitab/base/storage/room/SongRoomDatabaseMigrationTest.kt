package yuku.alkitab.base.storage.room

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema-level migration test scaffold for [SongRoomDatabase].
 *
 * **Why this exists, even though there's currently only one schema version:**
 * the first time someone bumps `@Database(version = ...)` on
 * [SongRoomDatabase] (e.g. for REM-21 swapping the `data` BLOB payload
 * from Parcelable to JSON), the "did the migration work?" question is hard
 * to answer by inspection. This test file is the template that future PR
 * pre-fills with a real assertion.
 *
 * **Pattern for adding a future migration (e.g. version 1 -> 2):**
 * 1. Update [SongRoomDatabase] to `@Database(version = 2, ...)`.
 * 2. Define `val MIGRATION_1_2 = object : Migration(1, 2) { ... }` on the
 *    `SongRoomDatabase` companion.
 * 3. Add a test below modelled on [v1 schema JSON is shippable…] that
 *    calls `helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)`
 *    and asserts on the post-migration shape.
 *
 * Schemas are emitted by KSP to `Alkitab/schemas/` (configured in
 * `build.gradle.kts`'s `ksp { arg("room.schemaLocation", ...) }`) and
 * mounted into both the `androidTest` and `test` asset paths so this
 * scaffold works under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SongRoomDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SongRoomDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun tearDown() {
        SongRoomDatabase.setForTesting(null)
    }

    /**
     * Sanity check: the v1 schema JSON exists, is valid, and matches what
     * [SongRoomDatabase] expects when opened by Room.
     *
     * If this test ever fails after a non-version-bumping change to
     * [SongInfoEntity] or [SongBookInfoEntity], the change *did* alter the
     * schema and either:
     * - the change is intentional → bump `@Database(version = ...)` and
     *   add a `Migration`, or
     * - the change is a typo → revert it.
     */
    @Test
    fun `v1 schema JSON is shippable and round-trips a row through MigrationTestHelper`() {
        // Create a v1 DB using the on-disk schema JSON, write a row via the
        // SupportSQLite handle, close — simulating "user is on the v1 build".
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO song_info (bookName, code, title, title_original, ordering, dataFormatVersion, data, updateTime) " +
                    "VALUES ('NKB', '001', 'Hymn 1', 'Original 1', 1, 3, X'10', 1700000000)",
            )
            db.execSQL(
                "INSERT INTO song_book_info (name, title, copyright) " +
                    "VALUES ('NKB', 'Buku Nyanyian', '©NKB')",
            )
        }

        // Now open the DB the normal way — Room validates the schema against
        // the entity annotations. Mismatches throw at this point.
        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SongRoomDatabase::class.java,
            TEST_DB,
        )
            .allowMainThreadQueries()
            .build()
        SongRoomDatabase.setForTesting(room)

        try {
            val songRow = room.songRoomDao().findSongInfoByBookNameAndCode("NKB", "001")
            assertNotNull("row written before Room opened should survive validation", songRow)
            assertEquals("Hymn 1", songRow!!.title)
            assertEquals(3, songRow.dataFormatVersion)
            assertArrayEquals(byteArrayOf(0x10), songRow.data)

            val bookRow = room.songRoomDao().findSongBookInfoByName("NKB")
            assertNotNull(bookRow)
            assertEquals("Buku Nyanyian", bookRow!!.title)
            assertEquals("©NKB", bookRow.copyright)

            // And the (bookName, code) and (bookName, ordering) indexes are
            // both present in sqlite_master (Room validates this internally
            // already, but explicit assertions catch silent renames).
            room.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='song_info'",
            ).use { c ->
                val indexes = mutableListOf<String>()
                while (c.moveToNext()) indexes += c.getString(0)
                assertTrue(
                    "expected index_song_info_bookName_code among $indexes",
                    indexes.contains("index_song_info_bookName_code"),
                )
                assertTrue(
                    "expected index_song_info_bookName_ordering among $indexes",
                    indexes.contains("index_song_info_bookName_ordering"),
                )
            }
            room.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='song_book_info'",
            ).use { c ->
                val indexes = mutableListOf<String>()
                while (c.moveToNext()) indexes += c.getString(0)
                assertTrue(
                    "expected index_song_book_info_name among $indexes",
                    indexes.contains("index_song_book_info_name"),
                )
            }
        } finally {
            room.close()
        }
    }

    private companion object {
        const val TEST_DB = "song-migration-test"
    }
}
