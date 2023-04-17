package yuku.alkitab.base.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import kotlin.Unit;
import yuku.alkitab.base.App;
import yuku.alkitab.base.widget.MaterialDialogJavaHelper;
import yuku.alkitab.debug.R;

public class OtherAppIntegration {
    public static void askToInstallDictionary(final Activity activity) {
        MaterialDialogJavaHelper.showOkDialog(
            activity,
            activity.getString(R.string.dict_download_prompt),
            activity.getString(R.string.dict_download_button),
            () -> {
                openMarket(activity, "org.sabda.kamus");
                return Unit.INSTANCE;
            }
        );
    }

    public static void openMarket(final Activity activity, final String packageName) {
        try {
            final Uri uri = Uri.parse("market://details?id=" + packageName + "&referrer=utm_source%3Dother_app%26utm_medium%3D" + activity.getPackageName());
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            MaterialDialogJavaHelper.showOkDialog(activity, activity.getString(R.string.google_play_store_not_installed));
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
