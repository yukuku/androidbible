package yuku.alkitab.base.verses

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    ReadingGuidePreviewContent(ReadingGuideMode.LABELS)
}

@Preview(name = "Light", group = "Left side line", widthDp = 360)
@Preview(name = "Dark", group = "Left side line", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadingLeftLinePreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.LINE)
}

@Preview(name = "Light", group = "Current reading indicator", widthDp = 360)
@Preview(name = "Dark", group = "Current reading indicator", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CurrentReadingIndicatorPreview() {
    ReadingGuidePreviewContent(ReadingGuideMode.CAPTION)
}

@Composable
internal fun ReadingGuidePreviewContent(mode: ReadingGuideMode, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val color = if (dark) 0xffeeeeee.toInt() else 0xff202020.toInt()
    val background = if (dark) Color(0xff202020) else Color.White
    val guide = ReadingGuide(mode, listOf(ReadingRange(Ari.encode(53, 6, 6), Ari.encode(53, 6, 10))))
    BibleAppTheme {
        Column(modifier.fillMaxWidth().background(background)) {
            if (mode == ReadingGuideMode.CAPTION) {
                CurrentReadingIndicator("1 Timothy 6:6–10", color, {})
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BasicText("1 Timothy 6", style = TextStyle(color = Color(color), fontSize = 20.sp), modifier = Modifier.padding(bottom = 12.dp))
                previewPassage.forEachIndexed { index, text ->
                    val verse = index + 5
                    val ari = Ari.encode(53, 6, verse)
                    ReadingGuideRow(guide, ari, true, color) {
                        VerseItemComposeContent(
                            state = previewVerseState(ari, text, color),
                            checked = false,
                            collapsed = false,
                            audioHighlightColor = 0,
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

private fun previewVerseState(ari: Int, text: String, color: Int) = VerseItemComposeState(
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
    fontSizeDp = 17f,
    verseNumberFontSizeDp = 12f,
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
