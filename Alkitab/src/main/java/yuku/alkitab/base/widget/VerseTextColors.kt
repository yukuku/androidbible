package yuku.alkitab.base.widget

import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.core.graphics.ColorUtils
import yuku.alkitab.base.util.TextColorUtil

object VerseTextColors {
    private data class ColorRange(val start: Int, val end: Int, val color: Int)

    fun apply(text: AnnotatedString, fontColor: Int, background: Int, adjustment: Float, isSelected: Boolean): AnnotatedString {
        val foregrounds = text.spanStyles.filter { it.item.color != Color.Unspecified }
            .map { ColorRange(it.start, it.end, it.item.color.toArgb()) }
        val backgrounds = text.spanStyles.filter { it.item.background != Color.Unspecified }
            .map { ColorRange(it.start, it.end, it.item.background.toArgb()) }
        val builder = AnnotatedString.Builder(text)
        adjustedRanges(text.length, fontColor, background, adjustment, isSelected, foregrounds, backgrounds).forEach {
            builder.addStyle(SpanStyle(color = Color(it.color)), it.start, it.end)
        }
        return builder.toAnnotatedString()
    }

    fun apply(text: SpannableStringBuilder, fontColor: Int, background: Int, adjustment: Float, isSelected: Boolean, verseNumberColor: Int) {
        val numbers = text.getSpans(0, text.length, VerseRenderer.VerseNumberSpan::class.java)
            .map { ColorRange(text.getSpanStart(it), text.getSpanEnd(it), if (isSelected) fontColor else verseNumberColor) }
        val foregrounds = numbers + text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            .map { ColorRange(text.getSpanStart(it), text.getSpanEnd(it), it.foregroundColor) }
        val backgrounds = text.getSpans(0, text.length, BackgroundColorSpan::class.java)
            .map { ColorRange(text.getSpanStart(it), text.getSpanEnd(it), it.backgroundColor) }
        adjustedRanges(text.length, fontColor, background, adjustment, isSelected, foregrounds, backgrounds).forEach {
            text.setSpan(ForegroundColorSpan(it.color), it.start, it.end, 0)
        }
    }

    private fun adjustedRanges(
        length: Int,
        fontColor: Int,
        background: Int,
        adjustment: Float,
        isSelected: Boolean,
        foregrounds: List<ColorRange>,
        backgrounds: List<ColorRange>,
    ): List<ColorRange> {
        val boundaries = (listOf(0, length) + (foregrounds + backgrounds).flatMap { listOf(it.start, it.end) }).distinct().sorted()
        return boundaries.zipWithNext().mapNotNull { (start, end) ->
            val original = foregrounds.lastOrNull { start >= it.start && start < it.end }?.color ?: fontColor
            val painted = backgrounds.filter { start >= it.start && start < it.end }
                .fold(background) { under, band -> ColorUtils.compositeColors(band.color, under) }
            val adjusted = TextColorUtil.adjustForContrast(original, painted, adjustment)
            if (adjusted != original || isSelected) ColorRange(start, end, adjusted) else null
        }
    }
}
