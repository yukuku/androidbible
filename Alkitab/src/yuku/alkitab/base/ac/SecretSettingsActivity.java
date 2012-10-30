package yuku.alkitab.base.ac;

import android.os.Bundle;

import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;

public class SecretSettingsActivity extends BasePreferenceActivity {
	public static final String TAG = SecretSettingsActivity.class.getSimpleName();
	
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.secret_settings);
	}
}
