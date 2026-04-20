package yuku.alkitab.base.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label

/**
 * Type-safe accessor for the `Marker_Label` table — the many-to-many junction
 * between markers and labels.
 *
 * Sync-notify side effects remain in [InternalDb]'s delegators.
 */
class Marker_LabelDao(private val helper: InternalDbHelper) {

    fun listAll(): List<Marker_Label> {
        val res = ArrayList<Marker_Label>()
        helper.readableDatabase.query(
            Db.TABLE_Marker_Label, null, null, null, null, null, null,
        ).use { cursor ->
            while (cursor.moveToNext()) res += marker_LabelFromCursor(cursor)
        }
        return res
    }

    fun listByMarker(marker: Marker): List<Marker_Label> {
        val res = ArrayList<Marker_Label>()
        helper.readableDatabase.query(
            Db.TABLE_Marker_Label, null,
            Db.Marker_Label.marker_gid + "=?", arrayOf(marker.gid),
            null, null, null,
        ).use { cursor ->
            while (cursor.moveToNext()) res += marker_LabelFromCursor(cursor)
        }
        return res
    }

    fun getByGid(gid: String): Marker_Label? {
        helper.readableDatabase.query(
            Db.TABLE_Marker_Label, null,
            Db.Marker_Label.gid + "=?", arrayOf(gid),
            null, null, null,
        ).use { cursor ->
            return if (cursor.moveToNext()) marker_LabelFromCursor(cursor) else null
        }
    }

    /** Inserts when `ml._id == 0`, otherwise updates by _id. Mutates `ml._id` on insert. */
    fun upsert(ml: Marker_Label) {
        val db = helper.writableDatabase
        if (ml._id != 0L) {
            db.update(Db.TABLE_Marker_Label, marker_labelToContentValues(ml), "_id=?", arrayOf(ml._id.toString()))
        } else {
            ml._id = db.insert(Db.TABLE_Marker_Label, null, marker_labelToContentValues(ml))
        }
    }

    /** Unconditional insert. Used by [InternalDb.updateLabels] inside a single transaction. */
    fun insert(ml: Marker_Label): Long =
        helper.writableDatabase.insert(Db.TABLE_Marker_Label, null, marker_labelToContentValues(ml))

    fun deleteById(_id: Long): Int = helper.writableDatabase.delete(
        Db.TABLE_Marker_Label, "_id=?", arrayOf(_id.toString()),
    )

    fun deleteByGid(gid: String): Int = helper.writableDatabase.delete(
        Db.TABLE_Marker_Label, Db.Marker_Label.gid + "=?", arrayOf(gid),
    )

    fun deleteByMarkerGid(markerGid: String): Int = helper.writableDatabase.delete(
        Db.TABLE_Marker_Label, Db.Marker_Label.marker_gid + "=?", arrayOf(markerGid),
    )

    fun deleteByLabelGid(labelGid: String): Int = helper.writableDatabase.delete(
        Db.TABLE_Marker_Label, Db.Marker_Label.label_gid + "=?", arrayOf(labelGid),
    )

    fun countByLabelGid(labelGid: String): Int = DatabaseUtils.longForQuery(
        helper.readableDatabase,
        "select count(*) from ${Db.TABLE_Marker_Label} where ${Db.Marker_Label.label_gid}=?",
        arrayOf(labelGid),
    ).toInt()

    companion object {
        fun marker_LabelFromCursor(c: Cursor): Marker_Label =
            Marker_Label.createEmptyMarker_Label().apply {
                _id = c.getLong(c.getColumnIndexOrThrow("_id"))
                gid = c.getString(c.getColumnIndexOrThrow(Db.Marker_Label.gid))
                marker_gid = c.getString(c.getColumnIndexOrThrow(Db.Marker_Label.marker_gid))
                label_gid = c.getString(c.getColumnIndexOrThrow(Db.Marker_Label.label_gid))
            }

        /** `_id` is not stored in the [ContentValues]. */
        fun marker_labelToContentValues(ml: Marker_Label): ContentValues = ContentValues().apply {
            put(Db.Marker_Label.gid, ml.gid)
            put(Db.Marker_Label.marker_gid, ml.marker_gid)
            put(Db.Marker_Label.label_gid, ml.label_gid)
        }
    }
}
