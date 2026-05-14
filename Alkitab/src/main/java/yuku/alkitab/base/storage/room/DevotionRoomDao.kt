package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room DAO for the `devotion` table. Consumed by the facade in
 * [yuku.alkitab.base.storage.DevotionDao].
 *
 * Naming convention: this DAO speaks in [DevotionEntity]; the mapping to/from
 * the public `DevotionArticle` model lives in the facade so call sites that
 * already use `DevotionArticle` don't need to change.
 */
@Dao
interface DevotionRoomDao {

    @Query("SELECT * FROM devotion")
    fun listAll(): List<DevotionEntity>

    @Query("SELECT COUNT(*) FROM devotion")
    fun count(): Int

    /**
     * Returns the cached row keyed by `(name, date, dataFormatVersion)`, or
     * `null` if none is cached. Mirrors the legacy DevotionDao.tryGet — the
     * dataFormatVersion filter is what the legacy SQL pinned to the literal
     * `1`; expose it here so a future format bump can search past versions.
     */
    @Query(
        "SELECT * FROM devotion" +
            " WHERE name = :name AND date = :date AND dataFormatVersion = :dataFormatVersion" +
            " LIMIT 1",
    )
    fun findByNameDateAndDataFormatVersion(
        name: String,
        date: String,
        dataFormatVersion: Int,
    ): DevotionEntity?

    @Query("DELETE FROM devotion WHERE name = :name AND date = :date")
    fun deleteByNameAndDate(name: String, date: String): Int

    /** Cache eviction: rows older than [touchTime] are removed wholesale. */
    @Query("DELETE FROM devotion WHERE touchTime < :touchTime")
    fun deleteWithTouchTimeBefore(touchTime: Int): Int

    @Insert
    fun insert(entity: DevotionEntity): Long

    /**
     * Bulk insert — runs atomically in a single transaction (Room's `@Insert`
     * default). Used by [DevotionDataMigration] so a partial migration can't
     * leave the table in a half-populated state.
     */
    @Insert
    fun insertAll(entities: List<DevotionEntity>)

    /**
     * Delete-then-insert by `(name, date)`. Mirrors the legacy
     * [yuku.alkitab.base.storage.DevotionDao.storeArticle] semantics: the
     * table has no uniqueness constraint, so the facade upserts by clearing
     * any row that already has the same `(name, date)` and inserting fresh.
     * Wrapped in a single Room transaction so a concurrent reader can't
     * observe an in-between state with zero rows.
     */
    @Transaction
    fun upsertByNameAndDate(entity: DevotionEntity) {
        val name = entity.name
            ?: error("upsertByNameAndDate requires a non-null name")
        val date = entity.date
            ?: error("upsertByNameAndDate requires a non-null date")
        deleteByNameAndDate(name, date)
        insert(entity)
    }
}
