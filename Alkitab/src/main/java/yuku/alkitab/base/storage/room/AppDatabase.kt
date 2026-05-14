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
 * - v3 (REM-10 follow-up) — restores the `(kind, caption COLLATE NOCASE)`
 *   index dropped in v2 because Room's `@Index` cannot express per-column
 *   collation. Added out-of-band via [MIGRATION_2_3] (for upgrades) and
 *   [noCaseCaptionIndexCallback] (for fresh installs). See [MARKER_KIND_CAPTION_NOCASE_INDEX_SQL].
 */
@Database(
    entities = [
        VersionEntity::class,
        MarkerEntity::class,
        LabelEntity::class,
        MarkerLabelEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun versionDao(): VersionRoomDao
    abstract fun markerDao(): MarkerRoomDao
    abstract fun labelDao(): LabelRoomDao
    abstract fun markerLabelDao(): MarkerLabelRoomDao

    companion object {
        const val DB_NAME = "AlkitabRoomDb"

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

        const val MARKER_KIND_CAPTION_NOCASE_INDEX_NAME = "index_marker_kind_caption_nocase"

        /**
         * SQL that creates the `(kind, caption COLLATE NOCASE)` index.
         * Room's `@Index` can't express per-column collation, so this index
         * is applied out-of-band in both [MIGRATION_2_3] (for existing users)
         * and [noCaseCaptionIndexCallback] (for fresh installs).
         */
        const val MARKER_KIND_CAPTION_NOCASE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `" + MARKER_KIND_CAPTION_NOCASE_INDEX_NAME + "` " +
                "ON `marker` (`kind`, `caption` COLLATE NOCASE)"

        /**
         * v2 → v3: restores `index_marker_kind_caption_nocase` on the
         * `marker` table. No entity schema changes — the index is added
         * out-of-band because Room's `@Index` cannot express per-column
         * collation. The same SQL is applied on fresh installs via
         * [noCaseCaptionIndexCallback].
         */
        @JvmField
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MARKER_KIND_CAPTION_NOCASE_INDEX_SQL)
            }
        }

        private val noCaseCaptionIndexCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Room's @Index creates (kind, caption) without COLLATE NOCASE
                // because @Index can't express per-column collation. Drop and
                // recreate with COLLATE NOCASE to match MIGRATION_2_3.
                db.execSQL("DROP INDEX IF EXISTS `$MARKER_KIND_CAPTION_NOCASE_INDEX_NAME`")
                db.execSQL(MARKER_KIND_CAPTION_NOCASE_INDEX_SQL)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(noCaseCaptionIndexCallback)
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
