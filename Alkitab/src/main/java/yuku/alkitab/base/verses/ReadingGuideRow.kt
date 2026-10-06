package yuku.alkitab.base.verses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun ReadingGuideRow(guide: ReadingGuide, ari: Int, color: Int, backgroundColor: Int, startInsetPx: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (guide.mode == ReadingGuideMode.OFF || guide.ranges.isEmpty()) {
        content()
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val line = guide.mode == ReadingGuideMode.LINE && guide.includes(ari)
    val extraPadding = with(LocalDensity.current) { if (line) (10.dp.toPx() - startInsetPx).coerceAtLeast(0f).toDp() else 0.dp }
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().drawBehind {
            if (line) {
                val inset = 5.dp.toPx() - startInsetPx
                val x = if (rtl) size.width - inset else inset
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
        }.padding(start = extraPadding)) {
            content()
        }
    }
}
