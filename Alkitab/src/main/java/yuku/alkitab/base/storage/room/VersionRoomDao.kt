package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Room DAO for the `version` table. Consumed by the facade in
 * [yuku.alkitab.base.storage.VersionDao] and by `InternalDb.reorderVersions`
 * which needs raw pair-wise ordering updates.
 *
 * Naming convention: this DAO speaks in [VersionEntity]; the mapping to/from
 * the public `MVersionDb` model lives in the facade so call sites that
 * already use `MVersionDb` don't need to change.
 */
@Dao
interface VersionRoomDao {

    @Query("SELECT * FROM version ORDER BY ordering ASC")
    fun listAll(): List<VersionEntity>

    @Query("SELECT IFNULL(MAX(ordering), 0) FROM version")
    fun getMaxOrdering(): Int

    @Query("SELECT * FROM version WHERE filename = :filename LIMIT 1")
    fun findByFilename(filename: String): VersionEntity?

    /**
     * Insert and return the assigned `_id`. Use [upsertByFilename] when you
     * want the existing-row dedupe semantics of the legacy
     * `VersionDao.insertOrUpdateWithActive`.
     */
    @Insert
    fun insert(entity: VersionEntity): Long

    @Update
    fun update(entity: VersionEntity): Int

    /**
     * Mirrors legacy `insertOrUpdateWithActive`: dedupes by `filename`. If the
     * filename already exists, the existing row's `ordering` is preserved
     * (the caller's value is ignored) and every other column is overwritten.
     * Otherwise a fresh row is inserted with the caller's `ordering`.
     *
     * Returns the row's `_id` (newly assigned on insert, existing on update)
     * and the ordering that ended up persisted, so the caller can sync that
     * back into its in-memory model — matching the legacy side-effect.
     */
    @Transaction
    fun upsertByFilename(entity: VersionEntity): UpsertResult {
        val filename = entity.filename
            ?: error("upsertByFilename requires a non-null filename")
        val existing = findByFilename(filename)
        return if (existing == null) {
            val id = insert(entity)
            UpsertResult(id = id, ordering = entity.ordering, wasInsert = true)
        } else {
            val merged = entity.copy(_id = existing._id, ordering = existing.ordering)
            update(merged)
            UpsertResult(id = existing._id, ordering = existing.ordering, wasInsert = false)
        }
    }

    @Query("UPDATE version SET active = :active WHERE preset_name = :presetName")
    fun setActiveByPresetName(presetName: String, active: Int): Int

    @Query("UPDATE version SET active = :active WHERE filename = :filename")
    fun setActiveByFilename(filename: String, active: Int): Int

    @Query("DELETE FROM version WHERE preset_name = :presetName")
    fun deleteByPresetName(presetName: String): Int

    @Query("DELETE FROM version WHERE filename = :filename")
    fun deleteByFilename(filename: String): Int

    // Pair-wise ordering updates used by `InternalDb.reorderVersions`. Inclusive/
    // exclusive bounds match the legacy SQL exactly — see InternalDb.reorderVersions.
    @Query("UPDATE version SET ordering = ordering + 1 WHERE :toOrdering <= ordering AND ordering < :fromOrdering")
    fun shiftOrderingUp(fromOrdering: Int, toOrdering: Int): Int

    @Query("UPDATE version SET ordering = ordering - 1 WHERE :fromOrdering < ordering AND ordering <= :toOrdering")
    fun shiftOrderingDown(fromOrdering: Int, toOrdering: Int): Int

    @Query("UPDATE version SET ordering = :ordering WHERE filename = :filename")
    fun setOrderingByFilename(filename: String, ordering: Int): Int

    @Query("DELETE FROM version")
    fun deleteAll(): Int

    /** Combines a [shiftOrderingUp]/[shiftOrderingDown] with a final ordering update inside a single transaction. */
    @Transaction
    fun reorderByFilename(filename: String, fromOrdering: Int, toOrdering: Int) {
        when {
            fromOrdering > toOrdering -> shiftOrderingUp(fromOrdering, toOrdering)
            fromOrdering < toOrdering -> shiftOrderingDown(fromOrdering, toOrdering)
        }
        setOrderingByFilename(filename, toOrdering)
    }

    data class UpsertResult(val id: Long, val ordering: Int, val wasInsert: Boolean)
}
