package yuku.alkitab.base.util

import androidx.annotation.Keep
import com.google.gson.JsonSyntaxException
import yuku.alkitab.base.App

object Highlights {
    private val TAG: String = Highlights::class.java.simpleName

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

    @JvmStatic
    fun alphaMix(colorRgb: Int): Int {
        return 0xa0000000.toInt() or (colorRgb and 0x00ffffff)
    }

    @JvmStatic
    fun hashCode(verseText: String): Int {
        return verseText.hashCode()
    }
}
