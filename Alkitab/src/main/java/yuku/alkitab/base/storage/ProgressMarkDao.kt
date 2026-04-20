package yuku.alkitab.base.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.provider.BaseColumns
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.model.ProgressMark
import yuku.alkitab.model.ProgressMarkHistory

/**
 * Type-safe accessor for the `ProgressMark` and `ProgressMarkHistory` tables.
 * Both live in the same DAO because they are always written together:
 * [insertOrUpdate] writes to history first, then upserts the mark.
 *
 * Schema ownership (create-table / onUpgrade) still lives in [InternalDbHelper].
 *
 * Note: the sync-notify call (`Sync.notifySyncNeeded(SYNC_SET_PINS)`) that
 * follows an upsert is kept in [InternalDb.insertOrUpdateProgressMark] so
 * this DAO stays pure persistence.
 */
class ProgressMarkDao(private val helper: InternalDbHelper) {

    /** Progress marks with `ari == 0` are the five default "empty" slots — excluded. */
    fun listAll(): List<ProgressMark> {
        val res = ArrayList<ProgressMark>()
        helper.readableDatabase.query(
            Db.TABLE_ProgressMark, null, Db.ProgressMark.ari + " != 0",
            null, null, null, null,
        ).use { cursor ->
            while (cursor.moveToNext()) res += progressMarkFromCursor(cursor)
        }
        return res
    }

    /** Matches [listAll]'s `ari != 0` filter. */
    fun countAll(): Int = DatabaseUtils.queryNumEntries(
        helper.readableDatabase, Db.TABLE_ProgressMark, Db.ProgressMark.ari + " != 0",
    ).toInt()

    fun getByPresetId(presetId: Int): ProgressMark? {
        helper.readableDatabase.query(
            Db.TABLE_ProgressMark, null,
            Db.ProgressMark.preset_id + "=?", arrayOf(presetId.toString()),
            null, null, null,
        ).use { cursor ->
            return if (cursor.moveToNext()) progressMarkFromCursor(cursor) else null
        }
    }

    /** Appends a history row, then inserts or updates the mark (by preset_id). */
    fun insertOrUpdate(progressMark: ProgressMark) {
        val db = helper.writableDatabase
        val historyCv = ContentValues().apply {
            put(Db.ProgressMarkHistory.progress_mark_preset_id, progressMark.preset_id)
            put(Db.ProgressMarkHistory.progress_mark_caption, progressMark.caption)
            put(Db.ProgressMarkHistory.ari, progressMark.ari)
            put(Db.ProgressMarkHistory.createTime, Sqlitil.toInt(progressMark.modifyTime))
        }

        db.beginTransactionNonExclusive()
        try {
            db.insert(Db.TABLE_ProgressMarkHistory, null, historyCv)

            val existing = DatabaseUtils.queryNumEntries(
                db, Db.TABLE_ProgressMark,
                Db.ProgressMark.preset_id + "=?", arrayOf(progressMark.preset_id.toString()),
            )
            if (existing > 0) {
                db.update(
                    Db.TABLE_ProgressMark, progressMarkToContentValues(progressMark),
                    Db.ProgressMark.preset_id + "=?", arrayOf(progressMark.preset_id.toString()),
                )
            } else {
                db.insert(Db.TABLE_ProgressMark, null, progressMarkToContentValues(progressMark))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun listHistoryByPresetId(presetId: Int): List<ProgressMarkHistory> {
        helper.readableDatabase.rawQuery(
            "select * from ${Db.TABLE_ProgressMarkHistory}" +
                " where ${Db.ProgressMarkHistory.progress_mark_preset_id}=?" +
                " order by ${Db.ProgressMarkHistory.createTime} asc",
            arrayOf(presetId.toString()),
        ).use { c ->
            val res = ArrayList<ProgressMarkHistory>()
            while (c.moveToNext()) res += progressMarkHistoryFromCursor(c)
            return res
        }
    }

    companion object {
        fun progressMarkFromCursor(c: Cursor): ProgressMark = ProgressMark().apply {
            _id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
            preset_id = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.preset_id))
            caption = c.getString(c.getColumnIndexOrThrow(Db.ProgressMark.caption))
            ari = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.ari))
            modifyTime = Sqlitil.toDate(c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.modifyTime)))
        }

        fun progressMarkToContentValues(progressMark: ProgressMark): ContentValues =
            ContentValues().apply {
                put(Db.ProgressMark.preset_id, progressMark.preset_id)
                put(Db.ProgressMark.caption, progressMark.caption)
                put(Db.ProgressMark.ari, progressMark.ari)
                put(Db.ProgressMark.modifyTime, Sqlitil.toInt(progressMark.modifyTime))
            }

        fun progressMarkHistoryFromCursor(c: Cursor): ProgressMarkHistory =
            ProgressMarkHistory().apply {
                _id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))
                progress_mark_preset_id = c.getInt(
                    c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_preset_id),
                )
                progress_mark_caption = c.getString(
                    c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_caption),
                )
                ari = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.ari))
                createTime = Sqlitil.toDate(
                    c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.createTime)),
                )
            }
    }
}
