package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a downloaded Bible version. Lives in [AppDatabase] (file
 * `AlkitabRoomDb`), distinct from the legacy `Version` table in `AlkitabDb`
 * which is still created by `InternalDbHelper` but no longer written to. The
 * one-time data copy on first launch is wired up in [VersionDataMigration].
 *
 * Indexes mirror the legacy `Version` table's three indexes
 * (`createIndexVersion` in `InternalDbHelper`) so query plans don't regress.
 *
 * Column types — `modifyTime`/`active`/`ordering` are non-nullable here even
 * though the legacy schema allowed NULLs; [VersionDataMigration.copy] coalesces
 * legacy NULLs to 0.
 */
@Entity(
    tableName = "version",
    indices = [
        Index(value = ["ordering"], name = "index_version_ordering"),
        Index(value = ["active", "longName"], name = "index_version_active_longName"),
        Index(value = ["preset_name"], name = "index_version_preset_name"),
    ],
)
data class VersionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long = 0L,
    val locale: String?,
    val shortName: String?,
    val longName: String?,
    val description: String?,
    val filename: String?,
    @ColumnInfo(name = "preset_name") val preset_name: String?,
    val modifyTime: Int,
    val active: Int,
    val ordering: Int,
)
