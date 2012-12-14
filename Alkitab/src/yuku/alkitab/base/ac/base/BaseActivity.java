package yuku.alkitab.base.ac.base;

import android.content.Context;
import android.content.Intent;

import yuku.alkitab.base.IsiActivity;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.analytics.tracking.android.EasyTracker;

public abstract class BaseActivity extends SherlockFragmentActivity {
	public static final String TAG = BaseActivity.class.getSimpleName();
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
            backToRootActivity(this);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override protected void onStart() {
		super.onStart();
	    EasyTracker.getInstance().activityStart(this); 
	}
	
	@Override protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	public static void backToRootActivity(Context context) {
		Intent intent = new Intent(context, IsiActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(intent);
	}
}
