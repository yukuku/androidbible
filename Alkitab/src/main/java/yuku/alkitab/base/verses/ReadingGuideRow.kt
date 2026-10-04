package yuku.alkitab.base.verses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yuku.alkitab.debug.R

@Composable
internal fun ReadingGuideRow(guide: ReadingGuide, ari: Int, verse: Boolean, color: Int, backgroundColor: Int, leftInsetPx: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (guide.mode == ReadingGuideMode.OFF || guide.mode == ReadingGuideMode.CAPTION || guide.ranges.isEmpty()) {
        content()
        return
    }
    val labels = guide.mode == ReadingGuideMode.LABELS && verse
    val line = guide.mode == ReadingGuideMode.LINE && guide.includes(ari)
    val extraPadding = with(LocalDensity.current) { if (line) (10.dp.toPx() - leftInsetPx).coerceAtLeast(0f).toDp() else 0.dp }
    Column(modifier.fillMaxWidth()) {
        if (labels && guide.startsAt(ari)) {
            ReadingBoundaryLabel(stringResource(R.string.current_reading_start), color)
        }
        Box(Modifier.fillMaxWidth().drawBehind {
            if (line) {
                val x = 5.dp.toPx() - leftInsetPx
                val radius = 1.dp.toPx()
                clipRect(left = x - radius, top = 0f, right = x + radius, bottom = size.height) {
                    drawLine(
                        Color(readingGuideLineColor(color, backgroundColor)),
                        Offset(x, if (guide.startsAt(ari)) radius else 0f),
                        Offset(x, size.height - if (guide.endsAt(ari)) radius else 0f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }.absolutePadding(left = extraPadding)) {
            content()
        }
        if (labels && guide.endsAt(ari)) {
            ReadingBoundaryLabel(stringResource(R.string.current_reading_end), color)
        }
    }
}

@Composable
private fun ReadingBoundaryLabel(text: String, color: Int, modifier: Modifier = Modifier) {
    BasicText(text, style = TextStyle(color = Color(color), fontSize = 12.sp), modifier = modifier.padding(vertical = 6.dp))
}
