package yuku.filechooser;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.view.Window;

public class Utils {
	public static final String TAG = Utils.class.getSimpleName();

	@TargetApi(11) public static void configureTitles(Activity activity, String title, String subtitle) {
		if (title == null) {
			activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
		} else {
			if (Build.VERSION.SDK_INT >= 11 && activity.getActionBar() != null) {
				ActionBar actionBar = activity.getActionBar();
				actionBar.setDisplayShowTitleEnabled(true);
				actionBar.setTitle(title);
				actionBar.setSubtitle(subtitle);
			} else {
				if (subtitle != null) {
					SpannableStringBuilder sb = new SpannableStringBuilder();
					sb.append(title);
					sb.append("\n");
					int sb_len = sb.length();
					sb.append(subtitle);
					sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
					activity.setTitle(sb);
				} else {
					activity.setTitle(title);
				}
			}
		}
	}
}
