package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.debug.R;

public class SettingsActivity extends BasePreferenceActivity {
	public static Intent createIntent() {
		return new Intent(App.context, SettingsActivity.class);
	}

	final Handler handler = new Handler();

	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		setTitle(R.string.pengaturan_alkitab);

		final ListPreference pref_language = (ListPreference) findPreference(getString(R.string.pref_language_key));
		pref_language.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				// do this after this method returns true
				handler.post(new Runnable() {
					@Override
					public void run() {
						App.updateConfigurationWithPreferencesLocale();
					}
				});
				return true;
			}
		});
		autoDisplayListPreference(pref_language);

		final ListPreference pref_volumeButtonNavigation = (ListPreference) findPreference(getString(R.string.pref_volumeButtonNavigation_key));
		autoDisplayListPreference(pref_volumeButtonNavigation);

		final CheckBoxPreference pref_showHiddenVersion = (CheckBoxPreference) findPreference(getString(R.string.pref_showHiddenVersion_key));
		pref_showHiddenVersion.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				final boolean value = (boolean) newValue;

				if (value) {
					new AlertDialog.Builder(SettingsActivity.this)
						.setMessage(R.string.show_hidden_version_warning)
						.setNegativeButton(R.string.cancel, null)
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog, final int which) {
								pref_showHiddenVersion.setChecked(true);
							}
						})
						.show();
					return false;
				}

				return true;
			}
		});
	}

	static void autoDisplayListPreference(final ListPreference pref) {
		final CharSequence label = pref.getEntry();
		if (label != null) {
			pref.setSummary(label);
		}

		final Preference.OnPreferenceChangeListener originalChangeListener = pref.getOnPreferenceChangeListener();
		pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				final boolean changed;

				if (originalChangeListener != null) {
					changed = originalChangeListener.onPreferenceChange(preference, newValue);
				} else {
					changed = true;
				}

				if (changed) {
					final int index = pref.findIndexOfValue((String) newValue);
					if (index >= 0) {
						pref.setSummary(pref.getEntries()[index]);
					}
				}

				return changed;
			}
		});
	}
}
