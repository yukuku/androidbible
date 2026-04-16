package android.util;

/**
 * Test-only replacement for {@code android.util.Log}.
 * <p>
 * The Android unit-test stub jar throws {@code RuntimeException("Method not mocked")}
 * for every {@code Log} method, which prevents testing any production code that logs on
 * an error path (e.g. {@link yuku.alkitab.base.util.AppLog}, which is itself used by
 * {@link yuku.alkitab.base.util.Highlights#decode(String)}). This file provides real
 * no-op implementations visible only on the unit-test classpath. The shape matches the
 * Android SDK's {@code Log} so production code behaves identically.
 */
public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, Throwable tr) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg) { return 0; }
    public static int wtf(String tag, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg, Throwable tr) { return 0; }
    public static String getStackTraceString(Throwable tr) { return ""; }
    public static int println(int priority, String tag, String msg) { return 0; }
    public static boolean isLoggable(String tag, int level) { return false; }
}
