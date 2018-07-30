package yuku.alkitab.base.widget;

import android.content.res.Resources;
import android.support.annotation.StringRes;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;

import java.util.Locale;

/**
 * App-wide-locale-aware version for getting strings.
 */
public class Localized {
	static final String TAG = Localized.class.getSimpleName();

	private static Resources currentResources;

	public static String string(@StringRes int resId) {
		return upToDateResources().getString(resId);
	}

	public static String string(@StringRes int resId, Object... formatArgs) {
		return upToDateResources().getString(resId, formatArgs);
	}

	public static CharSequence text(@StringRes int resId) {
		return upToDateResources().getText(resId);
	}

	private static Resources upToDateResources() {
		final Locale prefLocale = ConfigurationWrapper.getLocaleFromPreferences();

		final Resources cachedResources = currentResources;
		if (cachedResources != null) {
			final Locale currentLocale = ConfigurationWrapper.ConfigurationCompat.getLocale(cachedResources.getConfiguration());
			if (prefLocale.equals(currentLocale)) {
				return cachedResources;
			}
		}

		AppLog.d(TAG, "Need to adjust resources for pref locale: " + prefLocale);
		return currentResources = ConfigurationWrapper.wrap(App.context).getResources();
	}
}
