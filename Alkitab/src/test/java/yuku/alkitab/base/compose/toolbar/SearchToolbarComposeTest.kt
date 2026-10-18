package yuku.alkitab.base.compose.toolbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
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
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "sw360dp-w360dp-h640dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchToolbarComposeTest {
    private val frameClock = BroadcastFrameClock()
    private var frameTimeNanos = 0L

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `the compose toolbar fits its view host and keeps navigation and version actions clickable`() {
        for (widthDp in listOf(320, 360, 600)) {
            val controller = Robolectric.buildActivity(ComposeToolbarHostActivity::class.java).setup()
            val activity = controller.get()
            activity.setContentView(R.layout.activity_search)
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.setDisplayShowTitleEnabled(false)
            val compose = activity.findViewById<ComposeView>(R.id.composeToolbar)
            installRecomposer(compose)
            var backClicks = 0
            var versionClicks = 0
            compose.setContent {
                BibleAppTheme {
                    SearchToolbar("VERSION", { backClicks++ }, { versionClicks++ })
                }
            }

            try {
                layout(toolbar, widthDp)
                assertEquals(widthDp * 3, compose.width)
                assertEquals(56 * 3, compose.height)
                click(compose, 24f, 28f)
                click(compose, widthDp - 40f, 28f)
                assertEquals("back action at ${widthDp}dp", 1, backClicks)
                assertEquals("version action at ${widthDp}dp", 1, versionClicks)
            } finally {
                compose.disposeComposition()
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    fun `changing the selected version redraws the stadium label`() {
        val controller = Robolectric.buildActivity(ComposeToolbarHostActivity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.activity_search)
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayShowTitleEnabled(false)
        var initials by mutableStateOf("TB")
        val compose = activity.findViewById<ComposeView>(R.id.composeToolbar)
        installRecomposer(compose)
        compose.setContent {
            BibleAppTheme { SearchToolbar(initials, {}, {}) }
        }
        try {
            layout(toolbar, 360)
            assertTrue(hasText(compose, "TB"))
            val before = render(toolbar)
            initials = "VERSNM"
            layout(toolbar, 360)
            assertTrue(hasText(compose, "VERSNM"))
            assertFalse(hasText(compose, "TB"))
            assertFalse(before.sameAs(render(toolbar)))
        } finally {
            compose.disposeComposition()
            controller.pause().stop().destroy()
        }
    }

    private fun installRecomposer(compose: ComposeView) {
        // Compose caches its main dispatcher, but Robolectric resets Choreographer
        // between tests. Give each fixture a dispatcher and frame clock we control.
        val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher()
        compose.setParentCompositionContext(compose.createLifecycleAwareWindowRecomposer(dispatcher + frameClock))
    }

    private fun advanceFrame() {
        Snapshot.sendApplyNotifications()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        frameTimeNanos += 16_000_000L
        frameClock.sendFrame(frameTimeNanos)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun hasText(compose: ComposeView, text: String): Boolean =
        (compose.getChildAt(0) as RootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
        }

    private fun layout(toolbar: Toolbar, widthDp: Int) {
        repeat(2) {
            advanceFrame()
            toolbar.measure(
                View.MeasureSpec.makeMeasureSpec(widthDp * 3, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(56 * 3, View.MeasureSpec.EXACTLY),
            )
            toolbar.layout(0, 0, toolbar.measuredWidth, toolbar.measuredHeight)
            advanceFrame()
        }
    }

    private fun click(view: View, xDp: Float, yDp: Float) {
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 10, action, xDp * 3, yDp * 3, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        advanceFrame()
    }

    private fun render(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
}
