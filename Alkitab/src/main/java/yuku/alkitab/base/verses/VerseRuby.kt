package yuku.alkitab.base.verses

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.widget.VerseRendererCompose

/** Ruby text is drawn at this fraction of the base font size. */
internal const val RUBY_FONT_SIZE_RATIO = 0.5f

/** Gap between the ruby's bottom and the base glyph's top, as a fraction of the ruby font size. */
internal const val RUBY_GAP_RATIO = 0.1f

/** Clearance kept on each side of a ruby so neighbouring rubies never touch, as a fraction of the ruby font size. */
internal const val RUBY_SIDE_GAP_RATIO = 0.2f

/**
 * How far a ruby may hang over a neighbouring character that carries no ruby,
 * as a fraction of the ruby font size. One ruby em on each side is the
 * conventional allowance for furigana over adjacent kana, so a three-kana
 * reading over one kanji does not push the kanji away from its okurigana.
 */
internal const val RUBY_OVERHANG_RATIO = 1f

/**
 * Cap on the letter spacing a reading may add to its base, in base font
 * sizes per character. A reading far wider than that is data garbage and
 * is ellipsised instead of spreading the base over several lines.
 */
internal const val RUBY_MAX_LETTER_SPACING_EM = 2f

/**
 * The part of [ruby] that belongs to the base characters `segStart until segEnd`
 * when the base run `start until end` is broken across lines. The reading is
 * split by character count, so a two-kanji word broken in the middle keeps
 * half of its reading on each line.
 */
internal fun rubySliceFor(ruby: String, start: Int, end: Int, segStart: Int, segEnd: Int): String {
    val baseLen = end - start
    if (baseLen <= 0 || segEnd <= segStart) return ""
    if (segStart <= start && segEnd >= end) return ruby
    val from = surrogateSafe(ruby, ((ruby.length * (segStart - start) + baseLen / 2) / baseLen).coerceIn(0, ruby.length))
    val to = surrogateSafe(ruby, ((ruby.length * (segEnd - start) + baseLen / 2) / baseLen).coerceIn(from, ruby.length))
    return ruby.substring(from, to)
}

/**
 * Whether a reading over [base] may be split across lines when the base
 * wraps. Kana or ideographs read one character at a time, so a furigana or
 * pinyin reading follows its characters; a number, a word or a gloss over a
 * Latin, Hebrew or Greek base stays whole over the widest segment.
 */
internal fun rubySplitsAcrossLines(base: CharSequence): Boolean = base.any { ch ->
    val block = Character.UnicodeScript.of(ch.code)
    block == Character.UnicodeScript.HAN || block == Character.UnicodeScript.HIRAGANA ||
        block == Character.UnicodeScript.KATAKANA || block == Character.UnicodeScript.HANGUL
}

/** Moves [index] back by one when it would split a surrogate pair. */
private fun surrogateSafe(s: String, index: Int): Int =
    if (index in 1 until s.length && Character.isLowSurrogate(s[index]) && Character.isHighSurrogate(s[index - 1])) index - 1 else index

/**
 * Extra letter spacing, in px per character, that widens a [baseLength]-character
 * base run measuring [baseWidthPx] so its ruby of [rubyWidthPx] fits. Each side
 * contributes [leftSlackPx] / [rightSlackPx]: positive when the ruby may hang
 * over a neighbour without ruby, negative when a gap must be kept from a
 * neighbouring ruby. Zero when the ruby already fits.
 */
internal fun rubyLetterSpacingPx(baseWidthPx: Float, rubyWidthPx: Float, baseLength: Int, leftSlackPx: Float = 0f, rightSlackPx: Float = 0f): Float {
    val needed = rubyWidthPx - leftSlackPx - rightSlackPx
    if (baseLength <= 0 || needed <= baseWidthPx) return 0f
    return (needed - baseWidthPx) / baseLength
}

/**
 * Room on one side of the ruby base `start until end`: [overhangPx] when the
 * character next to it exists, is not a line break and carries no ruby of its
 * own; `-sideGapPx` when that character, or the first non-space character
 * beyond it, belongs to another ruby; zero at the start or end of the text or
 * at a line break. Looking past spaces keeps two long glosses over
 * neighbouring words from meeting over the space between them.
 */
internal fun rubySideSlackPx(
    text: CharSequence,
    rubies: List<VerseRendererCompose.RubyRange>,
    neighbourOffset: Int,
    direction: Int,
    overhangPx: Float,
    sideGapPx: Float,
): Float {
    if (neighbourOffset < 0 || neighbourOffset >= text.length) return 0f
    if (text[neighbourOffset] == '\n') return 0f
    fun underRuby(offset: Int) = rubies.any { offset >= it.start && offset < it.end }
    if (underRuby(neighbourOffset)) return -sideGapPx
    var i = neighbourOffset
    while (i in text.indices && text[i] == ' ') i += direction
    if (i in text.indices && text[i] != '\n' && underRuby(i)) return -sideGapPx
    return overhangPx
}

/**
 * The color the base character at [offset] is painted with: the innermost span
 * that specifies one, else [default]. Ruby follows the character under its
 * centre so a red-letter or a highlighted, selected run keeps its ruby
 * readable.
 */
internal fun rubyColorAt(text: AnnotatedString, offset: Int, default: Color): Color {
    var color = default
    for (range in text.spanStyles) {
        if (offset >= range.start && offset < range.end && range.item.color.isSpecified) {
            color = range.item.color
        }
    }
    return color
}

/**
 * Returns [text] with letter spacing added to every ruby base that is narrower
 * than its ruby, so the ruby never overlaps its neighbours. Offsets are
 * unchanged: the base text stays inline.
 */
internal fun widenRubyBases(
    text: AnnotatedString,
    rubies: List<VerseRendererCompose.RubyRange>,
    textStyle: TextStyle,
    rubyStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Float,
): AnnotatedString {
    if (rubies.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        for (r in rubies) {
            val start = r.start.coerceAtLeast(0)
            val end = r.end.coerceAtMost(text.length)
            if (end <= start || r.ruby.isEmpty()) continue
            val baseWidth = textMeasurer.measure(text.subSequence(start, end), textStyle, softWrap = false, maxLines = 1).size.width.toFloat()
            val rubyWidth = textMeasurer.measure(AnnotatedString(r.ruby), rubyStyle, softWrap = false, maxLines = 1).size.width.toFloat()
            val rubyFontPx = rubyStyle.fontSize.value * density
            val sideGapPx = rubyFontPx * RUBY_SIDE_GAP_RATIO
            val overhangPx = rubyFontPx * RUBY_OVERHANG_RATIO
            val leftSlack = rubySideSlackPx(text, rubies, start - 1, -1, overhangPx, sideGapPx)
            val rightSlack = rubySideSlackPx(text, rubies, end, 1, overhangPx, sideGapPx)
            val maxSpacingPx = textStyle.fontSize.value * density * RUBY_MAX_LETTER_SPACING_EM
            val spacingPx = rubyLetterSpacingPx(baseWidth, rubyWidth, end - start, leftSlack, rightSlack).coerceAtMost(maxSpacingPx)
            if (spacingPx > 0f) {
                addStyle(SpanStyle(letterSpacing = (spacingPx / density).sp), start, end)
            }
        }
    }
}

/**
 * Paints every ruby centered above its base run, in the band that
 * [computeLineMetrics] reserves above each line. A base run broken across
 * lines gets a proportional slice of its ruby on each line.
 *
 * [baseAscentPx] is the distance from the baseline up to the top of the base
 * glyphs; the ruby's bottom sits [gapPx] above that.
 */
internal fun Modifier.rubyOverlay(
    rubies: List<VerseRendererCompose.RubyRange>,
    layoutResultProvider: () -> TextLayoutResult?,
    textMeasurer: TextMeasurer,
    rubyStyle: TextStyle,
    textColor: Color,
    baseAscentPx: Float,
    gapPx: Float,
): Modifier = if (rubies.isEmpty()) this else drawWithContent {
    drawContent()
    val layout = layoutResultProvider() ?: return@drawWithContent
    val text = layout.layoutInput.text
    val textLen = text.length
    val maxWidthPx = size.width.toInt().coerceAtLeast(0)
    clipRect {
        for (r in rubies) {
            val start = r.start.coerceAtLeast(0)
            val end = r.end.coerceAtMost(textLen)
            if (end <= start || r.ruby.isEmpty()) continue
            val firstLine = layout.getLineForOffset(start)
            val lastLine = layout.getLineForOffset(end - 1)
            for (line in firstLine..lastLine) {
                val segStart = maxOf(start, layout.getLineStart(line))
                val segEnd = minOf(end, layout.getLineEnd(line, visibleEnd = true))
                if (segEnd <= segStart) continue
                val slice = rubySliceFor(r.ruby, start, end, segStart, segEnd)
                if (slice.isEmpty()) continue
                val first = layout.getBoundingBox(segStart)
                val last = layout.getBoundingBox(segEnd - 1)
                val left = minOf(first.left, last.left)
                val right = maxOf(first.right, last.right)
                val measured = textMeasurer.measure(
                    AnnotatedString(slice),
                    rubyStyle,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
                val w = measured.size.width.toFloat()
                val x = ((left + right - w) / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                val glyphTop = layout.getLineBaseline(line) - baseAscentPx
                val y = glyphTop - gapPx - measured.size.height
                val color = rubyColorAt(text, (segStart + segEnd - 1) / 2, textColor)
                drawText(measured, color = color, topLeft = Offset(x, y))
            }
        }
    }
}
