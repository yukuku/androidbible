package yuku.alkitab.base.util;

import android.os.Environment;

import java.io.File;

public class AddonManager {
	public static final String TAG = AddonManager.class.getSimpleName();

	public static String getYesPath() {
		return new File(Environment.getExternalStorageDirectory(), "bible/yes").getAbsolutePath(); //$NON-NLS-1$
	}

	/**
	 * @param yesName an added yes filename without the path.
	 */
	public static String getVersionPath(String yesName) {
		String yesPath = getYesPath();
		File yes = new File(yesPath, yesName);
		return yes.getAbsolutePath();
	}
	
	public static boolean hasVersion(String yesName) {
		File f = new File(getVersionPath(yesName));
		return f.exists() && f.canRead();
	}

	public static boolean mkYesDir() {
		File dir = new File(getYesPath());
		if (!dir.exists()) {
			return new File(getYesPath()).mkdirs();
		} else {
			return true;
		}
	}

}
