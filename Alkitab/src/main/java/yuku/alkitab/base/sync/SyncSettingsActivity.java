package yuku.alkitab.base.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.Set;

public class SyncSettingsActivity extends BaseActivity {
	/** Action to broadcast when sync status needs to be refreshed */
	public static final String ACTION_RELOAD = SyncSettingsActivity.class.getName() + ".action.RELOAD";

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sync_settings);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);
	}

	public static class SyncSettingsFragment extends PreferenceFragmentCompat {
		private static final int REQCODE_login = 1;
		private static final int REQCODE_login_initial = 2;

		private Preference pref_syncAccountName;

		final BroadcastReceiver br = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				if (ACTION_RELOAD.equals(intent.getAction())) {
					updateDisplay();
				}
			}
		};

		static final ThreadLocal<DateFormat> lastSyncDateFormat = new ThreadLocal<DateFormat>() {
			@Override
			protected DateFormat initialValue() {
				return android.text.format.DateFormat.getDateFormat(App.context);
			}
		};

		static final ThreadLocal<DateFormat> lastSyncTimeFormat = new ThreadLocal<DateFormat>() {
			@Override
			protected DateFormat initialValue() {
				return android.text.format.DateFormat.getTimeFormat(App.context);
			}
		};

		@Override
		public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
			addPreferencesFromResource(R.xml.sync_settings);

			final String syncAccountName = Preferences.getString(R.string.pref_syncAccountName_key);
			if (syncAccountName == null) {
				startActivityForResult(SyncLoginActivity.createIntent(), REQCODE_login_initial);
			}

			pref_syncAccountName = findPreference(getString(R.string.pref_syncAccountName_key));
			pref_syncAccountName.setOnPreferenceClickListener(pref_syncAccountName_click);
			updateDisplay();

			App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));
		}

		@Override
		public void onDestroy() {
			super.onDestroy();

			App.getLbm().unregisterReceiver(br);
		}


		@SuppressWarnings("deprecation")
		void updateDisplay() {
			final String syncAccountName = Preferences.getString(R.string.pref_syncAccountName_key);
			pref_syncAccountName.setSummary(syncAccountName != null ? syncAccountName : getString(R.string.sync_account_not_selected));

			for (final String syncSetName : SyncShadow.ALL_SYNC_SET_NAMES) {
				final Preference pref = findPreference(Sync.prefkeyForSyncSetEnabled(syncSetName));
				pref.setEnabled(syncAccountName != null);

				if (syncAccountName != null) {
					final Set<String> runningSyncs = SyncAdapter.getRunningSyncs();
					if (runningSyncs.contains(syncSetName)) {
						pref.setSummary(getString(R.string.sync_sync_set_pref_summary_syncing));
					} else {
						final int time = SyncRecorder.getLastSuccessTime(syncSetName);
						if (time == 0) {
							pref.setSummary(getString(R.string.sync_sync_set_pref_summary_never));
						} else {
							final Date date = Sqlitil.toDate(time);
							pref.setSummary(getString(R.string.sync_sync_set_pref_summary_last_synced, lastSyncDateFormat.get().format(date), lastSyncTimeFormat.get().format(date), S.getDb().getRevnoFromSyncShadowBySyncSetName(syncSetName)));
						}
					}
				} else {
					pref.setSummary(null);
				}
			}
		}

		Preference.OnPreferenceClickListener pref_syncAccountName_click = preference -> {
			final String syncAccountName = Preferences.getString(R.string.pref_syncAccountName_key);

			if (syncAccountName == null) {
				startActivityForResult(SyncLoginActivity.createIntent(), REQCODE_login);

			} else { // show logout instead
				new MaterialDialog.Builder(getActivity())
					.content(R.string.sync_logout_warning)
					.positiveText(R.string.ok)
					.onPositive((d, w) -> {
						SyncRecorder.log(SyncRecorder.EventKind.logout_pre, null, "accountName", syncAccountName);

						Preferences.hold();
						Preferences.remove(getString(R.string.pref_syncAccountName_key));
						Preferences.remove(Prefkey.sync_simpleToken);
						Preferences.remove(Prefkey.sync_token_obtained_time);

						for (final String syncSetName : SyncShadow.ALL_SYNC_SET_NAMES) {
							S.getDb().deleteSyncShadowBySyncSetName(syncSetName);
						}

						Preferences.unhold();

						SyncRecorder.removeAllLastSuccessTimes();

						SyncRecorder.log(SyncRecorder.EventKind.logout_post, null, "accountName", syncAccountName);

						updateDisplay();
					})
					.negativeText(R.string.cancel)
					.show();
			}
			return true;
		};

		@Override
		public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
			if (requestCode == REQCODE_login || requestCode == REQCODE_login_initial) {
				if (resultCode == RESULT_OK) {
					final SyncLoginActivity.Result result = SyncLoginActivity.obtainResult(data);

					// Success!
					SyncRecorder.log(SyncRecorder.EventKind.login_success_pre, null, "accountName", result.accountName);

					Preferences.hold();
					Preferences.setString(App.context.getString(R.string.pref_syncAccountName_key), result.accountName);
					Preferences.setString(Prefkey.sync_simpleToken, result.simpleToken);
					Preferences.setInt(Prefkey.sync_token_obtained_time, Sqlitil.nowDateTime());
					Preferences.unhold();

					SyncRecorder.log(SyncRecorder.EventKind.login_success_post, null, "accountName", result.accountName);

					// force sync immediately after login
					Sync.forceSyncNow();

					updateDisplay();
				} else if (resultCode == RESULT_CANCELED) {
					if (requestCode == REQCODE_login_initial) {
						getActivity().finish();
					}
				}
				return;
			}

			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sync_settings, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		final int itemId = item.getItemId();
		switch (itemId) {
			case R.id.menuSyncNow:
				Sync.forceSyncNow();
				return true;
			case R.id.menuSyncLog:
				startActivity(SyncLogActivity.createIntent());
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

    public static Intent createIntent() {
        return new Intent(App.context, SyncSettingsActivity.class);
    }
}
