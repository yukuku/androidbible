package yuku.alkitab.base.widget;

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
	
	static int[] getCurrentColors(final boolean forNightMode) {
		if (forNightMode) {
			return new int[] {
				Preferences.getInt(App.context.getString(R.string.pref_textColor_night_key), App.context.getResources().getInteger(R.integer.pref_textColor_night_default)),
				Preferences.getInt(App.context.getString(R.string.pref_backgroundColor_night_key), App.context.getResources().getInteger(R.integer.pref_backgroundColor_night_default)),
				Preferences.getInt(App.context.getString(R.string.pref_verseNumberColor_night_key), App.context.getResources().getInteger(R.integer.pref_verseNumberColor_night_default)),
				Preferences.getInt(App.context.getString(R.string.pref_redTextColor_night_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_night_default)),
			};
		} else {
			return new int[] {
				Preferences.getInt(App.context.getString(R.string.pref_textColor_key), App.context.getResources().getInteger(R.integer.pref_textColor_default)),
				Preferences.getInt(App.context.getString(R.string.pref_backgroundColor_key), App.context.getResources().getInteger(R.integer.pref_backgroundColor_default)),
				Preferences.getInt(App.context.getString(R.string.pref_verseNumberColor_key), App.context.getResources().getInteger(R.integer.pref_verseNumberColor_default)),
				Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default)),
			};
		}
	}
	
	static void setCurrentColors(int[] colors, final boolean forNightMode) {
		if (forNightMode) {
			Preferences.setInt(App.context.getString(R.string.pref_textColor_night_key), colors[0]);
			Preferences.setInt(App.context.getString(R.string.pref_backgroundColor_night_key), colors[1]);
			Preferences.setInt(App.context.getString(R.string.pref_verseNumberColor_night_key), colors[2]);
			Preferences.setInt(App.context.getString(R.string.pref_redTextColor_night_key), colors[3]);
		} else {
			Preferences.setInt(App.context.getString(R.string.pref_textColor_key), colors[0]);
			Preferences.setInt(App.context.getString(R.string.pref_backgroundColor_key), colors[1]);
			Preferences.setInt(App.context.getString(R.string.pref_verseNumberColor_key), colors[2]);
			Preferences.setInt(App.context.getString(R.string.pref_redTextColor_key), colors[3]);
		}
	}
}
