package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `ProgressMark` and `ProgressMarkHistory`
 * tables in `AlkitabDb` (managed by `InternalDbHelper`) into Room's
 * `progress_mark` / `progress_mark_history` tables in `AlkitabRoomDb`
 * (managed by [AppDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.progress_mark_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: any row the user clears (e.g. by overwriting a pin with the empty
 * placeholder) would be re-copied from the legacy table on the next launch.
 * See GitHub issue #195.
 *
 * Memory profile: rows are streamed from each legacy cursor straight into
 * Room inside a single `runInTransaction` block. The progress-mark table is
 * tiny (~5 rows in normal use) but the history table grows monotonically
 * with every pin update, so streaming bounds the migration footprint by one
 * row at a time.
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
object ProgressMarkDataMigration {
    private const val TAG = "ProgressMarkDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        if (Preferences.getBoolean(Prefkey.progress_mark_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.progressMarkDao()

        // Upgrade path: if a future build ever ships the count-based gate
        // accidentally (mirroring the pre-#198 mistakes for marker/version),
        // a non-empty Room table means the copy already happened. Set the
        // flag and bail.
        if (dao.countAll() > 0 || dao.countAllHistory() > 0) {
            Preferences.setBoolean(Prefkey.progress_mark_data_migration_v1_done, true)
            return
        }

        val (marks, histories) = streamCopyInsideTransaction(roomDb, dao, legacyHelper)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.progress_mark_data_migration_v1_done, true)
        if (marks > 0 || histories > 0) {
            AppLog.d(
                TAG,
                "Copied $marks progress_mark row(s) and $histories progress_mark_history " +
                    "row(s) from legacy tables to Room",
            )
        }
    }

    /**
     * Reads rows from each legacy cursor and inserts them into Room one at a
     * time inside a single Room transaction. Returns the number of rows
     * copied to each table.
     *
     * Rows missing a non-null Room column (`preset_id`, `ari`) are dropped.
     * Coalescing a NULL `preset_id` to 0 would collide with the legitimate
     * `preset_id = 0` placeholder row that the legacy schema always carries
     * — dropping is safer than corrupting the placeholder set. The same
     * skip-on-NULL rule covers the history table's `progress_mark_preset_id`
     * and `ari`.
     */
    private fun streamCopyInsideTransaction(
        roomDb: AppDatabase,
        dao: ProgressMarkRoomDao,
        legacyHelper: InternalDbHelper,
    ): Pair<Int, Int> {
        var marks = 0
        var histories = 0
        val legacyDb = legacyHelper.readableDatabase
        roomDb.runInTransaction {
            legacyDb.query(
                Db.TABLE_ProgressMark, null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colPresetId = c.getColumnIndexOrThrow(Db.ProgressMark.preset_id)
                val colCaption = c.getColumnIndexOrThrow(Db.ProgressMark.caption)
                val colAri = c.getColumnIndexOrThrow(Db.ProgressMark.ari)
                val colModifyTime = c.getColumnIndexOrThrow(Db.ProgressMark.modifyTime)
                while (c.moveToNext()) {
                    val presetId = if (c.isNull(colPresetId)) continue else c.getInt(colPresetId)
                    val ari = if (c.isNull(colAri)) continue else c.getInt(colAri)
                    dao.insert(
                        ProgressMarkEntity(
                            // Don't carry over `_id` — Room assigns a fresh one. Nothing
                            // outside the table referenced the legacy `_id`; callers
                            // address rows by `preset_id`.
                            _id = 0L,
                            preset_id = presetId,
                            caption = c.getString(colCaption),
                            ari = ari,
                            modifyTime = if (c.isNull(colModifyTime)) null else c.getInt(colModifyTime),
                        ),
                    )
                    marks++
                }
            }
            legacyDb.query(
                Db.TABLE_ProgressMarkHistory, null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colPresetId = c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_preset_id)
                val colCaption = c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_caption)
                val colAri = c.getColumnIndexOrThrow(Db.ProgressMarkHistory.ari)
                val colCreateTime = c.getColumnIndexOrThrow(Db.ProgressMarkHistory.createTime)
                while (c.moveToNext()) {
                    val presetId = if (c.isNull(colPresetId)) continue else c.getInt(colPresetId)
                    val ari = if (c.isNull(colAri)) continue else c.getInt(colAri)
                    dao.insertHistory(
                        ProgressMarkHistoryEntity(
                            _id = 0L,
                            progress_mark_preset_id = presetId,
                            progress_mark_caption = c.getString(colCaption),
                            ari = ari,
                            createTime = if (c.isNull(colCreateTime)) null else c.getInt(colCreateTime),
                        ),
                    )
                    histories++
                }
            }
        }
        return marks to histories
    }
}
