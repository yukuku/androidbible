package yuku.alkitab.base.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.alkitab.base.App;
import yuku.alkitab.debug.R;

public class OtherAppIntegration {
	public static void askToInstallDictionary(final Activity activity) {
		new MaterialDialog.Builder(activity)
			.content(R.string.dict_download_prompt)
			.positiveText(R.string.dict_download_button)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onPositive(final MaterialDialog dialog) {
					openMarket(activity, "org.sabda.kamus");
				}
			})
			.show();
	}

	public static void openMarket(final Activity activity, final String packageName) {
		try {
			final Uri uri = Uri.parse("market://details?id=" + packageName + "&referrer=utm_source%3Dother_app%26utm_medium%3D" + activity.getPackageName());
			activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
		} catch (ActivityNotFoundException e) {
			new MaterialDialog.Builder(activity)
				.content(R.string.google_play_store_not_installed)
				.positiveText(R.string.ok)
				.show();
		}
	}

	public static boolean hasIntegratedDictionaryApp() {
		try {
			final PackageInfo info = App.context.getPackageManager().getPackageInfo("org.sabda.kamus", 0);
			if (info.versionCode < 4) {
				return false;
			}
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}
}
