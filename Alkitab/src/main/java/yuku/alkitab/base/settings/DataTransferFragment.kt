package yuku.alkitab.base.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import yuku.alkitab.base.App
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.datatransfer.ui.DataTransferActivity
import yuku.alkitab.debug.R

class DataTransferFragment : PreferenceFragmentCompat() {

    val startActivityRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_data_transfer)

        findPreference<Preference>(getString(R.string.pref_data_transfer_export_key))?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivityRequest.launch(DataTransferActivity.createIntent(requireActivity(), DataTransferActivity.Mode.export))
            true
        }
        findPreference<Preference>(getString(R.string.pref_data_transfer_import_key))?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivityRequest.launch(DataTransferActivity.createIntent(requireActivity(), DataTransferActivity.Mode.import))
            true
        }
    }
}
