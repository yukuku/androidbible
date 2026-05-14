package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the reading-progress pin history row. Lives in
 * [AppDatabase] (file `AlkitabRoomDb`) alongside [ProgressMarkEntity]; the
 * legacy `ProgressMarkHistory` table in `AlkitabDb` is still created by
 * `InternalDbHelper` but no longer written to. The one-time data copy on
 * first launch is wired up in [ProgressMarkDataMigration].
 *
 * Identity: history rows are append-only and indexed by
 * `(progress_mark_preset_id, createTime)` for the `listHistoryByPresetId`
 * lookup. No uniqueness constraint — multiple history entries per preset
 * are the point of the table.
 *
 * Column types — the legacy `progress_mark_caption` column was declared
 * `INTEGER` in `InternalDbHelper.createTableProgressMarkHistory` but the DAO
 * always wrote a `String` into it (SQLite type affinity makes this work).
 * Room can't keep the affinity mismatch, so this entity declares the column
 * as `String?` to match real usage; the migration SQL inside
 * [AppDatabase.MIGRATION_4_5] also writes `TEXT`. `progress_mark_preset_id`
 * and `ari` are non-nullable in Room even though the legacy schema permitted
 * NULL — [ProgressMarkDataMigration] drops any legacy rows where either
 * column is NULL (they are unreachable by [ProgressMarkRoomDao.listHistoryByPresetId]).
 *
 * Index mirrors the legacy `index_701` on `(progress_mark_preset_id, createTime)`.
 */
@Entity(
    tableName = "progress_mark_history",
    indices = [
        Index(
            value = ["progress_mark_preset_id", "createTime"],
            name = "index_progress_mark_history_preset_id_createTime",
        ),
    ],
)
data class ProgressMarkHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val progress_mark_preset_id: Int,
    val progress_mark_caption: String?,
    val ari: Int,
    val createTime: Int?,
)
