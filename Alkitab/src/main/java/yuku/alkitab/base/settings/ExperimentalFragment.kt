package yuku.alkitab.base.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import yuku.alkitab.debug.R

class ExperimentalFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_experimental)
    }
}
