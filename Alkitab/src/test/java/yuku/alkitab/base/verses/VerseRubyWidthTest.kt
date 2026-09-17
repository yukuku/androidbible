package yuku.alkitab.base.verses

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.util.Ari

/** Native graphics so text measurement reports real advances. */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTextApi::class)
class VerseRubyWidthTest {

    private val verse =
        "@@@<rs=H3605@>Jadi@/, @<rs=H3117@>seluruh@/ @<rs=H2416@>masa@/ @<rs=H121@>hidup@/ @<rs=H1961@>Adam@/ " +
            "@<rs=H1961@>adalah@/ @<rs=H8672 H3967 H7970@>930@/ @<rs=H8141@>tahun@/, @<rs=H4191@>kemudian@/ dia @<rs=H4191@>mati@/."

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `padding a ruby base never pushes a line past the width it was laid out for`() {
        val density = Density(2f, 1f)
        val textMeasurer = androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(
                ApplicationProvider.getApplicationContext()
            ),
            defaultDensity = density,
            defaultLayoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        )
        val failures = mutableListOf<String>()
        for (gutter in listOf(false, true)) {
        val render = VerseRendererCompose.render(
            isVerseNumberShown = gutter,
            ari = Ari.encode(0, 5, 5),
            text = verse,
            verseNumberText = "5",
        )
        for (widthPx in listOf(320, 360, 420, 480, 540, 600, 660, 720, 800, 900, 1000)) {
        for (fontSizeDp in listOf(10f, 12f, 14f, 17f, 20f, 22f, 24f, 26f, 28f, 30f, 34f)) {
            val textStyle = TextStyle(fontSize = fontSizeDp.sp, fontFamily = FontFamily.SansSerif)
            val rubyStyle = textStyle.copy(fontSize = (fontSizeDp * RUBY_FONT_SIZE_RATIO).sp)
            val widened = widenRubyBases(render.text, render.rubies, textStyle, rubyStyle, textMeasurer, density)
            val layout = textMeasurer.measure(
                widened,
                textStyle,
                constraints = Constraints(maxWidth = widthPx),
                density = density,
            )
            for (line in 0 until layout.lineCount) {
                val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                if (lineWidth > widthPx + 0.5f) {
                    failures += "gutter=$gutter width=$widthPx ${fontSizeDp}dp line $line: ${lineWidth}px"
                }
            }
        }
        }
        }
        assertTrue("lines ran past the width they were given:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
