package yuku.alkitab.base;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.graphics.ColorUtils;
import android.widget.TextView;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.storage.NoBackupSharedPreferences;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.UUID;

public class U {
	static final String TAG = U.class.getSimpleName();

	/**
	 * If text is null, this returns null.
	 * If verse doesn't start with @: don't do anything.
	 * Otherwise, remove all @'s and one character after that and also text between @&lt; and @&gt;.
	 */
	@Nullable
	public static String removeSpecialCodes(@Nullable final String text) {
		return removeSpecialCodes(text, false);
	}

	/**
	 * If text is null, this returns null.
	 * If verse doesn't start with @: don't do anything, except when force is set to true.
	 * Otherwise, remove all @'s and one character after that and also text between @&lt; and @&gt;.
	 */
	@Nullable
	public static String removeSpecialCodes(@Nullable final String text, final boolean force) {
		if (text == null) return null;
		if (text.length() == 0) return text;
		if (!force && text.charAt(0) != '@') return text;

		final StringBuilder sb = new StringBuilder(text.length());
		int pos = 0;

		while (true) {
			final int p = text.indexOf('@', pos);
			if (p == -1) {
				break;
			}

			sb.append(text, pos, p);
			pos = p + 2;

			if (p + 1 < text.length()) {
				final char skipped = text.charAt(p + 1);
				switch (skipped) {
					// did we skip "@<"?
					case '<': {
						// look for matching "@>"
						int q = text.indexOf("@>", pos);
						if (q != -1) {
							pos = q + 2;
						}
					}
					break;
					// did we skip a paragraph marker, new paragraph, or newline?
					// if so, add a space if needed
					case '0':
					case '1':
					case '2':
					case '3':
					case '4':
					case '^':
					case '8': {
						// only add if the last character output is not already a whitespace
						if (sb.length() == 0 || Character.isWhitespace(sb.charAt(sb.length() - 1))) {
							// we do not need to put extra space
						} else {
							sb.append(' ');
						}
					}
					break;
				}
			}
		}

		sb.append(text, pos, text.length());
		return sb.toString();
	}

	public static String encodeLabelBackgroundColor(int colorRgb_background) {
		StringBuilder sb = new StringBuilder(10);
		sb.append('b'); // 'b': background color
		String h = Integer.toHexString(colorRgb_background);
		for (int x = h.length(); x < 6; x++) {
			sb.append('0');
		}
		sb.append(h);
		return sb.toString();
	}

	/**
	 * @return colorRgb (without alpha) or -1 if can't decode
	 */
	public static int decodeLabelBackgroundColor(String backgroundColor) {
		if (backgroundColor == null || backgroundColor.length() == 0) return -1;
		if (backgroundColor.length() >= 7 && backgroundColor.charAt(0) == 'b') { // 'b': background color
			return Integer.parseInt(backgroundColor.substring(1, 7), 16);
		} else {
			return -1;
		}
	}

	public static int getLabelForegroundColorBasedOnBackgroundColor(int colorRgb) {
		float[] hsl = {0.f, 0.f, 0.f};
		rgbToHsl(colorRgb, hsl);

		if (hsl[2] > 0.5f) hsl[2] -= 0.44f;
		else hsl[2] += 0.44f;

		return hslToRgb(hsl);
	}

	public static void rgbToHsl(int rgb, float[] hsl) {
		float r = ((0x00ff0000 & rgb) >> 16) / 255.f;
		float g = ((0x0000ff00 & rgb) >> 8) / 255.f;
		float b = ((0x000000ff & rgb)) / 255.f;
		float max = Math.max(Math.max(r, g), b);
		float min = Math.min(Math.min(r, g), b);
		float c = max - min;

		float h_ = 0.f;
		if (c == 0) {
			h_ = 0;
		} else if (max == r) {
			h_ = (g - b) / c;
			if (h_ < 0) h_ += 6.f;
		} else if (max == g) {
			h_ = (b - r) / c + 2.f;
		} else if (max == b) {
			h_ = (r - g) / c + 4.f;
		}
		float h = 60.f * h_;

		float l = (max + min) * 0.5f;

		float s;
		if (c == 0) {
			s = 0.f;
		} else {
			s = c / (1 - Math.abs(2.f * l - 1.f));
		}

		hsl[0] = h;
		hsl[1] = s;
		hsl[2] = l;
	}

	public static int hslToRgb(float[] hsl) {
		float h = hsl[0];
		float s = hsl[1];
		float l = hsl[2];

		float c = (1 - Math.abs(2.f * l - 1.f)) * s;
		float h_ = h / 60.f;
		float h_mod2 = h_;
		if (h_mod2 >= 4.f) h_mod2 -= 4.f;
		else if (h_mod2 >= 2.f) h_mod2 -= 2.f;

		float x = c * (1 - Math.abs(h_mod2 - 1));
		float r_, g_, b_;
		if (h_ < 1) {
			r_ = c;
			g_ = x;
			b_ = 0;
		} else if (h_ < 2) {
			r_ = x;
			g_ = c;
			b_ = 0;
		} else if (h_ < 3) {
			r_ = 0;
			g_ = c;
			b_ = x;
		} else if (h_ < 4) {
			r_ = 0;
			g_ = x;
			b_ = c;
		} else if (h_ < 5) {
			r_ = x;
			g_ = 0;
			b_ = c;
		} else {
			r_ = c;
			g_ = 0;
			b_ = x;
		}

		float m = l - (0.5f * c);
		int r = (int) ((r_ + m) * (255.f) + 0.5f);
		int g = (int) ((g_ + m) * (255.f) + 0.5f);
		int b = (int) ((b_ + m) * (255.f) + 0.5f);
		return r << 16 | g << 8 | b;
	}

	@SuppressWarnings("deprecation")
	public static void copyToClipboard(CharSequence text) {
		android.text.ClipboardManager clipboardManager = (android.text.ClipboardManager) App.context.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(text);
	}

	public static int getForegroundColorOnDarkBackgroundByBookId(int bookId) {
		if (bookId >= 0 && bookId < 39) { // OT
			return 0xff_ef5350; // Pink
		} else if (bookId >= 39 && bookId < 66) { // NT
			return 0xff_42a5f5; // Blue 400
		} else { // others
			return 0xff_eeeeee; // Grey 200
		}
	}

	public static int getBackgroundColorByBookId(int bookId) {
		if (bookId >= 0 && bookId < 39) { // OT
			return 0xff_e53935; // Red 600
		} else if (bookId >= 39 && bookId < 66) { // NT
			return 0xff_1e88e5; // Blue 600
		} else { // others
			return 0xff_212121; // Grey 900
		}
	}

	public static int getSearchKeywordTextColorByBrightness(float brightness) {
		if (brightness < 0.5f) {
			return 0xff_69f0ae; // Green A200
		} else {
			return 0xff_00c853; // Green A700
		}
	}

	public static boolean equals(Object a, Object b) {
		if (a == b) return true;
		if (a == null) return false;
		return a.equals(b);
	}

	public static int applyLabelColor(Label label, TextView view) {
		int bgColorRgb = U.decodeLabelBackgroundColor(label.backgroundColor);
		if (bgColorRgb == -1) {
			bgColorRgb = 0x212121; // default color Grey 900
		}

		GradientDrawable grad = null;

		Drawable bg = view.getBackground();
		if (bg instanceof GradientDrawable) {
			grad = (GradientDrawable) bg;
		} else if (bg instanceof StateListDrawable) {
			StateListDrawable states = (StateListDrawable) bg;
			Drawable current = states.getCurrent();
			if (current instanceof GradientDrawable) {
				grad = (GradientDrawable) current;
			}
		}
		if (grad != null) {
			grad.setColor(0xff000000 | bgColorRgb);
			final int labelColor = 0xff000000 | U.getLabelForegroundColorBasedOnBackgroundColor(bgColorRgb);
			view.setTextColor(labelColor);
			return labelColor;
		}
		return 0;
	}

	public static String inputStreamUtf8ToString(InputStream input) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final byte[] buf = new byte[1024];
		while (true) {
			final int read = input.read(buf);
			if (read < 0) break;
			baos.write(buf, 0, read);
		}
		return new String(baos.toByteArray(), Charset.forName("utf-8"));
	}

	/**
	 * The reason we use an installation id instead of just the simpleToken for sync
	 * to identify originating device, is so that the GCM messages does not contain
	 * simpleToken, which is sensitive.
	 */
	public synchronized static String getInstallationId() {
		final NoBackupSharedPreferences nbsp = NoBackupSharedPreferences.get();

		String res = Preferences.getString(Prefkey.installation_id, null);
		if (res == null) {
			res = nbsp.getString(Prefkey.installation_id.name());
			if (res == null) {
				res = "i1:" + UUID.randomUUID().toString();
				nbsp.setString(Prefkey.installation_id.name(), res);
			}
		} else {
			// we need to remove it from the backed up folder and move it to the nonbacked up folder
			Preferences.remove(Prefkey.installation_id);
			nbsp.setString(Prefkey.installation_id.name(), res);
		}

		return res;
	}

	public static int getTextColorForSelectedVerse(final int selectedVerseBgColor) {
		if (ColorUtils.calculateLuminance(selectedVerseBgColor) > 0.4) {
			return 0xff000000;
		} else {
			return 0xffffffff;
		}
	}

	public static void dumpIntent(final Intent intent, final String via) {
		AppLog.d(TAG, "Got intent via " + via);
		AppLog.d(TAG, "  action: " + intent.getAction());
		AppLog.d(TAG, "  data uri: " + intent.getData());
		AppLog.d(TAG, "  component: " + intent.getComponent());
		AppLog.d(TAG, "  flags: 0x" + Integer.toHexString(intent.getFlags()));
		AppLog.d(TAG, "  mime: " + intent.getType());
		final Bundle extras = intent.getExtras();
		AppLog.d(TAG, "  extras: " + (extras == null ? "null" : extras.size()));
		if (extras != null) {
			for (String key : extras.keySet()) {
				AppLog.d(TAG, "    " + key + " = " + extras.get(key));
			}
		}
	}

	static class InstallationInfoJson {
		public String installation_id;
		public String app_packageName;
		public int app_versionCode;
		public boolean app_debug;
		public String build_manufacturer;
		public String build_model;
		public String build_device;
		public String build_product;
		public int os_sdk_int;
		public String os_release;
		public String locale;
		public String last_commit_hash;
	}

	/**
	 * Return a JSON string that contains information about the app installation on this particular device.
	 */
	public static String getInstallationInfoJson() {
		final InstallationInfoJson obj = new InstallationInfoJson();
		obj.installation_id = getInstallationId();
		obj.app_packageName = App.context.getPackageName();
		obj.app_versionCode = App.getVersionCode();
		obj.app_debug = BuildConfig.DEBUG;
		obj.build_manufacturer = Build.MANUFACTURER;
		obj.build_model = Build.MODEL;
		obj.build_device = Build.DEVICE;
		obj.build_product = Build.PRODUCT;
		obj.os_sdk_int = Build.VERSION.SDK_INT;
		obj.os_release = Build.VERSION.RELEASE;
		final Locale locale = App.context.getResources().getConfiguration().locale;
		obj.locale = locale == null ? null : locale.toString();
		obj.last_commit_hash = App.context.getString(R.string.last_commit_hash);
		return App.getDefaultGson().toJson(obj);
	}

	public interface ThrowEverythingRunnable {
		void run() throws Exception;
	}

	public static void wontThrow(ThrowEverythingRunnable r) {
		try {
			r.run();
		} catch (Exception e) {
			throw new RuntimeException("ThrowEverythingRunnable is passed but caused exception: " + r.toString(), e);
		}
	}
}
