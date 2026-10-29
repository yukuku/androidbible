package yuku.alkitab.songs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import java.io.File
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongMediaToolbarActionTest {
    @Test
    fun `toolbar actions support detached measurement refreshes and clicks in every visible state`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        controller.setup().visible()
        val activity = controller.get()
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val clock = BroadcastFrameClock()
        frame.compositionContext = frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + clock)
        val toolbar = Toolbar(activity).apply {
            setBackgroundColor(Color.rgb(69, 90, 100))
            setTitleTextColor(Color.WHITE)
        }
        frame.addView(toolbar)
        val image = Bitmap.createBitmap(360, SongMediaToolbarState.entries.size * 56, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        var frameTime = 0L

        try {
            for ((index, state) in SongMediaToolbarState.entries.withIndex()) {
                toolbar.menu.clear()
                toolbar.title = state.name
                var isClicked = false
                val action = songMediaToolbarActionView(activity, toolbar, { state }, { isClicked = true })
                action.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                toolbar.menu.add("Media").apply {
                    actionView = action
                    isVisible = state.isVisible
                    isEnabled = state.isEnabled
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                repeat(3) {
                    Snapshot.sendApplyNotifications()
                    shadowOf(Looper.getMainLooper()).idle()
                    frameTime += 150_000_000L
                    clock.sendFrame(frameTime)
                    toolbar.measure(
                        View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY),
                    )
                    toolbar.layout(0, 0, 360, 56)
                }
                if (state.isVisible) {
                    assertEquals(state.name, 48, action.measuredWidth)
                    assertEquals(state.name, 48, action.measuredHeight)
                }
                if (state == SongMediaToolbarState.Loading) {
                    assertTrue(action.hasComposition)
                    toolbar.menu.clear()
                    assertFalse(action.isAttachedToWindow)
                    assertTrue("Loading composition must survive menu refreshes", action.hasComposition)
                    toolbar.menu.add("Media").setActionView(action).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    Snapshot.sendApplyNotifications()
                    shadowOf(Looper.getMainLooper()).idle()
                    toolbar.measure(
                        View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY),
                    )
                    toolbar.layout(0, 0, 360, 56)
                    assertTrue(action.hasComposition)
                }
                canvas.save()
                canvas.translate(0f, index * 56f)
                toolbar.draw(canvas)
                canvas.restore()

                if (state.isVisible) {
                    val owner = (action.getChildAt(0) as RootForTest).semanticsOwner
                    val node = requireNotNull(clickableNode(owner.unmergedRootSemanticsNode))
                    assertFalse(state.name, node.config.contains(SemanticsProperties.Disabled))
                    assertTrue(state.name, requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())
                    assertTrue(state.name, isClicked)
                }
            }
            val output = File("build/qa-sessions/song-media-toolbar/all-states.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            image.recycle()
            controller.pause().stop().destroy()
        }
    }

    private fun clickableNode(node: SemanticsNode): SemanticsNode? =
        if (node.config.contains(SemanticsActions.OnClick)) node else node.children.firstNotNullOfOrNull(::clickableNode)
}
