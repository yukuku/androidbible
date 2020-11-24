package yuku.alkitab.base.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.widget.ConfigurationWrapper;
import yuku.alkitab.debug.R;

public class DisplayFragment extends PreferenceFragmentCompat {

    final Preference.OnPreferenceChangeListener configurationPreferenceChangeListener = (preference, newValue) -> {
        final Handler handler = new Handler();

        // do this after this method returns true
        handler.post(() -> {
            ConfigurationWrapper.notifyConfigurationNeedsUpdate();

            // restart this activity
            final Activity ac = getActivity();
            ac.recreate();
        });
        return true;
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_display);

        final ListPreference pref_language = (ListPreference) findPreference(getString(R.string.pref_language_key));
        pref_language.setOnPreferenceChangeListener(configurationPreferenceChangeListener);
        SettingsActivity.autoDisplayListPreference(pref_language);

        final CheckBoxPreference pref_bottomToolbarOnText = (CheckBoxPreference) findPreference(getString(R.string.pref_bottomToolbarOnText_key));
        pref_bottomToolbarOnText.setOnPreferenceChangeListener((preference, newValue) -> {
            final Handler handler = new Handler();

            // do this after this method returns true
            handler.post(() -> App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_NEEDS_RESTART)));
            return true;
        });

        // show textPadding preference only when there is nonzero side padding on this configuration
        if (getResources().getDimensionPixelOffset(R.dimen.text_side_padding) == 0) {
            final Preference preference = findPreference(getString(R.string.pref_textPadding_key));
            getPreferenceScreen().removePreference(preference);
        }
    }
}
