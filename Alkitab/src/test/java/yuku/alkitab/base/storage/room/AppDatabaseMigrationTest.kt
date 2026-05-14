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
import org.junit.Assert.assertTrue
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
        // the entity annotations and applies the registered migrations on the
        // way from v1 to the current version. Mismatches throw at this point.
        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
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
     * REM-10: v1 → v2 adds the `marker`, `label`, and `marker_label` tables
     * to [AppDatabase]. The migration must:
     *
     * - Preserve every row already in the v1 `version` table.
     * - Create the three new tables with the schema Room's entity
     *   definitions emit (verified by [MigrationTestHelper.runMigrationsAndValidate]
     *   when `validateDroppedTables = true`).
     * - Leave the new tables empty (data copy is a separate concern handled
     *   by [MarkerDataMigration]).
     */
    @Test
    fun migrates1To2() {
        // Seed a v1 row so we can verify the migration doesn't drop it.
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

        // Run the migration; `validateDroppedTables = true` asks Room to
        // verify the migrated schema matches what its entity definitions
        // expect — including the three new tables and all their indexes.
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            // v1 row survived
            db.query("SELECT longName, ordering FROM version WHERE filename = '/data/a.yes'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("King James", c.getString(0))
                assertEquals(101, c.getInt(1))
            }
            // The three new tables exist
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('marker', 'label', 'marker_label') ORDER BY name").use { c ->
                val tables = mutableListOf<String>()
                while (c.moveToNext()) tables += c.getString(0)
                assertEquals(listOf("label", "marker", "marker_label"), tables)
            }
            // … and are empty
            db.query("SELECT COUNT(*) FROM marker").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM label").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM marker_label").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            // REM-10 follow-up: the (kind, caption) index must be created with
            // COLLATE NOCASE so caption-sorted bookmark queries can do an
            // indexed walk instead of an in-memory sort. Room's @Index can't
            // express per-column collation, so the migration writes raw SQL.
            db.query(
                "SELECT sql FROM sqlite_master WHERE type='index' AND name=?",
                arrayOf(AppDatabase.MARKER_KIND_CAPTION_INDEX_NAME),
            ).use { c ->
                assertTrue(
                    "${AppDatabase.MARKER_KIND_CAPTION_INDEX_NAME} not found in sqlite_master",
                    c.moveToFirst(),
                )
                val sql = c.getString(0)
                assertTrue(
                    "Expected COLLATE NOCASE in index SQL: $sql",
                    sql.uppercase().contains("NOCASE"),
                )
            }
        }
    }

    /**
     * v2 → v3 adds the `devotion` table. The migration must:
     *
     * - Preserve every row already in the v2 tables (`version`, `marker`,
     *   `label`, `marker_label`).
     * - Create the `devotion` table with the schema Room's entity definitions
     *   emit (verified by [MigrationTestHelper.runMigrationsAndValidate] when
     *   `validateDroppedTables = true`).
     * - Leave the new table empty (data copy is a separate concern handled
     *   by [DevotionDataMigration]).
     */
    @Test
    fun migrates2To3() {
        // Seed a v2 row in the `version` table so we can verify the migration
        // doesn't drop pre-existing data. Apply v1 → v2 first so the v2
        // tables exist.
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
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

        // Run v2 → v3.
        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            // v2 row survived
            db.query("SELECT longName FROM version WHERE filename = '/data/a.yes'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("King James", c.getString(0))
            }
            // The new table exists
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name = 'devotion'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("devotion", c.getString(0))
            }
            // … and is empty
            db.query("SELECT COUNT(*) FROM devotion").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            // Both indexes were created
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='devotion' ORDER BY name",
            ).use { c ->
                val indexes = mutableListOf<String>()
                while (c.moveToNext()) indexes += c.getString(0)
                assertTrue(
                    "expected index_devotion_name_date_dataFormatVersion among $indexes",
                    indexes.contains("index_devotion_name_date_dataFormatVersion"),
                )
                assertTrue(
                    "expected index_devotion_touchTime among $indexes",
                    indexes.contains("index_devotion_touchTime"),
                )
            }
        }
    }

    /**
     * The post-migration schema must round-trip a row through every new
     * entity. Catches drift between the [AppDatabase.MIGRATION_1_2] SQL and
     * the entity declarations (e.g. forgetting an index, getting a column
     * type wrong).
     */
    @Test
    fun `after migrating from v1 to v2 Room opens cleanly and round-trips a marker through the new DAOs`() {
        helper.createDatabase(TEST_DB, 1).close()

        // Open via Room — this triggers schema validation. If the migrated
        // schema doesn't match what Room's entities expect, the open throws.
        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        AppDatabase.setForTesting(room)

        try {
            val id = room.markerDao().insert(
                yuku.alkitab.base.storage.room.MarkerEntity(
                    _id = 0L,
                    gid = "g1",
                    ari = 100,
                    kind = 1,
                    caption = "c",
                    verseCount = 1,
                    createTime = 0,
                    modifyTime = 0,
                ),
            )
            assertTrue(id > 0)
            val loaded = room.markerDao().findByGid("g1")
            assertNotNull(loaded)
            assertEquals(100, loaded!!.ari)
        } finally {
            room.close()
        }
    }

    /**
     * v3 → v4 adds the `per_version` table. The migration must:
     *
     * - Preserve every row already in the v3 tables (`version`, `marker`,
     *   `label`, `marker_label`, `devotion`).
     * - Create the `per_version` table with the schema Room's entity
     *   definitions emit (verified by [MigrationTestHelper.runMigrationsAndValidate]
     *   when `validateDroppedTables = true`).
     * - Leave the new table empty (data copy is a separate concern handled
     *   by [PerVersionDataMigration]).
     */
    @Test
    fun migrates3To4() {
        // Seed a v3 row in the `devotion` table so we can verify the
        // migration doesn't drop pre-existing data. Walk v1 → v2 → v3 first
        // so the v3 tables exist.
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            val cv = ContentValues().apply {
                put("name", "RH")
                put("date", "2026-05-14")
                put("body", "body")
                put("readyToUse", 1)
                put("touchTime", 1_700_000_000)
                put("dataFormatVersion", 1)
            }
            db.insert("devotion", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
        }

        // Run v3 → v4.
        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            // v3 row survived
            db.query("SELECT body FROM devotion WHERE name = 'RH' AND date = '2026-05-14'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("body", c.getString(0))
            }
            // The new table exists
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name = 'per_version'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("per_version", c.getString(0))
            }
            // … and is empty
            db.query("SELECT COUNT(*) FROM per_version").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            // The unique index on `versionId` was created
            db.query(
                "SELECT sql FROM sqlite_master WHERE type='index' AND name='index_per_version_versionId'",
            ).use { c ->
                assertTrue(
                    "index_per_version_versionId not found in sqlite_master",
                    c.moveToFirst(),
                )
                val sql = c.getString(0)
                assertTrue(
                    "Expected UNIQUE in index SQL: $sql",
                    sql.uppercase().contains("UNIQUE"),
                )
            }
        }
    }

    /**
     * Companion to the v1→v2 marker round-trip test: verify that after
     * v1 → v3 (i.e. both migrations applied in sequence) Room can open the
     * DB and round-trip a row through the new [DevotionEntity] DAO. Catches
     * drift between [AppDatabase.MIGRATION_2_3] and the entity declaration.
     */
    @Test
    fun `after migrating from v1 to v3 Room opens cleanly and round-trips a devotion through the new DAO`() {
        helper.createDatabase(TEST_DB, 1).close()

        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        AppDatabase.setForTesting(room)

        try {
            val id = room.devotionDao().insert(
                DevotionEntity(
                    _id = 0L,
                    name = "RH",
                    date = "2026-05-14",
                    body = "body",
                    readyToUse = 1,
                    touchTime = 1_700_000_000,
                    dataFormatVersion = 1,
                ),
            )
            assertTrue(id > 0)
            val loaded = room.devotionDao()
                .findByNameDateAndDataFormatVersion("RH", "2026-05-14", 1)
            assertNotNull(loaded)
            assertEquals("body", loaded!!.body)
        } finally {
            room.close()
        }
    }

    /**
     * Companion to the v1→v3 devotion round-trip test: verify that after
     * v1 → v4 (i.e. all three migrations applied in sequence) Room can open
     * the DB and round-trip a row through the new [PerVersionEntity] DAO.
     * Catches drift between [AppDatabase.MIGRATION_3_4] and the entity
     * declaration.
     */
    @Test
    fun `after migrating from v1 to v4 Room opens cleanly and round-trips a per_version row through the new DAO`() {
        helper.createDatabase(TEST_DB, 1).close()

        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        )
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        AppDatabase.setForTesting(room)

        try {
            val id = room.perVersionDao().insert(
                PerVersionEntity(
                    _id = 0L,
                    versionId = "preset/kjv",
                    settings = """{"fontSizeMultiplier":1.5}""",
                ),
            )
            assertTrue(id > 0)
            val loaded = room.perVersionDao().findByVersionId("preset/kjv")
            assertNotNull(loaded)
            assertEquals("""{"fontSizeMultiplier":1.5}""", loaded!!.settings)
        } finally {
            room.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
