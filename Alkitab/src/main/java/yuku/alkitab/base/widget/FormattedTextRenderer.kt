package yuku.alkitab.base.widget

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan

object FormattedTextRenderer {
    /**
     * Renders a simple formatted text. This is a much simpler version of [VerseRenderer],
     * only some tags are supported. The supported tags are:
     *
     *     @9...@7 for italics
     *     @8 for line break
     *
     * @param text String with formatting tags
     * @param mustHaveFormattedHeader when true, the text must start with "@@" to enable formatting, otherwise, the text
     * @param appendToThis If not null, the results are appended to this string instead of newly created. Note that
     * the contents of this parameter will be modified.
     */
    @JvmStatic
    @JvmOverloads
    fun render(text: String, mustHaveFormattedHeader: Boolean = false, appendToThis: SpannableStringBuilder? = null): SpannableStringBuilder {
        if (mustHaveFormattedHeader) {
            if (!text.startsWith("@@")) {
                return appendToThis?.append(text) ?: SpannableStringBuilder(text)
            }
        }

        val text_len = text.length
        var pos = 0
        if (text.startsWith("@@")) { // always absorb "@@" at the beginning
            pos = 2
        }

        var startItalic = -1
        val sb = appendToThis ?: SpannableStringBuilder()
        while (true) {
            if (pos >= text_len) {
                break
            }
            val nextAt = text.indexOf('@', pos)
            if (nextAt == -1) { // no more, just append till the end of everything and exit
                sb.append(text, pos, text_len)
                break
            }

            // insert all text until the nextAt
            if (nextAt != pos) /* optimization */ {
                sb.append(text, pos, nextAt)
                pos = nextAt
            }
            pos++
            // just in case
            if (pos >= text_len) {
                break
            }
            when (val marker = text[pos]) {
                '9' -> {
                    startItalic = sb.length
                }
                '7' -> {
                    if (startItalic != -1) {
                        sb.setSpan(StyleSpan(Typeface.ITALIC), startItalic, sb.length, 0)
                        startItalic = -1
                    }
                }
                '8' -> {
                    sb.append("\n")
                }
                '0', '1', '2', '3', '4', '5', '6', '^' -> {
                }
                else -> {
                    // just print out as-is
                    sb.append("@").append(marker)
                }
            }
            pos++
        }
        return sb
    }
}
