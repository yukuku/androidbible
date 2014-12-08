package yuku.alkitab.base.ac.base;

import android.content.Intent;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;
import com.google.analytics.tracking.android.EasyTracker;

public abstract class BaseActivity extends ActionBarActivity {
	public static final String TAG = BaseActivity.class.getSimpleName();

	@Override protected void onStart() {
		super.onStart();
	    EasyTracker.getInstance(this).activityStart(this);
	}
	
	@Override protected void onStop() {
		super.onStop();
		EasyTracker.getInstance(this).activityStop(this);
	}

	protected void navigateUp() {
		final Intent upIntent = NavUtils.getParentActivityIntent(this);
		if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
			TaskStackBuilder.create(this)
				.addNextIntentWithParentStack(upIntent)
				.startActivities();
		} else {
			NavUtils.navigateUpTo(this, upIntent);
		}
	}
}
