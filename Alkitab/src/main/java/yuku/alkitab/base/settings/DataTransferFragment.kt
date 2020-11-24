package yuku.alkitab.base.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import yuku.alkitab.debug.R

class DataTransferFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String) {
        addPreferencesFromResource(R.xml.settings_data_transfer)

        findPreference(getString(R.string.pref_data_transfer_export_key)).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            true
        }
        findPreference(getString(R.string.pref_data_transfer_import_key)).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            true
        }
    }
}
