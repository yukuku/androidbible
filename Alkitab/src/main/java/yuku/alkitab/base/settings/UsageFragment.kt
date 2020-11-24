package yuku.alkitab.base.settings

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.util.OtherAppIntegration
import yuku.alkitab.debug.R

class UsageFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle, rootKey: String) {
        addPreferencesFromResource(R.xml.settings_usage)

        val pref_volumeButtonNavigation = findPreference(getString(R.string.pref_volumeButtonNavigation_key)) as ListPreference
        SettingsActivity.autoDisplayListPreference(pref_volumeButtonNavigation)

        val pref_autoDictionaryAnalyze = findPreference(getString(R.string.pref_autoDictionaryAnalyze_key)) as CheckBoxPreference
        pref_autoDictionaryAnalyze.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                if (!OtherAppIntegration.hasIntegratedDictionaryApp()) {
                    OtherAppIntegration.askToInstallDictionary(activity)
                    return@OnPreferenceChangeListener false
                }
            }
            true
        }

        // only show dictionary auto-lookup when enabled in app_config
        if (!AppConfig.get().menuDictionary) {
            preferenceScreen.removePreference(findPreference(getString(R.string.pref_autoDictionaryAnalyze_key)))
        }
    }
}
