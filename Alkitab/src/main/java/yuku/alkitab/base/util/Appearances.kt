package yuku.alkitab.base.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.text.underline
import yuku.alkitab.base.S

object Appearances {
    @JvmStatic
    fun applyMarkerSnippetContentAndAppearance(t: TextView, reference: String, verseText: CharSequence, textSizeMult: Float) {
        val sb = SpannableStringBuilder().apply {
            underline { append(reference) }
            append(" ")
            append(verseText)
        }
        t.text = sb
        applyTextAppearance(t, textSizeMult)
    }

    @JvmStatic
    fun applyTextAppearance(t: TextView, fontSizeMultiplier: Float) {
        val applied = S.applied()

        t.setTypeface(applied.fontFace, applied.fontBold)
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * fontSizeMultiplier)
        t.includeFontPadding = false
        t.setTextColor(applied.fontColor)
        t.setLinkTextColor(applied.fontColor)
        t.setLineSpacing(0f, applied.lineSpacingMult)
    }

    @JvmStatic
    fun applyPericopeTitleAppearance(t: TextView, fontSizeMultiplier: Float) {
        val applied = S.applied()

        t.setTypeface(applied.fontFace, Typeface.BOLD)
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * fontSizeMultiplier)
        t.setTextColor(applied.fontColor)
        t.setLineSpacing(0f, applied.lineSpacingMult)
    }

    @JvmStatic
    fun applyPericopeParallelTextAppearance(t: TextView, fontSizeMultiplier: Float) {
        val applied = S.applied()

        t.typeface = applied.fontFace
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 0.8235294f * fontSizeMultiplier)
        t.movementMethod = LinkMovementMethod.getInstance()
        t.setTextColor(applied.fontColor)
        t.setLinkTextColor(applied.fontColor)
        t.setLineSpacing(0f, applied.lineSpacingMult)
    }

    @JvmStatic
    fun applySearchResultReferenceAppearance(t: TextView, sb: SpannableStringBuilder, textSizeMult: Float) {
        applyMarkerTitleTextAppearance(t, textSizeMult)
        sb.setSpan(UnderlineSpan(), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        t.text = sb
        t.setLineSpacing(0f, S.applied().lineSpacingMult)
    }

    @JvmStatic
    fun applyMarkerTitleTextAppearance(t: TextView, textSizeMult: Float) {
        val applied = S.applied()

        t.setTypeface(applied.fontFace, applied.fontBold)
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 1.2f * textSizeMult)
        t.setTextColor(applied.fontColor)
    }

    @JvmStatic
    fun applyMarkerDateTextAppearance(t: TextView, textSizeMult: Float) {
        val applied = S.applied()

        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 0.8f * textSizeMult)
        t.setTextColor(applied.fontColor)
    }

    @JvmStatic
    fun applyVerseNumberAppearance(t: TextView, textSizeMult: Float) {
        val applied = S.applied()

        t.setTypeface(applied.fontFace, applied.fontBold)
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 0.7f * textSizeMult)
        t.includeFontPadding = false
        t.setTextColor(applied.verseNumberColor)
    }
}
