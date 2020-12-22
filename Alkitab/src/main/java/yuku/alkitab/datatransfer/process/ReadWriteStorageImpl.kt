package yuku.alkitab.datatransfer.process

import java.util.Date
import yuku.alkitab.base.S
import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Pin
import yuku.alkitab.datatransfer.model.Rpp
import yuku.alkitab.model.Marker
import yuku.alkitab.model.ProgressMark
import yuku.alkitab.util.IntArrayList

class ReadWriteStorageImpl : ReadonlyStorageImpl(), ReadWriteStorageInterface {
    override fun replaceHistory(entries: List<History.Entry>) {
        History.replaceAllEntries(entries)
    }

    /**
     * [marker] must have the correct _id for this to work correctly (0 for new).
     */
    override fun replaceMarker(marker: Marker) {
        S.getDb().insertOrUpdateMarker(marker)
    }

    override fun replacePin(pin: Pin) {
        val pm = ProgressMark().apply {
            preset_id = pin.preset_id
            ari = pin.ari
            caption = pin.caption
            modifyTime = Date(pin.modifyTime)
        }
        S.getDb().insertOrUpdateProgressMark(pm)
    }

    override fun replaceRpp(rpp: Rpp) {
        val readingCodes = IntArrayList(rpp.done.size)
        rpp.done.forEach { readingCodes.add(it) }

        S.getDb().replaceReadingPlanProgress(rpp.gid.value, readingCodes, System.currentTimeMillis())
    }
}
