package yuku.alkitab.base.storage

import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.VersionEntity
import yuku.alkitab.base.storage.room.VersionRoomDao

/**
 * Facade over the Room-backed [VersionRoomDao] that preserves the legacy
 * [MVersionDb]-based public surface. Existing call sites in [InternalDb], `S`,
 * `IsiActivity`, etc. don't need to change — they continue calling
 * [listAll]/[setActive]/[insertOrUpdateWithActive]/[delete]/[getMaxOrdering]
 * exactly as before, but the underlying storage is now Room.
 *
 * The pre-existing quirk documented in `VersionDaoTest` — that
 * [insertOrUpdateWithActive] dedupes only by `filename`, so two rows can share
 * the same `preset_name`, and [setActive] (matching by `preset_name` when set)
 * then flips both rows — is preserved verbatim.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so its
 * constructor signature stays unchanged from the legacy DAO. The helper is no
 * longer used internally; [AppDatabase] supplies the underlying SQLite file.
 * The one-time data copy from the legacy `Version` table runs in
 * [yuku.alkitab.base.storage.room.VersionDataMigration].
 */
@Suppress("UNUSED_PARAMETER")
class VersionDao(helper: InternalDbHelper) {

    // Lazy so unit tests that swap the AppDatabase singleton via
    // AppDatabase.resetForTesting() see the new instance.
    private val roomDao: VersionRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).versionDao()

    fun listAll(): List<MVersionDb> = roomDao.listAll().map(::toModel)

    fun setActive(mv: MVersionDb, active: Boolean) {
        val flag = if (active) 1 else 0
        // Pre-existing behavior: when preset_name is set, match by it; if the
        // match touched zero rows, fall through to filename. This matches the
        // legacy SQLite VersionDao.setActive but with an explicit fallback so
        // the test case where a stale preset_name is passed still updates by
        // filename.
        if (mv.preset_name != null) {
            val touched = roomDao.setActiveByPresetName(mv.preset_name, flag)
            if (touched > 0) return
        }
        roomDao.setActiveByFilename(mv.filename, flag)
    }

    fun getMaxOrdering(): Int = roomDao.getMaxOrdering()

    /**
     * Mirrors legacy semantics: if a row with the same `filename` already
     * exists, the existing row's `ordering` is preserved (the caller's
     * `mv.ordering` is overwritten as a side effect); every other column is
     * updated. Otherwise, a fresh row is inserted with the caller's
     * `mv.ordering`.
     */
    fun insertOrUpdateWithActive(mv: MVersionDb, active: Boolean) {
        val entity = toEntity(mv, active)
        val result = roomDao.upsertByFilename(entity)
        // Side effect that callers depend on: keep `mv.ordering` in sync with
        // what landed in the row. On insert, the ordering is what we sent; on
        // update, it's the existing row's ordering.
        mv.ordering = result.ordering
    }

    fun delete(mv: MVersionDb) {
        // Match by preset_name first (when set); on zero rows, fall back to
        // filename — mirrors legacy `VersionDao.delete`.
        if (mv.preset_name != null) {
            val deleted = roomDao.deleteByPresetName(mv.preset_name)
            if (deleted > 0) return
        }
        roomDao.deleteByFilename(mv.filename)
    }

    private fun toModel(e: VersionEntity): MVersionDb = MVersionDb().apply {
        locale = e.locale
        shortName = e.shortName
        longName = e.longName
        description = e.description
        filename = e.filename
        preset_name = e.preset_name
        modifyTime = e.modifyTime
        cache_active = e.active != 0
        ordering = e.ordering
    }

    private fun toEntity(mv: MVersionDb, active: Boolean): VersionEntity = VersionEntity(
        // _id = 0 means "let Room auto-generate"; for the upsert path Room
        // will look up the existing row by filename and reuse its _id.
        _id = 0L,
        locale = mv.locale,
        shortName = mv.shortName,
        longName = mv.longName,
        description = mv.description,
        filename = mv.filename,
        preset_name = mv.preset_name,
        modifyTime = mv.modifyTime,
        active = if (active) 1 else 0,
        ordering = mv.ordering,
    )
}
