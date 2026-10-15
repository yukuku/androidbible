package yuku.alkitab.base.verses

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import yuku.alkitab.base.widget.VerseRendererCompose

internal fun splitVerseAroundAttributes(
    render: VerseRendererCompose.Result,
    preparedText: AnnotatedString,
    narrowLayout: TextLayoutResult,
    attributeHeight: Int,
): Pair<VerseRendererCompose.Result, VerseRendererCompose.Result?> {
    var line = 0
    while (line < narrowLayout.lineCount - 1 && narrowLayout.getLineBottom(line) < attributeHeight) line++
    var split = narrowLayout.getLineEnd(line)
    // Keep a reading and its base together when the width changes beneath the icons.
    while (line < narrowLayout.lineCount - 1 && render.rubies.any { it.start < split && it.end > split }) {
        split = narrowLayout.getLineEnd(++line)
    }
    if (split >= render.text.length) return render.copy(text = preparedText) to null

    // A terminal newline would create an extra empty line in the upper text block.
    val prefixEnd = if (split > 0 && preparedText[split - 1] == '\n') split - 1 else split
    val first = sliceVerseRender(render.copy(text = preparedText), 0, prefixEnd)
    val isContinuation = split > 0 && render.text[split - 1] != '\n' &&
        render.text.paragraphStyles.none { it.start == split || it.end == split }
    val direction = if (isContinuation) narrowLayout.getParagraphDirection(split) else null
    return first to sliceVerseRender(render, split, render.text.length, direction)
}

private fun sliceVerseRender(
    render: VerseRendererCompose.Result,
    start: Int,
    end: Int,
    continuationDirection: ResolvedTextDirection? = null,
): VerseRendererCompose.Result {
    val sliced = render.text.subSequence(start, end)
    val text = if (continuationDirection == null) sliced else buildAnnotatedString {
        append(sliced)
        val firstParagraphEnd = minOf(
            sliced.text.indexOf('\n').takeIf { it >= 0 } ?: sliced.length,
            sliced.paragraphStyles.firstOrNull()?.end ?: sliced.length,
        )
        val indent = sliced.paragraphStyles.firstOrNull()?.item?.textIndent
        addStyle(
            ParagraphStyle(
                textIndent = indent?.let { TextIndent(it.restLine, it.restLine) },
                textDirection = if (continuationDirection == ResolvedTextDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr,
            ),
            0, firstParagraphEnd,
        )
    }
    return render.copy(
        text = text,
        gutterVerseNumber = render.gutterVerseNumber.takeIf { start == 0 },
        startPosAfterVerseNumber = (render.startPosAfterVerseNumber - start).coerceIn(0, end - start),
        inlineLinks = render.inlineLinks.mapNotNull {
            val from = maxOf(start, it.start)
            val to = minOf(end, it.end)
            if (from < to) it.copy(start = from - start, end = to - start) else null
        },
        rubies = render.rubies.filter {
            it.start >= start && it.end <= end &&
                (it.start < end || end == render.text.length || render.text[end] == '\n')
        }.map { it.copy(start = it.start - start, end = it.end - start) },
    )
}
