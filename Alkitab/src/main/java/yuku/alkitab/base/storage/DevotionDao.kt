package yuku.alkitab.base.storage

import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.base.devotion.ArticleMeidA
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish
import yuku.alkitab.base.devotion.ArticleRenunganHarian
import yuku.alkitab.base.devotion.ArticleRoc
import yuku.alkitab.base.devotion.ArticleSantapanHarian
import yuku.alkitab.base.devotion.DevotionArticle
import yuku.alkitab.base.storage.room.AppDatabase
import yuku.alkitab.base.storage.room.DevotionEntity
import yuku.alkitab.base.storage.room.DevotionRoomDao
import yuku.alkitab.base.util.Sqlitil
import java.util.Date

/**
 * Facade over the Room-backed [DevotionRoomDao] that preserves the legacy
 * `DevotionArticle`-based public surface. Existing call sites in [InternalDb]
 * don't need to change.
 *
 * Rows are identified by `(name, date, dataFormatVersion)`. Cached articles
 * with no body are stored as `readyToUse = 0` so a later download can
 * back-fill the body without invalidating the row.
 *
 * Cross-file note: this DAO is constructed with [InternalDbHelper] only so
 * its constructor signature stays unchanged. The helper is no longer used
 * internally; [AppDatabase] supplies the underlying SQLite file. The
 * one-time data copy runs in
 * [yuku.alkitab.base.storage.room.DevotionDataMigration].
 */
@Suppress("UNUSED_PARAMETER")
class DevotionDao(helper: InternalDbHelper) {

    private val roomDao: DevotionRoomDao
        get() = AppDatabase.get(yuku.afw.App.context).devotionDao()

    /**
     * Upserts the article row identified by `(kind.name, date)`. Implemented
     * as delete-then-insert inside a single Room transaction because the
     * table has no uniqueness constraint — see [DevotionRoomDao.upsertByNameAndDate].
     */
    fun storeArticle(article: DevotionArticle) {
        val entity = DevotionEntity(
            _id = 0L,
            name = article.kind.name,
            date = article.date,
            body = if (article.readyToUse) article.body else null,
            readyToUse = if (article.readyToUse) 1 else 0,
            touchTime = Sqlitil.nowDateTime(),
            dataFormatVersion = DATA_FORMAT_VERSION,
        )
        roomDao.upsertByNameAndDate(entity)
    }

    fun deleteWithTouchTimeBefore(date: Date): Int =
        roomDao.deleteWithTouchTimeBefore(Sqlitil.toInt(date))

    /** Returns the stored article, or null if none cached. Non-ready-to-use rows are returned too. */
    fun tryGet(name: String, date: String): DevotionArticle? {
        val row = roomDao.findByNameDateAndDataFormatVersion(name, date, DATA_FORMAT_VERSION)
            ?: return null
        val kind = DevotionActivity.DevotionKind.getByName(name) ?: return null
        val body = row.body
        val readyToUse = row.readyToUse > 0
        return when (kind) {
            DevotionActivity.DevotionKind.RH -> ArticleRenunganHarian(date, body, readyToUse)
            DevotionActivity.DevotionKind.SH -> ArticleSantapanHarian(date, body, readyToUse)
            // Legacy DevotionDao forced `readyToUse = true` for ME_EN — preserve verbatim.
            DevotionActivity.DevotionKind.ME_EN -> ArticleMorningEveningEnglish(date, body, true)
            DevotionActivity.DevotionKind.MEID_A -> ArticleMeidA(date, body, readyToUse)
            DevotionActivity.DevotionKind.ROC -> ArticleRoc(date, body, readyToUse)
        }
    }

    companion object {
        // The legacy DevotionDao hard-coded the value `1` on both write and
        // read sides. Preserve that exact contract so callers don't observe a
        // behaviour change; a future format bump can introduce a v2 alongside.
        private const val DATA_FORMAT_VERSION = 1
    }
}
