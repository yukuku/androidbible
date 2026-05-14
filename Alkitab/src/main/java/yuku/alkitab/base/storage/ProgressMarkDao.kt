package yuku.alkitab.base.storage

import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.ProgressMarkEntity
import yuku.alkitab.base.storage.room.ProgressMarkHistoryEntity
import yuku.alkitab.base.storage.room.ProgressMarkRoomDao
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.model.ProgressMark
import yuku.alkitab.model.ProgressMarkHistory

/**
 * Facade over the Room-backed [ProgressMarkRoomDao] that preserves the
 * legacy `ProgressMark` / `ProgressMarkHistory` public surface. Existing
 * call sites in [InternalDb] don't need to change.
 *
 * Rows in `progress_mark` are addressed by `preset_id`. The Room schema
 * keeps the legacy non-unique index — the facade's
 * `insertHistoryAndUpsertMark` does its own existence check inside a
 * `@Transaction`, mirroring the legacy `beginTransactionNonExclusive`
 * pattern.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.ProgressMarkDataMigration].
 *
 * Note: the sync-notify call (`Sync.notifySyncNeeded(SYNC_SET_PINS)`) that
 * follows an upsert is kept in [InternalDb.insertOrUpdateProgressMark] so
 * this DAO stays pure persistence.
 */
@Suppress("UNUSED_PARAMETER")
class ProgressMarkDao(helper: InternalDbHelper) {

    private val roomDao: ProgressMarkRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).progressMarkDao()

    /** Progress marks with `ari == 0` are the five default "empty" slots — excluded. */
    fun listAll(): List<ProgressMark> =
        roomDao.listAllWithNonZeroAri().map { it.toModel() }

    /** Matches [listAll]'s `ari != 0` filter. */
    fun countAll(): Int = roomDao.countAllWithNonZeroAri()

    fun getByPresetId(presetId: Int): ProgressMark? =
        roomDao.findByPresetId(presetId)?.toModel()

    /** Appends a history row, then inserts or updates the mark (by preset_id). */
    fun insertOrUpdate(progressMark: ProgressMark) {
        val createTime = Sqlitil.toInt(progressMark.modifyTime)
        roomDao.insertHistoryAndUpsertMark(
            history = ProgressMarkHistoryEntity(
                _id = 0L,
                progress_mark_preset_id = progressMark.preset_id,
                progress_mark_caption = progressMark.caption,
                ari = progressMark.ari,
                createTime = createTime,
            ),
            mark = ProgressMarkEntity(
                _id = 0L,
                preset_id = progressMark.preset_id,
                caption = progressMark.caption,
                ari = progressMark.ari,
                modifyTime = Sqlitil.toInt(progressMark.modifyTime),
            ),
        )
    }

    fun listHistoryByPresetId(presetId: Int): List<ProgressMarkHistory> =
        roomDao.listHistoryByPresetId(presetId).map { it.toModel() }

    private fun ProgressMarkEntity.toModel(): ProgressMark = ProgressMark().also { m ->
        m._id = _id
        m.preset_id = preset_id
        m.caption = caption
        m.ari = ari
        // Legacy cursor read used `getInt` which returns 0 for NULL — round-trip
        // that as epoch (Date(0)) to preserve behaviour for the placeholder rows
        // that the table is seeded with.
        m.modifyTime = Sqlitil.toDate(modifyTime ?: 0)
    }

    private fun ProgressMarkHistoryEntity.toModel(): ProgressMarkHistory = ProgressMarkHistory().also { h ->
        h._id = _id
        h.progress_mark_preset_id = progress_mark_preset_id
        h.progress_mark_caption = progress_mark_caption
        h.ari = ari
        h.createTime = Sqlitil.toDate(createTime ?: 0)
    }
}
