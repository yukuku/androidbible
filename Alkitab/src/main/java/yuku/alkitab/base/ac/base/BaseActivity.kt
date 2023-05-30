package yuku.alkitab.base.ac.base

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.content.res.ResourcesCompat
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.base.widget.Localized
import yuku.alkitab.debug.R

private const val TAG = "BaseActivity"

abstract class BaseActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastKnownConfigurationSerialNumber = ConfigurationWrapper.getSerialCounter()

        // Force locale that is needed after androidx activity 1.2.0
        // Reference: https://stackoverflow.com/a/40704077/11238
        val context = ConfigurationWrapper.wrap(this)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(context.resources.configuration, context.resources.displayMetrics)

        // to ensure that title is localized
        val activityInfo = packageManager.getActivityInfo(componentName, 0)
        if (activityInfo.labelRes != 0) {
            title = Localized.text(activityInfo.labelRes)
        }
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
