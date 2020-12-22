package yuku.alkitab.base.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import yuku.alkitab.datatransfer.ui.DataTransferActivity
import yuku.alkitab.debug.R

class DataTransferFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_data_transfer)

        findPreference(getString(R.string.pref_data_transfer_export_key)).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activity?.startActivity(DataTransferActivity.createIntent(requireActivity(), DataTransferActivity.Mode.export))
            true
        }
        findPreference(getString(R.string.pref_data_transfer_import_key)).onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activity?.startActivity(DataTransferActivity.createIntent(requireActivity(), DataTransferActivity.Mode.import))
            true
        }
    }
}
