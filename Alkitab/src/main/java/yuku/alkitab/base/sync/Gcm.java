package yuku.alkitab.base.sync;

import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.storage.NoBackupSharedPreferences;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Gcm {
	static final String TAG = Gcm.class.getSimpleName();

	public static final String SENDER_ID = "596544549791";

	public static final String GCM_MESSAGE_KEY_source_installation_id = "source_installation_id";
	public static final String GCM_MESSAGE_KEY_encoded_data = "encoded_data";

	public interface Listener {
		/**
		 * Called only when we get a NEW registration id
		 */
		void onNewRegistrationId(String registrationId);
	}

	private static final ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();

	private static boolean checkPlayServices() {
		final GoogleApiAvailability gaa = GoogleApiAvailability.getInstance();

		final int statusCode = gaa.isGooglePlayServicesAvailable(App.context);
		if (statusCode != ConnectionResult.SUCCESS) {
			if (gaa.isUserResolvableError(statusCode) && thisIsPlayDevice()) {
				gaa.showErrorNotification(App.context, statusCode);
			} else {
				AppLog.i(TAG, "This device is not supported.");
			}
			return false;
		}
		return true;
	}

	private static boolean thisIsPlayDevice() {
		if ("Amazon".equals(Build.MANUFACTURER) && Build.MODEL != null && (Build.MODEL.startsWith("KF") || Build.MODEL.startsWith("Kindle"))) {
			return false;
		}

		if ("qnx".equals(System.getProperty("os.name"))) {
			return false;
		}

		if ("Genymotion".equals(Build.MANUFACTURER)) {
			return false;
		}

		return true;
	}

	/**
	 * @param listener to be called if a new registration id is obtained in the background
	 * @return a registration id that is currently stored if any, null otherwise.
	 */
	@Nullable
	public static String renewGcmRegistrationIdIfNeeded(@Nullable final Listener listener) {
		if (!checkPlayServices()) {
			AppLog.i(TAG, "No valid Google Play Services APK found.");
			return null;
		}

		final String registrationId = getStoredRegistrationId();

		if (registrationId != null) {
			return registrationId;
		}

		if (listener != null) listeners.add(listener);
		registerInBackground();

		return null;
	}

	/**
	 * Returns the GCM registration id, only if it is stored and the app version when the
	 * registration was obtained is still the same as the current one.
	 *
	 * @return null if not available.
	 */
	@Nullable
	private static String getStoredRegistrationId() {
		final String registrationId = readGcmRegistrationId();
		if (registrationId == null) {
			AppLog.i(TAG, "Registration not found.");
			return null;
		}

		// Check if app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version.
		final int registeredVersionCode = Preferences.getInt(Prefkey.gcm_last_app_version_code, Integer.MIN_VALUE);
		final int currentVersionCode = App.getVersionCode();
		if (registeredVersionCode != currentVersionCode) {
			AppLog.i(TAG, "App version changed from " + registeredVersionCode + " to " + currentVersionCode);
			return null;
		}

		return registrationId;
	}

	private static String readGcmRegistrationId() {
		final NoBackupSharedPreferences nbsp = NoBackupSharedPreferences.get();

		String res = Preferences.getString(Prefkey.gcm_registration_id, null);
		if (res == null) {
			res = nbsp.getString(Prefkey.gcm_registration_id.name());
		} else {
			// we need to remove it from the backed up folder and move it to the nonbacked up folder
			Preferences.remove(Prefkey.gcm_registration_id);
			nbsp.setString(Prefkey.gcm_registration_id.name(), res);
		}

		return res;
	}

	private static void registerInBackground() {
		Background.run(() -> {
			try {
				final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(App.context);
				final String registrationId = gcm.register(SENDER_ID);
				AppLog.i(TAG, "Device registered, registration ID=" + registrationId);

				// You should send the registration ID to your server over HTTP,
				// so it can use GCM/HTTP or CCS to send messages to your app.
				while (true) {
					final Listener listener = listeners.poll();
					if (listener == null) break;
					listener.onNewRegistrationId(registrationId);
				}

				// Persist the regID - no need to register again.
				Preferences.setInt(Prefkey.gcm_last_app_version_code, App.getVersionCode());
				writeGcmRegistrationId(registrationId);
			} catch (IOException ex) {
				// If there is an error, don't just keep trying to register.
				// Require the user to click a button again, or perform exponential back-off.
				AppLog.e(TAG, "Error :" + ex.getMessage(), ex);
			}
		});
	}

	private static void writeGcmRegistrationId(final String registrationId) {
		Preferences.remove(Prefkey.gcm_registration_id);

		NoBackupSharedPreferences.get().setString(Prefkey.gcm_registration_id.name(), registrationId);
	}
}
