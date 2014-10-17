package yuku.alkitab.base.sync;

import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static yuku.alkitab.base.util.Literals.Array;

public class SyncSettingsActivity extends BasePreferenceActivity {
	static final String TAG = SyncSettingsActivity.class.getSimpleName();

	public static final int REQCODE_accountChooser = 1;

	private static final int REQUEST_RECOVER_FROM_AUTH_ERROR = 101;
	private static final int REQUEST_RECOVER_FROM_PLAY_SERVICES_ERROR = 102;

	private Preference pref_syncAccountName;

	final Target profilePictureTarget = new Target() {
		@Override
		public void onBitmapLoaded(final Bitmap bitmap, final Picasso.LoadedFrom from) {
			if (pref_syncAccountName != null && Preferences.getString(getString(R.string.pref_syncAccountName_key)) != null) {
				pref_syncAccountName.setIcon(new BitmapDrawable(getResources(), bitmap));
			}
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
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sync_settings, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.menuSyncNow) {
			Sync.forceSyncNow();
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
					Preferences.hold();
					Preferences.remove(getString(R.string.pref_syncAccountName_key));
					Preferences.remove(Prefkey.sync_simpleToken);
					Preferences.remove(Prefkey.sync_token_obtained_time);
					Preferences.remove(Prefkey.sync_profile_picture_url);

					for (final String syncSetName : SyncShadow.ALL_SYNC_SETS) {
						S.getDb().deleteSyncShadowBySyncSetName(syncSetName);
					}

					Preferences.unhold();

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

				final Sync.LoginResult loginResult = Sync.login(id_token);

				if (!loginResult.success) {
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
						runOnUiThread(() -> new AlertDialog.Builder(this)
							.setMessage(getString(R.string.sync_login_failed_with_reason, "Could not send GCM registration id"))
							.setPositiveButton(R.string.ok, null)
							.show());
						return;
					}
				} else {
					// if not, ignore. Later eventually we will have it.
				}

				// Success!
				Preferences.hold();
				Preferences.setString(App.context.getString(R.string.pref_syncAccountName_key), loginResult.user_email);
				Preferences.setString(Prefkey.sync_profile_picture_url, loginResult.profile_picture_url);
				Preferences.setString(Prefkey.sync_simpleToken, loginResult.simpleToken);
				Preferences.setInt(Prefkey.sync_token_obtained_time, Sqlitil.nowDateTime());
				Preferences.unhold();

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

	void updateDisplay() {
		final String syncAccountName = Preferences.getString(getString(R.string.pref_syncAccountName_key));
		pref_syncAccountName.setSummary(syncAccountName != null ? syncAccountName : getString(R.string.sync_account_not_selected));

		final String profile_picture_url = Preferences.getString(Prefkey.sync_profile_picture_url);
		if (profile_picture_url == null) {
			pref_syncAccountName.setIcon(null);
		} else {
			Picasso.with(this).load(profile_picture_url).into(profilePictureTarget);
		}

		for (final String syncSet : SyncShadow.ALL_SYNC_SETS) {
			final Preference pref = findPreference(Sync.prefkeyForSyncSetEnabled(syncSet));
			pref.setEnabled(syncAccountName != null);

			if (syncAccountName != null) {
				pref.setSummary("Last synced XXX XX XXXX, XX:XX (rYYY)");
			} else {
				pref.setSummary(null);
			}
		}
	}
}
