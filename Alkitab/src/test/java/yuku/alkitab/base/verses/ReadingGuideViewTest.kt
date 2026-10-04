package yuku.alkitab.base.verses

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.util.ReadingRange

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "mdpi")
class ReadingGuideViewTest {
    private fun view() = ReadingGuideView(TextView(ApplicationProvider.getApplicationContext()).apply { text = "Verse text" })
    private val ranges = listOf(ReadingRange(0x280905, 0x280906), ReadingRange(0x28090e, 0x280917))

    @Test
    fun `only the actual boundaries receive labels and a recycled row drops them`() {
        val view = view()
        val guide = ReadingGuide(ReadingGuideMode.LABELS, ranges)
        view.bind(guide, 0x280905, true, 0xff000000.toInt())
        assertEquals(View.VISIBLE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(2).visibility)
        view.bind(guide, 0x280906, true, 0xff000000.toInt())
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(View.VISIBLE, view.getChildAt(2).visibility)
        view.bind(guide, 0x280909, true, 0xff000000.toInt())
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(2).visibility)
    }

    @Test
    fun `line mode and recycled rows keep the verse at its original position and width`() {
        val view = view()
        view.bind(ReadingGuide(ReadingGuideMode.LINE, ranges), 0x280905, true, 0xff000000.toInt())
        view.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertEquals(0, view.getChildAt(1).paddingLeft)
        assertEquals(0, view.content.left)
        assertEquals(200, view.content.width)
        view.bind(ReadingGuide.NONE, 0x280905, true, 0xff000000.toInt())
        assertEquals(0, view.getChildAt(1).paddingLeft)
        assertEquals(View.GONE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(2).visibility)
    }
}
