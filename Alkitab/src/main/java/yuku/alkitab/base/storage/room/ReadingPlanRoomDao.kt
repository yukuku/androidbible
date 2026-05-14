package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import yuku.alkitab.util.IntArrayList

/**
 * Room DAO for the `reading_plan` and `reading_plan_progress` tables.
 * Consumed by the facade in [yuku.alkitab.base.storage.ReadingPlanDao].
 *
 * Two tables, one DAO: the legacy facade exposes operations that span both
 * tables (e.g. [replaceProgress] which deletes every existing row for a gid
 * before re-inserting the new set). [replaceProgress] and
 * [insertOrUpdateMultipleProgresses] are `@Transaction`-annotated so a
 * partial failure rolls back atomically, matching the legacy
 * `SQLiteDatabase.beginTransactionNonExclusive` pattern.
 *
 * Reading-plan blob payloads can be hundreds of KB. To avoid pulling them
 * into list queries the metadata-only projection [ReadingPlanMetaRow] is
 * used by [listAllMeta]; [getNameAndData] is the only path that materialises
 * the binary payload.
 *
 * Naming convention: this DAO speaks in entities / projections; mapping to
 * the public [yuku.alkitab.base.model.ReadingPlan.ReadingPlanInfo] /
 * [android.util.Pair] return types lives in the facade so call sites in
 * `InternalDb` don't need to change.
 */
@Dao
abstract class ReadingPlanRoomDao {

    @Insert
    abstract fun insert(entity: ReadingPlanEntity): Long

    @Query(
        "SELECT _id, version, name, title, description, duration, startTime " +
            "FROM reading_plan ORDER BY _id ASC",
    )
    abstract fun listAllMeta(): List<ReadingPlanMetaRow>

    @Query("SELECT name, data FROM reading_plan WHERE _id = :id LIMIT 1")
    abstract fun findNameAndDataById(id: Long): ReadingPlanNameAndDataRow?

    @Query("SELECT name FROM reading_plan WHERE name IS NOT NULL ORDER BY _id ASC")
    abstract fun listNames(): List<String>

    @Query("DELETE FROM reading_plan WHERE _id = :id")
    abstract fun deleteById(id: Long): Int

    @Query("UPDATE reading_plan SET startTime = :startTime WHERE _id = :id")
    abstract fun updateStartTime(id: Long, startTime: Long): Int

    @Query("SELECT COUNT(*) FROM reading_plan")
    abstract fun countAll(): Int

    @Query("SELECT * FROM reading_plan ORDER BY _id")
    abstract fun listAllForTest(): List<ReadingPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceProgress(entity: ReadingPlanProgressEntity): Long

    @Query(
        "DELETE FROM reading_plan_progress " +
            "WHERE reading_plan_progress_gid = :gid AND reading_code = :readingCode",
    )
    abstract fun deleteProgress(gid: String, readingCode: Int): Int

    @Query("DELETE FROM reading_plan_progress WHERE reading_plan_progress_gid = :gid")
    abstract fun deleteAllProgressForGid(gid: String): Int

    @Query(
        "SELECT reading_plan_progress_gid, reading_code " +
            "FROM reading_plan_progress",
    )
    abstract fun listAllProgressGidAndReadingCode(): List<ReadingPlanProgressSummaryRow>

    @Query(
        "SELECT reading_code FROM reading_plan_progress " +
            "WHERE reading_plan_progress_gid = :gid ORDER BY reading_code ASC",
    )
    abstract fun listReadingCodesForGid(gid: String): List<Int>

    @Query("SELECT COUNT(*) FROM reading_plan_progress")
    abstract fun countAllProgress(): Int

    @Query("SELECT * FROM reading_plan_progress ORDER BY _id")
    abstract fun listAllProgressForTest(): List<ReadingPlanProgressEntity>

    /**
     * Drops every progress row for [gid] then writes one row per code in
     * [readingCodes], all under a single Room transaction so a partial
     * failure rolls back. Mirrors the legacy facade's
     * `db.beginTransactionNonExclusive` / `db.delete` / `db.insert` block.
     */
    @Transaction
    open fun replaceProgress(gid: String, readingCodes: IntArrayList, checkTime: Long) {
        deleteAllProgressForGid(gid)
        for (i in 0 until readingCodes.size()) {
            insertOrReplaceProgress(
                ReadingPlanProgressEntity(
                    _id = 0L,
                    reading_plan_progress_gid = gid,
                    reading_code = readingCodes[i],
                    checkTime = checkTime,
                ),
            )
        }
    }

    /**
     * Upserts each `(gid, readingCode)` pair in [readingCodes] under a single
     * Room transaction. Mirrors the legacy facade's bulk
     * `INSERT OR REPLACE` loop.
     */
    @Transaction
    open fun insertOrUpdateMultipleProgresses(
        gid: String,
        readingCodes: IntArrayList,
        checkTime: Long,
    ) {
        for (i in 0 until readingCodes.size()) {
            insertOrReplaceProgress(
                ReadingPlanProgressEntity(
                    _id = 0L,
                    reading_plan_progress_gid = gid,
                    reading_code = readingCodes[i],
                    checkTime = checkTime,
                ),
            )
        }
    }
}

/**
 * Metadata-only projection for [ReadingPlanRoomDao.listAllMeta]. Drops the
 * binary [ReadingPlanEntity.data] column so listing every plan does not
 * load every plan's RPB blob.
 */
data class ReadingPlanMetaRow(
    val _id: Long,
    val version: Int?,
    val name: String?,
    val title: String?,
    val description: String?,
    val duration: Int?,
    val startTime: Long?,
)

/**
 * Projection for [ReadingPlanRoomDao.findNameAndDataById] — used by the
 * "open this plan for reading" path where the caller wants both the
 * human-readable name and the binary blob and nothing else.
 */
data class ReadingPlanNameAndDataRow(
    val name: String?,
    val data: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReadingPlanNameAndDataRow) return false
        return name == other.name && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Projection for [ReadingPlanRoomDao.listAllProgressGidAndReadingCode] —
 * powers the sync summary in [yuku.alkitab.base.storage.ReadingPlanDao.getProgressSummaryForSync],
 * which groups every reading code by gid across the whole table.
 */
data class ReadingPlanProgressSummaryRow(
    val reading_plan_progress_gid: String,
    val reading_code: Int,
)
