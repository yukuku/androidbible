package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.SongDbHelper
import yuku.alkitab.base.storage.Table
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `SongInfo` and `SongBookInfo` tables
 * in the `SongDb` SQLite file (managed by [SongDbHelper]) into Room's
 * `song_info` / `song_book_info` tables in `AlkitabSongRoomDb` (managed by
 * [SongRoomDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.song_db_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: a user who deletes a song book from the songs screen would
 * otherwise see it re-copied from the legacy table on the next launch.
 *
 * Memory profile: rows are streamed from each legacy cursor straight into
 * Room inside a single `runInTransaction` block. Songs are small (the
 * `data` BLOB is a Parcelable-marshalled `Song`, typically a few kB), so
 * row-by-row streaming bounds memory by the largest single row rather
 * than the full table.
 *
 * Crash safety:
 *  - If any insert fails mid-flight, Room rolls back the whole transaction
 *    and the flag is never set; the next launch retries.
 *  - If the process is killed after the transaction commits but before the
 *    flag is set, the next launch sees Room rows already present and takes
 *    the upgrade-path branch (sets the flag, does not re-copy).
 *
 * The legacy tables are intentionally left intact so a future release can
 * audit/rollback.
 */
object SongDbDataMigration {
    private const val TAG = "SongDbDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: SongRoomDatabase, legacyHelper: SongDbHelper) {
        if (Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.songRoomDao()

        // Upgrade path: the flag is unset but Room already holds rows, so the
        // copy has already happened (an install that migrated before the flag
        // existed, or a process kill between the commit and the flag write).
        // Set the flag and bail rather than copying a second time.
        if (dao.countAllSongInfos() > 0 || dao.countAllSongBookInfos() > 0) {
            Preferences.setBoolean(Prefkey.song_db_data_migration_v1_done, true)
            return
        }

        val (songs, books) = streamCopyInsideTransaction(roomDb, dao, legacyHelper)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.song_db_data_migration_v1_done, true)
        if (songs > 0 || books > 0) {
            AppLog.d(
                TAG,
                "Copied $songs song_info row(s) and $books song_book_info row(s) " +
                    "from legacy SongDb to Room",
            )
        }
    }

    /**
     * Reads rows from each legacy cursor and inserts them into Room one at
     * a time inside a single Room transaction. Returns the number of rows
     * copied to each table.
     *
     * Rows missing a non-null Room column conceptually required by the
     * facade (`bookName` and `code` for `song_info`, `name` for
     * `song_book_info`) are dropped with `?: continue` rather than
     * coalesced. The legacy facade always writes those columns with
     * concrete values; the skip is defence in depth for any historical row
     * that somehow ended up NULL.
     */
    private fun streamCopyInsideTransaction(
        roomDb: SongRoomDatabase,
        dao: SongRoomDao,
        legacyHelper: SongDbHelper,
    ): Pair<Int, Int> {
        var songs = 0
        var books = 0
        val legacyDb = legacyHelper.readableDatabase
        roomDb.runInTransaction {
            legacyDb.query(
                Table.SongInfo.tableName(),
                null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colBookName = c.getColumnIndexOrThrow(Table.SongInfo.bookName.name)
                val colCode = c.getColumnIndexOrThrow(Table.SongInfo.code.name)
                val colTitle = c.getColumnIndexOrThrow(Table.SongInfo.title.name)
                val colTitleOriginal = c.getColumnIndexOrThrow(Table.SongInfo.title_original.name)
                val colOrdering = c.getColumnIndexOrThrow(Table.SongInfo.ordering.name)
                val colDataFormatVersion = c.getColumnIndexOrThrow(Table.SongInfo.dataFormatVersion.name)
                val colData = c.getColumnIndexOrThrow(Table.SongInfo.data.name)
                val colUpdateTime = c.getColumnIndexOrThrow(Table.SongInfo.updateTime.name)
                while (c.moveToNext()) {
                    val bookName = if (c.isNull(colBookName)) continue else c.getString(colBookName)
                    val code = if (c.isNull(colCode)) continue else c.getString(colCode)
                    val title = if (c.isNull(colTitle)) null else c.getString(colTitle)
                    val titleOriginal = if (c.isNull(colTitleOriginal)) null else c.getString(colTitleOriginal)
                    val ordering = if (c.isNull(colOrdering)) 0 else c.getInt(colOrdering)
                    val dataFormatVersion =
                        if (c.isNull(colDataFormatVersion)) 0 else c.getInt(colDataFormatVersion)
                    val data = if (c.isNull(colData)) null else c.getBlob(colData)
                    val updateTime = if (c.isNull(colUpdateTime)) 0 else c.getInt(colUpdateTime)
                    dao.insertSongInfo(
                        SongInfoEntity(
                            _id = 0L,
                            bookName = bookName,
                            code = code,
                            title = title,
                            title_original = titleOriginal,
                            ordering = ordering,
                            dataFormatVersion = dataFormatVersion,
                            data = data,
                            updateTime = updateTime,
                        ),
                    )
                    songs++
                }
            }
            legacyDb.query(
                Table.SongBookInfo.tableName(),
                null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colName = c.getColumnIndexOrThrow(Table.SongBookInfo.name.name)
                val colTitle = c.getColumnIndexOrThrow(Table.SongBookInfo.title.name)
                val colCopyright = c.getColumnIndexOrThrow(Table.SongBookInfo.copyright.name)
                while (c.moveToNext()) {
                    val name = if (c.isNull(colName)) continue else c.getString(colName)
                    val title = if (c.isNull(colTitle)) null else c.getString(colTitle)
                    val copyright = if (c.isNull(colCopyright)) null else c.getString(colCopyright)
                    dao.insertSongBookInfo(
                        SongBookInfoEntity(
                            _id = 0L,
                            name = name,
                            title = title,
                            copyright = copyright,
                        ),
                    )
                    books++
                }
            }
        }
        return songs to books
    }
}
