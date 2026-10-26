package yuku.alkitab.base.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.TextColorUtil
import yuku.alkitab.base.verses.splitVerseAroundAttributes
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

        val band = Highlights.alphaMix(0xffff00)
        val bandStyles = result.text.spanStyles.filter { it.item.background == Color(band) }
        assertEquals(1, bandStyles.size)
        assertEquals(3, bandStyles[0].start)
        assertEquals(8, bandStyles[0].end)
        val foreground = result.text.spanStyles.last { it.start <= 3 && it.end > 3 && it.item.color != Color.Unspecified }.item.color
        assertEquals(Color(FONT_COLOR), foreground)
    }

    @Test
    fun `partial highlight spanning both text blocks keeps exact ranges and colors`() {
        val measurer = TextMeasurer(
            createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
            Density(1f),
            LayoutDirection.Ltr,
        )
        val textStyle = TextStyle(fontSize = 17.sp)
        val texts = listOf(
            "Words of mercy and peace fill this verse. ".repeat(4),
            "First line\nSecond line with more words",
        )
        for (text in texts) for (isChecked in listOf(false, true)) for (isVerseNumberShown in listOf(false, true)) {
            val plain = VerseRendererCompose.render(isVerseNumberShown, ARI, text, "1", checked = isChecked)
            val narrow = measurer.measure(plain.text, textStyle, constraints = Constraints(maxWidth = 160))
            val split = narrow.getLineEnd(0)
            val source = VerseRendererCompose.render(
                isVerseNumberShown = isVerseNumberShown,
                ari = ARI,
                text = text,
                verseNumberText = "1",
                highlightInfo = partialYellow(text, split - 4 - plain.startPosAfterVerseNumber, split + 5 - plain.startPosAfterVerseNumber),
                checked = isChecked,
            )
            val originalBand = source.text.spanStyles.single { it.item.background != Color.Unspecified }
            assertEquals(split - 4, originalBand.start)
            assertEquals(split + 5, originalBand.end)

            val (top, continuation) = splitVerseAroundAttributes(source, source.text, narrow, narrow.getLineBottom(0).toInt())
            val bottom = requireNotNull(continuation)
            val topBand = top.text.spanStyles.single { it.item.background != Color.Unspecified }
            val bottomBand = bottom.text.spanStyles.single { it.item.background != Color.Unspecified }
            assertEquals(originalBand.copy(end = top.text.length), topBand)
            assertEquals(originalBand.copy(start = 0, end = 5), bottomBand)
            assertEquals(source.text.text.substring(split), bottom.text.text)
            assertEquals(
                source.text.text.substring(originalBand.start, originalBand.end).replace("\n", ""),
                top.text.text.substring(topBand.start, topBand.end) + bottom.text.text.substring(bottomBand.start, bottomBand.end),
            )
        }
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

        val band = Highlights.alphaMix(0xffff00)
        val bandStyles = result.text.spanStyles.filter { it.item.background == Color(band) }
        assertEquals(1, bandStyles.size)
        assertEquals(Color.Unspecified, bandStyles[0].item.color)
    }
}
