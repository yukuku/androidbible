package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.Table
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `SyncShadow` and `SyncLog` tables in
 * `AlkitabDb` (managed by `InternalDbHelper`) into Room's `sync_shadow` /
 * `sync_log` tables in `AlkitabRoomDb` (managed by [AppDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.sync_shadow_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: a user who clears a sync shadow from the sync-settings screen
 * would otherwise see it re-copied from the legacy table on the next
 * launch. See GitHub issue #195.
 *
 * Memory profile: rows are streamed from each legacy cursor straight into
 * Room inside a single `runInTransaction` block. A SyncShadow row's `data`
 * blob can be multiple megabytes (full Mabel snapshots routinely exceed
 * the 2 MB CursorWindow limit), so the migration copies one shadow at a
 * time rather than buffering them all up front.
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
object SyncShadowDataMigration {
    private const val TAG = "SyncShadowDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        if (Preferences.getBoolean(Prefkey.sync_shadow_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.syncShadowDao()

        // Upgrade path: if a future build ever ships the count-based gate
        // accidentally (mirroring the pre-#198 mistakes for marker/version),
        // a non-empty Room table means the copy already happened. Set the
        // flag and bail.
        if (dao.countAllShadows() > 0 || dao.countAllLogs() > 0) {
            Preferences.setBoolean(Prefkey.sync_shadow_data_migration_v1_done, true)
            return
        }

        val (shadows, logs) = streamCopyInsideTransaction(roomDb, dao, legacyHelper)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.sync_shadow_data_migration_v1_done, true)
        if (shadows > 0 || logs > 0) {
            AppLog.d(
                TAG,
                "Copied $shadows sync_shadow row(s) and $logs sync_log row(s) " +
                    "from legacy tables to Room",
            )
        }
    }

    /**
     * Reads rows from each legacy cursor and inserts them into Room one at
     * a time inside a single Room transaction. Returns the number of rows
     * copied to each table.
     *
     * Rows missing a non-null Room column (`syncSetName` for shadow,
     * `createTime` / `kind` for log) are dropped with `?: continue` rather
     * than coalesced. The legacy facade always writes both columns with
     * concrete values; the skip is defence in depth for any historical row
     * that somehow ended up NULL.
     */
    private fun streamCopyInsideTransaction(
        roomDb: AppDatabase,
        dao: SyncShadowRoomDao,
        legacyHelper: InternalDbHelper,
    ): Pair<Int, Int> {
        var shadows = 0
        var logs = 0
        val legacyDb = legacyHelper.readableDatabase
        roomDb.runInTransaction {
            legacyDb.query(
                Table.SyncShadow.tableName(),
                null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colSyncSetName = c.getColumnIndexOrThrow(Table.SyncShadow.syncSetName.name)
                val colRevno = c.getColumnIndexOrThrow(Table.SyncShadow.revno.name)
                val colData = c.getColumnIndexOrThrow(Table.SyncShadow.data.name)
                while (c.moveToNext()) {
                    val syncSetName = if (c.isNull(colSyncSetName)) continue else c.getString(colSyncSetName)
                    val revno = if (c.isNull(colRevno)) 0 else c.getInt(colRevno)
                    val data = if (c.isNull(colData)) null else c.getBlob(colData)
                    dao.insertShadow(
                        SyncShadowEntity(
                            _id = 0L,
                            syncSetName = syncSetName,
                            revno = revno,
                            data = data,
                        ),
                    )
                    shadows++
                }
            }
            legacyDb.query(
                Table.SyncLog.tableName(),
                null, null, null, null, null, "_id ASC",
            ).use { c ->
                val colCreateTime = c.getColumnIndexOrThrow(Table.SyncLog.createTime.name)
                val colKind = c.getColumnIndexOrThrow(Table.SyncLog.kind.name)
                val colSyncSetName = c.getColumnIndexOrThrow(Table.SyncLog.syncSetName.name)
                val colParams = c.getColumnIndexOrThrow(Table.SyncLog.params.name)
                while (c.moveToNext()) {
                    val createTime = if (c.isNull(colCreateTime)) continue else c.getInt(colCreateTime)
                    val kind = if (c.isNull(colKind)) continue else c.getInt(colKind)
                    dao.insertLog(
                        SyncLogEntity(
                            _id = 0L,
                            createTime = createTime,
                            kind = kind,
                            syncSetName = if (c.isNull(colSyncSetName)) null else c.getString(colSyncSetName),
                            params = if (c.isNull(colParams)) null else c.getString(colParams),
                        ),
                    )
                    logs++
                }
            }
        }
        return shadows to logs
    }
}
