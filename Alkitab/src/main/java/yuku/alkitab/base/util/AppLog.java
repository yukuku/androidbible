package yuku.alkitab.base.util;

import android.util.Log;
import com.crashlytics.android.Crashlytics;

public class AppLog {
	public static void d(String tag, String msg) {
		Crashlytics.log(Log.DEBUG, tag, msg);
	}

	public static void d(String tag, String msg, Throwable ex) {
		Crashlytics.log(Log.DEBUG, tag, msg);
		Crashlytics.logException(ex);
		Log.d(tag, msg, ex);
	}

	public static void i(String tag, String msg) {
		Crashlytics.log(Log.INFO, tag, msg);
	}

	public static void i(String tag, String msg, Throwable ex) {
		Crashlytics.log(Log.INFO, tag, msg);
		Crashlytics.logException(ex);
		Log.i(tag, msg, ex);
	}

	public static void w(String tag, String msg) {
		Crashlytics.log(Log.WARN, tag, msg);
	}

	public static void w(String tag, String msg, Throwable ex) {
		Crashlytics.log(Log.WARN, tag, msg);
		Crashlytics.logException(ex);
		Log.w(tag, msg, ex);
	}

	public static void e(String tag, String msg) {
		Crashlytics.log(Log.ERROR, tag, msg);
	}

	public static void e(String tag, String msg, Throwable ex) {
		Crashlytics.log(Log.ERROR, tag, msg);
		Crashlytics.logException(ex);
		Log.e(tag, msg, ex);
	}
}
