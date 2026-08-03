package yuku.alkitab.base.widget

import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The split handle competes with the system navigation gestures when it is
 * dragged near a window edge: the back gesture along the left and right edges,
 * and the home / app-switch gesture along the bottom. The back-gesture areas
 * are handed to the handle through a system gesture exclusion rect; the bottom
 * area cannot be excluded, so the handle's travel is bounded instead. The top
 * is only bounded in fullscreen, where no status bar covers the gesture area.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitHandleGestureTest {

    private fun handle(width: Int, height: Int): SplitHandleButton {
        val button = LabeledSplitHandleButton(ApplicationProvider.getApplicationContext(), null)
        button.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        button.layout(0, 0, width, height)
        return button
    }

    @Test
    fun `the handle claims its own bounds from the system gesture areas once laid out`() {
        val button = handle(24, 800)

        val rects = button.systemGestureExclusionRects
        assertEquals(1, rects.size)
        assertEquals(0, rects[0].left)
        assertEquals(0, rects[0].top)
        assertEquals(24, rects[0].right)
        assertEquals(800, rects[0].bottom)
    }

    @Test
    fun `the exclusion rect follows the handle when it is laid out at a new size`() {
        val button = handle(24, 800)
        button.layout(0, 0, 1080, 24)

        val rects = button.systemGestureExclusionRects
        assertEquals(1, rects.size)
        assertEquals(1080, rects[0].right)
        assertEquals(24, rects[0].bottom)
    }

    @Test
    fun `the stacked split stops short of the bottom mandatory gesture area`() {
        // 1920 tall root, 24px handle, 48px gesture bar at the bottom: the
        // master pane can grow until the handle's bottom edge reaches the
        // gesture area, not until it reaches the window bottom.
        val range = masterHeightRange(rootHeight = 1920, handleThickness = 24, reservedTop = 0, reservedBottom = 48)

        assertEquals(0, range.first)
        assertEquals(1848, range.last)
    }

    @Test
    fun `the handle reaches the window top while the status bar is showing`() {
        val reservedTop = reservedTopForHandle(mandatoryInsetTop = 72, statusBarVisible = true)
        val range = masterHeightRange(rootHeight = 1920, handleThickness = 24, reservedTop = reservedTop, reservedBottom = 48)

        assertEquals(0, reservedTop)
        assertEquals(0, range.first)
        assertEquals(1848, range.last)
    }

    @Test
    fun `in fullscreen the handle stays below the top mandatory gesture area`() {
        val reservedTop = reservedTopForHandle(mandatoryInsetTop = 72, statusBarVisible = false)
        val range = masterHeightRange(rootHeight = 1920, handleThickness = 24, reservedTop = reservedTop, reservedBottom = 48)

        assertEquals(72, reservedTop)
        assertEquals(72, range.first)
        assertEquals(1848, range.last)
    }

    @Test
    fun `without mandatory gesture insets the handle can travel the whole root`() {
        val range = masterHeightRange(rootHeight = 1920, handleThickness = 24, reservedTop = 0, reservedBottom = 0)

        assertEquals(0, range.first)
        assertEquals(1896, range.last)
    }

    @Test
    fun `a root smaller than the reserved areas still yields a usable range`() {
        // Before the split root is laid out its height is 0; the range must
        // stay non-empty so coerceIn does not throw.
        val range = masterHeightRange(rootHeight = 0, handleThickness = 24, reservedTop = 72, reservedBottom = 48)

        assertEquals(0, range.first)
        assertEquals(0, range.last)
        assertEquals(0, 0.coerceIn(range))
    }
}
