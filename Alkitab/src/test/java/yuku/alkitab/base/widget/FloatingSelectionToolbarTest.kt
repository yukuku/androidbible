package yuku.alkitab.base.widget

import android.graphics.Outline
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class FloatingSelectionToolbarTest {
    @Test
    fun `vertical drag from the close button moves only the toolbar and release restores its edge without clicking`() {
        for (isBottom in listOf(false, true)) {
            withToolbar(isBottom) { layout, toolbar ->
                var closeCount = 0
                toolbar.setNavigationOnClickListener { closeCount++ }
                measure(layout)
                val button = toolbar.children.first { it.contentDescription == toolbar.navigationContentDescription }
                val x = toolbar.left + button.left + button.width / 2f
                val y = toolbar.top + button.top + button.height / 2f
                touch(layout, MotionEvent.ACTION_DOWN, x, y)
                touch(layout, MotionEvent.ACTION_UP, x, y)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(1, closeCount)
                closeCount = 0
                val distance = if (isBottom) -120f else 120f
                val content = layout.findViewById<View>(R.id.nontoolbar)
                val originalTop = content.top
                val originalHeight = content.height
                touch(layout, MotionEvent.ACTION_DOWN, x, y)
                touch(layout, MotionEvent.ACTION_MOVE, x, y + distance / 2)
                touch(layout, MotionEvent.ACTION_MOVE, x, y + distance)
                assertEquals(distance, toolbar.translationY, 0.1f)
                assertEquals(0f, toolbar.translationX, 0f)
                assertEquals(originalTop, content.top)
                assertEquals(originalHeight, content.height)
                touch(layout, MotionEvent.ACTION_UP, x, y + distance)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
                assertEquals(0f, toolbar.translationY, 0.1f)
                assertEquals(0, closeCount)
                touch(layout, MotionEvent.ACTION_DOWN, x, y)
                touch(layout, MotionEvent.ACTION_UP, x, y)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(1, closeCount)
            }
        }
    }

    @Test
    fun `cancelled drag snaps back and movement stays inside the safe area`() {
        withToolbar(false) { layout, toolbar ->
            val x = toolbar.left + toolbar.width / 2f
            val y = toolbar.top + toolbar.height / 2f
            touch(layout, MotionEvent.ACTION_DOWN, x, y)
            touch(layout, MotionEvent.ACTION_MOVE, x, y + 100)
            touch(layout, MotionEvent.ACTION_MOVE, x, y + 1000)
            assertEquals((layout.height - 16).toFloat(), toolbar.bottom + toolbar.translationY, 0.1f)
            touch(layout, MotionEvent.ACTION_CANCEL, x, y + 1000)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(0f, toolbar.translationY, 0.1f)
        }
    }

    @Test
    fun `floating toolbar has rounded outline and keeps sixteen dp clear of cutouts at either edge`() {
        for (isBottom in listOf(false, true)) {
            withToolbar(isBottom) { layout, toolbar ->
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(12, 28, 8, 10))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(toolbar, insets)
                measure(layout)
                assertEquals(28, toolbar.left)
                assertEquals(336, toolbar.right)
                if (isBottom) assertEquals(614, toolbar.bottom)
                else assertEquals(44, toolbar.top)
                val outline = Outline()
                toolbar.outlineProvider.getOutline(toolbar, outline)
                assertEquals(4f, outline.radius, 0f)
                assertTrue(outline.canClip())
            }
        }
    }

    private fun withToolbar(isBottom: Boolean, check: (ViewGroup, FloatingSelectionToolbar) -> Unit) {
        App.initWithAppContext(ApplicationProvider.getApplicationContext())
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val layout = activity.layoutInflater.inflate(R.layout.activity_isi_content, null) as ViewGroup
        activity.setContentView(layout)
        val root = layout.findViewById<ViewGroup>(R.id.root)
        root.removeView(root.findViewById(R.id.audio_bar))
        updateReaderToolbarLayout(root, true, isBottom, true, true)
        val toolbar = layout.findViewById<FloatingSelectionToolbar>(R.id.selectionToolbar)
        measure(layout)
        try {
            check(layout, toolbar)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun measure(layout: View) {
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, 360, 640)
    }

    private fun touch(layout: View, action: Int, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, action, x, y, 0)
        layout.dispatchTouchEvent(event)
        event.recycle()
    }
}
