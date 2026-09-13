package yuku.alkitab.base.verses

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `the side gap counts towards the width the base must reach`() {
        assertEquals(0f, rubyLetterSpacingPx(40f, 30f, 2, sideGapPx = 5f))
        assertEquals(4f, rubyLetterSpacingPx(40f, 38f, 2, sideGapPx = 5f))
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
