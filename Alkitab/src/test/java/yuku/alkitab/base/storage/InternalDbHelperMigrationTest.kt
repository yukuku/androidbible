package yuku.alkitab.base.storage

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
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
import java.io.File

/**
 * Migration tests for [InternalDbHelper.onUpgrade]. Most of these branches were
 * written years ago — pre-Room, pre-DAO-extraction — and the only ongoing
 * verification has been "users on old versions of the app didn't complain after
 * upgrading." That's a thin safety net, especially for the branches that
 * convert user data (Bookmark2 → Marker, Edisi → Version, ReadingPlanProgress
 * re-key). This file exists to keep those branches working as the surrounding
 * code evolves.
 *
 * **How a test works:**
 * 1. [seedLegacyDb] opens the SQLite file at the path `InternalDbHelper` will
 *    use (`databases/AlkitabDb`), runs the seed lambda to install whatever
 *    schema + rows the legacy app would have had, sets `user_version` to
 *    `oldVersion`, and closes the connection.
 * 2. `InternalDbHelper(app)` then opens the same file at the new
 *    `App.getVersionCode()`, sees the lower `user_version`, and runs every
 *    `if (oldVersion ...)` branch that matches.
 * 3. The test asserts on the post-migration state via raw SQL — staying
 *    pre-DAO so the assertions are not coupled to changes in the higher-level
 *    facade.
 *
 * **Test isolation:** every test deletes the `AlkitabDb` file (plus `-shm`/`-wal`
 * sidecars from WAL mode) in [setUp] and [tearDown], so tests don't share state.
 *
 * **Out of scope:** the very oldest branches that pre-date `oldVersion = 142`
 * are exercised implicitly by tests that start from later versions (because
 * the migration code uses cascading `if (oldVersion <= X)` blocks). Pure
 * "create new table" branches are covered by [allSchemaTablesExist].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class InternalDbHelperMigrationTest {
    private lateinit var app: Application
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        dbFile = app.getDatabasePath("AlkitabDb")
        dbFile.parentFile?.mkdirs()
        deleteDbFiles()
    }

    @After
    fun tearDown() {
        deleteDbFiles()
    }

    private fun deleteDbFiles() {
        // Platform helper that cleans the main file plus every SQLite sidecar
        // (`-journal`, `-shm`, `-wal`, `-mj*`). Safe when the file doesn't
        // exist yet.
        SQLiteDatabase.deleteDatabase(dbFile)
    }

    /**
     * Open the legacy file directly (no `InternalDbHelper`), run [seed], stamp
     * `user_version`, close. After this returns, `InternalDbHelper(app)` will
     * see the file at [oldVersion] and run `onUpgrade(oldVersion,
     * App.getVersionCode())`.
     */
    private fun seedLegacyDb(oldVersion: Int, seed: (SQLiteDatabase) -> Unit) {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            seed(db)
            db.version = oldVersion
        }
    }

    /**
     * Open the real helper. The first call to `writableDatabase` runs
     * `onUpgrade` (the migrations under test). Caller is responsible for
     * closing.
     */
    private fun upgradeViaHelper(): InternalDbHelper =
        InternalDbHelper(app).apply { writableDatabase }

    // region Helpers for emitting legacy schemas

    /**
     * Schema the app used between roughly v3.4.4 and v4.0.0-beta1 (i.e.
     * `oldVersion ∈ [143, 14000163)`) — old `Bukmak2` + `Label` + `Bukmak2_Label`,
     * plus `Edisi`, `ReadingPlan`, `ReadingPlanProgress` (gid-less), `ProgressMark`,
     * `ProgressMarkHistory`, `Renungan`. Stripped down to columns the migrations
     * actually read; padding columns the migrations don't touch isn't necessary.
     */
    private fun installLegacyV3Schema(db: SQLiteDatabase) {
        db.execSQL(
            "create table Bukmak2 (_id integer primary key autoincrement, " +
                "ari integer, jenis integer, tulisan text, waktuTambah integer, waktuUbah integer)"
        )
        db.execSQL(
            "create table Label (_id integer primary key autoincrement, " +
                "judul text, urutan integer, warnaLatar text)"
        )
        db.execSQL(
            "create table Bukmak2_Label (_id integer primary key autoincrement, " +
                "bukmak2_id integer, label_id integer)"
        )
        db.execSQL(
            "create table Edisi (_id integer primary key autoincrement, " +
                "shortName text, judul text, jenis text, keterangan text, " +
                "namafile text, namafile_pdbasal text, aktif integer, urutan integer)"
        )
        db.execSQL(
            "create table ReadingPlan (_id integer primary key autoincrement, " +
                "version integer, name text, title text, description text, " +
                "duration integer, startTime integer, data blob)"
        )
        // Legacy RPP keyed by reading_plan_id (the FK that the 14000225 migration
        // rewrites into a gid).
        db.execSQL(
            "create table ReadingPlanProgress (_id integer primary key autoincrement, " +
                "reading_plan_id integer, reading_code integer, checkTime integer)"
        )
        db.execSQL(
            "create table ProgressMark (_id integer primary key autoincrement, " +
                "preset_id integer, caption text, ari integer, modifyTime integer)"
        )
        db.execSQL(
            "create table ProgressMarkHistory (_id integer primary key autoincrement, " +
                "progress_mark_preset_id integer, progress_mark_caption integer, " +
                "ari integer, createTime integer)"
        )
        // The legacy Devotion table (`Renungan`). The 14000200 migration drops
        // it without looking inside — schema can be minimal.
        db.execSQL("create table Renungan (_id integer primary key autoincrement, name text)")
    }

    // endregion

    // region Data-conversion migrations

    /**
     * The big one. Validates the `oldVersion < 14000163` branch:
     * 1. `Bukmak2` rows turn into `Marker` rows with `_id` preserved, columns
     *    mapped (ari/jenis/tulisan/waktuTambah/waktuUbah → ari/kind/caption/createTime/modifyTime),
     *    `verseCount` defaulted to 1, and a fresh non-null gid.
     * 2. Each existing `Label` row gets a non-null `gid` populated by
     *    [InternalDbHelper.addGidColumnToLabelIfNeeded] + the conversion's
     *    label loop.
     * 3. `Bukmak2_Label` rows turn into `Marker_Label` rows that reference the
     *    just-assigned `marker.gid` and `label.gid` — *not_ the legacy `_id`s.
     */
    @Test
    fun `convertFromBookmark2ToMarker copies bookmarks, assigns gids, and rebuilds the label join`() {
        seedLegacyDb(oldVersion = 14000162) { db ->
            installLegacyV3Schema(db)

            // Two legacy bookmarks + two labels, with one bookmark having both labels.
            insertBookmark2(db, _id = 1, ari = 0x010001, kind = 1, caption = "first", addTime = 1000L, modifyTime = 1500L)
            insertBookmark2(db, _id = 2, ari = 0x010002, kind = 2, caption = "second", addTime = 2000L, modifyTime = 2500L)
            insertLabel(db, _id = 10, title = "favorites", ordering = 1)
            insertLabel(db, _id = 11, title = "todo", ordering = 2)
            insertBookmark2Label(db, _id = 100, bookmark2_id = 1, label_id = 10)
            insertBookmark2Label(db, _id = 101, bookmark2_id = 1, label_id = 11)
            insertBookmark2Label(db, _id = 102, bookmark2_id = 2, label_id = 10)
        }

        upgradeViaHelper().use { helper ->
            val db = helper.readableDatabase

            // Marker rows ----------------------------------------------------
            val markers = db.rawQuery(
                "select _id, ari, kind, caption, createTime, modifyTime, verseCount, gid from Marker order by _id",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            mapOf(
                                "_id" to c.getLong(0),
                                "ari" to c.getInt(1),
                                "kind" to c.getInt(2),
                                "caption" to c.getString(3),
                                "createTime" to c.getLong(4),
                                "modifyTime" to c.getLong(5),
                                "verseCount" to c.getInt(6),
                                "gid" to c.getString(7),
                            )
                        )
                    }
                }
            }
            assertEquals(2, markers.size)

            val first = markers[0]
            assertEquals(1L, first["_id"])
            assertEquals(0x010001, first["ari"])
            assertEquals(1, first["kind"])
            assertEquals("first", first["caption"])
            assertEquals(1000L, first["createTime"])
            assertEquals(1500L, first["modifyTime"])
            assertEquals(1, first["verseCount"])
            assertNotNull(first["gid"])
            assertTrue("gid should be non-empty", (first["gid"] as String).isNotEmpty())

            val second = markers[1]
            assertEquals(2L, second["_id"])
            assertEquals(0x010002, second["ari"])
            assertEquals("second", second["caption"])

            assertFalse(
                "each marker should get its own gid",
                first["gid"] == second["gid"],
            )

            // Label rows now have gids -------------------------------------
            val labels = db.rawQuery(
                "select _id, gid, judul from Label order by _id", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Triple(c.getLong(0), c.getString(1), c.getString(2)))
                    }
                }
            }
            assertEquals(2, labels.size)
            assertEquals(10L, labels[0].first)
            assertNotNull(labels[0].second)
            assertEquals("favorites", labels[0].third)
            assertEquals(11L, labels[1].first)
            assertNotNull(labels[1].second)
            assertFalse(
                "each label should get its own gid",
                labels[0].second == labels[1].second,
            )

            // Marker_Label rows reference gids, not _ids -------------------
            val markerLabels = db.rawQuery(
                "select _id, marker_gid, label_gid from Marker_Label order by _id", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Triple(c.getLong(0), c.getString(1), c.getString(2)))
                    }
                }
            }
            assertEquals(3, markerLabels.size)

            val markerGidById = markers.associate { it["_id"] as Long to it["gid"] as String }
            val labelGidById = labels.associate { it.first to it.second }

            assertEquals(markerGidById[1L], markerLabels[0].second)
            assertEquals(labelGidById[10L], markerLabels[0].third)
            assertEquals(markerGidById[1L], markerLabels[1].second)
            assertEquals(labelGidById[11L], markerLabels[1].third)
            assertEquals(markerGidById[2L], markerLabels[2].second)
            assertEquals(labelGidById[10L], markerLabels[2].third)
        }
    }

    /**
     * Validates the `oldVersion < 14000166` branch — `Edisi` → `Version`.
     *
     * - The `Version` table is created.
     * - Each `Edisi` row becomes a `Version` row, keeping `shortName`, `judul`
     *   → `longName`, `keterangan` → `description`, `namafile` → `filename`,
     *   and `aktif` → `active`. `locale` and `preset_name` come out null
     *   (Edisi has no analog), `modifyTime` defaults to 0, `ordering` is
     *   re-numbered starting from [MVersionDb.DEFAULT_ORDERING_START] + 1.
     * - The legacy `Edisi` table is dropped.
     *
     * No preset rows are inserted because no `.yes` files exist in the test
     * fixture; that path of `convertFromEdisiToVersion` is exercised by the
     * production migration when real files are present.
     */
    @Test
    fun `convertFromEdisiToVersion copies Edisi rows to Version and drops Edisi`() {
        seedLegacyDb(oldVersion = 14000164) { db ->
            installLegacyV3Schema(db)

            insertEdisi(db, _id = 1, shortName = "KJV", title = "King James", description = "1611", filename = "/sd/kjv.yes", active = 1, ordering = 1)
            insertEdisi(db, _id = 2, shortName = "ASV", title = "American Standard", description = "1901", filename = "/sd/asv.yes", active = 0, ordering = 2)
        }

        upgradeViaHelper().use { helper ->
            val rows = helper.readableDatabase.rawQuery(
                "select shortName, longName, description, filename, active, ordering, locale, preset_name " +
                    "from Version order by ordering",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            mapOf(
                                "shortName" to c.getString(0),
                                "longName" to c.getString(1),
                                "description" to c.getString(2),
                                "filename" to c.getString(3),
                                "active" to c.getInt(4),
                                "ordering" to c.getInt(5),
                                "locale" to c.getString(6),
                                "preset_name" to c.getString(7),
                            )
                        )
                    }
                }
            }

            assertEquals(2, rows.size)
            assertEquals("KJV", rows[0]["shortName"])
            assertEquals("King James", rows[0]["longName"])
            assertEquals("1611", rows[0]["description"])
            assertEquals("/sd/kjv.yes", rows[0]["filename"])
            assertEquals(1, rows[0]["active"])
            assertNull("locale isn't present in Edisi → should be null", rows[0]["locale"])
            assertNull("preset_name isn't present in Edisi → should be null", rows[0]["preset_name"])

            assertEquals("ASV", rows[1]["shortName"])
            assertEquals(0, rows[1]["active"])

            // Edisi table should be dropped by the migration.
            val edisiCount = helper.readableDatabase.rawQuery(
                "select count(*) from sqlite_master where type='table' and name='Edisi'",
                null,
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("legacy Edisi table should be dropped", 0, edisiCount)
        }
    }

    /**
     * Validates the `oldVersion > 142 && oldVersion < 14000225` branch:
     * `ReadingPlanProgress.reading_plan_id` (FK to `ReadingPlan._id`) is
     * rewritten into `reading_plan_progress_gid` (a deterministic hash of the
     * plan name). Rows whose `reading_plan_id` has no corresponding
     * `ReadingPlan` row are silently dropped — that's the legacy contract,
     * confirmed by this test.
     */
    @Test
    fun `migrateReadingPlanProgressTable converts reading_plan_id rows into gid rows`() {
        seedLegacyDb(oldVersion = 14000220) { db ->
            installLegacyV3Schema(db)

            insertReadingPlan(db, _id = 5, name = "M-Cheyne")
            insertReadingPlan(db, _id = 7, name = "Chronological")
            insertReadingPlanProgress_legacy(db, reading_plan_id = 5, reading_code = 1, checkTime = 1000L)
            insertReadingPlanProgress_legacy(db, reading_plan_id = 5, reading_code = 2, checkTime = 2000L)
            insertReadingPlanProgress_legacy(db, reading_plan_id = 7, reading_code = 1, checkTime = 3000L)
            // Orphan: no matching ReadingPlan row — should be silently dropped.
            insertReadingPlanProgress_legacy(db, reading_plan_id = 999, reading_code = 1, checkTime = 4000L)
        }

        upgradeViaHelper().use { helper ->
            val rows = helper.readableDatabase.rawQuery(
                "select reading_plan_progress_gid, reading_code, checkTime " +
                    "from ReadingPlanProgress order by reading_code, checkTime",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Triple(c.getString(0), c.getInt(1), c.getLong(2)))
                    }
                }
            }

            assertEquals("orphan row should have been dropped", 3, rows.size)
            // The gid is deterministic per plan name. Both M-Cheyne entries should share a gid.
            val mcheyneRows = rows.filter { it.third in 1000L..2000L }
            assertEquals(2, mcheyneRows.size)
            assertEquals(mcheyneRows[0].first, mcheyneRows[1].first)
            val chronoRow = rows.single { it.third == 3000L }
            assertFalse(
                "different plan names should produce different gids",
                mcheyneRows[0].first == chronoRow.first,
            )
        }
    }

    // endregion

    // region Bug-fix migrations

    /**
     * Validates the `oldVersion >= 137 && oldVersion <= 142` branch. A bug in
     * v3.3.3 created `ReadingPlanProgress` with a misspelled `checkedTime`
     * column (should have been `checkTime`); the migration drops the table so
     * the subsequent `oldVersion <= 142` branch can re-create it correctly.
     * If that drop ever stops happening, users who installed v3.3.3–v3.4.3
     * would be stuck with an unusable RPP table.
     */
    @Test
    fun `bad ReadingPlanProgress table with checkedTime column gets dropped on upgrade from v3 3 3 to v3 4 4`() {
        // 140 is in the band that triggers *every* prior migration too
        // (Bookmark2 conversion etc.), so the seed has to be the full legacy
        // v3 schema — but with the RPP table replaced by the buggy variant.
        seedLegacyDb(oldVersion = 140) { db ->
            installLegacyV3Schema(db)
            db.execSQL("drop table ReadingPlanProgress")
            // The bad RPP table: spelled `checkedTime` instead of `checkTime`.
            db.execSQL(
                "create table ReadingPlanProgress (_id integer primary key autoincrement, " +
                    "reading_plan_id integer, reading_code integer, checkedTime integer)"
            )
        }

        upgradeViaHelper().use { helper ->
            val columns = helper.readableDatabase.rawQuery(
                "pragma table_info(ReadingPlanProgress)", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(1 /* "name" column */))
                }
            }

            assertTrue(
                "post-migration RPP table must have the correct `checkTime` column, got $columns",
                "checkTime" in columns,
            )
            assertFalse(
                "post-migration RPP table must not still have the typo'd `checkedTime` column",
                "checkedTime" in columns,
            )
        }
    }

    // endregion

    // region Schema-only migrations (idempotency + presence)

    /**
     * After upgrading from a deeply old legacy state, every table the current
     * app expects should exist. Catches a typo, missing branch, or wrong
     * version threshold in any of the schema-creation `if` blocks.
     */
    @Test
    fun `all current schema tables exist after upgrading from legacy v3 state`() {
        seedLegacyDb(oldVersion = 14000162) { db ->
            installLegacyV3Schema(db)
        }

        upgradeViaHelper().use { helper ->
            val tables = helper.readableDatabase.rawQuery(
                "select name from sqlite_master where type='table' order by name", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0))
                }
            }

            // Every table currently used by the app should be present.
            for (expected in listOf(
                "Marker", "Marker_Label", "Label", "Version",
                "ProgressMark", "ProgressMarkHistory",
                "ReadingPlan", "ReadingPlanProgress",
                "SyncShadow", "SyncLog", "Devotion", "PerVersion",
            )) {
                assertTrue("table $expected should exist after upgrade; have: $tables", expected in tables)
            }

            // The legacy Renungan table should be gone — the v4.1 migration
            // explicitly drops it before creating the new `Devotion` table.
            assertFalse("legacy Renungan table should be dropped", "Renungan" in tables)
        }
    }

    /**
     * Re-running the gid-addition idempotently — exercises the `if (gidColumn
     * doesn't already exist)` guard inside [InternalDbHelper.addGidColumnToLabelIfNeeded].
     * Captured here so a future refactor can't accidentally remove the guard
     * and break upgrades from versions where the column already exists.
     */
    @Test
    fun `addGidColumnToLabelIfNeeded does not re-add the column when it already exists`() {
        seedLegacyDb(oldVersion = 14000162) { db ->
            installLegacyV3Schema(db)
            // Pre-existing gid column on Label — simulates a partially-upgraded DB.
            db.execSQL("alter table Label add column gid text")
            db.execSQL("insert into Label (judul, urutan, warnaLatar, gid) values ('preexisting', 1, null, 'preexisting-gid')")
        }

        upgradeViaHelper().use { helper ->
            // Should not throw with "duplicate column name: gid". Existing gid
            // gets overwritten by the migration's label loop (intended:
            // legacy DBs with manually-added gid weren't shipped, this is a
            // defensive path).
            val gids = helper.readableDatabase.rawQuery(
                "select gid from Label where judul='preexisting'", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0))
                }
            }
            assertEquals(1, gids.size)
            assertNotNull(gids.single())
            assertTrue(gids.single().isNotEmpty())
        }
    }

    // endregion

    // region Per-row inserts for legacy tables

    private fun insertBookmark2(db: SQLiteDatabase, _id: Long, ari: Int, kind: Int, caption: String, addTime: Long, modifyTime: Long) {
        val cv = ContentValues().apply {
            put("_id", _id)
            put("ari", ari)
            put("jenis", kind)
            put("tulisan", caption)
            put("waktuTambah", addTime)
            put("waktuUbah", modifyTime)
        }
        db.insert("Bukmak2", null, cv)
    }

    private fun insertLabel(db: SQLiteDatabase, _id: Long, title: String, ordering: Int) {
        val cv = ContentValues().apply {
            put("_id", _id)
            put("judul", title)
            put("urutan", ordering)
        }
        db.insert("Label", null, cv)
    }

    private fun insertBookmark2Label(db: SQLiteDatabase, _id: Long, bookmark2_id: Long, label_id: Long) {
        val cv = ContentValues().apply {
            put("_id", _id)
            put("bukmak2_id", bookmark2_id)
            put("label_id", label_id)
        }
        db.insert("Bukmak2_Label", null, cv)
    }

    private fun insertEdisi(
        db: SQLiteDatabase,
        _id: Long,
        shortName: String,
        title: String,
        description: String,
        filename: String,
        active: Int,
        ordering: Int,
    ) {
        val cv = ContentValues().apply {
            put("_id", _id)
            put("shortName", shortName)
            put("judul", title)
            put("keterangan", description)
            put("namafile", filename)
            put("aktif", active)
            put("urutan", ordering)
        }
        db.insert("Edisi", null, cv)
    }

    private fun insertReadingPlan(db: SQLiteDatabase, _id: Long, name: String) {
        val cv = ContentValues().apply {
            put("_id", _id)
            put("name", name)
            put("title", name)
            put("duration", 1)
            put("startTime", 0)
            put("version", 1)
        }
        db.insert("ReadingPlan", null, cv)
    }

    private fun insertReadingPlanProgress_legacy(db: SQLiteDatabase, reading_plan_id: Long, reading_code: Int, checkTime: Long) {
        val cv = ContentValues().apply {
            put("reading_plan_id", reading_plan_id)
            put("reading_code", reading_code)
            put("checkTime", checkTime)
        }
        db.insert("ReadingPlanProgress", null, cv)
    }

    // endregion

    private inline fun <R> InternalDbHelper.use(block: (InternalDbHelper) -> R): R =
        try {
            block(this)
        } finally {
            close()
        }
}
