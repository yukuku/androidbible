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
import yuku.alkitab.model.Marker

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

        if (options.importHistory) {
            history(root.snapshots.history, options.actualRun)
        }

        if (options.importMabel) {
            mabel(root.snapshots.mabel, options.actualRun)
        }

        if (options.importPins) {
            pins(root.snapshots.pins, options.actualRun)
        }

        if (options.importRpp) {
            rpp(root.snapshots.rp, options.actualRun)
        }
    }

    /**
     * For history, there is no merging done locally, local will be replaced completely.
     */
    private fun history(history: Snapshot<HistoryEntity>, actualRun: Boolean) {
        val logger = log.logger("History")
        val old = storage.history()
        val new = history.entities.map { entity ->
            History.Entry(entity.gid, entity.content.ari, entity.content.timestamp, entity.creator_id)
        }
        logger("${old.size} entries will be replaced by incoming ${new.size} entries")

        if (actualRun) {
            storage.replaceHistory(new)
            logger("replace succeeded")
        }
    }

    /**
     * For mabel, if the gid matches, they will be replaced. Otherwise, inserted.
     */
    private fun mabel(mabel: Snapshot<MabelEntity>, actualRun: Boolean) {
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

        for (entity in mabel.entities) {
            when (entity) {
                is MarkerEntity -> {
                    val oldMarker = markerGids[entity.gid]
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
                    if (actualRun) {
                        storage.replaceMarker(newMarker)
                    }
                }
                is LabelEntity -> TODO()
                is MarkerLabelEntity -> TODO()
            }
        }
    }

    private fun pins(pins: Snapshot<PinsEntity>, actualRun: Boolean) {
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

            if (actualRun) {
                storage.replacePin(newPin)
                logger("replace pin ${newPin.preset_id + 1} '${newPin.caption.orEmpty()}' succeeded")
            }
        }
    }

    private fun rpp(rpp: Snapshot<RppEntity>, actualRun: Boolean) {
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

            if (actualRun) {
                storage.replaceRpp(newRpp)
                logger("replace progress ${newRpp.gid} succeeded")
            }
        }
    }
}
