package yuku.alkitab.base.storage

import android.content.ContentValues
import yuku.alkitab.base.App
import yuku.alkitab.base.model.PerVersionSettings

/**
 * Type-safe accessor for the `PerVersion` table. Each row stores a JSON blob
 * of [PerVersionSettings] keyed by `versionId`.
 */
class PerVersionDao(private val helper: InternalDbHelper) {

    /** Returns the stored settings for [versionId], or defaults if no row exists. */
    fun getSettings(versionId: String): PerVersionSettings {
        helper.readableDatabase.query(
            Table.PerVersion.tableName(),
            arrayOf(Table.PerVersion.settings.name),
            Table.PerVersion.versionId.name + "=?",
            arrayOf(versionId),
            null, null, null,
        ).use { c ->
            return if (c.moveToNext()) {
                App.getDefaultGson().fromJson(c.getString(0), PerVersionSettings::class.java)
            } else {
                PerVersionSettings.createDefault()
            }
        }
    }

    fun storeSettings(versionId: String, settings: PerVersionSettings) {
        val cv = ContentValues().apply {
            put(Table.PerVersion.versionId.name, versionId)
            put(Table.PerVersion.settings.name, App.getDefaultGson().toJson(settings))
        }
        helper.writableDatabase.replace(Table.PerVersion.tableName(), null, cv)
    }
}
