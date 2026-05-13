package yuku.alkitab.base.storage.room

import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.util.AppLog

/**
 * One-time data copy from the legacy `Marker` / `Label` / `Marker_Label`
 * tables in `AlkitabDb` (managed by `InternalDbHelper`) into Room's
 * `marker` / `label` / `marker_label` tables in `AlkitabRoomDb` (managed
 * by [AppDatabase]).
 *
 * Idempotent: a no-op when *any* of the three Room tables already has rows.
 * The single combined check is intentional — the three tables migrate
 * together atomically inside a single Room transaction, so partial state
 * cannot exist; if even one of them is non-empty, the migration has already
 * run. If the copy fails mid-flight (e.g. process kill, OOM), Room rolls
 * back the whole transaction and the next launch retries cleanly.
 *
 * The legacy tables are intentionally left intact so a future release can
 * audit/rollback. See REM-10 in `docs/tech-debt-remediation.md`.
 */
object MarkerDataMigration {
    private const val TAG = "MarkerDataMigration"

    fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
        val markerDao = roomDb.markerDao()
        val labelDao = roomDb.labelDao()
        val mlDao = roomDb.markerLabelDao()

        // Combined idempotency check: if ANY of the three Room tables has
        // rows, we've already migrated (or the user has organically added
        // data after a previous migration). Don't double-copy.
        if (markerDao.count() > 0 || labelDao.count() > 0 || mlDao.count() > 0) {
            return
        }

        val markers = readLegacyMarkers(legacyHelper)
        val labels = readLegacyLabels(legacyHelper)
        val markerLabels = readLegacyMarkerLabels(legacyHelper)

        if (markers.isEmpty() && labels.isEmpty() && markerLabels.isEmpty()) {
            // No legacy data either (fresh install). Nothing to copy.
            return
        }

        // Atomic-or-nothing: all three insertAll calls run inside a single
        // Room transaction. A crash mid-migration leaves zero rows in all
        // three Room tables and the next launch retries cleanly. The legacy
        // `Marker_Label` table references `Marker` and `Label` only by gid,
        // so cross-table consistency is preserved by the gids themselves —
        // even if a partial insert were possible (it isn't), a half-inserted
        // marker_label row with a missing marker would just be an orphan,
        // not a corrupted reference.
        roomDb.runInTransaction {
            if (markers.isNotEmpty()) markerDao.insertAll(markers)
            if (labels.isNotEmpty()) labelDao.insertAll(labels)
            if (markerLabels.isNotEmpty()) mlDao.insertAll(markerLabels)
        }

        AppLog.d(
            TAG,
            "Copied ${markers.size} marker(s), ${labels.size} label(s), " +
                "${markerLabels.size} marker_label row(s) from legacy tables to Room",
        )
    }

    private fun readLegacyMarkers(legacyHelper: InternalDbHelper): List<MarkerEntity> {
        val res = ArrayList<MarkerEntity>()
        legacyHelper.readableDatabase.query(
            Db.TABLE_Marker, null, null, null, null, null, "_id ASC",
        ).use { c ->
            val colGid = c.getColumnIndexOrThrow(Db.Marker.gid)
            val colAri = c.getColumnIndexOrThrow(Db.Marker.ari)
            val colKind = c.getColumnIndexOrThrow(Db.Marker.kind)
            val colCaption = c.getColumnIndexOrThrow(Db.Marker.caption)
            val colVerseCount = c.getColumnIndexOrThrow(Db.Marker.verseCount)
            val colCreateTime = c.getColumnIndexOrThrow(Db.Marker.createTime)
            val colModifyTime = c.getColumnIndexOrThrow(Db.Marker.modifyTime)
            while (c.moveToNext()) {
                res += MarkerEntity(
                    // Don't carry over `_id` — Room assigns a fresh one. The
                    // sync-identity key used by callers is `gid`, not `_id`.
                    _id = 0L,
                    gid = c.getString(colGid),
                    ari = c.getInt(colAri),
                    kind = c.getInt(colKind),
                    caption = c.getString(colCaption),
                    verseCount = c.getInt(colVerseCount),
                    createTime = c.getInt(colCreateTime),
                    modifyTime = c.getInt(colModifyTime),
                )
            }
        }
        return res
    }

    private fun readLegacyLabels(legacyHelper: InternalDbHelper): List<LabelEntity> {
        val res = ArrayList<LabelEntity>()
        legacyHelper.readableDatabase.query(
            Db.TABLE_Label, null, null, null, null, null, Db.Label.ordering + " ASC",
        ).use { c ->
            val colGid = c.getColumnIndexOrThrow(Db.Label.gid)
            // Legacy column names are Indonesian (`judul`, `urutan`,
            // `warnaLatar`); Room columns are English. Translate during read.
            val colTitle = c.getColumnIndexOrThrow(Db.Label.title)
            val colOrdering = c.getColumnIndexOrThrow(Db.Label.ordering)
            val colBgColor = c.getColumnIndexOrThrow(Db.Label.backgroundColor)
            while (c.moveToNext()) {
                res += LabelEntity(
                    _id = 0L,
                    gid = c.getString(colGid),
                    title = c.getString(colTitle),
                    ordering = c.getInt(colOrdering),
                    backgroundColor = c.getString(colBgColor),
                )
            }
        }
        return res
    }

    private fun readLegacyMarkerLabels(legacyHelper: InternalDbHelper): List<MarkerLabelEntity> {
        val res = ArrayList<MarkerLabelEntity>()
        legacyHelper.readableDatabase.query(
            Db.TABLE_Marker_Label, null, null, null, null, null, "_id ASC",
        ).use { c ->
            val colGid = c.getColumnIndexOrThrow(Db.Marker_Label.gid)
            val colMarkerGid = c.getColumnIndexOrThrow(Db.Marker_Label.marker_gid)
            val colLabelGid = c.getColumnIndexOrThrow(Db.Marker_Label.label_gid)
            while (c.moveToNext()) {
                res += MarkerLabelEntity(
                    _id = 0L,
                    gid = c.getString(colGid),
                    marker_gid = c.getString(colMarkerGid),
                    label_gid = c.getString(colLabelGid),
                )
            }
        }
        return res
    }
}
