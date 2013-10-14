package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import yuku.alkitab.debug.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.base.model.ProgressMark;
import yuku.alkitab.base.model.ProgressMarkHistory;
import yuku.alkitab.base.util.Sqlitil;

import java.util.List;

public class SecretSettingsActivity extends BasePreferenceActivity {
	public static final String TAG = SecretSettingsActivity.class.getSimpleName();

	private Preference.OnPreferenceClickListener secret_progress_mark_history_click = new Preference.OnPreferenceClickListener() {
		@Override
		public boolean onPreferenceClick(final Preference preference) {
			final List<ProgressMark> progressMarks = S.getDb().listAllProgressMarks();

			final String[] items = new String[progressMarks.size()];
			for (int i = 0; i < progressMarks.size(); i++) {
				final ProgressMark progressMark = progressMarks.get(i);
				items[i] = progressMark.caption + " (preset_id " + progressMark.preset_id + ")";
			}

			new AlertDialog.Builder(SecretSettingsActivity.this)
			.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					final List<ProgressMarkHistory> pmhs = S.getDb().listProgressMarkHistoryByPresetId(progressMarks.get(which).preset_id);
					final String[] items = new String[pmhs.size()];
					for (int i = 0; i < pmhs.size(); i++) {
						final ProgressMarkHistory pmh = pmhs.get(i);
						items[i] = "'" + pmh.progress_mark_caption + "' " + Sqlitil.toLocaleDateMedium(pmh.createTime) + ": " + S.activeVersion.reference(pmh.ari);
					}
					new AlertDialog.Builder(SecretSettingsActivity.this)
					.setItems(items, null)
					.show();
				}
			})
			.show();

			return true;
		}
	};

	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.secret_settings);

		findPreference("secret_progress_mark_history").setOnPreferenceClickListener(secret_progress_mark_history_click);
	}
}
