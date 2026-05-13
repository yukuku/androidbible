package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema-level migration test scaffold for [AppDatabase].
 *
 * **Why this exists, even though there's currently only one schema version:**
 * the first time someone bumps `@Database(version = ...)` and adds a
 * `Migration` (e.g. to migrate the next legacy table off SQLite), the
 * "did the migration work?" question is hard to answer by inspection. This
 * test file is the template that future PR pre-fills with a real assertion.
 *
 * **Pattern for adding a future migration (e.g. version 1 -> 2):**
 * 1. Update [AppDatabase] to `@Database(version = 2, ...)`.
 * 2. Define `val MIGRATION_1_2 = object : Migration(1, 2) { ... }`.
 * 3. Add a test below modelled on [migratesFromV1ToV2_exampleScaffold]:
 *    ```kotlin
 *    @Test
 *    fun migrates1To2() {
 *        helper.createDatabase(TEST_DB, 1).use { db ->
 *            db.execSQL("INSERT INTO version (filename, ordering, active, modifyTime) " +
 *                "VALUES ('/a.yes', 101, 1, 0)")
 *        }
 *        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
 *            // assert post-migration shape, e.g.:
 *            db.query("SELECT new_column FROM version WHERE filename = '/a.yes'").use { c ->
 *                assertTrue(c.moveToFirst())
 *                assertEquals("expected", c.getString(0))
 *            }
 *        }
 *    }
 *    ```
 *
 * Schemas are emitted by KSP to `Alkitab/schemas/` (configured in
 * `build.gradle.kts`'s `ksp { arg("room.schemaLocation", ...) }`) and mounted
 * into both the `androidTest` and `test` asset paths so this scaffold works
 * under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun tearDown() {
        AppDatabase.setForTesting(null)
    }

    /**
     * Sanity check: the v1 schema JSON exists, is valid, and matches what
     * [AppDatabase] expects when opened by Room.
     *
     * If this test ever fails after a non-version-bumping change to
     * [VersionEntity], the change *did* alter the schema and either:
     * - the change is intentional → bump `@Database(version = ...)` and add a
     *   `Migration`, or
     * - the change is a typo → revert it.
     */
    @Test
    fun `v1 schema JSON is shippable and round-trips a row through MigrationTestHelper`() {
        // Create a v1 DB using the on-disk schema JSON, write a row via raw SQL,
        // close — simulating "user is on the v1 build".
        helper.createDatabase(TEST_DB, 1).use { db ->
            val cv = ContentValues().apply {
                put("filename", "/data/a.yes")
                put("preset_name", "kjv")
                put("locale", "en")
                put("shortName", "KJV")
                put("longName", "King James")
                put("description", "desc")
                put("modifyTime", 1_700_000_000)
                put("active", 1)
                put("ordering", 101)
            }
            db.insert("version", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
        }

        // Now open the DB the normal way — Room validates the schema against
        // [VersionEntity]'s annotations. Mismatches throw at this point.
        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .allowMainThreadQueries()
            .build()
        AppDatabase.setForTesting(room)

        try {
            val loaded = room.versionDao().findByFilename("/data/a.yes")
            assertNotNull("row written before Room opened should survive validation", loaded)
            assertEquals(101, loaded!!.ordering)
            assertEquals("kjv", loaded.preset_name)
            assertEquals(1, loaded.active)
        } finally {
            room.close()
        }
    }

    /**
     * Placeholder demonstrating the shape of a v1→v2 migration test. Marked
     * `@org.junit.Ignore` because there is no version 2 yet. Delete this and
     * write a real `migrates1To2` test when bumping `@Database(version = 2)`.
     */
    @org.junit.Ignore("scaffold — see KDoc on the class for how to fill this in when @Database version bumps")
    @Test
    fun migratesFromV1ToV2_exampleScaffold() {
        // helper.createDatabase(TEST_DB, 1).use { db -> /* seed v1 rows */ }
        // helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db -> /* assert v2 shape */ }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
