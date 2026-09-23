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
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
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

/**
 * Native Jetpack Compose renderer for a [SongDocument] — the experimental
 * counterpart of the WebView/HTML path in [SongFragment]
 * ([yuku.alkitab.songs.newdoc.SongDocumentRenderer] + `templates/song.html`).
 * It walks [SongDocument.blocks] in document order and maps each block/role to
 * the same visual treatment `song.css` applies, preserving every feature:
 * verse numbering, refrain styling, captions, roles (title/tune/musical/
 * authors/…), inline `u`/`b`/`i` spans, per-line size/alignment, clickable
 * scripture references and YouTube links, copyright, and the "send corrections"
 * (patch text) link.
 *
 * Colors, base font size, line spacing, and typeface come from the same
 * preference-derived dimensions the WebView path feeds into `song.html`; the
 * two-finger pinch zoom is applied as [zoomPercent].
 */

/** Immutable style inputs derived once from the caller's dimensions + zoom. */
class SongComposeStyle(
    /** ARGB text color. */
    val fontColor: Int,
    /** ARGB verse-number color. */
    val verseNumberColor: Int,
    /** ARGB background color. */
    val backgroundColor: Int,
    /** Base body font size in dp, before the per-role multipliers. */
    val baseFontSizeDp: Float,
    val lineSpacingMult: Float,
    /** Body typeface (user font); role text falls back to sans-serif. */
    val typeface: Typeface?,
    val zoomPercent: Int,
) {
    /** Effective base size after applying the pinch-zoom factor. */
    val baseSizeSp = baseFontSizeDp * zoomPercent / 100f

    val bodyColor = Color(fontColor)
    val verseNumberColorC = Color(verseNumberColor)

    /**
     * The body font's natural line height in em (ascent + descent + line gap), the CSS
     * `line-height: normal` that song.html measures before applying [lineSpacingMult].
     */
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

// Links keep the surrounding text color and are marked by the underline alone (song.css `a`).
private val LINK_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)

private val ALLOWED_ALIGNS = setOf("start", "center", "end")

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
    // Verse text is sized in dp, so strip the system font-scale — the pinch
    // zoom is the only multiplier, already folded into baseSizeSp.
    val baseDensity = LocalDensity.current
    val unscaledDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    CompositionLocalProvider(LocalDensity provides unscaledDensity) {
        // SelectionContainer makes the whole song selectable so users can
        // long-press to select and copy any part of the lyrics. Scripture,
        // YouTube, and patch-text links inside stay tappable.
        SelectionContainer {
            // Room to scroll the last line clear of the navigation bar the
            // song draws behind.
            val bottomInset = WindowInsets.safeDrawing
                .asPaddingValues()
                .calculateBottomPadding()

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // song.html <body>: the default 8px margin plus padding 16px 8px
                    .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp + bottomInset),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // The song code sits in the left gutter; when the first block is the
                    // title, they share a row (code left, title centered beside it).
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

/**
 * Top-of-song header: the bold song [code] in the left gutter and, when the
 * first block is the title, the centered title in a weighted column beside it.
 */
@Composable
private fun SongHeader(code: String, titleBlock: PBlock?, style: SongComposeStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (code.isNotEmpty()) {
            // .code { font-size: 125%; font-weight: bold; margin-right: 0.5em; }, where em is the code's own size
            BasicText(
                text = AnnotatedString(code),
                style = roleTextStyle(style, sizeMult = 1.25f, sansSerif = true).copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(end = 0.625f.em.toDp(style)),
            )
        }
        if (titleBlock != null) {
            Box(modifier = Modifier.weight(1f)) {
                PBlockView(titleBlock, style)
            }
        }
    }
}

/** Renders any non-lyric block (lyric blocks need their running index). */
@Composable
private fun BlockView(
    block: Block,
    style: SongComposeStyle,
    onScriptureClick: (String) -> Unit,
    onYoutubeClick: (String) -> Unit,
) {
    when (block) {
        is PBlock -> PBlockView(block, style)
        is RowBlock -> RowBlockView(block, style, onScriptureClick, onYoutubeClick)
        is ScriptureBlock -> ScriptureView(block.osis, style, onScriptureClick)
        is YoutubeBlock -> YoutubeView(block, style, onYoutubeClick)
        is GapBlock -> Spacer(Modifier.height((block.size ?: 1f).em.toDp(style)))
        is UnknownBlock -> {} // forward-compat sink: unrecognized block types are skipped
        is LyricBlock -> {} // handled by the caller with its running index
    }
}

@Composable
private fun PBlockView(block: PBlock, style: SongComposeStyle, inRow: Boolean = false) {
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
        // A row item is shrink-to-fit, so its own align moves the item (see RowBlockView), not its text.
        inRow -> TextAlign.Start
        // .tune { float: right; } → right-aligned on its own line
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

    // .title, .title_original { margin-bottom: 8px; }
    val bottomMargin = if (role == "title" || role == "title_original") 8.dp else 0.dp
    BasicText(text = content, style = textStyle, modifier = Modifier.fillMaxWidth().padding(bottom = bottomMargin))
}

/**
 * Lays out row items like song.css `.row` (`display: flex; justify-content:
 * space-between; align-items: baseline`): each item takes its content width,
 * leftover space goes between the items, and a PBlock `align` of end/center
 * turns into auto margins that take that space instead. When the items do not
 * fit, they shrink in proportion to their content width but not below their
 * longest word, so a short item like "YouTube" never breaks mid-word.
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
            // One Box per item keeps measurables in step with items even when an item renders nothing.
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

/**
 * CSS flex shrinking for `flex: 0 1 auto` items: when [maxWidths] overflow [available],
 * each item gives up space in proportion to its width, stopping at its [minWidths].
 * If even the minimum widths overflow, they are scaled down to fit.
 */
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
    // only a valid 11-char id yields a link
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
    // .youtube { margin: 0.5em 0; }
    BasicText(
        text = annotated,
        style = roleTextStyle(style, sizeMult = 1f, sansSerif = false),
        modifier = Modifier.padding(vertical = 0.5f.em.toDp(style)),
    )
}

@Composable
private fun LyricBlockView(block: LyricBlock, index: Int, totalCount: Int, style: SongComposeStyle) {
    // .lyric { padding-left: 0.4em; }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 0.4f.em.toDp(style))) {
        if (totalCount > 1 || block.caption != null) {
            val captionText = block.caption?.let { lineToAnnotated(it, style) }
                ?: AnnotatedString("Versi ${index + 1}")
            // .lyric_caption { font-weight: bold; font-size: 87.5%; padding-top: 1em; }, where em is the caption's own size
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
    // .verse { margin: 0.3em; margin-top: 0.8em; padding-bottom: 0.8em; border-bottom: 1px dashed #ccc; }
    // The 0.3em bottom margin collapses into the next verse's 0.8em top margin.
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

/**
 * NORMAL/TEXT verse: the lyric lines start 1em in and wrap to 3em
 * (song.css `.line { padding-left: 3em; text-indent: -2em; }`), with the
 * verse number floated into that first 1em. A number wider than the gutter
 * widens it rather than overlapping the text.
 */
@Composable
private fun NumberedVerse(verse: Verse, number: Int?, style: SongComposeStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (number != null) {
            // .verse_ordering { float: left; font-size: 120%; font-weight: 300; }
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

/**
 * REFRAIN verse: an italic "Ref.:" marker above italic lines, the block
 * indented 1em (song.css `.refrain { font-style: italic; padding-left: 1em; }`
 * plus the `Ref.:` `:before` marker; the numeric ordering is hidden). The
 * lines keep the `.line` 1em first-line inset, so they sit deeper than the marker.
 */
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
        // .copyright { font-size: 75%; text-transform: uppercase; font-weight: bold; opacity: 0.54; }
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
        // .patchtext { font-size: 75%; }
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
    // border-bottom: 1px dashed #ccc, drawn with the browser's 3px dash and gap
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

// ---- styling helpers ----

private val SANS_SERIF_ROLES = setOf(
    "title", "title_original", "tune", "musical", "authors_lyric", "authors_music",
)

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/** Per-role font-size multiplier from song.css (relative to the body size). */
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
    // CSS splits the extra line height evenly above and below the glyphs
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    fontFamily = style.bodyFontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Convert an em value (relative to the zoomed base font size) into dp. */
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

/** Map the closed `u`/`b`/`i` inline-style vocabulary to a [SpanStyle]. */
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
