package yuku.alkitab.base.verses

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.widget.VerseRendererCompose

/** Ruby text is drawn at this fraction of the base font size. */
internal const val RUBY_FONT_SIZE_RATIO = 0.5f

/** Gap between the ruby's bottom and the base glyph's top, as a fraction of the ruby font size. */
internal const val RUBY_GAP_RATIO = 0.1f

/** Clearance kept on each side of a ruby so neighbouring rubies never touch, as a fraction of the ruby font size. */
internal const val RUBY_SIDE_GAP_RATIO = 0.2f

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
    val from = ((ruby.length * (segStart - start) + baseLen / 2) / baseLen).coerceIn(0, ruby.length)
    val to = ((ruby.length * (segEnd - start) + baseLen / 2) / baseLen).coerceIn(from, ruby.length)
    return ruby.substring(from, to)
}

/**
 * Extra letter spacing, in px per character, that widens a [baseLength]-character
 * base run measuring [baseWidthPx] so it spans at least [rubyWidthPx] plus
 * [sideGapPx] on each side. Zero when the ruby already fits over its base.
 */
internal fun rubyLetterSpacingPx(baseWidthPx: Float, rubyWidthPx: Float, baseLength: Int, sideGapPx: Float = 0f): Float {
    val needed = rubyWidthPx + 2 * sideGapPx
    if (baseLength <= 0 || needed <= baseWidthPx) return 0f
    return (needed - baseWidthPx) / baseLength
}

/**
 * The color the base run at [offset] is painted with: the innermost span that
 * specifies one, else [default]. Ruby follows it so a red-letter or a
 * highlighted, selected run keeps its ruby readable.
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
            val sideGapPx = rubyStyle.fontSize.value * density * RUBY_SIDE_GAP_RATIO
            val spacingPx = rubyLetterSpacingPx(baseWidth, rubyWidth, end - start, sideGapPx)
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
    for (r in rubies) {
        val start = r.start.coerceAtLeast(0)
        val end = r.end.coerceAtMost(textLen)
        if (end <= start || r.ruby.isEmpty()) continue
        val color = rubyColorAt(text, start, textColor)
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
            val measured = textMeasurer.measure(AnnotatedString(slice), rubyStyle, softWrap = false, maxLines = 1)
            val w = measured.size.width.toFloat()
            val x = ((left + right - w) / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val glyphTop = layout.getLineBaseline(line) - baseAscentPx
            val y = glyphTop - gapPx - measured.size.height
            drawText(measured, color = color, topLeft = Offset(x, y))
        }
    }
}
