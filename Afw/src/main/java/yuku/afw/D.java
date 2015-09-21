package yuku.afw;

import android.content.pm.ApplicationInfo;

/**
 * Debug switch
 */
public class D {
	public static final String TAG = D.class.getSimpleName();

	public static boolean EBUG; // value depends on the static init BELOW.
	
	static {
		if (App.context == null) {
			throw new RuntimeException("D is called before App. Something is wrong!");
		}

		D.EBUG = ((App.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
	}
}
