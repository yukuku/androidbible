package yuku.alkitab.base.storage

import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.reflect.TypeToken
import yuku.alkitab.base.App
import yuku.alkitab.base.model.SyncLog
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.SyncLogEntity
import yuku.alkitab.base.storage.room.SyncShadowRoomDao
import yuku.alkitab.base.sync.SyncRecorder
import yuku.alkitab.base.util.Sqlitil

/**
 * Facade over the Room-backed [SyncShadowRoomDao] that preserves the
 * legacy `SyncShadow` / `SyncLog` public surface. Existing call sites in
 * [InternalDb] don't need to change.
 *
 * Two tables, one facade: both belong to the sync subsystem and are
 * commonly written together during sync application (a successful sync
 * push writes a new shadow and appends an `apply_result` log row).
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.SyncShadowDataMigration].
 *
 * Chunked blob read: a sync shadow row's `data` column can exceed the
 * Android CursorWindow's 2 MB limit (full Mabel snapshots routinely cross
 * that mark). Room can't express chunked `substr()` cursors natively, so
 * [getBySyncSetName] drops down to `SupportSQLiteDatabase` raw queries
 * with SQLite's `substr()` to walk the blob in 1 MB chunks under a single
 * transaction — same approach as before, just running against Room's
 * underlying SQLite handle.
 */
@Suppress("UNUSED_PARAMETER")
class SyncShadowDao(helper: InternalDbHelper) {

    private val roomDao: SyncShadowRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).syncShadowDao()

    // region SyncShadow

    /**
     * Reads a sync shadow row, streaming the `data` blob in 1 MB chunks to
     * avoid the system CursorWindow 2 MB cap.
     *
     * Runs inside a transaction so the summary probe and chunk reads
     * observe a consistent view of the row.
     */
    fun getBySyncSetName(syncSetName: String): SyncShadow? {
        val roomDb = AppDatabase.get(yuku.afw.App.context)
        val support = roomDb.openHelper.readableDatabase
        support.beginTransactionNonExclusive()
        try {
            val summary = roomDao.findShadowSummaryBySyncSetName(syncSetName) ?: return null

            val data = ByteArray(summary.dataLen)
            val chunkSize = 1_000_000
            var i = 0
            while (i < summary.dataLen) {
                support.query(
                    SimpleSQLiteQuery(
                        // sqlite substr is 1-indexed
                        "SELECT substr(data, ${i + 1}, $chunkSize) FROM sync_shadow WHERE _id = ?",
                        arrayOf<Any>(summary._id),
                    ),
                ).use { c ->
                    check(c.moveToNext()) {
                        "Cursor moveToNext returns false on a row that summary query found. _id=${summary._id}"
                    }
                    val chunk = c.getBlob(0)
                    if (i + chunk.size != summary.dataLen) {
                        check(chunk.size == chunkSize) {
                            "Not the requested size of chunk retrieved. dataLen=${summary.dataLen} i=$i chunk.len=${chunk.size}"
                        }
                        System.arraycopy(chunk, 0, data, i, chunkSize)
                    } else {
                        System.arraycopy(chunk, 0, data, i, chunk.size)
                    }
                }
                i += chunkSize
            }

            support.setTransactionSuccessful()
            return SyncShadow().apply {
                this.syncSetName = syncSetName
                this.revno = summary.revno
                this.data = data
            }
        } finally {
            support.endTransaction()
        }
    }

    fun getRevnoBySyncSetName(syncSetName: String): Int =
        roomDao.findRevnoBySyncSetName(syncSetName) ?: 0

    /** Upsert keyed by `syncSetName`. */
    fun insertOrUpdateBySyncSetName(ss: SyncShadow) {
        roomDao.insertOrUpdateShadow(ss.syncSetName, ss.revno, ss.data)
    }

    fun deleteBySyncSetName(syncSetName: String): Int =
        roomDao.deleteShadowBySyncSetName(syncSetName)

    // endregion

    // region SyncLog

    fun insertLog(createTime: Int, kind: SyncRecorder.EventKind, syncSetName: String?, params: String?) {
        roomDao.insertLog(
            SyncLogEntity(
                _id = 0L,
                createTime = createTime,
                kind = kind.code,
                syncSetName = syncSetName,
                params = params,
            ),
        )
    }

    fun listLatest(maxrows: Int): List<SyncLog> {
        val rows = roomDao.listLatestLogs(maxrows)
        val res = ArrayList<SyncLog>(rows.size)
        for (e in rows) {
            res += SyncLog().apply {
                createTime = Sqlitil.toDate(e.createTime)
                kind_code = e.kind
                syncSetName = e.syncSetName
                val paramsS = e.params
                params = if (paramsS == null) null else App.getDefaultGson().fromJson(paramsS, SYNC_LOG_PARAMS_TYPE)
            }
        }
        return res
    }

    // endregion

    private companion object {
        private val SYNC_LOG_PARAMS_TYPE = object : TypeToken<Map<String, Any>>() {}.type
    }
}
