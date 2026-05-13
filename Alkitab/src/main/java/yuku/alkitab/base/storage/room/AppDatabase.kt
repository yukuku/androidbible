package yuku.alkitab.base.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for tables migrated as part of the REM-11+ tech-debt plan.
 * Lives in a separate SQLite file (`AlkitabRoomDb`) from the legacy
 * `AlkitabDb` to avoid the `user_version` collision documented in
 * `docs/superpowers/specs/2026-05-13-rem-11-room-version-table-design.md`.
 *
 * Version policy: this database starts at `version = 1`. Future tables added
 * to Room bump the version and add a `Migration` (Room-style, not the legacy
 * `versionCode`-driven `InternalDbHelper.onUpgrade`).
 */
@Database(
    entities = [VersionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun versionDao(): VersionRoomDao

    companion object {
        const val DB_NAME = "AlkitabRoomDb"

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
