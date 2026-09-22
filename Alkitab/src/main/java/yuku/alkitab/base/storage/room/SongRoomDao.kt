package yuku.alkitab.base.storage.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room DAO for the `song_info` and `song_book_info` tables, consumed by the
 * facade in [yuku.alkitab.base.storage.SongDb].
 *
 * Both tables share one DAO because a song-book download writes them together
 * (`SongBookUtil.handleDownloadResult` calls `insertSongBookInfo` then
 * `storeSongs`).
 */
@Dao
abstract class SongRoomDao {

    // region SongInfo

    @Insert
    abstract fun insertSongInfo(entity: SongInfoEntity): Long

    @Query("DELETE FROM song_info WHERE bookName = :bookName")
    abstract fun deleteSongInfosByBookName(bookName: String): Int

    @Query(
        "SELECT * FROM song_info WHERE bookName = :bookName AND code = :code LIMIT 1",
    )
    abstract fun findSongInfoByBookNameAndCode(bookName: String, code: String): SongInfoEntity?

    @Query(
        "SELECT COUNT(*) FROM song_info WHERE bookName = :bookName AND code = :code",
    )
    abstract fun countSongInfosByBookNameAndCode(bookName: String, code: String): Int

    @Query(
        "SELECT * FROM song_info WHERE bookName = :bookName " +
            "ORDER BY ordering ASC LIMIT 1",
    )
    abstract fun findFirstSongInfoByBookName(bookName: String): SongInfoEntity?

    @Query(
        "SELECT * FROM song_info ORDER BY bookName ASC, ordering ASC LIMIT 1",
    )
    abstract fun findAnySongInfo(): SongInfoEntity?

    @Query(
        "SELECT * FROM song_info WHERE bookName = :bookName ORDER BY ordering ASC",
    )
    abstract fun listSongInfosByBookName(bookName: String): List<SongInfoEntity>

    @Query(
        "SELECT * FROM song_info ORDER BY bookName ASC, ordering ASC",
    )
    abstract fun listAllSongInfos(): List<SongInfoEntity>

    /**
     * Skips the `data` BLOB: songs run a few kB each, so `SELECT *` over a
     * whole song book would pull several MB onto the heap for a listing.
     */
    @Query(
        "SELECT bookName, code, title, title_original FROM song_info " +
            "WHERE bookName = :bookName ORDER BY ordering ASC",
    )
    abstract fun listSongInfoMetasByBookName(bookName: String): List<SongInfoMetaRow>

    /** All-books variant of [listSongInfoMetasByBookName], for `bookName == null`. */
    @Query(
        "SELECT bookName, code, title, title_original FROM song_info " +
            "ORDER BY bookName ASC, ordering ASC",
    )
    abstract fun listAllSongInfoMetas(): List<SongInfoMetaRow>

    /**
     * Returns a [Cursor] so the caller can stream. With a `List` return type
     * Room materialises every row, `data` BLOB included, before returning, and
     * the filter-everything path can then OOM on a device with many song books.
     * Streaming bounds memory at the CursorWindow (~2 MB) plus one song. The
     * caller is responsible for closing it.
     */
    @Query(
        "SELECT _id, bookName, code, title, title_original, data, dataFormatVersion " +
            "FROM song_info WHERE bookName = :bookName ORDER BY ordering ASC",
    )
    abstract fun queryDeepFilterRowsByBookName(bookName: String): Cursor

    /** All-books variant of [queryDeepFilterRowsByBookName]. */
    @Query(
        "SELECT _id, bookName, code, title, title_original, data, dataFormatVersion " +
            "FROM song_info ORDER BY bookName ASC, ordering ASC",
    )
    abstract fun queryAllDeepFilterRows(): Cursor

    /**
     * Rewrites a legacy Parcelable row as JSON once
     * [yuku.alkitab.base.storage.SongDb] has converted it. Idempotent.
     */
    @Query("UPDATE song_info SET data = :data, dataFormatVersion = 5 WHERE _id = :id")
    abstract fun writeBackJsonSongData(id: Long, data: ByteArray): Int

    @Query(
        "SELECT updateTime FROM song_info WHERE bookName = :bookName AND code = :code LIMIT 1",
    )
    abstract fun findUpdateTimeByBookNameAndCode(bookName: String, code: String): Int?

    @Query("SELECT COUNT(*) FROM song_info")
    abstract fun countAllSongInfos(): Int

    /**
     * Deleting by `bookName` alone, rather than `(bookName, dataFormatVersion)`,
     * is what makes updating a mixed-version book safe: lazy per-row conversion
     * leaves a book holding rows at several `dataFormatVersion`s, and a
     * version-scoped delete would leave the non-matching rows behind,
     * duplicating songs.
     */
    @Transaction
    open fun replaceSongsForBookName(
        bookName: String,
        entities: List<SongInfoEntity>,
    ) {
        deleteSongInfosByBookName(bookName)
        for (entity in entities) {
            insertSongInfo(entity)
        }
    }

    // endregion

    // region SongBookInfo

    @Insert
    abstract fun insertSongBookInfo(entity: SongBookInfoEntity): Long

    @Query("DELETE FROM song_book_info WHERE name = :name")
    abstract fun deleteSongBookInfoByName(name: String): Int

    @Query("SELECT * FROM song_book_info WHERE name = :name LIMIT 1")
    abstract fun findSongBookInfoByName(name: String): SongBookInfoEntity?

    @Query("SELECT * FROM song_book_info ORDER BY name ASC")
    abstract fun listAllSongBookInfos(): List<SongBookInfoEntity>

    @Query("SELECT COUNT(*) FROM song_book_info")
    abstract fun countAllSongBookInfos(): Int

    /** Upsert keyed by `name`, which the schema does not constrain as UNIQUE. */
    @Transaction
    open fun insertOrReplaceSongBookInfo(name: String, title: String?, copyright: String?) {
        deleteSongBookInfoByName(name)
        insertSongBookInfo(
            SongBookInfoEntity(
                _id = 0L,
                name = name,
                title = title,
                copyright = copyright,
            ),
        )
    }

    /**
     * Returns the number of `song_info` rows deleted. The facade runs a `VACUUM`
     * afterwards, outside this transaction.
     */
    @Transaction
    open fun deleteSongBookAndSongs(songBookName: String): Int {
        deleteSongBookInfoByName(songBookName)
        return deleteSongInfosByBookName(songBookName)
    }

    // endregion
}

/** Projection without the `data` BLOB, for the listing paths. */
data class SongInfoMetaRow(
    val bookName: String?,
    val code: String?,
    val title: String?,
    val title_original: String?,
)
