package yuku.alkitab.base.util;

import android.graphics.Typeface;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FontManager {
	public static final String TAG = FontManager.class.getSimpleName();

	public static class FontEntry {
		public String name;
		public String title;
		public String dir;
		public String regularPath;
		public String italicPath;
		public String boldPath;
		public String boldItalicPath;
	}
	
	public static class TypefaceCreateFromFileCacher {
		static ArrayList<String> keys = new ArrayList<String>();
		static ArrayList<Typeface> values = new ArrayList<Typeface>();
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

	public static String getFontsPath() {
		return new File(Environment.getExternalStorageDirectory(), "bible/fonts").getAbsolutePath(); //$NON-NLS-1$
	}
	
	public static Typeface getRegular(String name) {
		File file = getRegularPath(name);
		if (file.exists() && file.canRead()) {
			return TypefaceCreateFromFileCacher.createFromFile(file.getAbsolutePath());
		}
		return null;
	}
	
	public static File getFontDir(String name) {
		return new File(getFontsPath(), name);
	}

	private static File getRegularPath(String name) {
		return new File(getFontsPath(), name + "/" + name + "-Regular.ttf");
	}
	
	public static boolean isInstalled(String name) {
		return getRegularPath(name).exists();
	}
	
	public static List<FontEntry> getInstalledFonts() {
		List<FontEntry> res = new ArrayList<FontEntry>();
		
		// enum the bible/fonts directory
    	File fontsDir = new File(getFontsPath());
    	if (!fontsDir.exists()) {
    		fontsDir.mkdirs();
    	}
    	if (!fontsDir.exists() || !fontsDir.isDirectory()) {
    		// NOP, we can't do anything about it
    		return res;
    	}
    	
    	File[] dirs = fontsDir.listFiles(new FileFilter() {
			@Override public boolean accept(File pathname) {
				if (!pathname.isDirectory()) return false;
				String basename = pathname.getName();
				File ttf = getRegularPath(basename);
				if (!ttf.exists()) {
					Log.d(TAG, "Font dir " + pathname.getAbsolutePath() + " exists but " + ttf.getAbsolutePath() + " doesn't");
					return false;
				} else {
					return true;
				}
			}
		});
    	
    	Arrays.sort(dirs);
    	
    	for (File dir: dirs) {
    		FontEntry e = new FontEntry();
    		e.name = dir.getName(); 
    		e.title = dir.getName(); // TODO more friendly
    		e.dir = dir.getAbsolutePath();
    		String basename = dir.getName();
    		e.regularPath = getRegularPath(basename).getAbsolutePath();
    		// TODO italic etc
    		res.add(e);
    	}
    	
    	return res;
	}
	
	public static Typeface typeface(String name) {
		Typeface res;
		if (name == null || name.equals("DEFAULT") || name.equals("SANS_SERIF") || name.equals("<ADD>")) res = Typeface.SANS_SERIF; //$NON-NLS-1$
		else if (name.equals("SERIF")) res = Typeface.SERIF; //$NON-NLS-1$
		else if (name.equals("MONOSPACE")) res = Typeface.MONOSPACE; //$NON-NLS-1$
		else {
			res = getRegular(name);
			if (res == null) { 
				Log.w(TAG, "Failed to load font named " + name + " fallback to SANS_SERIF");
				res = Typeface.SANS_SERIF;
			}
		}
		return res;
	}
}
