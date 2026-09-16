package yuku.alkitab.base.verses

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertArrayEquals
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
    fun `only kana, ideographs and hangul let a reading split across lines`() {
        assertTrue(rubySplitsAcrossLines("創造"))
        assertTrue(rubySplitsAcrossLines("さいわい"))
        assertTrue(rubySplitsAcrossLines("创造"))
        assertTrue(rubySplitsAcrossLines("하나님"))
        assertTrue(!rubySplitsAcrossLines("di hadapan Allah"))
        assertTrue(!rubySplitsAcrossLines("בְּרֵאשִׁית"))
        assertTrue(!rubySplitsAcrossLines("λόγος"))
    }

    @Test
    fun `an empty segment yields no ruby`() {
        assertEquals("", rubySliceFor("abc", 0, 3, 2, 2))
        assertEquals("", rubySliceFor("abc", 5, 5, 5, 5))
    }

    @Test
    fun `letter spacing is zero when the ruby is narrower than its base`() {
        assertEquals(0f, rubyLetterSpacingPx(40f, 30f, 2, 0f, 0f))
        assertEquals(0f, rubyLetterSpacingPx(40f, 40f, 2, 0f, 0f))
    }

    @Test
    fun `letter spacing spreads the overflow evenly over the base characters`() {
        assertEquals(5f, rubyLetterSpacingPx(40f, 50f, 2, 0f, 0f))
        assertEquals(10f, rubyLetterSpacingPx(40f, 50f, 1, 0f, 0f))
    }

    @Test
    fun `letter spacing is zero for an empty base`() {
        assertEquals(0f, rubyLetterSpacingPx(0f, 50f, 0, 0f, 0f))
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
        val rubies = listOf(RubyRange(2, 3, "しゅ", null), RubyRange(3, 4, "い", null))
        val text = "ab主言う\nx"
        assertEquals(8f, rubySideSlackPx(text, rubies, 1, -1, 8f, 2f, 4f))
        assertEquals(-2f, rubySideSlackPx(text, rubies, 3, 1, 8f, 2f, 4f))
        assertEquals(8f, rubySideSlackPx(text, rubies, 4, 1, 8f, 2f, 4f))
        assertEquals(0f, rubySideSlackPx(text, rubies, 5, 1, 8f, 2f, 4f))
        assertEquals(0f, rubySideSlackPx(text, rubies, -1, -1, 8f, 2f, 4f))
        assertEquals(0f, rubySideSlackPx(text, rubies, text.length, 1, 8f, 2f, 4f))
    }

    @Test
    fun `two annotated words split the space between them, each keeping a side gap`() {
        val rubies = listOf(RubyRange(0, 3, "in the beginning", null), RubyRange(4, 6, "created", null))
        val text = "aaa bb cc"
        assertEquals(0f, rubySideSlackPx(text, rubies, 3, 1, 8f, 2f, 4f))
        assertEquals(0f, rubySideSlackPx(text, rubies, 3, -1, 8f, 2f, 4f))
    }

    @Test
    fun `a wider space between two annotated words gives each of them more room`() {
        val rubies = listOf(RubyRange(0, 3, "in the beginning", null), RubyRange(5, 7, "created", null))
        val text = "aaa  bb cc"
        assertEquals(6f, rubySideSlackPx(text, rubies, 3, 1, 8f, 2f, 8f))
    }

    @Test
    fun `the space beside an unannotated neighbour is claimed whole, on top of the overhang`() {
        val rubies = listOf(RubyRange(0, 3, "in the beginning", null), RubyRange(4, 6, "created", null))
        val text = "aaa bb cc"
        assertEquals(12f, rubySideSlackPx(text, rubies, 6, 1, 8f, 2f, 4f))
    }

    @Test
    fun `a reading narrower than its base leaves room beside it for the next reading`() {
        assertEquals(97.5f, rubyCentredLeftPx(90f, 145f, 40f))
        assertEquals(137.5f, rubyCentredLeftPx(90f, 145f, 40f) + 40f)
    }

    @Test
    fun `a reading wider than its base starts before the base does`() {
        assertEquals(5f, rubyCentredLeftPx(10f, 30f, 30f))
    }

    @Test
    fun `readings that do not collide keep the place they asked for`() {
        val xs = rubyLineLayoutPx(
            desiredLeftPx = floatArrayOf(10f, 100f, 200f),
            widthPx = floatArrayOf(30f, 30f, 30f),
            lineLeftPx = 0f,
            lineRightPx = 360f,
            sideGapPx = 2f,
        )
        assertArrayEquals(floatArrayOf(10f, 100f, 200f), xs, 0.01f)
    }

    @Test
    fun `a reading in the way pushes the next one right instead of being shortened`() {
        val xs = rubyLineLayoutPx(
            desiredLeftPx = floatArrayOf(0f, 20f),
            widthPx = floatArrayOf(130f, 40f),
            lineLeftPx = 0f,
            lineRightPx = 360f,
            sideGapPx = 2f,
        )
        assertArrayEquals(floatArrayOf(0f, 132f), xs, 0.01f)
    }

    @Test
    fun `the last reading is pulled back so it stays on the line`() {
        val xs = rubyLineLayoutPx(
            desiredLeftPx = floatArrayOf(300f),
            widthPx = floatArrayOf(100f),
            lineLeftPx = 0f,
            lineRightPx = 360f,
            sideGapPx = 2f,
        )
        assertArrayEquals(floatArrayOf(260f), xs, 0.01f)
    }

    @Test
    fun `positions still rise left to right when the line holds more than it can fit`() {
        val xs = rubyLineLayoutPx(
            desiredLeftPx = floatArrayOf(0f, 10f, 20f),
            widthPx = floatArrayOf(200f, 200f, 200f),
            lineLeftPx = 0f,
            lineRightPx = 360f,
            sideGapPx = 2f,
        )
        for (i in 1 until xs.size) assertTrue("positions must not go backwards: ${xs.toList()}", xs[i] >= xs[i - 1])
        assertTrue("every reading starts inside the line: ${xs.toList()}", xs.all { it >= 0f })
    }

    @Test
    fun `a line with room lets every reading keep its whole width`() {
        val widths = floatArrayOf(130f, 40f, 40f)
        val xs = rubyLineLayoutPx(
            desiredLeftPx = floatArrayOf(-45f, 20f, 300f),
            widthPx = widths,
            lineLeftPx = 0f,
            lineRightPx = 720f,
            sideGapPx = 2f,
        )
        val allowed = rubyAllowedWidthsPx(xs, 720f, 2f)
        for (i in widths.indices) {
            assertTrue("reading $i may only take ${allowed[i]} of the ${widths[i]} it needs", allowed[i] >= widths[i])
        }
    }

    @Test
    fun `a line with more readings than room hands back less than they need, never a negative`() {
        val widths = floatArrayOf(200f, 200f, 200f)
        val xs = rubyLineLayoutPx(
            desiredLeftPx = floatArrayOf(0f, 10f, 20f),
            widthPx = widths,
            lineLeftPx = 0f,
            lineRightPx = 360f,
            sideGapPx = 2f,
        )
        val allowed = rubyAllowedWidthsPx(xs, 360f, 2f)
        assertTrue("widths must never go negative: ${allowed.toList()}", allowed.all { it >= 0f })
        assertTrue("a crowded line must shorten something: ${allowed.toList()}", allowed.indices.any { allowed[it] < widths[it] })
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
