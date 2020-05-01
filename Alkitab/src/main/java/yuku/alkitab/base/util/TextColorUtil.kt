package yuku.alkitab.base.util

import androidx.core.graphics.ColorUtils

object TextColorUtil {

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
}
