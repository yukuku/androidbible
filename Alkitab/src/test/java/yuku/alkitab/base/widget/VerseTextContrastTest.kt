package yuku.alkitab.base.widget

import android.graphics.Color as AndroidColor
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseTextContrastTest {
    private lateinit var dims: S.CalculatedDimensions
    private val raw = "@@Ordinary @6Jesus speaks here@5 ordinary."
    private val body = "Ordinary Jesus speaks here ordinary."

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        dims = S.CalculatedDimensions().apply {
            fontColor = 0xff212121.toInt()
            fontRedColor = 0xffb71c1c.toInt()
            verseNumberColor = 0xff828282.toInt()
            backgroundColor = 0xfff0f0f0.toInt()
        }
        S.overrideAppliedDimensions(dims)
    }

    @Test
    fun `both renderers preserve fixed highlights and readable red letters across themes and selections`() {
        val themes = listOf(
            Triple(0xfff0f0f0, 0xff212121, 0xffb71c1c),
            Triple(0xff000000, 0xffaeaeae, 0xffa25c5c),
            Triple(0xff303030, 0xffdddddd, 0xffef9a9a),
            Triple(0xfffff8e7, 0xff3b3021, 0xff8b2c1c),
            Triple(0xff808080, 0xff808080, 0xffb71c1c),
        )
        for ((page, font, red) in themes) {
            dims.backgroundColor = page.toInt()
            dims.fontColor = font.toInt()
            dims.fontRedColor = red.toInt()
            for (selection in listOf(0xff0277bd, 0xfffff59d, 0xffe53935)) {
                Preferences.setInt(ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.pref_selectedVerseBgColor_key), selection.toInt())
                for (isSelected in listOf(false, true)) {
                    for (rgb in listOf<Int?>(null) + TypeHighlightDialog.PRESET_RGBS.toList()) {
                        for (isPartial in listOf(false, true)) {
                            val highlight = rgb?.let { color ->
                                Highlights.Info().apply {
                                    colorRgb = color
                                    partial = if (isPartial) Highlights.Info.Partial().apply {
                                        hashCode = body.hashCode()
                                        startOffset = 4
                                        endOffset = 18
                                    } else null
                                }
                            }
                            verify(raw, highlight, isSelected, selection.toInt())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `zero adjustment preserves intended ordinary and red colors even in selected verses`() {
        dims.textColorAdjustment = 0f
        dims.backgroundColor = 0xff808080.toInt()
        dims.fontColor = 0xff808080.toInt()
        Preferences.setInt(ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.pref_selectedVerseBgColor_key), 0xff0277bd.toInt())
        val result = VerseRendererCompose.render(false, 0x010101, raw, checked = true).text
        assertEquals(dims.fontColor, composePaint(result, 0).first)
        assertEquals(dims.fontRedColor, composePaint(result, body.indexOf("Jesus")).first)
    }

    @Test
    fun `text adjustment changes foregrounds without changing highlight colors or ranges`() {
        val highlight = Highlights.Info().apply { colorRgb = 0x0000ff }
        val texts = listOf("Plain verse", raw, "@@@1Ordinary @6Jesus speaks here@5 ordinary.")
        for (text in texts) for (amount in listOf(0f, 0.37f, 1f)) {
            dims.textColorAdjustment = amount
            val result = VerseRendererCompose.render(true, 0x010101, text, highlightInfo = highlight, checked = true)
            val bands = result.text.spanStyles.filter { it.item.background != Color.Unspecified }
            assertEquals(1, bands.size)
            assertEquals(0xa00000ff.toInt(), bands.single().item.background.toArgb())
            assertEquals(result.startPosAfterVerseNumber, bands.single().start)
            assertEquals(result.text.length, bands.single().end)
        }
    }

    private fun verify(text: String, highlight: Highlights.Info?, isSelected: Boolean, selection: Int) {
        val view = TextView(ApplicationProvider.getApplicationContext())
        VerseRenderer.render(lText = view, isVerseNumberShown = true, ari = 0x010101, text = text, highlightInfo = highlight, checked = isSelected)
        val legacy = view.text as Spanned
        val composed = VerseRendererCompose.render(true, 0x010101, text, highlightInfo = highlight, checked = isSelected)
        assertEquals(legacy.toString(), composed.text.text)
        val selectionBackground = if (isSelected) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(selection, 160), dims.backgroundColor) else dims.backgroundColor
        for (index in legacy.indices) {
            val (foreground, band) = composePaint(composed.text, index)
            val legacyPaint = TextPaint().apply { color = dims.fontColor }
            legacy.getSpans(index, index + 1, CharacterStyle::class.java).forEach { it.updateDrawState(legacyPaint) }
            assertEquals("foreground at $index", foreground, legacyPaint.color)
            assertEquals("band at $index", band, legacyPaint.bgColor)
            val painted = ColorUtils.compositeColors(band, selectionBackground)
            assertTrue("contrast at $index: ${ColorUtils.calculateContrast(foreground, painted)}", ColorUtils.calculateContrast(foreground, painted) >= 4.5)
            if (band != 0) assertEquals(160, AndroidColor.alpha(band))
        }
        val redOffset = composed.text.text.indexOf("Jesus")
        val red = composePaint(composed.text, redOffset).first
        assertTrue("red identity: page=${dims.backgroundColor.toUInt().toString(16)} original=${dims.fontRedColor.toUInt().toString(16)} selection=${selection.toUInt().toString(16)} selected=$isSelected highlight=${highlight?.colorRgb?.toString(16)} partial=${highlight?.partial != null} result=${red.toUInt().toString(16)}", AndroidColor.red(red) > AndroidColor.green(red))
        if (highlight?.partial == null) {
            assertNotEquals("red and ordinary text", composePaint(composed.text, composed.startPosAfterVerseNumber).first, red)
        }
    }

    private fun composePaint(text: AnnotatedString, offset: Int): Pair<Int, Int> {
        var foreground = dims.fontColor
        var background = 0
        text.spanStyles.filter { offset >= it.start && offset < it.end }.forEach {
            if (it.item.color != Color.Unspecified) foreground = it.item.color.toArgb()
            if (it.item.background != Color.Unspecified) background = it.item.background.toArgb()
        }
        return foreground to background
    }
}
