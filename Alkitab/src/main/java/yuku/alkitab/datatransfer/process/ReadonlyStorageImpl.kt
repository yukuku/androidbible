package yuku.alkitab.datatransfer.process

import yuku.alkitab.base.S
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.Gid
import yuku.alkitab.datatransfer.model.Rpp
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label
import yuku.alkitab.model.ProgressMark

open class ReadonlyStorageImpl : ReadonlyStorageInterface {
    override fun history(): List<History.Entry> {
        return History.listAllEntries()
    }

    override fun markers(): List<Marker> {
        return S.getDb().listAllMarkers()
    }

    override fun labels(): List<Label> {
        return S.getDb().listAllLabels()
    }

    override fun markerLabels(): List<Marker_Label> {
        return S.getDb().listAllMarker_Labels()
    }

    override fun pins(): List<ProgressMark> {
        return S.getDb().listAllProgressMarks()
    }

    override fun rpps(): List<Rpp> {
        val res = mutableListOf<Rpp>()

        // lookup map for startTime
        val startTimes = S.getDb().listAllReadingPlanInfo().associate { info ->
            ReadingPlan.gidFromName(info.name) to info.startTime
        }

        // The only source of data is from ReadingPlanProgress table,
        // but since reading plans with no done is not listed in ReadingPlanProgress,
        // we need to consult ReadingPlan table to know what they are.
        val map: Map<String, Set<Int>> = S.getDb().readingPlanProgressSummaryForSync
        for ((gid, set) in map) {
            val startTime = startTimes[gid] ?: continue

            res += Rpp(
                gid = Gid(gid),
                startTime = startTime,
                done = set.toList(),
            )
        }

        // add remaining reading plans without any done
        for ((gid, startTime) in startTimes) {
            if (map.containsKey(gid)) continue // already recorded above

            res += Rpp(
                gid = Gid(gid),
                startTime = startTime,
                done = emptyList(),
            )
        }

        return res
    }
}
