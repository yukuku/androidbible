package yuku.alkitab.base.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import kotlin.math.roundToInt
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.debug.R

class DisplayFragment : PreferenceFragmentCompat() {
    private val configurationPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
        val handler = Handler(Looper.getMainLooper())

        // do this after this method returns true
        handler.post {
            ConfigurationWrapper.notifyConfigurationNeedsUpdate()

            // restart this activity
            activity?.recreate()
        }

        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_display)

        val textAdjustment = SeekBarPreference(requireContext()).apply {
            key = Prefkey.textColorAdjustment.name
            isPersistent = false
            setTitle(R.string.text_color_adjustment)
            min = 0
            max = 100
            seekBarIncrement = 1
            showSeekBarValue = true
            updatesContinuously = true
            value = (TextColorUtil.normalizeAdjustment(Preferences.getFloat(Prefkey.textColorAdjustment, 1f)) * 100).roundToInt()
        }
        val contrastFormat = java.text.NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply {
            maximumFractionDigits = 3
        }
        fun updateAdjustmentSummary(percent: Int) {
            val value = if (percent == 0) "0%" else "$percent% · ${contrastFormat.format(TextColorUtil.targetContrast(percent / 100f))}:1"
            textAdjustment.summary = "$value\n${getString(R.string.text_color_adjustment_explanation)}"
        }
        updateAdjustmentSummary(textAdjustment.value)
        textAdjustment.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            val percent = value as Int
            Preferences.setFloat(Prefkey.textColorAdjustment, percent / 100f)
            updateAdjustmentSummary(percent)
            App.services.uiDimensions.recalculate()
            AppEvents.emitNeedsRestart()
            true
        }
        preferenceScreen.addPreference(textAdjustment)

        findPreference<ListPreference>(getString(R.string.pref_currentReadingDisplay_key))?.let {
            SettingsActivity.autoDisplayListPreference(it)
        }

        val pref_language = findPreference<ListPreference>(getString(R.string.pref_language_key))
        if (pref_language != null) {
            pref_language.onPreferenceChangeListener = configurationPreferenceChangeListener
            SettingsActivity.autoDisplayListPreference(pref_language)
        }

        val pref_bottomToolbarOnText = findPreference<CheckBoxPreference>(getString(R.string.pref_bottomToolbarOnText_key))
        pref_bottomToolbarOnText?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            // The preference is stored only after this listener returns true.
            Handler(Looper.getMainLooper()).post {
                (activity as? BaseActivity)?.refreshToolbarPlacement()
                AppEvents.emitNeedsRestart()
            }
            true
        }
    }
}
