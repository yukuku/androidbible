package yuku.alkitab.base.settings

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.preference.CheckBoxPreference
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.UiDimensionsProvider
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightAdjustmentSettingsTest {
    private lateinit var originalServices: AppServices

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(RuntimeEnvironment.getApplication())
        Preferences.invalidate()
        Preferences.remove(Prefkey.highlightColorAdjustment)
        originalServices = App.services

        val version = mockk<Version>()
        val book = Book().apply { bookId = 0; chapter_count = 1 }
        every { version.getBook(any()) } returns book
        every { version.firstBook } returns book
        every { version.loadChapterText(any(), any()) } returns SingleChapterVerses.EMPTY
        every { version.longName } returns "Preview version name"
        val versions = mockk<VersionManager>()
        every { versions.activeVersion() } returns version
        val dimensions = mockk<UiDimensionsProvider>(relaxed = true)
        every { dimensions.applied() } returns S.CalculatedDimensions().apply {
            fontSize2dp = 17f
            lineSpacingMult = 1.15f
        }
        App.services = AppServices(mockk(relaxed = true), versions, dimensions)
    }

    @After
    fun tearDown() {
        Preferences.remove(Prefkey.highlightColorAdjustment)
        App.services = originalServices
    }

    @Test
    fun `preferences hide endpoint summaries and migrate fractional values to whole percentages`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val frame = FrameLayout(activity).apply { id = View.generateViewId() }
        activity.setContentView(frame)

        try {
            for ((stored, expected) in listOf(0f to 0f, 0.001f to 0f, 0.371f to 0.37f, 0.998f to 1f, 1f to 1f)) {
                Preferences.setFloat(Prefkey.highlightColorAdjustment, stored)
                val fragment = DisplayFragment()
                activity.supportFragmentManager.beginTransaction().replace(frame.id, fragment).commitNow()
                val preferences = fragment.preferenceScreen
                val customize = (0 until preferences.preferenceCount).map(preferences::getPreference)
                    .single { it.title == activity.getString(R.string.highlight_style_customize) }
                val original = (0 until preferences.preferenceCount).map(preferences::getPreference)
                    .filterIsInstance<CheckBoxPreference>()
                    .single { it.title == activity.getString(R.string.highlight_style_original) }

                assertEquals(expected, Preferences.getFloat(Prefkey.highlightColorAdjustment, 1f))
                assertEquals(expected == 0f, original.isChecked)
                if (expected == 0f || expected == 1f) assertNull(customize.summary)
                else assertEquals("37%", customize.summary)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `slider applies exact percentages immediately and omits the title version and confirmation controls`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val view = ComposeView(activity)
        val clock = BroadcastFrameClock()
        view.setParentCompositionContext(frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + clock))
        val changes = mutableListOf<Float>()
        view.setContent {
            BibleAppTheme {
                HighlightStyleSheet.Content(onValueChanged = {
                    changes += it
                    Preferences.setFloat(Prefkey.highlightColorAdjustment, it)
                })
            }
        }
        frame.addView(view, ViewGroup.LayoutParams(-1, -1))
        var frameNumber = 0L

        fun settle() {
            repeat(5) {
                Snapshot.sendApplyNotifications()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                clock.sendFrame(++frameNumber * 16_000_000L)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 360, 640)
            }
        }

        fun nodes() = (view.getChildAt(0) as RootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        fun labels() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        fun setProgress(progress: Float) {
            val slider = nodes().single { it.config.contains(SemanticsActions.SetProgress) }
            assertEquals(99, slider.config[SemanticsProperties.ProgressBarRangeInfo].steps)
            assertTrue(requireNotNull(slider.config[SemanticsActions.SetProgress].action).invoke(progress))
            settle()
        }

        try {
            settle()
            for (hidden in listOf(activity.getString(R.string.highlight_style_customize), "Preview version name", activity.getString(R.string.ok), activity.getString(R.string.cancel))) {
                assertFalse(hidden, hidden in labels())
            }
            assertTrue(activity.getString(R.string.highlight_style_explanation) in labels())
            setProgress(0.371f)
            assertEquals(listOf(0.37f), changes)
            assertEquals(0.37f, Preferences.getFloat(Prefkey.highlightColorAdjustment, 1f))
            assertTrue(activity.getString(R.string.highlight_style_custom_value, 37) in labels())
            setProgress(0.001f)
            assertEquals(0f, Preferences.getFloat(Prefkey.highlightColorAdjustment, 1f))
            assertTrue(activity.getString(R.string.highlight_style_original_value) in labels())
            setProgress(0.998f)
            assertEquals(1f, Preferences.getFloat(Prefkey.highlightColorAdjustment, 0f))
            assertTrue(activity.getString(R.string.highlight_style_current) in labels())

            for ((key, expected) in listOf(R.string.highlight_style_zero to 0f, R.string.highlight_style_one to 1f)) {
                val endpoint = nodes().single { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(key) } == true }
                assertTrue(requireNotNull(endpoint.config[SemanticsActions.OnClick].action).invoke())
                settle()
                assertEquals(expected, Preferences.getFloat(Prefkey.highlightColorAdjustment, -1f))
            }
        } finally {
            view.disposeComposition()
            controller.pause().stop().destroy()
        }
        assertEquals(1f, Preferences.getFloat(Prefkey.highlightColorAdjustment, 0f))
    }
}
