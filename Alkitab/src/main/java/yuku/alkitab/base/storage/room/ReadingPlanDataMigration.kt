package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `ReadingPlan` and `ReadingPlanProgress`
 * tables in `AlkitabDb` (managed by `InternalDbHelper`) into Room's
 * `reading_plan` / `reading_plan_progress` tables in `AlkitabRoomDb`
 * (managed by [AppDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.reading_plan_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: any plan the user deletes (or any day they un-check) would be
 * re-copied from the legacy table on the next launch. See GitHub issue #195.
 *
 * Memory profile: rows are streamed from each legacy cursor straight into
 * Room inside a single `runInTransaction` block. Reading-plan rows carry a
 * binary RPB blob in the `data` column that can be hundreds of KB per row,
 * so streaming bounds the migration footprint by the largest single plan
 * rather than the sum of all plans.
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
object ReadingPlanDataMigration {
    private const val TAG = "ReadingPlanDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        if (Preferences.getBoolean(Prefkey.reading_plan_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.readingPlanDao()

        // Upgrade path: if a future build ever ships the count-based gate
        // accidentally (mirroring the pre-#198 mistakes for marker/version),
        // a non-empty Room table means the copy already happened. Set the
        // flag and bail.
        if (dao.countAll() > 0 || dao.countAllProgress() > 0) {
            Preferences.setBoolean(Prefkey.reading_plan_data_migration_v1_done, true)
            return
        }

        val (plans, progresses) = streamCopyInsideTransaction(roomDb, dao, legacyHelper)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.reading_plan_data_migration_v1_done, true)
        if (plans > 0 || progresses > 0) {
            AppLog.d(
                TAG,
                "Copied $plans reading_plan row(s) and $progresses reading_plan_progress " +
                    "row(s) from legacy tables to Room",
            )
        }
    }

    /**
     * Reads rows from each legacy cursor and inserts them into Room one at a
     * time inside a single Room transaction. Returns the number of rows
     * copied to each table.
     *
     * Progress rows missing a non-null Room column
     * (`reading_plan_progress_gid`, `reading_code`) are dropped with
     * `?: continue` rather than coalesced. Coalescing multiple legacy NULLs
     * to a placeholder would collide on the
     * `(reading_plan_progress_gid, reading_code)` UNIQUE index and roll the
     * migration back mid-startup — the failure mode Gemini caught on REM-28
     * (#204). In practice the legacy facade always writes both columns with
     * concrete values; the skip is defence in depth for any historical row
     * that somehow ended up NULL.
     */
    private fun streamCopyInsideTransaction(
        roomDb: AppDatabase,
        dao: ReadingPlanRoomDao,
        legacyHelper: InternalDbHelper,
    ): Pair<Int, Int> {
        var plans = 0
        var progresses = 0
        val legacyDb = legacyHelper.readableDatabase
        roomDb.runInTransaction {
            legacyDb.query(
                Db.TABLE_ReadingPlan, null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colVersion = c.getColumnIndexOrThrow(Db.ReadingPlan.version)
                val colName = c.getColumnIndexOrThrow(Db.ReadingPlan.name)
                val colTitle = c.getColumnIndexOrThrow(Db.ReadingPlan.title)
                val colDescription = c.getColumnIndexOrThrow(Db.ReadingPlan.description)
                val colDuration = c.getColumnIndexOrThrow(Db.ReadingPlan.duration)
                val colStartTime = c.getColumnIndexOrThrow(Db.ReadingPlan.startTime)
                val colData = c.getColumnIndexOrThrow(Db.ReadingPlan.data)
                while (c.moveToNext()) {
                    dao.insert(
                        ReadingPlanEntity(
                            // Don't carry over `_id` — Room assigns a fresh
                            // one. Nothing outside the table referenced the
                            // legacy `_id`: the progress table is keyed by a
                            // gid derived from `name`, not by the plan's
                            // primary key.
                            _id = 0L,
                            version = if (c.isNull(colVersion)) null else c.getInt(colVersion),
                            name = c.getString(colName),
                            title = c.getString(colTitle),
                            description = c.getString(colDescription),
                            duration = if (c.isNull(colDuration)) null else c.getInt(colDuration),
                            startTime = if (c.isNull(colStartTime)) null else c.getLong(colStartTime),
                            data = if (c.isNull(colData)) null else c.getBlob(colData),
                        ),
                    )
                    plans++
                }
            }
            legacyDb.query(
                Db.TABLE_ReadingPlanProgress, null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colGid = c.getColumnIndexOrThrow(Db.ReadingPlanProgress.reading_plan_progress_gid)
                val colReadingCode = c.getColumnIndexOrThrow(Db.ReadingPlanProgress.reading_code)
                val colCheckTime = c.getColumnIndexOrThrow(Db.ReadingPlanProgress.checkTime)
                while (c.moveToNext()) {
                    val gid = if (c.isNull(colGid)) continue else c.getString(colGid)
                    val readingCode = if (c.isNull(colReadingCode)) continue else c.getInt(colReadingCode)
                    dao.insertOrReplaceProgress(
                        ReadingPlanProgressEntity(
                            _id = 0L,
                            reading_plan_progress_gid = gid,
                            reading_code = readingCode,
                            checkTime = if (c.isNull(colCheckTime)) null else c.getLong(colCheckTime),
                        ),
                    )
                    progresses++
                }
            }
        }
        return plans to progresses
    }
}
