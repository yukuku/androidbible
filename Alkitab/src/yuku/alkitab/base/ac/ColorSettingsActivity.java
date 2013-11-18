package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import yuku.afw.App;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.debug.R;

public class ColorSettingsActivity extends BasePreferenceActivity {
	private static final String EXTRA_nightMode = "nightMode";

	public static Intent createIntent(final boolean nightMode) {
		final Intent res = new Intent(App.context, ColorSettingsActivity.class);
		res.putExtra(EXTRA_nightMode, nightMode);
		return res;
	}
	
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final boolean nightMode = getIntent().getBooleanExtra(EXTRA_nightMode, false);

		if (nightMode) {
			addPreferencesFromResource(R.xml.color_settings_night);
			setTitle(R.string.color_settings_night_title);
		} else {
			addPreferencesFromResource(R.xml.color_settings);
			setTitle(R.string.color_settings_title);
		}
	}
}
