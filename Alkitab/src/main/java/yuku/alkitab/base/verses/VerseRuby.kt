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

/**
 * Ruby text is drawn at this fraction of the base font size.
 *
 * At the default 17dp verse text a reading is drawn at 8.5dp.
 */
internal const val RUBY_FONT_SIZE_RATIO = 0.5f

/**
 * Gap between the ruby's bottom and the base glyph's top, as a fraction of the
 * ruby font size.
 *
 * With an 8.5dp reading the gap is 0.85dp.
 */
internal const val RUBY_GAP_RATIO = 0.1f

/**
 * Clearance kept on each side of a ruby so neighbouring rubies never touch, as
 * a fraction of the ruby font size.
 *
 * With an 8.5dp reading, two readings over adjacent characters stay 1.7dp
 * apart. This is what keeps `Qǐ` and `chū` legible over 起初 in a pinyin verse,
 * where every character carries its own reading.
 */
internal const val RUBY_SIDE_GAP_RATIO = 0.2f

/**
 * How far a ruby may hang over a neighbouring character that carries no ruby,
 * as a fraction of the ruby font size. One ruby em on each side is the
 * conventional allowance for furigana over adjacent kana, so a three-kana
 * reading over one kanji does not push the kanji away from its okurigana.
 *
 * In 神は, the reading かみ over 神 may spill 8.5dp over は rather than force a
 * gap between the two characters.
 */
internal const val RUBY_OVERHANG_RATIO = 1f

/**
 * Cap on the letter spacing a reading may add to its base, in base font
 * sizes per character. A reading far wider than that is data garbage and
 * is ellipsised instead of spreading the base over several lines.
 *
 * At 17dp verse text no character is spread by more than 34dp, so a thousand
 * character reading over one kanji widens that kanji to 51dp and is then cut
 * with an ellipsis, rather than pushing the verse across many lines.
 */
internal const val RUBY_MAX_LETTER_SPACING_EM = 2f

/**
 * The part of [ruby] that belongs to the base characters `segStart until segEnd`
 * when the base run `start until end` is broken across lines. The reading is
 * split by character count, so a two-kanji word broken in the middle keeps
 * half of its reading on each line.
 *
 * For そうぞう over 創造 at offsets 5 until 7, a line break between the two
 * kanji gives `rubySliceFor(そうぞう, 5, 7, 5, 6)` = そう on the first line and
 * `rubySliceFor(そうぞう, 5, 7, 6, 7)` = ぞう on the second.
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
 *
 * True for 創造 and for 天地, so そうぞう may be cut in two. False for `God`, so
 * a Strong's number such as `G2316` over it is never cut into `G23` and `16`.
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
 *
 * A 50px reading over a 40px two-character base needs 10px more, so each
 * character is spread by 5px. Given 8px of overhang on the left the shortfall
 * is only 2px, so each character is spread by 1px; given 8px on both sides the
 * reading fits and nothing is spread.
 */
internal fun rubyLetterSpacingPx(baseWidthPx: Float, rubyWidthPx: Float, baseLength: Int, leftSlackPx: Float, rightSlackPx: Float): Float {
    val needed = rubyWidthPx - leftSlackPx - rightSlackPx
    if (baseLength <= 0 || needed <= baseWidthPx) return 0f
    return (needed - baseWidthPx) / baseLength
}

/**
 * Room on one side of the ruby base `start until end`, in px. The whitespace
 * between two words is real room, so it counts: a run of spaces contributes
 * its own width, halved when the word beyond it carries a reading of its own
 * so that both readings can claim their half without meeting. A neighbour
 * carrying no reading contributes [overhangPx] on top, and a neighbour
 * character directly abutting the base and carrying a reading contributes
 * `-sideGapPx`. The start or end of the text and a line break contribute zero.
 *
 * For 神 in 神は, the neighbour は carries no reading, so かみ may overhang it by
 * [overhangPx]. In 起初 the readings abut with no space between them, so each
 * side yields `-sideGapPx`. Between two annotated words separated by one space
 * each side yields half a space width minus [sideGapPx], which is what lets a
 * Strong's number sit over a word as short as `air` without the word itself
 * being pulled apart.
 */
internal fun rubySideSlackPx(
    text: CharSequence,
    rubies: List<VerseRendererCompose.RubyRange>,
    neighbourOffset: Int,
    direction: Int,
    overhangPx: Float,
    sideGapPx: Float,
    spaceWidthPx: Float,
): Float {
    if (neighbourOffset < 0 || neighbourOffset >= text.length) return 0f
    if (text[neighbourOffset] == '\n') return 0f
    fun underRuby(offset: Int) = rubies.any { offset >= it.start && offset < it.end }
    if (underRuby(neighbourOffset)) return -sideGapPx
    var i = neighbourOffset
    while (i in text.indices && text[i] == ' ') i += direction
    val gapPx = (i - neighbourOffset) * direction * spaceWidthPx
    if (i in text.indices && text[i] != '\n' && underRuby(i)) return gapPx / 2f - sideGapPx
    return gapPx + overhangPx
}

/**
 * Left edge of a reading of width [rubyWidthPx] over a base run spanning
 * [leftPx] until [rightPx]. The reading is centred when it fits; when it is
 * wider it slides towards the side that has room ([leftSlackPx] and
 * [rightSlackPx] as returned by [rubySideSlackPx]), so an overhang lands on a
 * ruby-free neighbour and never on the neighbouring reading.
 *
 * Over a base spanning 10 until 30, a 16px reading is centred at 12. A 26px
 * reading with 8px of room on the left and a 2px gap owed on the right is
 * placed at 2 rather than centred at 7, so the whole 6px overhang falls on the
 * ruby-free left neighbour and the right edge stops short of the next reading.
 */
internal fun rubyLeftPx(leftPx: Float, rightPx: Float, rubyWidthPx: Float, leftSlackPx: Float, rightSlackPx: Float): Float {
    val centred = (leftPx + rightPx - rubyWidthPx) / 2f
    val minX = leftPx - leftSlackPx
    val maxX = rightPx + rightSlackPx - rubyWidthPx
    return if (maxX < minX) centred else centred.coerceIn(minX, maxX)
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
 * Returns [text] widened so that no ruby overlaps its neighbours. Offsets are
 * unchanged: the base text stays inline.
 *
 * A reading that overflows its base is first given the room already beside it,
 * as [rubySideSlackPx] reports it. Whatever is still missing is added to the
 * space characters flanking the base, half to each side, so the word keeps its
 * own shape and only the gaps around it grow. Padding both sides equally is
 * what lets the overlay centre the reading over the base and still clear the
 * neighbouring readings. Two words sharing one space both add to it.
 *
 * `penuhilah air di lautan` with a Strong's number over every word widens the
 * spaces around `air`, rather than spreading it into `a i r`. A base with no
 * space beside the side that needs room, such as a pinyin reading over a single
 * Han character in 起初, falls back to letter spacing inside the base.
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
    val spaceWidthPx = textMeasurer.measure(AnnotatedString(" "), textStyle, softWrap = false, maxLines = 1).size.width.toFloat()
    val padPxByOffset = mutableMapOf<Int, Float>()
    val spreadPxByRange = mutableListOf<Triple<Int, Int, Float>>()
    for (r in rubies) {
        val start = r.start.coerceAtLeast(0)
        val end = r.end.coerceAtMost(text.length)
        if (end <= start || r.ruby.isEmpty()) continue
        val baseWidth = textMeasurer.measure(text.subSequence(start, end), textStyle, softWrap = false, maxLines = 1).size.width.toFloat()
        val rubyWidth = textMeasurer.measure(AnnotatedString(r.ruby), rubyStyle, softWrap = false, maxLines = 1).size.width.toFloat()
        val halfOverflowPx = (rubyWidth - baseWidth) / 2f
        if (halfOverflowPx <= 0f) continue
        val rubyFontPx = rubyStyle.fontSize.value * density
        val sideGapPx = rubyFontPx * RUBY_SIDE_GAP_RATIO
        val overhangPx = rubyFontPx * RUBY_OVERHANG_RATIO
        val leftSlack = rubySideSlackPx(text, rubies, start - 1, -1, overhangPx, sideGapPx, spaceWidthPx)
        val rightSlack = rubySideSlackPx(text, rubies, end, 1, overhangPx, sideGapPx, spaceWidthPx)
        val maxSpacingPx = textStyle.fontSize.value * density * RUBY_MAX_LETTER_SPACING_EM
        val maxPadPx = maxSpacingPx * (end - start) / 2f
        val leftPad = (halfOverflowPx - leftSlack).coerceIn(0f, maxPadPx)
        val rightPad = (halfOverflowPx - rightSlack).coerceIn(0f, maxPadPx)
        if (leftPad <= 0f && rightPad <= 0f) continue
        val leftSpace = if (start > 0 && text[start - 1] == ' ') start - 1 else -1
        val rightSpace = if (end < text.length && text[end] == ' ') end else -1
        if ((leftPad > 0f && leftSpace < 0) || (rightPad > 0f && rightSpace < 0)) {
            val spreadPx = rubyLetterSpacingPx(baseWidth, rubyWidth, end - start, leftSlack, rightSlack).coerceAtMost(maxSpacingPx)
            if (spreadPx > 0f) spreadPxByRange += Triple(start, end, spreadPx)
            continue
        }
        if (leftPad > 0f) padPxByOffset[leftSpace] = (padPxByOffset[leftSpace] ?: 0f) + leftPad
        if (rightPad > 0f) padPxByOffset[rightSpace] = (padPxByOffset[rightSpace] ?: 0f) + rightPad
    }
    if (padPxByOffset.isEmpty() && spreadPxByRange.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        for ((start, end, spreadPx) in spreadPxByRange) {
            addStyle(SpanStyle(letterSpacing = (spreadPx / density).sp), start, end)
        }
        for ((offset, padPx) in padPxByOffset) {
            addStyle(SpanStyle(letterSpacing = (padPx / density).sp), offset, offset + 1)
        }
    }
}

/**
 * Paints every ruby over its base run, positioned by [rubyLeftPx], in the band
 * that [computeLineMetrics] reserves above each line. A base run broken across
 * lines gets a proportional slice of its ruby on each line.
 *
 * [baseAscentPx] is the distance from the baseline up to the top of the base
 * glyphs; the ruby's bottom sits [gapPx] above that. [spaceWidthPx] is the
 * unpadded advance of a space in the base style, which [rubySideSlackPx] needs
 * to value the whitespace beside a base run.
 */
internal fun Modifier.rubyOverlay(
    rubies: List<VerseRendererCompose.RubyRange>,
    layoutResultProvider: () -> TextLayoutResult?,
    textMeasurer: TextMeasurer,
    rubyStyle: TextStyle,
    textColor: Color,
    baseAscentPx: Float,
    gapPx: Float,
    spaceWidthPx: Float,
): Modifier = if (rubies.isEmpty()) this else drawWithContent {
    drawContent()
    val layout = layoutResultProvider() ?: return@drawWithContent
    val text = layout.layoutInput.text
    val textLen = text.length
    val maxWidthPx = size.width.toInt().coerceAtLeast(0)
    val rubyFontPx = rubyStyle.fontSize.value * density
    val sideGapPx = rubyFontPx * RUBY_SIDE_GAP_RATIO
    val overhangPx = rubyFontPx * RUBY_OVERHANG_RATIO
    clipRect {
        for (r in rubies) {
            val start = r.start.coerceAtLeast(0)
            val end = r.end.coerceAtMost(textLen)
            if (end <= start || r.ruby.isEmpty()) continue
            val firstLine = layout.getLineForOffset(start)
            val lastLine = layout.getLineForOffset(end - 1)
            val splits = firstLine == lastLine || rubySplitsAcrossLines(text.subSequence(start, end))
            val widestLine = if (splits) firstLine else (firstLine..lastLine).maxByOrNull { line ->
                val segStart = maxOf(start, layout.getLineStart(line))
                val segEnd = minOf(end, layout.getLineEnd(line, visibleEnd = true))
                if (segEnd <= segStart) -1f else layout.getBoundingBox(segEnd - 1).right - layout.getBoundingBox(segStart).left
            } ?: firstLine
            for (line in firstLine..lastLine) {
                if (!splits && line != widestLine) continue
                val segStart = maxOf(start, layout.getLineStart(line))
                val segEnd = minOf(end, layout.getLineEnd(line, visibleEnd = true))
                if (segEnd <= segStart) continue
                val slice = if (splits) rubySliceFor(r.ruby, start, end, segStart, segEnd) else r.ruby
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
                val leftSlack = rubySideSlackPx(text, rubies, start - 1, -1, overhangPx, sideGapPx, spaceWidthPx)
                val rightSlack = rubySideSlackPx(text, rubies, end, 1, overhangPx, sideGapPx, spaceWidthPx)
                val x = rubyLeftPx(left, right, w, leftSlack, rightSlack).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                val glyphTop = layout.getLineBaseline(line) - baseAscentPx
                val y = glyphTop - gapPx - measured.size.height
                val color = rubyColorAt(text, (segStart + segEnd - 1) / 2, textColor)
                drawText(measured, color = color, topLeft = Offset(x, y))
            }
        }
    }
}
