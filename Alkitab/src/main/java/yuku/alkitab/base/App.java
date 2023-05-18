package yuku.alkitab.base;

import android.content.Context;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDex;
import androidx.preference.PreferenceManager;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Request;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.connection.PRDownloaderOkHttpClient;
import yuku.alkitab.base.sync.Fcm;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.util.ExtensionManager;
import yuku.alkitab.debug.R;
import yuku.alkitab.tracking.Tracker;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.alkitabintegration.display.Launcher;

public class App extends yuku.afw.App {
    private static boolean initted = false;

    enum GsonWrapper {
        INSTANCE;

        final Gson gson = new Gson();
    }

    public static String downloadString(String url) throws IOException {
        return downloadCall(url).execute().body().string();
    }

    public static byte[] downloadBytes(String url) throws IOException {
        return downloadCall(url).execute().body().bytes();
    }

    public static Call downloadCall(String url) {
        return Connections.getOkHttp().newCall(new Request.Builder().url(url).build());
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
        if (initted) return;
        initted = true;

        if (context == null) {
            throw new RuntimeException("yuku.afw.App.context must have been set via initWithAppContext(Context) before calling this method.");
        }

        Tracker.init(context);

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

        { // FCM
            Fcm.renewFcmRegistrationIdIfNeeded(Sync::notifyNewFcmRegistrationId);
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
    }

    public static LocalBroadcastManager getLbm() {
        return LocalBroadcastManager.getInstance(context);
    }

    public static Gson getDefaultGson() {
        return GsonWrapper.INSTANCE.gson;
    }

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
