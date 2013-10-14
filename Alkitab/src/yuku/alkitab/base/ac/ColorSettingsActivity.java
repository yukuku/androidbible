package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;

import yuku.afw.App;
import yuku.alkitab.debug.R;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;

public class ColorSettingsActivity extends BasePreferenceActivity {
	public static Intent createIntent() {
		return new Intent(App.context, ColorSettingsActivity.class);
	}
	
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.color_settings);
		setTitle(R.string.color_settings);
	}
}
