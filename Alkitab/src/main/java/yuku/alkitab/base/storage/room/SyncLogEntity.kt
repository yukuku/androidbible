package yuku.alkitab.base.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one row of the `sync_log` table. Lives in [AppDatabase]
 * (file `AlkitabRoomDb`), distinct from the legacy `SyncLog` table in
 * `AlkitabDb` which is still created by `InternalDbHelper` but no longer
 * written to. The one-time data copy on first launch is wired up in
 * [SyncShadowDataMigration].
 *
 * Column types — [createTime] and [kind] are non-nullable in Room because
 * the facade ([yuku.alkitab.base.storage.SyncShadowDao]) has always written
 * both with concrete values (the create time is set from
 * [yuku.alkitab.base.util.Sqlitil.nowDateTime] and the kind from a
 * [yuku.alkitab.base.sync.SyncRecorder.EventKind] enum). [syncSetName] and
 * [params] remain nullable because the legacy facade explicitly writes
 * `null` for both on events that don't have a sync set scope (e.g.
 * `login_attempt`).
 *
 * The legacy `index_SyncLog_01` is a non-unique index on `createTime` to
 * keep the audit-log query in [yuku.alkitab.base.storage.SyncShadowDao.listLatest]
 * fast for the `ORDER BY createTime DESC LIMIT N` pattern.
 */
@Entity(
    tableName = "sync_log",
    indices = [Index(value = ["createTime"], name = "index_sync_log_createTime")],
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val _id: Long,
    val createTime: Int,
    val kind: Int,
    val syncSetName: String?,
    val params: String?,
)
