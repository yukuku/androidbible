package yuku.alkitab.base.verses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.util.ReadingRange

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingGuideRowTest {
    private val ink = 0xff202020.toInt()
    private val highlight = 0xffffff00.toInt()

    private fun render(mode: ReadingGuideMode, multiplePassages: Boolean = false): Bitmap {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val view = ComposeView(activity)
        val ranges = listOf(ReadingRange(0x280905, 0x280906)) + if (multiplePassages) listOf(ReadingRange(0x280908, 0x280909)) else emptyList()
        val guide = ReadingGuide(mode, ranges)
        view.setContent {
            Column(Modifier.background(Color.White)) {
                for (ari in if (multiplePassages) (0x280905..0x280909).toList() else (0x280905..0x280907).toList()) {
                    ReadingGuideRow(guide, ari, true, ink) {
                        Box(Modifier.fillMaxWidth().height(30.dp).background(Color(highlight)))
                    }
                }
            }
        }
        activity.setContentView(view, ViewGroup.LayoutParams(200, ViewGroup.LayoutParams.WRAP_CONTENT))
        repeat(2) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    @Test
    fun `the two dp line stops at the passage boundary and preserves existing highlight backgrounds`() {
        val bitmap = render(ReadingGuideMode.LINE)
        assertEquals(90, bitmap.height)
        for (y in listOf(10, 40)) {
            assertEquals(ink, bitmap.getPixel(0, y))
            assertEquals(ink, bitmap.getPixel(1, y))
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(2, y))
        }
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(0, 70))
        for (y in listOf(10, 40, 70)) assertEquals(highlight, bitmap.getPixel(10, y))
    }

    @Test
    fun `boundary labels reserve space outside existing verse highlights`() {
        val bitmap = render(ReadingGuideMode.LABELS)
        assertTrue(bitmap.height > 90)
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(199, 0))
        assertEquals(highlight, bitmap.getPixel(199, bitmap.height - 1))
    }

    @Test
    fun `off mode preserves verse dimensions and the entire highlight background`() {
        val bitmap = render(ReadingGuideMode.OFF)
        assertEquals(90, bitmap.height)
        for (y in listOf(10, 40, 70)) assertEquals(highlight, bitmap.getPixel(0, y))
    }

    @Test
    fun `the left line marks both disjoint passages and leaves the gap unmarked`() {
        val bitmap = render(ReadingGuideMode.LINE, true)
        assertEquals(150, bitmap.height)
        for (y in listOf(10, 40, 100, 130)) assertEquals(ink, bitmap.getPixel(0, y))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(0, 70))
        for (y in listOf(10, 40, 70, 100, 130)) assertEquals(highlight, bitmap.getPixel(10, y))
    }

    @Test
    fun `each disjoint passage receives its own start and end labels`() {
        val single = render(ReadingGuideMode.LABELS)
        val multiple = render(ReadingGuideMode.LABELS, true)
        assertEquals(2 * (single.height - 90), multiple.height - 150)
    }
}
