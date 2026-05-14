package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.Table
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `Devotion` table in `AlkitabDb`
 * (managed by `InternalDbHelper`) into Room's `devotion` table in
 * `AlkitabRoomDb` (managed by [AppDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.devotion_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: any rows the user (or the eviction sweep in
 * [yuku.alkitab.base.storage.DevotionDao.deleteWithTouchTimeBefore]) drops
 * would be re-copied from the legacy table on the next launch. See GitHub
 * issue #195.
 *
 * Memory profile: rows are streamed from the legacy cursor straight into
 * Room inside a single `runInTransaction` block. A devotion `body` can be
 * a multi-kilobyte HTML payload and the legacy cache may accumulate years
 * of articles, so buffering every row into an `ArrayList` before insert
 * (the REM-10/REM-11 pattern, where individual rows are small) would risk
 * an [OutOfMemoryError] on low-end devices. Streaming keeps the footprint
 * bound by one row at a time.
 *
 * Crash safety:
 *  - If any insert fails mid-flight, Room rolls back the whole transaction
 *    and the flag is never set; the next launch retries.
 *  - If the process is killed after the transaction commits but before the
 *    flag is set, the next launch sees Room rows already present and takes
 *    the upgrade-path branch (sets the flag, does not re-copy).
 *
 * The legacy table is intentionally left intact so a future release can
 * audit/rollback.
 */
object DevotionDataMigration {
    private const val TAG = "DevotionDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        if (Preferences.getBoolean(Prefkey.devotion_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.devotionDao()

        // Upgrade path: if a future build ever ships the count-based gate
        // accidentally (mirroring the pre-#198 mistakes for marker/version),
        // a non-empty Room table means the copy already happened. Set the
        // flag and bail.
        if (dao.count() > 0) {
            Preferences.setBoolean(Prefkey.devotion_data_migration_v1_done, true)
            return
        }

        val copied = streamCopyInsideTransaction(roomDb, dao, legacyHelper)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.devotion_data_migration_v1_done, true)
        if (copied > 0) {
            AppLog.d(TAG, "Copied $copied devotion row(s) from legacy Devotion table to Room")
        }
    }

    /**
     * Reads rows from the legacy cursor and inserts them into Room one at a
     * time inside a single Room transaction. Returns the number of rows
     * copied (0 on a fresh install where the legacy table is empty).
     *
     * Why not the REM-10 `dao.insertAll(list)` pattern? `Devotion.body` can
     * be a multi-kilobyte HTML payload; loading every cached year's worth
     * into a single `ArrayList` before insertion blows up memory on low-end
     * devices. Streaming through the cursor keeps memory bound by one row.
     */
    private fun streamCopyInsideTransaction(
        roomDb: AppDatabase,
        dao: DevotionRoomDao,
        legacyHelper: InternalDbHelper,
    ): Int {
        var copied = 0
        legacyHelper.readableDatabase.query(
            Table.Devotion.tableName(), null, null, null, null, null, "_id ASC",
        ).use { c ->
            val colName = c.getColumnIndexOrThrow(Table.Devotion.name.name)
            val colDate = c.getColumnIndexOrThrow(Table.Devotion.date.name)
            val colBody = c.getColumnIndexOrThrow(Table.Devotion.body.name)
            val colReadyToUse = c.getColumnIndexOrThrow(Table.Devotion.readyToUse.name)
            val colTouchTime = c.getColumnIndexOrThrow(Table.Devotion.touchTime.name)
            val colDataFormatVersion = c.getColumnIndexOrThrow(Table.Devotion.dataFormatVersion.name)
            roomDb.runInTransaction {
                while (c.moveToNext()) {
                    dao.insert(
                        DevotionEntity(
                            // Don't carry over `_id` — Room assigns a fresh one. Nothing
                            // outside the table referenced the legacy `_id`; the
                            // identity key used by callers is `(name, date, dataFormatVersion)`.
                            _id = 0L,
                            name = c.getString(colName),
                            date = c.getString(colDate),
                            body = c.getString(colBody),
                            readyToUse = c.getInt(colReadyToUse),
                            touchTime = c.getInt(colTouchTime),
                            dataFormatVersion = c.getInt(colDataFormatVersion),
                        ),
                    )
                    copied++
                }
            }
        }
        return copied
    }
}
