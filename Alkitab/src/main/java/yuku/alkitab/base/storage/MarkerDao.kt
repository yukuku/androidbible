package yuku.alkitab.base.storage

import android.content.ContentValues
import android.database.Cursor
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.model.Marker
import java.util.Date

/**
 * Type-safe accessor for primitive reads/writes on the `Marker` table.
 *
 * Scope: CRUD primitives plus [markerFromCursor]/[markerToContentValues]
 * helpers used across [InternalDb].
 *
 * Not in scope: orchestration methods that join `Marker` with `Marker_Label`
 * ([InternalDb.deleteMarkerById], [InternalDb.listMarkers]), cursor-streaming
 * reads with side effects ([InternalDb.putAttributes]), and the
 * highlight-upsert transactions ([InternalDb.updateOrInsertPartialHighlight],
 * [InternalDb.updateOrInsertHighlights]). Those compose the DAO primitives
 * inside their own transactions. Sync-notify side effects remain on the
 * delegators.
 */
class MarkerDao(private val helper: InternalDbHelper) {

    fun getById(_id: Long): Marker? {
        helper.readableDatabase.query(
            Db.TABLE_Marker, null, "_id=?", arrayOf(_id.toString()),
            null, null, null,
        ).use { cursor ->
            return if (cursor.moveToNext()) markerFromCursor(cursor) else null
        }
    }

    fun getByGid(gid: String): Marker? {
        helper.readableDatabase.query(
            Db.TABLE_Marker, null, Db.Marker.gid + "=?", arrayOf(gid),
            null, null, null,
        ).use { cursor ->
            return if (cursor.moveToNext()) markerFromCursor(cursor) else null
        }
    }

    fun listForAriKind(ari: Int, kind: Marker.Kind): List<Marker> {
        val res = ArrayList<Marker>()
        helper.readableDatabase.query(
            Db.TABLE_Marker, null,
            Db.Marker.ari + "=? and " + Db.Marker.kind + "=?",
            arrayOf(ari.toString(), kind.code.toString()),
            null, null, Db.Marker.modifyTime + " desc", null,
        ).use { c ->
            while (c.moveToNext()) res += markerFromCursor(c)
        }
        return res
    }

    fun listAll(): List<Marker> {
        val res = ArrayList<Marker>()
        helper.readableDatabase.query(
            Db.TABLE_Marker, null, null, null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) res += markerFromCursor(c)
        }
        return res
    }

    /** Inserts when `marker._id == 0`, otherwise updates by `_id`. Mutates `marker._id` on insert. */
    fun upsert(marker: Marker) {
        val db = helper.writableDatabase
        if (marker._id != 0L) {
            db.update(Db.TABLE_Marker, markerToContentValues(marker), "_id=?", arrayOf(marker._id.toString()))
        } else {
            marker._id = db.insert(Db.TABLE_Marker, null, markerToContentValues(marker))
        }
    }

    /**
     * Creates a fresh [Marker] (with a new gid), inserts it, and returns it with its assigned _id.
     */
    fun insertNew(
        ari: Int, kind: Marker.Kind, caption: String, verseCount: Int,
        createTime: Date, modifyTime: Date,
    ): Marker {
        val marker = Marker.createNewMarker(ari, kind, caption, verseCount, createTime, modifyTime)
        marker._id = helper.writableDatabase.insert(Db.TABLE_Marker, null, markerToContentValues(marker))
        return marker
    }

    fun deleteById(_id: Long): Int = helper.writableDatabase.delete(
        Db.TABLE_Marker, "_id=?", arrayOf(_id.toString()),
    )

    fun deleteByGid(gid: String): Int = helper.writableDatabase.delete(
        Db.TABLE_Marker, Db.Marker.gid + "=?", arrayOf(gid),
    )

    // Inclusive upper bound so a marker on verse 255 (ari == ariMax) is counted.
    fun countForAriRange(ariMin: Int, ariMax: Int): Int =
        helper.readableDatabase.compileStatement(
            "select count(*) from ${Db.TABLE_Marker}" +
                " where ${Db.Marker.ari}>=? and ${Db.Marker.ari}<=?",
        ).use { stmt ->
            stmt.bindLong(1, ariMin.toLong())
            stmt.bindLong(2, ariMax.toLong())
            stmt.simpleQueryForLong().toInt()
        }

    companion object {
        @JvmStatic
        fun markerFromCursor(cursor: Cursor): Marker = Marker.createEmptyMarker().apply {
            _id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
            gid = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker.gid))
            ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.ari))
            kind = Marker.Kind.fromCode(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.kind)))
            caption = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker.caption))
            verseCount = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.verseCount))
            createTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.createTime)))
            modifyTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.modifyTime)))
        }

        /** `_id` is not stored in the [ContentValues]. */
        @JvmStatic
        fun markerToContentValues(marker: Marker): ContentValues = ContentValues().apply {
            put(Db.Marker.ari, marker.ari)
            put(Db.Marker.gid, marker.gid)
            put(Db.Marker.kind, marker.kind.code)
            put(Db.Marker.caption, marker.caption)
            put(Db.Marker.verseCount, marker.verseCount)
            put(Db.Marker.createTime, Sqlitil.toInt(marker.createTime))
            put(Db.Marker.modifyTime, Sqlitil.toInt(marker.modifyTime))
        }
    }
}
