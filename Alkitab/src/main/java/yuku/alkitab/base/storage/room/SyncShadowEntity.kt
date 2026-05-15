package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one row of the `sync_shadow` table. Lives in [AppDatabase]
 * (file `AlkitabRoomDb`), distinct from the legacy `SyncShadow` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [SyncShadowDataMigration].
 *
 * Column types — [syncSetName] and [revno] are non-nullable in Room even
 * though the legacy schema declared them nullable. The facade
 * ([yuku.alkitab.base.storage.SyncShadowDao]) has always written both with
 * concrete values (every code path in `SyncApplier` / `Sync_Mabel` /
 * `Sync_Pins` / `Sync_Rp` / `Sync_History` populates `syncSetName` from a
 * compile-time constant and `revno` from the server response). The
 * migration drops any legacy row whose `syncSetName` is `NULL` with
 * `?: continue` rather than coalescing — same defence-in-depth pattern as
 * [ReadingPlanDataMigration].
 *
 * The legacy `index_SyncShadow_01` is a non-unique index on `syncSetName`;
 * the facade keeps the legacy count-then-update-or-insert upsert path
 * (wrapped in `@Transaction` on the Room side) rather than relying on a
 * UNIQUE constraint — see [SyncShadowRoomDao.insertOrUpdateShadow].
 *
 * The binary [data] column holds the serialized Mabel / Pins / RP / History
 * snapshot. For a large Mabel account it can easily exceed the Android
 * CursorWindow's 2 MB limit, so the facade reads it via SQLite's `substr()`
 * in 1 MB chunks instead of letting Room materialise the whole blob in one
 * query — see [yuku.alkitab.base.storage.SyncShadowDao.getBySyncSetName].
 */
@Entity(
    tableName = "sync_shadow",
    indices = [Index(value = ["syncSetName"], name = "index_sync_shadow_syncSetName")],
)
data class SyncShadowEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val syncSetName: String,
    val revno: Int,
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
        if (other !is SyncShadowEntity) return false
        return _id == other._id &&
            syncSetName == other.syncSetName &&
            revno == other.revno &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = _id.hashCode()
        result = 31 * result + syncSetName.hashCode()
        result = 31 * result + revno
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}
