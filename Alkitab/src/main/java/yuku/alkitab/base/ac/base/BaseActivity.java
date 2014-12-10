package yuku.alkitab.base.ac.base;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;

import yuku.alkitab.debug.R;

public abstract class BaseActivity extends ActionBarActivity {
	public static final String TAG = BaseActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }
    }

    protected void navigateUp() {
		final Intent upIntent = NavUtils.getParentActivityIntent(this);
		if (upIntent == null) { // not defined in manifest, let us finish() instead.
			finish();
			return;
		}

		if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
			TaskStackBuilder.create(this)
				.addNextIntentWithParentStack(upIntent)
				.startActivities();
		} else {
			NavUtils.navigateUpTo(this, upIntent);
		}
	}
}
