package yuku.alkitab.base.storage

import yuku.alkitab.base.App
import yuku.alkitab.base.model.PerVersionSettings
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.PerVersionEntity
import yuku.alkitab.base.storage.room.PerVersionRoomDao

/**
 * Facade over the Room-backed [PerVersionRoomDao] that preserves the legacy
 * `PerVersionSettings`-based public surface. Existing call sites in
 * [InternalDb] don't need to change.
 *
 * Rows are identified by `versionId`. The Room table enforces uniqueness on
 * that column, so [storeSettings] uses an `OnConflictStrategy.REPLACE`
 * insert that mirrors the legacy `SQLiteDatabase.replace` call.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.PerVersionDataMigration].
 */
@Suppress("UNUSED_PARAMETER")
class PerVersionDao(helper: InternalDbHelper) {

    private val roomDao: PerVersionRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).perVersionDao()

    /** Returns the stored settings for [versionId], or defaults if no row exists. */
    fun getSettings(versionId: String): PerVersionSettings {
        val row = roomDao.findByVersionId(versionId) ?: return PerVersionSettings.createDefault()
        val json = row.settings ?: return PerVersionSettings.createDefault()
        return App.getDefaultGson().fromJson(json, PerVersionSettings::class.java)
            ?: PerVersionSettings.createDefault()
    }

    fun storeSettings(versionId: String, settings: PerVersionSettings) {
        roomDao.upsert(
            PerVersionEntity(
                _id = 0L,
                versionId = versionId,
                settings = App.getDefaultGson().toJson(settings),
            ),
        )
    }
}
