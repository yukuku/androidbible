package yuku.alkitab.base.ac.base

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.RequestCodes
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.base.widget.Localized
import yuku.alkitab.debug.R

private const val TAG = "BaseActivity"

abstract class BaseActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private var willNeedStoragePermission: Boolean = false

    private var lastKnownConfigurationSerialNumber = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ConfigurationWrapper.wrap(base))
    }

    override fun onStart() {
        super.onStart()

        applyNightModeColors()

        val currentConfigurationSerialNumber = ConfigurationWrapper.getSerialCounter()
        if (lastKnownConfigurationSerialNumber != currentConfigurationSerialNumber) {
            AppLog.d(TAG, "Restarting activity ${javaClass.name} because of configuration change $lastKnownConfigurationSerialNumber -> $currentConfigurationSerialNumber")
            lastKnownConfigurationSerialNumber = currentConfigurationSerialNumber
            recreate()
        }
    }

    protected fun applyNightModeColors() {
        // action bar color, status bar color, backforward buttons color
        val isNightMode = Preferences.getBoolean(Prefkey.is_night_mode, false)

        val primaryColor = if (isNightMode) {
            ResourcesCompat.getColor(resources, R.color.primary_night_mode, theme)
        } else {
            TypedValue().apply { theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, this, true) }.data
        }

        val statusBarColor = if (isNightMode) {
            Color.BLACK
        } else {
            ResourcesCompat.getColor(resources, R.color.primary_dark, theme)
        }

        supportActionBar?.setBackgroundDrawable(ColorDrawable(primaryColor))

        findViewById<View>(R.id.panelBackForwardList)?.background = ColorDrawable(primaryColor)

        window.statusBarColor = statusBarColor
    }

    /**
     * Call this from subclasses before super.onCreate() to make
     * the activity ask for storage permission and do not proceed
     * if the permission is not granted.
     */
    protected fun willNeedStoragePermission() {
        this.willNeedStoragePermission = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastKnownConfigurationSerialNumber = ConfigurationWrapper.getSerialCounter()

        // Force locale that is needed after androidx activity 1.2.0
        // Reference: https://stackoverflow.com/a/40704077/11238
        val context = ConfigurationWrapper.wrap(this)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(context.resources.configuration, context.resources.displayMetrics)

        if (willNeedStoragePermission) {
            askStoragePermission()
        }

        // to ensure that title is localized
        val activityInfo = packageManager.getActivityInfo(componentName, 0)
        if (activityInfo.labelRes != 0) {
            title = Localized.text(activityInfo.labelRes)
        }
    }

    private fun askStoragePermission() {
        if (!(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                var oked = false
                MaterialDialog(this).show {
                    message(R.string.storage_permission_rationale)
                    positiveButton(R.string.ok) {
                        oked = true
                        ActivityCompat.requestPermissions(this@BaseActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), RequestCodes.PermissionFromActivity.Storage)
                    }
                    onDismiss {
                        if (!oked) {
                            finish()
                        }
                    }
                }
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), RequestCodes.PermissionFromActivity.Storage)
            }
        } else {
            onNeededPermissionsGranted(true)
        }
    }

    /**
     * Override this to do something after we confirm that all needed permissions are granted.
     * This is only called if [.willNeedStoragePermission] was called.
     *
     * @param immediatelyGranted whether the permission is granted immediately without leaving the first onCreate().
     * Use this to determine whether we need to do initialization (e.g. load dir contents)
     * and to determine whether it is safe to init now.
     */
    protected open fun onNeededPermissionsGranted(immediatelyGranted: Boolean) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == RequestCodes.PermissionFromActivity.Storage) {
            // all must be granted
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                onNeededPermissionsGranted(false)
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {

                    // user selects do not ask again
                    var oked = false
                    MaterialDialog(this).show {
                        message(text = "You need to have the Storage permission enabled to continue, because we need to store shared media such as Bible versions and fonts.")
                        positiveButton(R.string.ok) {
                            oked = true

                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", packageName, null))

                            startActivityForResult(intent, RequestCodes.FromActivity.PermissionSettings)
                        }
                        negativeButton(R.string.cancel)
                        onDismiss {
                            if (!oked) {
                                finish()
                            }
                        }
                    }
                } else {
                    finish()
                }
            }

            return
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCodes.FromActivity.PermissionSettings) {
            askStoragePermission()
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    @CallSuper
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            navigateUp()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    protected fun navigateUp() {
        val upIntent = NavUtils.getParentActivityIntent(this)
        if (upIntent == null) { // not defined in manifest, let us finish() instead.
            finish()
            return
        }

        if (NavUtils.shouldUpRecreateTask(this, upIntent) || isTaskRoot) {
            TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(upIntent)
                .startActivities()
        } else {
            NavUtils.navigateUpTo(this, upIntent)
        }
    }

    companion object {
        @JvmStatic
        fun dumpIntent(intent: Intent, via: String) {
            AppLog.d(TAG, "Got intent via $via")
            AppLog.d(TAG, "  action: ${intent.action}")
            AppLog.d(TAG, "  data uri: ${intent.data}")
            AppLog.d(TAG, "  component: ${intent.component}")
            AppLog.d(TAG, "  flags: 0x${Integer.toHexString(intent.flags)}")
            AppLog.d(TAG, "  mime: ${intent.type}")
            val extras = intent.extras
            AppLog.d(TAG, "  extras: ${extras?.size() ?: "null"}")
            if (extras != null) {
                for (key in extras.keySet()) {
                    AppLog.d(TAG, "    $key = ${extras.get(key)}")
                }
            }
        }
    }
}
