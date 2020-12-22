package yuku.alkitab.datatransfer.process

import java.util.Date
import kotlinx.serialization.decodeFromString
import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Gid
import yuku.alkitab.datatransfer.model.HistoryEntity
import yuku.alkitab.datatransfer.model.LabelEntity
import yuku.alkitab.datatransfer.model.MabelEntity
import yuku.alkitab.datatransfer.model.MarkerEntity
import yuku.alkitab.datatransfer.model.MarkerLabelEntity
import yuku.alkitab.datatransfer.model.PinsEntity
import yuku.alkitab.datatransfer.model.Root
import yuku.alkitab.datatransfer.model.Rpp
import yuku.alkitab.datatransfer.model.RppEntity
import yuku.alkitab.datatransfer.model.Snapshot
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label

class ImportProcess(
    private val storage: ReadWriteStorageInterface,
    private val log: LogInterface,
) {
    data class Options(
        val importHistory: Boolean,
        val importMabel: Boolean,
        val importPins: Boolean,
        val importRpp: Boolean,
        val actualRun: Boolean,
    )

    fun import(json: String, options: Options) {
        log.log("Decoding from JSON")

        val root = Serializer.json.decodeFromString<Root>(json)
        log.log("JSON read successfully")

        if (options.actualRun) {
            log.log("Proceeding with actual import unless error happens")
        } else {
            log.log("Simulating import")
        }

        storage.transact { handle ->
            if (options.importHistory) {
                history(root.snapshots.history)
            }

            if (options.importMabel) {
                mabel(root.snapshots.mabel)
            }

            if (options.importPins) {
                pins(root.snapshots.pins)
            }

            if (options.importRpp) {
                rpp(root.snapshots.rp)
            }

            if (options.actualRun) {
                log.log("Everything works without error, committing the change")
                handle.commit()
            }
        }

        log.log("All succeeded")
    }

    /**
     * For history, there is no merging done locally, local will be replaced completely.
     */
    private fun history(history: Snapshot<HistoryEntity>) {
        val logger = log.logger("History")
        val old = storage.history()
        val new = history.entities.map { entity ->
            History.Entry(entity.gid, entity.content.ari, entity.content.timestamp, entity.creator_id)
        }
        logger("${old.size} entries will be replaced by incoming ${new.size} entries")

        storage.replaceHistory(new)
        logger("replace succeeded")
    }

    /**
     * For mabel, if the gid matches, they will be replaced. Otherwise, inserted.
     */
    private fun mabel(mabel: Snapshot<MabelEntity>) {
        val logger = log.logger("Markers and labels")
        val oldMarkers = storage.markers()
        val oldLabels = storage.labels()
        val oldMarkerLabels = storage.markerLabels()

        logger("there are currently ${oldMarkers.size} markers")
        logger("- ${oldMarkers.count { it.kind == Marker.Kind.bookmark }} bookmarks")
        logger("- ${oldMarkers.count { it.kind == Marker.Kind.note }} notes")
        logger("- ${oldMarkers.count { it.kind == Marker.Kind.highlight }} highlights")
        logger("there are currently ${oldLabels.size} labels")

        val markerGids = oldMarkers.associateBy { it.gid }
        val labelGids = oldLabels.associateBy { it.gid }
        val markerLabelGids = oldMarkerLabels.associateBy { it.gid }

        // do the counting first
        logger("incoming markers will replace ${
            mabel.entities.count { it is MarkerEntity && markerGids.containsKey(it.gid) }
        } existing ones")
        logger("incoming labels will replace ${
            mabel.entities.count { it is LabelEntity && labelGids.containsKey(it.gid) }
        } existing ones")

        var markerNewCount = 0
        var markerEditCount = 0
        var labelNewCount = 0
        var labelEditCount = 0
        var markerLabelNewCount = 0
        var markerLabelEditCount = 0

        for (entity in mabel.entities) {
            when (entity) {
                is MarkerEntity -> {
                    val oldMarker = markerGids[entity.gid]
                    if (oldMarker == null) markerNewCount++ else markerEditCount++
                    val newMarker = (oldMarker ?: Marker.createEmptyMarker()).apply {
                        // _id will be nonzero if oldMarker is non-null
                        gid = entity.gid
                        ari = entity.content.ari
                        kind = Marker.Kind.fromCode(entity.content.kind)
                        caption = entity.content.caption
                        verseCount = entity.content.verseCount
                        createTime = Date(entity.content.createTime)
                        modifyTime = Date(entity.content.modifyTime)
                    }
                    storage.replaceMarker(newMarker)
                }
                is LabelEntity -> {
                    val oldLabel = labelGids[entity.gid]
                    if (oldLabel == null) labelNewCount++ else labelEditCount++
                    val newLabel = (oldLabel ?: Label.createEmptyLabel()).apply {
                        // _id will be nonzero if oldLabel is non-null
                        gid = entity.gid
                        title = entity.content.title
                        ordering = entity.content.ordering
                        backgroundColor = entity.content.backgroundColor
                    }
                    storage.replaceLabel(newLabel)
                }
                is MarkerLabelEntity -> {
                    // check validity of marker gid and label gid
                    val oldMarkerLabel = markerLabelGids[entity.gid]
                    if (oldMarkerLabel == null) markerLabelNewCount++ else markerLabelEditCount++
                    val newMarkerLabel = (oldMarkerLabel ?: Marker_Label.createEmptyMarker_Label()).apply {
                        // _id will be nonzero if oldMarkerLabel is non-null
                        gid = entity.gid
                        marker_gid = entity.content.marker_gid
                        label_gid = entity.content.label_gid
                    }
                    storage.replaceMarkerLabel(newMarkerLabel)
                }
            }
        }

        logger("added $markerNewCount markers, edited existing $markerEditCount markers")
        logger("added $labelNewCount labels, edited existing $labelEditCount labels")
        logger("added $markerLabelEditCount label-assignments, edited existing $markerLabelEditCount label-assignments")
    }

    private fun pins(pins: Snapshot<PinsEntity>) {
        val logger = log.logger("Pins")
        val old = storage.pins()
        val new = pins.entities.firstOrNull()?.content?.pins.orEmpty()
        logger("there are currently ${old.size} pins, incoming ${new.size} pins")

        for (newPin in new) {
            val oldPin = old.find { it.preset_id == newPin.preset_id }
            if (oldPin != null) {
                logger("current pin ${oldPin.preset_id + 1} '${oldPin.caption.orEmpty()}'" +
                    " will be replaced by an incoming pin '${newPin.caption.orEmpty()}'"
                )
            }

            storage.replacePin(newPin)
            logger("replace pin ${newPin.preset_id + 1} '${newPin.caption.orEmpty()}' succeeded")
        }
    }

    private fun rpp(rpp: Snapshot<RppEntity>) {
        val logger = log.logger("Reading plan progress")
        val old = storage.rpps()
        val new = rpp.entities.map { entity ->
            Rpp(Gid(entity.gid), entity.content.startTime, entity.content.done.toList())
        }
        logger("there are currently ${old.size} progresses, incoming ${new.size} progresses")

        for (newRpp in new) {
            val oldRpp = old.find { it.gid == newRpp.gid }
            if (oldRpp != null) {
                logger("current progress ${oldRpp.gid} will be replaced by an incoming progress")
            }

            storage.replaceRpp(newRpp)
            logger("replace progress ${newRpp.gid} succeeded")
        }
    }
}
