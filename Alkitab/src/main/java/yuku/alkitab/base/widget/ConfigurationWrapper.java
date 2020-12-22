package yuku.alkitab.base.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

/**
 * Context wrapper for changing app-wide locale or font scale.
 */
public class ConfigurationWrapper {
	static final String TAG = ConfigurationWrapper.class.getSimpleName();

	public static Context wrap(final Context base) {
		final Configuration config = base.getResources().getConfiguration();

		final Locale prefLocale = getLocaleFromPreferences();
		if (BuildConfig.DEBUG) AppLog.d(TAG, "@@wrap: config locale will be updated to: " + prefLocale);

		config.setLocale(prefLocale);

		final float fontScale = getFontScaleFromPreferences();
		if (config.fontScale != fontScale) {
			if (BuildConfig.DEBUG) AppLog.d(TAG, "@@wrap: fontScale will be updated to: " + fontScale);

			config.fontScale = fontScale;
		}

		return new ContextWrapper(base.createConfigurationContext(config));
	}

	private static final AtomicInteger serialCounter = new AtomicInteger();

	public static int getSerialCounter() {
		return serialCounter.get();
	}

	public static void notifyConfigurationNeedsUpdate() {
		serialCounter.incrementAndGet();
	}

	@NonNull
	public static Locale getLocaleFromPreferences() {
		final String lang = Preferences.getString(R.string.pref_language_key, R.string.pref_language_default);
		if ("DEFAULT".equals(lang)) {
			return Locale.getDefault();
		}

		if (!lang.contains("-")) {
			// From https://code.google.com/p/android/issues/detail?id=225679#c2
			// It's not a good idea to set a locale without a country in Android.
			// Various parts of the system expect a country and may behave in weird ways otherwise.
			// So if you are setting a locale programatically, set it to "ru-RU" instead of "ru".
			return localeWithCountry(lang);

		} else { // contains "-"
			return LocaleListCompat.forLanguageTags(lang).get(0);
		}
	}

	@NonNull
	private static Locale localeWithCountry(@NonNull final String lang) {
		// Reference:
		// http://download.geonames.org/export/dump/countryInfo.txt
		// https://wiki.openstreetmap.org/wiki/Nominatim/Country_Codes
		switch (lang) {
			case "af":
				return new Locale("af", "ZA");
			case "bg":
				return new Locale("bg", "BG");
			case "ceb":
				return new Locale("ceb", "PH");
			case "cs":
				return new Locale("cs", "CZ");
			case "da":
				return new Locale("da", "DK");
			case "de":
				return new Locale("de", "DE");
			case "en":
				return new Locale("en", "US");
			case "el":
				return new Locale("el", "GR");
			case "es":
				return new Locale("es", "ES");
			case "fr":
				return new Locale("fr", "FR");
			case "in":
				return new Locale("in", "ID");
			case "it":
				return new Locale("it", "IT");
			case "ja":
				return new Locale("ja", "JP");
			case "ko":
				return new Locale("ko", "KR");
			case "lv":
				return new Locale("lv", "LV");
			case "ms":
				return new Locale("ms", "MY");
			case "my":
				return new Locale("my", "MM");
			case "nl":
				return new Locale("nl", "NL");
			case "pl":
				return new Locale("pl", "PL");
			case "pt": // pt-BR has its own resources already
				return new Locale("pt", "PT");
			case "ro":
				return new Locale("ro", "RO");
			case "ru":
				return new Locale("ru", "RU");
			case "th":
				return new Locale("th", "TH");
			case "tl":
				return new Locale("tl", "PH");
			case "tr":
				return new Locale("tr", "TR");
			case "uk":
				return new Locale("uk", "UA");
			case "vi":
				return new Locale("vi", "VN");
			default:
				return new Locale(lang);
		}
	}

	private static float getFontScaleFromPreferences() {
		float res = 0.f;

		final String forceFontScale = Preferences.getString(R.string.pref_forceFontScale_key, R.string.pref_forceFontScale_default);
		if (!forceFontScale.equals(Preferences.getString(R.string.pref_forceFontScale_default))) {
			if (forceFontScale.equals(Preferences.getString(R.string.pref_forceFontScale_value_x1_5))) {
				res = 1.5f;
			} else if (forceFontScale.equals(Preferences.getString(R.string.pref_forceFontScale_value_x1_7))) {
				res = 1.7f;
			} else if (forceFontScale.equals(Preferences.getString(R.string.pref_forceFontScale_value_x2_0))) {
				res = 2.0f;
			}
		}

		if (res == 0.f) {
			final float defFontScale = Settings.System.getFloat(App.context.getContentResolver(), Settings.System.FONT_SCALE, 1.f);
			if (BuildConfig.DEBUG) AppLog.d(TAG, "defFontScale: " + defFontScale);
			res = defFontScale;
		}

		return res;
	}
}
