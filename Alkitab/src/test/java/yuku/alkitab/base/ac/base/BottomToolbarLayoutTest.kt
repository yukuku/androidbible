package yuku.alkitab.base.ac.base

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

class ToolbarLayoutTestActivity : BaseActivity() {
    fun refreshColors() = applyNightModeColors()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Alkitab)
        super.onCreate(savedInstanceState)
        setContentView(intent.getIntExtra("layout", R.layout.activity_settings))
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (intent.getBooleanExtra("scrollPast", false)) {
            setupEdgeToEdgeDisplayWithoutBottomInset(toolbar)
            applyScrollPastBottomInset(findViewById(android.R.id.list))
        } else {
            setupEdgeToEdgeDisplay(toolbar)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class BottomToolbarLayoutTest {
    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<App>()
        App.initWithAppContext(app)
        Preferences.invalidate()
        Shadows.shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
            packageName = app.packageName
            name = ToolbarLayoutTestActivity::class.java.name
            theme = R.style.Theme_Alkitab
        })
    }

    @Test
    fun `toolbar color follows night mode in both positions and restores the day color`() {
        for (isBottom in listOf(false, true)) {
            withLayout(R.layout.activity_settings, isBottom, false) { container, _ ->
                val activity = container.context as ToolbarLayoutTestActivity
                for (isNightMode in listOf(false, true, false)) {
                    Preferences.setBoolean(Prefkey.is_night_mode, isNightMode)
                    activity.refreshColors()
                    val expected = activity.getColor(if (isNightMode) R.color.primary_night_mode else R.color.primary)
                    assertEquals(expected, activity.toolbarBackgroundColor)
                }
            }
        }
    }

    @Test
    fun `bottom toolbars fit below content across secondary screen layouts`() {
        val layouts = listOf(
            R.layout.activity_about,
            R.layout.activity_data_transfer,
            R.layout.activity_devotion_content,
            R.layout.activity_font_manager,
            R.layout.activity_goto,
            R.layout.activity_help,
            R.layout.activity_marker_list,
            R.layout.activity_markers,
            R.layout.activity_note,
            R.layout.activity_patch_text,
            R.layout.activity_reading_plan_content,
            R.layout.activity_search,
            R.layout.activity_search_book_filter,
            R.layout.activity_settings,
            R.layout.activity_song_view_content,
            R.layout.activity_sync_login,
            R.layout.activity_versions,
            R.layout.ribka_activity_report,
        )
        for (layout in layouts) {
            withLayout(layout, true, false) { container, toolbar ->
                applyInsetsAndLayout(container, 0)
                val label = container.resources.getResourceEntryName(layout)
                assertEquals(label, 640, toolbar.bottom)
                assertEquals(label, 24, toolbar.paddingBottom)
                assertEquals(label, 0, toolbar.paddingTop)
                assertEquals(label, 24, container.paddingTop)
                assertTrue(label, toolbar.height > 24)
                container.children.filter { it !== toolbar && it.visibility != View.GONE }.forEach {
                    assertTrue("$label content overlaps toolbar", it.bottom <= toolbar.top)
                    assertTrue("$label content is empty", it.height > 0)
                }
            }
        }
    }

    @Test
    fun `keyboard raises bottom toolbar without adding navigation padding twice`() {
        withLayout(R.layout.activity_search, true, false) { container, toolbar ->
            applyInsetsAndLayout(container, 280)
            assertEquals(360, toolbar.bottom)
            assertEquals(0, toolbar.paddingBottom)
            assertEquals(24, container.paddingTop)
            applyInsetsAndLayout(container, 0)
            assertEquals(640, toolbar.bottom)
            assertEquals(24, toolbar.paddingBottom)
        }
    }

    @Test
    fun `top toolbar retains its status bar inset and content order`() {
        withLayout(R.layout.activity_settings, false, false) { container, toolbar ->
            applyInsetsAndLayout(container, 0)
            assertEquals(0, toolbar.top)
            assertEquals(24, toolbar.paddingTop)
            assertEquals(0, toolbar.paddingBottom)
            assertEquals(24, container.paddingBottom)
            assertTrue(container.getChildAt(1).top >= toolbar.bottom)
        }
    }

    @Test
    fun `scrolling content receives navigation padding only without a bottom toolbar`() {
        for (isBottom in listOf(false, true)) {
            withLayout(R.layout.activity_markers, isBottom, true) { container, _ ->
                val list = container.findViewById<View>(android.R.id.list)
                val base = list.paddingBottom
                applyInsetsAndLayout(container, 0)
                assertEquals(base + if (isBottom) 0 else 24, list.paddingBottom)
            }
        }
    }

    @Test
    fun `changing toolbar position preserves the existing activity and content`() {
        withLayout(R.layout.activity_about, false, false) { container, toolbar ->
            val content = container.getChildAt(1)
            val activity = container.context as ToolbarLayoutTestActivity
            for (isBottom in listOf(true, false, true)) {
                Preferences.setBoolean(App.context.getString(R.string.pref_bottomToolbarOnText_key), isBottom)
                activity.refreshToolbarPlacement()
                applyInsetsAndLayout(container, 0)
                assertTrue(content.parent === container)
                assertEquals(if (isBottom) 1 else 0, container.indexOfChild(toolbar))
                assertEquals(if (isBottom) 640 else toolbar.height, toolbar.bottom)
                assertTrue(content.height > 0)
                assertTrue(if (isBottom) content.bottom <= toolbar.top else content.top >= toolbar.bottom)
                assertTrue(!activity.isFinishing && !activity.isDestroyed)
            }
        }
    }

    private fun withLayout(layout: Int, isBottom: Boolean, isScrollPastBottomEnabled: Boolean, check: (LinearLayout, Toolbar) -> Unit) {
        Preferences.setBoolean(App.context.getString(R.string.pref_bottomToolbarOnText_key), isBottom)
        val intent = Intent(ApplicationProvider.getApplicationContext(), ToolbarLayoutTestActivity::class.java)
            .putExtra("layout", layout)
            .putExtra("scrollPast", isScrollPastBottomEnabled)
        val controller = Robolectric.buildActivity(ToolbarLayoutTestActivity::class.java, intent).setup()
        try {
            val toolbar = controller.get().findViewById<Toolbar>(R.id.toolbar)
            check(toolbar.parent as LinearLayout, toolbar)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun applyInsetsAndLayout(container: ViewGroup, keyboardHeight: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardHeight))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboardHeight > 0)
            .build()
        ViewCompat.dispatchApplyWindowInsets(container, insets)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 360, 640)
    }
}
