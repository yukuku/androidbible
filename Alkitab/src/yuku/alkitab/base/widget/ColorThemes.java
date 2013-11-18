package yuku.alkitab.base.widget;

import java.util.Locale;

import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.debug.R;

class ColorThemes {
	// text color, bg color, verse number color, red text color
	static int[] themeStringToColors(String themeString) {
		return new int[] {
			(int) Long.parseLong(themeString.substring(0, 8), 16),
			(int) Long.parseLong(themeString.substring(9, 17), 16),
			(int) Long.parseLong(themeString.substring(18, 26), 16),
			(int) Long.parseLong(themeString.substring(27, 35), 16)
		};
	}
	
	static String colorsToThemeString(int[] colors) {
		return String.format(Locale.US, "%08x %08x %08x %08x", colors[0], colors[1], colors[2], colors[3]);
	}
	
	static int[] getCurrentColors() {
		return new int[] {
			Preferences.getInt(App.context.getString(R.string.pref_textColor_key), App.context.getResources().getInteger(R.integer.pref_textColor_default)),
			Preferences.getInt(App.context.getString(R.string.pref_backgroundColor_key), App.context.getResources().getInteger(R.integer.pref_backgroundColor_default)),
			Preferences.getInt(App.context.getString(R.string.pref_verseNumberColor_key), App.context.getResources().getInteger(R.integer.pref_verseNumberColor_default)),
			Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default)),
		};
	}
	
	static void setCurrentColors(int[] colors) {
		Preferences.setInt(App.context.getString(R.string.pref_textColor_key), colors[0]);
		Preferences.setInt(App.context.getString(R.string.pref_backgroundColor_key), colors[1]);
		Preferences.setInt(App.context.getString(R.string.pref_verseNumberColor_key), colors[2]);
		Preferences.setInt(App.context.getString(R.string.pref_redTextColor_key), colors[3]);
	}
}
