package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room DAO for the `sync_shadow` and `sync_log` tables. Consumed by the
 * facade in [yuku.alkitab.base.storage.SyncShadowDao].
 *
 * Two tables, one DAO: both tables belong to the sync subsystem and are
 * commonly written together during sync application (e.g. a successful
 * Mabel push writes a new shadow and appends an `apply_result` log row).
 * Keeping them in one DAO mirrors the legacy facade that owned both.
 *
 * Naming convention: this DAO speaks in entities / projections; mapping to
 * the public [yuku.alkitab.base.model.SyncShadow] /
 * [yuku.alkitab.base.model.SyncLog] return types lives in the facade so
 * call sites in `InternalDb` don't need to change.
 *
 * The legacy schema does not put a UNIQUE constraint on `syncSetName`. The
 * facade's count-then-update-or-insert upsert path is preserved here via
 * [insertOrUpdateShadow], wrapped in `@Transaction` so the count and the
 * write see a consistent snapshot and roll back together on failure —
 * exact match for the legacy `SQLiteDatabase.beginTransactionNonExclusive`
 * pattern.
 *
 * The blob read in [yuku.alkitab.base.storage.SyncShadowDao.getBySyncSetName]
 * does not go through Room because Room can't express chunked `substr()`
 * cursors. The facade drops down to `SupportSQLiteDatabase` directly for
 * that one path. Every other read/write goes through this DAO.
 */
@Dao
abstract class SyncShadowRoomDao {

    @Insert
    abstract fun insertShadow(entity: SyncShadowEntity): Long

    @Query("SELECT * FROM sync_shadow WHERE syncSetName = :syncSetName LIMIT 1")
    abstract fun findShadowBySyncSetName(syncSetName: String): SyncShadowEntity?

    @Query("SELECT revno FROM sync_shadow WHERE syncSetName = :syncSetName LIMIT 1")
    abstract fun findRevnoBySyncSetName(syncSetName: String): Int?

    @Query(
        "SELECT _id AS _id, revno AS revno, length(data) AS dataLen FROM sync_shadow " +
            "WHERE syncSetName = :syncSetName LIMIT 1",
    )
    abstract fun findShadowSummaryBySyncSetName(syncSetName: String): SyncShadowSummaryRow?

    @Query(
        "UPDATE sync_shadow SET revno = :revno, data = :data WHERE syncSetName = :syncSetName",
    )
    abstract fun updateShadowBySyncSetName(syncSetName: String, revno: Int, data: ByteArray?): Int

    @Query("DELETE FROM sync_shadow WHERE syncSetName = :syncSetName")
    abstract fun deleteShadowBySyncSetName(syncSetName: String): Int

    @Query("SELECT COUNT(*) FROM sync_shadow WHERE syncSetName = :syncSetName")
    abstract fun countShadowsBySyncSetName(syncSetName: String): Int

    @Query("SELECT COUNT(*) FROM sync_shadow")
    abstract fun countAllShadows(): Int

    @Query("SELECT * FROM sync_shadow ORDER BY _id")
    abstract fun listAllShadowsForTest(): List<SyncShadowEntity>

    /**
     * Upsert keyed by `syncSetName`. Mirrors the legacy facade's
     * count-then-update-or-insert pattern under
     * `beginTransactionNonExclusive`. Wrapped in `@Transaction` so the
     * count and the write observe a consistent snapshot and roll back
     * together on failure.
     */
    @Transaction
    open fun insertOrUpdateShadow(syncSetName: String, revno: Int, data: ByteArray?) {
        val existing = countShadowsBySyncSetName(syncSetName)
        if (existing > 0) {
            updateShadowBySyncSetName(syncSetName, revno, data)
        } else {
            insertShadow(
                SyncShadowEntity(
                    _id = 0L,
                    syncSetName = syncSetName,
                    revno = revno,
                    data = data,
                ),
            )
        }
    }

    @Insert
    abstract fun insertLog(entity: SyncLogEntity): Long

    @Query("SELECT * FROM sync_log ORDER BY createTime DESC LIMIT :maxrows")
    abstract fun listLatestLogs(maxrows: Int): List<SyncLogEntity>

    @Query("SELECT COUNT(*) FROM sync_log")
    abstract fun countAllLogs(): Int

    @Query("SELECT * FROM sync_log ORDER BY _id")
    abstract fun listAllLogsForTest(): List<SyncLogEntity>
}

/**
 * Projection for [SyncShadowRoomDao.findShadowSummaryBySyncSetName] — used
 * by [yuku.alkitab.base.storage.SyncShadowDao.getBySyncSetName] to discover
 * the row's `_id`, the `revno`, and the byte length of the blob before
 * walking it in 1 MB chunks via `substr()`. Loading the full blob in this
 * query would defeat the chunking workaround.
 */
data class SyncShadowSummaryRow(
    val _id: Long,
    val revno: Int,
    val dataLen: Int,
)
