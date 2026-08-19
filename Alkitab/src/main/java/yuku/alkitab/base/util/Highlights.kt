package yuku.alkitab.base.util

import androidx.annotation.Keep
import com.google.gson.JsonSyntaxException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import yuku.alkitab.base.App

object Highlights {
    private val TAG: String = Highlights::class.java.simpleName

    /** Perceptual (Oklab) distance a highlight band aims to sit from the background it covers. */
    private const val TARGET_DELTA = 0.22f

    /** Smallest raw sRGB separation the band may keep from the background, channels scaled to 0..1. */
    private const val MIN_SRGB_DELTA = 0.30f

    private val blendCache = ConcurrentHashMap<Long, Int>()

    private val tmpInfo: ThreadLocal<Info> = ThreadLocal.withInitial { Info() }

    private val tmpPartial: ThreadLocal<Info.Partial> = ThreadLocal.withInitial { Info.Partial() }

    @Keep
    class Info {
        /** rgb only, without the alpha */
        @JvmField var colorRgb: Int = 0
        @JvmField var partial: Partial? = null

        @Keep
        class Partial {
            /** hashCode of the plain text (not with formatting) */
            @JvmField var hashCode: Int = 0
            @JvmField var startOffset: Int = 0
            @JvmField var endOffset: Int = 0
        }

        fun shouldRenderAsPartialForVerseText(verseText: CharSequence): Boolean {
            val partial = partial
            return partial != null && partial.hashCode == Highlights.hashCode(verseText.toString()) && partial.startOffset <= verseText.length && partial.endOffset <= verseText.length
        }
    }


    /** Encode a full-verse highlight */
    @JvmStatic
    fun encode(colorRgb: Int): String {
        val info = tmpInfo.get()!!

        info.colorRgb = colorRgb
        info.partial = null

        return App.getDefaultGson().toJson(info)
    }

    /** Encode a partial highlight */
    @JvmStatic
    fun encode(colorRgb: Int, hashCode: Int, startOffset: Int, endOffset: Int): String {
        val info = tmpInfo.get()!!

        info.colorRgb = colorRgb

        val partial = tmpPartial.get()!!
        info.partial = partial
        partial.hashCode = hashCode
        partial.startOffset = startOffset
        partial.endOffset = endOffset

        return App.getDefaultGson().toJson(info)
    }

    /**
     * Decodes color code for highlight. It starts with the string "c" then 6 hex digits rrggbb.
     * @return highlight info, or null if cannot decode
     */
    @JvmStatic
    fun decode(text: String?): Info? {
        if (text == null) return null
        if (text.length >= 7 && text[0] == 'c') { // legacy compat
            return try {
                val colorRgb = text.substring(1, 7).toInt(16)
                val res = Info()
                res.colorRgb = colorRgb
                res.partial = null
                res
            } catch (e: NumberFormatException) {
                AppLog.e(TAG, "@@decode", e)
                null
            }
        } else if (text.startsWith("{")) { // json format
            return try {
                App.getDefaultGson().fromJson(text, Info::class.java)
            } catch (e: JsonSyntaxException) {
                AppLog.e(TAG, "@@decode", e)
                null
            }
        } else {
            return null
        }
    }

    /**
     * Alpha-premixed highlight color, ready to draw over [backgroundColor].
     *
     * The alpha is solved so the highlighted band lands a fixed perceptual distance
     * ([TARGET_DELTA], measured in Oklab) away from the background it is drawn on. A color far
     * from the background (saturated blue on white) therefore needs very little alpha, while a
     * color close to it gets as much as it needs, up to fully opaque.
     *
     * [MIN_SRGB_DELTA] then floors the result. Oklab expands distances near black far beyond what
     * a screen in a lit room can resolve, so on a dark background the solved alpha alone would
     * leave the band invisible.
     */
    @JvmStatic
    fun blendOver(colorRgb: Int, backgroundColor: Int): Int {
        val rgb = colorRgb and 0xffffff
        val bg = backgroundColor and 0xffffff
        if (blendCache.size > 512) blendCache.clear()
        val alpha = blendCache.getOrPut((rgb.toLong() shl 32) or bg.toLong()) { solveAlpha(rgb, bg) }
        return (alpha shl 24) or rgb
    }

    private fun solveAlpha(rgb: Int, bg: Int): Int {
        val srgbDelta = srgbDistance(rgb, bg)
        if (srgbDelta == 0f) return 0xff

        val bgLab = oklab(bg)
        val alpha = if (oklabDistance(oklab(rgb), bgLab) <= TARGET_DELTA) {
            1f
        } else {
            var lo = 0f
            var hi = 1f
            repeat(16) {
                val mid = (lo + hi) / 2f
                if (oklabDistance(oklab(srcOver(rgb, bg, mid)), bgLab) < TARGET_DELTA) lo = mid else hi = mid
            }
            (lo + hi) / 2f
        }

        // srcOver is linear in alpha, so the alpha reaching MIN_SRGB_DELTA is exact rather than solved.
        val floor = (MIN_SRGB_DELTA / srgbDelta).coerceAtMost(1f)
        return (maxOf(alpha, floor) * 255f + 0.5f).toInt().coerceIn(0, 0xff)
    }

    private fun srgbDistance(x: Int, y: Int): Float {
        val dr = (((x shr 16) and 0xff) - ((y shr 16) and 0xff)) / 255f
        val dg = (((x shr 8) and 0xff) - ((y shr 8) and 0xff)) / 255f
        val db = ((x and 0xff) - (y and 0xff)) / 255f
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun srcOver(rgb: Int, bg: Int, alpha: Float): Int {
        var res = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val c = (rgb shr shift) and 0xff
            val b = (bg shr shift) and 0xff
            val v = (alpha * c + (1f - alpha) * b + 0.5f).toInt().coerceIn(0, 0xff)
            res = res or (v shl shift)
        }
        return res
    }

    private class Oklab(val l: Float, val a: Float, val b: Float)

    private fun oklab(rgb: Int): Oklab {
        val r = srgbToLinear((rgb shr 16) and 0xff)
        val g = srgbToLinear((rgb shr 8) and 0xff)
        val b = srgbToLinear(rgb and 0xff)

        val lCone = cbrt(0.41222146f * r + 0.53633255f * g + 0.051445995f * b)
        val mCone = cbrt(0.2119035f * r + 0.6806995f * g + 0.10739696f * b)
        val sCone = cbrt(0.08830246f * r + 0.28171885f * g + 0.6299787f * b)

        return Oklab(
            0.21045426f * lCone + 0.7936178f * mCone - 0.004072047f * sCone,
            1.9779985f * lCone - 2.4285922f * mCone + 0.4505937f * sCone,
            0.025904037f * lCone + 0.78277177f * mCone - 0.80867577f * sCone,
        )
    }

    private fun srgbToLinear(component: Int): Float {
        val c = component / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun oklabDistance(x: Oklab, y: Oklab): Float {
        val dl = x.l - y.l
        val da = x.a - y.a
        val db = x.b - y.b
        return sqrt(dl * dl + da * da + db * db)
    }

    @JvmStatic
    fun hashCode(verseText: String): Int {
        return verseText.hashCode()
    }
}
