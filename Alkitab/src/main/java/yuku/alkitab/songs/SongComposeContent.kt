package yuku.alkitab.songs

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import yuku.alkitab.songs.newdoc.Block
import yuku.alkitab.songs.newdoc.GapBlock
import yuku.alkitab.songs.newdoc.Line
import yuku.alkitab.songs.newdoc.LyricBlock
import yuku.alkitab.songs.newdoc.PBlock
import yuku.alkitab.songs.newdoc.RowBlock
import yuku.alkitab.songs.newdoc.ScriptureBlock
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.UnknownBlock
import yuku.alkitab.songs.newdoc.Verse
import yuku.alkitab.songs.newdoc.VerseKind
import yuku.alkitab.songs.newdoc.VerseLine
import yuku.alkitab.songs.newdoc.YoutubeBlock
import yuku.alkitab.songs.newdoc.plainText

class SongComposeStyle(
    val fontColor: Int,
    val verseNumberColor: Int,
    val backgroundColor: Int,
    val baseFontSizeDp: Float,
    val lineSpacingMult: Float,
    val typeface: Typeface?,
    val zoomPercent: Int,
) {
    val baseSizeSp = baseFontSizeDp * zoomPercent / 100f

    val bodyColor = Color(fontColor)
    val verseNumberColorC = Color(verseNumberColor)

    /** The font's natural line height in em, before [lineSpacingMult]. */
    val normalLineHeightEm: Float = Paint().run {
        typeface = this@SongComposeStyle.typeface ?: Typeface.DEFAULT
        textSize = 100f
        val fm = fontMetrics
        (fm.descent - fm.ascent + fm.leading) / 100f
    }

    val bodyFontFamily: FontFamily = when (val tf = typeface) {
        null, Typeface.DEFAULT -> FontFamily.Default
        Typeface.SERIF -> FontFamily.Serif
        Typeface.MONOSPACE -> FontFamily.Monospace
        Typeface.SANS_SERIF -> FontFamily.SansSerif
        else -> FontFamily(ComposeTypeface(tf))
    }
}

private val LINK_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)

private val ALLOWED_ALIGNS = setOf("start", "center", "end")

/** Compose renderer for a [SongDocument], the counterpart of the WebView renderer in [SongFragment]. */
@Composable
fun SongDocumentComposable(
    doc: SongDocument,
    style: SongComposeStyle,
    copyright: String?,
    patchTextLinkLabel: String?,
    onScriptureClick: (osis: String) -> Unit,
    onYoutubeClick: (videoId: String) -> Unit,
    onPatchTextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sizes are in dp and pinch zoom is already in baseSizeSp, so ignore the system font scale.
    val baseDensity = LocalDensity.current
    val unscaledDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides unscaledDensity) {
        SelectionContainer {
            // The song draws behind the navigation bar.
            val bottomInset = WindowInsets.safeDrawing
                .asPaddingValues()
                .calculateBottomPadding()

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp + bottomInset),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val titleBlock = (doc.blocks.firstOrNull() as? PBlock)?.takeIf { it.role == "title" }
                    val bodyBlocks = if (titleBlock != null) doc.blocks.drop(1) else doc.blocks

                    if (doc.code.isNotEmpty() || titleBlock != null) {
                        SongHeader(doc.code, titleBlock, style)
                    }

                    val lyricBlocks = bodyBlocks.filterIsInstance<LyricBlock>()
                    var lyricIndex = 0
                    for (block in bodyBlocks) {
                        when (block) {
                            is LyricBlock -> {
                                LyricBlockView(block, lyricIndex, lyricBlocks.size, style)
                                lyricIndex++
                            }
                            else -> BlockView(block, style, onScriptureClick, onYoutubeClick)
                        }
                    }

                    FooterView(copyright, patchTextLinkLabel, style, onPatchTextClick)
                }
            }
        }
    }
}

@Composable
private fun SongHeader(code: String, titleBlock: PBlock?, style: SongComposeStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (code.isNotEmpty()) {
            BasicText(
                text = AnnotatedString(code),
                style = roleTextStyle(style, sizeMult = 1.25f, sansSerif = true).copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(end = 0.625f.em.toDp(style)),
            )
        }
        if (titleBlock != null) {
            Box(modifier = Modifier.weight(1f)) {
                PBlockView(titleBlock, style, inRow = false)
            }
        }
    }
}

@Composable
private fun BlockView(
    block: Block,
    style: SongComposeStyle,
    onScriptureClick: (String) -> Unit,
    onYoutubeClick: (String) -> Unit,
) {
    when (block) {
        is PBlock -> PBlockView(block, style, inRow = false)
        is RowBlock -> RowBlockView(block, style, onScriptureClick, onYoutubeClick)
        is ScriptureBlock -> ScriptureView(block.osis, style, onScriptureClick)
        is YoutubeBlock -> YoutubeView(block, style, onYoutubeClick)
        is GapBlock -> Spacer(Modifier.height((block.size ?: 1f).em.toDp(style)))
        is UnknownBlock -> {}
        is LyricBlock -> {}
    }
}

@Composable
private fun PBlockView(block: PBlock, style: SongComposeStyle, inRow: Boolean) {
    val role = block.role
    val sansSerif = role in SANS_SERIF_ROLES
    val sizeMult = block.size?.takeIf { it.isFinite() } ?: roleSizeMult(role)
    val bold = role == "title" || role == "copyright"
    val italic = role == "note"
    val uppercase = role == "tune" || role == "copyright"
    val opacity = if (role == "copyright") 0.54f else 1f

    val align = block.align?.takeIf { it in ALLOWED_ALIGNS }
    val textAlign = when {
        role == "title" || role == "title_original" -> TextAlign.Center
        // In a row, align positions the item itself (see RowBlockView).
        inRow -> TextAlign.Start
        role == "tune" -> TextAlign.End
        align == "center" -> TextAlign.Center
        align == "end" -> TextAlign.End
        else -> null
    }

    var content = lineToAnnotated(block.content, style)
    if (uppercase) content = AnnotatedString(content.text.uppercase())

    val textStyle = roleTextStyle(style, sizeMult, sansSerif).copy(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        color = style.bodyColor.copy(alpha = opacity),
        textAlign = textAlign ?: TextAlign.Unspecified,
    )

    val bottomMargin = if (role == "title" || role == "title_original") 8.dp else 0.dp
    BasicText(text = content, style = textStyle, modifier = Modifier.fillMaxWidth().padding(bottom = bottomMargin))
}

/**
 * Items take their content width, with the leftover space between them, or before (end)
 * or around (center) an aligned item. Items too wide to fit shrink, but not below their
 * longest word. Items share a first baseline.
 */
@Composable
private fun RowBlockView(
    block: RowBlock,
    style: SongComposeStyle,
    onScriptureClick: (String) -> Unit,
    onYoutubeClick: (String) -> Unit,
) {
    val items = block.items
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            // A Box per item, so measurables line up with items even when an item draws nothing.
            for (item in items) {
                Box {
                    when (item) {
                        is PBlock -> PBlockView(block = item, style = style, inRow = true)
                        is ScriptureBlock -> ScriptureView(item.osis, style, onScriptureClick)
                        is YoutubeBlock -> YoutubeView(item, style, onYoutubeClick)
                        is GapBlock -> Spacer(Modifier.width((item.size ?: 1f).em.toDp(style)))
                        else -> {}
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val available = constraints.maxWidth
        val maxWidths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val minWidths = measurables.mapIndexed { i, m -> m.minIntrinsicWidth(Constraints.Infinity).coerceAtMost(maxWidths[i]) }
        val widths = shrinkRowItems(maxWidths, minWidths, available)

        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints(maxWidth = widths[i])) }
        val baselines = placeables.map { p -> p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: 0 }
        val aboveBaseline = baselines.maxOrNull() ?: 0
        val height = placeables.indices.maxOfOrNull { i -> aboveBaseline - baselines[i] + placeables[i].height } ?: 0

        val autoMargins = items.map { item ->
            when ((item as? PBlock)?.align) {
                "end" -> 1 to 0
                "center" -> 1 to 1
                else -> 0 to 0
            }
        }
        val autoMarginCount = autoMargins.sumOf { it.first + it.second }
        val free = (available - widths.sum()).coerceAtLeast(0)
        val autoMargin = if (autoMarginCount > 0) free / autoMarginCount else 0
        val between = if (autoMarginCount == 0 && items.size > 1) free / (items.size - 1) else 0

        layout(available, height) {
            var x = 0
            placeables.forEachIndexed { i, p ->
                x += autoMargins[i].first * autoMargin
                p.placeRelative(x, aboveBaseline - baselines[i])
                x += widths[i] + autoMargins[i].second * autoMargin + between
            }
        }
    }
}

/** Shrinks overflowing items in proportion to their width, stopping at [minWidths]. */
private fun shrinkRowItems(maxWidths: List<Int>, minWidths: List<Int>, available: Int): List<Int> {
    val widths = maxWidths.map { it.toFloat() }.toMutableList()
    if (widths.sum() <= available) return maxWidths
    if (minWidths.sum() >= available) {
        val total = minWidths.sum().coerceAtLeast(1)
        return minWidths.map { it * available / total }
    }
    val frozen = BooleanArray(widths.size)
    while (true) {
        val overflow = widths.sum() - available
        if (overflow <= 0.5f) break
        val unfrozenTotal = widths.indices.filter { !frozen[it] }.sumOf { widths[it].toDouble() }.toFloat()
        if (unfrozenTotal <= 0f) break
        var clamped = false
        for (i in widths.indices) {
            if (frozen[i]) continue
            val target = widths[i] - overflow * widths[i] / unfrozenTotal
            if (target < minWidths[i]) {
                widths[i] = minWidths[i].toFloat()
                frozen[i] = true
                clamped = true
            }
        }
        if (!clamped) {
            for (i in widths.indices) if (!frozen[i]) widths[i] -= overflow * widths[i] / unfrozenTotal
            break
        }
    }
    return widths.map { it.toInt() }
}

@Composable
private fun ScriptureView(osis: String, style: SongComposeStyle, onScriptureClick: (String) -> Unit) {
    val parts = remember(osis) { ScriptureReferenceRenderer.renderParts(osis) }
    if (parts.isEmpty()) return

    val annotated = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append("; ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = part.osisId,
                    styles = TextLinkStyles(LINK_STYLE),
                ) { onScriptureClick(part.osisId) },
            ) {
                append(part.readable)
            }
        }
    }

    BasicText(
        text = annotated,
        style = roleTextStyle(style, sizeMult = 1f, sansSerif = true),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun YoutubeView(block: YoutubeBlock, style: SongComposeStyle, onYoutubeClick: (String) -> Unit) {
    if (!YOUTUBE_ID_REGEX.matches(block.videoId)) return

    val annotated = buildAnnotatedString {
        withLink(
            LinkAnnotation.Clickable(
                tag = block.videoId,
                styles = TextLinkStyles(LINK_STYLE),
            ) { onYoutubeClick(block.videoId) },
        ) {
            append("YouTube")
        }
    }
    BasicText(
        text = annotated,
        style = roleTextStyle(style, sizeMult = 1f, sansSerif = false),
        modifier = Modifier.padding(vertical = 0.5f.em.toDp(style)),
    )
}

@Composable
private fun LyricBlockView(block: LyricBlock, index: Int, totalCount: Int, style: SongComposeStyle) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 0.4f.em.toDp(style))) {
        if (totalCount > 1 || block.caption != null) {
            val captionText = block.caption?.let { lineToAnnotated(it, style) }
                ?: AnnotatedString("Versi ${index + 1}")
            BasicText(
                text = captionText,
                style = roleTextStyle(style, sizeMult = 0.875f, sansSerif = false)
                    .copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 0.875f.em.toDp(style)),
            )
        }

        var verseNumberNormal = 0
        var verseNumberReff = 0
        for (verse in block.verses) {
            when (verse.kind) {
                VerseKind.REFRAIN -> verseNumberReff++
                VerseKind.NORMAL -> verseNumberNormal++
                VerseKind.TEXT -> {}
            }
            val number = when (verse.kind) {
                VerseKind.REFRAIN -> verseNumberReff
                VerseKind.NORMAL -> verseNumberNormal
                VerseKind.TEXT -> 0
            }
            VerseView(verse, number, style)
        }
    }
}

@Composable
private fun VerseView(verse: Verse, number: Int, style: SongComposeStyle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 0.3f.em.toDp(style), top = 0.8f.em.toDp(style), end = 0.3f.em.toDp(style)),
    ) {
        when (verse.kind) {
            VerseKind.REFRAIN -> RefrainVerse(verse, style)
            else -> NumberedVerse(verse, if (verse.kind == VerseKind.NORMAL) number else null, style)
        }

        Spacer(Modifier.height(0.8f.em.toDp(style)))
        DashedDivider()
    }
}

@Composable
private fun NumberedVerse(verse: Verse, number: Int?, style: SongComposeStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (number != null) {
            BasicText(
                text = AnnotatedString(number.toString()),
                style = roleTextStyle(style, sizeMult = 1.2f, sansSerif = false).copy(
                    fontWeight = FontWeight.Light,
                    color = style.verseNumberColorC,
                ),
                modifier = Modifier
                    .alignByBaseline()
                    .widthIn(min = 1f.em.toDp(style))
                    .padding(end = 0.25f.em.toDp(style)),
            )
        } else {
            Spacer(Modifier.width(1f.em.toDp(style)))
        }
        VerseLines(verse.lines, style, italic = false, modifier = Modifier.weight(1f).alignByBaseline())
    }
}

@Composable
private fun RefrainVerse(verse: Verse, style: SongComposeStyle) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 1f.em.toDp(style))) {
        BasicText(
            text = AnnotatedString("Ref.:"),
            style = roleTextStyle(style, sizeMult = 0.75f, sansSerif = true).copy(fontStyle = FontStyle.Italic),
        )
        VerseLines(
            verse.lines,
            style,
            italic = true,
            modifier = Modifier.fillMaxWidth().padding(start = 1f.em.toDp(style)),
        )
    }
}

@Composable
private fun VerseLines(lines: List<VerseLine>, style: SongComposeStyle, italic: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        for (line in lines) {
            val (content, lineSizeMult, lineAlign) = resolveVerseLine(line, style)
            val lineStyle = lyricLineStyle(style, lineSizeMult).copy(
                fontStyle = if (italic) FontStyle.Italic else null,
                textAlign = when (lineAlign) {
                    "center" -> TextAlign.Center
                    "end" -> TextAlign.End
                    else -> TextAlign.Unspecified
                },
                textIndent = TextIndent(firstLine = 0.sp, restLine = (style.baseSizeSp * 2f).sp),
            )
            BasicText(text = content, style = lineStyle, modifier = Modifier.fillMaxWidth())
        }
    }
}

private data class ResolvedLine(val content: AnnotatedString, val sizeMult: Float, val align: String?)

private fun resolveVerseLine(vl: VerseLine, style: SongComposeStyle): ResolvedLine = when (vl) {
    is VerseLine.Simple -> ResolvedLine(lineToAnnotated(vl.line, style), 1f, null)
    is VerseLine.Wrapped -> ResolvedLine(
        lineToAnnotated(vl.content, style),
        vl.size?.takeIf { it.isFinite() } ?: 1f,
        vl.align?.takeIf { it in ALLOWED_ALIGNS },
    )
}

@Composable
private fun FooterView(
    copyright: String?,
    patchTextLinkLabel: String?,
    style: SongComposeStyle,
    onPatchTextClick: () -> Unit,
) {
    Spacer(Modifier.height(0.8f.em.toDp(style)))
    if (!copyright.isNullOrEmpty()) {
        BasicText(
            text = AnnotatedString(copyright.uppercase()),
            style = roleTextStyle(style, sizeMult = 0.75f, sansSerif = false).copy(
                fontWeight = FontWeight.Bold,
                color = style.bodyColor.copy(alpha = 0.54f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(8.dp))
    if (!patchTextLinkLabel.isNullOrEmpty()) {
        val annotated = buildAnnotatedString {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "patchtext",
                    styles = TextLinkStyles(LINK_STYLE),
                ) { onPatchTextClick() },
            ) {
                append(patchTextLinkLabel)
            }
        }
        BasicText(
            text = annotated,
            style = roleTextStyle(style, sizeMult = 0.75f, sansSerif = false),
        )
    }
}

@Composable
private fun DashedDivider() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(1.dp),
    ) {
        val dash = 3.dp.toPx()
        val y = size.height / 2f
        var x = 0f
        val color = Color(0xFFCCCCCC)
        while (x < size.width) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset((x + dash).coerceAtMost(size.width), y),
                strokeWidth = size.height,
            )
            x += dash * 2
        }
    }
}

private val SANS_SERIF_ROLES = setOf(
    "title", "title_original", "tune", "musical", "authors_lyric", "authors_music",
)

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

private fun roleSizeMult(role: String?): Float = when (role) {
    "title" -> 1.25f
    "title_original", "tune", "musical", "authors_lyric", "authors_music" -> 0.875f
    "copyright" -> 0.75f
    else -> 1f
}

private fun roleTextStyle(style: SongComposeStyle, sizeMult: Float, sansSerif: Boolean): TextStyle = TextStyle(
    color = style.bodyColor,
    fontSize = (style.baseSizeSp * sizeMult).sp,
    fontFamily = if (sansSerif) FontFamily.SansSerif else style.bodyFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun lyricLineStyle(style: SongComposeStyle, sizeMult: Float): TextStyle = TextStyle(
    color = style.bodyColor,
    fontSize = (style.baseSizeSp * sizeMult).sp,
    lineHeight = (style.baseSizeSp * sizeMult * style.normalLineHeightEm * style.lineSpacingMult).sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    fontFamily = style.bodyFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun androidx.compose.ui.unit.TextUnit.toDp(style: SongComposeStyle) =
    (this.value * style.baseSizeSp).dp

private fun lineToAnnotated(line: Line, style: SongComposeStyle): AnnotatedString = when (line) {
    is Line.Plain -> AnnotatedString(line.text)
    is Line.Styled -> buildAnnotatedString {
        for (span in line.spans) {
            val spanStyle = spanStyleFor(span.style)
            if (spanStyle == null) {
                append(span.text)
            } else {
                withStyle(spanStyle) { append(span.text) }
            }
        }
    }
}

private fun spanStyleFor(styles: List<String>): SpanStyle? {
    if (styles.isEmpty()) return null
    var weight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var decoration: TextDecoration? = null
    for (s in styles) {
        when (s) {
            "b" -> weight = FontWeight.Bold
            "i" -> fontStyle = FontStyle.Italic
            "u" -> decoration = TextDecoration.Underline
        }
    }
    if (weight == null && fontStyle == null && decoration == null) return null
    return SpanStyle(fontWeight = weight, fontStyle = fontStyle, textDecoration = decoration)
}
