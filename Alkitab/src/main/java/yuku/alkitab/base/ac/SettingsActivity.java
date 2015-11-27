package yuku.alkitab.base.ac;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.sync.SyncSettingsActivity;
import yuku.alkitab.base.util.ChangeLanguageHelper;
import yuku.alkitab.base.util.OtherAppIntegration;
import yuku.alkitab.base.widget.VerseItem;
import yuku.alkitab.debug.R;

public class SettingsActivity extends BaseActivity {
	private static final String EXTRA_subClassName = "subClassName";

	static class Header {
		int titleResId;
		Class<? extends PreferenceFragmentCompat> clazz;
		Intent clickIntent;

		public Header(final int titleResId, final Class<? extends PreferenceFragmentCompat> clazz, final Intent clickIntent) {
			this.titleResId = titleResId;
			this.clazz = clazz;
			this.clickIntent = clickIntent;
		}
	}

	static Header[] headers = {
		new Header(R.string.pref_sync_title, null, new Intent(App.context, SyncSettingsActivity.class)),
		new Header(R.string.pref_penampilan_layar, DisplayFragment.class, null),
		new Header(R.string.pref_penggunaan, UsageFragment.class, null),
		new Header(R.string.pref_copy_share, CopyShareFragment.class, null),
	};

	RecyclerView lsHeaders;
	HeadersAdapter headersAdapter;

	public static Intent createIntent() {
		return new Intent(App.context, SettingsActivity.class);
	}

	private static Intent createSubIntent(Class<? extends PreferenceFragmentCompat> subClass) {
		return new Intent(App.context, SettingsActivity.class)
			.putExtra(EXTRA_subClassName, subClass.getName());
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.enableNonToolbarUpButton();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		final String subClassName = getIntent().getStringExtra(EXTRA_subClassName);
		if (subClassName == null) {
			lsHeaders = V.get(this, R.id.lsHeaders);
			lsHeaders.setLayoutManager(new LinearLayoutManager(this));
			lsHeaders.setAdapter(headersAdapter = new HeadersAdapter());
		} else {
			final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			ft.replace(R.id.fragment_container, Fragment.instantiate(this, subClassName), subClassName);
			ft.commit();
		}
	}

	static class VH extends RecyclerView.ViewHolder {
		public VH(final View itemView) {
			super(itemView);
		}
	}

	class HeadersAdapter extends RecyclerView.Adapter<VH> {
		final TypedValue tv = new TypedValue();

		@Override
		public VH onCreateViewHolder(final ViewGroup parent, final int viewType) {
			final View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
			getTheme().resolveAttribute(R.attr.selectableItemBackground, tv, true);
			v.setBackgroundResource(tv.resourceId);
			return new VH(v);
		}

		@Override
		public void onBindViewHolder(final VH holder, final int position) {
			final Header header = headers[position];
			((TextView) holder.itemView).setText(header.titleResId);
			holder.itemView.setOnClickListener(v -> {
				if (header.clickIntent != null) {
					startActivity(header.clickIntent);
				} else if (header.clazz != null) {
					startActivity(createSubIntent(header.clazz));
				}
			});
		}

		@Override
		public int getItemCount() {
			return headers.length;
		}
	}

	public static class DisplayFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			addPreferencesFromResource(R.xml.settings_display);

			final ListPreference pref_language = (ListPreference) findPreference(getString(R.string.pref_language_key));
			pref_language.setOnPreferenceChangeListener((preference, newValue) -> {
				final Handler handler = new Handler();

				// do this after this method returns true
				handler.post(() -> {
					App.updateConfigurationWithPreferencesLocale();
					ChangeLanguageHelper.notifyLocaleChanged();

					// restart this activity
					getActivity().recreate();
				});
				return true;
			});
			autoDisplayListPreference(pref_language);


			final CheckBoxPreference pref_bottomToolbarOnText = (CheckBoxPreference) findPreference(getString(R.string.pref_bottomToolbarOnText_key));
			pref_bottomToolbarOnText.setOnPreferenceChangeListener((preference, newValue) -> {
				final Handler handler = new Handler();

				// do this after this method returns true
				handler.post(() -> App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_NEEDS_RESTART)));
				return true;
			});

			final Preference pref_selectedVerseBgColor = findPreference(getString(R.string.pref_selectedVerseBgColor_key));
			pref_selectedVerseBgColor.setOnPreferenceChangeListener((preference, newValue) -> {
				VerseItem.invalidateSelectedVersePaints();
				return true;
			});

			// show textPadding preference only when there is nonzero side padding on this configuration
			if (getResources().getDimensionPixelOffset(R.dimen.text_side_padding) == 0) {
				final Preference preference = findPreference(getString(R.string.pref_textPadding_key));
				getPreferenceScreen().removePreference(preference);
			}
		}
	}

	public static class UsageFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
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

			final CheckBoxPreference pref_autoDictionaryAnalyze = (CheckBoxPreference) findPreference(getString(R.string.pref_autoDictionaryAnalyze_key));
			pref_autoDictionaryAnalyze.setOnPreferenceChangeListener((preference, newValue) -> {
				if (((boolean) newValue)) {
					if (!OtherAppIntegration.hasIntegratedDictionaryApp()) {
						OtherAppIntegration.askToInstallDictionary(getActivity());
						return false;
					}
				}
				return true;
			});

			// only show dictionary auto-lookup when enabled in app_config
			if (!AppConfig.get().menuDictionary) {
				getPreferenceScreen().removePreference(findPreference(getString(R.string.pref_autoDictionaryAnalyze_key)));
			}
		}
	}

	public static class CopyShareFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
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
