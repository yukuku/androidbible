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
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.Localized
import yuku.alkitab.debug.R

private const val TAG = "BaseActivity"

private val SAFE_AREA_TYPES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

abstract class BaseActivity : AppCompatActivity() {

    private var lastKnownConfigurationSerialNumber = 0

    private var edgeToEdge = false
    private var edgeToEdgeHasToolbar = false
    var isToolbarAtBottom by mutableStateOf(false)
        private set
    var toolbarBackgroundColor by mutableIntStateOf(Color.TRANSPARENT)
        private set
    private var edgeToEdgeToolbar: Toolbar? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ConfigurationWrapper.wrap(base))
    }

    override fun onStart() {
        super.onStart()

        refreshToolbarPlacement()
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

        toolbarBackgroundColor = primaryColor
        supportActionBar?.setBackgroundDrawable(primaryColor.toDrawable())
        // On the reading screen the toolbar host extends behind the status bar.
        // Its background must change with the toolbar when night mode changes.
        findViewById<View>(R.id.toolbarHost)?.setBackgroundColor(primaryColor)
        findViewById<View>(R.id.selectionToolbar)?.setBackgroundColor(primaryColor)

        for (id in intArrayOf(R.id.panelBackForwardList, R.id.bPrereleaseFeedback)) {
            findViewById<View>(id)?.apply {
                background = PaintDrawable(primaryColor).apply {
                    setCornerRadius(resources.getDimension(R.dimen.back_forward_list_corner_radius))
                }
                clipToOutline = true
            }
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
        val statusBarBackground = if (edgeToEdgeHasToolbar && !isToolbarAtBottom) toolbarColor else backgroundColor
        controller.isAppearanceLightStatusBars = ColorUtils.calculateLuminance(statusBarBackground) > 0.5
        val navigationBarBackground = if (edgeToEdgeHasToolbar && isToolbarAtBottom) toolbarColor else backgroundColor
        controller.isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(navigationBarBackground) > 0.5
    }

    /**
     * Call after setContentView with the toolbar directly inside a vertical
     * LinearLayout spanning the window. The toolbar owns the system-bar inset
     * at its preferred edge; the container handles the remaining safe area and
     * keyboard inset. Drawer layouts need [applySafeAreaPadding] separately.
     */
    protected fun setupEdgeToEdgeDisplay(toolbar: Toolbar) {
        setupEdgeToEdgeToolbar(toolbar, isScrollPastBottomEnabled = false)
    }

    /**
     * With a top toolbar, content must use [applyScrollPastBottomInset] to keep
     * its last item reachable beneath the navigation bar. The container omits
     * keyboard padding in that position, so this is for content without text input.
     * A bottom toolbar owns the navigation inset and stays above the keyboard.
     */
    protected fun setupEdgeToEdgeDisplayWithoutBottomInset(toolbar: Toolbar) {
        setupEdgeToEdgeToolbar(toolbar, isScrollPastBottomEnabled = true)
    }

    /**
     * [setupEdgeToEdgeDisplay] for an activity whose layout has no toolbar:
     * the whole container is padded into the safe area, including the top.
     */
    protected fun setupEdgeToEdgeDisplayWithoutToolbar(container: View) {
        enableEdgeToEdgeInternal(hasToolbar = false)
        setupContainerInsets(container, includeTop = true, includeBottom = true)
    }

    protected fun setupEdgeToEdgeComposeToolbar() {
        enableEdgeToEdgeInternal(hasToolbar = true)
        isToolbarAtBottom = Preferences.getBoolean(R.string.pref_bottomToolbarOnText_key, R.bool.pref_bottomToolbarOnText_default)
    }

    private fun setupEdgeToEdgeToolbar(toolbar: Toolbar, isScrollPastBottomEnabled: Boolean) {
        enableEdgeToEdgeInternal(hasToolbar = true)
        isToolbarAtBottom = Preferences.getBoolean(R.string.pref_bottomToolbarOnText_key, R.bool.pref_bottomToolbarOnText_default)
        val container = toolbar.parent as LinearLayout
        ViewGroupCompat.installCompatInsetsDispatch(container)
        edgeToEdgeToolbar = toolbar
        // A match-parent child measured before the toolbar would leave it no space.
        container.children.filter { it !== toolbar }.forEach { child ->
            child.updateLayoutParams<LinearLayout.LayoutParams> {
                if (height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    height = 0
                    weight = 1f
                }
            }
        }
        if (isToolbarAtBottom) {
            container.removeView(toolbar)
            container.addView(toolbar)
        }

        val tv = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)
        val actionBarSize = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES)
            val top = if (isToolbarAtBottom) 0 else insets.top
            val bottom = if (isToolbarAtBottom && !windowInsets.isVisible(WindowInsetsCompat.Type.ime())) insets.bottom else 0
            v.updatePadding(top = top, bottom = bottom)
            v.updateLayoutParams { height = actionBarSize + top + bottom }
            windowInsets
        }
        val basePadding = Rect(container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES)
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = when {
                isToolbarAtBottom -> ime.bottom
                isScrollPastBottomEnabled -> 0
                else -> maxOf(insets.bottom, ime.bottom)
            }
            v.setPadding(
                basePadding.left + insets.left,
                basePadding.top + if (isToolbarAtBottom) insets.top else 0,
                basePadding.right + insets.right,
                basePadding.bottom + bottom,
            )
            windowInsets
        }
    }

    fun refreshToolbarPlacement() {
        val isBottom = Preferences.getBoolean(R.string.pref_bottomToolbarOnText_key, R.bool.pref_bottomToolbarOnText_default)
        if (isToolbarAtBottom == isBottom) return
        isToolbarAtBottom = isBottom
        edgeToEdgeToolbar?.let { toolbar ->
            val container = toolbar.parent as LinearLayout
            container.removeView(toolbar)
            container.addView(toolbar, if (isBottom) container.childCount else 0)
            ViewCompat.requestApplyInsets(container)
        }
        if (edgeToEdge) applyNightModeColors()
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
            v.updatePadding(bottom = basePaddingBottom + if (isToolbarAtBottom) 0 else insets.bottom)
            windowInsets
        }
    }

    protected fun applySafeAreaPadding(view: LeftDrawer) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(SAFE_AREA_TYPES)
            view.setSafeAreaInsets(insets.left, insets.top, insets.right, insets.bottom)
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
