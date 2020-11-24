package yuku.alkitab.base.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import yuku.alkitab.debug.R;

public class CopyShareFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_copy_share);
    }
}
