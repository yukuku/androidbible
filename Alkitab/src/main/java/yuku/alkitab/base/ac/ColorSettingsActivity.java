package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.Toolbar;
import yuku.afw.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

public class ColorSettingsActivity extends BaseActivity {
	private static final String EXTRA_nightMode = "nightMode";

	boolean nightMode;

	public static Intent createIntent(final boolean nightMode) {
		final Intent res = new Intent(App.context, ColorSettingsActivity.class);
		res.putExtra(EXTRA_nightMode, nightMode);
		return res;
	}
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		nightMode = getIntent().getBooleanExtra(EXTRA_nightMode, false);
		setContentView(R.layout.activity_color_settings);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);
	}

	public static class ColorSettingsFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
			final ColorSettingsActivity activity = (ColorSettingsActivity) getActivity();
			final boolean nightMode = activity.nightMode;

			if (nightMode) {
				addPreferencesFromResource(R.xml.color_settings_night);
				activity.setTitle(R.string.color_settings_night_title);
			} else {
				addPreferencesFromResource(R.xml.color_settings);
				activity.setTitle(R.string.color_settings_title);
			}
		}
	}
}
