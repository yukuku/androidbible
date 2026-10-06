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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

    private fun render(mode: ReadingGuideMode, multiplePassages: Boolean, opaque: Boolean, lazy: Boolean, startMargin: Int, rtl: Boolean): Bitmap {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val view = ComposeView(activity)
        val ranges = listOf(ReadingRange(0x280905, 0x280906)) + if (multiplePassages) listOf(ReadingRange(0x280908, 0x280909)) else emptyList()
        val guide = ReadingGuide(mode, ranges)
        view.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                val rows: @Composable () -> Unit = {
                    Column {
                        for (ari in if (multiplePassages) (0x280905..0x280909).toList() else (0x280905..0x280907).toList()) {
                            ReadingGuideRow(
                                guide = guide,
                                ari = ari,
                                color = ink,
                                backgroundColor = android.graphics.Color.WHITE,
                                startInsetPx = startMargin.toFloat(),
                            ) {
                                Box(Modifier.fillMaxWidth().height(30.dp).background(if (opaque) Color(highlight) else Color.Transparent).onGloballyPositioned {
                                    val expectedInset = if (mode == ReadingGuideMode.LINE && guide.includes(ari)) maxOf(startMargin, 10) else startMargin
                                    assertEquals(if (rtl) 0f else expectedInset.toFloat(), it.positionInRoot().x, 0f)
                                    assertEquals(200 - expectedInset, it.size.width)
                                })
                            }
                        }
                    }
                }
                if (lazy) {
                    LazyColumn(Modifier.height(90.dp).background(Color.White), contentPadding = PaddingValues(start = startMargin.dp)) {
                        item { rows() }
                    }
                } else {
                    Column(Modifier.background(Color.White).padding(start = startMargin.dp)) { rows() }
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
    fun `the two dp line stops at the passage boundary without shifting or narrowing verses`() {
        val bitmap = render(ReadingGuideMode.LINE, false, false, false, 16, rtl = false)
        assertEquals(90, bitmap.height)
        assertTrue(bitmap.getPixel(4, 0) != ink)
        assertTrue(bitmap.getPixel(4, 59) != ink)
        assertEquals(ink, bitmap.getPixel(4, 29))
        assertEquals(ink, bitmap.getPixel(4, 30))
        for (y in listOf(10, 40)) {
            assertEquals(ink, bitmap.getPixel(4, y))
            assertEquals(ink, bitmap.getPixel(5, y))
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(6, y))
        }
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(4, 70))
        for (y in listOf(10, 40, 70)) assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(10, y))
    }

    @Test
    fun `the edge line stays outside opaque verse backgrounds`() {
        val bitmap = render(ReadingGuideMode.LINE, false, true, false, 16, rtl = false)
        assertEquals(ink, bitmap.getPixel(4, 10))
        assertEquals(ink, bitmap.getPixel(4, 40))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(4, 70))
        for (y in listOf(10, 40, 70)) {
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(15, y))
            assertEquals(highlight, bitmap.getPixel(16, y))
            assertEquals(highlight, bitmap.getPixel(199, y))
        }
    }

    @Test
    fun `off mode preserves verse dimensions and the entire highlight background`() {
        val bitmap = render(ReadingGuideMode.OFF, false, true, false, 16, rtl = false)
        assertEquals(90, bitmap.height)
        for (y in listOf(10, 40, 70)) assertEquals(highlight, bitmap.getPixel(16, y))
    }

    @Test
    fun `the left line marks both disjoint passages and leaves the gap unmarked`() {
        val bitmap = render(ReadingGuideMode.LINE, true, false, false, 16, rtl = false)
        assertEquals(150, bitmap.height)
        for (y in listOf(10, 40, 100, 130)) assertEquals(ink, bitmap.getPixel(4, y))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(4, 70))
        for (y in listOf(10, 40, 70, 100, 130)) assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(10, y))
    }

    @Test
    fun `lazy list padding does not clip the edge line or shift verse content`() {
        val bitmap = render(ReadingGuideMode.LINE, false, true, true, 16, rtl = false)
        assertEquals(ink, bitmap.getPixel(4, 10))
        assertEquals(ink, bitmap.getPixel(5, 40))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(4, 70))
        for (y in listOf(10, 40, 70)) {
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(15, y))
            assertEquals(highlight, bitmap.getPixel(16, y))
        }
    }
    @Test
    fun `small margins move only marked verses enough to clear the inset line`() {
        for (margin in listOf(0, 4, 8, 10)) {
            val bitmap = render(ReadingGuideMode.LINE, false, true, true, margin, rtl = false)
            for (y in listOf(10, 40)) {
                assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(3, y))
                assertEquals(ink, bitmap.getPixel(4, y))
                assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(9, y))
                assertEquals(highlight, bitmap.getPixel(10, y))
            }
            assertEquals(highlight, bitmap.getPixel(margin, 70))
        }
    }

    @Test
    fun `RTL mirrors the line and clearance for normal reduced and asymmetric padding`() {
        for (margin in listOf(0, 4, 8, 10, 16)) {
            for (lazy in listOf(false, true)) {
                val bitmap = render(ReadingGuideMode.LINE, false, true, lazy, margin, rtl = true)
                for (y in listOf(10, 40)) {
                    assertEquals(ink, bitmap.getPixel(194, y))
                    assertEquals(ink, bitmap.getPixel(195, y))
                    assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(196, y))
                    val textRight = 200 - maxOf(margin, 10)
                    assertEquals(highlight, bitmap.getPixel(textRight - 1, y))
                    assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(textRight, y))
                }
                assertEquals(highlight, bitmap.getPixel(199 - margin, 70))
                assertEquals(highlight, bitmap.getPixel(0, 10))
            }
        }
    }

    @Test
    fun `RTL off mode and disjoint passages preserve the unmarked verse width`() {
        val off = render(ReadingGuideMode.OFF, false, true, false, 0, rtl = true)
        assertEquals(highlight, off.getPixel(195, 10))
        val disjoint = render(ReadingGuideMode.LINE, true, false, false, 16, rtl = true)
        for (y in listOf(10, 40, 100, 130)) assertEquals(ink, disjoint.getPixel(195, y))
        assertEquals(android.graphics.Color.WHITE, disjoint.getPixel(195, 70))
        assertTrue(disjoint.getPixel(195, 0) != ink)
        assertTrue(disjoint.getPixel(195, 59) != ink)
    }
}
