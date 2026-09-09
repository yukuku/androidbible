package yuku.alkitab.base.widget

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseRendererComposeCheckedHighlightTest {

    private val FONT_COLOR = 0xff212121.toInt()
    private val BACKGROUND = 0xfff0f0f0.toInt()
    private val BLUE_SELECTION = 0xff0277bd.toInt()
    private val ARI = 0x010203

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        yuku.afw.App.initWithAppContext(context)
        yuku.afw.storage.Preferences.setInt(context.getString(R.string.pref_selectedVerseBgColor_key), BLUE_SELECTION)

        val dims = S.CalculatedDimensions().apply {
            fontColor = FONT_COLOR
            fontRedColor = 0xffb71c1c.toInt()
            verseNumberColor = 0xff828282.toInt()
            backgroundColor = BACKGROUND
            indentParagraphRest = 5
        }
        S.overrideAppliedDimensions(dims)
    }

    private fun partialYellow(text: String, start: Int, end: Int) = Highlights.Info().apply {
        colorRgb = 0xffff00
        partial = Highlights.Info.Partial().apply {
            hashCode = Highlights.hashCode(text)
            startOffset = start
            endOffset = end
        }
    }

    @Test
    fun `in a checked verse the highlighted run carries the text color chosen against the band`() {
        val text = "Hello world"
        val result = VerseRendererCompose.render(
            isVerseNumberShown = true,
            ari = ARI,
            text = text,
            verseNumberText = "1",
            highlightInfo = partialYellow(text, 0, 5),
            checked = true,
        )

        val band = Highlights.blendOver(0xffff00, BACKGROUND)
        val bandStyles = result.text.spanStyles.filter { it.item.background == Color(band) }
        assertEquals(1, bandStyles.size)
        assertEquals(3, bandStyles[0].start)
        assertEquals(8, bandStyles[0].end)
        assertEquals(Color(TextColorUtil.getForCheckedVerseHighlight(FONT_COLOR, BLUE_SELECTION, BACKGROUND, band)), bandStyles[0].item.color)
        assertEquals(Color(FONT_COLOR), bandStyles[0].item.color)
    }

    @Test
    fun `an unchecked highlighted verse leaves the run color unspecified so the reading color shows through the band`() {
        val text = "Hello world"
        val result = VerseRendererCompose.render(
            isVerseNumberShown = true,
            ari = ARI,
            text = text,
            verseNumberText = "1",
            highlightInfo = partialYellow(text, 0, 5),
            checked = false,
        )

        val band = Highlights.blendOver(0xffff00, BACKGROUND)
        val bandStyles = result.text.spanStyles.filter { it.item.background == Color(band) }
        assertEquals(1, bandStyles.size)
        assertEquals(Color.Unspecified, bandStyles[0].item.color)
    }
}
