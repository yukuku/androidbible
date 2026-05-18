package yuku.alkitab.base.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Songs subsystem — the `song_info` and
 * `song_book_info` tables migrated from the legacy `SongDb` SQLite file
 * (managed by `SongDbHelper`) as part of REM-32.
 *
 * Lives in a separate SQLite file (`AlkitabSongRoomDb`) from the legacy
 * `SongDb` file. The split matches the existing module isolation between
 * the Songs subsystem and the Bible-reading subsystem (see
 * `docs/modules/songs.md`) — the two domains share no rows, no foreign
 * keys, and no transactions.
 *
 * Version policy: this database starts at `version = 1`. A future change
 * that alters the row format of the `data` BLOB (REM-21, Parcelable →
 * JSON) will bump the version and add a `Migration`. The current revision
 * only swaps the storage engine; the BLOB payload is round-tripped
 * byte-for-byte.
 */
@Database(
    entities = [
        SongInfoEntity::class,
        SongBookInfoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SongRoomDatabase : RoomDatabase() {
    abstract fun songRoomDao(): SongRoomDao

    companion object {
        const val DB_NAME = "AlkitabSongRoomDb"

        @Volatile
        private var instance: SongRoomDatabase? = null

        @JvmStatic
        fun get(context: Context): SongRoomDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): SongRoomDatabase =
            Room.databaseBuilder(context, SongRoomDatabase::class.java, DB_NAME)
                // Reads on the main thread are allowed for now because every
                // current [yuku.alkitab.base.storage.SongDb] call site is
                // synchronous (the song list / song view / song-book picker
                // all read on the UI thread). Coroutine-based callers can be
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
        fun setForTesting(db: SongRoomDatabase?) {
            synchronized(this) {
                instance?.close()
                instance = db
            }
        }
    }
}
