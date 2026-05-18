package yuku.alkitab.base.storage

import android.content.ContentValues
import android.database.DatabaseUtils
import com.google.gson.reflect.TypeToken
import yuku.alkitab.base.App
import yuku.alkitab.base.model.SyncLog
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.sync.SyncRecorder
import yuku.alkitab.base.util.Sqlitil

/**
 * Type-safe accessor for the `SyncShadow` and `SyncLog` tables. Both belong
 * to the sync subsystem and are commonly written together during sync
 * application.
 *
 * Note: [getBySyncSetName] chunks its blob read because a shadow row can
 * exceed the Android CursorWindow's 2 MB limit.
 */
class SyncShadowDao(private val helper: InternalDbHelper) {

    // region SyncShadow

    /**
     * Reads a sync shadow row, streaming the `data` blob in 1 MB chunks to
     * avoid the system CursorWindow 2 MB cap.
     *
     * Runs inside a transaction so the length probe and chunk reads observe a
     * consistent view of the row.
     */
    fun getBySyncSetName(syncSetName: String): SyncShadow? {
        val db = helper.readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val dataLen: Int
            val id: Long
            val revno: Int

            db.rawQuery(
                "select ${Table.SyncShadow.revno.name}," +
                    " length(${Table.SyncShadow.data.name})," +
                    " _id" +
                    " from ${Table.SyncShadow.tableName()}" +
                    " where ${Table.SyncShadow.syncSetName.name}=?",
                arrayOf(syncSetName),
            ).use { c ->
                if (c.moveToNext()) {
                    revno = c.getInt(0)
                    dataLen = c.getInt(1)
                    id = c.getLong(2)
                } else {
                    return null
                }
            }

            val data = ByteArray(dataLen)
            val chunkSize = 1_000_000
            var i = 0
            while (i < dataLen) {
                db.rawQuery(
                    // sqlite substr func is 1-indexed
                    "select substr(${Table.SyncShadow.data.name}, ${i + 1}, $chunkSize)" +
                        " from ${Table.SyncShadow.tableName()} where _id=?",
                    arrayOf(id.toString()),
                ).use { c ->
                    check(c.moveToNext()) {
                        "Cursor moveToNext returns false, does not make sense, since previous query has indicated that this cursor has rows."
                    }
                    val chunk = c.getBlob(0)
                    if (i + chunk.size != dataLen) {
                        check(chunk.size == chunkSize) {
                            "Not the requested size of chunk retrieved. dataLen=$dataLen i=$i chunk.len=${chunk.size}"
                        }
                        System.arraycopy(chunk, 0, data, i, chunkSize)
                    } else {
                        System.arraycopy(chunk, 0, data, i, chunk.size)
                    }
                }
                i += chunkSize
            }

            db.setTransactionSuccessful()

            return SyncShadow().apply {
                this.syncSetName = syncSetName
                this.revno = revno
                this.data = data
            }
        } finally {
            db.endTransaction()
        }
    }

    fun getRevnoBySyncSetName(syncSetName: String): Int {
        helper.readableDatabase.query(
            Table.SyncShadow.tableName(),
            arrayOf(Table.SyncShadow.revno.name),
            Table.SyncShadow.syncSetName.name + "=?", arrayOf(syncSetName),
            null, null, null,
        ).use { c ->
            return if (c.moveToNext()) c.getInt(0) else 0
        }
    }

    /** Upsert keyed by `syncSetName`. */
    fun insertOrUpdateBySyncSetName(ss: SyncShadow) {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val count = DatabaseUtils.queryNumEntries(
                db, Table.SyncShadow.tableName(),
                Table.SyncShadow.syncSetName.name + "=?", arrayOf(ss.syncSetName),
            )
            if (count > 0) {
                db.update(
                    Table.SyncShadow.tableName(), toContentValues(ss),
                    Table.SyncShadow.syncSetName.name + "=?", arrayOf(ss.syncSetName),
                )
            } else {
                db.insert(Table.SyncShadow.tableName(), null, toContentValues(ss))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteBySyncSetName(syncSetName: String): Int = helper.writableDatabase.delete(
        Table.SyncShadow.tableName(),
        Table.SyncShadow.syncSetName.name + "=?", arrayOf(syncSetName),
    )

    // endregion

    // region SyncLog

    fun insertLog(createTime: Int, kind: SyncRecorder.EventKind, syncSetName: String?, params: String?) {
        val cv = ContentValues(4).apply {
            put(Table.SyncLog.createTime.name, createTime)
            put(Table.SyncLog.kind.name, kind.code)
            put(Table.SyncLog.syncSetName.name, syncSetName)
            put(Table.SyncLog.params.name, params)
        }
        helper.writableDatabase.insert(Table.SyncLog.tableName(), null, cv)
    }

    fun listLatest(maxrows: Int): List<SyncLog> {
        val res = ArrayList<SyncLog>()
        helper.readableDatabase.query(
            Table.SyncLog.tableName(),
            arrayOf(
                Table.SyncLog.createTime.name, Table.SyncLog.kind.name,
                Table.SyncLog.syncSetName.name, Table.SyncLog.params.name,
            ),
            null, null, null, null,
            Table.SyncLog.createTime.name + " desc", maxrows.toString(),
        ).use { c ->
            while (c.moveToNext()) {
                res += SyncLog().apply {
                    createTime = Sqlitil.toDate(c.getInt(0))
                    kind_code = c.getInt(1)
                    syncSetName = c.getString(2)
                    val paramsS = c.getString(3)
                    params = if (paramsS == null) null else App.getDefaultGson().fromJson(paramsS, SYNC_LOG_PARAMS_TYPE)
                }
            }
        }
        return res
    }

    // endregion

    companion object {
        private val SYNC_LOG_PARAMS_TYPE = object : TypeToken<Map<String, Any>>() {}.type

        fun toContentValues(ss: SyncShadow): ContentValues = ContentValues().apply {
            put(Table.SyncShadow.syncSetName.name, ss.syncSetName)
            put(Table.SyncShadow.revno.name, ss.revno)
            put(Table.SyncShadow.data.name, ss.data)
        }
    }
}
