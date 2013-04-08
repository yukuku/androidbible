package yuku.alkitab.base.ac;

import android.os.Bundle;

import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;

public class SettingsActivity extends BasePreferenceActivity {
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		setTitle(R.string.pengaturan_alkitab);
	}
}
