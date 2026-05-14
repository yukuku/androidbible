package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the per-version settings row. Lives in [AppDatabase] (file
 * `AlkitabRoomDb`), distinct from the legacy `PerVersion` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [PerVersionDataMigration].
 *
 * Identity: rows are keyed by `versionId` via a unique index. The facade
 * upserts via Room's `OnConflictStrategy.REPLACE` against that uniqueness
 * constraint, mirroring the legacy `SQLiteDatabase.replace` behaviour.
 *
 * Column types — `versionId` is non-nullable here even though the legacy
 * schema permitted NULL; [PerVersionDataMigration] coalesces legacy NULLs
 * to the empty string. `settings` stays nullable to faithfully round-trip
 * whatever legacy rows contain (the facade always writes a JSON blob, but
 * older imported rows could in theory carry NULL).
 *
 * Index mirrors the legacy `index_PerVersion_01` unique index.
 */
@Entity(
    tableName = "per_version",
    indices = [
        Index(value = ["versionId"], unique = true, name = "index_per_version_versionId"),
    ],
)
data class PerVersionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long = 0L,
    val versionId: String,
    val settings: String?,
)
