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
import yuku.alkitab.base.App
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.util.Ari

/**
 * Compose-native counterpart to [VerseRenderer]. Walks the same formatting-code
 * grammar (`@@`, `@0`..`@4`, `@^`, `@5`/`@6`, `@7`/`@9`, `@8`, `@<..@>`, `@/`)
 * and produces an [AnnotatedString] + the byte offsets of every inline link
 * (footnote `@<f..@>` / xref `@<x..@>`). The structure of this file
 * intentionally mirrors `VerseRenderer.kt` line-for-line so a side-by-side diff
 * stays readable.
 *
 * Pixel parity notes (vs. the legacy TextView path):
 * - All sizes are emitted in `sp` because [VerseItemComposeView] swaps in a
 *   [androidx.compose.ui.unit.Density] with `fontScale = 1f`. With that override
 *   `sp == dp` visually, matching `TypedValue.COMPLEX_UNIT_DIP`.
 * - `LeadingMarginSpan.Standard(first, rest)` is translated to
 *   `ParagraphStyle(textIndent = TextIndent(first, rest))`.
 * - `VerseNumberSpan` (0.7x size + baseline raised by 0.3 of ascent) maps to
 *   `SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(0.3f))`.
 *
 * The verse-number gutter prefix (when the format starts with `@@@^` or
 * `@@@1`..`@@@4`) is NOT prepended to the AnnotatedString — instead it is
 * returned in [Result.gutterVerseNumber] so the host composable can position it
 * the same way the legacy `lVerseNumber` TextView sits inside the FrameLayout.
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

    data class Result(
        val text: AnnotatedString,
        /** When non-null, the verse number is rendered separately (in a gutter) instead of inline. */
        val gutterVerseNumber: String?,
        /** Same semantics as [VerseRenderer.render] return: chars consumed before the verse text begins. 0 = gutter mode. */
        val startPosAfterVerseNumber: Int,
        val inlineLinks: List<InlineLinkRange>,
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
        processFormattingCodes(text, text_c, text_len, sb, startPosAfterVerseNumber, verseNumberText, checked, ari, inlineLinks)

        val built = sb.toAnnotatedString()
        val withHighlight = applyHighlight(built, highlightInfo, startPosAfterVerseNumber)

        return Result(
            text = withHighlight,
            gutterVerseNumber = gutterVerseNumber,
            startPosAfterVerseNumber = startPosAfterVerseNumber,
            inlineLinks = inlineLinks,
        )
    }

    /**
     * Verse number goes to the gutter when the formatted body opens with `@^` or `@1`..`@4`
     * (those paragraph markers take over layout themselves). `@0` keeps the number inline.
     * Mirrors [VerseRenderer.renderVerseNumber].
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

        val spanStart = sb.length
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
        return sb.length.also { _ -> /* mirror VerseRenderer's `return sb.length` */ require(spanStart >= 0) }
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
    ) {
        var paraType = -1
        var startPara = 0
        var startRed = -1
        var startItalic = -1
        var inSpecialTag = false
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
                    // Compose's ParagraphStyle differs from Android's
                    // LeadingMarginSpan in one critical way: applying a
                    // ParagraphStyle to a sub-range introduces an *implicit*
                    // paragraph break at its boundary. LeadingMarginSpan does
                    // not. To preserve legacy semantics we only emit a
                    // ParagraphStyle when we're crossing a real paragraph
                    // boundary (a `\n` is appended). When the transition
                    // happens before any verse text has been written —
                    // e.g. `@@@0Text...` where '0' is hit immediately after
                    // the verse-number prefix — we just adopt the new
                    // paraType and let it cover the whole paragraph
                    // (prefix + body) at the final flush.
                    if (sb.length > startPosAfterVerseNumber) {
                        // Close out the previous paragraph. We deliberately
                        // do NOT append a '\n' here: Compose's
                        // AnnotatedString.Builder.addStyle(ParagraphStyle)
                        // creates an *implicit* paragraph break at the style
                        // boundary, so an explicit '\n' would inject a blank
                        // line. (Legacy code appends '\n' because
                        // android.text.Spanned needs explicit separators —
                        // Compose does not.)
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
                '>' -> inSpecialTag = false
                '/' -> processSpecialTag(sb, tag, ari, inlineLinks)
            }

            pos++
        }

        applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0)
    }

    private fun applyHighlight(text: AnnotatedString, highlightInfo: Highlights.Info?, startPosAfterVerseNumber: Int): AnnotatedString {
        if (highlightInfo == null) return text
        val background = Color(Highlights.alphaMix(highlightInfo.colorRgb))
        val builder = AnnotatedString.Builder(text)
        val verseBody = text.subSequence(startPosAfterVerseNumber, text.length)
        if (highlightInfo.shouldRenderAsPartialForVerseText(verseBody.text)) {
            val rawStart = startPosAfterVerseNumber + highlightInfo.partial!!.startOffset
            val rawEnd = startPosAfterVerseNumber + highlightInfo.partial!!.endOffset
            val start = minOf(rawStart, rawEnd)
            val end = maxOf(rawStart, rawEnd)
            if (end > start) {
                builder.addStyle(SpanStyle(background = background), start, end)
            }
        } else {
            builder.addStyle(SpanStyle(background = background), startPosAfterVerseNumber, text.length)
        }
        return builder.toAnnotatedString()
    }

    private fun processSpecialTag(
        sb: AnnotatedString.Builder,
        tag: StringBuilder,
        ari: Int,
        inlineLinks: MutableList<InlineLinkRange>,
    ) {
        val spanStart = sb.length
        if (tag.length < 2) return
        when (tag[0]) {
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
        val withHighlight = applyHighlight(built, highlightInfo, startPosAfterVerseNumber)

        return Result(
            text = withHighlight,
            gutterVerseNumber = null,
            startPosAfterVerseNumber = startPosAfterVerseNumber,
            inlineLinks = emptyList(),
        )
    }
}
