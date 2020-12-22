package yuku.alkitab.datatransfer.process

import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Pin
import yuku.alkitab.datatransfer.model.Rpp
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label


interface ReadWriteStorageInterface : ReadonlyStorageInterface {
    fun interface TxHandle {
        fun commit()
    }

    fun transact(action: (TxHandle) -> Unit)

    fun replaceHistory(entries: List<History.Entry>)

    fun replaceMarker(marker: Marker)

    fun replaceLabel(label: Label)

    fun replaceMarkerLabel(markerLabel: Marker_Label)

    fun replacePin(pin: Pin)

    fun replaceRpp(rpp: Rpp)
}
