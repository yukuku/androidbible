package yuku.alkitab.base.storage

import android.content.ContentValues
import android.database.Cursor
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker

/**
 * Type-safe accessor for the `Label` table. Cross-table reads against
 * `Marker_Label` (for [listByMarker]) live here since the result type is
 * `Label`; pure `Marker_Label` operations live in [Marker_LabelDao].
 *
 * Sync-notify side effects remain in [InternalDb]'s delegators so the DAO
 * stays pure persistence. Multi-step flows that must run in a single
 * transaction (`updateLabels`, `deleteLabelAndMarker_LabelsByLabelId`,
 * `sortLabelsAlphabetically`, `reorderLabels`) also remain in [InternalDb]
 * and call DAO primitives.
 */
class LabelDao(private val helper: InternalDbHelper) {

    fun listAll(): List<Label> {
        val res = ArrayList<Label>()
        helper.readableDatabase.query(
            Db.TABLE_Label, null, null, null, null, null,
            Db.Label.ordering + " asc",
        ).use { cursor ->
            while (cursor.moveToNext()) res += labelFromCursor(cursor)
        }
        return res
    }

    fun getMaxOrdering(): Int {
        val db = helper.readableDatabase
        db.compileStatement("select max(${Db.Label.ordering}) from ${Db.TABLE_Label}").use { stmt ->
            return stmt.simpleQueryForLong().toInt()
        }
    }

    /**
     * Allocates the next ordering and inserts a fresh [Label]. Returns the
     * created row with its assigned `_id`. Does not send sync notifications —
     * the caller is responsible.
     */
    fun insertNew(title: String, bgColor: String?): Label {
        val res = Label.createNewLabel(title, getMaxOrdering() + 1, bgColor)
        res._id = helper.writableDatabase.insert(Db.TABLE_Label, null, labelToContentValues(res))
        return res
    }

    fun getById(_id: Long): Label? {
        helper.readableDatabase.query(
            Db.TABLE_Label, null, "_id=?", arrayOf(_id.toString()),
            null, null, null,
        ).use { cursor ->
            return if (cursor.moveToNext()) labelFromCursor(cursor) else null
        }
    }

    fun getByGid(gid: String): Label? {
        helper.readableDatabase.query(
            Db.TABLE_Label, null, Db.Label.gid + "=?", arrayOf(gid),
            null, null, null,
        ).use { cursor ->
            return if (cursor.moveToNext()) labelFromCursor(cursor) else null
        }
    }

    /** Inserts when `label._id == 0`, otherwise updates by _id. Mutates `label._id` on insert. */
    fun upsert(label: Label) {
        val db = helper.writableDatabase
        if (label._id != 0L) {
            db.update(Db.TABLE_Label, labelToContentValues(label), "_id=?", arrayOf(label._id.toString()))
        } else {
            label._id = db.insert(Db.TABLE_Label, null, labelToContentValues(label))
        }
    }

    fun deleteByGid(gid: String): Int = helper.writableDatabase.delete(
        Db.TABLE_Label, Db.Label.gid + "=?", arrayOf(gid),
    )

    fun deleteById(_id: Long): Int = helper.writableDatabase.delete(
        Db.TABLE_Label, "_id=?", arrayOf(_id.toString()),
    )

    /**
     * Labels attached to [marker], ordered by `Label.ordering asc`. Cross-joins
     * `Label` against `Marker_Label`.
     */
    fun listByMarker(marker: Marker): List<Label> {
        val res = ArrayList<Label>()
        helper.readableDatabase.rawQuery(
            "select ${Db.TABLE_Label}.* from ${Db.TABLE_Label}, ${Db.TABLE_Marker_Label}" +
                " where ${Db.TABLE_Marker_Label}.${Db.Marker_Label.label_gid}" +
                " = ${Db.TABLE_Label}.${Db.Label.gid}" +
                " and ${Db.TABLE_Marker_Label}.${Db.Marker_Label.marker_gid}=?" +
                " order by ${Db.TABLE_Label}.${Db.Label.ordering} asc",
            arrayOf(marker.gid),
        ).use { cursor ->
            while (cursor.moveToNext()) res += labelFromCursor(cursor)
        }
        return res
    }

    companion object {
        fun labelFromCursor(c: Cursor): Label = Label.createEmptyLabel().apply {
            _id = c.getLong(c.getColumnIndexOrThrow("_id"))
            gid = c.getString(c.getColumnIndexOrThrow(Db.Label.gid))
            title = c.getString(c.getColumnIndexOrThrow(Db.Label.title))
            ordering = c.getInt(c.getColumnIndexOrThrow(Db.Label.ordering))
            backgroundColor = c.getString(c.getColumnIndexOrThrow(Db.Label.backgroundColor))
        }

        /** `_id` is not stored in the [ContentValues]. */
        fun labelToContentValues(label: Label): ContentValues = ContentValues().apply {
            put(Db.Label.gid, label.gid)
            put(Db.Label.title, label.title)
            put(Db.Label.ordering, label.ordering)
            put(Db.Label.backgroundColor, label.backgroundColor)
        }
    }
}
