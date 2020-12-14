package yuku.alkitab.datatransfer.process

import kotlinx.serialization.encodeToString
import yuku.alkitab.base.sync.Sync
import yuku.alkitab.base.sync.Sync_Pins
import yuku.alkitab.datatransfer.model.HistoryContent
import yuku.alkitab.datatransfer.model.HistoryEntity
import yuku.alkitab.datatransfer.model.LabelContent
import yuku.alkitab.datatransfer.model.LabelEntity
import yuku.alkitab.datatransfer.model.MabelEntity
import yuku.alkitab.datatransfer.model.MarkerContent
import yuku.alkitab.datatransfer.model.MarkerEntity
import yuku.alkitab.datatransfer.model.MarkerLabelContent
import yuku.alkitab.datatransfer.model.MarkerLabelEntity
import yuku.alkitab.datatransfer.model.Pin
import yuku.alkitab.datatransfer.model.PinsContent
import yuku.alkitab.datatransfer.model.PinsEntity
import yuku.alkitab.datatransfer.model.Root
import yuku.alkitab.datatransfer.model.RppContent
import yuku.alkitab.datatransfer.model.RppEntity
import yuku.alkitab.datatransfer.model.Snapshot
import yuku.alkitab.datatransfer.model.Snapshots

class ExportProcess(
    private val storage: ReadonlyStorageInterface,
    private val log: LogInterface,
    private val creator_id: String,
) {
    fun export(): String {
        val snapshots = Snapshots(
            history(),
            mabel(),
            pins(),
            rpp(),
        )

        val root = Root(true, snapshots)
        log.log("Encoding to JSON")
        val res = Serializer.json.encodeToString(root)
        log.log("Exported successfully")
        return res
    }

    private fun <T> logList(list: List<T>, suffix: String): List<T> {
        log.log("Read ${list.size} $suffix")
        return list
    }

    private fun history(): Snapshot<HistoryEntity> {
        val entities = mutableListOf<HistoryEntity>()
        for (entry in logList(storage.history(), "history entry(s)")) {
            val content = HistoryContent(ari = entry.ari, timestamp = entry.timestamp)
            entities += HistoryEntity(gid = entry.gid, kind = Sync.Entity.KIND_HISTORY_ENTRY, creator_id = creator_id, content = content)
        }
        log.log("Exported history")
        return Snapshot(entities = entities)
    }

    private fun mabel(): Snapshot<MabelEntity> {
        val entities = mutableListOf<MabelEntity>()
        for (marker in logList(storage.markers(), "marker(s)")) {
            val content = MarkerContent(
                kind = marker.kind.code,
                ari = marker.ari,
                verseCount = marker.verseCount,
                createTime = marker.createTime.time,
                modifyTime = marker.modifyTime.time,
                caption = marker.caption,
            )
            entities += MarkerEntity(gid = marker.gid, kind = Sync.Entity.KIND_MARKER, creator_id = creator_id, content = content)
        }
        log.log("Exported markers")
        for (label in logList(storage.labels(), "label(s)")) {
            val content = LabelContent(title = label.title, ordering = label.ordering, backgroundColor = label.backgroundColor)
            entities += LabelEntity(gid = label.gid, kind = Sync.Entity.KIND_LABEL, creator_id = creator_id, content = content)
        }
        log.log("Exported labels")
        for (markerLabel in logList(storage.markerLabels(), "marker-label assignments")) {
            val content = MarkerLabelContent(markerLabel.marker_gid, markerLabel.label_gid)
            entities += MarkerLabelEntity(markerLabel.gid, kind = Sync.Entity.KIND_MARKER_LABEL, creator_id = creator_id, content = content)
        }
        log.log("Exported marker-label assignments")
        return Snapshot(entities = entities)
    }

    private fun pins(): Snapshot<PinsEntity> {
        val pins = mutableListOf<Pin>()
        for (pm in logList(storage.pins(), "pin(s)")) {
            pins += Pin(ari = pm.ari, modifyTime = pm.modifyTime.time, preset_id = pm.preset_id, caption = pm.caption)
        }
        val content = PinsContent(pins)
        val entity = PinsEntity(gid = Sync_Pins.GID_SPECIAL_PINS, kind = Sync.Entity.KIND_PINS, creator_id = creator_id, content = content)
        log.log("Exported pins")
        return Snapshot(entities = listOf(entity))
    }

    private fun rpp(): Snapshot<RppEntity> {
        val entities = mutableListOf<RppEntity>()
        for (rpp in logList(storage.rpps(), "reading plan progress(es)")) {
            val content = RppContent(startTime = rpp.startTime, done = rpp.done)
            entities += RppEntity(gid = rpp.gid.value, kind = Sync.Entity.KIND_RP_PROGRESS, creator_id = creator_id, content = content)
        }
        log.log("Exported reading plan progresses")
        return Snapshot(entities = entities)
    }
}
