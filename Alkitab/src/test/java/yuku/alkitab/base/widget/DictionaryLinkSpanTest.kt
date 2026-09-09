package yuku.alkitab.base.widget

import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryLinkSpanTest {

    @Test
    fun `a dictionary link underlines the word but leaves the color of the surrounding run alone`() {
        val span = DictionaryLinkSpan(DictionaryLinkInfo("body", "key_body")) {}
        val paint = TextPaint().apply {
            color = 0xff123456.toInt()
            linkColor = 0xffabcdef.toInt()
        }

        span.updateDrawState(paint)

        assertTrue(paint.isUnderlineText)
        assertEquals(0xff123456.toInt(), paint.color)
    }
}
