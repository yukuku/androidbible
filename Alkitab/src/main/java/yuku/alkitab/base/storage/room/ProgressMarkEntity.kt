package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the reading-progress pin row. Lives in [AppDatabase] (file
 * `AlkitabRoomDb`), distinct from the legacy `ProgressMark` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [ProgressMarkDataMigration].
 *
 * Identity: rows are addressed by `preset_id` (1..5 in caller code, plus the
 * five `ari = 0` placeholder rows inserted at table creation with
 * `preset_id` 0..4). The legacy index on `preset_id` was non-unique even
 * though callers treat the column as a primary key — the facade's
 * insert-or-update path does its own existence check. The Room schema keeps
 * that same non-unique index to avoid introducing a new constraint that
 * legacy data might violate.
 *
 * Column types — `preset_id` and `ari` are non-nullable here even though the
 * legacy schema permitted NULL. [ProgressMarkDataMigration] drops any legacy
 * rows where either column is NULL (those rows are unreachable by the
 * facade's keyed lookups, and coalescing a NULL `preset_id` to 0 would
 * collide with the legitimate `preset_id = 0` placeholder row that the table
 * always carries). `modifyTime` is nullable to round-trip the placeholder
 * rows which were inserted without a `modifyTime` value.
 *
 * Index mirrors the legacy `index_601` on `preset_id`.
 */
@Entity(
    tableName = "progress_mark",
    indices = [
        Index(value = ["preset_id"], name = "index_progress_mark_preset_id"),
    ],
)
data class ProgressMarkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val preset_id: Int,
    val caption: String?,
    val ari: Int,
    val modifyTime: Int?,
)
