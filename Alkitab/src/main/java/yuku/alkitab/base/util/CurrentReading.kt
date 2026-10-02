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
    fun set(ariStart: Int, ariEnd: Int) {
        setRanges(intArrayOf(ariStart, ariEnd))
    }

    @JvmStatic
    fun setRanges(aris: IntArray) {
        require(aris.isNotEmpty() && aris.size % 2 == 0)
        Preferences.withTransaction {
            Preferences.setString(Prefkey.current_reading_ranges, App.getDefaultGson().toJson(aris))
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
            clearPlan()
        }

        AppEvents.emitCurrentReadingChanged()
    }

    data class Plan(val name: String, val day: Int, val firstSequence: Int)

    @JvmStatic
    @JvmOverloads
    fun setReadingPlan(aris: IntArray, name: String, day: Int, firstSequence: Int = 0) {
        require(aris.isNotEmpty() && aris.size % 2 == 0)
        require(name.isNotEmpty() && day >= 0 && firstSequence >= 0 && firstSequence.toLong() + aris.size / 2 <= 256)
        Preferences.withTransaction {
            Preferences.setString(Prefkey.current_reading_ranges, App.getDefaultGson().toJson(aris))
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
            Preferences.setString(Prefkey.current_reading_plan_name, name)
            Preferences.setInt(Prefkey.current_reading_plan_day, day)
            Preferences.setInt(Prefkey.current_reading_plan_sequence, firstSequence)
        }
        AppEvents.emitCurrentReadingChanged()
    }

    @JvmStatic
    fun getPlan(): Plan? {
        val ranges = get() ?: return null
        val name = Preferences.getString(Prefkey.current_reading_plan_name) ?: return null
        val day = Preferences.getInt(Prefkey.current_reading_plan_day, -1)
        val sequence = Preferences.getInt(Prefkey.current_reading_plan_sequence, -1)
        return if (name.isNotEmpty() && day >= 0 && sequence >= 0 && sequence.toLong() + ranges.size / 2 <= 256) Plan(name, day, sequence) else null
    }

    @JvmStatic
    fun reference(version: Version): String? = get()?.toList()?.chunked(2)?.joinToString("; ") {
        if (it[0] == it[1] && Ari.toChapter(it[0]) == 0) version.reference(it[0]) else version.referenceRange(it[0], it[1])
    }

    @JvmStatic
    fun reference(version: Version, rangeIndex: Int): String? {
        val ranges = get() ?: return null
        if (rangeIndex !in 0 until ranges.size / 2) return null
        val start = ranges[rangeIndex * 2]
        val end = ranges[rangeIndex * 2 + 1]
        return if (start == end && Ari.toChapter(start) == 0) version.reference(start) else version.referenceRange(start, end)
    }

    private fun clearPlan() {
        Preferences.remove(Prefkey.current_reading_plan_name)
        Preferences.remove(Prefkey.current_reading_plan_day)
        Preferences.remove(Prefkey.current_reading_plan_sequence)
    }

    @JvmStatic
    fun getPlanCompletions(): BooleanArray? {
        val plan = installedPlan() ?: return null
        val ranges = get() ?: return null
        val codes = App.services.storage.db.getAllReadingCodesByReadingPlanProgressGid(ReadingPlan.gidFromName(plan.name))
        val completed = (0 until codes.size()).map { codes.get(it) }.toSet()
        return BooleanArray(ranges.size / 2) { index -> ((plan.day shl 8) or (plan.firstSequence + index)) in completed }
    }

    @JvmStatic
    @JvmOverloads
    fun getPlanCompletion(rangeIndex: Int = 0): Boolean? = getPlanCompletions()?.getOrNull(rangeIndex)

    private fun installedPlan(): Plan? {
        val plan = getPlan() ?: return null
        return plan.takeIf { it.name in App.services.storage.db.listReadingPlanNames() }
    }

    @JvmStatic
    @JvmOverloads
    fun setPlanCompleted(completed: Boolean, rangeIndex: Int = 0): Boolean {
        val plan = installedPlan() ?: return false
        val ranges = get() ?: return false
        if (rangeIndex !in 0 until ranges.size / 2) return false
        ReadingPlanManager.updateReadingPlanProgress(plan.name, plan.day, plan.firstSequence + rangeIndex, completed)
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
    fun get(): IntArray? {
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
