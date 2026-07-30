package yuku.alkitab.base.ac.base

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.PaintDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.base.widget.Localized
import yuku.alkitab.debug.R

private const val TAG = "BaseActivity"

private val SAFE_AREA_TYPES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

abstract class BaseActivity : AppCompatActivity() {

    private var lastKnownConfigurationSerialNumber = 0

    private var edgeToEdge = false
    private var edgeToEdgeHasToolbar = false

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

        supportActionBar?.setBackgroundDrawable(primaryColor.toDrawable())

        findViewById<View>(R.id.panelBackForwardList)?.apply {
            background = PaintDrawable(primaryColor).apply {
                setCornerRadius(resources.getDimension(R.dimen.back_forward_list_corner_radius))
            }
            clipToOutline = true
        }

        if (edgeToEdge) {
            updateSystemBarIconAppearance(primaryColor)
        } else {
            applyStatusBarColor(statusBarColor)
        }
    }

    protected open fun applyStatusBarColor(statusBarColor: Int) {
        window.statusBarColor = statusBarColor
    }

    private fun updateSystemBarIconAppearance(toolbarColor: Int) {
        val backgroundColor = TypedValue().apply { theme.resolveAttribute(android.R.attr.colorBackground, this, true) }.data
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val statusBarBackground = if (edgeToEdgeHasToolbar) toolbarColor else backgroundColor
        controller.isAppearanceLightStatusBars = ColorUtils.calculateLuminance(statusBarBackground) > 0.5
        controller.isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(backgroundColor) > 0.5
    }

    /**
     * Makes the window draw edge-to-edge, which Android 15 enforces when
     * targeting SDK 35 while ignoring window.statusBarColor. The toolbar
     * grows behind the status bar keeping its background color, and its
     * parent container is padded away from the navigation bar, side system
     * bars, display cutout, and on-screen keyboard (replacing the legacy
     * adjustResize behavior, which does not work in an edge-to-edge window).
     * System bar icon appearance then follows the toolbar and theme
     * background colors instead of the legacy statusBarColor.
     *
     * Call after setContentView. The toolbar must be a direct child of the
     * container that spans the whole window; for drawer layouts that is the
     * main-content container, and the drawer itself needs
     * [applySafeAreaPadding] separately.
     */
    protected fun setupEdgeToEdgeDisplay(toolbar: Toolbar) {
        setupEdgeToEdgeToolbar(toolbar)
        setupContainerInsets(toolbar.parent as View, includeTop = false, includeBottom = true)
    }

    /**
     * [setupEdgeToEdgeDisplay] for an activity whose content scrolls behind
     * the navigation bar: the container keeps its bottom edge at the window
     * bottom and the content itself adds the bottom inset as scroll-past
     * padding. The keyboard inset is left out as well, so this suits content
     * without text input.
     */
    protected fun setupEdgeToEdgeDisplayWithoutBottomInset(toolbar: Toolbar) {
        setupEdgeToEdgeToolbar(toolbar)
        setupContainerInsets(toolbar.parent as View, includeTop = false, includeBottom = false)
    }

    /**
     * [setupEdgeToEdgeDisplay] for an activity whose layout has no toolbar:
     * the whole container is padded into the safe area, including the top.
     */
    protected fun setupEdgeToEdgeDisplayWithoutToolbar(container: View) {
        enableEdgeToEdgeInternal(hasToolbar = false)
        setupContainerInsets(container, includeTop = true, includeBottom = true)
    }

    private fun setupEdgeToEdgeToolbar(toolbar: Toolbar) {
        enableEdgeToEdgeInternal(hasToolbar = true)

        val tv = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)
        val actionBarSize = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES)
            v.updatePadding(top = insets.top)
            v.updateLayoutParams { height = actionBarSize + insets.top }
            windowInsets
        }
    }

    /**
     * Lets a scrolling view's content draw through the bottom system bar
     * while keeping its last item scrollable clear of that bar, the way the
     * verse list behaves. Pairs with [setupEdgeToEdgeDisplayWithoutBottomInset].
     */
    protected fun applyScrollPastBottomInset(view: ViewGroup) {
        val basePaddingBottom = view.paddingBottom
        view.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES)
            v.updatePadding(bottom = basePaddingBottom + insets.bottom)
            windowInsets
        }
    }

    /**
     * Pads a full-height panel that extends behind the system bars (e.g. a
     * navigation drawer) into the safe area on all sides.
     */
    protected fun applySafeAreaPadding(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES)
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
    }

    private fun enableEdgeToEdgeInternal(hasToolbar: Boolean) {
        edgeToEdge = true
        edgeToEdgeHasToolbar = hasToolbar
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setupContainerInsets(container: View, includeTop: Boolean, includeBottom: Boolean) {
        val basePadding = Rect(container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES or WindowInsetsCompat.Type.ime())
            v.setPadding(
                basePadding.left + insets.left,
                basePadding.top + if (includeTop) insets.top else 0,
                basePadding.right + insets.right,
                basePadding.bottom + if (includeBottom) insets.bottom else 0,
            )
            windowInsets
        }
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
}
