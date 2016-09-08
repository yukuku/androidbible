package yuku.alkitab.base.ac.base;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.ChangeConfigurationHelper;
import yuku.alkitab.debug.R;

import java.util.concurrent.atomic.AtomicBoolean;

import static yuku.alkitab.base.util.Literals.Array;

public abstract class BaseActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
	public static final String TAG = BaseActivity.class.getSimpleName();

	private static final int REQCODE_PERMISSION_storage = 1;
	private static final int REQCODE_permissionSettings = 9970;

	private boolean willNeedStoragePermission;

	private int lastKnownConfigurationSerialNumber;

	@Override
	protected void onStart() {
		super.onStart();

		applyActionBarAndStatusBarColors();

		final int currentConfigurationSerialNumber = ChangeConfigurationHelper.getSerialCounter();
		if (lastKnownConfigurationSerialNumber != currentConfigurationSerialNumber) {
			Log.d(TAG, "Restarting activity " + getClass().getName() + " because of configuration change " + lastKnownConfigurationSerialNumber + " -> " + currentConfigurationSerialNumber);
			lastKnownConfigurationSerialNumber = currentConfigurationSerialNumber;
			recreate();
		}
	}

	protected void applyActionBarAndStatusBarColors() {
		// action bar color and status bar color are set based on night mode
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null) {
			if (Preferences.getBoolean(Prefkey.is_night_mode, false)) {
				actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.primary_night_mode)));

				if (Build.VERSION.SDK_INT >= 21) {
					getWindow().setStatusBarColor(0xff000000);
				}
			} else {
				final TypedValue tv = new TypedValue();
				getTheme().resolveAttribute(R.attr.colorPrimary, tv, true);
				actionBar.setBackgroundDrawable(new ColorDrawable(tv.data));

				if (Build.VERSION.SDK_INT >= 21) {
					getWindow().setStatusBarColor(getResources().getColor(R.color.primary_dark));
				}
			}
		}
	}

	/**
	 * Call this from subclasses before super.onCreate() to make
	 * the activity ask for storage permission and do not proceed
	 * if the permission is not granted.
	 */
	protected void willNeedStoragePermission() {
		this.willNeedStoragePermission = true;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		lastKnownConfigurationSerialNumber = ChangeConfigurationHelper.getSerialCounter();

		if (willNeedStoragePermission) {
			askStoragePermission();
		}
	}

	private void askStoragePermission() {
		if (!(
			Build.VERSION.SDK_INT < 16
			|| (
				ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
				&& ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
			)
		)) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				||ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)
				) {
				final AtomicBoolean oked = new AtomicBoolean(false);
				new MaterialDialog.Builder(this)
					.content(R.string.storage_permission_rationale)
					.positiveText(R.string.ok)
					.onPositive((materialDialog, dialogAction) -> {
						oked.set(true);
						ActivityCompat.requestPermissions(this, Array(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), REQCODE_PERMISSION_storage);
					})
					.dismissListener(dialog -> {
						if (!oked.get()) {
							finish();
						}
					})
					.show();
			} else {
				ActivityCompat.requestPermissions(this, Array(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), REQCODE_PERMISSION_storage);
			}
		} else {
			onNeededPermissionsGranted(true);
		}
	}

	/**
	 * Override this to do something after we confirm that all needed permissions are granted.
	 * This is only called if {@link #willNeedStoragePermission()} was called.
	 * @param immediatelyGranted whether the permission is granted immediately without leaving the first onCreate().
	 *                           Use this to determine whether we need to do initialization (e.g. load dir contents)
	 *                           and to determine whether it is safe to init now.
	 */
	protected void onNeededPermissionsGranted(boolean immediatelyGranted) {
	}

	@Override
	public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
		if (requestCode == REQCODE_PERMISSION_storage) {
			// all must be granted
			boolean allGranted = true;
			for (final int grantResult : grantResults) {
				if (grantResult != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
				}
			}

			if (allGranted) {
				onNeededPermissionsGranted(false);
			} else {
				if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
					|| !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {

					// user selects do not ask again
					final AtomicBoolean oked = new AtomicBoolean(false);
					new MaterialDialog.Builder(this)
						.content("You need to have the Storage permission enabled to continue, because we need to store shared media such as Bible versions and fonts.")
						.positiveText(R.string.ok)
						.onPositive((materialDialog, dialogAction) -> {
							oked.set(true);
							startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", getPackageName(), null)), REQCODE_permissionSettings);
						})
						.negativeText(R.string.cancel)
						.dismissListener(dialog -> {
							if (!oked.get()) {
								finish();
							}
						})
						.show();
				} else {
					finish();
				}
			}

			return;
		}

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_permissionSettings) {
			askStoragePermission();
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@CallSuper
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
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

	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		{ // reconfigure toolbar height (Need to have a toolbar with id toolbar)
			final View v = findViewById(R.id.toolbar);
			if (v instanceof Toolbar) {
				final Toolbar toolbar = (Toolbar) v;
				final ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
				final TypedValue tv = new TypedValue();
				getTheme().resolveAttribute(R.attr.actionBarSize, tv, true);
				final int h = (int) tv.getDimension(getResources().getDisplayMetrics());
				lp.height = h;
				toolbar.setLayoutParams(lp);
				// Workaround for https://code.google.com/p/android/issues/detail?id=79813
				toolbar.setMinimumHeight(h);
			}
		}
	}
}
