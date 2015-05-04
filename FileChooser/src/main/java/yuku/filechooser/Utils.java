package yuku.filechooser;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

public class Utils {
	public static final String TAG = Utils.class.getSimpleName();

	public static void configureTitles(AppCompatActivity activity, String title, String subtitle) {
		if (title == null) {
			activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
		} else {
			if (activity.getSupportActionBar() != null) {
				final ActionBar actionBar = activity.getSupportActionBar();
				actionBar.setDisplayShowTitleEnabled(true);
				actionBar.setTitle(title);
				actionBar.setSubtitle(subtitle);
			}
		}
	}
}
