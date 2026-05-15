package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one row of the `song_book_info` table. Lives in
 * [SongRoomDatabase] (file `AlkitabSongRoomDb`), distinct from the legacy
 * `SongBookInfo` table in the `SongDb` SQLite file which is still created
 * by `SongDbHelper` but no longer written to by production code. The
 * one-time data copy on first launch is wired up in [SongDbDataMigration].
 *
 * Column nullability — every column except `_id` is kept nullable to match
 * the legacy schema. The facade effectively requires `name` to be present
 * for any keyed lookup; [SongDbDataMigration] drops legacy rows with NULL
 * `name` rather than coalescing.
 *
 * The legacy schema has a non-unique index on `name`. A UNIQUE constraint
 * is intentionally *not* added — `SongDb.insertSongBookInfo` preserves the
 * legacy delete-then-insert upsert semantics (mirrored at the facade
 * level), and adding UNIQUE would fail the migration on any duplicate
 * `name` row that ever crept into the legacy table.
 */
@Entity(
    tableName = "song_book_info",
    indices = [Index(value = ["name"], name = "index_song_book_info_name")],
)
data class SongBookInfoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val name: String?,
    val title: String?,
    val copyright: String?,
)
