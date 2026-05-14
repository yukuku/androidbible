package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a bookmark, note, or highlight. Lives in [AppDatabase]
 * (file `AlkitabRoomDb`), distinct from the legacy `Marker` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [MarkerDataMigration].
 *
 * `kind` is stored as the `Marker.Kind.code` int (1 = bookmark, 2 = note,
 * 3 = highlight) to match the legacy schema byte-for-byte and keep the
 * data-copy a flat field assignment.
 *
 * `createTime` / `modifyTime` are epoch seconds (the legacy code persisted
 * `int` seconds via `Sqlitil.toInt(Date)`); the facade converts back to
 * [java.util.Date] for callers.
 *
 * Indexes mirror the legacy `Marker` table's six indexes
 * (`createIndexMarker` in `InternalDbHelper`) so query plans don't regress.
 * The legacy `index_Marker_05 (kind, caption COLLATE NOCASE)` cannot be
 * expressed via `@Index` because Room does not support per-column collation,
 * so it is created out-of-band by `AppDatabase.MARKER_KIND_CAPTION_NOCASE_INDEX_SQL`
 * — applied in `MIGRATION_2_3` for existing users and in the
 * `noCaseCaptionIndexCallback` for fresh installs.
 *
 * Column types — `ari`/`kind`/`verseCount`/`createTime`/`modifyTime` are
 * non-nullable here even though the legacy schema allowed NULLs;
 * [MarkerDataMigration] coalesces legacy NULLs to 0 (the same default the
 * legacy reader applied via `cursor.getInt`).
 */
@Entity(
    tableName = "marker",
    indices = [
        Index(value = ["ari"], name = "index_marker_ari"),
        Index(value = ["kind", "ari"], name = "index_marker_kind_ari"),
        Index(value = ["kind", "modifyTime"], name = "index_marker_kind_modifyTime"),
        Index(value = ["kind", "createTime"], name = "index_marker_kind_createTime"),
        Index(value = ["gid"], name = "index_marker_gid"),
        // Room's @Index can't express per-column COLLATE. The annotation
        // declares (kind, caption) so Room's schema validator accepts the
        // index; AppDatabase.MIGRATION_2_3 and noCaseCaptionIndexCallback
        // ensure the actual SQLite index is (kind, caption COLLATE NOCASE).
        // SQLite's PRAGMA index_info does not expose per-column collation,
        // so Room's TableInfo comparison sees identical column lists for both.
        Index(value = ["kind", "caption"], name = AppDatabase.MARKER_KIND_CAPTION_NOCASE_INDEX_NAME),
    ],
)
data class MarkerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long = 0L,
    val gid: String?,
    val ari: Int,
    val kind: Int,
    val caption: String?,
    val verseCount: Int,
    val createTime: Int,
    val modifyTime: Int,
)
