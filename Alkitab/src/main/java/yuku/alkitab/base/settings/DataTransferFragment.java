package yuku.alkitab.base.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import yuku.alkitab.debug.R;

public class DataTransferFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_data_transfer);

        findPreference(getString(R.string.pref_data_transfer_export_key)).setOnPreferenceClickListener(pref -> {
            return true;
        });

        findPreference(getString(R.string.pref_data_transfer_import_key)).setOnPreferenceClickListener(pref -> {
            return true;
        });
    }
}
