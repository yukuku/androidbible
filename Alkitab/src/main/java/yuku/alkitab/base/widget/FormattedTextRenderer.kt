package yuku.alkitab.base.widget

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan

object FormattedTextRenderer {

    interface TagListener {
        fun onTag(tag: String, buffer: Spannable, start: Int, end: Int) {}
    }

    private val EMPTY_TAG_LISTENER = object : TagListener {}

    data class TagMark(val start: Int, val tag: String)

    /**
     * Renders a simple formatted text. This is a much simpler version of [VerseRenderer],
     * only some tags are supported. The supported tags are:
     *
     *     @9...@7 for italics
     *     @8 for line break
     *     @<tag@>text@/ for tagging text with tag
     *
     * @param text String with formatting tags
     * @param mustHaveFormattedHeader when true, the text must start with "@@" to enable formatting, otherwise, the text
     * @param appendToThis If not null, the results are appended to this string instead of newly created. Note that
     * the contents of this parameter will be modified.
     * @param tagListener callback that will potentially be called zero to many times
     */
    @JvmStatic
    @JvmOverloads
    fun render(
        text: String,
        mustHaveFormattedHeader: Boolean = false,
        appendToThis: SpannableStringBuilder? = null,
        tagListener: TagListener = EMPTY_TAG_LISTENER
    ): SpannableStringBuilder {
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
        val tagMarks = mutableListOf<TagMark>() // to support nested tags
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
                '0', '1', '2', '3', '4', '5', '6', '^', '>' -> {
                }
                '<' -> {
                    val startResult = sb.length
                    // Since we can't have any other formatting tags, immediately find the closing tag
                    val end = text.indexOf("@>", pos + 1)
                    if (end != -1) {
                        val tag = text.substring(pos + 1, end)
                        tagMarks += TagMark(startResult, tag)
                        pos = end + 1
                    }
                }
                '/' -> {
                    val tagMark = tagMarks.lastOrNull()
                    if (tagMark != null) {
                        tagMarks.removeAt(tagMarks.size - 1)
                        tagListener.onTag(tagMark.tag, sb, tagMark.start, sb.length)
                    }
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
