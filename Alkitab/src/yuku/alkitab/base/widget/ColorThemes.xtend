package yuku.alkitab.base.widget

import java.util.Locale
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.R$string
import yuku.alkitab.R$integer

class ColorThemes {
	// text color, bg color, verse number color, red text color
	def static int[] themeStringToColors(String themeString) {
		#[
			Long::parseLong(themeString.substring(0, 8), 16) as int,
			Long::parseLong(themeString.substring(9, 17), 16) as int,
			Long::parseLong(themeString.substring(18, 26), 16) as int,
			Long::parseLong(themeString.substring(27, 35), 16) as int
		]
	}
	
	def static String colorsToThemeString(int[] colors) {
		String::format(Locale::US, "%08x %08x %08x %08x", colors.get(0), colors.get(1), colors.get(2), colors.get(3));
	}
	
	def static int[] getCurrentColors() {
		#[
			Preferences::getInt(App::context.getString(R$string::pref_warnaHuruf_int_key), App::context.getResources().getInteger(R$integer::pref_warnaHuruf_int_default)),
			Preferences::getInt(App::context.getString(R$string::pref_warnaLatar_int_key), App::context.getResources().getInteger(R$integer::pref_warnaLatar_int_default)),
			Preferences::getInt(App::context.getString(R$string::pref_warnaNomerAyat_int_key), App::context.getResources().getInteger(R$integer::pref_warnaNomerAyat_int_default)),
			Preferences::getInt(App::context.getString(R$string::pref_redTextColor_key), App::context.getResources().getInteger(R$integer::pref_redTextColor_default))
		]
	}
	
	def static setCurrentColors(int[] colors) {
		Preferences::setInt(App::context.getString(R$string::pref_warnaHuruf_int_key), colors.get(0))
		Preferences::setInt(App::context.getString(R$string::pref_warnaLatar_int_key), colors.get(1))
		Preferences::setInt(App::context.getString(R$string::pref_warnaNomerAyat_int_key), colors.get(2))
		Preferences::setInt(App::context.getString(R$string::pref_redTextColor_key), colors.get(3))
	}
}
