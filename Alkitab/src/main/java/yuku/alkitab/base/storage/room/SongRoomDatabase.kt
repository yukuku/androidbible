package yuku.alkitab.base.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Songs subsystem, holding the `song_info` and
 * `song_book_info` tables. The legacy `SongDb` SQLite file (managed by
 * `SongDbHelper`) still carries the same tables as a rollback safety net.
 *
 * Lives in a separate SQLite file (`AlkitabSongRoomDb`) from the legacy
 * `SongDb` file. The split matches the module isolation between the Songs
 * subsystem and the Bible-reading subsystem (see `docs/modules/songs.md`):
 * the two domains share no rows, no foreign keys, and no transactions.
 *
 * The `data` BLOB's own format is versioned by the `dataFormatVersion`
 * column, not by this database's version, so a payload change converts
 * rows lazily on read instead of bumping the schema and adding a
 * `Migration`.
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
                // all read on the UI thread).
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
