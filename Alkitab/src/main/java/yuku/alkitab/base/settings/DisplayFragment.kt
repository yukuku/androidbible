package yuku.alkitab.base.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlin.math.roundToInt
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Highlights
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

        val originalHighlight = CheckBoxPreference(requireContext()).apply {
            isPersistent = false
            setTitle(R.string.highlight_style_original)
        }
        val customizeHighlight = Preference(requireContext()).apply {
            setTitle(R.string.highlight_style_customize)
        }
        fun updateHighlightPreferences() {
            val amount = Highlights.normalizeAdjustment(Preferences.getFloat(Prefkey.highlightColorAdjustment, 1f))
            originalHighlight.isChecked = amount == 0f
            customizeHighlight.summary = when (amount) {
                0f -> getString(R.string.highlight_style_original)
                1f -> getString(R.string.highlight_style_current)
                else -> getString(R.string.highlight_style_custom_value, (amount * 100).roundToInt())
            }
        }
        fun saveHighlightAdjustment(amount: Float) {
            Preferences.setFloat(Prefkey.highlightColorAdjustment, amount)
            App.services.uiDimensions.recalculate()
            AppEvents.emitNeedsRestart()
            updateHighlightPreferences()
        }
        originalHighlight.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            saveHighlightAdjustment(if (value as Boolean) 0f else 1f)
            true
        }
        customizeHighlight.setOnPreferenceClickListener {
            HighlightStyleSheet.show(requireActivity(), ::saveHighlightAdjustment)
            true
        }
        preferenceScreen.addPreference(originalHighlight)
        preferenceScreen.addPreference(customizeHighlight)
        updateHighlightPreferences()

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
