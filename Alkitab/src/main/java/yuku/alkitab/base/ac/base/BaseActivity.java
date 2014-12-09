package yuku.alkitab.base.ac.base;

import android.content.Intent;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;

public abstract class BaseActivity extends ActionBarActivity {
	public static final String TAG = BaseActivity.class.getSimpleName();

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
