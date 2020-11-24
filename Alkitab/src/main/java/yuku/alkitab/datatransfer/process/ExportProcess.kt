package yuku.alkitab.datatransfer.process

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

private const val REVNO_NOT_USED = 0

class ExportProcess(
    private val storage: StorageInterface,
    private val creator_id: String,
) {
    private val format = Json {}

    fun export(): String {
        val snapshots = Snapshots(
            history(),
            mabel(),
            pins(),
            rp(),
        )

        val root = Root(true, snapshots)
        return format.encodeToString(root)
    }

    private fun history(): Snapshot<HistoryEntity> {
        val entities = mutableListOf<HistoryEntity>()
        for (entry in storage.history()) {
            val content = HistoryContent(ari = entry.ari, timestamp = entry.timestamp)
            entities += HistoryEntity(gid = entry.gid, kind = Sync.Entity.KIND_HISTORY_ENTRY, creator_id = creator_id, content = content)
        }
        return Snapshot(REVNO_NOT_USED, entities)
    }

    private fun mabel(): Snapshot<MabelEntity> {
        val entities = mutableListOf<MabelEntity>()
        for (marker in storage.markers()) {
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
        for (label in storage.labels()) {
            val content = LabelContent(title = label.title, ordering = label.ordering, backgroundColor = label.backgroundColor)
            entities += LabelEntity(gid = label.gid, kind = Sync.Entity.KIND_LABEL, creator_id = creator_id, content = content)
        }
        for (markerLabel in storage.markerLabels()) {
            val content = MarkerLabelContent(markerLabel.marker_gid, markerLabel.label_gid)
            entities += MarkerLabelEntity(markerLabel.gid, kind = Sync.Entity.KIND_MARKER_LABEL, creator_id = creator_id, content = content)
        }
        return Snapshot(REVNO_NOT_USED, entities)
    }

    private fun pins(): Snapshot<PinsEntity> {
        val pins = mutableListOf<Pin>()
        for (pm in storage.pins()) {
            pins += Pin(ari = pm.ari, modifyTime = pm.modifyTime.time, preset_id = pm.preset_id, caption = pm.caption)
        }
        val content = PinsContent(pins)
        val entity = PinsEntity(gid = Sync_Pins.GID_SPECIAL_PINS, kind = Sync.Entity.KIND_PINS, creator_id = creator_id, content = content)
        return Snapshot(REVNO_NOT_USED, listOf(entity))
    }

    private fun rp(): Snapshot<RppEntity> {
        val entities = mutableListOf<RppEntity>()
        for (rpp in storage.rpps()) {
            val content = RppContent(startTime = rpp.startTime, done = rpp.done)
            entities += RppEntity(gid = rpp.gid.value, kind = Sync.Entity.KIND_RP_PROGRESS, creator_id = creator_id, content = content)
        }
        return Snapshot(REVNO_NOT_USED, entities)
    }
}
