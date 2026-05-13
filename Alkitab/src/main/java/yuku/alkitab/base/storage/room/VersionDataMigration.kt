package yuku.alkitab.base.storage.room

import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `Version` table in `AlkitabDb`
 * (managed by `InternalDbHelper`) into the Room `version` table in
 * `AlkitabRoomDb` (managed by [AppDatabase]).
 *
 * Idempotent: a no-op when the Room table already has rows. If the copy fails
 * mid-flight, the next launch retries. The legacy table is intentionally left
 * intact so a future release can audit/rollback.
 *
 * See `docs/superpowers/specs/2026-05-13-rem-11-room-version-table-design.md`
 * for the migration rationale.
 */
object VersionDataMigration {
    private const val TAG = "VersionDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        val dao = roomDb.versionDao()
        if (dao.listAll().isNotEmpty()) {
            // Already migrated (or app freshly installed and a previous version
            // wrote to Room directly). Either way: nothing to do.
            return
        }

        val rows = readLegacyRows(legacyHelper)
        if (rows.isEmpty()) {
            // No legacy data either (fresh install). Nothing to copy.
            return
        }

        for (row in rows) {
            dao.insert(row)
        }
        AppLog.d(TAG, "Copied ${rows.size} version row(s) from legacy Version table to Room")
    }

    private fun readLegacyRows(legacyHelper: InternalDbHelper): List<VersionEntity> {
        val res = ArrayList<VersionEntity>()
        legacyHelper.readableDatabase.query(
            Db.TABLE_Version, null, null, null, null, null,
            Db.Version.ordering + " asc",
        ).use { c ->
            val colLocale = c.getColumnIndexOrThrow(Db.Version.locale)
            val colShortName = c.getColumnIndexOrThrow(Db.Version.shortName)
            val colLongName = c.getColumnIndexOrThrow(Db.Version.longName)
            val colDescription = c.getColumnIndexOrThrow(Db.Version.description)
            val colFilename = c.getColumnIndexOrThrow(Db.Version.filename)
            val colPresetName = c.getColumnIndexOrThrow(Db.Version.preset_name)
            val colModifyTime = c.getColumnIndexOrThrow(Db.Version.modifyTime)
            val colActive = c.getColumnIndexOrThrow(Db.Version.active)
            val colOrdering = c.getColumnIndexOrThrow(Db.Version.ordering)
            while (c.moveToNext()) {
                res += VersionEntity(
                    // Don't carry over `_id` — Room assigns a fresh one. Nothing
                    // outside the table referenced the legacy `_id`; the
                    // identity keys used by callers are `filename` and
                    // `preset_name`.
                    _id = 0L,
                    locale = c.getString(colLocale),
                    shortName = c.getString(colShortName),
                    longName = c.getString(colLongName),
                    description = c.getString(colDescription),
                    filename = c.getString(colFilename),
                    preset_name = c.getString(colPresetName),
                    modifyTime = c.getInt(colModifyTime),
                    active = c.getInt(colActive),
                    ordering = c.getInt(colOrdering),
                )
            }
        }
        return res
    }
}
