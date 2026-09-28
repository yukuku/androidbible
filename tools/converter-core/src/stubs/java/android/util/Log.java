package android.util;

public class Log {

	public static void d(final String tag, final String msg) {
		System.err.print(tag);
		System.err.print("/");
		System.err.println(msg);
	}

	public static void e(final String tag, final String msg) {
		e(tag, msg, null);
	}

	public static void e(final String tag, final String msg, final Throwable t) {
		System.err.print(tag);
		System.err.print("/");
		System.err.println(msg);
		if (t != null) {
			t.printStackTrace(System.err);
		}
	}
}
