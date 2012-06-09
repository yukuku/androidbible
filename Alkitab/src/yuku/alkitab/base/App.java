package yuku.alkitab.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import greendroid.app.GDApplication;

import java.io.File;
import java.util.Locale;

import yuku.alkitab.base.storage.Preferences;
import yuku.kirimfidbek.PengirimFidbek;
import yuku.kirimfidbek.R;

public class App extends GDApplication {
	public static final String TAG = App.class.getSimpleName();

	public static Context context;
	public static PengirimFidbek pengirimFidbek;
	
	@Override public void onCreate() {
		super.onCreate();

		context = getApplicationContext();
		pengirimFidbek = siapinPengirimFidbek(context);
		
		pengirimFidbek.cobaKirim();
		
		PreferenceManager.setDefaultValues(this, R.xml.pengaturan, false);
		
		Configuration config = getBaseContext().getResources().getConfiguration();
		Locale locale = getLocaleFromPreferences();
		if (!config.locale.getLanguage().equals(locale.getLanguage())) {
			Log.d(TAG, "onCreate: locale will be updated to: " + locale); //$NON-NLS-1$
			updateConfigurationWithLocale(config, locale);
		}
		
		// http://android-developers.blogspot.com/2011/09/androids-http-clients.html
		{
		    // HTTP connection reuse which was buggy pre-froyo
		    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
		        System.setProperty("http.keepAlive", "false");
		    }
		    
		    // Use reflection to enable HTTP response caching on devices that support it. This sample code will turn on the response cache on Ice Cream Sandwich without affecting earlier releases:
		    try {
		        long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
		        File httpCacheDir = new File(getCacheDir(), "http");
		        Class.forName("android.net.http.HttpResponseCache")
		            .getMethod("install", File.class, long.class)
		            .invoke(null, httpCacheDir, httpCacheSize);
		    } catch (Exception httpResponseCacheNotAvailable) {
		    }
		}
	}

	private Locale getLocaleFromPreferences() {
		String lang = Preferences.getString(R.string.pref_bahasa_key, R.string.pref_bahasa_default);
		if (lang == null || "DEFAULT".equals(lang)) { //$NON-NLS-1$
			lang = Locale.getDefault().getLanguage();
		}
		
		return new Locale(lang);
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		Log.d(TAG, "onConfigurationChanged: config changed to: " + newConfig); //$NON-NLS-1$
		updateConfigurationWithLocale(newConfig, getLocaleFromPreferences());
	}

	public static void updateConfigurationWithLocale(Configuration config, Locale locale) {
		if (locale != null) {
			config.locale = locale;

			Log.d(TAG, "updateConfigurationWithLocale: config updated to locale: " + locale); //$NON-NLS-1$
			context.getResources().updateConfiguration(config, null);
		}
	}

	private static PengirimFidbek siapinPengirimFidbek(final Context context) {
		PengirimFidbek res = new PengirimFidbek(context, getPreferencesInstan());
		res.activateDefaultUncaughtExceptionHandler();
		res.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
			@Override public void onSuccess(final byte[] response) {
				Log.e(TAG, "KirimFidbek respon: " + new String(response, 0, response.length)); //$NON-NLS-1$
			}
		});
		return res;
	}

	public static SharedPreferences getPreferencesInstan() {
		return context.getSharedPreferences(context.getPackageName(), 0);
	}
}
