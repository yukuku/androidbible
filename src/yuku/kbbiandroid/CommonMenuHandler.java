package yuku.kbbiandroid;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.*;
import android.net.*;
import android.text.*;
import android.util.*;
import android.view.*;

public class CommonMenuHandler {
	private final Context context;

	public CommonMenuHandler(Context context) {
		this.context = context;
	}

	public boolean handle(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menuTentang) {
	    	String verName = "null";
	    	int verCode = -1;
	    	
			try {
				PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
				verName = packageInfo.versionName;
				verCode = packageInfo.versionCode;
			} catch (NameNotFoundException e) {
				Log.e("kbbiandroid", "NameNotFoundException???", e);
			}
	    	
	    	new AlertDialog.Builder(context).setTitle(context.getString(R.string.tentang_n))
	    	.setMessage(Html.fromHtml(context.getString(R.string.tentangMessage_s, verName, verCode)))
	    	.setPositiveButton(R.string.kembali_v, null)
	    	.show();
	    	
	    	return true;
		} else if (item.getItemId() == R.id.menuCheckUpdate) {
			
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
				context.startActivity(intent);
			} catch (ActivityNotFoundException e) {
				new AlertDialog.Builder(context)
		    	.setMessage(R.string.marketGaAda_s)
		    	.show();
			}
			
			return true;
		}
		
		return false;
	}
	
	
}
