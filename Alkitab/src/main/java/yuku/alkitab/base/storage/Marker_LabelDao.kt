package yuku.alkitab.base.storage

import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.MarkerLabelEntity
import yuku.alkitab.base.storage.room.MarkerLabelRoomDao
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label

/**
 * Facade over the Room-backed [MarkerLabelRoomDao] that preserves the
 * legacy `Marker_Label`-based public surface. Existing call sites in
 * [InternalDb] don't need to change.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.MarkerDataMigration].
 */
@Suppress("UNUSED_PARAMETER")
class Marker_LabelDao(helper: InternalDbHelper) {

    private val roomDao: MarkerLabelRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).markerLabelDao()

    fun listAll(): List<Marker_Label> = roomDao.listAll().map(::toModel)

    fun listByMarker(marker: Marker): List<Marker_Label> =
        roomDao.listByMarkerGid(marker.gid).map(::toModel)

    fun getByGid(gid: String): Marker_Label? = roomDao.findByGid(gid)?.let(::toModel)

    /**
     * Inserts when `ml._id == 0`, otherwise updates by `_id`. Mutates
     * `ml._id` on insert.
     */
    fun upsert(ml: Marker_Label) {
        val entity = toEntity(ml)
        if (ml._id != 0L) {
            roomDao.update(entity)
        } else {
            ml._id = roomDao.insert(entity)
        }
    }

    /**
     * Unconditional insert. Used by `InternalDb.updateLabels` inside a
     * single Room transaction. Returns the newly assigned `_id`.
     */
    fun insert(ml: Marker_Label): Long {
        val newId = roomDao.insert(toEntity(ml))
        ml._id = newId
        return newId
    }

    fun deleteById(_id: Long): Int = roomDao.deleteById(_id)

    fun deleteByGid(gid: String): Int = roomDao.deleteByGid(gid)

    fun deleteByMarkerGid(markerGid: String): Int = roomDao.deleteByMarkerGid(markerGid)

    fun deleteByLabelGid(labelGid: String): Int = roomDao.deleteByLabelGid(labelGid)

    fun countByLabelGid(labelGid: String): Int = roomDao.countByLabelGid(labelGid)

    companion object {
        @JvmStatic
        fun toModel(e: MarkerLabelEntity): Marker_Label =
            Marker_Label.createEmptyMarker_Label().apply {
                _id = e._id
                gid = e.gid
                marker_gid = e.marker_gid
                label_gid = e.label_gid
            }

        @JvmStatic
        fun toEntity(ml: Marker_Label): MarkerLabelEntity = MarkerLabelEntity(
            _id = ml._id,
            gid = ml.gid,
            marker_gid = ml.marker_gid,
            label_gid = ml.label_gid,
        )
    }
}
