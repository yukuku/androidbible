package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.debug.R;

public class SettingsActivity extends BasePreferenceActivity {
	public static Intent createIntent() {
		return new Intent(App.context, SettingsActivity.class);
	}

	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		setTitle(R.string.pengaturan_alkitab);
	}
}
