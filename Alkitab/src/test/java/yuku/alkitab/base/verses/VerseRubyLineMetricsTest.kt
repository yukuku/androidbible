package yuku.alkitab.base.verses

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native graphics so `TextPaint.fontMetrics` reports real values. */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseRubyLineMetricsTest {

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `a verse without ruby reserves no band`() {
        val m = computeLineMetrics(null, 16f, android.graphics.Typeface.NORMAL, 1f, 2f)
        assertEquals(0f, m.rubyBandPx)
        assertTrue(m.baseAscentPx > 0f)
    }

    @Test
    fun `ruby adds a band on top of the line and the gutter number moves down by the whole surplus`() {
        val plain = computeLineMetrics(null, 16f, android.graphics.Typeface.NORMAL, 1f, 2f)
        val ruby = computeLineMetrics(null, 16f, android.graphics.Typeface.NORMAL, 1f, 2f, rubyFontSizeDp = 8f)
        assertTrue(ruby.rubyBandPx > 0f)
        assertEquals(plain.lineHeightSp + ruby.rubyBandPx / 2f, ruby.lineHeightSp, 0.01f)
        assertEquals(plain.rowExtraPaddingPx, ruby.rowExtraPaddingPx)
        assertEquals(plain.baseAscentPx, ruby.baseAscentPx, 0.01f)
        assertEquals(ruby.rubyBandPx, ruby.gutterTopPaddingPx.toFloat(), 1f)
    }
}
