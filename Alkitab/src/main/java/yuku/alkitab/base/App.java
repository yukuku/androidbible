package yuku.alkitab.base;

import android.content.Context;
import android.net.Uri;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.multidex.MultiDex;
import androidx.preference.PreferenceManager;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicBoolean;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.connection.PRDownloaderOkHttpClient;
import yuku.alkitab.base.services.AppServices;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Fcm;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.util.ExtensionManager;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.alkitabintegration.display.Launcher;

public class App extends yuku.afw.App {
    private static final AtomicBoolean initted = new AtomicBoolean(false);

    /**
     * App-level service container. Wraps the interfaces implemented by {@link S} so
     * new code can depend on narrow interfaces (and tests can swap fakes in) without
     * reaching into the {@link S} service locator directly. Existing {@code S.foo}
     * call sites are being migrated incrementally — see REM-24.
     *
     * <p>Initialized eagerly at class-load so call sites can read it before
     * {@link #staticInit()} has run. The adapter properties on {@link S} are
     * cheap object literals that only touch context/preferences when their
     * methods are invoked, so this is safe even from tests that haven't booted
     * the full {@link App} (e.g. by overriding the Application class in
     * Robolectric and calling {@code yuku.afw.App.initWithAppContext} directly).
     */
    public static AppServices services = new AppServices(S.storage, S.versions, S.uiDimensions);

    enum GsonWrapper {
        INSTANCE;

        final Gson gson = new Gson();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        staticInit();
    }

    /**
     * {@link yuku.afw.App#context} must have been set via {@link #initWithAppContext(Context)}
     * before calling this method.
     */
    public synchronized static void staticInit() {
        if (initted.getAndSet(true)) return;

        if (context == null) {
            throw new RuntimeException("yuku.afw.App.context must have been set via initWithAppContext(Context) before calling this method.");
        }

        FeedbackSender.getInstance(context).trySend();

        for (final int preferenceResId : new int[]{
            R.xml.settings_display,
            R.xml.settings_usage,
            R.xml.settings_copy_share,
            R.xml.secret_settings,
            R.xml.sync_settings,
        }) {
            PreferenceManager.setDefaultValues(context, preferenceResId, false);
        }

        // FCM is only useful once the user has signed into sync — the registration id
        // gets uploaded by Sync.notifyNewFcmRegistrationId, which itself early-exits
        // when sync_simpleToken is null. Skipping this block on first launch (before
        // any sync sign-in) keeps Firebase Messaging dormant and avoids prompting the
        // user for POST_NOTIFICATIONS before they have any reason to grant it. New
        // sign-ins are handled separately by SyncLoginActivity.gotSimpleToken.
        if (Preferences.getString(Prefkey.sync_simpleToken) != null) {
            final String fcmRegistrationId = Fcm.renewFcmRegistrationIdIfNeeded(Sync::notifyNewFcmRegistrationId);
            if (fcmRegistrationId != null) {
                Sync.retryPendingFcmRegistrationIfNeeded(fcmRegistrationId);
            }
        }

        PRDownloader.initialize(context, new PRDownloaderConfig.Builder()
            .setHttpClient(new PRDownloaderOkHttpClient(Connections.getOkHttp()))
            .setUserAgent(Connections.getHttpUserAgent())
            .build()
        );

        // Make sure extensions are up-to-date
        ExtensionManager.registerReceivers(context);

        // make sure launcher do not open other variants of the app
        Launcher.setAppPackageName(context.getPackageName());

        // remove unused notification channels
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.deleteNotificationChannel("devotion_downloader");
        notificationManager.deleteNotificationChannel("download_mapper");
        notificationManager.deleteNotificationChannel("devotion_reminder");

        // Bible audio playback channel — created here so that media3's
        // DefaultMediaNotificationProvider posts onto a low-importance,
        // silent channel rather than the default high-importance one.
        // Channel attributes (importance, sound, vibration) are immutable
        // after first creation, so creating it ourselves up-front is the
        // only way to control them.
        notificationManager.createNotificationChannel(
            new NotificationChannelCompat.Builder("audio_bible", NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.audio_bible_notification_channel_name))
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build()
        );
    }

    public static Gson getDefaultGson() {
        return GsonWrapper.INSTANCE.gson;
    }

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static String getAppIdentifierParamsEncoded() {
        return "app_versionCode=" + App.getVersionCode()
            + "&app_versionName=" + Uri.encode(App.getVersionName())
            + "&app_packageName=" + Uri.encode(BuildConfig.APPLICATION_ID);
    }

    public static String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    public static int getVersionCode() {
        return BuildConfig.VERSION_CODE;
    }
}
