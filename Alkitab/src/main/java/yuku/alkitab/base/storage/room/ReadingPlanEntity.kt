package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the reading-plan metadata row. Lives in [AppDatabase] (file
 * `AlkitabRoomDb`), distinct from the legacy `ReadingPlan` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [ReadingPlanDataMigration].
 *
 * Column nullability mirrors the legacy schema, which declared every column
 * except `_id` as nullable (`integer` / `text` / `blob` without `not null`).
 * Keeping them nullable on the Room side lets the data migration round-trip
 * any historical row without needing per-column placeholders.
 *
 * The binary [data] column holds the RPB plan file as written by
 * [yuku.alkitab.base.storage.ReadingPlanDao.insert]. It can be several
 * hundred kilobytes per plan, so [ReadingPlanRoomDao] reads it through a
 * dedicated `(name, data)` projection on the rare paths that need it
 * ([yuku.alkitab.base.storage.ReadingPlanDao.getNameAndData]) rather than
 * pulling it into every list query.
 *
 * No indexes — the legacy schema had none, and the facade's read paths
 * either iterate every row ([yuku.alkitab.base.storage.ReadingPlanDao.listAllInfo]
 * / `listNames`) or address a row by primary key ([getNameAndData],
 * [deleteById], `updateStartDate`).
 */
@Entity(tableName = "reading_plan")
data class ReadingPlanEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val version: Int?,
    val name: String?,
    val title: String?,
    val description: String?,
    val duration: Int?,
    val startTime: Long?,
    val data: ByteArray?,
) {
    /**
     * Generated `equals` / `hashCode` from `data class` use reference equality
     * on the `data` ByteArray, which makes round-trip assertions in the unit
     * tests brittle. Override both to use the array's content equality
     * instead — every other field is a value type so the rest of the
     * generated implementation would be correct.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReadingPlanEntity) return false
        return _id == other._id &&
            version == other.version &&
            name == other.name &&
            title == other.title &&
            description == other.description &&
            duration == other.duration &&
            startTime == other.startTime &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = _id.hashCode()
        result = 31 * result + (version ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (duration ?: 0)
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}
