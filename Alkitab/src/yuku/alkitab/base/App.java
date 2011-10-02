package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.preference.*;
import android.util.*;

import java.util.*;

import yuku.alkitab.base.storage.*;
import yuku.kirimfidbek.*;

public class App extends Application {
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
