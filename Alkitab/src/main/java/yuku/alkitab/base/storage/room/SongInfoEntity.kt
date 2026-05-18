package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one row of the `song_info` table. Lives in
 * [SongRoomDatabase] (file `AlkitabSongRoomDb`), distinct from the legacy
 * `SongInfo` table in the `SongDb` SQLite file which is still created by
 * `SongDbHelper` but no longer written to by production code. The one-time
 * data copy on first launch is wired up in [SongDbDataMigration].
 *
 * Column nullability — `bookName` / `code` / `title` / `title_original` /
 * `data` are kept nullable because the legacy schema permitted NULL for
 * every column except `_id`. The facade
 * ([yuku.alkitab.base.storage.SongDb]) effectively writes concrete values
 * for `bookName`, `code`, `dataFormatVersion`, `ordering`, and `updateTime`
 * on every code path, but [SongDbDataMigration] still has to ingest
 * whatever legacy rows exist on disk. The migration drops legacy rows
 * missing a non-null Room column (`bookName`, `code`) with `?: continue`
 * rather than coalescing — defence in depth.
 *
 * The binary [data] column holds the Parcelable-marshalled
 * [yuku.kpri.model.Song] snapshot. REM-32 round-trips these bytes
 * unchanged; a future REM-21 may switch the payload to JSON, but that's
 * not in scope here.
 *
 * Indexes mirror `SongDbHelper.setupTableSongInfo`:
 *  - `(bookName, code)` for keyed lookups by song
 *  - `(bookName, ordering)` for the `ORDER BY ordering` listing path
 */
@Entity(
    tableName = "song_info",
    indices = [
        Index(value = ["bookName", "code"], name = "index_song_info_bookName_code"),
        Index(value = ["bookName", "ordering"], name = "index_song_info_bookName_ordering"),
    ],
)
data class SongInfoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val bookName: String?,
    val code: String?,
    val title: String?,
    val title_original: String?,
    val ordering: Int,
    val dataFormatVersion: Int,
    val data: ByteArray?,
    val updateTime: Int,
) {
    /**
     * Generated `equals` / `hashCode` from `data class` use reference equality
     * on the `data` ByteArray, which makes round-trip assertions in the unit
     * tests brittle. Override both to use content equality on the byte
     * array — every other field is a value type so the rest of the
     * generated implementation would be correct.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SongInfoEntity) return false
        return _id == other._id &&
            bookName == other.bookName &&
            code == other.code &&
            title == other.title &&
            title_original == other.title_original &&
            ordering == other.ordering &&
            dataFormatVersion == other.dataFormatVersion &&
            data.contentEquals(other.data) &&
            updateTime == other.updateTime
    }

    override fun hashCode(): Int {
        var result = _id.hashCode()
        result = 31 * result + (bookName?.hashCode() ?: 0)
        result = 31 * result + (code?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (title_original?.hashCode() ?: 0)
        result = 31 * result + ordering
        result = 31 * result + dataFormatVersion
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + updateTime
        return result
    }
}
