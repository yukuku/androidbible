package yuku.alkitab.base.appwidget

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.debug.R

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-mdpi")
class DailyVerseConfigurationScreenTest {
    @Test
    fun `Compose controls select a version and clear dark text when transparency is disabled in either toolbar position`() {
        for ((isBottom, height) in listOf(false to 640, true to 640, false to 320, true to 320)) {
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
            val activity = controller.get()
            activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
            val frame = FrameLayout(activity)
            activity.setContentView(frame)
            val view = ComposeView(activity)
            val clock = BroadcastFrameClock()
            view.setParentCompositionContext(frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + clock))
            val state = mutableStateOf(DailyVerseConfiguration(isTransparent = true, isDarkText = true))
            var isSaved = false
            view.setContent {
                BibleAppTheme {
                    DailyVerseConfigurationScreen(
                        state.value, listOf(MVersionInternal().apply { longName = "Internal Bible" }),
                        isBottom, Color.DarkGray, { state.value = it }, { isSaved = true }, {},
                    )
                }
            }
            frame.addView(view, ViewGroup.LayoutParams(-1, -1))
            var frameNumber = 0L
            fun settle(frames: Int = 5) {
                repeat(frames) {
                    Snapshot.sendApplyNotifications()
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    clock.sendFrame(++frameNumber * 16_000_000L)
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, 360, height)
                }
            }
            fun node(text: String): SemanticsNode = (view.getChildAt(0) as RootForTest).semanticsOwner
                .getAllSemanticsNodes(mergingEnabled = true).single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == text } == true
                }
            fun click(text: String) {
                assertTrue(node(text).config[SemanticsActions.OnClick].action!!.invoke())
                settle()
            }
            try {
                settle()
                assertTrue(node(activity.getString(R.string.ok)).config.contains(SemanticsProperties.Disabled))
                click("Internal Bible")
                assertEquals("internal", state.value.versionId)
                assertFalse(node(activity.getString(R.string.ok)).config.contains(SemanticsProperties.Disabled))
                click(activity.getString(R.string.dv_option_transparent_background))
                assertFalse(state.value.isTransparent)
                assertFalse(state.value.isDarkText)
                assertTrue(node(activity.getString(R.string.dv_option_dark_text)).config.contains(SemanticsProperties.Disabled))
                if (height == 320) {
                    val scrollNode = (view.getChildAt(0) as RootForTest).semanticsOwner
                        .getAllSemanticsNodes(mergingEnabled = true).single { it.config.contains(SemanticsActions.ScrollBy) }
                    assertTrue(scrollNode.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 2000f))
                    settle(30)
                    // Drawing updates the card layer transforms before checking clipped semantic bounds.
                    val bitmap = android.graphics.Bitmap.createBitmap(360, height, android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    bitmap.recycle()
                    val iconBounds = node(activity.getString(R.string.dv_option_hide_app_icon)).boundsInRoot
                    val saveBounds = node(activity.getString(R.string.ok)).boundsInRoot
                    assertTrue("last setting stays above the action bar", iconBounds.bottom <= saveBounds.top)
                    assertTrue("last setting is visible after scrolling", iconBounds.height > 0f)
                }
                click(activity.getString(R.string.ok))
                assertTrue(isSaved)
            } finally {
                view.disposeComposition()
                controller.pause().stop().destroy()
            }
        }
    }
}
