package yuku.alkitab.base.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import yuku.alkitab.base.compose.colorpicker.ColorPickerDialog
import yuku.alkitab.debug.R

class ColorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var value: Int = 0

    init {
        widgetLayoutResource = R.layout.color_pref_widget
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val swatch = holder.itemView.findViewById<View>(R.id.color_pref_widget_swatch) ?: return
        val density = context.resources.displayMetrics.density
        swatch.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 3f * density
            setColor(value or 0xff000000.toInt())
            setStroke((1f * density + 0.5f).toInt(), 0x33000000)
        }
    }

    override fun onClick() {
        ColorPickerDialog.show(context, value) { color ->
            if (!callChangeListener(color)) return@show
            value = color
            persistInt(value)
            notifyChanged()
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, 0)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt((defaultValue as? Int) ?: value)
    }
}
