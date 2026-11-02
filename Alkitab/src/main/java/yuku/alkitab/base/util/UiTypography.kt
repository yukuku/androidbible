package yuku.alkitab.base.util

import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.annotation.StyleRes
import yuku.alkitab.debug.R

enum class UiTextRole(
    @get:StyleRes val appearance: Int,
    @get:DimenRes val size: Int,
    @get:DimenRes val lineHeight: Int,
    val weight: Int,
) {
    TITLE(R.style.TextAppearance_Alkitab_Title, R.dimen.ui_type_title_size, R.dimen.ui_type_title_line_height, 500),
    PRIMARY(R.style.TextAppearance_Alkitab_Primary, R.dimen.ui_type_primary_size, R.dimen.ui_type_primary_line_height, 400),
    EMPHASIS(R.style.TextAppearance_Alkitab_Emphasis, R.dimen.ui_type_primary_size, R.dimen.ui_type_primary_line_height, 500),
    SUPPORTING(R.style.TextAppearance_Alkitab_Supporting, R.dimen.ui_type_supporting_size, R.dimen.ui_type_supporting_line_height, 400),
    LABEL(R.style.TextAppearance_Alkitab_Label, R.dimen.ui_type_supporting_size, R.dimen.ui_type_supporting_line_height, 500),
    CAPTION(R.style.TextAppearance_Alkitab_Caption, R.dimen.ui_type_caption_size, R.dimen.ui_type_caption_line_height, 400),
    NUMBER(R.style.TextAppearance_Alkitab_Number, R.dimen.ui_type_number_size, R.dimen.ui_type_number_line_height, 500),
    KEYPAD(R.style.TextAppearance_Alkitab_Keypad, R.dimen.ui_type_keypad_size, R.dimen.ui_type_keypad_line_height, 500),
}

object UiTypography {
    fun nominalSp(resources: Resources, @DimenRes dimension: Int): Float {
        val value = TypedValue()
        resources.getValue(dimension, value, true)
        return TypedValue.complexToFloat(value.data)
    }
}
