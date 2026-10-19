package yuku.alkitab.base.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.widget.VerseRendererCompose

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class HighlightStylePreviewTest {
    private lateinit var applied: S.CalculatedDimensions
    private val ari = 0x2a0301

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        applied = S.CalculatedDimensions().apply {
            fontColor = 0xffeeeeee.toInt()
            fontRedColor = 0xffff8877.toInt()
            verseNumberColor = 0xff77bbff.toInt()
            backgroundColor = 0xff102030.toInt()
            highlightColorAdjustment = 1f
        }
        S.overrideAppliedDimensions(applied)
    }

    @Test
    fun `preview retains active red letter and verse number colors while adjusting only the highlight`() {
        val raw = "@@@0Normal @6Voice@5 @9italic@7"
        val original = VerseRendererCompose.render(true, ari, raw)
        for (amount in listOf(0f, 0.5f, 1f)) {
            val preview = HighlightStyleSheet.renderPreview(ari, raw, true, 0x00ff00, 0xff223344.toInt(), amount)
            assertEquals(original.text.text, preview.text.text)
            assertEquals(original.text.paragraphStyles, preview.text.paragraphStyles)
            assertTrue(preview.text.spanStyles.containsAll(original.text.spanStyles))
            assertTrue(preview.text.spanStyles.any { it.item.color == Color(applied.fontRedColor) })
            assertTrue(preview.text.spanStyles.any { it.item.color == Color(applied.verseNumberColor) })
            assertTrue(preview.text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
            val band = preview.text.spanStyles.last()
            assertEquals(Color(Highlights.blendOver(0x00ff00, 0xff223344.toInt(), amount)), band.item.background)
            assertEquals(original.startPosAfterVerseNumber, band.start)
            assertEquals(original.text.length, band.end)
        }
        assertSame(applied, S.applied())
        assertEquals(0xff102030.toInt(), applied.backgroundColor)
        assertEquals(1f, applied.highlightColorAdjustment)
    }

    @Test
    fun `preview preserves ruby link and gutter metadata for the reader layout`() {
        val raw = "@@@1@6@<r=reading@>base@/@5 @<f1@>@/"
        val original = VerseRendererCompose.render(true, ari, raw)
        val preview = HighlightStyleSheet.renderPreview(ari, raw, true, 0xffff00, applied.backgroundColor, 0.5f)
        assertTrue(original.rubies.isNotEmpty())
        assertTrue(original.inlineLinks.isNotEmpty())
        assertEquals(original.rubies, preview.rubies)
        assertEquals(original.inlineLinks, preview.inlineLinks)
        assertEquals(original.gutterVerseNumber, preview.gutterVerseNumber)
        assertEquals(raw, preview.sourceText)
        assertEquals(original.text.paragraphStyles, preview.text.paragraphStyles)
    }

    @Test
    fun `plain verses and hidden verse numbers retain their reader representation`() {
        for (raw in listOf("Plain verse", "@@@0Normal @6Voice@5")) {
            val original = VerseRendererCompose.render(false, ari, raw)
            val preview = HighlightStyleSheet.renderPreview(ari, raw, false, 0xffff00, applied.backgroundColor, 0f)
            assertEquals(original.text.text, preview.text.text)
            assertEquals(original.gutterVerseNumber, preview.gutterVerseNumber)
            assertEquals(0, preview.text.spanStyles.last().start)
        }
    }
}
