package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * Room DAO for the `label` table. Consumed by the facade in
 * [yuku.alkitab.base.storage.LabelDao] and by `InternalDb.reorderLabels`
 * (via [reorderById]) and `InternalDb.sortLabelsAlphabetically` (via
 * the transactional bulk-update).
 *
 * Naming convention: this DAO speaks in [LabelEntity]; the mapping to/from
 * the public `Label` model lives in the facade so call sites that already
 * use `Label` don't need to change.
 */
@Dao
interface LabelRoomDao {

    @Query("SELECT * FROM label ORDER BY ordering ASC")
    fun listAll(): List<LabelEntity>

    @Query("SELECT COUNT(*) FROM label")
    fun count(): Int

    @Query("SELECT * FROM label WHERE _id = :id LIMIT 1")
    fun findById(id: Long): LabelEntity?

    @Query("SELECT * FROM label WHERE gid = :gid LIMIT 1")
    fun findByGid(gid: String): LabelEntity?

    @Query("SELECT IFNULL(MAX(ordering), 0) FROM label")
    fun getMaxOrdering(): Int

    /**
     * Labels attached to the marker identified by [markerGid], ordered by
     * `Label.ordering ASC`. Mirrors legacy `LabelDao.listByMarker`. The join
     * predicate matches on the gid columns — see the [MarkerLabelEntity]
     * KDoc for why gids are the canonical join keys.
     */
    @Query(
        "SELECT label.* FROM label, marker_label" +
            " WHERE marker_label.label_gid = label.gid" +
            " AND marker_label.marker_gid = :markerGid" +
            " ORDER BY label.ordering ASC",
    )
    fun listByMarkerGid(markerGid: String): List<LabelEntity>

    @Insert
    fun insert(entity: LabelEntity): Long

    /**
     * Bulk insert — runs atomically in a single transaction. Used by
     * [MarkerDataMigration].
     */
    @Insert
    fun insertAll(entities: List<LabelEntity>)

    @Update
    fun update(entity: LabelEntity): Int

    @Query("DELETE FROM label WHERE _id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM label WHERE gid = :gid")
    fun deleteByGid(gid: String): Int

    // Pair-wise ordering updates used by `InternalDb.reorderLabels`.
    // Bounds match the legacy SQL exactly:
    //   move up: `to <= ordering AND ordering < from` → ordering += 1
    //   move down: `from < ordering AND ordering <= to` → ordering -= 1
    @Query("UPDATE label SET ordering = ordering + 1 WHERE :toOrdering <= ordering AND ordering < :fromOrdering")
    fun shiftOrderingUp(fromOrdering: Int, toOrdering: Int): Int

    @Query("UPDATE label SET ordering = ordering - 1 WHERE :fromOrdering < ordering AND ordering <= :toOrdering")
    fun shiftOrderingDown(fromOrdering: Int, toOrdering: Int): Int

    @Query("UPDATE label SET ordering = :ordering WHERE _id = :id")
    fun setOrderingById(id: Long, ordering: Int): Int

    /**
     * Combines a [shiftOrderingUp]/[shiftOrderingDown] with a final
     * ordering update inside a single transaction. Mirrors the body of
     * legacy `InternalDb.reorderLabels` exactly.
     */
    @Transaction
    fun reorderById(id: Long, fromOrdering: Int, toOrdering: Int) {
        when {
            fromOrdering > toOrdering -> shiftOrderingUp(fromOrdering, toOrdering)
            fromOrdering < toOrdering -> shiftOrderingDown(fromOrdering, toOrdering)
        }
        setOrderingById(id, toOrdering)
    }
}
