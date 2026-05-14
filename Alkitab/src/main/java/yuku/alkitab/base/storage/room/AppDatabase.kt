package yuku.alkitab.base.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for tables migrated as part of the REM-11+ tech-debt plan.
 * Lives in a separate SQLite file (`AlkitabRoomDb`) from the legacy
 * `AlkitabDb` to avoid the `user_version` collision documented in
 * `docs/superpowers/specs/2026-05-13-rem-11-room-version-table-design.md`.
 *
 * Version policy: this database starts at `version = 1`. Future tables added
 * to Room bump the version and add a `Migration` (Room-style, not the legacy
 * `versionCode`-driven `InternalDbHelper.onUpgrade`).
 *
 * History:
 * - v1 (REM-11) — introduced the `version` table.
 * - v2 (REM-10) — added the `marker`, `label`, `marker_label` tables and
 *   their indexes. See [MIGRATION_1_2]. Legacy `Marker` / `Label` /
 *   `Marker_Label` tables in `AlkitabDb` are left in place as a rollback
 *   safety net; a follow-up release will drop them.
 * - v3 — added the `devotion` table and its two indexes. See
 *   [MIGRATION_2_3]. Same rollback-safety convention: the legacy `Devotion`
 *   table in `AlkitabDb` is left intact.
 * - v4 — added the `per_version` table and its unique index on `versionId`.
 *   See [MIGRATION_3_4]. Same rollback-safety convention: the legacy
 *   `PerVersion` table in `AlkitabDb` is left intact.
 *
 * REM-10 follow-up note: [MIGRATION_1_2] creates the
 * `index_marker_kind_caption` index with `COLLATE NOCASE` (matching the
 * legacy `index_Marker_05`). Fresh installs of v2 go through Room's
 * entity-driven schema, which can't express per-column collation via
 * `@Index`, so they end up with the same index without `COLLATE NOCASE`.
 * Both shapes share the same PRAGMA column list, so Room's schema validator
 * accepts either.
 */
@Database(
    entities = [
        VersionEntity::class,
        MarkerEntity::class,
        LabelEntity::class,
        MarkerLabelEntity::class,
        DevotionEntity::class,
        PerVersionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun versionDao(): VersionRoomDao
    abstract fun markerDao(): MarkerRoomDao
    abstract fun labelDao(): LabelRoomDao
    abstract fun markerLabelDao(): MarkerLabelRoomDao
    abstract fun devotionDao(): DevotionRoomDao
    abstract fun perVersionDao(): PerVersionRoomDao

    companion object {
        const val DB_NAME = "AlkitabRoomDb"

        /**
         * Name of the `(kind, caption)` index on the `marker` table. Single
         * source of truth shared between the `@Index` annotation on
         * [MarkerEntity] and the `MIGRATION_1_2` SQL.
         *
         * The migration SQL adds `COLLATE NOCASE`; the `@Index`-generated SQL
         * for fresh installs cannot. PRAGMA hides per-column collation, so
         * Room's schema validator accepts both shapes.
         */
        const val MARKER_KIND_CAPTION_INDEX_NAME = "index_marker_kind_caption"

        /**
         * v1 → v2: create the `marker`, `label`, `marker_label` tables and
         * their indexes. SQL mirrors what Room emits for the v2 entities
         * (compare against `Alkitab/schemas/.../2.json`) — keep them in
         * sync if these entities change.
         *
         * A v1 device upgrading runs this migration once; a fresh install
         * skips straight to v2 via Room's `onCreate` (Room uses the entity
         * definitions, not this migration, for fresh schemas — that's why
         * the SQL here must match what Room would emit).
         */
        @JvmField
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `marker` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`gid` TEXT, " +
                        "`ari` INTEGER NOT NULL, " +
                        "`kind` INTEGER NOT NULL, " +
                        "`caption` TEXT, " +
                        "`verseCount` INTEGER NOT NULL, " +
                        "`createTime` INTEGER NOT NULL, " +
                        "`modifyTime` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_ari` ON `marker` (`ari`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_kind_ari` ON `marker` (`kind`, `ari`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_kind_modifyTime` ON `marker` (`kind`, `modifyTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_kind_createTime` ON `marker` (`kind`, `createTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_gid` ON `marker` (`gid`)")
                // The (kind, caption COLLATE NOCASE) index — see the class
                // KDoc for why this migration creates it with COLLATE while
                // the @Index annotation on MarkerEntity can't.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `" + MARKER_KIND_CAPTION_INDEX_NAME + "` " +
                        "ON `marker` (`kind`, `caption` COLLATE NOCASE)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `label` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`gid` TEXT, " +
                        "`title` TEXT, " +
                        "`ordering` INTEGER NOT NULL, " +
                        "`backgroundColor` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_label_ordering` ON `label` (`ordering`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_label_gid` ON `label` (`gid`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `marker_label` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`gid` TEXT, " +
                        "`marker_gid` TEXT, " +
                        "`label_gid` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_label_marker_gid` ON `marker_label` (`marker_gid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_label_label_gid` ON `marker_label` (`label_gid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_marker_label_gid` ON `marker_label` (`gid`)")
            }
        }

        /**
         * v2 → v3: create the `devotion` table and its two indexes. SQL
         * mirrors what Room emits for the v3 entity (compare against
         * `Alkitab/schemas/.../3.json`) — keep them in sync if [DevotionEntity]
         * changes.
         *
         * A v2 device upgrading runs this migration once; a fresh install
         * skips straight to v3 via Room's `onCreate` (Room uses the entity
         * definitions, not this migration, for fresh schemas — that's why
         * the SQL here must match what Room would emit).
         */
        @JvmField
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `devotion` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT, " +
                        "`date` TEXT, " +
                        "`body` TEXT, " +
                        "`readyToUse` INTEGER NOT NULL, " +
                        "`touchTime` INTEGER NOT NULL, " +
                        "`dataFormatVersion` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_devotion_name_date_dataFormatVersion` " +
                        "ON `devotion` (`name`, `date`, `dataFormatVersion`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_devotion_touchTime` " +
                        "ON `devotion` (`touchTime`)",
                )
            }
        }

        /**
         * v3 → v4: create the `per_version` table and its unique
         * `versionId` index. SQL mirrors what Room emits for the v4 entity
         * (compare against `Alkitab/schemas/.../4.json`) — keep them in
         * sync if [PerVersionEntity] changes.
         *
         * A v3 device upgrading runs this migration once; a fresh install
         * skips straight to v4 via Room's `onCreate` (Room uses the entity
         * definitions, not this migration, for fresh schemas — that's why
         * the SQL here must match what Room would emit).
         */
        @JvmField
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `per_version` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`versionId` TEXT NOT NULL, " +
                        "`settings` TEXT)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_per_version_versionId` " +
                        "ON `per_version` (`versionId`)",
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                // Reads on the main thread are allowed for now because the call
                // sites we migrate (S.getAvailableVersions, InternalDb.* facade
                // methods) are synchronous. Coroutine-based callers can be
                // introduced later — see REM-15 in tech-debt-remediation.md.
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /**
         * Test-only — replace the cached singleton with an in-memory database
         * (or null to release it). Closes the previous instance.
         *
         * Robolectric tests share JVM state across test methods within the
         * same suite, so production singletons leak between tests; this hook
         * lets `@Before` install a fresh DB and `@After` tear it down.
         */
        @androidx.annotation.VisibleForTesting
        @JvmStatic
        fun setForTesting(db: AppDatabase?) {
            synchronized(this) {
                instance?.close()
                instance = db
            }
        }
    }
}
