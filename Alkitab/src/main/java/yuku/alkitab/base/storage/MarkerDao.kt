package yuku.alkitab.base.storage

import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.MarkerEntity
import yuku.alkitab.base.storage.room.MarkerRoomDao
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.model.Marker
import java.util.Date

/**
 * Facade over the Room-backed [MarkerRoomDao] that preserves the legacy
 * `Marker`-based public surface. Existing call sites in [InternalDb] and
 * `MarkerDao.markerFromCursor` consumers don't need to change — they
 * continue calling [getById]/[upsert]/[insertNew]/etc. exactly as before,
 * but the underlying storage is now Room.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged from the legacy DAO. The helper
 * is no longer used internally; [AppDatabase] supplies the underlying
 * SQLite file. The one-time data copy from the legacy `Marker` table runs
 * in [yuku.alkitab.base.storage.room.MarkerDataMigration].
 *
 * Static `markerFromCursor` / `markerToContentValues` helpers from the
 * pre-REM-10 version are intentionally removed — every InternalDb caller
 * that used them now reads `MarkerEntity` from Room and translates via the
 * private `toModel` / `toEntity` helpers below. If a future caller needs
 * direct `Cursor` access for some reason, prefer adding a Room `@Query`
 * over reintroducing cursor plumbing.
 */
@Suppress("UNUSED_PARAMETER")
class MarkerDao(helper: InternalDbHelper) {

    // Lazy so unit tests that swap the AppDatabase singleton via
    // AppDatabase.setForTesting() see the new instance.
    private val roomDao: MarkerRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).markerDao()

    fun getById(_id: Long): Marker? = roomDao.findById(_id)?.let(::toModel)

    fun getByGid(gid: String): Marker? = roomDao.findByGid(gid)?.let(::toModel)

    fun listForAriKind(ari: Int, kind: Marker.Kind): List<Marker> =
        roomDao.listForAriKindOrderedByModifyTimeDesc(ari, kind.code).map(::toModel)

    fun listAll(): List<Marker> = roomDao.listAll().map(::toModel)

    /**
     * Inserts when `marker._id == 0`, otherwise updates by `_id`. Mutates
     * `marker._id` on insert — preserves the legacy side effect that
     * callers (e.g. `InternalDb.updateOrInsertPartialHighlight`) rely on.
     */
    fun upsert(marker: Marker) {
        val entity = toEntity(marker)
        if (marker._id != 0L) {
            roomDao.update(entity)
        } else {
            val newId = roomDao.insert(entity)
            marker._id = newId
        }
    }

    /**
     * Creates a fresh [Marker] (with a new gid), inserts it, and returns it
     * with its assigned `_id`. Mirrors legacy `MarkerDao.insertNew`.
     */
    fun insertNew(
        ari: Int, kind: Marker.Kind, caption: String, verseCount: Int,
        createTime: Date, modifyTime: Date,
    ): Marker {
        val marker = Marker.createNewMarker(ari, kind, caption, verseCount, createTime, modifyTime)
        marker._id = roomDao.insert(toEntity(marker))
        return marker
    }

    fun deleteById(_id: Long): Int = roomDao.deleteById(_id)

    fun deleteByGid(gid: String): Int = roomDao.deleteByGid(gid)

    // Inclusive upper bound so a marker on verse 255 (ari == ariMax) is counted.
    fun countForAriRange(ariMin: Int, ariMax: Int): Int =
        roomDao.countInAriRangeInclusive(ariMin, ariMax)

    companion object {
        @JvmStatic
        fun toModel(e: MarkerEntity): Marker = Marker.createEmptyMarker().apply {
            _id = e._id
            gid = e.gid
            ari = e.ari
            kind = Marker.Kind.fromCode(e.kind)
            caption = e.caption
            verseCount = e.verseCount
            createTime = Sqlitil.toDate(e.createTime)
            modifyTime = Sqlitil.toDate(e.modifyTime)
        }

        @JvmStatic
        fun toEntity(marker: Marker): MarkerEntity = MarkerEntity(
            _id = marker._id,
            gid = marker.gid,
            ari = marker.ari,
            kind = marker.kind.code,
            caption = marker.caption,
            verseCount = marker.verseCount,
            createTime = Sqlitil.toInt(marker.createTime),
            modifyTime = Sqlitil.toInt(marker.modifyTime),
        )
    }
}
