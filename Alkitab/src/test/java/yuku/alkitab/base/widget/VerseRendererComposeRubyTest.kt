package yuku.alkitab.base.widget

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.S
import yuku.alkitab.base.widget.VerseRendererCompose.RubyRange

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseRendererComposeRubyTest {

    private val ARI = 0x010203

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        S.overrideAppliedDimensions(S.CalculatedDimensions().apply {
            fontColor = 0xff212121.toInt()
            fontRedColor = 0xffb71c1c.toInt()
            verseNumberColor = 0xff828282.toInt()
            backgroundColor = 0xfff0f0f0.toInt()
            indentParagraphRest = 5
        })
    }

    private fun render(text: String, isVerseNumberShown: Boolean = true) =
        VerseRendererCompose.render(isVerseNumberShown = isVerseNumberShown, ari = ARI, text = text, verseNumberText = "1")

    @Test
    fun `ruby tags leave the base text inline and report their ranges`() {
        val result = render("@@@<r=しゅ@>主@/は@<r=い@>言@/われる")
        assertEquals("1  主は言われる", result.text.text)
        assertEquals(listOf(RubyRange(3, 4, "しゅ"), RubyRange(5, 6, "い")), result.rubies)
    }

    @Test
    fun `ruby offsets follow the hidden verse number`() {
        val result = render("@@@<r=かみ@>神@/は", isVerseNumberShown = false)
        assertEquals("神は", result.text.text)
        assertEquals(listOf(RubyRange(0, 1, "かみ")), result.rubies)
    }

    @Test
    fun `a multi-character base gets one range`() {
        val result = render("@@@<r=chuàngzào@>创造@/天地")
        assertEquals("1  创造天地", result.text.text)
        assertEquals(listOf(RubyRange(3, 5, "chuàngzào")), result.rubies)
    }

    @Test
    fun `ruby works alongside red letters and footnotes`() {
        val result = render("@@@6@<r=しゅ@>主@/@5@<f1@>@/")
        assertEquals("1  主¹", result.text.text)
        assertEquals(listOf(RubyRange(3, 4, "しゅ")), result.rubies)
        assertEquals(1, result.inlineLinks.size)
        assertEquals(4, result.inlineLinks[0].start)
    }

    @Test
    fun `an empty base or an empty reading produces no ruby`() {
        assertTrue(render("@@@<r=しゅ@>@/主").rubies.isEmpty())
        assertTrue(render("@@@<r=@>主@/").rubies.isEmpty())
        assertEquals("1  主", render("@@@<r=@>主@/").text.text)
    }

    @Test
    fun `a stray closing tag after a ruby produces no second range`() {
        val result = render("@@@<r=しゅ@>主@/は@/言")
        assertEquals("1  主は言", result.text.text)
        assertEquals(listOf(RubyRange(3, 4, "しゅ")), result.rubies)
    }

    @Test
    fun `a verse without formatting has no ruby`() {
        assertTrue(render("主は言われる").rubies.isEmpty())
    }
}
