package yuku.alkitab.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDex;
import androidx.preference.PreferenceManager;

import com.crashlytics.android.Crashlytics;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.google.gson.Gson;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import io.fabric.sdk.android.Fabric;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.Version;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Fcm;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.util.DevotionReminder;
import yuku.alkitab.tracking.Tracker;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.alkitabintegration.display.Launcher;
import yuku.stethoshim.StethoShim;

public class App extends yuku.afw.App {
	public static final String TAG = App.class.getSimpleName();

	private static boolean initted = false;

	static class UserAgentInterceptor implements Interceptor {
		@Override
		public Response intercept(final Chain chain) throws IOException {
			final Request originalRequest = chain.request();
			final Request requestWithUserAgent = originalRequest.newBuilder()
				.removeHeader("User-Agent")
				.addHeader("User-Agent", httpUserAgent())
				.build();
			return chain.proceed(requestWithUserAgent);
		}
	}

	@NonNull
	static String httpUserAgent() {
		return Version.userAgent() + " " + App.context.getPackageName() + "/" + App.getVersionName();
	}

	enum OkHttpClientWrapper {
		INSTANCE;

		final OkHttpClient longTimeoutClient;

		{
			final OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder
				.addNetworkInterceptor(new UserAgentInterceptor())
				.connectTimeout(300, TimeUnit.SECONDS)
				.readTimeout(300, TimeUnit.SECONDS)
				.writeTimeout(600, TimeUnit.SECONDS);
			StethoShim.addNetworkInterceptor(builder);
			longTimeoutClient = builder.build();
		}
	}

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
		return okhttp().newCall(new Request.Builder().url(url).build());
	}

	public static OkHttpClient getLongTimeoutOkHttpClient() {
		return OkHttpClientWrapper.INSTANCE.longTimeoutClient;
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

		Tracker.init(context);
		Fabric.with(context, new Crashlytics());

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
			.setUserAgent(httpUserAgent())
			.build()
		);

		// make sure launcher do not open other variants of the app
		Launcher.setAppPackageName(context.getPackageName());

		// sync on app start, if we are logged in
		if (Preferences.contains(Prefkey.sync_simpleToken)) {
			Sync.notifySyncNeeded(SyncShadow.ALL_SYNC_SET_NAMES);
		}
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

	private static OkHttpClient okhttp;

	@NonNull
	public static synchronized OkHttpClient okhttp() {
		OkHttpClient res = okhttp;
		if (res == null) {
			final File cacheDir = new File(context.getCacheDir(), "okhttp-cache");
			if (!cacheDir.exists()) {
				//noinspection ResultOfMethodCallIgnored
				cacheDir.mkdirs();
			}

			final OkHttpClient.Builder builder = new OkHttpClient.Builder()
				.cache(new Cache(cacheDir, 50 * 1024 * 1024))
				.connectTimeout(30, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.addNetworkInterceptor(new UserAgentInterceptor());

			if (BuildConfig.DEBUG) {
				builder.hostnameVerifier((hostname, session) -> true);
			}

			StethoShim.addNetworkInterceptor(builder);

			okhttp = res = builder.build();
		}
		return res;
	}

	static Picasso picasso;

	@NonNull
	public static synchronized Picasso picasso() {
		Picasso res;
		if (picasso == null) {
			picasso = res = new Picasso.Builder(context)
				.defaultBitmapConfig(Bitmap.Config.RGB_565)
				.downloader(new OkHttp3Downloader(okhttp()))
				.build();
			return res;
		}
		return picasso;
	}
}
