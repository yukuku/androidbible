package yuku.alkitab.datatransfer.process

import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Rpp
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label
import yuku.alkitab.model.ProgressMark


interface ReadonlyStorageInterface {
    fun history(): List<History.Entry>

    fun markers(): List<Marker>

    fun labels(): List<Label>

    fun markerLabels(): List<Marker_Label>

    fun pins(): List<ProgressMark>

    fun rpps(): List<Rpp>
}
