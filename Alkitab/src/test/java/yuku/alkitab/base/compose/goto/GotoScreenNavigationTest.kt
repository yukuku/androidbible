package yuku.alkitab.base.compose.goto

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class GotoScreenNavigationTest {
    @Before
    fun setUp() {
        App.initWithAppContext(ApplicationProvider.getApplicationContext())
        Preferences.invalidate()
    }

    @Test
    fun `tapping between the first and third tabs never focuses the intermediate text field`() {
        for ((initial, destination) in listOf(GotoTab.DIALER to GotoTab.GRID, GotoTab.GRID to GotoTab.DIALER)) {
            withScreen(initial) { screen ->
                screen.tapTab(destination)
                screen.advanceFrames(3)
                screen.assertSelected(destination)
                screen.advanceFrames(90) {
                    assertFalse("the intermediate text field must not take focus", screen.isInputFocused())
                }
                screen.assertSelected(destination)
            }
        }
    }

    @Test
    fun `quick tab taps keep the last requested page selected`() {
        withScreen(GotoTab.DIALER) { screen ->
            screen.tapTab(GotoTab.DIRECT)
            screen.advanceFrames(3)
            screen.tapTab(GotoTab.DIALER)
            screen.advanceFrames(3)
            screen.tapTab(GotoTab.GRID)
            screen.advanceFrames(3)
            screen.assertSelected(GotoTab.GRID)
            screen.advanceFrames(90) {
                assertFalse(screen.isInputFocused())
            }
            screen.assertSelected(GotoTab.GRID)
        }
    }

    @Test
    fun `selecting the direct tab focuses its input and leaving it clears focus`() {
        withScreen(GotoTab.DIALER) { screen ->
            screen.tapTab(GotoTab.DIRECT)
            screen.advanceFrames(90)
            screen.assertSelected(GotoTab.DIRECT)
            assertTrue("the selected direct tab must focus its input", screen.isInputFocused())

            screen.tapTab(GotoTab.GRID)
            screen.advanceFrames(90)
            screen.assertSelected(GotoTab.GRID)
            assertFalse(screen.isInputFocused())
        }
    }

    @Test
    fun `keyboard resize during tab switching does not change the requested destination`() {
        withScreen(GotoTab.DIRECT) { screen ->
            screen.advanceFrames(10)
            screen.setKeyboardHeight(280)
            screen.advanceFrames(3)
            screen.tapTab(GotoTab.DIALER)
            screen.advanceFrames(3)
            screen.tapTab(GotoTab.GRID)
            screen.setKeyboardHeight(0)
            screen.advanceFrames(3)
            screen.assertSelected(GotoTab.GRID)
            screen.setKeyboardHeight(280)
            screen.advanceFrames(3)
            screen.setKeyboardHeight(0)
            screen.advanceFrames(90)
            screen.assertSelected(GotoTab.GRID)
            assertFalse(screen.isInputFocused())
        }
    }

    @Test
    fun `prefilled values are replacement ready and typed values keep independent append state`() {
        Preferences.setBoolean(Prefkey.gotoAskForVerse, true)
        withScreen(GotoTab.DIALER) { screen ->
            val replace = screen.view.context.getString(R.string.keypad_value_replace)
            val append = screen.view.context.getString(R.string.keypad_value_append)
            assertEquals(listOf(replace, replace), screen.valueModes())
            screen.tapKey("3")
            screen.advanceFrames(5)
            assertEquals(listOf("3", "1"), screen.values())
            assertEquals(listOf(append, replace), screen.valueModes())
            screen.tapKey("4")
            screen.advanceFrames(5)
            assertEquals("34", screen.values()[0])
            screen.tapValue(1)
            screen.tapKey("2")
            screen.advanceFrames(5)
            assertEquals("2", screen.values()[1])
            screen.tapKey("1")
            screen.advanceFrames(5)
            assertEquals("21", screen.values()[1])
            screen.tapValue(0)
            screen.tapKey("5")
            screen.advanceFrames(5)
            assertEquals("5", screen.values()[0])
            assertEquals(listOf(append, append), screen.valueModes())
        }
    }

    private fun withScreen(initialTab: GotoTab, block: (Screen) -> Unit) {
        Preferences.setInt(Prefkey.goto_last_tab, initialTab.ordinal1Based)
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val view = ComposeView(activity)
        val clock = BroadcastFrameClock()
        val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher()
        view.setParentCompositionContext(frame.createLifecycleAwareWindowRecomposer(dispatcher + clock))
        view.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                BibleAppTheme {
                    GotoScreen(
                        isToolbarAtBottom = false,
                        toolbarColor = MaterialTheme.colorScheme.secondaryContainer,
                        initialBookId = 0,
                        initialChapter_1 = 1,
                        initialVerse_1 = 1,
                        onUp = {},
                        onGotoFinished = { _, _, _, _ -> },
                    )
                }
            }
        }
        frame.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        try {
            val screen = Screen(
                view,
                clock,
                activity.getString(R.string.goto_tab_dialer_label),
                activity.getString(R.string.goto_tab_direct_label),
                activity.getString(R.string.goto_tab_grid_label),
            )
            screen.advanceFrames(5)
            screen.assertSelected(initialTab)
            block(screen)
        } finally {
            view.disposeComposition()
            controller.pause().stop().destroy()
        }
    }

    private class Screen(
        val view: ComposeView,
        val clock: BroadcastFrameClock,
        vararg val titles: String,
    ) {
        private var frameNumber = 0L

        fun advanceFrames(count: Int, onFrame: () -> Unit = {}) {
            repeat(count) {
                Snapshot.sendApplyNotifications()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                clock.sendFrame(++frameNumber * 16_000_000L)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 360, 640)
                onFrame()
            }
        }

        private fun nodes(): List<SemanticsNode> =
            (view.getChildAt(0) as RootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)

        private fun tab(tab: GotoTab): SemanticsNode = nodes().single {
            it.config.contains(SemanticsProperties.Selected) &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == titles[tab.ordinal1Based - 1] } == true
        }

        private fun valueNodes(): List<SemanticsNode> = nodes().filter { it.config.contains(SemanticsProperties.StateDescription) }

        fun valueModes(): List<String> = valueNodes().map { it.config[SemanticsProperties.StateDescription] }

        fun values(): List<String> = valueNodes().map { it.config[SemanticsProperties.Text].joinToString("") { text -> text.text } }

        fun tapValue(index: Int) { tap(valueNodes()[index]); advanceFrames(5) }

        fun tapKey(text: String) {
            tap(nodes().single { !it.config.contains(SemanticsProperties.StateDescription) && it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true })
        }

        fun tapTab(tab: GotoTab) { tap(tab(tab)) }

        private fun tap(node: SemanticsNode) {
            val center = node.boundsInRoot.center
            val downTime = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(downTime, downTime + 16, action, center.x, center.y, 0)
                assertTrue("the tab touch must be handled", view.dispatchTouchEvent(event))
                event.recycle()
            }
        }

        fun setKeyboardHeight(height: Int) {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                .setVisible(WindowInsetsCompat.Type.ime(), height > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }

        fun assertSelected(tab: GotoTab) {
            assertEquals(true, tab(tab).config[SemanticsProperties.Selected])
        }

        fun isInputFocused(): Boolean = nodes().any {
            it.config.contains(SemanticsProperties.EditableText) && it.config.getOrNull(SemanticsProperties.Focused) == true
        }
    }
}
