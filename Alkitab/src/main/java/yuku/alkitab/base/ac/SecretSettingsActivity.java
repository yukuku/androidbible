package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.widget.Toast;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import gnu.trove.set.TLongSet;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Announce;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.ProgressMarkHistory;

import java.util.ArrayList;
import java.util.List;

public class SecretSettingsActivity extends BasePreferenceActivity {
	public static final String TAG = SecretSettingsActivity.class.getSimpleName();

	private Preference.OnPreferenceClickListener secret_progress_mark_history_click = preference -> {
		final List<ProgressMark> progressMarks = S.getDb().listAllProgressMarks();

		final String[] labels = new String[progressMarks.size()];
		for (int i = 0; i < progressMarks.size(); i++) {
			final ProgressMark progressMark = progressMarks.get(i);
			labels[i] = progressMark.caption + " (preset_id " + progressMark.preset_id + ")";
		}

		new AlertDialogWrapper.Builder(SecretSettingsActivity.this)
			.setItems(labels, (dialog, which) -> {
				final List<ProgressMarkHistory> pmhs = S.getDb().listProgressMarkHistoryByPresetId(progressMarks.get(which).preset_id);
				final String[] items = new String[pmhs.size()];
				for (int i = 0; i < pmhs.size(); i++) {
					final ProgressMarkHistory pmh = pmhs.get(i);
					items[i] = "'" + pmh.progress_mark_caption + "' " + Sqlitil.toLocaleDateMedium(pmh.createTime) + ": " + S.activeVersion.reference(pmh.ari);
				}
				new AlertDialogWrapper.Builder(SecretSettingsActivity.this)
					.setItems(items, null)
					.show();
			})
			.show();

		return true;
	};

	private Preference.OnPreferenceClickListener secret_version_table_click = preference -> {
		List<String> items = new ArrayList<>();

		for (final MVersionDb mv : S.getDb().listAllVersions()) {
			items.add(
				String.format("filename=%s preset_name=%s modifyTime=%s active=%s ordering=%s locale=%s shortName=%s longName=%s description=%s",
					mv.filename, mv.preset_name, mv.modifyTime, mv.getActive(), mv.ordering, mv.locale, mv.shortName, mv.longName, mv.description)
			);
		}

		new AlertDialogWrapper.Builder(SecretSettingsActivity.this)
			.setItems(items.toArray(new String[items.size()]), null)
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

		Toast.makeText(this, "Cleared read announcement ids.\n\nPreviously has " + read.size() + " items:\n" + read, Toast.LENGTH_LONG).show();
		return true;
	};

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.secret_settings);

		findPreference("secret_progress_mark_history").setOnPreferenceClickListener(secret_progress_mark_history_click);
		findPreference("secret_version_table").setOnPreferenceClickListener(secret_version_table_click);
		findPreference("secret_sync_debug").setOnPreferenceClickListener(secret_sync_debug);
		findPreference("secret_reset_read_announcements").setOnPreferenceClickListener(secret_reset_read_announcements);
	}

	public static Intent createIntent() {
		return new Intent(App.context, SecretSettingsActivity.class);
	}
}
