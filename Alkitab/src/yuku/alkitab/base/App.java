package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.preference.*;
import android.util.*;

import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.storage.*;

public class App extends Application {
	public static final String TAG = App.class.getSimpleName();

	public static Context context;
	
	@Override public void onCreate() {
		super.onCreate();

		context = getApplicationContext();
		
		PreferenceManager.setDefaultValues(this, R.xml.pengaturan, false);
		
		Configuration config = getBaseContext().getResources().getConfiguration();
		Locale locale = getLocaleFromPreferences();
		if (!config.locale.getLanguage().equals(locale.getLanguage())) {
			Log.d(TAG, "onCreate: locale will be updated to: " + locale);
			updateConfigurationWithLocale(config, locale);
		}
	}

	private Locale getLocaleFromPreferences() {
		String lang = Preferences.getString(R.string.pref_bahasa_key, R.string.pref_bahasa_default);
		if (lang == null || "DEFAULT".equals(lang)) {
			lang = Locale.getDefault().getLanguage();
		}
		
		return new Locale(lang);
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		Log.d(TAG, "onConfigurationChanged: config changed to: " + newConfig);
		updateConfigurationWithLocale(newConfig, getLocaleFromPreferences());
	}

	public static void updateConfigurationWithLocale(Configuration config, Locale locale) {
		if (locale != null) {
			config.locale = locale;

			Log.d(TAG, "updateConfigurationWithLocale: config updated to locale: " + locale);
			context.getResources().updateConfiguration(config, null);
		}
	}
}
