package yuku.alkitab.base.ac;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.View;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.sync.SyncSettingsActivity;
import yuku.alkitab.debug.R;

import java.util.List;

import static yuku.alkitab.base.util.Literals.List;

public class SettingsActivity extends BasePreferenceActivity {
	public List<String> VALID_FRAGMENT_NAMES = List(
		DisplayFragment.class.getName(),
		UsageFragment.class.getName(),
		CopyShareFragment.class.getName()
	);
	Header firstHeaderWithFragment;

	public static Intent createIntent() {
		return new Intent(App.context, SettingsActivity.class);
	}

	@Override
	public void onBuildHeaders(final List<Header> target) {
		super.onBuildHeaders(target);

		loadHeadersFromResource(R.xml.settings_headers, target);

		// look for first header with a fragment
		for (final Header header : target) {
			if (header.fragment != null) {
				firstHeaderWithFragment = header;
				break;
			}
		}

		for (final Header header : target) {
			if (header.id == R.id.header_id_sync) {
				header.intent = new Intent(App.context, SyncSettingsActivity.class);
			}
		}
	}

	@Override
	public Header onGetInitialHeader() {
		return firstHeaderWithFragment;
	}

	@Override
	protected boolean isValidFragment(final String fragmentName) {
		return VALID_FRAGMENT_NAMES.contains(fragmentName);
	}

	public static class DisplayFragment extends PreferenceFragment {
		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.settings_display);

			final ListPreference pref_language = (ListPreference) findPreference(getString(R.string.pref_language_key));
			pref_language.setOnPreferenceChangeListener((preference, newValue) -> {
				final Handler handler = new Handler();

				// do this after this method returns true
				handler.post(App::updateConfigurationWithPreferencesLocale);
				return true;
			});
			autoDisplayListPreference(pref_language);

			// show textPadding preference only when there is nonzero side padding on this configuration
			if (getResources().getDimensionPixelOffset(R.dimen.text_side_padding) == 0) {
				final Preference preference = findPreference(getString(R.string.pref_textPadding_key));
				getPreferenceScreen().removePreference(preference);
			}
		}
	}

	public static class UsageFragment extends PreferenceFragment {
		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.settings_usage);

			final ListPreference pref_volumeButtonNavigation = (ListPreference) findPreference(getString(R.string.pref_volumeButtonNavigation_key));
			autoDisplayListPreference(pref_volumeButtonNavigation);

			final CheckBoxPreference pref_showHiddenVersion = (CheckBoxPreference) findPreference(getString(R.string.pref_showHiddenVersion_key));
			pref_showHiddenVersion.setOnPreferenceChangeListener((preference, newValue) -> {
				final boolean value = (boolean) newValue;

				if (value) {
					new AlertDialogWrapper.Builder(getActivity())
						.setMessage(R.string.show_hidden_version_warning)
						.setNegativeButton(R.string.cancel, null)
						.setPositiveButton(R.string.ok, (dialog, which) -> pref_showHiddenVersion.setChecked(true))
						.show();
					return false;
				}

				return true;
			});

			// only show dictionary auto-lookup when enabled in app_config
			if (!AppConfig.get().menuDictionary) {
				getPreferenceScreen().removePreference(findPreference(getString(R.string.pref_autoDictionaryAnalyze_key)));
			}
		}
	}

	public static class CopyShareFragment extends PreferenceFragment {
		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.settings_copy_share);
		}
	}

	static void autoDisplayListPreference(final ListPreference pref) {
		final CharSequence label = pref.getEntry();
		if (label != null) {
			pref.setSummary(label);
		}

		final Preference.OnPreferenceChangeListener originalChangeListener = pref.getOnPreferenceChangeListener();
		pref.setOnPreferenceChangeListener((preference, newValue) -> {
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
		});
	}

	public static void setPaddingBasedOnPreferences(final View view) {
		final Resources r = App.context.getResources();
		if (Preferences.getBoolean(r.getString(R.string.pref_textPadding_key), r.getBoolean(R.bool.pref_textPadding_default))) {
			final int top = r.getDimensionPixelOffset(R.dimen.text_top_padding);
			final int bottom = r.getDimensionPixelOffset(R.dimen.text_bottom_padding);
			final int side = r.getDimensionPixelOffset(R.dimen.text_side_padding);
			view.setPadding(side, top, side, bottom);
		} else {
			final int no = r.getDimensionPixelOffset(R.dimen.text_nopadding);
			view.setPadding(no, no, no, no);
		}
	}
}
