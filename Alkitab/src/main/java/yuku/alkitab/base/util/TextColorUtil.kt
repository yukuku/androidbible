package yuku.alkitab.base.util

import androidx.core.graphics.ColorUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

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

    private data class ContrastKey(val foreground: Int, val background: Int, val percent: Int)

    private val contrastCache = ConcurrentHashMap<ContrastKey, Int>()

    fun normalizeAdjustment(value: Float): Float =
        if (value.isFinite()) (value.coerceIn(0f, 1f) * 100f).roundToInt() / 100f else 1f

    fun targetContrast(adjustment: Float): Double =
        1.0 + 3.5 * (normalizeAdjustment(adjustment) * 100f).roundToInt() / 100.0

    fun readingBackground(pageColor: Int, selectionColor: Int, isSelected: Boolean): Int {
        val page = pageColor or 0xff000000.toInt()
        return if (isSelected) {
            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(selectionColor, CHECKED_VERSE_OVERLAY_ALPHA), page)
        } else page
    }

    fun adjustForContrast(textColor: Int, backgroundColor: Int, adjustment: Float): Int {
        val foreground = textColor or 0xff000000.toInt()
        val background = backgroundColor or 0xff000000.toInt()
        val amount = normalizeAdjustment(adjustment)
        if (amount == 0f) return foreground
        if (contrastCache.size > 512) contrastCache.clear()
        val key = ContrastKey(foreground, background, (amount * 100f).roundToInt())
        return contrastCache.getOrPut(key) { solveContrast(foreground, background, targetContrast(amount)) }
    }

    private fun solveContrast(foreground: Int, background: Int, targetContrast: Double): Int {
        if (ColorUtils.calculateContrast(foreground, background) >= targetContrast) return foreground

        fun candidate(target: Int): Int? {
            if (ColorUtils.calculateContrast(target, background) < targetContrast) return null
            var lo = 0f
            var hi = 1f
            repeat(16) {
                val mid = (lo + hi) / 2f
                val color = ColorUtils.blendARGB(foreground, target, mid)
                if (ColorUtils.calculateContrast(color, background) >= targetContrast) hi = mid else lo = mid
            }
            return ColorUtils.blendARGB(foreground, target, hi)
        }

        fun distance(color: Int): Int = listOf(16, 8, 0).sumOf { shift ->
            val delta = ((foreground shr shift) and 0xff) - ((color shr shift) and 0xff)
            delta * delta
        }

        fun chroma(color: Int): Int {
            val channels = listOf((color shr 16) and 0xff, (color shr 8) and 0xff, color and 0xff)
            return channels.max() - channels.min()
        }

        val candidates = listOfNotNull(candidate(0xff000000.toInt()), candidate(0xffffffff.toInt()))
        val tinted = candidates.filter { chroma(it) > 0 }
        return (if (chroma(foreground) > 0 && tinted.isNotEmpty()) tinted else candidates).minBy(::distance)
    }
}
