package yuku.alkitab.base.verses

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.base.widget.VerseRendererCompose.RubyRange

class VerseRubyTest {

    @Test
    fun `a base run that fits on one line keeps its whole ruby`() {
        assertEquals("たくせん", rubySliceFor("たくせん", 10, 12, 10, 12))
        assertEquals("たくせん", rubySliceFor("たくせん", 10, 12, 8, 14))
    }

    @Test
    fun `a base run broken across lines splits its ruby by character count`() {
        assertEquals("たく", rubySliceFor("たくせん", 10, 12, 10, 11))
        assertEquals("せん", rubySliceFor("たくせん", 10, 12, 11, 12))
    }

    @Test
    fun `an uneven split rounds to the nearest ruby character`() {
        assertEquals("しゅ", rubySliceFor("しゅう", 0, 2, 0, 1))
        assertEquals("う", rubySliceFor("しゅう", 0, 2, 1, 2))
    }

    @Test
    fun `a slice never splits a surrogate pair`() {
        val ruby = "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02"
        val first = rubySliceFor(ruby, 0, 4, 0, 1)
        val rest = rubySliceFor(ruby, 0, 4, 1, 4)
        assertTrue(first.isEmpty() || Character.isLowSurrogate(first.last()))
        assertTrue(rest.isEmpty() || Character.isHighSurrogate(rest.first()))
        assertEquals(ruby, first + rest)
    }

    @Test
    fun `an empty segment yields no ruby`() {
        assertEquals("", rubySliceFor("abc", 0, 3, 2, 2))
        assertEquals("", rubySliceFor("abc", 5, 5, 5, 5))
    }

    @Test
    fun `letter spacing is zero when the ruby is narrower than its base`() {
        assertEquals(0f, rubyLetterSpacingPx(40f, 30f, 2))
        assertEquals(0f, rubyLetterSpacingPx(40f, 40f, 2))
    }

    @Test
    fun `letter spacing spreads the overflow evenly over the base characters`() {
        assertEquals(5f, rubyLetterSpacingPx(40f, 50f, 2))
        assertEquals(10f, rubyLetterSpacingPx(40f, 50f, 1))
    }

    @Test
    fun `letter spacing is zero for an empty base`() {
        assertEquals(0f, rubyLetterSpacingPx(0f, 50f, 0))
    }

    @Test
    fun `a gap to a neighbouring ruby counts towards the width the base must reach`() {
        assertEquals(0f, rubyLetterSpacingPx(40f, 30f, 2, leftSlackPx = -5f, rightSlackPx = -5f))
        assertEquals(4f, rubyLetterSpacingPx(40f, 38f, 2, leftSlackPx = -5f, rightSlackPx = -5f))
    }

    @Test
    fun `overhang over ruby-free neighbours reduces the width the base must reach`() {
        assertEquals(0f, rubyLetterSpacingPx(40f, 50f, 2, leftSlackPx = 8f, rightSlackPx = 8f))
        assertEquals(1f, rubyLetterSpacingPx(40f, 50f, 2, leftSlackPx = 8f, rightSlackPx = 0f))
    }

    @Test
    fun `side slack depends on what the neighbouring character carries`() {
        val rubies = listOf(RubyRange(2, 3, "しゅ"), RubyRange(3, 4, "い"))
        val text = "ab主言う\nx"
        assertEquals(8f, rubySideSlackPx(text, rubies, 1, -1, 8f, 2f))
        assertEquals(-2f, rubySideSlackPx(text, rubies, 3, 1, 8f, 2f))
        assertEquals(8f, rubySideSlackPx(text, rubies, 4, 1, 8f, 2f))
        assertEquals(0f, rubySideSlackPx(text, rubies, 5, 1, 8f, 2f))
        assertEquals(0f, rubySideSlackPx(text, rubies, -1, -1, 8f, 2f))
        assertEquals(0f, rubySideSlackPx(text, rubies, text.length, 1, 8f, 2f))
    }

    @Test
    fun `a ruby beyond a space still counts as a neighbouring ruby`() {
        val rubies = listOf(RubyRange(0, 3, "in the beginning"), RubyRange(4, 6, "created"))
        val text = "aaa bb cc"
        assertEquals(-2f, rubySideSlackPx(text, rubies, 3, 1, 8f, 2f))
        assertEquals(-2f, rubySideSlackPx(text, rubies, 3, -1, 8f, 2f))
        assertEquals(8f, rubySideSlackPx(text, rubies, 6, 1, 8f, 2f))
    }

    @Test
    fun `ruby color follows the innermost colored span at the base offset`() {
        val text = buildAnnotatedString {
            append("abcdef")
            addStyle(SpanStyle(color = Color.Red), 0, 6)
            addStyle(SpanStyle(color = Color.Blue), 2, 4)
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), 0, 6)
        }
        assertEquals(Color.Red, rubyColorAt(text, 0, Color.Black))
        assertEquals(Color.Blue, rubyColorAt(text, 2, Color.Black))
        assertEquals(Color.Red, rubyColorAt(text, 4, Color.Black))
        assertEquals(Color.Black, rubyColorAt(AnnotatedString("abc"), 1, Color.Black))
    }
}
