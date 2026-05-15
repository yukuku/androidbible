package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room DAO for the `song_info` and `song_book_info` tables. Consumed by
 * the facade in [yuku.alkitab.base.storage.SongDb].
 *
 * Two tables, one DAO: both belong to the Songs subsystem and are commonly
 * written together during song-book download
 * (`SongBookUtil.handleDownloadResult` calls `insertSongBookInfo` then
 * `storeSongs`). Keeping them in one DAO mirrors the legacy facade that
 * owned both.
 *
 * The legacy schema does not put a UNIQUE constraint on `song_book_info.name`.
 * The facade's delete-then-insert upsert path is preserved here via
 * [insertOrReplaceSongBookInfo], wrapped in `@Transaction` so the delete
 * and the insert observe a consistent snapshot and roll back together on
 * failure — exact match for the legacy
 * `SQLiteDatabase.beginTransactionNonExclusive` pattern.
 *
 * `storeSongs` is similarly preserved as
 * [replaceSongsForBookNameAndDataFormatVersion]: a `@Transaction` method
 * that deletes every row matching `(bookName, dataFormatVersion)` and
 * re-inserts the supplied list. Each inserted row uses the supplied
 * pre-computed `ordering` index so the legacy "ordering of the songs for
 * display" semantics are exactly preserved.
 */
@Dao
abstract class SongRoomDao {

    // region SongInfo

    @Insert
    abstract fun insertSongInfo(entity: SongInfoEntity): Long

    @Query(
        "DELETE FROM song_info WHERE bookName = :bookName AND dataFormatVersion = :dataFormatVersion",
    )
    abstract fun deleteSongInfosByBookNameAndDataFormatVersion(
        bookName: String,
        dataFormatVersion: Int,
    ): Int

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

    @Query(
        "SELECT dataFormatVersion FROM song_info WHERE bookName = :bookName LIMIT 1",
    )
    abstract fun findDataFormatVersionForBookName(bookName: String): Int?

    @Query(
        "SELECT updateTime FROM song_info WHERE bookName = :bookName AND code = :code LIMIT 1",
    )
    abstract fun findUpdateTimeByBookNameAndCode(bookName: String, code: String): Int?

    @Query("SELECT COUNT(*) FROM song_info")
    abstract fun countAllSongInfos(): Int

    /**
     * Replace every `song_info` row for the given `(bookName, dataFormatVersion)`
     * with the supplied entities. Mirrors the legacy facade's
     * `storeSongs(bookName, songs, dataFormatVersion)` semantics — the
     * count of pre-existing rows is unchanged from the caller's
     * perspective, and the new rows get the caller-assigned `ordering`
     * indexes.
     *
     * Wrapped in `@Transaction` so the delete and the inserts observe a
     * consistent snapshot and roll back together on failure — exact match
     * for the legacy `SQLiteDatabase.beginTransactionNonExclusive` pattern.
     */
    @Transaction
    open fun replaceSongsForBookNameAndDataFormatVersion(
        bookName: String,
        dataFormatVersion: Int,
        entities: List<SongInfoEntity>,
    ) {
        deleteSongInfosByBookNameAndDataFormatVersion(bookName, dataFormatVersion)
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

    /**
     * Upsert keyed by `name`. Mirrors the legacy facade's delete-then-insert
     * pattern under `beginTransactionNonExclusive`. Wrapped in
     * `@Transaction` so the delete and the insert observe a consistent
     * snapshot and roll back together on failure.
     */
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
     * Delete a song book and every song that belongs to it. Returns the
     * number of `song_info` rows deleted, mirroring the legacy facade's
     * return value (`SongDb.deleteSongBook`).
     *
     * Wrapped in `@Transaction` so both deletes commit (or roll back)
     * atomically — exact match for the legacy
     * `beginTransactionNonExclusive` semantics. The legacy facade ran a
     * `VACUUM` after the transaction completed; the new facade preserves
     * that behaviour outside this DAO method.
     */
    @Transaction
    open fun deleteSongBookAndSongs(songBookName: String): Int {
        deleteSongBookInfoByName(songBookName)
        return deleteSongInfosByBookName(songBookName)
    }

    // endregion
}
