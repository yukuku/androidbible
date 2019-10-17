package yuku.alkitab.base.util;

import androidx.annotation.Keep;
import com.google.gson.JsonSyntaxException;
import yuku.alkitab.base.App;

public class Highlights {
	static final String TAG = Highlights.class.getSimpleName();

	final static ThreadLocal<Info> tmpInfo = new ThreadLocal<Info>() {
		@Override
		protected Info initialValue() {
			return new Info();
		}
	};

	final static ThreadLocal<Info.Partial> tmpPartial = new ThreadLocal<Info.Partial>() {
		@Override
		protected Info.Partial initialValue() {
			return new Info.Partial();
		}
	};

	@Keep
	public static class Info {
		/** rgb only, without the alpha */
		public int colorRgb;
		public Partial partial;

		@Keep
		public static class Partial {
			/** hashCode of the plain text (not with formatting) */
			public int hashCode;
			public int startOffset;
			public int endOffset;
		}

		public boolean shouldRenderAsPartialForVerseText(final CharSequence verseText) {
			return partial != null && partial.hashCode == Highlights.hashCode(verseText.toString()) && partial.startOffset <= verseText.length() && partial.endOffset <= verseText.length();
		}
	}


	/** Encode a full-verse highlight */
	public static String encode(final int colorRgb) {
		final Info info = tmpInfo.get();

		info.colorRgb = colorRgb;
		info.partial = null;

		return App.getDefaultGson().toJson(info);
	}

	/** Encode a partial highlight */
	public static String encode(final int colorRgb, final int hashCode, final int startOffset, final int endOffset) {
		final Info info = tmpInfo.get();

		info.colorRgb = colorRgb;

		final Info.Partial partial = info.partial = tmpPartial.get();
		partial.hashCode = hashCode;
		partial.startOffset = startOffset;
		partial.endOffset = endOffset;

		return App.getDefaultGson().toJson(info);
	}

	/**
	 * Decodes color code for highlight. It starts with the string "c" then 6 hex digits rrggbb.
	 * @return highlight info, or null if cannot decode
	 */
	public static Info decode(final String text) {
		if (text == null) return null;
		if (text.length() >= 7 && text.charAt(0) == 'c') { // legacy compat
			try {
				final int colorRgb = Integer.parseInt(text.substring(1, 7), 16);
				final Info res = new Info();
				res.colorRgb = colorRgb;
				res.partial = null;
				return res;
			} catch (NumberFormatException e) {
				AppLog.e(TAG, "@@decode", e);
				return null;
			}
		} else if (text.startsWith("{")) { // json format
			try {
				return App.getDefaultGson().fromJson(text, Info.class);
			} catch (JsonSyntaxException e) {
				AppLog.e(TAG, "@@decode", e);
				return null;
			}
		} else {
			return null;
		}
	}

	public static int alphaMix(int colorRgb) {
		return 0xa0000000 | colorRgb;
	}

	public static int hashCode(final String verseText) {
		return verseText.hashCode();
	}
}
