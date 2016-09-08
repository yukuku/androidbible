package yuku.alkitab.base.util;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import yuku.alkitab.base.App;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FontManager {
	public static final String TAG = FontManager.class.getSimpleName();

	public static class FontEntry {
		public String name;
	}

	public static class TypefaceCreateFromFileCacher {
		static ArrayList<String> keys = new ArrayList<>();
		static ArrayList<Typeface> values = new ArrayList<>();
		static int max = 9;

		public static Typeface createFromFile(String path) {
			for (int i = keys.size() - 1; i >= 0; i--) {
				String key = keys.get(i);
				if (key.equals(path)) {
					return values.get(i);
				}
			}

			Log.d(TAG, "TypefaceCreateFromFileCacher creating entry for " + path);
			Typeface typeface = Typeface.createFromFile(path);

			// cache too full?
			if (keys.size() >= max) {
				keys.remove(0);
				values.remove(0);
				Log.d(TAG, "TypefaceCreateFromFileCacher removed entry from cache because cache is too full");
			}
			keys.add(path);
			values.add(typeface);

			return typeface;
		}
	}

	private static File getFontsDir() {
		final File res = new File(App.context.getFilesDir(), "bible/fonts");
		if (!res.exists()) {
			res.mkdirs();
		}
		return res;
	}

	private static File getLegacyFontsDir() {
		final File res = new File(Environment.getExternalStorageDirectory(), "bible/fonts");
		if (!res.exists()) {
			res.mkdirs();
		}
		return res;
	}

	public static Typeface getRegular(String name) {
		final File file = getRegularFontFile(name);
		if (file != null) {
			return TypefaceCreateFromFileCacher.createFromFile(file.getAbsolutePath());
		}
		return null;
	}

	public static File getFontDir(String name) {
		return new File(getFontsDir(), name);
	}

	@Nullable private static File getRegularFontFile(String name) {
		final String dfn = getRegularFilename(name);
		{
			final File res = new File(getFontsDir(), dfn);
			if (res.exists() && res.canRead()) {
				return res;
			}
		}
		{
			final File res = new File(getLegacyFontsDir(), dfn);
			if (res.exists() && res.canRead()) {
				return res;
			}
		}
		return null;
	}

	@NonNull
	private static String getRegularFilename(final String name) {
		return name + "/" + name + "-Regular.ttf";
	}

	public static boolean isInstalled(String name) {
		return getRegularFontFile(name) != null;
	}

	public static List<FontEntry> getInstalledFonts() {
		final List<FontEntry> res = new ArrayList<>();

		final List<File> dirs = new ArrayList<>();

		// enum the bible/fonts directory
		addAllFontDirs(dirs, getFontsDir());
		addAllFontDirs(dirs, getLegacyFontsDir());

		Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

		for (File dir : dirs) {
			final FontEntry e = new FontEntry();
			e.name = dir.getName();
			res.add(e);
		}

		return res;
	}

	private static void addAllFontDirs(@NonNull final List<File> fontDirs, final File fontsDir) {
		if (!fontsDir.exists() || !fontsDir.isDirectory()) {
			return;
		}

		final File[] ds = fontsDir.listFiles(pathname -> {
			if (!pathname.isDirectory()) return false;
			final String basename = pathname.getName();
			final File file = new File(fontsDir, getRegularFilename(basename));
			if (!file.exists()) {
				Log.d(TAG, "Font dir " + pathname.getAbsolutePath() + " exists but " + file.getAbsolutePath() + " doesn't");
				return false;
			} else {
				return true;
			}
		});

		if (ds != null) Collections.addAll(fontDirs, ds);
	}

	public static Typeface typeface(String name) {
		Typeface res;
		if (name == null || name.equals("DEFAULT") || name.equals("SANS_SERIF") || name.equals("<ADD>")) res = Typeface.SANS_SERIF;
		else if (name.equals("SERIF")) res = Typeface.SERIF;
		else if (name.equals("MONOSPACE")) res = Typeface.MONOSPACE;
		else {
			res = getRegular(name);
			if (res == null) {
				Log.w(TAG, "Failed to load font named " + name + " fallback to SANS_SERIF");
				res = Typeface.SANS_SERIF;
			}
		}
		return res;
	}

	public static boolean isCustomFont(String name) {
		return !(name == null || name.equals("DEFAULT") || name.equals("SANS_SERIF") || name.equals("SERIF") || name.equals("MONOSPACE"));
	}

	public static String getCustomFontUri(String name) {
		File path = getRegularFontFile(name);
		return Uri.fromFile(path).toString();
	}
}
