package yuku.alkitab.base.ac.base;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import yuku.alkitab.debug.R;

public abstract class BaseActivity extends AppCompatActivity {
	public static final String TAG = BaseActivity.class.getSimpleName();

	private boolean withNonToolbarUpButton;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
        }
    }

	protected void onCreateWithNonToolbarUpButton(Bundle savedInstanceState) {
		this.withNonToolbarUpButton = true;

		super.onCreate(savedInstanceState);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (withNonToolbarUpButton && item.getItemId() == android.R.id.home) {
			navigateUp();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

    protected void navigateUp() {
		final Intent upIntent = NavUtils.getParentActivityIntent(this);
		if (upIntent == null) { // not defined in manifest, let us finish() instead.
			finish();
			return;
		}

		if (NavUtils.shouldUpRecreateTask(this, upIntent) || isTaskRoot()) {
			TaskStackBuilder.create(this)
				.addNextIntentWithParentStack(upIntent)
				.startActivities();
		} else {
			NavUtils.navigateUpTo(this, upIntent);
		}
	}
}
