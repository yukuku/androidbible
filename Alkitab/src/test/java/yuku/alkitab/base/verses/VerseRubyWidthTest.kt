package yuku.alkitab.base.verses

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
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

    private val verses = listOf(
        "@@@<rs=H3605@>Jadi@/, @<rs=H3117@>seluruh@/ @<rs=H2416@>masa@/ @<rs=H121@>hidup@/ @<rs=H1961@>Adam@/ " +
            "@<rs=H1961@>adalah@/ @<rs=H8672 H3967 H7970@>930@/ @<rs=H8141@>tahun@/, @<rs=H4191@>kemudian@/ dia @<rs=H4191@>mati@/.",
        // multi-word base runs, which are held on one line, next to readings far wider than their bases
        "@@@<rs=H3117@>Jadi, masa hidup@/ @<rs=H3382@>Yared@/ @<rs=H8147 H8346 H8141 H8672 H3967@>adalah 962@/ " +
            "@<rs=H8141@>tahun@/, @<rs=H4191@>kemudian dia mati@/. [@<rs=H834 H8141@>@/]",
        // every word annotated, so most of the spaces beside a base carry padding
        "@@@<rs=H4390@>penuhilah@/ @<rs=H4325@>air@/ @<rs=H322@>di@/ @<rs=H3220@>lautan@/, @<rs=H5921@>dan@/ " +
            "@<rs=H5775@>burung-burung@/ @<rs=H7235@>berlipat@/ @<rs=H776@>ganda@/ @<rs=H5921@>di@/ @<rs=H776@>bumi@/.",
    )

    /**
     * A split inside a word that only a join could have caused. A word longer
     * than the line it is on has to be split whatever we do, so the unit around
     * the cut counts only when a non-breaking space is holding it together.
     */
    private fun joinSplitAWord(text: CharSequence, cut: Int): Boolean {
        if (cut <= 0 || cut >= text.length) return false
        if (!text[cut - 1].isLetterOrDigit() || !text[cut].isLetterOrDigit()) return false
        fun breakable(ch: Char) = ch == ' ' || ch == '\n'
        var from = cut
        while (from > 0 && !breakable(text[from - 1])) from--
        var until = cut
        while (until < text.length && !breakable(text[until])) until++
        return (from until until).any { text[it] == '\u00a0' }
    }

    @Test
    fun `holding words together keeps the styles and links the verse text carries`() {
        val density = Density(2f, 1f)
        val textMeasurer = androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(
                ApplicationProvider.getApplicationContext()
            ),
            defaultDensity = density,
            defaultLayoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        )
        val render = VerseRendererCompose.render(
            isVerseNumberShown = true,
            ari = Ari.encode(0, 5, 5),
            text = verses[0],
            verseNumberText = "5",
        )
        val tahun = render.text.text.indexOf("tahun")
        val decorated = androidx.compose.ui.text.buildAnnotatedString {
            append(render.text)
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), tahun, tahun + 5)
            addLink(LinkAnnotation.Clickable("dictionary") {}, tahun, tahun + 5)
            addStringAnnotation("tag", "value", tahun, tahun + 5)
        }
        val textStyle = TextStyle(fontSize = 17f.sp, fontFamily = FontFamily.SansSerif)
        val rubyStyle = textStyle.copy(fontSize = (17f * RUBY_FONT_SIZE_RATIO).sp)
        val widened = widenRubyBases(decorated, render.rubies, textStyle, rubyStyle, textMeasurer, density, 720)

        assertTrue("the fixture did not hold any word together", widened.text.contains('\u00a0'))
        assertEquals(decorated.text.length, widened.text.length)
        for (range in decorated.spanStyles) {
            assertTrue(
                "a style the verse carried was lost: $range",
                widened.spanStyles.any { it.item == range.item && it.start == range.start && it.end == range.end },
            )
        }
        assertEquals(1, widened.getLinkAnnotations(0, widened.length).size)
        assertEquals(tahun, widened.getLinkAnnotations(0, widened.length)[0].start)
        assertEquals(listOf("value"), widened.getStringAnnotations(0, widened.length).map { it.item })
        assertTrue(
            "the underline span was lost",
            widened.spanStyles.any { it.item.textDecoration == TextDecoration.Underline && it.start == tahun },
        )
    }

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `widening and holding a base run never overruns a line or breaks a word`() {
        val density = Density(2f, 1f)
        val textMeasurer = androidx.compose.ui.text.TextMeasurer(
            defaultFontFamilyResolver = androidx.compose.ui.text.font.createFontFamilyResolver(
                ApplicationProvider.getApplicationContext()
            ),
            defaultDensity = density,
            defaultLayoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        )
        val failures = mutableListOf<String>()
        for (verse in verses) {
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
            val widened = widenRubyBases(render.text, render.rubies, textStyle, rubyStyle, textMeasurer, density, widthPx)
            val layout = textMeasurer.measure(
                widened,
                textStyle,
                constraints = Constraints(maxWidth = widthPx),
                density = density,
            )
            for (line in 0 until layout.lineCount) {
                val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                if (lineWidth > widthPx + 0.5f) {
                    failures += "gutter=$gutter width=$widthPx ${fontSizeDp}dp line $line ran to ${lineWidth}px"
                }
                if (line < layout.lineCount - 1 && joinSplitAWord(widened.text, layout.getLineEnd(line))) {
                    val cut = layout.getLineEnd(line)
                    failures += "gutter=$gutter width=$widthPx ${fontSizeDp}dp line $line a join split a word: " +
                        "…${widened.text.substring((cut - 8).coerceAtLeast(0), cut)}|${widened.text.substring(cut, (cut + 8).coerceAtMost(widened.text.length))}…"
                }
            }
        }
        }
        }
        }
        assertTrue("holding a base run together must not break a word or overrun a line:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
