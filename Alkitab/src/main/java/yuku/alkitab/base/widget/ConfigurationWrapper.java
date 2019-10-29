package yuku.alkitab.base.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import android.provider.Settings;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context wrapper for changing app-wide locale or font scale.
 */
public class ConfigurationWrapper extends ContextWrapper {
	static final String TAG = ConfigurationWrapper.class.getSimpleName();

	public ConfigurationWrapper(final Context base) {
		super(base);
	}

	public static Context wrap(final Context base) {
		final Configuration config = base.getResources().getConfiguration();

		final Locale prefLocale = getLocaleFromPreferences();
		if (BuildConfig.DEBUG) AppLog.d(TAG, "@@wrap: config locale will be updated to: " + prefLocale);

		ConfigurationCompat.setLocale(config, prefLocale);

		final float fontScale = getFontScaleFromPreferences();
		if (config.fontScale != fontScale) {
			if (BuildConfig.DEBUG) AppLog.d(TAG, "@@wrap: fontScale will be updated to: " + fontScale);

			config.fontScale = fontScale;
		}

		return new ConfigurationWrapper(ConfigurationCompat.updateConfiguration(base, config));
	}

	private static AtomicInteger serialCounter = new AtomicInteger();

	public static int getSerialCounter() {
		return serialCounter.get();
	}

	public static void notifyConfigurationNeedsUpdate() {
		serialCounter.incrementAndGet();
	}

	@SuppressWarnings("deprecation")
	public static class ConfigurationCompat {
		@Nullable
		public static Locale getLocale(Configuration config) {
			if (Build.VERSION.SDK_INT >= 24) {
				final LocaleList locales = config.getLocales();
				if (locales.size() > 0) {
					return locales.get(0);
				} else {
					return null;
				}
			} else {
				return config.locale;
			}
		}

		public static void setLocale(Configuration config, @NonNull Locale locale) {
			config.setLocale(locale);
		}

		@CheckResult
		public static Context updateConfiguration(Context context, Configuration config) {
			return context.createConfigurationContext(config);
		}
	}


	@NonNull
	public static Locale getLocaleFromPreferences() {
		final String lang = Preferences.getString(R.string.pref_language_key, R.string.pref_language_default);
		if (lang == null || "DEFAULT".equals(lang)) {
			return Locale.getDefault();
		}

		if (!lang.contains("-")) {
			// From https://code.google.com/p/android/issues/detail?id=225679#c2
			// It's not a good idea to set a locale without a country in Android.
			// Various parts of the system expect a country and may behave in weird ways otherwise.
			// So if you are setting a locale programatically, set it to "ru-RU" instead of "ru".
			return localeWithCountry(lang);

		} else { // contains "-"
			switch (lang) {
				case "zh-CN":
					return Locale.SIMPLIFIED_CHINESE;
				case "zh-TW":
					return Locale.TRADITIONAL_CHINESE;
				default:
					return new Locale(lang);
			}
		}
	}

	@NonNull
	private static Locale localeWithCountry(@NonNull final String lang) {
		switch (lang) {
			case "af":
				return new Locale("af", "ZA");
			case "in":
				return new Locale("in", "ID");
			case "cs":
				return new Locale("cs", "CZ");
			case "da":
				return new Locale("da", "DK");
			case "de":
				return new Locale("de", "DE");
			case "en":
				return new Locale("en", "US");
			case "es":
				return new Locale("es", "ES");
			case "fr":
				return new Locale("fr", "FR");
			case "it":
				return new Locale("it", "IT");
			case "lv":
				return new Locale("lv", "LV");
			case "nl":
				return new Locale("nl", "NL");
			case "pl":
				return new Locale("pl", "PL");
			case "pt":
				return new Locale("pt", "BR");
			case "ro":
				return new Locale("ro", "RO");
			case "vi":
				return new Locale("vi", "VN");
			case "bg":
				return new Locale("bg", "BG");
			case "ru":
				return new Locale("ru", "RU");
			case "uk":
				return new Locale("uk", "UA");
			case "th":
				return new Locale("th", "TH");
			case "tr":
				return new Locale("tr", "TR");
			case "ja":
				return new Locale("ja", "JP");
			case "ko":
				return new Locale("ko", "KR");
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
