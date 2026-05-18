package yuku.alkitab.base.storage

import android.content.ContentValues
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.base.devotion.ArticleMeidA
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish
import yuku.alkitab.base.devotion.ArticleRenunganHarian
import yuku.alkitab.base.devotion.ArticleRoc
import yuku.alkitab.base.devotion.ArticleSantapanHarian
import yuku.alkitab.base.devotion.DevotionArticle
import yuku.alkitab.base.util.Sqlitil
import java.util.Date

/**
 * Type-safe accessor for the `Devotion` table (cached devotional articles).
 * Rows are identified by `(name, date, dataFormatVersion)`.
 */
class DevotionDao(private val helper: InternalDbHelper) {

    /**
     * Upserts the article row identified by `(kind.name, date)`. Implemented
     * as delete-then-insert inside a transaction because the table has no
     * uniqueness constraint.
     */
    fun storeArticle(article: DevotionArticle) {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put(Table.Devotion.name.name, article.kind.name)
            put(Table.Devotion.date.name, article.date)
            put(Table.Devotion.readyToUse.name, if (article.readyToUse) 1 else 0)
            if (article.readyToUse) {
                put(Table.Devotion.body.name, article.body)
            } else {
                putNull(Table.Devotion.body.name)
            }
            put(Table.Devotion.touchTime.name, Sqlitil.nowDateTime())
            put(Table.Devotion.dataFormatVersion.name, 1)
        }

        db.beginTransactionNonExclusive()
        try {
            db.delete(
                Table.Devotion.tableName(),
                Table.Devotion.name.name + "=? and " + Table.Devotion.date.name + "=?",
                arrayOf(article.kind.name, article.date),
            )
            db.insert(Table.Devotion.tableName(), null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteWithTouchTimeBefore(date: Date): Int {
        return helper.writableDatabase.delete(
            Table.Devotion.tableName(),
            Table.Devotion.touchTime.name + "<?",
            arrayOf(Sqlitil.toInt(date).toString()),
        )
    }

    /** Returns the stored article, or null if none cached. Non-ready-to-use rows are returned too. */
    fun tryGet(name: String, date: String): DevotionArticle? {
        helper.readableDatabase.query(
            Table.Devotion.tableName(),
            null,
            Table.Devotion.name.name + "=? and " + Table.Devotion.date.name + "=? and " +
                Table.Devotion.dataFormatVersion.name + "=?",
            arrayOf(name, date, "1"),
            null, null, null,
        ).use { c ->
            if (!c.moveToNext()) return null
            val colBody = c.getColumnIndexOrThrow(Table.Devotion.body.name)
            val colReadyToUse = c.getColumnIndexOrThrow(Table.Devotion.readyToUse.name)
            val body = c.getString(colBody)
            val readyToUse = c.getInt(colReadyToUse) > 0

            val kind = DevotionActivity.DevotionKind.getByName(name) ?: return null
            return when (kind) {
                DevotionActivity.DevotionKind.RH -> ArticleRenunganHarian(date, body, readyToUse)
                DevotionActivity.DevotionKind.SH -> ArticleSantapanHarian(date, body, readyToUse)
                DevotionActivity.DevotionKind.ME_EN -> ArticleMorningEveningEnglish(date, body, true)
                DevotionActivity.DevotionKind.MEID_A -> ArticleMeidA(date, body, readyToUse)
                DevotionActivity.DevotionKind.ROC -> ArticleRoc(date, body, readyToUse)
            }
        }
    }
}
