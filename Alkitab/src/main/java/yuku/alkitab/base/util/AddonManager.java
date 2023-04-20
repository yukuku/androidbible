package yuku.alkitab.base.util;

import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import yuku.alkitab.base.App;

import java.io.File;

public class AddonManager {
	private static File getYesDir() {
		final File res = new File(App.context.getFilesDir(), "bible/yes");
		if (!res.exists()) {
			res.mkdirs();
		}
		return res;
	}

	private static File getLegacyYesDir() {
		final File res = new File(Environment.getExternalStorageDirectory(), "bible/yes");
		if (!res.exists()) {
			res.mkdirs();
		}
		return res;
	}

	/**
	 * @param yesName a yes filename without the path, but with extension.
	 * @return null if no such file exists both on normal yes dir and legacy yes dir.
	 */
	@Nullable
	public static File getReadableVersionFile(String yesName) {
		{
			final File yes = new File(getYesDir(), yesName);
			if (yes.exists() && yes.canRead()) {
				return yes;
			}
		}

		{ // try legacy
			final File yes = new File(getLegacyYesDir(), yesName);
			if (yes.exists() && yes.canRead()) {
				return yes;
			}
		}

		return null;
	}

	/**
	 * @param yesName a yes filename without the path, but with extension.
	 */
	@NonNull
	public static File getWritableVersionFile(String yesName) {
		return new File(getYesDir(), yesName);
	}
}
