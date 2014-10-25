package yuku.alkitab.base.sync;

import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static yuku.alkitab.base.util.Literals.Array;

public class SyncSettingsActivity extends BasePreferenceActivity {
	static final String TAG = SyncSettingsActivity.class.getSimpleName();

	public static final int REQCODE_accountChooser = 1;

	private static final int REQUEST_RECOVER_FROM_AUTH_ERROR = 101;
	private static final int REQUEST_RECOVER_FROM_PLAY_SERVICES_ERROR = 102;

	/** Action to broadcast when sync status needs to be refreshed */
	public static final String ACTION_RELOAD = SyncSettingsActivity.class.getName() + ".action.RELOAD";

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

	final Target profilePictureTarget = new Target() {
		@Override
		public void onBitmapLoaded(final Bitmap bitmap, final Picasso.LoadedFrom from) {
			if (pref_syncAccountName != null && Preferences.getString(getString(R.string.pref_syncAccountName_key)) != null) {
				pref_syncAccountName.setIcon(new BitmapDrawable(getResources(), circled(bitmap)));
			}
		}

		Bitmap circled(Bitmap bitmap) {
			final int w = bitmap.getWidth();
			final int h = bitmap.getHeight();
			final Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			final Canvas canvas = new Canvas(output);
			final Paint paint = new Paint();
			paint.setColor(0xffffffff);
			paint.setAntiAlias(true);
			canvas.drawColor(0x0);
			canvas.drawCircle(w / 2, h / 2, w / 2, paint);
			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
			final Rect rect = new Rect(0, 0, w, h);
			canvas.drawBitmap(bitmap, rect, rect, paint);
			return output;
		}

		@Override
		public void onBitmapFailed(final Drawable errorDrawable) {}

		@Override
		public void onPrepareLoad(final Drawable placeHolderDrawable) {}
	};

	@Override
	@SuppressWarnings("deprecation")
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.sync_settings);

		pref_syncAccountName = findPreference(getString(R.string.pref_syncAccountName_key));
		pref_syncAccountName.setOnPreferenceClickListener(pref_syncAccountName_click);
		updateDisplay();

		App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		App.getLbm().unregisterReceiver(br);
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

	Preference.OnPreferenceClickListener pref_syncAccountName_click = preference -> {
		final String syncAccountName = Preferences.getString(getString(R.string.pref_syncAccountName_key));

		if (syncAccountName == null) {
			final Intent accountChooser = AccountManager.newChooseAccountIntent(null, null, Array("com.google"), true, null, null, null, null);
			startActivityForResult(accountChooser, REQCODE_accountChooser);

		} else { // show logout instead
			new AlertDialog.Builder(this)
				.setMessage(R.string.sync_logout_warning)
				.setPositiveButton(R.string.ok, (d, w) -> {
					SyncRecorder.log(SyncRecorder.EventKind.logout_pre, null, "accountName", syncAccountName);

					Preferences.hold();
					Preferences.remove(getString(R.string.pref_syncAccountName_key));
					Preferences.remove(Prefkey.sync_simpleToken);
					Preferences.remove(Prefkey.sync_token_obtained_time);
					Preferences.remove(Prefkey.sync_profile_picture_url);

					for (final String syncSetName : SyncShadow.ALL_SYNC_SETS) {
						S.getDb().deleteSyncShadowBySyncSetName(syncSetName);
					}

					Preferences.unhold();

					SyncRecorder.removeAllLastSuccessTimes();

					SyncRecorder.log(SyncRecorder.EventKind.logout_post, null, "accountName", syncAccountName);

					updateDisplay();
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
		}
		return true;
	};

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_accountChooser) {
			if (resultCode == RESULT_OK) {
				final String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				final String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);

				if ("com.google".equals(accountType)) {
					getTokenAndLogin(accountName);
				}
			}
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	void getTokenAndLogin(final String accountName) {
		final AtomicBoolean cancelRequested = new AtomicBoolean();
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.sync_logging_in), true, true, (dialog) -> cancelRequested.set(true));

		new Thread(() -> {
			try {
				Log.d(TAG, "Starting background auth for " + accountName);
				final String id_token = GoogleAuthUtil.getToken(App.context, accountName, "audience:server:client_id:" + Sync.CLIENT_ID, null);

				if (cancelRequested.get()) {
					return;
				}

				// Save auth token.
				Log.d(TAG, "Sending token to server for log in...");
				SyncRecorder.log(SyncRecorder.EventKind.login_attempt, null, "accountName", accountName);
				final Sync.LoginResult loginResult = Sync.login(id_token);

				if (!loginResult.success) {
					SyncRecorder.log(SyncRecorder.EventKind.login_failed, null, "accountName", accountName, "message", loginResult.message);
					runOnUiThread(() -> new AlertDialog.Builder(this)
						.setMessage(getString(R.string.sync_login_failed_with_reason, loginResult.message))
						.setPositiveButton(R.string.ok, null)
						.show());
					return;
				}

				// send GCM registration id, if we already have it.
				final String registration_id = Gcm.renewGcmRegistrationIdIfNeeded(Sync::notifyNewGcmRegistrationId);
				if (registration_id != null) {
					final boolean ok = Sync.sendGcmRegistrationId(loginResult.simpleToken, registration_id);
					if (!ok) {
						SyncRecorder.log(SyncRecorder.EventKind.login_gcm_sending_failed, null, "accountName", accountName);
						runOnUiThread(() -> new AlertDialog.Builder(this)
							.setMessage(getString(R.string.sync_login_failed_with_reason, "Could not send GCM registration id"))
							.setPositiveButton(R.string.ok, null)
							.show());
						return;
					}
				} else {
					// if not, ignore. Later eventually we will have it.
					SyncRecorder.log(SyncRecorder.EventKind.login_gcm_not_possessed_yet, null, "accountName", accountName);
				}

				// Success!
				SyncRecorder.log(SyncRecorder.EventKind.login_success_pre, null, "accountName", accountName, "profile_picture_url", loginResult.profile_picture_url);

				Preferences.hold();
				Preferences.setString(App.context.getString(R.string.pref_syncAccountName_key), loginResult.user_email);
				Preferences.setString(Prefkey.sync_profile_picture_url, loginResult.profile_picture_url);
				Preferences.setString(Prefkey.sync_simpleToken, loginResult.simpleToken);
				Preferences.setInt(Prefkey.sync_token_obtained_time, Sqlitil.nowDateTime());
				Preferences.unhold();

				SyncRecorder.log(SyncRecorder.EventKind.login_success_post, null, "accountName", accountName);

				for (final String syncSetName : SyncShadow.ALL_SYNC_SETS) {
					Sync.notifySyncNeeded(syncSetName);
				}

				runOnUiThread(this::updateDisplay);

			} catch (GooglePlayServicesAvailabilityException e) {
				runOnUiThread(() -> GooglePlayServicesUtil.getErrorDialog(e.getConnectionStatusCode(), this, REQUEST_RECOVER_FROM_PLAY_SERVICES_ERROR).show());

			} catch (UserRecoverableAuthException e) {
				runOnUiThread(() -> startActivityForResult(e.getIntent(), REQUEST_RECOVER_FROM_AUTH_ERROR));

			} catch (IOException | GoogleAuthException | RuntimeException e) {
				Log.e(TAG, "Exception encountered when getting token", e);

			} finally {
				pd.dismiss();
			}
		}).start();
	}

	@SuppressWarnings("deprecation")
	void updateDisplay() {
		final String syncAccountName = Preferences.getString(getString(R.string.pref_syncAccountName_key));
		pref_syncAccountName.setSummary(syncAccountName != null ? syncAccountName : getString(R.string.sync_account_not_selected));

		final String profile_picture_url = Preferences.getString(Prefkey.sync_profile_picture_url);
		if (profile_picture_url == null) {
			pref_syncAccountName.setIcon(null);
		} else {
			Picasso.with(this).load(profile_picture_url).into(profilePictureTarget);
		}

		for (final String syncSetName : SyncShadow.ALL_SYNC_SETS) {
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
}
