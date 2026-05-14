package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single completed reading-plan day. Lives in [AppDatabase]
 * (file `AlkitabRoomDb`) alongside [ReadingPlanEntity]; the legacy
 * `ReadingPlanProgress` table in `AlkitabDb` is still created by
 * `InternalDbHelper` but no longer written to. The one-time data copy on
 * first launch is wired up in [ReadingPlanDataMigration].
 *
 * Column types — [reading_plan_progress_gid] and [reading_code] are
 * non-nullable in Room even though the legacy schema declared them nullable.
 * The facade ([yuku.alkitab.base.storage.ReadingPlanDao]) has always written
 * both with concrete values (the legacy `replace` / `insertOrUpdateProgress`
 * paths put a non-null gid + reading code into every row); the migration
 * drops any legacy row missing either column with `?: continue` rather than
 * coalescing — coalescing multiple legacy NULLs into a placeholder would
 * collide on the UNIQUE index below and roll the migration back mid-startup
 * (the failure mode Gemini caught on REM-28 / #204).
 *
 * The legacy `index_902` is `UNIQUE (reading_plan_progress_gid, reading_code)`;
 * the facade's `insertOrUpdate*` paths rely on it for SQLite's
 * `INSERT OR REPLACE` semantics. The Room schema declares the same uniqueness
 * so [ReadingPlanRoomDao.insertOrReplaceProgress] can use
 * [androidx.room.OnConflictStrategy.REPLACE] for the same upsert behaviour.
 */
@Entity(
    tableName = "reading_plan_progress",
    indices = [
        Index(
            value = ["reading_plan_progress_gid", "reading_code"],
            name = "index_reading_plan_progress_gid_reading_code",
            unique = true,
        ),
    ],
)
data class ReadingPlanProgressEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val reading_plan_progress_gid: String,
    val reading_code: Int,
    val checkTime: Long?,
)
