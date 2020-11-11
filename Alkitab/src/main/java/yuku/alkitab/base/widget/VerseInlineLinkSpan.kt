package yuku.alkitab.base.widget

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

abstract class VerseInlineLinkSpan(private val type: Type, private val arif: Int) : ClickableSpan() {
    fun interface Factory {
        fun create(type: Type, arif: Int): VerseInlineLinkSpan
    }

    enum class Type {
        footnote, xref
    }

    override fun onClick(widget: View) {
        onClick(type, arif)
    }

    open fun onClick(type: Type, arif: Int) {}

    override fun updateDrawState(ds: TextPaint) {
        // don't call super to prevent link underline and link coloring
        // NOP
    }
}