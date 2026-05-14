package yuku.alkitab.base.storage.room

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `Version` table in `AlkitabDb`
 * (managed by `InternalDbHelper`) into the Room `version` table in
 * `AlkitabRoomDb` (managed by [AppDatabase]).
 *
 * Idempotency is anchored on the persistent
 * [Prefkey.version_data_migration_v1_done] flag rather than on a
 * `dao.count() > 0` check. The count check would resurrect deleted user
 * data: a user who deletes every downloaded version (or one who hasn't
 * downloaded any yet, so the Room table is legitimately empty) would re-copy
 * the legacy rows on every launch. See GitHub issue #195.
 *
 * Crash safety:
 *  - If the Room insert fails mid-flight, Room rolls back and the flag is
 *    never set; the next launch retries.
 *  - If the process is killed after the insert commits but before the flag
 *    is set, the next launch sees Room rows already present and takes the
 *    upgrade-path branch (sets the flag, does not re-copy).
 *
 * The legacy table is intentionally left intact so a future release can
 * audit/rollback. See
 * `docs/superpowers/specs/2026-05-13-rem-11-room-version-table-design.md`
 * for the migration rationale.
 */
object VersionDataMigration {
    private const val TAG = "VersionDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        if (Preferences.getBoolean(Prefkey.version_data_migration_v1_done, false)) {
            return
        }

        val dao = roomDb.versionDao()

        // Upgrade path for users who already migrated under the old
        // count-based code (PR #188) and have not yet seen this version.
        // Room having rows means the copy already happened; set the flag and
        // bail so we don't double-insert.
        if (dao.count() > 0) {
            Preferences.setBoolean(Prefkey.version_data_migration_v1_done, true)
            return
        }

        val rows = readLegacyRows(legacyHelper)
        if (rows.isEmpty()) {
            // No legacy data either (fresh install). Mark done so future
            // launches skip the legacy-read entirely.
            Preferences.setBoolean(Prefkey.version_data_migration_v1_done, true)
            return
        }

        // Bulk insert — Room's `@Insert` runs inside a single transaction, so
        // a crash mid-migration leaves zero rows in Room and the next launch
        // retries cleanly. Per-row inserts would leave a partial state and
        // would not be safely recoverable from the flag-only idempotency
        // check.
        dao.insertAll(rows)
        // Set the flag only after a successful commit. A crash between here
        // and the next launch is handled by the upgrade-path branch above.
        Preferences.setBoolean(Prefkey.version_data_migration_v1_done, true)
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
