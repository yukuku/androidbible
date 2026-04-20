package yuku.alkitab.base.storage

import android.content.ContentValues
import android.util.Pair
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.util.IntArrayList

/**
 * Type-safe accessor for the `ReadingPlan` and `ReadingPlanProgress` tables.
 * Both tables belong to the same feature module and are commonly written
 * together, so they share a DAO.
 *
 * Sync-notify side effects remain in [InternalDb]'s delegators.
 */
class ReadingPlanDao(private val helper: InternalDbHelper) {

    // region ReadingPlan

    fun insert(info: ReadingPlan.ReadingPlanInfo, data: ByteArray): Long {
        val cv = ContentValues().apply {
            put(Db.ReadingPlan.version, info.version)
            put(Db.ReadingPlan.name, info.name)
            put(Db.ReadingPlan.title, info.title)
            put(Db.ReadingPlan.description, info.description)
            put(Db.ReadingPlan.duration, info.duration)
            put(Db.ReadingPlan.startTime, info.startTime)
            put(Db.ReadingPlan.data, data)
        }
        return helper.writableDatabase.insert(Db.TABLE_ReadingPlan, null, cv)
    }

    fun listAllInfo(): List<ReadingPlan.ReadingPlanInfo> {
        val infos = ArrayList<ReadingPlan.ReadingPlanInfo>()
        helper.readableDatabase.query(
            Db.TABLE_ReadingPlan,
            arrayOf(
                "_id", Db.ReadingPlan.version, Db.ReadingPlan.name, Db.ReadingPlan.title,
                Db.ReadingPlan.description, Db.ReadingPlan.duration, Db.ReadingPlan.startTime,
            ),
            null, null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                infos += ReadingPlan.ReadingPlanInfo().apply {
                    id = c.getLong(0)
                    version = c.getInt(1)
                    name = c.getString(2)
                    title = c.getString(3)
                    description = c.getString(4)
                    duration = c.getInt(5)
                    startTime = c.getLong(6)
                }
            }
        }
        return infos
    }

    fun getNameAndData(_id: Long): Pair<String, ByteArray>? {
        helper.readableDatabase.query(
            Db.TABLE_ReadingPlan,
            arrayOf(Db.ReadingPlan.name, Db.ReadingPlan.data),
            "_id=?", arrayOf(_id.toString()),
            null, null, null,
        ).use { c ->
            return if (c.moveToNext()) Pair.create(c.getString(0), c.getBlob(1)) else null
        }
    }

    fun deleteById(id: Long) {
        helper.writableDatabase.delete(Db.TABLE_ReadingPlan, "_id=?", arrayOf(id.toString()))
    }

    fun updateStartDate(id: Long, startDate: Long) {
        val cv = ContentValues().apply { put(Db.ReadingPlan.startTime, startDate) }
        helper.writableDatabase.update(Db.TABLE_ReadingPlan, cv, "_id=?", arrayOf(id.toString()))
    }

    fun listNames(): List<String> {
        val res = ArrayList<String>()
        helper.readableDatabase.query(
            Db.TABLE_ReadingPlan, arrayOf(Db.ReadingPlan.name),
            null, null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) res += c.getString(0)
        }
        return res
    }

    // endregion

    // region ReadingPlanProgress

    /** Replaces the single progress row identified by `(gid, readingCode)`. */
    fun insertOrUpdateProgress(gid: String, readingCode: Int, checkTime: Long) {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            db.delete(
                Db.TABLE_ReadingPlanProgress,
                Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " +
                    Db.ReadingPlanProgress.reading_code + "=?",
                arrayOf(gid, readingCode.toString()),
            )
            val cv = ContentValues().apply {
                put(Db.ReadingPlanProgress.reading_plan_progress_gid, gid)
                put(Db.ReadingPlanProgress.reading_code, readingCode)
                put(Db.ReadingPlanProgress.checkTime, checkTime)
            }
            db.insert(Db.TABLE_ReadingPlanProgress, null, cv)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Deletes all progress rows for [gid], then inserts the new ones from
     * [readingCodes] with the same [checkTime].
     */
    fun replaceProgress(gid: String, readingCodes: IntArrayList, checkTime: Long) {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            db.delete(
                Db.TABLE_ReadingPlanProgress,
                Db.ReadingPlanProgress.reading_plan_progress_gid + "=?", arrayOf(gid),
            )
            for (i in 0 until readingCodes.size()) {
                val cv = ContentValues().apply {
                    put(Db.ReadingPlanProgress.reading_plan_progress_gid, gid)
                    put(Db.ReadingPlanProgress.reading_code, readingCodes[i])
                    put(Db.ReadingPlanProgress.checkTime, checkTime)
                }
                db.insert(Db.TABLE_ReadingPlanProgress, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Upserts each `(gid, readingCode)` pair in [readingCodes] with the same [checkTime].
     * Existing rows for the same pair are deleted before insert.
     */
    fun insertOrUpdateMultipleProgresses(gid: String, readingCodes: IntArrayList, checkTime: Long) {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val cv = ContentValues().apply {
                put(Db.ReadingPlanProgress.reading_plan_progress_gid, gid)
                put(Db.ReadingPlanProgress.checkTime, checkTime)
            }
            for (i in 0 until readingCodes.size()) {
                val readingCode = readingCodes[i]
                db.delete(
                    Db.TABLE_ReadingPlanProgress,
                    Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " +
                        Db.ReadingPlanProgress.reading_code + "=?",
                    arrayOf(gid, readingCode.toString()),
                )
                cv.put(Db.ReadingPlanProgress.reading_code, readingCode)
                db.insert(Db.TABLE_ReadingPlanProgress, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteProgress(gid: String, readingCode: Int) {
        helper.writableDatabase.delete(
            Db.TABLE_ReadingPlanProgress,
            Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " +
                Db.ReadingPlanProgress.reading_code + "=?",
            arrayOf(gid, readingCode.toString()),
        )
    }

    fun deleteAllProgressForGid(gid: String): Int = helper.writableDatabase.delete(
        Db.TABLE_ReadingPlanProgress,
        Db.ReadingPlanProgress.reading_plan_progress_gid + "=?", arrayOf(gid),
    )

    /** Map of `gid -> set of done reading codes`. Plans with no progress are absent. */
    fun getProgressSummaryForSync(): Map<String, Set<Int>> {
        val res = HashMap<String, HashSet<Int>>()
        helper.readableDatabase.query(
            Db.TABLE_ReadingPlanProgress,
            arrayOf(Db.ReadingPlanProgress.reading_plan_progress_gid, Db.ReadingPlanProgress.reading_code),
            null, null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                val gid = c.getString(0)
                val readingCode = c.getInt(1)
                res.getOrPut(gid) { HashSet() }.add(readingCode)
            }
        }
        return res
    }

    fun getAllReadingCodesByProgressGid(gid: String): IntArrayList {
        val res = IntArrayList()
        helper.readableDatabase.query(
            Db.TABLE_ReadingPlanProgress,
            arrayOf(Db.ReadingPlanProgress.reading_code),
            Db.ReadingPlanProgress.reading_plan_progress_gid + "=?", arrayOf(gid),
            null, null,
            Db.ReadingPlanProgress.reading_code + " asc",
        ).use { c ->
            while (c.moveToNext()) res.add(c.getInt(0))
        }
        return res
    }

    // endregion
}
