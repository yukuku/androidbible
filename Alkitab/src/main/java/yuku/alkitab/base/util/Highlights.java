package yuku.alkitab.base.util;

import android.util.Log;

public class Highlights {
	static final String TAG = Highlights.class.getSimpleName();

	public static String encode(int colorRgb) {
		StringBuilder sb = new StringBuilder(10);
		sb.append('c');
		String h = Integer.toHexString(colorRgb);
		for (int x = h.length(); x < 6; x++) {
			sb.append('0');
		}
		sb.append(h);
		return sb.toString();
	}

	/**
	 * Decodes color code for highlight. It starts with the string "c" then 6 hex digits rrggbb.
	 * @return colorRgb (without alpha) or -1 if can't decode. If the 6 hex digits can't be decoded, this will return 0.
	 */
	public static int decode(String text) {
		if (text == null) return -1;
		if (text.length() >= 7 && text.charAt(0) == 'c') {
			try {
				return Integer.parseInt(text.substring(1, 7), 16);
			} catch (NumberFormatException e) {
				Log.e(TAG, "@@decode", e);
				return 0;
			}
		} else {
			return -1;
		}
	}

	public static int alphaMix(int colorRgb) {
		return 0xa0000000 | colorRgb;
	}

}
