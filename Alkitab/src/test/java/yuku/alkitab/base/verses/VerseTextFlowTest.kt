package yuku.alkitab.base.verses

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRendererCompose

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseTextFlowTest {
    private val style = TextStyle(fontSize = 17.sp)
    private val measurer by lazy {
        TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)
    }
    private fun layout(text: AnnotatedString, width: Int = 160, layoutDirection: LayoutDirection = LayoutDirection.Ltr): TextLayoutResult =
        measurer.measure(text, style, constraints = Constraints(maxWidth = width), layoutDirection = layoutDirection)

    private fun render(text: AnnotatedString) = VerseRendererCompose.Result(text, null, 0, emptyList(), emptyList(), text.text)

    @Test
    fun `only lines beside the icons stay narrow and the remainder uses the full width`() {
        val source = render(AnnotatedString("Words of mercy and peace fill this verse. ".repeat(12)))
        val narrow = layout(source.text)
        val iconHeight = narrow.getLineBottom(1).toInt()
        val (top, bottom) = splitVerseAroundAttributes(source, source.text, narrow, iconHeight)

        assertNotNull(bottom)
        assertEquals(source.text.text, top.text.text + bottom!!.text.text)
        assertEquals(2, layout(top.text).lineCount)
        assertTrue(layout(top.text).size.height >= iconHeight)
        assertTrue(layout(bottom.text, 240).lineCount < layout(bottom.text, 160).lineCount)
    }

    @Test
    fun `a continued paragraph keeps its rest indent instead of repeating the first line indent`() {
        val text = buildAnnotatedString {
            append("Words of mercy and peace fill this verse. ".repeat(5))
            addStyle(ParagraphStyle(textIndent = TextIndent(40.sp, 6.sp)), 0, length)
        }
        val narrow = layout(text)
        val (_, bottom) = splitVerseAroundAttributes(render(text), text, narrow, narrow.getLineBottom(0).toInt())

        assertEquals(6f, layout(bottom!!.text, 240).getBoundingBox(0).left, 0.1f)
    }

    @Test
    fun `a new formatted paragraph keeps its first line indent`() {
        val text = buildAnnotatedString {
            append("Title")
            addStyle(ParagraphStyle(textIndent = TextIndent(0.sp, 0.sp)), 0, length)
            val start = length
            append("Words of mercy and peace fill this verse. ".repeat(3))
            addStyle(ParagraphStyle(textIndent = TextIndent(40.sp, 6.sp)), start, length)
        }
        val narrow = layout(text)
        val (_, bottom) = splitVerseAroundAttributes(render(text), text, narrow, narrow.getLineBottom(0).toInt())

        assertEquals(40f, layout(bottom!!.text, 240).getBoundingBox(0).left, 0.1f)
    }

    @Test
    fun `an explicit line break at the transition does not add a blank line`() {
        val source = render(AnnotatedString("First line\nSecond line with more words"))
        val narrow = layout(source.text)
        val (top, bottom) = splitVerseAroundAttributes(source, source.text, narrow, narrow.getLineBottom(0).toInt())

        assertEquals("First line", top.text.text)
        assertEquals(1, layout(top.text).lineCount)
        assertEquals("Second line with more words", bottom!!.text.text)
    }

    @Test
    fun `empty ruby bases on either side of a transition newline are retained once`() {
        val text = AnnotatedString("First line\nSecond line with more words")
        val source = render(text).copy(rubies = listOf(
            VerseRendererCompose.RubyRange(10, 10, "before", null),
            VerseRendererCompose.RubyRange(11, 11, "after", null),
        ))
        val narrow = layout(text)
        val (top, bottom) = splitVerseAroundAttributes(source, text, narrow, narrow.getLineBottom(0).toInt())

        assertEquals(listOf("before"), top.rubies.map { it.ruby })
        assertEquals(10, top.rubies.single().start)
        assertEquals(listOf("after"), bottom!!.rubies.map { it.ruby })
        assertEquals(0, bottom.rubies.single().start)
    }

    @Test
    fun `links highlights and ruby offsets survive the transition`() {
        val text = buildAnnotatedString {
            append("First line\nlinked ruby")
            addStyle(SpanStyle(background = Color.Yellow), 11, 22)
            addLink(LinkAnnotation.Clickable("dictionary") {}, 11, 17)
        }
        val source = render(text).copy(
            inlineLinks = listOf(VerseRendererCompose.InlineLinkRange(11, 17, VerseInlineLinkSpan.Type.footnote, 123)),
            rubies = listOf(VerseRendererCompose.RubyRange(18, 22, "reading", null)),
        )
        val narrow = layout(text)
        val (_, bottom) = splitVerseAroundAttributes(source, text, narrow, narrow.getLineBottom(0).toInt())

        assertEquals("linked ruby", bottom!!.text.text)
        assertEquals(0, bottom.inlineLinks.single().start)
        assertEquals(123, bottom.inlineLinks.single().arif)
        assertEquals(7, bottom.rubies.single().start)
        assertEquals(11, bottom.rubies.single().end)
        assertEquals(0, bottom.text.getLinkAnnotations(0, bottom.text.length).single().start)
        assertEquals(Color.Yellow, bottom.text.spanStyles.single().item.background)
    }

    @Test
    fun `a ruby base crossing the width transition stays with its reading`() {
        val text = AnnotatedString("Words of mercy and peace fill this verse. ".repeat(5))
        val narrow = layout(text)
        val rubyEnd = narrow.getLineEnd(1) - 2
        val source = render(text).copy(rubies = listOf(VerseRendererCompose.RubyRange(0, rubyEnd, "reading", null)))
        val (top, bottom) = splitVerseAroundAttributes(source, text, narrow, narrow.getLineBottom(0).toInt())

        assertTrue(top.text.length >= rubyEnd)
        assertEquals(source.rubies, top.rubies)
        assertTrue(bottom!!.rubies.isEmpty())
        assertEquals(text.text, top.text.text + bottom.text.text)
    }

    @Test
    fun `a continuation retains the original right to left paragraph direction`() {
        val text = AnnotatedString("שלום " + "mercy and peace ".repeat(10))
        val narrow = layout(text, layoutDirection = LayoutDirection.Rtl)
        assertEquals(ResolvedTextDirection.Rtl, narrow.getParagraphDirection(0))
        val (_, bottom) = splitVerseAroundAttributes(render(text), text, narrow, narrow.getLineBottom(0).toInt())

        assertFalse(bottom!!.text.text.contains("שלום"))
        assertEquals(ResolvedTextDirection.Rtl, layout(bottom.text, 240).getParagraphDirection(0))
    }

    @Test
    fun `short text beside tall attributes stays in one block`() {
        val source = render(AnnotatedString("Short verse"))
        val (top, bottom) = splitVerseAroundAttributes(source, source.text, layout(source.text), 200)

        assertEquals(source, top)
        assertNull(bottom)
    }
}
