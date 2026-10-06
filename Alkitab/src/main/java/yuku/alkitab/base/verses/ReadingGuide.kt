package yuku.alkitab.base.verses

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import yuku.afw.storage.Preferences
import yuku.alkitab.base.util.ReadingRange
import yuku.alkitab.debug.R

enum class ReadingGuideMode(val preferenceValue: String) {
    OFF("off"), LINE("line");

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

internal fun readingGuideLineColor(fontColor: Int, backgroundColor: Int): Int {
    val foreground = ColorUtils.setAlphaComponent(fontColor, 255)
    val background = ColorUtils.setAlphaComponent(backgroundColor, 255)
    if (ColorUtils.calculateContrast(foreground, background) >= 3.0) return foreground
    return if (ColorUtils.calculateContrast(Color.BLACK, background) >= ColorUtils.calculateContrast(Color.WHITE, background)) Color.BLACK else Color.WHITE
}
