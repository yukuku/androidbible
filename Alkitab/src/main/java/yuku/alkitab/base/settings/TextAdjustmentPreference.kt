package yuku.alkitab.base.settings

import android.content.Context
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.debug.R

class TextAdjustmentPreference(context: Context) : SeekBarPreference(context) {
    private var valueView: TextView? = null
    private val contrastFormat = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        roundingMode = RoundingMode.HALF_UP
    }

    init {
        layoutResource = R.layout.preference_text_adjustment
        showSeekBarValue = false
    }

    fun valueLabel(percent: Int): String {
        if (percent == 0) return "0%"
        val ratio = contrastFormat.format(BigDecimal.valueOf(TextColorUtil.targetContrast(percent / 100f)))
        return context.getString(R.string.text_color_adjustment_value, percent, ratio)
    }

    fun updateValueLabel(percent: Int) {
        valueView?.text = valueLabel(percent)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        valueView = holder.findViewById(R.id.text_adjustment_value) as TextView
        updateValueLabel(value)
    }
}
