package yuku.alkitab.base.util

import yuku.afw.storage.Preferences
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.App
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

/**
 * Persists the active passage and, for reading plans, the exact progress item it belongs to.
 */
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

    data class Plan(val name: String, val day: Int, val sequence: Int)

    @JvmStatic
    fun setReadingPlan(ariStart: Int, ariEnd: Int, name: String, day: Int, sequence: Int) {
        require(name.isNotEmpty() && day >= 0 && sequence in 0..255)
        Preferences.withTransaction {
            Preferences.setString(Prefkey.current_reading_ranges, App.getDefaultGson().toJson(intArrayOf(ariStart, ariEnd)))
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
            Preferences.setString(Prefkey.current_reading_plan_name, name)
            Preferences.setInt(Prefkey.current_reading_plan_day, day)
            Preferences.setInt(Prefkey.current_reading_plan_sequence, sequence)
        }
        AppEvents.emitCurrentReadingChanged()
    }

    @JvmStatic
    fun getPlan(): Plan? {
        if (get() == null) return null
        val name = Preferences.getString(Prefkey.current_reading_plan_name) ?: return null
        val day = Preferences.getInt(Prefkey.current_reading_plan_day, -1)
        val sequence = Preferences.getInt(Prefkey.current_reading_plan_sequence, -1)
        return if (name.isNotEmpty() && day >= 0 && sequence in 0..255) Plan(name, day, sequence) else null
    }

    @JvmStatic
    fun reference(version: Version): String? = get()?.toList()?.chunked(2)?.joinToString("; ") {
        if (it[0] == it[1] && Ari.toChapter(it[0]) == 0) version.reference(it[0]) else version.referenceRange(it[0], it[1])
    }

    private fun clearPlan() {
        Preferences.remove(Prefkey.current_reading_plan_name)
        Preferences.remove(Prefkey.current_reading_plan_day)
        Preferences.remove(Prefkey.current_reading_plan_sequence)
    }

    @JvmStatic
    fun getPlanCompletion(): Boolean? {
        val plan = installedPlan() ?: return null
        val codes = App.services.storage.db.getAllReadingCodesByReadingPlanProgressGid(ReadingPlan.gidFromName(plan.name))
        val code = (plan.day shl 8) or plan.sequence
        return (0 until codes.size()).any { codes.get(it) == code }
    }

    private fun installedPlan(): Plan? {
        val plan = getPlan() ?: return null
        return plan.takeIf { it.name in App.services.storage.db.listReadingPlanNames() }
    }

    @JvmStatic
    fun setPlanCompleted(completed: Boolean): Boolean {
        val plan = installedPlan() ?: return false
        ReadingPlanManager.updateReadingPlanProgress(plan.name, plan.day, plan.sequence, completed)
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
