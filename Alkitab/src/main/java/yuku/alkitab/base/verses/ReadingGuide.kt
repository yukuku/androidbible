package yuku.alkitab.base.verses

import yuku.afw.storage.Preferences
import yuku.alkitab.base.util.ReadingRange
import yuku.alkitab.debug.R

enum class ReadingGuideMode(val preferenceValue: String) {
    OFF("off"), LABELS("labels"), LINE("line"), CAPTION("caption");

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.preferenceValue == value } ?: OFF
        fun selected() = fromPreference(Preferences.getString(R.string.pref_currentReadingDisplay_key, R.string.pref_currentReadingDisplay_default))
    }
}

data class ReadingGuide(val mode: ReadingGuideMode, val ranges: List<ReadingRange>) {
    fun includes(ari: Int) = ranges.any { ari in it }
    fun startsAt(ari: Int) = ranges.any { it.start == ari }
    fun endsAt(ari: Int) = ranges.any { it.end == ari }

    companion object {
        val NONE = ReadingGuide(ReadingGuideMode.OFF, emptyList())
    }
}
