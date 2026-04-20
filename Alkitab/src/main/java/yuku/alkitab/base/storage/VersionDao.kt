package yuku.alkitab.base.storage

import android.content.ContentValues
import android.database.DatabaseUtils
import yuku.alkitab.base.model.MVersionDb

/**
 * Type-safe accessor for the `Version` table. Wraps [InternalDbHelper] so
 * callers work with [MVersionDb] objects instead of raw Cursor/ContentValues
 * code scattered across [InternalDb].
 *
 * Schema ownership (create-table / onUpgrade) still lives in
 * [InternalDbHelper]; this class only issues queries and updates against the
 * already-created table.
 */
class VersionDao(private val helper: InternalDbHelper) {

    fun listAll(): List<MVersionDb> {
        val res = ArrayList<MVersionDb>()
        helper.readableDatabase.query(
            Db.TABLE_Version, null, null, null, null, null,
            Db.Version.ordering + " asc",
        ).use { cursor ->
            val colLocale = cursor.getColumnIndexOrThrow(Db.Version.locale)
            val colShortName = cursor.getColumnIndexOrThrow(Db.Version.shortName)
            val colLongName = cursor.getColumnIndexOrThrow(Db.Version.longName)
            val colDescription = cursor.getColumnIndexOrThrow(Db.Version.description)
            val colFilename = cursor.getColumnIndexOrThrow(Db.Version.filename)
            val colPresetName = cursor.getColumnIndexOrThrow(Db.Version.preset_name)
            val colModifyTime = cursor.getColumnIndexOrThrow(Db.Version.modifyTime)
            val colActive = cursor.getColumnIndexOrThrow(Db.Version.active)
            val colOrdering = cursor.getColumnIndexOrThrow(Db.Version.ordering)

            while (cursor.moveToNext()) {
                res += MVersionDb().apply {
                    locale = cursor.getString(colLocale)
                    shortName = cursor.getString(colShortName)
                    longName = cursor.getString(colLongName)
                    description = cursor.getString(colDescription)
                    filename = cursor.getString(colFilename)
                    preset_name = cursor.getString(colPresetName)
                    modifyTime = cursor.getInt(colModifyTime)
                    cache_active = cursor.getInt(colActive) != 0
                    ordering = cursor.getInt(colOrdering)
                }
            }
        }
        return res
    }

    // Matches by preset_name when set, otherwise by filename. Pre-existing
    // quirk: if two rows share a non-null preset_name (possible because
    // insertOrUpdateWithActive dedupes only by filename), both rows flip.
    fun setActive(mv: MVersionDb, active: Boolean) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply { put(Db.Version.active, if (active) 1 else 0) }
        if (mv.preset_name != null) {
            db.update(Db.TABLE_Version, cv, Db.Version.preset_name + "=?", arrayOf(mv.preset_name))
        } else {
            db.update(Db.TABLE_Version, cv, Db.Version.filename + "=?", arrayOf(mv.filename))
        }
    }

    fun getMaxOrdering(): Int {
        val db = helper.readableDatabase
        return DatabaseUtils.longForQuery(
            db, "select max(${Db.Version.ordering}) from ${Db.TABLE_Version}", null,
        ).toInt()
    }

    /**
     * If the [MVersionDb.filename] of [mv] already exists in the table, update
     * is performed instead of insert. In that case, [MVersionDb.ordering] is
     * rewritten with the value already in the row (the passed-in ordering is
     * discarded).
     */
    fun insertOrUpdateWithActive(mv: MVersionDb, active: Boolean) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put(Db.Version.locale, mv.locale)
            put(Db.Version.shortName, mv.shortName)
            put(Db.Version.longName, mv.longName)
            put(Db.Version.description, mv.description)
            put(Db.Version.filename, mv.filename)
            put(Db.Version.preset_name, mv.preset_name)
            put(Db.Version.modifyTime, mv.modifyTime)
            put(Db.Version.active, active)
            put(Db.Version.ordering, mv.ordering)
        }

        db.beginTransactionNonExclusive()
        try {
            db.query(
                Db.TABLE_Version, arrayOf("_id", Db.Version.ordering),
                Db.Version.filename + "=?", arrayOf(mv.filename),
                null, null, null,
            ).use { c ->
                if (c.moveToNext()) {
                    val id = c.getLong(0)
                    val existingOrdering = c.getInt(1)
                    mv.ordering = existingOrdering
                    cv.put(Db.Version.ordering, existingOrdering)
                    db.update(Db.TABLE_Version, cv, "_id=?", arrayOf(id.toString()))
                } else {
                    db.insert(Db.TABLE_Version, null, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(mv: MVersionDb) {
        val db = helper.writableDatabase

        if (mv.preset_name != null) {
            val deleted = db.delete(
                Db.TABLE_Version, Db.Version.preset_name + "=?", arrayOf(mv.preset_name),
            )
            if (deleted > 0) return
        }

        db.delete(Db.TABLE_Version, Db.Version.filename + "=?", arrayOf(mv.filename))
    }
}
