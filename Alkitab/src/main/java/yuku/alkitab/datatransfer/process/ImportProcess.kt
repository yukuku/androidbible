package yuku.alkitab.datatransfer.process

import kotlinx.serialization.decodeFromString
import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Gid
import yuku.alkitab.datatransfer.model.HistoryEntity
import yuku.alkitab.datatransfer.model.PinsEntity
import yuku.alkitab.datatransfer.model.Root
import yuku.alkitab.datatransfer.model.Rpp
import yuku.alkitab.datatransfer.model.RppEntity
import yuku.alkitab.datatransfer.model.Snapshot

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
            // TODO
            // mabel(root.snapshots.mabel, actualRun)
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
        val old = storage.history()
        val new = history.entities.map { entity ->
            History.Entry(entity.gid, entity.content.ari, entity.content.timestamp, entity.creator_id)
        }
        log.log("History: ${old.size} entries will be replaced by incoming ${new.size} entries")

        if (actualRun) {
            storage.replaceHistory(new)
            log.log("History: replace succeeded")
        }
    }

    private fun pins(pins: Snapshot<PinsEntity>, actualRun: Boolean) {
        val old = storage.pins()
        val new = pins.entities.firstOrNull()?.content?.pins.orEmpty()
        log.log("Pins: there are currently ${old.size} pins, incoming ${new.size} pins")

        for (newPin in new) {
            val oldPin = old.find { it.preset_id == newPin.preset_id }
            if (oldPin != null) {
                log.log("Pins: current pin ${oldPin.preset_id + 1} '${oldPin.caption.orEmpty()}'" +
                    " will be replaced by an incoming pin '${newPin.caption.orEmpty()}'"
                )
            }

            if (actualRun) {
                storage.replacePin(newPin)
                log.log("Pins: replace pin ${newPin.preset_id + 1} '${newPin.caption.orEmpty()}' succeeded")
            }
        }
    }

    private fun rpp(rpp: Snapshot<RppEntity>, actualRun: Boolean) {
        val old = storage.rpps()
        val new = rpp.entities.map { entity ->
            Rpp(Gid(entity.gid), entity.content.startTime, entity.content.done.toList())
        }
        log.log("Reading plan progress: there are currently ${old.size} progresses, incoming ${new.size} progresses")

        for (newRpp in new) {
            val oldRpp = old.find { it.gid == newRpp.gid }
            if (oldRpp != null) {
                log.log("Reading plan progress: current progress ${oldRpp.gid} will be replaced by an incoming progress")
            }

            if (actualRun) {
                storage.replaceRpp(newRpp)
                log.log("Reading plan progress: replace progress ${newRpp.gid} succeeded")
            }
        }
    }
}
