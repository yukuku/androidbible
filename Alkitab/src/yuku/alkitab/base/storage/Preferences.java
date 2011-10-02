package yuku.alkitab.base.storage;

import android.content.*;
import android.preference.*;
import android.util.*;

import yuku.alkitab.base.*;

public class Preferences {
	private static final String TAG = Preferences.class.getSimpleName();
	
	private static SharedPreferences cache;
	private static boolean dirty = true;
	
	public static void invalidate() {
		dirty = true;
	}
	
	public static int getInt(Prefkey key, int def) {
		SharedPreferences pref = read(App.context);
		return pref.getInt(key.toString(), def);
	}
	
	public static float getFloat(Prefkey key, float def) {
		SharedPreferences pref = read(App.context);
		return pref.getFloat(key.toString(), def);
	}
	
	public static long getLong(Prefkey key, long def) {
		SharedPreferences pref = read(App.context);
		return pref.getLong(key.toString(), def);
	}
	
	public static String getString(Prefkey key, String def) {
		SharedPreferences pref = read(App.context);
		return pref.getString(key.toString(), def);
	}
	
	public static boolean getBoolean(Prefkey key, boolean def) {
		SharedPreferences pref = read(App.context);
		return pref.getBoolean(key.toString(), def);
	}
	
	public static boolean getBoolean(String key, boolean def) {
		SharedPreferences pref = read(App.context);
		return pref.getBoolean(key, def);
	}
	
	public static int getInt(int keyResId, int defResId) {
		SharedPreferences pref = read(App.context);
		return pref.getInt(App.context.getString(keyResId), App.context.getResources().getInteger(defResId));
	}
	
	public static float getFloat(int keyResId, float def) {
		SharedPreferences pref = read(App.context);
		return pref.getFloat(App.context.getString(keyResId), def);
	}
	
	public static String getString(int keyResId, int defResId) {
		SharedPreferences pref = read(App.context);
		return pref.getString(App.context.getString(keyResId), App.context.getString(defResId));
	}
	
	public static String getString(int keyResId, String def) {
		SharedPreferences pref = read(App.context);
		return pref.getString(App.context.getString(keyResId), def);
	}
	
	public static boolean getBoolean(int keyResId, int defResId) {
		SharedPreferences pref = read(App.context);
		return pref.getBoolean(App.context.getString(keyResId), App.context.getResources().getBoolean(defResId));
	}
	
	
	public static void setInt(Prefkey key, int val) {
		SharedPreferences pref = read(App.context);
		pref.edit().putInt(key.toString(), val).commit();
		Log.d(TAG, key + " = (int) " + val); //$NON-NLS-1$
	}
	
	public static void setLong(Prefkey key, long val) {
		SharedPreferences pref = read(App.context);
		pref.edit().putLong(key.toString(), val).commit();
		Log.d(TAG, key + " = (long) " + val); //$NON-NLS-1$
	}
	
	public static void setString(Prefkey key, String val) {
		SharedPreferences pref = read(App.context);
		pref.edit().putString(key.toString(), val).commit();
		Log.d(TAG, key + " = (string) " + val); //$NON-NLS-1$
	}
	
	public static void setBoolean(Prefkey key, boolean val) {
		SharedPreferences pref = read(App.context);
		pref.edit().putBoolean(key.toString(), val).commit();
		Log.d(TAG, key + " = (bool) " + val); //$NON-NLS-1$
	}
	
	public static void setBoolean(String key, boolean val) {
		SharedPreferences pref = read(App.context);
		pref.edit().putBoolean(key, val).commit();
		Log.d(TAG, key + " = (bool) " + val); //$NON-NLS-1$
	}
	
	public static void remove(Prefkey key) {
		SharedPreferences pref = read(App.context);
		pref.edit().remove(key.toString()).commit();
		Log.d(TAG, key + " removed"); //$NON-NLS-1$
	}
	
	private static SharedPreferences read(Context context) {
		SharedPreferences res;
		if (dirty || cache == null) {
			Log.d(TAG, "Preferences are read from disk"); //$NON-NLS-1$
			res = PreferenceManager.getDefaultSharedPreferences(App.context);
			dirty = false;
			cache = res;
		} else {
			res = cache;
		}
		
		return res;
	}
}
