package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for the `marker_label` junction table. Consumed by the facade in
 * [yuku.alkitab.base.storage.Marker_LabelDao] and by `InternalDb` transactions
 * that cascade marker/label deletions through this table.
 *
 * Naming convention: this DAO speaks in [MarkerLabelEntity]; the mapping
 * to/from the public `Marker_Label` model lives in the facade so call sites
 * that already use `Marker_Label` don't need to change.
 */
@Dao
interface MarkerLabelRoomDao {

    @Query("SELECT * FROM marker_label")
    fun listAll(): List<MarkerLabelEntity>

    @Query("SELECT COUNT(*) FROM marker_label")
    fun count(): Int

    @Query("SELECT * FROM marker_label WHERE marker_gid = :markerGid")
    fun listByMarkerGid(markerGid: String): List<MarkerLabelEntity>

    @Query("SELECT * FROM marker_label WHERE gid = :gid LIMIT 1")
    fun findByGid(gid: String): MarkerLabelEntity?

    @Query("SELECT COUNT(*) FROM marker_label WHERE label_gid = :labelGid")
    fun countByLabelGid(labelGid: String): Int

    @Insert
    fun insert(entity: MarkerLabelEntity): Long

    /**
     * Bulk insert — runs atomically in a single transaction. Used by
     * [MarkerDataMigration].
     */
    @Insert
    fun insertAll(entities: List<MarkerLabelEntity>)

    @Update
    fun update(entity: MarkerLabelEntity): Int

    @Query("DELETE FROM marker_label WHERE _id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM marker_label WHERE gid = :gid")
    fun deleteByGid(gid: String): Int

    @Query("DELETE FROM marker_label WHERE marker_gid = :markerGid")
    fun deleteByMarkerGid(markerGid: String): Int

    @Query("DELETE FROM marker_label WHERE label_gid = :labelGid")
    fun deleteByLabelGid(labelGid: String): Int
}
