package yuku.alkitab.base.util

import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

object CurrentReading {
    @JvmStatic
    fun setRanges(aris: IntArray) {
        Preferences.withTransaction {
            Preferences.setString(Prefkey.current_reading_ranges, App.getDefaultGson().toJson(aris))
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
            clearPlan()
        }

        AppEvents.emitCurrentReadingChanged()
    }

    data class Plan(val name: String, val day: Int)

    @JvmStatic
    fun setReadingPlan(aris: IntArray, name: String, day: Int) {
        Preferences.withTransaction {
            Preferences.setString(Prefkey.current_reading_ranges, App.getDefaultGson().toJson(aris))
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
            Preferences.setString(Prefkey.current_reading_plan_name, name)
            Preferences.setInt(Prefkey.current_reading_plan_day, day)
        }
        AppEvents.emitCurrentReadingChanged()
    }

    @JvmStatic
    fun getPlan(): Plan? {
        if (getRanges() == null) return null
        val name = Preferences.getString(Prefkey.current_reading_plan_name) ?: return null
        return Plan(
            name,
            Preferences.getInt(Prefkey.current_reading_plan_day, 0),
        )
    }

    @JvmStatic
    fun reference(version: Version, rangeIndex: Int): String? {
        val ranges = getRanges() ?: return null
        if (rangeIndex !in 0 until ranges.size / 2) return null
        val start = ranges[rangeIndex * 2]
        val end = ranges[rangeIndex * 2 + 1]
        return if (start == end && Ari.toChapter(start) == 0) version.reference(start) else version.referenceRange(start, end)
    }

    private fun clearPlan() {
        Preferences.remove(Prefkey.current_reading_plan_name)
        Preferences.remove(Prefkey.current_reading_plan_day)
    }

    @JvmStatic
    fun getPlanCompletions(): BooleanArray? {
        val plan = installedPlan() ?: return null
        val ranges = getRanges() ?: return null
        val codes = App.services.storage.db.getAllReadingCodesByReadingPlanProgressGid(ReadingPlan.gidFromName(plan.name))
        val completed = (0 until codes.size()).map { codes.get(it) }.toSet()
        return BooleanArray(ranges.size / 2) { index -> ((plan.day shl 8) or index) in completed }
    }

    @JvmStatic
    fun getPlanCompletion(rangeIndex: Int): Boolean? = getPlanCompletions()?.getOrNull(rangeIndex)

    private fun installedPlan(): Plan? {
        val plan = getPlan() ?: return null
        return plan.takeIf { it.name in App.services.storage.db.listReadingPlanNames() }
    }

    @JvmStatic
    fun setPlanCompleted(completed: Boolean, rangeIndex: Int): Boolean {
        val plan = installedPlan() ?: return false
        val ranges = getRanges() ?: return false
        if (rangeIndex !in 0 until ranges.size / 2) return false
        ReadingPlanManager.updateReadingPlanProgress(plan.name, plan.day, rangeIndex, completed)
        AppEvents.emitReadingPlanProgressChanged()
        return true
    }

    @JvmStatic
    fun clear() {
        Preferences.withTransaction {
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
            Preferences.remove(Prefkey.current_reading_ranges)
            clearPlan()
        }

        AppEvents.emitCurrentReadingChanged()
    }

    /**
     * @return null if no current reading
     */
    @JvmStatic
    fun getRanges(): IntArray? {
        Preferences.getString(Prefkey.current_reading_ranges)?.let { json ->
            val ranges = runCatching { App.getDefaultGson().fromJson(json, IntArray::class.java) }.getOrNull()
            return ranges?.takeIf { it.isNotEmpty() && it.size % 2 == 0 }
        }
        if (!Preferences.contains(Prefkey.current_reading_ari_start)) {
            return null
        }

        return intArrayOf(
            Preferences.getInt(Prefkey.current_reading_ari_start, 0),
            Preferences.getInt(Prefkey.current_reading_ari_end, 0),
        )
    }
}
