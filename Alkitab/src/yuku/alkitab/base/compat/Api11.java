package yuku.alkitab.base.compat;

import android.app.AlertDialog;
import android.content.Context;

public class Api11 {
	public static final String TAG = Api11.class.getSimpleName();
	
	public static int getTheme_Holo() {
		return android.R.style.Theme_Holo;
	}
	
	public static Context AlertDialog_Builder_getContext(AlertDialog.Builder builder) {
		return builder.getContext();
	}
}
