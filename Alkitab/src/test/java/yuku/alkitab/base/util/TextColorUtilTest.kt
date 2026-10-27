package yuku.alkitab.base.util

import androidx.core.graphics.ColorUtils
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextColorUtilTest {
    @Test
    fun `full adjustment meets minimum contrast across arbitrary text and background colors`() {
        val random = Random(42)
        repeat(10000) {
            val foreground = random.nextInt() or 0xff000000.toInt()
            val background = random.nextInt() or 0xff000000.toInt()
            val result = TextColorUtil.adjustForContrast(foreground, background, 1f)
            assertTrue("${result.toUInt().toString(16)} on ${background.toUInt().toString(16)}", ColorUtils.calculateContrast(result, background) >= 4.5)
            if (ColorUtils.calculateContrast(foreground, background) >= 4.5) assertEquals(foreground, result)
            assertEquals(foreground, TextColorUtil.adjustForContrast(foreground, background, 0f))
        }
    }

    @Test
    fun `export production color results for the standalone preview parity check`() {
        val random = Random(20261026)
        val cases = buildList {
            repeat(256) {
                val foreground = random.nextInt() or 0xff000000.toInt()
                val background = random.nextInt() or 0xff000000.toInt()
                for (percent in listOf(0, 1, 37, 50, 99, 100)) {
                    val result = TextColorUtil.adjustForContrast(foreground, background, percent / 100f)
                    add("[${foreground and 0xffffff},${background and 0xffffff},$percent,${result and 0xffffff}]")
                }
            }
        }
        val file = File("build/snapshots/text-adjustment/color-fixtures.json")
        requireNotNull(file.parentFile).mkdirs()
        file.writeText(cases.joinToString(prefix = "[", postfix = "]"))
    }

    @Test
    fun `red text retains a red tint when corrected over bright and dark backgrounds`() {
        val red = 0xffb71c1c.toInt()
        for (background in listOf(0xff000000, 0xff808080, 0xffffff00, 0xff0000ff, 0xfff0f0f0)) {
            val adjusted = TextColorUtil.adjustForContrast(red, background.toInt(), 1f)
            assertTrue(ColorUtils.calculateContrast(adjusted, background.toInt()) >= 4.5)
            assertTrue(((adjusted shr 16) and 255) > ((adjusted shr 8) and 255))
            assertEquals((adjusted shr 8) and 255, adjusted and 255)
        }
    }

    @Test
    fun `a readable tinted alternative is preferred when the nearest correction loses the text hue`() {
        val background = ColorUtils.compositeColors(Highlights.alphaMix(0xff8000), 0xff303030.toInt())
        val adjusted = TextColorUtil.adjustForContrast(0xffef9a9a.toInt(), background, 1f)
        assertTrue(ColorUtils.calculateContrast(adjusted, background) >= 4.5)
        assertTrue(((adjusted shr 16) and 255) > ((adjusted shr 8) and 255))
    }

    @Test
    fun `each percentage meets its target and preserves colors already meeting that target`() {
        val random = Random(73)
        repeat(100) {
            val foreground = random.nextInt() or 0xff000000.toInt()
            val background = random.nextInt() or 0xff000000.toInt()
            for (percent in (0..100) + (100 downTo 0)) {
                val amount = percent / 100f
                val target = 1.0 + 3.5 * percent / 100.0
                assertEquals(target, TextColorUtil.targetContrast(amount), 0.0)
                val result = TextColorUtil.adjustForContrast(foreground, background, amount)
                assertTrue("$percent%: $target", ColorUtils.calculateContrast(result, background) >= target)
                if (ColorUtils.calculateContrast(foreground, background) >= target) assertEquals(foreground, result)
            }
        }
    }

    @Test
    fun `intermediate targets solve contrast directly rather than blending the full correction`() {
        val foreground = 0xffaaaaaa.toInt()
        val background = 0xffcccccc.toInt()
        val result = TextColorUtil.adjustForContrast(foreground, background, 0.5f)
        val actual = ColorUtils.calculateContrast(result, background)
        assertTrue(actual >= 2.75 && actual < 2.85)
        assertEquals(4.5, TextColorUtil.targetContrast(1f), 0.0)
        assertEquals(1.0, TextColorUtil.targetContrast(0f), 0.0)
    }

    @Test
    fun `invalid adjustment values are bounded and nonfinite values use the default correction`() {
        assertEquals(0f, TextColorUtil.normalizeAdjustment(-1f))
        assertEquals(1f, TextColorUtil.normalizeAdjustment(2f))
        assertEquals(0.7f, TextColorUtil.normalizeAdjustment(Float.NaN))
        assertEquals(0.7f, TextColorUtil.normalizeAdjustment(Float.POSITIVE_INFINITY))
        assertEquals(0.37f, TextColorUtil.normalizeAdjustment(0.371f))
        assertEquals(0.38f, TextColorUtil.normalizeAdjustment(0.376f))
    }

    @Test
    fun `selection is composited over the page before a fixed highlight band`() {
        val page = 0xff202020.toInt()
        val selection = 0xff0277bd.toInt()
        val actual = TextColorUtil.readingBackground(page, selection, true)
        assertEquals(ColorUtils.compositeColors(0xa00277bd.toInt(), page), actual)
        assertEquals(page, TextColorUtil.readingBackground(page, selection, false))
        val band = Highlights.alphaMix(0xffff00)
        val painted = ColorUtils.compositeColors(band, actual)
        val result = TextColorUtil.adjustForContrast(0xffaeaeae.toInt(), painted, 1f)
        assertTrue(ColorUtils.calculateContrast(result, painted) >= 4.5)
    }
}
