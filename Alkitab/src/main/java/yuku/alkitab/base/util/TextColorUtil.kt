package yuku.alkitab.base.util

import androidx.core.graphics.ColorUtils

object TextColorUtil {

    /** Alpha for the selection overlay on a checked verse row. */
    const val CHECKED_VERSE_OVERLAY_ALPHA = 0xa0

    @JvmStatic
    fun getSearchKeywordByBrightness(brightness: Float): Int {
        return if (brightness < 0.5f) {
            0xff69f0ae.toInt() // Green A200
        } else {
            0xff00c853.toInt() // Green A700
        }
    }

    @JvmStatic
    fun getForCheckedVerse(checkedVerseBgColor: Int): Int {
        return if (ColorUtils.calculateLuminance(checkedVerseBgColor) > 0.4) {
            0xff000000.toInt()
        } else {
            0xffffffff.toInt()
        }
    }

    /**
     * Text color for a highlighted run inside a checked verse.
     *
     * The highlight band is drawn on top of the selection overlay. So the color from
     * [getForCheckedVerse] can be wrong there, for example white text on a yellow highlight.
     * This picks whichever color, the reading color or the checked-verse color, has better
     * contrast against what the band actually paints.
     */
    @JvmStatic
    fun getForCheckedVerseHighlight(fontColor: Int, checkedVerseBgColor: Int, pageBackgroundColor: Int, highlightBand: Int): Int {
        val checkedTextColor = getForCheckedVerse(checkedVerseBgColor)
        val overlay = ColorUtils.setAlphaComponent(checkedVerseBgColor, CHECKED_VERSE_OVERLAY_ALPHA)
        val selection = ColorUtils.compositeColors(overlay, pageBackgroundColor or 0xff000000.toInt())
        val painted = ColorUtils.compositeColors(highlightBand, selection)
        val readingColor = fontColor or 0xff000000.toInt()
        return if (ColorUtils.calculateContrast(checkedTextColor, painted) >= ColorUtils.calculateContrast(readingColor, painted)) {
            checkedTextColor
        } else {
            readingColor
        }
    }
}
