package yuku.alkitab.base.ac;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.ActionBar;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.widget.Toolbar;
import android.widget.Toast;
import com.afollestad.materialdialogs.MaterialDialog;
import gnu.trove.set.TLongSet;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Announce;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.widget.ConfigurationWrapper;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.ProgressMarkHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SecretSettingsActivity extends BaseActivity {
	public static final String TAG = SecretSettingsActivity.class.getSimpleName();

	public static class SecretSettingsFragment extends PreferenceFragmentCompat {

		private Preference.OnPreferenceClickListener secret_progress_mark_history_click = preference -> {
			final List<ProgressMark> progressMarks = S.getDb().listAllProgressMarks();

			final CharSequence[] labels = new CharSequence[progressMarks.size()];
			for (int i = 0; i < progressMarks.size(); i++) {
				final ProgressMark progressMark = progressMarks.get(i);
				labels[i] = progressMark.caption + " (preset_id " + progressMark.preset_id + ")";
			}

			new MaterialDialog.Builder(getActivity())
				.items(labels)
				.itemsCallback((dialog, itemView, position, text) -> {
					final List<ProgressMarkHistory> pmhs = S.getDb().listProgressMarkHistoryByPresetId(progressMarks.get(position).preset_id);
					final CharSequence[] items = new CharSequence[pmhs.size()];
					for (int i = 0; i < pmhs.size(); i++) {
						final ProgressMarkHistory pmh = pmhs.get(i);
						items[i] = "'" + pmh.progress_mark_caption + "' " + Sqlitil.toLocaleDateMedium(pmh.createTime) + ": " + S.activeVersion().reference(pmh.ari);
					}
					new MaterialDialog.Builder(getActivity())
						.items(items)
						.show();
				})
				.show();

			return true;
		};

		private Preference.OnPreferenceClickListener secret_version_table_click = preference -> {
			List<String> items = new ArrayList<>();

			for (final MVersionDb mv : S.getDb().listAllVersions()) {
				items.add(
					String.format(Locale.US, "filename=%s preset_name=%s modifyTime=%s active=%s ordering=%s locale=%s shortName=%s longName=%s description=%s",
						mv.filename, mv.preset_name, mv.modifyTime, mv.getActive(), mv.ordering, mv.locale, mv.shortName, mv.longName, mv.description)
				);
			}

			new MaterialDialog.Builder(getActivity())
				.items(items)
				.show();

			return true;
		};

		Preference.OnPreferenceClickListener secret_sync_debug = preference -> {
			startActivity(new Intent(App.context, SecretSyncDebugActivity.class));
			return true;
		};

		Preference.OnPreferenceClickListener secret_reset_read_announcements = preference -> {
			final TLongSet read = Announce.getReadAnnouncementIds();
			Preferences.remove(Prefkey.announce_read_ids);

			Toast.makeText(getActivity(), "Cleared read announcement ids.\n\nPreviously has " + read.size() + " items:\n" + read, Toast.LENGTH_LONG).show();
			return true;
		};

		final Preference.OnPreferenceChangeListener configurationPreferenceChangeListener = (preference, newValue) -> {
			final Handler handler = new Handler();

			// do this after this method returns true
			handler.post(() -> {
				ConfigurationWrapper.notifyConfigurationNeedsUpdate();

				// restart this activity
				final Activity ac = getActivity();
				ac.recreate();
			});
			return true;
		};

		@Override
		public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
			addPreferencesFromResource(R.xml.secret_settings);

			findPreference("secret_progress_mark_history").setOnPreferenceClickListener(secret_progress_mark_history_click);
			findPreference("secret_version_table").setOnPreferenceClickListener(secret_version_table_click);
			findPreference("secret_sync_debug").setOnPreferenceClickListener(secret_sync_debug);
			findPreference("secret_reset_read_announcements").setOnPreferenceClickListener(secret_reset_read_announcements);

			final ListPreference pref_forceFontScale = (ListPreference) findPreference(getString(R.string.pref_forceFontScale_key));
			pref_forceFontScale.setOnPreferenceChangeListener(configurationPreferenceChangeListener);
			SettingsActivity.autoDisplayListPreference(pref_forceFontScale);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_secret_settings);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);
	}

	public static Intent createIntent() {
		return new Intent(App.context, SecretSettingsActivity.class);
	}
}
