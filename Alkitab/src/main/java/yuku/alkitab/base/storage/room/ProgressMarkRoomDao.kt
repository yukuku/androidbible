package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Room DAO for the `progress_mark` and `progress_mark_history` tables.
 * Consumed by the facade in [yuku.alkitab.base.storage.ProgressMarkDao].
 *
 * Two tables, one DAO: the facade always writes to history first then upserts
 * the mark inside a transaction (the legacy
 * `SQLiteDatabase.beginTransactionNonExclusive` pattern). [insertHistoryAndUpsertMark]
 * preserves that atomicity using Room's `@Transaction` so partial failures
 * don't leave the two tables out of sync.
 *
 * Naming convention: this DAO speaks in [ProgressMarkEntity] /
 * [ProgressMarkHistoryEntity]; the mapping to/from the public
 * [yuku.alkitab.model.ProgressMark] / [yuku.alkitab.model.ProgressMarkHistory]
 * models lives in the facade so call sites in `InternalDb` don't need to
 * change.
 */
@Dao
abstract class ProgressMarkRoomDao {

    /** Matches the legacy facade's `ari != 0` filter — excludes placeholder rows. */
    @Query("SELECT * FROM progress_mark WHERE ari != 0")
    abstract fun listAllWithNonZeroAri(): List<ProgressMarkEntity>

    /** Matches the legacy facade's `ari != 0` filter. */
    @Query("SELECT COUNT(*) FROM progress_mark WHERE ari != 0")
    abstract fun countAllWithNonZeroAri(): Int

    @Query("SELECT COUNT(*) FROM progress_mark")
    abstract fun countAll(): Int

    @Query("SELECT * FROM progress_mark WHERE preset_id = :presetId LIMIT 1")
    abstract fun findByPresetId(presetId: Int): ProgressMarkEntity?

    @Query("SELECT * FROM progress_mark ORDER BY _id")
    abstract fun listAllForTest(): List<ProgressMarkEntity>

    @Insert
    abstract fun insert(entity: ProgressMarkEntity): Long

    @Update
    abstract fun update(entity: ProgressMarkEntity)

    @Insert
    abstract fun insertHistory(entity: ProgressMarkHistoryEntity): Long

    @Query(
        "SELECT * FROM progress_mark_history " +
            "WHERE progress_mark_preset_id = :presetId " +
            "ORDER BY createTime ASC",
    )
    abstract fun listHistoryByPresetId(presetId: Int): List<ProgressMarkHistoryEntity>

    @Query("SELECT COUNT(*) FROM progress_mark_history")
    abstract fun countAllHistory(): Int

    @Query("SELECT * FROM progress_mark_history ORDER BY _id")
    abstract fun listAllHistoryForTest(): List<ProgressMarkHistoryEntity>

    /**
     * Mirrors the legacy facade's transactional insert-or-update: append a
     * history row, then either update the existing mark with the same
     * `preset_id` or insert a new one. Atomic via Room's `@Transaction`.
     */
    @Transaction
    open fun insertHistoryAndUpsertMark(
        history: ProgressMarkHistoryEntity,
        mark: ProgressMarkEntity,
    ) {
        insertHistory(history)
        val existing = findByPresetId(mark.preset_id)
        if (existing != null) {
            update(mark.copy(_id = existing._id))
        } else {
            insert(mark)
        }
    }
}
