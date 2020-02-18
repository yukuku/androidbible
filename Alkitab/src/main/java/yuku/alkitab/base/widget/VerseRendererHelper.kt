package yuku.alkitab.base.widget

import android.widget.TextView
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.util.Ari

/**
 * TODO Remove this when [VerseRenderer] is converted to Kotlin.
 */
object VerseRendererHelper {
    fun render(
        lText: TextView? = null,
        lVerseNumber: TextView? = null,
        isVerseNumberShown: Boolean = false,
        ari: Int,
        text: String,
        verseNumberText: String = Ari.toVerse(ari).toString(),
        highlightInfo: Highlights.Info? = null,
        checked: Boolean = false,
        inlineLinkSpanFactory: VerseInlineLinkSpan.Factory? = null,
        ftr: VerseRenderer.FormattedTextResult? = null
    ): Int {
        return VerseRenderer.render(lText, lVerseNumber, isVerseNumberShown, ari, text, verseNumberText, highlightInfo, checked, inlineLinkSpanFactory, ftr)
    }
}
