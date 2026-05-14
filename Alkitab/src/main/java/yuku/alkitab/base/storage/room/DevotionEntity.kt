package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a cached devotional article. Lives in [AppDatabase] (file
 * `AlkitabRoomDb`), distinct from the legacy `Devotion` table in `AlkitabDb`
 * which is still created by `InternalDbHelper` but no longer written to. The
 * one-time data copy on first launch is wired up in [DevotionDataMigration].
 *
 * Identity: rows are matched on `(name, date, dataFormatVersion)`. There is no
 * uniqueness constraint — the facade dedupes via delete-then-insert in a
 * single transaction, mirroring the legacy [yuku.alkitab.base.storage.DevotionDao]
 * behaviour.
 *
 * Column types — `readyToUse`/`touchTime`/`dataFormatVersion` are non-nullable
 * here even though the legacy schema allowed NULLs; [DevotionDataMigration]
 * coalesces legacy NULLs to 0. The text columns (`name`/`date`/`body`) stay
 * nullable to faithfully round-trip whatever the legacy rows contain.
 *
 * Indexes mirror the legacy `Devotion` table's two indexes
 * (`createIndexDevotion` in `InternalDbHelper`).
 */
@Entity(
    tableName = "devotion",
    indices = [
        Index(
            value = ["name", "date", "dataFormatVersion"],
            name = "index_devotion_name_date_dataFormatVersion",
        ),
        Index(value = ["touchTime"], name = "index_devotion_touchTime"),
    ],
)
data class DevotionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long = 0L,
    val name: String?,
    val date: String?,
    val body: String?,
    val readyToUse: Int,
    val touchTime: Int,
    val dataFormatVersion: Int,
)
