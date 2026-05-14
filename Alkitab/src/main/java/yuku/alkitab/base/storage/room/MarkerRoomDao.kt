package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Room DAO for the `marker` table. Consumed by the facade in
 * [yuku.alkitab.base.storage.MarkerDao] and by the `InternalDb` methods that
 * still own multi-step transactions (delete-with-cascade, highlight
 * upsert/dedupe).
 *
 * Naming convention: this DAO speaks in [MarkerEntity]; the mapping to/from
 * the public `Marker` model lives in the facade so call sites that already
 * use `Marker` don't need to change.
 *
 * For the heterogeneous `InternalDb.listMarkers` query — which permutes
 * `(kind, label_id, sortColumn, sortAscending)` — see [listMarkersRaw]. The
 * SQL is built in the facade and passed in via [SupportSQLiteQuery] because
 * Room's `@Query` does not interpolate dynamic ORDER BY columns.
 */
@Dao
interface MarkerRoomDao {

    @Query("SELECT * FROM marker")
    fun listAll(): List<MarkerEntity>

    @Query("SELECT COUNT(*) FROM marker")
    fun count(): Int

    @Query("SELECT * FROM marker WHERE _id = :id LIMIT 1")
    fun findById(id: Long): MarkerEntity?

    @Query("SELECT * FROM marker WHERE gid = :gid LIMIT 1")
    fun findByGid(gid: String): MarkerEntity?

    /**
     * Mirrors legacy `MarkerDao.listForAriKind` — ordered by `modifyTime
     * desc` so callers iterating "newest first" (e.g. the action-mode menu
     * for an ari, [yuku.alkitab.base.storage.InternalDb.getHighlightColorRgb]
     * single-ari overload, the highlight-upsert dedupe loop) get the latest
     * entry first.
     */
    @Query("SELECT * FROM marker WHERE ari = :ari AND kind = :kind ORDER BY modifyTime DESC")
    fun listForAriKindOrderedByModifyTimeDesc(ari: Int, kind: Int): List<MarkerEntity>

    /**
     * Inclusive range — used by
     * [yuku.alkitab.base.storage.InternalDb.countMarkersForBookChapter]
     * and [yuku.alkitab.base.storage.InternalDb.putAttributes]. The
     * legacy SQL used `>= ariMin AND <= ariMax`, so a marker on verse 255
     * (ari == ariMax) is included; preserve verbatim.
     */
    @Query("SELECT COUNT(*) FROM marker WHERE ari >= :ariMin AND ari <= :ariMax")
    fun countInAriRangeInclusive(ariMin: Int, ariMax: Int): Int

    /**
     * Mirrors the inner `SELECT` in [yuku.alkitab.base.storage.InternalDb.putAttributes]:
     * inclusive range on `ari`, ordered by `modifyTime` ascending so a later
     * highlight (larger modifyTime) overwrites an earlier one when the
     * caller fills the per-verse color array.
     */
    @Query("SELECT * FROM marker WHERE ari >= :ariMin AND ari <= :ariMax ORDER BY modifyTime ASC")
    fun listInAriRangeOrderedByModifyTimeAsc(ariMin: Int, ariMax: Int): List<MarkerEntity>

    /**
     * Mirrors the inner `SELECT` in
     * [yuku.alkitab.base.storage.InternalDb.getHighlightColorRgb] (multi-verse
     * overload): exclusive lower bound on `ari`, inclusive upper bound,
     * filtered to highlight kind. Order doesn't matter — caller bucket-fills
     * a 256-element array indexed by `ari & 0xff`.
     */
    @Query("SELECT * FROM marker WHERE ari > :ariMin AND ari <= :ariMax AND kind = :kind")
    fun listForAriRangeExclusiveMinAndKind(ariMin: Int, ariMax: Int, kind: Int): List<MarkerEntity>

    /**
     * Raw-query escape hatch for [yuku.alkitab.base.storage.InternalDb.listMarkers],
     * which composes the SQL string at runtime (dynamic ORDER BY, optional
     * join against `marker_label`, optional NOT IN subquery). Room's `@Query`
     * can't interpolate column names into `ORDER BY`, so the facade builds
     * a [androidx.sqlite.db.SimpleSQLiteQuery] and we pass it through.
     */
    @RawQuery
    fun listMarkersRaw(query: SupportSQLiteQuery): List<MarkerEntity>

    @Insert
    fun insert(entity: MarkerEntity): Long

    /**
     * Bulk insert — runs atomically in a single transaction (Room's `@Insert`
     * default). Used by [MarkerDataMigration] so a partial migration can't
     * leave the table in a half-populated state.
     */
    @Insert
    fun insertAll(entities: List<MarkerEntity>)

    @Update
    fun update(entity: MarkerEntity): Int

    @Query("DELETE FROM marker WHERE _id = :id")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM marker WHERE gid = :gid")
    fun deleteByGid(gid: String): Int
}
