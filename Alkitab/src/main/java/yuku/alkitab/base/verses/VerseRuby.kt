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
import kotlin.math.ceil
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
 * Clearance a reading keeps from the reading beside it when it borrows room
 * from that neighbour's column, as a fraction of the ruby font size.
 *
 * A reading that reaches over a neighbouring word needs more than the
 * [RUBY_SIDE_GAP_RATIO] hairline to stay legible as a separate reading: with
 * an 8.5dp reading, four Strong's numbers spilling over `memperanakkan` stop
 * 8.5dp short of the number that word carries. Two readings that each stay
 * within their own column borrow nothing and keep the hairline.
 */
internal const val RUBY_BORROW_GAP_RATIO = 1f

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
 * Cap on the padding a reading may add to one space character beside its base,
 * in base font sizes. What a reading needs there depends on how much wider it
 * is than its base, not on how many characters the base happens to have, so a
 * three digit verse figure under four Strong's numbers may claim as much room
 * as a long word under the same four.
 *
 * At 17dp verse text a space grows by at most 102dp, enough to seat six
 * Strong's numbers over a three digit base. A reading wider than that stops
 * widening the line and is placed by [rubyLineLayoutPx] instead.
 */
internal const val RUBY_MAX_SPACE_PAD_EM = 6f

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
 * Room on one side of the ruby base `start until end`, in px, counting the
 * room a neighbouring reading leaves unused.
 *
 * Each annotated word is a column: its reading above, its base below. A column
 * whose reading is narrower than its base has room to spare on both sides of
 * that reading, and a column whose reading is wider has already spent room its
 * base does not own. [spareHalfPx] holds that figure per entry of [rubies],
 * half the base width less half the reading width, so it is positive for a
 * column with room to lend and negative for one already borrowing. The
 * whitespace between two words is real room and counts too, halved when the
 * word beyond it carries a reading of its own so that both may claim a share.
 * A neighbour carrying no reading contributes [overhangPx] on top. The start
 * or end of the text and a line break contribute zero.
 *
 * What a reading borrows from a neighbouring column stops [borrowGapPx] short
 * of that column's reading, so the two never read as one. A reading that finds
 * less room than that keeps [sideGapPx] instead, which is all that separates
 * two readings pressed against each other with nothing to lend either way.
 *
 * For 神 in 神は, the neighbour は carries no reading, so かみ may overhang it by
 * [overhangPx]. In 起初 the readings abut and each is wider than its own
 * character, so each side yields `-sideGapPx`. Over `815` in
 * `memperanakkan 815 tahun`, where H3205 leaves 30px spare over its long base,
 * the four Strong's numbers take half the space plus those 30px less
 * [borrowGapPx], rather than pushing the words apart to make room they could
 * have borrowed.
 */
internal fun rubySideSlackPx(
    text: CharSequence,
    rubies: List<VerseRendererCompose.RubyRange>,
    spareHalfPx: FloatArray,
    neighbourOffset: Int,
    direction: Int,
    overhangPx: Float,
    sideGapPx: Float,
    borrowGapPx: Float,
    spaceWidthPx: Float,
): Float {
    if (neighbourOffset < 0 || neighbourOffset >= text.length) return 0f
    if (text[neighbourOffset] == '\n') return 0f
    fun rubyAt(offset: Int) = rubies.indexOfFirst { offset >= it.start && offset < it.end }
    fun keepingClear(roomPx: Float) = roomPx - borrowGapPx.coerceAtMost(maxOf(sideGapPx, roomPx))
    val abutting = rubyAt(neighbourOffset)
    if (abutting >= 0) return keepingClear(spareHalfPx[abutting])
    var i = neighbourOffset
    while (i in text.indices && text[i] == ' ') i += direction
    val gapPx = (i - neighbourOffset) * direction * spaceWidthPx
    if (i in text.indices && text[i] != '\n') {
        val beyond = rubyAt(i)
        if (beyond >= 0) return keepingClear(gapPx / 2f + spareHalfPx[beyond])
    }
    return gapPx + overhangPx
}

/**
 * Left edge a reading of [readingWidthPx] takes when centred over a base
 * spanning [baseLeftPx] until [baseRightPx]. A neighbouring reading is assumed
 * to sit here, so this is what bounds the room beside it.
 *
 * A 40px reading over a base spanning 90 until 145 starts at 97.5, which is
 * inside the base: a reading narrower than its base leaves room on both sides
 * that the reading next to it may use.
 */
internal fun rubyCentredLeftPx(baseLeftPx: Float, baseRightPx: Float, readingWidthPx: Float): Float =
    (baseLeftPx + baseRightPx - readingWidthPx) / 2f

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
 * as [rubySideSlackPx] reports it: the whitespace around the word, and what a
 * neighbouring column whose reading is narrower than its base leaves unused.
 * Only what borrowing cannot cover is added to the space characters flanking
 * the base, half to each side, so the word keeps its own shape and only the
 * gaps around it grow. Padding both sides equally is what lets the overlay
 * centre the reading over the base and still clear the neighbouring readings.
 * Two words sharing one space both add to it.
 *
 * Over `815` in `memperanakkan 815 tahun`, the four Strong's numbers reach
 * across the room H3205 leaves unused over its long base, and the words stay
 * where they are. `penuhilah air di lautan` with a Strong's number over every
 * word has nothing to borrow, so it widens the spaces around `air` instead,
 * rather than spreading it into `a i r`. A base with no space beside the side
 * that needs room, such as a pinyin reading over a single Han character in
 * 起初, falls back to letter spacing inside the base.
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
    val rubyFontPx = rubyStyle.fontSize.value * density
    val sideGapPx = rubyFontPx * RUBY_SIDE_GAP_RATIO
    val borrowGapPx = rubyFontPx * RUBY_BORROW_GAP_RATIO
    val overhangPx = rubyFontPx * RUBY_OVERHANG_RATIO
    val maxSpacingPx = textStyle.fontSize.value * density * RUBY_MAX_LETTER_SPACING_EM
    val maxPadPx = textStyle.fontSize.value * density * RUBY_MAX_SPACE_PAD_EM

    val baseWidthPx = FloatArray(rubies.size)
    val rubyWidthPx = FloatArray(rubies.size)
    for ((i, r) in rubies.withIndex()) {
        val start = r.start.coerceAtLeast(0)
        val end = r.end.coerceAtMost(text.length)
        if (end <= start || r.ruby.isEmpty()) continue
        baseWidthPx[i] = textMeasurer.measure(text.subSequence(start, end), textStyle, softWrap = false, maxLines = 1).size.width.toFloat()
        rubyWidthPx[i] = textMeasurer.measure(AnnotatedString(r.ruby), rubyStyle, softWrap = false, maxLines = 1).size.width.toFloat()
    }
    val spareHalfPx = FloatArray(rubies.size) { (baseWidthPx[it] - rubyWidthPx[it]) / 2f }

    val padPxByOffset = mutableMapOf<Int, Float>()
    val spreadPxByRange = mutableListOf<Triple<Int, Int, Float>>()
    for ((i, r) in rubies.withIndex()) {
        val start = r.start.coerceAtLeast(0)
        val end = r.end.coerceAtMost(text.length)
        if (end <= start || r.ruby.isEmpty()) continue
        val halfOverflowPx = -spareHalfPx[i]
        if (halfOverflowPx <= 0f) continue
        val leftSlack = rubySideSlackPx(text, rubies, spareHalfPx, start - 1, -1, overhangPx, sideGapPx, borrowGapPx, spaceWidthPx)
        val rightSlack = rubySideSlackPx(text, rubies, spareHalfPx, end, 1, overhangPx, sideGapPx, borrowGapPx, spaceWidthPx)
        val leftPad = (halfOverflowPx - leftSlack).coerceIn(0f, maxPadPx)
        val rightPad = (halfOverflowPx - rightSlack).coerceIn(0f, maxPadPx)
        if (leftPad <= 0f && rightPad <= 0f) continue
        val leftSpace = if (start > 0 && text[start - 1] == ' ') start - 1 else -1
        val rightSpace = if (end < text.length && text[end] == ' ') end else -1
        if ((leftPad > 0f && leftSpace < 0) || (rightPad > 0f && rightSpace < 0)) {
            val spreadPx = rubyLetterSpacingPx(baseWidthPx[i], rubyWidthPx[i], end - start, leftSlack, rightSlack).coerceAtMost(maxSpacingPx)
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
 * Paints every ruby over its base run, in the band that [computeLineMetrics]
 * reserves above each line. A base run broken across lines gets a proportional
 * slice of its ruby on each line.
 *
 * Each reading is confined to the slot its own line actually offers, measured
 * from the laid-out geometry rather than from the text, because only the
 * geometry knows where the lines broke and how wide the padded gaps ended up.
 * A base that starts a wrapped line therefore has no room to its left, however
 * much whitespace precedes it in the text. Readings are ordered by where they
 * were laid out rather than by offset, so a right-to-left run is bounded by the
 * neighbours a reader sees beside it. A slot ends where the neighbouring
 * reading begins, not where its base does, so a reading narrower than its base
 * lends the room it does not need, and a reading over a base with no annotated
 * neighbour may spread over the plain text beside it. A reading too wide for
 * its slot is ellipsised into it, which is what keeps two readings from ever
 * overlapping.
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
    val rubyFontPx = rubyStyle.fontSize.value * density
    val sideGapPx = rubyFontPx * RUBY_SIDE_GAP_RATIO

    class Placement(
        val slice: String,
        val readingWidthPx: Float,
        val baseLeftPx: Float,
        val baseRightPx: Float,
        val colorOffset: Int,
    ) {
        val centredLeftPx get() = rubyCentredLeftPx(baseLeftPx, baseRightPx, readingWidthPx)
    }

    val perLine = HashMap<Int, MutableList<Placement>>()
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
            val width = textMeasurer.measure(AnnotatedString(slice), rubyStyle, softWrap = false, maxLines = 1).size.width.toFloat()
            perLine.getOrPut(line) { mutableListOf() } += Placement(
                slice = slice,
                readingWidthPx = width,
                baseLeftPx = minOf(first.left, last.left),
                baseRightPx = maxOf(first.right, last.right),
                colorOffset = (segStart + segEnd - 1) / 2,
            )
        }
    }

    clipRect {
        for ((line, unsorted) in perLine) {
            val placements = unsorted.sortedBy { it.baseLeftPx }
            val lineLeft = layout.getLineLeft(line)
            val glyphTop = layout.getLineBaseline(line) - baseAscentPx
            val xs = rubyLineLayoutPx(
                desiredLeftPx = FloatArray(placements.size) { placements[it].centredLeftPx },
                widthPx = FloatArray(placements.size) { placements[it].readingWidthPx },
                lineLeftPx = lineLeft,
                lineRightPx = size.width,
                sideGapPx = sideGapPx,
            )
            val allowed = rubyAllowedWidthsPx(xs, size.width, sideGapPx)
            for ((i, p) in placements.withIndex()) {
                val measured = textMeasurer.measure(
                    AnnotatedString(p.slice),
                    rubyStyle,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = minOf(maxWidthPx, ceil(allowed[i]).toInt())),
                )
                val y = glyphTop - gapPx - measured.size.height
                drawText(measured, color = rubyColorAt(text, p.colorOffset, textColor), topLeft = Offset(xs[i], y))
            }
        }
    }
}

/**
 * How much width each reading on a line may take, given the positions
 * [rubyLineLayoutPx] chose for it: everything up to where the next one starts,
 * less [sideGapPx], and up to [lineRightPx] for the last.
 *
 * A reading that got the place it asked for is allowed its full width, so
 * nothing is ellipsised merely because the line is busy. Only a line holding
 * more than it can fit hands back a width smaller than the reading needs. The
 * caller rounds up, because a width computed back out of a position must not
 * fall a fraction short of the reading it was derived from and cut it.
 */
internal fun rubyAllowedWidthsPx(leftPx: FloatArray, lineRightPx: Float, sideGapPx: Float): FloatArray =
    FloatArray(leftPx.size) { i ->
        val nextLeft = if (i == leftPx.lastIndex) lineRightPx else leftPx[i + 1] - sideGapPx
        (nextLeft - leftPx[i]).coerceAtLeast(0f)
    }

/**
 * Where each reading on one line is drawn, given where it would like to be
 * ([desiredLeftPx], centred over its own base) and how wide it is ([widthPx]),
 * both ordered left to right.
 *
 * A reading keeps its place when nothing is in the way. One that would collide
 * with the reading before it slides right, and the line is then pulled back
 * from [lineRightPx] so the last one still fits. Sliding rather than shrinking
 * is what lets a reading far wider than its base stay whole: on
 * `930 tahun, kemudian`, the three Strong's numbers over `930` push the narrow
 * one over `tahun` to the right, into the room its own base was not using.
 *
 * On a line with more readings than room, the positions returned still rise
 * left to right, so the caller can give each one the width up to the next and
 * ellipsise what does not fit, rather than letting two readings overlap.
 */
internal fun rubyLineLayoutPx(
    desiredLeftPx: FloatArray,
    widthPx: FloatArray,
    lineLeftPx: Float,
    lineRightPx: Float,
    sideGapPx: Float,
): FloatArray {
    val xs = desiredLeftPx.copyOf()
    var cursor = lineLeftPx
    for (i in xs.indices) {
        xs[i] = xs[i].coerceAtLeast(cursor)
        cursor = xs[i] + widthPx[i] + sideGapPx
    }
    var limit = lineRightPx
    for (i in xs.indices.reversed()) {
        xs[i] = minOf(xs[i], limit - widthPx[i]).coerceAtLeast(lineLeftPx)
        limit = xs[i] - sideGapPx
    }
    return xs
}
