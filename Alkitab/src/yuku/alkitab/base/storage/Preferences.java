package yuku.alkitab.base.storage;

import android.content.*;
import android.preference.PreferenceManager;
import android.util.Log;

public class Preferences {
	private static final String TAG = Preferences.class.getSimpleName();
	
	private static Context appContext;
	private static SharedPreferences cache;
	private static boolean dirty = true;
	
	public static void invalidate() {
		dirty = true;
	}
	
	public static int getInt(Prefkey key, int def) {
		SharedPreferences pref = read(appContext);
		return pref.getInt(key.toString(), def);
	}
	
	public static float getFloat(Prefkey key, float def) {
		SharedPreferences pref = read(appContext);
		return pref.getFloat(key.toString(), def);
	}
	
	public static long getLong(Prefkey key, long def) {
		SharedPreferences pref = read(appContext);
		return pref.getLong(key.toString(), def);
	}
	
	public static String getString(Prefkey key, String def) {
		SharedPreferences pref = read(appContext);
		return pref.getString(key.toString(), def);
	}
	
	public static boolean getBoolean(Prefkey key, boolean def) {
		SharedPreferences pref = read(appContext);
		return pref.getBoolean(key.toString(), def);
	}
	
	
	public static int getInt(int keyResId, int defResId) {
		SharedPreferences pref = read(appContext);
		return pref.getInt(appContext.getString(keyResId), appContext.getResources().getInteger(defResId));
	}
	
	public static float getFloat(int keyResId, float def) {
		SharedPreferences pref = read(appContext);
		return pref.getFloat(appContext.getString(keyResId), def);
	}
	
	public static String getString(int keyResId, int defResId) {
		SharedPreferences pref = read(appContext);
		return pref.getString(appContext.getString(keyResId), appContext.getString(defResId));
	}
	
	public static String getString(int keyResId, String def) {
		SharedPreferences pref = read(appContext);
		return pref.getString(appContext.getString(keyResId), def);
	}
	
	public static boolean getBoolean(int keyResId, int defResId) {
		SharedPreferences pref = read(appContext);
		return pref.getBoolean(appContext.getString(keyResId), appContext.getResources().getBoolean(defResId));
	}
	
	
	public static void setInt(Prefkey key, int val) {
		SharedPreferences pref = read(appContext);
		pref.edit().putInt(key.toString(), val).commit();
		Log.d(TAG, key + " = (int) " + val);
	}
	
	public static void setLong(Prefkey key, long val) {
		SharedPreferences pref = read(appContext);
		pref.edit().putLong(key.toString(), val).commit();
		Log.d(TAG, key + " = (long) " + val);
	}
	
	public static void setString(Prefkey key, String val) {
		SharedPreferences pref = read(appContext);
		pref.edit().putString(key.toString(), val).commit();
		Log.d(TAG, key + " = (string) " + val);
	}
	
	public static void setBoolean(Prefkey key, boolean val) {
		SharedPreferences pref = read(appContext);
		pref.edit().putBoolean(key.toString(), val).commit();
		Log.d(TAG, key + " = (bool) " + val);
	}
	
	public static void remove(Prefkey key) {
		SharedPreferences pref = read(appContext);
		pref.edit().remove(key.toString()).commit();
		Log.d(TAG, key + " removed");
	}
	
	private static SharedPreferences read(Context context) {
		SharedPreferences res;
		if (dirty || cache == null) {
			Log.d(TAG, "Preferences are (re-)read from disk");
			res = PreferenceManager.getDefaultSharedPreferences(appContext);
			dirty = false;
			cache = res;
			
			// debug
//			{
//				Log.d(TAG, "isi preferences: ===========");
//				
//				Map<String, ?> all = res.getAll();
//				for (Entry<String, ?> e: all.entrySet()) {
//					Log.d(TAG, e.getKey() + " = (" + e.getValue().getClass().getName() + ") " + e.getValue());
//				}
//			}
		} else {
			res = cache;
		}
		
		return res;
	}

	public static void setAppContext(Context appContext) {
		Preferences.appContext = appContext;
	}
}
