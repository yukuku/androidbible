package yuku.alkitab.base.util

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.view.ViewCompat
import yuku.alkitab.debug.R

object NumericEntryAppearance {
    @JvmStatic
    fun apply(view: TextView, isActive: Boolean, isReplaceOnNextInput: Boolean) {
        UiTypography.apply(view, UiTextRole.NUMBER)
        val density = view.resources.displayMetrics.density
        val accent = view.context.getColor(R.color.accent)
        view.setPaddingRelative((8 * density).toInt(), view.paddingTop, (8 * density).toInt(), view.paddingBottom)
        view.setTextColor(if (isReplaceOnNextInput) 0xffb2bdc4.toInt() else 0xfff4f7f9.toInt())
        view.background = GradientDrawable().apply {
            cornerRadius = 8 * density
            setColor(0xff293c45.toInt())
            setStroke(((if (isActive) 2 else 1) * density).toInt(), if (isActive) accent else 0xff89969d.toInt(), if (isReplaceOnNextInput) 4 * density else 0f, if (isReplaceOnNextInput) 3 * density else 0f)
        }
        val cursor = if (isActive && !isReplaceOnNextInput) GradientDrawable().apply {
            setColor(accent)
            setSize((2 * density).toInt(), (24 * density).toInt())
            setBounds(0, 0, (2 * density).toInt(), (24 * density).toInt())
        } else null
        view.compoundDrawablePadding = (3 * density).toInt()
        view.setCompoundDrawablesRelative(null, null, cursor, null)
        ViewCompat.setStateDescription(view, view.context.getString(if (isReplaceOnNextInput) R.string.keypad_value_replace else R.string.keypad_value_append))
    }
}
