package yuku.alkitab.base.compat;

import android.widget.*;

public class Api8 {
	public static final String TAG = Api8.class.getSimpleName();

	public static void ListView_smoothScrollToPosition(ListView lv, int position) {
		lv.smoothScrollToPosition(position);
	}
}
