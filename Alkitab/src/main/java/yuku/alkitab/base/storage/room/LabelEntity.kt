package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a bookmark/note/highlight label. Lives in [AppDatabase]
 * (file `AlkitabRoomDb`), distinct from the legacy `Label` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [MarkerDataMigration].
 *
 * Column-name note: the legacy `Label` table uses Indonesian column names
 * (`judul`, `urutan`, `warnaLatar`). The Room table is a fresh schema in a
 * separate DB file, so we use the same English names that already appear on
 * the [yuku.alkitab.model.Label] model fields (`title`, `ordering`,
 * `backgroundColor`). [MarkerDataMigration] does the legacy→Room column
 * mapping during the one-time copy.
 *
 * Indexes mirror the legacy `Label` table's two indexes (`createIndexLabel`
 * in `InternalDbHelper`).
 */
@Entity(
    tableName = "label",
    indices = [
        Index(value = ["ordering"], name = "index_label_ordering"),
        Index(value = ["gid"], name = "index_label_gid"),
    ],
)
data class LabelEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long = 0L,
    val gid: String?,
    val title: String?,
    val ordering: Int,
    val backgroundColor: String?,
)
