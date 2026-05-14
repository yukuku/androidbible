package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.Table
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `PerVersion` table in `AlkitabDb`
 * (managed by `InternalDbHelper`) into Room's `per_version` table in
 * `AlkitabRoomDb` (managed by [AppDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.per_version_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: any row the user clears (e.g. by resetting per-version settings)
 * would be re-copied from the legacy table on the next launch. See GitHub
 * issue #195.
 *
 * Memory profile: rows are streamed from the legacy cursor straight into
 * Room inside a single `runInTransaction` block. A `settings` cell is a
 * Gson-serialized [yuku.alkitab.base.model.PerVersionSettings] blob, which
 * today is small but is user-controlled and free to grow as new fields are
 * added; streaming keeps the footprint bound by one row at a time.
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
object PerVersionDataMigration {
    private const val TAG = "PerVersionDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        if (Preferences.getBoolean(Prefkey.per_version_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.perVersionDao()

        // Upgrade path: if a future build ever ships the count-based gate
        // accidentally (mirroring the pre-#198 mistakes for marker/version),
        // a non-empty Room table means the copy already happened. Set the
        // flag and bail.
        if (dao.count() > 0) {
            Preferences.setBoolean(Prefkey.per_version_data_migration_v1_done, true)
            return
        }

        val copied = streamCopyInsideTransaction(roomDb, dao, legacyHelper)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.per_version_data_migration_v1_done, true)
        if (copied > 0) {
            AppLog.d(TAG, "Copied $copied per_version row(s) from legacy PerVersion table to Room")
        }
    }

    /**
     * Reads rows from the legacy cursor and inserts them into Room one at a
     * time inside a single Room transaction. Returns the number of rows
     * copied (0 on a fresh install where the legacy table is empty).
     *
     * Why not the REM-10 `dao.insertAll(list)` pattern? The `settings` blob
     * is user-controlled JSON; even though today's payload is tiny, fanning
     * out hundreds of installed-version rows into a single ArrayList would
     * grow with every new field added to `PerVersionSettings`. Streaming
     * through the cursor keeps memory bound by one row.
     */
    private fun streamCopyInsideTransaction(
        roomDb: AppDatabase,
        dao: PerVersionRoomDao,
        legacyHelper: InternalDbHelper,
    ): Int {
        var copied = 0
        legacyHelper.readableDatabase.query(
            Table.PerVersion.tableName(), null, null, null, null, null, "_id ASC",
        ).use { c ->
            val colVersionId = c.getColumnIndexOrThrow(Table.PerVersion.versionId.name)
            val colSettings = c.getColumnIndexOrThrow(Table.PerVersion.settings.name)
            roomDb.runInTransaction {
                while (c.moveToNext()) {
                    // Coalesce a legacy NULL versionId to "" — Room enforces
                    // non-null on this column. A NULL row in the legacy table
                    // would be unreachable by the facade's keyed lookups
                    // anyway, so collapsing it to a placeholder is safer
                    // than dropping it silently.
                    val versionId = c.getString(colVersionId) ?: ""
                    dao.insert(
                        PerVersionEntity(
                            // Don't carry over `_id` — Room assigns a fresh one. Nothing
                            // outside the table referenced the legacy `_id`; the
                            // identity key used by callers is `versionId`.
                            _id = 0L,
                            versionId = versionId,
                            settings = c.getString(colSettings),
                        ),
                    )
                    copied++
                }
            }
        }
        return copied
    }
}
