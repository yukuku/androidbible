package yuku.afw.storage;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.StringRes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import yuku.afw.App;

public class Preferences {
	private static final String TAG = Preferences.class.getSimpleName();

	private static SharedPreferences cache;
	private static boolean dirty = true;
	private static SharedPreferences.Editor currentEditor;
	private static int held = 0;
	private static WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Void> observers = new WeakHashMap<>();

	public static void invalidate() {
		dirty = true;
	}

	private static SharedPreferences.Editor getEditor(SharedPreferences pref) {
		if (currentEditor == null) {
			currentEditor = pref.edit();
		}
		return currentEditor;
	}

	public static int getInt(Enum<?> key, int def) {
		return getInt(key.toString(), def);
	}

	public static int getInt(String key, int def) {
		SharedPreferences pref = read();
		return pref.getInt(key, def);
	}

	public static float getFloat(Enum<?> key, float def) {
		SharedPreferences pref = read();
		return pref.getFloat(key.toString(), def);
	}

	public static float getFloat(String key, float def) {
		SharedPreferences pref = read();
		return pref.getFloat(key, def);
	}

	public static long getLong(Enum<?> key, long def) {
		return getLong(key.toString(), def);
	}

	public static long getLong(String key, long def) {
		SharedPreferences pref = read();
		return pref.getLong(key, def);
	}

	public static String getString(Enum<?> key, String def) {
		SharedPreferences pref = read();
		return pref.getString(key.toString(), def);
	}

	public static String getString(String key, String def) {
		SharedPreferences pref = read();
		return pref.getString(key, def);
	}

	public static String getString(Enum<?> key) {
		SharedPreferences pref = read();
		return pref.getString(key.toString(), null);
	}

	public static String getString(String key) {
		SharedPreferences pref = read();
		return pref.getString(key, null);
	}

	public static boolean getBoolean(Enum<?> key, boolean def) {
		return getBoolean(key.toString(), def);
	}

	public static boolean getBoolean(String key, boolean def) {
		SharedPreferences pref = read();
		return pref.getBoolean(key, def);
	}

	public static Object get(String key) {
		SharedPreferences pref = read();
		return pref.getAll().get(key);
	}

	public static Map<String, ?> getAll() {
		SharedPreferences pref = read();
		return pref.getAll();
	}

	public static Set<String> getAllKeys() {
		return new HashSet<>(getAll().keySet());
	}

	public static void setInt(Enum<?> key, int val) {
		setInt(key.toString(), val);
	}

	public static void setInt(String key, int val) {
		SharedPreferences pref = read();
		getEditor(pref).putInt(key, val);
		commitIfNotHeld();
	}

	public static void setFloat(Enum<?> key, float val) {
		setFloat(key.toString(), val);
	}

	public static void setFloat(String key, float val) {
		SharedPreferences pref = read();
		getEditor(pref).putFloat(key, val);
		commitIfNotHeld();
	}

	public static void setLong(Enum<?> key, long val) {
		setLong(key.toString(), val);
	}

	public static void setLong(String key, long val) {
		SharedPreferences pref = read();
		getEditor(pref).putLong(key, val);
		commitIfNotHeld();
	}

	public static void setString(Enum<?> key, String val) {
		setString(key.toString(), val);
	}

	public static void setString(String key, String val) {
		SharedPreferences pref = read();
		getEditor(pref).putString(key, val);
		commitIfNotHeld();
	}

	public static void setString(@StringRes final int keyStringResId, String val) {
		final Resources r = App.context.getResources();
		final String key = r.getString(keyStringResId);
		setString(key, val);
	}

	public static void setBoolean(Enum<?> key, boolean val) {
		setBoolean(key.toString(), val);
	}

	public static void setBoolean(String key, boolean val) {
		SharedPreferences pref = read();
		getEditor(pref).putBoolean(key, val);
		commitIfNotHeld();
	}

	public static boolean contains(final Enum<?> key) {
		SharedPreferences pref = read();
		return pref.contains(key.toString());
	}

	public static boolean contains(final String key) {
		SharedPreferences pref = read();
		return pref.contains(key);
	}

	public static void remove(Enum<?> key) {
		remove(key.toString());
	}

	public static void remove(String key) {
		SharedPreferences pref = read();
		getEditor(pref).remove(key);
		commitIfNotHeld();
	}

	public static int getInt(@StringRes final int keyStringResId, @IntegerRes final int defaultIntResId) {
		final Resources r = App.context.getResources();
		final String key = r.getString(keyStringResId);
		final Object value = get(key);
		if (value == null) {
			return r.getInteger(defaultIntResId);
		} else {
			return (int) value;
		}
	}

	public static boolean getBoolean(@StringRes final int keyStringResId, @BoolRes final int defaultBoolResId) {
		final Resources r = App.context.getResources();
		final String key = r.getString(keyStringResId);
		final Object value = get(key);
		if (value == null) {
			return r.getBoolean(defaultBoolResId);
		} else {
			return (boolean) value;
		}
	}

	public static String getString(@StringRes final int keyStringResId, @StringRes final int defaultStringResId) {
		final Resources r = App.context.getResources();
		final String key = r.getString(keyStringResId);
		final Object value = get(key);
		if (value == null) {
			return r.getString(defaultStringResId);
		} else {
			return (String) value;
		}
	}

	public static String getString(@StringRes final int keyStringResId) {
		final Resources r = App.context.getResources();
		final String key = r.getString(keyStringResId);
		final Object value = get(key);
		if (value == null) {
			return null;
		} else {
			return (String) value;
		}
	}

	private synchronized static void commitIfNotHeld() {
		if (held > 0) {
			// don't do anything now
		} else {
			if (currentEditor != null) {
				currentEditor.apply();
				currentEditor = null;
			}
		}
	}

	public synchronized static void hold() {
		held++;
	}

	public synchronized static void unhold() {
		if (held <= 0) {
			throw new RuntimeException("unhold called too many times");
		}
		held--;
		if (held == 0) {
			if (currentEditor != null) {
				currentEditor.apply();
				currentEditor = null;
			}
		}
	}

	public synchronized static void registerObserver(final SharedPreferences.OnSharedPreferenceChangeListener observer) {
		SharedPreferences pref = read();
		pref.registerOnSharedPreferenceChangeListener(observer);
		observers.put(observer, null);
	}

	public synchronized static void unregisterObserver(final SharedPreferences.OnSharedPreferenceChangeListener observer) {
		SharedPreferences pref = read();
		pref.unregisterOnSharedPreferenceChangeListener(observer);
		observers.remove(observer);
	}

	private synchronized static SharedPreferences read() {
		SharedPreferences res;
		if (dirty || cache == null) {
			res = PreferenceManager.getDefaultSharedPreferences(App.context);
			dirty = false;

			// re-register observers if the SharedPreferences object changes
			if (cache != null && res != cache && observers.size() > 0) {
				for (final SharedPreferences.OnSharedPreferenceChangeListener observer : observers.keySet()) {
					cache.unregisterOnSharedPreferenceChangeListener(observer);
					res.registerOnSharedPreferenceChangeListener(observer);
				}
			}

			cache = res;
		} else {
			res = cache;
		}

		return res;
	}
}
