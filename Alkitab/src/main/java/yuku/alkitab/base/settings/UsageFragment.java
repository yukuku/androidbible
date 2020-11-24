package yuku.alkitab.base.settings;

import android.os.Bundle;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.util.OtherAppIntegration;
import yuku.alkitab.debug.R;

public class UsageFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_usage);

        final ListPreference pref_volumeButtonNavigation = (ListPreference) findPreference(getString(R.string.pref_volumeButtonNavigation_key));
        SettingsActivity.autoDisplayListPreference(pref_volumeButtonNavigation);

        final CheckBoxPreference pref_autoDictionaryAnalyze = (CheckBoxPreference) findPreference(getString(R.string.pref_autoDictionaryAnalyze_key));
        pref_autoDictionaryAnalyze.setOnPreferenceChangeListener((preference, newValue) -> {
            if (((boolean) newValue)) {
                if (!OtherAppIntegration.hasIntegratedDictionaryApp()) {
                    OtherAppIntegration.askToInstallDictionary(getActivity());
                    return false;
                }
            }
            return true;
        });

        // only show dictionary auto-lookup when enabled in app_config
        if (!AppConfig.get().menuDictionary) {
            getPreferenceScreen().removePreference(findPreference(getString(R.string.pref_autoDictionaryAnalyze_key)));
        }
    }
}
