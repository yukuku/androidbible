package yuku.alkitab.datatransfer.process

import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Pin
import yuku.alkitab.datatransfer.model.Rpp


interface ReadWriteStorageInterface : ReadonlyStorageInterface {
    fun replaceHistory(entries: List<History.Entry>)

    fun replacePin(pin: Pin)

    fun replaceRpp(rpp: Rpp)
}
