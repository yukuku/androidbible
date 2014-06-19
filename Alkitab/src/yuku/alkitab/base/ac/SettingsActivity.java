package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.debug.R;

public class SettingsActivity extends BasePreferenceActivity {
	public static final int RESULT_openTextAppearance = 1;
	
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		setTitle(R.string.pengaturan_alkitab);
		
		setupTextAppearanceHelper();
	}

	void setupTextAppearanceHelper() {
		String key = getString(R.string.pref_help_text_appearance_key);
		@SuppressWarnings("deprecation") Preference pref = findPreference(key);
		pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override public boolean onPreferenceClick(Preference preference) {
				setResult(RESULT_openTextAppearance);
				finish();
				return true;
			}
		});
	}

	public static Intent createIntent() {
		return new Intent(App.context, SettingsActivity.class);
	}
}
