package yuku.alkitab.base.compose.toolbar

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.alkitab.base.compose.BibleAppTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class ToolbarScaffoldTest {
    @Test
    fun `toolbar and content stay clear of system bars and keyboard in both positions`() {
        for (bottom in listOf(false, true)) {
            for (keyboardHeight in listOf(0, 280)) {
                val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
                val activity = controller.get()
                activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
                val frame = FrameLayout(activity)
                activity.setContentView(frame)
                val view = ComposeView(activity)
                val clock = BroadcastFrameClock()
                val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher()
                view.setParentCompositionContext(frame.createLifecycleAwareWindowRecomposer(dispatcher + clock))
                var toolbarBounds = Rect.Zero
                var contentBounds = Rect.Zero
                view.setContent {
                    BibleAppTheme {
                        ToolbarScaffold(
                            toolbarAtBottom = bottom,
                            toolbarColor = MaterialTheme.colorScheme.secondaryContainer,
                            toolbar = {
                                Box(Modifier.fillMaxWidth().height(56.dp).onGloballyPositioned {
                                    toolbarBounds = it.boundsInRoot()
                                })
                            },
                            snackbarHost = {},
                            containerColor = MaterialTheme.colorScheme.background,
                        ) { padding ->
                            Box(Modifier.padding(padding).fillMaxSize().onGloballyPositioned {
                                contentBounds = it.boundsInRoot()
                            })
                        }
                    }
                }
                frame.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardHeight))
                    .setVisible(WindowInsetsCompat.Type.ime(), keyboardHeight > 0)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(view, insets)
                try {
                    repeat(5) { frameIndex ->
                        Snapshot.sendApplyNotifications()
                        Shadows.shadowOf(Looper.getMainLooper()).idle()
                        clock.sendFrame((frameIndex + 1) * 16_000_000L)
                        Shadows.shadowOf(Looper.getMainLooper()).idle()
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, 360, 640)
                    }
                    assertEquals(56f, toolbarBounds.height, 0.1f)
                    assertTrue(contentBounds.height > 0)
                    if (bottom) {
                        assertEquals((640 - maxOf(24, keyboardHeight)).toFloat(), toolbarBounds.bottom, 0.1f)
                        assertTrue(contentBounds.bottom <= toolbarBounds.top)
                        assertEquals(24f, contentBounds.top, 0.1f)
                    } else {
                        assertEquals(24f, toolbarBounds.top, 0.1f)
                        assertTrue(contentBounds.top >= toolbarBounds.bottom)
                        assertEquals((640 - maxOf(24, keyboardHeight)).toFloat(), contentBounds.bottom, 0.1f)
                    }
                } finally {
                    view.disposeComposition()
                    controller.pause().stop().destroy()
                }
            }
        }
    }
}
