package yuku.alkitab.base.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for the `per_version` table. Consumed by the facade in
 * [yuku.alkitab.base.storage.PerVersionDao].
 *
 * Naming convention: this DAO speaks in [PerVersionEntity]; the mapping
 * to/from the public `PerVersionSettings` model lives in the facade so call
 * sites that already use `PerVersionSettings` don't need to change.
 */
@Dao
interface PerVersionRoomDao {

    @Query("SELECT * FROM per_version")
    fun listAll(): List<PerVersionEntity>

    @Query("SELECT COUNT(*) FROM per_version")
    fun count(): Int

    @Query("SELECT * FROM per_version WHERE versionId = :versionId LIMIT 1")
    fun findByVersionId(versionId: String): PerVersionEntity?

    /**
     * Upserts on the unique `versionId` index. Mirrors the legacy
     * `SQLiteDatabase.replace(Table.PerVersion.tableName(), null, cv)`
     * behaviour: a row with the same `versionId` is replaced atomically.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: PerVersionEntity): Long

    /**
     * Plain insert — used by [PerVersionDataMigration] to stream rows from
     * the legacy table into Room. Conflicts shouldn't happen during the
     * migration (the destination is empty), so the default `ABORT` strategy
     * is appropriate as a defence against double-runs.
     */
    @Insert
    fun insert(entity: PerVersionEntity): Long
}
