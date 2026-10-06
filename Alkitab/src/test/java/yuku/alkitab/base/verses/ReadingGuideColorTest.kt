package yuku.alkitab.base.verses

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class ReadingGuideColorTest {
    @Test
    fun `default day and night colors keep the text color with sufficient contrast`() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        for ((foregroundId, backgroundId) in listOf(
            R.integer.pref_textColor_default to R.integer.pref_backgroundColor_default,
            R.integer.pref_textColor_night_default to R.integer.pref_backgroundColor_night_default,
        )) {
            val foreground = resources.getInteger(foregroundId)
            val background = resources.getInteger(backgroundId)
            val line = readingGuideLineColor(foreground, background)
            assertEquals(foreground, line)
            assertTrue(ColorUtils.calculateContrast(line, background) >= 3.0)
        }
    }

    @Test
    fun `similar custom colors fall back to contrasting black or white`() {
        for (background in listOf(Color.WHITE, Color.BLACK, 0xff808080.toInt(), 0xfff4ecd8.toInt(), 0xff172337.toInt())) {
            val line = readingGuideLineColor(background, background)
            assertTrue(line == Color.BLACK || line == Color.WHITE)
            assertTrue(ColorUtils.calculateContrast(line, background) >= 3.0)
        }
    }

    @Test
    fun `the line is opaque even when the chosen text color is translucent`() {
        assertEquals(0xff202020.toInt(), readingGuideLineColor(0x202020, Color.WHITE))
    }
}
