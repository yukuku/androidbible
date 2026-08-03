package yuku.alkitab.base.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.debug.R

class ExperimentalFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_experimental)

        // The reader builds its verse content view once at creation, so this
        // flag only takes effect after the activity is restarted.
        val prefUseComposeVerseItem = findPreference<CheckBoxPreference>(getString(R.string.pref_useComposeVerseItem_key))
        prefUseComposeVerseItem?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            // do this after this method returns true
            Handler(Looper.getMainLooper()).post { AppEvents.emitNeedsRestart() }
            true
        }
    }
}
