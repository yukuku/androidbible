package yuku.alkitab.base.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextColorUtilTest {

    private val BLACK = 0xff000000.toInt()
    private val WHITE = 0xffffffff.toInt()
    private val LIGHT_PAGE = 0xfff0f0f0.toInt()
    private val LIGHT_PAGE_TEXT = 0xff212121.toInt()
    private val NIGHT_PAGE = 0xff000000.toInt()
    private val NIGHT_PAGE_TEXT = 0xffaeaeae.toInt()
    private val BLUE_SELECTION = 0xff0277bd.toInt()
    private val LIGHT_YELLOW_SELECTION = 0xfffff59d.toInt()

    @Test
    fun `getForCheckedVerse picks white on a dark selection and black on a light one`() {
        assertEquals(WHITE, TextColorUtil.getForCheckedVerse(BLUE_SELECTION))
        assertEquals(BLACK, TextColorUtil.getForCheckedVerse(LIGHT_YELLOW_SELECTION))
    }

    @Test
    fun `a yellow band on a light page keeps the reading color even though the blue selection alone would force white`() {
        val band = Highlights.blendOver(0xffff00, LIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(LIGHT_PAGE_TEXT, BLUE_SELECTION, LIGHT_PAGE, band)
        assertEquals(LIGHT_PAGE_TEXT, color)
    }

    @Test
    fun `a band that stays dark on the night page keeps the white the blue selection picks`() {
        val band = Highlights.blendOver(0x0080ff, NIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(NIGHT_PAGE_TEXT, BLUE_SELECTION, NIGHT_PAGE, band)
        assertEquals(WHITE, color)
    }

    @Test
    fun `a light selection under a yellow band stays black because black reads better than the grey reading color`() {
        val band = Highlights.blendOver(0xffff00, LIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(0xff606060.toInt(), LIGHT_YELLOW_SELECTION, LIGHT_PAGE, band)
        assertEquals(BLACK, color)
    }

    @Test
    fun `a reading color without an alpha channel is treated as opaque`() {
        val band = Highlights.blendOver(0xffff00, LIGHT_PAGE)
        val color = TextColorUtil.getForCheckedVerseHighlight(0x212121, BLUE_SELECTION, LIGHT_PAGE, band)
        assertEquals(LIGHT_PAGE_TEXT, color)
    }
}
