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
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-mdpi")
class DailyVerseConfigurationScreenTest {
    @Test
    fun `Compose controls select a version and clear dark text when transparency is disabled in either toolbar position`() {
        for (isBottom in listOf(false, true)) {
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
                click(activity.getString(R.string.ok))
                assertTrue(isSaved)
            } finally {
                view.disposeComposition()
                controller.pause().stop().destroy()
            }
        }
    }
}
