package yuku.alkitab.base.storage

import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.LabelEntity
import yuku.alkitab.base.storage.room.LabelRoomDao
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker

/**
 * Facade over the Room-backed [LabelRoomDao] that preserves the legacy
 * `Label`-based public surface. Existing call sites in [InternalDb] don't
 * need to change.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.MarkerDataMigration].
 *
 * `listByMarker` cross-joins `label` with `marker_label` — implemented on
 * the Room DAO ([LabelRoomDao.listByMarkerGid]) since both tables now live
 * in the same Room database.
 */
@Suppress("UNUSED_PARAMETER")
class LabelDao(helper: InternalDbHelper) {

    private val roomDao: LabelRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).labelDao()

    fun listAll(): List<Label> = roomDao.listAll().map(::toModel)

    fun getMaxOrdering(): Int = roomDao.getMaxOrdering()

    /**
     * Allocates the next ordering and inserts a fresh [Label]. Returns the
     * created row with its assigned `_id`. Does not send sync notifications
     * — the caller is responsible.
     */
    fun insertNew(title: String, bgColor: String?): Label {
        val res = Label.createNewLabel(title, getMaxOrdering() + 1, bgColor)
        res._id = roomDao.insert(toEntity(res))
        return res
    }

    fun getById(_id: Long): Label? = roomDao.findById(_id)?.let(::toModel)

    fun getByGid(gid: String): Label? = roomDao.findByGid(gid)?.let(::toModel)

    /**
     * Inserts when `label._id == 0`, otherwise updates by `_id`. Mutates
     * `label._id` on insert.
     */
    fun upsert(label: Label) {
        val entity = toEntity(label)
        if (label._id != 0L) {
            roomDao.update(entity)
        } else {
            label._id = roomDao.insert(entity)
        }
    }

    fun deleteByGid(gid: String): Int = roomDao.deleteByGid(gid)

    fun deleteById(_id: Long): Int = roomDao.deleteById(_id)

    /**
     * Labels attached to [marker], ordered by `Label.ordering asc`. The
     * cross-join against `marker_label` happens in the Room query.
     */
    fun listByMarker(marker: Marker): List<Label> =
        roomDao.listByMarkerGid(marker.gid).map(::toModel)

    companion object {
        @JvmStatic
        fun toModel(e: LabelEntity): Label = Label.createEmptyLabel().apply {
            _id = e._id
            gid = e.gid
            title = e.title
            ordering = e.ordering
            backgroundColor = e.backgroundColor
        }

        @JvmStatic
        fun toEntity(label: Label): LabelEntity = LabelEntity(
            _id = label._id,
            gid = label.gid,
            title = label.title,
            ordering = label.ordering,
            backgroundColor = label.backgroundColor,
        )
    }
}
