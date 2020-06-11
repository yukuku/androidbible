package yuku.alkitab.base;

import android.content.Context;
import android.os.Build;
import android.os.StrictMode;
import android.view.ViewConfiguration;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDex;
import androidx.preference.PreferenceManager;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Field;
import okhttp3.Call;
import okhttp3.Request;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.connection.PRDownloaderOkHttpClient;
import yuku.alkitab.base.sync.Fcm;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.util.DevotionReminder;
import yuku.alkitab.tracking.Tracker;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.alkitabintegration.display.Launcher;
import yuku.stethoshim.StethoShim;

public class App extends yuku.afw.App {
	static final String TAG = App.class.getSimpleName();

	private static boolean initted = false;

	enum GsonWrapper {
		INSTANCE;

		Gson gson = new Gson();
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

		{ // Stetho call through proxy
			StethoShim.initializeWithDefaults(this);
		}
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

		// Do not crash even if the devotion notification sound settings is set to file URI.
		// This only happens on Android 7.0 and 7.1.
		// https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/5b34ea186007d59fcd13a1ab
		if (Build.VERSION.SDK_INT >= 24 && Build.VERSION.SDK_INT <= 25) {
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
		}

		Tracker.init(context);

		final FeedbackSender fs = FeedbackSender.getInstance(context);
		fs.trySend();

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

		DevotionReminder.scheduleAlarm();

		forceOverflowMenu();

		PRDownloader.initialize(context, new PRDownloaderConfig.Builder()
			.setHttpClient(new PRDownloaderOkHttpClient(Connections.getOkHttp()))
			.setUserAgent(Connections.getHttpUserAgent())
			.build()
		);

		// make sure launcher do not open other variants of the app
		Launcher.setAppPackageName(context.getPackageName());
	}

	private static void forceOverflowMenu() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			return; // no need to do anything, it is already forced on KitKat
		}

		final ViewConfiguration config = ViewConfiguration.get(context);
		try {
			final Field sHasPermanentMenuKey = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
			sHasPermanentMenuKey.setAccessible(true);
			sHasPermanentMenuKey.setBoolean(config, false);
		} catch (Exception e) {
			AppLog.w(TAG, "ViewConfiguration has no sHasPermanentMenuKey field", e);
		}

		try {
			final Field sHasPermanentMenuKeySet = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKeySet");
			sHasPermanentMenuKeySet.setAccessible(true);
			sHasPermanentMenuKeySet.setBoolean(config, true);
		} catch (Exception e) {
			AppLog.w(TAG, "ViewConfiguration has no sHasPermanentMenuKeySet field", e);
		}
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
