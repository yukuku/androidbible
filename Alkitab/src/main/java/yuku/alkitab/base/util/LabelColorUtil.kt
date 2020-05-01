package yuku.alkitab.base.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import yuku.alkitab.model.Label

object LabelColorUtil {
    @JvmStatic
    fun encodeBackground(colorRgb_background: Int): String {
        val sb = StringBuilder(10)
        sb.append('b') // 'b': background color
        val h = Integer.toHexString(colorRgb_background)
        for (x in h.length until 6) {
            sb.append('0')
        }
        sb.append(h)
        return sb.toString()
    }

    /**
     * @return colorRgb (without alpha) or -1 if can't decode
     */
    @JvmStatic
    fun decodeBackground(backgroundColor: String?): Int {
        if (backgroundColor == null || backgroundColor.isEmpty()) return -1
        return if (backgroundColor.length >= 7 && backgroundColor[0] == 'b') { // 'b': background color
            Integer.parseInt(backgroundColor.substring(1, 7), 16)
        } else {
            -1
        }
    }

    @JvmStatic
    fun getForegroundBasedOnBackground(colorRgb: Int): Int {
        val hsl = floatArrayOf(0f, 0f, 0f)
        ColorUtils.RGBToHSL(Color.red(colorRgb), Color.green(colorRgb), Color.blue(colorRgb), hsl)

        if (hsl[2] > 0.5f) {
            hsl[2] -= 0.44f
        } else {
            hsl[2] += 0.44f
        }

        return ColorUtils.HSLToColor(hsl) and 0xffffff
    }

    @JvmStatic
    fun apply(label: Label, view: TextView): Int {
        var bgColorRgb = decodeBackground(label.backgroundColor)
        if (bgColorRgb == -1) {
            bgColorRgb = 0x212121 // default color Grey 900
        }

        var grad: GradientDrawable? = null

        val bg = view.background
        if (bg is GradientDrawable) {
            grad = bg
        } else if (bg is StateListDrawable) {
            val current = bg.current
            if (current is GradientDrawable) {
                grad = current
            }
        }
        if (grad != null) {
            grad.setColor(0xff000000.toInt() or bgColorRgb)
            val labelColor = 0xff000000.toInt() or getForegroundBasedOnBackground(bgColorRgb)
            view.setTextColor(labelColor)
            return labelColor
        }
        return 0
    }
}
