package yuku.alkitab.base.verses

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.util.ReadingRange
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.util.Ari

private val previewPassage = listOf(
    "Perverse disputings of men of corrupt minds, and destitute of the truth, supposing that gain is godliness: from such withdraw thyself.",
    "But godliness with contentment is great gain.",
    "For we brought nothing into this world, and it is certain we can carry nothing out.",
    "And having food and raiment let us be therewith content.",
    "But they that will be rich fall into temptation and a snare, and into many foolish and hurtful lusts, which drown men in destruction and perdition.",
    "For the love of money is the root of all evil: which while some coveted after, they have erred from the faith, and pierced themselves through with many sorrows.",
    "But thou, O man of God, flee these things; and follow after righteousness, godliness, faith, love, patience, meekness.",
)

@Preview(name = "Light", group = "Start and end markers", widthDp = 360)
@Preview(name = "Dark", group = "Start and end markers", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingBoundaryMarkersPreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.LABELS, false, 16)
}

@Preview(name = "Light", group = "Left side line", widthDp = 360)
@Preview(name = "Dark", group = "Left side line", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingLeftLinePreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.LINE, false, 16)
}

@Preview(name = "Light", group = "Current reading indicator", widthDp = 360)
@Preview(name = "Dark", group = "Current reading indicator", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CurrentReadingIndicatorPreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.CAPTION, false, 16)
}

@Preview(name = "Multiple passages", group = "Start and end markers", widthDp = 360)
@Preview(name = "Multiple passages dark", group = "Start and end markers", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MultipleReadingBoundaryMarkersPreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.LABELS, true, 16)
}

@Preview(name = "Multiple passages", group = "Left side line", widthDp = 360)
@Preview(name = "Multiple passages dark", group = "Left side line", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MultipleReadingLeftLinePreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.LINE, true, 16)
}

@Preview(name = "Multiple passages", group = "Current reading indicator", widthDp = 360)
@Preview(name = "Multiple passages dark", group = "Current reading indicator", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MultipleCurrentReadingIndicatorPreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.CAPTION, true, 16)
}

@Composable
internal fun ReadingGuidePreviewContent(mode: ReadingGuideMode, multiplePassages: Boolean, leftMarginDp: Int, modifier: Modifier = Modifier) {
    val ranges = if (multiplePassages) {
        listOf(ReadingRange(Ari.encode(53, 6, 6), Ari.encode(53, 6, 7)), ReadingRange(Ari.encode(53, 6, 9), Ari.encode(53, 6, 10)))
    } else {
        listOf(ReadingRange(Ari.encode(53, 6, 6), Ari.encode(53, 6, 10)))
    }
    ReadingGuideSample(mode, ranges, leftMarginDp, ReadingGuidePreviewCase.NORMAL, modifier)
}

internal enum class ReadingGuidePreviewCase {
    NORMAL, ZERO_PADDING, PADDING_4, PADDING_8, PADDING_9, PADDING_10, WIDE_PADDING,
    SINGLE_VERSE, DISJOINT_RANGES, ADJACENT_RANGES, EMPTY_RANGES, OUTSIDE_CHAPTER,
    FIRST_VISIBLE_VERSE, LAST_VISIBLE_VERSE, OPAQUE_BACKGROUND, SELECTED_VERSE,
    AUDIO_HIGHLIGHT, LARGE_TEXT, SEPIA, LOW_CONTRAST, OFF,
}

@Composable
internal fun ReadingGuideCaseContent(case: ReadingGuidePreviewCase, modifier: Modifier = Modifier) {
    val margin = when (case) {
        ReadingGuidePreviewCase.ZERO_PADDING, ReadingGuidePreviewCase.OPAQUE_BACKGROUND, ReadingGuidePreviewCase.SELECTED_VERSE -> 0
        ReadingGuidePreviewCase.PADDING_4 -> 4
        ReadingGuidePreviewCase.PADDING_8 -> 8
        ReadingGuidePreviewCase.PADDING_9 -> 9
        ReadingGuidePreviewCase.PADDING_10 -> 10
        ReadingGuidePreviewCase.WIDE_PADDING -> 32
        else -> 16
    }
    fun range(start: Int, end: Int) = ReadingRange(Ari.encode(53, 6, start), Ari.encode(53, 6, end))
    val ranges = when (case) {
        ReadingGuidePreviewCase.SINGLE_VERSE -> listOf(range(7, 7))
        ReadingGuidePreviewCase.DISJOINT_RANGES -> listOf(range(6, 7), range(9, 10))
        ReadingGuidePreviewCase.ADJACENT_RANGES -> listOf(range(6, 7), range(8, 10))
        ReadingGuidePreviewCase.EMPTY_RANGES -> emptyList()
        ReadingGuidePreviewCase.OUTSIDE_CHAPTER -> listOf(ReadingRange(Ari.encode(53, 5, 1), Ari.encode(53, 5, 3)))
        ReadingGuidePreviewCase.FIRST_VISIBLE_VERSE -> listOf(range(5, 7))
        ReadingGuidePreviewCase.LAST_VISIBLE_VERSE -> listOf(range(9, 11))
        else -> listOf(range(6, 10))
    }
    ReadingGuideSample(if (case == ReadingGuidePreviewCase.OFF) ReadingGuideMode.OFF else ReadingGuideMode.LINE, ranges, margin, case, modifier)
}

@Composable
internal fun ReadingGuideSplitPreviewContent(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        ReadingGuideCaseContent(ReadingGuidePreviewCase.NORMAL, Modifier.weight(1f))
        ReadingGuideCaseContent(ReadingGuidePreviewCase.ZERO_PADDING, Modifier.weight(1f))
    }
}

@Composable
private fun ReadingGuideSample(mode: ReadingGuideMode, ranges: List<ReadingRange>, leftMarginDp: Int, case: ReadingGuidePreviewCase, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val background = when (case) {
        ReadingGuidePreviewCase.SEPIA -> if (dark) Color(0xff30291f) else Color(0xfff4ecd8)
        else -> if (dark) Color(0xff202020) else Color.White
    }
    val color = when (case) {
        ReadingGuidePreviewCase.LOW_CONTRAST -> if (dark) 0xff333333.toInt() else 0xffeeeeee.toInt()
        ReadingGuidePreviewCase.SEPIA -> if (dark) 0xffe6d5b5.toInt() else 0xff503d27.toInt()
        else -> if (dark) 0xffeeeeee.toInt() else 0xff202020.toInt()
    }
    val guide = ReadingGuide(mode, ranges)
    BibleAppTheme {
        Column(modifier.fillMaxWidth().background(background)) {
            if (mode == ReadingGuideMode.CAPTION) {
                CurrentReadingIndicator(if (ranges.size > 1) "1 Timothy 6:6–7; 1 Timothy 6:9–10" else "1 Timothy 6:6–10", color, {})
            }
            Column(Modifier.padding(start = leftMarginDp.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                BasicText("1 Timothy 6", style = TextStyle(color = Color(color), fontSize = 20.sp), modifier = Modifier.padding(bottom = 12.dp))
                previewPassage.forEachIndexed { index, text ->
                    val verse = index + 5
                    val ari = Ari.encode(53, 6, verse)
                    ReadingGuideRow(guide, ari, true, color, background.toArgb(), with(LocalDensity.current) { leftMarginDp.dp.toPx() }) {
                        Box(if (case == ReadingGuidePreviewCase.OPAQUE_BACKGROUND && guide.includes(ari)) Modifier.background(if (dark) Color(0xff554419) else Color(0xffffed99)) else Modifier) {
                            VerseItemComposeContent(
                                state = previewVerseState(ari, text, color, if (case == ReadingGuidePreviewCase.LARGE_TEXT) 28f else 17f),
                                checked = case == ReadingGuidePreviewCase.SELECTED_VERSE && verse == 7,
                                collapsed = false,
                                audioHighlightColor = if (case == ReadingGuidePreviewCase.AUDIO_HIGHLIGHT && verse == 7) 0xff00a5ff.toInt() else 0,
                                attentionStart = 0L,
                                dragHover = false,
                                onAttentionDone = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun previewVerseState(ari: Int, text: String, color: Int, fontSize: Float) = VerseItemComposeState(
    render = VerseRendererCompose.Result(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(0.3f))) {
                append(Ari.toVerse(ari).toString())
            }
            append("  ")
            append(text)
        },
        gutterVerseNumber = null,
        startPosAfterVerseNumber = Ari.toVerse(ari).toString().length + 2,
        inlineLinks = emptyList(),
        rubies = emptyList(),
        sourceText = text,
    ),
    fontSizeDp = fontSize,
    verseNumberFontSizeDp = fontSize * 0.7f,
    fontColor = color,
    verseNumberColor = color,
    lineSpacingMult = 1.15f,
    typeface = Typeface.DEFAULT,
    fontBold = Typeface.NORMAL,
    attribute = AttributeState(0, 0, 0, false, 1f, null, null, ari, object : VersesController.AttributeListener() {}, List(AttributeView.PROGRESS_MARK_TOTAL_COUNT) { null }),
    onClick = {},
    onInlineLinkClick = { _, _ -> },
    onPinDropped = {},
)

@Preview(name = "Zero padding - light", group = "Reading line: Padding", widthDp = 360)
@Preview(name = "Zero padding - dark", group = "Reading line: Padding", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideZeroPaddingPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.ZERO_PADDING)
}

@Preview(name = "4 dp padding - light", group = "Reading line: Padding", widthDp = 360)
@Preview(name = "4 dp padding - dark", group = "Reading line: Padding", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuidePadding4Preview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.PADDING_4)
}

@Preview(name = "8 dp padding - light", group = "Reading line: Padding", widthDp = 360)
@Preview(name = "8 dp padding - dark", group = "Reading line: Padding", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuidePadding8Preview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.PADDING_8)
}

@Preview(name = "9 dp padding - light", group = "Reading line: Padding", widthDp = 360)
@Preview(name = "9 dp padding - dark", group = "Reading line: Padding", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuidePadding9Preview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.PADDING_9)
}

@Preview(name = "10 dp minimum clearance - light", group = "Reading line: Padding", widthDp = 360)
@Preview(name = "10 dp minimum clearance - dark", group = "Reading line: Padding", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuidePadding10Preview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.PADDING_10)
}

@Preview(name = "32 dp padding - light", group = "Reading line: Padding", widthDp = 360)
@Preview(name = "32 dp padding - dark", group = "Reading line: Padding", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideWidePaddingPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.WIDE_PADDING)
}

@Preview(name = "Single verse - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "Single verse - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideSingleVersePreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.SINGLE_VERSE)
}

@Preview(name = "Adjacent passages - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "Adjacent passages - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideAdjacentRangesPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.ADJACENT_RANGES)
}

@Preview(name = "No active reading - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "No active reading - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideEmptyRangesPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.EMPTY_RANGES)
}

@Preview(name = "Reading in another chapter - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "Reading in another chapter - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideOutsideChapterPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.OUTSIDE_CHAPTER)
}

@Preview(name = "Starts at first displayed verse - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "Starts at first displayed verse - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideFirstVisibleVersePreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.FIRST_VISIBLE_VERSE)
}

@Preview(name = "Ends at last displayed verse - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "Ends at last displayed verse - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideLastVisibleVersePreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.LAST_VISIBLE_VERSE)
}

@Preview(name = "Opaque verse background at zero padding - light", group = "Reading line: Overlays", widthDp = 360)
@Preview(name = "Opaque verse background at zero padding - dark", group = "Reading line: Overlays", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideOpaqueBackgroundPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.OPAQUE_BACKGROUND)
}

@Preview(name = "Selected verse at zero padding - light", group = "Reading line: Overlays", widthDp = 360)
@Preview(name = "Selected verse at zero padding - dark", group = "Reading line: Overlays", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideSelectedVersePreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.SELECTED_VERSE)
}

@Preview(name = "Audio highlighted verse - light", group = "Reading line: Overlays", widthDp = 360)
@Preview(name = "Audio highlighted verse - dark", group = "Reading line: Overlays", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideAudioHighlightPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.AUDIO_HIGHLIGHT)
}

@Preview(name = "Large reading text - light", group = "Reading line: Layout", widthDp = 360)
@Preview(name = "Large reading text - dark", group = "Reading line: Layout", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideLargeTextPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.LARGE_TEXT)
}

@Preview(name = "Custom sepia colors - light", group = "Reading line: Colors", widthDp = 360)
@Preview(name = "Custom sepia colors - dark", group = "Reading line: Colors", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideSepiaPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.SEPIA)
}

@Preview(name = "Low contrast text with line fallback - light", group = "Reading line: Colors", widthDp = 360)
@Preview(name = "Low contrast text with line fallback - dark", group = "Reading line: Colors", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideLowContrastPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.LOW_CONTRAST)
}

@Preview(name = "Guides disabled - light", group = "Reading line: Ranges", widthDp = 360)
@Preview(name = "Guides disabled - dark", group = "Reading line: Ranges", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideOffPreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.OFF)
}

@Preview(name = "Narrow pane - light", group = "Reading line: Layout", widthDp = 180)
@Preview(name = "Narrow pane - dark", group = "Reading line: Layout", widthDp = 180, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideNarrowPanePreview() {
    ReadingGuideCaseContent(ReadingGuidePreviewCase.ZERO_PADDING)
}

@Preview(name = "Split panes - light", group = "Reading line: Layout", widthDp = 600)
@Preview(name = "Split panes - dark", group = "Reading line: Layout", widthDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingGuideSplitPanesPreview() {
    ReadingGuideSplitPreviewContent()
}
