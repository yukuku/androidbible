package yuku.alkitab.base.widget

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.debug.R
import yuku.alkitab.util.Ari

/**
 * Walks the verse formatting grammar (`@@`, `@0`..`@4`, `@^`, `@5`/`@6`,
 * `@7`/`@9`, `@8`, `@<..@>`, `@/`) and produces an [AnnotatedString] plus the
 * offsets of every inline link (footnote `@<f..@>` / xref `@<x..@>`) and of
 * every ruby-annotated run (`@<r=ruby@>base@/`).
 *
 * Span translation:
 * - Leading margins become `ParagraphStyle(textIndent = TextIndent(first, rest))`.
 * - The verse-number span (0.7× size, baseline raised by 0.3 of ascent) becomes
 *   `SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(0.3f))`.
 *
 * All sizes are emitted in `sp`. The host composable swaps in a density with
 * `fontScale = 1f`, so sp values render at dp dimensions.
 *
 * When the formatted body opens with `@@@^` or `@@@1`..`@@@4`, the verse
 * number is NOT prepended to the AnnotatedString — instead it's returned in
 * [Result.gutterVerseNumber] so the host can position it inside the leading
 * margin reserved by the paragraph's TextIndent.
 */
object VerseRendererCompose {
    private val superscriptDigits = charArrayOf(
        '⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹',
    )

    const val XREF_MARK: Char = '※'

    data class InlineLinkRange(
        val start: Int,
        val end: Int,
        val type: VerseInlineLinkSpan.Type,
        val arif: Int,
    )

    /**
     * A run of base text in [Result.text] that carries a ruby annotation
     * (furigana, pinyin, a Strong's number, an interlinear gloss). The base
     * run stays inline at [start]..[end], so highlight and dictionary offsets
     * are unaffected; the host draws [ruby] above it.
     */
    data class RubyRange(
        val start: Int,
        val end: Int,
        val ruby: String,
    )

    data class Result(
        val text: AnnotatedString,
        /** When non-null, the verse number is rendered separately (in a gutter) instead of inline. */
        val gutterVerseNumber: String?,
        /** Same semantics as [VerseRenderer.render] return: chars consumed before the verse text begins. 0 = gutter mode. */
        val startPosAfterVerseNumber: Int,
        val inlineLinks: List<InlineLinkRange>,
        val rubies: List<RubyRange> = emptyList(),
    )

    private val buf_char_: ThreadLocal<CharArray> = ThreadLocal.withInitial { CharArray(1024) }
    private val buf_tag_: ThreadLocal<StringBuilder> = ThreadLocal.withInitial { StringBuilder(100) }

    /**
     * @param isVerseNumberShown whether the verse number should be visible at all.
     * @param ari encoded verse reference; used as the base for inline-link arif.
     * @param text raw verse text (possibly starting with `@@`).
     * @param verseNumberText label for the verse number (e.g. "12" or "12a").
     * @param highlightInfo full or partial highlight to overlay.
     * @param checked when the verse is selected; suppresses red text + verse-number color override.
     */
    fun render(
        isVerseNumberShown: Boolean,
        ari: Int,
        text: String,
        verseNumberText: String = Ari.toVerse(ari).toString(),
        highlightInfo: Highlights.Info? = null,
        checked: Boolean = false,
    ): Result {
        val text_len = text.length

        if (text_len < 2 || text[0] != '@' || text[1] != '@') {
            return simpleRender(isVerseNumberShown, text, verseNumberText, highlightInfo, checked)
        }

        var text_c = buf_char_.get()!!
        if (text_c.size < text_len) {
            text_c = CharArray(text_len)
            buf_char_.set(text_c)
        }
        text.toCharArray(text_c, 0, 0, text_len)

        val sb = AnnotatedString.Builder()
        val gutterMode = isGutterMode(text_c, text_len)

        val startPosAfterVerseNumber = renderVerseNumber(sb, isVerseNumberShown, verseNumberText, checked, gutterMode)
        val gutterVerseNumber = if (isVerseNumberShown && gutterMode) verseNumberText else null

        val inlineLinks = mutableListOf<InlineLinkRange>()
        val rubies = mutableListOf<RubyRange>()
        processFormattingCodes(text, text_c, text_len, sb, startPosAfterVerseNumber, verseNumberText, checked, ari, inlineLinks, rubies)

        val built = sb.toAnnotatedString()
        val withHighlight = applyHighlight(built, highlightInfo, startPosAfterVerseNumber, checked)

        return Result(
            text = withHighlight,
            gutterVerseNumber = gutterVerseNumber,
            startPosAfterVerseNumber = startPosAfterVerseNumber,
            inlineLinks = inlineLinks,
            rubies = rubies,
        )
    }

    /**
     * Verse number goes to the gutter when the formatted body opens with `@^`
     * or `@1`..`@4` (those paragraph markers take over the layout themselves).
     * `@0` keeps the number inline.
     */
    private fun isGutterMode(text_c: CharArray, text_len: Int): Boolean =
        text_len >= 4 && text_c[2] == '@' && (text_c[3] == '^' || text_c[3] in '1'..'4')

    private fun renderVerseNumber(
        sb: AnnotatedString.Builder,
        isVerseNumberShown: Boolean,
        verseNumberText: String,
        checked: Boolean,
        gutterMode: Boolean,
    ): Int {
        if (gutterMode) return 0
        if (!isVerseNumberShown) return 0

        val color = if (checked) Color.Unspecified else Color(App.services.uiDimensions.applied().verseNumberColor)
        sb.pushStyle(
            SpanStyle(
                color = color,
                fontSize = 0.7.em,
                baselineShift = BaselineShift(0.3f),
            )
        )
        sb.append(verseNumberText)
        sb.pop()
        sb.append("  ")
        return sb.length
    }

    private fun processFormattingCodes(
        text: String,
        text_c: CharArray,
        text_len: Int,
        sb: AnnotatedString.Builder,
        startPosAfterVerseNumber: Int,
        verseNumberText: String,
        checked: Boolean,
        ari: Int,
        inlineLinks: MutableList<InlineLinkRange>,
        rubies: MutableList<RubyRange>,
    ) {
        var paraType = -1
        var startPara = 0
        var startRed = -1
        var startItalic = -1
        var inSpecialTag = false
        // Where the text enclosed by the most recent `@>` ... `@/` begins.
        var tagContentStart = -1
        val tag = buf_tag_.get()!!

        var pos = 2

        while (true) {
            if (pos >= text_len) break

            val nextAt = text.indexOf('@', pos)

            if (nextAt == -1) {
                sb.append(text, pos, text_len)
                break
            }

            if (inSpecialTag) {
                tag.setLength(0)
                tag.append(text, pos, nextAt)
                pos = nextAt
            } else {
                if (nextAt != pos) {
                    sb.append(text, pos, nextAt)
                    pos = nextAt
                }
            }

            pos++
            if (pos >= text_len) break

            when (val marker = text_c[pos]) {
                '0', '1', '2', '3', '4', '^' -> {
                    // `AnnotatedString.Builder.addStyle(ParagraphStyle, start, end)`
                    // creates an implicit paragraph break at the style
                    // boundary, so only emit a ParagraphStyle when we're
                    // crossing a real paragraph boundary (i.e. some verse
                    // text has already been written past the verse-number
                    // prefix). For a transition like `@@@0Text...` where '0'
                    // arrives immediately after the prefix, we just switch
                    // paraType and let it cover prefix + body in the final
                    // flush. Note: no explicit `\n` is appended — Compose
                    // inserts the break automatically; an extra `\n` would
                    // render as a blank line.
                    if (sb.length > startPosAfterVerseNumber) {
                        applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0)
                        startPara = sb.length
                    }
                    paraType = marker.code
                }
                '6' -> startRed = sb.length
                '5' -> if (startRed != -1) {
                    if (!checked) {
                        sb.addStyle(
                            SpanStyle(color = Color(App.services.uiDimensions.applied().fontRedColor)),
                            startRed,
                            sb.length,
                        )
                    }
                    startRed = -1
                }
                '9' -> startItalic = sb.length
                '7' -> if (startItalic != -1) {
                    sb.addStyle(SpanStyle(fontStyle = FontStyle.Italic), startItalic, sb.length)
                    startItalic = -1
                }
                '8' -> sb.append("\n")
                '<' -> inSpecialTag = true
                '>' -> {
                    inSpecialTag = false
                    tagContentStart = sb.length
                }
                '/' -> {
                    processSpecialTag(sb, tag, tagContentStart, ari, inlineLinks, rubies)
                    // A tag is consumed by its closing code; a stray `@/` must not replay it.
                    tag.setLength(0)
                    tagContentStart = -1
                }
            }

            pos++
        }

        applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0)
    }

    /**
     * In a checked verse, the run under the band gets its own text color, picked against the
     * band instead of the selection color used for the rest of the verse.
     */
    private fun applyHighlight(text: AnnotatedString, highlightInfo: Highlights.Info?, startPosAfterVerseNumber: Int, checked: Boolean): AnnotatedString {
        if (highlightInfo == null) return text
        val applied = App.services.uiDimensions.applied()
        val band = Highlights.blendOver(highlightInfo.colorRgb, applied.backgroundColor)
        val textColor = if (checked) {
            val selectedVerseBgColor = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
            Color(TextColorUtil.getForCheckedVerseHighlight(applied.fontColor, selectedVerseBgColor, applied.backgroundColor, band))
        } else {
            Color.Unspecified
        }
        val style = SpanStyle(color = textColor, background = Color(band))

        val builder = AnnotatedString.Builder(text)
        val verseBody = text.subSequence(startPosAfterVerseNumber, text.length)
        if (highlightInfo.shouldRenderAsPartialForVerseText(verseBody.text)) {
            val rawStart = startPosAfterVerseNumber + highlightInfo.partial!!.startOffset
            val rawEnd = startPosAfterVerseNumber + highlightInfo.partial!!.endOffset
            val start = minOf(rawStart, rawEnd)
            val end = maxOf(rawStart, rawEnd)
            if (end > start) {
                builder.addStyle(style, start, end)
            }
        } else {
            builder.addStyle(style, startPosAfterVerseNumber, text.length)
        }
        return builder.toAnnotatedString()
    }

    private fun processSpecialTag(
        sb: AnnotatedString.Builder,
        tag: StringBuilder,
        tagContentStart: Int,
        ari: Int,
        inlineLinks: MutableList<InlineLinkRange>,
        rubies: MutableList<RubyRange>,
    ) {
        val spanStart = sb.length
        if (tag.length < 2) return
        when (tag[0]) {
            'r' -> if (tag[1] == '=' && tag.length > 2 && tagContentStart in 0 until spanStart) {
                rubies += RubyRange(tagContentStart, spanStart, tag.substring(2))
            }
            'f' -> try {
                val field = tag.substring(1).toInt()
                if (field < 1 || field > 255) throw NumberFormatException()
                appendSuperscriptNumber(sb, field)
                inlineLinks += InlineLinkRange(spanStart, sb.length, VerseInlineLinkSpan.Type.footnote, ari shl 8 or field)
            } catch (_: NumberFormatException) {
                reportInvalidSpecialTag("Invalid footnote tag at ari 0x${Integer.toHexString(ari)}: $tag")
            }
            'x' -> try {
                val field = tag.substring(1).toInt()
                if (field < 1 || field > 255) throw NumberFormatException()
                sb.append(XREF_MARK)
                inlineLinks += InlineLinkRange(spanStart, sb.length, VerseInlineLinkSpan.Type.xref, ari shl 8 or field)
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

    private fun appendSuperscriptNumber(sb: AnnotatedString.Builder, field: Int) {
        if (field in 0..9) {
            sb.append(superscriptDigits[field])
        } else if (field >= 10) {
            for (c in field.toString()) {
                sb.append(superscriptDigits[c - '0'])
            }
        }
    }

    private fun applyParaStyle(
        sb: AnnotatedString.Builder,
        paraType: Int,
        startPara: Int,
        verseNumberText: String,
        firstLineWithVerseNumber: Boolean,
    ) {
        val len = sb.length
        if (startPara == len) return

        val indentSpacingExtraUnits = if (verseNumberText.length < 3) 0 else verseNumberText.length - 2
        val applied = App.services.uiDimensions.applied()
        val density = App.context.resources.displayMetrics.density

        fun px(value: Int): TextUnit = (value / density).sp

        when (paraType) {
            -1, '0'.code -> {
                if (firstLineWithVerseNumber) {
                    sb.addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = px(applied.indentParagraphRest))), startPara, len)
                } else {
                    sb.addStyle(ParagraphStyle(textIndent = TextIndent(px(applied.indentParagraphRest), px(applied.indentParagraphRest))), startPara, len)
                }
            }
            '1'.code -> sb.addStyle(ParagraphStyle(textIndent = TextIndent(px(applied.indentSpacing1 + indentSpacingExtraUnits * applied.indentSpacingExtra), px(applied.indentSpacing1 + indentSpacingExtraUnits * applied.indentSpacingExtra))), startPara, len)
            '2'.code -> sb.addStyle(ParagraphStyle(textIndent = TextIndent(px(applied.indentSpacing2 + indentSpacingExtraUnits * applied.indentSpacingExtra), px(applied.indentSpacing2 + indentSpacingExtraUnits * applied.indentSpacingExtra))), startPara, len)
            '3'.code -> sb.addStyle(ParagraphStyle(textIndent = TextIndent(px(applied.indentSpacing3 + indentSpacingExtraUnits * applied.indentSpacingExtra), px(applied.indentSpacing3 + indentSpacingExtraUnits * applied.indentSpacingExtra))), startPara, len)
            '4'.code -> sb.addStyle(ParagraphStyle(textIndent = TextIndent(px(applied.indentSpacing4 + indentSpacingExtraUnits * applied.indentSpacingExtra), px(applied.indentSpacing4 + indentSpacingExtraUnits * applied.indentSpacingExtra))), startPara, len)
            '^'.code -> sb.addStyle(ParagraphStyle(textIndent = TextIndent(px(applied.indentParagraphFirst), px(applied.indentParagraphRest))), startPara, len)
        }
    }

    private fun simpleRender(
        isVerseNumberShown: Boolean,
        text: String,
        verseNumberText: String,
        highlightInfo: Highlights.Info?,
        checked: Boolean,
    ): Result {
        val sb = AnnotatedString.Builder()

        if (isVerseNumberShown) {
            val color = if (checked) Color.Unspecified else Color(App.services.uiDimensions.applied().verseNumberColor)
            sb.pushStyle(
                SpanStyle(
                    color = color,
                    fontSize = 0.7.em,
                    baselineShift = BaselineShift(0.3f),
                )
            )
            sb.append(verseNumberText)
            sb.pop()
            sb.append("  ")
        }
        val startPosAfterVerseNumber = sb.length

        sb.append(text)

        val applied = App.services.uiDimensions.applied()
        val density = App.context.resources.displayMetrics.density
        val rest = (applied.indentParagraphRest / density).sp
        if (isVerseNumberShown) {
            sb.addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = rest)), 0, sb.length)
        } else {
            sb.addStyle(ParagraphStyle(textIndent = TextIndent(rest, rest)), 0, sb.length)
        }

        val built = sb.toAnnotatedString()
        val withHighlight = applyHighlight(built, highlightInfo, startPosAfterVerseNumber, checked)

        return Result(
            text = withHighlight,
            gutterVerseNumber = null,
            startPosAfterVerseNumber = startPosAfterVerseNumber,
            inlineLinks = emptyList(),
        )
    }
}
