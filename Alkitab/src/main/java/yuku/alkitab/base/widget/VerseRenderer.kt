package yuku.alkitab.base.widget

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari

object VerseRenderer {
    // Unicode superscript digits 0..9 (U+2070, U+00B9, U+00B2, U+00B3, U+2074..U+2079).
    // Indexed by digit value, used by appendSuperscriptNumber to render footnote markers.
    private val superscriptDigits = charArrayOf(
        '\u2070', '\u00b9', '\u00b2', '\u00b3', '\u2074', '\u2075', '\u2076', '\u2077', '\u2078', '\u2079',
    )

    // U+203B REFERENCE MARK ("※") — placed inline where a cross-reference tag (@<x..@>) appears.
    const val XREF_MARK: Char = '\u203b'

    class VerseNumberSpan(private val applyColor: Boolean) : MetricAffectingSpan() {
        override fun updateMeasureState(tp: TextPaint) {
            tp.baselineShift += (tp.ascent() * 0.3f + 0.5f).toInt()
            tp.textSize = tp.textSize * 0.7f
        }

        override fun updateDrawState(tp: TextPaint) {
            tp.baselineShift += (tp.ascent() * 0.3f + 0.5f).toInt()
            tp.textSize = tp.textSize * 0.7f
            if (applyColor) {
                tp.color = App.services.uiDimensions.applied().verseNumberColor
            }
        }
    }

    class FormattedTextResult {
        @JvmField
        var result: CharSequence? = null
    }

    private fun createLeadingMarginSpan(all: Int): LeadingMarginSpan.Standard = LeadingMarginSpan.Standard(all, all)

    private fun createLeadingMarginSpan(first: Int, rest: Int): LeadingMarginSpan.Standard = LeadingMarginSpan.Standard(first, rest)

    private val buf_char_: ThreadLocal<CharArray> = ThreadLocal.withInitial { CharArray(1024) }

    private val buf_tag_: ThreadLocal<StringBuilder> = ThreadLocal.withInitial { StringBuilder(100) }

    /**
     * @param lText TextView for verse text, but can be null if rendering is for non-display
     * @param lVerseNumber TextView for verse number, but can be null if rendering is for non-display
     * @param ftr optional container for result that contains the verse text with span formattings, without the verse numbers
     * @return how many characters was used before the real start of verse text. This will be > 0 if the verse number is embedded inside lText.
     */
    // Verse text formatting markers (the tokens that follow an '@' inside a "@@"-prefixed verse):
    //   @@        start a verse containing paragraphs or formatting
    //   @0..@4    start a paragraph with indent 0..4
    //   @^        start-of-paragraph marker (first-line indent + hanging rest indent)
    //   @6 / @5   start / end of red text (Jesus' words)
    //   @9 / @7   start / end of italic
    //   @8        put a blank line to the next verse
    //   @< ... @> special tag (e.g. footnote, cross-reference); not visible for unsupported tags
    //   @/        end of special tag (since 2013-10-04 all special tags must be closed)
    @JvmStatic
    fun render(
        lText: TextView? = null,
        lVerseNumber: TextView? = null,
        isVerseNumberShown: Boolean = false,
        ari: Int,
        text: String,
        verseNumberText: String = Ari.toVerse(ari).toString(),
        highlightInfo: Highlights.Info? = null,
        checked: Boolean = false,
        inlineLinkSpanFactory: VerseInlineLinkSpan.Factory? = null,
        ftr: FormattedTextResult? = null,
    ): Int {
        val text_len = text.length

        // Determine if this verse text is a simple verse or formatted verse.
        // Formatted verses start with "@@".
        // Second character must be '@' too, if not it's wrong, we will fallback to simple render.
        if (text_len < 2 || text[0] != '@' || text[1] != '@') {
            ftr?.result = text
            return simpleRender(lText, lVerseNumber, isVerseNumberShown, text, verseNumberText, highlightInfo, checked)
        }

        // optimization, to prevent repeated calls to charAt()
        var text_c = buf_char_.get()!!
        if (text_c.size < text_len) {
            text_c = CharArray(text_len)
            buf_char_.set(text_c)
        }
        text.toCharArray(text_c, 0, 0, text_len)

        val sb = SpannableStringBuilder()

        // this has two uses
        // - to check whether a verse number has been written
        // - to check whether we need to put a new line when encountering a new para
        val startPosAfterVerseNumber = renderVerseNumber(sb, text_c, text_len, isVerseNumberShown, verseNumberText, checked)

        // initialize lVerseNumber to have no padding first
        lVerseNumber?.setPadding(0, 0, 0, 0)

        processFormattingCodes(text, text_c, text_len, sb, startPosAfterVerseNumber, verseNumberText, checked, ari, inlineLinkSpanFactory)

        applyHighlight(sb, highlightInfo, startPosAfterVerseNumber, checked)

        bindToTextViews(lText, lVerseNumber, sb, isVerseNumberShown, startPosAfterVerseNumber, verseNumberText)

        ftr?.result = sb

        return startPosAfterVerseNumber
    }

    /**
     * Writes the verse number prefix into [sb] when the formatted verse should embed it inline,
     * and returns the position past that prefix (or 0 if no prefix was written).
     *
     * The verse number is suppressed when the body opens with a paragraph marker that takes over
     * layout — specifically `@^` or `@1` through `@4`. `@0` does NOT suppress, by design (those
     * verses keep the inline number).
     */
    private fun renderVerseNumber(sb: SpannableStringBuilder, text_c: CharArray, text_len: Int, isVerseNumberShown: Boolean, verseNumberText: String, checked: Boolean): Int {
        // pos == 2 here (we start after "@@").
        if (text_len >= 4 && text_c[2] == '@' && (text_c[3] == '^' || text_c[3] in '1'..'4')) {
            // delegated to lVerseNumber instead
            return 0
        }
        if (isVerseNumberShown) {
            sb.append(verseNumberText)
            sb.setSpan(VerseNumberSpan(!checked), 0, sb.length, 0)
            sb.append("  ")
        }
        return sb.length
    }

    /**
     * Walks the formatted verse body starting after `"@@"` and applies all inline markers
     * (paragraph styles, italic/red runs, line breaks, special tags) to [sb]. Calls
     * [applyParaStyle] once at the end to flush the trailing paragraph's margin span.
     */
    private fun processFormattingCodes(text: String, text_c: CharArray, text_len: Int, sb: SpannableStringBuilder, startPosAfterVerseNumber: Int, verseNumberText: String, checked: Boolean, ari: Int, inlineLinkSpanFactory: VerseInlineLinkSpan.Factory?) {
        // '0'..'4', '^' indent 0..4 or new para; -1 undefined
        var paraType = -1
        // Note: this is intentionally 0 (not sb.length) so that for a verse with no paragraph
        // markers the implicit case-(-1) paragraph's LeadingMarginSpan covers the whole sb —
        // including the verse-number prefix that renderVerseNumber wrote. This also produces
        // the documented two-paragraph behavior for "@@@0Body" where the first paragraph just
        // covers the verse-number prefix.
        var startPara = 0
        var startRed = -1
        var startItalic = -1
        var inSpecialTag = false // between @< and @>
        val tag = buf_tag_.get()!!

        var pos = 2 // we start after "@@"

        while (true) {
            if (pos >= text_len) break

            val nextAt = text.indexOf('@', pos)

            if (nextAt == -1) { // no more, just append till the end of everything and exit
                sb.append(text, pos, text_len)
                break
            }

            if (inSpecialTag) { // are we in a tag?
                // we have encountered the end of a tag
                tag.setLength(0)
                tag.append(text, pos, nextAt)
                pos = nextAt
            } else {
                // insert all text until the nextAt
                if (nextAt != pos) /* extra check for optimization (prevent call to sb.append()) */ {
                    sb.append(text, pos, nextAt)
                    pos = nextAt
                }
            }

            pos++
            // just in case
            if (pos >= text_len) break

            when (val marker = text_c[pos]) {
                '0', '1', '2', '3', '4', '^' -> {
                    // apply previous
                    applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0)
                    if (sb.length > startPosAfterVerseNumber) {
                        sb.append("\n")
                    }
                    // store current
                    paraType = marker.code
                    startPara = sb.length
                }
                '6' -> startRed = sb.length
                '5' -> if (startRed != -1) {
                    if (!checked) {
                        sb.setSpan(ForegroundColorSpan(App.services.uiDimensions.applied().fontRedColor), startRed, sb.length, 0)
                    }
                    startRed = -1
                }
                '9' -> startItalic = sb.length
                '7' -> if (startItalic != -1) {
                    sb.setSpan(StyleSpan(Typeface.ITALIC), startItalic, sb.length, 0)
                    startItalic = -1
                }
                '8' -> sb.append("\n")
                '<' -> inSpecialTag = true
                '>' -> inSpecialTag = false
                '/' -> processSpecialTag(sb, tag, inlineLinkSpanFactory, ari)
            }

            pos++
        }

        // flush the trailing paragraph
        applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0)
    }

    /**
     * Attaches a [BackgroundColorSpan] for the highlight. A partial highlight whose hash still
     * matches the rendered body covers only its stored offsets; otherwise the whole verse body
     * (after the verse-number prefix) is highlighted.
     *
     * In a checked verse, the run under the band gets its own text color, picked against the
     * band instead of the selection color used for the rest of the verse.
     */
    private fun applyHighlight(sb: SpannableStringBuilder, highlightInfo: Highlights.Info?, startPosAfterVerseNumber: Int, checked: Boolean) {
        if (highlightInfo == null) return

        val applied = App.services.uiDimensions.applied()
        val band = Highlights.blendOver(highlightInfo.colorRgb, applied.backgroundColor)

        val start: Int
        val end: Int
        if (highlightInfo.shouldRenderAsPartialForVerseText(sb.subSequence(startPosAfterVerseNumber, sb.length))) {
            val rawStart = startPosAfterVerseNumber + highlightInfo.partial!!.startOffset
            val rawEnd = startPosAfterVerseNumber + highlightInfo.partial!!.endOffset
            start = minOf(rawStart, rawEnd)
            end = maxOf(rawStart, rawEnd)
        } else {
            start = startPosAfterVerseNumber
            end = sb.length
        }

        sb.setSpan(BackgroundColorSpan(band), start, end, 0)
        if (checked) {
            val selectedVerseBgColor = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
            val textColor = TextColorUtil.getForCheckedVerseHighlight(applied.fontColor, selectedVerseBgColor, applied.backgroundColor, band)
            sb.setSpan(ForegroundColorSpan(textColor), start, end, 0)
        }
    }

    /**
     * Pushes the rendered [sb] into [lText] and updates [lVerseNumber] so the verse-number
     * gutter is shown only when the number is not already embedded inline.
     */
    private fun bindToTextViews(lText: TextView?, lVerseNumber: TextView?, sb: SpannableStringBuilder, isVerseNumberShown: Boolean, startPosAfterVerseNumber: Int, verseNumberText: String) {
        lText?.text = sb
        if (lVerseNumber != null) {
            lVerseNumber.visibility = if (isVerseNumberShown) View.VISIBLE else View.GONE
            if (startPosAfterVerseNumber > 0) {
                lVerseNumber.text = ""
            } else {
                lVerseNumber.text = verseNumberText
            }
        }
    }

    private fun processSpecialTag(sb: SpannableStringBuilder, tag: StringBuilder, inlineLinkSpanFactory: VerseInlineLinkSpan.Factory?, ari: Int) {
        val sb_len = sb.length
        if (tag.length < 2) return
        when (tag[0]) {
            'f' -> try {
                val field = tag.substring(1).toInt()
                if (field < 1 || field > 255) throw NumberFormatException()
                appendSuperscriptNumber(sb, field)
                inlineLinkSpanFactory?.let {
                    sb.setSpan(it.create(VerseInlineLinkSpan.Type.footnote, ari shl 8 or field), sb_len, sb.length, 0)
                }
            } catch (_: NumberFormatException) {
                reportInvalidSpecialTag("Invalid footnote tag at ari 0x${Integer.toHexString(ari)}: $tag")
            }
            'x' -> try {
                val field = tag.substring(1).toInt()
                if (field < 1 || field > 255) throw NumberFormatException()
                sb.append(XREF_MARK)
                inlineLinkSpanFactory?.let {
                    sb.setSpan(it.create(VerseInlineLinkSpan.Type.xref, ari shl 8 or field), sb_len, sb.length, 0)
                }
            } catch (_: NumberFormatException) {
                reportInvalidSpecialTag("Invalid xref tag at ari 0x${Integer.toHexString(ari)}: $tag")
            }
        }
    }

    private var invalidSpecialTagToast: Toast? = null

    private fun reportInvalidSpecialTag(msg: String) {
        Handler(Looper.getMainLooper()).post {
            val toast = invalidSpecialTagToast?.also { it.setText(msg) }
                ?: Toast.makeText(App.context, msg, Toast.LENGTH_SHORT).also { invalidSpecialTagToast = it }
            toast.show()
        }
    }

    @JvmStatic
    fun appendSuperscriptNumber(sb: SpannableStringBuilder, field: Int) {
        if (field in 0..9) {
            sb.append(superscriptDigits[field])
        } else if (field >= 10) {
            for (c in field.toString()) {
                sb.append(superscriptDigits[c - '0'])
            }
        }
        // should not be negative
    }

    /**
     * @param paraType if -1, will apply the same thing as when paraType is 0 and firstLineWithVerseNumber is true.
     * @param firstLineWithVerseNumber If this is formatting for the first paragraph of a verse and that paragraph contains a verse number, so we can apply more lefty first-line indent.
     * This only applies if the paraType is 0.
     */
    private fun applyParaStyle(sb: SpannableStringBuilder, paraType: Int, startPara: Int, verseNumberText: String, firstLineWithVerseNumber: Boolean) {
        val len = sb.length

        if (startPara == len) return

        val indentSpacingExtraUnits = if (verseNumberText.length < 3) 0 else verseNumberText.length - 2
        val applied = App.services.uiDimensions.applied()

        when (paraType) {
            -1, '0'.code -> {
                if (firstLineWithVerseNumber) {
                    sb.setSpan(createLeadingMarginSpan(0, applied.indentParagraphRest), startPara, len, 0)
                } else {
                    sb.setSpan(createLeadingMarginSpan(applied.indentParagraphRest), startPara, len, 0)
                }
            }
            '1'.code -> sb.setSpan(createLeadingMarginSpan(applied.indentSpacing1 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0)
            '2'.code -> sb.setSpan(createLeadingMarginSpan(applied.indentSpacing2 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0)
            '3'.code -> sb.setSpan(createLeadingMarginSpan(applied.indentSpacing3 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0)
            '4'.code -> sb.setSpan(createLeadingMarginSpan(applied.indentSpacing4 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0)
            '^'.code -> sb.setSpan(createLeadingMarginSpan(applied.indentParagraphFirst, applied.indentParagraphRest), startPara, len, 0)
        }
    }

    /**
     * @return how many characters were used before the actual start of verse text. This will be > 0 if the verse number is embedded inside lText.
     */
    private fun simpleRender(lText: TextView?, lVerseNumber: TextView?, isVerseNumberShown: Boolean, text: String, verseNumberText: String, highlightInfo: Highlights.Info?, checked: Boolean): Int {
        val sb = SpannableStringBuilder()

        // verse number
        if (isVerseNumberShown) {
            sb.append(verseNumberText).append("  ")
            sb.setSpan(VerseNumberSpan(!checked), 0, verseNumberText.length, 0)
        }
        val startPosAfterVerseNumber = sb.length

        // verse text
        sb.append(text)
        if (isVerseNumberShown) {
            sb.setSpan(createLeadingMarginSpan(0, App.services.uiDimensions.applied().indentParagraphRest), 0, sb.length, 0)
        } else {
            sb.setSpan(createLeadingMarginSpan(App.services.uiDimensions.applied().indentParagraphRest), 0, sb.length, 0)
        }

        applyHighlight(sb, highlightInfo, startPosAfterVerseNumber, checked)

        lText?.text = sb

        // initialize lVerseNumber to have no padding first
        if (lVerseNumber != null) {
            lVerseNumber.setPadding(0, 0, 0, 0)
            lVerseNumber.visibility = View.GONE
            lVerseNumber.text = ""
        }

        return startPosAfterVerseNumber
    }
}
