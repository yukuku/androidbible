package yuku.alkitab.base.sync;

import androidx.annotation.Nullable;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.ConcurrentLinkedQueue;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.storage.NoBackupSharedPreferences;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;

public class Fcm {
    static final String TAG = Fcm.class.getSimpleName();

    public static final String SENDER_ID = "866757332604";

    public interface Listener {
        /**
         * Called only when we get a NEW registration id
         */
        void onNewRegistrationId(String registrationId);
    }

    private static final ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();

    /**
     * @param listener to be called if a new registration id is obtained in the background
     * @return a registration id that is currently stored if any, null otherwise.
     */
    @Nullable
    public static String renewFcmRegistrationIdIfNeeded(@Nullable final Listener listener) {
        final String registrationId = getStoredRegistrationId();

        if (registrationId != null) {
            return registrationId;
        }

        if (listener != null) listeners.add(listener);
        registerInBackground();

        return null;
    }

    /**
     * Returns the FCM registration id, only if it is stored and the app version when the
     * registration was obtained is still the same as the current one.
     *
     * @return null if not available.
     */
    @Nullable
    private static String getStoredRegistrationId() {
        final String registrationId = readFcmRegistrationId();
        if (registrationId == null) {
            AppLog.i(TAG, "Registration not found.");
            return null;
        }

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        final int registeredVersionCode = Preferences.getInt(Prefkey.fcm_last_app_version_code, 0);
        final int currentVersionCode = App.getVersionCode();
        if (registeredVersionCode != currentVersionCode) {
            AppLog.i(TAG, "App version changed from " + registeredVersionCode + " to " + currentVersionCode);
            return null;
        }

        return registrationId;
    }

    private static String readFcmRegistrationId() {
        final NoBackupSharedPreferences nbsp = NoBackupSharedPreferences.get();
        return nbsp.getString(Prefkey.fcm_registration_id.name());
    }

    private static void registerInBackground() {
        Background.run(() -> {
            FirebaseInstallations.getInstance().getToken(false).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform exponential back-off.
                    final Exception ex = task.getException();
                    if (ex != null) {
                        AppLog.e(TAG, "Error :" + ex.getMessage(), ex);
                    }
                    return;
                }

                // Get new Instance ID token
                final InstallationTokenResult result = task.getResult();
                if (result == null) return;

                final String registrationId = result.getToken();
                AppLog.i(TAG, "Device registered, registration ID=" + registrationId);

                // You should send the registration ID to your server over HTTP
                while (true) {
                    final Listener listener = listeners.poll();
                    if (listener == null) break;
                    listener.onNewRegistrationId(registrationId);
                }

                // Persist the regID - no need to register again.
                Preferences.setInt(Prefkey.fcm_last_app_version_code, App.getVersionCode());
                writeFcmRegistrationId(registrationId);
            });
        });
    }

    private static void writeFcmRegistrationId(final String registrationId) {
        NoBackupSharedPreferences.get().setString(Prefkey.fcm_registration_id.name(), registrationId);
    }
}
