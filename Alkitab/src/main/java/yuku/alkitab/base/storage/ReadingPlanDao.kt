package yuku.alkitab.base.storage

import android.util.Pair
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.ReadingPlanEntity
import yuku.alkitab.base.storage.room.ReadingPlanProgressEntity
import yuku.alkitab.base.storage.room.ReadingPlanRoomDao
import yuku.alkitab.util.IntArrayList

/**
 * Facade over the Room-backed [ReadingPlanRoomDao] that preserves the
 * legacy `ReadingPlan` / `ReadingPlanProgress` public surface. Existing
 * call sites in [InternalDb] don't need to change.
 *
 * Two tables, one DAO: the legacy facade exposes operations that span both
 * tables (e.g. [replaceProgress], which deletes every existing progress row
 * for a gid before re-inserting the new set). [ReadingPlanRoomDao] mirrors
 * that atomicity with `@Transaction`-annotated bulk operations so a partial
 * failure rolls back; the matching legacy code used
 * `SQLiteDatabase.beginTransactionNonExclusive`.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.ReadingPlanDataMigration].
 *
 * Sync-notify side effects remain in [InternalDb]'s delegators.
 */
@Suppress("UNUSED_PARAMETER")
class ReadingPlanDao(helper: InternalDbHelper) {

    private val roomDao: ReadingPlanRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).readingPlanDao()

    // region ReadingPlan

    fun insert(info: ReadingPlan.ReadingPlanInfo, data: ByteArray): Long =
        roomDao.insert(
            ReadingPlanEntity(
                _id = 0L,
                version = info.version,
                name = info.name,
                title = info.title,
                description = info.description,
                duration = info.duration,
                startTime = info.startTime,
                data = data,
            ),
        )

    fun listAllInfo(): List<ReadingPlan.ReadingPlanInfo> =
        roomDao.listAllMeta().map { row ->
            ReadingPlan.ReadingPlanInfo().apply {
                id = row._id
                // Legacy cursor read used `c.getInt(...)` / `c.getLong(...)`
                // which surfaces 0 / 0L for a NULL column; preserve that
                // behaviour so callers do not see a sudden NPE.
                version = row.version ?: 0
                name = row.name
                title = row.title
                description = row.description
                duration = row.duration ?: 0
                startTime = row.startTime ?: 0L
            }
        }

    fun getNameAndData(_id: Long): Pair<String, ByteArray>? {
        val row = roomDao.findNameAndDataById(_id) ?: return null
        return Pair.create(row.name, row.data)
    }

    fun deleteById(id: Long) {
        roomDao.deleteById(id)
    }

    fun updateStartDate(id: Long, startDate: Long) {
        roomDao.updateStartTime(id, startDate)
    }

    fun listNames(): List<String> = roomDao.listNames()

    // endregion

    // region ReadingPlanProgress

    fun insertOrUpdateProgress(gid: String, readingCode: Int, checkTime: Long) {
        roomDao.insertOrReplaceProgress(
            ReadingPlanProgressEntity(
                _id = 0L,
                reading_plan_progress_gid = gid,
                reading_code = readingCode,
                checkTime = checkTime,
            ),
        )
    }

    fun replaceProgress(gid: String, readingCodes: IntArrayList, checkTime: Long) {
        roomDao.replaceProgress(gid, readingCodes, checkTime)
    }

    fun insertOrUpdateMultipleProgresses(gid: String, readingCodes: IntArrayList, checkTime: Long) {
        roomDao.insertOrUpdateMultipleProgresses(gid, readingCodes, checkTime)
    }

    fun deleteProgress(gid: String, readingCode: Int) {
        roomDao.deleteProgress(gid, readingCode)
    }

    fun deleteAllProgressForGid(gid: String): Int = roomDao.deleteAllProgressForGid(gid)

    /** Map of `gid -> set of done reading codes`. Plans with no progress are absent. */
    fun getProgressSummaryForSync(): Map<String, Set<Int>> {
        val res = HashMap<String, HashSet<Int>>()
        for (row in roomDao.listAllProgressGidAndReadingCode()) {
            res.getOrPut(row.reading_plan_progress_gid) { HashSet() }.add(row.reading_code)
        }
        return res
    }

    fun getAllReadingCodesByProgressGid(gid: String): IntArrayList {
        val res = IntArrayList()
        for (code in roomDao.listReadingCodesForGid(gid)) {
            res.add(code)
        }
        return res
    }

    // endregion
}
